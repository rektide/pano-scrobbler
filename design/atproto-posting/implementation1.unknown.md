---
type: Implementation report
title: AT Protocol posting implementation
description: Implemented OAuth, DPoP, record posting, Pano provider integration, and remaining verification gates.
tags: [atproto, oauth, scrobbling, kotlin]
status: draft
generated: { by: model:unknown, at: 2026-09-13 }
sources:
  - id: research
    resource: /design/atproto-posting/research0-syn.unknown.md
    title: AT Protocol posting foundation research
  - id: oauth-spec
    resource: https://atproto.com/specs/oauth
    title: AT Protocol OAuth specification
  - id: teal-lexicon
    resource: https://lexicon.garden/nsid/fm.teal.feed.play
    title: Published Teal play lexicon
  - id: rocksky-lexicon
    resource: https://lexicon.garden/nsid/app.rocksky.scrobble
    title: Published Rocksky scrobble lexicon
---

# AT Protocol posting implementation

> The harness does not expose the synthesizing model's precise identity, hence
> the `unknown` filename suffix.

## Outcome

Pano Scrobbler now has a standalone `:atproto` module and a thin Pano provider
adapter. No AT Protocol SDK is used. Existing Ktor, coroutines, Kotlin
serialization, and Java cryptography facilities supply the generic runtime.

The implementation covers:

- handle and `did:plc` / `did:web` resolution;
- DID-to-PDS and PDS-to-authorization-server discovery with strict metadata
  validation;
- PKCE S256 and single-use persistent authorization state;
- P-256 JWK generation, RFC 7638 thumbprints, ES256 signing, and DER-to-JOSE
  signature conversion;
- DPoP proofs, access-token hashes, per-origin nonces, and one nonce retry;
- form-encoded PAR, token, refresh, and revocation requests;
- callback state and issuer checks, token-subject verification, and defensive
  revocation after failed completion;
- persistent rotating sessions and serialized in-process refreshes;
- authenticated `com.atproto.repo.createRecord`, including one refresh and
  retry after `invalid_token`;
- stable `fm.teal.feed.play` and `app.rocksky.scrobble` records;
- Android native callbacks and a desktop callback-URL paste fallback;
- independent Teal and Rocksky controls constrained by the scopes actually
  granted by the user.

## Record behavior

Completed scrobbles are mapped to both targets by default. No now-playing
record is emitted.

- Teal receives duration in seconds and `playedTime` from Pano's playback-start
  timestamp.
- Rocksky receives duration in milliseconds and `createdAt` from the same
  timestamp.
- Record strings are UTF-8-byte-truncated to published lexicon limits without
  splitting Unicode code points.
- Rocksky's required album and duration fields receive schema-valid fallbacks
  when Pano has no source value.
- The PDS chooses the TID record key, matching Web Scrobbler behavior.

When one target succeeds and the other fails, the provider persists the
successful target against a hash of the pending scrobble. Pano's later pending
retry skips that target, reducing duplicate dual-writes. There remains a small
crash window between the successful network response and progress persistence;
full transactional idempotency would require a record-key or server-side
strategy.

## Client identity

The default native client configuration is:

```text
client_id    https://rektide.github.io/web-scrobbler-pages/pano-scrobbler/oauth-client-metadata.json
redirect_uri io.github.rektide:/pano-scrobbler/oauth/callback
scope        atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create
```

Both URLs are overridable through `local.properties` or environment variables;
see [`instructions.md`](/instructions.md). Pending authorizations and completed
sessions retain the exact client identity that created them, so a later build
configuration change does not silently rotate an existing session to another
client.

The metadata document was added to the separate rektide GitHub Pages checkout
at `public/pano-scrobbler/oauth-client-metadata.json`. It must be deployed before
the default login can pass PAR client-metadata resolution.

## Pano integration

- `AccountType.ATPROTO` uses stable ID `10`; dormant Maloja ID `5` remains
  untouched.
- `UserAccountSerializable.authKey` contains only the account DID. OAuth
  secrets and DPoP state live in typed `MainPrefs` maps instead of a JSON blob
  in that field.
- [`AtProtoScrobblable.kt`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoScrobblable.kt)
  is the only adapter from `ScrobbleData` to the protocol module.
- Logout attempts token revocation and always removes local session state.
- TV UI hides the provider until a workable cross-device callback flow is
  designed.

## Verification

Passed:

- `:atproto:test`, covering encoding, PKCE, P-256/JWK, ES256 conversion and
  verification, DPoP claims and retries, identity/discovery, OAuth lifecycle,
  concurrent refresh, record requests, mappings, scopes, and partial results;
- `:composeApp:compileKotlinJvm`;
- XML parsing for the Android manifest and resource files;
- a live unauthenticated discovery walk for `atproto.com` through its DID,
  PDS, protected-resource metadata, and the `https://bsky.social`
  authorization server, including all 13 metadata requirements enforced by
  the module.

Outstanding gates:

- The default metadata URL currently returns HTTP 404 until the GitHub Pages
  metadata commit is deployed.
- This environment has no Android SDK, so Android compilation, manifest merge,
  and callback-device testing could not run.
- No human OAuth approval or live create-record operation was performed.
- GraalVM native-image crypto reachability remains untested.
- The existing `:composeApp:jvmTest` suite has one unrelated baseline failure:
  `MaiTest.parseYoutubeTitles` expects a trailing space that the current parser
  removes. The other application tests pass.

## Cross-references

- [Foundation research](/design/atproto-posting/research0-syn.unknown.md)
  records the source investigation, migration evidence, and original commit
  stack.
- [`atproto/`](/atproto/) is the isolated protocol implementation and test
  surface.
- [`PanoAtProtoStore.kt`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoStore.kt)
  adapts authorization state, sessions, target grants, and partial-post
  progress to Pano's DataStore.
- [`LoginScreens.kt`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/onboarding/LoginScreens.kt)
  contains the shared native/desktop login surface; Android intent conversion
  lives in [`DeepLinkUtils.kt`](/composeApp/src/androidMain/kotlin/com/arn/scrobble/navigation/DeepLinkUtils.kt).
