# EasyNotes-inspired handwriting and fast capture (0.8.92)

## Target and method

- Artifact: `/sdcard/Download/apk_for_pdf/EasyNotes_1.3.66.1117.apk`
- App: **EasyNotes: Notes, Memo & Lists**
- Package: `easynotes.notes.notepad.notebook.privatenotes.note`
- Version: `1.3.66.1117` (`versionCode 10573`)
- SHA-256: `4d49f01120923c643bd0c25223741e8a209a8addbf0b55462fddaae6c060c552`
- Size: 91,977,508 bytes; minSdk 23, targetSdk 36.
- Analysis was static only. The APK was not executed or installed.
- No competitor code, assets, fonts, templates, covers, or branding were copied.

REA archive inventory completed successfully:

- Parent inventory: `ev_85d623097e0980c068fd15169b134d262f618b29baa7c86ec3291f473d02752c`
- Summary: `ev_5dd6b9592b2077236457f959d757ed461d97ae29ca2b0498f5c140f5310bcb7b`
- Inventory: `ev_0fb50b68a349a74cfd08486b2ee993035dc59b3fd9b1cefba80c4e4c77cab7a0`

REA's JADX-backed Android class/method provider is unsupported on this
`android/arm64` host. Code findings therefore use read-only `aapt`, `dexdump -d
classes5.dex`, ZIP inventory, and `assets/db/notes.sql`. Negative string/API searches
are bounded to app-owned `classes5.dex` and packaged resources; reflection, dynamic
loading, OEM input filtering, and runtime behavior remain unknown.

## What EasyNotes actually does

### Observed drawing pipeline

- `BrushView` hosts separate `CanvasView` and `OverlayView` children, uses a hardware
  layer, and applies an inverse page matrix before drawing.
- `CanvasView.onDraw` paints background, image/sticker/tape layer, cached completed
  pen bitmap, current live action, eraser cursor, and selected image action.
- Completed strokes are cached into the dry pen bitmap; only the active action is
  drawn live.
- Freehand paths use midpoint/quadratic smoothing with `TOUCH_TOLERANCE == 1.0`.
- Fountain pen width changes with physical speed, uses Bézier interpolation and
  distance-based densification, and tapers to a zero-width terminal.
- Pencil uses deterministic bitmap stamps, midpoint geometry, timestamp averaging,
  and speed-dependent width.
- Tools include eraser, color pen, highlighter, pencil, fountain pen, dash pen,
  pattern pen, and explicit line/oval/rectangle shape modes.
- Text and image objects have direct move/scale/rotate/flip/delete handles.
- Drawing undo/redo is a command stack; rich-note editing uses bounded snapshot
  history with idle coalescing.
- Drawing drafts are debounced by 5 seconds and flushed immediately in `onPause`.
  The observed draft writer uses a direct file write; My PDF Writer's atomic
  temp/fsync/readback path is intentionally stronger.

### Important bounded negative findings

No app-owned references were found for `MotionEvent.getPressure`, `AXIS_PRESSURE`,
tilt, tool type, historical MotionEvent APIs, palm rejection, stylus routing, or
lasso in `classes5.dex`/packaged resources. `DrawPen.pressure` exists as a serialized
field but no external getter/setter call site was found.

Therefore EasyNotes' observed feel comes from **speed-based width, smoothing,
separate dry/wet layers, and tool-specific renderers**, not from a pressure/tilt
pipeline. My PDF Writer already has a stronger pressure/timing foundation and keeps
it. Coalesced history and bounded prediction in this release are independent
enhancements, not claims of EasyNotes parity.

## Implemented in 0.8.92

### Fast ink

- Split note rendering into a dry layer (paper, background, committed annotations)
  and a wet layer (active stroke/tool preview only).
- Capture Android coalesced `MotionEvent` history into a bounded, ordered,
  non-finite-rejecting per-stroke buffer.
- Drain historical samples before the current Compose sample, preserving pressure
  smoothing and per-point timestamps.
- Add transient prediction bounded to 12 ms and 12 px; never persist it and clear it
  on lift, cancel, dwell, page change, or gesture handoff.
- Keep preview preparation and commit on the existing shared geometry pipeline.

### Stability repairs required by the 0.8.91 contract

- Add real `MainActivity.onStop` flushing for the live notebook, detached study
  pane, pending retry, PDF docstate, reading progress and excerpts; restore keeps
  its mutation lease.
- Bind drag-and-drop payloads to source `docId`, content fingerprint, original
  source page, rect, rotation and excerpt id. Revalidate against the active bytes
  and current page layout before append; propagate persistence failure.
- Invalidate a detached pane when either document id or content fingerprint
  changes. New blank panes persist a full `sourceLink`; legacy URI-only notebooks
  cannot silently absorb a replacement PDF's captures.
- Treat platform cancellation as a non-commit path, select coalesced history by
  pointer tool type (stylus vs palm), and keep wet-ink opacity/stack position
  identical to the active layer.
- Verify reading/excerpt sidecar writes by canonical readback, retain failed
  snapshots for retry, and block close/backup on unresolved save or source-
  replacement isolation failures. Backups include source fingerprints; legacy
  source-included restores reset them safely.
- Preserve `NormalizedPoint.timeMs` through page reorientation and rotation.
- Use one layer paint-order/opacity policy in editor, thumbnails, studio previews,
  and export.
- Make thumbnails/studio previews include checklist and post-it content.

### Fast capture

- Home one-tap **빠른 메모** and **체크리스트** actions create an auto-titled note
  and open the editor without a mandatory title/cover/template step.
- Checklist quick capture seeds one card with three blank items.
- PDF export includes checklist title, every item, and checked state by default;
  the export sheet has an explicit include/exclude switch.
- Checklist search/backup behavior remains on the existing durable note model.

## Explicitly deferred

- Persisted per-device pressure calibration and adjustable stabilization slider.
- Current-page PNG sharing.
- Reminders, widgets, notebook encryption, tilt, handwriting recognition, in-page
  image objects, infinite canvas, cloud sync, accounts, ads, analytics, and AI.
- No new permissions and no `INTERNET`.

## Verification

- 1,302 JVM tests pass, including 29 motion-history tests, 12 checklist-layout tests,
  quick-capture round trips, timed page transforms, layer ordering/opacity,
  document-bound drag parsing/validation, and legacy fingerprint rejection.
- 164 instrumentation cases across 29 classes compile, including checklist raster
  include/exclude rendering and quick-capture button dispatch; device runs remain pending.
- Legacy JSON remains compatible: new export/capture fields are additive or
  transient; notebook JSON is unchanged.
- Arm64 debug APK and R8 release APK build successfully. Debug APK SHA-256:
  `feb63a611e8a4a8a44bc0233da034ec2f44a1755316a7339afe800c1f3e03934`.
  Unsigned release SHA-256:
  `b35a8b00912f58dea3fe85296835cbfd414068f66f131d54f6a3b85ea5d8c4b7`.
- Release manifest has version `0.8.92`/`101`, arm64-only native libraries, no
  `INTERNET`, and `RECORD_AUDIO` as the only sensitive runtime permission.
- No adb device/emulator or pressure-sensitive stylus was available; device runs
  and subjective ink-quality validation remain pending.
