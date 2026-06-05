#!/usr/bin/env python3
import json, os
from datetime import datetime

# Load data
with open('channels.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# Stats
total = len(data)
available = sum(1 for ch in data if ch.get('available'))
categories = {}
for ch in data:
    cat = ch.get('category', '未知')
    categories[cat] = categories.get(cat, 0) + 1

sorted_data = sorted(data, key=lambda x: x.get('latencyMs') if x.get('latencyMs') else 999999)

# Build HTML
html = f'''<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>IPTV Channels</title>
<style>
body {{ font-family: Arial; margin: 20px; }}
table {{ border-collapse: collapse; width: 100%; }}
th, td {{ border: 1px solid #ddd; padding: 8px; text-align: left; }}
th {{ background: #4CAF50; color: white; }}
tr:nth-child(even) {{ background: #f2f2f2; }}
</style>
</head>
<body>
<h1>IPTV Channels Report</h1>
<p>Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
<h2>Statistics</h2>
<ul>
<li>Total: {total}</li>
<li>Available: {available}</li>
<li>Categories: {len(categories)}</li>
</ul>

<h2>Top 50 by Latency</h2>
<table>
<tr><th>Name</th><th>Latency</th><th>Bandwidth</th><th>Category</th></tr>
'''

for ch in sorted_data[:50]:
    html += f"<tr><td>{ch.get('name','')}</td><td>{ch.get('latencyMs','N/A')} ms</td><td>{ch.get('bandwidthKbps','N/A')} Kbps</td><td>{ch.get('category','unknown')}</td></tr>\n"

html += '''</table>
</body>
</html>'''

with open('channels_report.html', 'w', encoding='utf-8') as f:
    f.write(html)

print('Generated: channels_report.html')
