# Flexcil 1.5.1.4 — static feature and UX analysis

> Scope: static inspection of a user-supplied APK and public Play material only.
> No app execution, no JADX/decompilation, no runtime instrumentation, no network
> calls, no account or purchase flow. Observed 2026-11-16.
>
> This document is input for an original, offline revision of My PDF Writer. It is
> not a specification to clone Flexcil, and it deliberately contains no Flexcil
> code, assets, templates, audio, stickers, screenshots, or branding.

---

## 1. Artifact identity and evidence

### 1.1 Identity

| Field | Value |
| --- | --- |
| File | `/sdcard/Download/apk_for_pdf/Flexcil_1.5.1.4.apk` |
| Label | `Flexcil` |
| Package | `com.flexcil.flexcilnote` |
| Version | `1.5.1.4` (versionCode `839`) |
| SDK | minSdk `26`, targetSdk `36`, compileSdk `36` |
| APK size | `122,019,860` bytes (116.4 MiB) |
| Uncompressed | `128,850,681` bytes, 3,826 zip entries |
| SHA-256 | `76ede392aed40bcb89d7ec5500b925b773995d1868eb879278dbab9a30621bdb` |
| Launchable | `com.flexcil.flexcilnote.derivedproduct.flexcilaccount.ui.FlexcilAccountActivity` |
| Application class | `com.flexcil.flexcilnote.FlexcilNoteApplication` |
| Code packaging | 5 dex files (`classes.dex` … `classes5.dex`), about 42 MiB; no `.so` native libraries |
| Resources | 1,951 string resources, 601 layouts, 1,871 drawables, 368 assets |
| Bundled content | 198 A4 paper templates (99 portrait, 99 landscape), 23 basic sticker PNGs, 2 pen-toolbar skins, 5 sample PDFs |
| Locales | default only (`--_--`); no additional locale qualifiers in this build |
| Public listing at capture | 1M+ downloads, 3.74/5 from 12,295 ratings, updated 2026-11-13, Productivity |

The launcher is an **account activity**, not the library. `MainActivity` is also
exported, but the public entry point starts with Flexcil's account flow. That is a
useful contrast for My PDF Writer, which opens directly into the local library.

The build carries no native libraries. Pen rendering, PDF work, audio and sync are
implemented in Java/Kotlin plus bundled models/assets.

### 1.2 Signing

| Check | Result |
| --- | --- |
| APK Signature Scheme v1 | not verified |
| v2 | verified |
| v3 | verified |
| v3.1 / v4 | not present |
| Play Source Stamp | present |
| Signer certificate DN | `CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US` |
| Signer cert SHA-256 | `38559e603939ecbf7d7aec357ca1d3565660b48094454b5a8fb649a9fd7a64ef` |
| Signer cert SHA-1 | `88a4610168f2fcb734d5d2b5af901ea2a2697406` |

The certificate is a generic Android/Google DN, not a Flexcil-branded
publisher DN. The artifact therefore cannot by itself confirm the Play publisher;
it may be a mirror or re-signed copy. A present Source Stamp is consistent with a
Play-distributed build, but it is not proof of the original signer. No conclusion
of tampering is implied.

### 1.3 Permissions

25 `uses-permission` entries:

| Group | Permissions |
| --- | --- |
| Network, ads, install | `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `AD_ID`, `ACCESS_ADSERVICES_TOPICS`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_CUSTOM_AUDIENCE`, install-referrer, `C2DM.RECEIVE`, `DOWNLOAD_WITHOUT_NOTIFICATION` |
| Billing / licence | `com.android.vending.BILLING`, `com.android.vending.CHECK_LICENSE` |
| Camera / microphone | `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `FOREGROUND_SERVICE_MICROPHONE` |
| Foreground work | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK` |
| Notifications / feedback | `POST_NOTIFICATIONS`, `VIBRATE` |
| Legacy storage | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` |
| App-private | `com.flexcil.flexcilnote.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` |

There is **no** `MANAGE_EXTERNAL_STORAGE`, no `READ_MEDIA_*`, no location and no
contacts permission. Camera is declared as an optional feature. Storage is legacy
read/write rather than all-files access.

Application flags: `allowBackup=false`, `usesCleartextTraffic=false`, a network
security config, `hasFragileUserData=true`, `supportsRtl=true`, and
`extractNativeLibs=false`. The app is not adb-backup friendly; user data is marked
fragile.

### 1.4 Declared components

| Type | Total | Flexcil namespace | Notable entries |
| --- | ---: | ---: | --- |
| Activities | 81 | 8 | `FlexcilAccountActivity` (launcher), `MainActivity`, `WritingViewActivity`, `IntentProcessingActivity`, `ExtraIntentProcessingActivity`, `SaveToDownloadFolderActivity`, `FlexcilStoreActivity`, `WebStoreAccountActivity` |
| Services | 20 | 3 | `recording.service.RecordingService`, `cloudSync.service.DataSyncService`, `cloudSync.fcm.FCMServiceCloudSync` |
| Receivers | 17 | 2 | `RecordingService$StopRecordingReceiver`, `DataSyncService$StopDataSyncingReceiver` |
| Providers | 11 | 0 | `androidx.core.content.FileProvider`, Firebase/ads/startup providers |

Most of the 81 activities are ad-SDK or billing screens. The ad/mediation surface
includes AppLovin MAX, Google AdMob, Meta Audience Network, Pangle/ByteDance,
Unity Ads, Fyber, Moloco, Adpie and ADX Corp SDKs, plus RevenueCat, AWS
Amplify/Cognito, Firebase and Play Billing. This explains much of the 122 MB size
and the permission surface; it is not PDF functionality.

The share/import entry points are `IntentProcessingActivity` and
`ExtraIntentProcessingActivity`. `WritingViewActivity` is the in-app document
editor and is not exported. Recording and cloud sync are foreground services with
their own stop receivers.

### 1.5 Evidence commands

All commands are read-only. Run them against the APK; do not unpack code into the
repository.

```bash
APK=/sdcard/Download/apk_for_pdf/Flexcil_1.5.1.4.apk

ls -l "$APK"
sha256sum "$APK"
aapt2 dump badging "$APK" | sed -n '1,45p'
apksigner verify --print-certs "$APK"
apksigner verify -v "$APK" | sed -n '1,14p'

aapt2 dump xmltree --file AndroidManifest.xml "$APK" \
  | grep -E 'E: (activity|service|receiver|provider)|android:name|android:exported'

unzip -Z1 "$APK" | awk -F/ '{print $1"/"$2}' | sort | uniq -c | sort -rn | head -20
unzip -Z1 "$APK" | grep -c '^assets/Template/'
unzip -Z1 "$APK" | grep '^assets/PenToolbarSkins/' | cut -d/ -f3 | sort -u

aapt2 dump resources "$APK" \
  | grep -E 'string/(sidemenu_title|doctab_|audio|premium_purchase_window_header)' \
  | head -60

unzip -p "$APK" assets/Defaults/WritingView/DefaultPens.json | head -c 800
```

Resource-label evidence below comes from an `aapt2 dump resources` dump. Labels
are quoted only as short functional evidence; long proprietary copy is
paraphrased.

---

## 2. Feature inventory

"Present" below means the artifact declares or ships the surface. It does not mean
the flow was executed or that every label is reachable in the current build.
Resource strings can be legacy or feature-flagged.

### 2.1 Library, folders, search

| Surface | Evidence | Notes |
| --- | --- | --- |
| Unified document library | `documents`, `files_filter_all`, `files_filter_pdfs`, `files_filter_notes`; phone shots 09 and landscape shots 17/24 | One grid with All / PDFs / Notes filters; PDFs and notebooks live together |
| Navigation rail | `Search`, `Documents`, `Trash`, `Files`, `Recents`, `Favorite` in the shots | Tablet rail; on phone this becomes bottom navigation or chips |
| Recents / favourites | `favorite`, `nav_files_recent_clearall`, `nav_favorite_removeall_*` | Clear-all actions; star on cards |
| Folders | `fab_menu_newfolder`, `folderitem_count_fmt`, `setting_general_stack_folder_*` | Coloured folder cards with item count/date; stacking documents creates a folder |
| Sort / layout | `doc_sort_name_u/d`, `created_u/d`, `modified_u/d`, `size_u/d`, grid/list icons | Eight sort orders (four keys × two directions) plus gallery/list toggle |
| Doc actions | `doc_popupmenu_rename/duplicate/move/delete/share`, screenshot 09/24 | Long-press context menu; `Move` and `Share` |
| Trash | `trash_restore`, `trash_delete`, `trash_deleteall_*`, `trash_empty_message` | Restore, permanent delete, empty trash |
| Search | `global_search_title_matches`, `global_search_content_matches`, `global_search_option_maintext`, `global_search_option_textbox`, `search_result_page_title_fmt` | Title, main text and text-box search; results by page |
| Tags | no tag-specific string keys found; shots show folders/favourites, not tag chips | Flexcil organisation is folder + favourite + recent + search. Tags are **our** differentiator, not a Flexcil parity item |
| Bulk actions | `nav_files_items_remove_*`, `nav_page_selectall`, multi-select circle in shots | Multi-select move/remove; page-level select all |

### 2.2 Templates, covers, paper

| Surface | Evidence | Notes |
| --- | --- | --- |
| Cover + template editor | `cover`, `template`, `edit_note_template`, `apply_note_template_all/select_page/default`; shots 07/15/22 | Cover and Template are separate tabs; title availability check; Done |
| Orientation | Portrait/landscape control in the editor; `ic_note_portrait`, `ic_note_landscape` | Same note can be portrait or landscape |
| Cover categories | `Basic` section in shots; `flexcil_premium_add_template`, `premium_purchase_window_header_template_*` | Basic covers plus premium packs; over 20 advertised digital content items |
| Custom templates | `edit_note_add_template_file`, `edit_note_add_template_gallery` | Import from Files or Gallery |
| Template application | `apply_note_template_all`, `apply_note_template_select_page`, `apply_note_template_default` | Change all pages, selected pages, or default page |
| Template warning | `edit_note_template_desc`, `edit_note_template_desc_caustion` | Page-size mismatch can lose handwriting; change is irreversible |
| Bundled paper | 198 PDFs under `assets/Template/` | A4 portrait/landscape; white/dark/yellow; plain/lined/dotted/graph/Cornell/legal/vertical/2-column |
| Cover view | `setting_singlecover_view_mode` ("First Page Alone") | Show the cover alone before a spread |
| Stickers / skins | 23 basic sticker PNGs, 2 pen-toolbar skins, 5 sample PDFs | Content-heavy, brand-adjacent; do not reuse |

### 2.3 Text selection, markup, study actions

| Surface | Evidence | Notes |
| --- | --- | --- |
| Gesture selection | `video_help_10` underline, `11` bracket, `12` tap-and-hold, `13` circle, `25` hold for text box/image | Selection is gesture-first, not a mode you must find in a toolbar |
| Markup types | `side_item_highlight`, `side_item_underline`, `side_item_textbox`, `tool_item_textbox`, `tool_item_stickynote` | Highlight, underline, text box/free text, sticky note |
| Text selection actions | `copy`, `menu_dictionary`, `menu_translate`, `ic_textpopup_bookmark`, `ic_textpopup_dictionary`, `ic_textpopup_translate` | Copy, dictionary, translate, bookmark from the text popup |
| Drag and drop | `msg_selection_drag_tutorial_1/2`, `msg_please_drag_and_drop`, `copy_toast_msg` | Press-and-hold to move; copy to another page, note, or AI field |
| Annotation markers | `setting_show_annotation_markers`, `allannotation_hide_mode_caustion_title` | Markers on page; hide-all mode with a caution banner |
| PDF import | `settings_pdfimport_name`, `settings_pdfimport_opts_anno_name`, `settings_pdfimport_opts_text_name` | Import PDF with editable highlight/underline and free-text annotations |
| Masking | `masking_name`, `masking_check_correct/wrong`, `masking_filter_page/score*`, premium header | Cover answers, mark O/X, track correct rate; a distinctive study tool |
| Text-box search | `global_search_option_textbox` | Text boxes are indexed for search |

### 2.4 Dictionary and translation

| Surface | Evidence | Notes |
| --- | --- | --- |
| Dictionary | `menu_dictionary`, `ic_textpopup_dictionary` | Action is present; backend not verified statically |
| Translate | `menu_translate`, `ic_textpopup_translate` | Delegates to Google Translate |
| Translation dependency | `translate_app_install_msg`, `translate_app_install_title` | Requires the Google Translate app |
| Translation limit | `translate_app_text_limit` | Advertised cap: 5,000 characters |

This is a good privacy pattern for an offline app: the PDF app does not perform
the network request; the user's translation app does. My PDF Writer already follows
the same model in `pdf/TextActions.kt`.

### 2.5 Bookmarks, outline, annotation panel

| Surface | Evidence | Notes |
| --- | --- | --- |
| Side menu | `sidemenu_title_nav`, `sidemenu_title_bookmark`, `sidemenu_title_outline`, `sidemenu_title_annotation`, `sidemenu_title_stickers` | Page Navigation, Bookmarks, Outlines, Annotations, Stickers |
| Bookmarks | `Add Bookmark`, `Edit Bookmark`, `ballon_side_bookmark_popupmenu_layout`, `modal_popup_bookmark_edit` | Create/edit bookmark; add/edit bookmarks is a paid feature |
| Outlines | `sidemenu_title_outline`, `outline_remove_caustion_msg` | Browse document outline; outline deletion string present |
| Page navigation | `btn_page_navigation_desc` ("Page Manager"), `nav_page_filter_all/annotated`, `nav_page_go_action` | Thumbnail navigation with All/Annotated filter |
| Annotation panel | shots 05/13/20; `side_annotation_filter_all/anno/link/masking`, `side_annotation_delete_annotation_all` | Grouped by page; filter All/Annotations/Links/Maskings; multi-delete |
| Annotation row | shots 05/13/20 | Type icon, colour/dashed thumbnail, snippet, page number; tap to jump |
| Link rows | `side_annotation_filter_link`, `disconnect_annotation_link_*` | Show source/target document and page; delete link |

The annotation panel is not a flat list: page headers are used, and link rows show
direction (`To:` / `From:`) with document and page. That is the pattern worth
reproducing.

### 2.6 Study note and annotation linking

| Surface | Evidence | Notes |
| --- | --- | --- |
| Popup document | `btn_popupnote_desc` ("Popup Document"), `default_popupnote_title` ("New Note"), `airaction_popupnote_open/close` | A second document opens over/below the PDF |
| Popup gesture | `setting_gesture_enable_popupnote_open_close`, `setting_gesture_enable_popupnote_scale` | Open/close by gesture; three-finger pinch to resize |
| Split/popup swap | `ic_popup_toolbar_popup_to_split`, `ic_popup_toolbar_split_to_popup` | Toolbar actions swap between popup and split |
| Bottom pane | shots 03/04/12/13/19 | "Study note" title bar: expand, layout grid, undo, close |
| Capture | `video_help_15` L gesture, `16` box gesture, `17` hold; `msg_addchecklist_guide`, `msg_addtextbox_guide` | Capture an area or selection into the note |
| Auto backlink | Play description; shots 03/04/05; `side_annotation_filter_link` | Captured text/image gets a link to its source |
| Link browsing | `setting_reference_link`, `msg_please_drag_and_drop`, shots 05/13/20 | Links panel shows To/From; tap to jump to the source page |
| Link deletion | `disconnect_annotation_link_msg`, `disconnect_annotation_link_title` | Delete link data between documents |

This is Flexcil's signature loop: **read → select/capture → drop into a note →
automatic backlink → jump back to the source**. It is more valuable than another
pen style.

### 2.7 Tabs, split, popup, reading modes

| Surface | Evidence | Notes |
| --- | --- | --- |
| Document tabs | `doctab_close_all_tab`, `doctab_close_other_tab`, `doctab_hide_tab_bar`, `doctab_show_tab_bar`, `video_help_7/8`; shots 11/16/23 | Tab strip with close x and overflow; close all/others, hide bar |
| Split screen | `btn_page_split_desc` ("split screen"), `btn_page_unsplit_desc` | Two documents side by side |
| Popup viewer | `setting_gesture_enable_popupnote_*`, `airaction_popupnote_*` | Floating, resizable, open/close by gesture |
| Multiple pages | `video_help_5`; shots 08/16/23 | Up to four pages in a spread |
| First page alone | `setting_singlecover_view_mode` | Cover by itself before the spread |
| Continuous scroll | `setting_continuous_scroll`, `setting_scrolling_direction` | Vertical continuous scrolling and scroll direction |
| Fullscreen | `video_help_6`; shots 06/14/21 | Chrome hidden; minimal back/page/pen/side-menu controls |
| Reading options | `setting_reading_opt`, `setting_color_filter`, `toast_msg_reading_mode` | Reading options, colour filter, read-only mode |
| One-finger pan | `setting_gesture_single_finger_panning_gesture/pen` | Pan with one finger in gesture or pen mode |

The landscape shots show four pages and a tab strip; the phone shots show one page
with the same chrome collapsed. That is the right split for a phone port.

### 2.8 Audio recording and replay

| Surface | Evidence | Notes |
| --- | --- | --- |
| Recording service | `RecordingService`; `FOREGROUND_SERVICE_MICROPHONE`, `RECORD_AUDIO` | Foreground microphone service |
| Duration | `audio_recording_start` | Advertised up to 6 hours per recording |
| Recording list | `audiolist_title`, `audio_name_edit_title`, `remove_audiodata_caustion_*` | List, rename, delete |
| Playback | `audiooption_playspeed`, `0.5x`…`2.0x` | Variable playback speed |
| Note sync | `audiooption_use_addsyncdata_inplaying`, `audiooption_use_syncrendering_inplaying` | Merge notes made during playback; replay handwriting as animation |
| Multi-doc sync | `audiooption_use_multidoc_syncdata` | Sync notes across multiple documents during recording |
| Jump to audio | `nav_audiopage_move_caustion_title` | Tap a note to jump to the recording point |
| Edit warning | `movepage_audiodata_caustion_msg` | Editing pages can remove audio/note sync data |
| Backup | `progressing_audio_backup_*`, "Audio File Backup" | Audio files can be backed up |
| Limits | `err_cant_playing_in_recording`, `err_cant_recording_in_playing`, `slient_recording_dueto_os_privacy_policy` | Cannot record while playing; background recording may be silent |
| Premium | `premium_purchase_window_header_voicerecording_*` | Voice recording + handwriting playback is premium |

This is a large feature. For My PDF Writer it is a later, local-only option, not a
v1 requirement.

### 2.9 Pens, erasers, lasso, shapes

| Surface | Evidence | Notes |
| --- | --- | --- |
| Pen presets | `assets/Defaults/WritingView/DefaultPens.json` | Types, colours, widths, straight mode, smoothing |
| Pen toolbar | `pentoolbar_management_pentool_count_*`, `tool_item_*` | Up to 30 pens in the toolbar; 50 favourites/colours premium |
| Pen types | `purchase_standard_content_desc8`, premium fountain-pen header; `pen_stroke_stabilization` | Fountain pen (pressure), laser pen, highlighter, stabilisation |
| Pen skins | `assets/PenToolbarSkins/` | Two bundled skins; cosmetic, do not reuse |
| Eraser | `eraser_stroke_path`, `eraser_split_path`, `eraser_pressure_eraser`, `eraser_include_highlighter`, `eraser_clear_page` | Stroke, partial, pressure, highlighter-only, clear page |
| Page eraser | `eraser_leftpage`, `rightpage`, `lefttoppage`, etc. | Erase by page region |
| Lasso | `tool_item_lasso`, `lasso_setting_enable_pen/highlighter/image/textbox` | Free/rect lasso; choose which content types are selectable |
| Lasso editing | `video_help_23/24` | Edit text/image and handwriting with lasso |
| Shapes | `shape_polygon_side`, `straight_line`, `purchase_standard_content_desc4` | Shape drawing and straight line; polygon sides |
| Other tools | `tool_item_image`, `tool_item_takephoto`, `tool_item_sticker`, `tool_item_checklist`, `tool_item_stickynote`, `tool_item_textbox` | Image, camera, sticker, checklist, sticky note, text box |
| Gestures | `setting_use_eraser_gesture`, `setting_gesture_enable_undo_redo`, `video_help_14/22` | Eraser gesture; undo/redo gesture |

### 2.10 Page manager, export, backup, cloud

| Surface | Evidence | Notes |
| --- | --- | --- |
| Page manager | `btn_page_navigation_desc`, `nav_page_filter_all/annotated`, `video_help_28` | Thumbnail grid, annotated filter, page jump |
| Page editing | `nav_addpage_menu_*`, `video_help_29/30` | Add page/template/photo, paste copied pages, copy/paste pages |
| Merge/extract | `btn_merge_desc`, `progressing_title_merge*`, `progressing_title_exportpage*` | Merge documents, export/extract pages |
| Export modes | `pdfexport_opt_original`, `pdfexport_opt_flatten`, `pdfexport_opt_desc_original/flatten` | Editable original annotations vs flattened PDF |
| Export includes | `pdfexport_opt_include_background/drawing/image/stickynote/text` | Page background, handwriting, images, sticky notes, text boxes |
| Export guard | `msg_adx_export_remove_watermark_*`, premium watermark header; `toast_select_max_share_pages` | Free watermark; remove by ad or premium; share up to 10 pages |
| Folder export | `folder_export_title/desc`, `.flex` files | Export a folder as `.flex` |
| Backup/restore | `setting_backup_*`, `setting_restore_*`, `msg_backup_*`, `wrong_backup_file` | Manual local backup/restore; `.flex` archive |
| Cloud sync | `setting_cloud_sync_*`, `cloudsync_*`, `flxsubs_sync_*` | Account-based sync, Google Drive strings, storage/device limits, conflict merge |
| Web viewer | `webviewer_upload_*`, `web_viewer_notice_*` | Upload to PC web viewer; account required; 14-day retention |
| PDF/note editing premium | `premium_purchase_window_header_documentediting_*` | Merge PDFs/notes, export pages |

I found no print-specific labels in the resource dump. Share/save-to-PDF is the
observable export path; do not assume a print flow.

### 2.11 Premium boundaries and advertised limits

The build exposes both one-time "Flexcil Standard" strings and subscription/plan
strings. The exact current SKU model was not verified.

| Area | Advertised premium unlock |
| --- | --- |
| Pens | 50 pens, 50 favourites/colours, pressure fountain pen |
| Editing | Edit/merge PDFs and notes, edit text/images, PDF/note page export |
| Organisation | Infinite folders/notes, premium covers/templates, folder colour |
| Reading | Add/edit bookmarks, open PDFs as pop-up notes |
| Study | Masking tape, voice recording + handwriting playback |
| Output | Remove ads, remove watermark |
| Cloud | Cloud sync |
| Content | Diary/sticker/template packs (20+ items) |

Observed quantitative limits:

- Recording: up to **6 hours** per file.
- Translation: up to **5,000 characters**.
- Share: up to **10 pages**.
- Pen toolbar: up to **30 pens**; **50** pen favourites/colours advertised.
- Fonts: up to **200** favourite fonts.
- Rewarded premium: **5 times/day**, **20 minutes** per view, up to **100 minutes/day**.
- Premium content: **20+** diary/sticker/template items.

Free-version strings say "limited" but do not enumerate every cap. Treat the list
above as advertised guardrails, not verified enforcement.

### 2.12 Study extras and AI surfaces

| Surface | Evidence | Notes |
| --- | --- | --- |
| Interactive checklist | `tool_item_checklist`, `checklist_*`, 33 checklist assets, 15 built-in checklist theme assets | Check items, linked progress graphics, groups, correct rate |
| Masking tape | `masking_*`, premium header | Cover answers, O/X, score, side-panel filter |
| Stickers/planner | `tool_item_sticker`, 23 basic stickers, planner purchase strings | Some planner packs use licensed characters; avoid |
| On-device model | `assets/Model/interactive_segmentation_model.tflite` (6.2 MB) | Likely capture/crop segmentation; exact use not runtime-verified |
| Flexcil AI | `msg_tutorial_popup_title1`, `web_ai_*`, `setting_lab_web_ai_description` | AI service selection, drag area into AI input; account/web based |
| Web viewer | `webviewer_*`, `web_viewer_notice_*` | Send a document to the web/PC; account required; 14-day retention |

### 2.13 Negative findings

- **No tag-specific string keys found.** Flexcil uses folders, favourites, recents and search.
- **No `MANAGE_EXTERNAL_STORAGE`** and no `READ_MEDIA_*`.
- **No native libraries.** No `.so` in the APK.
- **No additional locales.** Only the default resource set.
- **No print labels found.** Share/save is the observable output path.
- **No code-level verification.** All behavior above is inferred from resources,
  manifest, assets and marketing shots.

---

## 3. UI/UX patterns worth reproducing on a phone

### 3.1 Home and document list

**Pattern.** A calm, cover-forward library: left rail for Search / Documents /
Trash / Files / Recents / Favourite; top filter All/PDFs/Notes; sort and grid/list;
coloured folder cards with item count and date; document cards with cover/page
thumbnail, title, size/date, star and overflow; a FAB that expands into New Note,
Quick Note, New Folder, Files and Connections; long-press menu for Rename,
Duplicate, Move, Delete and Share.

**Phone translation.**
- Replace the permanent left rail with a compact top app bar plus filter chips or a
  bottom navigation bar. Keep Search, Recents, Favourites and Trash reachable in
  one tap.
- Use a two-column grid by default; list mode for long titles. Folder cards can be
  shorter and span two columns.
- Put the primary action in a thumb-reachable FAB speed dial: Import PDF, New note,
  Quick note, New folder. Do not bury import in an overflow.
- Long-press opens a bottom sheet, not a floating desktop menu. Include Rename,
  Duplicate, Move, Delete, Share, Add tag (our feature) and Add to folder.
- Show a single empty-state action. Avoid a wall of feature cards on first launch.

### 3.2 Reading chrome and tabs

**Pattern.** The reader is mostly content. A slim top bar has back, view-mode,
share/export, document title and overflow; a tab strip below it holds multiple
documents with close x and a close-all/others/hide-bar menu; a floating page pill
and a compact tool pill sit over the page; the side-menu icon opens the annotation
drawer. Fullscreen hides everything except a back button, page indicator, pen-mode
toggle and side-menu button.

**Phone translation.**
- Make the top bar and tab strip auto-hide on scroll; tap the page to toggle chrome.
- Cap visible tabs at 3–4 with a "+N" overflow. Long-press a tab for close others /
  close all / hide tab bar. Restore the last session on cold start.
- Put the page pill at the bottom centre, not the top right; thumb reach matters.
- Keep the pen/gesture toggle and undo in a bottom floating bar. Move export/share
  into the top overflow.
- Use immersive mode for fullscreen, but keep a small always-available exit target.

### 3.3 Annotation panel

**Pattern.** A side panel with page navigation, bookmarks, outlines and
annotations. The annotation view groups rows by page, offers All / Annotations /
Links / Maskings filters, and each row shows a type icon, colour-coded/dashed
thumbnail, two-line snippet and page number. Link rows show source/target document
and page. Tapping a row jumps to the page; multi-select deletes in bulk.

**Phone translation.**
- Use a modal bottom sheet at 80–90% height with a drag handle, not a right rail.
- Keep the filter segmented control sticky at the top; page headers sticky within
  the list.
- Row layout: 40–48 dp type icon or colour chip on the left, title/snippet in the
  middle, page chip on the right. A chevron is optional; the whole row is tappable.
- Long-press enters multi-select; show a bottom action bar with Delete, Share,
  Copy link. Add "Select all" only after a selection exists.
- Link rows need a distinct icon and a two-line "From/To document · page" label;
  tapping should jump and close the sheet.
- Add a "Hide annotations" toggle in the reader overflow with a persistent banner
  when active, mirroring Flexcil's hide mode.

### 3.4 Study note panel and capture/backlinks

**Pattern.** A second document (usually a notebook) opens in a bottom or side pane.
On phone it is a bottom pane with a dark title bar: expand, layout grid, title,
undo, close. The PDF stays visible above. Selected text/images can be dragged from
the PDF into the note; a link icon appears on the captured block, and the Links
filter shows the relationship in both directions. Tapping the link jumps back to
the source page.

**Phone translation.**
- Implement a bottom pane at 40–50% height with a drag handle, a full-screen toggle
  and a close button. Remember the height per document.
- Use a distinct surface colour for the pane title bar so it reads as a separate
  document, not a toolbar.
- Long-press selection → drag ghost → highlight the drop zone → drop. On drop,
  create a block with a small link chip: document icon + page number. Tapping the
  chip jumps and closes the pane if needed.
- Store source document id, page index and normalized rect with the captured block.
  That is enough for a reliable backlink even after page reordering, if you remap
  page ids the same way annotations are remapped.
- The pane should be a real note editor, not a text field. Reuse the notebook
  canvas, templates, pen tools and undo stack.

### 3.5 Split and popup concepts

**Pattern.** Flexcil supports two side-by-side documents ("split screen"), a
floating popup viewer that can be resized with a three-finger pinch, and a swap
between popup and split. On tablets this is a multitasking surface; on phones the
same note becomes a bottom pane.

**Phone translation.**
- Ship the bottom pane first; it delivers most of the value with far less window
  management complexity.
- Add a "float" action later: a draggable card with snap-to-edge and a resize
  handle. Do not copy the three-finger pinch gesture; it conflicts with zoom and is
  hard to discover.
- In landscape, allow a 50/50 split between PDF and note. In portrait, keep the
  bottom pane.
- Do not attempt a four-page spread on a phone. Reserve 2-up for landscape and
  4-up for tablets.

### 3.6 Reading mode and fullscreen

**Pattern.** Fullscreen reading with minimal chrome; continuous vertical scroll;
scroll-direction setting; one-finger panning in gesture and pen modes; first page
alone; multiple-page spread; reading options and colour filter; read-only mode.

**Phone translation.**
- Default to continuous vertical scroll with a small gap and page-number overlay.
  Keep pinch zoom and double-tap fit-width.
- Tap to toggle chrome; edge-swipe or page-pill tap for page navigation. Avoid
  horizontal pager gestures that fight with text selection.
- Add fit-width / fit-page buttons in the overflow; keep night mode where it is.
- Add "first page alone" only when a 2-up landscape mode exists.
- A read-only badge is enough for shared/imported documents; don't add a separate
  mode switch unless editing must be locked.

### 3.7 Selection, drag and pen toolbar

**Pattern.** Gesture-first selection (underline, bracket, circle, hold), a compact
tool pill with pen/text/image/comment/lasso, and a pen toolbar with presets. The
selection can be dragged to another page, a note, or an AI field. Lasso can select
pen, highlighter, image and text box separately.

**Phone translation.**
- Long-press a word to start selection; show handles and a floating action bar
  above the selection. Do not require a "select" tool.
- Offer gesture shortcuts only after the basic handles work: underline-drag,
  bracket-drag, circle-lasso. Add a short first-run hint, not a modal tutorial.
- Selection action bar: Copy, Highlight, Underline, Strikethrough, Dictionary,
  Translate, Bookmark, Add to note, Share. Keep the top 5 visible and overflow the
  rest.
- Pen toolbar: 3–5 visible slots (pen, highlighter, eraser, lasso, text) plus an
  overflow. Long-press a slot to change colour/width; tap the active slot to open
  the pen box.
- Add a pen/gesture mode toggle. In gesture mode one finger pans; in pen mode one
  finger draws. This is the single most important stylus/phone conflict to resolve.

### 3.8 Page manager, export and feedback

**Pattern.** A page-manager grid with All/Annotated filters, multi-select, rotate,
delete, move, copy/paste pages, add page from template/recent/photo, merge and
extract. Export offers original vs flattened, include background/handwriting/
images/sticky notes/text boxes. Progress is explicit.

**Phone translation.**
- Keep the existing thumbnail grid, but add an "Annotated" filter and a bottom
  action bar that appears on selection. Drag-to-reorder is optional; arrow buttons
  are more reliable on phone.
- Add "Insert page" with three choices: blank/template, photo, paste copied page.
- Export sheet: page range (current/all/selected), format (flattened/original),
  include toggles, file name, then Save/Share. Show a progress indicator and a
  success snackbar with "Open" and "Share".
- For errors, keep the sheet open and show a retry action; never lose the user's
  export settings.

---

## 4. Priorities for My PDF Writer

The existing app already covers the PDF workbench (annotations, page edits, search,
OCR, forms, export, print/share) and a notebook editor (templates, layers, lasso,
pen box, bookmarks, excerpts, translation). The gap is the **reading-to-study
loop** and the **phone-native multi-document surface**.

### 4.1 Ten must-add

| # | Must-add | What to build | Map to existing code |
| ---: | --- | --- | --- |
| 1 | Unified library hub | One home for PDFs and notebooks: All/PDFs/Notes, folders, tags, favourites, trash, sort, grid/list, search title + content + annotations, FAB speed dial | `ui/NotebookHomeScreen.kt`, `ui/LibraryScreen.kt`, `data/Stores.kt`, `data/NotebookStore.kt` |
| 2 | Document tabs | Tab strip with close x, close others/all, hide bar, session restore; cap 3–4 visible tabs on phone | `MainActivity.kt`, `ui/ViewerScreen.kt`, `ui/PdfViewModel.kt`, new `ui/DocumentTabs.kt` |
| 3 | Continuous scroll + fullscreen | Vertical continuous pager, tap-to-toggle chrome, page pill, fit width/page, immersive fullscreen; 2-up landscape later | `ui/PdfCanvas.kt`, `ui/ViewerScreen.kt`, `ui/PdfViewModel.kt` |
| 4 | Text selection layer | Character-box extraction, normalized selection rects, handles, long-press drag; persist highlight/underline as annotations | `pdf/PdfTextExtractor.kt`, `ui/PdfCanvas.kt`, `data/Models.kt`, `pdf/AnnotationPainter.kt`, `pdf/PdfExporter.kt` |
| 5 | Selection action bar | Copy, highlight, underline, strike, dictionary, translate, bookmark, add to note, share; dictionary via external intent | `pdf/TextActions.kt`, `ui/ExcerptSheet.kt`, `ui/ReaderSheets.kt`, `ui/ViewerScreen.kt` |
| 6 | Annotation panel | Bottom sheet, All/Annotations/Links filters, page groups, type icons, snippet, jump, multi-select delete, hide toggle | `ui/ViewerSheets.kt`, `ui/PdfViewModel.kt`, `data/Models.kt` |
| 7 | Bottom study-note pane | Resizable 40–50% pane, expand/fullscreen, undo, close, remembers height and note; real notebook canvas | `ui/NoteEditorScreen.kt`, `ui/NoteCanvas.kt`, `ui/NotebookViewModel.kt`, `ui/ViewerScreen.kt` |
| 8 | Capture with backlinks | Drag text/image into note; block stores source doc/page/rect; tap chip jumps back; Links filter | `data/Excerpt.kt`, `data/ExcerptStore.kt`, `ui/ExcerptSheet.kt`, `ui/NoteCanvas.kt`, `ui/PdfViewModel.kt` |
| 9 | Floating pen/gesture toolbar | Pen, highlighter, eraser, lasso, shape, text; presets; pen/gesture mode toggle; undo gesture | `ui/ViewerScreen.kt`, `ui/PenBoxSheet.kt`, `data/PenBoxStore.kt`, `ui/NoteLasso.kt`, `ui/NoteCanvas.kt`, `pdf/AnnotationPainter.kt` |
| 10 | Export presets | Current/all/selected pages; flattened vs original; include background/handwriting/images/text boxes; clean share | `pdf/PdfExporter.kt`, `pdf/NoteExporter.kt`, `ui/DocumentStyleDialog.kt`, `ui/ViewerScreen.kt` |

Why these ten: they turn the existing single-document viewer into a study surface
without copying Flexcil's content or cloud stack. Items 4, 5, 7 and 8 are the
differentiating loop; items 1, 2, 3 and 6 are the phone-native shell; items 9 and
10 close obvious gaps in the existing editor.

### 4.2 Five next

| # | Next | What to build | Map to existing code |
| ---: | --- | --- | --- |
| 1 | Page manager 2.0 | Annotated filter, copy/paste pages, insert template/photo page, drag reorder, split/merge pages | `ui/OrganizeScreen.kt`, `data/PageOps.kt`, `pdf/PdfTools.kt` |
| 2 | PDF bookmarks/outline editing | Named bookmarks, reorder, jump, outline browsing; reuse notebook outline semantics | `ui/NotebookOutline.kt`, `ui/NotebookOutlineSheet.kt`, `ui/ReaderSheets.kt`, `ui/PdfViewModel.kt` |
| 3 | Local audio recording + replay | Explicit mic permission, foreground service, timestamped strokes, playback highlight; no cloud | New `audio/` package, `ui/ViewerScreen.kt`, `ui/PdfViewModel.kt` |
| 4 | Cover/template studio | Original covers, custom template import (image/PDF), paper colour/orientation, page size | `ui/NotebookHomeScreen.kt` create dialog, `ui/NoteEditorScreen.kt` template dialog, `ui/PaperBackground.kt`, `data/DocumentStyle.kt` |
| 5 | Local backup/restore + folder export | `.mypdf` zip of docs/states/notes, SAF import/export, progress and conflict-safe restore | `data/Stores.kt`, `data/NotebookStore.kt`, `pdf/PdfShare.kt`, new `backup/` package |

### 4.3 Five skip or later

| # | Decision | Item | Reason |
| ---: | --- | --- | --- |
| 1 | **Skip** | Ads / rewarded premium unlock | Conflicts with the no-ads, no-INTERNET positioning; adds SDKs, permissions and privacy cost |
| 2 | **Later** | Account + cloud sync (Google Drive/Flexcil cloud) | Requires INTERNET, account, server and privacy policy; local backup first |
| 3 | **Skip** | Licensed planner/sticker store | Brand/IP risk, low differentiation, content-heavy maintenance |
| 4 | **Later** | AI web viewer / web upload / desktop portal | Account/cloud dependency; privacy tradeoff; not needed for offline study |
| 5 | **Later** | Floating popup + 4-page spread on phone | Tablet-first complexity; bottom pane + landscape 2-up covers most value |

---

## 5. IP boundary

**Safe to reuse:** functional concepts, information architecture, interaction
patterns, and the general reading-to-note workflow. These are ideas, not copied
expression.

**Do not reuse:**
- Flexcil code, dex files, resources, layouts, drawables, icons, colours or UI copy.
- PDF templates, covers, stickers, pen-toolbar skins, audio, dictionary data,
  bundled fonts, sample PDFs or screenshots.
- The Flexcil name, logo, app icon, store listing copy, or any branding that
  implies affiliation.
- Pixel-for-pixel layouts or a copied visual identity. Build an original Material 3
  design with our own typography, spacing and iconography.

**Implementation rules for this repository:**
- Keep the APK and Play screenshots outside the repo. This document is the only
  artifact committed from the analysis.
- Draw templates and covers with our own code (`PaperBackground`, Compose shapes)
  rather than importing Flexcil PDFs or images.
- Use our own link model and note-block UI; do not copy Flexcil's proprietary
  popup/split implementation.
- If we add a dictionary or audio, use permissively licensed data or user-created
  recordings. Do not extract content from the APK.
- No DRM circumvention, no premium bypass, no extraction of paid content.

---

## 6. Limitations

- **Static only.** Findings come from `aapt2`, `apksigner`, `unzip`, resource
  dumps, the public Play page and screenshots. There was no JADX, no code
  decompilation, no runtime execution, no Frida, no network/account/purchase flow.
- **Behavior is inferred.** Resource labels and component names show that a surface
  exists; they do not prove it works, is reachable, or is enabled in this build.
- **Premium gating is advertised, not verified.** The exact free caps may differ
  from the strings.
- **Cloud, audio sync and dictionary backends are not verified.** Treat their
  descriptions as product claims plus resource evidence, not implementation facts.
- **Marketing mockups may differ.** Play screenshots are composites; tablet and
  landscape shots may not represent the phone layout, and ads/watermarks may be
  omitted.
- **Only the default locale is present.** No localization or copy-quality analysis.
- **Signer is not conclusive.** The generic certificate may indicate a mirror or
  re-signed build; this analysis does not establish provenance.
- **No security assessment.** Permissions and SDKs are listed, but no vulnerability
  or privacy audit was performed.

---

## 7. Bottom line

Flexcil's advantage is not raw PDF editing. It is the **linked study loop**:
gesture text selection, a page-grouped annotation panel, a second note pane, and
automatic backlinks between the PDF and the note. Tabs, continuous scroll and
fullscreen make that loop comfortable on a tablet; a phone port should collapse
the same ideas into a bottom pane, a bottom sheet and a compact tab strip.

My PDF Writer already has the hard PDF and notebook pieces. The highest-return
work is to connect them: a unified library, text selection with actions, an
annotation panel, a bottom study-note pane, capture with backlinks, and export
presets. Skip the ad/cloud/AI/content-store surface; it conflicts with the offline,
no-ads product position and would dilute the study workflow.
