package com.iptv.crawler;

import com.iptv.crawler.service.IptvService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IptvCrawlerApplication {

    private static final Logger log = LoggerFactory.getLogger(IptvCrawlerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(IptvCrawlerApplication.class, args);
    }

    /**
     * 应用启动完成后自动执行一次爬取+两轮测速
     */
    @Bean
    public ApplicationRunner startupRunner(IptvService iptvService) {
        return args -> {
            log.info("");
            log.info("╔══════════════════════════════════════════════╗");
            log.info("║     IPTV Crawler 启动完成，开始初始化爬取...    ║");
            log.info("╚══════════════════════════════════════════════╝");
            log.info("");
            try {
                int saved = iptvService.crawlAndTest();
                log.info("");
                log.info("╔══════════════════════════════════════════════╗");
                log.info("║  启动初始化完成! 新增 {} 个频道                   ║", padRight(String.valueOf(saved), 5));
                log.info("╚══════════════════════════════════════════════╝");
                log.info("");
            } catch (Exception e) {
                log.error("启动初始化爬取失败: {}", e.getMessage(), e);
            }
        };
    }

    private static String padRight(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }
}
