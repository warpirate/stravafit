# StravaFit documentation index

Every doc in this folder, grouped by section, with a one-line description. Docs describe publicly observable behaviour of Strava for comparison only; see the [root README](../README.md) for the disclaimer.

## Suggested reading order

New contributors should read in this order:

1. [Product overview](00-overview/product-overview.md): what we are building and why.
2. [Information architecture](02-navigation/information-architecture.md): how the app is organised.
3. [Core screens](03-wireframes/core-screens.md): the main screens as wireframes.
4. [Recording](04-features/recording.md): the heart of the product.
5. [Build blueprint](08-blueprint/build-blueprint.md): how and in what order we build it.
6. [Feature-parity checklist](08-blueprint/feature-parity-checklist.md): what is in and out of scope.
7. [Landscape and legal](07-oss/landscape-legal.md): what to reuse and what to avoid.
8. [Design system](../design-system/README.md): tokens and Compose theme to build UI with.

Then dip into the feature docs, `09-details` and `99-gaps` for whatever area you are working on.

## Glossary

Terms such as segment, KOM/QOM, Local Legend, Relative Effort, Fitness & Freshness and privacy zone are defined where first used in the [product overview](00-overview/product-overview.md) and the relevant feature docs. A dedicated glossary is planned; until then, search the docs for the term.

## 00 Overview
- [product-overview.md](00-overview/product-overview.md): product scope, target users, core loops and positioning.

## 01 Onboarding
- [onboarding.md](01-onboarding/onboarding.md): sign-up, permissions, profile setup and first-run flows.

## 02 Navigation
- [information-architecture.md](02-navigation/information-architecture.md): tab structure, screen map and navigation patterns.

## 03 Wireframes
- [core-screens.md](03-wireframes/core-screens.md): wireframes for feed, record, activity detail, maps and profile.
- [secondary-screens.md](03-wireframes/secondary-screens.md): wireframes for settings, search, clubs, challenges and other supporting screens.

## 04 Features
- [recording.md](04-features/recording.md): GPS recording, sensors, auto-pause, splits and saving activities.
- [segments-leaderboards.md](04-features/segments-leaderboards.md): segment matching, efforts, leaderboards and crowns.
- [social.md](04-features/social.md): following, feed, kudos, comments, clubs and challenges.
- [training-analytics.md](04-features/training-analytics.md): training load, fitness, heart-rate/power zones and progress metrics.
- [maps-routes.md](04-features/maps-routes.md): map layers, heatmaps, route builder and navigation.
- [privacy-safety.md](04-features/privacy-safety.md): visibility controls, privacy zones, live tracking and safety features.
- [subscription-notifications.md](04-features/subscription-notifications.md): free vs paid tiers and notification types.
- [integrations.md](04-features/integrations.md): device sync, third-party apps, public API and webhooks.
- [share-export.md](04-features/share-export.md): share cards and exporting activity files.

## 05 Design system
- [design-system-observed.md](05-design-system/design-system-observed.md): observed colours, type, spacing and components used as reference.

## 06 Architecture
- [strava-architecture.md](06-architecture/strava-architecture.md): publicly known reference architecture and lessons for ours.
- [data-model-api.md](06-architecture/data-model-api.md): proposed data model, entities and API surface.

## 07 Open source
- [landscape-legal.md](07-oss/landscape-legal.md): existing OSS projects to learn from, licensing plan and legal/trademark guardrails.

## 08 Blueprint
- [build-blueprint.md](08-blueprint/build-blueprint.md): tech stack, phased roadmap and milestones.
- [feature-parity-checklist.md](08-blueprint/feature-parity-checklist.md): feature-by-feature parity list with priority and status.

## 09 Details (screen-level sweeps)
Fine-grained detail per screen or area, supplementing the feature docs:
[activity-detail](09-details/activity-detail.md) ·
[groups-clubs-challenges](09-details/groups-clubs-challenges.md) ·
[home-feed](09-details/home-feed.md) ·
[maps-routes](09-details/maps-routes.md) ·
[notifications-search](09-details/notifications-search.md) ·
[onboarding-auth](09-details/onboarding-auth.md) ·
[platform-extras](09-details/platform-extras.md) ·
[profile-progress](09-details/profile-progress.md) ·
[record](09-details/record.md) ·
[save-edit-activity](09-details/save-edit-activity.md) ·
[segments-leaderboards](09-details/segments-leaderboards.md) ·
[settings-privacy-units](09-details/settings-privacy-units.md)

## 99 Gaps
- [wear-os-watch-app.md](99-gaps/wear-os-watch-app.md): Wear OS companion and standalone watch recording.
- [units-localization-accessibility.md](99-gaps/units-localization-accessibility.md): units, locales, translation and accessibility requirements.
- [gps-qa-test-performance.md](99-gaps/gps-qa-test-performance.md): GPS accuracy QA, test strategy, battery and performance budgets.
- [moderation-admin-tooling.md](99-gaps/moderation-admin-tooling.md): reporting, moderation workflows and admin tools.

## Other
- [DECISIONS.md](DECISIONS.md): log of project decisions.
- [design-system/README.md](../design-system/README.md): design tokens (`tokens.json`) and Jetpack Compose theme files.
