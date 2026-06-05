package com.iptv.crawler.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 源黑名单服务：管理熔断/持续失败的 M3U 直播源
 * <p>数据持久化到 source_blacklist.txt，重启后仍然生效</p>
 */
@Service
public class SourceBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(SourceBlacklistService.class);

    private static final Path FILE_PATH = Paths.get("source_blacklist.txt");

    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        reload();
    }

    /** 从文件加载黑名单 */
    public void reload() {
        blacklist.clear();
        if (!Files.exists(FILE_PATH)) {
            log.info("source_blacklist.txt 不存在，源黑名单为空");
            return;
        }
        try {
            int count = 0;
            for (String line : Files.readAllLines(FILE_PATH, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                blacklist.add(line);
                count++;
            }
            log.info("源黑名单加载完成: {} 个被屏蔽的源", count);
        } catch (IOException e) {
            log.warn("读取 source_blacklist.txt 失败: {}", e.getMessage());
        }
    }

    /** 检查源是否在黑名单中 */
    public boolean contains(String sourceName) {
        return blacklist.contains(sourceName);
    }

    /** 获取所有黑名单源 */
    public Set<String> getAll() {
        return Collections.unmodifiableSet(blacklist);
    }

    /** 添加源到黑名单（熔断时自动调用） */
    public boolean add(String sourceName, String reason) {
        if (blacklist.contains(sourceName)) return false;
        blacklist.add(sourceName);
        try {
            String entry = sourceName + "  # " + reason + " " + java.time.LocalDateTime.now() + System.lineSeparator();
            Files.writeString(FILE_PATH, entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.warn("⚠ 源黑名单添加: {} (原因: {})", sourceName, reason);
            return true;
        } catch (IOException e) {
            log.error("写入 source_blacklist.txt 失败: {}", e.getMessage());
            blacklist.remove(sourceName);
            return false;
        }
    }

    /** 从黑名单移除源 */
    public boolean remove(String sourceName) {
        if (!blacklist.contains(sourceName)) return false;
        blacklist.remove(sourceName);
        try {
            if (Files.exists(FILE_PATH)) {
                var lines = Files.readAllLines(FILE_PATH, StandardCharsets.UTF_8);
                var filtered = lines.stream()
                        .map(String::stripTrailing)
                        .filter(line -> !line.startsWith(sourceName))
                        .toList();
                Files.write(FILE_PATH, filtered, StandardCharsets.UTF_8);
            }
            log.info("源黑名单移除: {}", sourceName);
            return true;
        } catch (IOException e) {
            log.error("写入 source_blacklist.txt 失败: {}", e.getMessage());
            blacklist.add(sourceName);
            return false;
        }
    }

    public int size() {
        return blacklist.size();
    }
}
