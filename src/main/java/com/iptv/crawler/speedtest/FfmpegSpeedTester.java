package com.iptv.crawler.speedtest;

import com.iptv.crawler.entity.IptvChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用 ffmpeg 测速: 延迟 +  bandwidth
 * <p>支持两轮测试和并发处理</p>
 */
@Component
public class FfmpegSpeedTester {

    private static final Logger log = LoggerFactory.getLogger(FfmpegSpeedTester.class);

    /** ffmpeg 可执行文件路径 */
    private final String ffmpegPath;

    /** 快速检测超时(秒) */
    private final int quickProbeSeconds;

    /** 详细测速超时(秒) */
    private final int maxProbeSeconds;

    /** 测速并发数 */
    private final int concurrency;

    public FfmpegSpeedTester(
            @Value("${iptv.ffmpeg.path:ffmpeg}") String ffmpegPath,
            @Value("${iptv.speedtest.quick-probe-seconds:4}") int quickProbeSeconds,
            @Value("${iptv.speedtest.max-probe-seconds:8}") int maxProbeSeconds,
            @Value("${iptv.speedtest.concurrency:1}") int concurrency) {
        this.ffmpegPath = resolveFfmpegPath(ffmpegPath);
        this.quickProbeSeconds = quickProbeSeconds;
        this.maxProbeSeconds = maxProbeSeconds;
        this.concurrency = concurrency;
        log.info("FfmpegSpeedTester 初始化: ffmpeg={}, quickProbe={}s, maxProbe={}s, concurrency={}",
                this.ffmpegPath, quickProbeSeconds, maxProbeSeconds, concurrency);
    }

    /**
     * 自动检测系统中可用的 ffmpeg
     */
    private String resolveFfmpegPath(String configuredPath) {
        if (!"ffmpeg".equals(configuredPath)) {
            return configuredPath;
        }
        if (testFfmpeg("ffmpeg")) {
            return "ffmpeg";
        }
        String[] winCandidates = {
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
        };
        for (String candidate : winCandidates) {
            if (testFfmpeg(candidate)) {
                log.info("自动检测到 ffmpeg: {}", candidate);
                return candidate;
            }
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            java.io.File wingetDir = new java.io.File(
                    localAppData + "\\Microsoft\\WinGet\\Packages");
            if (wingetDir.isDirectory()) {
                java.io.File[] packages = wingetDir.listFiles(
                        f -> f.getName().toLowerCase().contains("ffmpeg"));
                if (packages != null) {
                    for (java.io.File pkg : packages) {
                        java.io.File bin = new java.io.File(pkg, "bin\\ffmpeg.exe");
                        if (bin.exists() && testFfmpeg(bin.getAbsolutePath())) {
                            log.info("自动检测到 ffmpeg (winget): {}", bin.getAbsolutePath());
                            return bin.getAbsolutePath();
                        }
                    }
                }
            }
        }
        log.warn("未检测到可用的 ffmpeg，测速功能可能不可用。请安装 ffmpeg 或配置 iptv.ffmpeg.path");
        return "ffmpeg";
    }

    private boolean testFfmpeg(String path) {
        try {
            Process p = new ProcessBuilder(path, "-version")
                    .redirectErrorStream(true).start();
            return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 快速检测：短超时，仅判断流是否可达
     */
    public boolean quickCheck(IptvChannel channel) {
        String url = channel.getUrl();
        log.debug("快速检测: {} ({})", channel.getName(), url);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-v", "quiet",
                    "-t", String.valueOf(quickProbeSeconds),
                    "-i", url,
                    "-f", "null",
                    "-"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(quickProbeSeconds + 3, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.debug("快速检测超时: {}", channel.getName());
                return false;
            }

            return process.exitValue() == 0;
        } catch (Exception e) {
            log.debug("快速检测异常 {}: {}", channel.getName(), e.getMessage());
            return false;
        }
    }

    /**
     * 对单个频道进行详细测速（延迟 + 带宽）
     */
    public SpeedTestResult test(IptvChannel channel) {
        SpeedTestResult result = new SpeedTestResult();
        result.setAvailable(false);

        String url = channel.getUrl();
        log.debug("详细测速: {} ({})", channel.getName(), url);

        try {
            Instant start = Instant.now();

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-v", "quiet",
                    "-t", String.valueOf(maxProbeSeconds),
                    "-i", url,
                    "-f", "null",
                    "-"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(maxProbeSeconds + 5, TimeUnit.SECONDS);

            Instant end = Instant.now();
            long elapsedMs = Duration.between(start, end).toMillis();

            if (!finished) {
                process.destroyForcibly();
                log.debug("详细测速超时: {}", channel.getName());
                result.setLatencyMs(elapsedMs);
                return result;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                result.setAvailable(true);
            }

            // 读取 ffmpeg 输出估算带宽
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            // 解析带宽
            String outStr = output.toString();
            long bandwidthKbps = parseBandwidth(outStr);
            result.setBandwidthKbps(bandwidthKbps);
            result.setLatencyMs(elapsedMs);

        } catch (Exception e) {
            log.debug("详细测速异常 {}: {}", channel.getName(), e.getMessage());
        }

        return result;
    }

    /**
     * 解析ffmpeg输出中的bitrate
     */
    private long parseBandwidth(String output) {
        Pattern p = Pattern.compile("bitrate:\\s*(\\d+)\\s*kb/s");
        Matcher m = p.matcher(output);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return 0;
    }

    /**
     * 两轮测速：先快速筛选 → 不可用跳过 → 可用频道详细测速（支持并发）
     *
     * @param channels       待测频道列表
     * @param saveCallback   每测完一个频道的保存回调
     * @return 最终可用频道数
     */
    public int testTwoPass(List<IptvChannel> channels, Consumer<IptvChannel> saveCallback) {
        int total = channels.size();
        if (total == 0) return 0;

        // === 第一轮：快速检测（并发执行） ===
        log.info("");
        log.info("┌──────────────────────────────────────────────┐");
        log.info("│  第一轮-快速检测: {} 个频道 (超时 {}s/个, 并发:{})", padRight(String.valueOf(total), 3), quickProbeSeconds, concurrency);
        log.info("└──────────────────────────────────────────────┘");

        List<IptvChannel> quickPassed = new CopyOnWriteArrayList<>();
        long round1Start = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicInteger quickChecked = new java.util.concurrent.atomic.AtomicInteger(0);

        ExecutorService quickExecutor = Executors.newFixedThreadPool(concurrency);
        try {
            List<CompletableFuture<Void>> quickFutures = new ArrayList<>();
            for (IptvChannel ch : channels) {
                quickFutures.add(CompletableFuture.runAsync(() -> {
                    boolean reachable = quickCheck(ch);
                    ch.setLastCheckTime(java.time.LocalDateTime.now());

                    if (!reachable) {
                        ch.setAvailable(false);
                        ch.setLatencyMs(null);
                        ch.setBandwidthKbps(null);
                        if (saveCallback != null) saveCallback.accept(ch);
                    } else {
                        quickPassed.add(ch);
                    }

                    int done = quickChecked.incrementAndGet();
                    // 每 5 个或最后一个打印进度
                    if (done % 5 == 0 || done == total) {
                        int pct = done * 100 / total;
                        long elapsed = System.currentTimeMillis() - round1Start;
                        long eta = done > 0 ? elapsed * (total - done) / done : 0;
                        log.info("  快速检测 [{}/{}] {}% | 可达:{} 不可用:{} | 已耗时:{}s 预计剩余:{}s",
                                done, total, pct,
                                quickPassed.size(), done - quickPassed.size(),
                                elapsed / 1000, eta / 1000);
                    }
                }, quickExecutor));
            }

            // 等待全部完成
            for (CompletableFuture<Void> f : quickFutures) {
                try {
                    f.get(quickProbeSeconds + 5, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            }
        } finally {
            quickExecutor.shutdown();
            try {
                if (!quickExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    quickExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                quickExecutor.shutdownNow();
            }
        }

        long round1Time = System.currentTimeMillis() - round1Start;
        int skipped = total - quickPassed.size();
        log.info("│  第一轮完成: {} 可达 / {} 不可用 / {} 总计 (耗时 {}s)",
                quickPassed.size(), skipped, total, round1Time / 1000);
        log.info("");

        // === 第二轮：详细测速（支持并发） ===
        if (quickPassed.isEmpty()) {
            log.info("┌──────────────────────────────────────────────┐");
            log.info("│  无可达频道，测速结束                            │");
            log.info("└──────────────────────────────────────────────┘");
            return 0;
        }

        int passCount = quickPassed.size();
        log.info("┌──────────────────────────────────────────────┐");
        log.info("│  第二轮-详细测速: {} 个频道 (超时 {}s/个, 并发:{})", padRight(String.valueOf(passCount), 3), maxProbeSeconds, concurrency);
        log.info("└──────────────────────────────────────────────┘");

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        long round2Start = System.currentTimeMillis();
        try {
            // 提交并发任务
            java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger availableCount = new java.util.concurrent.atomic.AtomicInteger(0);

            List<CompletableFuture<SpeedTestResult>> futures = quickPassed.stream()
                    .map(ch -> CompletableFuture.supplyAsync(() -> test(ch), executor)
                            .thenApply(r -> {
                                int done = completed.incrementAndGet();
                                if (r.isAvailable()) availableCount.incrementAndGet();

                                int pct = done * 100 / passCount;
                                long elapsed = System.currentTimeMillis() - round2Start;
                                long eta = done > 0 ? elapsed * (passCount - done) / done : 0;
                                if (done % 5 == 0 || done == passCount) {
                                    log.info("  详细测速 [{}/{}] {}% | 可用:{} | 已耗时:{}s 预计剩余:{}s",
                                            done, passCount, pct, availableCount.get(),
                                            elapsed / 1000, eta / 1000);
                                }
                                return r;
                            }))
                    .toList();

            // 按顺序收集并保存结果
            int finalAvailable = 0;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    SpeedTestResult r = futures.get(i).get(maxProbeSeconds + 10, TimeUnit.SECONDS);
                    IptvChannel ch = quickPassed.get(i);
                    ch.setAvailable(r.isAvailable());
                    ch.setLatencyMs(r.getLatencyMs());
                    ch.setBandwidthKbps(r.getBandwidthKbps());
                    ch.setLastCheckTime(java.time.LocalDateTime.now());
                    if (r.isAvailable()) finalAvailable++;
                    if (saveCallback != null) saveCallback.accept(ch);
                } catch (Exception e) {
                    IptvChannel ch = quickPassed.get(i);
                    ch.setAvailable(false);
                    if (saveCallback != null) saveCallback.accept(ch);
                }
            }

            long round2Time = System.currentTimeMillis() - round2Start;
            long totalTime = round1Time + round2Time;
            log.info("│  第二轮完成: {} 最终可用 / {} 总计 (耗时 {}s)", finalAvailable, total, round2Time / 1000);
            log.info("┌──────────────────────────────────────────────┐");
            log.info("│  两轮测速完成! 总耗时: {}s │ 可用:{} 跳过:{} 总计:{}",
                    totalTime / 1000, finalAvailable, skipped, total);
            log.info("└──────────────────────────────────────────────┘");
            log.info("");
            return finalAvailable;

        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * 对频道列表批量测速（通用）
     */
    public void testBatch(List<IptvChannel> channels,
                          Consumer<IptvChannel> callback) {
        int total = channels.size();
        int checked = 0;
        for (IptvChannel ch : channels) {
            checked++;
            SpeedTestResult r = test(ch);
            ch.setAvailable(r.isAvailable());
            ch.setLatencyMs(r.getLatencyMs());
            ch.setBandwidthKbps(r.getBandwidthKbps());
            ch.setLastCheckTime(java.time.LocalDateTime.now());
            if (callback != null) {
                callback.accept(ch);
            }
            if (log.isDebugEnabled() || checked % 10 == 0 || checked == total) {
                log.info("测速进度 [{}/{}] {}: {} (延迟:{}ms 带宽:{}Kbps)",
                        checked, total, ch.getName(),
                        r.isAvailable() ? "可用" : "不可用",
                        r.getLatencyMs(), r.getBandwidthKbps());
            }
        }
    }

    // ========== 测速结果类 ==========

    private static String padRight(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    public static class SpeedTestResult {
        private boolean available;
        private long latencyMs;
        private long bandwidthKbps;

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }

        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

        public long getBandwidthKbps() { return bandwidthKbps; }
        public void setBandwidthKbps(long bandwidthKbps) { this.bandwidthKbps = bandwidthKbps; }
    }
}
