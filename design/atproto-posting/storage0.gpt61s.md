---
type: Implementation checkpoint
title: Native account storage foundation and protocol handoff
description: Implemented cross-process file locking, durable authorization claims and session updates, platform vault factories, evidence and remaining integration obligations.
resource: /design/atproto-posting/storage0.gpt61s.md
status: draft
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-28 }
extensions:
  ticket: pano-atproto-remake-app-vault
sources:
  - resource: "opencode://ses_e7b3d8e43ffeUWIng4yiONPAnf"
    title: Worker A implemented and tested the native storage authority after the parent approved the narrow local backend
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent approved one Pano-owned protocol path and commissioned a bounded storage slice before worker turnover
  - resource: /design/atproto-posting/remake0.gpt61s.md
    title: Earlier architecture proposal and published-library qualification
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStore.kt
    title: Durable authorization/session operations and incomplete-update policy
  - resource: /atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/JvmVaultPersistence.kt
    title: Private desktop files and atomic synchronized replacement
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStoreTest.kt
    title: Real-filesystem, child-process, cancellation, failure, corruption and encryption tests
  - resource: /composeApp/src/androidMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoVault.android.kt
    title: Android Keystore encryption and no-backup native persistence
---

# Native account storage foundation and protocol handoff

**Implemented: an internal storage authority, not user-visible OAuth or scrobbling.**
The parent accepted one narrow Pano-owned Kotlin path after the
[architecture checkpoint](remake0.gpt61s.md). No unpublished AT Protocol artifact,
Maven Local repository, JS runtime or alternative backend selector was added.

Workspace: `/home/rektide/src/pano-atproto-remake`; bookmark
`atproto-clean-20261028`. Base remains upstream
`990154031e761b8e5460a0585fc834b43c9e2d71`.

Core foundation commit: `dffcf58532dc5fba68277fd1784bea28ebc2504c`.
Platform factories: `06d280e46da8d707b48522be40967e9030c298a7`.
The final handoff head (including this evidence) is recorded in
`pano-atproto-remake-app-vault`. The expected empty working-copy child is not the
last feature commit.

## What the boundary establishes

[`AtProtoAccountStore`](/atproto/src/main/kotlin/com/arn/scrobble/atproto/storage/AtProtoAccountStore.kt)
owns one active DID per client ID. Pending and completed credentials live in
client-ID namespaces. A `SessionHandle` includes client ID, DID and a random login
generation, so the same DID after reauthorization is not the old session.

- `beginAuthorization` explicitly replaces the unfinished attempt for that client.
- `consumeAuthorization` matches state and durably deletes it **before** returning
  its opaque protocol payload. Wrong state/client does not cancel another attempt;
  matching expired state is consumed without returning a claim.
- `completeAuthorization` accepts only the current single-use claim. Logout or a
  newer attempt invalidates old claims; a completed claim cannot publish twice.
- `currentSession` rereads disk under the OS lock. There is no token cache.
- `updateSession` accepts only a current generation and serializes the action
  across processes, from fresh read through its durable result.
- `logout` durably removes that client's session, pending state and claim. It does
  not depend on remote revocation or delete other client identities.

Opaque `JsonObject` payloads deliberately leave the protocol schema to the next
OAuth slice. This store does **not** validate token grants, issuer, DID-document
binding, PKCE, callback URLs or DPoP proofs. Those are not placeholder successful
operations here; they are absent protocol capabilities.

### Lock and interrupted-update semantics

Every operation uses the same stable `credentials.lock` file. It is never renamed
or deleted. `FileChannel.tryLock` handles another process and overlapping local
instances; cancellation while waiting closes its descriptor without entering the
operation. Process exit releases the OS lock without lease expiry or stale-lock
cleanup machinery.

Before invoking a possibly rotating action, `updateSession` durably writes an
`updating` marker. The action and final persistence then finish under
`NonCancellable`, still holding the lock. The protocol owner must provide bounded
network calls. Cancellation before this window is honored; cancellation after it
starts may cancel the caller's result, but does not abandon its durable outcome.

Outcomes are `Replace`, `Keep` or `Remove`. **`Keep` requires protocol evidence
that the old credential was not consumed.** A thrown action, process death or
failed final write leaves the marker. Reopening that session throws
`IncompleteSessionUpdateException`; it does not replay the old token. Explicit
new authorization or logout remains possible.

The marker is intentionally conservative. In the next slice, catch and classify
known non-consuming failures *inside* the update action, return `Keep`, and surface
the original failure after storage acknowledges it. Do not turn ordinary 429/5xx
responses into unconditionally thrown actions that strand a usable session.
Ambiguous response loss is different from proven non-consumption.

This implements the proposed lock/vault boundary **without** slow HTTP inside
`MainPrefs.updateData` or a second DataStore transaction protocol. Fresh reads,
encoding and atomic file publication are all under the dedicated OS lock.

## Platform guarantees and limits

- **Android:** `PanoAtProtoVault.android.kt` places vault and lock under
  `applicationContext.noBackupFilesDir/atproto`. An AES-256 Android Keystore key
  encrypts the whole versioned vault using AES-GCM with a fresh nonce and
  authenticated envelope header. First key creation is under the same lock.
  A missing key over an existing vault is an error, not regeneration/reset.
  Writes explicitly sync the temporary descriptor, `Os.rename` within the same
  directory, then sync the directory. Key material is not exported. Hardware
  backing and Android runtime qualification are **not established** by compilation.
- **Desktop:** `JvmVaultPersistence` creates owner-private storage (0700 directory,
  0600 files on POSIX; owner-only ACL on an ACL filesystem), syncs the temporary
  file, requires atomic replacement and syncs the directory on POSIX. Unsupported
  privacy capabilities fail rather than silently using a public file. Linux/POSIX
  is tested; Windows ACL and macOS behavior are not field-qualified. This is
  **plaintext with OS access control**, not a desktop keychain and not protection
  from the same user, root, or that user's backup tools.
- Neither platform touches `MainPrefs`, imports old experimental sessions, prunes
  unrelated files, logs credentials or resets a corrupt vault. Corruption is an
  explicit failure. A future deliberate corruption/key-loss recovery UX must not
  silently discard it.

[`PanoAtProtoVault`](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoVault.kt)
is an unused internal factory/singleton, not a new login provider. The singleton
is a convenience; the shared file lock is the correctness boundary.

## Verification

Core source above:

```sh
bash gradlew :atproto:test --console=plain
```

**17/17 pass**, including:

- two independent JVM processes perform 24 serialized changes to the same stored
  credential, each deriving from the latest durable value;
- a child process dies while holding the lock inside an update; another process
  can acquire it, but the interrupted credential remains unusable;
- cancellation during lock wait versus after action entry;
- logout waiting for an update, stale claims/handles after replacement, client
  isolation, recreation, expiry and durable single-use consumption;
- failed marker/claim/completion/final writes and failed final encryption, without
  successful credential publication;
- corrupt vault preservation, authenticated AES-GCM tamper/wrong-key rejection,
  fresh encryption nonces, private POSIX modes and stable lock-file identity.

Platform factory source at `06d280e46da8d707b48522be40967e9030c298a7` was checked
with:

```sh
bash gradlew :composeApp:compileKotlinJvm :androidApp:assembleDebug --console=plain
```

The first invocation compiled JVM successfully but exposed a non-public Android
`O_DIRECTORY` constant. After switching that Android-only line to public
`O_RDONLY`, `bash gradlew :androidApp:assembleDebug --console=plain` succeeded,
including recompilation of the native adapter and APK packaging. No JVM source
changed after its successful compilation.

No emulator/device, real authorization, real session token, repository write,
release/R8 or Graal/native-image execution was used. The earlier baseline's
`MaiTest.parseYoutubeTitles` trailing-space failure is unchanged and was not
papered over or rerun just to confirm it.

## Next coherent slice for a fresh worker

1. Read this account API and its tests before creating OAuth stores or a token
   provider. **Never send authenticated requests using a detached cached
   `currentSession` snapshot.** Fresh, serialized operations belong inside the
   authority, and may need a non-rotating request operation alongside `updateSession`.
2. Add typed protocol payloads, strict discovery, durable PAR nonce, exact native
   callback guards and OAuth exchange. Route the same account lifecycle through
   claim completion, refreshing and logout; surface quarantined state honestly.
3. Add actual token rotation, invalid-grant/transient/error classification,
   bounded cancellation and failed-persistence tests through that protocol seam.
   The storage tests do not establish wire-level OAuth correctness.
4. Build the dedicated client from `Requesters.baseKtorClient` policy, without the
   generic Last.fm validator/cache. It must participate in proxy invalidation and
   engine lifetime and must not follow credential-bearing redirects.
5. Qualify Android Keystore, native atomic publication and multiprocess behavior
   with synthetic instrumented fixtures (including API 24/25). Keep real accounts
   untouched. Later UI, record mapping, target grants/progress and metadata-default
   integration are still absent from this foundation.

Parent session: `ses_e7ba46f7fffeze8ND55fl0uJF8`. Worker A:
`ses_e7b3d8e43ffeUWIng4yiONPAnf`. Durable work history and exact final revision/check
pointers live in `pano-atproto-remake-app-vault` and its parent
`pano-atproto-remake-app`.

At the platform checkpoint, the session's last reported context was **277,618**
tokens (lagging measured request total, not lifetime usage). Turn over to a fresh
worker for the protocol/integration slice rather than extending this session.
