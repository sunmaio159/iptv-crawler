package com.iptv.crawler.service;

import com.iptv.crawler.crawler.IptvSourceCrawler;
import com.iptv.crawler.entity.IptvChannel;
import com.iptv.crawler.repository.IptvChannelRepository;
import com.iptv.crawler.speedtest.FfmpegSpeedTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

/**
 * IptvService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class IptvServiceTest {

    @Mock
    private IptvChannelRepository channelRepository;

    @Mock
    private IptvSourceCrawler crawler;

    @Mock
    private FfmpegSpeedTester speedTester;

    @Mock
    private SourceMetricsService metricsService;

    @Captor
    private ArgumentCaptor<IptvChannel> channelCaptor;

    private IptvService iptvService;

    @BeforeEach
    void setUp() {
        iptvService = new IptvService(channelRepository, crawler, speedTester, metricsService);
    }

    @Test
    void shouldCrawlOnlyAndSaveNewChannels() throws Exception {
        // Given
        IptvChannel ch1 = createChannel("Channel1", "http://test.com/1.m3u8");
        IptvChannel ch2 = createChannel("Channel2", "http://test.com/2.m3u8");
        List<IptvChannel> crawledChannels = Arrays.asList(ch1, ch2);

        given(crawler.crawl()).willReturn(crawledChannels);
        given(channelRepository.existsByUrl(anyString()))
                .willReturn(false); // 所有都是新的

        // When
        int saved = iptvService.crawlOnly();

        // Then
        assertThat(saved).isEqualTo(2);
        then(channelRepository).should().save(channelCaptor.capture());
        List<IptvChannel> savedChannels = channelCaptor.getAllValues();
        assertThat(savedChannels).hasSize(2);
    }

    @Test
    void shouldNotSaveExistingChannels() throws Exception {
        // Given
        IptvChannel existing = createChannel("Existing", "http://test.com/exist.m3u8");
        given(crawler.crawl()).willReturn(Collections.singletonList(existing));
        given(channelRepository.existsByUrl("http://test.com/exist.m3u8")).willReturn(true);

        // When
        int saved = iptvService.crawlOnly();

        // Then
        assertThat(saved).isZero();
        then(channelRepository).should(never()).save(any());
    }

    @Test
    void shouldCrawlAndTestFullProcess() throws Exception {
        // Given
        IptvChannel newCh1 = createChannel("New1", "http://test.com/new1.m3u8");
        IptvChannel newCh2 = createChannel("New2", "http://test.com/new2.m3u8");
        List<IptvChannel> crawled = Arrays.asList(newCh1, newCh2);

        given(crawler.crawl()).willReturn(crawled);
        given(channelRepository.existsByUrl(anyString())).willReturn(false);
        given(channelRepository.findByAvailableFalse()).willReturn(crawled); // 新频道需要测速
        given(speedTester.testTwoPass(crawled, channelRepository::save)).willReturn(2); // 全部可用

        // When
        int saved = iptvService.crawlAndTest();

        // Then
        assertThat(saved).isEqualTo(2); // 新增2个
        then(speedTester).should().testTwoPass(crawled, channelRepository::save);
    }

    @Test
    void shouldHandleCrawlOnlyException() throws Exception {
        // Given
        given(crawler.crawl()).willThrow(new RuntimeException("Network error"));

        // When
        int saved = iptvService.crawlOnly();

        // Then
        assertThat(saved).isZero();
    }

    @Test
    void shouldRefreshSpeedTestOnAllChannels() throws Exception {
        // Given
        List<IptvChannel> allChannels = Arrays.asList(
                createAvailableChannel("Available1", "http://test.com/a1.m3u8", 100, 1000),
                createAvailableChannel("Available2", "http://test.com/a2.m3u8", 200, 2000),
                createChannel("Unavailable", "http://test.com/un.m3u8")
        );

        given(channelRepository.findAll()).willReturn(allChannels);
        given(speedTester.testTwoPass(allChannels, channelRepository::save)).willReturn(2);

        // When
        int available = iptvService.refreshSpeedTest();

        // Then
        assertThat(available).isEqualTo(2);
        then(speedTester).should().testTwoPass(allChannels, channelRepository::save);
    }

    @Test
    void shouldCleanupStaleChannels() {
        // Given
        LocalDateTime oldTime = LocalDateTime.now().minusDays(40);
        LocalDateTime recentTime = LocalDateTime.now().minusDays(10);

        IptvChannel stale = createChannel("Stale", "http://test.com/stale.m3u8");
        stale.setAvailable(false);
        stale.setUpdateTime(oldTime);

        IptvChannel recent = createAvailableChannel("Recent", "http://test.com/recent.m3u8", 100, 1000);
        recent.setUpdateTime(recentTime);

        IptvChannel available = createAvailableChannel("Available", "http://test.com/avail.m3u8", 100, 1000);
        available.setUpdateTime(recentTime);

        given(channelRepository.findAll()).willReturn(Arrays.asList(stale, recent, available));

        // When
        int cleaned = iptvService.cleanupStale();

        // Then
        assertThat(cleaned).isEqualTo(1); // 只清理了1个过期的不可用频道
        then(channelRepository).should().deleteAll(Collections.singletonList(stale));
    }

    @Test
    void shouldGetStats() {
        // Given
        given(channelRepository.count()).willReturn(100L);
        given(channelRepository.countByAvailableTrue()).willReturn(50L);

        // When
        IptvService.ChannelStats stats = iptvService.getStats();

        // Then
        assertThat(stats.total()).isEqualTo(100);
        assertThat(stats.available()).isEqualTo(50);
        assertThat(stats.categoryCount()).isEqualTo(0); // 未分类时
        assertThat(stats.categories()).isEmpty();
    }

    @Test
    void shouldGetAllChannels() {
        // Given
        List<IptvChannel> channels = Arrays.asList(
                createChannel("C1", "http://test.com/1.m3u8"),
                createChannel("C2", "http://test.com/2.m3u8")
        );
        given(channelRepository.findAll()).willReturn(channels);

        // When
        List<IptvChannel> result = iptvService.getAll();

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void shouldGetAvailableChannels() {
        // Given
        List<IptvChannel> available = Arrays.asList(
                createAvailableChannel("A1", "http://test.com/a1.m3u8", 100, 1000),
                createAvailableChannel("A2", "http://test.com/a2.m3u8", 200, 2000)
        );
        given(channelRepository.findByAvailableTrue()).willReturn(available);

        // When
        List<IptvChannel> result = iptvService.getAvailable();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.stream().allMatch(IptvChannel::isAvailable)).isTrue();
    }

    @Test
    void shouldGetByCategory() {
        // Given
        List<IptvChannel> cctv = Arrays.asList(
                createChannel("CCTV-1", "http://test.com/cctv1.m3u8", "央视"),
                createChannel("CCTV-2", "http://test.com/cctv2.m3u8", "央视")
        );
        given(channelRepository.findByCategory("央视")).willReturn(cctv);

        // When
        List<IptvChannel> result = iptvService.getByCategory("央视");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.stream().allMatch(ch -> ch.getCategory().equals("央视"))).isTrue();
    }

    @Test
    void shouldSearchByName() {
        // Given
        List<IptvChannel> results = Arrays.asList(
                createChannel("CCTV-1", "http://test.com/cctv1.m3u8"),
                createChannel("CCTV-2", "http://test.com/cctv2.m3u8")
        );
        given(channelRepository.findByNameContaining("CCTV")).willReturn(results);

        // When
        List<IptvChannel> result = iptvService.searchByName("CCTV");

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void shouldGetTopByLatency() {
        // Given
        List<IptvChannel> sorted = Arrays.asList(
                createAvailableChannel("Fast", "http://test.com/fast.m3u8", 50, 1000),
                createAvailableChannel("Medium", "http://test.com/med.m3u8", 150, 2000),
                createAvailableChannel("Slow", "http://test.com/slow.m3u8", 300, 1500)
        );
        given(channelRepository.findByAvailableTrueOrderByLatencyMsAsc())
                .willReturn(sorted);

        // When
        List<IptvChannel> top5 = iptvService.getTopByLatency(5);

        // Then
        assertThat(top5).hasSize(3);
        assertThat(top5.get(0).getName()).isEqualTo("Fast");
        assertThat(top5.get(1).getName()).isEqualTo("Medium");
        assertThat(top5.get(2).getName()).isEqualTo("Slow");
    }

    @Test
    void shouldGetTopByBandwidth() {
        // Given
        List<IptvChannel> sorted = Arrays.asList(
                createAvailableChannel("High", "http://test.com/high.m3u8", 100, 5000),
                createAvailableChannel("Mid", "http://test.com/mid.m3u8", 200, 3000),
                createAvailableChannel("Low", "http://test.com/low.m3u8", 150, 1000)
        );
        given(channelRepository.findByAvailableTrueOrderByBandwidthKbpsDesc())
                .willReturn(sorted);

        // When
        List<IptvChannel> top5 = iptvService.getTopByBandwidth(5);

        // Then
        assertThat(top5).hasSize(3);
        assertThat(top5.get(0).getName()).isEqualTo("High");
        assertThat(top5.get(1).getName()).isEqualTo("Mid");
        assertThat(top5.get(2).getName()).isEqualTo("Low");
    }

    // ========== Helper Methods ==========

    private IptvChannel createChannel(String name, String url) {
        return createChannel(name, url, "Test");
    }

    private IptvChannel createChannel(String name, String url, String category) {
        IptvChannel channel = new IptvChannel();
        channel.setName(name);
        channel.setUrl(url);
        channel.setCategory(category);
        channel.setSource("test-source");
        channel.setAvailable(false);
        channel.setCreateTime(LocalDateTime.now());
        channel.setUpdateTime(LocalDateTime.now());
        return channel;
    }

    private IptvChannel createAvailableChannel(String name, String url, long latency, long bandwidth) {
        IptvChannel channel = createChannel(name, url);
        channel.setAvailable(true);
        channel.setLatencyMs(latency);
        channel.setBandwidthKbps(bandwidth);
        channel.setLastCheckTime(LocalDateTime.now());
        return channel;
    }
}
