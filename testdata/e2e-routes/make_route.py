# SPDX-License-Identifier: AGPL-3.0-or-later
"""Build a realistic E2E replay track: a real foot route over OSM roads (FOSSGIS OSRM),
resampled at running speed with GPS-like noise and real elevation (Open-Meteo).
Usage: python -I make_route.py <name> <lat,lon start> <via lat,lon>... --pace 5:40
Output: <name>.replay.csv (t_s,lat,lon,alt_m,accuracy_m) and <name>.gpx (clean route).
Uses curl because Python HTTPS is unreliable on the dev machine."""
import json, math, random, subprocess, sys, urllib.parse

def curl(url):
    out = subprocess.run(["curl", "-s", "--max-time", "60", "-A",
                          "StravaFit-e2e/0.1 +https://github.com/warpirate/stravafit", url],
                         capture_output=True, text=True, check=True).stdout
    return json.loads(out)

def decode6(s):
    pts, i, lat, lon = [], 0, 0, 0
    while i < len(s):
        for which in (0, 1):
            shift = res = 0
            while True:
                b = ord(s[i]) - 63; i += 1
                res |= (b & 0x1f) << shift; shift += 5
                if b < 0x20: break
            d = ~(res >> 1) if res & 1 else res >> 1
            if which == 0: lat += d
            else: lon += d
        pts.append((lat / 1e6, lon / 1e6))
    return pts

def hav(a, b):
    R = 6371000.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp, dl = p2 - p1, math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))

def main():
    args = sys.argv[1:]
    pace = "5:40"
    if "--pace" in args:
        k = args.index("--pace"); pace = args[k + 1]; del args[k:k + 2]
    name, pts = args[0], [tuple(map(float, p.split(","))) for p in args[1:]]
    coords = ";".join(f"{lon},{lat}" for lat, lon in pts)
    r = curl(f"https://routing.openstreetmap.de/routed-foot/route/v1/foot/{coords}?overview=full&geometries=polyline6")
    route = decode6(r["routes"][0]["geometry"])
    m, s = pace.split(":"); speed = 1000.0 / (int(m) * 60 + int(s))  # m/s
    # cumulative distance, then sample one fix per second
    cum = [0.0]
    for a, b in zip(route, route[1:]): cum.append(cum[-1] + hav(a, b))
    total = cum[-1]
    samples, t, j = [], 0, 0
    while True:
        d = min(t * speed * (1 + 0.04 * math.sin(t / 37.0)), total)
        while j < len(cum) - 2 and cum[j + 1] < d: j += 1
        seg = max(cum[j + 1] - cum[j], 1e-9); f = (d - cum[j]) / seg
        lat = route[j][0] + f * (route[j + 1][0] - route[j][0]); lon = route[j][1] + f * (route[j + 1][1] - route[j][1])
        samples.append((t, lat, lon)); t += 1
        if d >= total: break
    # elevation for up to 100 points per request, interpolated
    step = -(-len(samples) // 90)
    idx = list(range(0, len(samples), step)) + [len(samples) - 1]
    lats = ",".join(f"{samples[i][1]:.5f}" for i in idx); lons = ",".join(f"{samples[i][2]:.5f}" for i in idx)
    elev = curl(f"https://api.open-meteo.com/v1/elevation?latitude={lats}&longitude={lons}")["elevation"]
    def alt_at(k):
        for n in range(len(idx) - 1):
            if idx[n] <= k <= idx[n + 1]:
                span = max(idx[n + 1] - idx[n], 1); return elev[n] + (elev[n + 1] - elev[n]) * (k - idx[n]) / span
        return elev[-1]
    # Gauss-Markov position noise (~3-6 m), realistic accuracy values
    rnd = random.Random(42); en = nn = 0.0
    with open(f"{name}.replay.csv", "w", newline="\n") as fo:
        fo.write("t_s,lat,lon,alt_m,accuracy_m\n")
        for k, (t, lat, lon) in enumerate(samples):
            en = 0.9 * en + rnd.gauss(0, 1.2); nn = 0.9 * nn + rnd.gauss(0, 1.2)
            dlat = nn / 111320.0; dlon = en / (111320.0 * math.cos(math.radians(lat)))
            acc = max(3.0, min(12.0, 4.5 + abs(rnd.gauss(0, 1.5))))
            fo.write(f"{t},{lat + dlat:.7f},{lon + dlon:.7f},{alt_at(k) + rnd.gauss(0, 1.0):.1f},{acc:.1f}\n")
    with open(f"{name}.gpx", "w", newline="\n") as fo:
        fo.write('<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="StravaFit e2e" xmlns="http://www.topografix.com/GPX/1/1">\n')
        fo.write(f"<trk><name>{name}</name><trkseg>\n")
        for k, (t, lat, lon) in enumerate(samples[::5]):
            fo.write(f'<trkpt lat="{lat:.7f}" lon="{lon:.7f}"><ele>{alt_at(k*5):.1f}</ele></trkpt>\n')
        fo.write("</trkseg></trk>\n</gpx>\n")
    print(f"{name}: {total/1000:.2f} km over {len(route)} road points, {len(samples)} s of fixes, elev {min(elev):.0f}-{max(elev):.0f} m")

main()
