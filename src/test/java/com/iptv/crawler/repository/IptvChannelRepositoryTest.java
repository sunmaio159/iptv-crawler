package com.iptv.crawler.repository;

import com.iptv.crawler.entity.IptvChannel;
import com.iptv.crawler.util.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * IptvChannelRepository 集成测试
 */
@DataJpaTest
class IptvChannelRepositoryTest {

    @Autowired
    private IptvChannelRepository channelRepository;

    @Test
    void shouldSaveAndFindChannel() {
        // Given
        IptvChannel channel = TestFixtures.createIptvChannel();

        // When
        IptvChannel saved = channelRepository.save(channel);
        IptvChannel found = channelRepository.findById(saved.getId()).orElse(null);

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo("Test Channel");
    }

    @Test
    void shouldFindByAvailableTrue() {
        // Given
        IptvChannel available1 = TestFixtures.createAvailableChannel("Avail1", "http://test.com/1.m3u8", 100, 1000);
        IptvChannel available2 = TestFixtures.createAvailableChannel("Avail2", "http://test.com/2.m3u8", 200, 2000);
        IptvChannel unavailable = TestFixtures.createIptvChannel("Unavail", "http://test.com/3.m3u8");

        channelRepository.saveAll(List.of(available1, available2, unavailable));

        // When
        List<IptvChannel> availableChannels = channelRepository.findByAvailableTrue();

        // Then
        assertThat(availableChannels).hasSize(2);
        assertThat(availableChannels.stream().allMatch(IptvChannel::isAvailable)).isTrue();
    }

    @Test
    void shouldFindByAvailableFalse() {
        // Given
        IptvChannel available = TestFixtures.createAvailableChannel("Avail", "http://test.com/avail.m3u8", 100, 1000);
        IptvChannel unavailable1 = TestFixtures.createIptvChannel("Unavail1", "http://test.com/un1.m3u8");
        IptvChannel unavailable2 = TestFixtures.createIptvChannel("Unavail2", "http://test.com/un2.m3u8");

        channelRepository.saveAll(List.of(available, unavailable1, unavailable2));

        // When
        List<IptvChannel> unavailableChannels = channelRepository.findByAvailableFalse();

        // Then
        assertThat(unavailableChannels).hasSize(2);
        assertThat(unavailableChannels.stream().allMatch(ch -> !ch.isAvailable())).isTrue();
    }

    @Test
    void shouldFindByCategory() {
        // Given
        IptvChannel cctv = TestFixtures.createIptvChannel("CCTV-1", "http://test.com/cctv1.m3u8", "央视");
        IptvChannel sports = TestFixtures.createIptvChannel("CCTV-5", "http://test.com/cctv5.m3u8", "体育");

        channelRepository.saveAll(List.of(cctv, sports));

        // When
        List<IptvChannel> cctvChannels = channelRepository.findByCategory("央视");
        List<IptvChannel> sportsChannels = channelRepository.findByCategory("体育");

        // Then
        assertThat(cctvChannels).hasSize(1);
        assertThat(cctvChannels.get(0).getName()).isEqualTo("CCTV-1");
        assertThat(sportsChannels).hasSize(1);
    }

    @Test
    void shouldFindByNameContaining() {
        // Given
        IptvChannel cctv1 = TestFixtures.createIptvChannel("CCTV-1", "http://test.com/cctv1.m3u8", "央视");
        IptvChannel cctv2 = TestFixtures.createIptvChannel("CCTV-2", "http://test.com/cctv2.m3u8", "央视");
        IptvChannel hunan = TestFixtures.createIptvChannel("湖南卫视", "http://test.com/hunan.m3u8", "卫视");

        channelRepository.saveAll(List.of(cctv1, cctv2, hunan));

        // When
        List<IptvChannel> cctvResults = channelRepository.findByNameContaining("CCTV");
        List<IptvChannel> hunanResults = channelRepository.findByNameContaining("湖南");

        // Then
        assertThat(cctvResults).hasSize(2);
        assertThat(hunanResults).hasSize(1);
    }

    @Test
    void shouldFindByAvailableTrueOrderByLatencyMsAsc() {
        // Given
        IptvChannel fast = TestFixtures.createAvailableChannel("Fast", "http://test.com/fast.m3u8", 50, 1000);
        IptvChannel medium = TestFixtures.createAvailableChannel("Medium", "http://test.com/medium.m3u8", 150, 2000);
        IptvChannel slow = TestFixtures.createAvailableChannel("Slow", "http://test.com/slow.m3u8", 300, 1500);

        channelRepository.saveAll(List.of(fast, medium, slow));

        // When
        List<IptvChannel> sortedByLatency = channelRepository.findByAvailableTrueOrderByLatencyMsAsc();

        // Then
        assertThat(sortedByLatency).hasSize(3);
        assertThat(sortedByLatency.get(0).getName()).isEqualTo("Fast");
        assertThat(sortedByLatency.get(1).getName()).isEqualTo("Medium");
        assertThat(sortedByLatency.get(2).getName()).isEqualTo("Slow");
    }

    @Test
    void shouldFindByAvailableTrueOrderByBandwidthKbpsDesc() {
        // Given
        IptvChannel highBW = TestFixtures.createAvailableChannel("HighBW", "http://test.com/high.m3u8", 100, 5000);
        IptvChannel midBW = TestFixtures.createAvailableChannel("MidBW", "http://test.com/mid.m3u8", 200, 3000);
        IptvChannel lowBW = TestFixtures.createAvailableChannel("LowBW", "http://test.com/low.m3u8", 150, 1000);

        channelRepository.saveAll(List.of(highBW, midBW, lowBW));

        // When
        List<IptvChannel> sortedByBandwidth = channelRepository.findByAvailableTrueOrderByBandwidthKbpsDesc();

        // Then
        assertThat(sortedByBandwidth).hasSize(3);
        assertThat(sortedByBandwidth.get(0).getName()).isEqualTo("HighBW");
        assertThat(sortedByBandwidth.get(1).getName()).isEqualTo("MidBW");
        assertThat(sortedByBandwidth.get(2).getName()).isEqualTo("LowBW");
    }

    @Test
    void shouldExistsByUrl() {
        // Given
        IptvChannel channel = TestFixtures.createIptvChannel("Test", "http://test.example.com/unique.m3u8");
        channelRepository.save(channel);

        // When & Then
        assertThat(channelRepository.existsByUrl("http://test.example.com/unique.m3u8")).isTrue();
        assertThat(channelRepository.existsByUrl("http://test.example.com/different.m3u8")).isFalse();
    }

    @Test
    void shouldCountByAvailableTrue() {
        // Given
        IptvChannel available1 = TestFixtures.createAvailableChannel("A1", "http://test.com/1.m3u8", 100, 1000);
        IptvChannel available2 = TestFixtures.createAvailableChannel("A2", "http://test.com/2.m3u8", 200, 2000);
        IptvChannel unavailable = TestFixtures.createIptvChannel("U1", "http://test.com/3.m3u8");

        channelRepository.saveAll(List.of(available1, available2, unavailable));

        // When
        long count = channelRepository.countByAvailableTrue();

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldDeleteUnavailable() {
        // Given
        IptvChannel available = TestFixtures.createAvailableChannel("Avail", "http://test.com/avail.m3u8", 100, 1000);
        IptvChannel unavailable1 = TestFixtures.createIptvChannel("Unavail1", "http://test.com/un1.m3u8");
        IptvChannel unavailable2 = TestFixtures.createIptvChannel("Unavail2", "http://test.com/un2.m3u8");

        channelRepository.saveAll(List.of(available, unavailable1, unavailable2));
        long initialCount = channelRepository.count();

        // When
        int deleted = channelRepository.deleteUnavailable();

        // Then
        assertThat(deleted).isEqualTo(2);
        assertThat(channelRepository.count()).isEqualTo(initialCount - 2);
        assertThat(channelRepository.findByAvailableFalse()).isEmpty();
    }
}
