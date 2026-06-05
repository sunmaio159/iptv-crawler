package com.iptv.crawler.controller;

import com.iptv.crawler.entity.SourceMetrics;
import com.iptv.crawler.service.SourceMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SourceMetricsController API 测试
 */
@WebMvcTest(SourceMetricsController.class)
class SourceMetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SourceMetricsService metricsService;

    @Test
    void shouldGetAllSourceMetrics() throws Exception {
        // Given
        List<SourceMetrics> metrics = Arrays.asList(
                createSourceMetrics("source-1", 10, 2, 80.0),
                createSourceMetrics("source-2", 20, 0, 90.0)
        );
        given(metricsService.getAllMetrics()).willReturn(metrics);

        // When & Then
        mockMvc.perform(get("/api/iptv/metrics/sources"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].sourceName").value("source-1"))
                .andExpect(jsonPath("$[1].qualityScore").value(90.0));
    }

    @Test
    void shouldGetHealthySources() throws Exception {
        // Given
        List<SourceMetrics> healthy = Arrays.asList(
                createSourceMetrics("healthy-1", 50, 0, 85.0),
                createSourceMetrics("healthy-2", 30, 0, 75.0)
        );
        given(metricsService.getHealthySources(10)).willReturn(healthy);

        // When & Then
        mockMvc.perform(get("/api/iptv/metrics/sources/healthy")
                        .param("minSuccess", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldGetHealthySourcesWithDefaultMinSuccess() throws Exception {
        // Given
        given(metricsService.getHealthySources(10)).willReturn(List.of());

        // When & Then - 默认 minSuccess=10
        mockMvc.perform(get("/api/iptv/metrics/sources/healthy"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRecalculateQualityScores() throws Exception {
        // Given
        // 无返回内容，只有日志

        // When & Then
        mockMvc.perform(post("/api/iptv/metrics/recalculate"))
                .andExpect(status().isOk())
                .andExpect(content().string("质量评分计算完成"));
    }

    @Test
    void shouldGetMetricsSummary() throws Exception {
        // Given
        Map<String, Object> summary = Map.of(
                "totalSources", 10L,
                "healthySources", 7L,
                "degradedSources", 2L,
                "avgQualityScore", 65.5
        );
        given(metricsService.getSummary()).willReturn(summary);

        // When & Then
        mockMvc.perform(get("/api/iptv/metrics/summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalSources").value(10))
                .andExpect(jsonPath("$.healthySources").value(7))
                .andExpect(jsonPath("$.avgQualityScore").value(65.5));
    }

    @Test
    void shouldGetSourceStatusByName() throws Exception {
        // Given
        SourceMetrics metrics = createSourceMetrics("test-source", 10, 0, 80.0);
        metrics.setConsecutiveFailures(0);
        metrics.setLastSuccessTime(LocalDateTime.now());
        given(metricsService.getOrCreate("test-source")).willReturn(metrics);
        given(metricsService.isCircuitBreakerOpen("test-source")).willReturn(false);

        // When & Then
        mockMvc.perform(get("/api/iptv/metrics/source/{sourceName}", "test-source"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceName").value("test-source"))
                .andExpect(jsonPath("$.circuitBreakerOpen").value(false));
    }

    @Test
    void shouldResetCircuitBreaker() throws Exception {
        // Given
        // No specific setup needed

        // When & Then
        mockMvc.perform(post("/api/iptv/metrics/source/{sourceName}/reset", "test-source"))
                .andExpect(status().isOk())
                .andExpect(content().string("已重置源的熔断状态: test-source"));
    }

    @Test
    void shouldRefreshMetricsCache() throws Exception {
        // Given
        // No specific setup needed

        // When & Then
        mockMvc.perform(post("/api/iptv/metrics/refresh"))
                .andExpect(status().isOk())
                .andExpect(content().string("源指标缓存已刷新"));
    }

    // ========== Helper Methods ==========

    private SourceMetrics createSourceMetrics(String name, int successCount, int failureCount, double qualityScore) {
        SourceMetrics metrics = new SourceMetrics();
        metrics.setSourceName(name);
        metrics.setSuccessCount(successCount);
        metrics.setFailureCount(failureCount);
        metrics.setConsecutiveFailures(0);
        metrics.setAvgResponseTimeMs(1500.0);
        metrics.setAvgChannelsPerRun(50.0);
        metrics.setQualityScore(qualityScore);
        metrics.setCreateTime(LocalDateTime.now());
        metrics.setUpdateTime(LocalDateTime.now());
        return metrics;
    }
}
