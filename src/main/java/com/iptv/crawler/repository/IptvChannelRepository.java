package com.iptv.crawler.repository;

import com.iptv.crawler.entity.IptvChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IptvChannelRepository extends JpaRepository<IptvChannel, Long> {

    /** 查找不可用频道 */
    List<IptvChannel> findByAvailableFalse();

    /** 按频道名称查找 */
    List<IptvChannel> findByNameContaining(String name);

    /** 按分类查找 */
    List<IptvChannel> findByCategory(String category);

    /** 查找可用频道 */
    List<IptvChannel> findByAvailableTrue();

    /** 按延迟排序 */
    List<IptvChannel> findByAvailableTrueOrderByLatencyMsAsc();

    /** 按带宽排序 */
    List<IptvChannel> findByAvailableTrueOrderByBandwidthKbpsDesc();

    /** 检查URL是否已存在 */
    boolean existsByUrl(String url);

    /** 删除不可用频道 */
    @Modifying
    @Query("DELETE FROM IptvChannel c WHERE c.available = false")
    int deleteUnavailable();

    /** 统计可用频道数 */
    long countByAvailableTrue();
}
