# All PDF Reader 2.1.9 — static feature and UX analysis

> Scope: static inspection of a user-supplied APK and its public Play listing.
> No app execution, no JADX/decompilation, no runtime instrumentation, no network
> calls. This is input for an original, offline reading experience in My PDF
> Writer — not a specification to clone.
>
> Observed: 2026-11-16. The APK, screenshots and resource dumps stay outside the
> repository. Only short functional labels are quoted here.

---

## 1. Artifact identity and evidence

### 1.1 Identity

| Field | Value |
| --- | --- |
| File | `/sdcard/Download/apk_for_pdf/PDF 리더기_2.1.9.apk` |
| Installed label | `All PDF Reader` |
| Public Play title | `PDF Reader - Easy PDF` (same package at observation time) |
| Package | `pdf.pdfreader.viewer.editor.free` |
| Version | `2.1.9` / versionCode `143` |
| SDK | minSdk `24`, targetSdk `35`, compileSdk `36` |
| APK size | `89,183,699` bytes (85.0 MiB) |
| Uncompressed | `105,586,661` bytes (100.7 MiB), `9,507` zip entries |
| SHA-256 | `4ee5b165da074d5220c2fb5942521a29f787171b8266c9b8fc277da4efc54337` |
| Signer | `CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US`; cert SHA-256 `12f5885eb4c3636b23f5d93e875b228e43e791bce628714f796eb5978bc4dae1` |
| Signature schemes | APK Signature Scheme v2 and v3; Play source stamp present; no v1 |
| Launchable activity | `pdf.pdfreader.viewer.editor.free.ReaderLauncherBridgeActivity` |
| Application class | `pdf.pdfreader.viewer.editor.free.ReaderPdfApplication` |
| Locales | 98 locale configs, including `ko` |
| Densities | `120`, `160`, `240`, `320`, `480`, `640`, `65534` |
| Screens | small, normal, large, xlarge; camera is optional; many activities force portrait |

The installed label and the public listing title differ. The signer DN is a
generic Android/Google identity, not a usable publisher identity; no publisher
claim is made. The APK is a large, multi-feature application, not a small reader.

### 1.2 Permissions and trust surface

`aapt2 dump permissions` reports **25 `uses-permission` entries** plus one custom
permission:

| Group | Permissions present | Relevance |
| --- | --- | --- |
| Storage / all files | `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`, `MANAGE_EXTERNAL_STORAGE` | Broad storage posture. `MANAGE_EXTERNAL_STORAGE` is a Play-policy-sensitive permission and a visible trust cost. My PDF Writer's SAF-only model avoids it. |
| Network / ads / attribution | `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS`, `ACCESS_ADSERVICES_CUSTOM_AUDIENCE`, Adjust preinstall permission, install-referrer service, `READ_GSERVICES`, `RECEIVE` (C2DM) | Confirms cloud/AI/ads/account surfaces and install attribution. |
| Camera | `CAMERA` | Scanner, ID-card scan, signature capture. |
| Notifications / background | `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK` | TTS notification, background work, media/data jobs. |
| Billing / OEM | `BILLING`, vivo badge permission | Subscription and home-screen badge paths. |
| Device feedback | `VIBRATE` | Haptics. |
| App-local | `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX receiver hardening. |

Not present: `RECORD_AUDIO`, location, contacts, `READ_MEDIA_AUDIO`,
`READ_MEDIA_VIDEO`. Read-aloud therefore does not need microphone access; a
system TTS engine can be used without adding a microphone permission.

Manifest flags of note: `allowBackup=false`, `largeHeap=true`,
`requestLegacyExternalStorage=true`, `extractNativeLibs=false`, a network security
config, and a `CoreComponentFactory`.

### 1.3 Components and integration surface

| Type | Count | What is there |
| --- | --- | --- |
| Activities | **241** declarations, including **85** `...WrapLetterBox` variants | Home, preview/reader, Office reader, page manager, search, folder/file picker, scan, OCR, convert, compress, merge/split, sign, AI, TTS onboarding, billing, FAQ, settings, recycle bin |
| Activity aliases | **10** | Exported per-format handlers, labelled e.g. `View PDF`; several declare **31 MIME types** and `VIEW` / `SEND` / `SEND_MULTIPLE` filters |
| Services | **19** | `TTSPlayerService`, FCM/messaging, WorkManager, ad services, Firebase sessions |
| Receivers | **26** | `TtsNotificationReceiver`, boot/time receivers, eight home-screen widget receivers, Adjust/Firebase receivers |
| Providers | **9** | `FileProvider`, Firebase, AdMob, Meta Audience Network, Adjust, Bigo Ads, AndroidX Startup, Picasso, Facebook |

Exported app components are limited to five base activities — launcher bridge,
deep-link receiver, deep-link activity, shortcut proxy, widget landing — plus the
ten exported aliases. The aliases are the file-handler surface; the explicit MIME
families are PDF, Word (legacy and OOXML, macro/template variants), Excel
(legacy/OOXML/CSV, template/addin variants), PowerPoint (legacy/OOXML,
slideshow/template variants) and images. **No `text/plain` filter was found.**

Deep links include a custom `reader://industries.deepthought/...` scheme and an
HTTPS host under `pdfreader.deepthought.industries`. These are integration
details only; they are not reused.

The 241 activity count is not 241 distinct screens. Letterbox wrappers, billing
A/B variants, ad landings and library components inflate it substantially.

### 1.4 Packaging, SDKs and assets

| Item | Observation |
| --- | --- |
| Code | 8 DEX files, `62,425,372` bytes (59.5 MiB), including `assets/audience_network/classes.dex` |
| Native code | No `lib/` directory and no `.so` files |
| Resources | 5,388 files, `21,693,915` bytes (20.7 MiB); `resources.arsc` is `6,131,588` bytes (5.85 MiB) |
| Strings | 2,754 string resources; the global string pool has 53,770 entries |
| Assets | 81 files, `12,801,688` bytes (12.2 MiB); demo PDF/DOCX/PPTX/XLSX, guide images, model-like blobs, ad-network dex |
| Fonts / images | 16 `.ttf` under `res/font`; 1,212 PNG and 521 WebP files |
| UI stack indicators | View-based layouts (AppCompat, Material, ConstraintLayout), Lottie, Room, WorkManager, ExoPlayer, Picasso |
| Document libraries | PDFBox-Android class strings and Apache POI classes/resources are present; Office handling is consistent with POI, but rendering fidelity was not verified |
| Model-like assets | `assets/sign_mnn_0909` (2.3 MB) and `assets/model_1209` (0.9 MB); runtime use not verified |

The size is consistent with a bundled Office stack, ad/analytics SDKs, guide
assets and demo documents, not with a minimal PDF renderer. Missing native
libraries also mean no assumption should be made about a native PDF engine.

### 1.5 Evidence commands

These are read-only commands against the APK. They do not unpack or execute code.

```bash
APK="/sdcard/Download/apk_for_pdf/PDF 리더기_2.1.9.apk"

sha256sum "$APK"
stat -c '%s bytes' "$APK"
aapt2 dump badging "$APK" | sed -n '1,25p'
aapt2 dump permissions "$APK"
apksigner verify --print-certs "$APK"        # if apksigner is installed
apksigner verify --verbose "$APK"

aapt2 dump xmltree --file AndroidManifest.xml "$APK" \
  | grep -E 'E: (activity|activity-alias|service|receiver|provider)|:name\(|:exported\(|:mimeType\(|:scheme\('

aapt2 dump resources "$APK" \
  | grep -E 'string/(reader_|reflow|read_aloud|night|bookmark|page_manager|tts|select_text|highlight|underline|strikethrough)'

unzip -l "$APK" | awk '
  /^ *[0-9]+ +[0-9-]+/ {
    size=$1; path=$4;
    if (path ~ /^res\//) {r+=size; rn++}
    else if (path ~ /^assets\//) {a+=size; an++}
    else if (path ~ /\.dex$/) {d+=size; dn++}
  }
  END {printf "res %d files %d bytes; assets %d files %d bytes; dex %d files %d bytes\n", rn,r,an,a,dn,d}'
```

Screenshot evidence: public Play page for `pdf.pdfreader.viewer.editor.free` and
the 900×1440 UI mockups in
`/data/data/com.termux/files/usr/tmp/opencode/pdfw/pdfreader_shots/` (10–17).
The 900×615 images (02–09) are banner crops of the same marketing set. Decorative
frames and graphics whose labels/metrics did not match this package were ignored.
No screenshot is copied into the repository.

---

## 2. Feature inventory

### 2.1 At-a-glance confidence

| Area | Observed surface | Confidence |
| --- | --- | --- |
| Library / all-document home | All files, Recent, Bookmark, Tools; All/PDF/Word/Excel/PPT/TXT tabs; folders, search, sort, recycle bin | High for PDF/Word/Excel/PPT; TXT advertised but not manifest-confirmed |
| Reader layouts | Vertical/horizontal × continuous/discontinuous | High: four exact labels and four distinct icons |
| Reader settings | Direction, five paper tones, brightness + auto, continuous, night mode, keep screen on, reflow, landscape switch | High: inspected layout IDs and labels |
| Reflow | Font picker, font size +/−, line spacing presets, previous/next page, fallback | High |
| Text selection | Copy, Cut, Paste, Delete, More; horizontal overflow; selectable text | High: layout and strings |
| Quick markup | Highlight, underline, strikethrough, text/area highlight, draw, eraser, annotate, fill & sign | High |
| Read aloud / TTS | Player, speed, sleep timer, voice picker, notification controls, system and AI voices | High |
| Night / dark reading | Night mode, color inversion, brightness, paper tones | High |
| Progress and bookmarks | Page HUD, go-to-page, thumbnail rail, add/list bookmarks, resume position | High |
| Page manager | Thumbnail grid, multi-select, insert/rotate/extract/delete/page setup, undo/redo/save | High |
| Search and outline | Full-text search, match counts, find next/previous, table of contents | High |
| Comments | `Add Comment` / `Add Comments` labels only; no reader-side comment list layout found | Unconfirmed |
| Scan / convert / compress / sign | Separate activities and tool labels | High presence; behavior not verified |
| AI / cloud | Assistant, chat, summary, translate, rewrite, outline, mind map, AI read-aloud, AI filters | High presence; out of scope |
| Ads / billing | Subscription activities, billing permission, multiple ad/attribution SDKs | High presence; out of scope |

### 2.2 Library and all-document home

The home surface is an “office inbox”, not a bare file picker:

- Bottom navigation: **All files**, **Recent**, **Bookmark**, **Tools**.
- File-type filters: **All**, **PDF**, **Word**, **Excel**, **PPT**, **TXT**
  (visible in the home mockups). PDF/Word/Excel/PPT are confirmed by MIME filters,
  `OfficeReaderActivity`, and `reader_title_*` strings.
- Recent cards show file name, date/time, size, source app, a bookmark marker and
  an overflow menu (mockup 17). Source-app metadata is useful provenance but a
  privacy consideration; My PDF Writer should not copy that field by default.
- Folders, create/rename/move/copy/delete, recycle bin, search, sort by
  name/date/size and grid/list modes are present in resource names and layouts.
- File actions include share, print, details, rename, favourite/mark, delete,
  copy/move, add to home screen and set as default reader.

The manifest's explicit MIME table does **not** include `text/plain`. TXT is
shown in the marketing home mockups, but static inspection cannot confirm whether
it is handled by extension, by a generic document path, or only by a related
build. Treat TXT as “advertised, unverified”.

### 2.3 Reader chrome and page flow

The main reader layout has a slim top bar with:

- Back, view/settings (with an active page-colour badge), share, search,
  PDF-to-Word, read aloud, AI assistant, reflow, and overflow.
- A page HUD with current page, slash, total pages, filename and a thin progress
  line; a go-to-page/thumbnail surface is available from the HUD.
- A bottom edit area with **Edit**, **Annotate** and **Fill & Sign** tabs, plus the
  active markup tools.

Four page-flow combinations are explicitly named:

| Mode | Exact label |
| --- | --- |
| Vertical, continuous | `Vertical continuous` |
| Vertical, discontinuous | `Vertical discontinuous` |
| Horizontal, continuous | `Horizontal continuous` |
| Horizontal, discontinuous | `Horizontal discontinuous` |

The settings sheet exposes **Scroll direction** (`Vertical` / `Horizontal`), a
**Continuous** switch, and **View mode**. This is a cleaner model than four
independent buttons: direction plus continuity, with four visual presets. Four
distinct icons exist for the four combinations. Other strings confirm
`Swipe to switch pages`, pinch/double-tap font sizing, and fit-to-screen zoom.

### 2.4 Reading settings

The inspected settings dialog contains, in order:

1. **Scroll direction** — Vertical / Horizontal.
2. **Background color** — five swatches: white, green, yellow, blue, dark.
3. **Brightness** — slider plus an **Auto** option.
4. **Continuous** — switch.
5. **Night mode** — switch.
6. **Keep screen on** — switch.
7. **Reflow mode** — entry point.
8. **Switch to landscape** — orientation control.
9. **Color inversion** — separate from night mode.

There is also a page-level **Page Color** and **Page size** panel in the page
manager, applied to selected pages. That is different from the reader’s display
background: one changes the document pages, the other changes the reading canvas.

Night mode and colour inversion are separate controls. A dark app theme alone is
not the same as inverting page rendering, and the target exposes both.

### 2.5 Reflow mode

Reflow is a first-class reading mode, not a zoom trick:

- Entry from the reader toolbar; `Entering reflow mode` / `Leaving reflow mode`
  feedback; loading state; `Unable to reflow this page` fallback.
- A font picker sheet lists font families with state icons (selected, loading,
  error) and titles such as `Font`, `Current font`, `Original font`.
- Bottom reflow strip: font size decrease/increase, previous page, next page.
- Font size can also be changed by pinch or double-tap; the string
  `Pinch or double-tap to adjust the font size` confirms the gesture.
- Line-spacing presets are 1.0, 1.2, 1.5 and 2.0; a small value indicator pops up
  while changing spacing.
- A reflow page HUD shows current/total and a go-to-page control.
- Word reflow has its own edit view and zoom/scale control (200% example in the
  layout), consistent with the Office/Word reading surface.
- `Continue using reflow mode?` and `Would you like to restart the reading?`
  indicate session continuation and resume behaviour.

`Margins` and `No margins` labels exist in resources, but no visible margin
control was confirmed in the inspected reflow layouts. Do not treat margins as a
confirmed reflow control without runtime verification.

### 2.6 Text selection and quick markup

Text selection is supported on the extracted text layer:

- `Select text`, selectable page text, and a floating selection menu with
  **Copy**, **Cut**, **Paste**, **Delete**, **More**.
- The menu is a horizontally scrollable row with left/right overflow affordances,
  so it can hold more actions than fit on a narrow phone.
- `Copy all` and page-level text copy exist; `Copied` feedback confirms clipboard
  behaviour.
- The selection popover is positioned near the selection, with draggable handles
  visible in the mockups.

Quick markup is a separate, always-available tool surface:

- **Highlight**, **Underline**, **Strikethrough**, **Text Highlight**,
  **Area Highlight**, **Draw annotation**, **Annotate**, **Fill & Sign**.
- Pen settings, colour palette, eraser, undo/redo.
- Bottom tabs **Edit** / **Annotate** / **Fill & Sign** separate text editing,
  freehand annotation and form/signature work.
- Signature tools include draw, scan, create, colour row, pen/eraser, save and
  placement. The `sign_mnn_0909` asset suggests model-assisted signature
  extraction, but that is not verified.

The mockups also show a text-edit bubble with copy, font larger/smaller and delete.
That is a text-object editor, not the same as the read-only selection menu.

### 2.7 Read aloud / TTS

Read-aloud has both a local-looking and a paid/cloud-looking path:

- Top-bar **Read Aloud** and **Read aloud from here**.
- A dedicated `TTSPlayerService` and `TtsNotificationReceiver`.
- Player layout: progress, sleep/alarm control, speed (e.g. `1.2x`), voice avatar
  carousel, previous, play/pause, next, close.
- Notification controls: previous, play, pause, next, replay.
- Voice choices include **System voice** / **System speech voice** and **AI
  voices**; AI read-aloud is described as a supplementary package with 80+ voices
  and subscription/quota language.
- `Reading completed`, `Back to current reading position`, and restart-reading
  prompts indicate reading across pages and resuming after interruption.

For My PDF Writer, the system TTS path is the offline-feasible one. A selected
system engine may itself use network-backed voices; the app should disclose that
and still declare no `INTERNET` permission. The AI voice path is out of scope.

### 2.8 Night mode, colour inversion and brightness

- Night mode is a switch in reader settings.
- Colour inversion is a separate switch with its own labels; the target can warn
  that dark mode is on and ask whether to enable inversion.
- Five reading background colours are available independently of app theme.
- Brightness has a manual slider and an Auto mode.
- Keep-screen-on is a reading setting.
- Eye Protection is named in strings; its exact rendering path was not verified.
- The split dark/light phone image in the marketing set is an illustrative
  mockup, not a runtime screenshot.

The useful pattern is three independent axes: app chrome theme, page background
tone, and page colour inversion. A fourth axis is screen brightness. Conflating
them produces a muddy dark mode.

### 2.9 Progress, bookmarks and page manager

Progress and bookmarks:

- Add/remove bookmark for the current page; a bookmark list; a **Bookmark** home
  tab; bookmark markers in the file list.
- Page HUD with current/total and a progress line.
- `Go to page`, numeric page entry, thumbnail rail, “only N pages in total”
  validation.
- Resume position, `Back to current reading position`, `Reading completed`.
- Reading completion and restart prompts.

Page manager:

- Full-screen thumbnail grid with fast scrolling.
- Select / Select all / multi-select; selection count.
- Undo / redo / Save; unsaved-changes guard.
- Bottom actions: **Insert**, **Rotate**, **Extract**, **Delete**, **Page
  setup**.
- Insert blank pages or pages from another file; reorder pages; extract success
  sheet with View/Share.
- Page setup: page size and page colour, applied to selected pages or all pages.

This is the strongest “document workbench” pattern in the app: reading and page
organisation are separate modes, but a page manager entry is always close.

### 2.10 Search, outline and comments

Search:

- Full-text document search with a search panel.
- Match counts (`N matches`), find backward/find forward, reached-beginning and
  reached-end feedback.
- Grouped results with page/context snippets; search history and clear-history.
- In-file search is separate from library file search.

Outline:

- `Table of Contents` / `Contents` entry with `outline_entry` rows.
- Likely PDF outline/TOC; the exact fallback when a PDF has no outline is not
  verified.

Comments:

- `Add Comment` and `Add Comments` strings exist.
- No reader-side comment list, threaded discussion layout, or comment composer
  was found in the inspected layouts.
- Treat this as an annotation/toolbar label, not a confirmed comment system.

### 2.11 Tools hub: scan, convert, compress, sign

The tools surface is broad and is implemented as separate activities, not as
reader sub-modes:

| Group | Observed tools |
| --- | --- |
| Scan | Camera scan, batch scan, auto-crop, image adjust, filters/enhance, ID-card scan, signature capture, OCR |
| Convert | Image↔PDF, Word↔PDF, PPT→PDF, Excel→PDF, PDF→Word/Excel/PPT, PDF→image/long image, text→PDF |
| Organise | Merge, split, extract pages, crop, rotate, page numbers, watermark |
| Secure / sign | Protect with password, remove password, fill forms, draw/scan signature, certificate sign |
| Optimise | Compress PDF, enhance/filter |

Many of these are subscription-gated or ad-supported. They are product-surface
breadth, not reading UX. My PDF Writer already covers OCR, page organisation,
merge/split, image→PDF, PDF→PNG, text extraction, watermark, page numbers,
password and signature at a smaller scope; do not rebuild the whole suite.

### 2.12 AI, cloud, ads and monetisation (out of scope)

AI/cloud surfaces observed:

- AI Assistant, AI Chat, AI history, AI preview inside the reader.
- AI Summary, AI Translate, AI Refine, Rewrite, Shorten, Expand, Outline, Mind
  Map, Key Points, Grammar Check, Sensitive Info Check, File Comparison, AI PDF
  Text Editor, AI filters.
- AI Read Aloud with “natural, lifelike voices”, quotas, supplementary package and
  subscription language.
- Cloud/account dependency is consistent with `INTERNET`, Firebase, billing and
  account/billing activities. No on-device LLM assets were found. The two
  model-like assets are small and their exact use was not verified; neither is a
  general language model by size or name.

Monetisation/ads observed:

- `BILLING`, subscription activities and multiple A/B subscription variants.
- AdMob, Meta Audience Network, Bigo Ads, Moloco, InMobi, Pangle/ByteDance,
  AppLovin and Unity mediation components.
- Adjust install attribution, Firebase, ad splash/interstitial/recall/preinstall
  activities, banner/native ad layouts.

These explain much of the size and permission surface. They are **out of scope**
for My PDF Writer: no accounts, no cloud AI, no ads, no analytics, no billing, no
new `INTERNET` permission, and no third-party AI voice.

A Google API-key-shaped literal appears in the resource dump. Its value is not
reproduced here. Presence is not proof of a working key or of cloud behaviour.

---

## 3. UI/UX patterns worth reproducing on a phone

These are interaction patterns, not a visual clone. All implementation should be
original Compose UI with My PDF Writer's own typography, colour and icon language.

### 3.1 Reading first, chrome second

**Observed:** the reader keeps a slim top bar and a separate bottom editing area;
the document itself owns most of the screen. Advanced actions live behind one
overflow and one settings sheet.

**Phone translation:** make a single tap on the page toggle the top and bottom
chrome. Leave a small page HUD visible when chrome is hidden. Never put a
permanent seekbar or a second toolbar row over the page. In draw mode, require a
deliberate toolbar tap or a two-finger gesture before hiding tools, so a stray
palm tap does not switch modes.

### 3.2 One “view mode” sheet with four visual choices

**Observed:** direction (vertical/horizontal) and continuity (continuous/
discontinuous) are separate controls, with four named combinations and four
icons.

**Phone translation:** use a bottom sheet with a 2×2 visual grid:
vertical-continuous, vertical-paged, horizontal-continuous, horizontal-paged.
Show a small page-stack thumbnail in each cell. Default to vertical-continuous
for phones, but remember the last choice per document and offer a global default.
Do not make the user decode “discontinuous”; label it “page by page” in the UI
while keeping the internal mode name.

### 3.3 A page HUD, not a permanent scrubber

**Observed:** current/total page text, filename and a thin progress line; a
go-to-page/thumbnail surface is attached to the HUD.

**Phone translation:** put a compact `12 / 84` capsule at the bottom centre, above
the navigation bar. A tap opens a thumbnail rail or a numeric go-to-page field.
Show a transient progress line only while the page is changing. This preserves
immersion and avoids stealing horizontal gestures from text selection or drawing.

### 3.4 Selection menu that stays inside the screen

**Observed:** a floating selection row with Copy, Cut, Paste, Delete, More and
horizontal overflow arrows.

**Phone translation:** anchor the popover above the selection when there is room,
below otherwise; clamp to system bars. Use a horizontally scrollable row with a
fade/arrow affordance rather than cramming five labelled buttons. Offer
**Copy**, **Highlight**, **Underline**, **Share**, **More** in the first row; put
destructive and cloud actions behind More. Keep the selection handles large
enough for a thumb.

### 3.5 Bottom markup tray with a visible active tool

**Observed:** Edit / Annotate / Fill & Sign tabs, a compact markup row, colour
palette and pen/eraser controls.

**Phone translation:** keep the tray at the bottom in the thumb zone. Highlight
the active tool with a filled pill, not only a colour change. Show the current
colour as a circle with a check mark, and long-press for a larger palette. Put
pen size and eraser in the same row; do not open a modal for every change.
Undo/redo should be persistent while annotating.

### 3.6 Reflow control strip: size and page, nothing else

**Observed:** bottom strip with font smaller/larger and previous/next page; font
family and line spacing live in a separate sheet; pinch/double-tap also changes
size.

**Phone translation:** keep four controls on the reflow canvas: A−, A+, previous,
next. Put font family, line spacing, margins and theme in a “Aa” sheet. Show a
short toast when the user pinches, so the gesture is discoverable. If a page
cannot reflow, keep the original page visible and explain why instead of showing
a blank canvas.

### 3.7 Reading settings grouped by intent

**Observed:** one sheet mixes layout, background, brightness and behaviour.

**Phone translation:** group into **Layout** (direction, continuous, orientation),
**Display** (paper tone, brightness, night, colour inversion) and **Behaviour**
(keep screen on, tap-to-turn, volume-key paging). Use switches for binary
settings and a segmented control for direction. Reset brightness on exit; do not
silently change the system brightness.

### 3.8 Dark reading that respects images and highlights

**Observed:** night mode, colour inversion, five page tones, brightness and
keep-screen-on are separate.

**Phone translation:** use a true dark canvas (near-black, not pure black) with a
warm off-white text colour. Do not invert images; dim them slightly instead.
Keep highlight colours recognisable by adjusting alpha, and test yellow, green
and pink on dark. Provide a separate “invert page colours” switch for users who
want a negative view, and never enable it silently when the app theme changes.

### 3.9 Library as an inbox, tools as a separate room

**Observed:** All files / Recent / Bookmark / Tools, type chips, metadata-rich
cards, folders and recycle bin.

**Phone translation:** keep My PDF Writer's notebook home, but add a **Continue
reading** card and a **Bookmarks** filter for PDFs. Show page count, last-read
date and a small progress bar. Keep scan/convert/compress/sign out of the reading
tab; they belong in Tools. Do not show source-app names or other people's app
icons in a privacy-first study app.

### 3.10 Feedback, safety and recoverability

**Observed:** short labels for Copied, Added bookmark, Unable to reflow, Reading
completed; destructive page actions have confirmations; page manager has
undo/redo and a save guard.

**Phone translation:** use snackbars for reversible actions and dialogs only for
destructive ones (delete pages, remove password). Never lose a reading position
on rotation or process death. Disable next/previous at the ends, show a clear
empty state for no bookmarks, and keep a visible “saved” state after annotation.

---

## 4. Prioritised offline features for My PDF Writer

### 4.1 Baseline

My PDF Writer already has a substantial base: SAF file opening, single-page PDF
rendering with zoom/pan, pen/highlighter/text/shape annotations, page manager
(rotate/delete/duplicate/move/blank/extract), search, PDF outline, page notes,
excerpts, Google Translate via external intent, on-device OCR, night mode
(colour-matrix inversion), export/share/print, and no `INTERNET` permission.

Parallel work already covers reading progress/bookmarks, reflow/TTS and a
continuous vertical reader. The list below treats those as must-have outcomes and
focuses on the remaining gaps and integration work; it does not ask for a rewrite
of existing tools.

### 4.2 Must-add (10)

| # | Feature | Target outcome | Offline / build notes | Effort |
| --- | --- | --- | --- | --- |
| 1 | Four page-flow modes | Vertical/horizontal × continuous/page-by-page, persisted per document | Compose `LazyColumn`/`LazyRow` for continuous, `Pager` for page-by-page; reuse the render cache; render at screen width and prefetch only 1–2 pages. Extend the planned continuous vertical work rather than adding a second reader. | M |
| 2 | Immersive chrome + page HUD | Tap to hide/show bars; `current / total` capsule; thin progress; tap to go to page | No new dependency. Keep HUD tappable when bars are hidden. Disable auto-hide while a text selection or active draw stroke is in progress. | S |
| 3 | Reading settings sheet | Brightness, 4–5 paper tones, night/colour inversion, keep screen on, orientation lock, continuous switch | Window brightness and `FLAG_KEEP_SCREEN_ON` need no permission. Persist globally with a per-document override. Reset brightness on exit. | S–M |
| 4 | Resume and progress | Restore page and scroll offset; “Continue reading” card; progress bar | Store page index/offset in the existing per-document JSON or DataStore. Save on pause, not on every scroll frame. This is the user-visible half of the planned progress work. | S |
| 5 | PDF page bookmarks | Add/remove bookmark, optional label, list with page number and snippet, jump, bookmark filter | JSON store keyed by document, like notes/excerpts. Distinguish these from notebook page bookmarks. Add a marker in the HUD and thumbnail rail. | S–M |
| 6 | Thumbnail rail / page overview | Lazy thumbnails with current-page highlight; tap to jump; link to page manager | Reuse low-resolution page renders and an LruCache; never hold full-size bitmaps. Cap thumbnail height and cancel off-screen work. | S–M |
| 7 | Text selection and clipboard | Word/sentence selection, handles, Copy/Share/Select all, page copy; menu stays on screen | Start with reflow text where Compose gives text layout and character bounds. For the PDF canvas, selection needs glyph/word boxes from PDFBox text positions; if unavailable, ship page-level copy first and add canvas selection later. | M–L |
| 8 | Selection quick markup | Highlight, underline, strikethrough from a selection; colour presets; annotation anchored to text | Extend `AnnotationType`/`AnnotationPainter`; map selected line boxes to page coordinates. Test rotation, zoom and export parity. Do not add a separate markup model. | M |
| 9 | System read-aloud | Play/pause, previous/next page, speed, language, sentence highlight, auto page advance, stop on audio focus loss | Android `TextToSpeech`; no `INTERNET`, no microphone. Surface that some installed TTS engines use network voices. Add a notification only if it does not require a foreground-service type the app cannot justify. | M |
| 10 | Reflow mode | Offline text reflow with font family/size, line spacing, margins, themes, previous/next page, select/copy, original-page fallback | Use the existing text extractor and Compose text layout. Start with text-only pages; fall back to the original page for scanned/complex layouts. Do not bundle third-party fonts without a licence. | L |

### 4.3 Next (5)

| # | Feature | Why next | Offline notes |
| --- | --- | --- | --- |
| 1 | Search result panel with in-page highlight | Current search lists hits; reading needs match highlighting and next/previous within the page | Reuse extracted page text and the existing search sheet; add an overlay rectangle and a match index. |
| 2 | Two-page landscape and RTL | Useful on tablets/foldables and for Arabic/Hebrew documents; not essential for phone-first v1 | Pair pages in landscape; add reading direction; keep single-page fallback for small widths. |
| 3 | TTS notification, sleep timer and follow-along | Makes long read-aloud sessions practical; complements the basic player | MediaSession/notification, audio focus, sleep timer, auto-scroll to the spoken sentence. No cloud voices. |
| 4 | Accessibility pass | Icon-only toolbars and dense reading UI need TalkBack labels, 200% font support, contrast and target-size checks | Add content descriptions, focus order, semantic headings, reduced-motion handling and a colour-blind-safe highlight palette. No new dependency. |
| 5 | External lookup from selection | A study reader benefits from dictionary/translate without shipping a dictionary or cloud SDK | Use `ACTION_PROCESS_TEXT` or a share chooser so the user picks an installed dictionary/translate app. No network code or API key in My PDF Writer. |

### 4.4 Skip / later (5)

| Item | Decision | Reason |
| --- | --- | --- |
| Cloud AI assistant, chat, summary, translation, rewrite, AI read-aloud voices | **Out of scope** | Requires `INTERNET`, accounts, cloud cost, privacy disclosure and model/vendor dependencies. Directly conflicts with the offline, no-account promise. |
| Accounts, subscriptions, billing, ads, analytics, attribution | **Out of scope** | Adds permissions, SDK weight, tracking and trust cost. The target's 25 permissions and ad/attribution stack are a warning, not a template. |
| Office reading/editing (DOCX/XLSX/PPTX) | **Later / probably skip** | Large dependency surface, fidelity problems and a different product category. If ever added, limit to DOCX text import, not a full Office editor. |
| Camera scan, ID-card scan, batch scan | **Later / skip** | Needs `CAMERA`, a capture pipeline and Play-policy work. My PDF Writer already has on-device OCR for imported scans; a camera scanner is not core reading. |
| Threaded comments/collaboration, full convert/compress/e-sign suites, page-flip/3D showcase animations | **Skip** | Page notes already cover personal annotation; threaded comments imply cloud/collab. The conversion/e-sign suite dilutes the offline study focus, and decorative page-turn animation adds risk without reading value. |

### 4.5 Sequencing and guardrails

1. **Small wins first:** immersive chrome + HUD, reading settings, resume, PDF
   bookmarks, thumbnail rail.
2. **Core reading:** four flow modes, selection, selection markup, system TTS.
3. **Differentiator:** reflow, with a hard fallback for pages that cannot reflow.
4. **Polish:** search highlighting, two-page/RTL, TTS notification/sleep timer,
   accessibility, external lookup.

Guardrails for every item:

- No new `INTERNET` permission, no cloud SDK, no account, no ads.
- No reader code, assets, fonts, icons, sounds, Lottie files, demo documents or
  model blobs from the target.
- Reading settings must not silently change system brightness or orientation.
- TTS must stop on audio-focus loss and must not require microphone access.
- Reflow must never replace a page with a blank view; fall back to the original.
- Test large PDFs, low-memory devices, rotation, process death and Korean text.

---

## 5. IP boundary

### 5.1 Allowed

- Studying interaction patterns: page-flow modes, selection popover, bottom
  markup tray, reflow strip, page HUD, library information architecture.
- Using generic functional labels: “Reflow”, “Read aloud”, “Bookmarks”, “Night
  mode”, “Go to page”.
- Writing original Kotlin/Compose code and original icons, illustrations, strings
  and colour systems.
- Using system fonts or third-party fonts with a compatible licence and recorded
  attribution.

### 5.2 Not allowed

- Copying source code, decompiled classes, XML layouts, drawables, icons,
  illustrations, screenshots, Lottie animations, sounds, demo PDF/DOCX/PPTX/XLSX
  files, MNN/model blobs, bundled fonts or ad SDK configuration.
- Reusing the package name, “All PDF Reader”, “PDF Reader - Easy PDF”, “A+ Read”,
  “Office in your hand” or other brand/listing copy.
- Reproducing the target's visual composition pixel-for-pixel, including its
  exact top-bar arrangement, colour palette, icon set or split-screen dark-mode
  promotional layout.
- Copying the Google API-key-shaped string, ad unit IDs, analytics keys or any
  other secret-like resource value.
- Sending document text to a cloud AI/translation service without explicit,
  separate, informed consent.

The implementation should be a clean-room reading experience: behaviour and
generic labels observed from the outside, original code and original visual
design inside. If a future feature uses an external app (for example Google
Translate via intent), it remains an explicit user action, not a bundled cloud
dependency.

---

## 6. Limitations

- **Static only.** Findings come from `aapt2` manifest/resource dumps, `unzip -l`,
  `strings`, `apksigner` and public screenshots. There was no app execution, no
  JADX/decompilation, no runtime inspection and no network capture.
- **Resource names are not behaviour.** Strings and layouts can be unused,
  legacy, feature-flagged or A/B-gated. Presence of a label does not prove a
  working feature; absence does not prove a feature is missing.
- **Marketing mockups may differ.** The 900×1440 images are promotional, not
  runtime screenshots. Some graphics in the screenshot folder carry a different
  app name or metrics than this package and were excluded from evidence. The
  split dark/light image is illustrative.
- **TXT is unconfirmed.** The home mockups show a TXT tab, but the manifest's
  explicit MIME table has no `text/plain`. TXT may be extension-based or
  marketing-only.
- **Comments are unconfirmed.** Only `Add Comment(s)` strings were found; no
  reader-side comment list or composer was identified.
- **Margins in reflow are unconfirmed.** `Margins`/`No margins` strings exist, but
  no visible control was confirmed in the inspected reflow layouts.
- **Counts are inflated.** The 241 activities include 85 `WrapLetterBox` variants
  and many library/ad/billing screens. They are not 241 distinct user features.
- **No behaviour verification.** Ad delivery, subscription gating, AI responses,
  Office fidelity, TTS voice availability, password handling and performance were
  not exercised. Do not treat any of them as guaranteed.
- **Signer identity is generic.** The certificate DN does not identify the
  publisher. The public listing title also differs from the installed label.
- **Secret-like value not reproduced.** A Google API-key-shaped string appears in
  the resource dump; its presence is noted only as a caution and is not proof of
  a working key or of cloud behaviour.
- **Time/version specific.** This analysis describes version `2.1.9` (143) as
  observed on 2026-11-16. Later builds may differ.

---

## 7. References

- Target APK: `/sdcard/Download/apk_for_pdf/PDF 리더기_2.1.9.apk`
- Public listing: `https://play.google.com/store/apps/details?id=pdf.pdfreader.viewer.editor.free`
- Play page snapshot: `/data/data/com.termux/files/usr/tmp/opencode/pdfw/pdfreader-play.html`
- Resource dump: `/data/data/com.termux/files/usr/tmp/opencode/pdfw/pdfreader-resources.txt`
- String dump: `/data/data/com.termux/files/usr/tmp/opencode/pdfw/pdfreader-strings.txt`
- Screenshot set: `/data/data/com.termux/files/usr/tmp/opencode/pdfw/pdfreader_shots/`

No APK, screenshot, font, icon, model or demo document is committed to this
repository. This document is the only deliverable.
