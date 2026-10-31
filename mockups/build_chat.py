# -*- coding: utf-8 -*-
"""对话页视觉稿 v3：厂商图标头像 + 三组输入区控件（就地弹出层）。
运行：python build_chat.py  → 产出 chat-v3.html
"""

import os
import re

# ═══════════════ 厂商图标 ═══════════════
BRAND = {
    'openai':    '#0F0F0F',
    'gemini':    '',          # 自带渐变，不覆盖 fill
    'anthropic': '#D97757',
    'deepseek':  '#4D6BFE',
    'ollama':    '#16191D',
    'qwen':      '',          # path 内已有 fill
}
_cache, _uid = {}, [0]


def _load(slug):
    if slug not in _cache:
        raw = open(os.path.join('assets', slug + '.svg'), encoding='utf-8').read()
        raw = re.sub(r'<title>.*?</title>', '', raw, flags=re.S)
        m = re.search(r'<svg[^>]*viewBox="([^"]+)"[^>]*>(.*)</svg>', raw, flags=re.S)
        _cache[slug] = (m.group(1), m.group(2))
    return _cache[slug]


def glyph(slug, size=15):
    vb, inner = _load(slug)
    _uid[0] += 1
    n = _uid[0]
    for _id in set(re.findall(r'id="([^"]+)"', inner)):
        inner = inner.replace('id="%s"' % _id, 'id="%s_%d"' % (_id, n))
        inner = inner.replace('url(#%s)' % _id, 'url(#%s_%d)' % (_id, n))
    fill = BRAND.get(slug, 'currentColor')
    return ('<svg width="%d" height="%d" viewBox="%s" fill="%s" '
            'xmlns="http://www.w3.org/2000/svg">%s</svg>' % (size, size, vb, fill, inner))


def av(slug, size=15, cls='av'):
    return '<div class="%s">%s</div>' % (cls, glyph(slug, size))


def mono_av(text, size=13, cls='av'):
    return '<div class="%s mono" style="font-size:%dpx">%s</div>' % (cls, size, text)


# ═══════════════ 样式 ═══════════════
CSS = r"""
  :root{
    --ink:#1B1C1E; --muted:#5F6368; --hint:#8A9099;
    --line:#E3E5EA; --line-soft:#EDEFF3; --surface:#F7F8FA;
    --accent:#1A73E8; --accent-soft:#E8F0FE;
    --ok:#1E7B4D; --ok-soft:#E7F4EC;
    --warn:#8A5A00; --warn-soft:#FDF3E0;
    --bad:#B3261E; --bad-soft:#FDECEC;
  }
  *{box-sizing:border-box;-webkit-font-smoothing:antialiased}
  html,body{margin:0}
  body{background:#F1F3F5;padding:32px;display:flex;flex-wrap:wrap;gap:28px;align-content:flex-start;
    width:1792px;font-family:"Segoe UI","Microsoft YaHei UI","Microsoft YaHei",system-ui,sans-serif;color:var(--ink)}
  body.single{padding:0;background:#fff;width:411px}
  body.single .cap{display:none}
  .cap{font-size:13px;font-weight:600;color:#8A9099;letter-spacing:.04em;margin:0 0 10px 6px}
  .phone{position:relative;width:411px;height:891px;background:#fff;border-radius:34px;overflow:hidden;
    display:flex;flex-direction:column;box-shadow:0 0 0 1px #E1E4E8,0 16px 40px rgba(15,23,42,.10)}

  .statusbar{height:44px;flex:none;position:relative;z-index:6;display:flex;align-items:center;
    justify-content:space-between;padding:0 26px 0 28px;font-size:13.5px;font-weight:600}
  .sb-r{display:flex;align-items:center;gap:5px}
  .sb-bat{width:22px;height:11px;border:1.4px solid var(--ink);border-radius:3px;position:relative}
  .sb-bat::after{content:"";position:absolute;left:1.4px;top:1.4px;bottom:1.4px;width:13px;background:var(--ink);border-radius:1px}
  .sb-bat::before{content:"";position:absolute;right:-3.4px;top:3px;width:2px;height:5px;background:var(--ink);border-radius:0 1px 1px 0}

  .topbar{height:58px;flex:none;display:flex;align-items:center;gap:6px;padding:0 12px 0 6px}
  .iconbtn{width:40px;height:40px;flex:none;border-radius:50%;display:grid;place-items:center;color:#3C4043}
  .ttl{font-size:15px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0}
  .chip{display:inline-flex;align-items:center;gap:6px;height:32px;padding:0 11px;border-radius:999px;font-size:13px;white-space:nowrap}
  .chip.ws{background:#F1F3F6;color:#3C4043;font-weight:500;flex:none}
  .spacer{flex:1}
  .dot{width:6px;height:6px;border-radius:50%;background:#1E9E5A;flex:none}

  .body{flex:1;min-height:0;overflow:hidden;padding:2px 16px 0}
  .u-msg{max-width:80%;background:#EDF1F9;border-radius:20px 20px 6px 20px;padding:11px 15px;
    font-size:14.5px;line-height:1.55;margin-left:auto}

  .ai-row{display:flex;gap:10px;margin-top:16px}
  .av{width:26px;height:26px;flex:none;border-radius:50%;background:#F7F8FA;border:1px solid #ECEEF2;
    display:grid;place-items:center;overflow:hidden}
  .av.mono{font-weight:700;color:#5F6368;letter-spacing:-.02em}
  .ai-col{flex:1;min-width:0}
  .mname{font-size:10.5px;font-weight:700;letter-spacing:.02em;color:#9298A1;margin:2px 0 3px}
  .ai-text{font-size:14.5px;line-height:1.62}

  .tcard{margin:11px 0 0 36px;border:1px solid var(--line);border-radius:15px;overflow:hidden;background:#fff}
  .tc-head{display:flex;align-items:center;gap:9px;padding:9px 11px;background:#FAFBFC;border-bottom:1px solid var(--line-soft)}
  .tc-ic{width:22px;height:22px;flex:none;border-radius:6px;background:var(--accent-soft);color:var(--accent);display:grid;place-items:center;font-size:10.5px;font-weight:700}
  .tc-name{font-size:12.5px;font-weight:600;font-family:"Cascadia Code",Consolas,monospace}
  .tc-path{font-size:12px;color:var(--hint);font-family:"Cascadia Code",Consolas,monospace;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .badge{margin-left:auto;font-size:10.5px;font-weight:600;padding:2.5px 7px;border-radius:999px;white-space:nowrap}
  .badge.ok{background:var(--ok-soft);color:var(--ok)}
  .badge.warn{background:var(--warn-soft);color:var(--warn)}
  .diff{font-family:"Cascadia Code",Consolas,monospace;font-size:12px;line-height:1.72;padding:7px 0}
  .diff div{display:flex;padding:0 10px 0 0;white-space:pre}
  .diff .ln{width:26px;flex:none;text-align:right;color:#B4BAC2;margin-right:9px}
  .diff .add{background:#EAF6EF;color:#17603C}
  .diff .del{background:#FCEEEE;color:#A3241B}
  .diff .ctx{color:#5F6368}
  .tc-foot{display:flex;align-items:center;gap:6px;padding:8px 11px;border-top:1px solid var(--line-soft);font-size:11.5px;color:var(--hint)}
  .tc-foot b{color:#3C4043;font-weight:600}

  .summary{margin:8px 0 0 36px;border:1px solid var(--line);border-radius:14px;background:#F7F9FC;
    padding:11px 12px;display:flex;align-items:center;gap:9px}
  .summary .txt{font-size:12.5px;line-height:1.5}
  .summary .txt b{font-weight:600}
  .plus{color:var(--ok);font-family:"Cascadia Code",Consolas,monospace;font-weight:600}
  .minus{color:var(--bad);font-family:"Cascadia Code",Consolas,monospace;font-weight:600}
  .btn-ghost{margin-left:auto;flex:none;border:1px solid #D5D9E0;background:#fff;border-radius:999px;
    padding:6px 12px;font-size:12px;font-weight:600;color:#3C4043;display:inline-flex;align-items:center;gap:5px}

  .qrow{display:flex;gap:8px;margin:11px 0 0 36px;flex-wrap:wrap}
  .qchip{border:1px solid #D5D9E0;border-radius:999px;padding:6px 12px;font-size:12.5px;font-weight:600;color:#3C4043;background:#fff}
  .qchip.hi{border-color:#C9DCF7;background:#F5F9FF;color:#12508F}

  .composer{flex:none;padding:6px 12px 10px}
  .chiprow{display:flex;align-items:center;gap:7px;padding:0 0 7px}
  .comp-box{display:flex;align-items:center;gap:9px;border:1px solid #D8DCE1;border-radius:26px;
    padding:8px 9px;box-shadow:0 1px 2px rgba(0,0,0,.04)}
  .comp-input{flex:1;padding:6px 0;font-size:14.5px;color:#9AA0A6;line-height:1.35}
  .comp-plus{width:30px;height:30px;flex:none;border-radius:50%;background:#F1F3F6;display:grid;place-items:center;color:#3C4043}
  .comp-send{width:32px;height:32px;flex:none;border-radius:50%;background:var(--accent);display:grid;place-items:center;color:#fff;
    box-shadow:0 1px 4px rgba(26,115,232,.3)}
  .cchip{height:29px;display:inline-flex;align-items:center;gap:5px;padding:0 8px 0 9px;border:1px solid transparent;
    border-radius:999px;font-size:11.5px;font-weight:600;color:#3C4043;background:#F1F3F6;white-space:nowrap;flex:none}
  .cchip .cv{margin-left:1px;opacity:.75}
  .cchip.mdl{background:#EAF1FD;color:#12508F}
  .cchip.permb{background:#E9F4EF;color:#1E6B50}
  .cchip.risk{background:#FDF3E0;color:#8A5A00}
  .cchip.off{color:#B0B5BC;background:#F7F8FA}
  .cchip.off .cv{opacity:.4}
  .comp-hint{display:flex;align-items:center;justify-content:center;gap:6px;padding:8px 0 0;font-size:10.5px;color:#A2A8B0}
  .sep{width:1px;height:9px;background:#DDE1E6}

  .pop{position:absolute;right:14px;bottom:121px;width:322px;background:#fff;border-radius:16px;
    border:1px solid #E3E5EA;box-shadow:0 10px 34px rgba(15,23,42,.18);padding:6px;z-index:5}
  .pop-hd{padding:9px 10px 7px;font-size:10.5px;font-weight:700;letter-spacing:.05em;color:#A2A8B0}
  .pitem{display:flex;align-items:flex-start;gap:9px;padding:9px 10px;border-radius:11px}
  .pitem.c{align-items:center}
  .pitem.sel{background:var(--accent-soft)}
  .pitem .radio{margin-top:1px;width:16px;height:16px}
  .pitem .nm{font-size:13px;font-weight:600}
  .pitem .dsc{font-size:10.5px;color:var(--hint);margin-top:2px;line-height:1.45}
  .pitem .id{font-size:10px;color:var(--hint);font-family:"Cascadia Code",Consolas,monospace;margin-top:2px;
    overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .pbadge{margin-left:auto;font-size:10px;font-weight:600;padding:2.5px 7px;border-radius:6px;
    background:#F1F3F6;color:#5F6368;white-space:nowrap;flex:none;align-self:center}
  .pbadge.hl{background:#EAF1FD;color:#12508F}
  .pfoot{padding:8px 10px 4px;font-size:10px;color:#A2A8B0;line-height:1.5;border-top:1px solid var(--line-soft);margin-top:4px}
  .radio{width:18px;height:18px;flex:none;border-radius:50%;border:2px solid #C6CBD2}
  .radio.on{border-color:var(--accent);background:var(--accent);box-shadow:inset 0 0 0 3px #fff}

  .page{flex:1;min-height:0;overflow:hidden;padding:0 16px}
  .irow{display:flex;align-items:center;gap:11px;padding:11px 2px;border-bottom:1px solid var(--line-soft)}
  .irow .nm{font-size:13.5px;font-weight:600}
  .irow .sub{font-size:10.5px;color:var(--hint);margin-top:2px;font-weight:400}
  .sizes{display:flex;align-items:center;gap:9px;margin-left:auto;flex:none}
  .av.lg{width:30px;height:30px;border-radius:50%}
  .av.sm{width:20px;height:20px}
  .lic{font-size:9px;font-weight:600;color:#8A9099;background:#F4F6F9;border-radius:4px;padding:1px 5px;margin-left:6px;
    vertical-align:1px}
  .sub2{font-size:10px;font-weight:700;letter-spacing:.05em;color:#A2A8B0;margin-bottom:6px}
  .rule{font-size:11.5px;line-height:1.55;color:#5F6368;margin-bottom:5px;padding-left:1px}
  .rule b{color:#1B1C1E;font-weight:600}
  .hintbox{display:flex;gap:8px;background:#F4F8FE;border:1px solid #DCE8FA;border-radius:12px;
    padding:10px 11px;font-size:11px;line-height:1.55;color:#2D4A6B;margin:13px 0 0}
"""

CHEV = ('<svg class="cv" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
        'stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>')
IW = ('width="13" height="13" viewBox="0 0 24 24" fill="none" stroke-width="1.9" '
      'stroke-linecap="round" stroke-linejoin="round"')

STATUS = '''<div class="statusbar"><span>14:06</span><div class="sb-r">
<svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><rect x="0" y="8" width="3" height="4" rx=".8"/><rect x="4.3" y="5.6" width="3" height="6.4" rx=".8"/><rect x="8.6" y="3.2" width="3" height="8.8" rx=".8"/><rect x="12.9" y="0" width="3" height="12" rx=".8" opacity=".35"/></svg>
<svg width="15" height="12" viewBox="0 0 15 12" fill="currentColor"><path d="M7.5 10.6 5.2 8.3a3.3 3.3 0 0 1 4.6 0l-2.3 2.3Zm0-4.2a6.1 6.1 0 0 0-4.3 1.8L1.8 6.8a8 8 0 0 1 11.4 0l-1.4 1.4a6.1 6.1 0 0 0-4.3-1.8Zm0-4a9.9 9.9 0 0 0-7 2.9L.1 3.9a11.8 11.8 0 0 1 14.8 0l-1.4 1.4a9.9 9.9 0 0 0-6-2.9Z"/></svg>
<div class="sb-bat"></div></div></div>'''

FOLDER = ('<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#5F6368" stroke-width="1.9" '
          'stroke-linejoin="round"><path d="M3 7.5A1.5 1.5 0 0 1 4.5 6h4l2 2.5h7A1.5 1.5 0 0 1 19 10v7.5A1.5 1.5 0 0 1 17.5 19h-13A1.5 1.5 0 0 1 3 17.5Z"/></svg>')

TOPBAR = ('''<div class="topbar">
<div class="iconbtn"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><path d="M3.5 6.5h17M3.5 12h17M3.5 17.5h17"/></svg></div>
<div class="ttl">整理 notes.md 的待办</div>
<div class="spacer"></div>
<div class="chip ws">''' + FOLDER + '''web/</div>
</div>''')

TOPBAR_ALT = ('''<div class="topbar">
<div class="iconbtn"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><path d="m15 5-7 7 7 7"/></svg></div>
<div class="ttl">模型图标</div>
<div class="spacer"></div>
</div>''')


# ═══════════════ 消息单元 ═══════════════
def user(text):
    return '<div class="u-msg">%s</div>' % text


def ai(slug, name, inner, label=True):
    a = mono_av('?', 13) if not slug else av(slug)
    nm = '<div class="mname">%s</div>' % name if label else ''
    return '<div class="ai-row">%s<div class="ai-col">%s%s</div></div>' % (a, nm, inner)


def toolcard(name, path, badge, diff, foot):
    return ('''<div class="tcard">
  <div class="tc-head"><div class="tc-ic">&lt;/&gt;</div><div class="tc-name">%s</div>
  <div class="tc-path">%s</div><div class="badge ok">%s</div></div>%s
  <div class="tc-foot">%s<span class="spacer"></span>完整 diff ›</div>
</div>''' % (name, path, badge, diff, foot))


DIFF1 = '''<div class="diff">
    <div class="ctx"><span class="ln">12</span>## 待办</div>
    <div class="del"><span class="ln">13</span>- TODO 接入设置页的模型切换</div>
    <div class="add"><span class="ln">13</span>+ DONE 接入设置页的模型切换</div>
    <div class="del"><span class="ln">14</span>- TODO 补 workspace 索引失效逻辑</div>
    <div class="add"><span class="ln">14</span>+ DONE 补 workspace 索引失效逻辑</div>
  </div>'''

SUMMARY = '''<div class="summary">
  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="#5F6368" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z"/><path d="M14 3v5h5"/></svg>
  <div class="txt">本次变更 <b>1 个文件</b>　<span class="plus">+9</span> <span class="minus">−3</span></div>
  <button class="btn-ghost"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7v6h6"/><path d="M3.5 13a9 9 0 1 0 2-9.5L3 7"/></svg>撤销</button>
</div>'''


def body(items):
    return '<div class="body">%s</div>' % ''.join(items)


BODY_MAIN = body([
    user('把 notes.md 里的 TODO 改成 DONE，顺手把遗留的 FIXME 汇总到文件末尾'),
    ai('openai', 'GPT-5', '<div class="ai-text">先读一下这个文件。</div>'),
    toolcard('replace_in_file', 'notes.md', '已应用', DIFF1, '<b>3 处替换</b> · 48ms · 备份已就绪'),
    ai('openai', 'GPT-5', '<div class="ai-text">改好了。另外发现 4 处 FIXME，已汇总到文件末尾的「遗留问题」小节。</div>', label=False),
    SUMMARY,
    ai('openai', 'GPT-5', '<div class="ai-text">要不要把这 4 处 FIXME 拆成可勾选的清单？</div>', label=False),
    '<div class="qrow"><div class="qchip hi">拆成清单</div><div class="qchip">只看不改</div><div class="qchip">不用了</div></div>',
])

BODY_MIX = body([
    user('把 notes.md 里的 TODO 改成 DONE'),
    ai('openai', 'GPT-5', '<div class="ai-text">先读一下这个文件。</div>'),
    toolcard('replace_in_file', 'notes.md', '已应用', DIFF1, '<b>3 处替换</b> · 48ms · 备份已就绪'),
    ai('openai', 'GPT-5', '<div class="ai-text">改好了。另外发现 4 处 FIXME。</div>', label=False),
    user('换成 Gemini 再看看这个目录还有什么要收拾的'),
    ai('gemini', 'Gemini 3 Pro', '<div class="ai-text">扫了一遍 web/，三类问题：<br>'
       '· 4 个 .log 超过 30 天<br>· 2 份 package-lock 各留了旧备份<br>· assets/ 里有 7 张没用到的图</div>'),
    ai('gemini', 'Gemini 3 Pro', '<div class="ai-text">要我按「日志归档、锁文件只留最新、无用图进回收站」做吗？</div>', label=False),
    '<div class="qrow"><div class="qchip hi">按这个来</div><div class="qchip">只清日志</div><div class="qchip">先列清单</div></div>',
])


# ═══════════════ 输入区 ═══════════════
def chip_model(name):
    return ('<div class="cchip mdl"><span class="dot" style="width:5px;height:5px"></span>%s %s</div>'
            % (name, CHEV))


def chip_think(label, off=False):
    icon = ('<svg %s stroke="currentColor"><path d="m12 3.4 1.6 4.5 4.5 1.6-4.5 1.6L12 15.6l-1.6-4.5L5.9 9.5l4.5-1.6z"/>'
            '<path d="m18.2 15 .7 1.9 1.9.7-1.9.7-.7 1.9-.7-1.9-1.9-.7 1.9-.7z"/></svg>' % IW)
    cls = 'cchip off' if off else 'cchip'
    return '<div class="%s">%s 思考·%s%s</div>' % (cls, icon, label, CHEV)


def chip_perm(label, mode='confirm'):
    icon = '<svg %s stroke="currentColor"><path d="M12 3 5 6v6c0 4.4 3 7.4 7 9 4-1.6 7-4.6 7-9V6Z"/></svg>' % IW
    cls = {'confirm': 'cchip permb', 'readonly': 'cchip', 'allow': 'cchip risk'}.get(mode, 'cchip permb')
    return '<div class="%s">%s %s%s</div>' % (cls, icon, label, CHEV)


def composer(model='gpt-5', think='中', perm='需确认', perm_mode='confirm'):
    names = {'gpt-5': 'GPT-5', 'gemini-3-pro': 'Gemini 3 Pro', 'deepseek-v3': 'DeepSeek V3'}
    return '''<div class="composer">
  <div class="chiprow">%s%s%s</div>
  <div class="comp-box">
    <div class="comp-plus"><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg></div>
    <div class="comp-input">继续问，或让它改文件</div>
    <div class="comp-send"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5"/><path d="m5 12 7-7 7 7"/></svg></div>
  </div>
  <div class="comp-hint"><span>自主：单轮工具</span><div class="sep"></div><span>12.4k / 128k tokens</span></div>
</div>''' % (chip_model(names.get(model, model)), chip_think(think), chip_perm(perm, perm_mode))


def phone(body_html, comp_html, overlay='', top=TOPBAR):
    return '<div class="phone">%s%s%s%s%s</div>' % (STATUS, top, body_html, comp_html, overlay)


def frame(n, cap, inner):
    return '<div class="frame" data-n="%s"><div class="cap">%s</div>%s</div>' % (n, cap, inner)


# ═══════════════ 弹出层 ═══════════════
POP_MODELS = '''<div class="pop">
<div class="pop-hd">切换模型 · 右侧为该模型实际支持的思考档位</div>
<div class="pitem c sel"><div class="radio on"></div><div style="flex:1;min-width:0"><div class="nm">GPT-5</div><div class="id">api.openai.com/v1 · gpt-5</div></div><div class="pbadge hl">思考 4 档</div></div>
<div class="pitem c"><div class="radio"></div><div style="flex:1;min-width:0"><div class="nm">Gemini 3 Pro</div><div class="id">googleapis.com · gemini-3-pro</div></div><div class="pbadge">思考 2 档</div></div>
<div class="pitem c"><div class="radio"></div><div style="flex:1;min-width:0"><div class="nm">DeepSeek V3</div><div class="id">api.deepseek.com · deepseek-chat</div></div><div class="pbadge">无思考档位</div></div>
<div class="pitem c"><div class="radio"></div><div style="flex:1;min-width:0"><div class="nm">Qwen3 32B（本机）</div><div class="id">192.168.1.12:11434 · 局域网</div></div><div class="pbadge">思考 2 档</div></div>
<div class="pfoot">切换后思考档位与工具能力按新模型重置</div>
</div>'''

POP_T4 = '''<div class="pop">
<div class="pop-hd">思考程度 · GPT-5 支持 4 档</div>
<div class="pitem"><div class="radio"></div><div><div class="nm">最小</div><div class="dsc">几乎不推理，最快。适合纯改写、格式整理</div></div></div>
<div class="pitem"><div class="radio"></div><div><div class="nm">低</div><div class="dsc">轻量推理，日常问答</div></div></div>
<div class="pitem sel"><div class="radio on"></div><div><div class="nm">中</div><div class="dsc">平衡，该模型默认档位</div></div></div>
<div class="pitem"><div class="radio"></div><div><div class="nm">高</div><div class="dsc">最慢最准，多文件改造</div></div></div>
<div class="pfoot">档位由模型能力决定，不支持的档位不会出现</div>
</div>'''

POP_T2 = '''<div class="pop">
<div class="pop-hd">思考程度 · Gemini 3 Pro 支持 2 档</div>
<div class="pitem sel"><div class="radio on"></div><div><div class="nm">低</div><div class="dsc">默认档位，响应更快</div></div></div>
<div class="pitem"><div class="radio"></div><div><div class="nm">高</div><div class="dsc">更充分的推理，复杂任务更稳</div></div></div>
<div class="pfoot">该模型不支持关闭思考，所以没有「关 / 最小」档</div>
</div>'''

POP_PERM = '''<div class="pop">
<div class="pop-hd">访问权限 · 仅作用于当前会话</div>
<div class="pitem"><div class="radio"></div><div><div class="nm">只读</div><div class="dsc">只能读与搜。写、删、移动工具直接从工具表移除</div></div></div>
<div class="pitem sel"><div class="radio on"></div><div><div class="nm">需确认</div><div class="dsc">写入与删除每次都弹确认并展示 diff</div></div></div>
<div class="pitem"><div class="radio"></div><div><div class="nm">全部允许</div><div class="dsc">写与删自动执行。仍拍快照、进回收站，可整组撤销</div></div></div>
<div class="pfoot">越界路径、备份失败、文件被外部改动，这三种情况即使「全部允许」也硬拦截</div>
</div>'''


# ═══════════════ 图标规范页 ═══════════════
def irow(slug, name, sub, lic):
    a_lg = (mono_av('?', 13, 'av lg') if not slug else av(slug, 17, 'av lg'))
    a_sm = (mono_av('?', 10, 'av sm') if not slug else av(slug, 11, 'av sm'))
    return ('''<div class="irow"><div style="min-width:0"><div class="nm">%s<span class="lic">%s</span></div>
    <div class="sub">%s</div></div><div class="sizes">%s%s</div></div>''' % (name, lic, sub, a_lg, a_sm))


SPEC = ('<div class="page" style="padding-top:6px">'
        '<div style="font-size:11.5px;line-height:1.6;color:#5F6368;margin:0 0 2px">'
        '助手头像 = 该条回复所用模型的厂商标识。同一次会话里换了模型，旧消息仍保留原图标。</div>'
        '<div style="display:flex;align-items:center;font-size:9.5px;letter-spacing:.04em;color:#B0B5BC;'
        'padding:10px 2px 4px;border-bottom:1px solid #EDEFF3"><span>厂商 / 来源</span>'
        '<span style="margin-left:auto">30dp　20dp</span></div>'
        + irow('openai', 'OpenAI', 'GPT-5 / GPT-5 mini', 'LobeHub')
        + irow('gemini', 'Google Gemini', 'Gemini 3 Pro / Flash', 'simple-icons')
        + irow('anthropic', 'Anthropic', 'Claude Sonnet 4.5', 'simple-icons')
        + irow('deepseek', 'DeepSeek', 'DeepSeek V3 / R1', 'simple-icons')
        + irow('qwen', 'Qwen', 'Qwen3 32B（本地）', 'LobeHub')
        + irow('ollama', 'Ollama', '本机 / 局域网模型', 'simple-icons')
        + irow('', '未知厂商', '回退：模型名首字母标', '内置')
        + '<div class="hintbox">'
          '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#3C6FA8" stroke-width="2" '
          'stroke-linecap="round" style="margin-top:1.5px;flex:none"><circle cx="12" cy="12" r="9"/>'
          '<path d="M12 16v-5M12 8h.01"/></svg>'
          '<div>图标仅用于标注「这条回复由谁生成」，不代表与厂商有合作或背书。'
          '每个模型也可在设置里上传自定义 PNG 覆盖内置图标。</div></div>'
        '<div style="margin-top:16px"><div class="sub2">尺寸</div>'
        '<div class="rule">消息流头像 <b>26dp</b>，图标 <b>15dp</b>；列表紧凑态 <b>20dp / 11dp</b>。</div>'
        '<div class="rule">图标始终等比居中，不做拉伸、不加内边框。</div>'
        '<div class="rule">单色标（OpenAI / Anthropic / DeepSeek / Ollama）跟随品牌色，深色模式下自动提亮 12%。</div>'
        '</div>'
        '<div style="margin-top:14px"><div class="sub2">不要这样做</div>'
        '<div class="rule">✕ 把图标放大到撑满圆形（失去留白，多个图标并排会糊成一团）</div>'
        '<div class="rule">✕ 给图标再加一层白底方块，会出现「方框套圆」的双层边</div>'
        '</div>'
        '</div>')


# ═══════════════ 组帧 ═══════════════
frames = [
    frame(1, '01 · 头像换成模型厂商图标，每条首句带模型名',
          phone(BODY_MAIN, composer('gpt-5', '中'))),
    frame(2, '02 · 会话中换模型：前后消息各留原图标',
          phone(BODY_MIX, composer('gemini-3-pro', '低'))),
    frame(3, '03 · 点「模型」：就地弹出，标出各模型档位数',
          phone(BODY_MAIN, composer('gpt-5', '中'), POP_MODELS)),
    frame(4, '04 · 点「思考」：GPT-5 四档',
          phone(BODY_MAIN, composer('gpt-5', '中'), POP_T4)),
    frame(5, '05 · 同一控件切 Gemini：只剩 低 / 高',
          phone(BODY_MAIN, composer('gemini-3-pro', '低'), POP_T2)),
    frame(6, '06 · 点「权限」：只读 / 需确认 / 全部允许',
          phone(BODY_MAIN, composer('gpt-5', '中', '需确认'), POP_PERM)),
    frame(7, '07 · 图标规范：来源、两种尺寸与回退',
          phone(SPEC, '', '', TOPBAR_ALT)),
]

HTML = '''<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<title>对话页 v3 · 厂商图标头像</title><style>%s</style></head><body>
%s
<script>
  const only = new URLSearchParams(location.search).get('only');
  if (only){document.body.classList.add('single');
    document.querySelectorAll('.frame').forEach(f=>{if(f.dataset.n!==only)f.remove();});}
</script></body></html>''' % (CSS, '\n'.join(frames))

open('chat-v3.html', 'w', encoding='utf-8').write(HTML)
print('written', len(HTML))
