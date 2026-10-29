---
type: Implementation handoff
title: Portable adoption and native feature qualification
description: Pinned portable adoption, R1/R2 corrections, actual-vault login and reference recovery checks, exact contribution boundaries and the outstanding engine receipt.
resource: /design/atproto-posting/native1.gpt61s.md
status: qualification-checkpoint
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-app-integration
sources:
  - resource: "opencode://ses_e7aedded3ffe8r5OwrYTHwM2nl"
    title: A3 integrates pinned validation, fixes R1/R2 and qualifies the native feature through actual storage and application seams
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent commissions clean native integration and separate B3 engine replay correction
  - resource: /design/atproto-posting/native0.gpt61s.md
    title: Predecessor activated native account/provider/UI checkpoint
  - resource: "file:///home/rektide/src/pano-atproto-protocol-notes/design/atproto-posting/protocol-handoff1.gpt61s.md"
    title: Immutable portable receipt and exact adoption map for 1a0c119e
  - resource: "file:///home/rektide/src/pano-atproto-review/design/atproto-posting/lifecycle-review0.gpt61s.md"
    title: Full frozen-head review, its three findings and positive safety probes
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt
    title: Retained native authority with shared grants, strict record result checks and classified refresh outcomes
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStore.kt
    title: Generation-bound explicit removal alongside intentionally client-wide logout
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoLogin.kt
    title: Actual process-local login coordinator used by the singleton and host tests
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccounts.kt
    title: Short generation-fenced publication and exact-reference removal
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccountsTest.kt
    title: Native vault plus synthetic wire and fault-injected general preference projection
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/oauth/NativeOAuthAuthorityTest.kt
    title: Inverted review negatives, positive race/publication probes and actual writer checks
  - resource: /doc/atproto.md
    title: Upstream-readable guide with code-only sources, operational limits and two independent PR outlines
---

# Portable adoption and native qualification

This continues the [activated native prefix](native0.gpt61s.md), not the old
standalone OAuth/poster composition. No backend or library research was repeated.
Workspace: `/home/rektide/src/pano-atproto-remake`; bookmark
`atproto-clean-20261028`; A3 session `ses_e7aedded3ffe8r5OwrYTHwM2nl`.

**R3 is still a prerequisite:** B3 owns the real OkHttp hidden-replay correction in
an isolated workspace. That active work was neither inspected nor duplicated.
Do not call this checkpoint release-ready without adopting its exact committed
receipt and checking the resulting composition. No push, PR, deployment or
real-account request was made here.

## Coherent stages and source map

- `f89fec57`: R1/R2 corrections and six additional authority regressions;
  **55 module tests pass**.
- `c25a32ce`: portable adoption from immutable
  `1a0c119e372af16298b004d81aebf21e657b3015`; **94 module tests pass**.
- `8b70e871`: native qualification adds reference-bound removal, the actual testable login
  coordinator, restored Android intent forwarding, CID profile parsing and
  cancellation attribution checks. The separate final evidence head is recorded
  on the tickets and in the parent receipt.

Only the commissioned portable source is carried:

| Pinned source | Native integration |
|---|---|
| `crypto/P256Jwk.kt`, `oauth/Pkce.kt` | Canonical imported components, curve/scalar/JCA pair correspondence and strict verifier alphabet. Native redaction retained. |
| `identity/Handle.kt`, `DidDocument.kt`, `IdentityResolver.kt` | Current claims, first-handle/PDS semantics, reserved TLDs and supported production DIDs. Mock handle fixtures changed from `.example` to `alice.example.com`, not a weakened production validator. |
| `http/DiscoveryJson.kt`, `JsonFetch.kt`, `HttpUrl.kt` | Bounded duplicate/depth preflight, one object and strict origins/URLs. JSON validation is adapted to the native bounded reader, not a second transport. |
| `oauth/Discovery.kt` | Portable URL/capability checks adapted to existing native typed metadata. No same-origin endpoint restriction or new DNS/SSRF framework. |
| `posting/RepoPermission.kt` and only old `AtProtoTarget` | Shared granular parser for screen/provider selection and the authority's fresh-token write guard. Old scope conventions are not fallback grants. |
| `posting/Play.kt`, `RecordText.kt`, `Records.kt` | Pure validated mappings behind the existing `CompletedPlay` identity adapter. Native progress journal and leases remain authoritative. Teal seconds; current Rocksky milliseconds. |

Portable regressions were carried and consolidated for the native module,
using its existing `runBlocking` test dependency rather than adding a test stack.
The predecessor's 86-test portable result is evidence for its own source, not
the adopted composition. The native run has its own count.

## Frozen review dispositions

The review assessed `def70d8a`, not this activated future tree. Its six probes
were focused observations/safety checks, not six universal conformance passes.

**R1 corrected.** `NativeCallback` catches input-bearing `URISyntaxException` at
that exact boundary and throws a generic validation failure with no cause or
suppressed exception. The regression inspects the entire stack for code and
state sentinels, asserts zero exchange and then completes the original valid
pending authorization.

**R2 corrected.** Received 400–599 errors consistently retain proven-unconsumed
refresh state and learned nonces, durably publish the outcome and then throw.
The matrix covers 400/403/503, valid/missing nonce and malformed/oversized bodies.
Body-reader cancellation coverage retains its original cancellation attribution.
The exact real **40000 ms** 503-header/nonterminating-body probe is inverted:
the same timeout is surfaced, the session remains usable and a later explicit
operation reads the durable nonce and old unconsumed refresh token. There is no
automatic retry of these failures.

Status is reset before each protocol-owned nonce retry: an earlier HTTP error
cannot classify a later request lost before headers. Unknown-status loss and
invalid successful data still quarantine. A post-atomic-replacement write error
may expose either the marker or valid new credentials, never reusable old ones;
the positive review probe now exercises this through the actual record boundary.
The newer-authorization-versus-older-exchange race is also preserved.

**R3 outstanding in this lane.** `retryOnConnectionFailure(false)` alone does not
prevent OkHttp 5.5.0's 503/`Retry-After: 0` follow-up. B3's exact receipt must fix
that real engine seam without response rewriting or disabling explicit fresh-proof
nonce retries. The final parent integration assessment must include it.

## Native feature seams qualified

- General preferences remain account/display references, never credential copies.
  `publishAccount` holds the native lock through only the short projection.
  Synthetic wire checks assert no request occurs in a general preference transform.
- Existing account presence cannot complete replacement login. Recreated
  authorities/controllers complete the same saved pending authorization through
  the exact single-slash URI validator; malformed double-slash input consumes
  nothing. Android now forwards native initial intents even after activity restore;
  the durable shared validator remains the destination/state authority.
- The singleton uses the testable `AtProtoLogin`, not a mirrored test algorithm.
  Duplicate concurrent callbacks cannot overwrite completion; overlapping screens
  consume the new generation once. Publication failure/retry remains tied to that
  exact completed generation, never an arbitrary replacement account.
- Preferences removal now passes the clicked reference. Native `removeSession`
  deletes only its matching DID/client/generation. Stale same/new-DID screens
  cannot remove a replacement or its preferences. Parameterless native `logout`
  remains deliberately client-wide; the two contracts are not conflated.
- After build-identity switch, an old reference cannot authenticate through the
  new client. Explicit old-reference removal can remove that old namespace while
  leaving a fresh new-client authorization intact. No migration or pruning runs.
- Failed display removal leaves no authorization. Explicit retry removes its
  stale reference. Removing an already orphaned reference does not cancel a
  newer pending authorization. Same-account choices/disabled state are retained;
  new DID defaults remain new-account defaults.
- Cached providers reread selected targets and obtain fresh generation authority;
  replacement/logout prevents sends or publication. Per-target acknowledgement,
  partial retry and documented duplicate windows remain unchanged.
- The full serialized outgoing request includes wrapper/escaping before the
  local 64 KiB check. Returned record URI/account/collection and canonical
  CIDv1/DAG-CBOR/SHA-256 profile are checked. No content hash, signed commit or
  repository cryptographic verification is implied.

## Checks and remaining boundaries

At source `8b70e871`, the final adopted native prefix passes **96 module tests,
14 AT Protocol app tests and Android debug assembly**. The full app suite is
**20/21 passing**, with only the known upstream `MaiTest.parseYoutubeTitles`
trailing-space assertion failure. No attempt was made to fix or mask it.

```sh
bash gradlew :atproto:test :composeApp:jvmTest :androidApp:assembleDebug \
  --continue --console=plain
```

This exits nonzero for that baseline failure, while `--continue` completes the
Android build. Earlier targeted native checking passed 96 module/13 app tests
and APK assembly; the full run also passes the additional same/new-DID
disabled-choice preservation fixture. Ignored logs are
`.test-agent/atproto-oauth/a3-review-corrections.log`, `a3-portable-check.log`,
`a3-native-seams-final.log` and `a3-composition-check.log`.

The merged default manifest retains separate `io.github.kawaiidango` native and
`pano-scrobbler`/`screen` navigation filters. A hostless single-slash native URI
requires the scheme-only Android filter and shared exact URL validation, not an
ignored hostless path predicate. Default and paired-override Gradle identity
contracts are inspected separately from native runtime claims. Both Android/JVM
generated constants match the default client and exact callback. Fresh
`gradlew help` checks accept the paired rektide client/redirect, reject the mixed
rektide/official pair, and accept the official pair again; this is configuration
evidence, not a developer-identity APK/runtime run. Ignored receipts are
`a3-identity-paired-developer.log`, `a3-identity-mismatched-developer.log` and
`a3-identity-restore-default.log` under the same scratch directory.

External gates remain separate: authorized metadata/Pages deployment, real OAuth
and service indexing, interactive UI/device behavior, API 24/25/current Android
Keystore/provider/fsync and multiprocess fixtures, desktop Windows/macOS and
native-image execution. No device/emulator/KVM was supplied. The native-runtime
ticket is not closed by these host tests or APK assembly.

Keep independent research/evidence commits separate from the feature-only PR
series. The [contributor guide](/doc/atproto.md) cites only feature code/tests and
native session provenance, so excluding this research corpus does not break it.

The current context readout was **224,002** provider-reported request tokens
(lagging measurement, not cumulative traffic). This is a useful checked boundary,
not a reason to consume the turnover budget. A3 can resume for the exact B3
receipt and resulting final composition check; no extra agent was spawned.
