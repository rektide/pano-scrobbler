---
type: Discussion brief
title: Understanding the Pano AT Protocol contribution
description: System boundaries, refreshed evidence, prepared Pages change, and proposed PR outlines for the operator's scope discussion.
resource: /design/atproto-posting/pr-brief0.gpt6a.md
status: draft
generated: { by: "model:openai/gpt-6-astra#xhigh", at: 2026-10-28 }
sources:
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Commissioned refresh and Pages port, recorded the operator's preparation boundary, and connected the later clean-remake commission
  - resource: "opencode://ses_e7ba37263ffeyDOE27UVF3xl6Q"
    title: Delegated upstream comparison, application build and test evidence, and contribution-boundary assessment
  - resource: "opencode://ses_e7ba31b2dffepgpPzTw9R5f0g2"
    title: Sol xhigh implemented and validated the independent upstream Pages metadata contribution
  - resource: https://github.com/kawaiiDango/pano-scrobbler/issues/937
    title: Native Rocksky OAuth and richer integration request
  - resource: /design/atproto-posting/pr-readiness0.gpt6a.md
    title: Exact refreshed revisions, checks, and unresolved evidence
  - resource: /design/atproto-posting/implementation1.unknown.md
    title: Local implementation architecture and record behavior
  - resource: /design/atproto-posting/upstream-field-path0.gpt56s.md
    title: Dual OAuth backends, ownership, and incompatible session formats
  - resource: /design/atproto-posting/upstream-migration0.gpt56s.md
    title: Fork dependency coordinates and local-publication mechanism
  - resource: /design/atproto-posting/library-tradeoff0.gpt56s.md
    title: Historical reasons for the narrow local implementation and qualified library fork
  - resource: "file:///home/rektide/src/pano-oauth-pages-pr/oauth-client-metadata.json"
    title: Reviewed prepared public native-client identity and permissions
  - resource: "file:///home/rektide/src/pano-oauth-pages-pr/.github/workflows/static-site.yml"
    title: Reviewed artifact copying and existing manual deployment requirements
---

# Understanding the Pano AT Protocol contribution

**Follow-on:** the operator subsequently commissioned a clean implementation
with two Sol xhigh workers and worker-owned tickets. The
[remake brief](remake0.gpt6a.md) owns that active work; this document remains the
system explanation and discussion-oriented PR outline.

**Original preparation commission: equip the operator to discuss and make the PRs.** The
operator clarified this after the initial submission request. No branch was
pushed and no PR was created. The GitHub fork
[`rektide/pano-scrobbler`](https://github.com/rektide/pano-scrobbler) had already
been created, with local remote `fork`, before that clarification.

At this preparation checkpoint the application contribution scope was still
open. The subsequent [remake decision](remake0.gpt6a.md#first-architectural-decision)
selects a single narrow Pano-owned implementation using current library evidence.
The Pages change is committed locally in its own workspace and bookmark.
[Reading index](index.md).

## 1. The system in one picture

```text
Pano detects a completed play
  → Pano AT Protocol provider converts its track metadata
  → selected OAuth backend authenticates a repository write
  → user's Personal Data Server stores the record
  → Rocksky / Teal can index and display it

Login:
handle → stable account DID → Personal Data Server → authorization server
  → browser approval → native app callback → persisted session

Authorization server fetches Pano's public client-metadata JSON from Pages.
```

The **Personal Data Server (PDS)** holds the user's AT Protocol repository.
Rocksky and Teal interpret their respective record collections. Creating a
record successfully establishes a PDS write; seeing it in Rocksky additionally
depends on Rocksky's indexing. That latter behavior has not been field-verified.

### Three distinct project relationships

| Project | Its role here |
|---|---|
| Pano Scrobbler upstream | Application and eventual official OAuth identity owner; target of issue #937. |
| Web Scrobbler Pages fork | Currently hosts Pano's static client metadata under rektide's domain. The app's runtime does not depend on a Web Scrobbler service to submit a play. |
| `atproto-kotlin` fork | Alternative OAuth/HTTP library backend compiled into the current experimental app stack. Separate from metadata hosting. |

Moving the JSON to Pano's Pages site changes the app's declared OAuth identity.
Choosing the local or library-backed implementation changes who owns protocol
code. **Those are independent decisions.** Both backends can use either correctly
configured metadata deployment.

## 2. What is already implemented

- Browser-based OAuth with account/server discovery, persistent pending login,
  callback checks, refresh, and logout.
- PKCE binds the authorization code to the initiating app; DPoP binds tokens
  to the session's private key and signs authenticated HTTP requests.
- Completed-play writes to `app.rocksky.scrobble` and `fm.teal.feed.play`.
  Both targets are enabled by default, bounded by the permissions actually
  granted; preferences can select the desired targets.
- Pano retains track mapping and partial-post progress. If one service's record
  succeeds and the other fails, later retries can skip the successful target.
  A crash between the network response and local persistence can still duplicate
  a record; this is not transactional exactly-once delivery.
- Android receives a custom-scheme callback. Desktop has a callback-URL paste
  path. The TV UI hides this provider.

Useful source entry points:

| Concern | Code |
|---|---|
| Pano's scrobble adapter and retry progress | [AtProtoScrobblable.kt](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoScrobblable.kt) |
| Target grants and posting orchestration | [AtProtoPoster.kt](/atproto/src/main/kotlin/com/arn/scrobble/atproto/posting/AtProtoPoster.kt) |
| Record fields and units | [Records.kt](/atproto/src/main/kotlin/com/arn/scrobble/atproto/posting/Records.kt) |
| Pending login, sessions, and progress storage | [PanoAtProtoStore.kt](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoStore.kt) |
| Backend choice and session compatibility | [AtProtoFieldPath.kt](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/AtProtoFieldPath.kt) |
| Client identity and requested scopes | [PanoAtProtoConfig.kt](/composeApp/src/commonMain/kotlin/com/arn/scrobble/api/atproto/PanoAtProtoConfig.kt) |

Issue #937 also describes now-playing, album art, charts, and social features.
The implemented contribution covers authentication and completed scrobbles.
The AT Protocol provider currently supplies no remote history, charts, social
reads, now-playing updates, album-art enrichment, love/unlove, or deletion.
That boundary should be clear in a PR description.

## 3. Why there are two OAuth backends

The initial implementation uses Pano's own isolated `:atproto` module, built on
Ktor, Kotlin serialization, coroutines, and Java cryptography. Pano owns its
OAuth/discovery/DPoP/session code.

Later work qualified an `atproto-kotlin` fork with protocol-conformance patches
and added a reversible field-test path. It supplies OAuth and authenticated
`XrpcClient` transport; Pano still owns its application storage adapters, target
selection, and custom Rocksky/Teal records. The large generated models library
is not used. Historical library findings are in the
[trade-off](library-tradeoff0.gpt56s.md); they are not a new survey of today's
library releases.

The build selector is `-Patproto.useUpstream=true`, defaulting to `false`.
Here **“upstream” in the selector means the patched Kotlin library path**, not
the Pano main branch or the official Pages hostname. Session representations
differ; switching implementations requires deliberate account removal and
fresh login rather than automatic conversion.

**The current build always compiles against the fork**, whichever backend is
selected. Its dependencies are:

```text
io.github.rektide.atproto:runtime:9.11.0-pano.1
io.github.rektide.atproto:oauth:9.11.0-pano.1
```

They were published into this machine's Maven Local cache. Turning the selector
off does not make the full current branch independently buildable from public
dependencies. The [migration report](upstream-migration0.gpt56s.md#fork-publication)
records how the fork artifacts were produced.

### The application PR scope discussion

| Possible first contribution | What the maintainer would take on | Preparation still needed |
|---|---|---|
| Local implementation checkpoint | Narrow, Pano-owned protocol implementation and its tests | Select the checkpoint, coordinate official identity, validate that exact branch and real-device behavior. |
| Fork-backed current work | Shared library plus its qualification/patch and distribution story, currently alongside the local backend | Establish reproducible dependency acquisition; decide which backend(s) the PR should expose and verify the selected scope. |

The refresh agent located the pre-fork checkpoint at
`eeb47616eb9e8e33b03cc0cc5294b4baba77dace`. It contains the complete local feature
and prior upstream UI adaptations. It is a candidate contribution boundary,
**not an adopted decision or a newly prepared app branch**. The existing
experimental stack remains intact.

## 4. Prepared Pages contribution

| Item | Value |
|---|---|
| Workspace | `/home/rektide/src/pano-oauth-pages-pr` |
| Bookmark | `oauth-client-metadata-20261028` |
| Base | `990154031e761b8e5460a0585fc834b43c9e2d71` |
| Commit | `c45fce9d9bac1136b3bbc30df25111afda5a5636` |
| Changed files | `oauth-client-metadata.json`, `.github/workflows/static-site.yml` |

```text
client_id:    https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json
redirect_uri: io.github.kawaiidango:/pano-scrobbler/oauth/callback
scope:       atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create
```

This is a **public native client**: the JSON declares app identity, callback,
and permissions, without a client secret or user tokens. The authorization
server fetches it during login. Its `client_id` must exactly equal its served
URL, and the app's callback must exactly match the declared redirect.

The workflow copies JSON into the Pages artifact after Jekyll because the site
configuration broadly excludes non-Markdown input. Deployment is currently
manual/repository-dispatch, coupled to the existing F-Droid publication steps
and secrets. Merging the two files alone does not deploy them.

Sol validated the metadata with official SDK schemas and native-client checks,
and exercised the workflow's copy commands and local HTTP serving. A full
Jekyll build was blocked by missing Ruby development headers. The target URL
still returns 404; live deployment verification remains with the eventual
deployment. Sol's scratch evidence is under
`/tmp/opencode/pano-oauth-pages-pr/`.

The app still defaults to the live rektide-hosted metadata and
`io.github.rektide:/pano-scrobbler/oauth/callback`. Adopting the official identity
will require coordinated defaults in `composeApp/build.gradle.kts` and
`androidApp/build.gradle.kts`, plus build instructions. Existing sessions retain
their original identity; exercising the new client requires fresh authorization.

## 5. Refreshed evidence

Upstream `main` remains `99015403`; the application was already based on it.
The refresh required no rebase or application source changes. On application
head `4a8f19f3`, the delegated checks found:

- Protocol tests: **52/52 pass**.
- App tests: **19/20 pass**, including all **13 AT Protocol tests**. The sole
  failure is the previously documented YouTube-title trailing-space assertion.
- Android debug builds: both selectors succeed; default APK signature verified.
- The last assembled APK selects the **fork backend**; rebuild with an explicit
  selector before using that output for a particular field test.

Full commands and bounds are in the [readiness report](pr-readiness0.gpt6a.md).
These checks are build/unit-test evidence, not live OAuth or Rocksky indexing
evidence. Real approval, callback/process-death, posting, and refresh remain
unrecorded. A selected contribution branch will need checks after its identity
and dependency choices are applied.

Two existing lifecycle concerns also matter to review: tokens and keys remain
in the general preferences store; refresh coordination is process-local while
Android declares separate `:bgScrobbler` components. Their resolution is part of
the [existing roadmap](milestone1.unknown.md#roadmap), rather than something
established by the successful debug build.

## 6. Proposed PR outlines

### Pages PR: Publish native AT Protocol OAuth client metadata

1. **Motivation:** give the native Rocksky/Teal integration discussed in #937 a
   Pano-owned, discoverable OAuth identity.
2. **Change:** add public native-client JSON with create-only scopes; copy it
   into the existing Pages artifact after Jekyll.
3. **Verification:** official SDK/native-profile validation and artifact-copy
   checks; state the full-build and live-deployment bounds.
4. **Deployment:** maintainer dispatches the existing workflow, then checks 200,
   `application/json`, no redirect, and exact body `client_id` equality.
5. **Coordination:** document the callback URI and link the eventual app PR.

### App PR: Add native AT Protocol OAuth and Rocksky/Teal scrobbling

1. **Motivation and scope:** address the login and completed-scrobble portion of
   #937 through direct AT Protocol repository writes.
2. **User flow:** add account by handle, approve in browser, return to Pano,
   select targets, scrobble, and log out.
3. **Architecture:** explain the chosen protocol backend, app-owned persistence,
   granted-scope checks, record mapping, and partial-target retry behavior.
4. **Dependencies and deployment:** state the selected branch's reproducible
   build requirements and the Pages identity prerequisite.
5. **Evidence:** report checks on the actual proposed head and separate device
   observations from unit tests; include remaining lifecycle work explicitly.
6. **Feature boundary:** describe the completed-play contribution and the richer
   Rocksky UI features left for subsequent work.

Use `Related to #937` while the intended issue-closing scope is under discussion.
The first substantive question is **who should own the OAuth implementation in
the proposed contribution: Pano's local module, or a distributed and qualified
library fork?** That choice sets the app PR's review and maintenance burden.

## Inspect the prepared work

These commands only inspect local state:

```sh
jj -R /home/rektide/src/pano-oauth-pages-pr diff --git \
  --from main@origin --to oauth-client-metadata-20261028
jj -R /home/rektide/src/pano-scrobbler-at log --no-graph \
  -r 'main@origin..eeb47616eb9e8e33b03cc0cc5294b4baba77dace'
```
