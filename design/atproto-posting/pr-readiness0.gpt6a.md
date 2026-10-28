---
type: Assessment
title: Rocksky OAuth upstream PR readiness
description: Current upstream comparison, repeatable local checks, and the self-contained contribution boundary before unpublished fork adoption.
resource: /design/atproto-posting/pr-readiness0.gpt6a.md
status: draft
generated: { by: "model:openai/gpt-6-astra#xhigh", at: 2026-10-28 }
sources:
  - resource: "opencode://ses_e7ba37263ffeyDOE27UVF3xl6Q"
    title: Inspected the application stack and independently checked upstream, tests, APK assembly, and contribution blockers
  - resource: https://github.com/kawaiiDango/pano-scrobbler/issues/937
    title: Rocksky native support request, read with GitHub CLI
  - resource: /design/atproto-posting/milestone2.glm53f.md
    title: Prior rebase and environment verification
  - resource: /design/atproto-posting/milestone1.unknown.md
    title: Staged fork decision and outstanding field-validation gates
  - resource: /design/atproto-posting/implementation1.unknown.md
    title: Local feature implementation and baseline test failure
---

# Rocksky OAuth upstream PR readiness

## Upstream and contribution boundary

`git ls-remote origin refs/heads/main` confirms upstream remains
`990154031e761b8e5460a0585fc834b43c9e2d71`. The checked application head is
`4a8f19f3d800614869fd051a360dcade86d6e2dd`; its working-copy child was empty.
The stack already descends from this upstream revision, and no stack revision
has conflicts. No refresh/rebase or source correction was needed.

**The full stack is not independently reproducible.** Both
[`atproto/build.gradle.kts`](/atproto/build.gradle.kts) and
[`composeApp/build.gradle.kts`](/composeApp/build.gradle.kts) unconditionally
depend on `io.github.rektide.atproto:{runtime,oauth}:9.11.0-pano.1`.
[`settings.gradle.kts`](/settings.gradle.kts) resolves these from Maven Local.
The default-off runtime selector does not remove their compile-time dependency.
Successful builds on this machine therefore do not establish a fresh contributor
or CI build of the full stack.

Recommended contribution starting point, subject to the parent's scope decision:
`eeb47616eb9e8e33b03cc0cc5294b4baba77dace` (local implementation record), before
the fork dependency commit `03367ec2f9d5af641e5ed784bb5664fb9e05461f`.
That checkpoint includes OAuth, DPoP, discovery, completed Rocksky and Teal
scrobbles, target controls, Android callbacks, and desktop callback paste. The
prior rebase's login-field and preferences fixes are already present there.
The local OAuth/HTTP/identity/crypto implementation, login screen, preferences
screen, and Android callback parser are unchanged between that checkpoint and
the inspected head. Preserve the full experimental stack as its own work; an
upstream contribution can branch at this boundary without deleting it.

The final selected branch still needs its own build after integration with the
separately owned upstream Pages metadata and corresponding application defaults.
This assessment did not modify those defaults or site files.

## Verification on the inspected head

- `bash gradlew :atproto:test --rerun --console=plain`: successful; XML reports
  **52 tests, zero failures/errors/skips**. This explicitly executed the test
  task rather than relying solely on cached results.
- `bash gradlew :atproto:test :composeApp:jvmTest :androidApp:assembleDebug
  --continue --console=plain`: debug APK assembly succeeds (up-to-date), app
  tests execute with **20 tests, one failure**. The failure is the documented
  `MaiTest.parseYoutubeTitles` trailing-space expectation: index 46 expects
  `(Artist, Track )`, parser returns `(Artist, Track)`. Its test file has no
  change against upstream. All 13 ATProto app tests pass.
- Android build-tools `36.0.0/apksigner verify` accepts
  `androidApp/build/outputs/apk/debug/pano-scrobbler-debug.apk`.
- `bash gradlew :androidApp:assembleDebug -Patproto.useUpstream=true
  --console=plain`: successful, including Android recompilation and APK
  packaging. The output APK now selects this experimental fork backend;
  rebuild with the default selector before installing a local-path field build.
- Use `bash gradlew` here: upstream itself tracks the wrapper as mode `100644`.
  SDK and signing properties are configured locally; no property values were
  copied into this report.

## PR claims and outstanding evidence

The implementation addresses the OAuth login and native completed-scrobble
portion of [issue 937](https://github.com/kawaiiDango/pano-scrobbler/issues/937).
The provider does not implement Rocksky now-playing, album-art enrichment,
charts, social reads, or record deletion. Describe the submission as this
bounded contribution rather than full last.fm parity.

The earlier milestones still provide no real-account authorization, Android
callback/process-death, or live Rocksky indexing evidence. This session did not
perform those actions. A draft PR can expose that remaining validation honestly.
Upstream metadata must be deployed before an upstream-identity build can log in.

Keep the prior storage/refresh limits visible during review: secrets remain in
the existing preferences store, and refresh locks are process-local. Android
actually declares `:bgScrobbler` components in
[`AndroidManifest.xml`](/composeApp/src/androidMain/AndroidManifest.xml), so
single-process safety must not be assumed. Release/R8, old-device behavior, and
desktop native-image reachability remain outside the checks above.

## Related records

- [Freshening milestone](milestone2.glm53f.md): precedent for the same upstream
  base and SDK/signing setup.
- [Consolidated roadmap](milestone1.unknown.md): owns the fork-adoption decision
  and field/hardening gates; the contribution boundary here does not supersede it.
- [Local implementation](implementation1.unknown.md): explains the functionality
  available before fork adoption and the existing parser-test failure.
