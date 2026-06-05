package com.iptv.crawler.service;

import com.iptv.crawler.entity.SourceMetrics;
import com.iptv.crawler.repository.SourceMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据源指标服务
 * 负责收集、计算和管理每个M3U源的性能与质量指标
 */
@Service
public class SourceMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SourceMetricsService.class);

    private final SourceMetricsRepository metricsRepository;
    private final SourceBlacklistService sourceBlacklistService;

    /** 熔断失败阈值（连续失败次数达到此值触发熔断） */
    @Value("${iptv.crawler.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    /** 内存缓存，避免频繁数据库查询 */
    private final Map<String, SourceMetrics> metricsCache = new ConcurrentHashMap<>();

    public SourceMetricsService(SourceMetricsRepository metricsRepository,
                                SourceBlacklistService sourceBlacklistService) {
        this.metricsRepository = metricsRepository;
        this.sourceBlacklistService = sourceBlacklistService;
    }

    /**
     * 获取或创建源指标记录
     */
    public SourceMetrics getOrCreate(String sourceName) {
        return metricsCache.computeIfAbsent(sourceName, name -> {
            SourceMetrics m = metricsRepository.findBySourceName(name);
            if (m == null) {
                m = new SourceMetrics();
                m.setSourceName(name);
                m.setSuccessCount(0);
                m.setFailureCount(0);
                m.setConsecutiveFailures(0);
                metricsRepository.save(m);
            }
            return m;
        });
    }

    /**
     * 记录源爬取成功
     * @param sourceName 源名称
     * @param channelCount 本次爬取的频道数
     * @param responseTimeMs 响应时间（毫秒）
     */
    @Transactional
    public void recordSuccess(String sourceName, int channelCount, long responseTimeMs) {
        SourceMetrics m = getOrCreate(sourceName);
        m.setSuccessCount(m.getSuccessCount() + 1);
        m.setConsecutiveFailures(0);
        m.setLastSuccessTime(LocalDateTime.now());
        m.setLastErrorMessage(null);

        // 滑动平均响应时间（EMA: alpha=0.1）
        Double avg = m.getAvgResponseTimeMs();
        m.setAvgResponseTimeMs(avg == null ? responseTimeMs :
            (avg * 0.9 + responseTimeMs * 0.1));

        // 滑动平均频道数（EMA）
        Double avgCh = m.getAvgChannelsPerRun();
        m.setAvgChannelsPerRun(avgCh == null ? channelCount :
            (avgCh * 0.9 + channelCount * 0.1));

        metricsRepository.save(m);
        metricsCache.put(sourceName, m);

        log.debug("源指标更新[成功]: {} (成功次数: {}, 平均响应: {}ms)",
                sourceName, m.getSuccessCount(), m.getAvgResponseTimeMs());
    }

    /**
     * 记录源爬取失败
     * @param sourceName 源名称
     * @param errorMessage 错误信息（前1000字符）
     */
    @Transactional
    public void recordFailure(String sourceName, String errorMessage) {
        SourceMetrics m = getOrCreate(sourceName);
        m.setFailureCount(m.getFailureCount() + 1);
        m.setConsecutiveFailures(m.getConsecutiveFailures() + 1);
        m.setLastFailureTime(LocalDateTime.now());
        if (errorMessage != null && errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 1000);
        }
        m.setLastErrorMessage(errorMessage);
        metricsRepository.save(m);
        metricsCache.put(sourceName, m);

        // 达到熔断阈值 → 自动加入源黑名单
        if (m.getConsecutiveFailures() >= failureThreshold) {
            sourceBlacklistService.add(sourceName,
                    "连续失败" + m.getConsecutiveFailures() + "次, 触发熔断");
        }

        log.debug("源指标更新[失败]: {} (失败次数: {}, 连续失败: {})",
                sourceName, m.getFailureCount(), m.getConsecutiveFailures());
    }

    /**
     * 获取所有源指标（从内存缓存）
     */
    public List<SourceMetrics> getAllMetrics() {
        return List.copyOf(metricsCache.values());
    }

    /**
     * 从数据库刷新缓存
     */
    @Transactional
    public void refreshCache() {
        metricsCache.clear();
        metricsCache.putAll(
            metricsRepository.findAll().stream()
                .collect(Collectors.toMap(SourceMetrics::getSourceName, m -> m))
        );
        log.info("源指标缓存已刷新，共{}个源", metricsCache.size());
    }

    /**
     * 获取健康源列表（成功次数大于指定值）
     */
    public List<SourceMetrics> getHealthySources(int minSuccess) {
        List<SourceMetrics> fromDb = metricsRepository.findBySuccessCountGreaterThanOrderByLastSuccessTimeDesc(minSuccess);
        fromDb.forEach(m -> metricsCache.put(m.getSourceName(), m));
        return fromDb;
    }

    /**
     * 检查源是否健康（未熔断）
     */
    public boolean isSourceHealthy(String sourceName) {
        SourceMetrics m = metricsCache.get(sourceName);
        if (m == null) return true; // 无记录的源视为健康
        return m.getConsecutiveFailures() < failureThreshold;
    }

    /**
     * 获取源的熔断器状态
     */
    public boolean isCircuitBreakerOpen(String sourceName) {
        SourceMetrics m = metricsCache.get(sourceName);
        if (m == null) return false;
        return m.getConsecutiveFailures() >= failureThreshold;
    }

    /**
     * 计算源质量评分 (0-100)
     * 基于: 成功率(40%) + 平均响应时间(20%) + 频道数(20%) + 最新度(20%)
     */
    @Transactional
    public void calculateQualityScores() {
        List<SourceMetrics> all = metricsRepository.findAll();
        int updated = 0;

        for (SourceMetrics m : all) {
            double score = calculateScore(m);
            m.setQualityScore(score);
            m.setLastScoreCalculated(LocalDateTime.now());
            updated++;
        }

        if (updated > 0) {
            metricsRepository.saveAll(all);
            // 更新缓存
            metricsCache.clear();
            metricsCache.putAll(all.stream()
                .collect(Collectors.toMap(SourceMetrics::getSourceName, m -> m)));
            log.info("源质量评分计算完成: 共{}个源", updated);
        }
    }

    /**
     * 计算单个源的得分
     */
    private double calculateScore(SourceMetrics m) {
        int total = m.getSuccessCount() + m.getFailureCount();
        if (total == 0) return 0.0;

        // 1. 成功率 (0-40分，权重40%)
        double successRate = (double) m.getSuccessCount() / total;
        double successScore = successRate * 40;

        // 2. 响应时间 (0-20分，权重20%，越快越好)
        Double avgTime = m.getAvgResponseTimeMs();
        double responseScore = 0;
        if (avgTime != null) {
            if (avgTime <= 1000) {
                responseScore = 20; // 1秒内满分
            } else if (avgTime <= 5000) {
                responseScore = 20 - (avgTime - 1000) / 4000.0 * 20; // 线性下降
            } else {
                responseScore = 0;
            }
        }

        // 3. 频道数 (0-20分，权重20%，越多越好)
        Double avgChannels = m.getAvgChannelsPerRun();
        double channelScore = 0;
        if (avgChannels != null) {
            // 每50个频道满分，超过不封顶
            channelScore = Math.min(20, avgChannels / 50.0 * 20);
        }

        // 4. 最新度 (0-20分，权重20%)
        LocalDateTime last = m.getLastSuccessTime();
        double freshnessScore = 0;
        if (last != null) {
            long hours = java.time.Duration.between(last, LocalDateTime.now()).toHours();
            if (hours <= 24) {
                freshnessScore = 20; // 24小时内满分
            } else if (hours <= 24 * 7) {
                freshnessScore = 20 - (hours - 24) / (24.0 * 6) * 20; // 7天内线性衰减到0
            } else {
                freshnessScore = 0;
            }
        }

        double totalScore = successScore + responseScore + channelScore + freshnessScore;
        return Math.round(totalScore * 10.0) / 10.0; // 保留1位小数
    }

    /**
     * 清理过期的指标记录
     * @param cutoff 截止时间（早于此时间的记录将被删除）
     * @return 删除的记录数
     */
    @Transactional
    public int cleanupOldMetrics(LocalDateTime cutoff) {
        int deleted = metricsRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("清理过期源指标记录: {} 条", deleted);
            metricsRepository.flush();
        }
        return deleted;
    }

    /**
     * 重置源的熔断状态（手动干预）
     */
    @Transactional
    public void resetCircuitBreaker(String sourceName) {
        SourceMetrics m = getOrCreate(sourceName);
        m.setConsecutiveFailures(0);
        metricsRepository.save(m);
        metricsCache.put(sourceName, m);
        log.info("已重置源的熔断状态: {}", sourceName);
    }

    /**
     * 检查所有熔断器，如果已超过半开超时时间则自动恢复
     *
     * @param halfOpenTimeoutMs 半开超时毫秒数（来自配置 iptv.crawler.circuit-breaker.half-open-timeout-ms）
     * @param failureThreshold 连续失败阈值（来自配置 iptv.crawler.circuit-breaker.failure-threshold）
     * @return 恢复的源数量
     */
    @Transactional
    public int autoRecoverCircuitBreakers(long halfOpenTimeoutMs, int failureThreshold) {
        int recovered = 0;
        List<SourceMetrics> all = metricsRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (SourceMetrics m : all) {
            if (m.getConsecutiveFailures() < failureThreshold) continue;
            if (m.getLastFailureTime() == null) continue;

            long elapsedMs = Duration.between(m.getLastFailureTime(), now).toMillis();
            if (elapsedMs >= halfOpenTimeoutMs) {
                log.info("[熔断恢复] 源: {} 连续失败{}次, 已过{}ms, 超过半开超时{}ms, 自动重置",
                        m.getSourceName(), m.getConsecutiveFailures(), elapsedMs, halfOpenTimeoutMs);
                m.setConsecutiveFailures(0);
                metricsRepository.save(m);
                metricsCache.put(m.getSourceName(), m);
                recovered++;
            }
        }

        if (recovered > 0) {
            log.info("熔断器自动恢复: {} 个源已恢复", recovered);
        }
        return recovered;
    }

    /**
     * 获取汇总统计
     */
    public Map<String, Object> getSummary() {
        List<SourceMetrics> all = getAllMetrics();
        long total = all.size();
        long healthy = all.stream()
            .filter(m -> m.getQualityScore() != null && m.getQualityScore() >= 60)
            .count();
        long degraded = all.stream()
            .filter(m -> m.getConsecutiveFailures() > 0)
            .count();
        double avgQuality = all.stream()
            .filter(m -> m.getQualityScore() != null)
            .mapToDouble(SourceMetrics::getQualityScore)
            .average()
            .orElse(0.0);

        return Map.of(
            "totalSources", total,
            "healthySources", healthy,
            "degradedSources", degraded,
            "avgQualityScore", Math.round(avgQuality * 10) / 10.0
        );
    }
}
