---
type: Independent integration review
title: Final prospective native AT Protocol integration assessment
description: Exact combined-candidate review of credential corrections, native authority and user-flow boundaries, with three actionable UI findings and four focused review probes.
resource: /design/atproto-posting/final-review0.gpt61s.md
status: review-requires-ui-corrections
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-final-review
  reviewed_commit: 97db5e7084b3c988e9697355fade08b8ba59736c
  native_base: 43065bf364c10b8cb5edb0668d04b119cd820a7e
  transport_delta: b3a69e4d62f73bb5de2eba4f06cf07e3ee9e3db0
  review_probes: fff5346b53c9fbb5ef6bf7e6b944cd133cbec9f9
sources:
  - resource: "opencode://ses_e7af14a6cffeDevLvL0joxsto6"
    title: B3 independently assesses the explicit prospective combined head, adds actual-login observation and real-engine composition probes, and records source-bound user-flow findings without production changes
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent commissions separate final integration assessment while A3 integrates and checks the production lane
  - resource: /design/atproto-posting/native1.gpt61s.md
    title: Exact native adoption and R1/R2 corrections, reported checks and qualification limits
  - resource: /design/atproto-posting/native0.gpt61s.md
    title: Activated provider, login, projection, journal and native callback composition
  - resource: /design/atproto-posting/oauth0.gpt61s.md
    title: Original native authority, status and leased-engine design
  - resource: /design/atproto-posting/storage0.gpt61s.md
    title: Durability and platform promises distinguished from runtime evidence
  - resource: /doc/atproto.md
    title: Feature-only contributor guide and deliberately separate metadata/application PR outlines
  - resource: "file:///home/rektide/src/pano-atproto-review/design/atproto-posting/lifecycle-review0.gpt61s.md"
    title: Frozen def70d8a review R1/R2/R3 and earlier qualification boundaries
  - resource: "file:///home/rektide/src/pano-atproto-transport/design/atproto-posting/transport0.gpt61s.md"
    title: Already accepted real-engine correction, public API rationale and six focused original checks
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt
    title: Fresh native session work, record request/result validation, target journal and refresh outcomes
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStore.kt
    title: Reservations, claims, rotating/nonrotating operations and explicit reference-bound removal
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/http/DpopHttp.kt
    title: Fresh-proof nonce retries and per-attempt received-status reset
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/http/BufferedHttp.kt
    title: Streaming bounded response and failure-attribution seam
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/NativeCallback.kt
    title: Exact callback validation and redacted URI syntax exception boundary
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/oauth/NativeOAuthAuthorityTest.kt
    title: Actual R1/R2 inversions, cancellation and unknown-status distinction, fresh grants, complete request byte limit and partial journal regressions
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoLogin.kt
    title: Actual controller ownership, callback admission and retained launch/completion state
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/onboarding/AtProtoLoginScreen.kt
    title: Actual browser side effect, completion consumption, publication retry and granted target controls
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccounts.kt
    title: Generation-fenced short preference projection, exact clicked reference removal and native failures
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProto.kt
    title: Actual singleton uses the tested controller and one native authority/leased client composition
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/pref/PrefsComponents.kt
    title: Native removal and enabled-state menu actions launch without local error handling
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/utils/Stuff.kt
    title: Supervisor application scope has no action-level exception handler
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccountsTest.kt
    title: Existing reported actual-vault projection, generation, identity-switch, cached-provider and expected failure checks
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoFinalLoginReviewProbeTest.kt
    title: Three new actual-controller observations demonstrate callback takeover/loss and retained browser launch after logout
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoFinalEngineCompositionProbeTest.kt
    title: New actual-engine composition proves nonce and invalid-token retry, single-attempt 503 refresh classification, fresh proofs and grant narrowing
  - resource: /composeApp/src/androidMain/kotlin/com/arn/scrobble/navigation/NavFromOutsideEffect.android.kt
    title: Initial, restored native and new-intent forwarding, distinct from full activity/UI automation
  - resource: /composeApp/src/androidMain/kotlin/com/arn/scrobble/navigation/DeepLinkUtils.kt
    title: Native scheme route always invokes the shared authority through the actual controller
  - resource: /composeApp/src/androidMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoVault.android.kt
    title: Dedicated no-backup encrypted vault, fail-closed key loss and explicit atomic durability operations
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/posting/RecordCid.kt
    title: Canonical record CID profile parsing, not content or repository verification
  - resource: /build.gradle.kts
    title: Paired native identity override validation and official defaults
  - resource: /settings.gradle.kts
    title: Public dependency repositories and one local protocol module, without Maven Local
---

# Final prospective integration assessment

## Verdict and exact target

**Credential corrections compose, and the native architecture is suitable for
operator/upstream discussion. Correct the three P2 user-flow findings below before
presenting the contribution as a finished login/logout experience.** No new P1
credential-replay, stale-generation authorization or account-resurrection defect
was found in this assessment. This is not release/device qualification.

Reviewed head: **`97db5e7084b3c988e9697355fade08b8ba59736c`**, exactly native evidence
head `43065bf364c10b8cb5edb0668d04b119cd820a7e` (source `8b70e871`) plus only the
accepted code/test delta `b3a69e4d62f73bb5de2eba4f06cf07e3ee9e3db0`, duplicated
without conflicts. Workspace `/home/rektide/src/pano-atproto-final-review`;
`atproto-final-review-20261029` freezes that candidate. Review probes are separate
commit `fff5346b53c9fbb5ef6bf7e6b944cd133cbec9f9`.

A3's uncommitted tree was not read or changed. Parent must compare its final
production tree to this explicit candidate. Different production source requires
an explicit revision-specific delta assessment, not assumed equivalence. The
candidate guide still says R3 is pending; its already-commissioned text refresh is
**not** a new finding. No production fix is included in this review.

## F1 — callback admission can steal a newer login or drop a distinct valid callback

**P2; reproduced through the actual controller and native vault.**

[`AtProtoLogin.completeCallback`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoLogin.kt)
lines 43–58 increments the UI operation counter before the authority determines
whether the callback belongs to anything. It also drops **every** callback while
`completing`, not just duplicate delivery of the same callback.

Two new traces in [the actual-controller probes](/composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoFinalLoginReviewProbeTest.kt):

1. Pause `login.begin` at the real authority's PAR request. Deliver an old,
   unmatched callback. It finishes with a generic error and supersedes the begin's
   operation ID. Release PAR: the new pending authorization is durably published,
   but its `finish` is ignored. The actual UI has **no launch and only the stale
   callback error**. Direct completion of the new saved callback succeeds, proving
   this is lost UI ownership, not failed PAR or consumed pending state.
2. With valid pending authorization, invoke `completeCallback("not-a-callback")`
   and immediately deliver its distinct valid callback. Only the malformed input
   runs: zero token exchanges and no completion. Redelivering the already received
   valid callback explicitly then succeeds. The native validator correctly
   preserved it; process-local coordination silently lost its delivery.

**Smallest correction:** make admission/ordering distinguish an applicable
callback from an unrelated older input. A failed unrelated callback must not erase
an active begin's launch. Deduplicate identical callbacks, but preserve or process
distinct delivery rather than dropping it merely because another completion is
busy. Keep the native single-use/generation authority; do not replay code exchanges
or infer success from an existing account. Invert both probes into positive
ownership/delivery regressions.

This is a recoverable login availability/UI defect, not evidence of code leakage,
state-validation bypass or account compromise.

## F2 — retained launch state automatically reopens the browser on screen re-entry

**P2; retained state reproduced, browser dispatch source-confirmed.**

[`AtProtoLoginScreen`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/onboarding/AtProtoLoginScreen.kt)
line 37 uses `LaunchedEffect(state.launch)` to open any retained launch. That
effect runs on each **new composition**, not once per authorization across screen
instances. There is no launch-consumption/acknowledgement API; the process-global
controller retains `State.launch` until some later operation replaces it. Its
`expiresAt` is not consulted here. A manual reopen button already exists at line 51.

Trace: start authorization, leave its screen while approval is pending, then
re-enter the login/destination screen. The unchanged launch automatically opens
the external browser again without another sign-in/reopen action. Repeated screen
entry repeats that dispatch. The third new controller probe also shows explicit
client-wide logout removes native authorization but leaves the old launch in UI
state, so a later entry can reopen that invalidated request URI.

**Smallest correction:** separate retained launch information/manual reopen from
the one-time automatic browser command. Consume the latter once per current
attempt across screen instances; invalidate it on explicit logout/supersession and
honor expiry. Retain an intentional reopen control if useful. Verify one initial
dispatch, no automatic re-entry dispatch, and no stale dispatch after logout.

No interactive Android/desktop browser loop was executed. The probe establishes
retention; the actual unconditional Compose effect establishes what a new screen
does with it. This is not a claim that ordinary recomposition alone loops forever.

## F3 — preference actions let expected native failures escape to uncaught handling

**P2; source-confirmed action boundary, not an executed Android crash test.**

The new AT Protocol menu actions in
[`PrefsComponents.kt`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/pref/PrefsComponents.kt)
lines 534–536 and 564–572 call native removal/publication directly from
`Stuff.appScope.launch`, with no error result handling. That
[application scope](/composeApp/src/commonMain/kotlin/com/arn/scrobble/utils/Stuff.kt)
lines 259–265 has a supervisor job and, on Android, `Dispatchers.Main.immediate`,
but no exception handler for these actions.

These are expected boundaries, not hypothetical exceptions: the existing
`failedGenerationBoundRemovalLeavesNoAuthorizationAndExplicitRetryRemovesStaleReference`
test injects a preference-write failure and receives `IOException` after native
removal. A stale toggle also calls `publishAccount` with a replaced handle and
receives `StaleSessionException`. The actual click coroutine lets either failure
reach uncaught handling (normally fatal on Android), rather than explaining the
completed local removal or allowing an actionable retry in the current UI.

**Smallest correction:** handle non-cancellation failures at these two new UI
action boundaries using the existing user-error/snackbar pattern. Preserve the
authoritative result: failed projection does not restore authorization, and stale
toggle does not update the replacement. Preserve cancellation; do not broadly
swallow errors in the native authority or install a global catch-all.

## What holds in this composition

- **R1:** the exact input-bearing `URISyntaxException` is discarded before any
  claim/exchange. Other callback-validation messages are generic. The existing
  inverted regression inspects the complete throwable stack, cause/suppressed
  chains and both sentinels, asserts zero exchange, then completes valid pending
  authorization. Its reported pass is trusted, not rerun merely to confirm.
- **R2:** per-attempt received status resets before every nonce retry.
  Cancellation is wrapped outside the real `withTimeout` only to carry known
  status to durable outcome classification, then the original failure is
  rethrown. Received 400–599 stays failure but retains proven-unconsumed state;
  unknown status and malformed 2xx remain quarantined. Existing matrix, real
  40000 ms deadline and cancellation-attribution tests were inspected and their
  reported passes trusted. No new deadline/full-suite repetition was needed.
- **R3 plus resource recovery:** the new real-engine composition probe performs
  PAR/exchange, resource `use_dpop_nonce`, exact `invalid_token`, then forced
  refresh returning 503/`Retry-After: 0`. Only one failed refresh reaches the wire;
  no resource resend follows it. A later explicit call uses the retained refresh
  credential and learned nonce; its successful refresh narrows scope and the
  fresh grant check prevents another resource attempt. All seven actual POSTs
  have distinct proof IDs. Discovery and logical-HTTPS-to-loopback routing alone
  are synthetic. This extends, rather than repeats, the accepted six engine tests.
- **Native authority/projection:** fresh DID/client/generation state is checked
  under the stable OS lock through credential work. Short projection/removal stays
  in that boundary without HTTP inside general preferences. Exact clicked
  references cannot remove a completed replacement. Explicit old-client/orphan
  recovery is deliberate, with no background pruning or implicit migration.
- **Record path:** selected targets and actual scopes intersect; fresh authority
  checks grants again after refresh. Full serialized request bytes, including
  wrapper/escaping, are capped before network work. Result URI/account/collection,
  record-key syntax and canonical CIDv1/DAG-CBOR/SHA-256 profile are checked.
  CID parsing is not content-hash/commit/repository verification. Target progress
  persists before the next destination, with partial failure and acknowledgement
  failure tests; documented duplicate windows are honest, not exactly-once claims.
- **Model/interfaces:** the narrow authority, typed secret-free account view,
  shared grant parser, pure mapping adapter and leased client are coherent reuse
  boundaries. The actual singleton uses the actual tested login controller.
  F1/F2 concern that controller's command/event ownership, not a need for another
  OAuth backend, credential cache, DNS framework or broad architecture rewrite.

## Checks, provenance and contribution fitness

New focused command, **four tests pass**:

```sh
bash gradlew :composeApp:jvmTest \
  --tests 'com.arn.scrobble.api.atproto.AtProtoFinalLoginReviewProbeTest' \
  --tests 'com.arn.scrobble.api.atproto.AtProtoFinalEngineCompositionProbeTest' \
  --console=plain --no-configuration-cache
```

Three `observed…` tests deliberately assert undesirable controller behavior;
they are **not passing UI conformance claims** and must not be adopted unchanged.
The fourth is a positive composition property. Exact XML/log receipts are in
ignored `.test-agent/final-review/` and normal Gradle result directories.

A3's **96 module / 14 AT Protocol app tests, full app 20/21 with only the known
YouTube baseline failure, and Android APK assembly** remain attributed reports on
source `8b70e871`. They were read through the native receipt/tests and not rerun.
No new full-suite, APK, physical device, emulator or real-account check occurred.

One public-dependency Kotlin path is preserved: native `:atproto`, Ktor/OkHttp,
coroutines/serialization and JCA; no Maven Local, unpublished fork or dual backend
selector appears in the inspected build. Paired official/developer identity
validation, generated constants and manifest placeholder wiring remain. Source
native/navigation filters were inspected; A3's merged-manifest/configuration
receipts are trusted reports, not a new merged-manifest build here.

Android's dedicated `noBackupFilesDir`, Keystore-owned AES-GCM key, fail-closed
missing-key behavior and explicit atomic publication match the stated design.
Desktop remains private-file plaintext, not a keychain. Existing API 24/25/current
Android/provider/fsync/multiprocess, full cold/warm/restored interactive UI,
Windows/macOS/Graal and live AS/PDS/indexing limits remain accurately separate.
No hypothetical threat model is added as a new release gate.

For upstream preparation, keep feature code/tests and the concise contributor
guide together; keep this review/research corpus separate without squashing away
its evidence. The guide's code-only sources remain carryable. Its already-known
R3/check-count wording should be refreshed by A3. Metadata/Pages deployment remains
an independent authorized operator gate; none of this review authorizes pushing,
PR creation, deployment, real credentials or live repository writes.
