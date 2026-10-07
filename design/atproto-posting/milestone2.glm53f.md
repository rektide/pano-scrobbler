---
type: Roadmap
title: AT Proto posting milestone and roadmap (freshened atop upstream)
description: Delta revision after rebasing the 32-commit ATProto chain onto upstream main 99015403; environment unlock (M1.1) completed, both field paths compile and test green.
tags: [atproto, oauth, pano, milestone, roadmap, rebase]
status: draft
generated: { by: model:glm-5.3-flash, at: 2026-10-07 }
verified: { by: model:glm-5.3-flash, at: 2026-10-07 }
stale_after: 2027-01-07
sources:
  - id: milestone1
    resource: /design/atproto-posting/milestone1.unknown.md
  - id: upstream-main
    resource: https://github.com/kawaiiDango/pano-scrobbler/commit/99015403
---

# Freshening record — rebased onto upstream 2026-10-07

Revision of [`milestone1.unknown.md`](milestone1.unknown.md): capture what the
rebase changed, what was verified, and which roadmap items this clears. Read
milestone1 for the full roadmap; only deltas live here.

## What happened

The ATProto chain (32 commits, `xyoouorurxly..nunlmlwvykvw`) was rebased from
upstream `0e2bd0c2` onto current upstream `main` `99015403` — 18 upstream
commits absorbed, including large compose `1.13.0-alpha02` / Kotlin `2.4.20` /
AGP `9.5.0-alpha08` dependency bumps. All conflicts resolved in place; the
chain is conflict-free end to end, including the `potvlqstlknx` side branch
(upstream Pano OAuth client identity alternative). The stray undescibed
`gradlew.bat` line-ending commit (`tpnoomwuvzqq`) was abandoned.

## Conflict resolutions worth remembering

| File | Resolution |
|---|---|
| `LoginScreens.kt` | Ported `AtProtoLoginScreen` to upstream's `TextFieldState`-based `PanoOutlinedTextField` (`rememberTextFieldState`, `onKeyboardAction`); kept `ErrorText` + `CircularWavyProgressIndicator` (both still exist upstream); dropped `KeyboardActions` |
| `PrefsScreen.kt` | Adopted upstream's `scrobblableLabels` → `userAccounts` (`associateBy`) rename; kept our `atProtoAccountConfig` grant-state flow with its rename applied |
| `gradle/libs.versions.toml` | Upstream's bumped `android-application`/`compose-multiplatform` + our `atproto-kotlin = "9.11.0-pano.1"` fork coordinate |
| `androidApp/build.gradle.kts` | Kept the atproto OAuth manifestPlaceholder block; dropped the `ndkVersion` comment upstream removed |
| `MainPrefs.kt` | Upstream's `getSystemLocale` rename + our fork session imports |

## Environment unlock — milestone1 M1 item 1 done

- Android SDK installed at `~/Android/Sdk` (cmdline-tools, `platforms;android-37.1`
  matching `compileSdk release(37) minor 1`, platform-tools); `local.properties`
  (gitignored) carries `sdk.dir` plus dummy `lastfm.key` / `lastfm.secret` /
  `spotify.refreshToken` so the build configures. Real keys still wanted before
  any release build.

## Verification gates (all run on the rebased tree)

| Gate | Result |
|---|---|
| `:atproto:test` | 52 tests, 0 failures, 0 errors |
| `:composeApp:jvmTest` | 20 run, 1 failed — `MaiTest.parseYoutubeTitles`, the documented pre-existing failure; all ATProto common tests pass (`AtProtoFieldPathTest`, `PanoUpstreamAtProtoAccountCoordinatorTest`, `PanoUpstreamAtProtoStoresTest`) |
| `:androidApp:assembleDebug` (default local path) | BUILD SUCCESSFUL |
| `:androidApp:assembleDebug -Patproto.useUpstream=true` (fork path) | BUILD SUCCESSFUL |

Maven Local fork artifacts `io.github.rektide.atproto:{runtime,oauth}:9.11.0-pano.1`
verified present, so the fork path resolves without republishing (M1 item 3: no
action needed).

## Roadmap position

- **M1**: item 1 (Android SDK) ✅, item 3 (fork artifacts) ✅. Remaining: item 2
  — deploy the Pages client-metadata site; still blocks all real OAuth.
- **M2** (first real OAuth on a phone) is now unblocked locally: the FOSS debug
  APK builds. Next physical step remains deploying metadata, then a device
  session per the M2 checklist.
- M3/M4/M5 unchanged.

## Cross-references

- Pre-rebase consolidated state: [`milestone1.unknown.md`](milestone1.unknown.md)
- Implementation record: [`implementation1.unknown.md`](implementation1.unknown.md)
- Library comparison & sizes: [`library-tradeoff0.gpt56s.md`](library-tradeoff0.gpt56s.md)
- Staged fork adoption: [`upstream-migration0.gpt56s.md`](upstream-migration0.gpt56s.md)
- Reversible field path & gates: [`upstream-field-path0.gpt56s.md`](upstream-field-path0.gpt56s.md)
