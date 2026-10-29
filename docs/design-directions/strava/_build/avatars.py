# Photo-like avatar crops, v3: no faces. Each athlete has a "photo" that reads as a
# small, slightly soft phone picture: back-lit profile, cap from behind, helmet+shades
# in shadow, trail scene, ridge at dusk, night bokeh. Everything is blurred a touch
# (no clean vector edges), has a colour cast, a vignette and film grain.
import random

_av = [0]

def _defs(u, seed, blur):
    return ('<filter id="%(u)sf" x="-10%%" y="-10%%" width="120%%" height="120%%"><feGaussianBlur stdDeviation="%(b).2f"/></filter>'
            '<filter id="%(u)sbk" x="-50%%" y="-50%%" width="200%%" height="200%%"><feGaussianBlur stdDeviation="4.5"/></filter>'
            '<filter id="%(u)sn" x="0" y="0" width="100%%" height="100%%"><feTurbulence type="fractalNoise" baseFrequency="1.1" numOctaves="2" seed="%(s)d"/>'
            '<feColorMatrix values="0 0 0 0 .5 0 0 0 0 .5 0 0 0 0 .5 0 0 0 .30 0"/></filter>'
            '<radialGradient id="%(u)sv" cx=".5" cy=".46" r=".66"><stop offset=".5" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".42"/></radialGradient>'
            '<clipPath id="%(u)sc"><circle cx="50" cy="50" r="50"/></clipPath>') % dict(u=u, s=seed, b=blur)

def _lg(id_, stops, x2=0, y2=1):
    return '<linearGradient id="%s" x1="0" y1="0" x2="%s" y2="%s">%s</linearGradient>' % (
        id_, x2, y2, ''.join('<stop offset="%s" stop-color="%s"/>' % st for st in stops))

def _rg(id_, cx, cy, r, stops):
    return '<radialGradient id="%s" cx="%s" cy="%s" r="%s">%s</radialGradient>' % (
        id_, cx, cy, r, ''.join('<stop offset="%s" stop-color="%s" stop-opacity="%s"/>' % st for st in stops))

def _bokeh(u, rng, cols, n, y0, y1, r0, r1, op=(.3, .7)):
    return '<g filter="url(#%sbk)">%s</g>' % (u, ''.join(
        '<circle cx="%d" cy="%d" r="%d" fill="%s" opacity="%.2f"/>' % (rng.randint(-5, 105), rng.randint(y0, y1), rng.randint(r0, r1), rng.choice(cols), rng.uniform(*op))
        for _ in range(n)))

def scene_sunrise_profile(u, rng):
    """back-lit side profile, ponytail, against a sunrise over the river"""
    d = _lg(u + 'sk', [(0, '#3A3F6A'), (.38, '#B65A72'), (.62, '#F09A5C'), (.8, '#FFD08A'), (1, '#E9B27A')])
    d += _rg(u + 'sun', .78, .7, .45, [(0, '#FFF6D8', 1), (.35, '#FFD9A0', .8), (1, '#FFB070', 0)])
    g = '<rect width="100" height="100" fill="url(#%ssk)"/><rect width="100" height="100" fill="url(#%ssun)"/>' % (u, u)
    g += '<path d="M-5 80 C20 76 45 79 70 77 S95 78 105 77 V105 H-5Z" fill="#7A4A52" opacity=".55"/>'
    g += _bokeh(u, rng, ['#FFE7B8', '#FFFFFF', '#F7B989'], 5, 55, 85, 4, 9)
    sil = ('M8 104 C10 88 20 80 34 76 L37 66 C26 62 20 52 21 39 C22 24 32 14 45 14 C58 14 66 22 67 32 '
           'L68 37 L74 45 L68 48 L68 52 L66 54 L67 58 C65 63 60 65 55 66 L56 73 C70 77 84 84 92 104 Z')
    tail = 'M26 28 C14 30 7 40 8 54 C13 46 18 42 25 41 Z'
    g += '<g transform="translate(4 2)"><path d="%s" fill="#FFC58C" opacity=".75" transform="translate(1.6 -.6)"/>' % sil
    g += '<path d="%s" fill="#FFC58C" opacity=".6" transform="translate(1 0)"/>' % tail
    g += '<path d="%s" fill="#1C1418"/><path d="%s" fill="#1C1418"/>' % (sil, tail)
    g += '<path d="M24 34 l4 5" stroke="#3A2A30" stroke-width="2.4" stroke-linecap="round"/></g>'
    g += '<rect width="100" height="100" fill="#FF8A3D" opacity=".06"/>'
    return d, g, 1.15

def scene_cap_back(u, rng):
    """runner's cap and shoulder from behind, hazy sea front at dusk"""
    d = _lg(u + 'sk', [(0, '#7F9AB5'), (.42, '#D9B9A0'), (.5, '#E8C6A2'), (.52, '#5D7A90'), (1, '#24384A')])
    d += _lg(u + 'cap', [(0, '#F4F4F6'), (.6, '#C9CBD2'), (1, '#8C8F99')], 1, .3)
    d += _lg(u + 'tee', [(0, '#E0552A'), (1, '#7A240C')], .6, 1)
    g = '<rect width="100" height="100" fill="url(#%ssk)"/>' % u
    g += _bokeh(u, rng, ['#FFE3A0', '#FFFFFF', '#FFC870'], 9, 44, 54, 2, 4, (.6, .95))
    g += '<path d="M-5 48 L30 46 L40 40 L46 46 L60 43 L70 47 L105 45 V52 H-5Z" fill="#4B5B6E" opacity=".55"/>'
    # shoulders from behind
    g += '<path d="M-6 104 C-2 82 16 72 34 70 L66 70 C84 72 102 82 106 104Z" fill="url(#%stee)"/>' % u
    g += '<path d="M40 62 L60 62 L62 74 C56 77 44 77 38 74Z" fill="#5A3322"/>'
    # back of the head (hair) + cap, seen from behind
    g += '<ellipse cx="50" cy="48" rx="19" ry="21" fill="#17110E"/>'
    g += '<path d="M29 46 C29 26 40 18 51 18 C63 18 72 26 72 46 C64 41 38 41 29 46Z" fill="url(#%scap)"/>' % u
    g += '<path d="M44 45.5 q6.5 -7 13 0 z" fill="#1E1A18"/><path d="M45 45 h11" stroke="#6C6F78" stroke-width="1.4"/>'
    g += '<path d="M51 19 C50 28 50 36 51 43" stroke="#9EA1AA" stroke-width="1.2" fill="none"/>'
    g += '<rect width="100" height="100" fill="#5C8AB8" opacity=".07"/>'
    return d, g, 1.0

def scene_helmet(u, rng):
    """helmet + wrap shades at 3/4, back-lit, blurred tree-lined road behind"""
    d = _lg(u + 'bg', [(0, '#CFE3D0'), (.3, '#6E9A6A'), (.62, '#3F5E3C'), (.64, '#8C9196'), (1, '#55595F')])
    d += _lg(u + 'hl', [(0, '#FFFFFF'), (.55, '#DADDE3'), (1, '#8E939E')], .8, 1)
    d += _lg(u + 'gl', [(0, '#5FA9E8'), (.45, '#1A2433'), (1, '#0B0F15')], 1, .4)
    d += _lg(u + 'jr', [(0, '#2E3440'), (1, '#0F1218')], 0, 1)
    g = '<rect width="100" height="100" fill="url(#%sbg)"/>' % u
    g += _bokeh(u, rng, ['#F6FBE6', '#FFFFFF', '#BFD99C'], 8, -5, 50, 6, 13)
    g += '<path d="M-6 104 C-2 84 14 76 32 74 L70 74 C86 77 100 86 106 104Z" fill="url(#%sjr)"/>' % u
    g += '<path d="M46 76 l8 28" stroke="#FC5200" stroke-width="3" opacity=".8"/>'
    # head in deep shade (back-lit): only a dark mass, rim light on the left
    g += '<path d="M33 46 C33 66 42 76 54 76 C66 76 72 64 71 48 Z" fill="#7E5A46" opacity=".55" transform="translate(-1.5 0)"/>'
    g += '<path d="M33 46 C33 66 42 76 54 76 C66 76 72 64 71 48 Z" fill="#2B1F19"/>'
    g += '<path d="M36 47 C40 45 66 45 72 47 L72 55 C66 58 58 58 54 55 C50 58 40 58 36 54Z" fill="url(#%sgl)"/>' % u
    g += '<path d="M40 49 l9 0" stroke="#BFE1FF" stroke-width="1.6" opacity=".55"/>'
    g += '<path d="M40 66 C46 72 60 72 66 64" stroke="#14100E" stroke-width="2" fill="none" opacity=".7"/>'
    g += '<path d="M26 46 C24 26 38 14 54 14 C70 14 80 26 77 46 C68 41 36 41 26 46Z" fill="url(#%shl)"/>' % u
    g += '<path d="M38 22 l4 9 M48 18 l1.5 10 M58 18 l-1 10 M67 22 l-4 9" stroke="#3A3F4A" stroke-width="2.6" stroke-linecap="round" opacity=".7"/>'
    g += '<path d="M30 47 C34 60 38 66 44 70" stroke="#1A1D22" stroke-width="1.6" fill="none"/>'
    g += '<rect width="100" height="100" fill="#FFE9B0" opacity=".06"/>'
    return d, g, 1.0

def scene_trail(u, rng):
    """forest trail with light shafts (Sinhagad-style), no person"""
    d = _lg(u + 'bg', [(0, '#E8F0C8'), (.3, '#7FA05A'), (.65, '#2F4A26'), (1, '#1E2C18')])
    d += _lg(u + 'tr', [(0, '#E9CF9E'), (1, '#8A6A40')])
    g = '<rect width="100" height="100" fill="url(#%sbg)"/>' % u
    g += _bokeh(u, rng, ['#FFFBE0', '#DDEBA8', '#FFFFFF'], 9, -5, 40, 5, 12)
    g += '<path d="M10 -5 L30 -5 L60 105 L44 105Z M62 -5 L74 -5 L84 105 L76 105Z" fill="#FFF4C8" opacity=".18"/>'
    g += '<path d="M-5 20 C10 24 14 60 6 105 H-5Z" fill="#1A2414"/><path d="M105 10 C88 20 86 60 96 105 H105Z" fill="#162012"/>'
    g += '<path d="M47 44 C44 60 30 80 12 105 L88 105 C70 82 56 62 52 44Z" fill="url(#%str)"/>' % u
    g += _bokeh(u, rng, ['#3E5A2A', '#567A36', '#24361A'], 7, 60, 105, 8, 16, (.5, .8))
    g += '<rect width="100" height="100" fill="#F0D890" opacity=".07"/>'
    return d, g, 1.2

def scene_ridge(u, rng):
    """figure on a ridge at dusk (Western Ghats), crop wide"""
    d = _lg(u + 'sk', [(0, '#22325A'), (.45, '#8E5C7A'), (.7, '#F08C5A'), (1, '#FFC27A')])
    g = '<rect width="100" height="100" fill="url(#%ssk)"/>' % u
    g += '<path d="M-5 70 L18 56 L32 62 L52 46 L66 56 L84 50 L105 60 V105 H-5Z" fill="#5A4A6A" opacity=".85"/>'
    g += '<path d="M-5 80 L22 70 L44 74 L70 64 L105 72 V105 H-5Z" fill="#2E2840"/>'
    g += '<path d="M-5 92 L30 84 L56 88 L105 82 V105 H-5Z" fill="#17141F"/>'
    # standing runner silhouette on the near ridge, arms up
    g += '<path d="M54 86 l-2 -12 l-4 -9 l2 -1 l4 6 l3 0 l4 -6 l2 1 l-4 9 l-1 12 Z" fill="#17141F"/><circle cx="55.5" cy="60.5" r="3.2" fill="#17141F"/>'
    g += _bokeh(u, rng, ['#FFE0B0', '#FFFFFF'], 3, 20, 50, 5, 9, (.2, .4))
    g += '<rect width="100" height="100" fill="#FF7A40" opacity=".05"/>'
    return d, g, 1.0

def scene_night(u, rng):
    """night city bokeh with a dark shoulder silhouette"""
    d = _lg(u + 'bg', [(0, '#141A2A'), (1, '#2A2230')])
    g = '<rect width="100" height="100" fill="url(#%sbg)"/>' % u
    g += _bokeh(u, rng, ['#FFC86A', '#FF9A4A', '#FFFFFF', '#8FC4FF'], 14, -5, 80, 4, 11, (.45, .9))
    g += '<path d="M10 105 C14 80 28 66 46 62 C40 54 38 44 42 34 C46 24 60 22 66 32 C72 42 68 56 60 62 C78 66 92 80 96 105Z" fill="#0C0C12"/>'
    g += '<path d="M66 32 C72 42 68 56 60 62" stroke="#FFB070" stroke-width="1.6" fill="none" opacity=".6"/>'
    return d, g, 1.1

PEOPLE = {
    'ananya': scene_sunrise_profile,
    'rohan': scene_cap_back,
    'lukas': scene_helmet,
    'priya': scene_trail,
    'arjun': scene_ridge,
    'kabir': scene_night,
}

def avatar(who, size):
    _av[0] += 1; u = 'a%d' % _av[0]
    rng = random.Random(sum(map(ord, who)) + 11)
    d, g, blur = PEOPLE[who](u, rng)
    blur = blur * max(.7, 40.0 / size) * .55
    return ('<svg class="av" width="%d" height="%d" viewBox="0 0 100 100"><defs>%s%s</defs><g clip-path="url(#%sc)">'
            '<g filter="url(#%sf)">%s</g><rect width="100" height="100" fill="url(#%sv)"/>'
            '<rect width="100" height="100" filter="url(#%sn)" opacity=".8"/>'
            '<circle cx="50" cy="50" r="49.5" fill="none" stroke="#000" stroke-opacity=".10"/></g></svg>') % (
        size, size, _defs(u, _av[0] * 5, blur), d, u, u, g, u, u)
