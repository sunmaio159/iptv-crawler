package com.iptv.crawler.repository;

import com.iptv.crawler.entity.SourceMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SourceMetricsRepository extends JpaRepository<SourceMetrics, Long> {

    SourceMetrics findBySourceName(String sourceName);

    /** 获取成功次数大于指定值的源，按最后成功时间降序 */
    List<SourceMetrics> findBySuccessCountGreaterThanOrderByLastSuccessTimeDesc(int minSuccess);

    /** 获取质量评分低于阈值的源（用于告警） */
    @Query("SELECT m FROM SourceMetrics m WHERE m.qualityScore < :minScore")
    List<SourceMetrics> findByQualityScoreLessThan(Double minScore);

    /** 清理过期指标（删除早于指定时间的记录） */
    @Modifying
    @Query("DELETE FROM SourceMetrics m WHERE m.updateTime < :cutoff")
    int deleteOlderThan(java.time.LocalDateTime cutoff);
}
