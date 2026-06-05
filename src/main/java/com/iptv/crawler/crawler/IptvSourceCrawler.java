package com.iptv.crawler.crawler;

import com.iptv.crawler.config.IptvProperties;
import com.iptv.crawler.config.IptvProperties.SourceConfig;
import com.iptv.crawler.entity.IptvChannel;
import com.iptv.crawler.entity.SourceMetrics;
import com.iptv.crawler.service.BlacklistService;
import com.iptv.crawler.service.SourceBlacklistService;
import com.iptv.crawler.service.SourceMetricsService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 从多个公开IPTV源爬取直播频道
 * <p>支持并发爬取、重试机制、熔断器、健康度监控</p>
 */
@Component
public class IptvSourceCrawler extends BaseCrawler {

    private static final Logger log = LoggerFactory.getLogger(IptvSourceCrawler.class);

    private final OkHttpClient client;
    private final OkHttpClient largeFileClient;
    private final IptvProperties properties;
    private final SourceMetricsService metricsService;
    private final BlacklistService blacklistService;
    private final SourceBlacklistService sourceBlacklistService;

    // 运行时配置值（从properties初始化）
    private final int concurrency;
    private final int maxRetries;
    private final long retryDelayMs;
    private final int circuitBreakerThreshold;

    // 分辨率过滤配置
    private final int minResolutionPixels;   // 最小分辨率数值（如 1080）
    private final boolean resolutionPermissive; // 无标注时是否放行

    public IptvSourceCrawler(
            IptvProperties properties,
            SourceMetricsService metricsService,
            BlacklistService blacklistService,
            SourceBlacklistService sourceBlacklistService) {
        super("crawler");
        this.properties = properties;
        this.metricsService = metricsService;
        this.blacklistService = blacklistService;
        this.sourceBlacklistService = sourceBlacklistService;

        // 初始化配置值
        IptvProperties.CrawlerConfig crawlerConfig = properties.getCrawler();
        this.concurrency = crawlerConfig.getConcurrency();
        this.maxRetries = crawlerConfig.getMaxRetries();
        this.retryDelayMs = crawlerConfig.getRetryDelayMs();
        this.circuitBreakerThreshold = crawlerConfig.getCircuitBreaker() != null ?
                crawlerConfig.getCircuitBreaker().getFailureThreshold() : 5;

        // 初始化分辨率过滤配置
        this.minResolutionPixels = parseResolutionPixels(crawlerConfig.getMinResolution());
        this.resolutionPermissive = "permissive".equalsIgnoreCase(crawlerConfig.getResolutionFilterMode());
        log.info("分辨率过滤: min={}p, mode={}",
                minResolutionPixels, resolutionPermissive ? "permissive" : "conservative");

        // 常规HTTP客户端
        this.client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();

        // 大文件使用更长超时
        this.largeFileClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();
    }

    /**
     * 主爬取方法（支持并发）
     */
    @Override
    public List<IptvChannel> crawl() throws Exception {
        List<SourceConfig> sources = properties.getSources();

        if (sources.isEmpty()) {
            log.warn("未配置任何直播源，请在 application.yml 中配置 iptv.sources");
            return List.of();
        }

        int totalSources = sources.size();
        log.info("");
        log.info("┌──────────────────────────────────────────────┐");
        log.info("│  开始并发爬取: {} 个直播源 (线程数: {})", padRight(String.valueOf(totalSources), 3), concurrency);
        log.info("└──────────────────────────────────────────────┘");

        // 打印所有源名称
        for (int i = 0; i < sources.size(); i++) {
            log.info("  源 [{}] {} → {}", (i + 1), sources.get(i).getName(), sources.get(i).getUrl());
        }
        log.info("");

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);

            // 提交所有源的爬取任务
            List<CompletableFuture<List<IptvChannel>>> futures = new ArrayList<>();
            for (int i = 0; i < sources.size(); i++) {
                final int index = i + 1;
                SourceConfig source = sources.get(i);
                log.info("  ▶ 开始爬取源 [{}/{}]: {} ...", index, totalSources, source.getName());
                CompletableFuture<List<IptvChannel>> future = CompletableFuture.supplyAsync(() -> {
                    List<IptvChannel> result = crawlSingleSource(source);
                    int done = completed.incrementAndGet();
                    int count = result.size();
                    log.info("  ✓ 源 [{}/{}] {} 完成 → {} 条频道 (进度: {}/{})",
                            done, totalSources, source.getName(), count, done, totalSources);
                    return result;
                }, executor);
                futures.add(future);
            }

            // 收集所有结果
            List<IptvChannel> allChannels = new CopyOnWriteArrayList<>();
            int[] totalCount = {0};

            for (CompletableFuture<List<IptvChannel>> future : futures) {
                try {
                    List<IptvChannel> channels = future.get(60, TimeUnit.SECONDS);
                    totalCount[0] += channels.size();
                    allChannels.addAll(channels);
                } catch (Exception e) {
                    log.error("爬取任务执行异常: {}", e.getMessage());
                }
            }

            log.info("");
            log.info("  并发爬取完成: 共 {} 条有效频道（去重前）", totalCount[0]);

            // 去重（基于URL）
            Map<String, IptvChannel> uniqueByUrl = allChannels.stream()
                    .collect(Collectors.toMap(
                            IptvChannel::getUrl,
                            ch -> ch,
                            (existing, replacement) -> existing // 保留第一个
                    ));

            int dedupCount = totalCount[0] - uniqueByUrl.size();
            List<IptvChannel> result = new ArrayList<>(uniqueByUrl.values());
            log.info("  去重: 移除 {} 条重复 → 最终 {} 条频道", dedupCount, result.size());
            log.info("┌──────────────────────────────────────────────┐");
            log.info("│  爬取完成! 总计 {} 条频道", padRight(String.valueOf(result.size()), 13));
            log.info("└──────────────────────────────────────────────┘");
            log.info("");
            return result;

        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * 单个源爬取逻辑（包含重试、熔断、指标）
     */
    private List<IptvChannel> crawlSingleSource(SourceConfig source) {
        String sourceName = source.getName();
        long startTime = System.currentTimeMillis();

        // 检查源黑名单（持久化，重启后仍生效）
        if (sourceBlacklistService.contains(sourceName)) {
            log.debug("[黑名单] 跳过源: {} (已加入 source_blacklist.txt)", sourceName);
            return List.of();
        }

        // 检查熔断器
        if (metricsService.isCircuitBreakerOpen(sourceName)) {
            log.warn("[熔断] 跳过源: {} (连续失败超过阈值)", sourceName);
            return List.of();
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 记录尝试次数到日志
                if (attempt > 1) {
                    log.info("[重试] 第{}次重试源: {}", attempt, sourceName);
                }

                String m3uContent = fetchUrl(source.getUrl(),
                        source.isLarge() ? largeFileClient : client);

                List<IptvChannel> parsed = parseM3uContent(m3uContent, sourceName);
                List<IptvChannel> filtered = filterValidChannels(parsed);

                long elapsed = System.currentTimeMillis() - startTime;

                // 记录成功指标
                metricsService.recordSuccess(sourceName, filtered.size(), elapsed);

                log.info("[成功] 源: {} (耗时: {}ms, 频道: {})",
                        sourceName, elapsed, filtered.size());

                return filtered;

            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;

                if (attempt == maxRetries) {
                    // 最终失败
                    log.warn("[失败] 源: {} (总耗时: {}ms, 已重试{}次) - {}",
                            sourceName, elapsed, maxRetries, e.getMessage());
                    metricsService.recordFailure(sourceName, e.getMessage());

                    // 检查是否触发熔断
                    SourceMetrics metrics = metricsService.getOrCreate(sourceName);
                    if (metrics.getConsecutiveFailures() >= circuitBreakerThreshold) {
                        log.error("[熔断触发!] 源: {} 连续失败 {} 次，将暂停爬取",
                                sourceName, metrics.getConsecutiveFailures());
                    }
                    return List.of();
                }

                // 指数退避等待
                try {
                    long delay = retryDelayMs * attempt;
                    log.debug("[等待] 源: {} 第{}次重试前等待 {}ms", sourceName, attempt, delay);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("[中断] 源爬取被中断: {}", sourceName);
                    return List.of();
                }
            }
        }

        return List.of();
    }

    // ========== 原有辅助方法保持不变 ==========

    private String fetchUrl(String url, OkHttpClient httpClient) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "*/*")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    private List<IptvChannel> parseM3uContent(String content, String source) {
        List<IptvChannel> list = new ArrayList<>();
        String[] lines = content.split("\\n");
        String currentName = "";
        String currentCategory = "未分类";

        Pattern extinfPattern = Pattern.compile(
                "#EXTINF:[-\\d]+(?:.*?group-title=\"([^\"]*)\")?(?:.*?,\\s*)?(.+)"
        );

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#EXTINF:")) {
                Matcher m = extinfPattern.matcher(line);
                if (m.find()) {
                    if (m.group(1) != null && !m.group(1).isEmpty()) {
                        currentCategory = m.group(1);
                    }
                    currentName = m.group(2) != null ? m.group(2).trim() : "";
                }
            } else if (!line.startsWith("#") && !line.isEmpty()
                    && (line.startsWith("http://") || line.startsWith("https://")
                    || line.startsWith("rtmp") || line.startsWith("rtsp"))
                    && !currentName.isEmpty()) {
                String protocol = guessProtocol(line);
                IptvChannel ch = createChannel(currentName, line, currentCategory, protocol);
                ch.setSource(source);
                // 设置初始质量评分（中位数）
                ch.setAvailable(false);
                ch.setLatencyMs(null);
                ch.setBandwidthKbps(null);
                list.add(ch);
                currentName = "";
            }
        }
        return list;
    }

    /**
     * 解析分辨率配置字符串为像素高度
     * "1080p"→1080, "4k"→2160, "720p"→720
     */
    private int parseResolutionPixels(String res) {
        if (res == null) return 1080;
        String lower = res.toLowerCase().trim();
        // 4k / 2160p  → 2160
        if (lower.contains("4k") || lower.equals("2160p") || lower.equals("2160")) return 2160;
        // 1080p → 1080
        if (lower.contains("1080p") || lower.equals("1080")) return 1080;
        // 720p → 720
        if (lower.contains("720p") || lower.equals("720")) return 720;
        // 480p → 480
        if (lower.contains("480p") || lower.equals("480")) return 480;
        // 360p → 360
        if (lower.contains("360p") || lower.equals("360")) return 360;
        // 默认安全值
        log.warn("无法解析分辨率配置 '{}', 使用默认 1080p", res);
        return 1080;
    }

    /**
     * 从频道名中提取分辨率数值，如 "CCTV-1 (1080p)" → 1080
     */
    private Integer extractResolution(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();
        for (String kw : new String[]{"2160p", "4k", "1080p", "720p", "600p", "540p", "576p", "480p", "360p", "270p", "240p", "144p"}) {
            if (lower.contains(kw)) {
                switch (kw) {
                    case "2160p": case "4k": return 2160;
                    case "1080p": return 1080;
                    case "720p": return 720;
                    case "600p": case "540p": case "576p": return 540;
                    case "480p": return 480;
                    case "360p": return 360;
                    case "270p": case "240p": case "144p": return 240;
                }
            }
        }
        return null; // 无分辨率标注
    }

    /**
     * 判断频道名是否达到配置的最低分辨率要求
     */
    private boolean isResolutionMet(String name) {
        if (name == null) return false;
        Integer nameRes = extractResolution(name);
        if (nameRes != null) {
            return nameRes >= minResolutionPixels;
        }
        // 无分辨率标注 → 按模式处理
        return resolutionPermissive;
    }

    /**
     * 过滤无效频道（内网、组播 + 配置文件中屏蔽的频道 + black_list.txt + 低于1080p）
     */
    private List<IptvChannel> filterValidChannels(List<IptvChannel> channels) {
        IptvProperties.BlocklistConfig blocklist = properties.getBlocklist();
        int[] discard = {0, 0}; // [blackListCount, lowResCount]

        List<IptvChannel> result = channels.stream()
                .filter(ch -> !isPrivateOrMulticast(ch.getUrl()))
                .filter(ch -> !isBlocked(ch, blocklist))
                .filter(ch -> {
                    if (blacklistService.contains(ch.getUrl())) {
                        discard[0]++;
                        return false;
                    }
                    return true;
                })
                .filter(ch -> {
                    if (!isResolutionMet(ch.getName())) {
                        discard[1]++;
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (discard[0] > 0) {
            log.info("[black_list] 屏蔽 {} 个频道", discard[0]);
        }
        if (discard[1] > 0) {
            log.info("[分辨率过滤] 跳过低于1080p: {} 个频道", discard[1]);
        }

        return result;
    }

    /**
     * 根据配置文件中的屏蔽规则判断频道是否应跳过
     */
    private boolean isBlocked(IptvChannel ch, IptvProperties.BlocklistConfig blocklist) {
        String name = ch.getName();
        String url = ch.getUrl();
        String cat = ch.getCategory();

        // 精确匹配 excludeNames
        if (blocklist.getExcludeNames() != null) {
            for (String ex : blocklist.getExcludeNames()) {
                if (name.equals(ex)) {
                    log.debug("[屏蔽] 频道名精确匹配: {} == {}", name, ex);
                    return true;
                }
            }
        }

        // 频道名关键词匹配 nameKeywords
        if (blocklist.getNameKeywords() != null) {
            for (String kw : blocklist.getNameKeywords()) {
                if (name.contains(kw)) {
                    log.debug("[屏蔽] 频道名包含关键词: {} 包含 {}", name, kw);
                    return true;
                }
            }
        }

        // URL关键词匹配 urlKeywords
        if (blocklist.getUrlKeywords() != null && url != null) {
            for (String kw : blocklist.getUrlKeywords()) {
                if (url.contains(kw)) {
                    log.debug("[屏蔽] URL包含关键词: {} 包含 {}", url, kw);
                    return true;
                }
            }
        }

        // 分类关键词匹配 categoryKeywords
        if (blocklist.getCategoryKeywords() != null && cat != null) {
            for (String kw : blocklist.getCategoryKeywords()) {
                if (cat.contains(kw)) {
                    log.debug("[屏蔽] 分类包含关键词: {} 包含 {}", cat, kw);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isPrivateOrMulticast(String url) {
        String host = extractHost(url);
        if (host == null) return false;

        // IPv4 私有地址
        if (host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("172.")) {
            if (host.startsWith("172.")) {
                try {
                    String[] parts = host.split("\\.");
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (Exception e) {
                    return true;
                }
            }
            return true;
        }
        // IPv4 回环
        if (host.equals("127.0.0.1") || host.startsWith("127.")) return true;
        // IPv6 检测
        if (host.contains(":") || host.startsWith("[")) return true;
        // 组播地址 (224.0.0.0 - 239.255.255.255)
        if (host.startsWith("224.") || host.startsWith("225.") || host.startsWith("226.")
                || host.startsWith("227.") || host.startsWith("228.") || host.startsWith("229.")
                || host.startsWith("230.") || host.startsWith("231.") || host.startsWith("232.")
                || host.startsWith("233.") || host.startsWith("234.") || host.startsWith("235.")
                || host.startsWith("236.") || host.startsWith("237.") || host.startsWith("238.")
                || host.startsWith("239.")) {
            return true;
        }
        return false;
    }

    private String extractHost(String url) {
        try {
            String s = url;
            if (s.contains("://")) {
                s = s.substring(s.indexOf("://") + 3);
            }
            int slash = s.indexOf('/');
            int colon = s.indexOf(':');
            if (slash < 0 && colon < 0) return s;
            int end = slash > 0 ? slash : s.length();
            if (colon > 0 && colon < end) end = colon;
            return s.substring(0, end);
        } catch (Exception e) {
            return null;
        }
    }

    private String guessProtocol(String url) {
        if (url.contains(".m3u8")) return "hls";
        if (url.contains(".flv")) return "http-flv";
        if (url.startsWith("rtmp://")) return "rtmp";
        if (url.startsWith("rtsp://")) return "rtsp";
        return "http";
    }

    private static String padRight(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }
}
