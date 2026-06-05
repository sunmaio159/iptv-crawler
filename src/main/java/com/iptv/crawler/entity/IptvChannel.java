package com.iptv.crawler.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * IPTV直播源实体类
 */
@Entity
@Table(name = "iptv_channel", indexes = {
    @Index(name = "idx_url", columnList = "url"),
    @Index(name = "idx_category", columnList = "category"),
    @Index(name = "idx_available", columnList = "available"),
})
public class IptvChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 频道名称 */
    @Column(nullable = false)
    private String name;

    /** 直播地址 URL */
    @Column(nullable = false, length = 2048)
    private String url;

    /** 来源网站 */
    private String source;

    /** 频道分类（央视/卫视/体育/电影等） */
    private String category;

    /** 协议类型（rtmp/hls/http-flv） */
    private String protocol;

    /** 延迟(ms)，ffmpeg测速结果 */
    private Long latencyMs;

    /** 带宽(Kbps)，ffmpeg测速结果 */
    private Long bandwidthKbps;

    /** 是否可用 */
    @Column(nullable = false)
    private Boolean available = false;

    /** 上次可用检测时间 */
    private LocalDateTime lastCheckTime;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }

    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }

    public Long getBandwidthKbps() { return bandwidthKbps; }
    public void setBandwidthKbps(Long bandwidthKbps) { this.bandwidthKbps = bandwidthKbps; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    /** 兼容isAvailable()调用 (boolean语义) */
    public boolean isAvailable() {
        return available != null && available;
    }

    public LocalDateTime getLastCheckTime() { return lastCheckTime; }
    public void setLastCheckTime(LocalDateTime lastCheckTime) { this.lastCheckTime = lastCheckTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
