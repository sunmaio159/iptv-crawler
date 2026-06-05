#!/usr/bin/env python3
"""导出 1080p+ CCTV + 卫视频道为 TXT 格式，支持连通性测试"""
import sys, json, re, socket, time, os
from urllib.request import urlopen, Request
from concurrent.futures import ThreadPoolExecutor, as_completed

API = "http://localhost:8080/api/iptv"
OUT = "channels_export.txt"
CLEAN = "channels_clean.txt"
BLACK_LIST_FILE = "black_list.txt"

# 已知死域名/ipv6地址（py无法直接连）
DEAD_DOMAINS = ["cctvtotallive.vn.hwcdn.net", "hwcdn.net"]


def load_black_list():
    """读取 black_list.txt，返回被屏蔽的 URL 集合（忽略空白行/注释）"""
    blocked = set()
    if not os.path.exists(BLACK_LIST_FILE):
        print(f"[INFO] {BLACK_LIST_FILE} 不存在，跳过")
        return blocked
    with open(BLACK_LIST_FILE, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("http://") or line.startswith("https://"):
                blocked.add(line)
    print(f"[INFO] 加载 black_list.txt: {len(blocked)} 条屏蔽地址")
    return blocked


def is_1080p_or_above(name):
    """判断频道名是否指示 1080P 及以上分辨率（4K/2160p/1080p），
       无分辨率标注或低于 1080p 则返回 False"""
    lower = name.lower()
    # 4K / 2160p / 1080p  → 保留
    for kw in ["4k", "2160p", "1080p", "1080P"]:
        if kw in lower:
            return True
    # 明确低于 1080p 的分辨率 → 排除
    for kw in ["720p", "600p", "540p", "576p", "480p", "360p", "270p", "240p", "144p"]:
        if kw in lower:
            return False
    # 无分辨率标注 → 保守排除（无法确认是 1080P+）
    return False

# 目标列表
CCTV = [
    "CCTV-1", "CCTV-2", "CCTV-3", "CCTV-4", "CCTV-5", "CCTV-5+",
    "CCTV-6", "CCTV-7", "CCTV-8", "CCTV-9", "CCTV-10", "CCTV-11",
    "CCTV-12", "CCTV-13", "CCTV-14", "CCTV-15", "CCTV-16", "CCTV-17",
]
SAT = [
    "广东卫视", "香港卫视", "浙江卫视", "湖南卫视", "北京卫视", "湖北卫视",
    "黑龙江卫视", "安徽卫视", "重庆卫视", "东方卫视", "东南卫视", "甘肃卫视",
    "广西卫视", "贵州卫视", "海南卫视", "河北卫视", "河南卫视", "吉林卫视",
    "江苏卫视", "江西卫视", "辽宁卫视", "内蒙古卫视", "宁夏卫视", "青海卫视",
    "山东卫视", "山西卫视", "陕西卫视", "四川卫视", "深圳卫视", "三沙卫视",
    "天津卫视", "西藏卫视", "新疆卫视", "云南卫视",
]


def extract_cctv_number(s):
    """从频道名提取CCTV数字编号，如 'CCTV-10 (720p)' -> '10', 'CCTV10' -> '10'"""
    m = re.match(r'CCTV-?(\d+\+?)', s)
    return m.group(1) if m else None


def match(name, targets):
    """检查频道名是否匹配目标列表中的任意一项，优先长匹配避免 CCTV-1 误匹配 CCTV-10"""
    for t in sorted(targets, key=len, reverse=True):
        # 精确匹配（含无横杠变体，如 CCTV10 ↔ CCTV-10）
        if name == t or name == t.replace("-", ""):
            return t
        # 带分隔符: CCTV-1 (1080p) / CCTV-1（1080p） / CCTV-1 综合
        for sep in [" ", "（", "("]:
            if name.startswith(t + sep):
                return t
        # CCTV 数字精确匹配（避免 CCTV-1 贪婪匹配到 CCTV-10）
        nt = extract_cctv_number(t)
        if nt:
            nn = extract_cctv_number(name)
            if nn == nt:
                return t
        # 非CCTV的通用前缀匹配（如卫视频道）
        if name.startswith(t):
            return t
    return None

def is_dead(url):
    for d in DEAD_DOMAINS:
        if d in url:
            return True
    return False

def is_lan_or_ipv6(url):
    """过滤内网/组播/IPv6地址"""
    try:
        h = url.split("://")[1].split("/")[0].split(":")[0]
        # IPv6: [2409:xxx] 或裸 IPv6
        if h.startswith("[") or ":" in h:
            return True
        if h.startswith("10.") or h.startswith("192.168.") or h.startswith("127."):
            return True
        if h.startswith("172."):
            p = int(h.split(".")[1])
            if 16 <= p <= 31:
                return True
        f = int(h.split(".")[0])
        if 224 <= f <= 239:
            return True
    except:
        pass
    return False

def rank(c):
    score = 0
    if c.get("available"): score += 10000
    if "1080" in c["name"] or "1080" in c["url"]: score += 1000
    lat = c.get("latencyMs") or 99999
    score += max(0, 999 - min(999, int(lat)))
    # 优先 Kimentanm/aptv 源(IPv4 代理), 其次 iptv-org
    src = c.get("source", "")
    if src == "aptv": score += 500
    elif src == "iptv-org": score += 300
    return score

def tcp_test(channels):
    """TCP连通性测试"""
    def test_one(item):
        name, info = item
        url = info["url"]
        try:
            host = url.split("://")[1].split("/")[0]
            if ":" in host:
                host, port = host.split(":")
                port = int(port)
            else:
                port = 443 if url.startswith("https") else 80
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(3)
            t0 = time.time()
            s.connect((host, port))
            ms = int((time.time() - t0) * 1000)
            s.close()
            return (name, ms)
        except:
            return (name, -1)

    results = {}
    with ThreadPoolExecutor(max_workers=15) as pool:
        futures = {pool.submit(test_one, item): item[0] for item in channels.items()}
        for f in as_completed(futures):
            n, ms = f.result()
            results[n] = ms
    return results

def main():
    print("Fetching channels...")
    req = Request(API + "/channels", headers={"Accept": "application/json"})
    with urlopen(req, timeout=30) as r:
        all_ch = json.loads(r.read().decode("utf-8"))
    print(f"Total: {len(all_ch)}")

    # 加载黑名单
    black_list = load_black_list()

    # 筛选：仅保留 1080P+ 且不在黑名单中的频道
    skipped_lowres = 0
    skipped_black = 0
    cctv_candidates = {}
    sat_candidates = {}

    for c in all_ch:
        name = c.get("name", "")
        url = c.get("url", "")
        if not url or is_dead(url) or is_lan_or_ipv6(url):
            continue
        # 分辨率过滤：仅保留 1080P+
        if not is_1080p_or_above(name):
            skipped_lowres += 1
            continue
        # 黑名单过滤
        if url in black_list:
            skipped_black += 1
            continue
        t = match(name, CCTV)
        if t:
            if t not in cctv_candidates or rank(c) > rank(cctv_candidates[t]):
                cctv_candidates[t] = c
        t = match(name, SAT)
        if t:
            if t not in sat_candidates or rank(c) > rank(sat_candidates[t]):
                sat_candidates[t] = c

    if skipped_lowres:
        print(f"[过滤] 跳过低于1080p: {skipped_lowres}")
    if skipped_black:
        print(f"[过滤] 黑名单屏蔽: {skipped_black}")

    # 按用户顺序排列
    ordered_cctv = {k: cctv_candidates.get(k) for k in CCTV if k in cctv_candidates}
    ordered_sat = {k: sat_candidates.get(k) for k in SAT if k in sat_candidates}

    # TCP 测试
    to_test = {}
    for k, v in ordered_cctv.items():
        to_test[k] = v
    for k, v in ordered_sat.items():
        to_test[k] = v
    print(f"Testing {len(to_test)} channels...")
    tcp = tcp_test(to_test)

    # 输出
    lines = []
    lines.append("📺央视频道,#genre#")
    for t in CCTV:
        c = cctv_candidates.get(t)
        if c:
            ms = tcp.get(t, -1)
            info = f" [TCP:{ms}ms]" if ms > 0 else f" [TCP:FAIL]"
            lines.append(f"{t},{c['url']}{info}")

    lines.append("")
    lines.append("📡卫视频道,#genre#")
    for t in SAT:
        c = sat_candidates.get(t)
        if c:
            ms = tcp.get(t, -1)
            info = f" [TCP:{ms}ms]" if ms > 0 else f" [TCP:FAIL]"
            lines.append(f"{t},{c['url']}{info}")

    # 纯播放列表版本（无调试信息，#未找到的频道直接跳过不显示）
    clean = []
    clean.append("📺央视频道,#genre#")
    for t in CCTV:
        c = cctv_candidates.get(t)
        if c:
            clean.append(f"{t},{c['url']}")
    clean.append("")
    clean.append("📡卫视频道,#genre#")
    for t in SAT:
        c = sat_candidates.get(t)
        if c:
            clean.append(f"{t},{c['url']}")

    output = "\n".join(lines)
    clean_output = "\n".join(clean)

    with open(OUT, "w", encoding="utf-8") as f:
        f.write(output)
    with open(CLEAN, "w", encoding="utf-8") as f:
        f.write(clean_output)

    cctv_ok = sum(1 for t in CCTV if t in cctv_candidates)
    sat_ok = sum(1 for t in SAT if t in sat_candidates)
    tcp_ok = sum(1 for v in tcp.values() if v > 0)

    print()
    print(f"央视: {cctv_ok}/{len(CCTV)}  卫视: {sat_ok}/{len(SAT)}  TCP通: {tcp_ok}/{len(to_test)}")
    print(f"详细结果: {OUT}")
    print(f"纯净版本: {CLEAN}")

    # 列出未找到的
    cctv_miss = [t for t in CCTV if t not in cctv_candidates]
    sat_miss = [t for t in SAT if t not in sat_candidates]
    if cctv_miss:
        print(f"央视缺失: {', '.join(cctv_miss)}")
    if sat_miss:
        print(f"卫视缺失: {', '.join(sat_miss)}")

if __name__ == "__main__":
    main()
