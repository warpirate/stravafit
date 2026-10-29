# StravaFit - "strava" design direction generator.
# Produces self-contained HTML phone frames (390x844) for 6 screens x 2 themes.
# Run:  python gen.py   (writes ../<screen>-<theme>.html)
import math, random, os

OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

# ------------------------------------------------------------------ tokens
SPEC = """<!--
====================================================================
 StravaFit / design direction "strava"  (ADR-014: match Strava's Android look)
 Shared design spec - identical header in every screen file.
--------------------------------------------------------------------
 FONT     Figtree (Google Fonts, OFL) 400/500/600/700/800, tabular numerals
          (font-feature-settings "tnum") on every number.
 TYPE     display 72/0.95 800 . stat-xl 40/1 700 . stat-l 26/1.1 700
          stat-m 20/1.15 700 . title-xl 24/1.2 800 . title-l 19/1.25 700
          title-m 16/1.3 700 . body 15/1.45 400 . body-s 14/1.4
          meta 13/1.35 . label 12/1.3 500 . nav 11/1.2 600
 SPACE    4-pt grid: 4 8 12 16 20 24 32 . screen gutter 16
 RADII    0 cards (edge-to-edge) . 4 buttons/inputs . 8 thumbnails
          16 sheet top . 999 avatars, map chips, round buttons
 ELEV     flat; shadow only on things floating over a map
 LIGHT    bg #FFFFFF . gap #EEEEF0 . text #242428 . muted #6D6D78
          hair #E5E5EA . accent #FC4C02 (fills/icons/route)
          accent-text #CC3D00 (small orange text, 5.2:1)
 DARK     bg #121214 . gap #000000 . surface #18181B . raised #26262B
          text #F2F2F4 . muted #A3A3AD . hair #2B2B30 . accent #FC4C02
          accent-text #FF6A2B
 ICONS    own 24px line set, 1.75 stroke, round caps/joins; active nav = filled
 NAMING   "High-five" (never kudos). No third-party logo or assets.
====================================================================
-->"""

CSS = r"""
@import url('https://fonts.googleapis.com/css2?family=Figtree:wght@400;500;600;700;800&display=block');
:root{
 --bg:#FFFFFF;--gap:#EEEEF0;--surface:#FFFFFF;--surface-2:#F5F5F7;--raised:#FFFFFF;
 --text:#242428;--text-2:#45454E;--muted:#6D6D78;--hair:#E5E5EA;--hair-strong:#CBCBD3;
 --accent:#FC4C02;--accent-press:#E04402;--accent-text:#CC3D00;--on-accent:#FFFFFF;
 --accent-area:rgba(252,76,2,.14);
 --ok:#16884A;--gold:#D6A01E;--silver:#9DA5AF;--bronze:#B4723E;--hr:#E0245E;
 --bar:#C3CCD8;--bar-fast:#FC4C02;--chart-elev:#E3E3E8;--chart-line:#3C78D8;
 --float:#FFFFFF;--float-text:#242428;--shadow:0 1px 2px rgba(0,0,0,.16),0 3px 10px rgba(0,0,0,.10);
 --pill:#242428;--loc:#2F74F0;--status:#242428;
 --m-land:#F2F1EC;--m-block:#E9E7E0;--m-park:#CFE5BF;--m-water:#A7D0EA;--m-road:#FFFFFF;--m-case:#D8D4CA;
 --m-major:#FFFFFF;--m-major-case:#C7C0B2;--m-art:#FCE7B0;--m-art-case:#DDBA6E;--m-label:#5A5B61;
 --m-halo:#F4F3EF;--m-water-label:#33689A;--m-park-label:#3B6A2E;--m-path:#AFCB9A;--route-case:#FFFFFF;
 --heat:#EE3F0A;--heat-core:#C42600;
}
[data-theme=dark]{
 --bg:#121214;--gap:#000000;--surface:#18181B;--surface-2:#222226;--raised:#26262B;
 --text:#F2F2F4;--text-2:#D2D2D8;--muted:#A3A3AD;--hair:#2B2B30;--hair-strong:#3E3E46;
 --accent:#FC4C02;--accent-press:#FF6A2B;--accent-text:#FF6A2B;--on-accent:#FFFFFF;
 --accent-area:rgba(252,76,2,.20);
 --ok:#3CC47A;--gold:#E7B43A;--silver:#B4BBC4;--bronze:#C98A55;--hr:#FF5C8A;
 --bar:#3D4654;--bar-fast:#FC4C02;--chart-elev:#2C2C33;--chart-line:#6FA2F5;
 --float:#26262B;--float-text:#F2F2F4;--shadow:0 1px 2px rgba(0,0,0,.6),0 4px 14px rgba(0,0,0,.45);
 --pill:#F2F2F4;--loc:#4C8DFF;--status:#F2F2F4;
 --m-land:#1A1C20;--m-block:#1F2227;--m-park:#18291E;--m-water:#0C2333;--m-road:#2B2F36;--m-case:#1A1C20;
 --m-major:#3A3F48;--m-major-case:#262A30;--m-art:#4B4535;--m-art-case:#2F2B22;--m-label:#A2A6B0;
 --m-halo:#1A1C20;--m-water-label:#78A6CF;--m-park-label:#86B48A;--m-path:#2C4434;--route-case:#121214;
 --heat:#FF6A12;--heat-core:#FFC46B;
}
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:390px;height:844px;overflow:hidden;background:var(--bg)}
body{font-family:'Figtree',system-ui,sans-serif;color:var(--text);-webkit-font-smoothing:antialiased;font-size:15px;line-height:1.45}
.num,.tn{font-feature-settings:"tnum" 1,"lnum" 1;font-variant-numeric:tabular-nums}
.phone{position:relative;width:390px;height:844px;overflow:hidden;background:var(--bg)}
svg{display:block}
.ic{width:24px;height:24px;fill:none;stroke:currentColor;stroke-width:1.75;stroke-linecap:round;stroke-linejoin:round}
.ic.s20{width:20px;height:20px}.ic.s18{width:18px;height:18px}.ic.s16{width:16px;height:16px;stroke-width:1.9}
/* status + gesture bars */
.sb{position:absolute;left:0;right:0;top:0;height:30px;display:flex;align-items:center;justify-content:space-between;padding:0 20px 0 22px;font-size:14px;font-weight:600;color:var(--status);z-index:30}
.sb .r{display:flex;gap:6px;align-items:center}
.sb.on-map{color:#1d1d20}
.scrim{position:absolute;left:0;right:0;top:0;height:96px;z-index:5;background:linear-gradient(var(--m-land) 0,color-mix(in srgb,var(--m-land) 70%,transparent) 34px,transparent 96px);pointer-events:none}
[data-theme=dark] .sb.on-map{color:#F2F2F4}
.gb{position:absolute;left:0;right:0;bottom:0;height:20px;z-index:40;display:flex;justify-content:center;align-items:center}
.gb i{display:block;width:108px;height:4px;border-radius:2px;background:var(--pill);opacity:.9}
/* top app bar */
.top{position:absolute;top:30px;left:0;right:0;height:52px;display:flex;align-items:center;padding:0 8px 0 16px;background:var(--bg);border-bottom:1px solid var(--hair);z-index:20}
.top h1{font-size:24px;font-weight:800;letter-spacing:-.3px;flex:1}
.top .btn{width:44px;height:44px;display:grid;place-items:center;color:var(--text);position:relative}
.dot{position:absolute;top:10px;right:10px;width:9px;height:9px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 2px var(--bg)}
/* bottom nav */
.nav{position:absolute;left:0;right:0;bottom:0;height:80px;padding-bottom:20px;background:var(--surface);border-top:1px solid var(--hair);display:flex;z-index:25}
.nav a{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;font-size:11px;font-weight:600;color:var(--muted);text-decoration:none;letter-spacing:.1px}
.nav a.on{color:var(--accent-text)}
.nav a.on .ic{color:var(--accent)}
/* feed */
.feed{position:absolute;top:82px;left:0;right:0;bottom:80px;background:var(--gap);overflow:hidden}
.card{background:var(--surface);margin-top:8px}
.card .hd{display:flex;gap:12px;padding:16px 16px 0;align-items:center}
.av{border-radius:50%;overflow:hidden;flex:none}
.hd .who{flex:1;min-width:0}
.hd .nm{font-size:15px;font-weight:700;line-height:1.25}
.hd .mt{font-size:13px;color:var(--muted);display:flex;align-items:center;gap:5px;line-height:1.35;margin-top:2px}
.hd .mt .ic{color:var(--muted)}
.more{color:var(--muted);width:32px;height:32px;display:grid;place-items:center;align-self:flex-start;margin-top:-2px}
.ttl{font-size:19px;font-weight:700;letter-spacing:-.2px;line-height:1.25;padding:12px 16px 0}
.desc{font-size:15px;padding:4px 16px 0;color:var(--text-2)}
.stats{display:flex;padding:12px 16px 0;gap:0}
.stats>div{padding-right:22px;margin-right:16px;border-right:1px solid var(--hair)}
.stats>div:last-child{border-right:0;margin-right:0;padding-right:0}
.stats .l{font-size:12px;color:var(--muted);font-weight:500;line-height:1.3}
.stats .v{font-size:20px;font-weight:700;letter-spacing:-.2px;line-height:1.2}
.stats .v small{font-size:13px;font-weight:600;margin-left:2px;letter-spacing:0}
.ach{display:flex;align-items:center;gap:6px;padding:12px 16px 0;font-size:14px;color:var(--text-2)}
.ach b{font-weight:700;color:var(--text)}
.media{display:flex;gap:2px;margin-top:12px;height:220px;overflow:hidden}
.media>*{flex:none}
.soc{display:flex;align-items:center;padding:12px 16px 0;gap:10px;font-size:13px;color:var(--muted)}
.pile{display:flex}
.pile .av{box-shadow:0 0 0 2px var(--surface)}
.pile .av+.av{margin-left:-7px}
.soc .sp{flex:1}
.acts{display:flex;justify-content:space-around;height:48px;align-items:center;margin-top:8px;border-top:1px solid var(--hair);color:var(--text)}
.acts span{display:flex;align-items:center;justify-content:center;width:72px;height:44px}
/* misc */
.chip{display:inline-flex;align-items:center;gap:6px;height:34px;padding:0 14px;border-radius:999px;background:var(--float);color:var(--float-text);box-shadow:var(--shadow);font-size:14px;font-weight:600;white-space:nowrap}
.chip.dark{background:#242428;color:#fff}
[data-theme=dark] .chip.dark{background:#F2F2F4;color:#18181B}
.fbtn{width:44px;height:44px;border-radius:50%;background:var(--float);color:var(--float-text);box-shadow:var(--shadow);display:grid;place-items:center}
.sec{font-size:19px;font-weight:700;letter-spacing:-.2px}
.lnk{font-size:14px;font-weight:600;color:var(--accent-text)}
.tabs{display:flex;border-bottom:1px solid var(--hair)}
.tabs a{flex:1;text-align:center;font-size:15px;font-weight:600;color:var(--muted);height:46px;line-height:46px;position:relative}
.tabs a.on{color:var(--text)}
.tabs a.on:after{content:"";position:absolute;left:0;right:0;bottom:-1px;height:3px;background:var(--accent)}
"""

# ------------------------------------------------------------------ icons
GEAR_PATH = None
def gear_path(cx=12, cy=12, r1=7.6, r2=9.6, teeth=8):
    pts = []
    n = teeth * 4
    for k in range(n):
        a = 2 * math.pi * k / n + math.pi / n / 2
        r = r2 if (k % 4) in (1, 2) else r1
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return 'M' + ' L'.join('%.2f %.2f' % p for p in pts) + 'Z'

I = {
 'home': '<path d="M3.5 10.6 12 3.6l8.5 7V20.5h-5.6v-5.8H9.1v5.8H3.5z"/>',
 'maps': '<path d="M3.5 6.2 9 4.2l6 2 5.5-2v13.6l-5.5 2-6-2-5.5 2z"/><path d="M9 4.2v13.6M15 6.2v13.6"/>',
 'record': '<circle cx="12" cy="12" r="8.6"/><circle cx="12" cy="12" r="3.6" fill="currentColor"/>',
 'groups': '<circle cx="9" cy="8.4" r="3.3"/><path d="M2.8 19.8c.4-3.6 3-5.6 6.2-5.6s5.8 2 6.2 5.6z"/><path d="M15.4 5.4a3.2 3.2 0 0 1 0 6.2M17.6 14.6c2.1.7 3.4 2.4 3.6 5.2h-3"/>',
 'you': '<circle cx="12" cy="8" r="4"/><path d="M4.4 20.6c.6-4.3 3.6-6.6 7.6-6.6s7 2.3 7.6 6.6z"/>',
 'search': '<circle cx="10.6" cy="10.6" r="6.4"/><path d="m15.4 15.4 5 5"/>',
 'chat': '<path d="M4 5.5h16v10.6H10l-4.6 3.6v-3.6H4z"/>',
 'bell': '<path d="M6.2 16.6V10.8a5.8 5.8 0 0 1 11.6 0v5.8l1.7 1.9H4.5z"/><path d="M10 20.6a2.2 2.2 0 0 0 4 0"/>',
 'hi5': '<path d="M8 12.2V5.6a1.4 1.4 0 0 1 2.8 0v5.6M10.8 10.8V4.4a1.4 1.4 0 0 1 2.8 0v6.4M13.6 11V5.4a1.4 1.4 0 0 1 2.8 0v6.8M16.4 12.4V8.6a1.4 1.4 0 0 1 2.8 0v5.4c0 4-2.8 7-6.6 7h-.9c-2 0-3.6-.9-4.8-2.4L3.6 14.6a1.5 1.5 0 0 1 2.2-2L8 14.6"/>',
 'comment': '<path d="M20.4 11.6c0 4.1-3.8 7.4-8.4 7.4-1.2 0-2.4-.2-3.4-.6L4 20l1.2-3.6a6.9 6.9 0 0 1-1.6-4.8c0-4.1 3.8-7.4 8.4-7.4s8.4 3.3 8.4 7.4z"/>',
 'share': '<path d="M12 3.8v11M7.6 8 12 3.6 16.4 8"/><path d="M8 11H5.6v9.4h12.8V11H16"/>',
 'more': '<circle cx="5.5" cy="12" r="1.2" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none"/><circle cx="18.5" cy="12" r="1.2" fill="currentColor" stroke="none"/>',
 'back': '<path d="M19.5 12H5M11 5.6 4.6 12l6.4 6.4"/>',
 'down': '<path d="m5.6 9 6.4 6.4L18.4 9"/>',
 'chev': '<path d="m9.4 5.6 6.4 6.4-6.4 6.4"/>',
 'caret': '<path d="m7.5 10 4.5 4.5 4.5-4.5"/>',
 'run': '<path d="M14.2 4.4a1.8 1.8 0 1 0 .1 0z" fill="currentColor" stroke="none"/><path d="m6.8 20.2 3.4-4.6 2.8 1.6.6 3M10.2 15.6l1.2-5.4-2.9 1.1-1.6 2.6M11.4 10.2l2.3 2.1 3.2.6M13.7 12.3l.7-3.2"/>',
 'shoe': '<path d="M3.4 15.4V8.8c1.4.9 3 .9 4.4-.3l1.6 2.2 1.6-1 1.4 1.7 1.4-.9c.8 1.6 2.4 2.6 4.4 3.1 1.6.4 2.4 1.2 2.4 2.6v1.6H4.6a1.2 1.2 0 0 1-1.2-1.2z"/><path d="M3.4 14.2h17.2"/>',
 'bike': '<circle cx="5.8" cy="15.4" r="3.6"/><circle cx="18.2" cy="15.4" r="3.6"/><path d="M5.8 15.4 9.4 8.6h6.4l2.4 6.8M9.4 8.6l3 6.8h-6.6M8.4 6.4h2.6M15.8 8.6l-1-2.4h2"/>',
 'walk': '<circle cx="12.6" cy="4.8" r="1.7" fill="currentColor" stroke="none"/><path d="m9 20.4 2.2-5.6 2.6 2.2v3.4M11.2 14.8l1-5.6-2.6 1.4-1 3M12.2 9.2l1.6 2.6 2.6 1"/>',
 'trophy': '<path d="M7.6 4.4h8.8v4.4a4.4 4.4 0 0 1-8.8 0z"/><path d="M7.6 6.2H4.4c0 2.6 1.4 4.2 3.6 4.4M16.4 6.2h3.2c0 2.6-1.4 4.2-3.6 4.4M12 13.2v3.4M8.6 20h6.8l-.8-3.4H9.4z"/>',
 'layers': '<path d="m12 4 8.4 4.4L12 12.8 3.6 8.4z"/><path d="m3.6 12.2 8.4 4.4 8.4-4.4M3.6 16 12 20.4l8.4-4.4"/>',
 'locate': '<path d="M20.4 3.6 3.6 10.8l7.2 2.4 2.4 7.2z"/>',
 'route': '<circle cx="6" cy="18" r="2"/><circle cx="18" cy="6" r="2"/><path d="M8 18h7.4a3.2 3.2 0 0 0 0-6.4H8.6a3.2 3.2 0 0 1 0-6.4H16"/>',
 'heart': '<path d="M12 19.6s-7.6-4.4-7.6-9.8A4.2 4.2 0 0 1 12 7.6a4.2 4.2 0 0 1 7.6 2.2c0 5.4-7.6 9.8-7.6 9.8z"/>',
 'sensor': '<path d="M12 19.6s-7.6-4.4-7.6-9.8A4.2 4.2 0 0 1 12 7.6a4.2 4.2 0 0 1 7.6 2.2c0 5.4-7.6 9.8-7.6 9.8z"/><path d="M7.4 12.4h2.4l1.2-2 2 4 1.2-2h2.4"/>',
 'lock': '<rect x="5.4" y="10.6" width="13.2" height="9.4" rx="1.6"/><path d="M8.4 10.6V8a3.6 3.6 0 0 1 7.2 0v2.6"/>',
 'flag': '<path d="M5.6 20.6V4M5.6 4.6h11.6l-2.4 3.8 2.4 3.8H5.6"/>',
 'plus': '<path d="M12 5v14M5 12h14"/>',
 'mountain': '<path d="m2.8 19 6.4-10.6 3.6 6 2.4-3.6L21.2 19z"/>',
 'clock': '<circle cx="12" cy="12" r="8.4"/><path d="M12 7.4V12l3 2"/>',
 'stats': '<path d="M5 19.5V11M10 19.5V5M15 19.5v-6.6M20 19.5V8.6"/>',
 'mapfold': '<path d="M3.5 6.2 9 4.2l6 2 5.5-2v13.6l-5.5 2-6-2-5.5 2z"/><path d="M9 4.2v13.6M15 6.2v13.6"/>',
 'music': '<path d="M9 17.6V5.8l10-1.8v11.6"/><circle cx="6.6" cy="17.6" r="2.4"/><circle cx="16.6" cy="15.6" r="2.4"/>',
 'beacon': '<circle cx="12" cy="12" r="2"/><path d="M8 8a5.6 5.6 0 0 0 0 8M16 8a5.6 5.6 0 0 1 0 8M5.2 5.2a9.6 9.6 0 0 0 0 13.6M18.8 5.2a9.6 9.6 0 0 1 0 13.6"/>',
 'pin': '<path d="M12 21s-6.4-6-6.4-11a6.4 6.4 0 0 1 12.8 0c0 5-6.4 11-6.4 11z"/><circle cx="12" cy="10" r="2.3"/>',
 'calendar': '<rect x="4" y="5.4" width="16" height="14.6" rx="1.6"/><path d="M4 10h16M8.4 3.4v3.6M15.6 3.4v3.6"/>',
 'medal': '<circle cx="12" cy="14.6" r="5.4"/><path d="M8.6 10.4 6 3.6h4l2 4.4 2-4.4h4l-2.6 6.8"/>',
}
I['gear'] = '<path d="%s"/><circle cx="12" cy="12" r="3.1"/>' % gear_path()
FILLED = {'home', 'you', 'groups', 'maps'}

def ic(name, cls='', fill=False, extra=''):
    body = I[name]
    style = ''
    if fill:
        body = body.replace('<path ', '<path fill="currentColor" ', 1) if name != 'maps' else \
            '<path fill="currentColor" d="M3.5 6.2 9 4.2l6 2 5.5-2v13.6l-5.5 2-6-2-5.5 2z"/><path stroke="var(--surface)" d="M9 4.2v13.6M15 6.2v13.6"/>'
        if name == 'you':
            body = body.replace('<circle ', '<circle fill="currentColor" ', 1)
        if name == 'groups':
            body = body.replace('<circle ', '<circle fill="currentColor" ', 1)
    return '<svg class="ic %s" viewBox="0 0 24 24" %s>%s</svg>' % (cls, extra, body)

def status_bar(on_map=False, time='7:42'):
    sig = '<svg width="16" height="14" viewBox="0 0 16 14"><path d="M1 13h2.2V9.6H1zM4.6 13h2.2V7.4H4.6zM8.2 13h2.2V4.8H8.2zM11.8 13H14V2h-2.2z" fill="currentColor"/></svg>'
    wifi = '<svg width="16" height="14" viewBox="0 0 16 14"><path d="M8 13 .6 4.6a11 11 0 0 1 14.8 0z" fill="currentColor"/></svg>'
    bat = '<svg width="11" height="17" viewBox="0 0 11 17"><rect x="3.3" y=".5" width="4.4" height="2" rx=".6" fill="currentColor"/><rect x="1" y="2.2" width="9" height="14" rx="1.6" fill="none" stroke="currentColor" stroke-width="1.3"/><rect x="2.6" y="6" width="5.8" height="8.6" rx=".7" fill="currentColor"/></svg>'
    return '<div class="sb%s"><span class="tn">%s</span><span class="r">%s%s%s</span></div>' % (
        ' on-map' if on_map else '', time, wifi, sig, bat)

def gesture():
    return '<div class="gb"><i></i></div>'

def bottom_nav(active):
    items = [('home', 'Home'), ('maps', 'Maps'), ('record', 'Record'), ('groups', 'Groups'), ('you', 'You')]
    out = []
    for k, lab in items:
        on = k == active
        out.append('<a class="%s">%s<span>%s</span></a>' % ('on' if on else '', ic(k, fill=on and k in FILLED), lab))
    return '<nav class="nav">%s</nav>' % ''.join(out)

# ------------------------------------------------------------------ avatars (photo-like)
PEOPLE = {
 'ananya': dict(bg=('#9DB98A', '#3F5F3A'), skin=('#D9A27A', '#9A6440'), hair='#1E1410', style='long', shirt=('#1F8A8A', '#11585A')),
 'rohan':  dict(bg=('#C9D6E3', '#5E7590'), skin=('#C88B62', '#8A5534'), hair='#15100D', style='short', shirt=('#E4572E', '#9E3315'), beard=True),
 'lukas':  dict(bg=('#8FB3D9', '#2F4F78'), skin=('#F0C9AE', '#C08D6E'), hair='#B88A4E', style='short', shirt=('#2B2F38', '#14161B')),
 'priya':  dict(bg=('#E8C9A8', '#9C6B49'), skin=('#B97A52', '#7A4A2C'), hair='#120C0A', style='bun', shirt=('#F2F0EA', '#BDB8AC')),
 'daniel': dict(bg=('#A9C4B2', '#40604E'), skin=('#7A4B2E', '#4A2A18'), hair='#0E0A08', style='buzz', shirt=('#3060C0', '#1C3A80')),
 'meera':  dict(bg=('#D7B7C4', '#7C5468'), skin=('#D29A73', '#94603E'), hair='#2A1A14', style='long', shirt=('#8E3B5E', '#5A1F38')),
 'arjun':  dict(bg=('#B9C2CC', '#4C5A68'), skin=('#BE8059', '#7E4C2E'), hair='#16100C', style='cap', shirt=('#FC4C02', '#B23300'), cap='#24262B', beard=True),
 'sofia':  dict(bg=('#F0D3A0', '#B0784A'), skin=('#E3B48F', '#A87650'), hair='#4A2A18', style='long', shirt=('#3F7D4E', '#24502F')),
 'kabir':  dict(bg=('#9FB0C0', '#3B4A5A'), skin=('#A86E48', '#6E4226'), hair='#0F0B09', style='short', shirt=('#D8D8DC', '#9C9CA4')),
}
_av = [0]
def avatar(who, size):
    p = PEOPLE[who]; _av[0] += 1; u = 'a%d' % _av[0]
    hair = p['hair']; st = p['style']
    defs = ('<defs><radialGradient id="%sb" cx=".3" cy=".25" r=".9"><stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></radialGradient>'
            '<radialGradient id="%sk" cx=".38" cy=".32" r=".75"><stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></radialGradient>'
            '<linearGradient id="%ss" x1="0" y1="0" x2=".3" y2="1"><stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></linearGradient>'
            '<radialGradient id="%sv" cx=".5" cy=".45" r=".62"><stop offset=".6" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".32"/></radialGradient>'
            '<filter id="%sf"><feGaussianBlur stdDeviation="1.1"/></filter><clipPath id="%sc"><circle cx="50" cy="50" r="50"/></clipPath></defs>') % (
        u, p['bg'][0], p['bg'][1], u, p['skin'][0], p['skin'][1], u, p['shirt'][0], p['shirt'][1], u, u, u)
    g = []
    g.append('<rect width="100" height="100" fill="url(#%sb)"/>' % u)
    # background bokeh blobs
    g.append('<circle cx="18" cy="22" r="14" fill="#fff" opacity=".18"/><circle cx="84" cy="30" r="10" fill="#fff" opacity=".12"/>')
    if st == 'long':
        g.append('<path d="M31 46C29 24 43 17 51 17c11 0 21 7 18 30 1 14 4 24 0 30H32c-5-8-2-18-1-31z" fill="%s"/>' % hair)
    g.append('<path d="M6 104C9 80 28 71 50 71s41 9 44 33z" fill="url(#%ss)"/>' % u)
    g.append('<path d="M42 58h16v14c-3 4-13 4-16 0z" fill="%s"/>' % p['skin'][1])
    g.append('<ellipse cx="50" cy="45" rx="15.5" ry="18.5" fill="url(#%sk)"/>' % u)
    g.append('<ellipse cx="34.8" cy="47" rx="2.6" ry="4" fill="%s"/><ellipse cx="65.2" cy="47" rx="2.6" ry="4" fill="%s"/>' % (p['skin'][1], p['skin'][1]))
    if p.get('beard'):
        g.append('<path d="M36 50c1 11 7 15.5 14 15.5S63 61 64 50c-3 6-8 7-14 7s-11-1-14-7z" fill="%s" opacity=".55"/>' % hair)
    if st in ('short', 'long', 'bun'):
        g.append('<path d="M34 44c-1-15 8-21 16-21 10 0 18 6 16 21-3-8-9-12-16-12-8 0-13 4-16 12z" fill="%s"/>' % hair)
    if st == 'bun':
        g.append('<circle cx="50" cy="20" r="7.5" fill="%s"/>' % hair)
    if st == 'buzz':
        g.append('<path d="M35 42c0-13 7-18 15-18s15 5 15 18c-3-6-8-9-15-9s-12 3-15 9z" fill="%s" opacity=".85"/>' % hair)
    if st == 'cap':
        g.append('<path d="M33 40c0-12 8-17 17-17s17 5 17 17z" fill="%s"/><path d="M48 38h28c-2 4-8 5-16 5H48z" fill="%s"/>' % (p['cap'], p['cap']))
        g.append('<path d="M34 40c-1 3-1 6 0 8M66 40c1 3 1 6 0 8" stroke="%s" stroke-width="2.4"/>' % hair)
    # face shading: eyes soft, light from left
    g.append('<ellipse cx="44" cy="47" rx="2.1" ry="1.3" fill="#1a0f0a" opacity=".55"/><ellipse cx="56" cy="47" rx="2.1" ry="1.3" fill="#1a0f0a" opacity=".55"/>')
    g.append('<path d="M45.5 57.5q4.5 2.4 9 0" stroke="#5a2f1f" stroke-width="1.3" fill="none" opacity=".45" stroke-linecap="round"/>')
    g.append('<ellipse cx="60" cy="50" rx="10" ry="16" fill="#000" opacity=".08"/>')
    g.append('<rect width="100" height="100" fill="url(#%sv)"/>' % u)
    return ('<svg class="av" width="%d" height="%d" viewBox="0 0 100 100">%s<g clip-path="url(#%sc)" filter="url(#%sf)">%s</g></svg>' %
            (size, size, defs, u, u, ''.join(g)))

# ------------------------------------------------------------------ photos
def photo_lake(w, h, uid):
    u = 'p' + uid
    return ('<svg width="%d" height="%d" viewBox="0 0 300 220" preserveAspectRatio="xMidYMid slice">'
      '<defs><linearGradient id="%ss" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#5B6E9A"/><stop offset=".45" stop-color="#E79A6B"/><stop offset=".62" stop-color="#F7C98B"/></linearGradient>'
      '<radialGradient id="%ssun" cx=".62" cy=".55" r=".35"><stop offset="0" stop-color="#FFF3C8"/><stop offset=".25" stop-color="#FFD48A" stop-opacity=".8"/><stop offset="1" stop-color="#FFB070" stop-opacity="0"/></radialGradient>'
      '<linearGradient id="%sw" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#C98B6C"/><stop offset="1" stop-color="#3A3F55"/></linearGradient>'
      '<filter id="%sb"><feGaussianBlur stdDeviation="1.2"/></filter><filter id="%sn"><feTurbulence baseFrequency=".9" numOctaves="2" seed="4"/><feColorMatrix values="0 0 0 0 .5 0 0 0 0 .5 0 0 0 0 .5 0 0 0 .07 0"/></filter></defs>'
      '<rect width="300" height="220" fill="url(#%ss)"/><rect width="300" height="220" fill="url(#%ssun)"/>'
      '<g filter="url(#%sb)"><path d="M0 120 40 104 70 112 110 96 150 108 190 92 230 106 260 98 300 110V140H0z" fill="#7C6C86" opacity=".7"/>'
      '<path d="M0 132 30 122 80 128 120 116 170 126 210 118 260 128 300 120V146H0z" fill="#4E4A63"/></g>'
      '<rect y="140" width="300" height="80" fill="url(#%sw)"/>'
      '<g stroke="#FFE2A8" stroke-linecap="round" opacity=".7"><path d="M170 148h36M160 156h52M176 164h22M150 172h70M168 182h30" stroke-width="2"/></g>'
      '<path d="M0 150c20-10 30-34 22-58 12 8 16 24 14 34 6-10 4-24 0-34 14 14 16 40 4 58z" fill="#1E2230"/>'
      '<path d="M236 146c6-18 4-30-2-44 10 10 14 22 12 30 4-12 2-22-2-28 12 12 14 30 6 42z" fill="#1E2230"/>'
      '<path d="M0 186c60-10 100-6 160 2s110 6 140 0v34H0z" fill="#2A2B33"/>'
      '<g fill="#14151B"><circle cx="96" cy="160" r="3.4"/><path d="M94 164l-2 9-4 7h2.4l3.4-6 3 3.4 1.4 6h2.2l-1.8-7.6-2.8-3.6.8-6 3 3 3-1-.6-1.6-2.2.6-3.2-3.6z"/></g>'
      '<rect width="300" height="220" filter="url(#%sn)"/></svg>') % ((w, h) + (u,) * 10)

def photo_trail(w, h, uid):
    u = 't' + uid
    return ('<svg width="%d" height="%d" viewBox="0 0 120 220" preserveAspectRatio="xMidYMid slice">'
      '<defs><linearGradient id="%sa" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#BFD6E6"/><stop offset="1" stop-color="#7FA16B"/></linearGradient><filter id="%sb"><feGaussianBlur stdDeviation="2"/></filter></defs>'
      '<rect width="120" height="220" fill="url(#%sa)"/>'
      '<g filter="url(#%sb)"><circle cx="20" cy="50" r="30" fill="#4F7A3E"/><circle cx="100" cy="40" r="34" fill="#3C6531"/><circle cx="60" cy="90" r="26" fill="#6E9450"/></g>'
      '<path d="M48 220 58 120h4l18 100z" fill="#C9A77E"/><path d="M0 140c30 4 40 30 50 80H0zM120 130c-24 10-30 40-34 90h34z" fill="#3E5F2E"/>'
      '<g fill="#1C1A1E"><circle cx="63" cy="128" r="3"/><path d="M61 131.5 59.4 141l-3.4 6.4h2l3-5.4 2.6 3 1 6h2l-1.4-7-2.4-3.4.8-5.4 2.4 2.4 2.6-.8-.4-1.4-2 .4-2.6-3z"/></g></svg>') % ((w, h) + (u,) * 4)

# ------------------------------------------------------------------ maps
class Lat:
    def __init__(s, spacing, theta, seed, warp=1.0):
        s.sp = spacing; s.t = math.radians(theta); s.seed = seed; s.warp = warp; s.ox = 0; s.oy = 0
    def raw(s, i, j):
        x = i * s.sp + s.sp * 0.3 * math.sin(i * 1.9 + s.seed); y = j * s.sp + s.sp * 0.27 * math.sin(j * 2.3 + s.seed * .7)
        c, n = math.cos(s.t), math.sin(s.t)
        X = x * c - y * n; Y = x * n + y * c
        X += 7 * s.warp * math.sin((Y + s.seed * 13) / 95.0) * s.sp / 34
        Y += 6 * s.warp * math.sin((X + s.seed * 7) / 120.0) * s.sp / 34
        if float(i).is_integer() and float(j).is_integer():
            r = random.Random(s.seed * 100003 + int(i) * 917 + int(j) * 31)
            X += r.uniform(-2.2, 2.2) * s.sp / 34; Y += r.uniform(-2.2, 2.2) * s.sp / 34
        return X, Y
    def p(s, i, j):
        X, Y = s.raw(i, j); return X + s.ox, Y + s.oy

def smooth_path(pts, closed=False):
    if len(pts) < 3:
        return 'M' + ' L'.join('%.1f %.1f' % q for q in pts)
    d = 'M%.1f %.1f' % pts[0]
    P = pts + ([pts[0], pts[1]] if closed else [])
    n = len(P)
    for k in range(n - 1):
        p0 = P[k - 1] if k > 0 else P[k]; p1 = P[k]; p2 = P[k + 1]; p3 = P[k + 2] if k + 2 < n else P[k + 1]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += ' C%.1f %.1f %.1f %.1f %.1f %.1f' % (c1 + c2 + p2)
    return d

def expand(route):
    """expand lattice waypoints (axis-aligned moves) into unit steps"""
    out = [route[0]]
    for (a, b) in zip(route, route[1:]):
        i, j = a
        while (i, j) != tuple(b):
            i += (b[0] > i) - (b[0] < i) if i != b[0] else 0
            if i == b[0]:
                j += (b[1] > j) - (b[1] < j) if j != b[1] else 0
            out.append((i, j))
    return out

def route_d(L, steps, rounded=True):
    pts = [L.p(i, j) for (i, j) in steps]
    if not rounded:
        return 'M' + ' L'.join('%.1f %.1f' % q for q in pts)
    # gentle corner rounding
    d = 'M%.1f %.1f' % pts[0]
    for k in range(1, len(pts) - 1):
        a, b, c = pts[k - 1], pts[k], pts[k + 1]
        r = 0.18
        q1 = (b[0] + (a[0] - b[0]) * r, b[1] + (a[1] - b[1]) * r)
        q2 = (b[0] + (c[0] - b[0]) * r, b[1] + (c[1] - b[1]) * r)
        d += ' L%.1f %.1f Q%.1f %.1f %.1f %.1f' % (q1 + b + q2)
    d += ' L%.1f %.1f' % pts[-1]
    return d

_map = [0]
def map_svg(w, h, spacing=34, theta=12, seed=1, route=None, center=None, anchor=(0, 0), parks=(), majors=(),
            river=None, river_w=26, lake=None, labels=(), art=None, heat=0, route_part=None, markers=True,
            loc=None, footpaths=True, blocks=True, route_w=3.6, extra_style='', cls='', heat_focus=None, heat_corr=None, part_center=False):
    _map[0] += 1; u = 'm%d' % _map[0]
    L = Lat(spacing, theta, seed)
    steps = expand(route) if route else []
    csteps = steps[:route_part] if (part_center and route_part) else steps
    if center is None:
        if steps:
            xs = [L.raw(i, j)[0] for i, j in csteps]; ys = [L.raw(i, j)[1] for i, j in csteps]
            cx0 = (min(xs) + max(xs)) / 2; cy0 = (min(ys) + max(ys)) / 2
        else:
            cx0, cy0 = L.raw(*anchor)
        center = (w / 2, h / 2)
    else:
        cx0, cy0 = L.raw(*anchor) if not steps else (
            (min(L.raw(i, j)[0] for i, j in csteps) + max(L.raw(i, j)[0] for i, j in csteps)) / 2,
            (min(L.raw(i, j)[1] for i, j in csteps) + max(L.raw(i, j)[1] for i, j in csteps)) / 2)
    L.ox = center[0] - cx0; L.oy = center[1] - cy0
    # lattice index range around screen centre
    ci = (route[0][0] if route else anchor[0]); cj = (route[0][1] if route else anchor[1])
    R = int(math.hypot(w, h) / spacing) + 4
    inv = lambda x, y: -40 <= x <= w + 40 and -40 <= y <= h + 40
    park_cells = set()
    for (i0, j0, i1, j1, _) in parks:
        for i in range(i0, i1):
            for j in range(j0, j1):
                park_cells.add((i, j))
    protect = set()
    for a, b in zip(steps, steps[1:]):
        protect.add(tuple(sorted([a, b])))
    major_edges = set()
    for m in majors:
        kind, k, a0, a1 = m[:4]
        for t in range(a0, a1):
            e = ((t, k), (t + 1, k)) if kind == 'row' else ((k, t), (k, t + 1))
            major_edges.add(e)
    blk, minor = [], []
    rng = random.Random(seed)
    for i in range(ci - R, ci + R):
        for j in range(cj - R, cj + R):
            p00 = L.p(i, j)
            if not inv(*p00):
                continue
            # block
            if blocks and (i, j) not in park_cells:
                q = [L.p(i, j), L.p(i + 1, j), L.p(i + 1, j + 1), L.p(i, j + 1)]
                cx = sum(a for a, _ in q) / 4; cy = sum(b for _, b in q) / 4
                ins = 0.16 if spacing > 24 else 0.2
                qq = [(a + (cx - a) * ins, b + (cy - b) * ins) for a, b in q]
                blk.append('M' + ' L'.join('%.1f %.1f' % z for z in qq) + 'Z')
            for e in (((i, j), (i + 1, j)), ((i, j), (i, j + 1))):
                key = tuple(sorted(e))
                if key in major_edges:
                    continue
                r = random.Random(seed * 7 + e[0][0] * 131 + e[0][1] * 71 + (e[1][0] - e[0][0]) * 13)
                if key not in protect and r.random() < 0.13:
                    continue
                if e[0] in park_cells and (e[1] in park_cells) and key not in protect:
                    pass
                a = L.p(*e[0]); b = L.p(*e[1])
                minor.append('M%.1f %.1fL%.1f %.1f' % (a + b))
    out = ['<svg class="map %s" width="%d" height="%d" viewBox="0 0 %d %d" style="%s">' % (cls, w, h, w, h, extra_style)]
    out.append('<defs><pattern id="%sck" width="4" height="4" patternUnits="userSpaceOnUse"><rect width="4" height="4" fill="#fff"/><rect width="2" height="2" fill="#111"/><rect x="2" y="2" width="2" height="2" fill="#111"/></pattern>'
               '<filter id="%sg" x="-20%%" y="-20%%" width="140%%" height="140%%"><feGaussianBlur stdDeviation="2.2"/></filter></defs>' % (u, u))
    out.append('<rect width="%d" height="%d" fill="var(--m-land)"/>' % (w, h))
    if blk:
        out.append('<path d="%s" fill="var(--m-block)"/>' % ' '.join(blk))
    for (i0, j0, i1, j1, name) in parks:
        pts = [L.p(i, j0) for i in range(i0, i1 + 1)] + [L.p(i1, j) for j in range(j0 + 1, j1 + 1)] + \
              [L.p(i, j1) for i in range(i1 - 1, i0 - 1, -1)] + [L.p(i0, j) for j in range(j1 - 1, j0, -1)]
        out.append('<path d="M%sZ" fill="var(--m-park)"/>' % ' L'.join('%.1f %.1f' % z for z in pts))
    sw = max(1.6, spacing / 12.0)
    if minor:
        dm = ' '.join(minor)
        out.append('<path d="%s" stroke="var(--m-case)" stroke-width="%.1f" stroke-linecap="round" fill="none"/>' % (dm, sw + 1.4))
        out.append('<path d="%s" stroke="var(--m-road)" stroke-width="%.1f" stroke-linecap="round" fill="none"/>' % (dm, sw))
    if footpaths:
        for (i0, j0, i1, j1, name) in parks:
            if (i1 - i0) * (j1 - j0) < 4:
                continue
            a = L.p(i0 + .3, j0 + .5); b = L.p((i0 + i1) / 2, (j0 + j1) / 2 + .2); c = L.p(i1 - .3, j1 - .6)
            d2 = L.p(i0 + .6, j1 - .4); e2 = L.p(i1 - .5, j0 + .4)
            out.append('<path d="%s M%.1f %.1f Q%.1f %.1f %.1f %.1f" stroke="var(--m-path)" stroke-width="1.3" stroke-dasharray="3 2.4" fill="none"/>' % (
                smooth_path([a, b, c]), d2[0], d2[1], b[0], b[1], e2[0], e2[1]))
    if lake:
        pts = [L.p(*q) for q in lake]
        out.append('<path d="%s" fill="var(--m-water)"/>' % smooth_path(pts, True))
    if river:
        pts = [L.p(*q) for q in river]
        out.append('<path d="%s" stroke="var(--m-water)" stroke-width="%d" stroke-linecap="round" stroke-linejoin="round" fill="none"/>' % (smooth_path(pts), river_w))
    # major roads
    for m in majors:
        kind, k, a0, a1 = m[:4]
        pts = [L.p(t, k) if kind == 'row' else L.p(k, t) for t in range(a0, a1 + 1)]
        d = smooth_path(pts)
        mw = sw * 2.3
        out.append('<path d="%s" stroke="var(--m-major-case)" stroke-width="%.1f" fill="none" stroke-linecap="round"/>' % (d, mw + 2))
        out.append('<path id="%sr%s%d" d="%s" stroke="var(--m-major)" stroke-width="%.1f" fill="none" stroke-linecap="round"/>' % (u, kind, k, d, mw))
    if art:
        pts = [L.p(*q) for q in art['pts']]
        d = smooth_path(pts)
        aw = sw * 2.8
        out.append('<path d="%s" stroke="var(--m-art-case)" stroke-width="%.1f" fill="none" stroke-linecap="round"/>' % (d, aw + 2.2))
        out.append('<path id="%sart" d="%s" stroke="var(--m-art)" stroke-width="%.1f" fill="none" stroke-linecap="round"/>' % (u, d, aw))
    # heat
    if heat:
        hr = random.Random(seed * 3 + 5)
        hi = heat_focus or (ci, cj)
        cnt = {}
        dirs = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        corr = heat_corr or []
        for n in range(heat):
            if corr and hr.random() < 0.55:
                kind, k, a0, a1 = hr.choice(corr)
                t = hr.randint(a0, a1)
                i, j = (t, k) if kind == 'row' else (k, t)
                dirc = (1, 0) if kind == 'row' else (0, 1)
                if hr.random() < .5: dirc = (-dirc[0], -dirc[1])
                turn = 0.08
            else:
                i = hi[0] + int(hr.gauss(0, 6)); j = hi[1] + int(hr.gauss(0, 7))
                dirc = hr.choice(dirs); turn = 0.3
            for s_ in range(hr.randint(5, 22)):
                if hr.random() < turn:
                    dirc = hr.choice(dirs)
                ni, nj = i + dirc[0], j + dirc[1]
                key = tuple(sorted([(i, j), (ni, nj)]))
                cnt[key] = cnt.get(key, 0) + 1
                i, j = ni, nj
        buckets = {}
        for key, c in cnt.items():
            a = L.p(*key[0]); b = L.p(*key[1])
            if not (inv(*a) or inv(*b)):
                continue
            lvl = min(5, c)
            buckets.setdefault(lvl, []).append('M%.1f %.1fL%.1f %.1f' % (a + b))
        out.append('<g fill="none" stroke-linecap="round">')
        allp = ' '.join(' '.join(v) for v in buckets.values())
        out.append('<path d="%s" stroke="var(--heat)" stroke-width="4.5" opacity=".16" filter="url(#%sg)"/>' % (allp, u))
        for lvl in sorted(buckets):
            col = 'var(--heat-core)' if lvl >= 4 else 'var(--heat)'
            op = [0, .32, .5, .7, .85, .95][lvl]
            wd = [0, 1.2, 1.5, 1.8, 2.0, 2.2][lvl]
            out.append('<path d="%s" stroke="%s" stroke-width="%.1f" opacity="%.2f"/>' % (' '.join(buckets[lvl]), col, wd, op))
        out.append('</g>')
    # labels
    lab = []
    for item in labels:
        text, i, j, kind = item[:4]
        x, y = L.p(i, j)
        if kind == 'area':
            lab.append('<text x="%.1f" y="%.1f" class="ml area">%s</text>' % (x, y, text))
        elif kind == 'park':
            lab.append('<text x="%.1f" y="%.1f" class="ml park">%s</text>' % (x, y, text))
        elif kind == 'water':
            rot = item[4] if len(item) > 4 else 0
            lab.append('<text x="%.1f" y="%.1f" class="ml water" transform="rotate(%d %.1f %.1f)">%s</text>' % (x, y, rot, x, y, text))
        elif kind == 'road':
            rot = item[4] if len(item) > 4 else 0
            lab.append('<text x="%.1f" y="%.1f" class="ml road" transform="rotate(%.1f %.1f %.1f)">%s</text>' % (x, y, rot, x, y, text))
        elif kind == 'poi':
            lab.append('<circle cx="%.1f" cy="%.1f" r="3" fill="var(--m-label)"/><text x="%.1f" y="%.1f" class="ml poi">%s</text>' % (x - 6, y - 4, x, y, text))
    out.append('<g class="labels">%s</g>' % ''.join(lab))
    # route
    if steps:
        part = steps if route_part is None else steps[:route_part]
        d = route_d(L, part)
        out.append('<path d="%s" stroke="var(--route-case)" stroke-width="%.1f" fill="none" stroke-linecap="round" stroke-linejoin="round"/>' % (d, route_w + 3))
        out.append('<path d="%s" stroke="var(--accent)" stroke-width="%.1f" fill="none" stroke-linecap="round" stroke-linejoin="round"/>' % (d, route_w))
        if markers:
            s0 = L.p(*part[0]); s1 = L.p(*part[-1])
            if route_part is None:
                off = 6 if part[0] == part[-1] else 0
                out.append('<circle cx="%.1f" cy="%.1f" r="5.6" fill="url(#%sck)" stroke="#fff" stroke-width="2"/>' % (s1[0] + off, s1[1] + off * .3, u))
            out.append('<circle cx="%.1f" cy="%.1f" r="5.6" fill="#1FA45B" stroke="#fff" stroke-width="2"/>' % (s0[0] - (0 if route_part is None else 0), s0[1]))
    if loc is not None:
        lx, ly = L.p(*loc) if isinstance(loc, tuple) and len(loc) == 2 and loc != 'end' else (None, None)
        if steps and route_part is not None:
            lx, ly = L.p(*steps[route_part - 1])
        out.append('<circle cx="%.1f" cy="%.1f" r="34" fill="var(--loc)" opacity=".14"/><circle cx="%.1f" cy="%.1f" r="34" fill="none" stroke="var(--loc)" stroke-opacity=".35"/>'
                   '<circle cx="%.1f" cy="%.1f" r="9.5" fill="#fff" style="filter:drop-shadow(0 1px 2px rgba(0,0,0,.35))"/><circle cx="%.1f" cy="%.1f" r="6.5" fill="var(--loc)"/>' % (lx, ly, lx, ly, lx, ly, lx, ly))
    out.append('</svg>')
    return ''.join(out)

MAP_CSS = """
.ml{font-family:'Figtree',sans-serif;paint-order:stroke;stroke:var(--m-halo);stroke-width:3px;stroke-linejoin:round}
.ml.area{font-size:10px;font-weight:700;letter-spacing:1.4px;fill:var(--m-label);text-anchor:middle}
.ml.park{font-size:10.5px;font-weight:600;fill:var(--m-park-label);text-anchor:middle}
.ml.water{font-size:10.5px;font-style:italic;font-weight:500;fill:var(--m-water-label);text-anchor:middle;letter-spacing:.3px}
.ml.road{font-size:9.5px;font-weight:600;fill:var(--m-label);text-anchor:middle}
.ml.poi{font-size:10px;font-weight:600;fill:var(--m-label)}
"""

# ------------------------------------------------------------------ page shell
def page(title, theme, body, extra_css=''):
    return ('<!doctype html>\n%s\n<html lang="en" data-theme="%s"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=390,initial-scale=1"><title>%s</title>'
            '<style>%s%s%s</style></head><body><div class="phone">%s</div></body></html>') % (
        SPEC, theme, title, CSS, MAP_CSS, extra_css, body)

def write(name, html):
    with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
        f.write(html)

# ------------------------------------------------------------------ data
# Pune half marathon loop (lattice coords) used in feed + detail
PUNE_ROUTE = [(0, 0), (7, 0), (7, 2), (10, 2), (10, 5), (5, 5), (5, 7), (1, 7), (1, 4), (0, 4), (0, 0)]
PUNE_RIVER = [(-8, -1.5), (-3, -0.7), (2, -0.55), (6, -0.6), (9, -0.4), (12, -1.2), (16, -1.6), (20, -1.0)]
PUNE_PARKS = [(2, 1, 4, 3, 'Bund Garden'), (7, 3, 9, 5, '')]
PUNE_MAJ = [('row', 5, -6, 18), ('col', 5, -6, 14)]

def pune_map(w, h, spacing, labels=True, rw=3.6, center=None, drop=()):
    labs = []
    if labels:
        labs = [('Mula-Mutha River', 4.5, -0.75, 'water', 12), ('KOREGAON PARK', 8.5, 6.3, 'area'),
                ('Bund Garden', 3, 2.15, 'park'), ('KALYANI NAGAR', 8.5, -2.3, 'area'),
                ('North Main Rd', 2.6, 5.0, 'road', 12)]
        labs = [l for l in labs if l[0] not in drop]
    return map_svg(w, h, spacing=spacing, theta=12, seed=3, route=PUNE_ROUTE, center=center, parks=PUNE_PARKS, majors=PUNE_MAJ,
                   river=PUNE_RIVER, river_w=int(spacing * 0.75), labels=labs, route_w=rw,
                   art={'pts': [(-6, 9.5), (0, 9.2), (6, 9.6), (13, 8.4), (18, 8.8)]})

# ================================================================== HOME
def home(theme):
    a1 = avatar('ananya', 40)
    pile = ''.join(avatar(x, 24) for x in ('rohan', 'priya', 'lukas'))
    media = ('<div style="width:304px;height:220px">%s</div>' % pune_map(304, 220, 22, rw=3.4) +
             '<div style="width:84px;height:220px;position:relative">%s</div>' % photo_lake(84, 220, theme))
    card1 = ('<article class="card" style="margin-top:0">'
             '<div class="hd">%s<div class="who"><div class="nm">Ananya Kulkarni</div>'
             '<div class="mt">%s<span>Today at 6:12 AM · Pune, Maharashtra</span></div>'
             '<div class="mt" style="margin-top:0"><span>Garmin Forerunner 265</span></div></div>'
             '<div class="more">%s</div></div>'
             '<h2 class="ttl">Riverside half before the heat</h2>'
             '<p class="desc">Legs felt fresh till 15k, then the humidity won. Chai at Bund Garden after.</p>'
             '<div class="stats num"><div><div class="l">Distance</div><div class="v">21.34<small>km</small></div></div>'
             '<div><div class="l">Pace</div><div class="v">5:28<small>/km</small></div></div>'
             '<div><div class="l">Time</div><div class="v">1h 56m</div></div></div>'
             '<div class="ach"><span style="color:var(--gold)">%s</span><span><b>4 achievements</b>, including a PR for Half-Marathon</span></div>'
             '<div class="media">%s</div>'
             '<div class="soc"><div class="pile">%s</div><span class="sp"><b class="num" style="color:var(--text);font-weight:600">24</b> high-fives</span><span class="num">4 comments</span></div>'
             '<div class="acts"><span>%s</span><span>%s</span><span>%s</span></div>'
             '</article>') % (a1, ic('shoe', 's16'), ic('more', 's20'), ic('trophy', 's18'), media, pile,
                              ic('hi5'), ic('comment'), ic('share'))
    card2 = ('<article class="card">'
             '<div class="hd">%s<div class="who"><div class="nm">Lukas Brenner</div>'
             '<div class="mt">%s<span>Yesterday at 5:48 AM · Bengaluru</span></div>'
             '<div class="mt" style="margin-top:0"><span>Wahoo ELEMNT Roam</span></div></div>'
             '<div class="more">%s</div></div>'
             '<h2 class="ttl">Nandi Hills at sunrise, sub-40 on the climb</h2>'
             '<div class="stats num"><div><div class="l">Distance</div><div class="v">82.6<small>km</small></div></div>'
             '<div><div class="l">Elev Gain</div><div class="v">1,184<small>m</small></div></div>'
             '<div><div class="l">Time</div><div class="v">3h 12m</div></div></div>'
             '</article>') % (avatar('lukas', 40), ic('bike', 's16'), ic('more', 's20'))
    top = ('<header class="top"><h1>Home</h1><span class="btn">%s</span><span class="btn">%s</span>'
           '<span class="btn">%s<i class="dot"></i></span></header>') % (ic('search'), ic('chat'), ic('bell'))
    body = status_bar() + top + '<main class="feed">%s%s</main>' % (card1, card2) + bottom_nav('home') + gesture()
    return page('Home', theme, body)

# ================================================================== MAPS
def route_card(name, place, dist, elev, time_, route, seed, theta, sp):
    m = map_svg(252, 100, spacing=sp, theta=theta, seed=seed, route=route, route_w=3,
                parks=[(1, 1, 3, 2, '')], majors=[('row', 3, -8, 16)])
    return ('<div class="rc"><div class="rm">%s</div><div class="rb"><div class="rn">%s</div>'
            '<div class="rp">%s</div><div class="rs num"><span><b>%s</b> km</span><span><b>%s</b> m</span><span><b>%s</b></span></div></div></div>') % (
        m, name, place, dist, elev, time_)

def maps(theme):
    labels = [('KOREGAON PARK', 3, 2.4, 'area'), ('KALYANI NAGAR', -2, -4.3, 'area'), ('YERAWADA', -5, -7.6, 'area'),
              ('SHIVAJINAGAR', -4.5, 4.6, 'area'), ('Mula-Mutha River', -1.5, -2.9, 'water', 12),
              ('Bund Garden', -3.2, 0.1, 'park'), ('Empress Garden', 6, 10.4, 'park'), ('North Main Rd', 6.4, 0.85, 'road', 12),
              ('Airport Rd', 9.5, -4.6, 'road', 12), ('Pune Station', -6.5, 9.2, 'poi')]
    m = map_svg(390, 844, spacing=30, theta=12, seed=11, anchor=(0, 0), center=(195, 330),
                parks=[(-4, -1, -2, 1, ''), (5, 9, 8, 11, ''), (11, 2, 13, 4, '')],
                majors=[('row', 1, -20, 24), ('col', 2, -24, 24), ('row', -5, -20, 24)],
                river=[(-22, -1.6), (-10, -2.4), (-3, -2.6), (3, -3.4), (8, -2.8), (14, -3.6), (24, -3.0)], river_w=24,
                art={'pts': [(-20, 8.0), (-10, 7.4), (0, 7.8), (8, 6.6), (20, 7.0)]},
                labels=labels, heat=110, heat_focus=(1, 2), heat_corr=[('row', -2, -14, 14), ('row', 1, -14, 14), ('col', 2, -14, 14), ('row', 6, -10, 12), ('col', -3, -10, 8)])
    cards = (route_card('Bund Garden riverside loop', 'Pune · 2.1 km away', '10.2', '38', '55m', [(0, 0), (4, 0), (4, 2), (6, 2), (6, 3), (1, 3), (1, 1), (0, 1), (0, 0)], 21, 12, 18) +
             route_card('Koregaon Park lanes', 'Pune · 0.8 km away', '7.4', '22', '41m', [(0, 0), (3, 0), (3, 1), (5, 1), (5, 3), (2, 3), (2, 2), (0, 2), (0, 0)], 22, 8, 20))
    css = """
.mtop{position:absolute;top:40px;left:12px;right:12px;display:flex;gap:8px;z-index:10}
.srch{flex:1;height:46px;border-radius:999px;background:var(--float);box-shadow:var(--shadow);display:flex;align-items:center;gap:10px;padding:0 16px;color:var(--muted);font-size:15px}
.srch .ic{color:var(--float-text)}
.chips{position:absolute;top:96px;left:12px;right:0;display:flex;gap:8px;z-index:10}
.chips .ic{width:18px;height:18px}
.ctrl{position:absolute;right:12px;top:150px;display:flex;flex-direction:column;gap:10px;z-index:10}
.fab{position:absolute;right:12px;top:436px;z-index:10;height:44px;padding:0 18px 0 14px;border-radius:999px;background:#242428;color:#fff;display:flex;align-items:center;gap:6px;font-weight:700;font-size:15px;box-shadow:var(--shadow)}
[data-theme=dark] .fab{background:#F2F2F4;color:#18181B}
.sheet{position:absolute;left:0;right:0;top:496px;bottom:80px;background:var(--surface);border-radius:16px 16px 0 0;box-shadow:0 -2px 12px rgba(0,0,0,.14);z-index:12;padding-top:8px}
.handle{width:36px;height:4px;border-radius:2px;background:var(--hair-strong);margin:0 auto 10px}
.sh{display:flex;align-items:baseline;padding:0 16px 10px}
.sh .sec{flex:1}
.sub{font-size:13px;color:var(--muted);padding:0 16px 10px;margin-top:-6px}
.rcs{display:flex;gap:12px;padding:0 16px;overflow:hidden}
.rc{flex:none;width:252px;border:1px solid var(--hair);border-radius:8px;overflow:hidden;background:var(--surface)}
.rm{height:100px;overflow:hidden}
.rb{padding:8px 12px 10px}
.rn{font-size:15px;font-weight:700;line-height:1.3}
.rp{font-size:12px;color:var(--muted)}
.rs{display:flex;gap:14px;font-size:13px;color:var(--muted);margin-top:4px}
.rs b{color:var(--text);font-weight:700;font-size:14px}
"""
    body = ('<div style="position:absolute;inset:0">%s</div>' % m + '<div class="scrim"></div>' + status_bar(on_map=True) +
            '<div class="mtop"><div class="srch">%s<span>Search routes, places, segments</span></div><span class="fbtn">%s</span></div>' % (ic('search', 's20'), ic('layers', 's20')) +
            '<div class="chips"><span class="chip dark">%s Run %s</span><span class="chip">Routes</span><span class="chip">Segments</span><span class="chip">Heatmap</span></div>' % (ic('shoe'), ic('caret', 's16')) +
            '<div class="ctrl"><span class="fbtn" style="font-weight:800;font-size:13px">3D</span><span class="fbtn">%s</span></div>' % ic('locate', 's20') +
            '<div class="fab">%s Create route</div>' % ic('plus', 's20') +
            '<section class="sheet"><div class="handle"></div><div class="sh"><span class="sec">Suggested routes</span><span class="lnk">See all</span></div>'
            '<div class="sub">Run · Loops near Koregaon Park · 5–12 km</div><div class="rcs">%s</div></section>' % cards +
            bottom_nav('maps') + gesture())
    return page('Maps', theme, body, css)

# ================================================================== RECORD (pre-start)
BLR_PARKS = [(-3, -2, 1, 3, 'Cubbon Park'), (6, 6, 8, 8, '')]
BLR_LAKE = [(5.6, -5.4), (8.4, -6.2), (10.2, -4.6), (9.6, -2.8), (7.2, -2.6), (5.4, -3.6)]
BLR_LABELS = [('Cubbon Park', -1, 0.6, 'park'), ('Ulsoor Lake', 7.6, -3.2, 'water'), ('SHANTHALA NAGAR', 2.6, 3.6, 'area'),
              ('MG Road', 6.0, 1.82, 'road', -6), ('Vittal Mallya Rd', -1.5, 4.85, 'road', 84), ('ASHOK NAGAR', 5.2, 8.6, 'area'), ('Trinity', 9.6, 2.6, 'poi')]
BLR_MAJ = [('row', 2, -20, 24), ('col', 4, -24, 24)]
BLR_ART = {'pts': [(-14, 9.2), (-4, 8.6), (6, 10.2), (16, 9.0)]}
BLR_ROUTE = [(2, 6), (2, 2), (-3, 2), (-3, -3), (2, -3), (2, -5), (5, -5), (5, -1), (8, -1), (8, 2), (4, 2), (4, 4)]

def rec_css():
    return """
.rtop{position:absolute;top:40px;left:12px;right:12px;display:flex;justify-content:space-between;z-index:10}
.gps{position:absolute;top:44px;left:50%;transform:translateX(-50%);z-index:10;height:36px;padding:0 14px;display:flex;align-items:center;gap:8px;border-radius:999px;background:var(--float);color:var(--float-text);box-shadow:var(--shadow);font-size:14px;font-weight:600}
.gps i{width:8px;height:8px;border-radius:50%;background:var(--ok)}
.rpanel{position:absolute;left:0;right:0;bottom:0;background:var(--surface);border-radius:16px 16px 0 0;box-shadow:0 -2px 14px rgba(0,0,0,.16);z-index:15;padding:20px 0 20px}
.rrow{display:flex;align-items:center;justify-content:space-around;padding:0 18px}
.side{display:flex;flex-direction:column;align-items:center;gap:6px;width:84px;font-size:13px;font-weight:600;color:var(--text)}
.side .c{width:56px;height:56px;border-radius:50%;border:1.5px solid var(--hair-strong);display:grid;place-items:center;color:var(--text)}
.go{width:92px;height:92px;border-radius:50%;background:var(--accent);display:grid;place-items:center;color:#fff;font-weight:800;font-size:19px;letter-spacing:.2px;box-shadow:0 2px 10px rgba(252,76,2,.35)}
.mctl{position:absolute;right:12px;display:flex;flex-direction:column;gap:10px;z-index:10}
"""

def record(theme):
    m = map_svg(390, 844, spacing=36, theta=-8, seed=7, anchor=(2, 4), center=(195, 400), parks=BLR_PARKS, majors=BLR_MAJ,
                lake=BLR_LAKE, labels=BLR_LABELS, art=BLR_ART, loc=(2, 4))
    tools = ''.join('<span style="display:flex;flex-direction:column;align-items:center;gap:4px;font-size:12px;color:var(--muted);font-weight:500;width:72px">%s<span>%s</span></span>' % (ic(k, 's20', extra='style="color:var(--text)"'), t)
                    for k, t in (('sensor', 'Sensor'), ('beacon', 'Beacon'), ('music', 'Music'), ('clock', 'Splits')))
    body = ('<div style="position:absolute;inset:0">%s</div>' % m + '<div class="scrim"></div>' + status_bar(on_map=True) +
            '<div class="rtop"><span class="fbtn">%s</span><span class="fbtn">%s</span></div>' % (ic('down'), ic('gear')) +
            '<div class="gps"><i></i>GPS signal acquired</div>' +
            '<div class="mctl" style="top:430px"><span class="fbtn">%s</span><span class="fbtn">%s</span></div>' % (ic('layers', 's20'), ic('locate', 's20')) +
            '<section class="rpanel"><div style="display:flex;justify-content:space-around;padding:0 18px 18px;border-bottom:1px solid var(--hair);margin-bottom:22px">%s</div>'
            '<div class="rrow"><div class="side"><span class="c">%s</span>Run</div><div class="go">Start</div><div class="side"><span class="c">%s</span>Add route</div></div>'
            '<div style="height:26px"></div></section>' % (tools, ic('shoe'), ic('route')) + gesture())
    return page('Record', theme, body, rec_css())

def record_live(theme):
    m = map_svg(390, 420, spacing=29, theta=-8, seed=7, route=BLR_ROUTE, center=(195, 222), parks=BLR_PARKS, majors=BLR_MAJ,
                lake=BLR_LAKE, labels=[l for l in BLR_LABELS if l[0] != 'VASANTH NAGAR'], art=BLR_ART, route_part=30, loc='end', route_w=4.4, part_center=True)
    css = rec_css() + """
.live{position:absolute;left:0;right:0;top:380px;bottom:0;background:var(--surface);border-radius:16px 16px 0 0;z-index:15;box-shadow:0 -2px 14px rgba(0,0,0,.16)}
.lv-row{display:grid;grid-template-columns:1fr 1fr;border-bottom:1px solid var(--hair)}
.lv-row>div{padding:14px 0 14px;text-align:center}
.lv-row>div+div{border-left:1px solid var(--hair)}
.k{font-size:12px;font-weight:600;color:var(--muted);letter-spacing:.8px;text-transform:uppercase}
.bigv{font-size:44px;font-weight:800;letter-spacing:-1px;line-height:1.05}
.bigv small{font-size:15px;font-weight:700;letter-spacing:0;margin-left:3px;color:var(--text-2)}
.hero{text-align:center;padding:14px 0 12px;border-bottom:1px solid var(--hair)}
.hero .bigv{font-size:80px;letter-spacing:-2px;line-height:1}
.prog{padding:4px 20px 0;display:flex;align-items:center;gap:12px;font-size:13px;color:var(--muted)}
.prog .t{flex:1;height:6px;border-radius:3px;background:var(--hair);overflow:hidden}
.prog .t i{display:block;height:100%;width:27%;background:var(--accent)}
.mini{display:grid;grid-template-columns:repeat(3,1fr);padding:10px 0}
.mini>div{text-align:center}
.mini .v{font-size:20px;font-weight:700}
.mini .v small{font-size:12px;font-weight:600;color:var(--muted);margin-left:2px}
.ctl{position:absolute;left:0;right:0;bottom:34px;display:flex;align-items:center;justify-content:space-around;padding:0 24px}
.ctl .side .c{width:56px;height:56px}
.stop{width:84px;height:84px;border-radius:50%;background:var(--accent);display:grid;place-items:center;box-shadow:0 2px 10px rgba(252,76,2,.35)}
.recpill{position:absolute;top:44px;left:50%;transform:translateX(-50%);z-index:10;height:34px;padding:0 14px;display:flex;align-items:center;gap:8px;border-radius:999px;background:var(--float);color:var(--float-text);box-shadow:var(--shadow);font-size:14px;font-weight:700}
.recpill i{width:9px;height:9px;border-radius:50%;background:#E5322D;box-shadow:0 0 0 3px rgba(229,50,45,.22)}
"""
    body = ('<div style="position:absolute;left:0;right:0;top:0;height:420px">%s</div>' % m + '<div class="scrim"></div>' + status_bar(on_map=True) +
            '<div class="rtop"><span class="fbtn">%s</span><span class="fbtn">%s</span></div>' % (ic('down'), ic('gear')) +
            '<div class="recpill"><i></i>Recording · Run</div>' +
            '<div class="mctl" style="top:302px"><span class="fbtn">%s</span></div>' % ic('locate', 's20') +
            '<section class="live num">'
            '<div class="hero"><div class="k">Time</div><div class="bigv">44:18</div></div>'
            '<div class="lv-row"><div><div class="k">Split pace</div><div class="bigv">5:21<small>/km</small></div></div>'
            '<div><div class="k">Distance</div><div class="bigv">8.27<small>km</small></div></div></div>'
            '<div class="mini"><div><div class="k">Avg pace</div><div class="v">5:21<small>/km</small></div></div>'
            '<div><div class="k">Elev gain</div><div class="v">42<small>m</small></div></div>'
            '<div><div class="k">Heart rate</div><div class="v"><span style="color:var(--hr);vertical-align:-3px;display:inline-block">%s</span> 152<small>bpm</small></div></div></div>'
            '<div class="prog"><span>Km 9</span><span class="t"><i></i></span><span>730 m to split</span></div>'
            '<div class="ctl"><div class="side"><span class="c">%s</span>Lap</div><div class="stop"><svg width="30" height="30" viewBox="0 0 30 30"><rect x="7" y="5" width="5.4" height="20" rx="1.4" fill="#fff"/><rect x="17.6" y="5" width="5.4" height="20" rx="1.4" fill="#fff"/></svg></div>'
            '<div class="side"><span class="c">%s</span>Lock</div></div></section>' % (ic('heart', 's16'), ic('flag'), ic('lock')) + gesture())
    return page('Recording', theme, body, css)

# ================================================================== ACTIVITY DETAIL
def activity(theme):
    m = pune_map(390, 230, 21, rw=4, center=(200, 142), drop=('KALYANI NAGAR',))
    css = """
.atop{position:absolute;top:38px;left:12px;right:12px;display:flex;gap:10px;z-index:10}
.atop .fbtn{width:40px;height:40px}
.body{position:absolute;top:230px;left:0;right:0;bottom:64px;background:var(--surface);overflow:hidden}
.ah{display:flex;gap:12px;align-items:center;padding:12px 16px 0}
.ah .nm{font-size:15px;font-weight:700;line-height:1.25}
.ah .mt{font-size:13px;color:var(--muted);line-height:1.35}
.at{font-size:24px;font-weight:800;letter-spacing:-.4px;line-height:1.2;padding:8px 16px 0}
.grid{display:grid;grid-template-columns:repeat(3,1fr);margin:14px 16px 0;border-top:1px solid var(--hair)}
.grid>div{padding:8px 0 8px;border-bottom:1px solid var(--hair)}
.grid .l{font-size:12px;color:var(--muted);font-weight:500}
.grid .v{font-size:20px;font-weight:700;letter-spacing:-.2px;line-height:1.2}
.grid .v small{font-size:13px;font-weight:600;margin-left:2px}
.achs{display:flex;gap:8px;padding:12px 16px 0;align-items:center;font-size:13px;color:var(--text-2)}
.md{width:22px;height:22px;border-radius:50%;display:grid;place-items:center;font-size:11px;font-weight:800;color:#fff}
.sph{display:flex;align-items:baseline;padding:14px 16px 4px}
.sph .sec{flex:1}
.tbl{padding:0 16px;font-size:14px}
.tr{display:grid;grid-template-columns:30px 62px 1fr 44px 36px;align-items:center;height:26px;gap:8px}
.tr.th{font-size:12px;color:var(--muted);font-weight:500;height:22px}
.tr .b{height:14px;background:var(--bar);border-radius:0 2px 2px 0}
.tr.fast .b{background:var(--bar-fast)}
.tr.fast .p{font-weight:700}
.tr .r{text-align:right;color:var(--text-2)}
.abar{position:absolute;left:0;right:0;bottom:0;height:84px;padding-bottom:20px;background:var(--surface);border-top:1px solid var(--hair);display:flex;justify-content:space-around;align-items:center;z-index:20;color:var(--text)}
.abar span{display:flex;align-items:center;gap:6px;font-size:14px;font-weight:600}
"""
    splits = [(1, '5:34', 2, 151), (2, '5:26', -3, 156), (3, '5:12', 4, 158)]
    secs = lambda s: int(s.split(':')[0]) * 60 + int(s.split(':')[1])
    rows = ''
    for k, p, e, hr in splits:
        # faster = longer bar
        w = min(90, 52 + (340 - secs(p)) * 2.2)
        rows += '<div class="tr %s"><span>%d</span><span class="p">%s</span><span><div class="b" style="width:%d%%"></div></span><span class="r">%+d</span><span class="r">%d</span></div>' % (
            'fast' if p == '5:12' else '', k, p, w, e, hr)
    # pace chart
    rnd = random.Random(5)
    ys = []; v = 0.5
    for i in range(60):
        v += rnd.uniform(-0.12, 0.12); v = min(0.85, max(0.2, v)); ys.append(v)
    W = 326; H = 70
    pace = 'M' + ' L'.join('%.1f %.1f' % (i * W / 59, 6 + ys[i] * (H - 12)) for i in range(60))
    elev = 'M0 %d ' % H + ' '.join('L%.1f %.1f' % (i * W / 59, H - 14 - 12 * math.sin(i / 9.0) - 6 * math.sin(i / 3.3)) for i in range(60)) + ' L%d %d Z' % (W, H)
    chart = ('<div class="sph" style="padding-top:10px"><span class="sec">Pace</span><span class="lnk">Analysis</span></div>'
             '<div style="padding:0 16px"><svg width="358" height="70" viewBox="0 0 358 70"><path d="%s" fill="var(--chart-elev)"/>'
             '<path d="M0 35H330" stroke="var(--hair)" stroke-dasharray="2 3"/><text x="358" y="12" text-anchor="end" style="font:600 10.5px Figtree;fill:var(--muted)">4:45</text><text x="358" y="39" text-anchor="end" style="font:600 10.5px Figtree;fill:var(--muted)">5:30</text><text x="358" y="66" text-anchor="end" style="font:600 10.5px Figtree;fill:var(--muted)">6:15</text><path d="%s" fill="none" stroke="var(--chart-line)" stroke-width="1.8" stroke-linejoin="round"/></svg></div>') % (elev, pace)
    body = ('<div style="position:absolute;top:0;left:0;right:0;height:230px">%s</div>' % m + '<div class="scrim"></div>' + status_bar(on_map=True) +
            '<div class="atop"><span class="fbtn">%s</span><span style="flex:1"></span><span class="fbtn">%s</span><span class="fbtn">%s</span></div>' % (
                ic('back', 's20'), ic('share', 's20'), ic('more', 's20')) +
            '<main class="body">'
            '<div class="ah">%s<div style="flex:1"><div class="nm">Ananya Kulkarni</div><div class="mt">Today at 6:12 AM · Pune, Maharashtra</div>'
            '<div class="mt">Garmin Forerunner 265</div></div></div>'
            '<h1 class="at">Riverside half before the heat</h1>'
            '<div class="grid num"><div><div class="l">Distance</div><div class="v">21.34<small>km</small></div></div>'
            '<div><div class="l">Avg Pace</div><div class="v">5:28<small>/km</small></div></div>'
            '<div><div class="l">Moving Time</div><div class="v">1:56:41</div></div>'
            '<div><div class="l">Elevation Gain</div><div class="v">96<small>m</small></div></div>'
            '<div><div class="l">Calories</div><div class="v">1,412</div></div>'
            '<div><div class="l">Avg Heart Rate</div><div class="v">158<small>bpm</small></div></div></div>'
            '<div class="achs"><span class="md" style="background:var(--gold)">1</span><span class="md" style="background:var(--silver)">2</span>'
            '<span class="md" style="background:var(--bronze)">3</span><span><b style="color:var(--text)">PR</b> Half-Marathon 1:56:12 · 3 more</span></div>'
            '<div class="sph"><span class="sec">Splits</span><span class="lnk">All 22 splits</span></div>'
            '<div class="tbl num"><div class="tr th"><span>Km</span><span>Pace</span><span></span><span class="r">Elev</span><span class="r">HR</span></div>%s</div>'
            '%s</main>' % (avatar('ananya', 44), rows, chart) +
            '<div class="abar"><span>%s <span class="num">24</span></span><span>%s <span class="num">4</span></span><span>%s</span></div>' % (
                ic('hi5'), ic('comment'), ic('share')) + gesture())
    return page('Activity', theme, body, css)

# ================================================================== YOU / PROGRESS
def you(theme):
    css = """
.ytop{position:absolute;top:30px;left:0;right:0;height:52px;display:flex;align-items:center;padding:0 8px 0 16px;gap:12px;background:var(--bg);z-index:20}
.ytop h1{font-size:24px;font-weight:800;flex:1;letter-spacing:-.3px}
.ytop .btn{width:44px;height:44px;display:grid;place-items:center}
.ybody{position:absolute;top:82px;left:0;right:0;bottom:80px;overflow:hidden;background:var(--gap)}
.blk{background:var(--surface);padding:0 16px 16px}
.sports{display:flex;gap:10px;padding:16px 0 4px}
.sp{width:48px;height:48px;border-radius:50%;border:1.5px solid var(--hair-strong);display:grid;place-items:center;color:var(--muted)}
.sp.on{border:2px solid var(--accent);color:var(--accent)}
.wk{font-size:13px;color:var(--muted);margin-top:14px;font-weight:500}
.tri{display:flex;margin-top:4px}
.tri>div{flex:1}
.tri .l{font-size:12px;color:var(--muted);font-weight:500}
.tri .v{font-size:26px;font-weight:800;letter-spacing:-.5px;line-height:1.15}
.tri .v small{font-size:14px;font-weight:600;margin-left:2px;letter-spacing:0}
.ttl2{display:flex;align-items:baseline;padding:16px 0 8px}
.ttl2 .sec{flex:1}
.wdays{display:grid;grid-template-columns:repeat(7,1fr);text-align:center;font-size:12px;color:var(--muted);font-weight:600}
.wdays .c{height:46px;display:grid;place-items:center}
.wdays .d{border-radius:50%;background:var(--accent)}
.wdays .e{width:6px;height:6px;border-radius:50%;background:var(--hair-strong)}
.be{display:flex;align-items:center;height:48px;border-top:1px solid var(--hair);gap:12px;font-size:15px}
.be .n{flex:1;font-weight:600}
.be .t{font-weight:700}
.be .dt{font-size:13px;color:var(--muted);width:70px;text-align:right}
"""
    vals = [18.2, 24.6, 21.0, 30.4, 27.8, 12.5, 33.1, 35.6, 29.0, 38.4, 41.2, 32.4]
    W, H, T, B = 314, 150, 10, 24
    xs = [8 + i * (W - 16) / 11 for i in range(12)]
    ys = [T + (H - T - B) * (1 - v / 50.0) for v in vals]
    line = 'M' + ' L'.join('%.1f %.1f' % (x, y) for x, y in zip(xs, ys))
    area = line + ' L%.1f %d L%.1f %dZ' % (xs[-1], H - B, xs[0], H - B)
    grid = ''.join('<path d="M0 %.1fH%d" stroke="var(--hair)" %s/><text x="%d" y="%.1f" class="ax" text-anchor="start">%s</text>' % (
        T + (H - T - B) * (1 - g / 50.0), W, 'stroke-dasharray="2 3"' if g else '', W + 6, T + (H - T - B) * (1 - g / 50.0) + 4, '%d km' % g) for g in (0, 25, 50))
    dots = ''.join('<circle cx="%.1f" cy="%.1f" r="3.2" fill="var(--surface)" stroke="var(--accent)" stroke-width="2"/>' % (x, y) for x, y in zip(xs[:-1], ys[:-1]))
    dots += '<path d="M%.1f %.1fV%d" stroke="var(--accent)" stroke-width="1.4" stroke-dasharray="2 2"/><circle cx="%.1f" cy="%.1f" r="6" fill="var(--accent)" stroke="var(--surface)" stroke-width="2.5"/>' % (xs[-1], ys[-1], H - B, xs[-1], ys[-1])
    months = ''.join('<text x="%.1f" y="%d" class="ax" text-anchor="middle">%s</text>' % (xs[i], H - 6, t) for i, t in ((1, 'AUG'), (5, 'SEP'), (9, 'OCT')))
    chart = ('<svg width="358" height="%d" viewBox="0 0 358 %d" style="margin-top:12px"><style>.ax{font:600 10.5px Figtree,sans-serif;fill:var(--muted);letter-spacing:.4px}</style>'
             '<defs><linearGradient id="ar" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--accent)" stop-opacity=".28"/><stop offset="1" stop-color="var(--accent)" stop-opacity="0"/></linearGradient></defs>'
             '%s<path d="%s" fill="url(#ar)"/><path d="%s" fill="none" stroke="var(--accent)" stroke-width="2.2" stroke-linejoin="round"/>%s%s</svg>') % (H, H, grid, area, line, dots, months)
    week = [('M', 6.2), ('T', 0), ('W', 10.4), ('T', 0), ('F', 5.0), ('S', 0), ('S', 10.8)]
    wd = ''.join('<div><div class="c">%s</div>%s</div>' % (
        ('<span class="d" style="width:%dpx;height:%dpx"></span>' % (14 + d * 2.4, 14 + d * 2.4)) if d else '<span class="e"></span>', n) for n, d in week)
    body = (status_bar() +
            '<header class="ytop">%s<h1>You</h1><span class="btn">%s</span><span class="btn">%s</span></header>' % (avatar('arjun', 32), ic('search'), ic('gear')) +
            '<main class="ybody"><div class="blk" style="padding-bottom:12px"><div class="tabs" style="margin:0 -16px"><a class="on">Progress</a><a>Activities</a></div>'
            '<div class="sports"><span class="sp on">%s</span><span class="sp">%s</span><span class="sp">%s</span></div>'
            '<div class="wk">This week · 27 Oct – 2 Nov</div>'
            '<div class="tri num"><div><div class="l">Distance</div><div class="v">32.4<small>km</small></div></div>'
            '<div><div class="l">Time</div><div class="v">2h 58m</div></div><div><div class="l">Elev Gain</div><div class="v">214<small>m</small></div></div></div>'
            '%s<div style="font-size:13px;color:var(--muted);margin-top:2px">Past 12 weeks · avg <b class="num" style="color:var(--text)">28.7 km</b> / week</div></div>'
            '<div class="blk" style="margin-top:8px"><div class="ttl2"><span class="sec">Training log</span><span class="lnk">View</span></div><div class="wdays num">%s</div></div>'
            '<div class="blk" style="margin-top:8px;padding-bottom:0"><div class="ttl2"><span class="sec">Best efforts</span><span class="lnk">All</span></div>'
            '<div class="be num"><span class="md" style="width:22px;height:22px;border-radius:50%%;background:var(--gold);color:#fff;font-size:11px;font-weight:800;display:grid;place-items:center">1</span><span class="n">5K</span><span class="t">24:12</span><span class="dt">12 Oct</span></div>'
            '<div class="be num"><span style="width:22px"></span><span class="n">10K</span><span class="t">51:40</span><span class="dt">28 Sep</span></div></div>'
            '</main>' % (ic('shoe'), ic('bike'), ic('walk'), chart, wd) + bottom_nav('you') + gesture())
    return page('You', theme, body, css)

if __name__ == '__main__':
    for th in ('light', 'dark'):
        _av[0] = 0; _map[0] = 0
        write('home-%s.html' % th, home(th))
        write('maps-%s.html' % th, maps(th))
        write('record-%s.html' % th, record(th))
        write('record-live-%s.html' % th, record_live(th))
        write('activity-%s.html' % th, activity(th))
        write('you-%s.html' % th, you(th))
    print('ok')
