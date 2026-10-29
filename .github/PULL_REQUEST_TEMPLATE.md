<!-- SPDX-License-Identifier: AGPL-3.0-or-later -->
<!--
Thanks for contributing! Please read CONTRIBUTING.md first.
Keep PRs small: one checklist ID per UI PR.
-->

## What and why

<!-- One or two sentences: what changes, and the reason. -->

## Checklist IDs

<!--
List the parity-checklist rows and acceptance items this PR covers.
- Parity rows: docs/08-blueprint/feature-parity-checklist.md
- Acceptance items: the "Acceptance checklist" in docs/09-details/<area>.md.
  An ID is the file's base name plus the item number, e.g. `record-4.4`.
Write "none" for pure infrastructure, refactor or docs PRs.
-->

- Closes #
- Checklist: `<area>-<item>`

## How it was tested

<!-- Tests added or changed, manual steps, devices or emulator API levels. -->

- [ ] Unit tests added or updated (`./gradlew test`)
- [ ] UI tests are tagged with the checklist ID, e.g. `@Checklist("record-4.4")`
- [ ] Backend: pgTAP tests added or updated for any RLS or SQL function change
- [ ] Both flavors build: `assembleGmsDebug` and `assembleFossDebug` (if `android/` changed)

## Screenshots / recordings

<!-- Required for UI changes: light and dark mode, and 200 % font scale where layout changes. -->

## Golden files

- [ ] This PR does **not** change golden files
- [ ] This PR changes golden files. The description explains why the new output is more correct, with before/after values or an image diff.

## Privacy and security

- [ ] No location, health or personal data is logged, sent to a new third party, or exposed beyond the user's visibility settings
- [ ] Privacy zones and visibility are respected in every new output (API, export, image, notification, tile)
- [ ] No proprietary SDKs in the `foss` flavor

## Contributor checklist

- [ ] Commits follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) and are **signed off** (`git commit -s`, DCO)
- [ ] New source files have the right SPDX header (`lib/**`: Apache-2.0, everything else: AGPL-3.0-or-later)
- [ ] No copied Strava code, assets, icons, copy text or screenshots, and UI uses neutral feature names
- [ ] No paywall, tier or upsell (ADR-001)
- [ ] Public API has KDoc, and anything deferred is stated above rather than left as a `TODO`
