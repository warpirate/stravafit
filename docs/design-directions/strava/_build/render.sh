#!/bin/sh
# render all (or given) html -> 2x png with headless Edge
cd /d/stravafit/docs/design-directions/strava
list="$*"; [ -z "$list" ] && list=$(ls *.html | sed "s/.html$//")
for b in $list; do
  out=$(cygpath -w "$PWD/$b.png")
  "/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=2 --window-size=390,844 --virtual-time-budget=6000 --screenshot="$out" "file:///D:/stravafit/docs/design-directions/strava/$b.html" >/dev/null 2>&1
  sleep 1
done
sleep 2
