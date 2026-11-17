# 가림 카드 (Masking cards) — study-only offline feature

This is an **original** implementation of the general "cover the answer, grade it,
track progress" study pattern described in `docs/flexcil-analysis.md`. No Flexcil
code, assets, layouts, icons, colours, strings or templates were copied. The tape
visual is drawn in Compose; the data is plain JSON under the app-private notebook
file. There is no network, account, permission, asset or version change.

## Where to find it

Both editor modes use the same action:

- Full-screen note editor: top bar **더보기** → `스터디` → **가림 카드**.
- Compact editor inside the PDF study pane: floating dock **더보기** → same sheet.

The overflow also contains a `카드 페이지에 표시` switch. The card sheet repeats the
same switch so a student can dismiss the overlay without leaving the sheet.

## Controls

| Action | Result |
| --- | --- |
| `현재 페이지에 카드 추가` | Opens the editor with default placement (x 34%, y 18%, w 32%, h 10%). |
| Editor: 질문 / 정답 | Text shown on the tape while hidden / after reveal. |
| Editor: X / Y / W / H | Page-relative percentages, 0–100. Empty or out-of-range values fall back / clamp to safe defaults. |
| Sheet row tap | Jumps to the card's page and closes the sheet. |
| Sheet row 연필 / 쓰레기통 | Edits / deletes the card. All card mutations are single undo steps in the note editor. |
| Tape tap (HIDDEN) | Reveals the answer: `HIDDEN → REVEALED`. |
| Tape O / X | Grades the revealed card: `REVEALED → CORRECT` or `WRONG`. |
| `카드 페이지에 표시` off | Removes every tape overlay node; the pen/lasso/eraser gets the full canvas again. |

States: `HIDDEN` (not started), `REVEALED` (seen, not graded), `CORRECT`, `WRONG`.
Revealing an already-revealed or graded card is a no-op; O/X can re-grade at any time.

Progress in the sheet header: `진행 N% · 정답 C/G (A%)` where progress counts started
cards (REVEALED + graded) over all cards, and accuracy is correct over graded cards.
Counts for all four states are printed underneath.

## Data schema

`Notebook.pages[i].maskingCards` is optional. Old notebooks without the key load as
an empty list, and pages without cards write no key at all.

```json
"maskingCards": [
  {
    "id": "uuid-or-stable-string",
    "prompt": "세포분열의 단계는?",
    "answer": "간기·전기·중기·후기·말기",
    "x": 0.34, "y": 0.18, "w": 0.32, "h": 0.10,
    "state": "HIDDEN",
    "createdAt": 1700000000000
  }
]
```

- `x/y/w/h` are normalized 0..1 fractions of the A4 page box, matching `Annotation`
  coordinates. `x/y` clamp to 0..1, `w/h` clamp to a small tappable minimum through 1.
- No absolute paths, content URIs, device ids or remote references. The record is
  portable between devices and local backups.
- `NotePage.normalized()` and `MaskingCard.fromJson()` repair blank ids, unknown
  states, blank text and non-finite/out-of-range geometry.
- Duplicating a page (single-page or page-manager multi-select) copies the cards
  with **fresh ids** while preserving text, geometry, state and `createdAt`, so the
  copy is an independent study record. Rotation, move and delete keep cards with
  their page.
- Cards ride inside the notebook JSON, so the existing local ZIP backup/restore
  includes them without any format-version bump.

## Export behaviour

Masking cards are **study-only and are not included in PDF export**. The overlay is
an interactive screen layer, not a stored annotation, so `NoteExporter` keeps using
the same WYSIWYG `AnnotationPainter` path as before. Pen, highlighter, text, shapes,
layers, layer opacity, custom backgrounds and page orientation exports are unchanged.

## Interaction and safety notes

- Overlays are drawn inside the canvas' own `BoxWithConstraints` using the same
  pan/zoom transform as the paper, so a tape stays pinned to its normalized rect
  while drawing, panning or zooming. They render only when the current page has
  cards and the display switch is on.
- A tape is small (minimum 92 × 42 dp) but it does consume taps in its rectangle.
  With the display switch off there are no overlay nodes at all. Pen-only stylus
  input and finger pan/pinch behaviour are otherwise untouched.
- Tape geometry follows page orientation (portrait/landscape) because it is stored
  as page fractions; rotating a page keeps the relative placement.
- In compact study-pane mode the overlay is clipped to the pane's canvas, exactly
  like the handwriting underneath it.
- Study state is part of note history: reveal, grade, create, edit and delete are
  one undo step each and survive save, page switches and backup.

## Verification

JVM coverage lives in `data/MaskingCardTest.kt` (legacy defaults, JSON round-trip,
malformed input, state transitions, score/progress, fresh-id copies) and
`ui/NotePageManagerTest.kt` (page-manager duplicate with cards). Build checks:
`assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest`.
