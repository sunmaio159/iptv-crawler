package com.iptv.crawler.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 数据源健康度指标
 * 跟踪每个M3U源的性能、成功率、质量
 */
@Entity
@Table(name = "source_metrics", indexes = {
    @Index(name = "idx_source_name", columnList = "sourceName"),
    @Index(name = "idx_last_success", columnList = "lastSuccessTime"),
})
public class SourceMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 源名称（与 IptvChannel.source 一致） */
    @Column(nullable = false, unique = true)
    private String sourceName;

    /** 总成功次数 */
    private int successCount = 0;

    /** 总失败次数 */
    private int failureCount = 0;

    /** 连续失败次数（用于熔断） */
    private int consecutiveFailures = 0;

    /** 最后成功时间 */
    private LocalDateTime lastSuccessTime;

    /** 最后失败时间 */
    private LocalDateTime lastFailureTime;

    /** 最后错误信息 */
    @Column(length = 1000)
    private String lastErrorMessage;

    /** 平均响应时间（毫秒） */
    private Double avgResponseTimeMs;

    /** 平均每批次爬取频道数 */
    private Double avgChannelsPerRun;

    /** 源质量评分（0-100，定期计算） */
    private Double qualityScore;

    /** 最后一次评分时间 */
    private LocalDateTime lastScoreCalculated;

    /** 创建时间 */
    @Column(updatable = false)
    private LocalDateTime createTime = LocalDateTime.now();

    /** 更新时间 */
    private LocalDateTime updateTime = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getFailureCount() { return failureCount; }
    public void setFailureCount(int failureCount) { this.failureCount = failureCount; }

    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }

    public LocalDateTime getLastSuccessTime() { return lastSuccessTime; }
    public void setLastSuccessTime(LocalDateTime lastSuccessTime) { this.lastSuccessTime = lastSuccessTime; }

    public LocalDateTime getLastFailureTime() { return lastFailureTime; }
    public void setLastFailureTime(LocalDateTime lastFailureTime) { this.lastFailureTime = lastFailureTime; }

    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }

    public Double getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public void setAvgResponseTimeMs(Double avgResponseTimeMs) { this.avgResponseTimeMs = avgResponseTimeMs; }

    public Double getAvgChannelsPerRun() { return avgChannelsPerRun; }
    public void setAvgChannelsPerRun(Double avgChannelsPerRun) { this.avgChannelsPerRun = avgChannelsPerRun; }

    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }

    public LocalDateTime getLastScoreCalculated() { return lastScoreCalculated; }
    public void setLastScoreCalculated(LocalDateTime lastScoreCalculated) { this.lastScoreCalculated = lastScoreCalculated; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
