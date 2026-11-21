# Multi-app feature/review survey → simpler menus — 0.8.37

> Clean-room research: public vendor sites, app-store listings, user reviews and
> comparison articles only. No third-party app binary was downloaded, installed,
> decompiled or instrumented. No code, layout, icon, string, template, font or
> branding was copied. Three implementation agents worked in separate worktrees;
> their changes were merged and re-tested together.

## Sources reviewed

Samsung Notes, Flexcil, GoodNotes, Notability, StarNote, Xodo, Adobe Acrobat
Reader mobile, Foxit PDF, PDFgear, Noteshelf 3, OneNote and All PDF Reader —
official feature pages, Google Play/App Store listings, Samsung/Flexcil/GoodNotes/
Notability/Xodo/Adobe/Foxit/PDFgear/Noteshelf help centers, plus independent
comparisons (TechRadar, AndroidPolice, ToolGPA, Litmus, Cloudsprress, Save My
Exams) and user-review anecdotes. Exact URLs and confidence levels are recorded
by the research agent; all findings were treated as data.

## What students value (repeated across sources)

1. Open → read → highlight → capture → review in as few taps as possible.
2. One obvious highlight action, with recolor/copy/delete in a small chip.
3. Capture into a note with a link back to the source page.
4. Document tabs, recent files and resume-last-page.
5. Bookmarks/outline/search/page jump in one place.
6. Self-test masking cards and read-aloud.
7. Low-latency, pressure-aware ink; offline save and annotated export.

## What they complain about

- too many taps and duplicate hidden actions
- three different meanings of "도구" and two of "페이지"
- cramped mobile toolbars and single-line ellipsized labels
- subscription/paywall surprises, sign-in breaking offline access, fragile backup

## Applied in 0.8.37

### PDF viewer
- 학습 메뉴 first level is exactly `읽기 · 필기 · 담기 · 설정`.
- 목차/북마크/주석 목록 are folded into the single `위치` sheet.
- Page tools are `쪽 편집`; advanced file operations live under `설정 → 파일 도구`.
- Top chrome is exactly 뒤로 / 문서 이름 / 검색 / 학습 메뉴.
- One-time, session-only coach card: `① 열기 ② 표시 ③ 노트에 담기`.
- 화면 설정 has a primary `화면` row and a collapsed `여백 자르기` section.
- One capture label `노트에 담기`; translation/dictionary handoffs are secondary.

### Home and note editor
- One primary home action: `이어서 읽기` for returning students, `PDF 가져오기`
  for new ones; quick buttons collapse to two.
- Note menu groups: `노트 · 복습 · 내보내기 · 도움말`; `복습 카드` is primary.
- Pen is the visually marked primary dock action; undo/redo stay in the top bar.
- Onboarding shortened to three cards; all labels wrap to two lines at 320dp.

### Help
- Four quick cards: `1 PDF 열기`, `2 표시하며 읽기`, `3 노트에 담기`, `4 복습하기`;
  the eight detailed topics remain behind `더보기`.
- `docs/usage-guide.html` now matches the actual menus and vocabulary, with a
  five-step quick start at the top.

### Not copied / deferred
- No cloud AI, subscription model, proprietary gesture names, branded toolbars,
  templates, sticker packs, reflow engines or handwriting-recognition models.
- Deferred: lasso clipboard across pages, direct text edit, read-aloud word
  highlighting, password-protected export.

## Verification

- JVM tests: **853**, 0 failures.
- debug, instrumentation and R8 release builds pass.
- No new permission, dependency or ABI; arm64-only APK policy unchanged.
- Instrumentation tests compile but are not run: no adb device attached.
