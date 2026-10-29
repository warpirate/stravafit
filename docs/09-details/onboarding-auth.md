# 09 · Details: Onboarding & Auth

Area: install, splash, sign-up and login, permissions, first-run onboarding, account settings, logout, deletion.

**Gating legend:** `[FREE]` / `[SUB]` show Strava's own gating and are **for reference only**. StravaFit has no paywall, so every feature listed here is free in our app.
**Our app:** `keep` = match Strava's behaviour · `improve` = match it and fix a known pain point · `drop` = deliberately not built (reason given).
Items marked *(unverified)* come from low-confidence or third-party sources and were not re-checked in this pass. This run was shortened at the user's request, so no extra web spot-checks were made.

---

## 1. Install & splash

| # | Detail | Strava behaviour | Gating | Our app |
|---|---|---|---|---|
| 1.1 | Default landing tab | After the splash (and after login), the app opens on either **Feed** or **Record**, depending on Settings > Display > Default tab. | [FREE] | keep. Read the stored preference before choosing the first route, so the wrong tab never flashes on screen. |
| 1.2 | Theme applies from the start | The Appearance setting (Default/Light/Dark, see 6.3) also applies to the splash and auth screens. | [FREE] | improve. Apply the theme before the first frame (Android 12+ SplashScreen API) so a dark-mode user never sees a white flash. |

## 2. Sign-up / onboarding flow

```
 Welcome ─► [Continue w/ Google] [Continue w/ Apple] [Email]
              │
              ▼
 Profile: First / Last name, Birthday, Gender
              ▼
 Choose a sport:  ( Ride ) ( Run ) ( Hike ) ( Ski ) ( Swim ) ...
              ▼
 Goals (multi):  train for event · stay healthy · explore ·
                 build habit · connect · compete
              ▼
 Level:  Beginner | Intermediate | Advanced | Pro
              ▼
 Privacy explainer (start/end hidden on map)  ─►  Permissions  ─►  Home
```

| # | Detail | Strava behaviour | Gating | Our app |
|---|---|---|---|---|
| 2.1 | Sign-up methods | Email + password, emailed one-time code, Google, and Apple. **Facebook login was removed on 16 Mar 2026.** Before the cutoff, Facebook users were asked to confirm their account email. Their existing follows and data were kept, but Facebook-based friend suggestions ended. | [FREE] | keep Email, OTC and Google. Apple is optional on Android. drop Facebook, since Strava has dropped it too. |
| 2.2 | Profile basics | Sign-up asks for first and last name, birthday and gender *(unverified)*. | [FREE] | keep. Make gender optional and include "prefer not to say". Birthday drives age groups. |
| 2.3 | Choose a sport | A picker offers Ride, Run, Hike, Ski, Swim and more, and sets the default sport type *(unverified)*. | [FREE] | keep |
| 2.4 | Goals question | Multi-select: train for an event, stay healthy, explore, build a habit, connect, compete. Possibly A/B tested *(unverified)*. | [FREE] | keep. Make it skippable and use it only to personalise suggestions. |
| 2.5 | Experience level | Beginner / Intermediate / Advanced / Pro *(unverified)*. | [FREE] | keep. Make it skippable. |
| 2.6 | Privacy education screen | Before the first recording, a screen explains the default safety features, such as hiding activity start and end points on the map *(unverified)*. | [FREE] | improve. Turn privacy zones on by default and let the user set one here. |
| 2.7 | Default privacy = Everyone | On a new account, the profile, activities and group activities are all set to Everyone. Users change this under Settings > Privacy Controls, where Followers is the usual alternative and Map Visibility is a sub-item. | [FREE] | improve. Ask on this screen with a 3-way choice (Everyone / Followers / Only me) instead of defaulting silently to public. |
| 2.8 | Apple "Hide My Email" | Hidden-email Apple sign-ups store an @privaterelay.appleid.com address. Changing that address requires setting a password first, and recovery goes through the relay address. | [FREE] | keep if we ship Apple sign-in, and show the relay address in Settings. |
| 2.9 | Apple linking pitfall | Apple sign-in links to an existing account only if the Apple ID email matches **and** the user chooses "Share my email". Choosing "Hide my email" silently creates a second account. | [FREE] | improve. If a new Apple account has a relay email, offer "Already have an account? Link it" so users don't end up with a duplicate. |

## 3. Login screen

```
┌──────────────────────────────┐
│  Log in                      │
│  [ email@example.com      ]  │
│  [ password               ]  │
│  ( Log in )                  │
│  Email me a code instead     │
│  Forgot your password?       │
│  ─────── or ───────          │
│  [G Continue with Google ]   │
│  [ Continue with Apple   ]   │
└──────────────────────────────┘
```

| # | Detail | Strava behaviour | Gating | Our app |
|---|---|---|---|---|
| 3.1 | Three auth types | Each account uses exactly one auth type: password, one-time code (OTC), or third-party (Google/Apple). Which settings appear depends on this type. | [FREE] | improve. Allow more than one method per account, e.g. Google plus a password. |
| 3.2 | "Email me a code" | User enters an email and taps the option. An email with the subject "Your one-time code" arrives, and after entering the code the user stays logged in until they log out. | [FREE] | keep |
| 3.3 | Code expiry 5 min | The code expires after 5 minutes, after which the user must request a new one. | [FREE] | keep. Add a visible countdown and a "Resend" button. |
| 3.4 | Resend cooldown 1 h | Help docs tell users to wait 1 hour between code requests to avoid login-attempt limits. | [FREE] | improve. Use a stepped cooldown (60 s, then 5 min, then 1 h) with a message that says how long to wait. Rate-limit on the server per email and per IP. |
| 3.5 | Email-only branch | If only an email is entered, OTC accounts get a code straight away. Other accounts are asked to choose "enable one-time codes" or "continue with password". Choosing password on a Google/Apple account that has none is a dead end, and Strava says this is expected. | [FREE] | improve. Detect the account type and offer the methods that work, e.g. "This account uses Google, continue with Google". |
| 3.6 | Forgot password = OTC | "Forgot your password?" emails a one-time code that logs the user in. Google/Apple users set their first password through this same flow (web: strava.com/account/recover sends a reset link). | [FREE] | keep. After a recovery login, offer "Set a password now". |
| 3.7 | Biometric = autofill only | Fingerprint or face unlock only autofills saved credentials (password-manager autofill). It is not a login method. | [FREE] | keep. Support Android Credential Manager and passkeys as a later improvement. |

## 4. Permissions (first run)

| # | Detail | Strava behaviour | Gating | Our app |
|---|---|---|---|---|
| 4.1 | Location + activity recognition | The manifest (third-party scan) lists precise and approximate location and ACTIVITY_RECOGNITION. Recording seems to use a foreground service, not "Allow all the time" *(unverified)*. | [FREE] | keep. Ask for foreground location only, at the first tap on Record, and keep tracking alive with a foreground service plus an ongoing notification. Don't ask for background location. |
| 4.2 | Notifications permission | Needed for the push categories in 6.5 (Android 13+ POST_NOTIFICATIONS). | [FREE] | keep. Ask after onboarding, not at launch. |
| 4.3 | Contacts | Once allowed, the address book is stored and re-synced from time to time to suggest people to follow. Turning the setting off stops future syncs. Whether contacts already stored are deleted is unclear *(unverified)*. | [FREE] | improve. Make it opt-in, match hashed contacts only, and delete stored data when the toggle is turned off. |
| 4.4 | Health data | Health data access (Health Connect / Google Fit) has its own Settings entry, separate from app permissions. | [FREE] | keep, using Health Connect. |

## 5. Settings entry & structure

```
You tab                         ⚙
┌──────────────────────────────┐
│ Settings                     │
│  Preferences ▸               │
│    Appearance / Display ▸    │
│    Default Maps, Feed order, │
│    Training Zones, Partner   │
│    integrations              │
│  Privacy Controls ▸          │
│  Push / Email notifications ▸│
│  Contacts · Health data ▸    │
│  Beacon ▸                    │
│  Change Email ▸              │
│  Help (chat) · Dev Hub ·     │
│  About · Legal               │
│  ── Account ──               │
│  Delete account              │
│  Log out                     │
└──────────────────────────────┘
```

| # | Detail | Strava behaviour | Gating | Our app |
|---|---|---|---|---|
| 5.1 | Gear icon entry | A gear at the top right of the You tab opens Settings. The units help article says the gear also appears on the Home and Groups tabs (medium confidence). There is no drawer. | [FREE] | keep, with the gear on You. Showing it on Home as well is optional. |
| 5.2 | Account section last | The last section of Settings is Account, holding "Delete account" (copy warns it is permanent) and "Log out". | [FREE] | keep |
| 5.3 | Preferences sync to account | Units, temperature, appearance and similar settings are saved to the account, so they apply on the web and other devices. | [FREE] | keep. Store them on the server and cache locally for offline use. |

## 6. Settings rows (detail)

| # | Detail | Strava behaviour | Gating | Our app |
|---|---|---|---|---|
| 6.1 | Display sub-screen (Android) | Gear > Display contains Units of Measurement, Temperature and Default tab. iOS has no Display step. | [FREE] | keep |
| 6.2 | Units: one switch | Metric shows km, min/km and m. Imperial shows mi, min/mi and ft. The app labels the choice "miles/kilometers", while the web labels it "miles/pounds" vs "kilometers/kilograms", so the same switch also sets weight units. Elevation can't be set separately *(unverified on current build)*. | [FREE] | improve. Keep one master switch, and offer optional overrides for elevation and weight under "Advanced". Label it with distance and weight. |
| 6.3 | Temperature separate | °C/°F is set independently of the units switch. | [FREE] | keep |
| 6.4 | Appearance tri-state | Default (follow system) / Light / Dark, released June 2024. | [FREE] | keep |
| 6.5 | Push categories (7) | Activities, Friends, Challenges, Clubs, Events, Posts, Other. | [FREE] | keep. Also map each category to an Android notification channel. |
| 6.6 | Email notifications | A single newsletter opt-in, with no per-category options. | [FREE] | improve. Add per-category email options (weekly summary, comments, follows). |
| 6.7 | Autoplay videos | A toggle for autoplaying video in the feed. | [FREE] | improve. Offer Always / Wi-Fi only / Never. |
| 6.8 | Feed: map vs media | Chooses whether the user's own uploads show the map or a photo as the main feed visual. | [FREE] | keep |
| 6.9 | Other preference rows | Default Maps, Feed order (sort), Training Zones (custom), Partner integrations (shown on own activities). Gating not documented. | ? | keep all of them. |
| 6.10 | Hide weather | A toggle to hide weather on activities. | [SUB] | keep (free for us) |
| 6.11 | Beacon toggle | Turns live location sharing on or off. | [SUB] | keep (free for us) |
| 6.12 | Weight | Profile weight is used for power estimates and weight-class leaderboard filters, in the units from 6.2. | [FREE] | keep. Make it optional and private by default. |
| 6.13 | Watch units separate | Changing units in Strava doesn't change a paired watch or bike computer *(unverified)*. | [FREE] | keep (out of our control). Add a hint below the units row. |
| 6.14 | Help = virtual agent chat | Help opens a chat with a virtual agent, which is also the only way back from OTC to password login. | [FREE] | drop the chat bot (no support staff). Link to docs and GitHub Issues, and make every account change self-service in-app. |
| 6.15 | About / Legal | About shows version, maps attribution, company info and "Rate the app" (Play Store). Legal lists Terms, Privacy and Copyright. A Developer Hub link is also present. | [FREE] | keep. Show the version, OSM/map attributions, open-source licences screen and GitHub link. Replace Dev Hub with "API docs". |

## 7. Account security & email

| # | Detail | Strava behaviour | Gating | Our app |
|---|---|---|---|---|
| 7.1 | Switch to One-Time Codes | You > gear > scroll > "Switch to One-Time Codes" makes the account passwordless. Going back requires contacting support. | [FREE] | improve. Make it a two-way toggle in-app. |
| 7.2 | Change Email row | One field plus "Next". What happens next depends on the auth type. On web: Settings > My Account > Login Details. | [FREE] | keep |
| 7.3 | Password accounts | User re-enters the password, then a confirm link goes to the new address. The link only works if clicked while logged in; logged-out users must first sign in with the old email. A success message appears afterwards. | [FREE] | improve. The link works when logged out too (signed token). Also send a "your email was changed" notice with an undo link to the old address. |
| 7.4 | OTC accounts | A code goes to the old address first, then a second code to the new address. Without access to the old inbox, the user has to contact support. | [FREE] | keep. Add a fallback for users who lost the old inbox (re-verify via a linked Google account). |
| 7.5 | Google/Apple accounts | These start with no password and no OTC. To change email, the user must first enable codes or set a password. | [FREE] | improve. Let them change email after re-authenticating with their provider. |
| 7.6 | Duplicate email error | An email already in use is rejected with a generic "This is not a valid email address". | [FREE] | improve. Say "This email is already linked to another account" (an acceptable enumeration risk once the user is logged in). |
| 7.7 | Log out everywhere | Support asks users to log out on every device after an email change, because a stale session can revert it. This suggests there is no server-side "log out everywhere" *(unverified)*. | [FREE] | improve. Revoke all other sessions when email or password changes, and add a "Log out of all devices" row. |

## 8. Logout

| # | Detail | Strava behaviour | Gating | Our app |
|---|---|---|---|---|
| 8.1 | Log out row | The last row of Settings > Account. | [FREE] | keep. If unsynced or in-progress recordings exist, show a confirm dialog warning about them. Logging out clears tokens and cached personal data. |

## 9. Account deletion

```
Settings ▸ Delete account
   ▼
Re-authenticate (log in again)
   ▼
┌──────────────────────────────────────┐
│ Delete your account                  │
│ ⚠ Public segments/routes stay        │
│ ⚠ Clubs you own become orphaned      │
│ How to export your data ↗            │
│ [✓] I've downloaded my data / don't  │
│     want it                          │
│ ( Request Account Deletion )  ← disabled until ticked
└──────────────────────────────────────┘
   ▼
Email "Delete Your Account" link ─► deleted
```

| # | Detail | Strava behaviour | Gating | Our app |
|---|---|---|---|---|
| 9.1 | Re-auth first | Tapping Delete asks the user to log in again before the deletion page opens. | [FREE] | keep |
| 9.2 | Export checkbox gate | The "Request Account Deletion" button is disabled until the box "downloaded my data / don't want it" is ticked. A link explains bulk export. | [FREE] | improve. Add an in-app "Export now" button (GPX/FIT zip) next to the checkbox. |
| 9.3 | Email confirm link | The request triggers an email, and the account is deleted only when its "Delete Your Account" link is clicked. Help says to check spam. Link expiry and grace period are not documented *(unverified)*. | [FREE] | improve. Confirm in-app with a code instead of a link, and allow a 14-day grace period that ends if the user logs back in. Google Play requires in-app deletion plus a web deletion URL. |
| 9.4 | Public segments/routes persist | Private segments and routes are deleted. Public ones remain under a perpetual license. Activities are removed from leaderboards. Public segments can't be made private, so users must delete them first. | [FREE] | improve. At deletion, let the user choose to delete or anonymise their public segments and routes. |
| 9.5 | Owned clubs orphaned | The user's name and posts disappear from clubs they joined. Clubs they created stay but have no owner, so nobody can delete them or add admins. | [FREE] | improve. Before deletion, require transferring ownership of each club or auto-promote the oldest admin. |
| 9.6 | "Former Member" attribution | Developer Hub content from deleted users is shown as written by "Former Member". | [FREE] | keep the pattern: show "Former member" for any remaining comments and kudos. drop the Dev Hub part (no forum). |
| 9.7 | Subscription is separate | Deleting the account doesn't cancel a Play subscription, and the deletion screen warns about this. | [SUB] | drop (we have no subscriptions). |

---

## Acceptance checklist

**Install & splash**
- [ ] 1.1 The app opens on the tab set in Settings > Display > Default tab, with no flash of the other tab.
- [ ] 1.2 With dark mode set, the splash and login screens show no white flash.

**Sign-up / onboarding**
- [ ] 2.1 Sign-up works with email + password, emailed code, and Google. There is no Facebook button.
- [ ] 2.2 Sign-up collects first and last name, birthday, and gender (optional, with "prefer not to say").
- [ ] 2.3 The sport chosen in "Choose a sport" becomes the default activity type on the Record screen.
- [ ] 2.4 The goals screen allows several choices and has a Skip button.
- [ ] 2.5 The level screen offers 4 levels and has a Skip button.
- [ ] 2.6 Before the first recording, a privacy screen appears and lets the user set a privacy zone.
- [ ] 2.7 Onboarding asks for default visibility (Everyone/Followers/Only me), and the answer is saved to the account.
- [ ] 2.8 (If Apple is shipped) A relay email address appears in Settings and can be changed.
- [ ] 2.9 (If Apple is shipped) A new Apple account with a relay email gets a "Link existing account" prompt.

**Login**
- [ ] 3.1 One account can have several working login methods at once.
- [ ] 3.2 "Email me a code" sends a code, and entering it logs the user in and keeps them logged in.
- [ ] 3.3 A code is rejected after 5 minutes, and the screen counts down to expiry.
- [ ] 3.4 Requesting codes too often triggers a server rate limit and a message saying how long to wait.
- [ ] 3.5 Entering only the email of a Google account shows "Continue with Google", not a password dead end.
- [ ] 3.6 Forgot password sends a code that logs the user in and then offers "Set a password".
- [ ] 3.7 Credential Manager autofill works on the login fields.

**Permissions**
- [ ] 4.1 Location is first requested when the user taps Record. Recording keeps going with the screen off, using a foreground service, without background-location permission.
- [ ] 4.2 The notification permission is requested after onboarding, not at launch.
- [ ] 4.3 Contacts sync is opt-in, and turning it off deletes contacts stored on the server.
- [ ] 4.4 Settings has a Health Connect entry that shows the current grant state.

**Settings**
- [ ] 5.1 The gear on the You tab opens Settings.
- [ ] 5.2 Account (Delete account, Log out) is the last section.
- [ ] 5.3 A unit change made on one device shows up on another after login.
- [ ] 6.1 Android Settings has a Display sub-screen with units, temperature and default tab.
- [ ] 6.2 The units switch changes distance, pace, elevation and weight together. Advanced overrides work on their own.
- [ ] 6.3 The temperature unit changes independently of the units switch.
- [ ] 6.4 Appearance Default follows the system theme, and Light/Dark override it.
- [ ] 6.5 Seven push categories can each be turned on or off and map to Android channels.
- [ ] 6.6 Email notification categories can each be turned on or off.
- [ ] 6.7 Autoplay offers Always / Wi-Fi only / Never, and the feed respects it.
- [ ] 6.8 The feed visual (map or media) for the user's own activities follows the preference.
- [ ] 6.9 Default Maps, Feed order, Training Zones and Partner integrations rows exist and work.
- [ ] 6.10 Every user can hide weather on activities.
- [ ] 6.11 Every user can turn Beacon on or off.
- [ ] 6.12 Weight is optional, private by default, and shown in the selected units.
- [ ] 6.13 The units row shows a hint that watches keep their own units.
- [ ] 6.14 Help links to the docs and GitHub Issues, and every account action works without contacting support.
- [ ] 6.15 About shows the version, map attributions, OSS licences and a Rate app link. Legal lists Terms and Privacy.

**Account security & email**
- [ ] 7.1 Switching between password and one-time codes works in both directions in the app.
- [ ] 7.2 The Change Email screen has one field and a Next button.
- [ ] 7.3 Password accounts: the password is re-checked, the confirm link works even when logged out, and the old address gets a change notice with an undo link.
- [ ] 7.4 OTC accounts: changing email needs a code from the old address and then a code from the new address.
- [ ] 7.5 A Google account can change its email after re-authenticating with Google.
- [ ] 7.6 Entering an email that is already in use shows an "already linked to another account" message.
- [ ] 7.7 Changing email or password logs out all other sessions, and "Log out of all devices" works.

**Logout**
- [ ] 8.1 Logging out with unsynced activities shows a warning. Logging out clears tokens and cached personal data.

**Deletion**
- [ ] 9.1 Opening Delete account requires re-authentication.
- [ ] 9.2 The Request button stays disabled until the export checkbox is ticked, and the "Export now" button produces a zip.
- [ ] 9.3 Deletion is confirmed by a code. During the 14-day grace period, logging back in cancels it. A web deletion URL is available (Play policy).
- [ ] 9.4 The user can choose to delete or anonymise public segments and routes, and their activities are removed from leaderboards.
- [ ] 9.5 Deletion is blocked until each owned club has a new owner, or ownership is auto-transferred to an admin.
- [ ] 9.6 Remaining comments and kudos from deleted users show "Former member".

---

## Sources
- Strava Support, One-Time Codes on Strava: https://support.strava.com/hc/en-us/articles/32040867673485-One-Time-Codes-on-Strava
- Strava Support, Delete your Strava account: https://support.strava.com/en-us/articles/15401906-delete-your-strava-account
- Strava Support, Android app settings: https://support.strava.com/en-us/articles/15402004-android-app-settings
- Strava Support, Changing units of measurement: https://support.strava.com/hc/en-us/articles/360057378851-Changing-the-Units-of-Measurement-on-your-Strava-Account and https://support.strava.com/en-us/articles/15401745-changing-the-units-of-measurement-on-your-strava-account
- Strava Support, Personal settings: https://support.strava.com/en-us/articles/15401922-how-do-i-change-my-personal-settings-on-strava
- Strava Support, How to change your email address: https://support.strava.com/hc/articles/35914426524685-How-to-Change-Your-Email-Address
- Strava Support, Sign in with Apple: https://support.strava.com/hc/en-us/articles/36009863440013-Sign-in-With-Apple
- BikeRadar, Strava drops Facebook login: https://www.bikeradar.com/news/strava-drops-facebook-login
- 9to5Google, Strava dark mode (June 2024): https://9to5google.com/2024/06/26/strava-dark-mode-android-ios/
- Tom's Guide, Strava privacy settings: https://tomsguide.com/wellness/fitness/how-to-adjust-your-strava-privacy-settings
- Exodus Privacy report (manifest permissions, low confidence): https://reports.exodus-privacy.eu.org/en/reports/562039
- ScreensDesign, Strava onboarding (low confidence): https://screensdesign.com/articles/strava-onboarding-design/
- Lazyweb, Strava onboarding flow (low confidence): https://lazyweb.com/canvas/flows/strava/onboarding
- MacMyths, Strava units (low confidence): https://macmyths.com/how-to-change-strava-distance-units-between-kilometers-and-miles/
