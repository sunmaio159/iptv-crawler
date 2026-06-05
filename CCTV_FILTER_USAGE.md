# 📺 IPTV频道过滤功能使用说明

## 🎯 功能说明
本系统已添加央视和卫视频道过滤功能，可通过`cnOnly=true`参数只导出包含"央视"、"卫视"、"CCTV"等关键词的频道。

## 🔧 使用方法

### 1. JSON格式导出 (推荐)
```
GET http://localhost:8080/api/iptv/playlist.json?cnOnly=true
```

### 2. TXT格式导出
```
GET http://localhost:8080/api/iptv/playlist.txt?cnOnly=true
```

### 3. M3U格式导出
```
GET http://localhost:8080/api/iptv/playlist.m3u?cnOnly=true
```

## 📋 过滤规则

### 频道名匹配 (包含以下任意关键词):
- CCTV
- 央视
- 卫视
- 北京卫视
- 上海卫视
- 江苏卫视
- 浙江卫视
- 湖南卫视
- 广东卫视
- 深圳卫视
- 天津卫视
- 重庆卫视
- 东方卫视
- 东南卫视
- 广西卫视
- 黑龙江卫视

### 分类匹配 (包含以下任意关键词):
- 央视
- 卫视
- CCTV
- 央视频道
- 卫视频道
- 央视IPV4
- 卫视IPV4

## 📊 当前统计
- 总频道数: 8,251个
- 可用频道: 988个
- 过滤后央视/卫视频道: 37个

## 🚀 使用示例

### 导出纯净版央视卫视频道列表:
```bash
# 导出JSON格式
curl "http://localhost:8080/api/iptv/playlist.json?cnOnly=true" > cn_channels.json

# 导出TXT格式(适合播放器)
curl "http://localhost:8080/api/iptv/playlist.txt?cnOnly=true" > cn_channels.txt

# 导出M3U格式(适合播放器)
curl "http://localhost:8080/api/iptv/playlist.m3u?cnOnly=true" > cn_channels.m3u
```

### 获取统计信息:
```bash
# 查看系统整体统计
curl http://localhost:8080/api/iptv/stats

# 查看源健康度
curl http://localhost:8080/api/iptv/metrics/summary
```

## ✅ 功能特点
- ✅ 精确匹配央视/卫视频道
- ✅ 支持多种导出格式
- ✅ 自动去重和排序
- ✅ 包含频道质量信息(延迟/带宽)
- ✅ 实时更新频道状态

## 📱 播放器兼容性
- **TXT格式**: 适合PotPlayer、VLC等中文播放器
- **M3U格式**: 通用IPTV播放器格式
- **JSON格式**: 适合程序处理和二次开发