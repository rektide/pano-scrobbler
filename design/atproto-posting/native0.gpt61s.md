---
type: Implementation handoff
title: Activated native account and completed-play integration
description: Native provider and login UI prefix, stable transport adoption, publication and target-progress proofs, exact checks and remaining primitive qualification.
resource: /design/atproto-posting/native0.gpt61s.md
status: partial-implementation-handoff
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-app-integration
sources:
  - resource: "opencode://ses_e7b1c1da4ffeyw8XS4l257gfAC"
    title: Continued the parent-accepted lifecycle into native account/provider/UI integration and prepared context-bounded turnover
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent accepted the internal lifecycle and commissioned working integration plus exact parser adoption
  - resource: /design/atproto-posting/oauth0.gpt61s.md
    title: Preceding typed lifecycle, source inventory and transport evidence
  - resource: "file:///home/rektide/src/pano-atproto-protocol-notes/design/atproto-posting/protocol-handoff0.gpt61s.md"
    title: Immutable transport receipt f8d7b93e and remaining independent primitive obligations
  - resource: "file:///home/rektide/src/pano-atproto-protocol-notes/design/atproto-posting/protocol-migration-probes0.gpt61s.kt"
    title: Old-behavior observation probes, including optional expiry and malformed imported credentials
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccounts.kt
    title: Short generation-checked account projection and removal
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccountsTest.kt
    title: Actual vault with synthetic wire and fault-injected preference projection/provider tests
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/oauth/NativeOAuthAuthorityTest.kt
    title: Exact resource rejection recovery, protocol boundaries and durable partial-target tests
  - resource: /composeApp/src/androidMain/kotlin/com/arn/scrobble/navigation/NavFromOutsideEffect.android.kt
    title: Initial and new Android intents feed shared callback routing
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/onboarding/AtProtoLoginScreen.kt
    title: Explicit completion result, external browser, paste fallback and granted selections
  - resource: /doc/atproto.md
    title: Concise preview guide, paired identity setup, limitations and two independent PR outlines
---

# Activated native integration and turnover

**The native account/provider/login path is now implemented, not another design-only
checkpoint. It remains a development preview pending the independent primitive
receipt and native runtime qualification.** No real accounts, pushes, PR creation
or deployment were used.

Workspace `/home/rektide/src/pano-atproto-remake`; commissioned bookmark
`atproto-clean-20261028` (name retained deliberately). Parent
`ses_e7ba46f7fffeze8ND55fl0uJF8`; A2 `ses_e7b1c1da4ffeyw8XS4l257gfAC`.

## New coherent stages

- `1bb10389`: exact challenge parser adopted from immutable
  `f8d7b93edab8553f667b7d6ddf08c18b5f2f33d0` using `jj file show` (the backing
  Git checkout did not expose that object). NQCHAR validation, redirect/retry
  guard and real streaming-before-EOF proof retained alongside **our** durable
  nonce and `HttpResponseBodyException` classification. Exact DPoP `invalid_token`
  forces one refresh/retry even before proactive expiry, with grant narrowing
  checked before resend. Missing expiry is supported as explicitly unknown.
- `34d5e286`: completed-play mapping wrapper, generation-scoped target journal,
  durable acknowledgement before the next target, and short publication/removal
  seams. Provisional mapping/literal grant subset is explicit, not a second
  independent audit of B2's assigned work.
- `7541a792`: native AccountType 10, one-account projection, provider, browser login,
  Android callback and desktop paste, destination controls, TV exclusion,
  scrobbles-only tabs and unsupported-action hiding. Production identity defaults,
  paired developer overrides and merged-manifest inspection.
- `c392ef4f`: one atomic completion consumer across overlapping navigation screens,
  tied to the newly completed generation rather than any-account presence.

The final evidence head and expected empty child are on the tickets/bookmark.

## What composes now

`PanoAtProto` constructs the platform vault plus leased dedicated `Requesters`
transport once. Its lifetime convenience is not the lock: independent instances
and Android processes use the shared native file lock and fresh credentials.

`PanoAtProtoAccounts.publish` validates the handle under that lock, then performs
a **short** `MainPrefs.updateData`. There is no HTTP in a preference transform.
Logout durably removes authorization and orders pointer removal under the same
short boundary, then attempts optional remote revocation outside it. Failure of
pointer removal leaves stale display data but no usable credential; explicit
logout can retry. No background reconciliation/prune system was added.

References carry DID, client and generation, not a second credential payload.
Same-DID reauthorization preserves selected targets and the enabled flag. New
generations invalidate retained providers and late publication. A publication
failure offers retry only for the account that **actually completed** that flow,
not whichever older account happens to exist. Completion is consumed once.

`AtProtoScrobblable` reads the current reference/choices and checks its generation.
It intersects selected and actual grants, maps a completed play independently for
each target, then uses the same authority. The authority persists each successful
target before the next; recreation and partial retry skip it. A failed progress
write stops further destinations without quarantining a non-rotating token.
Successful remote writes before failed/lost local acknowledgement still have an
honest duplicate window. Completion removes that play's journal entry; a crash
before Pano clears the pending play can duplicate the whole completed submission.

The UI is reachable through onboarding and service preferences, hidden on TV,
and supports replacement without observing global account presence as success.
Android's separate **scheme-only** callback filter is intentional: the native URI
has no authority, so Android ignores a path predicate without a host. Shared
`NativeCallback` remains the exact URL/query authority. Existing
`pano-scrobbler://screen` handling is unchanged.

## Verified activated prefix

At `7541a792`, full composition:

```sh
bash gradlew :atproto:test :composeApp:jvmTest :androidApp:assembleDebug \
  --continue --console=plain
```

- **49/49 module tests pass** (20 storage, 26 lifecycle/record/progress, one lease,
  one streaming guard, one mapping-units/identity fixture).
- **Five new app tests pass** (four projection/provider, one real OkHttp).
- Full app suite **11/12 passes**. Only the independently documented baseline
  `MaiTest.parseYoutubeTitles` trailing-space assertion fails. The command exits
  nonzero for that test; `--continue` still builds the Android debug APK successfully.
- Actual-vault host tests cover same/new DID replacement, failed publication and
  explicit retry, logout ordering, failed pointer removal, fresh target selection,
  and a cached provider unable to post or republish after replacement/logout.

After the small completion-consumer correction at `c392ef4f`, the targeted five app
tests and Android assembly pass again. No module source changed after its 49-test
run. Logs: ignored `.test-agent/atproto-oauth/final-native-check.log` and
`final-targeted-check.log`.

Merged manifest inspected at
`androidApp/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`:
separate `io.github.kawaiidango` filter; original `pano-scrobbler`/`screen` filter
retained; no placeholder left. Generated Android constants match the official
client ID and exact single-slash redirect. A mismatched rektide client/official
redirect fails Gradle configuration; the correctly paired rektide override passes
`gradlew help`. This latter check is configuration evidence, **not** a developer-
identity APK/runtime run.

No emulator/device/KVM, Android Keystore/API 24/25 runtime, Graal/native-image,
Windows/macOS execution, real OAuth or service indexing was qualified. Preview
Compose UI compiled on JVM/Android; no interactive UI automation is claimed.

## Fresh worker's remaining coherent slice

1. **Receive B2's exact committed receipt from the parent/ticket.** Do not read its
   uncommitted workspace or repeat its audit. Adopt key correspondence/canonical
   encoding, PKCE alphabet, identity/discovery and permission/mapping corrections.
   Keep the new native lifecycle, journal, leases and failure classification.
2. Replace `CompletedPlay.record`'s provisional preserved-shape mapping with the
   audited pure mapper or a narrow adapter. Replace `AtProtoTarget.grantedBy`'s
   deliberately conservative literal subset and the authority's same literal
   check together, so valid equivalent grants work without accidental escalation.
3. Qualify actual login/UI state and native callback transitions with synthetic
   host/instrumented fixtures where possible. In particular the Android route is
   broad by scheme but must always converge on the exact URI guard; replacement
   and restored-process behavior deserve runtime proof, not inferred green builds.
4. Reassess selected/native account removal on build-identity switches and explicit
   orphaned-reference recovery. There is no automatic pruning or hidden migration.
   Keep publication retry bound to the exact saved generation; never restore an
   arbitrary old account simply because `currentAccount` is non-null.
5. Run the final adopted composition's relevant checks/build and refresh the guide
   and tickets. Existing default metadata URL still needs the separate authorized
   operator deployment; no real-account testing is commissioned here.

Tickets `pano-atproto-remake-app-oauth`, `pano-atproto-remake-app-integration` and
`pano-atproto-remake-app-posting` remain open for these qualification obligations.
The native-runtime gate remains separately open. Turn over around this coherent
activated prefix rather than spending the remaining context on another audit.
