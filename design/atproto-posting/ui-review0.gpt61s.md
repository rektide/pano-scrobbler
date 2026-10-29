---
type: Independent delta assessment
title: Final UI corrections resolve the bounded integration findings
description: Source-bound F1/F2/F3 and cancellation follow-up on the exact A4 code and guide, with attributed checks and unchanged runtime/deployment limits.
resource: /design/atproto-posting/ui-review0.gpt61s.md
status: no-remaining-substantiated-local-blocker
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-ui-review
  code_commit: 18da8b912068a314493fef278c7b4c8290c800dd
  guide_commit: a17414ed9ef66726809c33256502361ddccde1da
  evidence_commit: fd9992d6b3985a4c7216c0d3e66c9a376102b348
sources:
  - resource: "opencode://ses_e7af14a6cffeDevLvL0joxsto6"
    title: B3 independently assesses only the immutable A4 correction delta and active UI/native-correlation seams, resolves the prior findings and preserves qualification limits
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent commissions the bounded exact-delta follow-up and final feature-tree equality proof
  - resource: /design/atproto-posting/ui-corrections0.gpt61s.md
    title: A4 exact correction receipt and attributed targeted checks
  - resource: /design/atproto-posting/native2.gpt61s.md
    title: Actual f2c49860 predecessor already contains automatic browserPending consumption and integrated qualification
  - resource: "file:///home/rektide/src/pano-atproto-final-review/design/atproto-posting/final-review0.gpt61s.md"
    title: Preserved independent findings on frozen97db5e70, not an assertion that this later predecessor was identical
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoLogin.kt
    title: Bounded callback serialization, exact Work cleanup, native-correlated dispatch and explicit invalidation
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoAccountActions.kt
    title: Actual removal and enabled-state error/cancellation boundaries
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProto.kt
    title: Actual controller, browser command and production snackbar wiring
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccounts.kt
    title: Generation-fenced setting write and exact clicked-reference removal seams
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/onboarding/AtProtoLoginScreen.kt
    title: Both actual automatic and manual browser paths use guarded commands
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/pref/PrefsComponents.kt
    title: Both actual preference click paths call the tested helpers
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt
    title: Read-only native launch correlation added without changing callback claim authority
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/OAuthModels.kt
    title: Internally constructed launch holds opaque reservation correlation rather than OAuth state or credentials
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStore.kt
    title: Retained attempt identity, duplicate-publication rejection and read-only locked pending check
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoLoginUiTest.kt
    title: Inspected fourteen actual controller/vault/action regressions including both former callback traces, held-lock races and cancellation cleanup
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/oauth/NativeOAuthAuthorityTest.kt
    title: Inspected new read-only client/attempt/expiry correlation regression
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStoreTest.kt
    title: Inspected amended reservation and duplicate-publish assertion
  - resource: /doc/atproto.md
    title: Separately pinned feature-only guide delta describes corrected behavior and attributes new versus predecessor checks
---

# Bounded UI correction assessment

**F1, F2 and F3 are resolved in the reviewed local contribution scope; current-work
cancellation/timeout cleanup is also addressed. No remaining substantiated blocker
or new concrete blocker introduced by this delta was found.** This is an exact
source assessment, not universal production, interactive-device or live OAuth
certification.

## Exact scope

- Code/tests: `18da8b912068a314493fef278c7b4c8290c800dd`.
- Guide: `a17414ed9ef66726809c33256502361ddccde1da`.
- Evidence/workspace base: `fd9992d6b3985a4c7216c0d3e66c9a376102b348`.
- Review workspace: `/home/rektide/src/pano-atproto-ui-review`;
  `atproto-ui-review-20261029` freezes the evidence base.

The actual pre-correction feature source was `f2c49860` (evidence `ada72c5d`),
**not** my old `97db5e70` candidate. The predecessor already supplied atomic
`browserPending` consumption and a manual reopen button. The
[earlier report](file:///home/rektide/src/pano-atproto-final-review/design/atproto-posting/final-review0.gpt61s.md)
remains valid for its own frozen source and is preserved unchanged. This review
examined only A4's delta, its active seams/tests and guide changes; it did not
reopen the accepted protocol/engine architecture.

## Dispositions

| Finding | Verdict and source basis |
|---|---|
| **F1 callback ownership/delivery** | **Resolved.** `AtProtoLogin` serializes begin, callback and saved-generation publication work. A callback waits behind PAR rather than stealing its owner. One active action plus four distinct waiting callbacks is bounded; identical active/queued/last-completed delivery is deduplicated, and overflow explicitly requests redelivery. Rejected unrelated delivery preserves a current launch/unconsumed completion/publication retry. Both original traces are positive actual-controller/native-vault regressions. No duplicate UI protocol validator or arbitrary existing-account completion was added. |
| **F2 browser command lifetime** | **Resolved.** The earlier one-shot re-entry fix is retained, not credited as new here. Both actual automatic and manual screen paths now check opaque client/attempt/expiry correlation through the native authority, then recheck the retained launch, busy state, automatic claim and time after suspension. Local logout supersedes jobs/queued input/commands and gates admission; exact removal checks surviving native pending state rather than discarding unrelated client/generation work. Held-real-lock tests cover suspended reads versus logout/new begin. |
| **F3 preference action failures** | **Resolved.** Both actual preference click coroutines call `AtProtoAccountActions`; the production sink emits existing snackbar visuals. Expected removal/setting failures become generic actionable messages. Cancellation is rethrown, not converted to success or a snackbar. Exact-handle setting writes cannot update a replacement, and post-removal preference failure does not restore authorization. Inspected tests call these actual helpers, including retry, stale/missing references, write failures and cancellation. |

**Cancellation/timeout addition:** cleanup uses exact `Work` identity and a
completion handler registered on the lazy job. It runs even if cancellation
prevents the body from starting, and cannot clear newer work's ownership. The
current operation releases busy state; saved-generation publication remains
explicitly retryable. Callback/PAR cancellation permits a new explicit login, not
code replay. A4's tests exercise cancelled exchange/publication, pre-start
cancellation, logout during paused PAR/exchange and the authority's actual PAR
deadline.

## Authority and linearization

The new browser correlation is **read-only**, not a claim or credential cache.
`AuthorizationLaunch` has an internal constructor, opaque reservation identity and
no public data-class copy method. Native pending checks use the stable OS lock and
matching client/attempt/unexpired pending entry. Retaining the existing attempt
alongside pending data does not reopen publication: `publishAuthorization` also
rejects an already-present pending entry, and the amended test checks that exact
duplicate. Callback consumption still clears pending/attempt and uses the existing
single-use claim/generation authority. No migration or expiry pruning was added.

A fresh native read and the subsequent controller claim are separate boundaries.
The post-await state/time checks correctly fence local supersession. A later
cross-process logout cannot recall an already-dispatched OS browser; this is not
required or claimed. Native callback/claim completion remains the final fence
against installing invalidated authorization. The correction preserves those
ownership boundaries rather than adding another OAuth backend or validator.

## Checks and limits

**No new executable probe, Gradle test, compile, APK or 40-second timeout was run
for this follow-up:** source/test inspection exposed no concrete unresolved
concern justifying repetition. I inspected the exact code/guide diffs, actual
screen/click/singleton wiring, new native correlation assertions and the fourteen
positive UI/action regressions. Document checking is recorded with this review.

A4's reported **34/34 affected module tests, 34/34 AT Protocol app tests and
JVM/Android compilation/debug APK** are trusted and attributed to the correction
receipt. Its real PAR timeout records **40.173 seconds**. The predecessor's full
96/96 module and 26/27 full-app results, with only the known YouTube baseline
failure, remain predecessor evidence—not a newly rerun corrected full suite.

The guide accurately distinguishes these receipts and retains feature-only source
links. Parent still owns the final contribution tree-equality proof for the pinned
code and guide; this report does not silently certify a different source tree.
Runtime/device/API 24/25/Keystore/multiprocess, Windows/macOS/native-image, interactive
browser/UI, live OAuth/indexing and authorized metadata deployment gates remain
separate. No implementation/contribution workspace edits, secrets copied, extra
agents, real accounts, push, PR or deployment occurred.
