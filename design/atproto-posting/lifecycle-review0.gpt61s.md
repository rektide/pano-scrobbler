---
type: Independent implementation review
title: Frozen native OAuth lifecycle review before activation
description: Source-bound assessment of authority, persistence, cancellation and engine behavior, with three actionable findings and six focused synthetic probes.
resource: /design/atproto-posting/lifecycle-review0.gpt61s.md
status: review-requires-corrections
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-lifecycle-review
  reviewed_commit: def70d8a42e58c60de520ae154f03c6c7bb97fef
  reviewed_source: 04a2cda4
sources:
  - resource: "opencode://ses_e7b181b8effef7JO12Vhel0XwH"
    title: B2 independently reviews frozen native lifecycle source and demonstrates callback leakage, inconsistent error quarantine and hidden OkHttp credential replay with focused synthetic probes
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent commissions this independent frozen-head review and defines accepted authority and platform boundaries
  - resource: /design/atproto-posting/oauth0.gpt61s.md
    title: Native lifecycle owner's checkpoint, accepted reported checks and deliberately unfinished integration
  - resource: /design/atproto-posting/storage0.gpt61s.md
    title: Durable storage authority and platform qualification account
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStore.kt
    title: Native lock, reservations, single-use claims and rotating/nonrotating publication boundaries
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt
    title: Typed OAuth ownership, refresh classification, callback exchange and local-first logout
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/NativeCallback.kt
    title: Exact native callback parsing and input-bearing URI exception boundary
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/OAuthModels.kt
    title: Public account view, typed private credentials and redacted model strings
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/http/BufferedHttp.kt
    title: Bounded response reading and loss of received status on cancellation
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/http/AtProtoHttpClientPool.kt
    title: Whole-operation lease, retirement and close behavior
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoTransport.kt
    title: Actual credential engine policy, distinct from generic transport
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/Requesters.kt
    title: Dedicated engine construction, proxy invalidation and base TLS/timeout policy
  - resource: /composeApp/src/androidMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoVault.android.kt
    title: Android no-backup directory, Keystore key and atomic publication factory
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/AesGcmVaultCipher.kt
    title: Versioned authenticated encryption envelope and fresh encryption nonce
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/JvmVaultPersistence.kt
    title: Private desktop file permissions and post-rename durability acknowledgement boundary
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/oauth/LifecycleReviewProbeTest.kt
    title: Five new focused authority/error/publication probes on the frozen source, including an actual 40000ms deadline
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoEngineReviewProbeTest.kt
    title: New real OkHttp loopback proof of identical POST/body/DPoP replay on HTTP 503 Retry-After zero
  - resource: https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-jvm/5.5.0/okhttp-jvm-5.5.0-sources.jar
    title: Exact published engine source confirms HTTP 503 follow-up bypasses the connection retry flag and honors a one-shot body gate
---

# Frozen native OAuth lifecycle review

## Verdict and evidence boundary

**The lock/generation architecture is coherent, but correct the hidden engine
replay before activation.** Also sanitize malformed callback exceptions and make
received-error refresh classification consistent. No new stale-account
resurrection or replay of an already-consumed stored refresh credential was found
in the authority boundary reviewed here.

Target is exactly `def70d8a42e58c60de520ae154f03c6c7bb97fef`, source `04a2cda4`, in
`/home/rektide/src/pano-atproto-review`. A2's active workspace and unfinished future
integration were neither read nor modified. Review-only test commits are
`f567740a` and `2b07e142`; the ticket/bookmark records the final report head.

The owner's reported **41 module tests, one real-engine redirect test and Android
debug build** are trusted reports, not rerun review evidence. I read their source
and added only six tests answering concrete concerns. Provisional key, PKCE,
grant, identity and challenge gaps already covered by B2's accepted `1a0c119e`
receipt, and the known optional-`expires_in` policy item, are not new findings.
No backend reconsideration or same-origin endpoint restriction is proposed.

## R3 — hidden HTTP 503 credential replay

**P1; high confidence; fix before activation.**

[`atProtoCredentialPolicy`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoTransport.kt)
lines 7–16 disables redirects and `retryOnConnectionFailure`, but that is not a
general one-attempt guarantee. The new real OkHttp loopback probe sends one POST
carrying a synthetic refresh body and DPoP header. Its first response is `503` with
`Retry-After: 0`; the client returns the second response, `200`. The server records
**two identical credential bodies and two identical DPoP proofs**.

This is the actual app policy seam, not a mock transport inference. Gradle
`dependencyInsight` confirms OkHttp **5.5.0** on this test runtime. Its published
`RetryAndFollowUpInterceptor` lines 258–270 confirms that the 503
branch does not consult the connection-retry flag. The generic follow-up gate at
104–109 does check `RequestBody.isOneShot()`.

The first response never reaches protocol-owned nonce/status handling, and the
second wire request cannot receive a fresh proof from that owner. This violates
the selected no-hidden-credential-replay boundary. It is **not** a demonstration
of account compromise or a claim that a conforming AS consumed a token while
reporting failure.

Smallest plausible correction: make credential-bearing bodies non-replayable at
the real OkHttp request-body boundary, or use an equivalently verified
one-attempt mechanism. Do not rewrite server response codes. Invert the probe to
require one network request and delivery of the initial 503; retain explicit
protocol nonce retries with a fresh proof per actual request. Existing redirect
coverage does not establish this separate property.

## R1 — malformed callback exceptions expose the authorization code

**P2; high confidence; sanitize before wiring errors to UI/logging.**

[`NativeCallback.parse`](/atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/NativeCallback.kt)
line 17 directly calls `URI(url)`. An otherwise valid synthetic callback followed
by `&unknown=|` passes the initial whitespace/control check, then throws
`URISyntaxException`. `stackTraceToString()` contains the complete callback,
including the synthetic authorization-code sentinel and state.

The probe confirms **zero token exchanges**, and the original valid pending
authorization remains completable. Thus destination/single-use validation is not
bypassed; the finding is secret exposure through an error boundary despite safe
model `toString()` implementations elsewhere.

Catch URI syntax failure at this specific untrusted-input boundary and expose a
generic callback-validation failure. Do not retain the raw input-bearing
exception as its cause or suppressed exception. Invert the probe to inspect the
entire throwable chain/stack for the sentinel while still asserting no exchange
and no unintended state consumption. This is a real recovery/security boundary,
not a recommendation to broadly swallow exceptions.

## R2 — received HTTP failure can become a quarantined “ambiguous success”

**P2; high confidence under the accepted received-error nonconsumption policy.**

There are two concrete inconsistent paths:

1. [`refreshIfNeeded`](/atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt)
   lines 179–186 quarantines a received `400` or `403` HTML error with a valid
   nonce because `oauthError().error` is null. The identical status/body with no
   nonce is retained through the `DpopNonceException` catch. Both calls correctly
   reject the response; only the valid-nonce case strands the account.
2. [`boundedRequest`](/atproto/src/main/kotlin/com/arn/scrobble/atproto/http/BufferedHttp.kt)
   lines 45–46 rethrows cancellation without the status already observed. A new
   probe receives **503 headers and a valid nonce**, then a nonterminating body.
   The authority's actual **40000ms** deadline fires: the original
   `TimeoutCancellationException` propagates, but the durable rotating marker is
   left unusable. This was a real deadline, not an injected exception or claimed
   Android/socket test.

The accepted policy already treats received HTTP errors as non-success even when
their nonce/body is unusable; it does not solve a server that rotates and then
lies about failure. Apply that policy consistently across small malformed bodies
and body-read deadlines. Preserve known status across cancellation, durably
retain the proven-unconsumed credential/learned nonce, then rethrow the original
error or timeout. Do not turn failure into apparent success. Missing headers,
transport loss before status, malformed **successful** responses and failed
publication should retain the conservative quarantine boundary.

The minimal regression matrix is received 400/403/503 × valid/missing nonce ×
malformed/oversized/timed-out body, contrasted with unknown-status loss and 2xx
invalid data. This is an availability/classification correction, not permission
to blindly retry writes.

## What held up under review

- **Authorization races:** reservation precedes discovery/PAR; newer reservation or
  logout invalidates its publication. Consumed claims are generation-scoped and
  checked again after exchange. A new probe pauses an old exchange, completes a
  newer authorization through an independent authority, then releases the old
  response. The old completion fails stale; the replacement remains current and
  the old detached session handle cannot send a resource request.
- **Fresh locked state:** `withSession` checks client/DID/generation against disk
  under the stable OS lock, before credentials authorize work. Its captured
  persistence state cannot be interleaved by another cooperating process while
  the lock is held. Existing child-process tests support that boundary; no
  singleton is being credited with cross-process correctness.
- **Rotation versus ordinary requests:** the durable marker precedes rotation;
  bounded rotation/publication runs non-cancellably under the same lock. Ordinary
  resource failure does not create that marker. Discovery before rotation is
  cancellable and non-consuming. There is no network inside `MainPrefs` transactions.
- **Publication failure:** existing reported tests cover pre-replacement failure.
  A new probe injects an error *after* the atomic replacement, modeling a failed
  post-rename acknowledgement. The call throws, but a later request uses the new
  durable credential without another refresh. Documentation should allow either
  the marker or valid new state after such a failure—not promise the marker in
  every case. Neither outcome should expose old reusable credentials.
- **Engine leases:** synchronized acquisition/retirement plus idempotent release
  covers inter-request lifecycle gaps. A retired entry cannot close while a lease
  remains; closing the pool prevents new acquisition without abandoning old work.

## Public account, backup and native qualification

`AtProtoAccount` contains client/DID/generation, display handle, PDS and granted
scope metadata—not tokens, verifier or key. Its handle is only a lookup/fence;
authenticated operations reread privileged state. Never publish the lower-level
`SessionSnapshot`/opaque credential payload as account metadata.

Future `MainPrefs` publication/removal still needs a generation-safe protocol:
pause after OAuth completion, logout or replace the native generation, then
resume public-view publication. A stale view must not recreate a visible account
or remove its replacement. A detached reread alone is not an atomic publication
fence. This integration is absent at the frozen prefix, so it is an **obligation**,
not a newly discovered defect in A2's unfinished UI. Parameterless `logout()` is
client-wide by contract; do not silently interpret a stale UI object's removal
as generation-specific deletion.

Source inspection supports Android's dedicated `noBackupFilesDir/atproto` vault,
Keystore-owned AES key, authenticated versioned GCM envelope and fail-closed
missing-key behavior. Backup XML includes ordinary file/sharedpref/database
domains, not this dedicated directory. Desktop is honestly private-file
plaintext, not a keychain. None of this source or host-JVM evidence establishes
API 24/25 Keystore/provider/fsync behavior, Android multiprocess execution,
Windows ACLs, macOS or Graal runtime.

A proportional native path is an isolated test application/client with only
synthetic credentials: exercise the real factory/backup location, key loss and
envelope tampering, two same-UID Android processes sharing the lock, process death
after the rotating marker, and durable reopen after replacement/cancellation.
Use API 24/25 plus a current runtime when an actual device/CI runner is available.
Do not run these against a production vault or install an emulator here; no
device, `/dev/kvm` or installed emulator is available. The native qualification
ticket remains open.

## Receipts and next action

```sh
bash gradlew :atproto:test \
  --tests 'com.arn.scrobble.atproto.oauth.LifecycleReviewProbeTest' \
  --console=plain --no-configuration-cache
bash gradlew :composeApp:jvmTest \
  --tests 'com.arn.scrobble.api.atproto.AtProtoEngineReviewProbeTest' \
  --console=plain --no-configuration-cache
```

**Five module probes and one real-engine probe pass their observation/safety
assertions.** Methods named `observed…` deliberately prove undesirable frozen
behavior; they are not six passing conformance claims. XML receipts remain in
their normal result directories and ignored `.test-agent/lifecycle-review/`.
No unchanged full suite, baseline YouTube failure or Android build was rerun.

Parent should relay R3 first, then R1/R2 and the public-view publication gate.
Re-review the actual fixes/activated composition on a new explicit head; this
report does not judge A2's uncommitted future code. No production edits,
dependency changes, real accounts, remote writes, push, PR or deployment occurred.
