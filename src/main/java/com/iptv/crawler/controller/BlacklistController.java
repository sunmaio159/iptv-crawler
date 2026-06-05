package com.iptv.crawler.controller;

import com.iptv.crawler.service.BlacklistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * black_list.txt REST API
 * <p>运行时管理手工屏蔽的 HTTP 直播源地址</p>
 */
@RestController
@RequestMapping("/api/iptv/blacklist")
public class BlacklistController {

    private final BlacklistService blacklistService;

    public BlacklistController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    /** 列出所有被屏蔽的 URL */
    @GetMapping
    public Set<String> list() {
        return blacklistService.getAll();
    }

    /** 添加 URL 到黑名单 */
    @PostMapping
    public ResponseEntity<String> add(@RequestParam String url) {
        url = url.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ResponseEntity.badRequest().body("URL 必须以 http:// 或 https:// 开头");
        }
        boolean added = blacklistService.add(url);
        if (added) {
            return ResponseEntity.ok("已添加: " + url);
        } else {
            return ResponseEntity.ok("已存在: " + url);
        }
    }

    /** 从黑名单移除 URL */
    @DeleteMapping
    public ResponseEntity<String> remove(@RequestParam String url) {
        url = url.trim();
        boolean removed = blacklistService.remove(url);
        if (removed) {
            return ResponseEntity.ok("已移除: " + url);
        } else {
            return ResponseEntity.ok("未找到: " + url);
        }
    }

    /** 重新从文件加载黑名单 */
    @PostMapping("/reload")
    public ResponseEntity<String> reload() {
        blacklistService.reload();
        return ResponseEntity.ok("black_list.txt 已重新加载，共 " + blacklistService.size() + " 条");
    }
}
