---
type: Contributor guide
title: Native AT Protocol scrobbling
description: Native Rocksky and Teal authorization, completed-play delivery, development identity configuration and contribution boundaries.
resource: /doc/atproto.md
status: development-preview
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
sources:
  - resource: "opencode://ses_e7b1c1da4ffeyw8XS4l257gfAC"
    title: Implemented and tested the native account/provider/UI prefix and recorded its remaining qualification gates
  - resource: "opencode://ses_e7aedded3ffe8r5OwrYTHwM2nl"
    title: Integrated portable validation, corrected callback and refresh failure handling, and qualified native login and reference recovery
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProto.kt
    title: Shared native authority and client identity
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/onboarding/AtProtoLoginScreen.kt
    title: Browser approval, paste fallback and granted destination controls
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/oauth/AtProtoAccountAuthority.kt
    title: Locked token lifecycle, record boundary and target acknowledgement
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoLogin.kt
    title: Generation-specific completion, duplicate callback coordination and explicit publication retry
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccountsTest.kt
    title: Actual-vault application qualification with synthetic HTTP and preference failures
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/posting/Records.kt
    title: Separate bounded Teal and Rocksky record mapping and duration units
---

# Native AT Protocol scrobbling

**Development preview:** native login and completed-play delivery are implemented,
including strict key, identity, discovery, permission and record validation.
A real-engine HTTP 503 follow-up replay correction is still pending at this
checkpoint; it is not release-ready. No real-account or device validation is claimed.

## Use

Choose **AT Protocol (Rocksky / Teal)** in onboarding or service preferences.
Enter a handle or DID and approve in your external browser. Android receives the
native callback; desktop users can paste the **complete callback URL** into the
login screen. Never share that URL or put it in a bug report.

Pano keeps one AT Protocol account. Reauthorization replaces its login generation;
same-account destination choices and its enabled/disabled setting are preserved.
Service preferences → **Completed-play destinations / sign in** lets you select
Teal and Rocksky independently. A target must also have been granted by OAuth;
a preference cannot create permission. Selecting neither target submits nothing.

Only **completed plays** are sent. Teal duration is in seconds; Rocksky duration
is in milliseconds. There are no now-playing records, remote history/chart/social
reads, artwork enrichment, love/unlove or delete operations. Unsupported controls
are hidden; the empty remote-history view is not a failed indexing check.

Logout removes local authorization before best-effort remote revocation. A failed
account-reference write cannot make removed credentials usable. If authorization
was saved but account publication failed, the screen offers a retry tied to that
specific generation; it does not replay the callback code.

Removing an account from preferences targets the reference you clicked, not a
newer login that replaced it while the screen was open. After switching build
identity, sign in afresh. You may explicitly remove the old displayed reference
and its old-client authorization; nothing is automatically migrated or pruned.
If removal of the display reference fails, authorization is already removed;
retry the explicit removal. A corrupt vault or missing encryption key fails
closed rather than silently resetting credentials.

## Client identity and development

Production identity:

```text
client_id:    https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json
redirect_uri: io.github.kawaiidango:/pano-scrobbler/oauth/callback
scope:        atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create
```

The public metadata URL must be deployed before production-identity login can
work. App compilation does not deploy it. Metadata changes are an independent PR.

Supply **both** development values, through environment or ignored
`local.properties` (`atproto.clientId`, `atproto.redirectUri`):

```sh
PANO_ATPROTO_CLIENT_ID=https://rektide.github.io/pano-scrobbler/oauth-client-metadata.json \
PANO_ATPROTO_REDIRECT_URI=io.github.rektide:/pano-scrobbler/oauth/callback \
bash gradlew :androidApp:assembleDebug
```

The build validates the pair and uses it for generated constants and the Android
callback scheme. The complete URI is checked again by the shared authority.
Android cannot enforce a hostless single-slash URI's path with a host/path filter;
the callback has its own scheme-only filter, separate from `pano-scrobbler://screen`.
Changing client identity requires fresh authorization, not reusing another
client's vault entry. Local configuration additionally rejects encoded metadata
paths and query-bearing identities; that is a local supported-configuration policy.

## Ownership and retry limits

The application owns one Kotlin protocol path using the project's existing public
Ktor, OkHttp, coroutines and serialization dependencies plus JCA. No unpublished
AT Protocol library, Maven Local artifact, JavaScript runtime or backend selector
is required. This also means the application owns protocol maintenance and tests.

One native vault owns credentials across UI/background processes. General
preferences contain only account references/display data and destination choices.
Android encrypts the no-backup vault with a Keystore key. Desktop storage is
owner-private **plaintext**, not an OS keychain. Supported-platform runtime
qualification remains separate from source inspection and host tests.

Requests use fresh generation-scoped credentials under the OS lock. A rotating
refresh is durably finished before releasing it; ambiguous response/persistence
loss quarantines the old credential rather than replaying it. `expires_in` may
be absent: its expiry stays unknown, without decoding the opaque token or claiming
it is forever valid. An exact DPoP `invalid_token` rejection permits one forced
refresh and request retry. Ordinary 401s/network failures never authorize replay.
Received HTTP errors retain proven-unconsumed refresh state and learned nonces,
then still report the original failure or timeout. Invalid successful token data
and loss before any status remain conservative. A failed post-replacement disk
acknowledgement can leave either a quarantine marker or the valid new credential,
not a promise that the old credential is usable.

Metadata is bounded and checked for valid Unicode before truncation. The complete
serialized `createRecord` request, including wrapper fields and escaping, has a
local 64 KiB ceiling. Returned record locations must match the account and target;
record CIDs must parse as canonical CIDv1/DAG-CBOR/SHA-256. These are typed response
checks, **not** content-hash, commit-signature or repository verification.

Each successful destination is durably acknowledged before the next is sent.
Partial retry skips acknowledged targets. PDS success before local acknowledgement
and final success before Pano clears its pending play retain duplicate windows;
this is **not exactly-once delivery**. A bounded journal allows 128 unfinished
plays per session and does not silently prune them.

## Contributor checks and PR outlines

```sh
bash gradlew :atproto:test :composeApp:jvmTest :androidApp:assembleDebug --continue
```

For the scoped AT Protocol checks without unrelated app tests:

```sh
bash gradlew :atproto:test :composeApp:jvmTest \
  --tests 'com.arn.scrobble.api.atproto.*' :androidApp:assembleDebug
```

The checked native prefix passes **96 module tests and 14 AT Protocol app tests**;
Android debug APK assembly succeeds. The full app suite is **20/21 passing**:
only the known `MaiTest.parseYoutubeTitles` upstream trailing-space assertion fails.
The `--continue` command therefore exits nonzero despite completing APK assembly.
Host tests/builds do not qualify Android Keystore/API 24/25 runtime, interactive
UI/device behavior, desktop native images, Windows/macOS providers, real
authorization or service indexing. Resolve the outstanding engine replay gate
before treating these local results as a release candidate.

1. **Application PR:** one public-dependency `:atproto` implementation, native
   vault/transport, account/provider/UI integration, tests and this guide. Explain
   completed-only capabilities, credential ownership, grant selection and retry
    limitations. Resolve the real-engine replay correction and retain its wire
    regression before calling this release-ready. Exclude private configuration,
    credentials and local research/probes; this guide's source links refer only to
    feature files included in that PR.
2. **Metadata/Pages PR:** public native OAuth metadata JSON and deterministic Pages
   artifact inclusion only. Match the client/callback/scope above; explain that
   deployment precedes production login. Keep operator runbooks/research separate
   from that minimal code change.

Neither outline authorizes pushing, PR creation, deployment or real-account writes.
