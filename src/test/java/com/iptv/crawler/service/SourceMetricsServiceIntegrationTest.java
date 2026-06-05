package com.iptv.crawler.service;

import com.iptv.crawler.entity.SourceMetrics;
import com.iptv.crawler.repository.SourceMetricsRepository;
import com.iptv.crawler.util.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SourceMetricsService 基础集成测试
 */
@SpringBootTest
class SourceMetricsServiceIntegrationTest {

    @Autowired
    private SourceMetricsService metricsService;

    @Autowired
    private SourceMetricsRepository metricsRepository;

    @Test
    void shouldCreateAndRetrieveMetrics() {
        // When
        String sourceName = "integration-test-source";
        SourceMetrics metrics = metricsService.getOrCreate(sourceName);

        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.getSourceName()).isEqualTo(sourceName);
        assertThat(metrics.getSuccessCount()).isZero();
    }

    @Test
    void shouldRecordSuccess() {
        // Given
        String source = "test-success-source";
        metricsService.getOrCreate(source);

        // When
        metricsService.recordSuccess(source, 50, 1000);

        // Then
        SourceMetrics metrics = metricsRepository.findBySourceName(source);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getSuccessCount()).isEqualTo(1);
        assertThat(metrics.getConsecutiveFailures()).isZero();
    }

    @Test
    void shouldRecordFailure() {
        // Given
        String source = "test-failure-source";
        metricsService.getOrCreate(source);

        // When
        metricsService.recordFailure(source, "Test error");

        // Then
        SourceMetrics metrics = metricsRepository.findBySourceName(source);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getFailureCount()).isEqualTo(1);
        assertThat(metrics.getConsecutiveFailures()).isEqualTo(1);
        assertThat(metrics.getLastErrorMessage()).isEqualTo("Test error");
    }
}
