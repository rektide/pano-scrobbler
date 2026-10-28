---
type: Implementation brief
title: Clean Pano AT Protocol remake
description: Commission, preserved source, parallel implementation owners, architectural checkpoints, and handoff discipline for the clean contribution series.
resource: /design/atproto-posting/remake0.gpt6a.md
status: draft
generated: { by: "model:openai/gpt-6-astra#xhigh", at: 2026-10-28 }
extensions:
  ticket: pano-atproto-remake
sources:
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Operator commissioned two Sol xhigh workers, owned tickets, clean bookmarks and a remake modeled on current Web Scrobbler
  - resource: /design/atproto-posting/pr-brief0.gpt6a.md
    title: Earlier system explanation, contribution choices and PR outlines
  - resource: /design/atproto-posting/pr-readiness0.gpt6a.md
    title: Current upstream comparison and old implementation verification
  - resource: "file:///home/rektide/src/web-scrobbler-atproto/doc/atproto.md"
    title: Current extension-owned OAuth, durable stores and static callback contract
  - resource: "file:///home/rektide/src/web-scrobbler-atproto/src/core/scrobbler/atproto/oauth-client.ts"
    title: Inspected lifecycle ownership, DID-only account pointer and library-backed client construction
  - resource: "file:///home/rektide/src/rekon/remake/README.md"
    title: Capability-oriented construction and assessment checkpoints
  - resource: "file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/remake0.gpt61s.md"
    title: Sol's current-library probes and native account-authority proposal at f72378b4
---

# Clean Pano AT Protocol remake

## Current commission

The operator authorized a clean implementation line, informed by the newer
Web Scrobbler `atproto` design, with **two Sol xhigh workers**. Each worker owns
and maintains readable tickets. The deliverable remains local, reviewable work
and the knowledge needed to discuss and make PRs; the earlier boundary against
automatic submission continues.

The previous [discussion brief](pr-brief0.gpt6a.md) explains the system and
possible contributions. This commission advances from discussing those
possibilities to materializing a defensible clean series. Backend and lifecycle
choices must be grounded before substantial implementation depends on them.

## Pinned basis and preserved work

| Concern | Basis |
|---|---|
| Pano upstream | `990154031e761b8e5460a0585fc834b43c9e2d71`, unchanged by fresh fetch |
| Preserved implementation and research | `atproto-before-remake-20261028` at `5655abe8` |
| Clean app workspace | `/home/rektide/src/pano-atproto-remake` |
| Clean app bookmark | `atproto-clean-20261028`, created directly on upstream |
| Existing metadata checkpoint | `oauth-client-metadata-20261028` at `c45fce9d9bac1136b3bbc30df25111afda5a5636` |
| Metadata workspace | `/home/rektide/src/pano-oauth-pages-pr` |
| Metadata working bookmark | Worker-owned `atproto-metadata-ready-20261028` |
| Current Web Scrobbler model | `/home/rektide/src/web-scrobbler-atproto`, commit `e1267413e77b12c2ddaf93ae5586bb6643757106` |

The old source line and independent research are preserved. Clean app work
starts from the upstream tree, using the previous implementation as evidence.
Its old default-off library experiment is not an implicit requirement of the
new contribution.

## What the Web Scrobbler model contributes

The current extension owns OAuth through public `@atcute/oauth-node-client`
2.0.1. Its records, authenticated transport adapter, configuration, session
store and lifecycle are distinct concerns. Durable entries are scoped by client
identity, the active account holds a DID rather than another token copy, and
locks serialize lifecycle and refresh operations.

Those ownership choices are relevant to Pano. Their native implementation must
fit Kotlin and Pano's actual processes and persistence. Web Locks and a static
HTTPS callback relay are browser-specific mechanisms. Pano's intended callback
is a native URI; adopting the extension's architectural model does not imply
embedding its JavaScript library or transplanting its callback relay.

The substantial Kotlin choice is who owns the OAuth protocol implementation
and how that code is obtained reproducibly. The old full feature stack depends
on machine-local fork artifacts even when the default backend is selected.
The clean proposal must give an explicit dependency and maintenance account.

### First architectural decision

At worker checkpoint `f72378b4c057b41f26b8342deb7a5c24507cb2df`, the parent
selected **one narrow Pano-owned Kotlin protocol implementation**, using existing
public dependencies, for the clean line. The dual-backend experiment remains in
the preserved source history.

This choice uses current evidence: Sol exercised published `atproto-kotlin`
**9.13.0** with five probes against public Maven artifacts. They reproduce missing
callback/token binding and grant validation, insufficient discovery binding,
and missing resource-response nonce observation. These passing probes demonstrate
undesirable library behavior; they are not a conformance pass. The
[worker checkpoint](file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/remake0.gpt61s.md)
records the pinned sources, results and alternatives.

The next commissioned slice establishes a dedicated credential vault and stable
cross-process account lock. Android Keystore encryption and non-backup storage,
desktop user-private persistence, fresh session reads, and durable callback-state
consumption are implementation hypotheses to verify. General preferences should
not be locked through OAuth network work. The dedicated HTTP boundary must retain
Pano's proxy/TLS/user-agent policy and proxy invalidation, while allowing the
protocol layer to inspect OAuth error responses rather than encountering the
generic client's Last.fm-style response validator.

At this decision, only the clean upstream baseline and library probes had been
checked; no production feature code had yet been ported. Worker A resumed for
the bounded native-authority slice at 226,416 reported context tokens, with a
handoff requested around 280k–320k before a fresh worker takes the next slice.

## Parallel ownership and tickets

Epic: **`pano-atproto-remake`**.

The shared tracker is rooted at
`/home/rektide/archive/kawaiiDango/pano-scrobbler/.beads`; the convenient
`/home/rektide/src/pano-scrobbler-at/.beads` path is a symlink to it. Beads was
initialized in local/stealth mode with prefix `pano`, automatic exports, and
without agent instructions or hooks. Workers run from their own project
directories with that explicit `BEADS_DIR`.

| Owner | Scope | Session |
|---|---|---|
| Sol A | Clean app architecture, capability commits and relevant checks; owns `pano-atproto-remake-app` and meaningful child work | `ses_e7b3d8e43ffeUWIng4yiONPAnf` |
| Sol B | Native metadata contract, artifact/deployment evidence and independent integration assessment; owns `pano-atproto-remake-metadata` and subsequent scoped work | `ses_e7b3ccec5ffeuJpOmxDoerRcX6` |
| Parent | Architectural decisions, cross-workspace integration, evidence synthesis and operator-facing explanation | `ses_e7ba46f7fffeze8ND55fl0uJF8` |

Worker tickets carry scope and acceptance, append-only findings, exact heads and
checks, current pointers, and session provenance. Completion means the stated
local outcome was verified; upstream deployment and real-account approval
remain separate outcomes requiring their actual evidence.

## Consequential checkpoints

This table commissions assessment; it does not claim that the new trees already
pass their gates.

| Checkpoint | Required architectural argument | Evidence at that point |
|---|---|---|
| Construction direction | Native consumer boundaries; retained source capabilities; local/library dependency choice; coherent landing sequence | Pinned source comparison and a proposed owner map, before a large redesign |
| Durable lifecycle | Identity-bound pending/session state, refresh rotation, logout and account replacement; coordination fits `:bgScrobbler` and UI processes | Failure/restart and concurrency checks through the actual storage/coordination seam |
| Pano feature integration | Login/callback/provider/preferences fit native conventions; completed Rocksky/Teal writes and granted target controls compose | Module and application checks plus Android build and callback-contract inspection |
| Cross-workspace identity | Metadata, app defaults, generated manifest and developer overrides agree | Exact client/callback/scope comparisons on candidate heads and artifact verification |
| Contribution handoff | Reproducible dependencies; clear build instructions; accurate behavior and limitations; reviewable series | Committed heads, test/build receipts, current tickets and PR outlines |

Metadata preparation can proceed alongside app work. Actual deployment is a
runtime prerequisite for login using the official identity, rather than a
prerequisite for compiling the app or reviewing its protocol tests.

The feature destination is native authorization and completed scrobbles for
Rocksky/Teal. Rich now-playing, artwork, charts and social interfaces are the
broader issue's aspirations; the existing narrow implementation does not
establish them.

## Native identity rendezvous

```text
client_id:    https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json
redirect_uri: io.github.kawaiidango:/pano-scrobbler/oauth/callback
scope:       atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create
```

The public native client uses DPoP-bound tokens and authorization-code/refresh
grants. Worker B verifies the contract; worker A integrates its app-side use.
Developer overrides retain a way to exercise the already-live rektide identity.
Existing authorizations retain the identity that created them; fresh
authorization is needed to exercise the official client.

## Evidence and turnover

The old checked app's 52 passing module tests, 13 passing AT Protocol app tests
and successful debug builds are useful regression evidence. They do not certify
a reconstructed tree. Each new coherent capability needs its own relevant
checks; the final selected composition needs integration evidence.

The SDK's absolute `platform-tools/adb` command is available, but its device
listing was empty during this commission. Real-device authorization and live
Rocksky indexing cannot be inferred from mocked HTTP or a debug build.

Workers return at architectural checkpoints. Reported context usage governs
turnover: target a committed handoff around 240k–300k tokens when further work
remains, with 360k as the requested upper end. This is provider-reported context
usage, not cumulative input/cache traffic across requests. A worker should
finish or checkpoint useful work earlier rather than consume a quota.

Handoffs must leave a current ticket with workspace/bookmark/head, decisions,
checks, unresolved concerns and the next coherent slice. A fresh Sol can then
continue without reconstructing the effort from conversational memory.
