---
type: Implementation handoff
title: Portable protocol validation for the native Pano remake
description: Carryable key, PKCE, grant, identity, discovery and record-bound fixes with original-failure proofs, adoption instructions and the native lifecycle boundary.
resource: /design/atproto-posting/protocol-handoff1.gpt61s.md
status: implementation-handoff
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-protocol
sources:
  - resource: "opencode://ses_e7b181b8effef7JO12Vhel0XwH"
    title: B2 implements portable validation after transport turnover, verifies current specifications and lexicons, demonstrates old failures and preserves native integration obligations
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent commissions the bounded reusable lane alongside A2 native OAuth and lifecycle work
  - resource: /design/atproto-posting/protocol-handoff0.gpt61s.md
    title: Preserved predecessor transport checkpoint, original-failure proofs and native integration boundary
  - resource: /design/atproto-posting/protocol-migration-probes0.gpt61s.kt
    title: Evidence-only old callback, expiry, imported-key, PKCE and grant defects; not conformance tests
  - resource: https://atproto.com/specs/permission
    title: Current positional and named repo permissions, encoded values, actions and whole-collection wildcard rules, read directly
  - resource: https://atproto.com/specs/nsid
    title: Current NSID authority and name syntax, read directly
  - resource: https://atproto.com/specs/did
    title: Two supported DID methods, hostname-only did:web, first claimed handle and PDS interpretation, read directly
  - resource: https://atproto.com/specs/handle
    title: ASCII syntax, normalization, reserved domains and allowed delegated resolution, read directly
  - resource: https://atproto.com/specs/oauth
    title: Exact identity and issuer binding, resource metadata and required server capability matrix, read directly
  - resource: https://atproto.com/specs/lexicon
    title: UTF-8 byte limits, URI 8 KiB bound and four-digit datetime syntax, read directly
  - resource: https://atproto.com/specs/data-model
    title: Signed 64-bit integers and the separate 53-bit JavaScript interoperability recommendation, read directly
  - resource: https://raw.githubusercontent.com/teal-fm/teal/bb80aca16e15894059e4f7978ef085876ce3d2b5/lexicons/fm.teal/feed/play.json
    title: Public Teal HEAD play contract, seconds, URI fields and field byte limits; fetched after checking local archives
  - resource: https://raw.githubusercontent.com/teal-fm/teal/bb80aca16e15894059e4f7978ef085876ce3d2b5/lexicons/fm.teal/feed/defs.json
    title: Public Teal HEAD artist definition and play-view fields, fetched and read
  - resource: https://tangled.org/rocksky.app/rocksky/blob/850216da9b21d2fb245591a207527efe4262dd53/apps/api/lexicons/scrobble/scrobble.json
    title: Public Rocksky HEAD fetched into the existing archive and read with git show; current duration is milliseconds and artists is supported
  - resource: "file:///home/rektide/archive/rocksky.app/rocksky/apps/api/lexicons/artist/defs.json"
    title: Local Rocksky artistMbid object shape and name limit
  - resource: "file:///home/rektide/archive/tsiry-sandratraina.com/rocksky-lexicons/lexicons/scrobble/scrobble.json"
    title: Older separate schema archive whose seconds description must not override current Rocksky monorepo source
---

# Portable protocol validation for the native Pano remake

**Adopt the portable deltas, not this old application branch.** B2 completes the
bounded validation lane after the [transport handoff](protocol-handoff0.gpt61s.md).
A2 owns the new native OAuth lifecycle, authority/vault composition, repository
writer, runtime and UI. Those files and its uncommitted work were not inspected or
modified. The single Pano-owned Kotlin path and existing public dependencies remain
the selected architecture; published-library qualification was not repeated.

## Checkpoints and tickets

- Workspace: `/home/rektide/src/pano-atproto-protocol`.
- Code bookmark: `atproto-protocol-hardened-20261028` →
  **`1a0c119e372af16298b004d81aebf21e657b3015`**.
- Preserved predecessor code: `f8d7b93edab8553f667b7d6ddf08c18b5f2f33d0`.
- Separate notes bookmark: `atproto-protocol-notes-20261028`; it retains
  predecessor notes `508ceaffc763b8d107152649c39eed0ecd09954a` and this successor
  account. Code ancestry does not contain these evidence documents.
- Main work anchor: `pano-atproto-remake-protocol`; bounded capability children:
  `pano-atproto-remake-protocol-inputs`, `pano-atproto-remake-protocol-identity`,
  `pano-atproto-remake-protocol-records`.
- B2 session: `ses_e7b181b8effef7JO12Vhel0XwH`; parent:
  `ses_e7ba46f7fffeze8ND55fl0uJF8`.

| Commit | Coherent change |
|---|---|
| `ff832a51` | Imported P-256, strict PKCE, current repo grants and selected/granted intersection |
| `a1c5e7d3` | Identity semantics, strict discovery JSON/URLs, issuer/capability checks and encoded DPoP paths |
| `5bd56892` | Separate new helper tests from original-compatible identity regressions |
| `e47b8b8b` | Bounded record input, Unicode, URI, datetime/integer and conversion-cost checks |
| `1a0c119e` | Accept standard DID JSON media type alongside plain JSON and DID JSON-LD |

Inspect B2 without replaying the old app:

```sh
jj diff --git --from f8d7b93edab8553f667b7d6ddf08c18b5f2f33d0 \
  --to atproto-protocol-hardened-20261028
```

The complete transport-plus-validation delta starts at old self-contained baseline
`eeb47616eb9e8e33b03cc0cc5294b4baba77dace`. Do not import the historical application
or obsolete lifecycle constructors merely to obtain these utilities.

## Adoption map and property proofs

Paths below are under `atproto/src/main/kotlin/com/arn/scrobble/atproto/`.

| Files | Established property and carry instruction |
|---|---|
| `crypto/P256Jwk.kt` | Every constructor/copy/deserialization checks canonical unpadded 32-byte components, field range and finite point-on-curve; private scalar must be `1 <= d < n`. P-256 cofactor is one. JCA sign/verify establishes imported private/public correspondence without implementing secret-dependent scalar multiplication. Provider availability is not hidden by a catch-and-continue path. |
| `oauth/Pkce.kt` | Imported verifier must be 43–128 unreserved ASCII characters before US-ASCII hashing. Generation remains unchanged. |
| `posting/RepoPermission.kt`, **only `AtProtoTarget` in `posting/AtProtoPoster.kt`** | Positional or repeated named collections; percent-decoded names/values; unique valid `create/update/delete` actions; no action means all. Unknown parameters, invalid collections/actions, mixed positional/named collection, malformed encoding and partial wildcards invalidate that entire token. Only bare `*` is a wildcard. `grantedBy(scope, selectedTargets)` requires `atproto` and intersects actual grants with selection, including `transition:generic`. No desired-target fallback. Do not copy the obsolete poster constructor into A2's new composition. |
| `identity/Handle.kt`, `identity/DidDocument.kt`, `identity/IdentityResolver.kt` | UI normalization is separate from protocol-claim parsing. ASCII is validated before lowercase; claims cannot acquire trimming or `@`-prefix tolerance. Only the first syntactically valid `at://hostname` claim is considered, even when unresolvable/reserved. A later alias cannot authenticate the requested handle. Production resolution rejects reserved TLDs, path/port/encoded-authority did:web and unsupported methods before network access. No localhost-development mode was added. Document DID must match exactly; first matching current-account PDS entry is authoritative. |
| `http/DiscoveryJson.kt`, `http/JsonFetch.kt` | Streaming body limits and strict UTF-8 remain. Preflight rejects duplicate decoded object keys, including escaped spellings and nested unknown objects, before the normal parser can erase them. Nesting is capped at 32; result must be one JSON object. This is a small bounded structural preflight, not a replacement general JSON parser. |
| `http/HttpUrl.kt`, `oauth/Discovery.kt` | Exact PDS resource and declared AS issuer binding, one AS, mandatory capability matrix, canonical origin syntax with no explicit default port. Endpoint URLs may have distinct HTTPS origins, optional nondefault ports and queries; no invented same-origin constraint. Reject credentials, fragments and ambiguous/out-of-range ports. DPoP target path encodes literal Unicode consistently with the outgoing Ktor URL. |
| `posting/Play.kt`, `posting/RecordText.kt`, `posting/Records.kt` | Valid Unicode scalar strings before serialization; bounded raw metadata; one forward code-point walk for byte truncation rather than quadratic repeated encoding. URI byte limit, four-digit-year timestamp range and safely interoperable positive integer durations. Existing field mapping, fallbacks, single supplied artist string and duration units remain unchanged. |

All corresponding tests are in the same package paths under `atproto/src/test/`.
Carry the final `JsonFetch.kt`/`HttpUrl.kt` versions, not the predecessor copies over
these further validations. Adapt discovery metadata field checks to A2's typed
models rather than replacing A2's lifecycle/state composition.

An old thumbprint test used an arbitrary off-curve coordinate pair. It is replaced
by the known P-256 generator and an independently computed RFC 7638 canonical
thumbprint. That fixture correction is not evidence against the new curve check.

### Current record contracts and deliberate limits

Local archives were inspected first. Public Teal HEAD `bb80aca1` was then fetched
by immutable raw URLs. Public Rocksky HEAD `850216da` was fetched into the existing
archive and read with `git show`, without changing its checkout. Current Rocksky
declares **milliseconds**, supports the emitted `artists` reference, and retains
the existing required fields. The separate older `rocksky-lexicons` archive at
`56fdffca` describes seconds; it is not the current monorepo contract.

- Teal: track/artist/release/client-agent limits 256 UTF-8 bytes; duration seconds.
- Rocksky: title 512 bytes; artist/album/albumArtist/tag/name 256 bytes; duration
  milliseconds, minimum one. Existing missing album/duration fallbacks remain.
- Generic Lexicon URI limit: **8192 UTF-8 bytes**, checked without truncating a URI.
- Datetimes: positive epoch milliseconds through `253402300799999`, the end of
  year 9999. Extended signed years emitted for arbitrary `Long` are not valid here.
- Duration ceiling `9007199254740991`: a **local interoperability policy** following
  the data model's JS precision recommendation, not a claim that protocol integers
  are only 53-bit. Protocol integer representation is signed 64-bit.
- Raw text ceiling: **16 Ki UTF-16 code units per metadata value**, checked before
  scanning/copying. A local resource defense, not a Lexicon-defined maximum.
- Output truncation preserves code points, not whole grapheme clusters. Byte
  bounds also keep these outputs below their much larger Teal grapheme ceilings.

## Checks actually run

At final code `1a0c119e`:

```sh
bash gradlew :atproto:test --console=plain --no-configuration-cache
```

**86 tests, zero failures/errors/skips**, from XML counts. All code uses existing
public dependencies and API-24-compatible standard operations; no new dependency
or Java Base64 runtime dependency was added. This is JVM source/provider evidence,
not an Android-device, Android assembly or native-image provider claim.

Original-failure work used the preserved `/tmp/opencode/pano-protocol-baseline` at
exact old `eeb47616`, adding only new test files. No production fix was copied in:

- Imported-key, discovery JSON, discovery matrix/URL and record-bound suites:
  **16 tests, 11 expected failures, 5 passes**. Passing cases are positive controls
  and already-enforced rules, not claims that every new test should fail old code.
- Original-compatible identity regressions: **6 tests, all 6 fail**. The final
  standard-DID-MIME positive test was added afterward and was not run on baseline.
- The predecessor's nine transport failures and five evidence-only old-behavior
  probes remain preserved; they were not gratuitously rerun.

XML evidence: `.test-agent/protocol-audit/b2-original-*.xml` and
`b2-fixed-*.xml`. Baseline failures are intentional and separate from the passing
fixed module. Targeted baseline runs replace Gradle's result directory, so the
archived XMLs, not the last directory contents alone, hold the combined evidence.

## Native integration obligations: A2, not a second implementation here

Retain the predecessor's dedicated no-redirect/no-automatic-retry transport,
required nonce on every DPoP response, role/origin/account nonce separation,
durable pending/session authority, cancellation and rotation persistence duties.
Pure utility tests cannot establish those native lifecycle properties.

### Exact callback/token negatives to invert

Base callback: `io.github.kawaiidango:/pano-scrobbler/oauth/callback`.
These are expected **rejections before any token exchange**, not migrated old
acceptance tests:

1. `?state=first&state=second&code=c&iss=https%3A%2F%2Fauth.example.com`.
2. Duplicate decoded spelling: `?state=s&sta%74e=t&code=c&iss=...`.
3. Query followed by `#state=fragment-state&code=fragment-code`; any fragment,
   including an empty fragment, must not override or supplement the query.
4. Wrong native scheme/authority/path, `://` instead of the declared single slash,
   or extra path components; missing/ambiguous state/issuer; both code and error.
5. A denial callback is classified without exchange; it must not be transformed
   into an apparently successful code callback by duplicate/fragment handling.

The preserved expiry witness is `expires_in=Long.MAX_VALUE` at clock `1000`:
old conversion wraps into the past. New token **and PAR** conversion needs checked
multiplication/addition and positive bounded lifetimes. Missing `expires_in` needs
an explicit conservative strategy, not an invented indefinitely usable token.
Also verify exact expected subject, DPoP token type and the returned `atproto`
scope; use actual granted targets, never desired targets when scope is absent.

### Complete request-body bound and repository response

Field limits are not the final HTTP request limit. Before sending a write, the new
repository writer must serialize the **complete** `createRecord` request, including
`repo`, `collection`, `record` and serializer escaping, then enforce a UTF-8 byte
ceiling. **64 KiB** is a proportional local ceiling for these two small mappings,
not an AT Protocol-wide maximum. Record tests exercise multibyte/control-character
escaping and show emitted records below 32 KiB; they do not falsely claim to test
A2's complete outgoing request. Add a request-level oversized-wrapper/record test
that proves no network request occurs.

For resource `invalid_token`, use exact challenge parsing (the reusable
`Headers.hasDpopError("invalid_token")` with status 401), not the old repository's
scheme-prefix/substr check. Do not retry ambiguous PDS writes on arbitrary I/O
failure. Validate the returned record URI against account/collection and parse
the CID as the successor writer requires; these old response wrappers were not
qualified or carried as a new repository implementation.

### Qualification boundaries, not speculative blockers

No DNS/address-level SSRF framework, DNS-rebinding subsystem, sign-key/repository
signature verifier or new did method was commissioned. The native client still
needs the platform's normal TLS, timeout and proxy policy. JCA checks must be
exercised on supported native providers before claiming provider coverage. These
are honest boundaries, not claims that every untested platform hypothetical is a
feature blocker. No live authorization, real-account write, deployment, push or PR
creation occurred. Official metadata publication remains the independent gate
identified in the predecessor's metadata handoff.

## Concise contribution outline

For the parent's clean feature PR, describe one native Pano protocol path and the
user-visible Teal/Rocksky behavior from A2's tested implementation. Keep this old
application branch and research corpus out of the feature diff. The portable
portion can be summarized as:

1. Bounded dedicated discovery/DPoP transport with exact nonce/challenge behavior.
2. Strict imported keys, PKCE and actual granular grants.
3. Current identity semantics and exact discovery bindings without a new DNS stack.
4. Pure bounded Unicode-safe record mappings preserving verified units.
5. Deterministic mock/JVM regressions, clearly separated from native/runtime and
   undeployed-metadata checks; no claims of live-account verification.

These are implementation and evidence outcomes, not acceptance of the final app
or authorization to publish it.
