package com.iptv.crawler.crawler;

import com.iptv.crawler.entity.IptvChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * IPTV直播源爬虫基类
 */
public abstract class BaseCrawler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 来源名称 */
    protected String sourceName;

    public BaseCrawler(String sourceName) {
        this.sourceName = sourceName;
    }

    /** 执行爬取 */
    public abstract List<IptvChannel> crawl() throws Exception;

    protected IptvChannel createChannel(String name, String url, String category, String protocol) {
        IptvChannel ch = new IptvChannel();
        ch.setName(name);
        ch.setUrl(url);
        ch.setSource(sourceName);
        ch.setCategory(category);
        ch.setProtocol(protocol);
        return ch;
    }

    public String getSourceName() {
        return sourceName;
    }
}
