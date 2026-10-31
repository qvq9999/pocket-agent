# -*- coding: utf-8 -*-
"""AI 产出文件 · 对话底部快捷方式 —— 界面/交互效果图（仅视觉稿，不改 app 代码）。

产出：mockups/file-shortcut/
  fd1-single.html   单文件
  fd2-multi.html    多文件（横滚）
  fd3-chooser.html  点击后系统「打开方式」
  fd4-noapp.html    没有可用应用
  fd5-failed.html   生成失败 / 文件已不在
  overview.html     五帧对照 + 交互流程
运行：python build_file_shortcut.py
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_sidebar_ia as B          # noqa: E402  配色 / 机身 / 状态栏 / 图标
import build_composer_attach as C     # noqa: E402  输入区样式与控件

OUT = os.path.join(HERE, "file-shortcut")

# 补两个本稿要用的图标（check / warn），基础调色板和图标集来自 build_sidebar_ia
B.PATHS.update({
    "check": '<path d="M4.6 12.6l5.2 5.2L19.4 6.4"/>',
    "warn": ('<circle cx="12" cy="12" r="8.4"/><path d="M12 7.6v5.6"/>'
             '<circle cx="12" cy="16.4" r=".9"/>'),
})

# ── 文件类型色：按"一眼分清"排序，不做花哨渐变 ──
TYPES = {
    "DOCX": ("#E8F0FE", "#12508F"),
    "XLSX": ("#E7F4EC", "#1E7B4D"),
    "PDF":  ("#FDECEC", "#B3261E"),
    "MD":   ("#F1F3F6", "#5F6368"),
    "IMG":  ("#F4EEFB", "#6B3FA0"),
}

CSS = C.CSS + r"""
  /* ── 消息区（本稿自己排，方便放工具卡与产出卡）── */
  .chat-body{flex:1;overflow:hidden;padding:0 16px}
  .tcard{border:1px solid var(--line);border-radius:12px;background:#fff;padding:9px 11px;
    margin:2px 0 10px 30px}
  .tcard .h{display:flex;align-items:center;gap:7px;font-size:12.5px;font-weight:600;color:var(--ink)}
  .tcard .h .ok{color:var(--ok)}
  .tcard .h .bad{color:var(--bad)}
  .tcard .p{font-size:10.5px;color:var(--hint);margin-top:3px;
    font-family:"Cascadia Code",Consolas,monospace;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .tcard.fail{border-color:#EFD5D3;background:#FFFBFB}

  /* ── 产出卡：贴在输入区上方 ── */
  .outbar{flex:none;padding:0 12px 8px}
  .ocard{border:1px solid var(--line);border-radius:14px;background:var(--card);
    padding:9px 10px;box-shadow:0 1px 2px rgba(15,23,42,.04)}
  .ocard .hd{display:flex;align-items:center;gap:6px;padding:0 2px 7px;font-size:10.5px;
    font-weight:700;letter-spacing:.2px;color:var(--muted)}
  .ocard .hd .n{margin-left:auto;font-weight:400;color:var(--faint)}
  .chips{display:flex;flex-wrap:wrap;gap:7px}
  .fchip{display:flex;align-items:center;gap:7px;height:34px;padding:0 9px 0 5px;border-radius:10px;
    background:var(--surface-alt);max-width:150px;flex:none}
  .fchip .nm{font-size:12px;color:var(--ink);overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
  .fchip .sz{font-size:9.5px;color:var(--faint);margin-left:2px;flex:none}
  .fchip.more{background:transparent;border:1px dashed var(--line);color:var(--hint);
    font-size:11.5px;padding:0 10px}

  /* 单文件：一整行，右侧给"打开"提示 */
  .frow{display:flex;align-items:center;gap:11px;padding:2px 2px}
  .frow .t{flex:1;min-width:0}
  .frow .t .nm{font-size:13.5px;font-weight:600;color:var(--ink);overflow:hidden;
    white-space:nowrap;text-overflow:ellipsis}
  .frow .t .sub{font-size:10.5px;color:var(--hint);margin-top:2px}
  .frow .go{flex:none;width:28px;height:28px;border-radius:50%;background:var(--accent-soft);
    display:grid;place-items:center;color:var(--accent)}

  .fico{border-radius:9px;display:grid;place-items:center;font-size:9px;font-weight:700;
    letter-spacing:.4px;flex:none}

  /* 失效项 */
  .fchip.gone{background:transparent;border:1px dashed var(--line);opacity:.7}
  .fchip.gone .nm{color:var(--faint);text-decoration:line-through}
  .gone-tag{font-size:9px;color:var(--faint);border:1px solid var(--line);border-radius:5px;
    padding:1px 3px;flex:none;margin-left:2px}
  .fchip.gone .nm{max-width:76px}

  /* 提示条（Bad tone，和 app 的 NoticeBar 同一套） */
  .notice{flex:none;margin:0 12px 8px;border:1px solid #EDD6D4;background:var(--bad-soft);
    border-radius:12px;padding:9px 11px;display:flex;gap:8px;align-items:flex-start}
  .notice .tx{font-size:11px;line-height:16px;color:var(--bad)}
  .notice .tx b{font-weight:700}

  /* ── 系统「打开方式」弹层 ── */
  .dim{position:absolute;inset:0;background:rgba(15,18,24,.42);z-index:6}
  .sheet{position:absolute;left:0;right:0;bottom:0;background:#F7F8FA;border-radius:26px 26px 0 0;
    z-index:7;padding:8px 18px 22px;box-shadow:0 -8px 30px rgba(15,23,42,.18)}
  .sheet .grab{width:34px;height:4px;border-radius:2px;background:#D7DBE2;margin:2px auto 12px}
  .sheet .ttl{font-size:15px;font-weight:600;color:#1B1C1E}
  .sheet .sub{font-size:11.5px;color:#5F6368;margin-top:2px}
  .agrid{display:flex;justify-content:space-between;margin-top:16px}
  .app{width:64px;text-align:center}
  .app .ic{width:54px;height:54px;border-radius:15px;margin:0 auto;display:grid;place-items:center;
    font-size:17px;font-weight:700;color:#fff;box-shadow:0 2px 6px rgba(15,23,42,.12)}
  .app .lb{font-size:10px;color:#3C4043;margin-top:6px;line-height:1.25}
  .sheet .row2{display:flex;align-items:center;gap:8px;margin-top:18px;padding-top:14px;
    border-top:1px solid #E3E5EA;font-size:12px;color:#3C4043}
  .sheet .row2 .sp{margin-left:auto;display:flex;align-items:center;gap:6px;color:#8A9099;font-size:11px}
  .toggle{width:34px;height:20px;border-radius:10px;background:#D7DBE2;position:relative}
  .toggle.on{background:#1A73E8}
  .toggle::after{content:"";position:absolute;left:2px;top:2px;width:16px;height:16px;border-radius:50%;
    background:#fff}
  .toggle.on::after{left:16px}
"""


def fico(ext, size=28):
    bg, fg = TYPES.get(ext, ("#F1F3F6", "#5F6368"))
    return ('<div class="fico" style="background:%s;color:%s;width:%dpx;height:%dpx">%s</div>'
            % (bg, fg, size, size, ext))


def msg_u(text):
    return '<div class="msg-u">%s</div>' % text


def msg_a(html):
    return '<div class="msg-a">%s</div>' % html


def tcard(title, path, state="ok"):
    icon = B.ic("check", 13, "#1E7B4D", 2.4) if state == "ok" else B.ic("warn", 13, "#B3261E", 2.2)
    cls = "tcard" + (" fail" if state != "ok" else "")
    return ('<div class="%s"><div class="h">%s<span class="%s">%s</span></div>'
            '<div class="p">%s</div></div>'
            % (cls, icon, "ok" if state == "ok" else "bad", title, path))


def outbar_single(name, ext, sub="12.4 KB · 点击选择打开方式"):
    return ('<div class="outbar"><div class="ocard">'
            '<div class="frow">%s<div class="t"><div class="nm">%s</div>'
            '<div class="sub">%s</div></div>'
            '<div class="go">%s</div></div></div></div>'
            % (fico(ext, 34), name, sub, B.ic("chev-right", 14, B.C_ACCENT)))


def outbar_multi(items, extra=0):
    chips = "".join(
        '<div class="fchip">%s<span class="nm">%s</span><span class="sz">%s</span></div>'
        % (fico(ext, 24), name, size) for name, ext, size in items
    )
    if extra:
        chips += '<div class="fchip more">+%d</div>' % extra
    return ('<div class="outbar"><div class="ocard">'
            '<div class="hd">%s<span>本对话产出</span><span class="n">%d 个文件</span></div>'
            '<div class="chips">%s</div></div></div>'
            % (B.ic("folder", 12, B.C_MUTED, 2.0), len(items) + extra, chips))


def outbar_gone():
    return ('<div class="outbar"><div class="ocard">'
            '<div class="hd">%s<span>本对话产出</span><span class="n">2 个文件</span></div>'
            '<div class="chips">'
            '<div class="fchip">%s<span class="nm">汇报提纲.md</span><span class="sz">2.1 KB</span></div>'
            '<div class="fchip gone">%s<span class="nm">旧版报价.xlsx</span>'
            '<span class="gone-tag">已删</span></div>'
            '</div></div></div>'
            % (B.ic("folder", 12, B.C_MUTED, 2.0), fico("MD", 24), fico("XLSX", 24)))


def shell(body, tail, extra=""):
    """顶栏 + 消息区 + [提示条/产出卡] + 输入区"""
    return (
        '<div class="chat">'
        '<div class="topbar">'
        '<div class="ibtn">%s</div><div class="tb-title">Pocket Agent</div>'
        '<div class="tb-right"><div class="plus30">%s</div>'
        '<div class="chip">%s<span>resume</span>%s</div></div></div>'
        '<div class="chat-body">%s</div>'
        '%s%s%s</div>'
        % (B.ic("menu", 20, 1.9, "#1B1C1E"), B.ic("plus", 16, 2.3, "#1B1C1E"),
           B.ic("folder", 13, 1.9, B.C_MUTED), B.ic("chev-down", 11, 2.6, B.C_MUTED),
           body, tail, extra, C.composer_now())
    )


def phone(inner):
    return '<div class="phone">%s%s</div>' % (B.statusbar(), inner)


# ══════════ ① 单文件 ══════════
def fd1():
    body = (msg_u("按这份简历改一版，把项目经历提到前面")
            + tcard("写入文件", "resume/面试稿-改.docx · +38 −12 行")
            + msg_a("改好了：项目经历提到教育背景之前，技能里删掉了重复的“熟练”前缀。"))
    return phone(shell(body, outbar_single("面试稿-改.docx", "DOCX")))


# ══════════ ② 多文件 ══════════
def fd2():
    body = (msg_u("把这三份材料各整理一版")
            + msg_a("整理完了，三份都在 resume/ 下面。")
            + tcard("写入文件", "resume/面试稿.docx")
            + tcard("写入文件", "resume/报价汇总.xlsx")
            + tcard("写入文件", "resume/复盘.md"))
    items = [("面试稿.docx", "DOCX", "12.4K"), ("报价汇总.xlsx", "XLSX", "8.1K"),
             ("复盘.md", "MD", "2.6K")]
    return phone(shell(body, outbar_multi(items)))


# ══════════ ③ 系统「打开方式」 ══════════
def fd3():
    body = (msg_u("按这份简历改一版")
            + tcard("写入文件", "resume/面试稿-改.docx")
            + msg_a("改好了。"))
    sheet = ('<div class="dim"></div>'
             '<div class="sheet">'
             '<div class="grab"></div>'
             '<div class="ttl">打开方式</div>'
             '<div class="sub">面试稿-改.docx · 选择要使用的应用</div>'
             '<div class="agrid">'
             '<div class="app"><div class="ic" style="background:#E8453C">W</div>'
             '<div class="lb">WPS Office</div></div>'
             '<div class="app"><div class="ic" style="background:#2B579A">M</div>'
             '<div class="lb">Word</div></div>'
             '<div class="app"><div class="ic" style="background:#5F6368">%s</div>'
             '<div class="lb">文本查看</div></div>'
             '<div class="app"><div class="ic" style="background:#F1F3F6;color:#5F6368">%s</div>'
             '<div class="lb">其他应用</div></div>'
             '</div>'
             '<div class="row2">%s<span>仅此一次 / 始终</span>'
             '<span class="sp">默认打开方式<span class="toggle on"></span></span></div>'
             '</div>'
             % ("T", "···", B.ic("info", 13, "#8A9099", 2.0)))
    return phone(shell(body, outbar_single("面试稿-改.docx", "DOCX")) + sheet)


# ══════════ ④ 没有可用应用 ══════════
def fd4():
    body = (msg_u("生成一份 PDF 报告")
            + tcard("写入文件", "report/周报.pdf")
            + msg_a("报告写好了，但手机上好像没有能打开 PDF 的阅读器。"))
    notice = ('<div class="notice">%s<div class="tx"><b>没有能打开 .pdf 的应用。</b>'
              '装一个阅读器后再点一次，或用「分享」把文件发到别的设备。</div></div>'
              % B.ic("warn", 13, "#B3261E", 2.2))
    return phone(shell(body, notice + outbar_single("周报.pdf", "PDF", "486 KB · 无可用应用")))


# ══════════ ⑤ 生成失败 ══════════
def fd5():
    body = (msg_u("把这份表按月份拆开")
            + tcard("写入失败", "sheets/拆分结果.xlsx · 原文件被其他应用占用", state="fail")
            + msg_a("这次没写成：目标文件正被别的应用打开。关掉它我再试一次。"))
    return phone(shell(body, outbar_gone()))


# ══════════ 总览 ══════════
def page(title, body, w, h, note=""):
    return ('<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>%s</title>'
            '<style>%s\n body{width:%dpx;height:%dpx;overflow:hidden}%s</style></head>'
            '<body>%s</body></html>' % (title, CSS, w, h, note, body))


def overview():
    def cap(no, t, sub):
        return ('<div class="c"><div class="n">%s</div><div class="ct">%s</div>'
                '<div class="cs">%s</div></div>' % (no, t, sub))

    return page(
        "AI 产出文件 · 对话底部快捷方式",
        '<div class="hd"><div class="h1">AI 产出文件：对话底部的快捷方式</div>'
        '<div class="h2">只在"这一轮真的写出了文件"时出现；一个文件一整行，多个文件横滑；'
        '点击走系统「打开方式」，我们不做内置预览器</div></div>'
        '<div class="row">'
        '<div class="col">%s%s</div><div class="col">%s%s</div><div class="col">%s%s</div>'
        '</div>'
        '<div class="row">'
        '<div class="col">%s%s</div><div class="col">%s%s</div><div class="col"></div>'
        '</div>'
        % (fd1(), cap("①", "单个文件", "整行：图标 + 文件名 + 大小，右侧箭头提示可点"),
           fd2(), cap("②", "多个文件", "同一张卡里横滑；标题行给总数，末尾 +N 兜底"),
           fd3(), cap("③", "点击后 · 系统弹层", "ACTION_VIEW + 读权限，交给系统「打开方式」"),
           fd4(), cap("④", "没有可用应用", "红条说明原因 + 给两条出路，不静默失败"),
           fd5(), cap("⑤", "生成失败 / 文件已删", "失败不产出快捷方式；已删的项划线标注")),
        1440, 2180,
        note="""
  .hd{padding:40px 40px 0}
  .h1{font-size:25px;font-weight:700}
  .h2{font-size:13px;color:#5F6368;margin-top:7px;line-height:1.5}
  .row{display:flex;gap:34px;padding:22px 40px 0}
  .col{display:flex;flex-direction:column}
  .c{padding:12px 2px 0;width:411px}
  .c .n{font-size:11px;font-weight:700;color:#A2A8B0}
  .c .ct{font-size:14.5px;font-weight:600;margin-top:4px}
  .c .cs{font-size:11.5px;color:#5F6368;margin-top:4px;line-height:1.55}
""")


def main():
    os.makedirs(OUT, exist_ok=True)
    files = {
        "fd1-single.html": ("单文件", fd1()),
        "fd2-multi.html": ("多文件", fd2()),
        "fd3-chooser.html": ("系统打开方式", fd3()),
        "fd4-noapp.html": ("没有可用应用", fd4()),
        "fd5-failed.html": ("生成失败", fd5()),
    }
    for name, (title, body) in files.items():
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
            f.write(page(title, body, 411, 891))
    with open(os.path.join(OUT, "overview.html"), "w", encoding="utf-8") as f:
        f.write(overview())
    print("written:", OUT)


if __name__ == "__main__":
    main()
