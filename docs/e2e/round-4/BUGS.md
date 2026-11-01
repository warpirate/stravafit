# E2E round 4 (2026-11-01), main 91bd37a, emulator "dwell" (Android 15), Pune tracks replayed at 2x

| # | Where | What | Status |
|---|-------|------|--------|
| 1 | Home, suggested routes | While more loops load, a pull-to-refresh spinner floats over the subtitle text, and the refresh button shows as a tiny orange dot: two indicators, one covering text (03-home-suggestions.png) | fixed (FeedScreen: pull indicator only after a pull) |
| 2 | Activity detail header | Device line shows the raw model code ("Google sdk_gphone64_x86_64"; real phones give codes like "SM-S918B") (13-detail.png) | open |
| - | Laps 3/4 (1:19 / 0:50) | Not an app bug: the replay script caught up after adb stalls with a burst of fixes. replay.py now shifts its schedule instead | harness fixed |
| - | Elev Gain 0 m on the 2 km run | Expected: the track's real climb is ~7 m, under the 10 m GPS hysteresis | not a bug |
| - | 7 km at 3x: 5.26 km recorded of a 7.89 km route | Harness: at 3x the track's GPS noise implies jumps over the 12 m/s run limit, so fixes are dropped. 2x keeps it valid (2.20 km recorded vs 2.14 km route). Don't replay runs faster than 2x | harness |
| - | No off-route alert at 3x | Harness: the 120 m sideways step in 10 s was rejected as a GPS glitch before the route follower saw it. Redone at 2x with a 40 s ramp | harness |
| 3 | Off-route vibration | Fired (656 ms buzz-pause-buzz) but with usage UNKNOWN, which some phones silence | fixed (tagged as a notification vibration) |
| ok | Following a route at 2x | "Off route. The route is 120 m away." banner and vibration at a 120 m detour; back on route afterwards with progress kept (17-off-route.png, 19-back-on-route.png) | works |
| - | 7 km at 2x: 7.44 km recorded vs 7.89 km route | Harness: this track's GPS noise is heavier; at 2x, 438 fixes imply >12 m/s and are dropped. Needs a 1x replay to judge distance accuracy | harness, rerun at 1x |
| ok | Goals | Weekly run goal 9.64 / 12 km, 80 %, behind pace, last day; edit to 11 km; streak 1 week (22-goals-you.png) | works |
| ok | Notifications | "Weekly run goal reached: 11.84 km this week, on the last day." and "2nd best ½ mile: 2:08" posted after the third run (23-notifications.png) | works |
| 4 | Notification icon | Small icon tinted the system's default navy, not StravaFit orange | fixed (setColor #FC5200) |
| 5 | Tapping grouped notifications | With 2+ notifications Android auto-groups them; tapping the group opens its made-up summary, which only launches the app (stays on the current screen) | fixed (own group + summary that opens the inbox) |
| ok | Inbox | Bell dot while unread; 4 real entries; tapping the goal entry opens Goals and marks it read; Mark all as read clears the dot (25-inbox.png) | works |
| ok | Share sheet | StravaFit offered for a .gpx in Files; "Import this file" dialog; a timestamp-less GPX is refused as a planned route (26/27) | works |
| 6 | Shared file the app cannot read | Said "not a GPX, TCX or FIT file" | fixed ("Couldn't read the shared file. Share it again…") |
| 7 | Import results | File name shown with the copy's index prefix ("0-pune-loop-7k.gpx") | fixed (one folder per copy) |
