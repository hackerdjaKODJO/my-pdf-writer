# Latent bug review and fixes — 0.8.38

Three read-only agents reviewed the 0.8.37 source in parallel (PDF viewer/state,
note pipeline, render/data/export). Confirmed defects were split across three
fix agents in separate worktrees, merged and re-tested together. No app was
launched or decompiled; all findings are from source analysis plus JVM tests.

## Fixed — high severity

### PDF save queue dropped edits on document switch
`DocSaveQueue.pending` was one `DocState`, so switching tabs before the IO drain
overwrote the previous document's last state. The queue is now keyed by `docId`
and drains one document per iteration.

### Concurrent document opens interleave
`openDocument()` was an untracked coroutine. Rapid tab/recent/intent taps could
let an older open finish last, mixing `_viewer.docId`, `workingFile` and
`sourceFile`. An `openJob` + generation gate cancels and invalidates superseded
opens; `pendingInitialPage` is captured synchronously. `closeDocument` /
`discardDocument` invalidate in-flight opens.

### SAF export used the wrong document
The PDF export launcher read `_viewer` at picker-result time; a document switch
while the picker was open exported the new document under the old filename. The
snapshot (`docId`, source, pages, annotations, style) is now captured at launch
and verified before publishing. Generation guards also cover extract/share/print
completions.

### Pressure calibrator corrupted by pointer-up samples
`NoteCanvas.capturePressure()` fed every event to the calibrator, including UP
where stylus pressure is 0. After several strokes the learned minimum collapsed
toward 0 and ink became too bold. Only `pressed` stylus samples now calibrate;
memo taps and non-drawing events are skipped.

### Crop clip in wrong coordinate space
`PdfCanvas` centred the crop at the local origin but clipped with raw page
pixels, so even zero insets clipped only a quadrant while hit-testing disagreed.
A pure `clipRectForCanvas` helper now maps the visible rect into page-local
coordinates.

## Fixed — medium / low

- Backup/restore double-entry replaced with an atomic `BackupOperationGate`.
- Restore completion no longer closes a PDF opened while restore was running;
  it also discards stale in-memory notebook state before refresh.
- Backup flushes pending notebook saves before archiving.
- All notebook JSON writes funnel through one lock, removing the shared
  `${id}.json.tmp` race.
- Canvas pan resets on document switch even when the page index is the same.
- Search clears stale hits immediately on a new query and highlights use the
  trimmed query.
- SAF summary text survives recreation (`rememberSaveable`) and clears on
  cancel/failure; annotation summary order matches the Locations panel.
- Audio replay precomputes brush/fountain envelopes from the full stroke so
  revealed ink matches the exported image.
- Eraser no longer crashes on TEXT/STAMP annotations without points; preview
  ellipse matches the real erase radii.
- `점 간소화` can no longer increase stored point count (Chaikin before RDP).
- Ink settings count derives from the actual 10 options.

## Verification
- JVM tests: **879**, 0 failures.
- debug, instrumentation and R8 release builds pass.
- No new permission, dependency or ABI; offline contract unchanged.
- Instrumentation tests compile but are not run: no adb device attached.
