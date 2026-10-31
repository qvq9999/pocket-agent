# -*- coding: utf-8 -*-
"""添加模型流程视觉稿：厂商预设 → 填 Key → 识别模型 → 勾选保存 → 可切换。
运行：python build_addmodel.py → add-model.html
"""

import os
import re

BRAND = {
    'openai': '#0F0F0F', 'gemini': '', 'anthropic': '#D97757', 'deepseek': '#4D6BFE',
    'ollama': '#16191D', 'qwen': '', 'xiaomi': '#FF6900', 'kimi': '#16191D',
    'zhipu': '#3859FF', 'openrouter': '#16191D', 'lmstudio': '#4A5568',
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
    return ('<svg width="%d" height="%d" viewBox="%s" fill="%s" xmlns="http://www.w3.org/2000/svg">%s</svg>'
            % (size, size, vb, fill, inner))


def tile(logo=None, letters='', color='#5F6368', size=34, radius=11):
    if logo:
        return ('<div class="tile" style="width:%dpx;height:%dpx;border-radius:%dpx;background:#F7F8FA;'
                'border:1px solid #ECEEF2">%s</div>' % (size, size, radius, glyph(logo, int(size * 0.56))))
    return ('<div class="tile" style="width:%dpx;height:%dpx;border-radius:%dpx;background:%s14;color:%s;'
            'font-size:%dpx">%s</div>' % (size, size, radius, color, color, int(size * 0.36), letters))


CSS = r"""
  :root{
    --ink:#1B1C1E; --muted:#5F6368; --hint:#8A9099;
    --line:#E3E5EA; --line-soft:#EDEFF3;
    --accent:#1A73E8; --accent-soft:#E8F0FE;
    --ok:#1E7B4D; --ok-soft:#E7F4EC;
    --warn:#8A5A00; --warn-soft:#FDF3E0;
    --bad:#B3261E; --bad-soft:#FDECEC;
  }
  *{box-sizing:border-box;-webkit-font-smoothing:antialiased}
  html,body{margin:0}
  body{background:#F1F3F5;padding:32px;display:flex;flex-wrap:wrap;gap:28px;align-content:flex-start;
    width:1353px;font-family:"Segoe UI","Microsoft YaHei UI","Microsoft YaHei",system-ui,sans-serif;color:var(--ink)}
  body.single{padding:0;background:#fff;width:411px}
  body.single .cap{display:none}
  .cap{font-size:13px;font-weight:600;color:#8A9099;letter-spacing:.04em;margin:0 0 10px 6px}
  .phone{position:relative;width:411px;height:891px;background:#fff;border-radius:34px;overflow:hidden;
    display:flex;flex-direction:column;box-shadow:0 0 0 1px #E1E4E8,0 16px 40px rgba(15,23,42,.10)}

  .statusbar{height:44px;flex:none;display:flex;align-items:center;justify-content:space-between;
    padding:0 26px 0 28px;font-size:13.5px;font-weight:600}
  .sb-r{display:flex;align-items:center;gap:5px}
  .sb-bat{width:22px;height:11px;border:1.4px solid var(--ink);border-radius:3px;position:relative}
  .sb-bat::after{content:"";position:absolute;left:1.4px;top:1.4px;bottom:1.4px;width:13px;background:var(--ink);border-radius:1px}
  .sb-bat::before{content:"";position:absolute;right:-3.4px;top:3px;width:2px;height:5px;background:var(--ink);border-radius:0 1px 1px 0}

  .topbar{height:54px;flex:none;display:flex;align-items:center;gap:6px;padding:0 14px 0 6px}
  .iconbtn{width:40px;height:40px;flex:none;border-radius:50%;display:grid;place-items:center;color:#3C4043}
  .ttl{font-size:17px;font-weight:600}
  .spacer{flex:1}
  .sub-ttl{font-size:11px;color:var(--hint);margin-top:1px;font-weight:400}

  .page{flex:1;min-height:0;overflow:hidden;padding:0 16px}
  .tile{flex:none;display:grid;place-items:center;font-weight:700;letter-spacing:-.01em}

  .hint{font-size:11.5px;line-height:1.6;color:#5F6368;background:#F7F9FC;border:1px solid #E8ECF3;
    border-radius:12px;padding:9px 11px;margin:0 0 10px}
  .grp{font-size:10.5px;font-weight:700;letter-spacing:.06em;color:#A2A8B0;margin:11px 0 2px}

  .vrow{display:flex;align-items:center;gap:11px;padding:7px 2px}
  .vrow .nm{font-size:13.5px;font-weight:600}
  .vrow .ep{font-size:10px;color:var(--hint);font-family:"Cascadia Code",Consolas,monospace;margin-top:2px;
    overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .vrow .chev{flex:none;color:#C6CBD2}

  .card{border:1px solid var(--line);border-radius:15px;padding:11px 13px}
  .field{margin-bottom:14px}
  .field label{display:flex;align-items:center;font-size:11.5px;font-weight:600;color:#5F6368;margin-bottom:6px}
  .field label .r{margin-left:auto;font-weight:600;font-size:11px;color:var(--accent)}
  .input{height:46px;border:1px solid #DADCE0;border-radius:12px;display:flex;align-items:center;
    padding:0 12px;font-size:13.5px;gap:8px}
  .input .val{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .input .mono{font-family:"Cascadia Code",Consolas,monospace;font-size:12px}

  .btn-primary{width:100%;height:46px;border-radius:23px;background:var(--accent);color:#fff;
    font-size:14.5px;font-weight:600;display:grid;place-items:center;box-shadow:0 2px 8px rgba(26,115,232,.28)}
  .btn-primary.off{background:#EDEFF2;color:#A8AEB6;box-shadow:none}
  .btn-line{width:100%;height:44px;border-radius:22px;border:1px solid #D5D9E0;background:#fff;
    font-size:13.5px;font-weight:600;color:#3C4043;display:grid;place-items:center}
  .under{font-size:10.5px;color:#A2A8B0;text-align:center;margin-top:9px;line-height:1.6}

  .warnbox{display:flex;gap:8px;background:var(--warn-soft);border:1px solid #F3E2C4;border-radius:12px;
    padding:10px 11px;font-size:11px;line-height:1.55;color:#6B4A0B;margin-bottom:12px}
  .infobox{display:flex;gap:8px;background:#F4F8FE;border:1px solid #DCE8FA;border-radius:12px;
    padding:10px 11px;font-size:11px;line-height:1.55;color:#2D4A6B;margin-bottom:14px}
  .warnbox b,.infobox b{font-weight:600}

  .mhead{display:flex;align-items:center;gap:8px;margin:10px 0 2px}
  .mhead .t{font-size:10.5px;font-weight:700;letter-spacing:.06em;color:#A2A8B0}
  .mhead .n{font-size:10.5px;color:#B0B5BC;margin-left:auto}

  .mrow{display:flex;align-items:flex-start;gap:10px;padding:9px 2px;border-bottom:1px solid var(--line-soft)}
  .cb{width:18px;height:18px;flex:none;border-radius:5px;border:1.8px solid #C6CBD2;margin-top:1px;
    display:grid;place-items:center}
  .cb.on{background:var(--accent);border-color:var(--accent)}
  .cb.off{border-color:#E0E3E8;background:#F7F8FA}
  .mrow .nm{font-size:13px;font-weight:600;font-family:"Cascadia Code",Consolas,monospace}
  .mrow .mta{font-size:10.5px;color:var(--hint);margin-top:3px}
  .mrow.dim .nm{color:#9AA0A6}
  .pill{font-size:9.5px;font-weight:700;padding:1.5px 6px;border-radius:5px;margin-left:7px;vertical-align:1.5px}
  .pill.rec{background:var(--accent-soft);color:#12508F}
  .pill.dead{background:#F1F3F6;color:#8A9099}
  .pill.media{background:#F4EEFB;color:#6B3FA0}

  .mchip{display:inline-flex;align-items:center;height:26px;padding:0 9px;border-radius:8px;
    background:#F1F3F6;font-size:11px;font-weight:600;color:#3C4043;margin:6px 5px 0 0}
  .mchip.cur{background:#EAF1FD;color:#12508F}
  .mchip.ban{background:#F1F3F6;color:#98A0A8}
  .pcard{border:1px solid var(--line);border-radius:15px;padding:12px 13px;margin-bottom:10px}
  .pcard .hd{display:flex;align-items:center;gap:10px}
  .pcard .nm{font-size:14px;font-weight:600}
  .pcard .ep{font-size:10px;color:var(--hint);font-family:"Cascadia Code",Consolas,monospace;margin-top:2px}
  .badge{font-size:10px;font-weight:600;padding:2.5px 7px;border-radius:999px;white-space:nowrap;flex:none}
  .badge.ok{background:var(--ok-soft);color:var(--ok)}
  .badge.warn{background:var(--warn-soft);color:var(--warn)}
  .badge.med{background:#F4EEFB;color:#6B3FA0}
"""

STATUS = '''<div class="statusbar"><span>14:06</span><div class="sb-r">
<svg width="16" height="12" viewBox="0 0 16 12" fill="currentColor"><rect x="0" y="8" width="3" height="4" rx=".8"/><rect x="4.3" y="5.6" width="3" height="6.4" rx=".8"/><rect x="8.6" y="3.2" width="3" height="8.8" rx=".8"/><rect x="12.9" y="0" width="3" height="12" rx=".8" opacity=".35"/></svg>
<svg width="15" height="12" viewBox="0 0 15 12" fill="currentColor"><path d="M7.5 10.6 5.2 8.3a3.3 3.3 0 0 1 4.6 0l-2.3 2.3Zm0-4.2a6.1 6.1 0 0 0-4.3 1.8L1.8 6.8a8 8 0 0 1 11.4 0l-1.4 1.4a6.1 6.1 0 0 0-4.3-1.8Zm0-4a9.9 9.9 0 0 0-7 2.9L.1 3.9a11.8 11.8 0 0 1 14.8 0l-1.4 1.4a9.9 9.9 0 0 0-6-2.9Z"/></svg>
<div class="sb-bat"></div></div></div>'''

BACK = ('<div class="iconbtn"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
        'stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"><path d="m15 5-7 7 7 7"/></svg></div>')
PLUS = ('<div class="iconbtn"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
        'stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg></div>')
CHEV = ('<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" '
        'stroke-linecap="round" stroke-linejoin="round"><path d="m9 6 6 6-6 6"/></svg>')

# 厂商预设：端点均按官方文档核对；agnes 无公开矢量图标，用字母块
VENDORS_MI = [
    ('xiaomi', '小米 MiMo', 'api.xiaomimimo.com/v1'),
    ('deepseek', 'DeepSeek', 'api.deepseek.com/v1'),
    (None, 'Agnes 国内站', 'api.agnes-ai.cn/v1'),
    ('kimi', '月之暗面 Kimi', 'api.moonshot.cn/v1'),
    ('zhipu', '智谱 GLM', 'open.bigmodel.cn/api/paas/v4'),
    ('qwen', '阿里通义', 'dashscope.aliyuncs.com/compatible-mode/v1'),
]
VENDORS_INTL = [
    ('openai', 'OpenAI', 'api.openai.com/v1'),
    ('gemini', 'Google Gemini', 'generativelanguage.googleapis.com/v1beta/openai'),
    ('openrouter', 'OpenRouter', 'openrouter.ai/api/v1'),
]
VENDORS_LOCAL = [
    ('ollama', 'Ollama', '192.168.1.12:11434/v1'),
    ('lmstudio', 'LM Studio', '192.168.1.12:1234/v1'),
]


def vrow(slug, name, ep):
    t = tile(logo=slug) if slug else tile(letters='Ag', color='#6B4FBB')
    return ('<div class="vrow">%s<div style="min-width:0;flex:1"><div class="nm">%s</div>'
            '<div class="ep">%s</div></div><div class="chev">%s</div></div>' % (t, name, ep, CHEV))


def phone(content):
    return '<div class="phone">%s%s</div>' % (STATUS, content)


def frame(n, cap, inner):
    return '<div class="frame" data-n="%s"><div class="cap">%s</div>%s</div>' % (n, cap, inner)


# ══════ 08 厂商预设 ══════
rows_mi = ''.join(vrow(*v) for v in VENDORS_MI)
rows_intl = ''.join(vrow(*v) for v in VENDORS_INTL)
rows_local = ''.join(vrow(*v) for v in VENDORS_LOCAL)

F08 = phone(
    '<div class="topbar">' + BACK + '<div class="ttl">添加模型</div></div>'
    '<div class="page">'
    '<div class="hint">选一个厂商，端点已按官方文档预填。你只需填 API Key，'
    '点「识别模型」自动拉取可用模型。</div>'
    '<div class="grp">国内</div>' + rows_mi +
    '<div class="grp">海外</div>' + rows_intl +
    '<div class="grp">本地 / 局域网</div>' + rows_local +
    '<div style="text-align:center;font-size:12px;font-weight:600;color:#5F6368;padding:12px 0">'
    '没有你的厂商？手动填写端点 ›</div>'
    '</div>')

# ══════ 09 填 Key ══════
F09 = phone(
    '<div class="topbar">' + BACK + '<div><div class="ttl">小米 MiMo</div>'
    '<div class="sub-ttl">OpenAI 兼容协议 · 端点已预填</div></div></div>'
    '<div class="page" style="padding-top:6px">'
    '<div class="field"><label>API Key<span class="r">从控制台复制</span></label>'
    '<div class="input"><div class="val mono">sk-••••••••••••••••••7c2e</div>'
    '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#9AA0A6" stroke-width="1.9" '
    'stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="3" width="11" height="18" rx="2"/>'
    '<path d="M5 8v12"/></svg></div></div>'
    '<div class="field"><label>Base URL<span class="r">修改</span></label>'
    '<div class="input"><div class="val mono">https://api.xiaomimimo.com/v1</div></div></div>'
    '<div class="infobox">'
    '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#3C6FA8" stroke-width="2" '
    'stroke-linecap="round" style="margin-top:1.5px;flex:none"><circle cx="12" cy="12" r="9"/>'
    '<path d="M12 16v-5M12 8h.01"/></svg>'
    '<div>买的是 <b>Token Plan 订阅</b>？key 以 <b>tp-</b> 开头时端点不同，要换成 '
    '<b>token-plan-cn.xiaomimimo.com/v1</b>。'
    '<span style="color:#1A73E8;font-weight:600">切换端点 ›</span></div></div>'
    '<div class="btn-primary">识别模型</div>'
    '<div class="under">点按后请求 GET /v1/models，只读接口，不消耗 token。</div>'
    '<div class="grp" style="margin-top:18px">识别失败会告诉你是哪一类</div>'
    '<div class="card" style="font-size:11.5px;line-height:1.75;color:#5F6368">'
    '<b style="color:#B3261E">401</b>　Key 无效或已过期<br>'
    '<b style="color:#B3261E">404</b>　端点写错，或该厂商没有 /models<br>'
    '<b style="color:#B3261E">400</b>　base URL 里重复了 /v1（拼成 /v1/v1/…）<br>'
    '<b style="color:#8A5A00">超时</b>　网络或代理问题；局域网地址需先授予「附近设备」权限<br>'
    '<b style="color:#5F6368">200 但列表为空</b>　厂商要求按模型 ID 直接调用，改手动添加'
    '</div>'
    '<div class="grp" style="margin-top:16px">去哪拿 Key</div>'
    '<div class="card" style="display:flex;align-items:center;gap:10px">' + tile(logo='xiaomi', size=28) +
    '<div style="flex:1;min-width:0;font-size:12px;line-height:1.5">'
    'platform.xiaomimimo.com<span style="color:#A2A8B0"> → API Keys</span></div>' + CHEV + '</div>'
    '</div>')

# ══════ 10 / 11 识别结果 ══════
CB_ON = ('<div class="cb on"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="#fff" '
         'stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"><path d="m4 12.5 5.5 5.5L20 7"/></svg></div>')
CB_OFF = '<div class="cb"></div>'
CB_DEAD = '<div class="cb off"></div>'


def mrow(name, meta, state='off', pills=''):
    cb = {'on': CB_ON, 'off': CB_OFF, 'dead': CB_DEAD}[state]
    dim = ' dim' if state == 'dead' else ''
    return ('<div class="mrow%s">%s<div style="min-width:0"><div class="nm">%s%s</div>'
            '<div class="mta">%s</div></div></div>' % (dim, cb, name, pills, meta))


r10 = ''.join([
    mrow('mimo-v2.6-pro', '1M 上下文 · 原生工具调用 · thinking 开关', 'on',
         '<span class="pill rec">推荐</span>'),
    mrow('mimo-v2.6-flash', '128K 上下文 · 原生工具调用 · 更快更省', 'on'),
    mrow('mimo-v2.6-pro-ultraspeed', '1M 上下文 · 高速输出', 'off'),
    mrow('mimo-v2.5-pro', '已于 2026-10-21 下线', 'dead', '<span class="pill dead">已下线</span>'),
    mrow('mimo-v2.5', '已于 2026-10-21 下线', 'dead', '<span class="pill dead">已下线</span>'),
])
r10_other = mrow('mimo-v2.5-asr', '语音转写专用，不能用于对话', 'dead',
                 '<span class="pill media">音频</span>')

F10 = phone(
    '<div class="topbar">' + BACK + '<div><div class="ttl">选择模型</div>'
    '<div class="sub-ttl">小米 MiMo · 识别到 6 个</div></div></div>'
    '<div class="page" style="padding-top:2px">'
    '<div class="mhead"><div class="t">可用于对话</div><div class="n">5 个</div></div>' + r10 +
    '<div class="mhead"><div class="t">不适用于对话</div><div class="n">1 个</div></div>' + r10_other +
    '<div class="under" style="text-align:left;margin-top:12px">'
    '勾选的模型会加入模型列表，之后在输入区点模型 chip 随时切换。</div>'
    '<div class="infobox" style="margin-top:14px">'
    '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#3C6FA8" stroke-width="2" '
    'stroke-linecap="round" style="margin-top:1.5px;flex:none"><circle cx="12" cy="12" r="9"/>'
    '<path d="M12 16v-5M12 8h.01"/></svg>'
    '<div>厂商会随时增删模型，随时回来重新识别即可同步；'
    '已保存的模型不会因为你没勾选就被删掉。</div></div>'
    '</div>'
    '<div style="padding:8px 16px 20px;flex:none"><div class="btn-primary">保存 2 个模型</div></div>')

r11 = ''.join([
    mrow('agnes-3.0-flash', '512K 上下文 · 工具调用 · 视觉输入 · 思考 7 档', 'on',
         '<span class="pill rec">推荐</span>'),
    mrow('agnes-2.5-flash', '聊天与代码 · 工具调用 · 视觉输入 · 思考开/关', 'on'),
])
r11_media = ''.join([
    mrow('agnes-image-2.5-flash', '文生图 / 图生图 / 多图合成', 'dead', '<span class="pill media">图像</span>'),
    mrow('agnes-video-2.5-flash', '文生视频 / 图生视频 / 首尾帧', 'dead', '<span class="pill media">视频</span>'),
])

F11 = phone(
    '<div class="topbar">' + BACK + '<div><div class="ttl">选择模型</div>'
    '<div class="sub-ttl">Agnes 国内站 · 识别到 4 个</div></div></div>'
    '<div class="page" style="padding-top:2px">'
    '<div class="infobox">'
    '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#3C6FA8" stroke-width="2" '
    'stroke-linecap="round" style="margin-top:1.5px;flex:none"><circle cx="12" cy="12" r="9"/>'
    '<path d="M12 16v-5M12 8h.01"/></svg>'
    '<div>同一厂商同时提供对话与媒体模型。<b>只有对话类能勾选</b>，媒体类另走图像/视频服务。</div></div>'
    '<div class="mhead"><div class="t">可用于对话</div><div class="n">2 个</div></div>' + r11 +
    '<div class="mhead"><div class="t">其他模态</div><div class="n">2 个</div></div>' + r11_media +
    '<div class="under" style="text-align:left;margin-top:12px">'
    'agnes-3.0-flash 的思考档位是 7 档（none → max），切过去后输入区的思考控件会自动变成 7 档。</div>'
    '</div>'
    '<div style="padding:8px 16px 20px;flex:none"><div class="btn-primary">保存 2 个模型</div></div>')

# ── 新增 13 帧：0 个对话模型的兜底分支 ──
r13 = ''.join([
    mrow('xxx-image-1.0', '图像生成 / 编辑', 'dead', '<span class="pill media">图像</span>'),
    mrow('xxx-video-1.0', '文生视频', 'dead', '<span class="pill media">视频</span>'),
])

F13 = phone(
    '<div class="topbar">' + BACK + '<div><div class="ttl">选择模型</div>'
    '<div class="sub-ttl">纯图像厂商 · 识别到 2 个</div></div></div>'
    '<div class="page" style="padding-top:2px">'
    '<div class="warnbox">'
    '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#8A5A00" stroke-width="2" '
    'stroke-linecap="round" style="margin-top:1.5px;flex:none"><path d="M12 3 2.5 20h19Z"/>'
    '<path d="M12 9v5M12 17h.01"/></svg>'
    '<div>这个厂商<b>只提供图像与视频模型</b>，没有对话模型，不能作为对话模型保存。</div></div>'
    '<div class="mhead"><div class="t">可用于对话</div><div class="n">0 个</div></div>'
    '<div style="font-size:11.5px;color:#A2A8B0;padding:8px 2px 4px">'
    '没有识别到 chat / responses 类型的模型。</div>'
    '<div class="mhead"><div class="t">其他模态</div><div class="n">2 个</div></div>' + r13 +
    '<div class="infobox" style="margin-top:12px">'
    '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#3C6FA8" stroke-width="2" '
    'stroke-linecap="round" style="margin-top:1.5px;flex:none"><circle cx="12" cy="12" r="9"/>'
    '<path d="M12 16v-5M12 8h.01"/></svg>'
    '<div>可以保留为<b>图像专用服务</b>，仅用于对话里调用「生成图片」工具，'
    '不出现在模型切换列表里。</div></div>'
    '</div>'
    '<div style="padding:6px 16px 18px;flex:none">'
    '<div class="btn-primary off">没有可保存的对话模型</div>'
    '<div class="btn-line" style="margin-top:9px">仅保留为图像服务</div></div>')

# ══════ 12 保存后模型列表 ══════
F12 = phone(
    '<div class="topbar">' + BACK + '<div class="ttl">模型</div><div class="spacer"></div>' + PLUS + '</div>'
    '<div class="page" style="padding-top:2px">'
    '<div class="pcard"><div class="hd">' + tile(logo='xiaomi') +
    '<div style="min-width:0;flex:1"><div class="nm">小米 MiMo</div>'
    '<div class="ep">api.xiaomimimo.com/v1</div></div><div class="badge ok">可用</div></div>'
    '<div><span class="mchip cur">mimo-v2.6-pro</span><span class="mchip">mimo-v2.6-flash</span></div></div>'
    '<div class="pcard"><div class="hd">' + tile(logo='deepseek') +
    '<div style="min-width:0;flex:1"><div class="nm">DeepSeek</div>'
    '<div class="ep">api.deepseek.com/v1</div></div><div class="badge ok">可用</div></div>'
    '<div><span class="mchip">deepseek-chat</span></div></div>'
    '<div class="pcard"><div class="hd">' + tile(letters='Ag', color='#6B4FBB') +
    '<div style="min-width:0;flex:1"><div class="nm">Agnes 国内站</div>'
    '<div class="ep">api.agnes-ai.cn/v1</div></div><div class="badge med">仅图像</div></div>'
    '<div><span class="mchip ban">agnes-image-2.1-flash</span></div>'
    '<div style="font-size:10.5px;color:#A2A8B0;margin-top:6px">不参与对话与文件操作，只在生成图片时调用</div></div>'
    '<div class="pcard"><div class="hd">' + tile(logo='ollama') +
    '<div style="min-width:0;flex:1"><div class="nm">本机 Ollama</div>'
    '<div class="ep">192.168.1.12:11434/v1</div></div><div class="badge warn">需授权</div></div>'
    '<div><span class="mchip ban">qwen3:14b</span></div>'
    '<div style="font-size:10.5px;color:#A2A8B0;margin-top:6px">接局域网模型需先授予「附近设备」权限</div></div>'
    '<div class="under" style="margin-top:12px">在对话输入区点模型 chip 即可切换。'
    '带「仅图像」的厂商不会出现在切换列表里。</div>'
    '</div>')

frames = [
    frame(1, '08 · 添加模型：厂商预设，端点已按官方文档预填', F08),
    frame(2, '09 · 只需填 Key，点「识别模型」', F09),
    frame(3, '10 · 识别结果：按模态分组勾选，已下线自动置灰', F10),
    frame(4, '11 · Agnes：对话 2 个 + 媒体 2 个，只有对话类可勾选', F11),
    frame(5, '12 · 保存后进入模型列表，可在输入区切换', F12),
    frame(6, '13 · 兜底：识别到 0 个对话模型时的处理', F13),
]

HTML = '''<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<title>添加模型流程 v1</title><style>%s</style></head><body>
%s
<script>
  const only = new URLSearchParams(location.search).get('only');
  if (only){document.body.classList.add('single');
    document.querySelectorAll('.frame').forEach(f=>{if(f.dataset.n!==only)f.remove();});}
</script></body></html>''' % (CSS, '\n'.join(frames))

open('add-model.html', 'w', encoding='utf-8').write(HTML)
print('written', len(HTML))
