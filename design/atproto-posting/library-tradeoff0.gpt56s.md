---
type: Decision report
title: Kotlin AT Protocol library trade-off for Pano Scrobbler
description: Decides whether Pano's narrow native-OAuth and custom-record implementation should be replaced by atproto-kotlin or kbsky, including measured dependency size.
resource: /design/atproto-posting/library-tradeoff0.gpt56s.md
tags: [atproto, oauth, kotlin, android, graalvm, dependencies, size]
status: draft
generated: { by: model:gpt-5.6-sol, at: 2026-09-13 }
verified: { by: agent:gpt-5.6-sol, at: 2026-09-13 }
stale_after: 2026-12-13
sources:
  - id: pano-atproto
    resource: /atproto
    title: Pano's local AT Protocol implementation and tests
  - id: atproto-kotlin
    resource: https://github.com/kikin81/atproto-kotlin/tree/64a4ac1f2c3b81c7256b390c8408d8d6135b204e
    title: atproto-kotlin 9.11.0 source
  - id: kbsky
    resource: https://github.com/uakihir0/kbsky/tree/0cab17d4fb164b87fbe59d04085fecb6f02feb5c
    title: kbsky main after the 0.4.0 release
  - id: oauth-spec
    resource: https://atproto.com/specs/oauth
    title: AT Protocol OAuth specification
---

# Kotlin AT Protocol library trade-off for Pano Scrobbler

## Decision

**Keep and harden Pano's local `:atproto` module. Do not replace it with an upstream library at this time.**

This is narrower than, but does not contradict, the prior Dull-firetip decision. That work selected `atproto-kotlin` for a broader client that needed standard generated Bluesky models and several reads as well as a write. Pano already has the complete narrow path it needs: native public OAuth, refresh/revoke, one DPoP-authenticated `com.atproto.repo.createRecord` procedure, and two local custom-record shapes. For this repository:

- `atproto-kotlin` 9.11.0 is still the best Kotlin candidate, but its OAuth and XRPC response handling still fail mandatory profile checks. Those failures cannot be repaired at a wrapper boundary because the relevant token response and discovery types are internal and `AuthProvider` is only notified for HTTP 401. Adoption therefore means a maintained fork/patch set, not one upstream dependency.
- `atproto-kotlin:models` is unnecessary. Generic `XrpcClient.procedure` accepts caller serializers, and `createRecord.record` is a `JsonObject`. Pulling the model artifact adds a measured **3,948,328-byte JAR and 1,936 classes** before shrinking to avoid roughly one local request, response, and endpoint call.
- Runtime plus OAuth without models is the only plausible future upstream shape. It adds 373,531 raw classpath bytes after existing-coordinate subtraction and a `kotlinx-datetime` replacement, before retaining Pano's custom records and app seam. It would improve some refresh behavior, but today it exchanges inspected local code for a fork whose conformance patches Pano must still own.
- `kbsky` is not a viable fallback. Its released JVM graph is missing the cryptography provider needed by its own OAuth code, it treats opaque access tokens as JWTs, leaves discovery/callback/session rotation to the caller, has non-conforming DPoP construction, and requires `RecordUnion` for custom records.

The recommendation is **not** that the current local code is finished. It has release-blocking hardening work (§Local gaps). The decision is that those fixes are smaller, more direct, and easier to test against Pano's exact behavior than adopting and perpetually rebasing an upstream fork.

## Decision matrix

| Choice | Narrow feature fit | OAuth security as inspected | Session/store ownership | Incremental raw JVM bytes | Maintenance result | Decision |
|---|---|---|---|---:|---|---|
| **A. `atproto-kotlin` runtime + models + OAuth 9.11.0** | Works, but models are overwhelmingly unrelated | PAR/PKCE/DPoP and strong refresh logic; mandatory metadata, `iss`, `sub`, returned-scope/token-type, and resource-nonce behavior remain incomplete | Pano still owns durable pending/session stores and process-wide provider ownership | **4,321,859** including datetime replacement; 2,116 class delta | Delete much local machinery, then own a fork and upstream qualification suite | **Reject now** |
| **A-min. `atproto-kotlin` runtime + OAuth, no models** | Best upstream-shaped option; generic `JsonObject` write works | Same OAuth blockers as A | Same caller-owned stores and cross-instance lock | **373,531**; 180 class delta | Smaller artifact cost, but still a fork | **Future break-even candidate** |
| **B. `atproto-kotlin` runtime only + local OAuth** | Models required: **none**; runtime adds little beyond current Ktor code | Preserves local strict checks, but runtime's auth hook cannot observe 400 or successful nonce rotation | Local implementation retains all refresh/store ownership | **189,776**; 95 class delta, before any small local deletion | Duplicate abstractions with no meaningful security transfer | **Reject** |
| **C. Keep local `:atproto`** | Exact requirement, exact records, no unrelated API | Stricter discovery/token checks than 9.11.0, but local nonce, cancellation, multi-instance refresh, and storage gaps must be fixed | Entirely Pano-owned; ownership is explicit | Existing feature JAR **317,219**; no new external coordinates | Smallest surface; Pano must track the OAuth spec | **Recommend and harden** |
| **D. kbsky core + auth 0.4.0** | Broad Bluesky client; custom records fight a closed union | Does not supply conforming end-to-end native OAuth/session management | Caller owns discovery validation, callback binding, persistence, refresh serialization, terminal error policy, and revoke | **3,261,185** for a functional JVM graph with JDK crypto provider; 1,723 class delta | More dependencies and more local security code | **Reject** |

“Incremental” means candidate artifacts not already on Pano's resolved desktop runtime classpath, plus the byte delta for a coordinate version replacement. It is a pre-R8/pre-native-image proxy, not an APK estimate.

## Scope changes the earlier answer

The selected prior research is internally consistent:

- [`draft1.gpt56s.md`](/home/rektide/src/dull-firetip/design/atproto-integration/draft1.gpt56s.md#L1-L12) chose one `atproto-kotlin` stack for a broader app with account authentication, Bluesky notification and feed reads, and a custom write.
- [`init0-syn1.glm53.md`](/home/rektide/src/dull-firetip/design/atproto-integration/init0-syn1.glm53.md#L45-L69) made adoption conditional on a patch set, app-owned stores, and one provider/lock per account.
- The two source reviews verified the OAuth caveats rather than calling the module a conforming drop-in ([`review1.gpt56s.md`](/home/rektide/src/dull-firetip/design/atproto-integration/review1.gpt56s.md#L19-L48), [`review1.glm53.md`](/home/rektide/src/dull-firetip/design/atproto-integration/review1.glm53.md#L62-L84)).

Pano's earlier posting research deliberately chose no SDK, then implemented the narrow seam ([`research0-syn.unknown.md`](/design/atproto-posting/research0-syn.unknown.md#L119-L142), [`implementation1.unknown.md`](/design/atproto-posting/implementation1.unknown.md#L28-L52)). The local implementation is now real switching cost, not hypothetical code yet to be written.

## Current candidates and maintenance

### `atproto-kotlin`

- **Current release:** `9.11.0`, published 2026-08-05. Maven metadata for `runtime`, `models`, and `oauth` all reports 9.11.0 as latest/release. The local checkout and remote `main` both resolve to `64a4ac1f2c3b81c7256b390c8408d8d6135b204e`; tag `v9.11.0` points at the release commit immediately before its docs update.
- **Compatibility line:** source uses Kotlin 2.4.10, coroutines 1.11.0, serialization 1.11.0, and Ktor 3.5.2. Pano uses Kotlin 2.4.20 with the other three versions identical. Gradle resolves the stdlib to Pano's 2.4.20.
- **Activity:** the repository had 154 non-bot commits authored as Francisco Velazquez in 2026 through August, in addition to release/docs automation. It is actively developed but effectively has one primary human maintainer. Rapid major versions reflect active API/codegen evolution, not a mature long-lived compatibility line.
- **Testing caveat:** the repository has 75 OAuth tests, but both CI and release workflows run runtime/generator and Android sample tests without `:oauth:test`. A Pano adoption would need its own upstream-version qualification gate.

### `kbsky`

- **Current release:** `0.4.0`, published 2026-05-06 and confirmed by Maven metadata. Its README still shows 0.3.0 under “Stable,” so Maven metadata/release are authoritative.
- **Current source:** remote/local `main` is `0cab17d4fb164b87fbe59d04085fecb6f02feb5c` from 2026-09-08. Main has post-release API work and uses Kotlin 2.4.0, Ktor 3.5.1, coroutines/serialization 1.11.0, `khttpclient` 0.0.9, and datetime 0.8.0. Those changes are not in Maven Central 0.4.0.
- **Released dependency line:** the 0.4.0 POM declares Kotlin 2.3.21, Ktor core 3.4.3, coroutines 1.10.2, serialization 1.11.0, `khttpclient` 0.0.8, datetime `0.7.1-0.6.x-compat`, and cryptography 0.6.0. Pano upgrades the overlapping core coordinates, but `khttpclient` still introduces `ktor-client-websockets-jvm:3.3.2`, leaving a mixed Ktor graph unless explicitly aligned.
- **Activity:** main is active and has meaningful work from two recurring humans, but its CI's multiplatform test workflow runs one selected `core` live request test on native targets, not JVM auth tests. The OAuth token and refresh tests are ignored/manual.

### No better current Kotlin candidate

The official current OAuth library list names TypeScript implementations and Indigo for Go, with no Kotlin/Java entry. A GitHub repository search found application/sample repositories and `atpkt`; `atpkt` is archived and its own README says to use `atproto-kotlin`. No third Kotlin artifact has stronger primary-source evidence for native public OAuth, DPoP refresh, and custom XRPC than the two candidates above. It should not be added to this decision on name similarity alone.

## Option A — full `atproto-kotlin` runtime + models + OAuth

### Exact modules and graph

Direct Pano additions would be:

```text
io.github.kikin81.atproto:runtime:9.11.0  -> runtime-jvm
io.github.kikin81.atproto:models:9.11.0   -> models-jvm
io.github.kikin81.atproto:oauth:9.11.0    -> JVM jar
```

The resolved 28-artifact JVM graph is those three plus:

```text
io.ktor:ktor-client-content-negotiation-jvm:3.5.2
io.ktor:ktor-client-core-jvm:3.5.2
io.ktor:ktor-events-jvm:3.5.2
io.ktor:ktor-http-cio-jvm:3.5.2
io.ktor:ktor-http-jvm:3.5.2
io.ktor:ktor-io-jvm:3.5.2
io.ktor:ktor-network-jvm:3.5.2
io.ktor:ktor-serialization-jvm:3.5.2
io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2
io.ktor:ktor-serialization-kotlinx-jvm:3.5.2
io.ktor:ktor-sse-jvm:3.5.2
io.ktor:ktor-utils-jvm:3.5.2
io.ktor:ktor-websocket-serialization-jvm:3.5.2
io.ktor:ktor-websockets-jvm:3.5.2
org.jetbrains:annotations:23.0.0
org.jetbrains.kotlin:kotlin-stdlib:2.4.20
org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0
org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.11.0
org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.8.0-0.6.x-compat
org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.9.1
org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.1
org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0
org.jetbrains.kotlinx:kotlinx-serialization-json-io-jvm:1.11.0
org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0
org.slf4j:slf4j-api:2.0.18
```

Pano already has every non-ATProto coordinate on its desktop runtime classpath. The only replacement is `kotlinx-datetime-jvm:0.7.1` → `0.8.0-0.6.x-compat` (+59,533 compressed JAR bytes and +19 classes). The SDK accepts Pano's caller-supplied Ktor client, so it does not introduce another HTTP engine or OkHttp version.

### API and custom-record fit

The standard generated `CreateRecordRequest.record` is a `JsonObject`; `RepoService.createRecord` is sufficient. Both Teal and Rocksky records must remain Pano-owned because they are not in the generated standard lexicon corpus. `runtime.encodeRecord` can add `$type`, but Pano's current serializers already do this.

The whole `models` artifact is avoidable. Runtime's generic `XrpcClient.procedure` accepts local request/response serializers, so the exact create-record request and response can remain local. No minimum models artifact smaller than `models-jvm` is published. There is no endpoint-scoped model artifact.

### OAuth and security behavior at 9.11.0

What upstream gets right:

- native public-client PAR, form encoding, PKCE S256, fresh P-256 DPoP key, strict `state`, browser-roundtrip pending-store interface and TTL;
- PDS and AS nonce fields, DPoP `ath`, no obsolete `iss` in proofs, and JCA signing without a third-party JWT/crypto provider;
- strong one-provider refresh single-flight, `invalid_grant` terminal classification, transient-error preservation, non-cancellable completion after a refresh is sent, and a persistence-failure callback;
- caller-provided granular scope is sent verbatim, despite the insecure-for-Pano default documentation of `atproto transition:generic`.

What remains release-blocking:

1. `completeLogin` accepts a missing callback `iss`; it only rejects a non-null mismatch.
2. Existing-account login accepts missing token `sub`; it only rejects a non-null mismatch.
3. Initial and refreshed token responses decode but do not validate `token_type`, returned `scope`, expiry, or refreshed `sub`.
4. Discovery accepts the first authorization server rather than exactly one, does not verify protected-resource `resource`, substitutes the requested AS URL for missing `issuer`, and does not validate required grants, response type, S256, auth methods, ES256, PAR-required, callback-issuer support, or metadata content type/redirect behavior.
5. `XrpcClient` reports only HTTP 401 to `AuthProvider`; the profile calls for resilience to HTTP 400 nonce updates and requires clients to reject every DPoP response that lacks `DPoP-Nonce`. Successful response headers are never reported to auth.
6. The session and pending stores remain app-owned. Defaults are in-memory for pending state; the sample's durable session implementation is not an acceptable encrypted/backup policy for Pano.
7. Refresh serialization is per `DpopAuthProvider`. Two providers can read the same rotating token before either writes because the store API has no transaction/CAS lock. Pano still needs one process-wide provider or an app-owned per-DID lock.
8. Refresh runs non-cancellable, correctly, but the SDK installs no timeout. Pano's injected HTTP client currently has a 40-second request timeout; that policy must remain explicit.

These are not all wrapper-fixable. Token response validation happens inside `AtOAuth`; discovery wire types are internal; and the runtime auth interface exposes neither 400 nor successful headers. A conforming adoption is therefore a fork or accepted upstream patches.

### Source/tests removed and retained

Optimistically, A can delete about 1,646 of 1,864 production lines and 1,169 of 1,313 test lines from `:atproto`: local encoding, JWK/JWS, PKCE, DPoP HTTP, discovery, identity, OAuth/session client, and repository transport. It must retain or rewrite:

- `posting/Play.kt`, `posting/Records.kt`, and `posting/AtProtoPoster.kt` plus record and partial-success tests;
- an app-owned account/session coordinator so foreground posting and logout cannot create racing providers;
- both durable store adapters and their serialization/migration tests;
- Pano's scope-to-target logic and exact custom record fixtures;
- black-box conformance tests for all eight blockers above;
- the fork patch ledger and upstream `:oauth:test` qualification.

Thus source count falls, but security ownership does not fall to zero; it changes from direct implementation to upstream audit + patch rebase + app-store correctness.

### Coherent migration commits

1. Pin all three 9.11.0 modules and add a disabled adapter without changing live behavior.
2. Port Pano's existing conformance fixtures to black-box tests against the adapter; add missing-`iss`, missing-`sub`, token-scope/type, metadata, nonce, and multi-provider cases.
3. Carry the smallest fork patches needed to make those tests pass, then run upstream `:oauth:test` as dependency qualification.
4. Adapt Pano's pending/session store and introduce one process-wide account coordinator with bounded HTTP policy.
5. Port create-record through `RepoService`, retaining the two local record serializers and partial-success behavior.
6. Switch the app seam, then delete local protocol implementation files only after parity tests pass.
7. Verify API 24/25, API 37 R8 release, process death, backup exclusion, and desktop native-image before accepting the replacement.

This sequence is technically possible, but it does not meet the “one upstream library without a fork” objective today.

## Option B — runtime only, retaining local OAuth/session

### Exact modules and graph

The only direct module is:

```text
io.github.kikin81.atproto:runtime:9.11.0 -> runtime-jvm
```

Its 26-artifact graph is `runtime-jvm` plus the same 25 non-ATProto artifacts listed under A. All coordinates already exist in Pano; datetime is replaced with the 0.8 compatibility build. **Minimum models required: none.**

### Fit and conflict

Runtime would replace only the small create-record transport layer. Pano would keep its crypto, DPoP, discovery, OAuth, refresh, records, and store contracts. The generic procedure API is pleasant, but its `AuthProvider` seam is weaker than Pano's needed transport seam:

- it can produce request headers;
- it receives headers only after HTTP 401;
- it cannot receive HTTP 400 or successful response headers;
- it therefore cannot implement the current OAuth spec's nonce rules without patching `runtime` or bypassing `XrpcClient`.

If Pano keeps `DpopHttp` and `AtProtoRepository`, runtime is unused. If Pano replaces the repository, it must add an auth adapter and patch/fork the runtime callback. Option B is duplication, not delegation.

### Source/tests removed and retained

At most it could delete `AtProtoRepository.kt` (122 lines) and rewrite its 221-line test around generic XRPC. Every security-critical local file and test remains. In practice, the upstream response-hook limitation blocks even that deletion without a patch.

### Coherent migration commits

1. Add runtime 9.11.0 and a compile-only local `AuthProvider` adapter.
2. Re-run the DPoP 400/401/success matrix through generic XRPC.
3. Stop if response nonce behavior cannot pass without an upstream API change; do not switch production transport.
4. Only after an upstream response-hook release, port create-record and remove the local repository wrapper.

The expected stop occurs at commit 2 for 9.11.0, so B is rejected.

## Option C — keep local `:atproto`

### Exact modules and graph

The module has no ATProto-specific external artifact. It uses only dependencies Pano already carries:

```text
org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0
org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0
io.ktor:ktor-client-core:3.5.2
```

With Kotlin's default stdlib dependency, their exact 20-artifact JVM closure is:

```text
io.ktor:{ktor-client-core-jvm,ktor-events-jvm,ktor-http-cio-jvm,ktor-http-jvm,
  ktor-io-jvm,ktor-network-jvm,ktor-serialization-jvm,ktor-sse-jvm,
  ktor-utils-jvm,ktor-websocket-serialization-jvm,ktor-websockets-jvm}:3.5.2
org.jetbrains:annotations:23.0.0
org.jetbrains.kotlin:kotlin-stdlib:2.4.20
org.jetbrains.kotlinx:{kotlinx-coroutines-core-jvm,kotlinx-coroutines-slf4j}:1.11.0
org.jetbrains.kotlinx:{kotlinx-io-bytestring-jvm,kotlinx-io-core-jvm}:0.9.1
org.jetbrains.kotlinx:{kotlinx-serialization-core-jvm,kotlinx-serialization-json-jvm}:1.11.0
org.slf4j:slf4j-api:2.0.18
```

The built feature JAR is 317,219 bytes, 714,449 uncompressed ZIP-entry bytes, and 142 JVM classes. Production is 1,864 lines across 23 Kotlin files; tests are 1,313 lines with 44 `@Test` cases. `:atproto:test --rerun-tasks` passed during this review.

### Why it fits

- The two custom records are first-class local types with exact unit, field-limit, and partial-success tests.
- The repository operation is exactly one request and returns exactly `{uri,cid}`; there is no standard lexicon model surface to keep current.
- OAuth metadata and token response checking is materially stricter than `atproto-kotlin` 9.11.0: exact one-AS and resource binding, issuer and required capabilities, required callback issuer, required token subject/scope/type, post-token identity re-resolution, strict content types, bounded discovery bodies, and redirect rejection.
- The app store is keyed by DID and the provider seam already exposes only complete operations.

### Local gaps that must be fixed

Keeping local code is acceptable only with explicit hardening. Source inspection found gaps not captured by the implementation report:

1. **DPoP response invariant:** `DpopHttp` stores a nonce if present but accepts a DPoP response with no nonce. The OAuth profile says the client must reject it.
2. **Resource HTTP 400:** resource nonce retry recognizes only HTTP 401 + `WWW-Authenticate`; clients should recover from unexpected HTTP 400 nonce rotation too.
3. **Pending AS nonce:** successful PAR nonce state is only in memory. `PendingAuthorization` does not persist it, so process death causes the token request to start without the known AS nonce. This is especially risky because an authorization code can be consumed on a nonce-challenged token request.
4. **Refresh completion:** refresh token exchange and durable `sessions.put` are cancellable as a unit. Cancellation or persistence failure after the server rotates the token can strand the session. There is no `NonCancellable` completion window or persistence-failure recovery signal.
5. **Terminal error policy:** `invalid_grant` is thrown as a generic OAuth response and the stale session remains stored. It should become a terminal signed-out state; transient network/429/5xx failures should preserve state.
6. **Cross-instance concurrency:** locks belong to each `AtProtoSessionClient`. The app constructs clients in login, each `AtProtoScrobblable`, and logout. The DataStore API is atomic per update but does not prevent two instances from sending the same old refresh token.
7. **Nonce persistence:** session nonces are process-local rather than persisted per account and origin. This adds challenge traffic after restart and interacts poorly with single-use auth artifacts.
8. **Secret storage:** `PanoAtProtoStore` puts refresh tokens, DPoP private keys, and pending PKCE state into the general `MainPrefs` DataStore. No encryption or Android backup exclusion was found. An upstream OAuth library would not fix this; stores remain caller-owned.
9. **Coverage:** current tests prove one-client concurrent refresh, but not two-client races, process death, cancellation after server rotation, persistence failure, `invalid_grant`, missing nonce, 400 resource nonce, or API 24/25 and Graal reachability.

The existing 40-second shared Ktor request timeout is a useful bound and should be retained, though a dedicated OAuth client would make the policy easier to audit than a generic shared client.

### Source/tests retained

All current local production and tests remain initially. Hardening can deepen existing seams rather than add another SDK abstraction:

- add persisted per-origin nonce fields to pending/session models;
- make one process-wide `AtProtoSessionClient`/coordinator own all refreshes;
- keep `AuthorizationStateStore.take` atomic and make rotation persistence behavior explicit;
- keep exact record/posting tests unchanged;
- add an encrypted secret store adapter while leaving non-secret target/progress preferences in ordinary DataStore.

### Coherent implementation commits

1. Add failing conformance fixtures for missing response nonce, resource 400, persisted PAR nonce, terminal/transient refresh, cancellation, and two-client rotation.
2. Persist AS/PDS nonces by origin and enforce exactly one challenge retry plus the mandatory response nonce invariant.
3. Add a process-wide per-DID session coordinator and route login, posting, and logout through it.
4. Make post-send refresh completion non-cancellable through atomic persistence; distinguish `invalid_grant`, retryable failure, and indeterminate post-send failure.
5. Move pending/session secrets to an encrypted, backup-excluded store with migration and corruption tests.
6. Add process-death, API 24/25, API 37 R8, and desktop native-image qualification.

This keeps each security change reviewable and avoids changing the posting domain while OAuth is being hardened.

## Option D — kbsky as rejected alternative

### Exact functional JVM modules and graph

The advertised direct modules are:

```text
work.socialhub.kbsky:core:0.4.0
work.socialhub.kbsky:auth:0.4.0
```

The resolved JVM graph does **not** contain a cryptography provider, although kbsky calls `CryptographyProvider.Default`. A direct probe of the released graph throws:

```text
IllegalStateException: No providers registered. Please provide a dependency or register provider explicitly
```

A minimally functional JVM graph therefore also needs:

```text
dev.whyoleg.cryptography:cryptography-provider-jdk:0.6.0
```

New coordinates after subtracting Pano's runtime classpath are exactly:

```text
dev.whyoleg.cryptography:cryptography-bigint-jvm:0.6.0
dev.whyoleg.cryptography:cryptography-core-jvm:0.6.0
dev.whyoleg.cryptography:cryptography-provider-base-jvm:0.6.0
dev.whyoleg.cryptography:cryptography-provider-jdk-jvm:0.6.0
dev.whyoleg.cryptography:cryptography-random-jvm:0.6.0
dev.whyoleg.cryptography:cryptography-serialization-asn1-jvm:0.6.0
dev.whyoleg.cryptography:cryptography-serialization-asn1-modules-jvm:0.6.0
dev.whyoleg.cryptography:cryptography-serialization-pem-jvm:0.6.0
io.ktor:ktor-client-websockets-jvm:3.3.2
work.socialhub:khttpclient-jvm:0.0.8
work.socialhub.kbsky:auth-jvm:0.4.0
work.socialhub.kbsky:core-jvm:0.4.0
```

The other 24 artifacts in the 36-artifact graph are exactly:

```text
com.squareup.okhttp3:okhttp-jvm:5.5.0
com.squareup.okio:okio-jvm:3.18.1
io.ktor:{ktor-client-core-jvm,ktor-client-okhttp-jvm,ktor-events-jvm,
  ktor-http-cio-jvm,ktor-http-jvm,ktor-io-jvm,ktor-network-jvm,
  ktor-serialization-jvm,ktor-sse-jvm,ktor-utils-jvm,
  ktor-websocket-serialization-jvm,ktor-websockets-jvm}:3.5.2
org.jetbrains:annotations:23.0.0
org.jetbrains.kotlin:kotlin-stdlib:2.4.20
org.jetbrains.kotlinx:{kotlinx-coroutines-core-jvm,kotlinx-coroutines-slf4j}:1.11.0
org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.7.1-0.6.x-compat
org.jetbrains.kotlinx:{kotlinx-io-bytestring-jvm,kotlinx-io-core-jvm}:0.9.1
org.jetbrains.kotlinx:{kotlinx-serialization-core-jvm,kotlinx-serialization-json-jvm}:1.11.0
org.slf4j:slf4j-api:2.0.18
```

Kbsky replaces Pano datetime 0.7.1 with `0.7.1-0.6.x-compat` (+30,893 bytes, +12 classes). Pano's direct Ktor OkHttp dependency raises that artifact to 3.5.2, while `khttpclient` leaves client-websockets at 3.3.2.

### API, OAuth, and security fit

Kbsky is unsuitable for this operation even apart from size:

- OAuth is a sequence of low-level calls. The caller owns identity discovery and validation, callback `state`/`iss`, `sub` binding, persistence, expiry, refresh concurrency/rotation, invalid-grant policy, and revocation. Its documentation explicitly tells the caller to save context fields.
- Its manual token test has `state` and `iss` extraction commented out; token and refresh tests are ignored.
- `OAuthProvider` parses `accessTokenJwt` to derive `sub`, `aud`, and `iss`. The current OAuth spec says access tokens are opaque to clients.
- It has one mutable `dPoPNonce` for all origins and retries resource nonce only on HTTP 401 with a body substring.
- Resource DPoP URL construction renders an explicit default port; proof payloads add obsolete `iss`, `sub`, and `exp`; JWK `x`/`y` construction prepends a zero byte to already fixed-width coordinates. This is not a profile-conforming DPoP implementation.
- Public OAuth methods use blocking wrappers internally rather than a suspend-first API.
- `RepoCreateRecordRequest.record` is `RecordUnion`, and `RecordPolymorphicSerializer` enumerates known Bluesky records. Teal and Rocksky are not members. Pano would need a fork, an unsafe custom subclass/serializer workaround, or bypass the repository API.

### Source/tests removed and retained

Almost no local security code can be safely deleted. Pano still needs strict discovery, callback completion, a store, serialized refresh, revoke, and likely its own create-record transport. Kbsky would replace local cryptographic primitives with a much larger provider stack while adding a second HTTP wrapper.

### Coherent migration sequence

There is no recommended production sequence. A disposable proof would:

1. Resolve core + auth + JDK provider and assert provider availability on JVM/Android/Graal.
2. Run Pano's callback/token/DPoP fixtures unchanged.
3. Attempt both custom records through `RepoCreateRecordRequest` without modifying kbsky.
4. Stop and remove the spike when opaque-token, conformance, or `RecordUnion` tests fail.

Primary source already demonstrates those failures, so even this spike is not warranted.

## Size impact

### Measured inputs

JAR bytes are compressed artifact/file bytes. “Uncompressed” sums ZIP entry sizes. Class counts count top-level `.class` entries. Candidate graphs were resolved as JVM runtime variants with Pano's Kotlin/Ktor/kotlinx/OkHttp constraints, then compared by `group:artifact` with Pano's actual 117-artifact desktop runtime classpath.

| Shape | Raw resolved graph (all artifacts) | New artifacts after coordinate subtraction | New artifact JAR bytes | Replacement delta | Incremental classpath bytes | Incremental classes | Incremental uncompressed ZIP bytes |
|---|---:|---:|---:|---:|---:|---:|---:|
| atproto-kotlin runtime only | 26 artifacts / 8,024,139 B | 1 | 130,243 | +59,533 datetime | **189,776** | 95 | 439,133 |
| atproto-kotlin runtime + models | 27 / 11,972,467 B | 2 | 4,078,571 | +59,533 | **4,138,104** | 2,031 | 9,865,872 |
| atproto-kotlin runtime + OAuth | 27 / 8,207,894 B | 2 | 313,998 | +59,533 | **373,531** | 180 | 855,409 |
| **A: runtime + models + OAuth** | 28 / 12,156,222 B | 3 | 4,262,326 | +59,533 | **4,321,859** | 2,116 | 10,282,148 |
| kbsky core + auth, provider missing | 31 / 11,803,488 B | 7 | 2,618,233 | +30,893 datetime | **2,649,126** | 1,375 | 6,254,361 |
| **D: kbsky + required JDK provider** | 36 / 12,415,547 B | 12 | 3,230,292 | +30,893 | **3,261,185** | 1,723 | 7,631,755 |
| **C: current local feature JAR** | existing Pano classpath | 0 external | 317,219 project JAR | none | **317,219 feature input** | 142 | 714,449 |

Direct candidate artifacts explain the result:

| Artifact | Compressed JAR bytes | Uncompressed ZIP bytes | Classes |
|---|---:|---:|---:|
| `atproto-kotlin:runtime-jvm:9.11.0` | 130,243 | 275,142 | 76 |
| `atproto-kotlin:oauth:9.11.0` | 183,755 | 416,276 | 85 |
| `atproto-kotlin:models-jvm:9.11.0` | **3,948,328** | **9,426,739** | **1,936** |
| Pano `:atproto` | 317,219 | 714,449 | 142 |
| `kbsky:core-jvm:0.4.0` | 2,125,411 | 5,145,798 | 1,018 |
| `kbsky:auth-jvm:0.4.0` | 117,379 | 297,570 | 43 |
| kbsky-required cryptography artifacts, total | 936,872 | 1,989,767 | 630 |
| `khttpclient-jvm:0.0.8` + new Ktor client-websockets | 50,630 | 115,844 | 20 |

An optimistic “replace the entire current JAR” lower bound is +4,004,640 raw bytes for A and +2,943,966 for functional kbsky. Those are too optimistic because Pano's custom records/poster and app store must remain. Runtime+OAuth without models is only +56,312 bytes against deleting the whole current JAR, but retaining the custom-record portion makes the real delta larger and unmeasured.

### Android APK/AAB and R8

No APK/AAB delta is claimed. This environment has Android platform/build-tools 35, while Pano compiles against 37; the production release build could not be measured. Pano also uses R8 (`isMinifyEnabled` and resource shrinking), so raw JAR bytes are an upper-bound input, not a download-size prediction.

Reasonable expectations, deliberately not numeric estimates:

- R8 should remove most unused generated model classes, but adding a 1,936-class model corpus merely to use create-record is avoidable risk and build cost. Generic XRPC avoids relying on shrinker quality.
- Runtime+OAuth has a much smaller reachable surface than full models.
- Current local code is already purpose-trimmed, so a larger share of its 142 classes is likely reachable.

The required acceptance measurement is an API 37 release APK/AAB pair built from otherwise identical revisions, compared with APK Analyzer and R8 usage reports. Until then, the reproducible raw graph/class counts above are the strongest proxy.

### Desktop JVM and GraalVM

For a non-shrunk JVM distribution, incremental JAR bytes approximate additional classpath payload before installer compression. For GraalVM, final size is **unknown**: native-image reachability can eliminate unused generated models, but JCA, Ktor, serializers, and service-loaded crypto providers may add configuration and reachable implementation code.

None of the inspected atproto-kotlin or kbsky JARs contains `META-INF/native-image` reachability metadata. Atproto-kotlin OAuth uses ordinary JCA like Pano's local code. Kbsky additionally uses a service-loaded cryptography provider; its no-provider graph fails on an ordinary JVM and the provider-expanded graph is larger. Pano's `--exact-reachability-metadata` native build makes a real smoke test mandatory for either candidate.

## Platform implications

| Concern | atproto-kotlin 9.11.0 | Local `:atproto` | kbsky 0.4.0 |
|---|---|---|---|
| JVM bytecode | Java 17 / class major 61 | Current toolchain emits Java 25 / major 69 | Java 11 / major 55 |
| Android artifact | Runtime/models are KMP JVM publications; OAuth is JVM; upstream Android sample consumes them | JVM project consumed by common code | KMP JVM publications, not Android AARs |
| Demonstrated minSdk | Upstream sample minSdk 28, not a library guarantee | Pano declares minSdk 24; Android build remains unverified | No equivalent native-OAuth Android qualification found |
| API 24/25 risk | OAuth clock calibration directly uses `java.time.ZonedDateTime` (API 26 without core-library desugaring); Pano currently enables no desugaring | No direct `java.time` in OAuth code | Release OAuth uses datetime/cryptography abstractions; actual API 24/25 behavior unproven |
| Ktor/serialization/coroutines | Exact Ktor 3.5.2, serialization/coroutines 1.11.0 alignment | Exact Pano catalog | Released older Ktor/coroutines line partly upgraded by Pano; one Ktor 3.3.2 module remains |
| OkHttp | Uses injected Pano Ktor client; no new engine | Uses Pano Ktor client | Introduces `khttpclient`; Pano's OkHttp remains but wrapper/Ktor mix expands |
| GraalVM | No metadata; JCA/serialization smoke required | Already outstanding; no metadata | No metadata; service-loaded provider and broad models increase risk |

Atproto-kotlin adoption would therefore also require either raising Pano's effective OAuth floor to API 26, enabling/testing core-library desugaring, or replacing the `java.time` calibration path in the fork.

## Break-even conditions

Revisit the recommendation when **all** of these hold in one released `atproto-kotlin` version:

1. Existing-account completion requires callback `iss`, token `sub`, `token_type=DPoP`, and returned `scope` containing `atproto`.
2. Resource and authorization metadata are fully profile-validated, including exact one-AS/resource/issuer binding, required capabilities, content type, and no redirects.
3. XRPC/auth APIs process 400, 401, and successful `DPoP-Nonce` headers and reject missing nonces on DPoP requests.
4. Refreshed token responses are validated, and one app-owned coordinator or an atomic store contract can prevent cross-provider replay.
5. OAuth tests are release-gated upstream, or Pano can run the exact released source's OAuth suite reproducibly.
6. Pano can depend on `runtime + oauth` **without models**, using local serializers for create-record.
7. API 24/25 (or a deliberate minSdk/desugaring decision), R8 release, process-death login, backup exclusion, and Graal native-image pass.
8. Adoption removes the local crypto/discovery/refresh implementation without a long-lived fork. A tiny temporary compatibility shim is acceptable; a source fork is not the desired steady state.

Two product changes can alter the economics earlier:

- If Pano adds several standard `app.bsky.*`/`com.atproto.*` reads and writes, generated models become useful rather than dead weight.
- If local conformance upkeep grows beyond this narrow operation or a maintained official Kotlin OAuth SDK appears, upstream maintenance may outweigh local simplicity even with a modest size increase.

Kbsky's break-even bar is much higher: opaque-token support, conforming DPoP, end-to-end discovery/callback/session/revoke orchestration, open `JsonObject` custom records, a correct published JVM crypto graph, and hermetic auth CI would all need to land first.

## Sources and reproducibility

### Primary source references

- [AT Protocol OAuth spec, clients and metadata](https://atproto.com/specs/oauth#clients), [identity authentication](https://atproto.com/specs/oauth#identity-authentication), [scopes](https://atproto.com/specs/oauth#authorization-scopes), [tokens](https://atproto.com/specs/oauth#tokens-and-session-lifetime), [DPoP](https://atproto.com/specs/oauth#demonstrating-proof-of-possession-d-po-p), and [server metadata](https://atproto.com/specs/oauth#server-metadata). The inspected local specification is [`en.mdx` L35–169, L194–243, L266–331](/home/rektide/archive/bluesky-social/atproto-website/src/app/[locale]/specs/oauth/en.mdx#L35-L331), last changed by `7c510649945bbae7d598803ec9483b4f165170f4` on 2026-06-11.
- [Official OAuth library list](https://atproto.com/guides/about-oauth#sdk-libraries), locally [`about-oauth/en.mdx` L17–33](/home/rektide/archive/bluesky-social/atproto-website/src/app/[locale]/guides/about-oauth/en.mdx#L17-L33) — TypeScript and Go, no Kotlin implementation.
- [`atproto-kotlin` 9.11.0 `AtOAuth.kt` L70–96, L229–360, L419–551](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/oauth/src/main/kotlin/io/github/kikin81/atproto/oauth/AtOAuth.kt#L70-L551) — defaults, optional `iss`/`sub`, stores, create-client, revoke, PAR/token behavior.
- [`atproto-kotlin` `DiscoveryChain.kt` L63–131, L184–336, L380–396](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/oauth/src/main/kotlin/io/github/kikin81/atproto/oauth/DiscoveryChain.kt#L63-L396) — discovery and parsed metadata subset.
- [`atproto-kotlin` `DpopAuthProvider.kt` L49–230, L265–438](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/oauth/src/main/kotlin/io/github/kikin81/atproto/oauth/DpopAuthProvider.kt#L49-L438) — timeout ownership, single-flight, rotation persistence, terminal/transient behavior.
- [`atproto-kotlin` `AuthProvider.kt` L32–52](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/runtime/src/commonMain/kotlin/io/github/kikin81/atproto/runtime/AuthProvider.kt#L32-L52) and [`XrpcClient.kt` L44–97, L154–183](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/runtime/src/commonMain/kotlin/io/github/kikin81/atproto/runtime/XrpcClient.kt#L44-L183) — generic local serializers and 401-only response hook.
- [`atproto-kotlin` module dependencies](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/gradle/libs.versions.toml), [`runtime/build.gradle.kts` L26–50](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/runtime/build.gradle.kts#L26-L50), [`oauth/build.gradle.kts` L18–28](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/oauth/build.gradle.kts#L18-L28); Maven metadata: [`runtime`](https://repo1.maven.org/maven2/io/github/kikin81/atproto/runtime/maven-metadata.xml), [`models`](https://repo1.maven.org/maven2/io/github/kikin81/atproto/models/maven-metadata.xml), [`oauth`](https://repo1.maven.org/maven2/io/github/kikin81/atproto/oauth/maven-metadata.xml).
- [`atproto-kotlin` CI omits OAuth tests](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/.github/workflows/ci.yaml#L75-L87); [Android sample minSdk/toolchain](https://github.com/kikin81/atproto-kotlin/blob/64a4ac1f2c3b81c7256b390c8408d8d6135b204e/samples/android/build.gradle.kts#L9-L43).
- [`kbsky` OAuth walkthrough](https://github.com/uakihir0/kbsky/blob/0cab17d4fb164b87fbe59d04085fecb6f02feb5c/docs/OAUTH.md#L9-L90), [`OAuthProvider.kt` L18–119](https://github.com/uakihir0/kbsky/blob/0cab17d4fb164b87fbe59d04085fecb6f02feb5c/auth/src/commonMain/kotlin/work/socialhub/kbsky/auth/OAuthProvider.kt#L18-L119), [`OAuthHelper.kt` L18–89](https://github.com/uakihir0/kbsky/blob/0cab17d4fb164b87fbe59d04085fecb6f02feb5c/auth/src/commonMain/kotlin/work/socialhub/kbsky/auth/helper/OAuthHelper.kt#L18-L89), and [`OAuthResourceImpl.kt` L33–251](https://github.com/uakihir0/kbsky/blob/0cab17d4fb164b87fbe59d04085fecb6f02feb5c/auth/src/commonMain/kotlin/work/socialhub/kbsky/auth/internal/OAuthResourceImpl.kt#L33-L251).
- [`kbsky` custom record request](https://github.com/uakihir0/kbsky/blob/0cab17d4fb164b87fbe59d04085fecb6f02feb5c/core/src/commonMain/kotlin/work/socialhub/kbsky/api/entity/com/atproto/repo/RepoCreateRecordRequest.kt#L11-L35), [`RecordUnion`](https://github.com/uakihir0/kbsky/blob/0cab17d4fb164b87fbe59d04085fecb6f02feb5c/core/src/commonMain/kotlin/work/socialhub/kbsky/model/share/RecordUnion.kt#L28-L43), and [closed serializer dispatch](https://github.com/uakihir0/kbsky/blob/0cab17d4fb164b87fbe59d04085fecb6f02feb5c/core/src/commonMain/kotlin/work/socialhub/kbsky/util/json/RecordPolymorphicSerializer.kt#L21-L53).
- Kbsky Maven metadata/POMs: [`core` metadata](https://repo1.maven.org/maven2/work/socialhub/kbsky/core/maven-metadata.xml), [`auth` metadata](https://repo1.maven.org/maven2/work/socialhub/kbsky/auth/maven-metadata.xml), [`core 0.4.0 POM`](https://repo1.maven.org/maven2/work/socialhub/kbsky/core/0.4.0/core-0.4.0.pom), [`auth 0.4.0 POM`](https://repo1.maven.org/maven2/work/socialhub/kbsky/auth/0.4.0/auth-0.4.0.pom).
- Pano source: [`Discovery.kt` L44–131](/atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/Discovery.kt#L44-L131), [`AtProtoOAuthClient.kt` L60–218](/atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoOAuthClient.kt#L60-L218), [`AtProtoSessionClient.kt` L39–97](/atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoSessionClient.kt#L39-L97), [`DpopHttp.kt` L30–106](/atproto/src/main/kotlin/com/arn/scrobble/atproto/http/DpopHttp.kt#L30-L106), [`AtProtoRepository.kt` L37–122](/atproto/src/main/kotlin/com/arn/scrobble/atproto/repo/AtProtoRepository.kt#L37-L122), and [`PanoAtProtoStore.kt` L11–53](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoStore.kt#L11-L53).
- Pano versions and platforms: [`libs.versions.toml` L1–52](/gradle/libs.versions.toml#L1-L52), [`composeApp/build.gradle.kts` L47–142](/composeApp/build.gradle.kts#L47-L142), [`androidApp/build.gradle.kts` L30–78](/androidApp/build.gradle.kts#L30-L78), and [`desktopApp/build.gradle.kts` L204–265](/desktopApp/build.gradle.kts#L204-L265).

### Measurement commands

The ignored resolver and full method are in `.test-agent/atproto-library-size/README.md`. Core reproduction commands:

```sh
# Resolve candidate graphs with Pano's conflict versions.
sh ./gradlew -p .test-agent/atproto-library-size resolveCandidates

# Record artifact, ZIP, and class measurements.
for name in panoLocalExternal atkRuntime atkRuntimeModels atkRuntimeOauth atkFull kbskyCoreAuth kbskyCoreAuthJdk; do
  sh ./gradlew -p .test-agent/atproto-library-size \
    -I "$PWD/.test-agent/atproto-library-size/report-artifacts.init.gradle" \
    -PartifactConfig="$name" \
    -PartifactOutput="build/reports/$name-detail.tsv" \
    reportResolvedArtifacts
done

# Build and measure the current local module.
sh ./gradlew :atproto:jar
stat -c '%s' atproto/build/libs/atproto.jar
unzip -Z1 atproto/build/libs/atproto.jar | grep -c '\.class$'

# Verify its tests from clean task execution.
sh ./gradlew --no-configuration-cache :atproto:test --rerun-tasks

# Demonstrate kbsky's missing JVM crypto provider, then the explicit-provider fix.
CP=$(awk -F '\t' 'NR>1 {print $10}' \
  .test-agent/atproto-library-size/build/reports/kbskyCoreAuth-detail.tsv | paste -sd:)
javac -cp "$CP" .test-agent/atproto-library-size/provider-probe/ProviderProbe.java
java -cp ".test-agent/atproto-library-size/provider-probe:$CP" ProviderProbe
# => IllegalStateException: No providers registered

CP=$(awk -F '\t' 'NR>1 {print $10}' \
  .test-agent/atproto-library-size/build/reports/kbskyCoreAuthJdk-detail.tsv | paste -sd:)
java -cp ".test-agent/atproto-library-size/provider-probe:$CP" ProviderProbe
# => JDK
```
