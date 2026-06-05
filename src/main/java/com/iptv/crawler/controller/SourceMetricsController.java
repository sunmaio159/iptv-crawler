package com.iptv.crawler.controller;

import com.iptv.crawler.entity.SourceMetrics;
import com.iptv.crawler.service.SourceMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据源健康度监控 API
 */
@RestController
@RequestMapping("/api/iptv/metrics")
public class SourceMetricsController {

    private final SourceMetricsService metricsService;

    public SourceMetricsController(SourceMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * 获取所有源的指标
     */
    @GetMapping("/sources")
    public List<SourceMetrics> getAllSourceMetrics() {
        return metricsService.getAllMetrics();
    }

    /**
     * 获取健康源列表（成功次数大于指定值）
     */
    @GetMapping("/sources/healthy")
    public List<SourceMetrics> getHealthySources(
            @RequestParam(defaultValue = "10") int minSuccess) {
        return metricsService.getHealthySources(minSuccess);
    }

    /**
     * 手动触发质量评分计算
     * 通常由定时任务自动调用，也可手动触发
     */
    @PostMapping("/recalculate")
    public ResponseEntity<String> recalculateScores() {
        metricsService.calculateQualityScores();
        return ResponseEntity.ok("质量评分计算完成");
    }

    /**
     * 获取汇总统计信息
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return metricsService.getSummary();
    }

    /**
     * 检查特定源的熔断状态
     */
    @GetMapping("/source/{sourceName}")
    public ResponseEntity<Map<String, Object>> getSourceStatus(
            @PathVariable String sourceName) {
        SourceMetrics m = metricsService.getOrCreate(sourceName);
        boolean circuitOpen = metricsService.isCircuitBreakerOpen(sourceName);

        return ResponseEntity.ok(Map.of(
            "sourceName", sourceName,
            "consecutiveFailures", m.getConsecutiveFailures(),
            "lastSuccessTime", m.getLastSuccessTime(),
            "lastFailureTime", m.getLastFailureTime(),
            "lastErrorMessage", m.getLastErrorMessage(),
            "circuitBreakerOpen", circuitOpen,
            "qualityScore", m.getQualityScore(),
            "successCount", m.getSuccessCount(),
            "failureCount", m.getFailureCount()
        ));
    }

    /**
     * 重置源的熔断状态（强制重试）
     */
    @PostMapping("/source/{sourceName}/reset")
    public ResponseEntity<String> resetCircuit(@PathVariable String sourceName) {
        metricsService.resetCircuitBreaker(sourceName);
        return ResponseEntity.ok("已重置源的熔断状态: " + sourceName);
    }

    /**
     * 触发缓存刷新
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshCache() {
        metricsService.refreshCache();
        return ResponseEntity.ok("源指标缓存已刷新");
    }
}
