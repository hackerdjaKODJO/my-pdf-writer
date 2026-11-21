# Latent bug and performance pass — 0.8.34

## Method

Four read-only agents reviewed the 0.8.33 source from different angles:
latent bugs, performance hotspots, test gaps, and integration risk. Four
implementation agents then fixed non-overlapping slices, followed by an
integrator pass for the backup-restore race.

No third-party code/assets/permissions were added; offline contract unchanged.

## Critical bug fixes

### PDF state and persistence
- Annotation undo no longer restores stale `notes`/`memoCards`, so undoing a
  stroke cannot delete a later note or post-it. `noteDraft` is re-derived from
  the current notes.
- `persistNow()` now coalesces writes through one `DocSaveQueue` and one IO drain
  job. Rapid brightness changes or strokes can no longer race on the same
  `docstate.json.tmp` or let an older write finish last.
- `renderPage()` rethrows `CancellationException`; quick page flips no longer
  produce a bogus "unsupported PDF" banner.
- Search cancels its job on document open, guards by `docId`, rethrows
  cancellation, and clears stale failure state on success.
- Extraction drops annotations on stale legacy pages instead of mapping them to
  page `-1`.
- `discardDocument()` drops the current viewer without persisting stale state;
  used after backup restore so restored sidecars are not overwritten.

### Viewer UI
- System Back now dismisses the post-it sheet and viewer pen box instead of
  closing the document.
- Search is available in **both** full and compact split chrome; the default
  85% study pane no longer loses search.
- A stale memo-edit request no longer silently swallows a canvas tap.
- Search results use unique LazyList keys even when several hits share a page.

### Backup and restore
- Restoring while a PDF is open closes the stale viewer before refreshing lists.
- `BackupManager.restore` rolls replaced directories back if preference install
  fails, and only deletes the temp work dir after success.
- Intent carrier clearing and recents policy are pure and tested; clearing is
  idempotent.

## Performance fixes

- Notebook save JSON serialization, disk write and library reload moved to
  `Dispatchers.IO`; only the final StateFlow publish happens on Main. A
  generation guard prevents stale writes from clobbering newer ones while the
  multi-stroke save path and synchronous `saveNow()` flush remain intact.
- `AnnotationPainter` derives response mean/variation from one `inkResponseSeries`
  pass instead of two; neutral legacy rendering and replay stability unchanged.
- Custom page backgrounds decode through `produceState(Dispatchers.IO)` rather
  than during composition; page switching no longer blocks Main on bitmap decode.
- `visibleAnnotations` is remembered per page rather than rebuilt every
  recomposition.

## Deferred (documented, not fixed here)

- Full live-stroke recomposition rework and PdfRenderer reuse across pages.
- Continuous-reader zoom recomposition throttling and text-selection debounce.
- Replay position kept out of whole-screen recomposition.

## Verification

- JVM: **732 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` pass.
- No `INTERNET`; arm64-only.
- Real-device profiling and stress testing still pending (no adb device).
