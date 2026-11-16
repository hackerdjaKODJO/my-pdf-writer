# StarNote 1.5.4.1 — static reverse-engineering and UX notes

> Scope: static inspection of a user-supplied APK only. No app execution, no JADX,
> no dynamic instrumentation, no network calls. This document is input for an
> original, offline revision of My PDF Writer — not a specification to clone.
>
> Observed: 2026-11-16. APK and Play screenshots stay outside the repository.

---

## 1. Artifact identity and evidence

### 1.1 Identity

| Field | Value |
| --- | --- |
| File | `/sdcard/Download/apk_for_pdf/StarNote_1.5.4.1.apk` |
| Label | `StarNote` |
| Package | `com.onyx.galaxy.global.note` |
| Version | `1.5.4.1` (versionCode `5706`) |
| SDK | minSdk `29`, targetSdk `36`, compileSdk `36` |
| APK size | `141,127,399` bytes (141.1 MB / 134.6 MiB) |
| Uncompressed | `180,531,877` bytes, 14,711 zip entries |
| SHA-256 | `0d7ec1d4de20cc0565215ee757d38a0fcf29b3d892def4944317dfd01b608897` |
| Signer | Onyx International; cert SHA-256 `c54552804534f876822a77290eecee02413ffc9a6d93c9b51b953549e667302f` |
| Launchable | `com.onyx.galaxy.note.ui.activity.SplashActivity` |
| Application class | `com.sagittarius.v6.StubApplication` (Baidu packer stub) |
| Code packaging | `classes.dex` is only 35,856 bytes; real code is in 17 `assets/baiduprotect*.jar` plus 16 `.i.dex` stubs |
| Components | 41 activities, 15 services, 15 receivers, 8 providers |
| Permissions | 36 `uses-permission` entries (some duplicated) |
| Locales | 130 locale configs, including `ko` and `ko-KR` |
| Resources | 2,943 string resources; `resources.arsc` is 4.08 MB |
| Assets | 509 files under `assets/`; 3,801 files under `res/` |
| Screens | supports small/normal/large/xlarge; all densities including 640+ |
| Native code | no `.so` in the APK |

The signer and package indicate an Onyx International build. The global package
is not the Chinese `com.onyx...` note package, but the build still carries Chinese
account, payment, cloud and vendor SDK components.

### 1.2 Permissions, grouped

| Group | Permissions present |
| --- | --- |
| Network/account/billing | `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `BILLING`, `AD_ID`, `ACCESS_ADSERVICES_*`, install-referrer, `READ_GSERVICES`, Huawei market common data |
| Camera/microphone | `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `FLASHLIGHT`, `VIBRATE` |
| Media/storage | `READ_MEDIA_IMAGES`, `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`, legacy `READ/WRITE_EXTERNAL_STORAGE` |
| System/foreground | `SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_DATA_SYNC` |
| Privacy/identity | `USE_BIOMETRIC`, `USE_FINGERPRINT`, `GET_CLIPBOARD_SERVICE` |
| Vendor | OPPO pencil/IoT permissions; Huawei app-market permission |

Notable manifest flags: `allowBackup=true`, `usesCleartextTraffic=true`,
`requestLegacyExternalStorage=true`, `largeHeap=true`. There is **no**
`MANAGE_EXTERNAL_STORAGE`.

### 1.3 Declared components and integration surface

| Type | Examples | Relevance |
| --- | --- | --- |
| Home/editor | `SplashActivity`, `NoteListActivity`, `NoteScribbleActivity`, `NoteFilePickerActivity` | Library, notebook editor, file picking |
| Import/share | `ThirdPartyImportActivity` (exported; `VIEW`, `SEND`, `SEND_MULTIPLE`, `*/*`) | Opens PDFs/other files from other apps |
| Account/cloud | `LoginActivity`, `RecentLoginActivity`, `UserCenterActivity`, `DataRepositoryActivity`, `ShareResourceActivity` | Account, Pro, cloud data |
| Settings | `SettingsActivity` | Local/cloud backup, AI, stylus, privacy, theme |
| Media | `RecordService` (microphone/dataSync), `ScreenCaptureService` (media projection) | Recording; screen capture for lesson/PiP-style workflows |
| Handwriting | `HWRProvider2` (not exported) | Sunia handwriting-recognition provider |
| Widgets | Favorite-note, note-list medium/large, personal-data, quick-create | Home-screen widgets |
| Shortcuts | Quick note, open note, home, user center, local backup | Launcher shortcuts |
| Provider | `FileProvider`, crop provider, startup provider | File sharing/cropping |
| Optional libraries | `libtflite_interface_wrap`, `libbrushstroke_interface`, `librecognize_interface`, Huawei/Honor penkit | Vendor stylus/ink engines; optional at runtime |

### 1.4 Evidence commands

Run these against the APK. They are read-only and do not unpack code.

```bash
APK=/sdcard/Download/apk_for_pdf/StarNote_1.5.4.1.apk

sha256sum "$APK"
aapt dump badging "$APK" | head -40
aapt dump badging "$APK" | grep -E \
  'package:|sdkVersion|targetSdkVersion|application-label:|launchable-activity|locales:'
apksigner verify --print-certs "$APK"

aapt dump xmltree "$APK" AndroidManifest.xml \
  | grep -E 'E: (activity|service|receiver|provider)|android:name|android:exported|android:authorities'

unzip -l "$APK" | awk '{print $4}' | grep -E '^(assets|res)/'
unzip -l "$APK" | awk '{print $4}' | grep -E '^assets/template_.*\.svg$' | wc -l
unzip -l "$APK" | awk '{print $4}' | grep -E '^assets/cover_.*\.(webp|png)$' | wc -l
unzip -p "$APK" res/raw/category_template.json
unzip -p "$APK" res/raw/category_cover.json
unzip -p "$APK" assets/sunia/en_US.zip \
  | python3 -c 'import sys,zipfile,io; z=zipfile.ZipFile(io.BytesIO(sys.stdin.buffer.read())); print("\n".join(z.namelist()))'
```

Resource-string evidence was read from a local `aapt dump resources` dump. Short
labels quoted below are from that dump; long proprietary copy is intentionally
paraphrased.

---

## 2. Feature inventory

The inventory below is inferred from the manifest, resource strings, layout names,
assets and the Play screenshots. “Present” means the artifact declares or ships
the surface; it does not mean the flow was executed.

### 2.1 Notes

| Feature | Evidence / representative labels | Korean sample |
| --- | --- | --- |
| Library sections | `All notes`, `Note`, `PDF`; left rail `Search`, `All notes`, `Trash`, `Folder`, `Tag` | 모든 메모, 메모, PDF, 검색, 휴지통, 폴더, 태그 |
| Notebook grid/list | Gallery/list toggle, sort, batch management, dates, tag chips, dashed create tile | 갤러리 보기, 목록 보기, 일괄 관리 |
| Cover-forward cards | 61 cover designs, portrait/landscape; change cover from long-press menu | 커버, 커버 변경 |
| Create flow | Create new note, unbound/no-margin note, import files, import image, take photo, quick note | 메모 생성, 빠른 노트 |
| Paper editor | Blank/lined/grid/dot/Cornell/planner/music and 45 named templates; page margin; page direction | 템플릿, 종이, 페이지 |
| Pen tools | Writing pen, fountain, calligraphy, ballpoint, pencil, brush, soft-paint, highlighter, laser | 필기 펜, 만년필, 서예 펜, 형광펜, 레이저 포인터 |
| Eraser modes | Stroke erase, pressure-sensitive eraser, highlighter-only, tape-only | 스트로크 지우기, 압력 감지 지우개, 형광펜만 지우기 |
| Selection/shapes | Lasso (free/rectangular), shapes with fill/stroke/opacity, one-stroke shape conversion | 올가미, 도형, 한 획 도형화 |
| Text | Text box, rich text, font size/line spacing, links, table | 텍스트 상자, 표, 링크 |
| Objects | Image, camera, sticker, masking tape, ruler, magnifier | 사진, 스티커, 테이프, 자, 돋보기 |
| Layers | Add/rename/hide/lock/opacity/merge/delete/restore, layer sync in split view | 레이어, 불투명도, 레이어 동기화 |
| Page sidebar | Thumbnails; tabs Overview/Bookmark/Excerpt/Outline; page search; deleted pages | 문서 개요, 책갈피, 발췌, 아웃라인 |
| Document tabs | Multiple note/PDF tabs, close, add, split-screen horizontal/vertical | 가로 분할 화면, 세로 분할 화면 |
| Writing modes | Pen writing vs finger/stylus writing; one finger writes, two fingers pan; palm rejection | 펜 쓰기, 손가락/스타일러스 쓰기, 손바닥 오작동 방지 |
| Stylus integration | Stylus button shortcuts, stylus swipe-to-scroll, vendor support for Samsung/Huawei/Xiaomi/OPPO/Vivo/Lenovo | 스타일러스 설정, 스타일러스로 페이지 스와이프 |
| Ink feel | Thickness in mm, stroke stabilization, pressure sensitivity, adaptive stroke width | 스트로크 안정화, 압력 감도 |
| Page scale lock | `page_scale_locked`; layout `editor_view_lock_layout`; adaptive paper | 페이지 비율 잠금 |
| Note security | 4–12 digit note password, biometric unlock, auto-lock interval, note encryption | 메모 비밀번호, 메모 암호화 |
| Export/share | PDF (editable/non-editable), PNG, full text, include template/tape/audio, share/print | 내보내기, 편집 가능한 PDF |
| Widgets/shortcuts | Quick create, favorite note, recent notes, personal stats | 빠른 노트, 자주 쓰는 노트 |

The editor is **page-based**: a PDF import and a blank notebook use the same page
canvas and annotation toolbar. That is the single most important structural idea
to reuse. It also matches My PDF Writer’s existing `PageSpec` + `Annotation` model.

### 2.2 PDF

| Feature | Evidence / representative labels | Notes |
| --- | --- | --- |
| Import/open PDF | `Import PDF`, `ACTION_VIEW`/`SEND` for `*/*` | PDF is presented on the same page canvas and toolbar |
| Annotate PDF | Same pen/highlighter/shape/text toolbar as notes; handwriting in margins | Screenshots 03/09/12 |
| Text selection toolbar | Highlight colors, underline, strikethrough, copy, translate, add to excerpt | Screenshot 06/12; Korean 강조, 밑줄, 복사, 번역, 발췌에 추가 |
| Reading aids | Document overview, outline, bookmarks, excerpts, page thumbnails, page search | 문서 개요, 아웃라인, 책갈피, 발췌 |
| Page layout | Single/continuous scroll, two-page browsing, page direction, page margin adjustment | 두 페이지 보기, 페이지 넘김 방향 |
| Multiple documents | Tabs above canvas; split-screen with a note | 가로/세로 분할 화면 |
| Export | Entire file or selected pages; editable vs non-editable PDF; include template/tape/audio; Pro removes watermark | 편집 가능한 PDF, 편집 불가능한 PDF |
| OCR | ML Kit text-recognition models and “Text recognition”/“Scan document” strings are present | No evidence of searchable-PDF export |
| Forms/security | No string/layout evidence of AcroForm fill, redaction, PDF password, digital signature, or watermark editing beyond export watermark | Static negative; not proof of absence |

This is deliberately narrower than My PDF Writer’s current PDF workbench. StarNote
does not appear to compete on forms, redaction, document passwords, watermark
editing, optimization, merge/split/image-to-PDF, or searchable OCR export.

### 2.3 Import

| Input | Evidence | Constraint |
| --- | --- | --- |
| PDF | `Import PDF`; PDF tab | Local |
| PPT / DOC | Import-files hint, conversion UI | **Requires internet** (`require_online_convert_network_warn`) — server-side conversion |
| EPUB / MOBI / AZW3 | Import-files hint; book-like imports | Local renderer; no separate Books section in captured UI |
| CAJ | Korean hint lists CAJ in addition to the English list | Region/format-specific |
| Images | Gallery import, camera, crop, scan document | Local |
| Audio | Import audio, insert recording, audio list | Local record/import; ALAC explicitly unsupported |
| Fonts | Import TTF/OTF | Local |
| Backups | Local backup, cloud backup import | Local/cloud |
| Cloud drives | Google Drive, OneDrive, Dropbox, WebDAV, Baidu, Aliyun, Huawei, NutStore, Evernote/OneNote | Account/network |

The UI’s visible file types are PDF/PPT/DOC/EPUB/MOBI/AZW3 (screenshot 04/10).
No separate “Books” rail item was found; e-book imports live in the same library.

### 2.4 Organization

| Feature | Evidence / representative labels | Notes |
| --- | --- | --- |
| Nested folders | Manage folder, create subfolder, move, delete, search, counts | 폴더 관리, 하위 폴더 생성, 다음으로 이동 |
| Hierarchical tags | Use `/` for multi-level tags; create subtag; drag/drop; swipe to delete/unlink | 태그 관리, 새 하위 태그 생성 |
| Tag tree | Counts per tag in left rail; tag chips on cards; page-level tags | 태그 |
| Pin/favorite/trash | Pin note, unpin, favorite, move to trash, restore, delete forever | 노트 고정, 휴지통으로 이동 |
| Batch actions | Batch management: select all, copy, move, tag, export, delete | 일괄 관리 |
| Sorting/view | Gallery/list, sort menu, last-edited date | 갤러리 보기, 목록 보기 |
| Search | Global search; scope by folder/tag; title/text/handwriting sections; history | 스마트 검색, 손글씨 |
| Empty states | Dedicated empty-folder/tag/search messages with create hints | — |

Folder and tag management are first-class screens, not just filter chips. The
left rail shows a tree with counts; long-press drags, swipe-left deletes. This is
a larger organizational surface than My PDF Writer’s current recent-document list.

### 2.5 Audio / AI

| Feature | Evidence / representative labels | Notes |
| --- | --- | --- |
| Recording | Record, pause, continue, stop, insert recording, audio list, export audio | Ambient, device playback, or mixed sources |
| Audio-ink sync | Handwriting replay follows audio; tap a stroke to jump to its audio position | 오디오 잉크 동기화 |
| Transcription | Real-time transcription, original transcript, audio-to-text | String says transcription supports Chinese/English |
| Translation | Audio translation, real-time translation | AI points / network |
| Summary | Smart summary with scene presets: class notes, meeting summary, general summary; highlights preview | AI points; network |
| AI settings | StarNote AI settings, AI points balance, enable/disable, Pro | `fragment_settings_ai` |
| Ink beautification | Real-time handwriting beautification, target font, unify size/spacing, trial/Pro | Vendor/HWR engine |
| Smart recognition | Handwriting recognition, image recognition, text recognition, smart search | ML Kit/Sunia; some flows need network for certification |

Audio is reachable from the editor top bar (microphone) and from the shortcut bar;
AI is behind Settings → StarNote AI and inside the audio panel. Cloud/backup is
behind Settings/account, not on the home rail.

### 2.6 Backup / cloud

| Feature | Evidence / representative labels | Notes |
| --- | --- | --- |
| Local backup | Local backup, manual backup, import local backup, progress dialog, last backup time | Offline archive |
| Cloud backup | Cloud backup, import cloud backup, path `Cloud root/StarNote` | Google Drive, OneDrive, Dropbox, WebDAV, Baidu, Aliyun, Huawei, NutStore, etc. |
| Cloud sync | Cloud sync, sync path, last sync, Wi-Fi only or Wi-Fi/mobile, background sync | Account binding per provider |
| Restore/import | Import backup, restore progress, “imported notes will not overwrite existing notes” | Non-destructive import |
| Account/Pro | Login, user center, membership, AI points, billing | Network/account required |
| Note privacy | Note password, biometric, auto-lock, encryption | Local note lock; not a PDF password feature |

My PDF Writer currently has none of this and intentionally has no `INTERNET`
permission. Keep local backup/restore as the near-term data-safety feature; defer
cloud/account unless the product decision changes.

### 2.7 Study

| Feature | Evidence / representative labels | Notes |
| --- | --- | --- |
| Study templates | Cornell A/B/C, four quadrants, Ebbinghaus, Todai, error log A/B, reading note/list, study plan, class schedule | 45 named templates, 7 categories |
| Timer/stopwatch | Timer, stopwatch, history, alarm, lap | `fragment_counter_*` |
| Self-testing | Masking tape can hide content; show all tapes; export includes tape | 테이프 |
| Excerpt collection | Text/boxed excerpt, excerpt sidebar, add to excerpt, links back to source | 발췌 |
| Linked review | Page links, audio links, link shapes, “jump to recording/page” | 링크, 녹음 위치로 이동 |
| Search-based review | Title/text/handwriting search; search scope by folder/tag | 스마트 검색 |
| Lecture review | AI class/meeting summary, transcript, translation | Network/AI points |
| No flashcard engine | No quiz/flashcard/spaced-repetition strings or layouts found | Study is template + capture + review, not SRS |

There is no dedicated “study dashboard”. Study value comes from templates, audio
summaries, tape self-testing, excerpts, and search. Do not invent a flashcard
system while claiming StarNote parity.

### 2.8 File formats

| Direction | Formats / behavior | Evidence |
| --- | --- | --- |
| Import documents | PDF, PPT, DOC, EPUB, MOBI, AZW3, CAJ | Import-files hint (EN + KO) |
| Import media | Images (gallery/camera/scan), audio | Import image/take photo/scan document/import audio |
| Fonts | TTF, OTF | Import font hint |
| Open from other apps | `VIEW`, `SEND`, `SEND_MULTIPLE` with `*/*` | Manifest |
| Export | PDF (editable/non-editable), PNG, full text, audio (single/ZIP), backup | Export strings |
| Office conversion | PPT/DOC upload and server conversion | “PPT/DOC requires internet” warning |

---

## 3. Template inventory

### 3.1 Paper templates

- **45 named base templates** shipped as SVG; **87 SVG files** including landscape
  variants.
- **7 categories** from `res/raw/category_template.json`: Base, Study, Plan, Work,
  Life, Write, Specialty.
- Additional generic base patterns are code-defined (blank, lined, grid, dot,
  etc.), plus “custom paper” and “no-bound page” variants.
- Paper templates can be applied to the current page, a new page, or all pages;
  page-margin settings can also be applied to the notebook cover (screenshot 09).
  Orientation is selectable.

Representative generic names, grouped by the shipped category (examples only;
some assignment is inferred because per-template mapping lives in packed code):

| Category | Examples |
| --- | --- |
| Base | Blank, Lined 12/16/20, Small/Mid/Big Grid, Dot grid, Grid Point, Vertical Lines, Graph paper 20×20, 25×26, 30×16, MM paper, log/semi-log |
| Study | Cornell A/B/C, Four quadrants 01/02, Ebbinghaus, Todai, Error log A/B, Reading note, Reading list, Study plan, Class schedule, English 4-line, Tianzi grid, Mig paper |
| Plan | Annual schedule, Monthly planner, Weekly schedule, Daily planner, Daily to-do, Time plan, Task list, To-do list, 100-day habits, Monthly habits A/B, Weekly habits |
| Work | Meeting minutes / Summary A, Work summary / Summary B, Accenture note 01/02, Lesson plan, SWOT 01–03, McKinsey |
| Life | Account book, Legal file, Stationery, Writing pad |
| Write | Writing pad, Stationery, 4-line paper, Letter paper |
| Specialty | Five-line staff, Four-line staff, TAB, Zither score, Badminton/Basketball/Football/Tennis plays, Tianzi/Mig, Calendar |

### 3.2 Covers

- **61 unique cover designs**, shipped as **122 files** (portrait + landscape),
  mostly WebP plus some PNG.
- **6 categories** from `res/raw/category_cover.json`: Simple, Graph, Fruit,
  Illustration, Black and white, Yearly.
- Examples: solid/label/simple A–C, iceberg/hills/desert/sunrise, fruit series,
  pets/rabbit/bear, black-and-white illustration, year-themed covers.
- Custom cover and “change cover” are supported. Covers are decorative; they do
  not affect the page canvas.

**No SVG, WebP, PNG, font, icon or cover asset from StarNote is to be copied.**
The revision should draw its own generic patterns (lines, grids, dots, Cornell
bands, checklist boxes, planner rules) with Compose Canvas, and use flat colors
or simple geometric covers.

### 3.3 Fonts, OCR, HWR and rich-text assets

| Asset group | What ships | Revision note |
| --- | --- | --- |
| Font | `assets/font/DefaultFont.ttf` (13.2 MB) + title/textbox/content preview PNGs; user font import supports TTF/OTF | Do not bundle; use a system/licensed font |
| OCR | 61 files under `assets/mlkit-google-ocr-models/`: legacy line recognizers for Kore, Latn, Hani, Jpan, Deva, Beng; Taser text detection/script ID; layout/line-clustering models | We already bundle Korean/English ML Kit OCR; no new models needed |
| Digital Ink | `assets/mlkit/mlkit_model_v2_en_us.zip` (14.8 MB): Latin scribe model, autodraw, English compact FST | Do not add; HWR is out of scope this iteration |
| HWR | `assets/sunia/en_US.zip` (12.2 MB) with `.hdb/.ldb/.lex/.conf`; `assets/hwr/*` build info and math dictionary | Do not reuse Sunia engine/dictionaries |
| Point traces | Five JSON-like stroke traces under `assets/points/` (English, Chinese, math, test) | Test fixtures; do not copy |
| Rich text | 15 JS files under `assets/js/` (ProseMirror extensions plus `main.js` 1.57 MB); `assets/css/editor.css`, `normalizer.css` | Original Compose text fields are sufficient; no WebView editor needed now |
| Language config | 61 `assets/conf/*.conf` configuration files (mostly locale-specific); 52 `.hyb` hyphenation dictionaries | Useful only if document-language/hyphenation is added later |

---

## 4. UI/UX observations

### 4.1 Screenshot map

The public Play set is 900×1600 portrait marketing frames (tablet devices inside a
phone-shaped canvas). The 900×506 files are banner crops of the same shots.

| File | What it shows | Use |
| --- | --- | --- |
| `02.img` | Notebook editor, dotted paper, handwriting; floating pen palette; right pen-box rail; top toolbar | Editor + pen workflow |
| `03.img` | PDF page with margin handwriting; same toolbar; horizontal page swipe cues | PDF annotation |
| `04.img` | “All notes” home: left rail, ALL/NOTE/PDF tabs, 3-column cover grid, create menu | Library/home |
| `05.img` | Editor toolbar close-up: tool row, color swatches, size controls | Toolbar hierarchy |
| `06.img` | PDF text selection: highlight colors, underline/strikethrough, Copy, Translate, Add to excerpt; two document tabs | PDF study flow |
| `07.img` | Monthly planner template; lasso selection; two tabs; Jan–Dec right-edge index | Templates + page index |
| `08–13.img` | Banner crops of 02–07; `09` adds page-margin dialog, `12` shows outline sidebar | Supporting detail |
| `37–39.img` | Unrelated “Notepad” app screenshots (brown list/detail) | Exclude; not StarNote |

### 4.2 Library / navigation

- **Tablet-first left rail** is persistent: avatar at top, Search, All notes,
  Trash, Folder (+), Tag (+), then a tag tree with per-tag counts.
- On compact widths the resources point to a drawer (`Open navigation drawer`,
  `fragment_note_list_drawer_function`) rather than a bottom bar.
- Main content header: title, grid/list toggle, sort, batch-select. Tabs are
  **ALL / NOTE / PDF**; there is no visible “Books” tab.
- The grid is cover-forward: large thumbnail, title, last-edited date, and small
  tag chips. A dashed “create” tile is the first cell.
- Create is a contextual menu anchored to the create tile: new note, unbound
  note, import files, import image, take photo, quick note. Import is not buried
  in a separate wizard.
- Rail counts and tag chips make organization visible without opening a manager.

### 4.3 Notebook editor

- Two-tier top chrome: app bar (back, camera/scan, page overview, microphone,
  layers, fullscreen, split, overflow) and a tool row (undo/redo, pen, highlighter,
  eraser, lasso, shape, text, image/table, color swatches, size).
- The pen tool opens a **floating parameter card**: pen type, thickness in mm,
  stroke stabilization percentage, color, “Add to pen box”.
- A **docked pen-box rail** on the right keeps saved pen presets and sizes one tap
  away. This is better than a modal palette for frequent pen switching.
- Document tabs sit above the canvas; a chevron opens tab management and
  split-screen options.
- The right page sidebar has Overview / Bookmark / Excerpt / Outline tabs, page
  thumbnails, and a page recycle bin. Screenshot 12 shows the Outline tab with
  page numbers.
- Template-specific indexes can appear on the right edge (months in the monthly
  planner, screenshot 07/13).
- Writing modes explicitly separate “pen writes / finger scrolls” from
  “finger or stylus writes”; two fingers pan. Palm rejection and stylus-button
  shortcuts are settings, not hidden gestures.
- Page-scale lock (`page_scale_locked`, `editor_view_lock_layout`) holds the page
  scale; the app warns when an adaptive-screen action needs it unlocked.
- The editor is dense but scannable on a tablet. On a phone it would be crowded;
  the revision should collapse the second row into a single adaptive toolbar with
  an overflow, not copy the density.

### 4.4 PDF reading and annotation

- PDF uses the **same page canvas and toolbar** as notes; handwriting sits over
  the page and in margins. There is no separate “PDF editor” mode.
- Text selection shows a compact floating toolbar: highlight color chips,
  underline/strikethrough toggles, Copy, Translate, Add to excerpt. This is the
  strongest study interaction in the set.
- Right sidebar combines document outline, bookmarks, excerpts and page
  thumbnails; outline rows show page numbers.
- Multi-document tabs make it easy to keep a textbook, lecture slides and a note
  open together; split-screen is explicitly supported.
- Page margin adjustment (screenshot 09) has size, color, template style, apply
  to entire note, apply to notebook cover, and page-turn direction.
- Export distinguishes editable vs non-editable PDF and can include template,
  tape and recordings. Free export carries a watermark; Pro removes it.

### 4.5 Organization UX

- Folder and tag managers are explicit screens: create, rename, move, search,
  drag-to-reorder, swipe-to-delete, parent selection, conflict errors.
- Tags are hierarchical (`Notes/Class Notes`), with subtags and per-tag counts.
- Notes support pin, favorite, lock, move, copy, trash, restore, delete forever.
- Batch management is a first-class mode, not a hidden long-press.
- Search has scope filters (all folders/all tags/current folders/current tags) and
  sections for title, text and handwriting. This is a “find anything” surface,
  not just a filename filter.

### 4.6 Audio / AI / cloud / backup placement

- Audio: microphone in the editor app bar, audio shortcut, insert recording,
  audio list, audio-ink sync, export audio, AI summary tabs.
- AI: Settings → StarNote AI, AI points balance, enable/disable; audio summary,
  transcript, translation, handwriting beautification. Network/account is
  implicit in the points model.
- Cloud/backup: Settings → Local backup / Cloud backup / Cloud sync; provider
  binding and last-sync status. Not on the home rail.
- Account/Pro: avatar/user center, membership, AI points, billing. This is the
  monetization surface, not a study surface.

### 4.7 Visual language

- Light neutral surfaces, white cards, soft blue selection/accent, rounded
  corners, minimal shadows; cover art supplies most of the color.
- Thin line icons, small labels, generous page canvas; the chrome recedes while
  writing.
- Empty states are friendly and action-oriented (“create one now”, “go to
  Resource Center”), but the exact copy should not be reused.
- Dark mode and reading/night mode exist; our app already has light/dark and night
  mode, so this is a visual-polish gap, not a new subsystem.

### 4.8 Patterns worth recreating — and anti-patterns to avoid

Recreate the **workflow**, not the pixels:

1. Cover-forward library with a persistent organizational rail.
2. One page canvas for notes and PDFs.
3. Contextual tool palette plus a persistent pen box.
4. Page sidebar with thumbnails and document outline.
5. Text-selection toolbar with highlight + excerpt, not just copy.
6. Explicit stylus/finger modes and palm rejection.
7. Template picker with apply-scope choices.

Avoid:

- Account/login, AI points, Pro quotas, watermarks, ads/AD_ID, billing.
- Server-side PPT/DOC conversion; it conflicts with the offline promise.
- Two-row dense toolbar on phones; use adaptive collapse.
- Copying cover art, template SVGs, fonts, tape icons, screenshots or logo.
- Treating “more features” as success; the differentiator is offline trust and
  PDF depth.

---

## 5. Gap analysis and prioritized revision

### 5.1 Current baseline (My PDF Writer 0.2.0)

| Area | Current state |
| --- | --- |
| Platform | Kotlin + Compose, minSdk 26, targetSdk 36, one activity, no Navigation/Room/Hilt |
| PDF | Open via SAF, render, annotate (pen/highlighter/text/shapes/underline/strikethrough/eraser), undo/redo, pages, search, notes |
| PDF tools | Merge/split/image→PDF/PDF→PNG/text extraction/optimize, page organizer, rotate/delete/duplicate/move/blank/extract |
| OCR | Offline Korean/English ML Kit OCR, text extraction, searchable PDF with invisible text layer |
| Security/forms | Document info, open password set/remove, watermark, page numbers, AcroForm fill, stamps/signatures/redaction |
| Library | Recent documents, open PDF, blank PDF, tools entry; no notebook grid |
| Notebook model | `Notebook`, `NotePage`, `NotebookDraft`, `PaperTemplate` (11 generic), `CoverStyle` (4), `NotebookStore` JSON — **model only, no UI yet** |
| Network | No `INTERNET` permission; no ads/analytics/account/cloud/AI SDK |

The gap is therefore mostly **library + notebook UX + organization + study flow**,
not PDF engine capability. Do not rebuild the PDF engine to imitate StarNote.

### 5.2 Gap summary

| Area | StarNote | My PDF Writer today | Revision decision |
| --- | --- | --- | --- |
| Home | Nav rail, folders/tags, cover grid, ALL/NOTE/PDF | Recent PDF list + tools | Build notebook home around existing library |
| Notebook editor | Templates, layers, pen box, tape, audio, lasso | PDF-only canvas; annotation primitives exist | Reuse annotations on a template page |
| PDF | Annotation, text selection, outline, tabs | Annotation, organize, OCR, forms, security, watermark | Keep ours; add PDF tab and text selection/tabs |
| Import | PDF/PPT/DOC/EPUB/MOBI/AZW3/CAJ/images/audio | PDF + images→PDF | Add image insert; defer e-book/Office |
| Organization | Nested folders, hierarchical tags, pin/trash/batch | No folder/tag UI | Build on `Notebook.folder/tags/favorite/trashed` |
| Search | Title/text/handwriting across notes | PDF text + page notes | Add notebook index; HWR later |
| Audio/AI | Recording, sync, transcript, summary, translation | None | Defer; no cloud/AI SDK this iteration |
| Backup/cloud | Local + cloud sync | None | Local backup next; cloud later |
| Study | Templates, timer, tape, excerpts, AI summary | Page notes + templates model | Templates + timer/excerpts later |
| Trust | Network/account/ads/billing | Offline, no INTERNET, no account | Keep as the core differentiator |

### 5.3 Ten must-haves

These are the minimum to make the revision feel like the StarNote *workflow* while
staying original and offline.

| # | Must-have | StarNote evidence | Existing leverage | New work (original) |
| --- | --- | --- | --- | --- |
| 1 | Tablet home shell with adaptive nav rail (All notes/Trash/Folders/Tags/Search) plus ALL/NOTE/PDF tabs | Screenshot 04; `fragment_note_list_*`, `nav_app_bar_open_drawer` | `LibraryScreen`, `RecentDoc`, `NotebookStore` | Responsive rail/drawer, tab state, search entry |
| 2 | Notebook grid with covers, title/date/tag chips, grid/list, sort, multi-select | Screenshot 04; `item_note_list_grid_*`, `note_list_op_*` | `Notebook.coverColor/coverStyle/updatedAt/tags/favorite/trashed` | Compose cover card, grid/list toggle, sort, selection mode |
| 3 | Create flow: new note, unbound page, import PDF/image, quick note | Screenshot 04/10; `popup_create_note`, `import_*` | `NotebookDraft`, SAF PDF import, image→PDF bitmap pipeline | Create bottom sheet, draft/template/cover step, import routing |
| 4 | Notebook editor on the existing annotation engine | Screenshot 02/05; `NoteScribbleActivity`, `fragment_note_scribble` | `Annotation`, `AnnotationPainter`, normalized coordinates, undo/redo | Template page renderer, notebook page state, page gestures |
| 5 | Shared toolbar: pen/highlighter/eraser/text/shape/image, color/width, undo/redo | Screenshot 02/05; `shortcut_bar_*`, pen popups | `PdfCanvas` tools, `ViewerScreen` toolbar, palette | Notebook toolbar binding, image object, adaptive overflow |
| 6 | Page sidebar/overview: thumbnails, add/duplicate/delete/reorder, jump | `fragment_note_sidebar`, `item_note_page_thumbnail`, `popup_note_overview_options` | `PageOps`, `OrganizeScreen`, `PageSpec` | Notebook thumbnails, page list, jump, page actions |
| 7 | Stylus-vs-finger modes, palm rejection, zoom/scale lock | `shortcut_bar_writing_mode_*`, `palm_rejection`, `page_scale_locked` | `PdfCanvas` gesture handling | Input-mode toggle, palm heuristic, scale lock UI |
| 8 | Template picker with generic categories and apply scope | 45 templates, 7 categories; screenshot 09 | `PaperTemplate` (11), `CoverStyle` (4) | Expand to ~24 original patterns, category tabs, apply current/new/all/cover |
| 9 | Folders + hierarchical tags + pin/favorite/trash + batch actions | Screenshots 04; `manager_folder_*`, `manager_tag_*`, `popup_note_list_*` | `Notebook.folder/tags/favorite/trashed` | Folder/tag tree, managers, move/rename/delete, batch bar |
| 10 | PDF tab and annotation parity, with all existing tools still reachable | Screenshots 03/06; `all_note` + PDF tab | `ViewerScreen`, `PdfViewModel`, `ToolsScreen`, `OcrEngine`, `PdfTools` | Home PDF section, PDF open/import, preserve tools entry |

### 5.4 Five next

| # | Next | Why after the must-haves | Existing leverage |
| --- | --- | --- | --- |
| 1 | Multi-document tabs + split view | Needs stable home/editor state first; high study value | `ViewerScreen` state, `PdfViewModel`; new tab host |
| 2 | Page layout controls: margin, page direction, single/continuous/two-page, read-only/presentation | Template/page model must settle first | `PageSpec`, rotation/page ops, night mode |
| 3 | Layers: add/hide/lock/opacity/merge/restore | Requires `Annotation` layer id and z-order; UI-heavy | Annotation list, `AnnotationPainter` |
| 4 | Lasso + pen box + ruler + tape | Advanced physical-feel tools; high effort but strong differentiation | Eraser/highlighter primitives, `PdfCanvas` |
| 5 | Local backup/restore + note lock (biometric/password) | Data safety before study extras; no network needed | JSON stores, `NotebookStore`, Android biometric APIs |

### 5.5 Five later

| # | Later | Reason to defer | Constraint |
| --- | --- | --- | --- |
| 1 | Audio recording + audio-ink sync + playback on page | Large media subsystem; needs lifecycle/foreground service | Local only; new permissions if recording is enabled |
| 2 | On-device transcription/summary, or optional AI | Model size/performance; AI points would add network | No cloud/AI SDK this iteration; keep offline baseline |
| 3 | Cloud backup/sync | Account, provider SDKs, conflict resolution | Explicitly out of scope now; local backup first |
| 4 | EPUB/MOBI/AZW3 and Office import | E-book rendering is a new engine; Office is server-side in StarNote | Do not add server conversion; PDF/image only for now |
| 5 | Study suite: timer/stopwatch, excerpt collection, page/audio links, Ebbinghaus review | Needs notebook pages, tags and search first | Original UI; no flashcard claims |

### 5.6 Guardrails for the revision

- Keep `INTERNET` absent. No ads, analytics, account, billing, cloud or AI SDK.
- Keep the existing PDF tool depth: annotation, organize, OCR, searchable PDF,
  forms, password, watermark, redaction, print/share.
- Keep WYSIWYG export: screen and PDF use the same painter. Do not adopt
  StarNote’s watermark/Pro export split.
- Keep Korean as the primary UI language; add English only when the screen is
  stable. Avoid shared `strings.xml` churn while worktrees are parallel.
- Prefer one adaptive Compose screen with state hoisting over a navigation graph;
  the app is still small.
- Measure success by “open a PDF or notebook and write in under 5 seconds”, not by
  feature count.

---

## 6. IP boundary

This revision is a **clean-room workflow study**, not a port.

1. **No StarNote code.** No decompilation of `baiduprotect*.jar`, no reuse of
   ONYX/Sunia/Baidu code, no copied Kotlin/Java/JS.
2. **No StarNote assets.** Do not copy template SVGs, cover WebP/PNG, tape icons,
   Lottie files, default font, ML Kit/OCR models, HWR dictionaries, point traces,
   sounds, manual `.note` files, screenshots, logo or app icon.
3. **No branding.** Do not use the StarNote name, package, domain, Play listing
   copy, iconography or color identity. Use My PDF Writer branding and original
   Material 3 styling.
4. **No proprietary text.** Short feature labels are fine; do not copy paragraphs,
   marketing copy, empty-state text or error strings.
5. **No network/cloud/AI.** This iteration adds no `INTERNET` permission, no
   Firebase/analytics, no cloud provider SDK, no AI API, no billing.
6. **Original templates.** Draw generic paper patterns with Compose Canvas
   (lines, grids, dots, Cornell bands, checklist boxes, planner rules). Do not
   trace or vectorize StarNote SVGs.
7. **Original covers.** Use solid colors and simple geometric patterns from the
   existing `CoverStyle`; do not reproduce cover illustrations or photos.
8. **Original fonts.** Use the system font or a properly licensed font. Do not
   bundle `DefaultFont.ttf`.
9. **Own data format.** Keep the JSON notebook schema; do not promise StarNote
   file compatibility or import their backup format.
10. **Evidence hygiene.** Keep the APK and Play screenshots out of git. Reference
    them by path in research notes only; do not redistribute them.

Layout ideas (rail, grid, toolbar, page sidebar) are functional patterns and can
be reimplemented. Pixel-copying, asset copying and brand imitation are not
allowed.

---

## 7. Unknowns and limitations

- **Static only.** Nothing was executed. Gestures, latency, palm rejection,
  zoom-lock behavior and real rendering were not observed.
- **Packed code.** `classes.dex` is a 35 KB stub; the real implementation is in
  Baidu-protected jars loaded at runtime. Class-level logic, data formats and
  exact interaction rules cannot be verified without unpacking, which was not
  done.
- **Resource inference.** Feature presence comes from string/layout/manifest
  names. Some strings may be unused, legacy, library-owned, or region-gated.
- **Marketing screenshots.** The 900×1600 images are promotional tablet mockups;
  spacing, icon placement and behavior may differ on a real device. `08–13` are
  crops, not independent screens. `37–39` are an unrelated Notepad app and were
  excluded.
- **PiP claim.** The Play listing mentions picture-in-picture; no explicit
  `supportsPictureInPicture` flag was found in the manifest. A screen-capture
  service exists, but PiP behavior is unverified.
- **Template mapping.** The 7 paper categories and 6 cover categories come from
  `res/raw/category_*.json`; the per-template category assignment is partly
  inferred from names because the mapping lives in packed code.
- **Negative claims.** “No evidence of forms/redaction/PDF password/searchable
  OCR export” means no matching strings/layouts were found, not proof that the
  feature is absent in every build/region.
- **Regional variance.** The APK carries Chinese account/payment/cloud services
  and vendor SDKs. Global behavior, provider availability and AI features may
  differ by account/region.
- **No performance data.** APK size, startup time, memory and stylus latency were
  not measured. StarNote is ~141 MB; My PDF Writer’s size advantage is an
  assumption from existing builds, not a measured comparison here.
- **No legal review.** This document is engineering research, not a legal opinion.
  Treat StarNote as proprietary; verify trademark, trade-dress and asset licensing
  before any public comparison.

---

## Appendix A — representative English/Korean labels

Short labels only; these illustrate terminology, not copied UI copy.

| Area | English | Korean |
| --- | --- | --- |
| Library | All notes / Note / PDF / Trash | 모든 메모 / 메모 / PDF / 휴지통 |
| Library | Folder / Tag / Search | 폴더 / 태그 / 검색 |
| Editor | Pen writing / Finger-stylus writing | 펜 쓰기 / 손가락·스타일러스 쓰기 |
| Editor | Palm rejection / Stroke stabilization | 손바닥 오작동 방지 / 스트로크 안정화 |
| Editor | Layer / Lasso / Ruler / Tape | 레이어 / 올가미 / 자 / 테이프 |
| Editor | Page scale locked | 페이지 비율 잠금 |
| PDF | Highlight / Underline / Copy / Translate | 강조 / 밑줄 / 복사 / 번역 |
| PDF | Add to excerpt / Outline / Bookmark | 발췌에 추가 / 아웃라인 / 책갈피 |
| PDF | Two-page browsing / Page direction | 두 페이지 보기 / 페이지 넘김 방향 |
| Import | Import files / Import PDF / Import image | 파일 가져오기 / PDF 가져오기 / 이미지 가져오기 |
| Import | Take photo / Scan document | 사진 촬영 / 문서 스캔 |
| Organization | Manage folder / Manage tags | 폴더 관리 / 태그 관리 |
| Organization | Create subfolder / Add new tag | 하위 폴더 생성 / 새 태그 추가 |
| Organization | Pin note / Move to trash / Batch management | 노트 고정 / 휴지통으로 이동 / 일괄 관리 |
| Audio/AI | Recording / Audio-Ink Sync | 녹음 / 오디오 잉크 동기화 |
| Audio/AI | Smart Voice / Real-time transcription / Smart summary | 스마트 음성 / 실시간 텍스트 변환 / 스마트 요약 |
| Backup | Local backup / Cloud backup / Cloud sync | 로컬 백업 / 클라우드 백업 / 클라우드 동기화 |
| Study | Study plan / Ebbinghaus / Cornell | 학습 계획 / 에빙하우스 / 코넬 |
| Study | Excerpt / Timer | 발췌 / 타이머 |

## Appendix B — asset counts at a glance

| Asset group | Count / size |
| --- | --- |
| Named paper templates | 45 base names |
| Template SVG files | 87 (portrait + landscape) |
| Template categories | 7 |
| Cover designs | 61 unique |
| Cover files | 122 (portrait + landscape) |
| Cover categories | 6 |
| ML Kit text-recognition model files | 61 |
| Digital Ink model zip | 14.8 MB (`assets/mlkit/mlkit_model_v2_en_us.zip`) |
| Sunia HWR English pack | 12.2 MB (`assets/sunia/en_US.zip`) |
| Default font | 13.2 MB TTF + 3 preview PNGs |
| Point-trace fixtures | 5 text files under `assets/points/` |
| JS rich-text modules | 15 JS files (ProseMirror extensions + `main.js` 1.57 MB) |
| CSS | 2 files (`editor.css`, `normalizer.css`) |
| Manual/sample note | `res/raw/user_manual_overseas.note` 17.4 MB |
| Native `.so` | none |
