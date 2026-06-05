package com.iptv.crawler.entity;

import com.iptv.crawler.util.TestFixtures;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IptvChannel 实体测试
 */
class IptvChannelTest {

    @Test
    void shouldCreateIptvChannelWithAllFields() {
        // Given
        IptvChannel channel = TestFixtures.createIptvChannel();

        // When & Then - 验证所有字段
        assertThat(channel.getName()).isEqualTo("Test Channel");
        assertThat(channel.getUrl()).isEqualTo("http://test.example.com/stream.m3u8");
        assertThat(channel.getCategory()).isEqualTo("TestCategory");
        assertThat(channel.getSource()).isEqualTo("test-source");
        assertThat(channel.getProtocol()).isEqualTo("hls");
        assertThat(channel.isAvailable()).isFalse();
        assertThat(channel.getLatencyMs()).isNull();
        assertThat(channel.getBandwidthKbps()).isNull();
        assertThat(channel.getLastCheckTime()).isNull();
        assertThat(channel.getCreateTime()).isNotNull();
        assertThat(channel.getUpdateTime()).isNotNull();
    }

    @Test
    void shouldSetAvailableChannelProperties() {
        // Given
        IptvChannel channel = TestFixtures.createAvailableChannel("Live Channel", "http://test.com/live.m3u8", 150, 2000);

        // When & Then
        assertThat(channel.isAvailable()).isTrue();
        assertThat(channel.getLatencyMs()).isEqualTo(150);
        assertThat(channel.getBandwidthKbps()).isEqualTo(2000);
        assertThat(channel.getLastCheckTime()).isNotNull();
    }

    @Test
    void shouldUpdateChannelProperties() {
        // Given
        IptvChannel channel = TestFixtures.createIptvChannel();

        // When
        LocalDateTime newTime = LocalDateTime.now();
        channel.setAvailable(true);
        channel.setLatencyMs(200L);
        channel.setBandwidthKbps(5000L);
        channel.setLastCheckTime(newTime);

        // Then
        assertThat(channel.isAvailable()).isTrue();
        assertThat(channel.getLatencyMs()).isEqualTo(200);
        assertThat(channel.getBandwidthKbps()).isEqualTo(5000);
        assertThat(channel.getLastCheckTime()).isEqualTo(newTime);
    }

    @Test
    void shouldHandleNullValues() {
        // Given
        IptvChannel channel = new IptvChannel();

        // When & Then - 确保getter方法能安全处理null
        assertThat(channel.getName()).isNull();
        assertThat(channel.getUrl()).isNull();
        assertThat(channel.getCategory()).isNull();
        assertThat(channel.isAvailable()).isFalse(); // boolean默认值
        assertThat(channel.getLatencyMs()).isNull();
        assertThat(channel.getBandwidthKbps()).isNull();
    }

    @Test
    void shouldSetAndGetProtocolTypes() {
        // Test different protocol types
        String[] protocols = {"hls", "http-flv", "rtmp", "rtsp", "http"};

        for (String protocol : protocols) {
            IptvChannel channel = TestFixtures.createIptvChannel();
            channel.setProtocol(protocol);
            assertThat(channel.getProtocol()).isEqualTo(protocol);
        }
    }
}
