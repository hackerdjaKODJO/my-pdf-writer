# Shape-tool UX overhaul (0.8.99)

## Goal

Replace text-only shape choices with a consistent visual language across PDF and
note tools, and make the shape inventory match what the app can actually draw.
The patch adds a real triangle instead of showing a decorative glyph with no
matching annotation path.

## Shared visual system

- `ui/ShapeToolVisuals.kt` owns the canonical order, labels, kind mapping and
  stable test tags: **사각형 · 삼각형 · 타원 · 직선 · 화살표**.
- `ui/ShapeIcon.kt` draws original Compose `Canvas` glyphs with a 24 dp logical
  viewport, consistent 1.8 dp strokes and round joins.
- `ui/ShapeToolTile.kt` provides one 48 dp selectable tile with icon, short label,
  selected border/background, selected semantics and a stable tag.
- No competitor code, assets, fonts, templates or branding were copied.

## Surfaces updated

- Full note editor and compact study-pane shape trays now use the shared icon
  tiles and an exact 48 dp height.
- The note shape dock slot shows the active subtype (rectangle by default) and
  announces it to TalkBack while keeping the stable group label `도형`.
- The PDF annotation sheet separates `글상자` from a balanced `도형` grid.
- PDF shape style headers show the active glyph and specific style name.
- An active PDF shape gets a contextual row with its icon/name, `스타일`, undo
  and redo. This restores style access without adding a seventh permanent dock
  slot.
- The split PDF toolbar reflects the active shape and remains the entry to the
  complete annotation sheet.
- Note style headers and tool hints use the same canonical shape names.

## Triangle data and geometry

`Tool.TRIANGLE` and `AnnotationType.TRIANGLE` are appended to their enums so
existing ordinals remain stable. JSON stays at version 1 and uses the existing
variable-length `pts` array.

A drag generates three explicit vertices: top-center, base-left and base-right.
Storing three vertices (rather than deriving an always-upward triangle later)
preserves orientation through page rotation, note page reorientation, selection
transforms and clipboard/page duplication.

The shared `AnnotationPainter` renders the closed path, so PDF display, note
display, thumbnails and flattened PDF exports use identical geometry. Triangle
eraser hit-testing follows all three edges; note lasso selection uses the full
three-vertex bounds. Shape recognition remains limited to its existing
line/rectangle/ellipse behavior.

### Compatibility note

The additive enum is readable by this and newer builds. Older builds do not know
`"TRIANGLE"` and will skip such an annotation, so downgrading after creating a
triangle can lose it on the next older-build save. No schema migration or new
permission is introduced.

## Verification

- JVM: canonical inventory/order/labels/tags, PDF↔note parity, 48 dp tray fit,
  contextual actions, explicit vertices, commit width/page data, JSON round trip,
  PDF eraser edges and note lasso bounds.
- Instrumentation: full note tray targets/selection/triangle commit and PDF
  annotation-sheet selection state. These compile but require a device/emulator
  to run.
- Existing six-slot dock, note persistence and annotation JSON regressions remain
  in place.

Real-device visual, touch and narrow-screen validation is still pending.

## Build artifacts

- Debug arm64 APK: `MyPDFWriter-0.8.99-arm64.apk`
- SHA-256: `7dcbe720750e807169b73cd130e7f59a23d7e26026dd1e14696f7eddf3e6ba33`
- Unsigned arm64 R8 release SHA-256:
  `ba79737170285de697dbfcc411d8bad3af50ea519bb383f05da7159392189405`
- APK verification: versionCode 108, arm64-v8a only, v2 signed, no `INTERNET`;
  `RECORD_AUDIO` remains the only sensitive app permission.
