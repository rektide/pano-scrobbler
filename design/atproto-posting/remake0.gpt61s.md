---
type: Architecture checkpoint
title: One native AT Protocol account authority for Pano
description: Current published-library qualification, proposed native ownership and persistence boundaries, and a clean capability sequence from upstream.
resource: /design/atproto-posting/remake0.gpt61s.md
status: draft
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-28 }
extensions:
  ticket: pano-atproto-remake-app
sources:
  - resource: "opencode://ses_e7b3d8e43ffeUWIng4yiONPAnf"
    title: Worker A inspected the current baseline and Web Scrobbler model, qualified published Kotlin 9.13.0, and proposed the clean native ownership boundary
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent commissioned a clean application workspace, independent metadata lane, and an architectural checkpoint before broad implementation
  - resource: "file:///home/rektide/src/rekon/remake/README.md"
    title: Derive a destination and assess each activated capability instead of replaying history
  - resource: "file:///home/rektide/src/rekon/code/task-reconstruction/philosophy.md"
    title: Reconstruction from current goals and baseline
  - resource: "file:///home/rektide/src/pano-scrobbler-at/design/atproto-posting/pr-brief0.gpt6a.md"
    title: Preserved feature boundaries, dependency history and Pages contract
  - resource: "file:///home/rektide/src/web-scrobbler-atproto/doc/atproto.md"
    title: Extension-owned OAuth model and current verification limits
  - resource: "file:///home/rektide/src/web-scrobbler-atproto/src/core/scrobbler/atproto/oauth-client.ts"
    title: Client-scoped durable stores, DID-only account pointer and lifecycle serialization
  - resource: "file:///home/rektide/src/web-scrobbler-atproto/tests/background/atproto-oauth.test.ts"
    title: Storage, callback, refresh and logout boundary tests
  - resource: https://repo.maven.apache.org/maven2/io/github/kikin81/atproto/oauth/maven-metadata.xml
    title: Current public OAuth release is 9.13.0, not the previously reviewed 9.11.0
  - resource: https://github.com/kikin81/atproto-kotlin/tree/adce99b8370c397333f48d6f8e84e5f815b43727
    title: Published 9.13.0 release; published OAuth and runtime source JARs inspected directly
  - resource: https://atproto.com/specs/oauth
    title: Native public-client, account-binding, granted-scope, discovery and DPoP requirements
---

# One native AT Protocol account authority for Pano

**Recommendation, awaiting the parent's architectural decision:** build one
narrow Pano-owned Kotlin implementation, using only public existing dependencies.
Retain useful protocol primitives and fixtures from the old feature, but replace
its session/store/lifecycle composition. Do not carry either the unpublished
fork dependency or the two-backend selector into the clean contribution.

This is the first checkpoint, not a claim that the proposed feature is built.
Only baseline verification and an isolated published-library probe have run.

## Destination and scope

On upstream `990154031e761b8e5460a0585fc834b43c9e2d71`, users can authenticate
natively, select independently granted Rocksky/Teal destinations, submit completed
plays, survive app recreation and refresh, and explicitly remove authorization.
The browser performs approval; Pano owns discovery, state, exchange, credentials,
refresh, authenticated PDS writes and local logout. No JS runtime or token service
is introduced.

The proposed contribution does **not** implement now-playing writes, charts,
social reads, album-art enrichment, love/unlove, deletion, or exactly-once delivery.
Issue #937 remains only partially addressed. No push, PR creation or deployment
is authorized.

Working home: `/home/rektide/src/pano-atproto-remake`, bookmark
`atproto-clean-20261028`. Historical feature/research remains independently
preserved in `pano-scrobbler-at` at `atproto-before-remake-20261028` (`5655abe8`).
Tracking: `pano-atproto-remake-app`, child of `pano-atproto-remake`; shared tracker
at `/home/rektide/src/pano-scrobbler-at/.beads`.

## Current evidence, not inherited decisions

The inspected Web Scrobbler model is
`e1267413e77b12c2ddaf93ae5586bb6643757106`. Its transferable ideas are:

- one OAuth authority rather than UI/provider-owned token copies;
- stores scoped by client ID, session identity by DID;
- awaited durable state consumption and token writes;
- an account pointer separate from secrets;
- lifecycle/refresh serialization and field mapping separate from authenticated
  create-record transport.

Its Web Locks and HTTPS-page callback relay do not transfer literally to Android.
Pano really declares `:bgScrobbler` components in
[`AndroidManifest.xml`](/composeApp/src/androidMain/AndroidManifest.xml). An object
singleton or coroutine mutex coordinates one process only.

### Published Kotlin library rechecked

Maven Central's current OAuth release is **9.13.0**, metadata last-updated
`20261023015810`; release commit is
`adce99b8370c397333f48d6f8e84e5f815b43727`, remote main observed at
`f394c645a45434f76471154853f3b0329abd5598`. The old 9.11 report is historical
evidence, not the basis for rejecting today's artifact.

Published `oauth:9.13.0` and `runtime-jvm:9.13.0` source JARs were downloaded and
inspected. A separate Gradle project uses the actual public artifacts and a Ktor
MockEngine, with no Maven Local repository or source substitution. **Five probes
pass by asserting these observed undesirable behaviors:**

| Probe | Observed behavior of 9.13.0 |
|---|---|
| Missing callback issuer | `completeLogin` accepts a callback without `iss`. |
| Missing subject and scope | Existing-account completion accepts tokens without `sub` or `scope`. |
| Invalid token properties | `Bearer`, no `atproto` grant and negative expiry still produce a saved session. |
| Unbound resource discovery | A wrong `resource`, two authorization servers and incomplete AS capabilities are accepted. |
| Resource nonce updates | Runtime never notifies auth of HTTP 400 or successful response nonce headers. |

Source also shows that `OAuthSession` contains neither granted scope nor token
expiry. Pano cannot safely reconstruct granted destination permissions from that
session API. These are not all fixable by an app wrapper: exchange and discovery
validation sit inside the library and the runtime response hook is 401-only.
Its refresh persistence and nonce implementation are useful learning material,
but adopting it still means owning a fork or equivalent protocol interception.

**Alternatives:** a reproducibly distributed, qualified fork remains technically
possible, but adds an independently maintained dependency surface without removing
native storage/concurrency responsibility. Vendoring that fork moves rather than
removes this ownership. Embedding the extension's JS library is not a coherent
native dependency choice. For the commissioned two-record operation, hardened
local ownership is the smaller reviewable steady state.

## Proposed module and state ownership

Keep two real boundaries, not two protocol backends:

1. **`:atproto`** — ordinary Kotlin/JVM using Pano's public Ktor, serialization,
   coroutines and JCA facilities. Own strict discovery, PKCE/DPoP, OAuth/token
   validation, authenticated create-record, target grants and pure record mapping.
   Expose complete account operations, not freely cached mutable token providers.
2. **`composeApp/api/atproto`** — owns Pano account registration, preferences,
   pending-play progress, platform credential persistence and construction of the
   protocol account authority. UI and `AtProtoScrobblable` call that same authority;
   neither constructs its own independent refresh client.

### Native serialization and persistence

Proposed implementation hypothesis for the next slice:

- A **stable OS file lock** covers the native account lifecycle across processes
  and independent client instances. A coroutine mutex may optimize local access
  but is not the correctness boundary. Never replace/delete the lock file.
- Under that lock, read the latest durable session before each operation; never
  trust a cached token or a copied session in `UserAccountSerializable.authKey`.
  For a DataStore-backed vault, obtain authoritative snapshots through
  `updateData`, not a potentially observed old flow value.
- Use a **dedicated credential vault**, not `MainPrefs`. Android uses an encrypted
  serializer with an Android Keystore key and stores the vault in
  `noBackupFilesDir`; this avoids exporting/backing up device-bound credentials.
  Desktop uses a user-private credential file with restrictive permissions; do
  not pretend a key stored beside ciphertext is an OS keychain.
- Keep client ID in the namespace and DID in the session key. `authKey` and the
  native account list are account references/display metadata, not credentials.
  Authorization lookup remains authoritative even if a display-pointer update
  fails. Switching developer identity requires fresh authorization, not reuse.
- Consume matching pending state **durably before code exchange**, save the PAR
  nonce with it, validate exact redirect and unambiguous query parameters, and
  require state, issuer, expected DID and returned grants. Fresh instances must
  complete the same pending attempt without in-memory state.
- Hold the lifecycle lock across refresh and its durable replacement. Once a
  rotating request is sent, finish its bounded response/persistence window
  non-cancellably. A caller cancelled before sending must remain cancellable.
  Failed persistence is surfaced; do not report volatile credentials as durable.
- Serialize logout against callback, posting and refresh. Remove pending/current
  authorization and Pano's account reference on explicit logout; attempt remote
  refresh-token revocation, but local deletion must not depend on its success.
  A completed old request cannot resurrect the logged-out account.

The file-lock/platform-vault mechanics need executable process and cancellation
proof before integration. AndroidX's published DataStore 1.3.0-alpha11 source was
also inspected: `updateData` holds an interprocess coordinator lock around its
transform and write, but a nested `NonCancellable` token request alone is **not**
enough. Returning from its caller-context transform can observe cancellation
before persistence. Do not improvise network refresh inside a normal cancellable
`updateData` transform or nest updates of the same DataStore.

### Transport, grants and posting

- Use a dedicated bounded OAuth/PDS HTTP client. Pano's
  [`Requesters.genericKtorClient`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/Requesters.kt)
  installs a Last.fm-style error validator that throws on non-2xx responses;
  that is the wrong seam for inspecting OAuth 400/401 nonce/error responses.
  Reuse proxy/TLS policy, not that response validator or its cache.
- Disable redirects before sending credentials, bound response bodies while
  reading, keep the existing explicit request timeout, and validate discovery
  URLs/documents and identity/issuer binding. Do not log callback codes, tokens,
  keys or PKCE state.
- Persist nonces by client/account/server and observe all DPoP responses. Reject
  missing nonces; allow one correctly classified nonce retry. Treat access tokens
  as opaque. `invalid_grant` is terminal; ordinary network/429/5xx failures must
  not silently sign out the user.
- Pure mapping owns Teal seconds versus Rocksky milliseconds and Unicode/lexicon
  field bounds. Selected targets are intersected with the actual current grants.
- Persist each successful target before proceeding to another; retries skip
  completed targets. PDS-assigned record keys retain a crash/ambiguous-response
  duplicate window. Do not claim transactional exactly-once posting.

## Proposed construction and assessment sequence

These are capability dependencies, not a replay or a fixed commit count:

1. **Native account authority and storage contract.** Establish shared ownership,
   client/DID scoping, durable single-use state, locking and failure behavior with
   deterministic storage tests and separate JVM-process tests. No user-visible
   login is claimed yet. Assess before activating rotating network credentials.
2. **Conforming public OAuth and authenticated requests.** Reuse audited narrow
   primitives where suitable, add current nonce/discovery/callback/rotation tests,
   and prove cancellation and failed persistence boundaries. No machine-local
   AT Protocol artifacts or alternative selector enters this prefix.
3. **Completed-play mapping and resilient dual submission.** Exact fixtures,
   granted/selected targets, partial success, retry progress and error propagation
   exercise the same account authority, including two competing callers/logout.
4. **Native Pano login/provider integration.** Account ID 10, Android callback,
   desktop paste fallback, destination controls, TV exclusion and explicit
   unsupported-feature behavior. Credentials never enter general preferences.
   Verify merged manifest, JVM tests and Android build at this actual prefix.
5. **Contribution qualification and concise contributor guidance.** Review the
   final composition, public dependency graph, defaults/overrides, exact checks
   and operator PR outline. Keep this reasoning/probe out of the eventual concise
   upstream feature explanation.

The native production defaults must align with the independent metadata lane:

```text
client_id:    https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json
redirect_uri: io.github.kawaiidango:/pano-scrobbler/oauth/callback
scope:        atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create
```

Retain developer overrides for the existing live rektide identity. Production
metadata deployment and real approval/callback/refresh/indexing are separate
operator gates; the prepared upstream URL currently is not deployed.

## Checks actually run

Untouched application base: `990154031e761b8e5460a0585fc834b43c9e2d71` (empty
working-copy child `e67b65a8a2c09926d3a47dcf7f184339c579eb0c`). Privately copied
ignored SDK/signing/build properties; no values are included in this evidence.

```sh
bash gradlew :composeApp:jvmTest :androidApp:assembleDebug --continue --console=plain
```

Android debug assembly succeeds. Baseline app tests are **6/7 passing**; the sole
failure is the existing `MaiTest.parseYoutubeTitles` index-46 trailing-space
expectation. This is baseline evidence, not a newly introduced failure and not
something to paper over. Missing `google-services.json` is reported by the
upstream task but does not prevent this debug assembly.

Published-artifact probe (ignored scratch, no app dependency changes):

```sh
bash gradlew -p .test-agent/atproto-remake/probe test --console=plain --no-configuration-cache
```

**5/5 passing**, explicitly proving the library behaviors listed above, not
OAuth conformance. Source JAR SHA-256:

```text
oauth-9.13.0-sources.jar
b6f6362e769dd587d72ff8fc7566392f2d131964400829608fa1a51ecb03db3d
runtime-jvm-9.13.0-sources.jar
8b2a2533e29d6212aba089de8d6b49a4c2bbf9c2282e653223de1aaaf429b241
```

Scratch source `PublishedOAuthProbeTest.kt` SHA-256:
`e40b55218f9af79775cc379cfa6ac9b88a287df5b892a46d7493f3858924b938`.
The scratch README, build and XML reports remain in `.test-agent/atproto-remake`.
No real authorization or repository writes were performed.

## Decision requested at this checkpoint

Approve **one hardened narrow local implementation**, with the native
cross-process/credential authority as the first executable slice, rather than
turning the unpublished fork into the contribution's new prerequisite. The lock
and vault design above is a testable implementation hypothesis, not a verified
platform capability. If the parent prefers a distributed library fork, settle
that explicitly before implementing a different state/transport API.
