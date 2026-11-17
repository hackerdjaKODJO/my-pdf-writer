# Flexcil APK — static feature-evidence ledger

> Documentation-only companion to `docs/flexcil-analysis.md` and
> `docs/flexcil-handwriting-review.md`, written for the My PDF Writer 0.8.11 →
> 0.8.12 planning round (`/data/data/com.termux/files/usr/tmp/opencode/pdfw/FLEXCIL-0812.md`).
>
> This ledger records **resource-name and asset-path evidence only**. It is input
> for an original, offline revision of My PDF Writer. It contains no Flexcil code,
> assets, templates, components, fonts, layouts, audio, screenshots, UI copy or
> branding.

---

## 1. Target identity, commands, limitations

### 1.1 Target identity

| Field | Value |
| --- | --- |
| File | `/sdcard/Download/apk_for_pdf/Flexcil_1.5.1.4.apk` |
| Label | Flexcil |
| Package | `com.flexcil.flexcilnote` |
| Version | `1.5.1.4` (versionCode `839`) |
| SDK | minSdk `26`, targetSdk `36`, compileSdk `36` |
| APK size | `122,019,860` bytes |
| SHA-256 | `76ede392aed40bcb89d7ec5500b925b773995d1868eb879278dbab9a30621bdb` |

The SHA-256 printed by `sha256sum` matches the value supplied with the task, so
the inspected bytes are the intended artifact.

### 1.2 Exact commands used

All commands were read-only and run from Termux. No file was decompiled, extracted,
copied into the repository or committed. `aapt2 dump resources` output was filtered
in-flight with `grep`/`awk`/`sort`/`uniq`/`wc`; no resource dump was written to disk.

```bash
APK=/sdcard/Download/apk_for_pdf/Flexcil_1.5.1.4.apk

# identity / integrity
ls -l "$APK"
sha256sum "$APK"
aapt2 dump badging "$APK" | sed -n '1,6p'
aapt2 version | head -2                      # Android Asset Packaging Tool (aapt) 2.19

# resource-name evidence (examples; one pattern per key family)
aapt2 dump resources "$APK" | grep -oE 'string/checklist_[A-Za-z0-9_]+' | sort -u
aapt2 dump resources "$APK" | grep -iE 'audiooption_(playspeed|speed)|[0-9]_[0-9]{2}x' | sort -u
aapt2 dump resources "$APK" | grep -oE 'string/audiooption_use_[A-Za-z0-9_]+' | sort -u
aapt2 dump resources "$APK" | grep -iE 'string/(menu_dictionary|ic_textpopup_dictionary|menu_translate|ic_textpopup_translate|translate_app_[A-Za-z0-9_]*)' | sort -u
aapt2 dump resources "$APK" | grep -oE 'string/masking_[A-Za-z0-9_]+' | sort -u
aapt2 dump resources "$APK" | grep -oE 'string/side_annotation_filter_[A-Za-z0-9_]+' | sort -u
aapt2 dump resources "$APK" | grep -oE 'string/doc(tab|_tab)_[A-Za-z0-9_]+' | sort -u

# asset inventory (paths and counts only — never unzip -p / extract contents)
unzip -Z1 "$APK" | grep -c '^assets/Checklist/'
unzip -Z1 "$APK" | grep -c '^assets/Template/'
unzip -Z1 "$APK" | grep -c '^assets/Defaults/WritingView/'
unzip -Z1 "$APK" | grep -c '^assets/Model/'
unzip -Z1 "$APK" | grep '^assets/Checklist/' | awk -F/ '{print $3}' | sort | uniq -c
unzip -Z1 "$APK" | grep '^assets/Checklist/' | awk -F. 'NF>1{print "."$NF}' | sort | uniq -c
unzip -Z1 "$APK" | grep '^assets/Template/'  | awk -F. 'NF>1{print "."$NF}' | sort | uniq -c
```

### 1.3 Limitations — read before using this ledger

- **Resource-name evidence only.** Every finding below is a resource key, a
  resource type/path, or an asset path/length. It shows that a surface is
  *declared or shipped*, not that it is reachable, enabled, working, or enabled
  for free in this build. String values were not used as proof; only short
  functional labels derived from key names are quoted.
- **No runtime or decompiled proof.** No JADX/decompilation, no dex inspection,
  no execution, no Frida/instrumentation, no network, account, purchase or
  purchase-gating flow. Behavior is inferred, never verified.
- **REA constraint.** REA `inspect_android_package` is unsupported on this
  android/arm64 host (JADX subprocess ownership unsupported). All evidence here
  comes from the read-only Termux tools `aapt2`, `unzip` and `sha256sum` instead.
- **No asset contents read.** `unzip -Z1` lists names only. The bundled PDFs,
  JSON, TFLite model, components and themes were neither extracted nor opened.
- **Gating is advertised.** Premium/unavailable strings are labels, not proof of
  enforcement. Backends for dictionary, cloud sync and AI are not verified.
- **Scope of mapping.** "Already present" in §4 means verified in *this*
  worktree's source; sibling-branch features are marked planned, never claimed
  as implemented here.

---

## 2. Newly inspected resource-key families

Functional labels are short and derived from the key names. No proprietary copy
or long strings are reproduced. Counts are of unique keys observed in the
default-resource `aapt2 dump resources` output.

### 2.1 `checklist_*` — interactive checklist component

72 unique `string/checklist_*` keys, plus `drawable/`, `color/` and `layout/`
entries. Representative keys and short labels:

| Key family / keys | Short functional label |
| --- | --- |
| `checklist_component_create`, `checklist_creation_options`, `checklist_created_with_default_style` | Create checklist / creation choices |
| `checklist_component_design_type`, `..._design_style`, `checklist_design_shape`, `..._size`, `..._column_count`, `..._direction` | Component type, style and layout attributes |
| `checklist_component_bounds`, `checklist_cell_append_unavailable`, `checklist_component_capacity_unavailable` | Bounds and capacity limits |
| `checklist_component_ui_group_count`, `..._ui_untitled`, `..._ui_placement_hint`, `..._ui_overflow` | Sidebar/group UI states |
| `checklist_side_menu_add_group`, `..._rename_group`, `..._delete_confirm`, `..._percent` | Group add / rename / delete / progress |
| `checklist_sidebar_checked_total`, `checklist_sidebar_ungrouped`, `checklist_sidebar_unnamed` | Checked count and grouping labels |
| `checklist_option_strikethrough`, `checklist_delete_child_checklists`, `checklist_keep_linked_objects` | Strike completed / cascade delete / retain links |
| `checklist_group_created`, `checklist_group_view`, `checklist_group_ungrouped` | Group lifecycle |
| `checklist_guide_*` (title, content, style, graphic, text, align, bundle, side group, menu) | First-run guidance topics |
| `checklist_planner_inserting`, `checklist_planner_invalid_archive`, `checklist_planner_storage_failed` | Planner import/error states |
| `checklist_unavailable_*` (builtin, product, resources, store, version, title) | Unavailable / gated states |
| `checklist_source_delete_*`, `checklist_sidebar_output_*` | Source/output delete and recovery |

Associated visuals (names only, not copied): `checklist_control_shape_circle_on/off`,
`..._square_on/off`, sketch variants, `checklist_direction_horizontal/vertical`,
`checklist_menu_align_left/center/right`, `checklist_menu_group_add/dissolve`,
`checklist_menu_row/column_before/after`, `checklist_connection_link/arrow`,
`checklist_completion_effect_*`, `checklist_sidebar_*`.

### 2.2 Audio playback speed and sync options

| Key / path | Short functional label |
| --- | --- |
| `layout/filem_setting_audiooption_playspeed` (also `-sw600dp`) | Playback-speed settings screen |
| `string/audiooption_playspeed` | Playback speed setting |
| `string/audiooption_speed_0_50x`, `0_75x`, `1_00x`, `1_25x`, `1_50x`, `2_00x` | Six speed steps, 0.5×–2.0× |
| `id/id_play_speed_0_75x`, `id/id_play_speed_1_25x` | Speed selector row IDs |
| `string/audiooption_use_addsyncdata_inplaying` (+ `_detailmsg`) | Add notes made during playback |
| `string/audiooption_use_syncrendering_inplaying` (+ `_detailmsg`) | Replay handwriting during playback |
| `string/audiooption_use_multidoc_syncdata` (+ `_detailmsg`) | Sync notes across documents |

### 2.3 Dictionary and translate handoff

| Key / path | Short functional label |
| --- | --- |
| `string/menu_dictionary`, `drawable/ic_textpopup_dictionary` | Dictionary action from text popup / selection |
| `string/menu_translate`, `drawable/ic_textpopup_translate` | Translate action from text popup / selection |
| `string/translate_app_install_title`, `..._install_msg` | External translate-app install prompt |
| `string/translate_app_text_limit` | Advertised character limit |

### 2.4 Masking study tool

| Key | Short functional label |
| --- | --- |
| `string/masking_name` | Masking feature label |
| `string/masking_check_correct`, `..._check_wrong` | Correct / wrong marking (O/X) |
| `string/masking_filter_page`, `..._filter_scorehigh`, `..._filter_scorelow` | Page and score filters |

### 2.5 Annotation panel filters

| Key | Short functional label |
| --- | --- |
| `string/side_annotation_filter_all` | All annotations filter |
| `string/side_annotation_filter_anno` | Annotations-only filter |
| `string/side_annotation_filter_link` | Links filter |
| `string/side_annotation_filter_masking` | Maskings filter |

### 2.6 Document tabs — prefix clarification

The task name `doc_tab_*` does not appear; the shipped prefix is **`doctab_`**:

| Key | Short functional label |
| --- | --- |
| `string/doctab_close_all_tab` | Close all tabs |
| `string/doctab_close_other_tab` | Close other tabs |
| `string/doctab_hide_tab_bar` | Hide tab bar |
| `string/doctab_show_tab_bar` | Show tab bar |

### 2.7 Other high-value keys found

| Key family / keys | Short functional label |
| --- | --- |
| `doc_popupmenu_rename`, `..._duplicate`, `..._move`, `..._delete`, `..._share`, `..._note_property`, `..._webupload` | Document card context actions |
| `sidemenu_title_nav`, `..._bookmark`, `..._outline`, `..._annotation`, `..._stickers` | Side-panel sections |
| `nav_page_filter_all`, `nav_page_filter_annotated` | Page-manager filters |
| `setting_show_annotation_markers` | Annotation-marker visibility |
| `side_item_highlight`, `side_item_underline`, `side_item_textbox` | Markup row types |
| `tool_item_checklist`, `msg_addchecklist_guide` | Checklist tool and add-guide |

---

## 3. Asset inventory facts (paths and counts only)

`unzip -Z1` name listings were counted in-flight. **No archive member was
extracted, opened, copied or committed.** These are inventory facts, not a
description of asset contents.

| Path prefix | Entries | Observed structure / extensions |
| --- | ---: | --- |
| `assets/Checklist/` | 33 | `components/` 18 + `themes/` 15; 31 `.pdf` + 2 `.json` |
| `assets/Checklist/themes/` | 15 | `builtin/` with `manifest.json`, `styles.json`, `decoration/` and `preview/` subfolders |
| `assets/Checklist/components/` | 18 | `progress/` subfolder (bar, box, ring, semicircle, step PDFs) |
| `assets/Template/` | 198 | all 198 are `.pdf`; names follow a portrait/landscape × paper-colour × layout pattern (plain/lined/dotted/graph/Cornell/legal/vertical/2-column) |
| `assets/Defaults/WritingView/` | 1 | `DefaultPens.json` |
| `assets/Model/` | 1 | `interactive_segmentation_model.tflite` |

Notes:

- The 198 template PDFs and the 33 checklist PDFs are bundled **content**. None
  was read; My PDF Writer must draw its own paper, covers and checklist graphics
  in code (e.g. `PaperBackground`, Compose) rather than importing these files.
- The two checklist JSON files are theme/component manifests; they were counted
  by name only. Their contents are not a source of truth for our data model.
- The TFLite model is a single bundled binary; its exact runtime use is not
  verified. It is not a candidate for reuse.
- Broader asset facts (stickers, pen-toolbar skins, sample PDFs, locales, dex
  count) remain in `docs/flexcil-analysis.md`; this ledger re-verifies only the
  four directories named in the task.

---

## 4. Mapping to My PDF Writer 0.8.11

Baseline: `app/build.gradle.kts` in this worktree reports `versionName = "0.8.11"`,
`versionCode = 20`. Claims below are scoped to source found in this worktree.

### 4.1 Already present in this worktree (verified locally)

| Flexcil surface (evidence) | Local 0.8.11 status | Basis in this worktree |
| --- | --- | --- |
| Masking / masking filter (`masking_*`, `side_annotation_filter_masking`) | **Present** as offline masking study cards (0.8.9); not an asset copy | `data/MaskingCard.kt`, `ui/MaskingCardOverlay.kt`, `ui/MaskingCardsSheet.kt`, `test/.../MaskingCardTest.kt` |
| Annotation panel / hide mode / page grouping (`side_annotation_filter_*`, `setting_show_annotation_markers`) | **Partly present**: PDF panel with filters, page grouping and display-only hide (0.8.3); the exact four-way All/Annotations/Links/Maskings split and To/From link rows were not re-verified here | Documented in `docs/flexcil-handwriting-review.md` §1.1/§4; local UI files exist |
| Study-note pane / excerpt backlinks (bottom pane, source links) | **Present** (0.8.4–0.8.8), resizable split, detached save/export, source links | `ui/StudyPaneSheet.kt`, `ui/StudyPaneLayout.kt`, `data/Excerpt.kt`, `data/ExcerptStore.kt`, `test/.../StudyPaneLayoutTest.kt` |
| Translation handoff (`menu_translate`, `translate_app_*`) | **Present**: external Translate handoff via `ACTION_PROCESS_TEXT`/deep link, no `INTERNET` | `pdf/TextActions.kt` (and `docs/flexcil-handwriting-review.md` §5/§7) |
| Local audio recording + point-level replay (`audiooption_*` neighbourhood) | **Core present**: recording, timestamped strokes, seek/reveal; **no playback-speed control** | `audio/AudioNoteController.kt`, `audio/AudioReplayController.kt`, `data/AudioNoteStore.kt`, `ui/AudioReplaySheet.kt` |
| Document context actions (`doc_popupmenu_*`) | Reported in prior analyses; not re-verified in this ledger — treat as documented, not proven here | `docs/flexcil-analysis.md` §2.1 |

### 4.2 Planned in sibling branches — NOT implemented in this worktree

The 0.8.12 handoff assigns three features to parallel worktrees. At the time of
this ledger, **no source for any of them exists in this worktree**:

| Planned feature | Sibling branch (from `FLEXCIL-0812.md`) | Local evidence | Correct claim |
| --- | --- | --- | --- |
| Offline interactive checklist cards on note pages | `~/wt/checklist` / `work/checklist` | No file matching `*checklist*` under `app/src` | **Planned elsewhere**; do not claim implemented |
| Audio playback speed 0.5×–2.0× | `~/wt/audio-speed` / `work/audio-speed` | No speed control in `AudioReplayController.kt` / `AudioReplaySheet.kt` | **Planned elsewhere**; do not claim implemented |
| External dictionary handoff from text selection | `~/wt/dictionary` / `work/dictionary` | `TextActions.kt` has translate only; no dictionary action | **Planned elsewhere**; do not claim implemented |

### 4.3 Gaps not currently assigned

- Document tab strip (`doctab_*`): no tab-strip implementation found in this
  worktree; a later shell item, not a 0.8.12 deliverable.
- Playback note-sync options (`audiooption_use_*`): beyond the 0.8.12 speed
  scope; timestamp-preserving replay is the local foundation.
- Four-way annotation filter including explicit Links and Maskings categories:
  local masking cards and links exist; a unified filter taxonomy is a later
  polish item.
- Skip entirely: ads/rewarded unlock, account and cloud sync, AI/web viewer,
  licensed sticker/planner store (see §5).

---

## 5. IP and offline boundary

**Implement original behavior only.** The static evidence above is a map of
*what to do*, never a source of *what to copy*.

- No Flexcil code, dex, resources, layouts, drawables, icons, colour tokens,
  component PDFs, theme JSON, TFLite model, templates, paper backgrounds,
  fonts, audio, dictionary data, screenshots, app icon, name, logo or store copy.
- No extraction or "borrowing" from the APK: no `unzip -p`, no asset import, no
  premium/DRM bypass. Draw checklist controls, paper and covers with our own
  Compose code; store our own checklist data model in app-private notebook JSON.
- **Offline contract:** no `INTERNET`, no cloud sync, no account, no ads, no
  analytics, no crash upload, no WebView/HTTP client, no cloud AI, no remote
  font/template fetch. Translation and dictionary remain external *handoffs*;
  the app itself performs no network request.
- `RECORD_AUDIO` stays local and runtime-requested only when recording starts.
  Any playback-speed feature operates entirely on local timestamped strokes.
- Re-check the **merged** manifest on every release so a transitive dependency
  cannot reintroduce `INTERNET`. Keep backups local, versioned and user-initiated.
- Keep new labels original (Material 3, Korean UI strings); do not mirror Flexcil
  key names, text, spacing or visual identity in the product.

---

## 6. Prioritized implementation shortlist and acceptance criteria

Priorities reflect the 0.8.12 sibling split plus low-risk follow-ups. All items
assume the version/permission policy in `AGENTS.md` (next version `0.8.12`,
`versionCode = 21`).

### P1 — Offline checklist cards on note pages (`work/checklist`)

**Acceptance criteria**

1. A checklist block can be created on a note page and toggled item-by-item with
   a visible checked count / progress indication.
2. Optional group rename and a reset/delete action are undoable and do not
   corrupt sibling annotations.
3. State persists in app-private notebook JSON and is included in local backup
   manifest + restore tests; legacy notebooks without checklist data load safely.
4. All visuals are original Compose drawings; **zero** APK PDFs/JSON/components
   are imported or referenced. No new permission.
5. Unit tests plus debug build/unit-test compile pass; E2E compile affected note
   input paths.

### P1 — Playback speed 0.5×–2.0× (`work/audio-speed`)

**Acceptance criteria**

1. Selectable steps exactly `0.50 / 0.75 / 1.00 / 1.25 / 1.50 / 2.00`; `1.00`
   is the default and preserves current behavior.
2. Stroke reveal timing, seek and page markers scale with the chosen speed;
   `createdAt`/`durationMs` are preserved and never mutated by playback.
3. Switching speed mid-replay keeps the playhead position consistent; no audio
   data is written back to the note.
4. UI is original, fully local, and adds no permission. Unit tests cover timing
   math and boundary steps.

### P1 — External dictionary handoff from selection (`work/dictionary`)

**Acceptance criteria**

1. Selection action bar offers a Dictionary action that hands text to an
   external dictionary app via `ACTION_PROCESS_TEXT` (or a chooser), matching the
   existing Translate pattern.
2. The app itself performs **no** network call and bundles **no** dictionary
   data; no `INTERNET` permission is added.
3. Graceful fallback when no handler is installed (action hidden or a short
   original hint), and no crash on empty/whitespace selection.
4. Existing copy/translate/bookmark actions and `ACTION_PROCESS_TEXT` prefill
   behavior remain intact; unit tests cover intent construction and fallback.

### P2 — Masking-study parity polish (local, no sibling branch)

**Acceptance criteria**

1. Existing offline masking cards support correct/wrong marking plus page and
   score filtering using our own data model and visuals.
2. No Flexcil asset/thème PDFs are read; manifest/backup tests still pass.

### P2 — Annotation panel taxonomy polish (local, no sibling branch)

**Acceptance criteria**

1. Add explicit Links and Maskings categories to the existing page-grouped panel
   without changing stored or exported PDF data.
2. Hide mode remains display-only with a persistent banner and a regression test
   proving exported PDFs are unchanged.

### P3 — Document tabs (later shell work)

**Acceptance criteria**

1. A capped tab strip (3–4 visible on phone) with close x, close others/all and
   hide/show bar; session restore on cold start.
2. No loss of unsaved note state when switching tabs; E2E compile for note
   multi-stroke paths.

### Explicitly out of scope

Ads/rewarded unlock, account and cloud sync, AI/web upload, licensed content
store, popup-window/4-page phone spreads, and any reuse of APK assets.

### Release gate for every shortlisted item

`assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest`,
`assembleRelease`; merged manifest has no `INTERNET` and no unexpected
permissions; version `0.8.12` / `versionCode = 21` with README, `AGENTS.md` and
usage docs updated in the same commit; **no push from the evidence branch**.
