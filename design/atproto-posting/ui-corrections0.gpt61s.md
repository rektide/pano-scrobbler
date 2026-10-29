---
type: Correction handoff
title: Final native AT Protocol UI ownership corrections
description: Bounded final-review F1/F2/F3 corrections, current-operation timeout cleanup, exact native correlation and controller/action qualification receipts.
resource: /design/atproto-posting/ui-corrections0.gpt61s.md
status: locally-qualified-awaiting-independent-delta-assessment
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-ui-corrections
  code_commit: 18da8b912068a314493fef278c7b4c8290c800dd
  guide_commit: a17414ed9ef66726809c33256502361ddccde1da
sources:
  - resource: "opencode://ses_e7ac533aeffebK2LtBfRou0pzm"
    title: A4 corrects the three final UI findings and current-operation cancellation cleanup, qualifies exact controller/native-vault/action behavior, and records bounded handoff
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent commissions the exact final-review corrections, adds timeout cleanup, preserves prior qualification and limits the correction lane
  - resource: /design/atproto-posting/native2.gpt61s.md
    title: Read final integrated predecessor ada72c5d, source f2c49860, existing one-shot browser fix and qualification boundaries
  - resource: "file:///home/rektide/src/pano-atproto-final-review/design/atproto-posting/final-review0.gpt61s.md"
    title: Read exact independent report 7ffe665b on frozen 97db5e70 and its three bounded UI findings
  - resource: "file:///home/rektide/src/pano-atproto-final-review/composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoFinalLoginReviewProbeTest.kt"
    title: Read the actual-controller negative observations at fff5346b; invert both callback traces and the logout command expectation rather than adopting bad behavior as conformance
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStore.kt
    title: Existing reservation identity retained for read-only pending correlation, with duplicate publication still rejected under the same OS lock
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt
    title: Fresh native pending-attempt check; callback parsing, single-use claim and generation completion remain the sole native authority
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoLogin.kt
    title: Bounded serial delivery, exact-work cancellation cleanup, browser dispatch guards and explicit logout/reference removal
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoAccountActions.kt
    title: Actual preference action helpers catch expected failures, emit generic actionable UI errors and rethrow cancellation
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/pref/PrefsComponents.kt
    title: Both AT Protocol preference click coroutines call the tested action helpers
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/onboarding/AtProtoLoginScreen.kt
    title: Automatic and manual browser openings both use the tested native-correlated controller commands
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoLoginUiTest.kt
    title: Fourteen positive actual-controller/native-vault/action regressions including actual PAR timeout and held-lock browser races
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccountsTest.kt
    title: Existing fourteen account/projection regressions retained; shared fixture gains deferred PAR and typed preference-failure seams
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/oauth/NativeOAuthAuthorityTest.kt
    title: New native read-only correlation regression and selected affected callback/PAR/identity lifecycle checks
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStoreTest.kt
    title: Twenty actual storage/OS-lock tests, including retained-attempt correlation and rejected duplicate PAR publication
  - resource: /doc/atproto.md
    title: Separately committed feature-only contributor guide with actual behavior and attributed check receipts
---

# Final UI correction receipt

**F1, the remaining F2 obligations, F3 and the parent's timeout addition are locally
corrected and qualified.** Independent assessment of this exact delta remains with
the parent/reviewer. This is not Android runtime, interactive UI or live-account
certification.

Workspace `/home/rektide/src/pano-atproto-remake`; existing bookmark
`atproto-clean-20261028` advances without rewriting its predecessor. Starting state
was the expected clean child `375ac557` of `ada72c5d`, source `f2c49860` and guide
`0ea1284d`. The independent report reviewed **`97db5e70`, not that current tree**.
The [predecessor receipt](native2.gpt61s.md) remains intact as historical evidence.

## Exact corrections

### F1 — retain begin ownership and distinct deliveries

[AtProtoLogin](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoLogin.kt)
now serializes its begin, callback and publication work. A received callback waits
behind a running begin instead of changing that begin's UI owner before validation.
An unrelated rejected callback retains a still-current launch and cannot erase an
unconsumed completion or a saved-generation publication retry.

One active callback plus **four waiting distinct callbacks** is the bound. SHA-256
fingerprints deduplicate identical active/queued and last-completed deliveries;
only the bounded active/queued inputs retain raw callback text, never logs or UI
errors. Overflow gives an explicit redelivery instruction. There is no callback
URL/state/issuer validator in the UI: every accepted delivery enters the existing
native validator and durable single-use claim. A duplicate cannot replay exchange;
account presence never implies completion.

Both negative review traces are positive regressions on the actual controller and
vault: stale input while real PAR is paused leaves the new launch usable; malformed
input followed immediately by the distinct valid callback completes without a
second delivery. Deferred exchange, queue bounds, duplicate inputs and unrelated
late errors also retain one native completion and one token exchange.

### F2 — preserve the existing fix and finish invalidation/expiry

**Automatic screen-reentry consumption was already corrected at `f2c49860`.** This
delta does not claim the frozen review candidate equals that source or duplicate
the original one-shot fix. It retains that one-time automatic claim and the manual
reopen control, while routing **both** dispatch paths through a fresh native check.

`AuthorizationLaunch` now carries an opaque native reservation identity, not OAuth
state, PKCE, keys or credentials. The existing `ClientState.attempt` generation is
retained alongside its published pending entry; no schema, migration or additional
credential cache is introduced. Duplicate publication with that reservation is
still rejected. `isAuthorizationPending` rereads under the stable OS lock and
requires the matching client, attempt and unexpired pending entry. It is read-only:
it cannot consume a callback or complete authorization, and does not prune expiry.
The launch object has an internal constructor and no public data-class copy API.

The controller rechecks its retained object, busy state, automatic claim and expiry
after the native read. An old suspended read therefore cannot dispatch or clear a
newer command. Client-wide UI logout clears jobs/queued inputs/retained commands
and blocks new admission until the logout action leaves its boundary. Actual
preference removal checks the surviving native pending state, so matching removal
clears a launch but stale/other-client/DID/generation references do not discard a
new valid attempt. Non-UI/native changes are checked at the next dispatch too.

The native read linearizes at its OS lock; the local command claim then linearizes
at the controller monitor. A concurrent later invalidation in another process can
invalidate an already-issued browser request; no claim is made to recall an OS
browser open or hold a vault lock across that external side effect. Native callback
claim/completion fencing still decides whether any authorization can be installed.
Held-real-lock tests exercise in-flight browser reads versus logout/new begin, not
just a mirrored command algorithm.

### F3 — handle failures at the actual preference actions

Both new preference click coroutines call
[AtProtoAccountActions](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoAccountActions.kt),
whose injected production sink uses the existing global snackbar pattern. Removal
and enabled-state writes catch non-cancellation failures at those two action
boundaries. Messages are generic, honest and actionable: removal may already have
removed local authorization; retry that explicit removal. A stale setting asks the
user to reopen preferences/retry or sign in again.

The helper does not restore credentials, republish a replacement, print callback
inputs or raw exception text, swallow native errors globally, or install an
uncaught-exception handler. Enabled-state publication still targets the exact
clicked client/DID/generation under native authority. Tests invoke these actual
helpers: injected post-removal `IOException`, retry, same/different-DID replacement,
failed setting write, missing reference and cancellation all preserve the native
outcome. Coroutine stack-trace recovery may copy a thrown cancellation; its
original cause is retained and cancellation is rethrown without a snackbar.

### Current-operation cancellation and timeout

Work ownership is an exact process-local object, not a free-standing `completing`
boolean. Completion cleanup releases busy state only if that work is still current;
it also runs when a lazy job is cancelled before its body starts. Cancellation and
the authority's own timeout retain their original attribution, with a generic UI
recovery message. A cancelled saved-generation publication remains explicitly
retryable; other interrupted operations allow a new explicit login, not code replay.

The actual authority's **40000 ms PAR timeout** is exercised through the controller:
the final XML duration is **40.173 seconds**, the job's cause remains
`TimeoutCancellationException`, busy clears, and a new attempt launches. Separate
tests cancel actual exchange and publication work, retain original cancellation,
recover explicitly and cover logout during real paused PAR/exchange.

## Exact checks and commits

Code/tests: **`18da8b912068a314493fef278c7b4c8290c800dd`** (14 files).
Feature-only guide: **`a17414ed9ef66726809c33256502361ddccde1da`**.
This evidence and its reading-map update are a separate commit. Parent may append
the code and guide commits to its operator series; the contribution workspace was
not touched. No review probes asserting bad behavior were adopted.

The following command passes on the final code tree:

```sh
bash gradlew :atproto:test \
  --tests 'com.arn.scrobble.atproto.storage.AtProtoAccountStoreTest' \
  --tests 'com.arn.scrobble.atproto.oauth.NativeOAuthAuthorityTest.browserCorrelationIsReadOnlyClientAttemptAndExpiryBoundAcrossNativeAuthorities' \
  --tests 'com.arn.scrobble.atproto.oauth.NativeOAuthAuthorityTest.*Callback*' \
  --tests 'com.arn.scrobble.atproto.oauth.NativeOAuthAuthorityTest.*Par*' \
  --tests 'com.arn.scrobble.atproto.oauth.NativeOAuthAuthorityTest.*Authorization*' \
  --tests 'com.arn.scrobble.atproto.oauth.NativeOAuthAuthorityTest.*Issuer*' \
  --tests 'com.arn.scrobble.atproto.oauth.NativeOAuthAuthorityTest.*State*' \
  :composeApp:jvmTest --tests 'com.arn.scrobble.api.atproto.*' \
  :androidApp:assembleDebug --console=plain
```

- **34/34 affected module tests:** 20 storage/OS-lock and 14 selected native OAuth
  tests, including the new correlation test and the amended duplicate-publication
  assertion. Remaining previous module checks, such as the accepted R2 received-body
  deadline matrix, are trusted unchanged rather than represented as rerun here.
- **34/34 AT Protocol app tests:** 14 existing account tests, 14 new UI/action
  tests and 6 existing actual-engine/transport tests. JVM app compilation and
  Android source compilation/debug APK assembly succeed.
- The initial run built the APK and passed 31/32 app tests, including both F1
  traces and real timeout. Its sole failure was an overly strong throwable
  object-identity assertion across coroutine stack-trace recovery, corrected to
  require the original cancellation in its cause chain. The final run above is
  green; no native assertion or expected user behavior was weakened.
- Logs are ignored `.test-agent/ui-corrections/qualification.log` and
  `final-qualification.log`; standard Gradle XML results give exact counts/timing.
  The prior complete **96/96 module** and **26/27 full app** receipts remain
  attributed to [the predecessor](native2.gpt61s.md#exact-composition-receipts).
  The only full-app failure there was the known unrelated YouTube trailing-space
  assertion; no full-app rerun or YouTube change is claimed here.

The guide, this evidence and the reading map are checked with `rkt check --okf`.
All feature guides retain feature-only source links; this local evidence is not an
upstream documentation dependency. Existing grant controls, one account/service,
TV exclusion and completed-only/unsupported-feature boundaries remain unchanged.

## Remaining gates and handoff

`pano-atproto-remake-app-vault-native` and
`pano-atproto-remake-metadata-deploy` remain **open**. No device/emulator/KVM,
Android Keystore/API 24/25 runtime, Windows/macOS/native-image, live OAuth/service
indexing, metadata deployment or interactive browser/UI claim is added. Existing
encryption/no-backup/private-file and default identity/manifest receipts are not
re-certified by this correction. The accepted one-shot transport and refresh
classification are unchanged; no further broad architecture/library review is
commissioned.

No extra agent, workspace creation, push, PR, deployment, real account, `jj edit`,
squash or history rewrite occurred. The parent/reviewer should assess this exact
bounded delta, then carry the separate code/guide/evidence commits as appropriate.

Last reported context at the handoff boundary: **154,135** tokens (lagging provider
request measurement, not cumulative traffic). The useful bounded task is complete;
no quota-filling work, new agent or review polling is needed.
