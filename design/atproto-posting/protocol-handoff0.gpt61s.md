---
type: Implementation handoff
title: Reusable protocol transport hardening for the Pano remake
description: Carryable streaming and DPoP fixes, original-failure proofs, strict native integration obligations and remaining validation findings at worker turnover.
resource: /design/atproto-posting/protocol-handoff0.gpt61s.md
status: partial-implementation-handoff
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-28 }
extensions:
  ticket: pano-atproto-remake-protocol
sources:
  - resource: "opencode://ses_e7b3ccec5ffeuJpOmxDoerRcX6"
    title: Isolated the old self-contained checkpoint, reproduced transport failures, implemented reusable fixes and recorded successor obligations without changing native authority
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent selected a narrow local backend and commissioned bounded reusable protocol qualification alongside worker A's native authority lane
  - resource: "file:///home/rektide/src/pano-scrobbler-at/design/atproto-posting/pr-brief0.gpt6a.md"
    title: Preserved old implementation and public-dependency boundary
  - resource: https://www.rfc-editor.org/rfc/rfc9449.html#section-8
    title: Authorization-server nonce errors, nonce syntax, successful response updates and fresh proofs
  - resource: https://www.rfc-editor.org/rfc/rfc9449.html#section-9
    title: Resource-server 401 DPoP challenge classification and distinct nonce contexts
  - resource: https://atproto.com/specs/oauth
    title: Mandatory nonce headers and client/account/issuer binding; read in this session
  - resource: https://atproto.com/specs/did
    title: Hostname-only did:web, first claimed handle and PDS service interpretation; read 2026-10-28
  - resource: https://atproto.com/specs/permission
    title: Named and positional collection scopes, percent encoding and action arrays; read 2026-10-28
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/http/DpopHttp.kt
    title: Fixed bounded DPoP exchange and role-scoped nonce behavior
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/http/JsonFetch.kt
    title: Fixed streaming discovery reads
  - resource: https://repo.maven.apache.org/maven2/io/ktor/ktor-client-core-jvm/3.6.0/ktor-client-core-jvm-3.6.0-sources.jar
    title: Public baseline source confirms request eager buffering and HttpStatement streaming/cleanup behavior
---

# Reusable protocol transport hardening for the Pano remake

**Adopt the small transport delta, not this entire old application branch.** The
parent selected one Pano-owned Kotlin implementation after worker A's published
library qualification. That choice is not reopened here. A owns native storage,
cross-process account authority, pending/session composition, platform config and
UI. This lane changed none of those concerns and did not inspect A's uncommitted
work.

## References and ownership

- Session: `ses_e7b3ccec5ffeuJpOmxDoerRcX6`; parent:
  `ses_e7ba46f7fffeze8ND55fl0uJF8`.
- Workspace: `/home/rektide/src/pano-atproto-protocol`, created with `jw` from
  **old self-contained local** checkpoint
  `eeb47616eb9e8e33b03cc0cc5294b4baba77dace`, before the unpublished fork.
- Reusable code bookmark: `atproto-protocol-hardened-20261028` →
  **`f8d7b93edab8553f667b7d6ddf08c18b5f2f33d0`**.
- Separate evidence/knowledge bookmark: `atproto-protocol-notes-20261028`.
- Work anchor: `pano-atproto-remake-protocol`; remaining work is explicit rather
  than claiming all protocol/crypto/identity qualification complete.
- Accepted metadata workspace and both metadata bookmarks were left intact.

Inspect only the owned delta:

```sh
jj diff --git --from eeb47616eb9e8e33b03cc0cc5294b4baba77dace \
  --to atproto-protocol-hardened-20261028
```

Do not replay the historical app commits or copy these evidence documents into
the concise feature PR.

## Original → fixed mapping

All source paths below are relative to
`atproto/src/main/kotlin/com/arn/scrobble/atproto/`.

| Primitive | Original behavior | Fixed behavior / carry instruction |
|---|---|---|
| `http/JsonFetch.kt` | `get` eagerly saves the entire body; size checked only after `bodyAsText`. Final-URL check happens after redirects. | Uses `prepareGet(...).execute { }`, checks status/type inside the streaming scope, reads at most limit+1 bytes, strict UTF-8. Requires safe client policy before any network call. |
| New `http/ResponseBody.kt` | No reusable pre-buffer limit or policy guard. | Shared bounded streaming reader and fail-closed `requireProtocolTransport`. Caller must use a dedicated client with redirects and generic automatic retries disabled. |
| `http/DpopHttp.kt` | Unbounded body; missing/empty nonce accepted; same-origin AS/RS nonce conflation; repeated nonce value disables retry. | 64 KiB default response cap, configurable by constructor; one valid nonce required/observed on every response; `(role, origin)` nonce keys; one fresh-proof retry even if nonce string is unchanged. |
| New `http/AuthenticationChallenge.kt` | `startsWith("DPoP")` plus case-insensitive substring search can confuse schemes or error text. | Parses challenge boundaries and quoted/token parameters, including multiple challenges/header lines. Scheme/parameter names are case-insensitive; error values are exact and duplicates are rejected. |

The existing proof factory still supplies a new proof on each actual attempt.
There is no new protocol library or runtime dependency. The additions use Pano's
existing public Ktor 3.6.0 and Kotlin libraries; no Java Base64/API-26 dependency
was introduced. This is source/JVM evidence, not a new API-24 device run.

The four files are a delta, not a standalone replacement module. They expect the
compatible `HttpUrl`, `DpopProofFactory`, epoch/random-token, encoding and JCA key
utilities from the old pure layer (or their reviewed successors). The malformed
key/PKCE findings below must still be addressed. No production edit to
`AtProtoOAuthClient`, `AtProtoSessionClient`, `OAuthModels`, `AtProtoPoster` or
`AtProtoRepository` is part of this code commit.

### Nonce policy: normative versus local defenses

- RFC 9449 **AS nonce challenge**: HTTP **400**, JSON `error: use_dpop_nonce`.
- RFC 9449 **resource nonce challenge**: HTTP **401**, a **DPoP**
  `WWW-Authenticate` challenge with exact `error=use_dpop_nonce`.
- A nonce header on an ordinary 400/401 does **not** authorize replay. Successful
  responses and non-challenge failures can update the next nonce without retry.
- AT Protocol additionally requires a `DPoP-Nonce` response header for DPoP
  requests. Missing, duplicate or invalid nonce headers fail rather than silently
  discarding the requirement. Nonce syntax follows RFC 9449/RFC 6749 NQCHAR.
- One retry, the 64 KiB DPoP body cap, 16 MiB maximum configurable reader limit
  and 4096-character nonce ceiling are bounded client defenses, **not invented
  protocol-wide maximums**. The body limit is applied while reading, not after an
  unbounded download.

Do not replace these distinctions with a generic "retry all 400/401" rule. The
skill summary's shorthand is not the authoritative RFC classification.

## Checks actually run

At code commit `f8d7b93e`:

```sh
bash gradlew :atproto:test --console=plain --no-configuration-cache
```

**55 tests, zero failures/errors/skips** (XML counts). This includes nine new
transport regressions and two parser tests, with parser subcases for multiple
schemes, quoting, parameter case, exact error values and ambiguous input.
The pre-change self-contained module test run also passed before modifications.

The transport regressions prove:

1. Unsafe redirect-capable clients are rejected before sending a request.
2. Oversized streaming discovery responses fail without waiting for EOF.
3. Invalid UTF-8 is not silently replaced.
4. DPoP responses require one valid nonce, including success.
5. Same nonce values still allow one fresh-proof retry.
6. Valid multi-challenge/token-parameter syntax is recognized.
7. Lookalike schemes/error text do not trigger write retries.
8. AS and resource nonce contexts remain separate at a shared origin.

Original-failure fixture: `jw` created a separate workspace at
`/tmp/opencode/pano-protocol-baseline`, based on the exact old checkpoint with only
the regression test copied in. **All nine transport tests fail there.** This is a
fresh source build, not a claim based on stale class files. An earlier attempted
`-x compileKotlin` shortcut failed Gradle's friend-path configuration and supplies
no verification evidence. The streaming test uses a real dispatcher for its
deadline, avoiding a virtual-time/MockEngine scheduling false positive.

Five additional **evidence-only** probes of old behavior are preserved in
[protocol-migration-probes0.gpt61s.kt](protocol-migration-probes0.gpt61s.kt). They
assert the undesirable observations to document them; they are **not passing
conformance tests** and must not be adopted as such. Copying that file to the
baseline module's test source allows targeted reproduction:

```sh
bash gradlew :atproto:test \
  --tests 'com.arn.scrobble.atproto.oauth.ProtocolMigrationProbeTest' \
  --console=plain --no-configuration-cache
```

The combined fresh-baseline run completed **14 tests: nine failing transport
conformance regressions and five passing observation probes**. The failing exit
code belongs to that deliberately unmodified baseline, not the fixed module.

Fixtures/source JARs and scratch notes:
`/home/rektide/src/pano-atproto-protocol/.test-agent/protocol-audit/`.
Ignored local SDK/build properties were privately copied, never printed or
committed. No Android assembly, live authorization, PDS write or deployment was
performed. No push, squash, `jj edit` or PR creation occurred.

## Obligations for the fresh native/OAuth successor

### Required transport integration

1. Construct **one dedicated native protocol client** with `followRedirects=false`
   and no `HttpRequestRetry` plugin. Keep the native timeout/proxy/TLS policy;
   do not pass Pano's cached Last.fm/error-validating generic client. The utility
   fails closed if redirect/retry plugins are present. Do not remove that guard
   just to reuse the old construction seam.
2. Keep `expectSuccess=false` and allow protocol code to inspect 400/401 bodies.
   A custom global response validator can still interfere; this utility does not
   make arbitrary clients safe. Similarly, do not install a logging/auth plugin
   that exposes or injects unrelated credentials.
3. Adapt nonce state to A's authority/vault boundary. The retained
   `DpopNonceStore` is an in-memory convenience in the old composition, **not**
   durable native lifecycle coordination. Persist by client/account plus role and
   origin as appropriate; recover PAR nonce state after process death.
4. Serialize authenticated request/refresh/logout through that same authority.
   These utilities preserve cancellation; they do not own a non-cancellable
   rotating-response/persistence window or know whether a failed write consumed a
   refresh token. A still owns that boundary.
5. Add successor-level tests for nonce persistence, cancellation, missing nonce
   during rotation, automatic-retry rejection and actual outgoing body limits.
   Do not retry ambiguous PDS writes merely because networking failed.

The four existing lifecycle/repository/discovery test fixtures only changed their
HTTP-client redirect setting. The three older DPoP tests additionally gained
nonce headers on successful mock responses. Those fixture changes are not a
redesign of the old lifecycle.

### Remaining validation findings

These were not silently folded into a second lifecycle implementation:

| Finding | Evidence and exact successor obligation |
|---|---|
| Callback duplicate/fragment ambiguity | Evidence probe shows `OAuthCallback.fromUrl` accepts duplicate query parameters, merges fragments and lets fragments override query. Do **not** carry that parser unchanged. Validate exact native single-slash scheme/authority/path, reject fragments and duplicate decoded parameters, require unambiguous issuer/state and code-or-denial before exchange. |
| Token expiry arithmetic | Evidence probe shows positive `expires_in=Long.MAX_VALUE` wraps into an already expired token. Bound/check multiplication and addition. An omitted `expires_in` is optional OAuth metadata, not permission to invent an infinite usable lifetime; select an explicit conservative strategy. Verify exact expected subject, DPoP type and actual returned scopes in the successor. |
| Invalid imported JWK material | Evidence probe shows constructors accept `(x,y)=(0,0)` and `d=0` solely because widths match. Validate canonical encoding, field/curve membership and scalar range; establish private/public correspondence before using imported state. Valid generation/proof tests do not qualify malformed imports or Android providers. |
| Invalid PKCE alphabet | Evidence probe shows non-ASCII and `?` verifiers hash identically after lossy US-ASCII replacement. Require RFC 7636 unreserved alphabet as well as length. Generated tokens are already URL-safe; malformed imported pending state is the unqualified boundary. |
| Target-grant grammar | Evidence probe shows valid named/percent-encoded collection/action grants are missed, while an invalid action alongside `create` is accepted. Parse current permission syntax deliberately: positional or named collections, encoded values, repeated unique allowed actions, no mixed positional/named parameter, whole-collection wildcards only. Intersect granted and selected targets. |
| DID method policy | Current normative AT Protocol DID spec supports **two** methods and hostname-only `did:web`; old `IdentityResolverTest` explicitly accepts a path-based DID. Do not copy that policy or the skill's contrary three-method/path guidance. No new webvh verifier was commissioned. |
| Canonical handle binding | Current DID spec uses the **first syntactically valid** claimed handle, ignoring later aliases even if the first fails back-verification. Old resolver checks any alias and loops until one verifies. Add first-claim and case-normalization fixtures; delegated `resolveHandle` remains allowed, so no new DNS stack is required by this finding. |
| Mapping/request bounds | Seconds versus milliseconds and existing UTF-8 fixtures were inspected, not freshly qualified against current Teal/Rocksky lexicons. Audit uncapped optional URIs, malformed Unicode, large-input truncation cost, integer/date bounds and complete outgoing JSON limits. Do not present this transport slice as record-format qualification. |

Additional limits: discovery document duplicate-key/depth behavior, address-level
SSRF/DNS-rebinding policy, redirect-free engine behavior, URL canonicalization and
the full AS/RS capability/subject/grant matrix still need intentional successor
qualification. Existing AS endpoint validation permits different HTTPS endpoint
origins; do not impose a same-origin rule by fiat when OAuth metadata allows
distinct endpoints. Bind the discovered issuer/PDS/account chain exactly instead.

For native callback/manifest and production/development identity review, retain
the separate [metadata handoff](file:///home/rektide/src/pano-oauth-pages-pr/design/atproto-posting/metadata-handoff0.gpt61s.md).
Changing the native client identity still requires fresh authorization; this
hardening slice neither deploys that identity nor migrates an existing grant.
