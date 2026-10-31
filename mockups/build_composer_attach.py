# -*- coding: utf-8 -*-
"""输入区「+」改为三选一菜单（相机 / 相册 / 文件）· 示意效果图。

只出图，不改 app 代码。
产出：mockups/composer-attach/{a1-now,a2-menu,a3-attached,overview}.html + 同名 png

复用 sidebar-ia 那套配色/机身/图标，保证两张稿是同一个视觉语言。
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import build_sidebar_ia as B  # noqa: E402

OUT = os.path.join(HERE, "composer-attach")

# ── 额外图标 ──
B.PATHS.update({
    "camera": ('<path d="M4 8.6a2 2 0 012-2h1.9l1.2-1.8h5.8l1.2 1.8H18a2 2 0 012 2v8.2a2 2 0 01-2 2H6a2 2 0 01-2-2V8.6z"/>'
               '<circle cx="12" cy="12.7" r="3.4"/>'),
    "image": ('<rect x="3.4" y="4.8" width="17.2" height="14.4" rx="2.6"/>'
              '<circle cx="8.6" cy="9.6" r="1.5"/>'
              '<path d="M4 16.6l4.6-4.2 3.2 2.9 3.4-3.6 4.8 4.6"/>'),
    "doc": ('<path d="M6.4 3.4h7.2l5 5v12.2H6.4z"/><path d="M13.4 3.6v5h5"/>'
            '<path d="M9.4 13.2h6M9.4 16.6h4"/>'),
})

CSS = B.CSS + r"""
  /* ── 输入区 ── */
  .composer{flex:none;padding:6px 12px 12px}
  .chiprow{display:flex;align-items:center;gap:7px;padding:0 1px 8px}
  .cchip{height:29px;display:inline-flex;align-items:center;gap:5px;padding:0 8px 0 9px;border-radius:999px;
    font-size:11.5px;font-weight:600;background:var(--surface-alt);color:var(--muted);white-space:nowrap}
  .cchip.mdl{background:#EAF1FD;color:#12508F}
  .cchip.permb{background:#E9F4EF;color:#1E6B50}
  .cbox{border:1px solid #D8DCE1;border-radius:26px;padding:8px 9px}
  .refs{display:flex;align-items:center;gap:6px;padding:1px 1px 8px}
  .rimg{position:relative;width:42px;height:42px;border-radius:9px;overflow:hidden;flex:none;
    box-shadow:0 0 0 1px rgba(15,23,42,.08)}
  .rimg .x{position:absolute;right:3px;top:3px;width:15px;height:15px;border-radius:50%;
    background:rgba(20,24,30,.56);color:#fff;display:grid;place-items:center}
  .rfch{display:flex;align-items:center;gap:6px;height:30px;padding:0 4px 0 9px;border-radius:8px;
    background:var(--surface-alt);font-size:11px;color:var(--ink);max-width:132px}
  .rfch .nm{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
  .irow{display:flex;align-items:center;gap:9px}
  .plusb{width:30px;height:30px;border-radius:50%;background:var(--surface-alt);display:grid;place-items:center;flex:none}
  .plusb.on{background:var(--accent-soft);color:var(--accent)}
  .inp{flex:1;font-size:14.5px;color:var(--hint)}
  .snd{width:32px;height:32px;border-radius:50%;background:var(--accent);display:grid;place-items:center;flex:none}

  /* ── 「+」菜单 ── */
  .attach{margin:0 0 8px 12px;width:238px;background:#fff;border:1px solid var(--line);
    border-radius:16px;padding:6px}
  .aitem{display:flex;align-items:center;gap:10px;padding:8px 9px;border-radius:11px}
  .aitem .ib{width:32px;height:32px;border-radius:10px;display:grid;place-items:center;flex:none}
  .aitem .t{font-size:13.5px;font-weight:600}
  .aitem .d{font-size:10.5px;color:var(--hint);margin-top:1px}
  .afoot{font-size:10px;line-height:15px;color:var(--faint);padding:7px 10px 4px;
    border-top:1px solid var(--line-soft);margin-top:4px}
  .okdot{display:inline-block;width:5px;height:5px;border-radius:50%;background:var(--ok);margin-right:4px;
    vertical-align:middle}
"""


def chat_shell(composer_html, extra=""):
    """对话页外壳：顶栏 + 两条消息 + 输入区（+ 可选浮层）"""
    return (
        '<div class="chat">'
        '<div class="topbar">'
        '<div class="ibtn">%s</div><div class="tb-title">Pocket Agent</div>'
        '<div class="tb-right"><div class="plus30">%s</div>'
        '<div class="chip">%s<span>resume</span>%s</div></div>'
        '</div>'
        '<div class="chat-body">'
        '<div class="msg-u">看看这两张图，把简历里的排版问题挑出来</div>'
        '<div class="msg-a">先发出来我看看。可以直接拍，也可以从相册选，最多一次 4 张。</div>'
        '</div>'
        '%s%s</div>'
        % (B.ic("menu", 20, 1.9, "#1B1C1E"), B.ic("plus", 16, 2.3, "#1B1C1E"),
           B.ic("folder", 13, 1.9, B.C_MUTED), B.ic("chev-down", 11, 2.6, B.C_MUTED),
           extra, composer_html))


def chips():
    return ('<div class="chiprow">'
            '<div class="cchip mdl">deepseek-chat %s</div>'
            '<div class="cchip">思考 关 %s</div>'
            '<div class="cchip permb">需确认 %s</div>'
            '</div>'
            % (B.ic("chev-down", 11, 2.6, "#12508F"),
               B.ic("chev-down", 11, 2.6, B.C_MUTED),
               B.ic("chev-down", 11, 2.6, "#1E6B50")))


def sendbtn():
    return ('<div class="snd"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" '
            'stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5M6 11l6-6 6 6"/>'
            '</svg></div>')


def composer_now():
    return ('<div class="composer">%s<div class="cbox"><div class="irow">'
            '<div class="plusb">%s</div><div class="inp">继续问，或让它改文件</div>%s'
            '</div></div></div>'
            % (chips(), B.ic("plus", 17, 2.2, "#1B1C1E"), sendbtn()))


def composer_menu():
    return ('<div class="composer">%s<div class="cbox"><div class="irow">'
            '<div class="plusb on">%s</div><div class="inp">继续问，或让它改文件</div>%s'
            '</div></div></div>'
            % (chips(), B.ic("plus", 17, 2.2, B.C_ACCENT), sendbtn()))


def composer_attached():
    return ('<div class="composer">%s<div class="cbox">'
            '<div class="refs">'
            '<div class="rimg" style="background:linear-gradient(140deg,#DDE7F6,#B9CCE9)">'
            '<div class="x">%s</div></div>'
            '<div class="rimg" style="background:linear-gradient(140deg,#EFE3DA,#D8C2B2)">'
            '<div class="x">%s</div></div>'
            '<div class="rfch">%s<span class="nm">面试稿.md</span></div>'
            '</div>'
            '<div class="irow"><div class="plusb">%s</div>'
            '<div class="inp" style="color:#1B1C1E">看看这三张的排版</div>%s</div>'
            '</div></div>'
            % (chips(),
               B.ic("close", 9, 2.6, "#fff"), B.ic("close", 9, 2.6, "#fff"),
               B.ic("folder", 12, 1.9, B.C_HINT),
               B.ic("plus", 17, 2.2, "#1B1C1E"), sendbtn()))


def attach_menu():
    def row(icon, tint, title, desc):
        return ('<div class="aitem"><div class="ib" %s>%s</div>'
                '<div><div class="t">%s</div><div class="d">%s</div></div></div>'
                % (B.tint(tint), B.ic(icon, 17, 2.0), title, desc))

    return ('<div class="attach">'
            + row("camera", B.ACCENT, "相机", "拍完直接引用")
            + row("image", B.MEDIA, "相册", "可多选 · 最多 4 张")
            + row("doc", B.OK, "文件", "文本 / 代码 / 文档")
            + '<div class="afoot"><span class="okdot"></span>图片按视觉输入发送 · 当前模型支持视觉</div>'
            '</div>')


def phone(composer, extra=""):
    return ('<div class="phone">%s%s</div>' % (B.statusbar(), chat_shell(composer, extra)))


def a1():
    return phone(composer_now())


def a2():
    return phone(composer_menu(), attach_menu())


def a3():
    return phone(composer_attached())


def page(title, body, w, h, note=""):
    return ('<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>%s</title>'
            '<style>%s\n body{width:%dpx;height:%dpx;overflow:hidden}%s</style></head>'
            '<body>%s</body></html>' % (title, CSS, w, h, note, body))


def overview():
    def cap(no, t, sub):
        return ('<div class="c"><div class="n">%s</div><div class="ct">%s</div>'
                '<div class="cs">%s</div></div>' % (no, t, sub))

    return page("输入区「+」三选一 · 效果图",
                '<div class="hd"><div class="h1">输入区「+」：相机 / 相册 / 文件</div>'
                '<div class="h2">加号从"直接开文件选择器"改成三选一 —— 按来源分，不按文件类型分</div></div>'
                '<div class="row">'
                '<div class="col">%s%s</div>'
                '<div class="col">%s%s</div>'
                '<div class="col">%s%s</div>'
                '</div>'
                % (a1(), cap("①", "现状", "加号只有一个出口：系统文件选择器，选图要先翻到相册目录"),
                   a2(), cap("②", "点加号 · 三选一", "相机 / 相册 / 文件；底部一行说明视觉输入与模型能力"),
                   a3(), cap("③", "选完", "图片以缩略图挂在输入框里，可逐张删除；文件仍是文件名胶囊")),
                1440, 1130,
                note="""
  .hd{padding:40px 40px 0}
  .h1{font-size:25px;font-weight:700}
  .h2{font-size:13px;color:#5F6368;margin-top:7px}
  .row{display:flex;gap:34px;padding:24px 40px 0}
  .col{display:flex;flex-direction:column}
  .c{padding:14px 2px 0;width:411px}
  .c .n{font-size:11px;font-weight:700;color:#A2A8B0}
  .c .ct{font-size:14.5px;font-weight:600;margin-top:4px}
  .c .cs{font-size:11.5px;color:#5F6368;margin-top:4px;line-height:1.55}
""")


def main():
    os.makedirs(OUT, exist_ok=True)
    files = {
        "a1-now.html": ("现状 · 输入区", a1(), 411, 891),
        "a2-menu.html": ("点加号 · 三选一", a2(), 411, 891),
        "a3-attached.html": ("选完 · 已引用", a3(), 411, 891),
    }
    for name, (title, body, w, h) in files.items():
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
            f.write(page(title, body, w, h))
    with open(os.path.join(OUT, "overview.html"), "w", encoding="utf-8") as f:
        f.write(overview())
    print("written:", OUT)


if __name__ == "__main__":
    main()
