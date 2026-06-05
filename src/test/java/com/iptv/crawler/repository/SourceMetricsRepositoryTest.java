package com.iptv.crawler.repository;

import com.iptv.crawler.entity.SourceMetrics;
import com.iptv.crawler.util.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SourceMetricsRepository 集成测试
 */
@DataJpaTest
class SourceMetricsRepositoryTest {

    @Autowired
    private SourceMetricsRepository metricsRepository;

    @Test
    void shouldSaveAndFindSourceMetrics() {
        // Given
        SourceMetrics metrics = TestFixtures.createSourceMetrics();

        // When
        SourceMetrics saved = metricsRepository.save(metrics);
        SourceMetrics found = metricsRepository.findBySourceName(saved.getSourceName());

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getSourceName()).isEqualTo("test-source");
        assertThat(found.getSuccessCount()).isEqualTo(10);
    }

    @Test
    void shouldFindBySourceName() {
        // Given
        SourceMetrics metrics = TestFixtures.createSourceMetrics("unique-source");
        metricsRepository.save(metrics);

        // When
        SourceMetrics found = metricsRepository.findBySourceName("unique-source");

        // Then
        assertThat(found).isNotNull();
        assertThat(found.getSourceName()).isEqualTo("unique-source");
    }

    @Test
    void shouldReturnNullWhenSourceNameNotFound() {
        // When
        SourceMetrics found = metricsRepository.findBySourceName("non-existent");

        // Then
        assertThat(found).isNull();
    }

    @Test
    void shouldFindBySuccessCountGreaterThan() {
        // Given
        SourceMetrics healthy = TestFixtures.createHealthySourceMetrics("healthy-source");
        SourceMetrics degraded = TestFixtures.createSourceMetrics("degraded-source");
        degraded.setSuccessCount(5);

        metricsRepository.saveAll(List.of(healthy, degraded));

        // When
        List<SourceMetrics> healthySources = metricsRepository.findBySuccessCountGreaterThanOrderByLastSuccessTimeDesc(10);

        // Then
        assertThat(healthySources).hasSize(1);
        assertThat(healthySources.get(0).getSourceName()).isEqualTo("healthy-source");
    }

    @Test
    void shouldDeleteOlderThan() {
        // Given
        SourceMetrics recent = TestFixtures.createSourceMetrics("recent");
        recent.setUpdateTime(LocalDateTime.now().minusHours(2));

        SourceMetrics old = TestFixtures.createSourceMetrics("old");
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        old.setUpdateTime(cutoff.minusHours(1));

        metricsRepository.saveAll(List.of(recent, old));

        // When
        int deleted = metricsRepository.deleteOlderThan(cutoff);

        // Then
        assertThat(deleted).isEqualTo(1);
        assertThat(metricsRepository.findBySourceName("old")).isNull();
        assertThat(metricsRepository.findBySourceName("recent")).isNotNull();
    }

    @Test
    void shouldHandleDuplicateSourceName() {
        // Given
        SourceMetrics metrics1 = TestFixtures.createSourceMetrics("same-name");
        metrics1.setSuccessCount(10);

        // When - save first
        metricsRepository.save(metrics1);

        // Then - updating should work (JPA merge)
        metrics1.setSuccessCount(20);
        SourceMetrics updated = metricsRepository.save(metrics1);

        assertThat(updated.getId()).isEqualTo(metrics1.getId());
        assertThat(updated.getSuccessCount()).isEqualTo(20);
    }
}
