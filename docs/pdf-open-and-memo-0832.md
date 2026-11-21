# Faster PDF open + PDF post-it cards — 0.8.32

## Three parallel agent tasks

1. **Open latency** — audit and optimize `PdfViewModel.openDocument()`.
2. **PDF post-its** — persist/render/edit memo cards on PDF pages.
3. **Close UX** — make post-it closing obvious and non-destructive.

All work stayed offline; no new permissions, accounts, cloud, ads or assets.

## 1. Faster PDF open

Before, opening a PDF did four expensive passes:
copy URI → `pageCount()` (PDFBox load) → `sourcePageSizes()` (PDFBox load again)
→ `assemble()` (PDFBox load + save) → first render.

Changes:
- `PdfRepository.documentInfo(file)` loads the document **once** for count and
  page sizes.
- `isIdentityPageSpecs()` detects untouched layouts. Identity documents skip
  `assemble()` entirely and render from the source copy.
- A session-only URI→File source cache avoids recopying the same PDF during one
  app session. Page edits still assemble a separate working file; rebuilds never
  overwrite the cached source.
- `resetPages()` reuses already-loaded sizes.
- Busy state, restored page, bookmarks, recents, annotations, display prefs and
  error handling are unchanged.

Microbenchmark (300-page generated PDF): pre-render open work dropped from
~160 ms to ~29 ms, about **82% less**, before on-device rendering. Image-heavy
PDFs gain more because the full rewrite is skipped.

## 2. Post-it cards on PDF pages

- `MemoCard` gained an additive `page` field (omitted from JSON for page 0).
- `DocState` now stores `memoCards` with backward-compatible JSON and remaps
  them when the source PDF changes.
- `ViewerUiState` exposes cards; `PdfViewModel.upsertMemoCard/deleteMemoCard`
  persist them through one undo step.
- New `PdfMemoCardLayer` renders original folded-paper cards in the PDF page's
  pan/zoom transform; tap edits, top-strip drag moves, bottom-right grip resizes,
  and card hit areas do not create strokes.
- PDF menu → 읽기 → `메모 카드` opens a manager list with current-page highlight,
  add/edit/delete, show-on-page toggle and page jumps.
- Page rotate/move/delete/duplicate/insert/reset remap cards with fresh ids where
  needed.

Deferred: cards are not yet drawn into **exported** PDFs; the manager sheet says
so. Export remains unchanged and unbroken.

## 3. Easier close

- The tiny eye-off icon is replaced by a **48dp `닫기` button** with X icon,
  content description `메모 카드 닫기` and a per-card test tag.
- Header is >=48dp; move drag is confined to a dedicated grip so the close and
  ⋮ buttons are excluded.
- Sheet header has `메모 카드 숨기기` / `메모 카드 표시` and hidden pages show a
  `메모 카드 표시` restore pill.
- Delete stays only in the ⋮ menu, now with a confirm dialog. Closing never
  deletes a card.

## Verification

- JVM: **689 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` pass.
- No `INTERNET`; arm64 build.
- On-device timing and Touch/TalkBack feel still need a real device.
