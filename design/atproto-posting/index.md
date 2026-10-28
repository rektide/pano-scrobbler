---
type: Index
title: AT Protocol posting reading map
description: Current contribution preparation, implemented architecture, and earlier research and milestone records.
resource: /design/atproto-posting/index.md
status: draft
generated: { by: "model:openai/gpt-6-astra#xhigh", at: 2026-10-28 }
sources:
  - resource: "opencode://ses_e7ba46f7fffeze8ND55fl0uJF8"
    title: Connected the refreshed contribution brief with implementation, fork, and roadmap records
  - resource: /design/atproto-posting/pr-brief0.gpt6a.md
    title: Current system explanation and discussion-oriented PR outlines
  - resource: /design/atproto-posting/pr-readiness0.gpt6a.md
    title: Refreshed upstream comparison and validation evidence
---

# AT Protocol posting reading map

## Current contribution preparation

- [System and PR discussion brief](pr-brief0.gpt6a.md): start here for the app,
  metadata-hosting, and library boundaries; prepared Pages workspace; application
  scope choices; and proposed PR outlines.
- [Upstream readiness assessment](pr-readiness0.gpt6a.md): exact revisions,
  refreshed build/test evidence, Maven Local dependency, and pre-fork checkpoint.

## Implemented architecture

- [Local implementation](implementation1.unknown.md): original OAuth, record
  mapping, partial-post progress, and Pano adapter. Its old environment/deployment
  status is superseded by the current readiness assessment and brief.
- [Reversible library field path](upstream-field-path0.gpt56s.md): two complete
  backends, process-wide client ownership, session formats, and selection rules.
- [Staged library migration](upstream-migration0.gpt56s.md): dependency pins,
  Maven Local publication, and generic create-record adapter.

## Research and roadmap history

- [Library trade-off](library-tradeoff0.gpt56s.md): historical comparison of
  Pano-owned protocol code and Kotlin library candidates; reasons a qualified
  fork and a narrow dependency graph were considered.
- [Foundation research](research0-syn.unknown.md): original protocol and
  implementation direction preceding the local feature.
- [Consolidated milestones](milestone1.unknown.md): field validation, lifecycle
  hardening, and prerequisites for a library-backend default switch.
- [October 7 refresh](milestone2.glm53f.md): prior upstream adaptation,
  SDK/signing setup, and confirmation that rektide's metadata was deployed.
