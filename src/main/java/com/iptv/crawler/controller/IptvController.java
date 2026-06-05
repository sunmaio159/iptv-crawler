package com.iptv.crawler.controller;

import com.iptv.crawler.entity.IptvChannel;
import com.iptv.crawler.service.IptvService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * IPTV直播源 REST API
 */
@RestController
@RequestMapping("/api/iptv")
public class IptvController {

    private static final Logger log = LoggerFactory.getLogger(IptvController.class);

    private final IptvService iptvService;

    public IptvController(IptvService iptvService) {
        this.iptvService = iptvService;
    }

    // ========== 查询接口 ==========

    /** 获取统计信息 */
    @GetMapping("/stats")
    public ResponseEntity<IptvService.ChannelStats> getStats() {
        return ResponseEntity.ok(iptvService.getStats());
    }

    /** 获取所有频道 */
    @GetMapping("/channels")
    public List<IptvChannel> getAll() {
        return iptvService.getAll();
    }

    /** 获取可用频道 */
    @GetMapping("/channels/available")
    public List<IptvChannel> getAvailable() {
        return iptvService.getAvailable();
    }

    /** 按分类获取 */
    @GetMapping("/channels/category/{category}")
    public List<IptvChannel> getByCategory(@PathVariable String category) {
        return iptvService.getByCategory(category);
    }

    /** 搜索频道 */
    @GetMapping("/channels/search")
    public List<IptvChannel> search(@RequestParam String name) {
        return iptvService.searchByName(name);
    }

    /** 获取延迟最低的 N 个频道 */
    @GetMapping("/channels/top/latency")
    public List<IptvChannel> topByLatency(@RequestParam(defaultValue = "50") int limit) {
        return iptvService.getTopByLatency(limit);
    }

    /** 获取带宽最高的 N 个频道 */
    @GetMapping("/channels/top/bandwidth")
    public List<IptvChannel> topByBandwidth(@RequestParam(defaultValue = "50") int limit) {
        return iptvService.getTopByBandwidth(limit);
    }

    // ========== 操作接口 ==========

    /** 手动触发爬取（不含测速） */
    @PostMapping("/crawl")
    public ResponseEntity<String> triggerCrawl() {
        int saved = iptvService.crawlOnly();
        return ResponseEntity.ok("爬取完成，新增 " + saved + " 个频道");
    }

    /** 手动触发完整爬取+测速 */
    @PostMapping("/crawl-and-test")
    public ResponseEntity<String> triggerCrawlAndTest() {
        int saved = iptvService.crawlAndTest();
        return ResponseEntity.ok("爬取+测速完成，新增 " + saved + " 个频道");
    }

    /** 手动触发测速刷新 */
    @PostMapping("/speedtest")
    public ResponseEntity<String> triggerSpeedTest() {
        int available = iptvService.refreshSpeedTest();
        return ResponseEntity.ok("测速刷新完成，" + available + " 个可用");
    }

    /** 手动触发清理过期频道 */
    @PostMapping("/cleanup")
    public ResponseEntity<String> triggerCleanup() {
        int cleaned = iptvService.cleanupStale();
        return ResponseEntity.ok("清理完成，删除 " + cleaned + " 个过期频道");
    }

    // ========== 导出接口 ==========

    /**
     * 发布为 M3U 格式直播源
     * 这是最通用的IPTV播放器格式
     * 支持: cnOnly=true(仅央视卫视)
     */
    @GetMapping(value = "/playlist.m3u", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportM3u(@RequestParam(defaultValue = "false") boolean cnOnly) {
        List<IptvChannel> channels = iptvService.getAvailable();

        // 过滤仅央视卫视
        if (cnOnly) {
            channels = iptvService.filterCnOnly(channels);
            channels = iptvService.sortCnByPredefinedOrder(channels);
        } else {
            // 按延迟升序（低延迟优先），延迟缺失的排末尾
            channels = channels.stream()
                    .sorted(Comparator.comparingLong((IptvChannel c) ->
                            c.getLatencyMs() != null ? c.getLatencyMs() : Long.MAX_VALUE))
                    .collect(Collectors.toList());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        for (IptvChannel ch : channels) {
            sb.append("#EXTINF:-1 group-title=\"")
              .append(ch.getCategory() != null ? ch.getCategory() : "未分类")
              .append("\" tvg-name=\"")
              .append(ch.getName())
              .append("\",")
              .append(ch.getName())
              .append("\n");
            sb.append(ch.getUrl()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 发布为 TXT 格式 (中文播放器常用，按分类分组)
     * 支持: all=true(所有频道), maxPerCategory=N(每类限制数量), cnOnly=true(仅央视卫视)
     * 自动清理URL查询参数，确保兼容性
     */
    @GetMapping(value = "/playlist.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportTxt(
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(defaultValue = "50") int maxPerCategory,
            @RequestParam(defaultValue = "false") boolean cnOnly) {
        List<IptvChannel> channels = all ?
                iptvService.getAll() : iptvService.getAvailable();

        // 如果需要只导出央视和卫视，先过滤并按预定义顺序排序
        if (cnOnly) {
            channels = iptvService.filterCnOnly(channels);
            channels = iptvService.sortCnByPredefinedOrder(channels);
        }

        // 按频道名去重，保留最优的（基于可用性>延迟>带宽）
        // 使用 LinkedHashMap 保持排序后的顺序
        Map<String, IptvChannel> bestByChannel = new LinkedHashMap<>();
        for (IptvChannel ch : channels) {
            IptvChannel existing = bestByChannel.get(ch.getName());
            if (existing == null || isBetter(ch, existing)) {
                bestByChannel.put(ch.getName(), ch);
            }
        }
        List<IptvChannel> deduped = new ArrayList<>(bestByChannel.values());

        // 按分类分组
        Map<String, List<IptvChannel>> grouped = deduped.stream()
                .collect(Collectors.groupingBy(
                        ch -> ch.getCategory() != null ? ch.getCategory() : "其他",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 如果仅中文，重新排序分类：央视第一，卫视第二，其他按拼音
        List<String> sortedCategories = new ArrayList<>(grouped.keySet());
        if (cnOnly) {
            sortedCategories.sort((c1, c2) -> {
                boolean c1Cctv = c1 != null && c1.contains("央视");
                boolean c1Sat = c1 != null && c1.contains("卫视");
                boolean c2Cctv = c2 != null && c2.contains("央视");
                boolean c2Sat = c2 != null && c2.contains("卫视");
                if (c1Cctv && !c2Cctv) return -1;
                if (!c1Cctv && c2Cctv) return 1;
                if (c1Sat && !c2Sat) return -1;
                if (!c1Sat && c2Sat) return 1;
                return c1.compareTo(c2);
            });
        } else {
            // 默认排序：央视频道 -> 卫视频道 -> 其他分类按拼音
            sortedCategories.sort(Comparator.comparingInt(IptvController::categoryOrder)
                    .thenComparing(String::compareTo));
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String category : sortedCategories) {
            List<IptvChannel> list = grouped.get(category);
            if (list.isEmpty()) continue;

            // 限制每个分类最大数量，避免文件过大
            // cnOnly 时保留预定义顺序，否则按延迟排序
            List<IptvChannel> limited;
            if (cnOnly) {
                limited = new ArrayList<>(list); // 保持已排序的顺序
            } else {
                limited = list.stream()
                        .sorted(Comparator.comparingLong((IptvChannel c) ->
                                c.getLatencyMs() != null ? c.getLatencyMs() : Long.MAX_VALUE))
                        .collect(Collectors.toList());
            }
            if (maxPerCategory > 0 && limited.size() > maxPerCategory) {
                limited = limited.subList(0, maxPerCategory);
            }

            if (!first) {
                sb.append("\n");
            }
            first = false;

            String display = decorateCategory(category);
            sb.append(display).append(",#genre#\n");
            for (IptvChannel ch : limited) {
                String cleanUrl = cleanUrl(ch.getUrl()); // 清理查询参数
                sb.append(ch.getName()).append(",").append(cleanUrl).append("\n");
            }
        }

        // 添加统计信息
//        sb.append("\n# ============================\n");
//        sb.append("# 共 ").append(deduped.size()).append(" 个频道 (去重后)\n");
//        sb.append("# ============================\n");

        // 添加更新时间区块
        sb.append("\n🕿️更新时间,#genre#\n");
        sb.append(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        sb.append(",https://github.com/iptv-org/iptv\n");

        return sb.toString();
    }

    // ========== 辅助方法 ==========

    /**
     * 判断频道A是否比频道B更优
     * 优先级：可用 > 延迟低 > 带宽高
     */
    private boolean isBetter(IptvChannel a, IptvChannel b) {
        if (a.getAvailable() && !b.getAvailable()) return true;
        if (!a.getAvailable() && b.getAvailable()) return false;
        // 都可用或都不可用，比较延迟
        if (a.getLatencyMs() != null && b.getLatencyMs() != null) {
            if (a.getLatencyMs() < b.getLatencyMs()) return true;
            if (a.getLatencyMs() > b.getLatencyMs()) return false;
        }
        // 延迟相近或缺失，比较带宽
        if (a.getBandwidthKbps() != null && b.getBandwidthKbps() != null) {
            return a.getBandwidthKbps() > b.getBandwidthKbps();
        }
        return false;
    }

    /**
     * 计算频道质量分（用于同分类内排序）
     */
    private static int channelScore(IptvChannel ch) {
        int score = 0;
        if (ch.getAvailable() != null && ch.getAvailable()) score += 1000;
        if (ch.getLatencyMs() != null) score += Math.max(0, 500 - (int)(ch.getLatencyMs() / 10));
        if (ch.getBandwidthKbps() != null) score += Math.min(500, (int)(ch.getBandwidthKbps() / 100));
        return score;
    }

    /**
     * 清理URL：移除查询参数，只保留协议、主机、路径
     * 某些播放器不支持?后面的参数
     */
    private String cleanUrl(String url) {
        if (url == null) return "";
        int protoEnd = url.indexOf("://");
        if (protoEnd < 0) return url;
        String protocol = url.substring(0, protoEnd + 3);
        String rest = url.substring(protoEnd + 3);

        // 分离主机/路径和查询参数
        int queryStart = rest.indexOf('?');
        if (queryStart >= 0) {
            rest = rest.substring(0, queryStart);
        }
        int fragmentStart = rest.indexOf('#');
        if (fragmentStart >= 0) {
            rest = rest.substring(0, fragmentStart);
        }

        return protocol + rest;
    }

    /**
     * 分类排序权重：值越小越靠前
     */
    private static int categoryOrder(String category) {
        if (category == null) return 100;
        if (category.contains("央视")) return 1;
        if (category.contains("卫视")) return 2;
        if (category.contains("4K") || category.contains("8K")) return 3;
        if (category.contains("体育")) return 4;
        if (category.contains("电影")) return 5;
        if (category.contains("新闻")) return 6;
        if (category.contains("少儿") || category.contains("儿童")) return 7;
        if (category.contains("综艺") || category.contains("娱乐")) return 8;
        if (category.contains("纪录片")) return 9;
        if (category.contains("音乐")) return 10;
        return 50; // 其他
    }

    /**
     * 分类名装饰：添加 emoji 前缀
     */
    private static String decorateCategory(String category) {
        if (category == null) return "📂其他频道";
        if (category.contains("央视")) return "📺" + category;
        if (category.contains("卫视")) return "📡" + category;
        if (category.contains("4K") || category.contains("8K")) return "🎬" + category;
        if (category.contains("体育")) return "⚽" + category;
        if (category.contains("电影")) return "🎥" + category;
        if (category.contains("新闻")) return "📰" + category;
        if (category.contains("少儿") || category.contains("儿童")) return "🧒" + category;
        if (category.contains("综艺") || category.contains("娱乐")) return "🎭" + category;
        if (category.contains("纪录片")) return "🎞️" + category;
        if (category.contains("音乐")) return "🎵" + category;
        return "📂" + category;
    }

    /**
     * 发布为 JSON 格式
     * 支持: cnOnly=true(仅央视卫视)
     */
    @GetMapping("/playlist.json")
    public List<IptvChannel> exportJson(@RequestParam(defaultValue = "false") boolean cnOnly) {
        List<IptvChannel> channels = iptvService.getAvailable();

        // 如果需要只导出央视和卫视，先过滤并排序
        if (cnOnly) {
            channels = iptvService.filterCnOnly(channels);
            channels = iptvService.sortCnByPredefinedOrder(channels);
        }
        return channels;
    }
}
