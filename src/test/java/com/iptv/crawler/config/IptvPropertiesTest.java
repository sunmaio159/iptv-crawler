package com.iptv.crawler.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IptvProperties 配置测试 (简化版)
 */
@SpringBootTest(classes = IptvProperties.class)
class IptvPropertiesTest {

    @Autowired
    private IptvProperties properties;

    @Test
    void shouldLoadCrawlerConfiguration() {
        // When
        IptvProperties.CrawlerConfig crawler = properties.getCrawler();

        // Then
        assertThat(crawler).isNotNull();
        assertThat(crawler.getConcurrency()).isGreaterThan(0);
        assertThat(crawler.getMaxRetries()).isGreaterThanOrEqualTo(0);
        assertThat(crawler.getRetryDelayMs()).isGreaterThan(0);
    }

    @Test
    void shouldLoadCircuitBreakerConfiguration() {
        // When
        IptvProperties.CircuitBreakerConfig circuitBreaker =
                properties.getCrawler().getCircuitBreaker();

        // Then
        assertThat(circuitBreaker).isNotNull();
        assertThat(circuitBreaker.getFailureThreshold()).isGreaterThan(0);
        assertThat(circuitBreaker.getHalfOpenTimeoutMs()).isGreaterThan(0);
    }

    @Test
    void shouldLoadSpeedtestConfiguration() {
        // When
        IptvProperties.SpeedtestConfig speedtest = properties.getSpeedtest();

        // Then
        assertThat(speedtest).isNotNull();
        assertThat(speedtest.getQuickProbeSeconds()).isGreaterThan(0);
        assertThat(speedtest.getMaxProbeSeconds()).isGreaterThan(0);
        assertThat(speedtest.getConcurrency()).isGreaterThan(0);
    }

    @Test
    void shouldLoadMetricsConfiguration() {
        // When
        IptvProperties.MetricsConfig metrics = properties.getMetrics();

        // Then
        assertThat(metrics).isNotNull();
        assertThat(metrics.getRetentionHours()).isGreaterThanOrEqualTo(0);
        assertThat(metrics.getScoreCalculationIntervalMinutes()).isGreaterThan(0);
    }

    @Test
    void shouldLoadFfmpegConfiguration() {
        // When
        IptvProperties.FfmpegConfig ffmpeg = properties.getFfmpeg();

        // Then
        assertThat(ffmpeg).isNotNull();
        assertThat(ffmpeg.getPath()).isNotBlank();
    }

    @Test
    void shouldLoadCleanupConfiguration() {
        // When
        IptvProperties.CleanupConfig cleanup = properties.getCleanup();

        // Then
        assertThat(cleanup).isNotNull();
        assertThat(cleanup.getRetentionDays()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldGetCircuitBreakerFailureThreshold() {
        // When
        int threshold = properties.getCircuitBreakerFailureThreshold();

        // Then
        assertThat(threshold).isGreaterThan(0);
    }

    @Test
    void shouldHaveReasonableDefaults() {
        // Given - load empty properties (defaults)
        IptvProperties props = new IptvProperties();

        // When
        IptvProperties.CrawlerConfig crawler = props.getCrawler();
        IptvProperties.SpeedtestConfig speedtest = props.getSpeedtest();
        IptvProperties.MetricsConfig metrics = props.getMetrics();
        IptvProperties.CleanupConfig cleanup = props.getCleanup();

        // Then - verify default values from source code
        assertThat(crawler.getConcurrency()).isEqualTo(5);
        assertThat(crawler.getMaxRetries()).isEqualTo(3);
        assertThat(speedtest.getConcurrency()).isEqualTo(1);
        assertThat(metrics.getRetentionHours()).isEqualTo(168);
        assertThat(cleanup.getRetentionDays()).isEqualTo(30);
    }
}
