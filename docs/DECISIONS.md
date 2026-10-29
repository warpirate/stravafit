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

## Notes

- **005**: chosen for reliable GPS foreground service, BLE sensors (HR/power/cadence), Health Connect and battery control.
- **006**: use PostGIS for geometry (routes, segments, heatmaps), Row Level Security for privacy/visibility rules, Storage for photos/FIT files, Edge Functions or a worker for segment matching and heatmap tiles. Heavy jobs (segment matching, heatmap generation) may move to a separate worker service if Edge Function limits bite.
- **007**: AGPL means anyone who hosts a modified server must publish their source.
