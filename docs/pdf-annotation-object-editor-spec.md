# PDF annotation object editor — Stage 1 technical spec

## Scope

Add a PDF annotation object editor without changing the six-slot dock or the
existing drawing tools. The editor works on app-created annotations stored in
`DocState`; it does not claim to edit arbitrary annotations embedded in the
source PDF.

Stage 1 delivers:

- tap selection and a visible selection frame;
- move, resize and rotate;
- duplicate, delete and bring-forward/send-backward;
- edit text/stamp content and basic appearance;
- squiggly underline as a new drag annotation;
- one undo/redo step per completed manipulation.

Permanent redaction application, arbitrary source-PDF object editing, AcroForm
authoring and certificate signatures are later stages.

## State and history

- Selection is ephemeral viewer state, not persisted JSON.
- `DocSnapshot` already owns annotations, so all committed transforms and style
  changes use the existing undo/redo history.
- A drag updates a local preview only. The ViewModel receives one committed
  annotation replacement on pointer-up.
- Document switches, page changes that remove the selected annotation and
  restore operations clear selection.
- Autosave remains read-back verified; no gesture writes directly to disk.

## Pure geometry contract

New pure helpers must not depend on Android Canvas:

- `annotationBounds(annotation, pageWidth, pageHeight): Rect?`
- `annotationHitTest(annotation, point, pageWidth, pageHeight, slack): Boolean`
- `translateAnnotation(annotation, dx, dy)`
- `resizeAnnotation(annotation, fromBounds, toBounds)`
- `rotateAnnotation(annotation, angleRadians, pageWidth, pageHeight)`
- stable selection-handle geometry derived from bounds.

Rules:

- Point-based shapes transform every stored point.
- Rectangle/ellipse/line/arrow/triangle preserve their type and timing arrays.
- Text/stamp anchors transform and `textRotation` is updated for rotation.
- Width scales for resize only when the user explicitly changes it; basic move
  and rotate preserve width.
- All output points remain normalized 0..1 unless the user deliberately allows
  an annotation to extend beyond the page.
- NaN/Infinity and empty geometry return a safe failure, never a partial edit.

## Overlay interaction

A Compose overlay appears only while annotation edit mode is active:

1. Tap empty space clears selection.
2. Tap an annotation selects the topmost hit in reverse annotation order.
3. Drag the selected body to move.
4. Drag a corner/edge handle to resize.
5. Drag the rotation handle to rotate around the selection center.
6. The overlay consumes gestures only in edit mode so normal pan, pinch, text
   selection and drawing remain unchanged.

The overlay must keep 48 dp touch targets even when the visible handle is
smaller, and expose TalkBack actions through the selection action row rather
than relying on handle graphics alone.

## ViewModel operations

Add narrow operations, each pushing one history snapshot:

- `selectAnnotation(id)`
- `replaceAnnotation(updated)`
- `duplicateAnnotation(id)`
- `deleteAnnotation(id)`
- `moveAnnotationOrder(id, forward)`
- `updateAnnotationContent(id, content)`
- `updateAnnotationAppearance(id, color, width, opacity, fontSize, textRotation)`

Each operation verifies the selected ID still exists in the active document and
page. Stale IDs fail without mutating another document.

## UI entry points

- Contextual PDF action row: `주석 편집`.
- `주석 도구` sheet: `주석 편집` entry.
- Selection action row: `스타일`, `내용 편집`, `복제`, `앞으로`, `뒤로`, `삭제`,
  `편집 종료`.
- Existing six dock slots remain unchanged.

## Squiggly underline

- Add `Tool.SQUIGGLY` and `AnnotationType.SQUIGGLY` at the end of their enums.
- Reuse the underline/strikeout drag gesture and selected-text markup path.
- Render a scale-invariant sine wave between the first and last point.
- Treat it as line geometry for eraser, lasso, filtering, summary and export.
- Legacy JSON is unchanged; old builds will skip the new type, so document the
  same downgrade limitation as other additive enum values.

## Trace events

Extend `PdfFlowTrace` with privacy-safe events:

- `annotation_select` — opaque annotation ID, type
- `annotation_transform` — kind, page, duration
- `annotation_style` — kind, duration
- `annotation_duplicate` / `annotation_delete` — type, count
- `annotation_order` — direction, type

Never log text, file names, paths, URIs, colors as user content, or geometry.

## Tests

### JVM

- Bounds/hit-test for every app-created annotation type.
- Move/resize/rotate point and timestamp preservation.
- Non-square page rotation math and 90°/270° page composition.
- Empty/NaN/pathological geometry safety.
- Squiggly JSON round-trip and filter/summary classification.
- Legacy JSON golden fixtures remain unchanged.

### Instrumentation

- Select, move, resize, rotate and delete an annotation.
- One drag creates one undo/redo step.
- Selection clears on document/page change.
- Full and compact viewer chrome expose the same actions.
- Squiggly can be drawn, saved, reopened and exported.

### Real-file E2E

Create annotation → transform → save → close → reopen → export → verify the
export contains the transformed appearance. Extend `PdfFlowTraceE2ETest` for
the new events.
