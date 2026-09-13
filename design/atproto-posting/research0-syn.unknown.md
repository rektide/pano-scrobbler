---
type: Design research
title: AT Protocol posting foundation
description: Evidence and implementation direction for isolated AT Protocol OAuth and scrobble posting in Pano Scrobbler.
tags: [atproto, oauth, scrobbling, kotlin]
status: draft
generated: { by: model:unknown, at: 2026-09-13 }
sources:
  - id: pano-provider-interface
    resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/Scrobblable.kt
    title: Pano Scrobbler provider interface and registry
  - id: web-scrobbler-atproto
    resource: https://github.com/rektide/web-scrobbler/tree/0a26101baa9f30d680939dc672db913a9b8b182a/src/core/scrobbler/atproto
    title: Web Scrobbler AT Protocol implementation
  - id: web-scrobbler-oauth-site
    resource: https://github.com/rektide/rektide.github.io/tree/55702c90dc6b94409e30632712662e3e5816259e/src/routes/oauth
    title: Web Scrobbler OAuth relay implementation
  - id: teal-lexicon
    resource: https://lexicon.garden/fm.teal.feed.play
    title: Published Teal play lexicon
  - id: rocksky-lexicon
    resource: https://lexicon.garden/app.rocksky.scrobble
    title: Published Rocksky scrobble lexicon
---

# AT Protocol posting foundation

> The harness does not expose the synthesizing model's precise identity, hence the
> `unknown` filename suffix. Two GLM 5.3 Max investigations and one GLM Flash
> repository sweep supplied the findings reconciled here.

## Goal

Port Web Scrobbler's AT Protocol posting into Pano Scrobbler without an AT
Protocol SDK. Build only the protocol behavior this app needs, keep it isolated
from Compose and provider models, and land it from foundations upward before
wiring the GUI.

## Established facts

### Record protocols

Web Scrobbler has migrated from `fm.teal.alpha.feed.play` to the stable
`fm.teal.feed.play` collection. It currently writes both:

| Target | Collection | Time field | Duration unit |
|---|---|---|---|
| teal.fm | `fm.teal.feed.play` | `playedTime` | seconds |
| Rocksky | `app.rocksky.scrobble` | `createdAt` | milliseconds |

Both collections use TID record keys. Web Scrobbler omits `rkey`, allowing the
PDS to mint one. It writes only completed scrobbles; now-playing events are
no-ops. The two targets are independently toggleable and both default on.

The published Teal lexicon currently trails the Teal source repository for the
optional `station` field. Pano does not presently expose equivalent station
metadata, so its first implementation should omit that disputed extension.

### Repository write

The required resource operation is one authenticated request:

```http
POST {pds}/xrpc/com.atproto.repo.createRecord
Authorization: DPoP {access-token}
DPoP: {proof-jwt-with-ath}
Content-Type: application/json

{
  "repo": "did:plc:...",
  "collection": "fm.teal.feed.play",
  "record": { "$type": "fm.teal.feed.play", "...": "..." }
}
```

No `rkey`, `swapCommit`, or `validate` field is needed. The useful response is
`{uri, cid}`.

### OAuth behavior that must be local

The implementation needs:

1. Handle resolution, followed by `did:plc` or `did:web` document resolution.
2. Extraction and validation of the DID document's `#atproto_pds` service.
3. PDS protected-resource metadata discovery and exactly one authorization
   server.
4. Authorization-server metadata validation, including issuer equality, PAR,
   token endpoint, and client metadata document support.
5. A public OAuth client metadata URL (`client_id`) and redirect URI supplied as
   configuration rather than fixed constants.
6. PKCE S256, a fresh P-256 DPoP key, single-use state, and a PAR request.
7. Authorization callback checks for `state`, `code`, and issuer before token
   exchange.
8. DPoP-bound authorization-code exchange and identity revalidation.
9. Persistent access token, rotating refresh token, DPoP private key, PDS, DID,
   authorization-server endpoints, scope, and expiry.
10. Refresh within a 60-second expiry margin, serialized per DID.
11. Per-origin DPoP nonce caching and exactly one retry for
    `use_dpop_nonce`; exactly one refresh-and-retry for `invalid_token`.
12. Best-effort token revocation on logout.

The Web Scrobbler extension's hosted-page `postMessage` relay is an MV3
workaround and should not be ported. Its current raw-session handoff also fails
to seed atcute's session database, so refresh after token expiry is broken. Pano
must persist every refreshed session atomically through an app-owned session
store.

### DPoP details

- ES256 only: P-256 key and SHA-256.
- JWT header: `typ=dpop+jwt`, `alg=ES256`, public JWK only.
- JWT payload: `htm`, normalized `htu` (origin and path, no query or fragment),
  `iat` in epoch seconds, unique `jti`, optional `nonce`, and `ath` for resource
  requests carrying an access token.
- `ath` is unpadded base64url SHA-256 of the access token.
- Java `SHA256withECDSA` generally returns DER; JOSE requires a 64-byte
  `r || s` signature. Conversion needs direct tests.

## Destination seam

Pano has no existing AT Protocol, Bluesky, Teal, Rocksky, PKCE, PAR, or DPoP
implementation. [`Scrobblable`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/Scrobblable.kt)
is the correct app-level seam, and the Pleroma adapter is the closest post-only
provider precedent. It should not become the protocol implementation.

Create a standalone `:atproto` JVM module. Both shipped targets run on the JVM,
and `composeApp` already consumes JVM-only project modules from `commonMain`.
The module can therefore use JCA for crypto while exposing no Compose or Pano
types. It should accept an injected Ktor `HttpClient` so the app retains its
proxy, TLS, user-agent, and transport policy.

The external interface should eventually make a few complete operations easy:

- begin authorization for an account identifier;
- complete authorization from callback parameters;
- create one or more typed play records with a persisted session;
- revoke a session.

Discovery, PKCE, DPoP, nonce retries, refresh rotation, and wire validation are
implementation details behind that interface. Pure protocol types and crypto
helpers may remain public only where callers genuinely need to persist or map
data.

## Commit stack

Each item is intended to compile and be reviewable independently.

1. Record this research and the architectural seam.
2. Add the isolated `:atproto` module and its empty test surface.
3. Add encoding, randomness, URI normalization, PKCE, and protocol data types
   with deterministic tests.
4. Add P-256 JWK and ES256/JWS primitives, including DER-to-P1363 conversion and
   signature verification tests.
5. Add DPoP proof generation, nonce challenge parsing, and retry tests.
6. Add handle/DID/PDS/authorization-server discovery with strict metadata
   validation and mocked HTTP fixtures.
7. Add begin/complete/refresh/revoke OAuth operations and serializable sessions.
8. Add `com.atproto.repo.createRecord` with invalid-token refresh and DPoP nonce
   handling.
9. Add Teal and Rocksky record models and app-independent validation.
10. Add the thin Pano mapping/provider adapter and pending-scrobble behavior.
11. Add session storage, metadata/redirect configuration, and login callback
    adapters for Android and desktop.
12. Add settings controls for independently enabling Teal and Rocksky, followed
    by end-to-end GUI and native-image smoke checks.

## Decisions retained for later slices

- **Redirect adapter:** hosted HTTPS callback, Android private-use scheme, and
  desktop loopback each have different operational tradeoffs. The protocol
  module should accept exact client metadata and redirect URIs without choosing
  a platform policy.
- **Session location:** avoid packing session JSON into `authKey` merely to dodge
  schema work. Choose an app-owned store once the protocol session shape is
  stable.
- **Provider ID:** enum value `5` is vacant but visibly reserved for dormant
  Maloja support; use a new stable ID such as `10` unless project history says
  otherwise.
- **Batch semantics:** if one lexicon write succeeds and the other fails, a
  whole-scrobble retry can duplicate the successful record. The provider slice
  needs an explicit partial-success/idempotency policy rather than blindly
  copying Web Scrobbler's behavior.

## Cross-references

- [`Scrobblable.kt`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/Scrobblable.kt)
  defines the existing provider seam, account IDs, cache, and factory that the
  eventual adapter must satisfy.
- [`UserCached.kt`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/UserCached.kt)
  shows why AT Protocol session persistence should not be reduced to the
  existing single `authKey` string without an explicit decision.
- [Web Scrobbler field mapping](https://github.com/rektide/web-scrobbler/blob/0a26101baa9f30d680939dc672db913a9b8b182a/src/core/scrobbler/atproto/field-mapping.ts)
  is the behavioral source for Teal/Rocksky mappings and their duration-unit
  difference.
- [Web Scrobbler OAuth metadata](https://github.com/rektide/rektide.github.io/blob/55702c90dc6b94409e30632712662e3e5816259e/public/oauth-client-metadata.json)
  supplies the current permission-scoped client configuration to generalize.
