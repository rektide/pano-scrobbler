---
type: Roadmap
title: AT Proto posting milestone and roadmap
description: Consolidated state of local implementation, fork adoption, Pages identities, and sequenced next steps toward an upstream default.
tags: [atproto, oauth, pano, milestone, roadmap]
status: draft
generated: { by: model:unknown, at: 2026-09-15 }
stale_after: 2026-12-15
sources:
  - id: implementation
    resource: /design/atproto-posting/implementation1.unknown.md
  - id: tradeoff
    resource: /design/atproto-posting/library-tradeoff0.gpt56s.md
  - id: migration
    resource: /design/atproto-posting/upstream-migration0.gpt56s.md
  - id: field-path
    resource: /design/atproto-posting/upstream-field-path0.gpt56s.md
---

# AT Proto posting milestone and roadmap

> The harness does not expose the authoring model's precise identity, hence the
> `unknown` filename suffix. Substantial implementation work in this arc was
> executed and self-verified by a GPT-5.6 Sol subagent whose checkpoint reports
> are cited throughout.

## Where we are

Three coordinated lines of work are committed and clean:

### 1. Pano: AT Proto posting exists and works (local path, default)

`/home/rektide/src/pano-scrobbler-at`, latest `bf0cc074`.

- A standalone `:atproto` module implements native public OAuth, DPoP with
  nonce handling, refresh/revoke, and one authenticated
  `com.atproto.repo.createRecord`, with 52 passing tests.
- Completed scrobbles dual-write `fm.teal.feed.play` (Teal, seconds) and
  `app.rocksky.scrobble` (Rocksky, milliseconds), independently toggleable
  within granted scope, with partial-failure progress so pending retries skip
  already-created targets.
- Android native-scheme login plus desktop callback-paste; Teal/Rocksky
  toggles in Settings; `AccountType.ATPROTO(10)`.
- Cumulative raw incremental cost of the local feature: ~317 KB / 142 classes
  project JAR, no new external coordinates.
- Verified: JVM compile, hermetic tests, live discovery walk for
  `atproto.com` → `bsky.social` (all 13 enforced metadata checks pass).
- **Not yet verified with a real OAuth approval or a real scrobble write.**

### 2. Library decision: adopt the fork, staged and reversible

The trade-off study ([`library-tradeoff0.gpt56s.md`](library-tradeoff0.gpt56s.md))
compared `atproto-kotlin` 9.11.0, `kbsky` 0.4.0, and keeping local code:

- `kbsky`: rejected (non-conforming DPoP, closed record union, broken JVM
  crypto graph; +3.26 MB functional).
- Full `atproto-kotlin` with models: rejected for Pano (models alone ~3.95 MB /
  1,936 classes; unnecessary for `JsonObject` records).
- Chosen shape: **`atproto-kotlin` runtime + oauth without models** — roughly
  +374 KB incremental to the whole app graph — once conformance gaps are fixed.

### 3. Fork: qualified and locally published

`/home/rektide/src/atproto-kotlin-boop`, based on upstream `9.11.0`, latest
`dd7df2c7`. Isolated, justified commits add:

- strict discovery/metadata validation, required callback `iss`, strict token
  `sub`/`token_type`/`scope`/expiry validation, terminal handling of malformed
  refresh responses, OAuth tests in CI gates;
- a generic `AuthProvider.observeResponse` seam (additive ABI, one
  client-owned retry, buffered body shared with decoding);
- DPoP conformance: exact 400/401 nonce-challenge classification, nonce
  retention from every response, single fresh-proof retry, isolated
  strict missing-nonce rejection, `invalid_token`-only refresh, opaque-token
  handling (JWT expiry parsing removed);
- granted-scope persistence in `OAuthSession` (serialization-compatible) and
  API-24-safe HTTP Date parsing.

Tests: runtime 105, OAuth 124, all green; API checks pass.

Published locally under fork coordinates (unsigned, Maven Local only):

```text
io.github.rektide.atproto:runtime:9.11.0-pano.1
io.github.rektide.atproto:oauth:9.11.0-pano.1
```

### 4. Pano: upstream field path wired but default-off

Selector `-Patproto.useUpstream=true` (or `ATPROTO_USE_UPSTREAM=true`)
compiles a complete fork-backed lifecycle: durable fork pending/session
stores, one process-wide authenticated `XrpcClient` per account, upstream
`AtProtoPoster` construction, granted-scope-aware targets, and logout —
with explicit no-conversion errors when the other path's session exists.
Default remains the local path; both selector values compile and are tested
(52 module tests, 13 focused app tests).

### 5. Pages identities: committed, not deployed

- rektide metadata in `web-scrobbler-github` (commits up to `75047a82`,
  includes later unrelated vosse/dull-firetip additions) —
  `https://rektide.github.io/web-scrobbler-pages/pano-scrobbler/oauth-client-metadata.json`.
- kawaiiDango metadata in the `pano-scrobbler-at-pages` workspace
  (`d8cb8da5`, `f0f1fc13`) with the Pages workflow copying it into the
  deployed artifact —
  `https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json`.

**Neither URL is live yet** (both currently 404). Real login cannot succeed
until one is deployed.

## Known gaps and risks

| Item | Status |
|---|---|
| Client metadata 404s | Blocks all real OAuth; deploy first |
| No real OAuth/login/write ever performed | Milestone M2 exists to fix this |
| Android build unverified in this environment | `adb` present, but no SDK dir detected; Android compile may still be blocked locally |
| Strict missing-nonce rejection | Highest compatibility risk vs non-conforming PDSes; isolated commit, may be dropped |
| Secrets unencrypted in MainPrefs; backup exposure | Deferred, both paths |
| Cross-process refresh race | Process-wide mutex only; store lease deferred |
| Signup issuer loop-back | Fork gap deliberately unresolved (Pano doesn't use signup) |
| `MaiTest.parseYoutubeTitles` failure | Pre-existing, unrelated to this work |
| GraalVM/native-image | Untouched; needed before desktop release |
| Maven Local only | Immutable distribution deliberately deferred per decision |

## Roadmap

### M1 — Environment and deployment unlock (now)

1. Confirm/build Android: locate or install the Android SDK (platforms 37 for
   compile, 24/25 targets for old-device testing), set `sdk.dir` in
   `local.properties`.
2. Deploy the rektide Pages site (push `web-scrobbler-github`); verify the
   metadata URL returns HTTP 200 `application/json` with no redirect.
3. Republish fork artifacts if the fork moved: run the documented
   `publishToMavenLocal` command for `9.11.0-pano.1`.

### M2 — Local-path field validation on the phone (first real OAuth)

Goal: one real account, real token, real Teal/Rocksky writes, default build.

1. Build & install the FOSS debug APK (default selector).
2. Login via handle; confirm Custom Tab/browser authorization, callback
   through the `io.github.rektide` scheme, account appears.
3. Play music; verify both records appear (check via a PDS record viewer or
   `com.atproto.repo.listRecords`).
4. Verify partial behavior: disable Rocksky, confirm Teal-only writes;
   re-enable; confirm toggles and granted-scope interplay.
5. Logout; confirm revocation attempt and clean account removal.
6. Long-lived session: force token expiry (wait or revoke access token
   server-side), confirm refresh-on-next-scrobble works.
7. Kill the app mid-authorization; relaunch from callback; confirm pending
   state survival.

### M3 — Upstream field path validation (same phone, selector build)

Goal: same real-world flows through the fork.

1. Build/install with `-Patproto.useUpstream=true` (Android variant).
2. Remove the local ATProto account first; confirm the mismatch error path,
   then fresh fork login.
3. Repeat M2's write/partial/logout/expiry checks through the upstream path.
4. Watch specifically for: nonce challenge/retry behavior, strict
   missing-nonce exceptions against the real PDS (telemetry-worthy),
   process-death during authorization, refresh after expiry.
5. Compare behavior and note any divergence from M2.

### M4 — Hardening both paths share

1. Move AT Proto secrets (both session formats) to encrypted,
   backup-excluded storage; migration from current prefs.
2. Address cross-process refresh: either prove single-process deployment on
   Android or implement the store lease/CAS seam.
3. Decide strict missing-nonce policy from field evidence; keep or drop the
   isolated commit accordingly.
4. Fork: consolidate PAR/code-exchange nonce loops onto the classifier;
   optional per-origin nonce store.
5. R8 release build + API 24/25 device pass; desktop GraalVM native-image
   smoke.

### M5 — Upstream becomes default

Preconditions (from the field-path gate):

- immutable artifact distribution replacing Maven Local (decision pending;
  Maven Central fork coordinates are the leading option when you're ready),
- M3 parity evidence recorded,
- M4 security items landed.

Then: flip the selector default, field-test the default-upstream build,
and only after that — as its own checkpoint — delete the local
OAuth/DPoP/crypto/repository implementation while keeping records/posting.

### M6 — Upstream contribution (parallel, optional)

The fork's conformance work maps to upstream PRs: OAuth test CI gate,
discovery/token validation, response seam, DPoP handling. The isolated
commits were structured for this; contributing them upstream would
eventually remove the fork entirely.

## Immediate next actions

1. Install/locate the Android SDK; confirm `:composeApp:compileDebugKotlinAndroid`.
2. Deploy rektide Pages metadata; curl-verify the URL.
3. M2 session on the phone; log findings to a new
   `design/atproto-posting/field-log0.*.md`.

## Cross-references

- Local implementation record: [`implementation1.unknown.md`](implementation1.unknown.md)
- Library comparison & sizes: [`library-tradeoff0.gpt56s.md`](library-tradeoff0.gpt56s.md)
- Staged fork adoption: [`upstream-migration0.gpt56s.md`](upstream-migration0.gpt56s.md)
- Reversible field path & gates: [`upstream-field-path0.gpt56s.md`](upstream-field-path0.gpt56s.md)
- Fork conformance reports: `/home/rektide/src/atproto-kotlin-boop/design/oauth-conformance/`
  (`validation0`, `response-seam0`, `dpop-response0`, `pano-readiness0`)
