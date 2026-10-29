---
type: Implementation checkpoint
title: Native OAuth lifecycle on the durable account authority
description: Executable native callback, PAR, token, nonce and refresh lifecycle; leased dedicated Pano transport; tested properties and the next integration boundary.
resource: /design/atproto-posting/oauth0.gpt61s.md
status: draft
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-app-oauth
sources:
  - resource: "opencode://ses_e7b1c1da4ffeyw8XS4l257gfAC"
    title: Fresh worker A2 implemented the locked-request seam, native OAuth lifecycle and leased transport, then exercised synthetic wire and persistence failures
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent commissioned the clean local native path, isolated primitive audit and consequential lifecycle checkpoint
  - resource: /design/atproto-posting/storage0.gpt61s.md
    title: Predecessor native vault implementation and actual platform qualification limits
  - resource: /design/atproto-posting/remake0.gpt61s.md
    title: Selected narrow local architecture and current public-library qualification
  - resource: "file:///home/rektide/src/pano-scrobbler-at/design/atproto-posting/remake0.gpt6a.md"
    title: Parent commission and native Pano integration obligations
  - resource: "file:///home/rektide/src/pano-scrobbler-at/atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoOAuthClient.kt"
    title: Preserved old flow and primitive source, not the new lifecycle owner
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStore.kt
    title: Pre-discovery reservations and fresh locked non-rotating operations with an in-lock rotation transition
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt
    title: New typed account lifecycle and narrow authenticated request operation
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/NativeCallback.kt
    title: Exact native destination and unambiguous query validation shared by future Android and desktop entrypoints
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/http/AtProtoHttpClientPool.kt
    title: Whole-operation leases and deferred engine retirement
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/Requesters.kt
    title: Shared Pano base policy with a separate credential-traffic engine and proxy invalidation
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/oauth/NativeOAuthAuthorityTest.kt
    title: Twenty synthetic native lifecycle, wire, rotation, persistence and failure tests
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoTransportTest.kt
    title: Real OkHttp credential-bearing redirect rejection on a loopback fixture
  - resource: "file:///home/rektide/archive/bluesky-social/atproto/packages/oauth/oauth-client/src/session-getter.ts"
    title: Reference single-use refresh, cancellation boundary and terminal invalid_grant handling
  - resource: https://atproto.com/specs/oauth
    title: Current native public-client, identity, callback, grant, token and mandatory DPoP nonce requirements
---

# Native OAuth lifecycle checkpoint

**Implemented and tested internally; no login UI or real account activation.**
This continues the [storage foundation](storage0.gpt61s.md), not the old
free-standing OAuth/session-store composition. The selected dependency account
is unchanged: existing public Ktor, serialization, coroutines and JCA, with no
Maven Local, unpublished AT Protocol artifact or alternative backend selector.

Workspace: `/home/rektide/src/pano-atproto-remake`; bookmark
`atproto-clean-20261028`. Parent: `ses_e7ba46f7fffeze8ND55fl0uJF8`.
Current worker: `ses_e7b1c1da4ffeyw8XS4l257gfAC`.

## Coherent source stages

1. `f3a0ac8a`: locked non-rotating requests and pre-discovery authorization
   reservations. Twenty storage tests pass, including the predecessor's real
   child-process and process-death cases.
2. `631854e0`: typed native OAuth, durable PAR nonce/PKCE state, generation-scoped
   refresh, secret-free account views, bounded authenticated record transport,
   dedicated Pano HTTP policy and leased engine retirement. Forty module tests,
   one real-engine redirect test and Android assembly pass at this source.
3. `04a2cda4`: bounded error-body classification retains an unconsumed refresh
   credential while still rejecting the response; successful malformed/oversized
   responses remain quarantined. Exact token-success status and native URI guard
   tightened. Final source checks below are on this stage.

The evidence/index commit is recorded on the ticket/bookmark. An empty working
child is expected, not an additional feature revision.

## Authority and failure boundary

[`AtProtoAccountAuthority`](/atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt)
is the only new protocol owner. Public account metadata contains a
client/DID/login-generation handle, display handle, PDS and actual granted scopes;
it contains no tokens, private key or verifier. Protocol credentials are typed
only inside the vault boundary. Authenticated work rereads them **under the same
OS lock** and retains that lock through completion. No detached account view is
used as credentials and no mutable token provider is cached.

- A durable reservation precedes discovery/PAR. Logout or a newer attempt makes
  slow earlier work unable to publish a browser launch.
- PAR uses a new DPoP key and PKCE pair. Its final nonce is persisted with pending
  state before returning the launch. Launch lifetime is bounded by PAR expiry
  and a ten-minute local maximum. A recreated authority can complete the flow.
- The callback checks the exact configured native URL, rejects fragments,
  duplicate decoded parameter names, conflicting code/error, malformed escaping,
  invalid UTF-8 and controls. Matching state is durably consumed before exchange;
  issuer is mandatory and must exactly match. Denial is single-use too.
- Token responses require HTTP 200, a DPoP token type, expected DID, explicit
  `atproto` grant and positive non-overflowing expiry. Tokens are opaque, not
  decoded JWTs. Completion rechecks the original handle/DID, PDS and AS binding.
- Refresh discovery runs before the rotating marker. Once rotation begins, its
  bounded request/result publication is non-cancellable under the native lock.
  Independent authorities cannot refresh the same stale token concurrently.
- `invalid_grant` removes the session. Completed classified HTTP errors retain
  credentials and learned nonces, persist that outcome, then throw the protocol
  error. A received 4xx/5xx response with an unusable nonce or oversized/broken
  body is still rejected, but does not masquerade as a successful rotation.
  Body-reader failures are rethrown with their original exception type/cause.
- Ambiguous transport/response loss, invalid successful token data, process death
  or failed final persistence leaves the existing durable quarantine marker.
  The old refresh token is not replayed. New authorization/logout remains possible.
- Ordinary PDS requests are **not** rotating updates. Network failure or caller
  cancellation cannot poison the session. Response nonce publication uses a
  non-rotating write inside the existing lock, with no nested acquisition.
- Logout durably removes pending/claims/session before optional best-effort
  revocation. An old claim or generation cannot restore a logged-out account.

This status classification trusts an authoritative server's received HTTP error
to represent failure, as OAuth clients normally must; it does not pretend to
solve an AS that rotates a credential and then incorrectly reports failure.

## Real transport seam

[`Requesters.kt`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/Requesters.kt)
now builds the dedicated AT Protocol client from the same base construction as
`baseKtorClient`: proxy/authentication, normal TLS, user agent and 40-second
timeout. It does not inherit `genericKtorClient`'s Last.fm validator or cache.
Both Ktor and OkHttp redirects are disabled, including SSL redirects; OkHttp
connection-failure retry is disabled for single-use credential traffic.

Whole-operation [leases](/atproto/src/main/kotlin/com/arn/scrobble/atproto/http/AtProtoHttpClientPool.kt)
span gaps between discovery and token calls. Proxy invalidation retires the old
engine and selects a fresh one for new work; the old engine closes only after
its last operation releases it. Merely retaining a raw supplier would not have
established this lifetime property.

Response bodies use streaming `prepareRequest().execute`, bounded reads and
strict UTF-8 decoding, not unbounded `bodyAsText()` followed by a size check.
No general preference transaction contains network work.

## Exact verification

On source `04a2cda4`:

```sh
bash gradlew :atproto:test :composeApp:jvmTest \
  --tests 'com.arn.scrobble.api.atproto.AtProtoTransportTest' \
  :androidApp:assembleDebug --console=plain
```

**Successful:** 41 module tests (20 storage, 20 native OAuth, one lease/retirement),
one targeted JVM app test, Android debug APK assembly. The app test makes actual
OkHttp loopback POSTs carrying synthetic code/token headers and proves that
301/302/303/307/308 responses are returned without following the target.
OAuth fixtures verify real ES256/P1363 proofs with JCA, PKCE, fresh proof IDs,
PAR-to-callback nonce persistence, separate AS/PDS nonce state, single-use state,
concurrent callbacks, token/grant mutation, stale generation, refresh/logout,
cancelled refresh, disk failure, response loss and bounded-body failure.

Full final build log is ignored scratch at
`.test-agent/atproto-oauth/checkpoint.log`; XML receipts are the normal Gradle
test-result directories. The known unrelated `MaiTest.parseYoutubeTitles`
baseline failure was not rerun or changed. This was a targeted app test, not a
claim that the entire app suite passes.

No emulator/device, API 24/25 runtime, Windows/macOS/Graal execution, real OAuth,
real token or live repository write was used. The predecessor's
[platform qualification limits](storage0.gpt61s.md) remain open.

## Deliberately unfinished before app activation

**Adopt Worker B's finalized primitive corrections before final qualification.**
The preserved original encoding/crypto/identity/discovery/DPoP-proof/PKCE/form/URL
source was provisionally adapted to make this lifecycle executable. No files or
uncommitted work in `pano-atproto-protocol` were read or changed. Those imports
are not a second completed primitive audit. The new DPoP HTTP adapter adds durable
role/origin nonce state, mandatory nonce checks and bounded reads; its resource
`WWW-Authenticate` recognition still uses the old simple shape and must receive
the independently hardened challenge parser. Reconcile interfaces deliberately,
not by replacing this native authority with the old stores/session client.

Next coherent slice after parent review/primitive receipt:

1. Integrate B's corrections, pure mapping and grant parser. The current secret-
   free view exposes actual scopes; the narrow record operation accepts only
   literal `repo:<one-of-two-collections>?action=create`, not all valid equivalent
   permission expressions. It has no target-selection UI or completed-play mapper.
2. Implement tested resource `invalid_token` recovery only at a proven rejection
   boundary. Current record requests surface it, with no blind replay. Add actual
   response/schema checks and target-by-target durable pending-play progress;
   retain the documented ambiguous-response duplicate window.
3. Connect the platform vault and leased client to one Pano-owned construction
   point, then generation-safe account publication/removal. `MainPrefs` remains
   display/account-pointer state, never another credential store. Ordinary writes
   must not hold the network lock; late callbacks must not republish stale accounts.
4. Reconstruct AccountType 10, one-account-per-type login, replacement-login
   completion, TV exclusion, Android/desktop callback routing, actual granted
   destination controls and hidden unsupported actions. Follow the
   [parent's source-derived obligations](file:///home/rektide/src/pano-scrobbler-at/design/atproto-posting/remake0.gpt6a.md#pano-integration-obligations-to-carry-forward).
5. Align official metadata defaults and paired developer overrides at that native
   integration prefix, inspect the merged manifest, then qualify the resulting
   composition. Metadata deployment remains an operator gate, not permission to
   deploy or use real accounts here.

The parent can assess this executable lifecycle before a broader UI reconstruction.
`pano-atproto-remake-app-oauth` remains in progress for that deliberate integration,
not closed as a finished end-user feature.
