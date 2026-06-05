package com.iptv.crawler.controller;

import com.iptv.crawler.entity.IptvChannel;
import com.iptv.crawler.service.IptvService;
import com.iptv.crawler.service.SourceMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IptvController API 测试
 */
@WebMvcTest(IptvController.class)
class IptvControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IptvService iptvService;

    @MockBean
    private SourceMetricsService metricsService;

    // ========== 统计接口测试 ==========

    @Test
    void shouldGetStats() throws Exception {
        // Given
        Set<String> categories = new HashSet<>(Arrays.asList("央视", "卫视", "体育"));
        IptvService.ChannelStats stats = new IptvService.ChannelStats(100, 50, 3, categories);
        given(iptvService.getStats()).willReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/iptv/stats"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(100))
                .andExpect(jsonPath("$.available").value(50))
                .andExpect(jsonPath("$.categoryCount").value(3));
    }

    // ========== 查询接口测试 ==========

    @Test
    void shouldGetAllChannels() throws Exception {
        // Given
        List<IptvChannel> channels = createChannelList(2);
        given(iptvService.getAll()).willReturn(channels);

        // When & Then
        mockMvc.perform(get("/api/iptv/channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Channel 0"))
                .andExpect(jsonPath("$[1].name").value("Channel 1"));
    }

    @Test
    void shouldGetAvailableChannels() throws Exception {
        // Given
        List<IptvChannel> available = createAvailableChannelList(2);
        given(iptvService.getAvailable()).willReturn(available);

        // When & Then
        mockMvc.perform(get("/api/iptv/channels/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].latencyMs").value(100));
    }

    @Test
    void shouldGetByCategory() throws Exception {
        // Given
        List<IptvChannel> cctv = Arrays.asList(
                createChannel("CCTV-1", "http://test.com/1.m3u8", "央视"),
                createChannel("CCTV-2", "http://test.com/2.m3u8", "央视")
        );
        given(iptvService.getByCategory("央视")).willReturn(cctv);

        // When & Then
        mockMvc.perform(get("/api/iptv/channels/category/央视"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldSearchChannelsByName() throws Exception {
        // Given
        List<IptvChannel> results = Arrays.asList(
                createChannel("CCTV-1", "http://test.com/cctv1.m3u8", "央视"),
                createChannel("CCTV-2", "http://test.com/cctv2.m3u8", "央视")
        );
        given(iptvService.searchByName("CCTV")).willReturn(results);

        // When & Then
        mockMvc.perform(get("/api/iptv/channels/search")
                        .param("name", "CCTV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldRequireNameParamForSearch() throws Exception {
        mockMvc.perform(get("/api/iptv/channels/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetTopByLatency() throws Exception {
        // Given
        List<IptvChannel> top = Arrays.asList(
                createAvailableChannel("Fast", "http://test.com/fast.m3u8", 50),
                createAvailableChannel("Medium", "http://test.com/med.m3u8", 100)
        );
        given(iptvService.getTopByLatency(20)).willReturn(top);

        // When & Then
        mockMvc.perform(get("/api/iptv/channels/top/latency")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latencyMs").value(50))
                .andExpect(jsonPath("$[1].latencyMs").value(100));
    }

    @Test
    void shouldGetDefaultLimitForTopLatency() throws Exception {
        // Given
        given(iptvService.getTopByLatency(50)).willReturn(List.of());

        // When & Then - 使用默认limit=50
        mockMvc.perform(get("/api/iptv/channels/top/latency"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldGetTopByBandwidth() throws Exception {
        // Given
        List<IptvChannel> top = Arrays.asList(
                createAvailableChannel("High", "http://test.com/high.m3u8", 100, 5000),
                createAvailableChannel("Mid", "http://test.com/mid.m3u8", 200, 3000)
        );
        given(iptvService.getTopByBandwidth(20)).willReturn(top);

        // When & Then
        mockMvc.perform(get("/api/iptv/channels/top/bandwidth")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bandwidthKbps").value(5000))
                .andExpect(jsonPath("$[1].bandwidthKbps").value(3000));
    }

    // ========== 操作接口测试 ==========

    @Test
    void shouldTriggerCrawl() throws Exception {
        // Given
        given(iptvService.crawlOnly()).willReturn(5);

        // When & Then
        mockMvc.perform(post("/api/iptv/crawl"))
                .andExpect(status().isOk())
                .andExpect(content().string("爬取完成，新增 5 个频道"));
    }

    @Test
    void shouldTriggerCrawlAndTest() throws Exception {
        // Given
        given(iptvService.crawlAndTest()).willReturn(10);

        // When & Then
        mockMvc.perform(post("/api/iptv/crawl-and-test"))
                .andExpect(status().isOk())
                .andExpect(content().string("爬取+测速完成，新增 10 个频道"));
    }

    @Test
    void shouldTriggerSpeedTest() throws Exception {
        // Given
        given(iptvService.refreshSpeedTest()).willReturn(50);

        // When & Then
        mockMvc.perform(post("/api/iptv/speedtest"))
                .andExpect(status().isOk())
                .andExpect(content().string("测速刷新完成，50 个可用"));
    }

    @Test
    void shouldTriggerCleanup() throws Exception {
        // Given
        given(iptvService.cleanupStale()).willReturn(20);

        // When & Then
        mockMvc.perform(post("/api/iptv/cleanup"))
                .andExpect(status().isOk())
                .andExpect(content().string("清理完成，删除 20 个过期频道"));
    }

    // ========== 导出接口测试 ==========

    @Test
    void shouldExportM3u() throws Exception {
        // Given
        List<IptvChannel> available = Arrays.asList(
                createChannel("CCTV-1", "http://test.com/cctv1.m3u8", "央视")
        );
        given(iptvService.getAvailable()).willReturn(available);

        // When & Then
        mockMvc.perform(get("/api/iptv/playlist.m3u"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("#EXTM3U")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CCTV-1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("http://test.com/cctv1.m3u8")));
    }

    @Test
    void shouldExportTxtWithAllChannels() throws Exception {
        // Given
        List<IptvChannel> channels = Arrays.asList(
                createChannel("CCTV-1", "http://test.com/cctv1.m3u8", "央视"),
                createChannel("湖南卫视", "http://test.com/hunan.m3u8", "卫视")
        );
        given(iptvService.getAll()).willReturn(channels);

        // When & Then
        mockMvc.perform(get("/api/iptv/playlist.txt")
                        .param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("央视频道,#genre#")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("卫视频道,#genre#")));
    }

    @Test
    void shouldExportTxtDefaultOnlyAvailable() throws Exception {
        // Given
        List<IptvChannel> available = Arrays.asList(
                createChannel("CCTV-1", "http://test.com/cctv1.m3u8", "央视")
        );
        given(iptvService.getAvailable()).willReturn(available);

        // When & Then
        mockMvc.perform(get("/api/iptv/playlist.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("更新时间")));
    }

    @Test
    void shouldExportJson() throws Exception {
        // Given
        List<IptvChannel> available = Arrays.asList(
                createChannel("CCTV-1", "http://test.com/cctv1.m3u8", "央视")
        );
        given(iptvService.getAvailable()).willReturn(available);

        // When & Then
        mockMvc.perform(get("/api/iptv/playlist.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("CCTV-1"));
    }

    // ========== Helper Methods ==========

    private IptvChannel createChannel(String name, String url, String category) {
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

    private IptvChannel createAvailableChannel(String name, String url, long latencyMs) {
        return createAvailableChannel(name, url, latencyMs, 1000);
    }

    private IptvChannel createAvailableChannel(String name, String url, long latencyMs, long bandwidthKbps) {
        IptvChannel channel = createChannel(name, url, "Test");
        channel.setAvailable(true);
        channel.setLatencyMs(latencyMs);
        channel.setBandwidthKbps(bandwidthKbps);
        channel.setLastCheckTime(LocalDateTime.now());
        return channel;
    }

    private List<IptvChannel> createChannelList(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> createChannel("Channel " + i, "http://test.com/ch" + i + ".m3u8", "Test"))
                .toList();
    }

    private List<IptvChannel> createAvailableChannelList(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> createAvailableChannel("Available " + i, "http://test.com/avail" + i + ".m3u8", 100 + i * 10))
                .toList();
    }
}
