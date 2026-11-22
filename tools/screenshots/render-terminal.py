#!/usr/bin/env python3
"""Bir komut ciktisini (ANSI renkli) terminal gorunumlu HTML'e cevirir.

    komut 2>&1 | python3 render-terminal.py "pencere basligi" "$ komut" > out.html
    node screenshot-html.js out.html docs/images/x.png
"""
import html
import re
import sys

title, cmd = sys.argv[1], sys.argv[2]
raw = sys.stdin.read()
COLORS = {"31": "#ff6b6b", "32": "#69db7c", "33": "#ffd43b", "36": "#66d9e8"}


def ansi_to_html(s):
    out, open_span = [], False
    for part in re.split(r"(\x1b\[[0-9;]*m)", s):
        m = re.match(r"\x1b\[([0-9;]*)m", part)
        if m:
            if open_span:
                out.append("</span>")
                open_span = False
            codes = [c for c in m.group(1).split(";") if c]
            color = next((COLORS[c] for c in codes if c in COLORS), None)
            if color:
                out.append('<span style="color:%s">' % color)
                open_span = True
        else:
            out.append(html.escape(part.replace("\r", "")))
    if open_span:
        out.append("</span>")
    return "".join(out)


print("""<!doctype html><html><head><meta charset="utf-8"><style>
body{margin:0;background:#f0f2f5;padding:24px;font-family:-apple-system,Segoe UI,sans-serif}
.win{background:#1e1f29;border-radius:10px;box-shadow:0 8px 30px rgba(0,0,0,.25);overflow:hidden;display:inline-block;min-width:900px}
.bar{background:#2d2f3b;padding:10px 14px;color:#c0c4d6;font-size:13px}
.dot{display:inline-block;width:12px;height:12px;border-radius:50%%;margin-right:6px}
pre{margin:0;padding:16px 20px;color:#e6e6e6;font:13px/1.5 "DejaVu Sans Mono",Menlo,Consolas,monospace;white-space:pre}
.cmd{color:#69db7c}
</style></head><body><div class="win"><div class="bar">
<span class="dot" style="background:#ff5f56"></span><span class="dot" style="background:#ffbd2e"></span><span class="dot" style="background:#27c93f"></span>
&nbsp; %s</div><pre><span class="cmd">%s</span>
%s</pre></div></body></html>""" % (html.escape(title), html.escape(cmd), ansi_to_html(raw)))
