# iptv-crawler
# IPTV Crawler — 项目使用文档

> IPTV 直播源自动爬取、测速、导出系统  
> 基于 Spring Boot 3.1.5 + JPA + H2 + OkHttp + ffmpeg

---

## 📑 目录

1. [项目概述](#1-项目概述)
2. [快速开始](#2-快速开始)
3. [配置说明](#3-配置说明)
4. [运行与管理](#4-运行与管理)
5. [REST API 速查](#5-rest-api-速查)
6. [black_list.txt 使用](#6-black_listtxt-使用)
7. [分辨率过滤机制](#7-分辨率过滤机制)
8. [熔断与自动恢复](#8-熔断与自动恢复)
9. [导出播放列表](#9-导出播放列表)
10. [Python 辅助脚本](#10-python-辅助脚本)
11. [定时任务](#11-定时任务)
12. [常见问题](#12-常见问题)

---

## 1. 项目概述

### 功能

- **自动爬取**：从 74+ 个公开 M3U 源并发抓取直播频道
- **测速验证**：两轮 ffmpeg 测速（快速检测 + 详细带宽/延迟）
- **智能过滤**：分辨率过滤（仅保留 1080P+）、黑名单、内网/IPv6 过滤
- **熔断保护**：对失效源自动熔断，每小时尝试恢复
- **多格式导出**：M3U / TXT（中文播放器格式）/ JSON
- **REST API**：完整的查询、操作、监控接口
- **定时任务**：每日爬取、每 6 小时测速、每周清理

### 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Spring Boot | 3.1.5 | Web 框架 |
| H2 Database | 嵌入式 | 数据存储 |
| OkHttp | 4.12.0 | HTTP 客户端 |
| ffmpeg | 外部 | 流媒体测速 |
| Maven | 3.8+ | 构建工具 |

### 目录结构

```
iptv-crawler/
├── src/main/java/com/iptv/crawler/
│   ├── IptvCrawlerApplication.java    # 启动入口
│   ├── config/
│   │   └── IptvProperties.java        # 配置映射
│   ├── controller/
│   │   ├── IptvController.java        # 频道 REST API
│   │   ├── SourceMetricsController.java # 指标 REST API
│   │   └── BlacklistController.java   # 黑名单 REST API (新增)
│   ├── crawler/
│   │   ├── BaseCrawler.java           # 爬虫基类
│   │   └── IptvSourceCrawler.java     # 主爬虫实现
│   ├── entity/
│   │   ├── IptvChannel.java           # 频道实体
│   │   └── SourceMetrics.java         # 指标实体
│   ├── repository/                    # JPA 仓库
│   ├── scheduler/
│   │   └── IptvScheduler.java         # 定时任务
│   ├── service/
│   │   ├── IptvService.java           # 频道业务逻辑
│   │   ├── SourceMetricsService.java  # 指标业务逻辑
│   │   └── BlacklistService.java      # 黑名单服务 (新增)
│   └── speedtest/
│       └── FfmpegSpeedTester.java     # ffmpeg 测速
├── src/main/resources/
│   └── application.yml                # 主配置文件
├── src/test/                          # 测试代码
├── black_list.txt                     # 手工屏蔽 URL 列表
├── export_channels.py                 # Python 导出脚本
├── check_channels.py                  # Python 查询工具
├── channels.json                      # 频道数据 JSON
├── channels_clean.txt                 # 纯净播放列表
└── channels_export.txt                # 带延迟标记的播放列表
```

---

## 2. 快速开始

### 前置条件

1. **Java 17+**：确认已安装
   ```bash
   java -version
   ```

2. **ffmpeg**：测速需要（可选，不影响爬取）
   ```bash
   ffmpeg -version
   ```

3. **Python 3.7+**：辅助脚本需要
   ```bash
   python --version
   ```

### 启动服务

```bash
# 克隆/进入项目目录
cd iptv-crawler

# 编译
mvn clean package -DskipTests

# 启动
java -jar target/iptv-crawler-1.0.0.jar

# 或使用 Maven 直接运行
mvn spring-boot:run
```

启动后访问：
- **Web 控制台**：http://localhost:8080
- **H2 数据库控制台**：http://localhost:8080/h2-console
- **健康检查**：http://localhost:8080/actuator/health

### 首次运行流程

```bash
# 1. 查看当前状态
curl http://localhost:8080/api/iptv/stats

# 2. 自动爬取频道
curl -X POST http://localhost:8080/api/iptv/crawl

# 3. 执行测速
curl -X POST http://localhost:8080/api/iptv/speedtest --max-time 18000

# 4. 导出播放列表
curl http://localhost:8080/api/iptv/playlist.txt > playlist.txt
```

---

## 3. 配置说明

### 主配置文件 `application.yml`

#### 直播源配置

```yaml
iptv:
  sources:
    - name: iptv-org-cn      # 源名称（唯一标识）
      url: https://...        # M3U 文件 URL
      large: true             # 大文件使用更长超时
```

支持任意数量的 M3U 源，内置 74 个源覆盖：
- **iptv-org**：按国家、语言、分类（30+ 个）
- **fanmingming**：中文源系列（6 个）
- **其他中文源**：aptv、yuechan、ip_tv 等（10 个）
- **国际源**：综合源（5 个）

#### 爬虫配置

```yaml
iptv:
  crawler:
    concurrency: 5                    # 并发线程数 (1-10)
    max-retries: 3                    # 最大重试次数
    retry-delay-ms: 2000             # 重试延迟（指数退避）
    min-resolution: 1080p            # 最低分辨率要求
    resolution-filter-mode: conservative  # 无标注时: conservative(排除) / permissive(放行)
```

#### 熔断器配置

```yaml
    circuit-breaker:
      failure-threshold: 5           # 连续失败 N 次触发熔断
      half-open-timeout-ms: 300000   # 5 分钟后自动尝试恢复
```

#### 测速配置

```yaml
iptv:
  speedtest:
    quick-probe-seconds: 4           # 第一轮快速检测超时
    max-probe-seconds: 8             # 第二轮详细测速超时
    concurrency: 4                   # 测速并发数
```

#### 频道屏蔽配置

```yaml
iptv:
  blocklist:
    name-keywords:                   # 频道名包含即屏蔽
      - "CNN"
      - "BBC"
      - ...
    url-keywords:                    # URL 包含即屏蔽
      - "cctvplus.com"
      - "udp://"
      - ...
    category-keywords:               # 分类包含即屏蔽
      - "Religious"
      - "Shop"
      - ...
    exclude-names:                   # 频道名精确匹配
      - "CCTV-4 Asia"
      - "CCTV-4 Europe"
```

> **注意**：`blocklist` 和 `black_list.txt` 的区别  
> - `blocklist`：通过关键词批量过滤，在 `application.yml` 中配置  
> - `black_list.txt`：精确到单个 URL 的手工屏蔽，通过 API 或文件管理

---

## 4. 运行与管理

### 构建

```bash
# 跳过测试编译
mvn clean package -DskipTests

# 全量构建（含测试）
mvn clean package

# 仅编译，不打包
mvn compile
```

### 启动方式

```bash
# 方式一：JAR 包启动
java -jar target/iptv-crawler-1.0.0.jar

# 方式二：Maven 直接运行
mvn spring-boot:run

# 方式三：指定端口
java -jar target/iptv-crawler-1.0.0.jar --server.port=9090

# 方式四：指定配置文件
java -jar target/iptv-crawler-1.0.0.jar --spring.profiles.active=prod
```

### 数据库

默认使用嵌入式 H2 数据库，文件存储在 `./data/iptv.mv.db`：

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/iptv;AUTO_SERVER=TRUE
```

如需切换 MySQL，取消 `application.yml` 底部注释并注释 H2 配置。

### 日志

```bash
# 实时查看日志
tail -f app.log

# 查看最近错误
grep -i error app.log
```

---

## 5. REST API 速查

### 频道查询

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/iptv/stats` | 统计信息 |
| GET | `/api/iptv/channels` | 所有频道 |
| GET | `/api/iptv/channels/available` | 可用频道 |
| GET | `/api/iptv/channels/category/{cat}` | 按分类查询 |
| GET | `/api/iptv/channels/search?name=` | 按名称搜索 |
| GET | `/api/iptv/channels/top/latency?limit=50` | 最低延迟 |
| GET | `/api/iptv/channels/top/bandwidth?limit=50` | 最高带宽 |

### 操作

| 方法 | 路径 | 说明 | 耗时 |
|------|------|------|------|
| POST | `/api/iptv/crawl` | 仅爬取（不测速） | ~4 分钟 |
| POST | `/api/iptv/crawl-and-test` | 爬取+测速 | 数小时 |
| POST | `/api/iptv/speedtest` | 重新测速 | 数小时 |
| POST | `/api/iptv/cleanup` | 清理过期频道 | 瞬间 |

### 导出

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/iptv/playlist.m3u?cnOnly=false` | M3U 格式 |
| GET | `/api/iptv/playlist.txt?all=false&cnOnly=true` | TXT 格式 |
| GET | `/api/iptv/playlist.json?cnOnly=false` | JSON 格式 |

**TXT 格式参数**：
- `all=true/false`：是否包含不可用频道（默认 false）
- `cnOnly=true/false`：仅央视/卫视频道（默认 false）
- `maxPerCategory=50`：每类最多频道数

### 源指标

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/iptv/metrics/sources` | 所有源指标 |
| GET | `/api/iptv/metrics/sources/healthy?minSuccess=10` | 健康源 |
| GET | `/api/iptv/metrics/summary` | 汇总统计 |
| GET | `/api/iptv/metrics/source/{name}` | 单源状态 |
| POST | `/api/iptv/metrics/recalculate` | 重算质量评分 |
| POST | `/api/iptv/metrics/source/{name}/reset` | 重置熔断器 |
| POST | `/api/iptv/metrics/refresh` | 刷新缓存 |

### 黑名单管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/iptv/blacklist` | 列出所有屏蔽 URL |
| POST | `/api/iptv/blacklist?url=...` | 添加 URL |
| DELETE | `/api/iptv/blacklist?url=...` | 移除 URL |
| POST | `/api/iptv/blacklist/reload` | 从文件重载 |

---

## 6. black_list.txt 使用

### 文件格式

```
# 这是一条注释（以 # 开头）
http://example.com/bad-stream.m3u8
https://another-bad-source.com/live.m3u8
```

- 每行一个 HTTP/HTTPS 地址
- `#` 开头的行视为注释
- 空行自动忽略

### 两种管理方式

**方式一：直接编辑文件**

```bash
# 添加屏蔽地址
echo "http://bad-source.com/live.m3u8" >> black_list.txt

# 通过 API 重载
curl -X POST http://localhost:8080/api/iptv/blacklist/reload
```

**方式二：通过 API 管理**

```bash
# 查看当前黑名单
curl http://localhost:8080/api/iptv/blacklist

# 添加
curl -X POST "http://localhost:8080/api/iptv/blacklist?url=http://bad.com/stream.m3u8"

# 移除
curl -X DELETE "http://localhost:8080/api/iptv/blacklist?url=http://bad.com/stream.m3u8"
```

### 生效范围

- `black_list.txt` 中的地址**在爬虫阶段就被过滤**，不会入库
- 已存在于数据库的频道不会受新添加黑名单的影响（需要重新爬取）

---

## 7. 分辨率过滤机制

### 工作原理

爬虫解析 M3U 文件时，根据频道名中的分辨率标签判断是否保留：

| 标签 | 默认行为 (conservative) | permissive 模式 |
|------|------------------------|-----------------|
| `4K`, `2160p` | ✅ 保留 | ✅ 保留 |
| `1080p` | ✅ 保留 | ✅ 保留 |
| `720p`, `600p`, `480p`, `360p` | ❌ 排除 | ❌ 排除 |
| 无分辨率标注 | ❌ 排除 | ✅ 放行 |

### 配置方式

```yaml
iptv:
  crawler:
    min-resolution: 1080p            # 可选: 4k / 2160p / 1080p / 720p / 480p
    resolution-filter-mode: conservative  # 可选: conservative / permissive
```

- **conservative**（默认）：只有明确标注 1080P+ 的才保留
- **permissive**：只排除明确低分辨率，无标注的视为通过

---

## 8. 熔断与自动恢复

### 熔断机制

当某个 M3U 源连续爬取失败达到阈值时，自动触发熔断，不再尝试该源：

```
正常 → 连续失败 N 次 → 熔断开始 → 跳过该源 → 自动恢复 → 重试
```

### 触发条件

```yaml
iptv:
  crawler:
    circuit-breaker:
      failure-threshold: 5           # 连续失败 5 次触发
      half-open-timeout-ms: 300000   # 5 分钟后半开
```

### 自动恢复流程

- 每小时检查一次
- 熔断中的源如果距 `lastFailureTime` 已超过 `halfOpenTimeoutMs`
- 自动重置 `consecutiveFailures = 0`，下次爬取会重新尝试

### 手动管理

```bash
# 查看特定源状态
curl http://localhost:8080/api/iptv/metrics/source/iptv-org-cn

# 手动重置熔断
curl -X POST http://localhost:8080/api/iptv/metrics/source/iptv-org-cn/reset
```

---

## 9. 导出播放列表

### M3U 格式（通用）

```bash
curl http://localhost:8080/api/iptv/playlist.m3u > playlist.m3u
curl "http://localhost:8080/api/iptv/playlist.m3u?cnOnly=true" > playlist_cn.m3u
```

适用于：VLC、PotPlayer、Kodi、IPTV Smarters 等

### TXT 格式（中文播放器）

```bash
# 纯可用频道
curl http://localhost:8080/api/iptv/playlist.txt > playlist.txt

# 仅央视卫视
curl "http://localhost:8080/api/iptv/playlist.txt?cnOnly=true" > playlist_cn.txt

# 所有频道（含不可用）
curl "http://localhost:8080/api/iptv/playlist.txt?all=true" > playlist_all.txt
```

输出示例：
```
📺央视频道,#genre#
CCTV-1,http://example.com/cctv1.m3u8
CCTV-2,http://example.com/cctv2.m3u8

📡卫视频道,#genre#
湖南卫视,http://example.com/hunan.m3u8
浙江卫视,http://example.com/zhejiang.m3u8
```

适用于：DIYP、百川影音、IPTV Pro 等中文播放器

### cnOnly 过滤逻辑

启用 `cnOnly=true` 时，采用**两级匹配**策略：
1. **精确匹配**：与预定义的 18 个 CCTV 频道 + 34 个卫视频道列表进行编号/前缀匹配（支持 "CCTV1" ↔ "CCTV-1"、编号防误匹配等）
2. **模糊回退**：检查 category/name 是否包含"央视"/"卫视"/"CCTV"等关键词

---

## 10. Python 辅助脚本

### export_channels.py — 导出精选频道

从 API 获取所有频道，筛选 1080P+ 的央视和卫视频道，生成播放列表。

```bash
# 确保服务正在运行
python export_channels.py
```

输出文件：
- `channels_export.txt` — 带 TCP 延迟标记的版本
- `channels_clean.txt` — 纯净播放列表版本

功能：
- 读取 `black_list.txt` 屏蔽地址
- 仅保留 1080P+ 分辨率
- TCP 连通性测试（15 并发）
- 按质量评分选择最优源

### check_channels.py — 查询工具

```bash
# 查看统计
python check_channels.py --stats

# 查看可用频道 top10
python check_channels.py --available

# 按分类查看
python check_channels.py --category 央视

# 搜索
python check_channels.py --search CCTV
```

### generate_channels_html.py — 生成 HTML 报告

```bash
python generate_channels_html.py
# 生成 channels_report.html
```

---

## 11. 定时任务

| 时间 | 任务 | 说明 |
|------|------|------|
| 每天 03:00 | `dailyCrawl()` | 完整爬取+测速+质量评分 |
| 每 6 小时 | `refreshSpeed()` | 刷新所有频道测速 |
| 每周日 04:00 | `cleanupStale()` | 清理 30 天以上不可用频道 |
| 每小时 | `checkCircuitBreakers()` | 熔断器自动恢复检查 |

可通过调整 `application.yml` 中的 cron 表达式修改：  
（源码在 `IptvScheduler.java`）

---

## 12. 常见问题

### Q: 启动报错 `port 8080 already in use`

```bash
# 查找占用端口的进程
netstat -ano | findstr :8080
# 修改端口启动
java -jar target/iptv-crawler-1.0.0.jar --server.port=9090
```

### Q: 爬取非常慢

- 74 个源中部分已失效，会触发重试导致等待
- 建议：查看 `/api/iptv/metrics/sources` 识别并移除连续失败的源
- 调整 `concurrency: 8` 提高并发

### Q: 测速耗时太长

- 两轮测速对所有频道逐个检测（988+ 频道）
- 按需使用：日常只 `POST /crawl`，每周再跑一次 `POST /speedtest`
- 减少测速并发数防止 ffmpeg 消耗过多 CPU

### Q: `ffmpeg not found`

```bash
# Windows: 下载 ffmpeg 并加入 PATH
# 或指定路径
iptv:
  ffmpeg:
    path: C:\ffmpeg\bin\ffmpeg.exe
```

没有 ffmpeg 不影响爬取，只影响测速功能。

### Q: 如何查看数据库？

访问 http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/iptv`
- 用户名: `sa`
- 密码:（空）

### Q: 频道数据太多，如何清理？

```bash
# 清理 30 天以上不可用的频道
curl -X POST http://localhost:8080/api/iptv/cleanup

# 或通过配置调整保留天数
iptv:
  cleanup:
    retention-days: 7
```

### Q: 浏览器跨域问题？

API 默认不支持跨域。可通过配置 `@CrossOrigin` 或添加 Spring Security 解决。

---

## 附：配置模板

最小可用配置（`application.yml`）：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/iptv;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect

iptv:
  sources:
    - name: iptv-org-cn
      url: https://iptv-org.github.io/iptv/countries/cn.m3u
      large: true
    - name: fanmingming-main
      url: https://raw.githubusercontent.com/fanmingming/live/main/tv.m3u
      large: false

  crawler:
    concurrency: 5
    max-retries: 3
    retry-delay-ms: 2000
    min-resolution: 1080p
    resolution-filter-mode: conservative
    circuit-breaker:
      failure-threshold: 5
      half-open-timeout-ms: 300000

  speedtest:
    quick-probe-seconds: 4
    max-probe-seconds: 8
    concurrency: 4

  ffmpeg:
    path: ffmpeg

  cleanup:
    retention-days: 30
```

---

> **文档版本**: v2.0  
> **更新日期**: 2026-06-04  
> **新增**: black_list.txt API、分辨率过滤配置、熔断器自动恢复、cnOnly 精确匹配
