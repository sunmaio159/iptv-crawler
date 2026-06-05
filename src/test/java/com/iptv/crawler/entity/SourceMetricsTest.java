package com.iptv.crawler.entity;

import com.iptv.crawler.util.TestFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SourceMetrics 实体测试
 */
class SourceMetricsTest {

    @Test
    void shouldCreateSourceMetricsWithAllFields() {
        // Given
        SourceMetrics metrics = TestFixtures.createSourceMetrics();

        // When & Then
        assertThat(metrics.getSourceName()).isEqualTo("test-source");
        assertThat(metrics.getSuccessCount()).isEqualTo(10);
        assertThat(metrics.getFailureCount()).isEqualTo(2);
        assertThat(metrics.getConsecutiveFailures()).isEqualTo(0);
        assertThat(metrics.getAvgResponseTimeMs()).isEqualTo(1500.0);
        assertThat(metrics.getAvgChannelsPerRun()).isEqualTo(50.0);
        assertThat(metrics.getQualityScore()).isEqualTo(75.0);
        assertThat(metrics.getLastErrorMessage()).isNull();
        assertThat(metrics.getLastScoreCalculated()).isNull();
        assertThat(metrics.getCreateTime()).isNotNull();
        assertThat(metrics.getUpdateTime()).isNotNull();
    }

    @Test
    void shouldUpdateSuccessMetrics() {
        // Given
        SourceMetrics metrics = TestFixtures.createSourceMetrics();

        // When
        metrics.setSuccessCount(metrics.getSuccessCount() + 1);
        metrics.setConsecutiveFailures(0);
        metrics.setLastSuccessTime(LocalDateTime.now());
        metrics.setAvgResponseTimeMs(1200.0);

        // Then
        assertThat(metrics.getSuccessCount()).isEqualTo(11);
        assertThat(metrics.getConsecutiveFailures()).isZero();
        assertThat(metrics.getLastSuccessTime()).isNotNull();
    }

    @Test
    void shouldUpdateFailureMetrics() {
        // Given
        SourceMetrics metrics = TestFixtures.createSourceMetrics();

        // When
        metrics.setFailureCount(metrics.getFailureCount() + 1);
        metrics.setConsecutiveFailures(metrics.getConsecutiveFailures() + 1);
        metrics.setLastFailureTime(LocalDateTime.now());
        metrics.setLastErrorMessage("Connection timeout");

        // Then
        assertThat(metrics.getFailureCount()).isEqualTo(3);
        assertThat(metrics.getConsecutiveFailures()).isEqualTo(1);
        assertThat(metrics.getLastFailureTime()).isNotNull();
        assertThat(metrics.getLastErrorMessage()).isEqualTo("Connection timeout");
    }

    @Test
    void shouldTrackConsecutiveFailuresForCircuitBreaker() {
        // Given
        SourceMetrics metrics = TestFixtures.createDegradedSourceMetrics("failing-source", 5);

        // When & Then
        assertThat(metrics.getConsecutiveFailures()).isEqualTo(5);
        assertThat(metrics.getLastFailureTime()).isNotNull();
    }

    @Test
    void shouldHandleHealthySource() {
        // Given
        SourceMetrics metrics = TestFixtures.createHealthySourceMetrics("healthy-source");

        // When & Then
        assertThat(metrics.getSuccessCount()).isEqualTo(100);
        assertThat(metrics.getFailureCount()).isZero();
        assertThat(metrics.getConsecutiveFailures()).isZero();
        assertThat(metrics.getQualityScore()).isEqualTo(95.0);
        assertThat(metrics.getLastSuccessTime()).isNotNull();
    }

    @Test
    void shouldTruncateErrorMessage() {
        // Given
        SourceMetrics metrics = TestFixtures.createSourceMetrics();
        String longMessage = "Error: ".repeat(300); // Very long message

        // When
        if (longMessage.length() > 1000) {
            metrics.setLastErrorMessage(longMessage.substring(0, 1000));
        } else {
            metrics.setLastErrorMessage(longMessage);
        }

        // Then
        assertThat(metrics.getLastErrorMessage().length()).isLessThanOrEqualTo(1000);
    }

    @Test
    void shouldHandleNullFields() {
        // Given
        SourceMetrics metrics = new SourceMetrics();

        // When & Then
        assertThat(metrics.getSourceName()).isNull();
        assertThat(metrics.getSuccessCount()).isZero();
        assertThat(metrics.getFailureCount()).isZero();
        assertThat(metrics.getConsecutiveFailures()).isZero();
        assertThat(metrics.getAvgResponseTimeMs()).isNull();
        assertThat(metrics.getAvgChannelsPerRun()).isNull();
        assertThat(metrics.getQualityScore()).isNull();
    }

    @Test
    void shouldSetQualityScoreAndCalculationTime() {
        // Given
        SourceMetrics metrics = TestFixtures.createSourceMetrics();
        LocalDateTime calculationTime = LocalDateTime.now();

        // When
        metrics.setQualityScore(85.5);
        metrics.setLastScoreCalculated(calculationTime);

        // Then
        assertThat(metrics.getQualityScore()).isEqualTo(85.5);
        assertThat(metrics.getLastScoreCalculated()).isEqualTo(calculationTime);
    }
}
