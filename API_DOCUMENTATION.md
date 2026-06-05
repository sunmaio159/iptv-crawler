# IPTV Crawler REST API 接口文档

## 基础信息

- **Base URL**: `http://localhost:8080`
- **数据格式**: JSON (除导出接口外)
- **认证**: 无（生产环境建议添加）
- **跨域**: 默认不支持，需配置CORS

---

## 📊 频道管理接口

### 1. 获取统计信息

**接口**: `GET /api/iptv/stats`

**说明**: 获取系统整体统计信息

**响应参数**:
| 字段 | 类型 | 说明 |
|------|------|------|
| total | long | 频道总数 |
| available | long | 可用频道数 |
| categoryCount | int | 分类数量 |
| categories | String[] | 所有分类名称列表 |

**示例**:
```bash
curl http://localhost:8080/api/iptv/stats
```

**响应**:
```json
{
  "total": 8251,
  "available": 144,
  "categoryCount": 172,
  "categories": ["央视", "卫视", "体育", "电影", "News", "Sports", ...]
}
```

---

### 2. 获取所有频道

**接口**: `GET /api/iptv/channels`

**说明**: 获取数据库中所有频道（包括可用和不可用）

**查询参数**:
- 无

**响应**: `IptvChannel[]` 数组

**IptvChannel 字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| name | String | 频道名称 |
| url | String | 直播地址 |
| source | String | 数据源标识 |
| category | String | 分类 |
| protocol | String | 协议类型 (hls/http-flv/rtmp/rtsp/http) |
| latencyMs | Long | 延迟(毫秒)，可能为null |
| bandwidthKbps | Long | 带宽(Kbps)，可能为null |
| available | Boolean | 是否可用 |
| lastCheckTime | LocalDateTime | 最后检测时间 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

**示例**:
```bash
curl http://localhost:8080/api/iptv/channels
```

---

### 3. 获取可用频道

**接口**: `GET /api/iptv/channels/available`

**说明**: 仅获取检测可用的频道（`available=true`）

**响应**: `IptvChannel[]` 数组（同第2项字段）

---

### 4. 按分类查询

**接口**: `GET /api/iptv/channels/category/{category}`

**说明**: 根据分类名称筛选频道

**路径参数**:
- `category` (String): 分类名称（支持中文如"央视"、"卫视"等）

**响应**: `IptvChannel[]` 数组

**示例**:
```bash
# 查询央视分类
curl http://localhost:8080/api/iptv/channels/category/央视

# 查询体育分类
curl http://localhost:8080/api/iptv/channels/category/Sports
```

---

### 5. 搜索频道

**接口**: `GET /api/iptv/channels/search`

**说明**: 根据频道名称模糊搜索

**查询参数**:
- `name` (String, 必填): 搜索关键词（支持部分匹配）

**响应**: `IptvChannel[]` 数组（按相关度排序）

**示例**:
```bash
curl "http://localhost:8080/api/iptv/channels/search?name=CCTV"
curl "http://localhost:8080/api/iptv/channels/search?name=体育"
```

---

### 6. 获取延迟最低的频道

**接口**: `GET /api/iptv/channels/top/latency`

**说明**: 按延迟升序获取可用频道（延迟最低的在前）

**查询参数**:
- `limit` (int, optional, default=50): 返回数量限制

**响应**: `IptvChannel[]` 数组（仅可用频道）

**示例**:
```bash
# 获取延迟最低的20个频道
curl "http://localhost:8080/api/iptv/channels/top/latency?limit=20"
```

---

### 7. 获取带宽最高的频道

**接口**: `GET /api/iptv/channels/top/bandwidth`

**说明**: 按带宽降序获取可用频道（带宽最高的在前）

**查询参数**:
- `limit` (int, optional, default=50): 返回数量限制

**响应**: `IptvChannel[]` 数组（仅可用频道）

**示例**:
```bash
# 获取带宽最高的50个频道
curl "http://localhost:8080/api/iptv/channels/top/bandwidth?limit=50"
```

---

## 🔄 操作接口

### 8. 手动触发爬取（仅爬取，不测速）

**接口**: `POST /api/iptv/crawl`

**说明**: 从配置的所有源爬取频道并保存到数据库，**不进行速度测试**

**注意**: 
- 此操作可能耗时数分钟（取决于源数量和网络）
- 仅新增之前不存在的频道（基于URL去重）
- 新增频道默认 `available=false`

**响应**: 纯文本消息

**示例**:
```bash
curl -X POST http://localhost:8080/api/iptv/crawl
# 响应: "爬取完成，新增 354 个频道"
```

---

### 9. 手动触发完整爬取+测速

**接口**: `POST /api/iptv/crawl-and-test`

**说明**: 执行完整的爬取+两轮测速流程

**流程**:
1. 爬取所有配置的源
2. 去重保存新频道（`available=false`）
3. 对所有 `available=false` 的频道执行两轮测速
   - 第一轮：快速检测（4秒）→ 不可用直接标记
   - 第二轮：详细测速（8秒，默认并发4）→ 可用频道记录延迟和带宽

**注意**:
- 此操作耗时**非常长**（数千频道可能需要数小时）
- HTTP请求可能超时，建议在服务端日志观察结果
- 建议使用 `/api/iptv/metrics/summary` 查看最终状态

**响应**: 纯文本消息

**示例**:
```bash
# 使用长超时
curl -X POST http://localhost:8080/api/iptv/crawl-and-test --max-time 36000
# 响应: "爬取+测速完成，新增 354 个频道"
```

---

### 10. 手动刷新测速

**接口**: `POST /api/iptv/speedtest`

**说明**: 对**所有**频道重新执行两轮测速（包括已标记可用的）

**用途**: 更新所有频道的延迟和带宽数据，发现可用性变化

**注意**: 耗时较长，建议定期在定时任务中执行

**响应**: 纯文本消息

**示例**:
```bash
curl -X POST http://localhost:8080/api/iptv/speedtest --max-time 18000
# 响应: "测速刷新完成，144 个可用"
```

---

### 11. 清理过期不可用频道

**接口**: `POST /api/iptv/cleanup`

**说明**: 删除 `available=false` 且超过保留期限（默认30天）的频道

**保留期配置**: `iptv.cleanup.retention-days` (application.yml)

**响应**: 纯文本消息

**示例**:
```bash
curl -X POST http://localhost:8080/api/iptv/cleanup
# 响应: "清理完成，删除 123 个过期频道"
```

---

## 📤 导出接口

### 12. 导出 M3U 格式

**接口**: `GET /api/iptv/playlist.m3u`

**Content-Type**: `text/plain`

**说明**: 导出所有**可用**频道为 M3U 格式，通用IPTV播放器兼容

**格式示例**:
```m3u
#EXTM3U
#EXTINF:-1 group-title="央视" tvg-name="CCTV-1",CCTV-1
http://example.com/stream1.m3u8
#EXTINF:-1 group-title="卫视" tvg-name="湖南卫视",湖南卫视
http://example.com/stream2.m3u8
```

**使用**:
```bash
curl http://localhost:8080/api/iptv/playlist.m3u > playlist.m3u
```

---

### 13. 导出 TXT 格式

**接口**: `GET /api/iptv/playlist.txt`

**Content-Type**: `text/plain`

**说明**: 导出所有**可用**频道为 TXT 格式（中文播放器常用）

**特点**:
- 按分类分组
- 分类名称添加 emoji 图标（如 📺央视、📡卫视）
- 分类排序：央视→卫视→4K→体育→电影→新闻→少儿→...
- 末尾添加更新时间区块

**格式示例**:
```txt
📺央视频道,#genre#
CCTV-1,http://...
CCTV-2,http://...

📡卫视频道,#genre#
湖南卫视,http://...
浙江卫视,http://...

🕿️更新时间,#genre#
2026-06-02 22:30:00,https://github.com/iptv-org/iptv
```

**使用**:
```bash
curl http://localhost:8080/api/iptv/playlist.txt > playlist.txt
```

---

### 14. 导出 JSON 格式

**接口**: `GET /api/iptv/playlist.json`

**说明**: 导出所有**可用**频道为 JSON 数组

**响应**: `IptvChannel[]` 数组（同 `/api/iptv/channels/available`）

**示例**:
```bash
curl http://localhost:8080/api/iptv/playlist.json > channels.json
```

---

## 📈 源监控指标接口（新增）

### 15. 获取所有源指标

**接口**: `GET /api/iptv/metrics/sources`

**说明**: 获取所有配置数据源的运行指标

**响应**: `SourceMetrics[]` 数组

**SourceMetrics 字段说明**:
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键ID |
| sourceName | String | 源名称（与配置一致） |
| successCount | int | 总成功次数 |
| failureCount | int | 总失败次数 |
| consecutiveFailures | int | 连续失败次数（熔断依据） |
| lastSuccessTime | LocalDateTime | 最后成功时间 |
| lastFailureTime | LocalDateTime | 最后失败时间 |
| lastErrorMessage | String | 最后错误信息（前1000字符） |
| avgResponseTimeMs | Double | 平均响应时间（毫秒，EMA滑动平均） |
| avgChannelsPerRun | Double | 平均每批次爬取频道数 |
| qualityScore | Double | 源质量评分（0-100，可能为null） |
| lastScoreCalculated | LocalDateTime | 最后评分时间 |
| createTime | LocalDateTime | 创建时间 |
| updateTime | LocalDateTime | 更新时间 |

**示例**:
```bash
curl http://localhost:8080/api/iptv/metrics/sources
```

**响应片段**:
```json
[
  {
    "id": 45,
    "sourceName": "iptv-org-movies",
    "successCount": 3,
    "failureCount": 0,
    "consecutiveFailures": 0,
    "lastSuccessTime": "2026-06-02T22:46:31.022",
    "lastFailureTime": null,
    "lastErrorMessage": null,
    "avgResponseTimeMs": 21913.6,
    "avgChannelsPerRun": 347.0,
    "qualityScore": 80.0,
    "lastScoreCalculated": "2026-06-02T22:50:00.123",
    "createTime": "2026-06-02T22:15:55.480",
    "updateTime": "2026-06-02T22:15:55.482"
  }
]
```

---

### 16. 获取健康源列表

**接口**: `GET /api/iptv/metrics/sources/healthy`

**说明**: 获取成功次数达到指定阈值的源（用于识别稳定源）

**查询参数**:
- `minSuccess` (int, optional, default=10): 最小成功次数阈值

**响应**: `SourceMetrics[]` 数组（按最后成功时间降序）

**示例**:
```bash
# 获取成功次数≥10的源
curl "http://localhost:8080/api/iptv/metrics/sources/healthy?minSuccess=10"
```

---

### 17. 手动触发质量评分计算

**接口**: `POST /api/iptv/metrics/recalculate`

**说明**: 手动触发对所有源的质量评分计算

**评分算法**:
```
质量分 = 成功率×40% + 响应时间分×20% + 频道数分×20% + 新鲜度分×20%

其中:
- 成功率分 = (成功次数/总次数) × 40
- 响应时间分: ≤1秒满分20，>5秒为0，线性衰减
- 频道数分: 每50个频道得20分（可超过）
- 新鲜度分: 24小时内满分20，7天内线性衰减到0
```

**响应**: 纯文本消息

**定时任务**: 每日凌晨3点在 `IptvScheduler.dailyCrawl()` 中自动调用

**示例**:
```bash
curl -X POST http://localhost:8080/api/iptv/metrics/recalculate
# 响应: "质量评分计算完成"
```

---

### 18. 获取汇总统计

**接口**: `GET /api/iptv/metrics/summary`

**说明**: 获取源指标的整体汇总信息

**响应字段**:
| 字段 | 类型 | 说明 |
|------|------|------|
| totalSources | long | 总源数量 |
| healthySources | long | 健康源数量（质量分≥60） |
| degradedSources | long | 降级源数量（连续失败>0） |
| avgQualityScore | double | 平均质量分 |

**示例**:
```bash
curl http://localhost:8080/api/iptv/metrics/summary
```

**响应**:
```json
{
  "totalSources": 74,
  "healthySources": 53,
  "degradedSources": 21,
  "avgQualityScore": 57.7
}
```

---

### 19. 查询特定源状态

**接口**: `GET /api/iptv/metrics/source/{sourceName}`

**说明**: 获取单个源的详细状态（包括熔断器状态）

**路径参数**:
- `sourceName` (String): 源名称（与配置的 `name` 一致）

**响应字段**:
| 字段 | 类型 | 说明 |
|------|------|------|
| sourceName | String | 源名称 |
| consecutiveFailures | int | 连续失败次数 |
| lastSuccessTime | LocalDateTime | 最后成功时间 |
| lastFailureTime | LocalDateTime | 最后失败时间 |
| lastErrorMessage | String | 最后错误信息 |
| circuitBreakerOpen | boolean | 熔断器是否开启 |
| qualityScore | Double | 质量评分 |
| successCount | int | 总成功次数 |
| failureCount | int | 总失败次数 |

**示例**:
```bash
# 查询 iptv-org-cn 源状态
curl http://localhost:8080/api/iptv/metrics/source/iptv-org-cn
```

**响应**:
```json
{
  "sourceName": "iptv-org-cn",
  "consecutiveFailures": 0,
  "lastSuccessTime": "2026-06-02T22:45:49.196",
  "lastFailureTime": null,
  "lastErrorMessage": null,
  "circuitBreakerOpen": false,
  "qualityScore": 80.5,
  "successCount": 3,
  "failureCount": 0
}
```

---

### 20. 重置源的熔断器

**接口**: `POST /api/iptv/metrics/source/{sourceName}/reset`

**说明**: 手动重置指定源的熔断状态（将 `consecutiveFailures` 归零）

**用途**: 当源修复后，强制允许重试

**路径参数**:
- `sourceName` (String): 源名称

**响应**: 纯文本消息

**示例**:
```bash
# 重置 fanmingming-main 源的熔断状态
curl -X POST http://localhost:8080/api/iptv/metrics/source/fanmingming-main/reset
# 响应: "已重置源的熔断状态: fanmingming-main"
```

---

### 21. 刷新指标缓存

**接口**: `POST /api/iptv/metrics/refresh`

**说明**: 强制从数据库重新加载指标到内存缓存

**用途**: 外部修改数据库后同步内存状态

**响应**: 纯文本消息

**示例**:
```bash
curl -X POST http://localhost:8080/api/iptv/metrics/refresh
# 响应: "源指标缓存已刷新"
```

---

## 🎯 使用示例

### 完整工作流示例

```bash
# 1. 查看系统状态
curl http://localhost:8080/api/iptv/stats
# {"total":8251,"available":144,"categoryCount":172}

# 2. 查看源健康状况
curl http://localhost:8080/api/iptv/metrics/summary
# {"totalSources":74,"healthySources":53,"degradedSources":21}

# 3. 列出失败源
curl http://localhost:8080/api/iptv/metrics/sources \
  | jq '.[] | select(.consecutiveFailures > 0) | {source: .sourceName, errors: .lastErrorMessage}'
# 识别需要修复或删除的源

# 4. 手动触发质量评分
curl -X POST http://localhost:8080/api/iptv/metrics/recalculate
# "质量评分计算完成"

# 5. 导出最优频道列表
curl http://localhost:8080/api/iptv/channels/available > all_available.json
curl http://localhost:8080/api/iptv/playlist.m3u > playlist.m3u
curl http://localhost:8080/api/iptv/playlist.txt > playlist.txt

# 6. 查询高速频道
curl "http://localhost:8080/api/iptv/channels/top/latency?limit=20" \
  | jq '.[] | {name: .name, latency: .latencyMs, url: .url}'
```

---

## 🔍 监控查询示例

### 识别最佳数据源

```bash
# 按质量分排序源
curl http://localhost:8080/api/iptv/metrics/sources \
  | jq -r 'sort_by(.qualityScore) | reverse[] | "\(.sourceName) \(.qualityScore)"' \
  | head -10
```

### 监控失败源

```bash
# 查看连续失败的源
curl http://localhost:8080/api/iptv/metrics/sources \
  | jq '.[] | select(.consecutiveFailures >= 3) | {source: .sourceName, failures: .consecutiveFailures, error: .lastErrorMessage}'
```

### 评估源性能

```bash
# 查看响应时间最快的源
curl http://localhost:8080/api/iptv/metrics/sources \
  | jq '.[] | select(.avgResponseTimeMs != null) | {source: .sourceName, avgMs: .avgResponseTimeMs, channels: .avgChannelsPerRun}' \
  | sort_by(.avgMs) | head -10
```

---

## ⚠️ 故障排查

### 接口超时

**问题**: `POST /api/iptv/crawl-and-test` 长时间无响应

**原因**: 数千频道测速需数小时，HTTP超时

**解决方案**:
1. 使用后台任务（未来版本）
2. 分阶段执行：先爬取，后单独测速
3. 调整超时参数：`--max-time 36000` (10小时)

### 熔断器触发

**现象**: 源连续失败，不再尝试

**查询**: `GET /api/iptv/metrics/source/{sourceName}` 查看 `consecutiveFailures`

**恢复**:
```bash
# 手动重置
curl -X POST /api/iptv/metrics/source/{源名称}/reset

# 或等待自动恢复（待实现）
# TODO: 实现半开状态自动尝试恢复机制
```

### 数据源不可用

**排查步骤**:
1. 检查源状态: `GET /api/iptv/metrics/sources`
2. 查看错误信息: `lastErrorMessage` 字段
3. 常见错误:
   - `HTTP 404`: 源URL已失效，需更新配置
   - `timeout`: 网络超时，源响应慢
   - `iptv-abc.xyz`: DNS解析失败，域名失效

4. 清理不良源（可选）:
   ```sql
   -- 从数据库删除失败源记录
   DELETE FROM source_metrics WHERE failure_count > 0 AND success_count = 0;
   -- 从配置文件移除对应 entry
   ```

---

## 📊 数据字典

### IptvChannel 实体

```sql
CREATE TABLE iptv_channel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    source VARCHAR(100),
    category VARCHAR(100),
    protocol VARCHAR(50),
    latency_ms BIGINT,
    bandwidth_kbps BIGINT,
    available BOOLEAN NOT NULL DEFAULT FALSE,
    last_check_time TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    INDEX idx_url (url),
    INDEX idx_category (category),
    INDEX idx_available (available)
);
```

### SourceMetrics 实体

```sql
CREATE TABLE source_metrics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_name VARCHAR(100) NOT NULL UNIQUE,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    consecutive_failures INT NOT NULL DEFAULT 0,
    last_success_time TIMESTAMP,
    last_failure_time TIMESTAMP,
    last_error_message VARCHAR(1000),
    avg_response_time_ms DOUBLE,
    avg_channels_per_run DOUBLE,
    quality_score DOUBLE,
    last_score_calculated TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    INDEX idx_source_name (source_name),
    INDEX idx_last_success (last_success_time)
);
```

---

## 🔧 配置参数参考

### application.yml 关键配置

```yaml
iptv:
  # 数据源列表（可扩展至50+）
  sources:
    - name: iptv-org-cn
      url: https://iptv-org.github.io/iptv/countries/cn.m3u
      large: true

  # 爬虫配置
  crawler:
    concurrency: 5              # 并发线程数 (1-10)
    max-retries: 3              # 最大重试次数 (0-5)
    retry-delay-ms: 2000       # 重试延迟基数（毫秒）
    circuit-breaker:
      failure-threshold: 5      # 熔断失败阈值 (3-10)
      half-open-timeout-ms: 300000  # 熔断恢复时间（毫秒）

  # 测速配置
  speedtest:
    quick-probe-seconds: 4      # 快速检测超时
    max-probe-seconds: 8       # 详细测速超时
    concurrency: 4             # 测速并发数 (1-4)

  # 指标配置
  metrics:
    retention-hours: 168       # 指标保留时间（小时）
    score-calculation-interval-minutes: 1440  # 评分计算间隔

  # FFmpeg配置
  ffmpeg:
    path: ffmpeg               # ffmpeg可执行文件路径

  # 清理配置
  cleanup:
    retention-days: 30         # 不可用频道保留天数
```

---

## 📈 性能指标

### 实测数据（2026-06-02）

| 操作 | 频道数 | 耗时 | 平均速度 |
|------|--------|------|----------|
| 纯爬取（5并发） | 7,561 → 7,951 | 3分51秒 | ~2,000 频道/分钟 |
| 快速测速（4并发） | 7,753 | ~45分钟* | ~170 频道/分钟 |
| 完整流程（爬+测） | 7,561 | ~数小时 | - |

*快速测速估算值，实际取决于源响应速度

---

## 🚀 最佳实践

1. **日常使用**: 仅调用 `POST /api/iptv/crawl` 更新频道列表
2. **每周深度更新**: 调用 `POST /api/iptv/crawl-and-test` 完整更新
3. **监控告警**: 定时查询 `/api/iptv/metrics/summary`，健康源<50%时告警
4. **导出部署**: 使用 `GET /api/iptv/playlist.txt` 生成最终播放列表
5. **源优化**: 定期分析 `/api/iptv/metrics/sources`，删除质量分<30的源

---

**文档版本**: v1.0  
**生成日期**: 2026-06-02  
**最后更新**: 包含74个数据源的完整监控体系
