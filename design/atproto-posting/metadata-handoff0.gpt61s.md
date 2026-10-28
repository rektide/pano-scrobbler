---
type: Integration handoff
title: Pano native OAuth metadata — contract and operator handoff
description: Minimal Pages contribution, exact production and development identities, artifact evidence, deployment runbook, PR outline and native integration assessment plan.
resource: /design/atproto-posting/metadata-handoff0.gpt61s.md
status: ready-for-operator-review
generated: { by: "model:openai/gpt-6.1-sol#xhigh", at: 2026-10-28 }
sources:
  - resource: "opencode://ses_e7b3ccec5ffeuJpOmxDoerRcX6"
    title: Reviewed the current extension-owned OAuth model, verified the native contract and real Jekyll artifact, and prepared separate operator checks and handoff
  - resource: "opencode://ses_e7ba31b2dffepgpPzTw9R5f0g2"
    title: Prior metadata port and reported official SDK, native-profile and isolated-copy checks
  - resource: /oauth-client-metadata.json
    title: Prepared production native metadata, unchanged at c45fce9d
  - resource: /.github/workflows/static-site.yml
    title: Manual deployment triggers, post-Jekyll static copy and uploaded directory
  - resource: /_config.yml
    title: Existing Jekyll selection and keep_files settings
  - resource: "file:///home/rektide/src/pano-scrobbler-at/design/atproto-posting/pr-brief0.gpt6a.md"
    title: Earlier contribution boundaries, previous checks and operator-only PR preparation
  - resource: "file:///home/rektide/src/web-scrobbler-atproto/doc/atproto.md"
    title: Current extension-owned public OAuth and credential-free web callback contract
  - resource: "file:///home/rektide/src/web-scrobbler-atproto/src/core/scrobbler/atproto/oauth-store.ts"
    title: Awaited storage namespaced by client ID
  - resource: "file:///home/rektide/src/web-scrobbler-github/doc/oauth.md"
    title: Current static relay ownership and rollout boundaries
  - resource: "file:///home/rektide/archive/bluesky-social/atproto/packages/oauth/oauth-types/src/uri.ts"
    title: Official SDK single-slash private-use URI validation
  - resource: "file:///home/rektide/archive/bluesky-social/atproto/packages/oauth/oauth-provider/src/client/client-manager.ts"
    title: Official provider enforcement of reverse-client-host callback scheme
  - resource: https://atproto.com/specs/oauth
    title: Native redirects, metadata HTTP delivery and client-bound tokens; read 2026-10-28
  - resource: https://developer.android.com/guide/topics/manifest/data-element
    title: Android host and path intent-filter dependencies; read 2026-10-28
  - resource: /composeApp/src/androidMain/AndroidManifest.xml
    title: Upstream singleTop activity, exported deep links and separate bgScrobbler components
  - resource: /composeApp/src/androidMain/res/xml/data_extraction_rules.xml
    title: Upstream broad backup and device-transfer inclusion
---

# Pano native OAuth metadata — contract and operator handoff

**The existing two-file contribution is sufficient; no production correction was
needed.** This lane did not push, open a PR, deploy, authorize a real account or
modify worker A's application branch. Local preparation is distinct from live
deployment and application acceptance.

## Local deliverables and scope

| Reference | Purpose |
|---|---|
| Workspace `/home/rektide/src/pano-oauth-pages-pr` | Independent upstream-based metadata lane |
| Base `990154031e761b8e5460a0585fc834b43c9e2d71` | Inspected Pano upstream main |
| `atproto-metadata-ready-20261028` → `c45fce9d9bac1136b3bbc30df25111afda5a5636` | **Submit this code scope only:** JSON plus explicit workflow copy |
| `oauth-client-metadata-20261028` → the same commit | Preserved predecessor bookmark, not moved |
| `atproto-metadata-notes-20261028` | Separate operator helpers and this handoff; **not the tiny Pages PR** |
| `pano-atproto-remake-metadata` | Local preparation ticket in the shared tracker |
| `pano-atproto-remake-metadata-deploy` | Remaining operator/maintainer publication gate |

The [JSON](/oauth-client-metadata.json) contains public application metadata, not
credentials. The only [workflow](/.github/workflows/static-site.yml) change renames
the existing final copy step and adds the JSON beside its FAQ/privacy copies.
No new dependency, callback page, server, secret or deployment trigger is added.

Inspect the code contribution independently of the notes:

```sh
jj diff --git --from 990154031e761b8e5460a0585fc834b43c9e2d71 \
  --to atproto-metadata-ready-20261028
```

## Stable native-client contract

| Field | Official production value |
|---|---|
| `client_id` | `https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json` |
| `redirect_uri` | `io.github.kawaiidango:/pano-scrobbler/oauth/callback` |
| `application_type` | `native` |
| `token_endpoint_auth_method` | `none` — public client, no client secret |
| `dpop_bound_access_tokens` | `true` |
| `grant_types` | `authorization_code`, `refresh_token` |
| `response_types` | `code` |

Exactly these declared/requestable scopes:

```text
atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create
```

The callback scheme reverses the **metadata hostname**, not the Android package
ID or repository path. It has one slash after the colon and no authority.
The SDK's generic metadata schema alone does not establish the relationship
between callback scheme and client hostname; native-profile assertions are also
needed. The archived official provider enforces this relationship at source
revision `cd736b3dbbec957cf4eaa7e67fa5b8be8f8ae04e`.

### Development is a different identity

The live development pair remains:

```text
client_id:    https://rektide.github.io/web-scrobbler-pages/pano-scrobbler/oauth-client-metadata.json
redirect_uri: io.github.rektide:/pano-scrobbler/oauth/callback
```

Retain a deliberate build override for that **matched pair** while the official
URL is undeployed. Changing only the callback or only the metadata URL is invalid.
Do not add the development scheme to official metadata as a migration shortcut.

Tokens, DPoP keys and unfinished authorization attempts belong to the identity
under which they were issued. An official-identity build must not relabel a
development grant or refresh it with the new `client_id`. Require fresh
authorization across this switch; keep stored sessions and pending state
client-ID-scoped. Changing protocol-library implementation is a separate decision
and does not change this public metadata contract.

The metadata declares possible permissions, not the permissions a user actually
granted. The app must inspect returned scopes and enable writes only to permitted
targets. Neither scope grants update, delete, blob upload, now-playing or broader
account access. Supporting such features later would require deliberate changes.

## What transfers from the current Web Scrobbler model

Inspected local evidence:

- Extension: `e1267413e77b12c2ddaf93ae5586bb6643757106`, including `doc/atproto.md`,
  `oauth-config.ts`, `oauth-client.ts`, `oauth-messages.ts`, `oauth-store.ts` and
  `src/core/content/atproto-oauth.ts`.
- Hosting source: `96bd14c44312300581d2f10dc771c9597ee6f58e`, including
  `doc/oauth.md`, `src/routes/oauth/callback.tsx` and both metadata documents.

The extension owns public `@atcute/oauth-node-client` 2.0.1, durable awaited state
and session writes, client-ID namespaces and a DID-only active-account pointer.
Its hosted callback only relays the original query to the extension. It does not
exchange codes, receive tokens or own the session. These ownership and persistence
principles inform Pano; the TypeScript dependency is not an app dependency choice.

**Pano does not need that browser relay.** Its metadata selects an OS-delivered
native URI, not an HTTPS callback route. The native receiver must validate the
exact callback and pending state before exchange. OS intent delivery is not
authentication. Do not introduce a website-owned OAuth session, fragment-token
transfer or `postMessage` bridge into the native design.

The extension's Web Locks are browser-specific. Pano's real manifest declares
separate `:bgScrobbler` components, so equivalent lifecycle ownership must account
for Android processes rather than merely translating the lock API.

## Verification and limits

### Existing evidence retained

The predecessor session reported successful `@atproto/oauth-types@0.7.7`
metadata/discoverable-ID/scope schema checks, additional public/native assertions,
five negative controls, exact live-source port comparison, workflow parsing,
actual copy-command execution and local HTTP serving. These unchanged checks were
trusted, not represented as new executions. The generic skill's Python validator
rejects custom schemes and is not a reliable native-client oracle here.

### New checks in this lane

- Official source passes the separately maintained strict contract helper;
  development live metadata also passes.
- Five helper tests pass, including eleven contract mutations, missing fields,
  non-object/duplicate-member JSON, redirects, non-200 success status, wrong MIME
  type and oversized bodies. These are contract/delivery tests, not an OAuth flow.
- The actual `actions/jekyll-build-pages@v1` image was exercised with rootless
  Podman: `ghcr.io/actions/jekyll-build-pages:v1.0.13`, image digest
  `sha256:6791ebfd912185ed59bfb5fb102664fa872496b79f87ff8b9cfba292a7345041`.
- A 948-file tracked snapshot ran the workflow's real asset-copy commands and
  rendered Jekyll successfully. The original exact-config attempt failed at
  GitHub's authenticated Pages metadata lookup without a CI token. The successful
  **local fixture** adds only explicit `url`, `baseurl` and `description` to its
  scratch config, bypassing that lookup. No checkout config or machine packages
  were changed, and no account credential was passed into the container.
- Generated `index.html` exists, the F-Droid `keep_files` sentinel survives, and
  the metadata's source bytes survive Jekyll. The real post-Jekyll copy also
  overwrites deliberately stale fixture bytes with the correct JSON. Its upload
  path is `./static-site`. Serving that artifact under `/pano-scrobbler/` yields
  HTTP 200, `application/json` and exact bytes without a redirect.

**Correction to the earlier copy rationale:** the tested builder already copies
the root JSON despite the broad `exclude` setting. The explicit post-copy remains
a small, deterministic final byte/path guarantee matching existing workflow
practice; it is not proven necessary merely by reading the exclusion pattern.

Scratch reproduction files and build output:
`/tmp/opencode/pano-oauth-pages-pr/worker-b/` (`prepare-build.py`, `finish-build.py`,
`site-source/`). The successful render is not the full workflow: F-Droid generation,
secret setup, JSON-LD patching, artifact upload and deployment were not run.

### Repeatable operator checks

From this knowledge branch/workspace:

```sh
python -B -m unittest discover -s design/atproto-posting -p 'test_*.py' -v
python -B design/atproto-posting/verify-metadata.py --file oauth-client-metadata.json
python -B design/atproto-posting/verify-metadata.py --live --profile dev
```

[The stdlib helper](verify-metadata.py) checks the exact reviewed Pano contract,
not arbitrary OAuth metadata. Intentional metadata additions require updating its
expected profile and tests. It caps responses at 64 KiB and refuses redirects.
Its optional live mode only chooses the fixed URL for the selected profile; no
credentials are required. [Tests](test_verify_metadata.py) use a local HTTP server.

As of 2026-10-28, the official URL returns **404**. The development URL returns
**200**, `application/json; charset=utf-8`, with no redirect and the expected
native identity. No live account approval, native callback, refresh, PDS write or
Rocksky/Teal indexing was performed by this lane.

## Operator deployment runbook — not executed

1. Review the **code** bookmark and coordinate the eventual app contribution.
   The public native contract above is the shared interface, independent of the
   app's selected library/backend.
2. Merge only the selected Pages contribution. Merge alone **does not deploy**:
   the current workflow has only `workflow_dispatch` and `repository_dispatch`
   (`static_site`), not push/PR triggers.
3. A maintainer decides whether to dispatch that existing workflow. It also
   generates the F-Droid repository and requires `KEYSTORE_P12` and `CONFIG_YML`.
   Do not put those values into metadata, notes, test fixtures or a PR body.
   A standalone metadata/Pages workflow would be a separate architectural change,
   not something this lane silently creates.
4. After the approved deployment succeeds, inspect the Pages artifact and verify
   the exact official URL. With these operator helpers available:

   ```sh
   python -B design/atproto-posting/verify-metadata.py --live
   ```

   Require exactly HTTP 200, JSON MIME type, no redirect, exact body `client_id`,
   native single-slash callback and the reviewed create-only scopes. Record the
   deployed source revision and workflow run. A local render cannot replace this.
5. Build the app with the matching official defaults and merged Android manifest.
   Authorize afresh with an operator-selected test account. Record real callback,
   refresh and intended PDS-write observations separately from metadata success.

`pano-atproto-remake-metadata-deploy` remains open for this external gate. Its
existence is not permission for an agent to dispatch or deploy anything.

## Ready-to-adapt Pages PR outline

**Title:** Publish native AT Protocol OAuth client metadata

- **Motivation:** give Pano's native OAuth/completed Rocksky and Teal scrobbling
  contribution a Pano-owned discoverable client identity. Related to
  [issue #937](https://github.com/kawaiiDango/pano-scrobbler/issues/937); do not
  imply this JSON implements that issue's richer Rocksky features.
- **Changes:** public native metadata with DPoP, code/refresh grants and two
  create-only collection permissions; explicit copy into the final Pages artifact.
- **Security:** no client secret, published private key, website-owned session or
  new backend. Per-session DPoP keys remain app-owned.
- **Checks:** official SDK schemas plus native invariants (predecessor), exact
  contract/delivery helper tests, Jekyll action-image render with documented local
  fixture settings, post-copy bytes and project-path HTTP delivery.
- **Deployment/coordination:** existing workflow still requires maintainer
  dispatch/F-Droid secrets; official URL is currently 404. Deploy before shipping
  an official-identity app build and require fresh authorization across identities.
  No push, PR creation, deployment or real-account OAuth is claimed here.

Keep the helper, research and handoff commit out of that tiny code PR unless a
maintainer explicitly asks for them.

## Subsequent independent app-integration assessment

This is a review plan, **not a claim that worker A's unfinished app passes**. Start
from its exact returned head and ticket, without rewriting its implementation.

| Native boundary | Evidence to inspect and exercise |
|---|---|
| Client/default configuration | Production app constants equal this metadata. Development overrides change both ID and callback, consistently in common config and Android manifest placeholders. |
| Android delivery | Inspect the **merged** manifest/APK, not just source. Use a separate `VIEW`/`DEFAULT`/`BROWSABLE` native-scheme filter; existing `pano-scrobbler://screen` navigation still works. Exercise cold start and warm `singleTop`/new-intent delivery. |
| Single-slash URI parsing | This URI has no authority. Android path attributes are ignored without a host; adding a host would fail to match this callback. Require app-level exact scheme/authority/path checks and reject `://`, sibling paths, trailing-path variants and fragments. |
| Callback protocol | Require one unambiguous `state`, `iss` and `code` or denial. Reject wrong issuer, duplicate parameters, expired/missing/replayed state and a response for another client identity. Persist pending state before opening the browser. Test process death before return. |
| Grant bounds | Validate returned `atproto` and collection action grants. Teal-only, Rocksky-only, both and neither-write-grant sessions behave honestly; toggles do not manufacture permissions or broaden requested scopes. |
| Identity-bound sessions | DID-only account pointer; credentials/pending state scoped by client ID and DID. A rektide grant is not restored as official. Logout during callback/refresh cannot revive an account. |
| Durable refresh ownership | Actual Android background components run in `:bgScrobbler`. Check the selected coordinator/IPC or cross-process serialization and persistence failure behavior, not only an in-process mutex. |
| Native secret lifecycle | Check storage, backup/device transfer, logs and exports. Upstream backup rules broadly include files/databases/preferences; do not assume a new credential file is excluded. |
| Desktop/TV | Desktop fallback consumes the same full native URI without token/fragment import. Verify supported native packaging separately. TV remains hidden if native authorization is not supported. |

The preceding [system brief](file:///home/rektide/src/pano-scrobbler-at/design/atproto-posting/pr-brief0.gpt6a.md)
and [readiness report](file:///home/rektide/src/pano-scrobbler-at/design/atproto-posting/pr-readiness0.gpt6a.md)
describe the preserved old app, not evidence for the remake. Their dependency and
lifecycle warnings explain why a fresh assessment must bind claims to the new
application head. This handoff supersedes only the older Pages build limitation
and exclusion-rule rationale, not its unperformed live-device/deployment gates.
