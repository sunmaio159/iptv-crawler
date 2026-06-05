package com.iptv.crawler.service;

import com.iptv.crawler.crawler.IptvSourceCrawler;
import com.iptv.crawler.entity.IptvChannel;
import com.iptv.crawler.repository.IptvChannelRepository;
import com.iptv.crawler.speedtest.FfmpegSpeedTester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class IptvService {

    private static final Logger log = LoggerFactory.getLogger(IptvService.class);

    // == cnOnly 精确匹配的目标频道列表（固定顺序） ==
    private static final List<String> CCTV_TARGETS = List.of(
            "CCTV-1", "CCTV-2", "CCTV-3", "CCTV-4", "CCTV-5", "CCTV-5+",
            "CCTV-6", "CCTV-7", "CCTV-8", "CCTV-9", "CCTV-10", "CCTV-11",
            "CCTV-12", "CCTV-13", "CCTV-14", "CCTV-15", "CCTV-16", "CCTV-17"
    );

    private static final List<String> SAT_TARGETS = List.of(
            "广东卫视", "香港卫视", "浙江卫视", "湖南卫视", "北京卫视", "湖北卫视",
            "黑龙江卫视", "安徽卫视", "重庆卫视", "东方卫视", "东南卫视", "甘肃卫视",
            "广西卫视", "贵州卫视", "海南卫视", "河北卫视", "河南卫视", "吉林卫视",
            "江苏卫视", "江西卫视", "辽宁卫视", "内蒙古卫视", "宁夏卫视", "青海卫视",
            "山东卫视", "山西卫视", "陕西卫视", "四川卫视", "深圳卫视", "三沙卫视",
            "天津卫视", "西藏卫视", "新疆卫视", "云南卫视"
    );

    private static final Pattern CCTV_NUM_PATTERN = Pattern.compile("CCTV-?(\\d+\\+?)");

    private final IptvChannelRepository channelRepository;
    private final IptvSourceCrawler crawler;
    private final FfmpegSpeedTester speedTester;
    private final SourceMetricsService metricsService;

    @Value("${iptv.speedtest.max-probe-seconds:8}")
    private int maxProbeSeconds;

    public IptvService(IptvChannelRepository channelRepository,
                       IptvSourceCrawler crawler,
                       FfmpegSpeedTester speedTester,
                       SourceMetricsService metricsService) {
        this.channelRepository = channelRepository;
        this.crawler = crawler;
        this.speedTester = speedTester;
        this.metricsService = metricsService;
    }

    /**
     * 只执行爬取，不测速
     */
    @Transactional
    public int crawlOnly() {
        log.info("===== 开始爬取IPTV源 =====");
        int saved = 0;
        try {
            List<IptvChannel> channels = crawler.crawl();
            log.info("爬取到 {} 个频道", channels.size());
            for (IptvChannel ch : channels) {
                if (!channelRepository.existsByUrl(ch.getUrl())) {
                    channelRepository.save(ch);
                    saved++;
                }
            }
            log.info("新增 {} 个频道", saved);
        } catch (Exception e) {
            log.error("爬取失败: {}", e.getMessage(), e);
        }
        return saved;
    }

    /**
     * 执行完整爬取+两轮测速流程：
     * 1. 爬取所有源 → 去重入库
     * 2. 第一轮快速检测 → 不可用直接标记跳过
     * 3. 第二轮详细测速 → 可用频道获取延迟+带宽
     */
    @Transactional
    public int crawlAndTest() {
        log.info("");
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║     📡 IPTV 爬取+两轮测速 开始执行              ║");
        log.info("╚══════════════════════════════════════════════╝");
        int saved = 0;

        try {
            // 1. 爬取
            List<IptvChannel> channels = crawler.crawl();
            log.info("【入库】爬取到 {} 个频道，开始去重入库...", channels.size());

            // 2. 去重保存，记录新增频道（新频道默认 available=false）
            int alreadyExist = 0;
            List<IptvChannel> newChannels = new ArrayList<>();
            for (IptvChannel ch : channels) {
                if (!channelRepository.existsByUrl(ch.getUrl())) {
                    ch.setAvailable(false);
                    ch.setLatencyMs(null);
                    ch.setBandwidthKbps(null);
                    channelRepository.save(ch);
                    newChannels.add(ch);
                    saved++;
                } else {
                    alreadyExist++;
                }
            }
            log.info("【入库完成】新增 {} 个频道，已存在 {} 个", saved, alreadyExist);

            // 3. 只对本次新增的频道进行两轮测速
            channelRepository.flush();
            if (!newChannels.isEmpty()) {
                int available = speedTester.testTwoPass(newChannels, channelRepository::save);
                log.info("【测速完成】本次新增可用: {} 个 (共检测 {} 个)", available, newChannels.size());
            } else {
                log.info("【测速跳过】无新增频道");
            }

        } catch (Exception e) {
            log.error("【失败】爬取流程异常: {}", e.getMessage(), e);
        }

        log.info("╔══════════════════════════════════════════════╗");
        log.info("║     流程结束: 新增 {} 个频道                     ║", padRight(String.valueOf(saved), 5));
        log.info("╚══════════════════════════════════════════════╝");
        log.info("");
        return saved;
    }

    /**
     * 刷新测速：两轮测速所有频道（包含已标记可用和不可用的）
     * 第一轮快速跳过不可用 → 第二轮详细测速可用频道
     */
    @Transactional
    public int refreshSpeedTest() {
        log.info("===== 刷新测速 =====");

        List<IptvChannel> allChannels = channelRepository.findAll();
        if (allChannels.isEmpty()) {
            log.info("没有频道需要测速");
            return 0;
        }

        log.info("待测速: {} 个频道", allChannels.size());
        int available = speedTester.testTwoPass(allChannels, channelRepository::save);
        log.info("测速刷新完成: {} 个可用 / {} 总数", available, allChannels.size());

        return available;
    }

    /**
     * 清理过期不可用频道
     */
    @Transactional
    public int cleanupStale() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(30);
        List<IptvChannel> stale = channelRepository.findAll().stream()
                .filter(c -> !c.getAvailable())
                .filter(c -> c.getUpdateTime() != null && c.getUpdateTime().isBefore(deadline))
                .toList();

        if (stale.isEmpty()) {
            log.info("没有需要清理的过期频道");
            return 0;
        }

        log.info("清理 {} 个过期不可用频道", stale.size());
        channelRepository.deleteAll(stale);
        return stale.size();
    }

    /**
     * 统计信息
     */
    public ChannelStats getStats() {
        long total = channelRepository.count();
        long available = channelRepository.countByAvailableTrue();
        Set<String> categories = new LinkedHashSet<>();
        channelRepository.findAll().forEach(c -> {
            if (c.getCategory() != null) categories.add(c.getCategory());
        });
        return new ChannelStats(total, available, categories.size(), categories);
    }

    /**
     * 从频道名提取 CCTV 编号，如 "CCTV-10 (1080p)" → "10"，"CCTV5+" → "5+"
     */
    private String extractCctvNumber(String name) {
        if (name == null) return null;
        Matcher m = CCTV_NUM_PATTERN.matcher(name);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 精确匹配：频道名是否匹配目标名
     * 支持 "CCTV1" ↔ "CCTV-1"、前缀+分隔符、编号精确匹配
     */
    private boolean matchPrecise(String name, String target) {
        if (name == null || target == null) return false;
        // 等号匹配（含无横杠变体）
        if (name.equals(target) || name.equals(target.replace("-", ""))) return true;
        // 前缀+分隔符: "CCTV-1 (1080p)" / "CCTV-1（1080p）" / "CCTV-1 综合"
        for (String sep : new String[]{" ", "（", "("}) {
            if (name.startsWith(target + sep)) return true;
        }
        // CCTV 编号精确匹配（避免 CCTV-1 贪婪匹配到 CCTV-10）
        String nt = extractCctvNumber(target);
        if (nt != null) {
            String nn = extractCctvNumber(name);
            if (nn != null && nn.equals(nt)) return true;
        }
        // 非 CCTV 的通用前缀匹配（卫视频道）
        if (!target.startsWith("CCTV") && name.startsWith(target)) return true;
        return false;
    }

    /**
     * 过滤仅央视/卫视频道（cnOnly 导出用）
     * <p>两级匹配：
     *   1. 精确匹配：与 CCTV_TARGETS / SAT_TARGETS 列表进行编号/前缀匹配
     *   2. 模糊回退：检查 category/name 是否包含 "央视"/"卫视"/"CCTV" 等关键词
     * </p>
     * <p>同时规范化分类：所有匹配频道归入 "央视频道" 或 "卫视频道"，
     *    消除原始数据中的英文分类（News/Sports/Business 等）</p>
     */
    public List<IptvChannel> filterCnOnly(List<IptvChannel> channels) {
        return channels.stream()
                .filter(ch -> {
                    String name = ch.getName();
                    String cat = ch.getCategory();

                    // Tier 1: 精确匹配
                    if (name != null) {
                        for (String t : CCTV_TARGETS) {
                            if (matchPrecise(name, t)) return true;
                        }
                        for (String t : SAT_TARGETS) {
                            if (matchPrecise(name, t)) return true;
                        }
                    }

                    // Tier 2: 模糊回退（category/name 关键词匹配）
                    boolean matchCategory = cat != null && (
                            cat.contains("央视") || cat.contains("卫视") ||
                            cat.contains("CCTV") || cat.contains("央视频道") ||
                            cat.contains("卫视频道") || cat.contains("央视IPV4") ||
                            cat.contains("卫视IPV4")
                    );
                    boolean matchName = name != null && (
                            name.contains("CCTV") || name.contains("央视") ||
                            name.contains("卫视") || name.contains("CCTV-")
                    );
                    return matchCategory || matchName;
                })
                .peek(ch -> normalizeCnChannelCategory(ch))
                .collect(Collectors.toList());
    }

    /**
     * 将频道分类统一归为 "央视频道" 或 "卫视频道"
     */
    private void normalizeCnChannelCategory(IptvChannel ch) {
        String name = ch.getName();
        if (name == null) return;

        // 优先精确匹配 CCTV 列表
        for (String t : CCTV_TARGETS) {
            if (matchPrecise(name, t)) {
                ch.setCategory("央视频道");
                return;
            }
        }
        // 优先精确匹配卫视列表
        for (String t : SAT_TARGETS) {
            if (matchPrecise(name, t)) {
                ch.setCategory("卫视频道");
                return;
            }
        }
        // 模糊判断：频道名含 CCTV/央视 → 央视频道
        if (name.contains("CCTV") || name.contains("央视")) {
            ch.setCategory("央视频道");
            return;
        }
        // 频道名含 卫视 → 卫视频道
        if (name.contains("卫视")) {
            ch.setCategory("卫视频道");
            return;
        }
        // 回退：检查原分类
        String cat = ch.getCategory();
        if (cat != null) {
            if (cat.contains("央视") || cat.contains("CCTV")) {
                ch.setCategory("央视频道");
            } else if (cat.contains("卫视")) {
                ch.setCategory("卫视频道");
            }
        }
    }

    /**
     * 按预定义顺序排列央视/卫视频道
     * <p>CCTV-1 → CCTV-17 → 广东卫视 → ... → 云南卫视</p>
     */
    public List<IptvChannel> sortCnByPredefinedOrder(List<IptvChannel> channels) {
        // 构建顺序索引：频道名 → 位置
        Map<String, Integer> orderMap = new LinkedHashMap<>();
        int idx = 0;
        for (String t : CCTV_TARGETS) {
            orderMap.put(t, idx++);
        }
        for (String t : SAT_TARGETS) {
            orderMap.put(t, idx++);
        }

        return channels.stream()
                .sorted(Comparator.comparingInt(ch -> {
                    String name = ch.getName();
                    if (name == null) return Integer.MAX_VALUE;
                    // 找最匹配的目标频道
                    int best = Integer.MAX_VALUE;
                    for (Map.Entry<String, Integer> e : orderMap.entrySet()) {
                        if (matchPrecise(name, e.getKey())) {
                            best = Math.min(best, e.getValue());
                        }
                    }
                    return best;
                }))
                .collect(Collectors.toList());
    }

    public List<IptvChannel> getAll() {
        return channelRepository.findAll();
    }

    public List<IptvChannel> getAvailable() {
        return channelRepository.findByAvailableTrue();
    }

    public List<IptvChannel> getByCategory(String category) {
        return channelRepository.findByCategory(category);
    }

    public List<IptvChannel> searchByName(String name) {
        return channelRepository.findByNameContaining(name);
    }

    public List<IptvChannel> getTopByLatency(int limit) {
        return channelRepository.findByAvailableTrueOrderByLatencyMsAsc()
                .stream().limit(limit).toList();
    }

    public List<IptvChannel> getTopByBandwidth(int limit) {
        return channelRepository.findByAvailableTrueOrderByBandwidthKbpsDesc()
                .stream().limit(limit).toList();
    }

    /**
     * 频道统计 DTO
     */
    public record ChannelStats(long total, long available, int categoryCount, Set<String> categories) {}

    private static String padRight(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }
}
