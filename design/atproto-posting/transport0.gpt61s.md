---
type: Implementation checkpoint
title: One-attempt credential POSTs at the real OkHttp engine boundary
description: Focused R3 correction using the public one-shot body contract, with real-wire status preservation, explicit nonce retry and proxy CONNECT evidence.
resource: /design/atproto-posting/transport0.gpt61s.md
status: locally-verified
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-transport-replay
  code_commit: b3a69e4d62f73bb5de2eba4f06cf07e3ee9e3db0
  frozen_base: def70d8a42e58c60de520ae154f03c6c7bb97fef
sources:
  - resource: "opencode://ses_e7af14a6cffeDevLvL0joxsto6"
    title: B3 implements and locally verifies the isolated public one-shot engine correction, positive R3 regression, real adapter nonce retry and CONNECT boundary
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent commissions the focused R3 correction independently of A2's native integration and other lifecycle corrections
  - resource: "file:///home/rektide/src/pano-atproto-review/design/atproto-posting/lifecycle-review0.gpt61s.md#r3--hidden-http-503-credential-replay"
    title: Frozen native review demonstrates identical credential body and proof replay on HTTP 503 Retry-After zero
  - resource: /design/atproto-posting/oauth0.gpt61s.md
    title: Existing native lifecycle and dedicated leased-client composition
  - resource: /design/atproto-posting/storage0.gpt61s.md
    title: Native durability authority and platform qualification limits, unchanged here
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoTransport.kt
    title: Public application interceptor and transparent one-shot request-body wrapper
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/Requesters.kt
    title: Existing dedicated engine construction and base proxy, TLS, user-agent, timeout and invalidation policy
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt
    title: Every current native PAR, exchange, refresh, revocation and record POST uses the leased client
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/http/DpopHttp.kt
    title: Protocol-owned nonce retry mints a fresh proof and constructs a new request for each attempt
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoTransportReplayTest.kt
    title: Positive real-wire replay and status/header/body regression, current POST shapes and transparent body semantics
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoProtocolTransportTest.kt
    title: Real native authority and engine POSTs retain a fresh-proof, single-budget explicit nonce retry
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoProxyTransportTest.kt
    title: Real HTTPS proxy CONNECT challenge/authentication still runs before origin credentials and TLS initialization
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoTransportTest.kt
    title: Existing real-engine redirect rejection, rerun against the corrected policy
  - resource: https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp-jvm/5.5.0/okhttp-jvm-5.5.0-sources.jar
    title: Exact public RequestBody contract and retry, application-interceptor and CONNECT implementation inspected
  - resource: https://repo.maven.apache.org/maven2/io/ktor/ktor-client-okhttp-jvm/3.6.0/ktor-client-okhttp-jvm-3.6.0-sources.jar
    title: Exact public interceptor registration, engine body conversion and streaming-body delegation inspected
---

# One-attempt credential POST transport

**R3 corrected locally; no app activation or real-account qualification.** Carry
only code/test commit `b3a69e4d62f73bb5de2eba4f06cf07e3ee9e3db0` into A2's
new native head, not the frozen branch's whole tree. This workspace is
`/home/rektide/src/pano-atproto-transport`; code bookmark is
`atproto-transport-one-shot-20261029`. A separate evidence commit contains this
receipt and the reading-map link. R1/R2, native integration and resource recovery
remain A2's work; its uncommitted tree was neither read nor modified.

## Why this seam

The [frozen R3 observation](file:///home/rektide/src/pano-atproto-review/design/atproto-posting/lifecycle-review0.gpt61s.md#r3--hidden-http-503-credential-replay)
is a real OkHttp 5.5.0 follow-up, not a protocol retry: HTTP 503 with
`Retry-After: 0` bypasses `retryOnConnectionFailure(false)`. Its generic follow-up
gate honors `RequestBody.isOneShot()`. That **public contract explicitly names
503, 408, authentication and coalesced-connection retransmission**.

[`atProtoCredentialPolicy`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoTransport.kt)
now registers a public Ktor/OkHttp **application interceptor**, before the engine's
retry/follow-up layer, to wrap every non-null request body as one-shot. A network
interceptor would be too late: the retry layer retains its own upstream request.
The existing redirect and connection-retry prohibitions remain in place.

The wrapper delegates content type, byte length (including unknown `-1`), duplex
mode and `writeTo` directly. It never reads/buffers the body, changes bytes or
closes the sink. It never rewrites server status, body, nonce, `Retry-After` or
other meaningful response headers. It uses no private engine API, dependency
upgrade, special authenticator or endpoint/header heuristic.

The selected [native authority](/atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt)
uses the same dedicated leased client for PAR, code exchange, refresh, revocation
and PDS record POSTs. Their string/JSON bodies all receive the policy. Ktor 3.6.0
also gives a bodyless POST an empty non-null OkHttp body; this case is tested.
**This is not a generic one-attempt claim for credential-bearing bodyless GETs,
WebSockets, arbitrary later middleware or some future engine version.** Current
exposed authenticated native operations are POSTs, not such a general fetch API.

## Retries and proxy setup stay with their owners

[`DpopHttp`](/atproto/src/main/kotlin/com/arn/scrobble/atproto/http/DpopHttp.kt)
constructs a new request and fresh proof for an accepted nonce challenge. Each
engine call gets its own wrapper; no global payload-consumption flag blocks the
legitimate second call. Resource `invalid_token` recovery is not changed here.

The patch does not alter [Pano's base construction](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/Requesters.kt):
normal TLS, proxy and proxy authenticator, user agent and timeout remain; lease
retirement and proxy invalidation are untouched. HTTPS proxy CONNECT authentication
is connection setup, using a separate engine-generated request before any origin
credential POST. It is not an origin replay and remains allowed. An HTTP-proxy 407
after sending an origin body must not silently replay that body.

## Focused verification

Before the production edit, the positive R3 regression fails against frozen
`def70d8a`: **expected 503, actual 200**. The original loopback first response is
hidden by the automatic replay. The red XML/log is preserved in ignored scratch.

After the fix, **six targeted JVM tests pass**:

| Test | Established property |
|---|---|
| Positive R3 | One refresh POST and proof; original 503, nonce, Retry-After and body returned. |
| POST-shape matrix | PAR, code, refresh, revoke, PDS UTF-8 JSON and empty POST each sent once on 503 and 408; exact payload, content type and byte length retained. |
| Wrapper contract | Known/unknown length and duplex/nonduplex metadata preserved; same sink delegated once without pre-reading. This is not an HTTP/2 duplex runtime test. |
| Native adapter nonce | Real authority/DPoP adapter emits two actual loopback POSTs with identical PAR payload, same public key, different proof/jti, correct learned nonce; a second challenge stops at two. Only discovery and endpoint-to-loopback routing are synthetic. |
| HTTPS proxy CONNECT | Actual proxy challenge causes two CONNECTs, with proxy authentication and no origin token/proof/body; TLS handshake then begins. Fixture deliberately closes at TLS initialization, not a successful TLS/tunnel or trust qualification. |
| Existing redirects | 301/302/303/307/308 still returned without following the credential-bearing target. |

```sh
bash gradlew :composeApp:jvmTest \
  --tests 'com.arn.scrobble.api.atproto.AtProtoTransportReplayTest' \
  --tests 'com.arn.scrobble.api.atproto.AtProtoProtocolTransportTest' \
  --tests 'com.arn.scrobble.api.atproto.AtProtoProxyTransportTest' \
  --tests 'com.arn.scrobble.api.atproto.AtProtoTransportTest' \
  --console=plain --no-configuration-cache
```

Android source compilation also passes on the code checkpoint:

```sh
bash gradlew :composeApp:compileAndroidMain --console=plain --no-configuration-cache
```

This verifies that the changed shared policy compiles against the Android engine;
it is not APK assembly, on-device execution or platform-vault evidence.

Receipts: `.test-agent/transport-replay/` and normal Gradle test-result directories.
The first combined run exposed a missing synthetic discovery Content-Type header;
the fixture was corrected before the six-test pass. No unchanged full app/module
suite, primitive audit, unrelated YouTube failure or device test was rerun. The
[existing native vault qualification limits](storage0.gpt61s.md#platform-guarantees-and-limits)
remain: host OkHttp evidence is not Android Keystore, API 24/25, multiprocess,
Windows/macOS/Graal or live AS/PDS evidence. No real credentials, public-host
requests from the fixtures, push, PR or deployment occurred.
