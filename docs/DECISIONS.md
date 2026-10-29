# Project Decisions (ADR log)

Binding decisions for the open-source Strava alternative. Every doc, the blueprint and all code must follow these.

| # | Date | Decision | Status |
|---|------|----------|--------|
| 001 | 2026-10-29 | No subscriptions, paywall or premium tier. Every feature Strava gates behind its subscription is free in our app. | Accepted |
| 002 | 2026-10-29 | Map/route export is P0 (MVP): vector SVG, transparent PNG, PNG with background, share cards/stories, PDF poster, GPX/TCX/FIT/GeoJSON. | Accepted |
| 003 | 2026-10-29 | Feature parity down to minor details (micro-interactions, defaults, formatting, edge cases). See `docs/09-details/`. | Accepted |
| 004 | 2026-10-29 | "StravaFit" is a working/repo name only. Pick a new public name (no "Strava" in it) before Play Store launch. | Accepted |
| 005 | 2026-10-29 | Mobile: native Android, Kotlin + Jetpack Compose (Material 3). iOS later as a separate effort. | Accepted |
| 006 | 2026-10-29 | Backend: Supabase (Postgres + PostGIS, Auth, Storage, Realtime, Edge Functions). Must remain self-hostable via the open-source Supabase docker stack. App works offline-first; server needed for social, segments, leaderboards, feed. | Accepted |
| 007 | 2026-10-29 | License: AGPL-3.0. | Accepted |
| 008 | 2026-10-29 | Heavy backend jobs (ingest, segment matching, heatmaps, exports) run in a Kotlin/JVM worker on a Postgres job queue (pgmq); Edge Functions only for light glue. | Accepted |
| 009 | 2026-10-29 | Metrics, polyline, geo and model code live in `shared/*` modules (KMP-ready) used by both app and worker. | Accepted |
| 010 | 2026-10-29 | Reusable libraries in `lib/*` (FIT/GPX/TCX/GeoJSON, segment matcher, route-art) are Apache-2.0; everything else AGPL-3.0-or-later. | Accepted |
| 011 | 2026-10-29 | Two Android flavors: `gms` (Play) and `foss` (F-Droid, no Google services). | Accepted |
| 012 | 2026-10-29 | Maps: MapLibre Native + self-hosted Protomaps PMTiles (OSM); on-device routing with BRouter. | Accepted |
| 013 | 2026-10-29 | Feed is chronological by default; any ranked mode is opt-in and its signals are documented in-app. | Accepted |
| 014 | 2026-10-29 | Distribution: GitHub releases only (no Play Store plan). Visual design deliberately follows Strava's look and feel (white/dark themes, orange accent, layout, density). We still draw our own logo, icons and illustrations; no copied proprietary assets. Supersedes the "no orange" rule in the design system and makes ADR-004's rename optional. | Accepted |

## Notes

- **005**: chosen for reliable GPS foreground service, BLE sensors (HR/power/cadence), Health Connect and battery control.
- **006**: use PostGIS for geometry (routes, segments, heatmaps), Row Level Security for privacy/visibility rules, Storage for photos/FIT files, Edge Functions or a worker for segment matching and heatmap tiles. Heavy jobs (segment matching, heatmap generation) may move to a separate worker service if Edge Function limits bite.
- **007**: AGPL means anyone who hosts a modified server must publish their source.
