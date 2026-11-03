# SPDX-License-Identifier: AGPL-3.0-or-later
"""Builds strava-export-sample.zip, a small Strava bulk export shaped like the real one.

Tracks follow real Pune streets (points taken from testdata/e2e-routes/pune-run-2k.gpx). The ZIP keeps
Strava's layout (activities.csv, activities/, media/, other CSVs, the real column header with its
repeated names) and puts one track before activities.csv on purpose, so the importer's second pass is
exercised. Run from the repo root:

    python android/core/import/src/test/resources/strava-export/make_fixture.py
"""
import csv
import datetime as dt
import gzip
import io
import os
import re
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, *[".."] * 7))
SOURCE = os.path.join(ROOT, "testdata", "e2e-routes", "pune-run-2k.gpx")

points = [(float(a), float(b)) for a, b in re.findall(r'lat="([\d.]+)" lon="([\d.]+)"', open(SOURCE).read())]
track = points[::8][:24]


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def gpx(name, sport, start, step_s, pts, hr=None):
    out = ['<?xml version="1.0" encoding="UTF-8"?>',
           '<gpx creator="StravaGPX" version="1.1" xmlns="http://www.topografix.com/GPX/1/1" '
           'xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">',
           f' <metadata><time>{iso(start)}</time></metadata>',
           f' <trk><name>{name}</name><type>{sport}</type><trkseg>']
    for i, (lat, lon) in enumerate(pts):
        t = start + dt.timedelta(seconds=i * step_s)
        ext = ""
        if hr:
            ext = (f"<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>{hr + i % 7}</gpxtpx:hr>"
                   "</gpxtpx:TrackPointExtension></extensions>")
        out.append(f'  <trkpt lat="{lat:.7f}" lon="{lon:.7f}"><ele>{558 + i * 0.4:.1f}</ele><time>{iso(t)}</time>{ext}</trkpt>')
    out.append(" </trkseg></trk>\n</gpx>\n")
    return "\n".join(out).encode()


def tcx(start, step_s, pts):
    tps = []
    for i, (lat, lon) in enumerate(pts):
        t = start + dt.timedelta(seconds=i * step_s)
        tps.append(f"<Trackpoint><Time>{iso(t)}</Time><Position><LatitudeDegrees>{lat:.7f}</LatitudeDegrees>"
                   f"<LongitudeDegrees>{lon:.7f}</LongitudeDegrees></Position><AltitudeMeters>{560 + i * 0.3:.1f}</AltitudeMeters>"
                   f"<HeartRateBpm><Value>{128 + i % 9}</Value></HeartRateBpm><Cadence>{84 + i % 4}</Cadence></Trackpoint>")
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">'
            f'<Activities><Activity Sport="Biking"><Id>{iso(start)}</Id><Lap StartTime="{iso(start)}">'
            f"<TotalTimeSeconds>{(len(pts) - 1) * step_s}</TotalTimeSeconds><DistanceMeters>0</DistanceMeters>"
            f"<Track>{''.join(tps)}</Track></Lap></Activity>"
            "</Activities></TrainingCenterDatabase>\n").encode()


# Smallest valid baseline JPEG (1x1 grey pixel).
JPEG = bytes.fromhex(
    "ffd8ffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c"
    "20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffc0000b080001000101011100ffc4001f000001050101010101010000"
    "0000000000000102030405060708090a0bffc400b5100002010303020403050504040000017d01020300041105122131410613516107227114328191a1"
    "082342b1c11552d1f02433627282090a161718191a25262728292a3435363738393a434445464748494a535455565758595a636465666768696a73747576"
    "7778797a838485868788898a92939495969798999aa2a3a4a5a6a7a8a9aab2b3b4b5b6b7b8b9bac2c3c4c5c6c7c8c9cad2d3d4d5d6d7d8d9dae1e2e3e4e5"
    "e6e7e8e9eaf1f2f3f4f5f6f7f8f9faffda0008010100003f00fbfcffd9")

HEADER = ["Activity ID", "Activity Date", "Activity Name", "Activity Type", "Activity Description", "Elapsed Time",
          "Distance", "Max Heart Rate", "Relative Effort", "Commute", "Activity Private Note", "Activity Gear",
          "Filename", "Athlete Weight", "Bike Weight", "Elapsed Time", "Moving Time", "Distance", "Max Speed",
          "Average Speed", "Elevation Gain", "Elevation Loss", "Elevation Low", "Elevation High", "Max Grade",
          "Average Grade", "Max Cadence", "Average Cadence", "Max Heart Rate", "Average Heart Rate", "Max Watts",
          "Average Watts", "Calories", "Perceived Exertion", "Start Time", "From Upload", "Media"]


def strava_date(t):
    hour = t.hour % 12 or 12
    return f"{t.strftime('%b')} {t.day}, {t.year}, {hour}:{t.strftime('%M:%S')} {'AM' if t.hour < 12 else 'PM'}"


def row(id_, start, name, type_, desc, elapsed, km, file_, moving, meters, gain, avg_hr, max_hr, media):
    r = [""] * len(HEADER)

    def put(col, value, occurrence=0):
        r[[i for i, h in enumerate(HEADER) if h == col][occurrence]] = value

    put("Activity ID", str(id_))
    put("Activity Date", strava_date(start))
    put("Activity Name", name)
    put("Activity Type", type_)
    put("Activity Description", desc)
    put("Elapsed Time", str(elapsed))
    put("Distance", km)
    put("Commute", "false")
    put("Filename", file_)
    put("Elapsed Time", f"{elapsed}.0" if elapsed != "" else "", 1)
    put("Moving Time", moving)
    put("Distance", meters, 1)
    put("Elevation Gain", gain)
    put("Average Heart Rate", avg_hr)
    put("Max Heart Rate", max_hr, 0)
    put("Max Heart Rate", max_hr, 1)
    put("From Upload", "1.0" if file_ else "0.0")
    put("Media", media)
    return r


t1 = dt.datetime(2026, 10, 3, 1, 15, 0)   # 06:45 in Pune (UTC+5:30)
t2 = dt.datetime(2026, 10, 4, 13, 2, 30)
t3 = dt.datetime(2026, 10, 6, 6, 40, 10)
t4 = dt.datetime(2026, 10, 7, 12, 30, 0)
t5 = dt.datetime(2026, 10, 9, 0, 55, 0)
t6 = dt.datetime(2026, 10, 10, 1, 5, 0)

rows = [
    row(1001, t1, "Morning Run", "Run", "Easy loop by the river,\nfelt good", 230, "0.92", "activities/1001.gpx",
        "230.0", "921.4", "9.2", "142.3", "151", "media/0F3C2A9E-1001.jpg"),
    row(1002, t2, "Evening Walk", "Walk", "", 460, "0.92", "activities/1002.gpx.gz", "455.0", "921.4", "9.2", "", "", ""),
    row(1003, t3, "Lunch Ride", "Ride", 'Commute "the long way"', 115, "0.92", "activities/1003.tcx", "115.0", "921.4",
        "6.9", "132.0", "136", "media/7B11-1003.jpg"),
    row(1004, t4, "Yoga", "Yoga", "Hatha, 60 min", 3600, "0.00", "", "3600.0", "0.0", "0.0", "", "", ""),
    row(1005, t5, "Tempo run", "Run", "", 1800, "5.10", "activities/1005.fit.gz", "1795.0", "5103.2", "21.0", "158.1",
        "171", ""),
    row(1006, t6, "Broken upload", "Trail Run", "", "", "", "activities/1006.gpx", "", "", "", "", "", ""),
]

buf = io.StringIO()
writer = csv.writer(buf, lineterminator="\n")
writer.writerow(HEADER)
writer.writerows(rows)

out = os.path.join(HERE, "strava-export-sample.zip")
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("activities/1003.tcx", tcx(t3, 5, track))  # before the list: needs a second pass
    z.writestr("profile.csv", "Athlete ID,Email Address,First Name\n9001,runner@example.org,Asha\n")
    z.writestr("activities.csv", buf.getvalue())
    z.writestr("activities/1001.gpx", gpx("Morning Run", "running", t1, 10, track, hr=140))
    z.writestr("activities/1002.gpx.gz", gzip.compress(gpx("Evening Walk", "walking", t2, 20, track), mtime=0))
    z.writestr("activities/1006.gpx", b"<html>upload failed</html>")
    z.writestr("media/0F3C2A9E-1001.jpg", JPEG)
    z.writestr("media/7B11-1003.jpg", JPEG)
    z.writestr("media/unattached.jpg", JPEG)
print("wrote", out)
