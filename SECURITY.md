<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->

# Security Policy

StravaFit handles some of the most sensitive data a person can generate: where they live, when they leave home, the routes they repeat and their health metrics. We treat security and privacy bugs as top priority.

## Reporting a vulnerability

**Do not open a public issue, pull request or discussion for a security problem.**

Report privately through one of these channels:

1. **GitHub private vulnerability reporting (preferred):** go to the repository's **Security** tab and choose **Report a vulnerability**. This opens a private advisory that only the maintainers can see.
2. **Email:** `<SECURITY_CONTACT_EMAIL>`. This is a placeholder: the maintainers will publish a dedicated address and an OpenPGP key here before the first public release. Until then, use channel 1.

Please include:

- the affected component (app flavor and version, backend commit, library and version, or self-hosted setup),
- steps to reproduce, or a proof of concept,
- the impact you think it has (what an attacker can read, change or infer),
- whether real user data was involved. If it was, see [Handling data you find](#handling-data-you-find).

### What to expect

| Step | Target time |
|---|---|
| Acknowledge your report | 3 working days |
| Initial assessment and severity | 10 working days |
| Fix or mitigation for critical and high severity | 30 days, sooner where we can |
| Coordinated public disclosure | after a fix ships, or 90 days after the report, whichever comes first, unless we agree otherwise |

We will credit you in the advisory and release notes unless you ask us not to. This is a volunteer project with no bug bounty.

## Scope

**In scope:**

- The Android app (`android/**`), both the `gms` and `foss` flavors.
- The backend in this repository: Supabase migrations, **Row Level Security (RLS) policies**, SQL functions, Edge Functions, and the worker (`backend/**`).
- Shared code and libraries (`shared/**`, `lib/**`), for example parser crashes or resource exhaustion from malicious FIT, GPX or TCX files.
- Build and release integrity: CI workflows, signing and reproducible-build setup.
- The default Docker Compose self-hosting configuration that we ship.

**Out of scope:**

- Vulnerabilities in third-party services or upstream projects (Supabase, PostgreSQL, MapLibre, OpenStreetMap tile servers, Google Play services). Report those upstream. Tell us too if our configuration makes the issue worse.
- Instances run by third parties that we do not operate. Contact their operator.
- Attacks that need a rooted or compromised device, or physical access to an unlocked phone, unless the app makes them notably easier.
- Missing hardening with no demonstrated impact, such as missing security headers on static pages.
- Social engineering of maintainers or users, and denial of service by volume.

## Location data

Treat any bug that exposes location or health data beyond what the user chose to share as **high severity or worse**. That includes:

- **Privacy-zone bypass:** a hidden start, end or home area can be recovered from any output, such as API responses, map tiles, heatmaps, segment efforts, exported GPX/FIT/GeoJSON files, route-art images, image metadata (EXIF/XMP), share links or notifications.
- **Visibility bypass:** activities, routes, live locations or profile fields set to *Only me* or *Followers* can be read by anyone else, for example through an RLS gap, a Realtime channel, Storage object paths or a cache.
- **Inference attacks:** start or home locations can be reconstructed from aggregates such as heatmaps, leaderboards, "nearby" features or timing data, even when the raw track is hidden.
- **Live-location sharing:** a safety or live-tracking link stays valid after it is revoked or expires, or it leaks to people who were not invited.
- **Leaks to logs or crash reports:** coordinates, precise timestamps tied to places, or health metrics written to logs, crash reports or analytics. Crash reports must be location-scrubbed and opt-in.
- **Third-party exposure:** location sent to a third party, such as a tile, geocoding or push provider, without the user's knowledge, beyond what the feature strictly needs.

## Handling data you find

If you come across other people's personal data while researching:

- access only the minimum needed to show the issue,
- do not keep, share or process it, and delete it once the report is resolved,
- tell us in your report exactly what you accessed.

We will not pursue legal action against good-faith research that follows this policy, avoids privacy violations and service disruption, and gives us reasonable time to fix the issue before disclosure.

## Supported versions

StravaFit has not made a public release yet. Until v1.0, only the latest release and the `main` branch receive security fixes. Self-hosters should stay on the latest release. Upgrade notes will flag security-relevant changes.

## For contributors

- Changes to RLS policies or auth flows need **two reviewers** and pgTAP tests (see [CONTRIBUTING.md](CONTRIBUTING.md#9-tests-and-golden-files)).
- Never commit secrets, keystores, `google-services.json` or real GPS tracks. `.gitignore` covers the common cases.
- Fixtures must be synthetic or anonymised CC0 tracks. Never include real users' data or data from Strava's API.
