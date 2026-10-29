<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->

# Contributing to StravaFit

Thanks for helping build a free, open-source, privacy-first activity tracker. *StravaFit* is a working codename.

This guide covers:

1. [Ground rules](#1-ground-rules)
2. [Read first](#2-read-first)
3. [Development setup](#3-development-setup) (Windows, macOS, Linux)
4. [Module map](#4-module-map)
5. [Picking work: the parity checklist](#5-picking-work-the-parity-checklist)
6. [Making a change](#6-making-a-change)
7. [Commit messages (Conventional Commits)](#7-commit-messages-conventional-commits)
8. [Sign-off (DCO)](#8-sign-off-dco)
9. [Tests and golden files](#9-tests-and-golden-files)
10. [Licensing and file headers](#10-licensing-and-file-headers)
11. [Reviews and code owners](#11-reviews-and-code-owners)

By taking part you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md). To report a security problem, follow [SECURITY.md](SECURITY.md). Do not open a public issue for it.

---

## 1. Ground rules

These come from [`docs/DECISIONS.md`](docs/DECISIONS.md) and bind every contribution.

- **No paywall (ADR-001).** Every feature is free. Do not add tiers, trials, upsells or "premium" gates.
- **Export is P0 (ADR-002).** Map, route and file export are first-class features.
- **Clean room.** Do not copy Strava code, assets, icons, screenshots, UI copy or decompiled material. Describe observable behaviour in your own words and build it with our design system. Do not put Strava API data in fixtures.
- **Neutral names in the UI.** Do not use trademarked feature names. For example, say "high-five", not "kudos".
- **Privacy first.** Location data is sensitive personal data. Do not log coordinates, send telemetry without opt-in, or add proprietary tracking SDKs. See [SECURITY.md](SECURITY.md#location-data).
- **No proprietary SDKs in the `foss` flavor.** Google Play services code belongs only in the `gms` flavor.

## 2. Read first

1. [`README.md`](README.md)
2. [`docs/DECISIONS.md`](docs/DECISIONS.md), the binding architecture decisions (ADRs)
3. [`docs/08-blueprint/build-blueprint.md`](docs/08-blueprint/build-blueprint.md), the master plan
4. The teardown doc for your area under [`docs/`](docs/README.md)
5. That area's acceptance checklist in [`docs/09-details/`](docs/09-details/)

## 3. Development setup

### 3.1 Requirements

| Tool | Version | Used for |
|---|---|---|
| JDK | **17** (Temurin recommended) | Gradle, all Kotlin modules |
| Android SDK | platforms 34 and 35, build-tools 35 | `android/*` modules |
| Android Studio | latest stable (optional) | app development, previews |
| Git | 2.40+ | |
| Docker | Desktop or Engine, 24+ | local Supabase stack (backend work only) |
| Supabase CLI | latest | `backend/supabase` (backend work only) |

You do not need Gradle installed. Always use the wrapper (`./gradlew`).

### 3.2 Windows

1. Install a JDK 17, for example `winget install EclipseAdoptium.Temurin.17.JDK`, and set `JAVA_HOME`.
2. Install Android Studio, or the command-line tools, and install the SDK platforms and build-tools listed above. The SDK usually lives in `%LOCALAPPDATA%\Android\Sdk`.
3. Install Git for Windows. Use **Git Bash** for the commands in this guide. Run `git config --global core.autocrlf false`. The repo's `.gitattributes` already enforces LF line endings.
4. Clone the repo into a short path, such as `C:\src\stravafit` or `D:\stravafit`. Long paths can break Android builds.
5. Create `local.properties` in the repo root. Use forward slashes:

   ```properties
   sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
   ```

6. Build with `./gradlew build --console=plain` in Git Bash, or `gradlew.bat build` in PowerShell.

For backend work, install Docker Desktop with the WSL 2 backend, then run `scoop install supabase` or follow the [Supabase CLI install guide](https://supabase.com/docs/guides/local-development/cli/getting-started).

### 3.3 macOS

1. Run `brew install --cask temurin@17`, then `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.
2. Install Android Studio and the SDK packages above. The SDK usually lives in `~/Library/Android/sdk`.
3. Create `local.properties` containing `sdk.dir=/Users/<you>/Library/Android/sdk`.
4. Run `./gradlew build`.

For backend work, run `brew install supabase/tap/supabase` and start Docker Desktop, OrbStack or Colima.

### 3.4 Linux

1. Install a JDK 17 from your package manager (`openjdk-17-jdk`, `java-17-openjdk-devel`) or from Adoptium.
2. Install the Android SDK with Android Studio or `sdkmanager "platforms;android-35" "build-tools;35.0.0"`.
3. Create `local.properties` containing `sdk.dir=/home/<you>/Android/Sdk`.
4. Run `./gradlew build`.

For backend work, install Docker Engine and the Supabase CLI. The CLI is distributed as a `.deb` or `.rpm` package, through Homebrew on Linux, or with `npx supabase`.

### 3.5 Useful commands

```bash
./gradlew projects                          # list the modules that exist right now
./gradlew test                              # all unit tests
./gradlew :lib:route-art:test               # one module
./gradlew :android:app:assembleGmsDebug     # Play-services build
./gradlew :android:app:assembleFossDebug    # F-Droid build (no Google services)

# backend (from the repo root)
supabase --workdir backend start
supabase --workdir backend db reset         # apply migrations + seed
supabase --workdir backend test db          # pgTAP tests
```

`settings.gradle.kts` includes a module only when its `build.gradle.kts` exists, so a partial checkout still configures.

## 4. Module map

Dependency rule: `feature:*` → `core:*` → `shared:*` / `lib:*`. Features never depend on each other.

| Path | Gradle project | Licence | What it is |
|---|---|---|---|
| `shared/model` | `:shared:model` | AGPL-3.0-or-later | Sport types, activity, stream and lap models |
| `shared/geo` | `:shared:geo` | AGPL-3.0-or-later | Haversine, Mercator, privacy trim |
| `shared/polyline` | `:shared:polyline` | AGPL-3.0-or-later | Polyline encode/decode, simplification |
| `shared/metrics` | `:shared:metrics` | AGPL-3.0-or-later | Distance, moving time, elevation, GAP, best efforts, zones |
| `lib/route-art` | `:lib:route-art` | Apache-2.0 | Route-art renderer (SVG/PNG/PDF) |
| `lib/gpx-tcx` | `:lib:gpx-tcx` | Apache-2.0 | GPX and TCX readers and writers |
| `lib/geojson` | `:lib:geojson` | Apache-2.0 | GeoJSON writer |
| `lib/fit` | `:lib:fit` | Apache-2.0 | FIT file support |
| `android/core/recording` | `:android:core:recording` | AGPL-3.0-or-later | Pure-Kotlin recording engine (no `android.*`) |
| `android/core/designsystem` | `:android:core:designsystem` | AGPL-3.0-or-later | Compose theme built from `design-system/tokens.json` |
| `android/app` | `:android:app` | AGPL-3.0-or-later | App shell and navigation, with `gms` and `foss` flavors |
| `backend/supabase` | (Supabase CLI) | AGPL-3.0-or-later | Migrations, RLS policies, Edge Functions, pgTAP tests |
| `design-system/` | none | CC-BY-4.0 | Design tokens and reference Compose theme |
| `docs/` | none | CC-BY-4.0 | Research, specs, blueprint, ADRs |

The full target layout, including modules that do not exist yet, is in [blueprint §7](docs/08-blueprint/build-blueprint.md#7-repository--monorepo-structure) and [§8](docs/08-blueprint/build-blueprint.md#8-android-module-breakdown).

## 5. Picking work: the parity checklist

All planned work is tracked in [`docs/08-blueprint/feature-parity-checklist.md`](docs/08-blueprint/feature-parity-checklist.md). Each row has a priority: `P0` (MVP), `P1`, `P2`, `Later` or `Won't`. Each row also links to the doc that specifies it.

1. **Pick a row.** Choose `P0` first, then `P1`. Never pick `Won't`. Look for issues labelled `good-first-issue`. These cover units and formatting, strings, a single checklist item, a GPS corpus track with its golden file, or a design-token component.
2. **Find its acceptance items.** Detailed acceptance criteria live in the "Acceptance checklist" section of the matching `docs/09-details/<area>.md`. A **checklist ID** is the file's base name plus the item number. For example, item `4.4` in `docs/09-details/record.md` has the ID `record-4.4`.
3. **Claim it.** Comment on the existing issue, or open one with the "Feature" or "Parity gap" template, naming the checklist ID(s). For large work, wait for a maintainer to agree on scope.
4. **One checklist ID per UI PR.** Keep UI PRs small. Non-UI PRs, such as libraries or infrastructure, may cover several related IDs.
5. **Tag tests with the ID.** UI tests carry the checklist ID, for example `@Checklist("record-4.4")`, so coverage can be traced back to the checklist.
6. **Found something missing?** Open a "Parity gap" issue instead of editing the checklist in your feature PR.

## 6. Making a change

1. Fork the repo and create a branch from `main`: `feat/<short-topic>`, `fix/<short-topic>` or `docs/<short-topic>`.
2. Keep the change focused. Refactors go in separate PRs.
3. Run `./gradlew build` (and the backend tests if you touched `backend/`) before pushing.
4. Open a PR using the template. Link the checklist ID(s), include screenshots or recordings for UI changes, and fill in the privacy section.
5. CI must be green before review. `main` must always be releasable. Ship unfinished features behind a feature flag.

**Code style**

- Kotlin follows the [official Kotlin style](https://kotlinlang.org/docs/coding-conventions.html). `.editorconfig` configures ktlint and the IDE.
- Keep files small and focused. Put KDoc on public API.
- Domain code has no Android dependencies. `:android:core:recording` must stay pure JVM so golden replays run everywhere.
- Do not commit `TODO` stubs that pretend to be done. If something is deferred, say so in the PR.

## 7. Commit messages (Conventional Commits)

We use [Conventional Commits 1.0](https://www.conventionalcommits.org/en/v1.0.0/). release-please builds the changelog from them.

```
<type>(<optional scope>): <imperative summary, max 72 chars>

<optional body: what and why>

<optional footers>
Signed-off-by: Your Name <you@example.com>
```

- **Types:** `feat`, `fix`, `perf`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `revert`.
- **Scopes** are usually a module or area: `recording`, `route-art`, `gpx-tcx`, `designsystem`, `app`, `backend`, `ci`, `license`.
- **Breaking changes** use `feat(scope)!: ...` or a `BREAKING CHANGE:` footer.
- **Examples:**
  - `feat(recording): auto-pause after 5 s below walking speed`
  - `fix(gpx-tcx): keep timezone offset when writing <time>`
  - `test(recording): add urban-canyon golden replay`

## 8. Sign-off (DCO)

We use the [Developer Certificate of Origin 1.1](https://developercertificate.org/) instead of a CLA. Every commit must carry a `Signed-off-by` line whose name and email match the commit author. By adding it you certify that you wrote the change, or otherwise have the right to submit it under the project's licences.

```bash
git commit -s -m "fix(geo): handle anti-meridian crossing"
# forgot? amend or rebase:
git commit --amend -s --no-edit
git rebase --signoff main
```

## 9. Tests and golden files

The full strategy is in [blueprint §11](docs/08-blueprint/build-blueprint.md#11-testing-strategy-gps-replay-fixtures).

- **Every PR runs the L1 JVM tests.** These are unit tests, golden GPS replays, `lib/*` round-trips and route-art golden SVGs. Keep them deterministic: no wall-clock time, no network, no unseeded randomness, and no dependence on the default locale or time zone. Inject a clock and use a fixed `Locale` in formatting tests.
- **Golden files** are expected outputs: `testdata/gps-corpus/**/*.expected.json`, `testdata/render-golden/**` and `lib/*/src/test/resources/**`.
  - Never regenerate goldens just to make a failing test pass. First work out whether the new output is *more correct*.
  - A PR that changes a golden must explain why in its description, and show the before/after values or an image diff.
  - Golden changes need approval from a `testdata/` code owner.
  - Each metric in a golden carries an explicit tolerance. Do not widen a tolerance without a reason in the PR.
- **GPS corpus tracks** must be synthetic (generated by the seeded track generator) or donated under CC0 and anonymised: trim the first and last 500 m, shift timestamps, and remove device and user IDs. Record the source and licence in `manifest.yaml`.
- **Backend:** RLS policies need pgTAP tests in `backend/supabase/tests/`. RLS changes need two reviewers.
- **UI:** Compose UI or screenshot tests tagged with the checklist ID. Include screenshots in the PR.

## 10. Licensing and file headers

The repo follows the [REUSE specification](https://reuse.software). [`REUSE.toml`](REUSE.toml) maps paths to licences, and the licence texts are in [`LICENSES/`](LICENSES/).

<!-- REUSE-IgnoreStart -->

| Path | Licence | Header for new source files |
|---|---|---|
| `lib/**` | Apache-2.0 (ADR-010) | `// SPDX-License-Identifier: Apache-2.0` |
| `docs/**`, `design-system/**` | CC-BY-4.0 | none needed |
| everything else | AGPL-3.0-or-later (ADR-007) | `// SPDX-License-Identifier: AGPL-3.0-or-later` (SQL: `-- SPDX-License-Identifier: AGPL-3.0-or-later`) |

<!-- REUSE-IgnoreEnd -->

Every new source file starts with the SPDX line for its path. Copyright is held by the individual contributors, collectively "StravaFit contributors". Do not add third-party code without checking that its licence is compatible, and annotate it in `REUSE.toml` with its original copyright and licence.

To check compliance locally, run `pipx run reuse lint`.

## 11. Reviews and code owners

- [`.github/CODEOWNERS`](.github/CODEOWNERS) assigns reviewers per area.
- One approving review is required. RLS policy changes and golden-file changes need the reviewers described above.
- Reviewers check correctness, tests, privacy impact, licensing headers and clean-room compliance.
- Cross-cutting proposals start as an RFC in `docs/rfcs/`. Accepted architecture decisions are appended to `docs/DECISIONS.md`.
