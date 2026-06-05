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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * black_list.txt 管理服务
 * <p>支持运行时增删查 reload，修改自动持久化到文件</p>
 */
@Service
public class BlacklistService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistService.class);

    /** black_list.txt 文件路径（相对于当前工作目录） */
    private static final Path BLACK_LIST_PATH = Paths.get("black_list.txt");

    /** 内存中的黑名单集合（线程安全） */
    private final Set<String> blackList = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 从文件重新加载黑名单
     */
    public void reload() {
        blackList.clear();
        if (!Files.exists(BLACK_LIST_PATH)) {
            log.info("black_list.txt 不存在，跳过黑名单加载");
            return;
        }
        try {
            List<String> lines = Files.readAllLines(BLACK_LIST_PATH, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("http://") || line.startsWith("https://")) {
                    blackList.add(line);
                }
            }
            log.info("black_list.txt 加载完成: {} 条屏蔽地址", blackList.size());
        } catch (IOException e) {
            log.warn("读取 black_list.txt 失败: {}", e.getMessage());
        }
    }

    /**
     * 获取所有黑名单 URL 的快照
     */
    public Set<String> getAll() {
        return Set.copyOf(blackList);
    }

    /**
     * 检查 URL 是否在黑名单中
     */
    public boolean contains(String url) {
        return blackList.contains(url);
    }

    /**
     * 添加 URL 到黑名单（追加到文件末尾）
     * @return true=新增, false=已存在
     */
    public boolean add(String url) {
        if (blackList.contains(url)) return false;
        blackList.add(url);
        try {
            // 追加到文件末尾
            String line = url.endsWith("\n") ? url : url + System.lineSeparator();
            Files.writeString(BLACK_LIST_PATH, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("black_list.txt 添加: {}", url);
            return true;
        } catch (IOException e) {
            log.error("写入 black_list.txt 失败: {}", e.getMessage());
            blackList.remove(url); // 回滚
            return false;
        }
    }

    /**
     * 从黑名单移除 URL（重写文件，保留注释和空行）
     * @return true=移除成功, false=不在列表中
     */
    public boolean remove(String url) {
        if (!blackList.contains(url)) return false;
        blackList.remove(url);
        try {
            // 重新读取原文件，过滤掉要移除的 URL
            if (Files.exists(BLACK_LIST_PATH)) {
                List<String> originalLines = Files.readAllLines(BLACK_LIST_PATH, StandardCharsets.UTF_8);
                List<String> newLines = originalLines.stream()
                        .map(String::stripTrailing)
                        .filter(line -> !line.equals(url))
                        .collect(Collectors.toList());
                Files.write(BLACK_LIST_PATH, newLines, StandardCharsets.UTF_8);
            }
            log.info("black_list.txt 移除: {}", url);
            return true;
        } catch (IOException e) {
            log.error("写入 black_list.txt 失败: {}", e.getMessage());
            blackList.add(url); // 回滚
            return false;
        }
    }

    /**
     * 获取黑名单数量
     */
    public int size() {
        return blackList.size();
    }
}
