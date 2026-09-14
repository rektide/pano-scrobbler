---
type: Adoption checkpoint
title: Staged atproto-kotlin fork adoption
description: Local fork publication, narrowly resolved dependencies, an inactive upstream-backed create-record adapter, verification evidence, and measured size impact.
resource: /design/atproto-posting/upstream-migration0.gpt56s.md
tags: [atproto, oauth, pano, kotlin, migration, dependencies, size]
status: draft
generated: { by: model:gpt-5.6-sol, at: 2026-09-14 }
verified: { by: agent:gpt-5.6-sol, at: 2026-09-14 }
sources:
  - id: prior-tradeoff
    resource: /design/atproto-posting/library-tradeoff0.gpt56s.md
    title: Kotlin AT Protocol library trade-off for Pano Scrobbler
  - id: fork-readiness
    resource: file:///home/rektide/src/atproto-kotlin-boop/design/oauth-conformance/pano-readiness0.gpt56s.md
    title: Pano-ready ATProto Kotlin fork
---

# Staged atproto-kotlin fork adoption

## Checkpoint

Pano's isolated `:atproto` module now resolves the qualified fork's `runtime` and `oauth` artifacts and contains an upstream-backed `com.atproto.repo.createRecord` adapter. Production behavior has **not** switched:

- `AtProtoPoster` still constructs the existing local `AtProtoRepository`;
- local OAuth, DPoP, crypto, identity, repository, and store code remains present;
- `LoginViewModel`, account/provider selection, posting progress, and logout remain unchanged;
- generated `models` is neither published nor resolved.

This checkpoint proves dependency and transport fit before taking ownership-sensitive session/store decisions.

## Commits

### Fork repository

| Change | Commit | Result |
|---|---|---|
| `xzrozpwn` | `d9e78204` | Property-driven group/version override with unchanged upstream defaults. |
| `nktywktk` | `dd7df2c7` | Explicit unsigned local-publication switch after the first no-signatory attempt exposed required signing. |

### Pano repository

| Change | Commit | Result |
|---|---|---|
| `rvrmtvzw` | `9173f4fa` | Narrow Maven Local resolution and pinned runtime/oauth dependencies. |
| `kzzvullz` | `8c52d3a5` | Inactive upstream-backed create-record adapter and hermetic tests. |

## Fork publication

Default fork builds still use upstream coordinates from the repository:

```text
io.github.kikin81.atproto:<artifact>:9.11.0
```

`publicationGroup` and `publicationVersion` override those values only for the invoking Gradle build. Signing remains required unless explicitly disabled. The exact local command used was:

```bash
./gradlew \
  :runtime:publishToMavenLocal \
  :oauth:publishToMavenLocal \
  -PpublicationGroup=io.github.rektide.atproto \
  -PpublicationVersion=9.11.0-pano.1 \
  -PpublicationSigningRequired=false \
  --no-configuration-cache
```

Explicit module task paths are important: a root publication lifecycle could include `models`, compose, or other publishable projects. The successful task graph published only `:runtime` and `:oauth`; all signing tasks were skipped by the explicit local override.

### Maven Local artifacts

| Coordinate/file | Local path | SHA-256 |
|---|---|---|
| `io.github.rektide.atproto:runtime:9.11.0-pano.1` | `~/.m2/repository/io/github/rektide/atproto/runtime/9.11.0-pano.1/runtime-9.11.0-pano.1.jar` | `089e24417bf9f6c34eec5720eebb2cbcc82ea2855ecef90133eb17e1079d5868` |
| JVM target selected from runtime metadata | `~/.m2/repository/io/github/rektide/atproto/runtime-jvm/9.11.0-pano.1/runtime-jvm-9.11.0-pano.1.jar` | `da095f91d6a88d049064bfe05cf3141d832e0ddfa852e72b5519cd0b13f7bfd0` |
| `io.github.rektide.atproto:oauth:9.11.0-pano.1` | `~/.m2/repository/io/github/rektide/atproto/oauth/9.11.0-pano.1/oauth-9.11.0-pano.1.jar` | `bd9a813d69e65b3e6e55ad9848267793ce42099add42a14890058d39726ef318` |

No `io.github.rektide.atproto:models` directory exists in Maven Local.

## Pano dependency boundary

[`settings.gradle.kts`](/settings.gradle.kts) gives `mavenLocal()` a content filter containing only `io.github.rektide.atproto`. Local artifacts from any other group cannot shadow Maven Central, Google, or Compose dependencies.

[`libs.versions.toml`](/gradle/libs.versions.toml) pins both aliases to `9.11.0-pano.1`:

```text
io.github.rektide.atproto:runtime:9.11.0-pano.1
io.github.rektide.atproto:oauth:9.11.0-pano.1
```

Both are explicit `:atproto` implementation dependencies. The resolved JVM graph selects `runtime-jvm:9.11.0-pano.1` through Kotlin Multiplatform metadata. It contains no `io.github.kikin81.atproto` and no `models` dependency.

Maven Local is mutable and machine-local. A clean machine cannot reproduce this build from Pano alone: it must first check out the exact fork changes and run the publication command, or consume immutable artifacts from a private repository with published checksums.

## Adapter interface and wire behavior

The new transport is intentionally internal:

```kotlin
internal class UpstreamAtProtoRepository(
    xrpcClient: XrpcClient,
) {
    suspend fun createRecord(
        did: String,
        collection: String,
        record: JsonObject,
    ): CreatedRecord
}
```

Only the constructor sees the fork's `XrpcClient`, and the class itself is internal. Its operation accepts Pano's existing `JsonObject` and returns Pano's existing typed `CreatedRecord(uri, cid)`. Private Pano-local `@Serializable` request/response classes prevent generated models or fork DTOs from crossing the adapter seam.

The procedure call is exactly:

```text
POST /xrpc/com.atproto.repo.createRecord
Content-Type: application/json

{
  "repo": "<did>",
  "collection": "<collection>",
  "record": { ... }
}
```

It sends no `rkey`, `validate`, or `swapCommit`, and passes no `atproto-proxy`. Record `$type` must still equal the collection before transport. The supplied authenticated `XrpcClient` owns Authorization/DPoP headers, response observation, nonce updates, one-retry control, decoding, and `XrpcError` mapping. The adapter does not catch or translate upstream errors.

Tests serialize the actual local Teal and Rocksky record classes to `JsonObject`; no standard model artifact or generated record type participates.

## Verification

### Fork qualification before publication

```text
./gradlew :runtime:jvmTest :oauth:test
  :runtime:spotlessCheck :oauth:spotlessCheck
  :runtime:apiCheck :oauth:apiCheck
  --rerun-tasks
  -PpublicationGroup=io.github.rektide.atproto
  -PpublicationVersion=9.11.0-pano.1
```

- runtime: **105 tests passed**;
- OAuth: **124 tests passed**;
- Spotless and API checks passed;
- generated OAuth POM pointed at `io.github.rektide.atproto:runtime-jvm:9.11.0-pano.1`;
- the exact Maven Local publication command completed.

### Pano

- `:atproto:test --rerun-tasks` — **49 tests, 0 failures**.
- `:composeApp:compileKotlinJvm` — passed.
- Dependency report confirmed only fork `oauth`, `runtime`, and selected `runtime-jvm`; no models/upstream-group artifact.
- `local.properties` was temporary and removed after each invocation. It contained the local Android SDK path and dummy required build-config values, not production credentials.

New adapter tests verify:

- exact method/path/query/content type/body and omitted optional controls;
- typed `{uri,cid}` result;
- absent proxy header;
- DPoP/auth headers supplied only by the configured `AuthProvider`;
- successful and failed response nonce observation;
- one fresh-auth retry with identical request body;
- unwrapped XRPC name/message/status;
- actual Pano Teal and Rocksky `JsonObject` records;
- mismatched `$type` rejection before transport.

## Size measurement

The baseline is Pano commit `6e8ecbd4`, built from a temporary `git archive` with the same Gradle/JDK/cache environment. The staged measurement is the current adoption tree.

### Project JAR: code Pano ships from `:atproto`

| Metric | Baseline | Staged | Delta |
|---|---:|---:|---:|
| `atproto.jar` compressed bytes | 317,209 | 333,028 | **+15,819 (+4.99%)** |
| JVM `.class` entries | 142 | 151 | **+9** |
| ZIP entries | 156 | 165 | **+9** |

The nine classes are the adapter, two private serializable wire types, their serializers/companions, continuation, and file facade. The project JAR contains **zero** `io/github/kikin81/...` classes: Gradle does not shade external dependencies into this ordinary JVM library JAR.

The current project JAR is `/home/rektide/src/pano-scrobbler-at/atproto/build/libs/atproto.jar`, SHA-256 `c954e40e416f5e2557a12888225124f09fb88cb89e58a1949e90965c2a8edd92`.

### Resolved `:atproto` JVM runtime graph: external inputs

| Metric | Baseline | Staged | Delta |
|---|---:|---:|---:|
| Resolved external JARs | 20 | 27 | **+7** |
| Sum of compressed JAR bytes | 7,047,415 | 8,246,081 | **+1,198,666** |

Added artifacts:

| Artifact | Bytes |
|---|---:|
| fork `oauth` | 212,491 |
| fork `runtime-jvm` | 139,694 |
| `kotlinx-datetime-jvm` compatibility artifact | 753,869 |
| Ktor client content negotiation | 28,016 |
| Ktor serialization-kotlinx-json | 28,653 |
| Ktor serialization-kotlinx | 28,889 |
| kotlinx serialization JSON I/O | 7,054 |

The two fork code JARs account for 352,185 bytes; their newly selected transitive JARs account for 846,481 bytes. Existing Ktor core, serialization core/JSON, coroutines, Kotlin stdlib, I/O, websocket, and logging coordinates were already in the isolated baseline graph.

This graph measurement differs from the earlier whole-desktop comparison in [`library-tradeoff0.gpt56s.md`](/design/atproto-posting/library-tradeoff0.gpt56s.md): the isolated baseline `:atproto:runtimeClasspath` had no datetime artifact to replace, so it counts the entire 753,869-byte compatibility JAR rather than only a version delta elsewhere in the application graph.

### What these numbers do not mean

- The project-JAR delta is not the resolved dependency cost.
- The raw dependency-JAR sum is not an APK, DEX, R8, desktop distribution, or native-image delta.
- Shared artifacts elsewhere in an application graph must not be counted twice.
- R8 and native-image reachability can remove unused code, while reflection/serialization metadata can retain it.
- JAR byte checksums and small compressed-size differences may vary with build-tool/JDK timestamps; class names/counts and resolved coordinates are the more stable comparison.
- Maven Local can be overwritten under the same version, so its checksums are evidence for this machine's staged input, not immutable publication provenance.

## What remains before a production switch

1. Implement Pano-owned adapters for the fork's `PendingAuthStore` and `OAuthSessionStore` without deleting existing stores.
2. Decide how existing local sessions transition. The local JWK/session representation is not the fork's PKCS8/X509 `OAuthSession`; a forced fresh authorization may be safer than key/token conversion.
3. Construct one upstream `AtOAuth`/authenticated `XrpcClient` per account lifecycle with Pano's existing bounded `HttpClient` policy.
4. Map the fork's persisted `scope`/`grantedScopes` into Pano's current target gating and preserve reduced-grant behavior.
5. Add a staged selection seam so `AtProtoPoster` can exercise `UpstreamAtProtoRepository` without deleting the local fallback; then run field tests before making it the default.
6. Verify partial-success/progress retry behavior across both Teal and Rocksky with the upstream transport selected.
7. Exercise login, process death, strict missing-nonce telemetry, refresh, logout, API 24/25 devices, release R8, and desktop packaging.
8. Remove local protocol files only after the upstream path is the reviewed production default and parity evidence passes.

The atomic lease/store redesign remains explicitly deferred. Existing encryption/backup concerns also remain app-owned and unresolved; adding dependencies or this adapter does not improve them.

## Proposed next switch slice

Add fork-store adapters and an account-scoped upstream client factory **without changing the default poster path**. Require a fresh fork-backed login in test fixtures rather than converting existing private-key material. Then add one narrow, disabled transport selector at the composition boundary that can supply `UpstreamAtProtoRepository::createRecord` to `AtProtoPoster` while retaining the local repository as default/fallback.

That slice makes end-to-end field testing possible while keeping rollback immediate and avoids prematurely deleting the known local implementation.

## Code references

- [`settings.gradle.kts` fork-only Maven Local filter](/settings.gradle.kts#L23-L39).
- [`libs.versions.toml` pinned fork aliases](/gradle/libs.versions.toml).
- [`atproto/build.gradle.kts` runtime/oauth dependencies](/atproto/build.gradle.kts#L10-L20).
- [`UpstreamAtProtoRepository.kt`](/atproto/src/main/kotlin/com/arn/scrobble/atproto/repo/UpstreamAtProtoRepository.kt).
- [`UpstreamAtProtoRepositoryTest.kt`](/atproto/src/test/kotlin/com/arn/scrobble/atproto/repo/UpstreamAtProtoRepositoryTest.kt).

## Cross-references

- [`library-tradeoff0.gpt56s.md`](/design/atproto-posting/library-tradeoff0.gpt56s.md) established runtime+OAuth without models as the only plausible upstream shape and measured the pre-patch adoption blockers.
- The unpublished fork report at `/home/rektide/src/atproto-kotlin-boop/design/oauth-conformance/pano-readiness0.gpt56s.md` documents the qualified behavior, binary break, and strict-nonce caveat consumed here.
- [`implementation1.unknown.md`](/design/atproto-posting/implementation1.unknown.md) documents the local implementation retained as the production path during this staged adoption.
