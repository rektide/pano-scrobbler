---
type: Implementation brief
title: Clean Pano AT Protocol remake
description: Commission, preserved source, parallel implementation owners, architectural checkpoints, and handoff discipline for the clean contribution series.
resource: /design/atproto-posting/remake0.gpt6a.md
status: draft
generated: { by: "model:openai/gpt-6-astra#xhigh", at: 2026-10-28 }
updated: { by: "model:openai/gpt-6-astra#xhigh", at: 2026-10-29 }
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
  - resource: "file:///home/rektide/src/pano-oauth-pages-pr/design/atproto-posting/metadata-handoff0.gpt61s.md"
    title: Completed metadata preparation, actual Jekyll fixture and independent operator deliverables
  - resource: "file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/storage0.gpt61s.md"
    title: Implemented native storage boundary, platform factories, checked properties and turnover handoff
  - resource: "file:///home/rektide/src/pano-atproto-protocol-notes/design/atproto-posting/protocol-handoff0.gpt61s.md"
    title: Reusable transport fixes, regression evidence and remaining portable validation obligations
  - resource: "file:///home/rektide/src/pano-atproto-protocol-notes/design/atproto-posting/protocol-handoff1.gpt61s.md"
    title: Completed portable input, identity, discovery and record validation with exact adoption map
  - resource: "file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/oauth0.gpt61s.md"
    title: Executable typed native OAuth and leased transport checkpoint with current integration obligations
  - resource: "file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/native0.gpt61s.md"
    title: Activated native provider/UI prefix, source stages, tests and remaining adoption work
  - resource: "file:///home/rektide/src/pano-atproto-review/design/atproto-posting/lifecycle-review0.gpt61s.md"
    title: Independent frozen-head lifecycle review with real hidden-replay and error-boundary findings
  - resource: "file:///home/rektide/src/pano-atproto-ui-review/design/atproto-posting/ui-review0.gpt61s.md"
    title: Final independent exact-delta assessment resolves UI findings and cancellation cleanup
  - resource: /design/atproto-posting/contribution0.gpt6a.md
    title: Final operator-ready contribution coordinates, verified lineage, scope and PR outlines
---

# Clean Pano AT Protocol remake

## Current commission

The operator authorized a clean implementation line, informed by the newer
Web Scrobbler `atproto` design, with **two Sol xhigh workers**. Each worker owns
and maintains readable tickets. The deliverable remains local, reviewable work
and the knowledge needed to discuss and make PRs; the earlier boundary against
automatic submission continues.

Current coherent state: the local application and metadata contributions are
prepared, the portable code and all concrete review corrections are integrated,
and the final independent delta assessment found no remaining substantiated local
blocker. Native device/runtime and deployment evidence remain separate. Start
with the [operator brief](contribution0.gpt6a.md) for current coordinates and use
this document for the implementation and review history.

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
| Sol A, completed | Architecture and native storage foundation through `1e44bfac`; retired at 283,943 measured context tokens | `ses_e7b3d8e43ffeUWIng4yiONPAnf` |
| Sol A2, completed | Typed OAuth and native feature prefix through `a38b6272`; retired at 307,727 measured context tokens | `ses_e7b1c1da4ffeyw8XS4l257gfAC` |
| Sol A3, completed | Portable adoption, R1/R2/R3 integration and native feature qualification; finished at 289,542 measured context tokens | `ses_e7aedded3ffe8r5OwrYTHwM2nl` |
| Sol A4, completed | Bounded callback/browser/preference UI corrections and cancellation qualification; finished useful work at 157,580 tokens | `ses_e7ac533aeffebK2LtBfRou0pzm` |
| Sol B, completed | Metadata preparation and reusable transport fixes through `f8d7b93e`; retired at 317,410 measured context tokens | `ses_e7b3ccec5ffeuJpOmxDoerRcX6` |
| Sol B2, completed | Portable validation through `1a0c119e` and independent lifecycle review through `78fff9d8`; retired at 282,508 measured context tokens | `ses_e7b181b8effef7JO12Vhel0XwH` |
| Sol B3, completed | One-shot engine correction, independent combined review and exact UI-delta follow-up; finished at 316,881 measured context tokens | `ses_e7af14a6cffeDevLvL0joxsto6` |
| Parent | Architectural decisions, cross-workspace integration, evidence synthesis and operator-facing explanation | `ses_e7ba46f7fffeze8ND55fl0uJF8` |

Worker tickets carry scope and acceptance, append-only findings, exact heads and
checks, current pointers, and session provenance. Completion means the stated
local outcome was verified; upstream deployment and real-account approval
remain separate outcomes requiring their actual evidence.

### Metadata completion and reassignment

Sol B completed metadata preparation with the unchanged two-file code bookmark
`atproto-metadata-ready-20261028` at `c45fce9d`. Separate operator helpers, five
passing tests and the runbook/PR outline are committed on
`atproto-metadata-notes-20261028` at `2c30a896`. Its
[handoff](file:///home/rektide/src/pano-oauth-pages-pr/design/atproto-posting/metadata-handoff0.gpt61s.md)
records a successful render using the actual Jekyll action image and documented
local fixture configuration, final copy/byte checks and project-path serving.
It corrects the earlier exclusion-based rationale: that builder already copies
root JSON; the explicit final copy is a deterministic artifact guarantee.

`pano-atproto-remake-metadata` is closed for local preparation; the separate
`pano-atproto-remake-metadata-deploy` ticket remains an operator gate. The code
bookmark and the notes bookmark intentionally identify different contribution
scopes.

At 179,042 reported context tokens, Sol B was reassigned to reusable protocol
hardening in an isolated workspace based on the old self-contained checkpoint
`eeb47616eb9e8e33b03cc0cc5294b4baba77dace`. This work can proceed alongside
Sol A's new storage authority: crypto/encoding, identity/discovery, HTTP/DPoP,
pure record mapping and concrete regression proofs are its scope. The native
vault and replacement lifecycle interfaces remain with A. The next clean
implementation worker can deliberately adopt the corrected primitives and
migration obligations without replaying the old app integration.

### Native storage checkpoint and first turnover

Sol A delivered clean head `1e44bfac450db8e6ec3cdf4f4a96678cfc56b6e3`, comprising
core storage commit `dffcf585`, platform factory commit `06d280e4`, and a separate
[storage handoff](file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/storage0.gpt61s.md).
The implementation establishes client/DID/login-generation handles, durable
single-use pending claims, fresh reads under a stable OS file lock, and an
explicit interrupted-rotation marker. Android factories use Keystore encryption
under `noBackupFilesDir`; desktop files use owner-private permissions.

Reported checks: **17/17 storage tests**, including separate OS processes,
forced process death and cancellation; JVM compilation; Android debug APK
assembly. The parent read the implemented boundary and accepted it as an
internal foundation. The storage ticket is closed; its separate native-runtime
qualification ticket remains open. These results do not establish actual OAuth
or PDS behavior.

The first worker was turned over at **283,943** reported context tokens. Fresh
Sol A2 takes the same workspace for typed protocol behavior and eventual Pano
integration, while B continues isolated primitive hardening. In particular,
`updateSession` marks credentials before potentially rotating work: ordinary
authenticated requests need a suitable non-rotating locked operation rather
than quarantining a session after every harmless network error. Protocol error
classification and durable rotation now activate their own evidence obligations.

### Reusable transport checkpoint and second turnover

Sol B delivered code `f8d7b93edab8553f667b7d6ddf08c18b5f2f33d0` on
`atproto-protocol-hardened-20261028`, with separate notes at `508ceaff`.
The carryable delta consists of four HTTP source files and targeted tests:
genuinely streaming bounded responses, strict UTF-8, dedicated no-redirect/retry
client guards, exact authentication-challenge parsing, required nonce
observation, distinct authorization/resource nonce contexts, and a single
fresh-proof retry even when the nonce value is unchanged.

The fixed module passes **55/55 tests**. All nine added transport regressions
fail on a freshly built old checkpoint. Five further observation probes
demonstrate malformed old callback, expiry, JWK, PKCE and grant handling; their
passing result records bad behavior and must not be called conformance.
The [transport handoff](file:///home/rektide/src/pano-atproto-protocol-notes/design/atproto-posting/protocol-handoff0.gpt61s.md)
pins exact files, original-failure evidence, integration duties and remaining
validation findings. Its stable notes workspace preserves that evidence while
the implementation workspace advances from the separate code bookmark.

The second worker turned over at **317,410** reported context tokens. Fresh B2
continues portable validation in the isolated source workspace; A2 owns new
callback/token and lifecycle composition. A2's ticket received the immutable
transport receipt and malformed-callback/expiry probes. The whole old branch
is not the clean integration input. In particular, nonces must become durable
within the new native authority, and actual engine redirect/retry policy must
match the utility guard's assumptions.

### Executable OAuth and portable adoption rendezvous

A2 returned `def70d8a42e58c60de520ae154f03c6c7bb97fef`, with source through
`04a2cda4`: non-rotating locked requests, pre-discovery reservations, typed native
PAR/callback/token/refresh behavior, durable nonce handling and leased HTTP engines
that retire safely on proxy changes. Reported checks pass **41 module tests**, one
real OkHttp test covering redirect statuses 301/302/303/307/308, and Android debug
assembly. Read the [OAuth checkpoint](file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/oauth0.gpt61s.md).
The parent reviewed this boundary and resumed A2 at 209,471 context tokens for
record submission, safe resource-token recovery and generation-safe native
account/UI integration. Optional token expiry needs an explicit interoperability
policy; it must not be described as a mandatory OAuth field without a basis.

B2 then delivered portable code `1a0c119e372af16298b004d81aebf21e657b3015`, notes
`9194a163`, and **86 passing module tests**. New original-baseline probes executed
22 tests with 17 expected failures and five positive controls. Corrections cover
imported P-256 material, PKCE, granular permissions, current identity/discovery
rules, duplicate/depth-bounded JSON, and bounded Unicode-safe record conversion.
Current Rocksky source confirms milliseconds; the older separate schema archive's
seconds description does not override it. Teal remains seconds.

The [portable adoption map](file:///home/rektide/src/pano-atproto-protocol-notes/design/atproto-posting/protocol-handoff1.gpt61s.md)
names precisely which files and tests carry over. A2's ticket now has that
immutable receipt. Its new native authority and constructors remain authoritative;
only the target-grant portion of the obsolete poster is reusable. The full
serialized outgoing request bound and actual record response validation belong
to the new writer, not the pure mapping tests.

B2 finished the portable slice at 195,558 measured context tokens and continues
with a bounded independent review of frozen clean head `def70d8a`, rather than
reading A2's active tree or padding the turnover budget. Already acknowledged
provisional imports are distinct from defects in the new native composition.

### Native feature preview and review corrections

A2 delivered `a38b6272a06c806c6ad81c6ea02200a0fedd082e`: completed-play provider,
durable partial-target progress, secret-free generation-fenced account projection,
native browser/callback/paste UI, destination preferences, TV exclusion and hidden
unsupported actions. The [native handoff](file:///home/rektide/src/pano-atproto-remake/design/atproto-posting/native0.gpt61s.md)
reports 49 passing module tests, five passing AT Protocol app tests, full app
11/12 with the known upstream YouTube-title failure, and successful Android debug
assembly. Generated constants and the merged manifest were inspected. Final B2
portable adoption remains an explicit successor obligation.

The independent [frozen lifecycle review](file:///home/rektide/src/pano-atproto-review/design/atproto-posting/lifecycle-review0.gpt61s.md)
at `78fff9d8`, examining `def70d8a`, established three corrective tasks:

- **R3, P1:** actual OkHttp 5.5.0 resends a credential POST and identical DPoP
  proof on `503` with `Retry-After: 0`, despite disabled connection retries.
  B3 owns a focused engine-body replay correction and real-wire regression.
- **R1, P2:** a malformed callback can put its code/state in a raw URI parser
  exception chain. A3 owns sanitization at that untrusted-input boundary.
- **R2, P2:** received HTTP errors can inconsistently quarantine a refresh,
  including an actual body deadline after 503 headers. A3 owns consistent durable
  classification followed by propagation of the original failure/cancellation.

The review also proved generation fencing against an older in-flight exchange,
stale-handle denial and safe use of new durable credentials after an error that
occurred after atomic replacement. The six focused probes are observation/safety
evidence, not a blanket conformance pass. The review ticket's closure means
delivery, not feature approval.

A2 and B2 turned over at 307,727 and 282,508 context tokens respectively. Fresh A3
inherits the app and exact portable/review receipts; fresh B3 owns only the
independent engine correction. A3's final composition must adopt that correction
before readiness is claimed. Parent source review additionally asks UI tests to
cover late global login-state outcomes, duplicate callback delivery and retained
browser-launch effects alongside the already fenced credential generation.

### Final UI corrections, independent assessment and prepared series

A4 corrected F1 callback arbitration, remaining F2 logout/expiry obligations,
F3 preference-action error handling and current-work timeout cleanup at
`18da8b912068a314493fef278c7b4c8290c800dd`, with guide `a17414ed` and evidence
`fd9992d6`. Reported final affected checks pass 34 module tests, 34 AT Protocol
app tests and Android debug assembly. The actual PAR deadline case takes
40.173 seconds and permits a subsequent attempt. Prior broader module/full-app
results remain attributed rather than represented as rerun.

B3's independent exact-delta report `a4c5dae47d3832deab7199d34e99ae488626e29e`
resolves all three UI findings and cancellation cleanup, with no remaining
substantiated local blocker or new concrete blocker found in that delta. Its
[source/test assessment](file:///home/rektide/src/pano-atproto-ui-review/design/atproto-posting/ui-review0.gpt61s.md)
is deliberately narrower than device/runtime or live-account certification.

The parent assembled 19 capability/guide commits on
`atproto-native-20261029` at `775d4fa61dce7ff17995a042cb9f31225d6d126c`, in
`/home/rektide/src/pano-atproto-contribution`. No squash or original-history
rewrite was used; author identity/date preservation was checked. The resulting
feature tree equals source `fd9992d6` exactly except for eight omitted research
files. The independent two-file metadata bookmark remains
`atproto-metadata-ready-20261028` at `c45fce9d`. Both remain unpushed.

The final [operator brief](contribution0.gpt6a.md) holds the architecture, current
branch map, qualification bounds, two PR outlines and manual publication commands.
Local preparation is complete; native runtime and authorized metadata deployment
remain separately tracked. A final upstream fetch on October 29 changed nothing.

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

### Pano integration obligations to carry forward

The parent inspected the old self-contained checkpoint's app delta against
upstream (`99015403..eeb47616`) while the workers owned storage and protocol.
Its useful native integration should be reconstructed deliberately:

- Preserve `AccountType.ATPROTO(10)` and the existing one-account-per-service
  behavior of `Scrobblables.add`; a client/DID-scoped vault does not by itself
  commission a multi-account UI.
- Route login from onboarding/preferences; hide it on TV. The previous feature
  already limited AT Protocol to the scrobbles tab and hid love/delete actions in
  `ScrobblesScreen`. Retain those truthful capability boundaries.
- Feed both Android callbacks and desktop paste through one strict native
  callback authority. Keep the existing `pano-scrobbler://screen` navigation
  filter separate. Inspect the merged manifest, not only Gradle placeholders.
- Replace the old `LoginViewModel`-owned OAuth instance with the new authority.
  The old login screen's global “any AT Protocol account exists” completion
  observation must not prematurely complete a replacement login or cancel its
  callback merely because an earlier account is present.
- Preference toggles previously read credential scopes directly from
  `MainPrefs`. Reconstruct them from a secret-free authoritative account/grant
  view; enforce the same grant intersection at write time. UI selection cannot
  grant a missing permission.
- `Scrobblables` caches providers per process. A retained provider must obtain
  current authority for its DID/client and must not republish credentials after
  logout or account replacement. Ordinary account/display persistence should
  not become a second token store.

These are source-derived integration requirements and review targets. Their
presence here is not a claim that the unfinished clean app implements them.

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
The SDK has no installed emulator and `/dev/kvm` is absent. Native Keystore,
actual Android multiprocess/API 24–25, Windows/macOS and Graal runtime checks
remain explicitly tracked rather than inferred from Linux host tests.

Workers return at architectural checkpoints. Reported context usage governs
turnover: target a committed handoff around 240k–300k tokens when further work
remains, with 360k as the requested upper end. This is provider-reported context
usage, not cumulative input/cache traffic across requests. A worker should
finish or checkpoint useful work earlier rather than consume a quota.

Handoffs must leave a current ticket with workspace/bookmark/head, decisions,
checks, unresolved concerns and the next coherent slice. A fresh Sol can then
continue without reconstructing the effort from conversational memory.
