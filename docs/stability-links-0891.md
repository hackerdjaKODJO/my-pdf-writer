# Stability and PDF↔note links — 0.8.91

This document records the evidence, scope and invariants for the 0.8.91
stability/linking pass. Competitor analysis is behavioral only; no code, assets,
fonts, templates or branding are copied.

## Method

- Target source: My PDF Writer `0.8.90` / commit `5443b24`.
- Read-only source audit plus static APK inspection. No competitor app was
  executed; no device/process-death failure was reproduced.
- Tools: project source/tests, existing decompiled outputs, `aapt2`, and the
  Androguard 3.3.5 DEX parser. No tools were installed.

## Reference artifacts

| App | Package / version | SHA-256 |
| --- | --- | --- |
| Samsung Notes | `com.samsung.android.app.notes` / `4.4.48.15` | `484d3dd599355789508034ba90af9a35e09f81532564a992c131710632a467ce` |
| Flexcil | `com.flexcil.flexcilnote` / `1.5.1.4` | `76ede392aed40bcb89d7ec5500b925b773995d1868eb879278dbab9a30621bdb` |
| StarNote | `com.onyx.galaxy.global.note` / `1.5.4.1` | `0d7ec1d4de20cc0565215ee757d38a0fcf29b3d892def4944317dfd01b608897` |
| MyReader | `com.myreader.reader` / `2.11.43` | `5b62d7536d521d0b118e268635bccb0c5b36575212d253cece7c4c93cd4905b2` |
| Ridi | `com.initialcoms.ridi` / `26.11.1` | `e77ffd2c0c0801430d90922977f388de336805b52595e95986c5c005951e02de` |
| Maru | `my.geulga` / `4.13.11` | `0da0dee1cab4521419cc519c33f47475b1e75c569295bde4c1ffce9e27c70f59` |

## Observed reference patterns

- Ridi separates immutable `bookId` from mutable source location and stores
  per-book metadata/annotations.
- MyReader PDF highlights persist page + rectangles + quote; EPUB re-anchors by
  quote after reopen.
- Reader progress is debounced but flushed on pause/Back; instance state covers
  short-term recreation.
- Recents are an index: missing sources need explicit relink/remove handling.
- Annotation mutations use serialized/transactional writers in the stronger
  references.
- A saved mark should retain a structured locator, not only a document URI.
- MyReader/Ridi/Maru reinforce three applicable patterns: stable document ID
  separate from mutable location, page/rect/quote anchors, and debounced state
  flushed on pause/stop. Ridi's per-book metadata and MyReader's PDF highlight
  geometry are the closest references for this patch.
- Flexcil's serialized reference has one record with source and destination
  endpoints (URI, name, page index, frames, text) plus explicit document/page key
  remapping. Samsung Notes keeps a UUID document registry and stable `PageId`
  for bookmarks across page insert/delete. StarNote exposes page-link and
  excerpt-backlink resources with invalid-source/link-missing states. These
  corroborate one durable bidirectional link with explicit resolution failure
  states; full stable `PageSpec.id` migration is deferred.

## Current critical defects

1. Different PDFs with the same name can merge into one study notebook.
2. Note links store current viewer indices, not original source-page ordinals.
3. A detached study pane can overwrite restored notebook data.
4. Same-URI PDF replacement silently reuses old sidecars and links.
5. Notebook write failure is reported as capture/close success.
6. Pane jumps/drag payloads are not bound to the active document.
7. Same-document rapid close/reopen can supersede a pending final save.
8. Deleting canonical sidecars can leave recoverable `.tmp` data behind.
9. Backup/restore is not mutation-exclusive and the busy sheet is dismissible.
10. No lifecycle flush guarantees recent edits after background/process death.

## 0.8.91 design

### Additive link model

Keep all existing fields. Add optional `NotePage.sourceLink`:

```json
"sourceLink": {
  "uri": "content://...",
  "docId": "<sha256-of-uri>",
  "sourcePage": 2,
  "fingerprint": "<sha256-of-source-bytes>",
  "rect": { "left": 0.1, "top": 0.2, "right": 0.7, "bottom": 0.3 },
  "excerptId": "<optional excerpt id>",
  "rotation": 0
}
```

Compatibility:

- Legacy notebooks still load from root `sourceDocumentUri` + `sourcePage`.
- Malformed optional links fall back to legacy fields.
- `noteId`/`pageId` remain the containing notebook/page IDs.
- JSON/backup version stays compatible because the field is additive.

### Identity and resolution

- Exact URI/docId identity only when both sources have a URI.
- Name/title fallback is allowed only for legacy sources with no URI.
- A content fingerprint detects same-URI replacement.
- `sourcePage` is the original source ordinal. Jump resolution finds the current
  `PageSpec.sourcePage` match after insert/delete/move/rotate/reset.
- Resolution returns `Found`, `WrongDocument`, `SourceChanged`, `PageMissing`,
  `SourceUnavailable` or `Ambiguous`; it never silently clamps to the last page.

### Pane and capture binding

- Drag payloads carry docId and optional excerptId.
- Pane append/jump verifies the active document identity.
- A stale pane is saved/closed or rejected, never appended to.
- Capture with the pane closed opens the appended page.
- Pane page navigation persists `lastPageIndex`.
- Restore discards the detached pane without saving it.

### Persistence and lifecycle

- Notebook writes verify canonical readback and propagate failure.
- Close/capture/backup do not report success over an unverified write.
- Deletion removes canonical and `.tmp` files.
- Same-document reopen flushes pending doc/reading writes before loading.
- Backup/restore blocks dismissal and data mutations until completion.
- `ON_STOP` flushes notebook, PDF doc state and reading state.

## Deferred

- Full crash-safe restore journal with startup reconciliation.
- Recreation-safe pending SAF operation snapshots.
- Stage-all-outputs-before-copying to SAF.
- Excerpt actor serialization and audio-recording lifecycle recovery.
- Stable `PageSpec.id` for exact duplicate-page instance identity.
- General recent relinking UI and content-hash import dedupe.

These remain documented risks; 0.8.91 does not claim they are complete.

## Invariants

- Same name + different URI/docId never merge.
- A link survives insert/delete/move/rotate/reset via source-page resolution.
- Missing/changed/wrong sources are reported, not silently clamped.
- Restore cannot be overwritten by a pre-restore pane.
- A notebook is reported saved only after canonical readback succeeds.
- Successful deletion cannot be undone by `.tmp` recovery.
- A disk load observes every previously queued write for that document.
- No user-data mutation starts during backup/restore.
- After `ON_STOP`, current notebook/PDF/reading state is canonical.

## Verification

JVM tests cover link identity/resolution, persistence verification, deletion,
fingerprints, source-page remapping and backup gating. Instrumentation tests
compile. Real process-death, restore-interruption and stylus validation remain
pending.

### 0.8.91 host verification

- 1217 JVM tests pass with 0 failures/errors/skips.
- 161 instrumentation cases across 28 classes compile.
- Arm64 debug APK and R8 release APK build successfully.
- Release manifest has no `INTERNET`; `RECORD_AUDIO` remains the only sensitive
  app permission.
- No adb device or pressure-sensitive stylus was available.
