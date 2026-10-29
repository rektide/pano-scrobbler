---
type: Implementation handoff
title: Integrated native AT Protocol feature and one-shot transport
description: Final native integration of the exact B3 engine receipt, retained lifecycle regressions, one-use browser launch correction, checked source and contribution boundaries.
resource: /design/atproto-posting/native2.gpt61s.md
status: local-qualification-handoff
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-app-integration
sources:
  - resource: "opencode://ses_e7aedded3ffe8r5OwrYTHwM2nl"
    title: A3 integrates the exact one-shot receipt, checks the combined feature, corrects retained browser launch behavior and finishes local implementation handoff
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Parent accepts the host-qualified prefix, relays the immutable engine fix and separates final review and external runtime/deployment gates
  - resource: "opencode://ses_e7af14a6cffeDevLvL0joxsto6"
    title: B3 authors the isolated public one-shot engine correction and real-wire regressions
  - resource: /design/atproto-posting/native1.gpt61s.md
    title: Qualified predecessor with exact portable source map, R1/R2 corrections and native account/login recovery tests
  - resource: "file:///home/rektide/src/pano-atproto-transport/design/atproto-posting/transport0.gpt61s.md"
    title: Read committed B3 transport handoff at evidence 2d239328, code b3a69e4d; no active independent-review material read
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoTransport.kt
    title: Public application interceptor and transparent per-call one-shot body wrapper
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoTransportReplayTest.kt
    title: Exact received 503 preservation, current POST shape matrix and body-wrapper contract
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoProtocolTransportTest.kt
    title: Actual native authority and engine retain fresh-proof protocol-owned nonce retry
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/AtProtoProxyTransportTest.kt
    title: Actual CONNECT authentication remains before TLS initialization and origin credentials
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoLogin.kt
    title: Process-local one-use browser and navigation completion claims, with durable OAuth still owned by the authority
  - resource: /composeApp/src/commonMain/kotlin/com/arn/scrobble/onboarding/AtProtoLoginScreen.kt
    title: Shared screen consumes automatic browser launch once and retains explicit reopening
  - resource: /composeApp/src/jvmTest/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoAccountsTest.kt
    title: Actual-vault login/projection fixtures include deferred exchange and retained-browser-launch regression
  - resource: /atproto/src/test/kotlin/com/arn/scrobble/atproto/oauth/NativeOAuthAuthorityTest.kt
    title: R1/R2 regressions rerun on the engine-integrated composition, including the real 40000ms deadline
  - resource: /doc/atproto.md
    title: Separately committed upstream guide, only feature-code/test/session sources, limits and two PR outlines
---

# Final integrated native feature

**Local protocol and native app integration are qualified by host tests and Android
debug builds, not by Android runtime or real-account certification.** This succeeds
the [portable/native checkpoint](native1.gpt61s.md) at `43065bf3`. Its R3-pending
statements describe that predecessor, not this integrated source.

Workspace: `/home/rektide/src/pano-atproto-remake`; bookmark retained as
`atproto-clean-20261028`. Parent `ses_e7ba46f7fffeze8ND55fl0uJF8`; A3
`ses_e7aedded3ffe8r5OwrYTHwM2nl`.

## Exact integration map

1. Native foundation and feature prefix through `43065bf3`, production through
   `8b70e871`: retained unchanged except the narrow browser-launch correction below.
2. Portable source `1a0c119e372af16298b004d81aebf21e657b3015` was already adopted at
   `c25a32ce`. The [predecessor source map](native1.gpt61s.md#coherent-stages-and-source-map)
   records exact files and adaptations, without importing old OAuth/poster owners.
3. **Only** engine code/test commit
   `b3a69e4d62f73bb5de2eba4f06cf07e3ee9e3db0` was duplicated onto `43065bf3` as
   `df8874d9`. Its entire four-file delta applies cleanly: `AtProtoTransport.kt`
   and `AtProtoTransportReplayTest`, `AtProtoProtocolTransportTest`,
   `AtProtoProxyTransportTest`. No synthetic fixture adaptation, production
   weakening, old-branch replacement or evidence-commit import was needed.
4. At `f2c49860`, the parent's earlier retained-browser-launch target is corrected separately.
   `AtProtoLogin.State.browserPending` plus an atomic consumer allow one automatic
   browser launch across overlapping/recreated screen instances. The launch URL
   remains available to the explicit reopen button; callback completion removes
   it. Existing one-use navigation completion remains separate. The overlapping
   callback fixture now pauses a real token exchange, delivers its duplicate and
   tries a competing begin while busy, then verifies exactly one completion.

Final production: `f2c498601a6b4222cd0d1eaa6c32e0917d688967`.
Exact transport adoption: `df8874d9fd8c1f28b3da3e3207549256b931cd99`.
The separately committed guide is `0ea1284d`; the final evidence head and bookmark
are recorded on the scoped tickets and in the parent receipt. Independent history
and research remain preserved.

## Review findings on the composition

- **R1 corrected:** malformed callback URI errors are generic, with no raw
  code/state cause or suppressed input. Rejection does not consume valid pending
  authorization and causes no exchange.
- **R2 corrected:** known received HTTP errors retain proven-unconsumed refresh
  state and learned nonce before rethrowing the original failure/cancellation.
  The real 40000ms 503-header/body deadline and the 400/403/503 reader/error matrix
  pass. Unknown-status loss, invalid successful data and ambiguous rotation remain
  conservative; no automatic retry was introduced.
- **R3 integrated and checked:** a public OkHttp application interceptor gives
  each non-null POST body a transparent `isOneShot()` wrapper before follow-up
  handling. Type, length, duplex and direct writes are delegated; no buffering or
  status/header/body rewriting is used. The real 503/`Retry-After: 0` response is
  delivered unchanged after one credential POST. HTTP 408 and current PAR/code/
  refresh/revoke/PDS/empty POST shapes are covered too.
- Protocol-owned nonce retries still make a new engine call and fresh proof,
  preserving key, payload and a single retry budget. Existing redirect rejection
  still passes. HTTPS proxy CONNECT authentication still precedes TLS and origin
  credentials; its fixture deliberately stops at TLS initialization, not a claim
  of successful TLS tunnel/trust qualification.

The one-shot claim is for the current exposed credential-bearing POST operations,
not arbitrary bodyless GETs, WebSockets, later middleware or future engine versions.
Normal Pano TLS/proxy/user-agent/timeout policy, engine leases and native credential
authority remain unchanged.

## Exact composition receipts

At final production source `f2c49860`, **96/96 module tests and 20/20 AT Protocol
app tests pass**. Full app suite: **26/27**, with only the independently established
upstream `MaiTest.parseYoutubeTitles` trailing-space assertion failure. Android
debug APK assembly succeeds. No baseline expectation was changed.

```sh
# Rerun the module explicitly on the R3-integrated composition, including the
# actual 40000ms R2 deadline. All new real-engine wire tests run in the app suite.
bash gradlew :atproto:test --rerun :composeApp:jvmTest :androidApp:assembleDebug \
  --continue --console=plain

# After the narrow browser-launch correction; module code is unchanged.
bash gradlew :atproto:test :composeApp:jvmTest :androidApp:assembleDebug \
  --continue --console=plain
```

The first composition (`df8874d9`) passes 96 module/19 AT Protocol app tests,
full app 25/26, plus APK assembly. The second adds the browser regression and
passes the final counts above; unchanged module tests are up-to-date, not falsely
claimed as re-executed after an app-only change. Both commands exit nonzero only
for that baseline app assertion, while `--continue` completes Android assembly.
Ignored logs: `.test-agent/atproto-oauth/a3-integrated-composition.log` and
`a3-final-composition.log`; normal Gradle XML results hold the exact counts.
The integrated R2 deadline case records **40.131 seconds**, with zero failures;
the malformed callback, received-error matrix and reader-cancellation regressions
also pass on that rerun. All four adopted B3 files were byte-compared with the
immutable receipt and match exactly. Final merged default manifest and generated
Android/JVM constants were checked again after assembly.

The default merged manifest and generated Android/JVM identity remain aligned
with the official metadata URL and exact single-slash callback. The independently
prepared metadata contract and the predecessor's accepted/rejected paired override
checks are unchanged; transport/UI corrections do not deploy or mutate them.

## Runtime, operator and contribution boundaries

The local OAuth/posting/integration tasks can close once this composition's checks
are recorded. Their completion does not close these separate gates:

- `pano-atproto-remake-app-vault-native`: synthetic API 24/25/current Android
  Keystore/provider/fsync/no-backup/multiprocess/device fixtures and remaining
  Windows/macOS/native-image runtime qualification. No device, emulator or KVM
  was supplied; no such certification is claimed from a build.
- `pano-atproto-remake-metadata-deploy`: operator-approved official metadata/Pages
  publication and served contract verification. The code and guide do not deploy it.
- Real OAuth, service indexing and interactive UI/device validation remain
  unperformed and require their own explicit commission and evidence.
- Parent assessment and B3's independent frozen-candidate review remain separate
  from this implementation receipt. That active review was not read, duplicated,
  waited for or polled. The final production delta includes the small additional
  browser-launch correction, which the parent should account for when comparing
  the frozen candidate with this tree.

The [upstream guide](/doc/atproto.md) has only feature-code/test/session sources,
no dependency on this research corpus. Its application and metadata/Pages PR
outlines remain independent scopes. No push, PR creation, deployment, real-account
request, extra agent, `jj edit`, squash or history rewrite was performed.

Last reported context before this final evidence commit: **281,777** tokens
(lagging provider-request measurement, not cumulative traffic). Finish at this
useful boundary, comfortably before the requested 300k target; no review polling
or extra implementation lane was started.
