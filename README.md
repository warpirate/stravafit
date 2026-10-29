# StravaFit

An open-source, privacy-respecting alternative to Strava for recording, analysing and sharing runs, rides and other activities. *StravaFit* is a working name.

Repository: https://github.com/warpirate/stravafit

> **Status: research and design phase.** The repo currently holds product research, wireframes, a design system, an architecture blueprint and an early export library (`export-core`). There is no app you can install yet.

## Disclaimer

StravaFit is an independent project. It is **not affiliated with, endorsed by or sponsored by Strava, Inc.** "Strava" and related marks are trademarks of their respective owners and are used here only to describe and compare features. The research docs describe **publicly observable behaviour** of the Strava product (public help pages, marketing material, public API docs and normal use of the app). They do not use or include proprietary code, assets or confidential information.

## Repository layout

```
stravafit/
├── docs/                 Product research, specs and blueprint (start at docs/README.md)
│   ├── 00-overview/      Product overview
│   ├── 01-onboarding/    Onboarding flows
│   ├── 02-navigation/    Information architecture
│   ├── 03-wireframes/    Core and secondary screen wireframes
│   ├── 04-features/      Feature deep dives
│   ├── 05-design-system/ Observed design language
│   ├── 06-architecture/  Reference architecture, data model and API
│   ├── 07-oss/           Open-source landscape and legal notes
│   ├── 08-blueprint/     Build blueprint and feature-parity checklist
│   ├── 09-details/       Screen-by-screen detail sweeps
│   └── 99-gaps/          Gap analyses (Wear OS, l10n/a11y, GPS QA, moderation)
├── design-system/        Tokens (tokens.json) and Jetpack Compose theme
├── export-core/          Kotlin library for activity export
├── gradle/, gradlew*     Gradle wrapper
└── LICENSE
```

## Key links

- [Documentation index](docs/README.md)
- [Design system](design-system/README.md)
- [Build blueprint](docs/08-blueprint/build-blueprint.md)
- [Feature-parity checklist](docs/08-blueprint/feature-parity-checklist.md)
- [OSS landscape and legal notes](docs/07-oss/landscape-legal.md)

## Contributing

Contributions are welcome, especially while the design is still taking shape.

1. Read the [suggested reading order](docs/README.md#suggested-reading-order) in the docs index.
2. Open an issue before starting large work so we can agree on scope.
3. Keep PRs small and focused, and link the doc section or checklist item they relate to.
4. **Clean-room rule:** do not copy Strava code, assets, icons, copy text or decompiled material. Describe observable behaviour in your own words and use our own design system.
5. Mark uncertain claims in docs as **(unverified)** and cite public sources where possible.

## License

The repository currently ships a `LICENSE` file with **AGPL-3.0**. The licensing plan in [docs/07-oss/landscape-legal.md](docs/07-oss/landscape-legal.md) recommends:

- **AGPL-3.0-or-later** for the server,
- **GPL-3.0-or-later** (or AGPL) for the Android client,
- **Apache-2.0** for reusable libraries (e.g. FIT/GPX parsing, segment matcher).

Final per-module licensing is still to be confirmed. Until then, treat all code as AGPL-3.0.
