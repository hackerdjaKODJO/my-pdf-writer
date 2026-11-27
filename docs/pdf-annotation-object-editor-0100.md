# PDF annotation object editor — 0.8.100

## Scope

This is stage 1 of the offline Foxit-style editor roadmap. It adds an object
editor for **app-created annotations stored in `DocState`**. It does not edit
arbitrary native PDF annotations or rewrite source PDF text.

Implemented:

- `주석 편집` entry in the PDF contextual row and annotation-tool sheet.
- Tap selection with a clipped selection frame and 48 dp handle hit regions.
- Move, proportional text resize, general resize, rotation, duplicate, reorder
  and delete.
- Content editing for text, stamp and text signatures.
- Basic appearance editing: color, width, opacity, font size and supported text
  rotation.
- One committed ViewModel mutation and one undo/redo step per manipulation.
- Squiggly underline as a drag tool and as selected-text markup in `더보기`.
- Privacy-safe `annotation_edit` trace events and matching E2E assertions.

## Geometry and interaction

- `pdf/AnnotationGeometry.kt` provides pure bounds, shape-aware hit testing,
  translation, resize, rotation and page-bound constraints.
- Text width accounts for page aspect; stamp bounds are centered; malformed
  one-point rectangles fail safely.
- The overlay uses the same crop/zoom/pan transform as the page renderer and
  clips selection/handles to the visible crop.
- Preview updates do not restart the active gesture; cancellation clears the
  preview and exiting edit mode restores normal tool input.
- The six-slot PDF dock is unchanged.

## Compatibility

- `Tool.SQUIGGLY` and `AnnotationType.SQUIGGLY` are appended to their enums.
- Existing JSON remains readable and unchanged when the new type is absent.
- Older builds will skip squiggly annotations, so downgrading after creating one
  can lose it on the next older-build save.
- No new permission, network dependency, competitor code or asset is introduced.

## Verification

- JVM: geometry, crop-independent transforms, malformed data, shape-aware hit
  testing, ordering, duplication, JSON, tool inventory and trace policy.
- Instrumentation: overlay selection/move, PDF shape picker, note shape tray and
  real PDF flow trace. These compile but require a device/emulator to run.
- Real-device gesture, TalkBack and visual validation remain pending.

## Build artifacts

- Debug arm64 APK: `MyPDFWriter-0.8.100-arm64.apk`
- SHA-256: `d237f75c77097263ebc01b5c4142a00049b38308d9061655e3823b16d5f8422c`
- Unsigned arm64 R8 release SHA-256:
  `daabb83fe541c21c220b41af173566ea3d68c8c11b314397fdedfa9435923d9e`
- APK verification: versionCode 109, arm64-v8a only, v2 signed, no `INTERNET`;
  `RECORD_AUDIO` remains the only sensitive app permission.

## Deferred parity work

True source-text replacement, image/link/media annotations, native PDF annotation
interoperability, on-page forms, reusable drawn signatures, persistent crop,
page labels, outline write-back and advanced offline optimization remain in the
later stages documented in `foxit-parity-roadmap.md`.
