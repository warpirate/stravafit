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
 TYPE     display 84/1 800 (live Time only) . stat-xl 52/1.05 700 . stat-l 26/1.1 700
          stat-m 20/1.15 700 (stat-m and up tracked -0.02em) . title-xl 22/1.2 800
          top-bar title 21/700 . title-l 19/1.25 700
          title-m 16/1.3 700 . body 15/1.45 400 . body-s 14/1.4
          meta 13/1.35 . label 12/1.3 500 . nav 11/1.2 600
 SPACE    4-pt grid: 4 8 12 16 20 24 32 . screen gutter 16
 RADII    0 cards (edge-to-edge) . 4 buttons/inputs . 8 thumbnails
          16 sheet top . 999 avatars, map chips, round buttons
 ELEV     flat; shadow only on things floating over a map
 LIGHT    bg #FFFFFF . gap #EEEEF0 . text #242428 . muted #6D6D78
          hair #E5E5EA . accent #FC5200 (fills/icons/route)
          accent-text #CC3D00 (small orange text, 5.0:1)
 DARK     bg #121214 . gap #000000 . surface #1C1C1F . raised #2A2A2F
          text #F2F2F4 . muted #A3A3AD . hair #2E2E34 . accent #FC5200
          accent-text #FF6A2B
 ICONS    own 24px line set, 1.75 stroke, round caps/joins; active nav = filled
 CASING   stat labels in Title Case everywhere (Distance, Avg Pace, Moving Time,
          Elev Gain, Heart Rate); chart month ticks in caps (AUG SEP OCT).
 AVATARS  photo-like crops without faces (back-lit profiles, scenes), grain + blur
 MAPS     sparse minor streets (long runs, T-junctions, empty landuse); heat only on
          drawn streets; floating controls never cover the route
 NAMING   "High-five" (never kudos). No third-party logo or assets.
====================================================================
-->"""

CSS = r"""
@import url('https://fonts.googleapis.com/css2?family=Figtree:wght@400;500;600;700;800&display=block');
:root{
 --bg:#FFFFFF;--gap:#EEEEF0;--surface:#FFFFFF;--surface-2:#F5F5F7;--raised:#FFFFFF;
 --text:#242428;--text-2:#45454E;--muted:#6D6D78;--hair:#E5E5EA;--hair-strong:#CBCBD3;
 --accent:#FC5200;--accent-press:#E04A00;--accent-text:#CC3D00;--on-accent:#FFFFFF;
 --accent-area:rgba(252,82,0,.14);
 --ok:#16884A;--gold:#D6A01E;--silver:#9DA5AF;--bronze:#C98A55;--on-medal:#242428;--hr:#E0245E;
 --bar:#C3CCD8;--bar-fast:#FC5200;--chart-elev:#E3E3E8;--chart-line:#3C78D8;
 --float:#FFFFFF;--float-text:#242428;--shadow:0 1px 2px rgba(0,0,0,.16),0 3px 10px rgba(0,0,0,.10);
 --pill:#242428;--loc:#2F74F0;--status:#242428;
 --m-land:#F2F1EC;--m-block:#E9E7E0;--m-park:#CFE5BF;--m-water:#A7D0EA;--m-road:#FFFFFF;--m-case:#E2DED5;
 --m-major:#FFFFFF;--m-major-case:#C7C0B2;--m-art:#FCE7B0;--m-art-case:#DDBA6E;--m-label:#5A5B61;
 --m-halo:#F4F3EF;--m-water-label:#33689A;--m-park-label:#3B6A2E;--m-path:#AFCB9A;--route-case:#FFFFFF;
 --heat:#EE3F0A;--heat-core:#C42600;
}
[data-theme=dark]{
 --bg:#121214;--gap:#000000;--surface:#1C1C1F;--surface-2:#242428;--raised:#2A2A2F;
 --text:#F2F2F4;--text-2:#D2D2D8;--muted:#A3A3AD;--hair:#2E2E34;--hair-strong:#43434B;
 --accent:#FC5200;--accent-press:#FF6A2B;--accent-text:#FF6A2B;--on-accent:#FFFFFF;
 --accent-area:rgba(252,82,0,.10);
 --ok:#3CC47A;--gold:#E7B43A;--silver:#B4BBC4;--bronze:#C98A55;--on-medal:#242428;--hr:#FF5C8A;
 --bar:#424B59;--bar-fast:#FC5200;--chart-elev:#2C2C33;--chart-line:#6FA2F5;
 --float:#2A2A2F;--float-text:#F2F2F4;--shadow:0 1px 2px rgba(0,0,0,.6),0 4px 14px rgba(0,0,0,.45);
 --pill:#F2F2F4;--loc:#4C8DFF;--status:#F2F2F4;
 --m-land:#1A1C20;--m-block:#1F2227;--m-park:#18291E;--m-water:#11314A;--m-road:#2B2F36;--m-case:#1A1C20;
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
.sb{position:absolute;left:0;right:0;top:0;height:30px;display:flex;align-items:center;justify-content:space-between;padding:0 20px 0 22px;font-size:14px;font-weight:500;color:var(--status);z-index:30}
.sb .r{display:flex;gap:6px;align-items:center}
.sb.on-map{color:#1d1d20}
.scrim{position:absolute;left:0;right:0;top:0;height:96px;z-index:5;background:linear-gradient(var(--m-land) 0,color-mix(in srgb,var(--m-land) 70%,transparent) 34px,transparent 96px);pointer-events:none}
[data-theme=dark] .sb.on-map{color:#F2F2F4}
.gb{position:absolute;left:0;right:0;bottom:0;height:20px;z-index:40;display:flex;justify-content:center;align-items:center}
.gb i{display:block;width:108px;height:4px;border-radius:2px;background:var(--pill);opacity:.9}
/* top app bar */
.top{position:absolute;top:30px;left:0;right:0;height:52px;display:flex;align-items:center;padding:0 8px 0 16px;background:var(--bg);border-bottom:1px solid var(--hair);z-index:20}
.top h1{font-size:21px;font-weight:700;letter-spacing:-.2px;flex:1}
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
.ttl{font-size:19px;font-weight:700;letter-spacing:-.2px;line-height:1.25;padding:10px 16px 0}
.desc{font-size:15px;line-height:1.4;padding:4px 16px 0;color:var(--text-2)}
.stats{display:flex;padding:12px 16px 0;gap:0}
.stats>div{margin-right:34px}
.stats>div:last-child{margin-right:0}
.stats .l{font-size:12px;color:var(--muted);font-weight:500;line-height:1.3}
.stats .v{font-size:20px;font-weight:700;letter-spacing:-.02em;line-height:1.2}
.stats .v small{font-size:13px;font-weight:600;margin-left:2px;letter-spacing:0}
.ach{display:flex;align-items:center;gap:6px;padding:12px 16px 0;font-size:14px;color:var(--text-2)}
.ach b{font-weight:700;color:var(--text)}
.media{position:relative;margin-top:12px;height:236px;overflow:hidden}
.media>*{flex:none}
.soc{display:flex;align-items:center;padding:8px 16px 0;gap:10px;font-size:13px;color:var(--muted)}
.pile{display:flex}
.pile .av{box-shadow:0 0 0 2px var(--surface)}
.pile .av+.av{margin-left:-7px}
.soc .sp{flex:1}
.acts{display:flex;justify-content:space-around;height:48px;align-items:center;margin-top:4px;color:var(--text)}
.acts span{display:flex;align-items:center;justify-content:center;width:72px;height:44px}
.acts .ic{width:22px;height:22px;stroke-width:1.6}
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
 'share': '<circle cx="17.6" cy="5.6" r="2.4"/><circle cx="6.4" cy="12" r="2.4"/><circle cx="17.6" cy="18.4" r="2.4"/><path d="m8.5 10.8 7-4M8.5 13.2l7 4"/>',
 'check': '<path d="m5.4 12.4 4.2 4.2 9-9"/>',
 'flame': '<path d="M12 21c-3.9 0-6.6-2.6-6.6-6.2 0-3.4 2.4-5.4 3.8-8 .5 1.8 1.4 3 2.6 3.6-.2-2.6.8-5 2.8-6.6.4 3 4 5.4 4 10 0 4.4-2.6 7.2-6.6 7.2z"/>',
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


# ================================================================== chrome
def status_bar(on_map=False, time='8:24'):
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

_md = [0]
def medal(rank, size=24):
    """ribbon medal glyph; dark numeral on metal (AA on all three metals)"""
    _md[0] += 1
    metal = {1: 'var(--gold)', 2: 'var(--silver)', 3: 'var(--bronze)'}[rank]
    return ('<svg width="%d" height="%d" viewBox="0 0 24 26" style="flex:none;display:block">'
            '<path d="M6.2 1h4.6l2.6 7.6-4 1.6z" fill="var(--accent)"/><path d="M17.8 1h-4.6l-2.6 7.6 4 1.6z" fill="#B23A00"/>'
            '<circle cx="12" cy="16" r="8.6" fill="%s"/><circle cx="12" cy="16" r="6.9" fill="none" stroke="#000" stroke-opacity=".14"/>'
            '<text x="12" y="16.3" text-anchor="middle" dominant-baseline="central" style="font:800 10.5px Figtree,sans-serif;fill:var(--on-medal)">%d</text></svg>') % (size, int(size * 26 / 24), metal, rank)

# ================================================================== avatars (photo-like crops)
import sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from avatars import avatar, _av

# ================================================================== map engine v2 (world in km, x east / y south)
def smooth_pts(pts, closed=False):
    return smooth_path([tuple(q) for q in pts], closed)

def poly_len(pts):
    return sum(math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(pts, pts[1:]))

def gen_minor(p, seed, mult=1.8):
    """sparse street grid: long straight runs of 3-8 blocks, T-junctions, gaps; no stubs"""
    a = math.radians(p['ang']); spu = p['sp'] * mult; spv = spu * p.get('asp', 1.6)
    xs = [q[0] for q in p['poly']]; ys = [q[1] for q in p['poly']]
    cx = (min(xs) + max(xs)) / 2; cy = (min(ys) + max(ys)) / 2
    R = math.hypot(max(xs) - min(xs), max(ys) - min(ys)) / 2 + spu
    ni = int(R / spu) + 1; nj = int(R / spv) + 1
    rng = random.Random(seed)
    org = p.get('org', 0) * .35
    ca, sa = math.cos(a), math.sin(a)
    # per-line offsets (whole streets shift together, so they stay straight)
    off_i = {i: rng.uniform(-.18, .18) * spu for i in range(-ni, ni + 2)}
    off_j = {j: rng.uniform(-.15, .15) * spv for j in range(-nj, nj + 2)}
    nodes = {}
    for i in range(-ni, ni + 1):
        for j in range(-nj, nj + 1):
            u = i * spu + off_i[i]; v = j * spv + off_j[j]
            if org:
                u += org * spu * math.sin(j * .35 + seed); v += org * spv * .5 * math.sin(i * .3 + seed * 2)
            nodes[(i, j)] = (cx + u * ca - v * sa, cy + u * sa + v * ca)
    runs = []
    for axis in (0, 1):
        outer = range(-nj, nj + 1) if axis == 0 else range(-ni, ni + 1)
        inner = list(range(-ni, ni + 1) if axis == 0 else range(-nj, nj + 1))
        for o in outer:
            if rng.random() < (.25 if axis == 0 else .35):
                continue                      # whole street missing -> larger blocks
            t = inner[0]
            on = rng.random() < .7
            while t < inner[-1]:
                ln = rng.randint(3, 8) if on else rng.randint(1, 3)
                if on:
                    run = []
                    for k in range(t, min(t + ln, inner[-1]) + 1):
                        run.append(nodes[(k, o)] if axis == 0 else nodes[(o, k)])
                    if len(run) > 1:
                        runs.append(run)
                t += ln; on = not on
    return runs

def cr_sample(pts, n=10):
    """dense points along exactly the curve smooth_path() draws (so heat sits on the road centreline)"""
    P = [tuple(q) for q in pts]
    if len(P) < 3:
        out = []
        for a, b in zip(P, P[1:]):
            out += [(a[0] + (b[0] - a[0]) * k / n, a[1] + (b[1] - a[1]) * k / n) for k in range(n)]
        return out + [P[-1]]
    out = []; m = len(P)
    for k in range(m - 1):
        p0 = P[k - 1] if k > 0 else P[k]; p1 = P[k]; p2 = P[k + 1]; p3 = P[k + 2] if k + 2 < m else P[k + 1]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        for j in range(n):
            t = j / n; mt = 1 - t
            out.append(tuple(mt ** 3 * p1[i] + 3 * mt * mt * t * c1[i] + 3 * mt * t * t * c2[i] + t ** 3 * p2[i] for i in (0, 1)))
    return out + [P[-1]]

def corridor_pts(W, key, a=None, b=None):
    for r in W.roads:
        if r[0] == key or (len(r) > 3 and r[3] == key):
            S = cr_sample(r[1])
            if a is None:
                return S
            near = lambda q: min(range(len(S)), key=lambda k: (S[k][0] - q[0]) ** 2 + (S[k][1] - q[1]) ** 2)
            i, j = sorted((near(a), near(b)))
            return S[i:j + 1]
    raise KeyError(key)

_minor_cache = {}

class World:
    def __init__(s, **kw):
        s.__dict__.update(kw)
    def minor(s, idx, mult=1.8):
        key = (s.name, idx, mult)
        if key not in _minor_cache:
            _minor_cache[key] = gen_minor(s.patches[idx], s.seed * 31 + idx, mult)
        return _minor_cache[key]

def text_w(t, fs, kind):
    if kind == 'area':
        return len(t) * fs * .70 + len(t) * 1.4
    return len(t) * fs * .53

_m = [0]
def render_map(W, w, h, scale, center, route=None, live=False, heat=False, avoid=(), labels=True, rw=3.6,
               markers=True, loc=None, label_scale=1.0, drop=(), fade_bottom=False, you_dot=None, thumb=False, extra_labels=()):
    """W world, w/h px, scale px per km, center (km). avoid: list of (x0,y0,x1,y1) px rects labels must not touch"""
    _m[0] += 1; u = 'm%d' % _m[0]
    cx, cy = center
    T = lambda q: ((q[0] - cx) * scale + w / 2, (q[1] - cy) * scale + h / 2)
    Tl = lambda pts: [T(q) for q in pts]
    inview = lambda P, m=24: -m <= P[0] <= w + m and -m <= P[1] <= h + m
    out = ['<svg class="map" width="%d" height="%d" viewBox="0 0 %d %d">' % (w, h, w, h)]
    defs = ['<pattern id="%sck" width="4" height="4" patternUnits="userSpaceOnUse"><rect width="4" height="4" fill="#fff"/><rect width="2" height="2" fill="#111"/><rect x="2" y="2" width="2" height="2" fill="#111"/></pattern>' % u,
            '<filter id="%sg" x="-20%%" y="-20%%" width="140%%" height="140%%"><feGaussianBlur stdDeviation="2.4"/></filter>' % u]
    body = ['<rect width="%d" height="%d" fill="var(--m-land)"/>' % (w, h)]
    # width tiers in px (zoom aware)
    w_min = max(.55, min(2.6, scale * .0105))
    w_sec = max(1.2, min(4.6, scale * .022))
    w_maj = max(2.0, min(6.0, scale * .032))
    w_art = max(2.6, min(7.5, scale * .04))
    # landuse + minor streets per neighbourhood patch
    minor_segs = []
    for idx, p in enumerate(W.patches):
        pp = Tl(p['poly'])
        dpoly = 'M' + ' L'.join('%.1f %.1f' % q for q in pp) + 'Z'
        defs.append('<clipPath id="%sp%d"><path d="%s"/></clipPath>' % (u, idx, dpoly))
        body.append('<path d="%s" fill="var(--m-built)"/>' % dpoly)
        ds = []
        for run in W.minor(idx, 1.3 if scale >= 80 else 1.8):
            P = Tl(run)
            if not any(inview(q) for q in P):
                continue
            dd_ = smooth_path(P) if (p.get('org') and len(P) > 2) else 'M' + ' L'.join('%.1f %.1f' % q for q in P)
            ds.append(dd_)
            minor_segs.append((idx, P, dd_))
        if ds:
            d = ' '.join(ds)
            g = '<g clip-path="url(#%sp%d)" fill="none" stroke-linecap="round">' % (u, idx)
            if w_min > 1.3:
                g += '<path d="%s" stroke="var(--m-case)" stroke-width="%.2f"/>' % (d, w_min + 1.1)
            g += '<path d="%s" stroke="var(--m-road)" stroke-width="%.2f"/></g>' % (d, w_min)
            body.append(g)
    heat_at = len(body)
    for poly in getattr(W, 'voids', []):
        body.append('<path d="M%s Z" fill="var(--m-land)"/>' % ' L'.join('%.1f %.1f' % q for q in Tl(poly)))
    for poly, name, _ in W.parks:
        body.append('<path d="%s" fill="var(--m-park)"/>' % smooth_path(Tl(poly), True))
        if scale > 90 and not thumb:
            P = Tl(poly); c = (sum(q[0] for q in P) / len(P), sum(q[1] for q in P) / len(P))
            body.append('<path d="M%.1f %.1f Q%.1f %.1f %.1f %.1f M%.1f %.1f Q%.1f %.1f %.1f %.1f" stroke="var(--m-path)" stroke-width="1.2" stroke-dasharray="3 2.4" fill="none"/>' % (
                P[0][0] * .7 + c[0] * .3, P[0][1] * .7 + c[1] * .3, c[0], c[1] + 4, P[2][0] * .7 + c[0] * .3, P[2][1] * .7 + c[1] * .3,
                P[1][0] * .7 + c[0] * .3, P[1][1] * .7 + c[1] * .3, c[0] - 3, c[1], P[-1][0] * .7 + c[0] * .3, P[-1][1] * .7 + c[1] * .3))
    for pts, wkm in W.rivers:
        body.append('<path d="%s" stroke="var(--m-water)" stroke-width="%.1f" stroke-linecap="round" stroke-linejoin="round" fill="none"/>' % (
            smooth_path(Tl(pts)), max(5, wkm * scale)))
    for poly in W.lakes:
        body.append('<path d="%s" fill="var(--m-water)"/>' % smooth_path(Tl(poly), True))
    if getattr(W, 'rail', None):
        d = smooth_path(Tl(W.rail))
        body.append('<path d="%s" stroke="var(--m-rail)" stroke-width="%.1f" fill="none"/>' % (d, 1.6 if scale > 80 else 1.1))
        if scale > 60:
            body.append('<path d="%s" stroke="var(--m-rail)" stroke-width="5" stroke-dasharray="1 5" fill="none"/>' % d)
    roads = {'sec': [], 'maj': [], 'art': []}
    for r in W.roads:
        roads[r[2]].append((r[0], Tl(r[1])))
    for tier, wd, col, case in (('sec', w_sec, 'var(--m-road)', 'var(--m-case)'), ('maj', w_maj, 'var(--m-major)', 'var(--m-major-case)'),
                                ('art', w_art, 'var(--m-art)', 'var(--m-art-case)')):
        if not roads[tier]:
            continue
        d = ' '.join(smooth_path(P) for _, P in roads[tier])
        body.append('<path d="%s" stroke="%s" stroke-width="%.1f" fill="none" stroke-linecap="round" stroke-linejoin="round"/>' % (d, case, wd + 1.8))
        body.append('<path d="%s" stroke="%s" stroke-width="%.1f" fill="none" stroke-linecap="round" stroke-linejoin="round"/>' % (d, col, wd))
    # heatmap: faint heat = whole street runs (>= 3 blocks) that lie on drawn streets; hot corridors snap to road centrelines
    if heat:
        hr = random.Random(W.seed + 99)
        fx, fy = T(W.heat_focus)
        faint, mid = {}, {}
        for idx, P, dd_ in minor_segs:
            if len(P) < 4:
                continue
            mx = sum(q[0] for q in P) / len(P); my = sum(q[1] for q in P) / len(P)
            dd = math.hypot(mx - fx, my - fy) / (scale * 1.6)
            pr = .75 * math.exp(-dd * dd)
            r = hr.random()
            if r < pr * .3:
                mid.setdefault(idx, []).append(dd_)
            elif r < pr:
                faint.setdefault(idx, []).append(dd_)
        cor = [(Tl(corridor_pts(W, *c[:-1])), c[-1]) for c in W.corridors]
        poly = lambda P: 'M' + ' L'.join('%.1f %.1f' % q for q in P)
        hot = ' '.join(poly(P) for P, lv in cor if lv >= 3)
        warm = ' '.join(poly(P) for P, lv in cor if lv < 3)
        fg = ['<g fill="none" stroke-linecap="round" stroke-linejoin="round">']
        for idx in set(faint) | set(mid):
            fg.append('<g clip-path="url(#%sp%d)"><path d="%s" stroke="var(--heat)" stroke-width="1" opacity="var(--heat-faint)"/>'
                        '<path d="%s" stroke="var(--heat)" stroke-width="1.3" opacity=".5"/></g>' % (u, idx, ' '.join(faint.get(idx, [])), ' '.join(mid.get(idx, []))))
        fg.append('</g>')
        body.insert(heat_at, ''.join(fg))   # under water and parks: faint heat lives on the street layer
        body.append('<g fill="none" stroke-linecap="round" stroke-linejoin="round">')
        body.append('<path d="%s %s" stroke="var(--heat)" stroke-width="7" opacity=".30" filter="url(#%sg)"/>' % (hot, warm, u))
        body.append('<path d="%s" stroke="var(--heat)" stroke-width="1.8" opacity=".85"/>' % warm)
        body.append('<path d="%s" stroke="var(--heat)" stroke-width="3" opacity=".9"/>' % hot)
        body.append('<path d="%s" stroke="var(--heat-core)" stroke-width="1.4"/>' % hot)
        body.append('</g>')
    # route
    rpts = []
    if route:
        P = Tl(route)
        rpts = P
        d = smooth_path(P)
        body.append('<path d="%s" stroke="var(--route-case)" stroke-width="%.1f" fill="none" stroke-linecap="round" stroke-linejoin="round"/>' % (d, rw + 3))
        body.append('<path d="%s" stroke="var(--accent)" stroke-width="%.1f" fill="none" stroke-linecap="round" stroke-linejoin="round"/>' % (d, rw))
    # labels (above the route, with halo; skipped when they would hit the route, the frame edge, or UI)
    placed = []
    def blocked(x, y, tw, th, rot):
        cr, sr = math.cos(math.radians(rot)), math.sin(math.radians(rot))
        corners = [(x + cr * dx_ - sr * dy_, y + sr * dx_ + cr * dy_) for dx_ in (-tw / 2, tw / 2) for dy_ in (-th / 2, th / 2)]
        if any(not (14 <= c[0] <= w - 14 and 4 <= c[1] <= h - 4) for c in corners):
            return True
        bx0 = min(c[0] for c in corners); bx1 = max(c[0] for c in corners); by0 = min(c[1] for c in corners); by1 = max(c[1] for c in corners)
        for (ax0, ay0, ax1, ay1) in list(avoid) + placed:
            if bx0 < ax1 and bx1 > ax0 and by0 < ay1 and by1 > ay0:
                return True
        # route proximity, sampled densely along the polyline
        for a, b in zip(rpts, rpts[1:]):
            n = max(1, int(math.hypot(b[0] - a[0], b[1] - a[1]) / 3))
            for k in range(n + 1):
                px = a[0] + (b[0] - a[0]) * k / n - x; py = a[1] + (b[1] - a[1]) * k / n - y
                uu = px * cr + py * sr; vv = -px * sr + py * cr
                if abs(uu) < tw / 2 + 3 and abs(vv) < th / 2 + 3:
                    return True
        return False
    lab = []
    if labels:
        items = []
        for name, pt, kind in list(extra_labels) + list(W.labels):
            if name in drop:
                continue
            items.append((name, T(pt), kind, 0))
        for r in W.roads:
            name, pts, tier = r[:3]
            if not name or name in drop or tier == 'sec' and scale < 80:
                continue
            P = Tl(pts)
            # candidate positions along the road
            L_ = poly_len(P)
            for frac in (.5, .35, .65, .22, .78):
                acc = 0; target = L_ * frac
                for a, b in zip(P, P[1:]):
                    sl = math.hypot(b[0] - a[0], b[1] - a[1])
                    if acc + sl >= target:
                        t = (target - acc) / sl if sl else 0
                        x = a[0] + (b[0] - a[0]) * t; y = a[1] + (b[1] - a[1]) * t
                        ang = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))
                        if ang > 90: ang -= 180
                        if ang < -90: ang += 180
                        items.append((name, (x, y), 'road', ang, frac))
                        break
                    acc += sl
        done_roads = set()
        for it in items:
            name, (x, y), kind, rot = it[:4]
            if name in done_roads:
                continue
            fs = {'area': 10, 'park': 10.5, 'water': 10.5, 'road': 9.5, 'poi': 10}[kind] * label_scale
            tw = text_w(name.upper() if kind == 'area' else name, fs, kind) + (10 if kind == 'poi' else 0)
            th = fs + 3
            offs = [(0, 0)] if kind == 'road' else [(0, 0), (0, -13), (0, 13), (0, -24), (0, 24), (-18, 0), (18, 0)]
            for ox, oy in offs:
                if not blocked(x + ox, y + oy, tw, th, rot):
                    x += ox; y += oy
                    cr = abs(math.cos(math.radians(rot))); sr = abs(math.sin(math.radians(rot)))
                    bw = tw * cr + th * sr; bh = tw * sr + th * cr
                    placed.append((x - bw / 2 - 2, y - bh / 2 - 2, x + bw / 2 + 2, y + bh / 2 + 2))
                    if kind == 'poi':
                        lab.append('<circle cx="%.1f" cy="%.1f" r="3.2" fill="var(--m-poi)" stroke="var(--m-halo)" stroke-width="1.2"/>'
                                   '<text x="%.1f" y="%.1f" class="ml poi" dominant-baseline="central">%s</text>' % (x - tw / 2 + 3, y, x - tw / 2 + 10, y, name))
                    else:
                        tr = ' transform="rotate(%.1f %.1f %.1f)"' % (rot, x, y) if rot else ''
                        lab.append('<text x="%.1f" y="%.1f" class="ml %s" dominant-baseline="central"%s>%s</text>' % (x, y, kind, tr, name))
                    done_roads.add(name)
                    break
    body.append('<g class="labels">%s</g>' % ''.join(lab))
    if route and markers:
        s0, s1 = rpts[0], rpts[-1]
        if not live:
            loop = math.hypot(s1[0] - s0[0], s1[1] - s0[1]) < 8
            body.append('<circle cx="%.1f" cy="%.1f" r="5.6" fill="url(#%sck)" stroke="#fff" stroke-width="2"/>' % (s1[0] + (7 if loop else 0), s1[1] + (2 if loop else 0), u))
        body.append('<circle cx="%.1f" cy="%.1f" r="5.6" fill="#1FA45B" stroke="#fff" stroke-width="2"/>' % s0)
    if live and rpts:
        loc = rpts[-1]
    elif loc is not None:
        loc = T(loc)
    if loc is not None:
        lx, ly = loc
        body.append('<circle cx="%.1f" cy="%.1f" r="30" fill="var(--loc)" opacity=".14"/><circle cx="%.1f" cy="%.1f" r="30" fill="none" stroke="var(--loc)" stroke-opacity=".35"/>'
                    '<circle cx="%.1f" cy="%.1f" r="9.5" fill="#fff" style="filter:drop-shadow(0 1px 2px rgba(0,0,0,.35))"/><circle cx="%.1f" cy="%.1f" r="6.5" fill="var(--loc)"/>' % (lx, ly, lx, ly, lx, ly, lx, ly))
    if you_dot is not None:
        lx, ly = T(you_dot)
        body.append('<circle cx="%.1f" cy="%.1f" r="8.5" fill="#fff" style="filter:drop-shadow(0 1px 2px rgba(0,0,0,.4))"/><circle cx="%.1f" cy="%.1f" r="5.8" fill="var(--loc)"/>' % (lx, ly, lx, ly))
    out.append('<defs>%s</defs>' % ''.join(defs))
    out.extend(body)
    out.append('</svg>')
    return ''.join(out)

MAP_CSS = """
.ml{font-family:'Figtree',sans-serif;paint-order:stroke;stroke:var(--m-halo);stroke-width:3px;stroke-linejoin:round}
.ml.area{font-size:10px;font-weight:700;letter-spacing:1.4px;fill:var(--m-label);text-anchor:middle}
.ml.park{font-size:10.5px;font-weight:600;fill:var(--m-park-label);text-anchor:middle}
.ml.water{font-size:10.5px;font-style:italic;font-weight:500;fill:var(--m-water-label);text-anchor:middle;letter-spacing:.3px}
.ml.road{font-size:9.5px;font-weight:600;fill:var(--m-road-label);text-anchor:middle}
.ml.poi{font-size:10px;font-weight:600;fill:var(--m-label)}
:root{--m-built:#ECEAE3;--m-rail:#B4B0A6;--m-poi:#8A8790;--m-road-label:#55565C;--heat-faint:.24}
[data-theme=dark]{--m-built:#1D2025;--m-rail:#454A53;--m-poi:#7D828C;--m-road-label:#B3B7C0;--heat-faint:.30}
"""

# ------------------------------------------------------------------ worlds
PUNE_RIVER = [(-8, -0.1), (-6.5, -0.2), (-5, -0.6), (-3.6, -0.3), (-2.2, -0.8), (-0.8, -0.55), (0.6, -0.2), (1.8, -0.7), (3.2, -1.25), (4.6, -1.0), (6.2, -1.5), (8, -1.9)]
def river_y(x, R=PUNE_RIVER):
    for a, b in zip(R, R[1:]):
        if a[0] <= x <= b[0]:
            return a[1] + (b[1] - a[1]) * (x - a[0]) / (b[0] - a[0])
    return R[-1][1]
def bank(x0, x1, bottom, side):
    """polygon from the river centre line to a straight edge (bottom y); side=+1 south bank, -1 north"""
    xs = [x0 + (x1 - x0) * k / 24 for k in range(25)]
    top = [(x, river_y(x)) for x in xs]
    return top + [(x1, bottom), (x0, bottom)]

PUNE = World(
    name='pune', seed=3,
    patches=[
        dict(poly=bank(-8, -2.5, 3.25, 1), ang=8, sp=.13, asp=1.7),
        dict(poly=bank(-8, -1.2, -5, -1), ang=-22, sp=.14, org=.6, asp=1.3),
        dict(poly=bank(-1.2, 8, -5, -1), ang=28, sp=.12, asp=1.9),
        dict(poly=bank(-2.5, 1.3, 3.3, 1), ang=12, sp=.15, asp=1.5, drop=.12),
        dict(poly=bank(1.3, 8, 3.0, 1), ang=-36, sp=.15, org=.9, asp=1.2, drop=.2),
        dict(poly=[(-8, 3.6), (8, 3.1), (8, 7), (-8, 7)], ang=2, sp=.11, asp=2.0),
    ],
    parks=[([(-1.0, 0.18), (-0.25, 0.12), (-0.2, 0.62), (-0.9, 0.7)], 'Bund Garden', 1),
           ([(3.2, 0.25), (3.9, 0.15), (4.0, 0.62), (3.3, 0.7)], 'Osho Teerth Park', 1),
           ([(1.9, 2.25), (2.8, 2.15), (2.9, 2.85), (2.0, 2.95)], 'Empress Garden', 1),
           ([(-6.4, -2.6), (-5.4, -2.8), (-5.2, -1.9), (-6.2, -1.7)], '', 0),
           ([(0.7, -0.62), (1.5, -0.8), (1.75, -1.25), (0.95, -1.38)], 'Salim Ali Bird Sanctuary', 1)],
    rivers=[(PUNE_RIVER, .17)],
    lakes=[],
    rail=[(-8, 3.25), (-3.5, 3.35), (-1.5, 3.6), (0.5, 3.15), (2.5, 2.6), (4.5, 2.2), (8, 2.0)],
    roads=[
        ('Riverside Rd', [(-8, 0.25), (-6.5, 0.12), (-5, -0.28), (-3.6, 0.02), (-2.2, -0.48), (-0.8, -0.25), (0.6, 0.12), (1.8, -0.38), (3.2, -0.95), (4.6, -0.7), (6.2, -1.2)], 'sec'),
        ('North Main Rd', [(1.4, 0.95), (2.4, 0.8), (3.4, 0.95), (4.6, 0.7), (6.2, 0.35), (8, 0.1)], 'maj'),
        ('Bund Garden Rd', [(-3.0, 1.15), (-1.6, 1.0), (-0.2, 0.95), (1.4, 0.95)], 'maj'),
        ('Nagar Rd', [(-1.4, -1.7), (0.4, -1.75), (2.0, -1.9), (3.6, -2.5), (5.6, -2.7), (8, -3.0)], 'art'),
        ('Station Rd', [(-8, 3.0), (-4.5, 2.75), (-1.8, 2.95), (0.2, 2.6), (1.3, 2.0), (1.6, 1.0)], 'art'),
        ('Koregaon Rd', [(1.3, -0.1), (1.5, 1.0), (1.45, 2.2), (1.5, 3.5)], 'sec'),
        ('', [(4.1, -0.8), (4.3, 0.7), (4.1, 2.3), (4.4, 3.5)], 'sec'),
        ('', [(-5.3, -0.25), (-5.5, 1.1), (-5.0, 3.2)], 'sec'),
        ('Jangli Maharaj Rd', [(-7.5, 0.9), (-6.0, 1.1), (-4.4, 1.6), (-3.0, 2.1)], 'sec'),
        ('', [(-1.75, -0.55), (-1.65, -1.75), (-1.5, -3.6)], 'sec', '#yb'),
        ('', [(2.25, -0.55), (2.4, -1.9), (2.2, -4.0)], 'sec', '#kn'),
        ('', [(2.55, -0.62), (2.95, 0.0), (3.1, 0.6), (2.9, 1.5), (2.0, 1.75), (1.5, 1.1)], 'sec', '#kp'),
        ('', [(-0.4, -0.85), (0.6, -0.55), (1.3, -0.72), (1.8, -0.95), (2.6, -1.35), (3.4, -1.55), (4.6, -1.8)], 'sec', '#nb'),
        ('', [(2.2, -1.1), (2.9, -1.6), (3.6, -1.72), (3.8, -2.2), (3.9, -3.4)], 'sec', '#kn2'),
        ('', [(-4.0, -0.5), (-3.6, -2.2), (-3.0, -4.5)], 'sec'),
        ('', [(-2.4, 3.3), (-1.2, 2.0), (-0.5, 1.0), (-0.3, 0.1)], 'sec'),
        ('', [(5.4, -2.6), (5.8, -1.4)], 'sec'),
    ],
    labels=[('SHIVAJINAGAR', (-6.0, 1.9), 'area'), ('KOREGAON PARK', (3.3, 1.55), 'area'), ('KALYANI NAGAR', (3.6, -2.0), 'area'),
            ('YERAWADA', (-2.6, -2.4), 'area'), ('CAMP', (-0.5, 2.3), 'area'), ('SANGAMWADI', (-4.6, -1.3), 'area'),
            ('Mula-Mutha River', (-1.5, -0.7), 'water'), ('Bund Garden', (-0.6, 0.42), 'park'), ('Osho Teerth Park', (3.6, 0.42), 'park'),
            ('Empress Garden', (2.4, 2.55), 'park'), ('Pune Station', (-1.0, 3.75), 'poi'), ('Ruby Hall', (0.85, 1.55), 'poi'),
            ('Aga Khan Palace', (3.45, -2.75), 'poi'), ('Phoenix Mall', (3.15, -2.18), 'poi'), ('Salim Ali Bird Sanctuary', (1.25, -1.0), 'park')],
    heat_focus=(2.0, -0.7),
    corridors=[('Riverside Rd', (-5, -0.28), (4.6, -0.7), 3),
               ('North Main Rd', (1.4, 0.95), (6.2, 0.35), 3),
               ('Bund Garden Rd', 2),
               ('Koregaon Rd', (1.3, -0.1), (1.45, 2.2), 2),
               ('#kp', 3),
               ('Nagar Rd', (0.4, -1.75), (3.6, -2.5), 2),
               ('#nb', (-0.4, -0.85), (3.4, -1.55), 3),
               ('#kn', (2.25, -0.55), (2.4, -1.9), 2),
               ('#kn2', 2),
               ('#yb', (-1.75, -0.55), (-1.65, -1.75), 1)],
    voids=[[(-2.35, 1.55), (-1.3, 1.45), (-1.2, 2.35), (-2.25, 2.45)],          # cantonment / army land, Camp
           [(-1.7, 3.15), (0.1, 3.0), (0.15, 3.45), (-1.7, 3.55)],              # rail yard by Pune Station
           [(0.75, -2.65), (1.75, -2.75), (1.85, -2.15), (0.85, -2.1)],          # defence land off Nagar Rd
           [(2.65, -3.45), (3.45, -3.35), (3.5, -2.9), (2.7, -2.95)],            # Aga Khan Palace grounds
           [(-4.4, -2.8), (-3.4, -2.9), (-3.3, -2.0), (-4.3, -1.95)],            # Yerawada jail
           [(4.6, 1.6), (5.8, 1.5), (5.9, 2.4), (4.7, 2.5)]],                    # race course
)

# Riverside half: out-and-back west along the river with a lollipop at Shivajinagar, then a Koregaon Park loop
HALF_ROUTE = [(0.0, 0.22), (-0.8, -0.2), (-1.5, -0.42), (-2.2, -0.43), (-2.9, -0.2), (-3.6, 0.07),
              (-4.3, 0.25), (-4.75, 0.75), (-4.6, 1.45), (-3.95, 1.8), (-3.25, 1.5), (-3.05, 0.85), (-3.45, 0.18),
              (-3.6, 0.12), (-2.9, -0.15), (-2.2, -0.38), (-1.5, -0.37), (-0.8, -0.15), (0.0, 0.17), (0.6, 0.17), (1.2, 0.0),
              (1.8, -0.33), (2.55, -0.62), (3.0, -0.05), (3.4, 0.6), (3.3, 1.3), (2.75, 1.72), (2.0, 1.78), (1.55, 1.25),
              (1.25, 0.75), (0.7, 0.55), (0.1, 0.32)]
LOOP_RIVER = [(-0.3, 0.12), (-1.2, -0.32), (-1.72, -0.5), (-1.68, -1.2), (-1.4, -1.55), (-0.4, -1.12), (0.6, -0.55), (1.6, -1.0),
              (2.35, -1.45), (2.3, -0.62), (1.8, -0.3), (0.9, 0.14), (0.0, 0.2)]
LOOP_KP = [(3.4 + (x - 3.4) * 1.5, 1.3 + (y - 1.3) * 1.5) for x, y in [(2.6, 0.85), (3.15, 0.92), (3.6, 0.72), (4.15, 0.65), (4.25, 1.25), (4.0, 1.75), (3.3, 1.95), (2.85, 1.6), (2.6, 1.2), (2.6, 0.85)]]

BLR = World(
    name='blr', seed=7,
    patches=[
        dict(poly=[(-4, -3.5), (4, -3.5), (4, -1.95), (-4, -1.75)], ang=14, sp=.11, asp=1.5, drop=.12),
        dict(poly=[(0.15, -1.9), (4, -2.0), (4, 0.62), (0.25, 0.68)], ang=-8, sp=.1, org=.5, asp=1.4, drop=.18),
        dict(poly=[(-4, 0.9), (4, 0.62), (4, 4), (-4, 4)], ang=-6, sp=.095, asp=1.7, drop=.1),
        dict(poly=[(-4, -1.75), (-1.95, -1.8), (-1.95, 0.9), (-4, 0.9)], ang=22, sp=.12, org=.3, asp=1.3),
        dict(poly=[(-1.95, -1.8), (0.15, -1.9), (0.25, 0.68), (-1.95, 0.9)], ang=4, sp=.1, asp=1.5),
    ],
    parks=[([(-1.9, -1.15), (-0.75, -1.45), (0.05, -0.9), (0.12, 0.45), (-0.7, 0.66), (-1.85, 0.62)], 'Cubbon Park', 1),
           ([(2.6, 1.9), (3.2, 1.85), (3.3, 2.4), (2.7, 2.5)], '', 0)],
    rivers=[],
    lakes=[[(1.05, -1.35), (1.55, -1.62), (2.05, -1.45), (2.2, -1.0), (1.85, -0.62), (1.3, -0.65), (1.0, -0.95)]],
    rail=[(-4, -2.15), (-1.0, -2.05), (1.5, -2.25), (4, -2.0)],
    roads=[
        ('MG Road', [(-4, 0.86), (-1.9, 0.82), (-0.5, 0.78), (0.6, 0.74), (1.6, 0.66), (3.0, 0.62), (4, 0.6)], 'art'),
        ('Kasturba Rd', [(0.2, 3.4), (0.18, 1.6), (0.18, 0.75), (0.18, 0.0), (0.18, -0.9), (0.3, -1.9), (0.4, -3.8)], 'maj'),
        ('Vittal Mallya Rd', [(-0.95, 0.8), (-1.0, 1.8), (-1.05, 3.2)], 'sec'),
        ('Residency Rd', [(-4, 1.85), (-1.9, 1.75), (-0.2, 1.6), (1.2, 1.55), (2.6, 1.7), (4, 1.75)], 'art'),
        ('Hosur Rd', [(-4, 3.2), (-1.0, 2.8), (1.4, 2.95), (4, 2.7)], 'art'),
        ('Old Madras Rd', [(2.3, -1.2), (2.9, -1.5), (4, -1.65)], 'art'),
        ('', [(0.9, 0.7), (0.95, -0.5), (0.85, -1.9), (0.95, -3.8)], 'sec'),
        ('', [(-1.95, -1.8), (-1.95, 0.9)], 'sec'),
        ('Queens Rd', [(-4, -0.9), (-1.9, -1.3), (-0.8, -1.55), (0.3, -1.9), (1.4, -2.6), (2.2, -3.8)], 'maj'),
        ('', [(2.6, 0.64), (2.55, -0.4), (2.3, -1.2)], 'sec'),
    ],
    voids=[[(-1.85, 0.98), (-1.05, 0.95), (-1.05, 1.58), (-1.85, 1.62)],   # parade ground
           [(2.3, -3.2), (3.4, -3.1), (3.5, -2.45), (2.4, -2.5)],        # ASC centre
           [(1.25, 2.05), (2.2, 2.0), (2.25, 2.6), (1.3, 2.65)]],         # cantonment
    labels=[('Cubbon Park', (-0.85, -0.25), 'park'), ('Ulsoor Lake', (1.6, -1.05), 'water'), ('SHANTHALA NAGAR', (0.7, 1.15), 'area'),
            ('ASHOK NAGAR', (0.6, 2.25), 'area'), ('VASANTH NAGAR', (-1.2, -2.6), 'area'), ('HALASURU', (2.9, -0.55), 'area'),
            ('Trinity', (2.15, 0.45), 'poi'), ('Chinnaswamy Stadium', (-0.55, 1.2), 'poi'), ('Garuda Mall', (0.1, 2.0), 'poi')],
    heat_focus=(0, 0), corridors=[],
)
LIVE_ROUTE = [(0.18, 1.32), (0.18, 0.75), (0.18, 0.0), (0.17, -0.75), (-0.35, -1.2), (-0.95, -1.38), (-1.55, -1.15),
              (-1.78, -0.55), (-1.75, 0.15), (-1.45, 0.58), (-0.7, 0.785), (0.3, 0.75), (0.9, 0.722)]

# ------------------------------------------------------------------ page shell
def page(title, theme, body, extra_css=''):
    return ('<!doctype html>\n%s\n<html lang="en" data-theme="%s"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=390,initial-scale=1"><title>%s</title>'
            '<style>%s%s%s</style></head><body><div class="phone">%s</div></body></html>') % (
        SPEC, theme, title, CSS, MAP_CSS, extra_css, body)

def write(name, html):
    with open(os.path.join(OUT, name), 'w', encoding='utf-8') as f:
        f.write(html)

# ================================================================== HOME
HOME_CSS = """
.top .fsw{display:flex;align-items:center;gap:2px;font-size:15px;font-weight:600;color:var(--text-2);margin-left:10px;flex:1;height:44px}
.top .fsw .ic{width:18px;height:18px;stroke-width:2}
.top h1{flex:none}
.dots{display:flex;justify-content:center;align-items:center;gap:6px;height:6px;margin-top:10px}
.dots i{width:6px;height:6px;border-radius:50%;background:var(--hair-strong)}
.dots i.on{background:var(--text)}
.soc b{color:var(--text);font-weight:700}
.grp{font-size:13px;color:var(--muted);padding:2px 16px 0}
"""
def home(theme):
    a1 = avatar('ananya', 40)
    pile = ''.join(avatar(x, 24) for x in ('rohan', 'priya', 'lukas'))
    m = render_map(PUNE, 390, 236, 41, (-0.68, 0.68), route=HALF_ROUTE, rw=3.2,
                   avoid=[(320, 0, 390, 40)], drop=('Pune Station', 'Aga Khan Palace', 'Phoenix Mall', 'Ruby Hall', 'Osho Teerth Park', 'Empress Garden', 'Jangli Maharaj Rd', 'Koregaon Rd', 'Riverside Rd'))
    media = m
    card1 = ('<article class="card" style="margin-top:0">'
             '<div class="hd">%s<div class="who"><div class="nm">Ananya Kulkarni</div>'
             '<div class="mt">%s<span>Today at 6:12 AM · Pune, Maharashtra</span></div></div>'
             '<div class="more">%s</div></div>'
             '<h2 class="ttl">Riverside half before the heat</h2>'
             '<p class="desc">Legs felt fresh till 15k, then the humidity won. Chai at Bund Garden after.</p>'
             '<div class="stats num"><div><div class="l">Distance</div><div class="v">21.34<small>km</small></div></div>'
             '<div><div class="l">Pace</div><div class="v">5:28<small>/km</small></div></div>'
             '<div><div class="l">Time</div><div class="v">1h 56m</div></div></div>'
             '<div class="ach"><span style="color:var(--gold)">%s</span><span><b>4 achievements</b>, including a PR for Half-Marathon</span></div>'
             '<div class="media">%s</div><div class="dots"><i class="on"></i><i></i><i></i></div>'
             '<div class="soc"><div class="pile">%s</div><span class="sp"><b class="num">24</b> gave high-fives</span><span class="num">4 comments</span></div>'
             '<div class="acts"><span>%s</span><span>%s</span><span>%s</span></div>'
             '</article>') % (a1, ic('shoe', 's16'), ic('more', 's20'), ic('trophy', 's18'), media, pile,
                              ic('hi5'), ic('comment'), ic('share'))
    card2 = ('<article class="card">'
             '<div class="hd">%s<div class="who"><div class="nm">Lukas Brenner</div>'
             '<div class="mt">%s<span>Yesterday at 5:48 AM · Bengaluru</span></div></div>'
             '<div class="more">%s</div></div>'
             '<h2 class="ttl">Nandi Hills at sunrise, sub-40 on the climb</h2>'
             '<div class="grp">with Kabir Sethi and 2 others</div>'
             '<div class="stats num"><div><div class="l">Distance</div><div class="v">82.6<small>km</small></div></div>'
             '<div><div class="l">Elev Gain</div><div class="v">1,184<small>m</small></div></div>'
             '<div><div class="l">Time</div><div class="v">3h 12m</div></div></div>'
             '</article>') % (avatar('lukas', 40), ic('bike', 's16'), ic('more', 's20'))
    top = ('<header class="top"><h1>Home</h1><span class="fsw">Following%s</span><span class="btn">%s</span><span class="btn">%s</span>'
           '<span class="btn">%s<i class="dot"></i></span></header>') % (ic('caret'), ic('search'), ic('chat'), ic('bell'))
    body = status_bar() + top + '<main class="feed">%s%s</main>' % (card1, card2) + bottom_nav('home') + gesture()
    return page('Home', theme, body, HOME_CSS)

# ================================================================== MAPS
def route_card(name, place, dist, elev, time_, route, uid):
    xs = [q[0] for q in route]; ys = [q[1] for q in route]
    c = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    sc = min(220 / (max(xs) - min(xs)), 64 / (max(ys) - min(ys)))
    m = render_map(PUNE, 252, 88, sc, c, route=route, rw=3, labels=False, thumb=True)
    return ('<div class="rc"><div class="rm">%s</div><div class="rb"><div class="rn">%s</div>'
            '<div class="rp">%s</div><div class="rs num">'
            '<div><span class="k">Distance</span><b>%s<small> km</small></b></div>'
            '<div><span class="k">Elev Gain</span><b>%s<small> m</small></b></div>'
            '<div><span class="k">Est. Time</span><b>%s<small> min</small></b></div></div></div></div>') % (
        m, name, place, dist, elev, time_)

def maps(theme):
    avoid = [(0, 0, 390, 140), (318, 140, 390, 260), (190, 414, 390, 478), (0, 478, 390, 844)]
    m = render_map(PUNE, 390, 844, 118, (2.2, -0.6), heat=True, avoid=avoid, you_dot=(1.95, -0.18),
                   drop=('Ruby Hall',), extra_labels=[('KOREGAON PARK', (1.12, -0.3), 'area'), ('KALYANI NAGAR', (2.0, -1.62), 'area'),
                                                      ('Mula-Mutha River', (3.0, -1.05), 'water')])
    cards = (route_card('Bund Garden riverside loop', 'Pune · 2.1 km away', '10.2', '38', '55', LOOP_RIVER, 'a') +
             route_card('Koregaon Park lanes', 'Pune · 0.8 km away', '7.4', '22', '41', LOOP_KP, 'b'))
    css = """
.mtop{position:absolute;top:40px;left:12px;right:12px;display:flex;gap:8px;z-index:10}
.srch{flex:1;height:46px;border-radius:999px;background:var(--float);box-shadow:var(--shadow);display:flex;align-items:center;gap:10px;padding:0 16px;color:var(--muted);font-size:15px}
.srch .ic{color:var(--float-text)}
.chips{position:absolute;top:96px;left:0;right:0;display:flex;gap:8px;z-index:10;padding:0 12px 8px;overflow:hidden;
 -webkit-mask-image:linear-gradient(90deg,#000 0,#000 calc(100% - 44px),transparent 100%);mask-image:linear-gradient(90deg,#000 0,#000 calc(100% - 44px),transparent 100%)}
.chips .ic{width:18px;height:18px}
.chip.on{background:#242428;color:#fff}
.chip.on .ic{color:var(--accent);stroke-width:2.2}
[data-theme=dark] .chip.on{background:#F2F2F4;color:#18181B}
.ctrl{position:absolute;right:12px;top:150px;display:flex;flex-direction:column;gap:10px;z-index:10}
.fab{position:absolute;right:12px;top:424px;z-index:10;height:44px;padding:0 18px 0 14px;border-radius:999px;background:#242428;color:#fff;display:flex;align-items:center;gap:6px;font-weight:700;font-size:15px;box-shadow:var(--shadow)}
[data-theme=dark] .fab{background:#F2F2F4;color:#18181B}
.sheet{position:absolute;left:0;right:0;top:482px;bottom:80px;background:var(--surface);border-radius:16px 16px 0 0;box-shadow:0 -2px 12px rgba(0,0,0,.14);z-index:12;padding-top:8px}
[data-theme=dark] .sheet{background:#232327;box-shadow:0 -1px 0 var(--hair-strong),0 -4px 16px rgba(0,0,0,.5)}
[data-theme=dark] .sheet .rc{background:#232327;border-color:var(--hair-strong)}
.handle{width:36px;height:4px;border-radius:2px;background:var(--hair-strong);margin:0 auto 10px}
.sh{display:flex;align-items:baseline;padding:0 16px 10px}
.sh .sec{flex:1}
.sub{font-size:13px;color:var(--muted);padding:0 16px 10px;margin-top:-6px}
.rcs{display:flex;gap:12px;padding:0 16px;overflow:hidden}
.rc{flex:none;width:252px;border:1px solid var(--hair);border-radius:8px;overflow:hidden;background:var(--surface)}
.rm{height:88px;overflow:hidden}
.rb{padding:8px 12px 10px}
.rn{font-size:15px;font-weight:700;line-height:1.3}
.rp{font-size:12px;color:var(--muted)}
.rs{display:flex;gap:16px;margin-top:6px}
.rs>div{display:flex;flex-direction:column}
.rs .k{font-size:11px;color:var(--muted);font-weight:500;line-height:1.2}
.rs b{color:var(--text);font-weight:700;font-size:15px;line-height:1.25}
.rs b small{font-size:12px;font-weight:600}
"""
    body = ('<div style="position:absolute;inset:0">%s</div>' % m + '<div class="scrim"></div>' + status_bar(on_map=True, time='8:36') +
            '<div class="mtop"><div class="srch">%s<span>Search routes, places, segments</span></div><span class="fbtn">%s</span></div>' % (ic('search', 's20'), ic('layers', 's20')) +
            '<div class="chips"><span class="chip dark">%s Run %s</span><span class="chip on">%s Heatmap</span><span class="chip">Routes</span><span class="chip">Segments</span><span class="chip">Points of interest</span></div>' % (
                ic('shoe'), ic('caret', 's16'), ic('check')) +
            '<div class="ctrl"><span class="fbtn" style="font-weight:800;font-size:13px">3D</span><span class="fbtn">%s</span></div>' % ic('locate', 's20') +
            '<div class="fab">%s Create route</div>' % ic('plus', 's20') +
            '<section class="sheet"><div class="handle"></div><div class="sh"><span class="sec">Suggested routes</span><span class="lnk">See all</span></div>'
            '<div class="sub">Run · Loops near Koregaon Park · 5–12 km</div><div class="rcs">%s</div></section>' % cards +
            bottom_nav('maps') + gesture())
    return page('Maps', theme, body, css)

# ================================================================== RECORD
def rec_css():
    return """
.rtop{position:absolute;top:40px;left:12px;right:12px;display:flex;justify-content:space-between;z-index:10}
.gps{position:absolute;top:44px;left:50%;transform:translateX(-50%);z-index:10;height:36px;padding:0 14px;display:flex;align-items:center;gap:8px;border-radius:999px;background:var(--float);color:var(--float-text);box-shadow:var(--shadow);font-size:14px;font-weight:600;white-space:nowrap}
.gps i{width:8px;height:8px;border-radius:50%;background:var(--ok)}
.rpanel{position:absolute;left:0;right:0;bottom:0;background:var(--surface);border-radius:16px 16px 0 0;box-shadow:0 -2px 14px rgba(0,0,0,.16);z-index:15;padding:16px 0 32px}
.sels{display:flex;gap:10px;padding:0 16px}
.sel{flex:1;height:40px;border:1px solid var(--hair-strong);border-radius:999px;display:flex;align-items:center;gap:8px;padding:0 12px 0 14px;font-size:14px;font-weight:700;color:var(--text)}
.sel .ic{width:20px;height:20px}
.sel .t{flex:1}
.sel .s{font-weight:500;color:var(--muted);font-size:13px}
.sel .ic.s18{color:var(--muted)}
.rrow{display:flex;align-items:center;justify-content:space-around;padding:20px 22px 0}
.side{display:flex;flex-direction:column;align-items:center;gap:6px;width:84px;font-size:13px;font-weight:600;color:var(--text)}
.side .c{width:56px;height:56px;border-radius:50%;border:1.5px solid var(--hair-strong);display:grid;place-items:center;color:var(--text)}
.go{width:96px;height:96px;border-radius:50%;background:var(--accent);display:grid;place-items:center;color:#fff;font-weight:800;font-size:20px;letter-spacing:.2px;box-shadow:0 1px 2px rgba(0,0,0,.18)}
.hint{text-align:center;font-size:13px;color:var(--muted);margin-top:16px}
.mctl{position:absolute;right:12px;display:flex;flex-direction:column;gap:10px;z-index:10}
"""

def record(theme):
    avoid = [(0, 0, 390, 96), (318, 440, 390, 560), (0, 590, 390, 844)]
    m = render_map(BLR, 390, 844, 120, (0.65, 0.3), avoid=avoid, loc=(0.5, 0.45), drop=('Garuda Mall',),
                   extra_labels=[('Cubbon Park', (-0.75, -0.55), 'park')])
    body = ('<div style="position:absolute;inset:0">%s</div>' % m + '<div class="scrim"></div>' + status_bar(on_map=True, time='6:09') +
            '<div class="rtop"><span class="fbtn">%s</span><span class="fbtn">%s</span></div>' % (ic('down'), ic('gear')) +
            '<div class="gps"><i></i>GPS signal acquired</div>' +
            '<div class="mctl" style="top:452px"><span class="fbtn">%s</span><span class="fbtn">%s</span></div>' % (ic('layers', 's20'), ic('locate', 's20')) +
            '<section class="rpanel">'
            '<div class="sels"><div class="sel">%s<span class="t">Run</span>%s</div><div class="sel">%s<span class="t">Add route</span>%s</div></div>'
            '<div class="rrow"><div class="side"><span class="c">%s</span>Sensors</div><div class="go">Start</div><div class="side"><span class="c">%s</span>Beacon</div></div>'
            '<div class="hint">Auto-pause on · Splits every 1 km · Spotify linked</div>'
            '</section>' % (ic('shoe'), ic('caret', 's18'), ic('route'), ic('chev', 's18'), ic('sensor'), ic('beacon')) + gesture())
    return page('Record', theme, body, rec_css())

LIVE_CSS = """
.live{position:absolute;left:0;right:0;top:372px;bottom:0;background:var(--surface);border-radius:16px 16px 0 0;z-index:15;box-shadow:0 -2px 14px rgba(0,0,0,.16)}
.k{font-size:13px;font-weight:600;color:var(--muted);letter-spacing:.1px}
.bigv{font-size:52px;font-weight:700;letter-spacing:-1px;line-height:1.05}
.hero{text-align:center;padding:14px 0 10px;border-bottom:1px solid var(--hair)}
.hero .bigv{font-size:84px;font-weight:800;letter-spacing:-2.5px;line-height:1}
.lv-row{display:grid;grid-template-columns:1fr 1fr;border-bottom:1px solid var(--hair)}
.lv-row>div{padding:12px 0 12px;text-align:center}
.lv-row>div+div{border-left:1px solid var(--hair)}
.mini{display:grid;grid-template-columns:repeat(3,1fr);padding:14px 0 14px}
.mini>div{text-align:center}
.mini .v{font-size:24px;font-weight:700;line-height:1.2}
.prog{padding:2px 20px 0;display:flex;align-items:center;gap:12px;font-size:13px;color:var(--muted)}
.prog .t{flex:1;height:6px;border-radius:3px;background:var(--hair);overflow:hidden}
.prog .t i{display:block;height:100%;width:27%;background:var(--accent)}
.ctl{position:absolute;left:0;right:0;bottom:36px;display:flex;align-items:center;justify-content:space-around;padding:0 24px}
.ctl .side .c{width:56px;height:56px}
.stop{width:84px;height:84px;border-radius:50%;background:var(--accent);display:grid;place-items:center;box-shadow:0 1px 2px rgba(0,0,0,.18)}
.recpill{position:absolute;top:44px;left:50%;transform:translateX(-50%);z-index:10;height:34px;padding:0 14px;display:flex;align-items:center;gap:8px;border-radius:999px;background:var(--float);color:var(--float-text);box-shadow:var(--shadow);font-size:14px;font-weight:700;white-space:nowrap}
.recpill i{width:9px;height:9px;border-radius:50%;background:#E5322D;box-shadow:0 0 0 3px rgba(229,50,45,.22)}
.recpill.p i{background:#E8A317;box-shadow:0 0 0 3px rgba(232,163,23,.25)}
.pz{display:flex;justify-content:center;gap:36px;position:absolute;left:0;right:0;bottom:34px}
.pz>div{width:96px;height:96px;border-radius:50%;display:grid;place-items:center;font-size:19px;font-weight:800;letter-spacing:.2px}
.pz .res{border:2px solid var(--accent);color:var(--accent-text)}
.pz .fin{background:var(--accent);color:#fff;box-shadow:0 1px 2px rgba(0,0,0,.18)}
.pstrip{height:34px;display:flex;align-items:center;justify-content:center;gap:8px;background:#C43B00;color:#fff;font-size:14px;font-weight:700;letter-spacing:.2px;border-radius:16px 16px 0 0}
.pstrip i{width:10px;height:10px;display:flex;gap:2px}.pstrip i:before,.pstrip i:after{content:"";width:3.5px;height:10px;background:#fff;border-radius:1px}
.paused .hero{padding:6px 0 4px}.paused .hero .bigv{font-size:72px;letter-spacing:-2px}.paused .lv-row>div{padding:8px 0}.paused .lv-row .bigv{font-size:48px}.paused .mini{padding:10px 0}
.hrv{position:relative;display:inline-block}.hrv .ic{position:absolute;right:100%;top:50%;margin:-8px 5px 0 0;color:var(--hr)}
"""

def record_live(theme, paused=False):
    avoid = [(0, 0, 390, 92), (318, 280, 390, 340), (0, 360, 390, 420)]
    m = render_map(BLR, 390, 420, 88, (-0.39, -0.19), route=LIVE_ROUTE, live=True, rw=4.4, avoid=avoid,
                   drop=('Garuda Mall', 'ASHOK NAGAR', 'Trinity', 'Hosur Rd', 'Chinnaswamy Stadium'))
    stats = ('<div class="hero"><div class="k">Time</div><div class="bigv">44:18</div></div>'
             '<div class="lv-row"><div><div class="k">Split Avg (/km)</div><div class="bigv">5:09</div></div>'
             '<div><div class="k">Distance (km)</div><div class="bigv">8.27</div></div></div>'
             '<div class="mini"><div><div class="k">Avg Pace (/km)</div><div class="v">5:21</div></div>'
             '<div><div class="k">Elev Gain (m)</div><div class="v">42</div></div>'
             '<div><div class="k">Heart Rate (bpm)</div><div class="v"><span class="hrv">%s152</span></div></div></div>'
             '<div class="prog"><span>Km 9</span><span class="t"><i></i></span><span>730 m to split</span></div>') % ic('heart', 's16')
    if paused:
        ctl = '<div class="pz"><div class="res">Resume</div><div class="fin">Finish</div></div>'
        stats = '<div class="pstrip"><i></i>Paused</div>' + stats
        pill = '<div class="recpill p"><i></i>Paused · Run</div>'
    else:
        ctl = ('<div class="ctl"><div class="side"><span class="c">%s</span>Lap</div>'
               '<div class="stop"><svg width="30" height="30" viewBox="0 0 30 30"><rect x="6" y="6" width="18" height="18" rx="2.5" fill="#fff"/></svg></div>'
               '<div class="side"><span class="c">%s</span>Lock</div></div>') % (ic('flag'), ic('lock'))
        pill = '<div class="recpill"><i></i>Recording · Run</div>'
    body = ('<div style="position:absolute;left:0;right:0;top:0;height:420px">%s</div>' % m + '<div class="scrim"></div>' + status_bar(on_map=True, time='6:58' if paused else '6:56') +
            '<div class="rtop"><span class="fbtn">%s</span><span class="fbtn">%s</span></div>' % (ic('down'), ic('gear')) + pill +
            '<div class="mctl" style="top:298px"><span class="fbtn">%s</span></div>' % ic('locate', 's20') +
            '<section class="live num%s">%s%s</section>' % (' paused' if paused else '', stats, ctl) + gesture())
    return page('Paused' if paused else 'Recording', theme, body, rec_css() + LIVE_CSS)

# ================================================================== ACTIVITY DETAIL
def activity(theme):
    HH = 236
    m = render_map(PUNE, 390, HH, 42, (-0.68, -0.08), route=HALF_ROUTE, rw=3.8, avoid=[(0, 0, 390, 84)],
                   drop=('Mula-Mutha River', 'Pune Station', 'Aga Khan Palace', 'Phoenix Mall', 'Ruby Hall', 'Osho Teerth Park', 'Jangli Maharaj Rd', 'Koregaon Rd', 'Riverside Rd', 'Empress Garden'))
    css = """
.atop{position:absolute;top:38px;left:12px;right:12px;display:flex;gap:10px;z-index:10}
.atop .fbtn{width:40px;height:40px}
.hline{position:absolute;left:0;right:0;top:%dpx;height:1px;background:var(--hair);z-index:4}
.body{position:absolute;top:%dpx;left:0;right:0;bottom:84px;background:var(--surface);overflow:hidden;padding-top:9px}
.ah{display:flex;gap:12px;align-items:center;padding:0 16px 0}
.ah .nm{font-size:15px;font-weight:700;line-height:1.25}
.ah .mt{font-size:13px;color:var(--muted);line-height:1.35;display:flex;align-items:center;gap:5px}
.at{font-size:22px;font-weight:800;letter-spacing:-.4px;line-height:1.2;padding:6px 16px 0}
.grid{display:grid;grid-template-columns:repeat(3,1fr);margin:10px 16px 0;border-top:1px solid var(--hair)}
.grid>div{padding:3px 0 4px;border-bottom:1px solid var(--hair)}
.grid .l{font-size:12px;color:var(--muted);font-weight:500}
.grid .v{font-size:20px;font-weight:700;letter-spacing:-.02em;line-height:1.2}
.grid .v small{font-size:13px;font-weight:600;margin-left:2px}
.achs{display:flex;gap:10px;padding:8px 16px 8px;align-items:center;font-size:14px;color:var(--text-2);border-bottom:1px solid var(--hair);margin:0 16px;padding-left:0;padding-right:0}
.achs b{color:var(--text);font-weight:700}
.achs .sp{flex:1}
.achs .tm{font-weight:700;color:var(--text)}
.sph{display:flex;align-items:baseline;padding:8px 16px 0}
.sph .sec{flex:1}
.tbl{padding:0 16px;font-size:14px}
.tr{display:grid;grid-template-columns:28px 48px 1fr 40px 34px;align-items:center;height:25px;column-gap:10px}
.tr.th{font-size:12px;color:var(--muted);font-weight:500;height:22px}
.tr .b{height:14px;background:var(--bar);border-radius:0 2px 2px 0}
.tr.fast .p{font-weight:700}
.tr .r{text-align:right;color:var(--text-2)}
.abar{position:absolute;left:0;right:0;bottom:0;height:84px;padding-bottom:20px;background:var(--surface);border-top:1px solid var(--hair);display:flex;justify-content:space-around;align-items:center;z-index:20;color:var(--text)}
.abar span{display:flex;align-items:center;gap:6px;font-size:14px;font-weight:600}
.abar .ic{width:22px;height:22px;stroke-width:1.6}
.ax{font:600 10.5px Figtree,sans-serif;fill:var(--muted)}
.avl{paint-order:stroke;stroke:var(--surface);stroke-width:3px}
""" % (HH, HH)
    splits = [(1, '5:34', 2, 151), (2, '5:12', -3, 156)]
    secs = lambda s: int(s.split(':')[0]) * 60 + int(s.split(':')[1])
    rows = ''
    for k, p, e, hr in splits:
        w = min(92, 50 + (340 - secs(p)) * 2.4)
        rows += '<div class="tr %s"><span>%d</span><span class="p">%s</span><span><div class="b" style="width:%d%%"></div></span><span class="r">%s</span><span class="r">%d</span></div>' % (
            'fast' if p == '5:12' else '', k, p, w, ('+%d' % e) if e >= 0 else '-%d' % -e, hr)
    # pace chart: smooth, km axis
    W = 322; H = 110; KM = 21.34
    def pace_at(x):  # seconds per km: easy start, settle, mid surge, late humidity fade, kick
        return (334 - 16 * math.exp(-((x - 8.5) / 3.2) ** 2) + 17 * max(0, (x - 15) / 5) ** 1.4
                + 7 * math.sin(x / 1.5) + 4 * math.sin(x / 0.7 + 1) - 14 * math.exp(-((x - 21.1) / 0.5) ** 2) + 10 * math.exp(-((x - 0.3) / 0.8) ** 2))
    LO, HI = 300, 372
    def ypace(s): return 6 + (s - LO) / (HI - LO) * (H - 16)
    N = 120
    pts = [(i * W / (N - 1), ypace(pace_at(i * KM / (N - 1)))) for i in range(N)]
    pace = smooth_path(pts[::2] + [pts[-1]])
    elev_pts = [(i * W / (N - 1), H - 12 - 16 * math.sin(i / 19.0) ** 2 - 7 * math.sin(i / 6.5 + 1) ** 2) for i in range(N)]
    elev = 'M0 %d L' % H + smooth_path(elev_pts[::3] + [elev_pts[-1]])[1:] + ' L%d %d Z' % (W, H)
    ticks = ''.join('<path d="M%.1f %dv4" stroke="var(--hair-strong)"/><text x="%.1f" y="%d" class="ax" text-anchor="%s">%s</text>' % (
        k * W / KM, H, k * W / KM, H + 15, 'start' if k == 0 else 'middle', ('%d km' % k) if k == 20 else '%d' % k) for k in (0, 5, 10, 15, 20))
    avg = ypace(328)
    chart = ('<div class="sph"><span class="sec">Pace</span><span class="lnk">Analysis</span></div>'
             '<div style="padding:6px 16px 0"><svg width="358" height="%d" viewBox="0 0 358 %d"><path d="%s" fill="var(--chart-elev)"/>'
             '<path d="M0 %dH%d" stroke="var(--hair-strong)"/>'
             '<path d="M0 %.1fH%d" stroke="var(--muted)" stroke-dasharray="2 3" opacity=".8"/>'
             '<path d="%s" fill="none" stroke="var(--chart-line)" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>'
             '<text x="2" y="%.1f" class="ax avl">Avg 5:28</text>'
             '<text x="358" y="%.1f" text-anchor="end" class="ax">5:00</text><text x="358" y="%.1f" text-anchor="end" class="ax">5:28</text><text x="358" y="%.1f" text-anchor="end" class="ax">6:12</text>'
             '%s</svg></div>') % (
        H + 18, H + 18, elev, H, W, avg, W, pace, avg - 5, ypace(300) + 4, avg + 4, ypace(372) - 2, ticks)
    body = ('<div style="position:absolute;top:0;left:0;right:0;height:%dpx">%s</div>' % (HH, m) + '<div class="scrim"></div><div class="hline"></div>' + status_bar(on_map=True, time='8:31') +
            '<div class="atop"><span class="fbtn">%s</span><span style="flex:1"></span><span class="fbtn">%s</span><span class="fbtn">%s</span></div>' % (
                ic('back', 's20'), ic('share', 's20'), ic('more', 's20')) +
            '<main class="body">'
            '<div class="ah">%s<div style="flex:1"><div class="nm">Ananya Kulkarni</div><div class="mt">%s<span>Today at 6:12 AM · Pune, Maharashtra</span></div>'
            '<div class="mt">Garmin Forerunner 265</div></div></div>'
            '<h1 class="at">Riverside half before the heat</h1>'
            '<div class="grid num"><div><div class="l">Distance</div><div class="v">21.34<small>km</small></div></div>'
            '<div><div class="l">Avg Pace</div><div class="v">5:28<small>/km</small></div></div>'
            '<div><div class="l">Moving Time</div><div class="v">1:56:41</div></div>'
            '<div><div class="l">Elev Gain</div><div class="v">96<small>m</small></div></div>'
            '<div><div class="l">Calories</div><div class="v">1,412</div></div>'
            '<div><div class="l">Avg Heart Rate</div><div class="v">158<small>bpm</small></div></div></div>'
            '<div class="achs num">%s<span class="sp"><b>PR</b> · Half-Marathon</span><span class="tm">1:56:12</span><span class="lnk">+3 more</span></div>'
            '<div class="sph"><span class="sec">Splits</span><span class="lnk">All 22 splits</span></div>'
            '<div class="tbl num"><div class="tr th"><span>Km</span><span>Pace</span><span>Relative pace</span><span class="r">Elev</span><span class="r">HR</span></div>%s</div>'
            '%s</main>' % (avatar('ananya', 44), ic('shoe', 's16'), medal(1, 22), rows, chart) +
            '<div class="abar"><span>%s <span class="num">24</span></span><span>%s <span class="num">4</span></span><span>%s</span></div>' % (
                ic('hi5'), ic('comment'), ic('share')) + gesture())
    return page('Activity', theme, body, css)

# ================================================================== YOU / PROGRESS
def you(theme):
    css = """
.ycap{position:absolute;top:0;left:0;right:0;height:30px;background:var(--surface);z-index:19}
.ytop{position:absolute;top:30px;left:0;right:0;height:52px;display:flex;align-items:center;padding:0 8px 0 16px;gap:12px;background:var(--surface);z-index:20}
.ytop h1{font-size:21px;font-weight:700;flex:1;letter-spacing:-.2px}
.ytop .btn{width:44px;height:44px;display:grid;place-items:center}
.ybody{position:absolute;top:82px;left:0;right:0;bottom:80px;overflow:hidden;background:var(--gap)}
.blk{background:var(--surface);padding:0 16px 16px}
.sports{display:flex;gap:10px;padding:14px 0 2px}
.sp{width:48px;height:48px;border-radius:50%;border:1.5px solid var(--hair-strong);display:grid;place-items:center;color:var(--muted)}
.sp.on{border:2px solid var(--accent);color:var(--accent)}
.wk{font-size:13px;color:var(--muted);margin-top:12px;font-weight:500}
.tri{display:flex;margin-top:2px}
.tri>div{flex:1}
.tri .l{font-size:12px;color:var(--muted);font-weight:500}
.tri .v{font-size:26px;font-weight:700;letter-spacing:-.02em;line-height:1.15}
.tri .v small{font-size:14px;font-weight:600;margin-left:2px;letter-spacing:0}
.ttl2{display:flex;align-items:baseline;padding:12px 0 4px}
.ttl2 .sec{flex:1}
.wdays{display:grid;grid-template-columns:repeat(7,1fr);text-align:center;font-size:12px;color:var(--muted);font-weight:600}
.wdays .c{height:44px;display:grid;place-items:center}
.wdays .d{border-radius:50%;background:var(--accent)}
.wdays .e{width:8px;height:8px;border-radius:50%;background:var(--hair-strong)}
.wdays .f{width:4px;height:4px;border-radius:50%;background:var(--hair)}
.wdays .today{color:var(--text);font-weight:800}
.be{display:flex;align-items:center;height:46px;border-top:1px solid var(--hair);gap:12px;font-size:15px}
.be .n{flex:1;font-weight:600}
.be .t{font-weight:700}
.be .dt{font-size:13px;color:var(--muted);width:56px;text-align:right}
.tip{font:700 11.5px Figtree,sans-serif;fill:var(--surface)}
"""
    vals = [18.2, 24.6, 21.0, 30.4, 27.8, 12.5, 33.1, 35.6, 29.0, 38.4, 41.2, 32.4]
    W, H, T, B = 358, 130, 34, 22
    xs = [8 + i * (W - 22) / 11 for i in range(12)]
    ys = [T + (H - T - B) * (1 - v / 50.0) for v in vals]
    line = 'M' + ' L'.join('%.1f %.1f' % (x, y) for x, y in zip(xs, ys))
    area = line + ' L%.1f %d L%.1f %dZ' % (xs[-1], H - B, xs[0], H - B)
    # y labels sit inside the plot, left-aligned just above their gridline
    grid = ''.join('<path d="M0 %.1fH%d" stroke="var(--hair)" %s/>' % (T + (H - T - B) * (1 - g / 50.0), W, 'stroke-dasharray="2 3"' if g else '') for g in (0, 25, 50))
    grid += ''.join('<text x="0" y="%.1f" class="ax" text-anchor="start">%d km</text>' % (T + (H - T - B) * (1 - g / 50.0) - 4, g) for g in (25, 50))
    dots = ''.join('<circle cx="%.1f" cy="%.1f" r="3.2" fill="var(--surface)" stroke="var(--accent)" stroke-width="2"/>' % (x, y) for x, y in zip(xs[:-1], ys[:-1]))
    cx_, cy_ = xs[-1], ys[-1]
    dots += '<path d="M%.1f %.1fV%d" stroke="var(--accent)" stroke-width="1.6" stroke-dasharray="3 2.5" opacity=".9"/><circle cx="%.1f" cy="%.1f" r="6" fill="var(--accent)" stroke="var(--surface)" stroke-width="2.5"/>' % (cx_, cy_, H - B, cx_, cy_)
    # tooltip hangs under the point, right-aligned inside the frame; y labels live on the left, so nothing collides
    dots += ('<path d="M%.1f %.1fl-5 6h10z" fill="var(--text)"/><rect x="%.1f" y="%.1f" width="56" height="20" rx="4" fill="var(--text)"/>'
             '<text x="%.1f" y="%.1f" class="tip" text-anchor="middle">32.4 km</text>') % (cx_ - 6, cy_ + 9, cx_ - 54, cy_ + 15, cx_ - 26, cy_ + 29)
    months = ''.join('<text x="%.1f" y="%d" class="ax" text-anchor="middle">%s</text>' % (xs[i], H - 6, t) for i, t in ((1, 'AUG'), (5, 'SEP'), (9, 'OCT')))
    op = '.22' if theme == 'light' else '.16'
    chart = ('<svg width="358" height="%d" viewBox="0 0 358 %d" style="margin-top:4px"><style>.ax{font:600 10.5px Figtree,sans-serif;fill:var(--muted);letter-spacing:.4px}</style>'
             '<defs><linearGradient id="ar" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="var(--accent)" stop-opacity="%s"/><stop offset=".7" stop-color="var(--accent)" stop-opacity="0"/></linearGradient></defs>'
             '%s<path d="%s" fill="url(#ar)"/><path d="%s" fill="none" stroke="var(--accent)" stroke-width="2.2" stroke-linejoin="round"/>%s%s</svg>') % (H, H, op, grid, area, line, dots, months)
    # week of Mon 26 Oct - Sun 1 Nov 2026; today is Thu 29 Oct. 6.0 + 8.1 + 18.3 = 32.4 km
    week = [('M', 6.0, 'p'), ('T', 0, 'p'), ('W', 8.1, 'p'), ('T', 18.3, 'today'), ('F', 0, 'f'), ('S', 0, 'f'), ('S', 0, 'f')]
    wd = ''
    for n, d, k in week:
        if d:
            sz = 16 + d * 1.35
            dot = '<span class="d" style="width:%dpx;height:%dpx"></span>' % (sz, sz)
        else:
            dot = '<span class="%s"></span>' % ('f' if k == 'f' else 'e')
        wd += '<div><div class="c">%s</div><span class="%s">%s</span></div>' % (dot, 'today' if k == 'today' else '', n)
    best = [(1, '5K', '24:12', '12 Oct'), (1, '10K', '51:40', '28 Sep'), (2, 'Half-Marathon', '1:58:05', '7 Sep')]
    brows = ''.join('<div class="be num">%s<span class="n">%s</span><span class="t">%s</span><span class="dt">%s</span></div>' % (medal(r, 22), n, t, d) for r, n, t, d in best)
    body = ('<div class="ycap"></div>' + status_bar(time='8:42') +
            '<header class="ytop">%s<h1>You</h1><span class="btn">%s</span><span class="btn">%s</span></header>' % (avatar('arjun', 32), ic('search'), ic('gear')) +
            '<main class="ybody"><div class="blk" style="padding-bottom:12px"><div class="tabs" style="margin:0 -16px"><a class="on">Progress</a><a>Activities</a></div>'
            '<div class="sports"><span class="sp on">%s</span><span class="sp">%s</span><span class="sp">%s</span></div>'
            '<div class="wk">This week · 26 Oct – 1 Nov</div>'
            '<div class="tri num"><div><div class="l">Distance</div><div class="v">32.4<small>km</small></div></div>'
            '<div><div class="l">Time</div><div class="v">2h 58m</div></div><div><div class="l">Elev Gain</div><div class="v">214<small>m</small></div></div></div>'
            '%s<div style="font-size:13px;color:var(--muted);margin-top:0">Past 12 weeks · avg <b class="num" style="color:var(--text)">28.7 km</b> / week</div></div>'
            '<div class="blk" style="margin-top:8px;padding-bottom:12px"><div class="ttl2"><span class="sec">Training log</span><span class="lnk">View</span></div><div class="wdays num">%s</div></div>'
            '<div class="blk" style="margin-top:8px;padding-bottom:0"><div class="ttl2"><span class="sec">Best efforts</span><span class="lnk">All</span></div>%s</div>'
            '</main>' % (ic('shoe'), ic('bike'), ic('walk'), chart, wd, brows) + bottom_nav('you') + gesture())
    return page('You', theme, body, css)

if __name__ == '__main__':
    print('half route %.2f km, river loop %.2f, kp loop %.2f, live %.2f' % (
        poly_len(HALF_ROUTE), poly_len(LOOP_RIVER), poly_len(LOOP_KP), poly_len(LIVE_ROUTE)))
    for th in ('light', 'dark'):
        _av[0] = 0; _m[0] = 0
        write('home-%s.html' % th, home(th))
        write('maps-%s.html' % th, maps(th))
        write('record-%s.html' % th, record(th))
        write('record-live-%s.html' % th, record_live(th))
        write('record-paused-%s.html' % th, record_live(th, paused=True))
        write('activity-%s.html' % th, activity(th))
        write('you-%s.html' % th, you(th))
    print('ok')
