---
type: Operator brief
title: Prepared Pano AT Protocol contributions
description: Clean application and metadata bookmarks, current architecture, verification boundaries, proposed PR outlines and deliberate submission commands.
resource: /design/atproto-posting/contribution0.gpt6a.md
status: ready-for-operator-review
generated: { by: "model:openai/gpt-6-astra#xhigh", at: 2026-10-29 }
extensions:
  ticket: pano-atproto-remake-contribution
sources:
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Coordinated clean implementation and reviews, preserved worker handoffs, assembled author-preserving feature history and verified exact feature-tree equality
  - resource: "file:///home/rektide/src/pano-atproto-contribution/doc/atproto.md"
    title: Feature-only contributor guide and current native behavior
  - resource: "file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/native2.gpt61s.md"
    title: Combined portable and one-shot transport implementation checks and runtime bounds
  - resource: "file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/ui-corrections0.gpt61s.md"
    title: Final UI correction and actual affected-boundary tests
  - resource: "file:///home/rektide/src/pano-atproto-ui-review/design/atproto-posting/ui-review0.gpt61s.md"
    title: Independent exact-delta assessment resolves F1/F2/F3 and cancellation cleanup with no remaining substantiated local blocker
  - resource: "file:///home/rektide/src/pano-oauth-pages-pr/design/atproto-posting/metadata-handoff0.gpt61s.md"
    title: Independent native metadata contract, artifact proof and operator deployment runbook
  - resource: /design/atproto-posting/remake0.gpt6a.md
    title: Decisions, source lineage, independent findings and worker turnover history
  - resource: https://github.com/kawaiiDango/pano-scrobbler/issues/937
    title: Native Rocksky support request addressed in part by these contributions
---

# Prepared Pano AT Protocol contributions

**The two local contributions are prepared for operator/upstream review.**
Implementation, clean-series assembly and the independent final UI-delta
assessment are complete. No remaining substantiated blocker was found in the
reviewed local scope. No branch was pushed, PR opened or site deployed. The earlier GitHub fork
[`rektide/pano-scrobbler`](https://github.com/rektide/pano-scrobbler) exists for
the operator's eventual submission.

## The two contributions

Both start from upstream `990154031e761b8e5460a0585fc834b43c9e2d71`; the final
2026-10-29 fetch reported no upstream change.

| Contribution | Workspace | Bookmark | Head |
|---|---|---|---|
| Native application | `/home/rektide/src/pano-atproto-contribution` | `atproto-native-20261029` | `775d4fa61dce7ff17995a042cb9f31225d6d126c` |
| Native OAuth metadata / Pages | `/home/rektide/src/pano-oauth-pages-pr` | `atproto-metadata-ready-20261028` | `c45fce9d9bac1136b3bbc30df25111afda5a5636` |

The app contribution contains **19 capability/guide commits**, 84 changed files,
and the current code, tests and contributor guide. The metadata contribution is
the independently deployable two-file JSON/workflow change. In the metadata
workspace, its working copy contains separately bookmarked operator notes; use
the **code bookmark**, rather than assuming `@` is the intended PR head.

The application series duplicates owned stages without squashing or rewriting
their original history. The initial guide was extracted from a duplicate of its
mixed guide/evidence commit. Author identity and date match for all 19 carried
commits. Comparing the resulting tree with implementation handoff
`fd9992d6b3985a4c7216c0d3e66c9a376102b348` shows **only eight omitted `design/`
research files**: application code, tests, build configuration and guide are
byte-identical. This is the basis for carrying the implementation check receipts
to the prepared series; they are not falsely represented as fresh builds in the
new workspace.

The original experimental work remains at `atproto-before-remake-20261028`
(`5655abe8`). The full clean implementation and evidence remain at
`atproto-clean-20261028` (`fd9992d6`). Other independent protocol, metadata,
transport and review checkpoints remain available. The source-to-contribution
receipt is in the original workspace's
`.test-agent/contribution-assembly/receipt.json`; the stable final tree coordinates
above are sufficient to inspect the result without that scratch script.

## What the application does

```text
handle / DID → account and server discovery → external browser approval
  → native callback → durable account authority

completed play → granted + selected Rocksky/Teal destinations
  → authenticated records in the user's PDS → service indexing
```

The application owns **one narrow Kotlin protocol implementation**. It uses the
project's public Ktor/OkHttp, serialization and coroutine dependencies plus JCA.
There is no unpublished AT Protocol fork, Maven Local requirement, JavaScript
runtime or backend selector. The newer Web Scrobbler implementation informed its
ownership model; its browser-specific mechanism was not copied into Android.

- One account per service, with generation-bound references and independent
  destination choices constrained by actual granted permissions.
- Android browser callback and a desktop complete-callback-URL paste fallback.
  TV hides the unsupported authorization flow.
- A stable OS file lock coordinates UI/background processes. Credentials live
  in a dedicated vault; ordinary preferences contain only display/reference and
  selection data. Android uses Keystore encryption and no-backup storage;
  desktop uses owner-private plaintext files.
- Protocol-controlled nonce and invalid-token retries mint fresh proofs.
  Single-use credential POSTs have a real-engine one-shot body policy, avoiding
  hidden OkHttp replay. Received-error and ambiguous-rotation outcomes are
  distinguished durably.
- Partial destination acknowledgement survives retry; documented crash and
  ambiguous-response windows still permit duplicates. No exactly-once claim.
- Login commands are bounded and correlated with native attempts. Duplicate and
  distinct callbacks are handled deliberately, browser launches are checked for
  current identity/expiry, and preference failures produce actionable UI errors.

This addresses **native OAuth and completed scrobbles** from issue #937. It does
not implement the issue's broader now-playing, artwork, chart or social UI
aspirations. Unsupported love/delete controls are hidden. A successful PDS write
and its eventual appearance in Rocksky are different observations.

## Verification and remaining evidence

| Checked boundary | Actual evidence |
|---|---|
| Combined protocol/native prefix | 96 module tests and 20 AT Protocol app tests pass; Android debug APK builds. Full app 26/27: only the independently established YouTube-title trailing-space failure. |
| Final UI correction | 34 affected module tests and 34 AT Protocol app tests pass; JVM/Android compilation and debug APK build pass. The previous broader checks remain attributed to their checked predecessor. |
| Real transport | Hidden 503 replay correction, current POST forms, fresh-proof protocol retries, redirects and HTTPS CONNECT-before-TLS fixtures. |
| State/failure boundaries | Real child OS processes, process death, cancellation, disk faults, stale generations, publication/removal failure and restored callback/controller fixtures. Actual PAR and response-body deadline probes ran. |
| Prepared app series | All non-research files equal the qualified source; author/date preservation and guide provenance/link checks pass. |
| Pages artifact | SDK/native-profile checks, strict delivery helper tests, actual Jekyll action-image render with documented local fixture configuration, exact copied bytes and project-path HTTP serving. |

There is **no physical-device/emulator, live OAuth, service-indexing or interactive
UI certification**. Android API 24/25/current Keystore/provider/fsync/multiprocess,
Windows/macOS and native-image behavior remain explicit qualification work.
Relevant remaining tickets are `pano-atproto-remake-app-vault-native` and
`pano-atproto-remake-metadata-deploy`.

The final independent [UI-delta verdict](file:///home/rektide/src/pano-atproto-ui-review/design/atproto-posting/ui-review0.gpt61s.md),
commit `a4c5dae47d3832deab7199d34e99ae488626e29e`, resolves F1/F2/F3 and
cancellation/timeout cleanup on exact code `18da8b91` and guide `a17414ed`.
It found no remaining substantiated local blocker or new concrete blocker in
that delta. Earlier independent review found the credential architecture coherent
and proved real combined retry/grant behavior. The parent then rechecked that the
prepared contribution differs from reviewed source `fd9992d6` only by the eight
omitted research files. Tests and review are not human acceptance or release
certification.

## Metadata and development identity

The official contract is:

```text
client_id:    https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json
redirect_uri: io.github.kawaiidango:/pano-scrobbler/oauth/callback
scope:       atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create
```

The parent checked these unauthenticated URLs on **2026-10-29**: official metadata
returned **404**; the existing rektide deployment returned **200 JSON**, no
redirect, and the expected paired client/callback/scopes. The existing Pages
workflow needs maintainer dispatch and its F-Droid secrets; merging alone does
not deploy it. Its explicit final JSON copy guarantees artifact placement/bytes;
the tested Jekyll builder already copies the root JSON, so broad exclusions alone
are not the justification for that step.

For a developer build against the live metadata, use the matched pair:

```sh
PANO_ATPROTO_CLIENT_ID=https://rektide.github.io/web-scrobbler-pages/pano-scrobbler/oauth-client-metadata.json \
PANO_ATPROTO_REDIRECT_URI=io.github.rektide:/pano-scrobbler/oauth/callback \
bash gradlew :androidApp:assembleDebug
```

Identity changes require fresh authorization. Build/configuration tests did not
perform that authorization. The new contribution workspace intentionally contains
no private `local.properties` or signing material; configure those locally using
upstream instructions when building there.

## PR outlines and operator actions

### Pages: Publish native AT Protocol OAuth client metadata

- Motivation: a Pano-owned discoverable native client identity for #937.
- Scope: public JSON, DPoP/code/refresh, create-only collection permissions and
  deterministic final artifact inclusion.
- Evidence: native metadata/profile and artifact checks, with fixture limits.
- Coordination: exact callback and explicit maintainer deployment before an
  official-identity login can succeed.

### Application: Add native AT Protocol OAuth and Rocksky/Teal scrobbling

- User flow: handle/DID, external approval, native return, selected completed-play
  destinations, refresh and logout.
- Architecture: one publicly buildable protocol path, platform vault, generation
  authority, separate mappings and explicit retry/error contracts.
- Evidence: current qualified heads and tests, plus clearly separate native/live
  evidence gaps and the unchanged upstream test failure.
- Feature boundary: completed scrobbles; no claim of full Last.fm-style parity.

Use **Related to #937** while issue-closing scope remains a maintainer discussion.
The [feature guide](file:///home/rektide/src/pano-atproto-contribution/doc/atproto.md)
and [metadata runbook](file:///home/rektide/src/pano-oauth-pages-pr/design/atproto-posting/metadata-handoff0.gpt61s.md)
provide the implementation-owned detail.

Inspect locally first:

```sh
jj -R /home/rektide/src/pano-atproto-contribution diff --stat \
  --from main@origin --to atproto-native-20261029
jj -R /home/rektide/src/pano-oauth-pages-pr diff --git \
  --from main@origin --to atproto-metadata-ready-20261028
```

If and when the operator elects to publish, the explicit commands are:

```sh
jj -R /home/rektide/src/pano-oauth-pages-pr git push --remote fork \
  --bookmark atproto-metadata-ready-20261028
gh pr create --repo kawaiiDango/pano-scrobbler --base main \
  --head rektide:atproto-metadata-ready-20261028

jj -R /home/rektide/src/pano-atproto-contribution git push --remote fork \
  --bookmark atproto-native-20261029
gh pr create --repo kawaiiDango/pano-scrobbler --base main \
  --head rektide:atproto-native-20261029
```

These are instructions, **not executed actions**. Choose title/body and draft
standing deliberately from the outlines and evidence above.
