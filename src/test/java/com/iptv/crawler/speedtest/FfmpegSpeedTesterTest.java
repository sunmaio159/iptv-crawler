package com.iptv.crawler.speedtest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FfmpegSpeedTester 基础测试
 */
class FfmpegSpeedTesterTest {

    @Test
    void shouldCreateSpeedTester() {
        // When
        FfmpegSpeedTester tester = new FfmpegSpeedTester("ffmpeg", 4, 8, 1);

        // Then
        assertThat(tester).isNotNull();
    }

    @Test
    void shouldCreateSpeedTestResult() {
        // When
        FfmpegSpeedTester.SpeedTestResult result = new FfmpegSpeedTester.SpeedTestResult();

        // Then
        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getLatencyMs()).isZero();
        assertThat(result.getBandwidthKbps()).isZero();
    }

    @Test
    void shouldUpdateSpeedTestResult() {
        // Given
        FfmpegSpeedTester.SpeedTestResult result = new FfmpegSpeedTester.SpeedTestResult();

        // When
        result.setAvailable(true);
        result.setLatencyMs(1500);
        result.setBandwidthKbps(2000);

        // Then
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getLatencyMs()).isEqualTo(1500);
        assertThat(result.getBandwidthKbps()).isEqualTo(2000);
    }
}
