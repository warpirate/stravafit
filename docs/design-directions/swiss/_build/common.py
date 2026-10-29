"""Shared pieces for the Swiss direction mockups. Run build.py to regenerate the HTML files."""
import math, random

FONTS = ('<link rel="preconnect" href="https://fonts.googleapis.com">'
         '<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>'
         '<link href="https://fonts.googleapis.com/css2?family=Archivo:wdth,wght@62..125,300..900'
         '&family=IBM+Plex+Mono:wght@400;500;600&display=block" rel="stylesheet">')

CSS = r"""
:root{
  --ink:#0B0B0C; --ink-2:#3B3C40; --ink-3:#66686D; --ink-4:#9A9CA1;
  --rule:#0B0B0C; --hair:#D6D6D2; --paper:#FFFFFF; --paper-2:#F4F4F1;
  --accent:#1238FF; --accent-ink:#FFFFFF; --accent-soft:#E6EAFF;
  --land:#ECECE8; --road:#FFFFFF; --road-case:#CFCFCA; --park:#DADAD3; --water:#C6C9CD; --contour:#BDBDB6;
  --u:8px; --m:16px; --g:8px;
}
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:390px;height:844px;overflow:hidden;background:var(--paper)}
body{font-family:'Archivo',sans-serif;font-variation-settings:'wdth' 100;color:var(--ink);
  -webkit-font-smoothing:antialiased;font-feature-settings:'tnum' 1,'lnum' 1}
.phone{position:relative;width:390px;height:844px;overflow:hidden;background:var(--paper);display:flex;flex-direction:column}
.mono{font-family:'IBM Plex Mono',monospace}
/* system bars */
.sb{height:28px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 var(--m) 0 20px;
  font:600 13px/1 'Archivo';font-variation-settings:'wdth' 100;letter-spacing:.01em}
.sb .ic{display:flex;gap:6px;align-items:center}
.gnav{height:20px;flex:none;display:flex;align-items:center;justify-content:center;background:var(--paper)}
.gnav i{display:block;width:108px;height:4px;border-radius:2px;background:var(--ink)}
/* type */
.lbl{font:500 10px/12px 'IBM Plex Mono',monospace;letter-spacing:.06em;text-transform:uppercase;color:var(--ink-3)}
.num{font-family:'Archivo';font-variation-settings:'wdth' 62;font-weight:700;letter-spacing:-.01em;font-variant-numeric:tabular-nums;line-height:.9}
.unit{font:500 12px/1 'IBM Plex Mono',monospace;color:var(--ink-2);letter-spacing:0;margin-left:2px}
.ttl{font-variation-settings:'wdth' 88;font-weight:700;letter-spacing:-.01em}
/* rules + motif: timing tape (tick ruler) */
.rule{height:1px;background:var(--rule)}
.hair{height:1px;background:var(--hair)}
.tape{height:6px;background:
  linear-gradient(var(--ink),var(--ink)) 0 0/100% 1px no-repeat,
  repeating-linear-gradient(90deg,var(--ink) 0 1px,transparent 1px 8px) 0 1px/100% 3px no-repeat,
  repeating-linear-gradient(90deg,var(--ink) 0 1px,transparent 1px 40px) 0 1px/100% 5px no-repeat}
.sec{display:flex;align-items:baseline;gap:8px;padding:0 var(--m)}
.sec .ix{font:600 10px/12px 'IBM Plex Mono';color:var(--ink)}
.sec .nm{font:600 10px/12px 'IBM Plex Mono';letter-spacing:.08em;text-transform:uppercase}
.sec .rt{margin-left:auto;font:500 10px/12px 'IBM Plex Mono';letter-spacing:.06em;text-transform:uppercase;color:var(--ink-3)}
/* tags */
.tag{display:inline-flex;align-items:center;height:16px;padding:0 4px;font:600 9.5px/1 'IBM Plex Mono';letter-spacing:.06em;
  text-transform:uppercase;border:1px solid var(--ink);color:var(--ink)}
.tag.solid{background:var(--ink);color:#fff}
.tag.acc{background:var(--accent);border-color:var(--accent);color:#fff}
.av{width:32px;height:32px;flex:none;background:var(--ink);color:#fff;display:grid;place-items:center;
  font:600 11px/1 'IBM Plex Mono';letter-spacing:.02em}
.av.sm{width:18px;height:18px;font-size:8px;outline:2px solid #fff}
svg{display:block}
.icon{width:24px;height:24px;fill:none;stroke:var(--ink);stroke-width:1.6;stroke-linecap:square;stroke-linejoin:miter}
/* bottom nav */
.bnav{flex:none;height:60px;display:grid;grid-template-columns:1fr 1fr 88px 1fr 1fr;border-top:1px solid var(--ink);background:var(--paper)}
.bnav a{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;font:500 9.5px/1 'IBM Plex Mono';
  letter-spacing:.08em;text-transform:uppercase;color:var(--ink-3);text-decoration:none;position:relative}
.bnav a .icon{stroke:var(--ink-3)}
.bnav a.on{color:var(--ink)} .bnav a.on .icon{stroke:var(--ink)}
.bnav a.on::before{content:"";position:absolute;top:-1px;left:16px;right:16px;height:3px;background:var(--ink)}
.bnav a.rec{background:var(--ink);color:#fff;margin-top:-9px;gap:7px}
.bnav a.rec b{width:20px;height:20px;border-radius:50%;background:var(--accent);box-shadow:0 0 0 3px var(--ink),0 0 0 4px #fff}
"""

STATUS = '''<div class="sb"><span>09:41</span><span class="ic">
<svg width="16" height="12" viewBox="0 0 16 12"><path d="M1 11h2V8H1zM5 11h2V6H5zM9 11h2V3H9zM13 11h2V0h-2z" fill="currentColor"/></svg>
<svg width="16" height="12" viewBox="0 0 16 12"><path d="M8 11.5L.5 3.6C2.6 1.6 5.2.5 8 .5s5.4 1.1 7.5 3.1z" fill="currentColor"/></svg>
<svg width="24" height="12" viewBox="0 0 24 12"><rect x=".5" y=".5" width="20" height="11" fill="none" stroke="currentColor"/><rect x="2" y="2" width="14" height="8" fill="currentColor"/><rect x="21.5" y="4" width="2" height="4" fill="currentColor"/></svg>
<span style="font-size:12px">78</span></span></div>'''

GNAV = '<div class="gnav"><i></i></div>'

I = {
 'feed':'<path d="M4 6h16M4 12h10M4 18h13"/>',
 'explore':'<circle cx="12" cy="12" r="8"/><path d="M12 2v5M12 17v5M2 12h5M17 12h5"/>',
 'groups':'<circle cx="9" cy="9" r="3.2"/><circle cx="16.5" cy="10" r="2.6"/><path d="M3.5 19c.6-3.2 2.8-5 5.5-5s4.9 1.8 5.5 5M15 14.2c2.6-.2 4.6 1.3 5.3 4.3"/>',
 'me':'<circle cx="12" cy="8.5" r="3.6"/><path d="M4.5 20c1-4 3.9-6.2 7.5-6.2s6.5 2.2 7.5 6.2"/>',
 'search':'<circle cx="10.5" cy="10.5" r="6"/><path d="M15 15l5.5 5.5"/>',
 'bell':'<path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15zM10 20.5h4"/>',
 'more':'<path d="M5 12h.01M12 12h.01M19 12h.01" stroke-width="2.6" stroke-linecap="round"/>',
 'back':'<path d="M20 12H5M11 5l-7 7 7 7"/>',
 'share':'<path d="M12 15V3M7 8l5-5 5 5M5 13v7h14v-7"/>',
 'chev':'<path d="M9 5l7 7-7 7"/>',
 'down':'<path d="M6 9l6 6 6-6"/>',
 'layers':'<path d="M12 4l9 5-9 5-9-5zM3 14l9 5 9-5"/>',
 'lock':'<rect x="5" y="11" width="14" height="9"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
 'gear':'<circle cx="12" cy="12" r="3"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"/>',
 'up':'<path d="M12 20V5M5 11l7-7 7 7"/>',
 'comment':'<path d="M4 5h16v11H9l-5 4z"/>',
}
def icon(name, size=24, cls='icon', style=''):
    return f'<svg class="{cls}" style="width:{size}px;height:{size}px;{style}" viewBox="0 0 24 24">{I[name]}</svg>'

def bnav(active):
    items=[('feed','Feed'),('explore','Explore'),None,('groups','Groups'),('me','Me')]
    out=['<nav class="bnav">']
    for it in items:
        if it is None:
            out.append('<a class="rec" href="#"><b></b><span>Record</span></a>'); continue
        k,l=it
        out.append(f'<a class="{"on" if k==active else ""}" href="#">{icon(k,22)}<span>{l}</span></a>')
    out.append('</nav>')
    return ''.join(out)

# ---------- greyscale map generator ----------
def _wavy(x0,y0,x1,y1,rnd,amp=4,n=6):
    pts=[]
    for i in range(n+1):
        t=i/n; x=x0+(x1-x0)*t; y=y0+(y1-y0)*t
        dx,dy=y1-y0,-(x1-x0); L=math.hypot(dx,dy) or 1
        o=0 if i in (0,n) else rnd.uniform(-amp,amp)
        pts.append((x+dx/L*o,y+dy/L*o))
    d=f'M{pts[0][0]:.1f} {pts[0][1]:.1f}'
    for i in range(1,len(pts)):
        mx=(pts[i-1][0]+pts[i][0])/2; my=(pts[i-1][1]+pts[i][1])/2
        d+=f' Q{pts[i-1][0]:.1f} {pts[i-1][1]:.1f} {mx:.1f} {my:.1f}'
    d+=f' L{pts[-1][0]:.1f} {pts[-1][1]:.1f}'
    return d

def streets(w,h,rnd,angle,spacing,amp=3,minor=True):
    """family of near-parallel streets crossing the box at `angle` degrees"""
    a=math.radians(angle); ux,uy=math.cos(a),math.sin(a); nx,ny=-uy,ux
    R=math.hypot(w,h); cx,cy=w/2,h/2
    out=[]; off=-R/2
    while off<R/2:
        px,py=cx+nx*off,cy+ny*off
        out.append(_wavy(px-ux*R,py-uy*R,px+ux*R,py+uy*R,rnd,amp,8))
        off+=spacing*rnd.uniform(.6,1.4)
    return out

def contours(cx,cy,rx,ry,n,rnd,step=9,rot=0):
    out=[]
    for k in range(n):
        r1=rx+k*step; r2=ry+k*step*.8; pts=[]
        ph=rnd.uniform(0,6.28)
        for i in range(36):
            t=i/36*6.283
            j=1+.08*math.sin(3*t+ph)+.05*math.sin(5*t+ph*1.7)
            x=r1*math.cos(t)*j; y=r2*math.sin(t)*j
            c,s=math.cos(math.radians(rot)),math.sin(math.radians(rot))
            pts.append((cx+x*c-y*s,cy+x*s+y*c))
        d='M'+' L'.join(f'{x:.1f} {y:.1f}' for x,y in pts)+'Z'
        out.append(d)
    return out

def map_svg(w,h,seed,river=None,river_w=22,parks=(),hill=None,grids=((28,46),(-62,58)),major=(),route=None,
            route_w=3.5,start=None,end=None,dark=False,km=True,labels=(),idattr='m',hatch=True,buildings=True):
    rnd=random.Random(seed)
    land,road,case,park,water,cont = ('#ECECE8','#FFFFFF','#D2D2CD','#DCDCD5','#C4C8CD','#B9B9B2') if not dark else ('#1B1C1E','#3A3B3F','#26272A','#232524','#0E1013','#34353A')
    s=[f'<svg viewBox="0 0 {w} {h}" width="{w}" height="{h}" preserveAspectRatio="xMidYMid slice" style="background:{land}">']
    s.append(f'<defs><pattern id="{idattr}h" width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)"><path d="M0 0v5" stroke="{cont}" stroke-width=".7"/></pattern>'
             f'<pattern id="{idattr}d" width="6" height="6" patternUnits="userSpaceOnUse"><circle cx="1.5" cy="1.5" r=".7" fill="{cont}"/></pattern></defs>')
    if hill:
        for d in contours(*hill,rnd):
            s.append(f'<path d="{d}" fill="none" stroke="{cont}" stroke-width=".6"/>')
    for p in parks:
        s.append(f'<path d="{p}" fill="{park}"/><path d="{p}" fill="url(#{idattr}d)"/>')
    # minor streets
    allst=[]
    for ang,sp in grids:
        allst+=streets(w,h,rnd,ang,sp,2.5)
    if buildings:
        for _ in range(int(w*h/1400)):
            x=rnd.uniform(0,w); y=rnd.uniform(0,h); bw=rnd.uniform(5,14); bh=rnd.uniform(4,10)
            s.append(f'<rect x="{x:.0f}" y="{y:.0f}" width="{bw:.0f}" height="{bh:.0f}" fill="{case}" opacity=".55" transform="rotate({grids[0][0]} {x:.0f} {y:.0f})"/>')
    for d in allst:
        s.append(f'<path d="{d}" fill="none" stroke="{case}" stroke-width="5"/>')
    for d in allst:
        s.append(f'<path d="{d}" fill="none" stroke="{road}" stroke-width="3.2"/>')
    if river:
        s.append(f'<path d="{river}" fill="none" stroke="{water}" stroke-width="{river_w}" stroke-linecap="round"/>')
        if hatch: s.append(f'<path d="{river}" fill="none" stroke="url(#{idattr}h)" stroke-width="{river_w}" opacity=".5"/>')
    for d in major:
        s.append(f'<path d="{d}" fill="none" stroke="{case}" stroke-width="9"/><path d="{d}" fill="none" stroke="{road}" stroke-width="6.5"/>')
    for (x,y,t,anch) in labels:
        col='#6E7076' if not dark else '#8C8E94'
        s.append(f'<text x="{x}" y="{y}" text-anchor="{anch}" font-family="IBM Plex Mono" font-size="7.5" font-weight="500" letter-spacing=".8" fill="{col}">{t}</text>')
    if route:
        halo='#FFFFFF' if not dark else '#000000'
        s.append(f'<path d="{route}" fill="none" stroke="{halo}" stroke-width="{route_w+3.5}" stroke-linejoin="round" stroke-linecap="round" opacity=".9"/>')
        s.append(f'<path class="route" id="{idattr}r" d="{route}" fill="none" stroke="#1238FF" stroke-width="{route_w}" stroke-linejoin="round" stroke-linecap="round"/>')
        if km: s.append(f'<g class="kms" data-route="{idattr}r" data-km="{km}"></g>')
        if start: s.append(f'<circle cx="{start[0]}" cy="{start[1]}" r="5" fill="#fff" stroke="#0B0B0C" stroke-width="2"/>')
        if end: s.append(f'<rect x="{end[0]-4.5}" y="{end[1]-4.5}" width="9" height="9" fill="#0B0B0C" stroke="#fff" stroke-width="1.5"/>')
    s.append('</svg>')
    return ''.join(s)

# places km markers along routes: <g class="kms" data-route=id data-km="total km">
KM_JS = r"""<script>
document.querySelectorAll('g.kms').forEach(g=>{
  const p=document.getElementById(g.dataset.route); const km=+g.dataset.km; if(!p||!km) return;
  const L=p.getTotalLength(); const every = km>30?10:(km>14?2:1); const ns='http://www.w3.org/2000/svg';
  for(let k=every;k<km-0.3;k+=every){const pt=p.getPointAtLength(L*k/km);
    const r=document.createElementNS(ns,'rect');r.setAttribute('x',pt.x-6);r.setAttribute('y',pt.y-6);r.setAttribute('width',12);r.setAttribute('height',12);r.setAttribute('fill','#0B0B0C');g.appendChild(r);
    const t=document.createElementNS(ns,'text');t.setAttribute('x',pt.x);t.setAttribute('y',pt.y+2.8);t.setAttribute('text-anchor','middle');t.setAttribute('font-family','IBM Plex Mono');t.setAttribute('font-size','7.5');t.setAttribute('font-weight','600');t.setAttribute('fill','#fff');t.textContent=k;g.appendChild(t);}
});
</script>"""

def page(title, body, extra_css=''):
    return f'''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=390">
<title>{title}</title>{FONTS}<style>{CSS}{extra_css}</style></head><body>{body}{KM_JS}</body></html>'''
