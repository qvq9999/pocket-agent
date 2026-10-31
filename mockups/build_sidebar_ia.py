# -*- coding: utf-8 -*-
"""侧边栏信息架构优化 · 示意效果图（仅视觉稿，不改 app 代码）。

产出（mockups/sidebar-ia/）：
  f1-drawer-before.html  优化前：抽屉 = 会话列表 + 6 行功能入口
  f2-drawer-after.html   优化后：抽屉 = 只会话切换，底部单行「设置」
  f3-settings.html       优化后：设置成为唯一功能中枢
  f4-models.html         从设置进入的功能页（返回回到设置）
  overview.html          四联对照组 + 跳转流程图
运行：python build_sidebar_ia.py
"""

import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "sidebar-ia")

# ══════════════ 图标（24 网格，描边 2，与 Glyph 的视觉重量对齐）══════════════
PATHS = {
    "menu": '<path d="M4 7h16M4 12h16M4 17h16"/>',
    "plus": '<path d="M12 5v14M5 12h14"/>',
    "search": '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.6-3.6"/>',
    "close": '<path d="M6 6l12 12M18 6L6 18"/>',
    "chev-down": '<path d="M6 9l6 6 6-6"/>',
    "chev-right": '<path d="M9 5l7 7-7 7"/>',
    "back": '<path d="M15 5l-7 7 7 7"/>',
    "cube": ('<path d="M12 3l8 4.4v9.2L12 21l-8-4.4V7.4L12 3z"/>'
             '<path d="M12 12l8-4.4M12 12v9M12 12L4 7.6"/>'),
    "sparkle": ('<path d="M12 3.2l1.9 5.3 5.3 1.9-5.3 1.9L12 17.6l-1.9-5.3L4.8 10.4l5.3-1.9L12 3.2z"/>'
                '<path d="M18.6 15.4l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8.8-2z"/>'),
    "sliders": ('<path d="M3.4 7.6h5.2M13 7.6h7.6M3.4 16.4h8.4M16.2 16.4h4.4"/>'
                '<circle cx="10.8" cy="7.6" r="2.2"/><circle cx="14" cy="16.4" r="2.2"/>'),
    # 齿轮走 feather 的 settings 路径：之前的「圆 + 八根芒刺」和「主题（太阳）」撞图形
    "settings": ('<circle cx="12" cy="12" r="3.1"/>'
                 '<path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0'
                 '-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0'
                 '-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3'
                 'a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1'
                 '2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09'
                 'a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06'
                 'a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0'
                 '-1.51 1z"/>'),
    "download": '<path d="M12 4v10.5M8 11l4 4 4-4M4.6 19.4h14.8"/>',
    "trash": ('<path d="M4 7h16M9.4 7V4.8h5.2V7M6.4 7l1 13h9.2l1-13"/>'
              '<path d="M10.2 11v5.6M13.8 11v5.6"/>'),
    "shield": '<path d="M12 3.2l7 2.8v5.6c0 4.1-2.9 7.1-7 8.8-4.1-1.7-7-4.7-7-8.8V6L12 3.2z"/>',
    "theme": ('<circle cx="12" cy="12" r="4.2"/><path d="M12 2.8v2.2M12 19v2.2M2.8 12H5M19 12h2.2'
              'M5.5 5.5l1.6 1.6M16.9 16.9l1.6 1.6M18.5 5.5l-1.6 1.6M7.1 16.9l-1.6 1.6"/>'),
    "info": '<circle cx="12" cy="12" r="8.4"/><path d="M12 11v5.4"/><circle cx="12" cy="7.9" r=".9"/>',
    "chart": '<path d="M3.4 20.2h17.2M6.6 20.2v-6.4M11.8 20.2V6.4M17 20.2v-9.2"/>',
    "folder": '<path d="M3.4 7.4a2 2 0 012-2h3.6l2 2.2h7.6a2 2 0 012 2v8.6a2 2 0 01-2 2H5.4a2 2 0 01-2-2V7.4z"/>',
    "more": ('<circle cx="5" cy="12" r="1.7" fill="currentColor" stroke="none"/>'
             '<circle cx="12" cy="12" r="1.7" fill="currentColor" stroke="none"/>'
             '<circle cx="19" cy="12" r="1.7" fill="currentColor" stroke="none"/>'),
    "cpu": ('<rect x="7" y="7" width="10" height="10" rx="2.2"/>'
            '<path d="M10 3.4v2.2M14 3.4v2.2M10 18.4v2.2M14 18.4v2.2'
            'M3.4 10h2.2M3.4 14h2.2M18.4 10h2.2M18.4 14h2.2"/>'),
}


def ic(name, size=15, sw=2.0, color="currentColor", extra=""):
    return (
        '<svg width="%s" height="%s" viewBox="0 0 24 24" fill="none" stroke="%s" '
        'stroke-width="%s" stroke-linecap="round" stroke-linejoin="round" %s '
        'xmlns="http://www.w3.org/2000/svg">%s</svg>'
        % (size, size, color, sw, extra, PATHS[name])
    )


def tint(rgb, a=0.10):
    return 'style="background:rgba(%d,%d,%d,%.2f);color:rgb(%d,%d,%d)"' % (
        rgb[0], rgb[1], rgb[2], a, rgb[0], rgb[1], rgb[2])


ACCENT = (26, 115, 232)
MEDIA = (107, 63, 160)
OK = (30, 123, 77)
MUTED = (95, 99, 104)
BAD = (179, 38, 30)
WARN = (138, 90, 0)

C_ACCENT = "#1A73E8"
C_MEDIA = "#6B3FA0"
C_OK = "#1E7B4D"
C_MUTED = "#5F6368"
C_BAD = "#B3261E"
C_WARN = "#8A5A00"
C_HINT = "#8A9099"
C_FAINT = "#A2A8B0"

# ══════════════ 样式 ══════════════
CSS = r"""
  :root{
    --ink:#1B1C1E; --muted:#5F6368; --hint:#8A9099; --faint:#A2A8B0;
    --line:#E3E5EA; --line-soft:#EDEFF3; --surface:#F7F8FA; --surface-alt:#F1F3F6;
    --accent:#1A73E8; --accent-soft:#E8F0FE; --accent-ink:#12508F;
    --ok:#1E7B4D; --ok-soft:#E7F4EC; --warn:#8A5A00; --warn-soft:#FDF3E0;
    --bad:#B3261E; --bad-soft:#FDECEC; --media:#6B3FA0; --media-soft:#F4EEFB;
  }
  *{box-sizing:border-box;margin:0;padding:0}
  body{background:#fff;color:var(--ink);font-family:"Segoe UI","Microsoft YaHei UI","Microsoft YaHei",system-ui,sans-serif}
  .phone{position:relative;width:411px;height:891px;background:#fff;border-radius:34px;overflow:hidden;
    box-shadow:0 0 0 1px #E1E4E8,0 16px 40px rgba(15,23,42,.10)}

  /* ── 系统状态栏 ── */
  .sb{position:absolute;left:0;right:0;top:0;height:44px;z-index:9;display:flex;align-items:center;
    justify-content:space-between;padding:0 26px 0 28px;font-size:13.5px;font-weight:600;color:var(--ink)}
  .sb-r{display:flex;align-items:center;gap:5px}
  .sb-bat{width:22px;height:11px;border:1.4px solid var(--ink);border-radius:3px;position:relative}
  .sb-bat::after{content:"";position:absolute;left:1.4px;top:1.4px;bottom:1.4px;width:13px;background:var(--ink);border-radius:1px}
  .sb-bat::before{content:"";position:absolute;right:-3.4px;top:3px;width:2px;height:5px;background:var(--ink);border-radius:0 1px 1px 0}

  /* ── 会话页（抽屉下面那一层）── */
  .chat{position:absolute;inset:0;display:flex;flex-direction:column;background:#fff}
  .topbar{flex:none;height:56px;margin-top:44px;padding:0 12px 0 6px;display:flex;align-items:center}
  .ibtn{width:40px;height:40px;border-radius:50%;display:grid;place-items:center;color:var(--ink);flex:none}
  .tb-title{font-size:15px;font-weight:600;margin-left:4px}
  .tb-right{margin-left:auto;display:flex;align-items:center;gap:7px}
  .plus30{width:30px;height:30px;border-radius:50%;background:var(--surface-alt);display:grid;place-items:center;color:var(--ink)}
  .chip{height:29px;display:inline-flex;align-items:center;gap:5px;padding:0 7px 0 9px;border-radius:999px;
    font-size:11.5px;font-weight:600;background:var(--surface-alt);color:var(--muted);white-space:nowrap}
  .chat-body{flex:1;overflow:hidden;padding:0 16px}
  .msg-u{margin:12px 0 12px auto;max-width:250px;background:#DCE8FB;border-radius:16px;padding:10px 13px;
    font-size:14.5px;line-height:1.5}
  .msg-a{margin:0 0 12px 30px;background:#F4F5F7;border-radius:16px;padding:11px 13px;font-size:13.5px;line-height:1.62;color:#2A2C2F}
  .composer{flex:none;padding:6px 12px 12px}
  .comp-row{display:flex;align-items:center;gap:7px;padding-bottom:8px}
  .comp-box{flex:1;display:flex;align-items:center;gap:9px;border:1px solid #D8DCE1;border-radius:26px;
    padding:8px 9px;box-shadow:0 1px 2px rgba(0,0,0,.04)}
  .comp-input{flex:1;font-size:14.5px;color:#9AA0A6;padding:6px 0}
  .comp-plus{width:30px;height:30px;flex:none;border-radius:50%;background:var(--surface-alt);display:grid;place-items:center;color:#3C4043}
  .comp-send{width:32px;height:32px;flex:none;border-radius:50%;background:var(--accent);display:grid;place-items:center;color:#fff}

  .scrim{position:absolute;inset:0;background:rgba(24,28,34,.34);z-index:2}

  /* ── 抽屉 ── */
  .drawer{position:absolute;left:0;top:0;bottom:0;width:302px;background:#fff;border-right:1px solid var(--line);
    display:flex;flex-direction:column;z-index:3}
  .dr-top{height:44px;flex:none}
  .brand{flex:none;display:flex;align-items:center;gap:11px;padding:10px 8px 14px 18px}
  .brand .nm{font-size:15.5px;font-weight:600;line-height:1.24}
  .brand .sub{font-size:11.5px;color:var(--muted);margin-top:1px}
  .brand .x{margin-left:auto;width:40px;height:40px;border-radius:50%;display:grid;place-items:center;color:var(--ink)}
  .newchat{flex:none;margin:0 14px;height:40px;border-radius:50px;background:var(--accent);color:#fff;
    display:flex;align-items:center;justify-content:center;gap:7px;font-size:13.5px;font-weight:600}
  .search{flex:none;margin:10px 14px 0;height:38px;border-radius:50px;background:var(--surface-alt);
    display:flex;align-items:center;gap:8px;padding:0 13px;font-size:13.5px;color:var(--hint)}
  .list{flex:1;overflow:hidden;padding-top:6px}
  .grp{display:flex;align-items:center;gap:5px;padding:8px 14px 6px 13px}
  .grp .cv{color:var(--muted);flex:none}
  .grp .lbl{font-size:11.5px;font-weight:700;letter-spacing:.3px;color:var(--muted)}
  .grp .hint{font-size:10.5px;color:var(--faint);overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
  .grp .cnt{margin-left:auto;font-size:10.5px;color:var(--faint)}
  .sess{margin:1px 8px;padding:9px 12px;border-radius:12px}
  .sess.on{background:var(--accent-soft)}
  .sess .t{font-size:13.5px;line-height:1.35;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .sess.on .t{font-weight:500}
  .sess .tt{font-size:11.5px;line-height:17px;color:var(--hint);margin-top:1px}
  .hair{flex:none;height:1px;background:var(--line-soft);margin:6px 14px}
  .navrow{flex:none;display:flex;align-items:center;gap:11px;margin:1px 8px;padding:9px 10px;border-radius:12px}
  .navrow .ib{width:28px;height:28px;border-radius:9px;display:grid;place-items:center;flex:none}
  .navrow .nm{font-size:13.5px;font-weight:500}
  .navrow .val{margin-left:auto;font-size:11.5px;color:var(--muted);max-width:96px;overflow:hidden;
    white-space:nowrap;text-overflow:ellipsis}
  .dr-bottom{flex:none;height:24px}

  /* 当前会话操作条（导出 / 清空）：作用域是「当前这段会话」，所以贴着会话区，而不是进设置 */
  .curbar{flex:none;display:flex;align-items:center;gap:6px;padding:0 14px 8px}
  .curbar .lab{font-size:11px;color:var(--hint);margin-right:auto}
  .curbtn{height:26px;display:inline-flex;align-items:center;gap:4px;padding:0 9px;border-radius:999px;
    border:1px solid var(--line);background:#fff;font-size:11.5px;font-weight:600;color:var(--muted)}
  .curbtn.danger{color:var(--bad);border-color:#EFD5D3}
  .ell{width:26px;height:26px;border-radius:50%;display:grid;place-items:center;color:var(--muted);flex:none}
  .pop2{position:absolute;right:14px;width:190px;background:#fff;border:1px solid var(--line);
    border-radius:14px;box-shadow:0 10px 30px rgba(15,23,42,.16);padding:5px;z-index:5}
  .p2item{display:flex;align-items:center;gap:9px;padding:9px 10px;border-radius:10px;font-size:13px}
  .p2item .ib{width:24px;height:24px;border-radius:8px;display:grid;place-items:center;flex:none}
  .p2item .v{margin-left:auto;font-size:10.5px;color:var(--faint)}

  /* ── 二级页 ── */
  .page{position:absolute;inset:0;background:#fff;display:flex;flex-direction:column}
  .pbody{flex:1;overflow:hidden;padding:0 16px}
  .slabel{display:flex;align-items:center;font-size:11.5px;font-weight:700;letter-spacing:.3px;
    color:var(--muted);padding:13px 0 6px}
  .slabel .tr{margin-left:auto;font-size:10.5px;color:var(--faint);font-weight:400;letter-spacing:0}
  .card{border:1px solid var(--line);border-radius:15px;overflow:hidden;background:#fff}
  .srow{display:flex;align-items:center;gap:11px;padding:12px 14px}
  .srow .ib{width:28px;height:28px;border-radius:9px;display:grid;place-items:center;flex:none}
  .srow .t{font-size:14px;font-weight:500}
  .srow .v{margin-left:auto;font-size:12.5px;font-weight:500;color:var(--muted);white-space:nowrap;
    overflow:hidden;text-overflow:ellipsis;max-width:170px}
  .srow .cv{color:var(--faint);flex:none;margin-left:auto}
  .srow .v+.cv{margin-left:5px}
  .srow.danger .t{color:var(--bad)}
  .hl{height:1px;background:var(--line-soft)}
  .mrow{display:flex;align-items:center;gap:11px;padding:13px 14px}
  .av{width:30px;height:30px;border-radius:10px;display:grid;place-items:center;font-size:12px;
    font-weight:700;color:#fff;flex:none}
  .mrow .nm{font-size:14px;font-weight:600}
  .mrow .id{font-size:11px;color:var(--hint);font-family:"Cascadia Code",Consolas,monospace;margin-top:2px;
    max-width:210px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
  .badge{margin-left:auto;font-size:10px;font-weight:600;padding:3px 8px;border-radius:7px;
    background:var(--surface-alt);color:var(--muted);flex:none}
  .badge.on{background:var(--accent-soft);color:var(--accent-ink)}
  .ghost{height:30px;padding:0 12px;border-radius:999px;border:1px solid #D5D9E0;display:inline-flex;
    align-items:center;gap:5px;font-size:12px;font-weight:600;color:#3C4043}
  .cap{font-size:10.5px;line-height:15px;color:var(--faint);padding:8px 2px 0}

  /* ── 用量账单页 ── */
  .fin{padding:13px}
  .figs{display:flex;align-items:flex-start}
  .fig{flex:1;text-align:center}
  .fig .l{font-size:10.5px;color:var(--faint)}
  .fig .n{font-size:17px;font-weight:600;margin-top:6px;color:rgba(27,28,30,.82)}
  .fig .n span{font-size:9.5px;font-weight:400;color:var(--faint);margin-left:3px}
  .fig.p .l{font-weight:600;color:var(--muted)}
  .fig.p .n{font-size:25px;color:var(--ink)}
  .fig.p .n span{font-size:10.5px}
  .fdiv{width:1px;height:28px;background:var(--line-soft);margin-top:18px}
  .mrow3{display:flex;gap:6px;margin-top:12px}
  .mcell{flex:1;border-radius:9px;background:var(--surface-alt);padding:8px 10px}
  .mcell .v{font-size:13px;font-weight:600}
  .mcell .l{font-size:10px;color:var(--faint);margin-top:2px}
  .uhead{display:flex;align-items:center;padding:14px 0 8px}
  .uhead .t{font-size:11px;font-weight:700;letter-spacing:.3px;color:var(--muted)}
  .uhead .r{margin-left:auto;font-size:10px;color:var(--faint)}
  .bars{display:flex;align-items:flex-end;justify-content:space-between;height:54px}
  .bar{width:22px;border-radius:4px;background:var(--surface-alt)}
  .bar.on{background:var(--accent)}
  .dayrow{display:flex;justify-content:space-between;margin-top:5px}
  .day{width:22px;text-align:center;font-size:10px;color:var(--faint)}
  .day.on{color:var(--accent);font-weight:600}
  .base{height:1px;background:var(--line-soft)}
  .mbar{margin-top:9px}
  .mbar .tp{display:flex;align-items:center;font-size:10.5px}
  .mbar .tp .nm{color:var(--ink);overflow:hidden;white-space:nowrap;text-overflow:ellipsis;max-width:210px}
  .mbar .tp .v{margin-left:auto;color:var(--muted)}
  .mbar .tp .p{margin-left:6px;font-weight:600}
  .track{height:5px;border-radius:3px;background:var(--surface-alt);margin-top:5px;overflow:hidden}
  .track i{display:block;height:5px;border-radius:3px;background:var(--accent)}
  .ur{display:flex;font-size:10.5px;padding:3px 0}
  .ur .v{margin-left:auto;color:var(--faint)}
  .note{font-size:10.5px;line-height:15px;color:var(--faint);padding:8px 2px 0}
"""


def logo(size=36):
    return (
        '<svg width="%d" height="%d" viewBox="0 0 512 512" fill="none" xmlns="http://www.w3.org/2000/svg">'
        '<defs><linearGradient id="lg" x1="0" y1="0" x2="512" y2="512" gradientUnits="userSpaceOnUse">'
        '<stop stop-color="#5B9BF8"/><stop offset="1" stop-color="#1550C0"/></linearGradient></defs>'
        '<path d="M112 0H400A112 112 0 01512 112V400A112 112 0 01400 512H112A112 112 0 010 400V112A112 112 0 01112 0Z" fill="url(#lg)"/>'
        '<path d="M306 262H400A44 44 0 01444 306V400A44 44 0 01400 444H306A44 44 0 01262 400V306A44 44 0 01306 262Z" fill="#fff"/>'
        '<path d="M308 326H398M308 358H366M308 390H384" stroke="#1550C0" stroke-width="15" stroke-linecap="round"/>'
        '<path d="M196 66Q208 186 328 198Q208 210 196 330Q184 210 64 198Q184 186 196 66Z" fill="#fff"/>'
        '</svg>' % (size, size)
    )


def statusbar():
    return ('<div class="sb"><div>9:41</div><div class="sb-r">'
            '<svg width="17" height="12" viewBox="0 0 17 12" fill="none" stroke="#1B1C1E" stroke-width="1.6" '
            'stroke-linecap="round"><path d="M1 9.6V11M5.2 7V11M9.4 4.2V11M13.6 1.2V11"/></svg>'
            '<svg width="15" height="12" viewBox="0 0 15 12" fill="none" stroke="#1B1C1E" stroke-width="1.5" '
            'stroke-linecap="round"><path d="M1 4.4a9.6 9.6 0 0113 0M3.4 7a6.2 6.2 0 018.2 0"/>'
            '<circle cx="7.5" cy="10" r="1.1" fill="#1B1C1E" stroke="none"/></svg>'
            '<div class="sb-bat"></div></div></div>')


# ══════════════ 会话数据（前后两稿用同一份，保证对比公平）══════════════
GROUPS = [
    ("普通会话", None, [
        ("给 README 补一段安装说明", "2 分钟前", True),
        ("把这个 CSV 按月拆成 12 张表", "1 小时前", False),
        ("排查 adb 忽然连不上的原因", "3 小时前", False),
        ("重写周报里的第三段", "昨天", False),
        ("写个脚本把 PDF 转成 Markdown", "10 月 29 日", False),
        ("确认一下打包用的签名配置", "10 月 27 日", False),
        ("把这段录音转成文字稿", "10 月 26 日", False),
    ]),
    ("工作区", "resume", [
        ("批量重命名面试录音", "昨天", False),
        ("Hugo 主题的暗色适配", "10 月 29 日", False),
        ("压缩 assets 下的大图", "10 月 28 日", False),
        ("这份简历该怎么改", "10 月 25 日", False),
        ("把面试题整理成一张表", "10 月 24 日", False),
    ]),
]


def sess_list(limit_rows=None):
    """limit_rows=None 表示不限制（由 flex 高度自然裁剪）"""
    out, n = [], 0
    for label, hint, rows in GROUPS:
        out.append('<div class="grp"><span class="cv">%s</span><span class="lbl">%s</span>%s'
                   '<span class="cnt">%d</span></div>' % (
                       ic("chev-down", 13, 2.2, C_MUTED), label,
                       ('<span class="hint">%s</span>' % hint) if hint else "",
                       len(rows)))
        for t, tm, on in rows:
            out.append('<div class="sess%s"><div class="t">%s</div><div class="tt">%s</div></div>'
                       % (" on" if on else "", t, tm))
            n += 1
            if limit_rows and n >= limit_rows:
                return "".join(out)
    return "".join(out)


def chat_behind():
    return (
        '<div class="chat">'
        '<div class="topbar">'
        '<div class="ibtn">%s</div><div class="tb-title">Pocket Agent</div>'
        '<div class="tb-right"><div class="plus30">%s</div>'
        '<div class="chip">%s<span>resume</span>%s</div></div>'
        '</div>'
        '<div class="chat-body">'
        '<div class="msg-u">用一句话说清楚这段简历的定位</div>'
        '<div class="msg-a">这段简历的定位可以收成一句：<br>“能独立把一个 Android 端到端的完整功能做完，'
        '包含设备通信、离线存储与协议逆向。”</div>'
        '</div>'
        '<div class="composer"><div class="comp-row">'
        '<div class="comp-box"><div class="comp-plus">%s</div>'
        '<div class="comp-input">发消息</div><div class="comp-send">%s</div></div>'
        '</div></div></div>'
        % (ic("menu", 20, 1.9, "#1B1C1E"), ic("plus", 16, 2.3, "#1B1C1E"),
           ic("folder", 13, 1.9, C_MUTED), ic("chev-down", 11, 2.6, C_MUTED),
           ic("plus", 16, 2.3, "#3C4043"),
           '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.2" '
           'stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5M6 11l6-6 6 6"/></svg>')
    )


def drawer_head():
    return (
        '<div class="dr-top"></div>'
        '<div class="brand">%s<div><div class="nm">Pocket Agent</div>'
        '<div class="sub">4 个模型可用</div></div>'
        '<div class="x">%s</div></div>'
        '<div class="newchat">%s<span>新会话</span></div>'
        '<div class="search">%s<span>搜索会话</span></div>'
        % (logo(36), ic("close", 18, 2.1, "#1B1C1E"), ic("plus", 15, 2.4, "#fff"), ic("search", 15, 2.1, C_HINT))
    )


def navrow(icon, title, tint_rgb, val="", danger=False):
    return ('<div class="navrow%s"><div class="ib" %s>%s</div>'
            '<div class="nm">%s</div>%s</div>'
            % (" danger" if danger else "", tint(tint_rgb), ic(icon, 15, 2.0),
               title, ('<div class="val">%s</div>' % val) if val else ""))


def f1_before():
    return ('<div class="phone">%s%s%s'
            '<div class="scrim"></div>'
            '<div class="drawer">%s<div class="list">%s</div>'
            '<div class="hair"></div>%s%s%s%s%s%s'
            '<div class="dr-bottom"></div></div></div>'
            % (statusbar(), chat_behind(), "",
               drawer_head(), sess_list(),
               navrow("cube", "模型", ACCENT, "4 个"),
               navrow("sparkle", "记忆", MEDIA),
               navrow("sliders", "扩展", OK),
               navrow("settings", "设置", MUTED),
               navrow("download", "导出会话", ACCENT, "Markdown"),
               navrow("trash", "清空当前会话", BAD, danger=True)))


def curbar():
    """当前会话操作条：作用域是这段对话，跟着会话区走，不放进设置"""
    return ('<div class="curbar"><div class="lab">当前会话</div>'
            '<div class="curbtn">%s导出</div>'
            '<div class="curbtn danger">%s清空</div></div>'
            % (ic("download", 12, 2.0), ic("trash", 12, 2.0)))


def settings_row():
    return ('<div class="navrow"><div class="ib" %s>%s</div><div class="nm">设置</div>'
            '<div class="val" style="color:#A2A8B0">%s</div></div>'
            % (tint(MUTED), ic("settings", 15, 2.0), ic("chev-right", 13, 2.2)))


def f2_after():
    """方案 V1：抽屉底部一条「当前会话」操作条 + 单行设置"""
    return ('<div class="phone">%s%s'
            '<div class="scrim"></div>'
            '<div class="drawer">%s<div class="list">%s</div>'
            '<div class="hair"></div>%s<div class="hair"></div>%s'
            '<div class="dr-bottom"></div></div></div>'
            % (statusbar(), chat_behind(), drawer_head(), sess_list(),
               curbar(), settings_row()))


def f5_after_menu():
    """方案 V2：搜索行尾部「⋯」弹出会话操作，零常驻行高"""
    head = drawer_head().replace(
        '<div class="search">%s<span>搜索会话</span></div>'
        % ic("search", 15, 2.1, C_HINT),
        '<div class="search" style="padding-right:5px">%s<span style="flex:1">搜索会话</span>'
        '<div class="ell">%s</div></div>'
        % (ic("search", 15, 2.1, C_HINT), ic("more", 16, 2.0)))
    pop = ('<div class="pop2" style="top:196px">'
           '<div class="p2item"><div class="ib" %s>%s</div><div>导出会话</div>'
           '<div class="v">Markdown</div></div>'
           '<div class="p2item" style="color:#B3261E"><div class="ib" %s>%s</div>'
           '<div>清空当前会话</div></div></div>'
           % (tint(ACCENT), ic("download", 13, 2.0), tint(BAD), ic("trash", 13, 2.0)))
    return ('<div class="phone">%s%s'
            '<div class="scrim"></div>'
            '<div class="drawer">%s<div class="list">%s</div>'
            '<div class="hair"></div>%s%s'
            '<div class="dr-bottom"></div></div></div>'
            % (statusbar(), chat_behind(), head, sess_list(), settings_row(), pop))


def srow(icon, title, tint_rgb, val="", danger=False, chev="chev-right"):
    tail = ('<div class="cv">%s</div>' % ic(chev, 13, 2.2)) if chev else ""
    return ('<div class="srow%s"><div class="ib" %s>%s</div><div class="t">%s</div>%s%s</div>'
            % (" danger" if danger else "", tint(tint_rgb), ic(icon, 15, 2.0), title,
               ('<div class="v">%s</div>' % val) if val else "", tail))


def page_topbar(title, trailing=""):
    return ('<div class="topbar"><div class="ibtn">%s</div><div class="tb-title">%s</div>'
            '<div class="tb-right">%s</div></div>' % (ic("back", 20, 1.9, "#1B1C1E"), title, trailing))


def f3_settings():
    return (
        '<div class="phone">%s<div class="page">%s<div class="pbody">'

        '<div class="slabel">功能</div>'
        '<div class="card">%s<div class="hl"></div>%s<div class="hl"></div>%s</div>'

        # 对话：只留「工具能做多远」的两个策略项。
        # 导出 / 清空是**这段会话**的动作，按作用域归属留在抽屉里，不搬进设置。
        '<div class="slabel">对话</div>'
        '<div class="card">%s<div class="hl"></div>%s</div>'

        '<div class="slabel">外观</div>'
        '<div class="card">%s</div>'

        '<div class="slabel">用量</div>'
        '<div class="card">%s</div>'

        '<div class="slabel">关于</div>'
        '<div class="card">%s<div class="hl"></div>%s</div>'
        '<div style="height:18px"></div>'

        '</div></div></div>'
        % (statusbar(), page_topbar("设置"),
           srow("cube", "模型", ACCENT, "4 个 · deepseek-v3.2"),
           srow("sparkle", "记忆", MEDIA, "12 条习惯 · 3 篇笔记"),
           srow("sliders", "扩展", OK, "Linux 环境 · 已装"),

           srow("shield", "默认访问权限", WARN, "需确认"),
           srow("sliders", "永久允许的工具", MEDIA, "2 个"),

           srow("theme", "主题", ACCENT, "跟随系统"),

           srow("chart", "用量账单", ACCENT, "今日 12.4k tok"),

           srow("info", "版本", MUTED, "1.4.0 (12)", chev=""),
           srow("sparkle", "使用引导", ACCENT, "已完成")))


def f4_models():
    def mrow(letter, bg, name, mid, badge, on=False):
        return ('<div class="mrow"><div class="av" style="background:%s">%s</div>'
                '<div><div class="nm">%s</div><div class="id">%s</div></div>'
                '<div class="badge%s">%s</div></div>' % (bg, letter, name, mid, " on" if on else "", badge))

    return (
        '<div class="phone">%s<div class="page">%s<div class="pbody">'
        '<div class="slabel">已配置<span class="tr">4 个</span></div>'
        '<div class="card">%s<div class="hl"></div>%s<div class="hl"></div>%s<div class="hl"></div>%s</div>'
        '</div></div></div>'
        % (statusbar(), page_topbar("模型", '<div class="ghost">%s添加</div>' % ic("plus", 12, 2.4, "#3C4043")),
           mrow("D", "#4D6BFE", "DeepSeek", "api.deepseek.com", "当前", True),
           mrow("O", "#0F0F0F", "OpenAI", "api.openai.com", "可切换"),
           mrow("A", "#D97757", "Anthropic", "api.anthropic.com", "可切换"),
           mrow("Q", "#615CED", "通义千问", "dashscope.aliyuncs.com", "可切换")))


def f6_usage():
    """用量账单页：原来设置首屏那张大卡搬进来，「清空账单」跟着它作用的数据一起搬"""
    days = [("一", 20, 0), ("二", 31, 0), ("三", 0, 0), ("四", 46, 0),
            ("五", 36, 0), ("六", 54, 1), ("日", 0, 0)]

    def bar(h, on):
        return '<div class="bar%s" style="height:%dpx"></div>' % (" on" if on else "", h)

    def day(l, on):
        return '<div class="day%s">%s</div>' % (" on" if on else "", l)

    def mbar(name, tok, pct, w):
        return ('<div class="mbar"><div class="tp"><div class="nm">%s</div>'
                '<div class="v">%s</div><div class="p">%d%%</div></div>'
                '<div class="track"><i style="width:%d%%"></i></div></div>'
                % (name, tok, pct, w))

    return (
        '<div class="phone">%s<div class="page">%s<div class="pbody">'

        '<div class="slabel">用量账单<span class="tr">累计 96.2k tok</span></div>'
        '<div class="card"><div class="fin">'

        '<div class="figs">'
        '<div class="fig p"><div class="l">今日</div><div class="n">12.4k<span>tok</span></div></div>'
        '<div class="fdiv"></div>'
        '<div class="fig"><div class="l">近 7 天</div><div class="n">38.9k<span>tok</span></div></div>'
        '<div class="fdiv"></div>'
        '<div class="fig"><div class="l">累计</div><div class="n">96.2k<span>tok</span></div></div>'
        '</div>'

        '<div class="mrow3">'
        '<div class="mcell"><div class="v">42 次</div><div class="l">调用</div></div>'
        '<div class="mcell"><div class="v">28 tok/s</div><div class="l">输出速度</div></div>'
        '<div class="mcell"><div class="v">76%%</div><div class="l">缓存命中</div></div>'
        '</div>'

        '<div class="uhead"><div class="t">近 7 天趋势</div><div class="r">今日最高</div></div>'
        '<div class="bars">%s</div><div class="base"></div>'
        '<div class="dayrow">%s</div>'

        '<div class="uhead"><div class="t">主要消耗</div><div class="r">2 个模型</div></div>'
        '%s%s'

        '<div class="uhead"><div class="t">按用途</div></div>'
        '<div class="ur"><div>对话</div><div class="v">61.3k</div></div>'
        '<div class="ur"><div>工具循环</div><div class="v">28.7k</div></div>'
        '<div class="ur"><div>上下文压缩</div><div class="v">6.2k</div></div>'

        '<div class="note">一次模型往返记一行（含工具循环与上下文压缩）。厂商未返回 usage 时按字符估算，'
        '记为 ≈；「—」表示该项厂商没有上报。</div>'
        '</div></div>'

        '<div class="slabel">危险区</div>'
        '<div class="card">%s</div>'
        '<div class="note">清空后今日、近 7 天与累计都会归零，无法恢复。</div>'
        '<div style="height:18px"></div>'

        '</div></div></div>'
        % (statusbar(), page_topbar("用量账单"),
           "".join(bar(h, on) for _, h, on in days),
           "".join(day(l, on) for l, _, on in days),
           mbar("deepseek-chat", "52.8k", 55, 55),
           mbar("gpt-4.1-mini", "31.6k", 33, 33),
           srow("trash", "清空账单", BAD, danger=True, chev="")))



# ══════════════ 跳转流程图 ══════════════
def flow_svg():
    W, H = 1180, 448

    def box(x, y, w, h, title, sub, fill="#fff", stroke="#E3E5EA", tcol="#1B1C1E", scol="#8A9099"):
        s = ('<rect x="%d" y="%d" width="%d" height="%d" rx="14" fill="%s" stroke="%s"/>'
             % (x, y, w, h, fill, stroke))
        s += ('<text x="%d" y="%d" font-size="14.5" font-weight="600" fill="%s">%s</text>'
              % (x + 16, y + (30 if sub else h / 2 + 5), tcol, title))
        if sub:
            s += ('<text x="%d" y="%d" font-size="11.5" fill="%s">%s</text>'
                  % (x + 16, y + 50, scol, sub))
        return s

    def arrow(x1, y1, x2, y2, color="#9AA3AE", dash=""):
        d = ' stroke-dasharray="5 4"' if dash else ""
        return ('<path d="M%d %dH%d" stroke="%s" stroke-width="1.6" fill="none"%s/>'
                '<path d="M%d %d l-7 -4.2 v8.4 z" fill="%s"/>'
                % (x1, y1, x2 - 8, color, d, x2 - 1, y2 if y2 else y1, color))

    def curve(x1, y1, x2, y2, color="#9AA3AE", dash=""):
        d = ' stroke-dasharray="5 4"' if dash else ""
        mx = (x1 + x2) / 2
        return ('<path d="M%d %d C%d %d, %d %d, %d %d" stroke="%s" stroke-width="1.6" fill="none"%s/>'
                '<path d="M%d %d l-8 -4.4 v8.8 z" fill="%s"/>'
                % (x1, y1, mx, y1, mx, y2, x2 - 2, y2, color, d, x2, y2, color))

    s = ['<svg width="%d" height="%d" viewBox="0 0 %d %d" xmlns="http://www.w3.org/2000/svg" '
         'font-family="Segoe UI,Microsoft YaHei,sans-serif">' % (W, H, W, H)]

    # 等级标注
    for i, lab in enumerate(["L0 根", "L1 唯一入口", "L2 功能中枢", "L3 功能页"]):
        s.append('<text x="%d" y="24" font-size="10.5" font-weight="700" letter-spacing=".6" fill="#A2A8B0">%s</text>'
                 % ([40, 300, 560, 890][i], lab))

    # L0
    s.append(box(40, 40, 190, 78, "会话页", "始终在最底层，不参与转场", "#F7F8FA", "#E3E5EA"))
    # L1
    s.append(box(300, 40, 200, 78, "抽屉", "切换 · 搜索 · 当前会话导出/清空", "#fff", "#C9DCF7"))
    # L2
    s.append(box(560, 40, 210, 78, "设置", "唯一的功能入口集合", "#E8F0FE", "#B6D0F5", "#12508F", "#4A6E9E"))
    # L3
    names = [("模型", "4 个 · 当前 deepseek"),
             ("记忆", "12 条习惯 · 3 篇笔记"),
             ("扩展", "Linux 环境 · 已装"),
             ("用量账单", "今日 12.4k tok")]
    ys = [40, 118, 196, 274]
    for (t, sub), y in zip(names, ys):
        s.append(box(890, y, 250, 62, t, sub))

    # 边
    s.append(arrow(230, 79, 300, 79, "#B6D0F5"))
    s.append('<text x="232" y="134" font-size="10.5" fill="#8A9099">右滑 / 点 ☰</text>')
    s.append(arrow(500, 79, 560, 79, "#8AB4F8"))
    s.append('<text x="486" y="134" font-size="10.5" fill="#8A9099">点「设置」</text>')
    for (t, _), y in zip(names, ys):
        s.append(curve(770, 79, 890, y + 31))

    # 回流（虚线）
    s.append('<path d="M1015 340 V378 H120 V122" stroke="#C8B8E6" stroke-width="1.6" fill="none" '
             'stroke-dasharray="5 4"/>')
    s.append('<path d="M120 122 l-4.4 9 h8.8 z" fill="#C8B8E6"/>')
    s.append('<text x="300" y="400" font-size="11" fill="#7C6BA0">'
             '返回逐级退：功能页 → 设置 → 会话页；抽屉全程不动，回去时仍是展开态（搜索词与滚动位置都在）</text>')
    s.append('<line x1="40" y1="414" x2="1140" y2="414" stroke="#EDEFF3" stroke-width="1"/>')
    s.append('<text x="40" y="436" font-size="10.5" fill="#8A9099">'
             '实线＝进入：抽屉 → 设置（瞬时盖满，抽屉不动）　→ 功能页（位移 1/14 屏宽 · 240ms）'
             '　　虚线＝返回：逐级 pop，任何时候抽屉都还是展开的</text>')
    s.append('</svg>')
    return "".join(s)


def page(title, body, w, h, note=""):
    return ('<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>%s</title>'
            '<style>%s\n body{width:%dpx;height:%dpx;overflow:hidden}%s</style></head>'
            '<body>%s</body></html>' % (title, CSS, w, h, note, body))


def overview():
    def cap(no, t, sub):
        return ('<div class="c"><div class="n">%s</div><div class="ct">%s</div>'
                '<div class="cs">%s</div></div>' % (no, t, sub))

    phones = (
        '<div class="row">'
        '<div class="col">%s%s</div>'
        '<div class="col">%s%s</div>'
        '<div class="col">%s%s</div>'
        '<div class="col">%s%s</div>'
        '<div class="col">%s%s</div>'
        '</div>'
        % (f1_before(), cap("①", "现状 · 抽屉", "会话列表 + 6 行功能入口；工作区整组被挤到可视区外"),
           f5_after_menu(), cap("②", "定稿 V2 · 抽屉", "搜索行尾部「⋯」承载导出/清空；底部只剩一行设置"),
           f3_settings(), cap("③", "定稿 · 设置", "功能 / 对话 / 外观 / 用量 / 关于，不再含任何会话动作"),
           f6_usage(), cap("④", "用量账单页（从设置进入）", "原设置首屏大卡搬进来，「清空账单」跟着数据一起搬"),
           f4_models(), cap("⑤", "模型页（同类跳转）", "返回退回设置，而不是退回会话页")))

    return page("侧边栏信息架构优化 · 效果图",
                '<div class="hd"><div class="h1">侧边栏信息架构优化 · 方案 A</div>'
                '<div class="h2">抽屉回归「切换对话」单一职责：模型 / 记忆 / 扩展 / 用量收进设置并可跳转；'
                '导出与清空属于「当前这段会话」，收在抽屉搜索行的「⋯」里（定稿 V2）</div></div>'
                + phones +
                '<div class="hd2"><div class="h3">跳转与返回路径</div>'
                '<div class="h4">抽屉不再承载导航。返回栈由「一级页」升级为真实栈，'
                '因此从设置进功能页、再退回来，落点是设置而不是会话页。</div></div>'
                + '<div class="flow">%s</div>' % flow_svg(),
                2320, 1670,
                note="""
  .hd{padding:44px 40px 4px}
  .h1{font-size:26px;font-weight:700;letter-spacing:.2px}
  .h2{font-size:13.5px;color:#5F6368;margin-top:8px}
  .row{display:flex;gap:34px;padding:22px 40px 0}
  .col{display:flex;flex-direction:column}
  .c{padding:14px 2px 0;width:411px}
  .c .n{font-size:11px;font-weight:700;color:#A2A8B0;letter-spacing:.5px}
  .c .ct{font-size:14.5px;font-weight:600;margin-top:4px}
  .c .cs{font-size:11.5px;color:#5F6368;margin-top:4px;line-height:1.55}
  .hd2{padding:34px 40px 0}
  .h3{font-size:17px;font-weight:700}
  .h4{font-size:12px;color:#5F6368;margin-top:6px}
  .flow{padding:8px 34px 0}
""")


def main():
    os.makedirs(OUT, exist_ok=True)
    files = {
        "f1-drawer-before.html": ("优化前 · 抽屉", f1_before(), 411, 891),
        "f2-drawer-after.html": ("优化后 · 抽屉", f2_after(), 411, 891),
        "f3-settings.html": ("优化后 · 设置", f3_settings(), 411, 891),
        "f4-models.html": ("从设置进入的功能页", f4_models(), 411, 891),
        "f5-drawer-menu.html": ("定稿 V2 · 会话操作菜单", f5_after_menu(), 411, 891),
        "f6-usage.html": ("用量账单页", f6_usage(), 411, 891),
    }
    for name, (title, body, w, h) in files.items():
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
            f.write(page(title, body, w, h))
    with open(os.path.join(OUT, "overview.html"), "w", encoding="utf-8") as f:
        f.write(overview())
    print("written:", OUT)


if __name__ == "__main__":
    main()
