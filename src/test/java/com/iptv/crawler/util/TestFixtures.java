package com.iptv.crawler.util;

import com.iptv.crawler.entity.IptvChannel;
import com.iptv.crawler.entity.SourceMetrics;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试数据工厂 - 用于生成一致的测试数据
 */
public final class TestFixtures {

    private TestFixtures() {
        throw new AssertionError("Utility class");
    }

    // ========== IptvChannel 构建器 ==========

    public static IptvChannel createIptvChannel() {
        return createIptvChannel("Test Channel", "http://test.example.com/stream.m3u8");
    }

    public static IptvChannel createIptvChannel(String name, String url) {
        return createIptvChannel(name, url, "TestCategory");
    }

    public static IptvChannel createIptvChannel(String name, String url, String category) {
        IptvChannel channel = new IptvChannel();
        channel.setName(name);
        channel.setUrl(url);
        channel.setCategory(category);
        channel.setSource("test-source");
        channel.setProtocol("hls");
        channel.setAvailable(false);
        channel.setCreateTime(LocalDateTime.now());
        channel.setUpdateTime(LocalDateTime.now());
        return channel;
    }

    public static IptvChannel createAvailableChannel(String name, String url, long latency, long bandwidth) {
        IptvChannel channel = createIptvChannel(name, url);
        channel.setAvailable(true);
        channel.setLatencyMs(latency);
        channel.setBandwidthKbps(bandwidth);
        channel.setLastCheckTime(LocalDateTime.now());
        return channel;
    }

    public static List<IptvChannel> createChannelList(int count) {
        List<IptvChannel> channels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            channels.add(createIptvChannel("Channel " + i, "http://test.example.com/ch" + i + ".m3u8"));
        }
        return channels;
    }

    public static List<IptvChannel> createAvailableChannelList(int count) {
        List<IptvChannel> channels = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            channels.add(createAvailableChannel(
                    "Available Channel " + i,
                    "http://test.example.com/avail" + i + ".m3u8",
                    100 + i * 10,
                    1000 + i * 100
            ));
        }
        return channels;
    }

    // ========== SourceMetrics 构建器 ==========

    public static SourceMetrics createSourceMetrics() {
        return createSourceMetrics("test-source");
    }

    public static SourceMetrics createSourceMetrics(String sourceName) {
        SourceMetrics metrics = new SourceMetrics();
        metrics.setSourceName(sourceName);
        metrics.setSuccessCount(10);
        metrics.setFailureCount(2);
        metrics.setConsecutiveFailures(0);
        metrics.setAvgResponseTimeMs(1500.0);
        metrics.setAvgChannelsPerRun(50.0);
        metrics.setQualityScore(75.0);
        metrics.setCreateTime(LocalDateTime.now());
        metrics.setUpdateTime(LocalDateTime.now());
        return metrics;
    }

    public static SourceMetrics createDegradedSourceMetrics(String sourceName, int consecutiveFailures) {
        SourceMetrics metrics = createSourceMetrics(sourceName);
        metrics.setConsecutiveFailures(consecutiveFailures);
        metrics.setLastFailureTime(LocalDateTime.now());
        return metrics;
    }

    public static SourceMetrics createHealthySourceMetrics(String sourceName) {
        SourceMetrics metrics = createSourceMetrics(sourceName);
        metrics.setSuccessCount(100);
        metrics.setFailureCount(0);
        metrics.setConsecutiveFailures(0);
        metrics.setAvgResponseTimeMs(800.0);
        metrics.setAvgChannelsPerRun(200.0);
        metrics.setQualityScore(95.0);
        metrics.setLastSuccessTime(LocalDateTime.now());
        return metrics;
    }

    public static List<SourceMetrics> createSourceMetricsList(int count) {
        List<SourceMetrics> metrics = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SourceMetrics m = createSourceMetrics("test-source-" + i);
            m.setQualityScore(50.0 + i * 5);
            metrics.add(m);
        }
        return metrics;
    }

    // ========== M3U 内容样例 ==========

    public static String createSampleM3u() {
        return "#EXTM3U\n" +
                "#EXTINF:-1 group-title=\"央视\" tvg-id=\"CCTV-1\",CCTV-1 1080p\n" +
                "http://test.example.com/cctv1.m3u8\n" +
                "#EXTINF:-1 group-title=\"卫视\" tvg-id=\"Hunan\",湖南卫视 720p\n" +
                "http://test.example.com/hunan.m3u8\n" +
                "#EXTINF:-1 group-title=\"体育\" ,CCTV-5\n" +
                "http://test.example.com/cctv5.m3u8\n";
    }

    public static String createLargeSampleM3u(int channelCount) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n");
        for (int i = 0; i < channelCount; i++) {
            sb.append("#EXTINF:-1 group-title=\"TestCategory\" ,Channel ").append(i).append("\n");
            sb.append("http://test.example.com/channel").append(i).append(".m3u8\n");
        }
        return sb.toString();
    }

    public static String createM3uWithInvalidEntries() {
        return "#EXTM3U\n" +
                "#EXTINF:-1 group-title=\"央视\",CCTV-1\n" +
                "http://test.example.com/cctv1.m3u8\n" +
                "#EXTINF:-1,CCTV-2\n" +
                "http://192.168.1.1/stream.m3u8\n" +  // 内网地址
                "#EXTINF:-1,CCTV-3\n" +
                "rtsp://224.0.0.1/stream\n" +  // 组播地址
                "#EXTINF:-1,Invalid Channel\n" +
                "not-a-url\n" +
                "#EXTINF:-1,CCTV-4\n" +
                "http://test.example.com/cctv4.m3u8\n";
    }

    // ========== 实用方法 ==========

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static LocalDateTime hoursAgo(int hours) {
        return LocalDateTime.now().minusHours(hours);
    }

    public static LocalDateTime daysAgo(int days) {
        return LocalDateTime.now().minusDays(days);
    }
}
