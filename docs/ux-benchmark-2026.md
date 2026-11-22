# 2026 UX benchmark — Flexcil / GoodNotes vs My PDF Writer

Sources checked 2026-11-22 (official sites only; behaviour/UX reference, no assets or code copied):

- Flexcil — https://www.flexcil.com/
- GoodNotes for students — https://www.goodnotes.com/learning/students
- GoodNotes — https://www.goodnotes.com/

## What the competitors promise

| Pattern | Flexcil | GoodNotes |
| --- | --- | --- |
| Gesture-first, "do everything in 2-step" | ✓ pen gestures | ✓ pen-first writing |
| PDF + notebook on one page | ✓ | ✓ (split / documents) |
| Find anything instantly | — | ✓ search across PDF, text, handwriting |
| Subject folders / centralised materials | — | ✓ folders, textbooks + notes together |
| Quick capture in one click | ✓ sticky note / guideline | ✓ Quicknotes |
| Tap-to-replay audio | ✓ audio + handwriting replay | ✓ tap note to hear the moment |
| Masking / self-testing | ✓ masking pen + O/X | ✓ AI quiz (cloud) |
| Multi-page view | ✓ 1/2/4 pages | — |
| Cross-device continuation | ✓ web + tablet | ✓ phone/tablet/desktop |

## Where we already match (do not regress)

- Offline-only, no account/sign-in, no `INTERNET`, no ads or analytics — a deliberate differentiator.
- Split PDF + study note with drag capture and source links (`ViewerScreen`, `StudyPaneSheet`).
- One-tap resume of the last PDF page (`HomeStart` + `PdfViewModel` reading state).
- Masking cards with O/X scoring, local audio notes with stroke replay, vector/pressure ink.
- PDF export flattened with the same painter used on screen.

## Gaps closed in this release (v0.8.51)

1. **"노트에 담기" now really puts text in a note.** The primary selection action routed only to the excerpt list and posted a false success message; it now goes through `captureToNote()` — persist, hand to the live pane, open the note (Flexcil/GoodNotes "2-step" parity).
2. **Note search one tap away.** Added a top-bar `검색` action (previous route: 노트 메뉴 → 검색 = 3 taps).
3. **Notebook resume returns to the last written page** (`Notebook.lastPageIndex`), matching PDF resume — GoodNotes "pick up where you left off" without cross-device sync.
4. **Menu wording cleaned up.** `담은 목록 → 발췌 목록` (the sheet is titled 발췌), dock `텍스트 → 텍스트 선택` (matches help/guide and disambiguates from 글상자), Home filter `보기 → 노트 범위` (chips are 전체/즐겨찾기/휴지통, a scope not a view), `사용법/도움말 → 도움말`, and the stale "내보내기는 파일 도구" subtitle corrected (export is a first-level 설정 row).

## Deliberate non-adoption (policy)

- Cloud sync, cloud storage, accounts, subscriptions, cloud AI — `AGENTS.md` forbids `INTERNET`, accounts, ads, analytics and cloud AI SDKs. On-device OCR is the only acceptable ML surface.
- Handwriting search (GoodNotes) needs an on-device recognizer — out of scope for a surgical UX pass; note search still covers memos, text boxes, stamps and signatures, and says so.
- Pen-gesture command shortcuts (Flexcil) would risk corrupting drawing input; the six-slot dock + long-press + two/three-finger undo remain the command vocabulary.
- Third-party icons, templates, covers, fonts or branding are never copied — competitor research is behaviour only.
