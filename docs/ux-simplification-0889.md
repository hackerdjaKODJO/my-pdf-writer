# PDF usability simplification — 0.8.89

This document records the behavioral evidence and design decisions behind the
0.8.89 “easier PDF use” pass. It is a reference for later development, not a
licence to reuse competitor code or assets.

## Scope and method

- Target: My PDF Writer `0.8.88` / `versionCode 97`, source commit `f6ef669`.
- Research was read-only and static. No competitor APK was executed or installed.
- Tools: existing decompiled Maru/Ridi trees, prior Ridi review documents,
  `aapt2` for package/resources/binary XML, and Androguard's DEX parser for
  semantic class/method names. The installed Androguard APK/AXML layer was
  incomplete (`lxml` missing); no dependency was installed.
- REA Android providers were available, but their local apktool/JADX
  prerequisites were absent. The investigation continued with the supported
  local tooling above instead of changing the environment.
- Source audit findings are **source-inferred**, not measured through analytics
  or usability sessions. Competitor strings/classes prove affordances exist,
  not runtime order, first-launch defaults or touch behavior.

## Artifact identities

| App | Package / version | Size (bytes) | SHA-256 |
| --- | --- | ---: | --- |
| StarNote | `com.onyx.galaxy.global.note` / `1.5.4.1` (5706) | 141,127,399 | `0d7ec1d4de20cc0565215ee757d38a0fcf29b3d892def4944317dfd01b608897` |
| Samsung Notes | `com.samsung.android.app.notes` / `4.4.48.15` (444815000) | 207,203,557 | `484d3dd599355789508034ba90af9a35e09f81532564a992c131710632a467ce` |
| Flexcil | `com.flexcil.flexcilnote` / `1.5.1.4` (839) | 122,019,860 | `76ede392aed40bcb89d7ec5500b925b773995d1868eb879278dbab9a30621bdb` |
| All PDF Reader | `pdf.pdfreader.viewer.editor.free` / `2.1.9` (143) | 89,183,699 | `4ee5b165da074d5220c2fb5942521a29f787171b8266c9b8fc277da4efc54337` |
| Maru | `my.geulga` / `4.13.11` (6770) | 24,966,041 | `0da0dee1cab4521419cc519c33f47475b1e75c569295bde4c1ffce9e27c70f59` |
| MyReader | `com.myreader.reader` / `2.11.43` (418) | 55,490,514 | `5b62d7536d521d0b118e268635bccb0c5b36575212d253cece7c4c93cd4905b2` |
| Ridi Books | `com.initialcoms.ridi` / `26.11.1` (2026001630) | 102,555,042 | `e77ffd2c0c0801430d90922977f388de336805b52595e95986c5c005951e02de` |

## Observed patterns

### Writing and recovery

- Flexcil's `toolbar_layout.xml` names simple Pen/Highlight/Eraser controls and
  separate undo/redo containers. Samsung Notes has `QuickToolContainer`
  undo/redo update methods and “Saving… / Note saved / Save failed” vocabulary.
- StarNote and Samsung Notes keep a small writing core while placing deep pen,
  eraser, page and export options behind popovers/settings.
- All PDF Reader's floating edit bar includes Undo/Redo/OK and explicit
  Save/Saving/Saved vocabulary.

### Reading and location

- MyReader keeps four PDF bottom actions (Pages, Contents, Bookmarks, Display),
  shows a centered page bubble while seeking, and opens Contents as one list.
- Ridi uses a thin title bar plus a two-row bottom toolbar, a tabless TOC with a
  page column, and a slider drag card with page + chapter.
- Maru separates reading chrome from a translucent overlay menu and suppresses
  irrelevant actions by reader mode.

### Guidance and output

- Samsung Notes uses versioned first-use classes plus contextual tips for FAB
  long-press, toolbar dragging and finger drawing.
- StarNote uses `GuideTipsView` and an export dialog with scope/format/output
  choices.
- Flexcil layers Tips/Help/User Guide and keeps deep editing controls out of the
  first screen.
- No inspected app established a global Simple/Expert toggle. The recurring
  pattern is a stable core plus conditional overflow and point-of-need help.

## Current My PDF Writer friction (observed in source)

1. Annotations auto-persist, but the reader had no save-status field; users could
   not tell whether work was durable.
2. Undo required `PDF 메뉴 → 주석 도구 → 실행 취소`; pen style was long-press-only
   or buried in the menu.
3. The PDF menu exposed 33 actions across four groups on its first screen.
4. Help still described a removed bottom page bar, omitted that PDF zoom needs
   `보기`, and implied a PDF two-finger undo gesture that belongs to notes.
5. The three-card onboarding auto-opened before the first real task and was
   marked seen as soon as it appeared.
6. Page/outline lists did not open at the current location.
7. The study pane default left only 15% for the source PDF.
8. Page edits were snapshot-undoable in the ViewModel, but the organizer neither
   exposed history nor described deletion truthfully.

## Design decisions

| Decision | Evidence / rationale |
| --- | --- |
| Keep six labeled dock slots | Already matches the “small labeled core” pattern; avoids icon-only ambiguity. |
| Contextual Style + Undo/Redo row | Recovery belongs next to writing in Flexcil, Samsung Notes and All PDF Reader. |
| Six-task easy menu + `전체 도구 보기` | Progressive disclosure is consistently used; no extra global mode decision. |
| Truthful quiet auto-save status | Communicates My PDF Writer's existing durability advantage without copying competitors' explicit-save friction. |
| Actionable export feedback | Samsung/StarNote name outcomes and next steps; export is a deliberate output action. |
| Current-position page/outline lists | MyReader/Ridi reduce “where am I?” cost with current-position navigation. |
| 70% study-pane default | Keeps note space while preserving a usable 30% source PDF strip. |
| Optional 60-second guide | Contextual help is more targeted than a forced tour; tutorial replay remains available. |
| Organizer Undo/Redo + truthful delete copy | The data model already snapshots page edits; the UI should expose that recovery. |

## Deliberately not adopted

- No competitor code, assets, fonts, templates, covers or branding.
- No cloud sync, account, advertising, AI or network permission.
- No global Simple/Expert mode: it would add another setting and decision.
- No full competitor eraser catalog, pen matrix, mode tabs or toolbar
  customization in this patch.
- No export-format overhaul. My PDF Writer continues to export a separate,
  flattened annotated PDF; the original source remains unchanged.

## Verification and limitations

- Static evidence cannot establish animation quality, runtime visibility,
  feature flags, accessibility behavior or real touch ergonomics.
- StarNote's editor implementation is protected; resource/menu evidence is
  useful but semantic editor recovery is limited.
- Resource strings/classes can be legacy or unused; negative findings are
  bounded to the searched vocabulary.
- MyReader's local source version differs from the supplied APK; APK evidence is
  authoritative for this document.
- The change must be verified with JVM tests, instrumentation compilation and a
  real-device smoke test (small phone, large font, TalkBack, finger and stylus).
