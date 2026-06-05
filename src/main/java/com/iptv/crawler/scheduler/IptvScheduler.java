package com.iptv.crawler.scheduler;

import com.iptv.crawler.config.IptvProperties;
import com.iptv.crawler.service.IptvService;
import com.iptv.crawler.service.SourceMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：自动爬取 + 测速 + 清理 + 质量评分 + 熔断器恢复
 */
@Component
public class IptvScheduler {

    private static final Logger log = LoggerFactory.getLogger(IptvScheduler.class);

    private final IptvService iptvService;
    private final SourceMetricsService metricsService;
    private final IptvProperties properties;

    public IptvScheduler(IptvService iptvService,
                         SourceMetricsService metricsService,
                         IptvProperties properties) {
        this.iptvService = iptvService;
        this.metricsService = metricsService;
        this.properties = properties;
    }

    /** 每天凌晨3点执行完整爬取+测速 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyCrawl() {
        log.info("===== 定时任务: 每日IPTV爬取+测速 =====");
        try {
            int saved = iptvService.crawlAndTest();
            log.info("每日爬取完成，新增 {} 个频道", saved);

            // 计算源质量评分
            metricsService.calculateQualityScores();
            log.info("源质量评分计算完成");
        } catch (Exception e) {
            log.error("每日爬取异常: {}", e.getMessage(), e);
        }
    }

    /** 每6小时刷新测速 */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void refreshSpeed() {
        log.info("===== 定时任务: 刷新测速 =====");
        try {
            int available = iptvService.refreshSpeedTest();
            log.info("测速刷新完成，{} 个可用", available);
        } catch (Exception e) {
            log.error("测速刷新异常: {}", e.getMessage(), e);
        }
    }

    /** 每周日凌晨4点清理过期不可用频道 */
    @Scheduled(cron = "0 0 4 ? * SUN")
    public void cleanupStale() {
        log.info("===== 定时任务: 清理过期频道 =====");
        try {
            int cleaned = iptvService.cleanupStale();
            log.info("清理完成，删除 {} 个过期频道", cleaned);
        } catch (Exception e) {
            log.error("清理过期频道异常: {}", e.getMessage(), e);
        }
    }

    /** 每小时检查熔断器状态，对超过半开超时的源自动恢复 */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkCircuitBreakers() {
        long timeoutMs = properties.getCrawler().getCircuitBreaker().getHalfOpenTimeoutMs();
        int threshold = properties.getCrawler().getCircuitBreaker().getFailureThreshold();
        int recovered = metricsService.autoRecoverCircuitBreakers(timeoutMs, threshold);
        if (recovered > 0) {
            log.info("熔断器自动恢复检查: {} 个源已恢复", recovered);
        } else {
            log.debug("熔断器检查完成，无需要恢复的源");
        }
    }
}
