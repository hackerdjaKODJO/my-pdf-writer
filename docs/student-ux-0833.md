# Student-first UX redesign — 0.8.33

## Method

Three read-only agents reviewed:
1. public student workflows from GoodNotes, Notability, Flexcil, Samsung Notes,
   Xodo, PDF Expert and GoodReader plus university note-taking guidance;
2. the current app's cognitive load (surface counts, tap paths, duplicate labels);
3. a radical but implementable information architecture.

Then three implementation agents worked in parallel on Home, PDF viewer and note
editor with disjoint file ownership. A final QA pass is recorded below.

No third-party code, assets, fonts, branding, cloud flows or new permissions were
used. The target user is a student studying lecture PDFs offline.

## Diagnosis

- 4 mutually exclusive activity modes, 3 Home tabs, 5 reader modes, 4 top-bar
  actions, 6 dock slots + a second row, and a 32-action PDF menu.
- Common student tasks were 3–5 taps deep: margin note, post-it, bookmark list,
  outline, annotation list, export.
- One concept had several names: 발췌 / 노트 패널에 추가 / 스터디 노트 / 메모 카드.
- `페이지 편집` opened a jump sheet; dock `주석` opened tools; `검색` and
  `백업/복원` were duplicated.

## New model

### Home — `홈`
- Bottom tabs demoted to a compact top segmented control `노트 · PDF · 태그`.
- Persistent `이어서 학습` cards at the top.
- Quick actions reduced to `PDF 가져오기`, `새 노트`.
- New replayable `빠른 학습` sheet: 4 steps (`1/4 … 4/4`), `다음`, `건너뛰기`,
  `완료`; opens from Home only, never blocks startup.
- `PDF 도구` and `백업/복원` remain in `설정`.

### PDF viewer — `PDF`
- Top bar: `뒤로`, 문서명, `검색`, `북마크`/`해제`, `노트`, `학습 메뉴`.
- One 6-slot study dock: `보기 · 형광펜 · 펜 · 지우개 · 텍스트 · 노트`.
- Undo/redo/style moved into quick actions at the top of `학습 메뉴`; the second
  dock row is gone. All annotation tools remain in `주석 도구`.
- `PDF 메뉴` renamed `학습 메뉴`; groups: `학습 · 도구 · 보기·페이지 · 내보내기 ·
  기타`. Every existing action is reachable; search is explicitly top-bar-owned.
- Selection bar: `형광펜 · 밑줄 · 취소선 · 복사 · 노트에 담기 · 더보기`; `발췌로 저장`
  lives in more.
- Page sheet title `페이지` instead of `페이지 편집`.
- Post-it surface label `포스트잇`.

### Note editor — `노트`
- Top bar: `뒤로`, 제목, `실행 취소`, `다시 실행`, `노트 메뉴`.
- One-row dock: `펜 · 형광펜 · 지우개 · 선택 · 글상자 · 도형` + `스타일`.
- Favorite and export remain in `노트 메뉴`, not the top bar.
- Menu quick actions: undo/redo/style; 11 rows and 4 sections unchanged.
- Student labels: `복습 카드`, `가리기 카드`, `선택`.

## Preserved

Offline/no permissions, SAF import/export, PDF open optimizations, annotation
model and pressure profiles, study pane 85%/35–85% clamp, 50/70/85 presets,
memo cards, masking/checklist, audio, masks, bookmarks, search, reflow, TTS,
backup, and state restoration.

## Verification

- JVM: **705 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` pass.
- No `INTERNET`; arm64-only.
- Real-device Touch/TalkBack smoke test still pending (no adb device).

## QA follow-up before release

- Added a `검색` button to the split PDF toolbar, so search remains one tap away
  while the study pane is open (it is top-bar-owned in normal mode). A migration
  test now asserts search is reachable in both chrome modes.
- Updated help text to the new `학습 메뉴` groups and six-slot dock labels.
- Fixed mechanical label repeats in README and the usage guide, and corrected
  the legacy-term comment in `NoteEditorMenu.kt`.
