package com.iptv.crawler.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * IPTV 配置属性，映射 application.yml 中 iptv.* 配置
 */
@Component
@ConfigurationProperties(prefix = "iptv")
public class IptvProperties {

    private static final Logger log = LoggerFactory.getLogger(IptvProperties.class);

    private static final Path SOURCES_FILE = Paths.get("sources.txt");

    /** M3U 直播源列表 */
    private List<SourceConfig> sources = new ArrayList<>();

    /** 爬虫配置 */
    private CrawlerConfig crawler = new CrawlerConfig();

    /** 测速配置 */
    private SpeedtestConfig speedtest = new SpeedtestConfig();

    /** 指标配置 */
    private MetricsConfig metrics = new MetricsConfig();

    /** ffmpeg 配置 */
    private FfmpegConfig ffmpeg = new FfmpegConfig();

    /** 清理配置 */
    private CleanupConfig cleanup = new CleanupConfig();

    /** 频道屏蔽配置 */
    private BlocklistConfig blocklist = new BlocklistConfig();

    // ===== getters & setters =====

    public List<SourceConfig> getSources() {
        return sources;
    }

    public void setSources(List<SourceConfig> sources) {
        this.sources = sources;
    }

    public CrawlerConfig getCrawler() {
        return crawler;
    }

    public void setCrawler(CrawlerConfig crawler) {
        this.crawler = crawler;
    }

    public SpeedtestConfig getSpeedtest() {
        return speedtest;
    }

    public void setSpeedtest(SpeedtestConfig speedtest) {
        this.speedtest = speedtest;
    }

    public MetricsConfig getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsConfig metrics) {
        this.metrics = metrics;
    }

    public FfmpegConfig getFfmpeg() {
        return ffmpeg;
    }

    public void setFfmpeg(FfmpegConfig ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public BlocklistConfig getBlocklist() {
        return blocklist;
    }

    public void setBlocklist(BlocklistConfig blocklist) {
        this.blocklist = blocklist;
    }

    public CleanupConfig getCleanup() {
        return cleanup;
    }

    public void setCleanup(CleanupConfig cleanup) {
        this.cleanup = cleanup;
    }

    /**
     * 获取熔断失败阈值
     */
    public int getCircuitBreakerFailureThreshold() {
        return crawler.getCircuitBreaker() != null ?
                crawler.getCircuitBreaker().getFailureThreshold() : 5;
    }

    /**
     * 从 sources.txt 加载额外的直播源
     * <p>格式: name|url 或 name|url|large</p>
     * <p>启动时自动执行，会与 application.yml 中的源合并（去重基于 name）</p>
     */
    @PostConstruct
    public void loadSourcesFromFile() {
        if (!Files.exists(SOURCES_FILE)) {
            log.info("sources.txt 不存在，仅使用 application.yml 中的直播源配置");
            return;
        }
        try {
            int added = 0;
            int skipped = 0;
            for (String line : Files.readAllLines(SOURCES_FILE, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|");
                if (parts.length < 2) {
                    log.warn("sources.txt 格式错误，跳过行: {}", line);
                    continue;
                }

                String name = parts[0].trim();
                String url = parts[1].trim();
                if (name.isEmpty() || url.isEmpty()) continue;

                // 与 YAML 源去重（基于 name）
                boolean exists = sources.stream().anyMatch(s -> s.getName().equals(name));
                if (exists) {
                    skipped++;
                    continue;
                }

                SourceConfig config = new SourceConfig();
                config.setName(name);
                config.setUrl(url);
                if (parts.length >= 3) {
                    config.setLarge(Boolean.parseBoolean(parts[2].trim()));
                }
                sources.add(config);
                added++;
            }
            log.info("从 sources.txt 加载 {} 个直播源 (跳过已存在 {} 个)", added, skipped);
        } catch (IOException e) {
            log.warn("读取 sources.txt 失败: {}", e.getMessage());
        }
    }

    /**
     * 单个 M3U 直播源配置
     */
    public static class SourceConfig {
        /** 来源名称，用于标识数据库中 source 字段 */
        private String name = "unknown";
        /** M3U 文件 URL */
        private String url;
        /** 是否大文件（使用更长读取超时） */
        private boolean large = false;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public boolean isLarge() { return large; }
        public void setLarge(boolean large) { this.large = large; }
    }

    /**
     * 爬虫配置
     */
    public static class CrawlerConfig {
        /** 并发线程数 */
        private int concurrency = 5;
        /** 最大重试次数 */
        private int maxRetries = 3;
        /** 重试延迟（毫秒） */
        private long retryDelayMs = 2000;
        /** 最低分辨率要求，如 "1080p", "720p", "480p", "4k" */
        private String minResolution = "1080p";
        /**
         * 当频道名无分辨率标注时的处理模式
         * - conservative (默认): 不明确标注1080p+则丢弃
         * - permissive: 无分辨率标注视为通过（仅排除明确低分辨率）
         */
        private String resolutionFilterMode = "conservative";
        /** 熔断器配置 */
        private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }

        public String getMinResolution() { return minResolution; }
        public void setMinResolution(String minResolution) { this.minResolution = minResolution; }

        public String getResolutionFilterMode() { return resolutionFilterMode; }
        public void setResolutionFilterMode(String resolutionFilterMode) { this.resolutionFilterMode = resolutionFilterMode; }

        public CircuitBreakerConfig getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreakerConfig circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }
    }

    /**
     * 熔断器配置
     */
    public static class CircuitBreakerConfig {
        /** 连续失败阈值 */
        private int failureThreshold = 5;
        /** 半开超时（毫秒） */
        private long halfOpenTimeoutMs = 300000;

        public int getFailureThreshold() { return failureThreshold; }
        public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }

        public long getHalfOpenTimeoutMs() { return halfOpenTimeoutMs; }
        public void setHalfOpenTimeoutMs(long halfOpenTimeoutMs) { this.halfOpenTimeoutMs = halfOpenTimeoutMs; }
    }

    /**
     * 测速配置
     */
    public static class SpeedtestConfig {
        /** 快速检测超时（秒） */
        private int quickProbeSeconds = 4;
        /** 详细测速超时（秒） */
        private int maxProbeSeconds = 8;
        /** 测速并发数 */
        private int concurrency = 1;

        public int getQuickProbeSeconds() { return quickProbeSeconds; }
        public void setQuickProbeSeconds(int quickProbeSeconds) { this.quickProbeSeconds = quickProbeSeconds; }

        public int getMaxProbeSeconds() { return maxProbeSeconds; }
        public void setMaxProbeSeconds(int maxProbeSeconds) { this.maxProbeSeconds = maxProbeSeconds; }

        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    }

    /**
     * 指标配置
     */
    public static class MetricsConfig {
        /** 指标保留时间（小时） */
        private int retentionHours = 168;
        /** 质量评分计算间隔（分钟） */
        private int scoreCalculationIntervalMinutes = 1440;

        public int getRetentionHours() { return retentionHours; }
        public void setRetentionHours(int retentionHours) { this.retentionHours = retentionHours; }

        public int getScoreCalculationIntervalMinutes() { return scoreCalculationIntervalMinutes; }
        public void setScoreCalculationIntervalMinutes(int scoreCalculationIntervalMinutes) {
            this.scoreCalculationIntervalMinutes = scoreCalculationIntervalMinutes;
        }
    }

    /**
     * ffmpeg 配置
     */
    public static class FfmpegConfig {
        /** ffmpeg 可执行文件路径 */
        private String path = "ffmpeg";

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    /**
     * 清理配置
     */
    public static class CleanupConfig {
        /** 保留天数 */
        private int retentionDays = 30;

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }

    /**
     * 频道屏蔽配置 — 爬取时自动过滤掉不需要的频道
     * <p>支持四种过滤方式，匹配任意一项即跳过：</p>
     * <ul>
     *   <li>nameKeywords — 频道名包含关键词（如"港澳台"、"Religious"）</li>
     *   <li>urlKeywords — URL包含关键词（如"udp://"、"cctvplus"）</li>
     *   <li>categoryKeywords — 分类包含关键词（如"港澳台"、"Kids"）</li>
     *   <li>excludeNames — 频道名精确匹配（用于排除特定频道）</li>
     * </ul>
     */
    public static class BlocklistConfig {
        /** 频道名包含任意关键词即跳过 */
        private List<String> nameKeywords = new ArrayList<>();
        /** URL包含任意关键词即跳过 */
        private List<String> urlKeywords = new ArrayList<>();
        /** 分类包含任意关键词即跳过 */
        private List<String> categoryKeywords = new ArrayList<>();
        /** 频道名精确匹配（完全相等）即跳过 */
        private List<String> excludeNames = new ArrayList<>();

        public List<String> getNameKeywords() { return nameKeywords; }
        public void setNameKeywords(List<String> nameKeywords) { this.nameKeywords = nameKeywords; }

        public List<String> getUrlKeywords() { return urlKeywords; }
        public void setUrlKeywords(List<String> urlKeywords) { this.urlKeywords = urlKeywords; }

        public List<String> getCategoryKeywords() { return categoryKeywords; }
        public void setCategoryKeywords(List<String> categoryKeywords) { this.categoryKeywords = categoryKeywords; }

        public List<String> getExcludeNames() { return excludeNames; }
        public void setExcludeNames(List<String> excludeNames) { this.excludeNames = excludeNames; }
    }
}
