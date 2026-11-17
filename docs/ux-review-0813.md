# UX review — My PDF Writer 0.8.13

> Documentation-only review on `work/ux-review`, against the current delivered
> source `0.8.12 / versionCode 21` (`app/build.gradle.kts:23-24`, AGENTS.md
> changelog). Prepared 2026-11-17 for the 0.8.13 planning round. No app code,
> tests, assets, manifests or build files were changed in this worktree. Nothing
> here is an implementation; every fix is a recommendation with acceptance
> criteria for the owning agent.

## 1. Scope and method

- **Baseline:** the checked-out 0.8.12 source only. Primary evidence:
  `MainActivity.kt`, `ui/NotebookHomeScreen.kt`, `ui/HomeStart.kt`,
  `ui/ViewerScreen.kt`, `ui/NoteEditorScreen.kt`, `ui/StudyPaneSheet.kt`,
  `ui/HelpCenterSheet.kt`, plus the small policy/layout helpers they call
  (`NoteCompactLayout.kt`, `StudyPaneLayout.kt`, `PdfHubSheet.kt`,
  `BackupSheet.kt`) and `res/values/strings.xml`.
- **Question:** how usable is the app on a phone for a first-time user and for
  someone repeating the read → mark → note → search → backup loop? Both
  first-use and repeated-use friction are considered, including the transitions
  between Home, PDF and note surfaces.
- **Behavioral references only:** Flexcil, GoodNotes and other tools are used
  solely as *interaction patterns* documented in `docs/flexcil-analysis.md`,
  `docs/flexcil-handwriting-review.md`, `docs/commercial-tool-review.md`,
  `docs/pdf-ux-selection-and-hub.md` and `docs/ux-overhaul.md`. No third-party
  code, dex/resources, layouts, icons, colours, copy, templates, fonts,
  screenshots or branding were inspected, copied or committed. All proposed UI
  remains original Material 3 with our own Korean labels and code-drawn paper.
- **Limits:** no adb device or emulator was available, so touch, stylus
  pressure, pane overlap and slow-PDF behaviour are reasoned from source and
  prior docs, not measured on hardware. Prior reviews cite 352 JVM tests and a
  compiled instrumentation APK, but that is not an on-device UX proof. Severity
  reflects inferred impact on a phone, not a benchmark. Statements about
  sibling-agent features are targets, never claims of shipped code.
- **Out of scope:** new permissions, INTERNET/cloud/ads/analytics, paywalls,
  watermarks, export caps, third-party assets, and any second-document split or
  tablet-first pattern with low phone return.

## 2. Journey map

Severity: **H** high (blocks the core loop or first-use comprehension),
**M** medium (slows or confuses a common step), **L** low (polish). "Flow" cites
the composable and code path that produces the observation; no screenshots or
copied visuals are used.

| # | Step | What the user sees (evidence) | Friction | Sev |
| --- | --- | --- | --- | --- |
| 1 | First launch | `NotebookHomeScreen` shows an empty library with `EmptyNotesState` ("첫 노트를 만들어 보세요", `NotebookHomeScreen.kt:1184-1188`) and four quick actions in `HomeStart.kt:87-115`. Help is only **홈 ⋮ → 사용법/도움말** (`NotebookHomeScreen.kt:313-322`). | No first-run guidance; a new user must discover text selection, the note pane and backup from overflow menus. Nothing teaches the core loop in-app. | H |
| 2 | Home | `HomeStartContent` shows "이어서 읽기/쓰기" cards plus PDF 열기 / 새 노트 / PDF 도구 / 백업·복원 (`HomeStart.kt:59-117`); tabs 노트/PDF/태그 and search/filter (`NotebookHomeScreen.kt:259-370`). | Good for returning users, but carries no hint about the next step after opening a PDF. Quick-action labels are terse; "PDF 도구" is ambiguous next to "PDF 열기". | L |
| 3 | Open PDF | SAF import via `MainActivity.openPdf`; `ViewerScreen` top bar shows doc name, page/progress, direct **노트**, bookmark, **PDF 허브** (`ViewerScreen.kt:521-591`). Dock has seven tool slots + style + 더보기 (`ViewerScreen.kt:646-657`). | Power actions live behind the hub's simple/expanded split (`PdfHubSheet.PDF_HUB_PRIMARY_LIMIT = 7`); first-timers may not realise "모든 도구 보기" exists. | M |
| 4 | Select / highlight | **텍스트 선택** dock/hub action (`ViewerScreen.kt:428-434, 650`), long-press shortcut (`requestSelectionAt`, `:221-237`), floating action bar with copy/translate/dictionary/excerpt/panel/highlight/underline/strikeout (`:1240-1389`). Scanned page shows error banner + "텍스트 선택 종료" (`:741, 1308, 1360-1367`). | Selection was still often undiscovered despite the 0.8.6 rename; the scanned-PDF message explains *what* happened but offers no OCR action in place; nine actions scroll horizontally, so highlight is not always visible. | H |
| 5 | Open note | **노트** button → `MainActivity.openStudyPane` → `StudyPaneSheet` at 85% (`MainActivity.kt:251-255`, `StudyPaneLayout.kt:17-28`). PDF chrome shrinks to 36 dp floating controls (`ViewerScreen.kt:826-858`); an 18 dp divider resizes the split (`:918-963`). | Default 85% leaves a ~15% PDF strip; the source page is mostly context, and the 18 dp divider plus two independent drag grips are a subtle affordance. | M |
| 6 | Write | `NoteEditorScreen(compact = true)` hides its app bar; a 34 dp tool row + 32 dp action row floats over the canvas (`NoteEditorScreen.kt:1371-1431`, `NoteCompactLayout.kt:25-36`). Six primary tools; search/templates/settings/help are in the ⋮ overflow (`:3280-3577`). | Targets are 32-34 dp (below 48 dp guidance) and sit over paper and PDF controls; note search is several taps deep; help is at the bottom of a long sheet. | M |
| 7 | Search | PDF hub **검색** + page hits, on-page highlight and next/prev chip (`ViewerScreen.kt:418, 800-813, 1490-1526`); note search covers memo/text/stamp/signature only (`NoteEditorScreen.kt:3293-3299`). | Inconsistent entry points (hub vs note overflow), no handwriting search, and no in-place empty-state action when a query has no hits. | M |
| 8 | Backup | Home 백업/복원 → `BackupSheet`: ZIP with optional source PDFs, progress, restore confirmation, unencrypted warning (`BackupSheet.kt:47-255`; `MainActivity.kt:80-86, 326-340`). | "기존 데이터는 안전하게 교체" in the usage guide understates that restore overwrites notes/settings; the sheet is clear but reachable only from Home, not from an active document. | M |

Reading across the map, the two high-severity points are both **discovery**
problems: the core text-selection gesture and the fact that help/tutorial
content exists at all. The medium points are mostly about **reach and feedback**
inside dense phone chrome. None of them requires new architecture; all can be
addressed with original Material 3 components and clearer copy.

## 3. Top 10 UX problems (ranked by impact)

Each item has acceptance criteria (AC) and a file mapping. Items 2 and 3 are
**sibling-agent work**: mark them as planned/owned elsewhere and do not report
them as implemented in 0.8.12 source.

| # | Problem | Acceptance criteria (summary) | Map to current files | Owner / pri |
| --- | --- | --- | --- | --- |
| 1 | Text-selection discoverability | Explicit primary invite; highlight visible at 320 dp; states preserved | `ViewerScreen.kt`, `ViewerToolDock.kt`, `PdfHubSheet.kt` | This repo / P0 |
| 2 | No first-run onboarding | 4-5 dismissible coach cards, replay from Help Center, one-time flag | `MainActivity.kt`, `HelpCenterSheet.kt`, new `OnboardingSheet.kt` | **Sibling `work/onboarding`** / P0 |
| 3 | Scanned-PDF empty state | In-place OCR explanation/route; exit retained; no OCR change | `ViewerScreen.kt`, `ViewerSheets.kt`, `ViewerToolDock.kt` | **Sibling `work/contextual-hints`** / P0 |
| 4 | Split-pane height / affordance | 35-85%, one obvious resize cue, presets kept, fraction policy stated | `ViewerScreen.kt`, `StudyPaneSheet.kt`, `StudyPaneLayout.kt`, `MainActivity.kt` | This repo / P1 |
| 5 | Compact dock reachability | ≥44-48 dp effective targets; no hotspot overlap; snackbar clear | `NoteEditorScreen.kt`, `NoteCompactLayout.kt` | This repo / P1 |
| 6 | Help discoverability | Visible entry on first-run/empty states; replay in Help Center | `HelpCenterSheet.kt`, `NotebookHomeScreen.kt`, `ViewerScreen.kt` | Shared with onboarding / P1 |
| 7 | Note search buried / shallow | ≤2 taps; scope labelled; empty state has next action | `NoteEditorScreen.kt` | This repo / P2 |
| 8 | Backup semantics | Copy states exactly what restore replaces; confirm kept; no auto-backup | `BackupSheet.kt`, `MainActivity.kt`, `docs/usage-guide.html` | This repo / P2 |
| 9 | Toolbar density | Most-used action visible at 320 dp; dense sets in a labelled sheet | `ViewerToolDock.kt`, `ViewerScreen.kt`, `NoteEditorScreen.kt` | This repo / P2 |
| 10 | Inconsistent error feedback | Every failure has message + retry/dismiss; no silent failure | `ViewerScreen.kt`, `BackupSheet.kt`, ViewModels | This repo / P2 |

### 3.1 Detail and acceptance criteria

1. **Text-selection discoverability.** The 0.8.6 rename (올가미 → 텍스트 선택)
   and forgiving hit-testing helped, but selection remains a mode entered from
   the dock/hub; the nine-action horizontal bar can hide the primary highlight
   action on a narrow screen. AC: (a) a first-time PDF view surfaces a
   one-time/contextual invitation to select text; (b) loading/active/empty
   states and the long-press shortcut stay intact; (c) at 320 dp,
   highlight/underline/strikeout are reachable without scrolling; (d) no new
   text-layer model or text-selection logic changes. The contextual tip itself
   is the sibling contextual-hints agent's deliverable.
2. **No first-run onboarding.** There is no preference flag or coach UI in
   `MainActivity` or the viewer; `showHelp` is a plain `mutableStateOf(false)`.
   AC: 4-5 short dismissible cards, replayable from Help Center, a persisted
   one-time flag, no INTERNET/analytics, library never blocked. Owned by
   `work/onboarding`; not present in this branch.
3. **Scanned-PDF empty state.** The empty banner says text is unavailable and
   offers only "텍스트 선택 종료"; OCR is mentioned in static help but not at
   the moment of failure. AC: in-place explanation and a single route to
   PDF 도구/OCR, exit still available, selection behaviour unchanged. Owned by
   `work/contextual-hints`; not present in this branch.
4. **Split-pane height.** The 85% default and 18 dp divider are deliberate, but
   two drag handles plus a tiny size popup are easy to miss and the fraction is
   session-only in `MainActivity`. AC: fraction stays clamped 35-85%; one
   obvious resize affordance is explained on first use; 50/70/85% presets
   remain; PDF strip stays ≥15%; rotation/session behaviour is documented.
5. **Compact dock reachability.** `NoteCompactLayout` targets are 34/32 dp with
   16 px icons. AC: primary tools have ≥44-48 dp effective touch areas (or
   equivalent spacing), the floating pill does not cover the writing hotspot,
   the context tray and snackbar stay clear at 320 dp, and the six labelled
   tools remain legible.
6. **Help discoverability.** Help exists in three ⋮ locations and the content is
   strong, but no surface advertises it to a new user. AC: a visible help entry
   on first-run/empty states and a replay entry in Help Center; retain the
   single-file offline sheet; no web view, link or asset.
7. **Note search.** It is one row deep in a long overflow sheet and excludes
   handwriting. AC: reachable in ≤2 taps from the editor, result → page jump
   preserved, scope text explicitly says "no handwriting search", empty result
   offers a next action, no cloud recognizer or INTERNET dependency.
8. **Backup semantics.** Restore replaces notes/ink/excerpts/reading/audio/
   settings; the guide's "안전하게 교체" can read as non-destructive. AC: sheet
   and guide state exactly what changes and what is untouched, confirmation
   stays, include-sources size warning stays, no silent auto-backup, source PDFs
   remain optional.
9. **Toolbar density.** Full-screen PDF dock (7 tools + style + more), the
   selection bar (up to 9 scrolled actions) and the full note dock are dense.
   AC: each context shows its single most likely action at 320 dp, dense sets
   collapse into a clearly labelled "더보기" sheet, active tool state is always
   visible, no capability is removed.
10. **Error feedback.** Copy uses `Toast`; search/backup use `Snackbar` or inline
    messages; import/export failures are sparse and the busy overlay is generic.
    AC: every failure yields a short message plus retry/dismiss, no silent
    failure on import/export/OCR/selection, success toasts stay brief, no note
    content in logs.

Do **not** implement in 0.8.13: accounts, cloud sync, ads, watermarks, export
caps, third-party fonts/assets or any dependency adding `INTERNET`.

## 4. What is already good (preserve)

- **Offline by construction:** no `INTERNET`/`ACCESS_NETWORK_STATE`, no ads,
  accounts, analytics or cloud AI; translation/TTS go through external intents.
  Re-check the merged manifest every release and reject transitive SDKs that
  re-add `INTERNET`.
- **Local ZIP backup/restore:** manifest/count validation, path-traversal
  rejection, SAF, optional source PDFs, confirm dialog, progress and an
  unencrypted-storage warning (`BackupSheet.kt`).
- **Local audio replay:** `createdAt`/`durationMs` point-by-point stroke reveal
  with seek; free, no paywall; keep timestamps stable through ink edits and
  preserve replay regression tests.
- **No paywall or caps:** local PDF/note export without watermark or page limit;
  a deliberate differentiator against commercial tools.
- **Study links:** `StudyNoteBuilder` + source-page To/From index, local jump
  that keeps pane and PDF together (`StudyPaneSheet.kt:439-554`); keep source
  ids stable through page moves.
- **Help center:** offline single-file `HelpCenterSheet` with eight topics and a
  5-minute quick start, mirroring `docs/usage-guide.html` — strong once found.
- **One-handed chrome:** bottom docks, floating page pill and 36 dp PDF overlay,
  plus a draggable split; strong feature breadth for a phone-first offline tool.

## 5. Recommended 0.8.13 scope

1. **Sibling work — treat as planned, not implemented in this branch.**
   - `work/onboarding`: 4-5 first-run coach cards + replay from Help Center.
   - `work/contextual-hints`: one-time text-selection tip, scanned-PDF OCR
     guidance, search empty-state action.
   Verify each in source after merge; until then report as *target*, never as
   delivered 0.8.12 behaviour. Do not claim these features exist based on this
   review document.
2. **This repo can prepare** (documentation-level findings above): clarify
   backup semantics in `docs/usage-guide.html`, specify help-entry and
   note-search criteria, and define compact-dock target/spacing rules before
   any code change lands.
3. **Merge and release gates** per AGENTS.md: shared-file owners first, verify
   `git status --short` is clean, bump 0.8.13/code22 in the same commit as
   build/README/changelog, run debug + unit + androidTest compile + release,
   confirm no `INTERNET`, publish arm64-only APK. Do not push from a review
   branch.

## 6. Measurement and manual phone-test checklist

Run on a small phone (≤360 dp) and a stylus device if available. No adb device
was used for this review; check each box on hardware.

- [ ] Cold launch shows the library; the first action is obvious without docs.
- [ ] Open a text PDF; locate 텍스트 선택 unaided; tap a word and drag a phrase.
- [ ] Confirm loading/active/empty selection states and the long-press shortcut.
- [ ] Open an image-only PDF; confirm empty banner, exit and OCR route.
- [ ] At 320 dp, confirm highlight/underline/strikeout are visible in the bar.
- [ ] Open the note pane; confirm 85% default, readable strip, both grips work.
- [ ] Drag the centre divider and header grip to 35/50/70/85% without data loss.
- [ ] In the compact dock, write with finger and stylus; check target size,
      overlap, context tray and snackbar clearance.
- [ ] Run note search from the editor in ≤2 taps; open a result page.
- [ ] Run PDF search; use next/prev chip and confirm wrap and current highlight.
- [ ] Create a backup with and without source PDFs; restore on a copy; verify
      the wording matches what actually changed.
- [ ] Trigger an import/export failure; confirm message + retry/dismiss, no
      silent failure.
- [ ] Open Help Center from Home, PDF and note; replay any coach card after merge.
- [ ] Toggle dark theme and large font scale; recheck dock labels, chips and
      pane header.
- [ ] Verify permissions: no `INTERNET`; `RECORD_AUDIO` only at recording time.
