#!/usr/bin/env python3
"""
IPTV 频道检查工具
用法:
  python check_channels.py [--host http://localhost:8080]
"""
import sys, json, argparse
from urllib.request import urlopen, Request
from urllib.error import URLError

def main():
    parser = argparse.ArgumentParser(description="IPTV 频道列表检查工具")
    parser.add_argument("--host", default="http://localhost:8080",
                        help="IPTV Crawler 服务地址 (默认: http://localhost:8080)")
    parser.add_argument("--available", action="store_true",
                        help="仅显示可用频道")
    parser.add_argument("--category", help="按分类筛选")
    parser.add_argument("--search", help="按名称搜索")
    parser.add_argument("--stats", action="store_true",
                        help="显示统计信息")
    args = parser.parse_args()

    base = args.host.rstrip("/") + "/api/iptv"

    try:
        if args.stats:
            data = fetch_json(base + "/stats")
            print(f"总频道: {data['total']}")
            print(f"可用: {data['available']}")
            print(f"分类数: {data['categoryCount']}")
            print(f"分类: {', '.join(data['categories'])}")
            return

        if args.category:
            path = f"/channels/category/{args.category}"
        elif args.search:
            path = f"/channels/search?name={args.search}"
        elif args.available:
            path = "/channels/available"
        else:
            path = "/channels"

        data = fetch_json(base + path)
        print(f"Total channels: {len(data)}")

        # 分类统计
        cats = {}
        for c in data:
            cat = c.get("category", "unknown")
            cats[cat] = cats.get(cat, 0) + 1
        for k, v in sorted(cats.items()):
            print(f"  {k}: {v}")

        print()
        print("Top 10:")
        for c in data[:10]:
            status = "✅" if c.get("available") else "❌"
            name = c.get("name", "?")
            cat = c.get("category", "?")
            lat = c.get("latencyMs") or "-"
            bw = c.get("bandwidthKbps") or "-"
            print(f"  {status} {name} ({cat}) 延迟:{lat}ms 带宽:{bw}Kbps")

    except URLError as e:
        print(f"无法连接到 {base}: {e}", file=sys.stderr)
        sys.exit(1)


def fetch_json(url: str):
    req = Request(url, headers={"Accept": "application/json"})
    with urlopen(req, timeout=10) as resp:
        raw = resp.read().decode("utf-8")
        return json.loads(raw)


if __name__ == "__main__":
    main()
