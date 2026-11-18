# Samsung Notes post-it + Flexcil favorite pens — 0.8.27

## Evidence boundary

The user-supplied Samsung Notes APK
`/sdcard/Download/apk_for_pdf/삼성 노트_4.4.48.15.apk`
(`com.samsung.android.app.notes`, 4.4.48.15, SHA-256
`484d3dd599355789508034ba90af9a35e09f81532564a992c131710632a467ce`)
was inspected read-only. REA Android analysis was attempted but is unavailable on
this android/arm64 host: `inspect_android_package` reports unsupported JADX
subprocess ownership and `decode_android_resources` needs an apktool launcher
that is not installed. No tools were installed. The reliable evidence therefore
comes from `aapt2 dump badging/resources/xmltree`, `unzip -Z1` name listings,
and public Samsung support pages. No app was executed, no archive member was
extracted into the repository, and no Samsung/Flexcil code, layouts, icons,
colors, strings, templates or branding were copied.

Flexcil evidence came from the existing static ledger and read-only resource
inspection of `Flexcil_1.5.1.4.apk`; it is similarly behavioral only.

## What Samsung Notes showed

- Sticky memo is an **insertable page object**, not a separate screen
  (`composer_add_sticky_memo`, `layout/sticky_memo_view`).
- It has bounded text (char-limit toast/truncation dialog), four pastel
  background colors with dark variants, a card-local `⋮` menu for background
  color and delete, a minimize/hide action, a resize handle, and a
  touch-and-hold top-center drag to move.
- Only text and pen strokes can be pasted into sticky notes; card-level
  tags/pin/reminder do not exist.
- Public support docs confirm: Add (+) → Sticky notes, `⋮` → background color,
  minimize, drag the top to move, expand → delete.

## What Flexcil showed

- A configurable pen toolbar with visible/hidden tool lists, add/move/remove
  actions, a numeric slot count that shrinks on narrow screens, and a
  `usedTime` field in `DefaultPens.json` suggesting recency/frequency ranking.
- Premium caps (30 toolbar pens, 50 favorites/colors), toolbar skins, shop,
  ads and cloud layers were explicitly excluded.

## Implemented in 0.8.27

### Original post-it memo cards

- New `MemoCard` model on `NotePage`: bounded text (600 chars), five original
  pastel tints, normalized rect with clamping, optional tilt, stable ids,
  backward-compatible JSON, and `Memos.freshIds`.
- Compose-drawn overlay in `NoteCanvas`: rounded card, soft shadow, folded
  corner, clipped text, minimize/hide, card-local `⋮` menu (tint/edit/delete),
  top-strip drag to move, bottom-right resize handle. Pointer input is consumed
  so a tap or drag on a card never becomes a pen stroke; drawing outside cards
  is unchanged. Each move/resize commits one undo step.
- `MemoCardsSheet`: cross-page list, show-on-page switch, jump, add, edit with
  tint and percentage geometry fields, delete confirmation. Entry in the note
  study-card menu as `메모 카드`.
- Duplicate-page/notebook paths refresh memo ids; the pre-existing masking-id
  omission was fixed in the same pass.
- `NoteCardPainter` renders post-its into exported/thumbnailed note PDFs by
  default, with WYSIWYG layout and Korean-safe text wrapping.

### Favorite/frequent pens (fancy but offline)

- `FavoritePens` pure registry (max 6) stored in the existing `pen_box`
  SharedPreferences under `favorites_v1`, so local ZIP backup already includes
  it. Missing key means first four default pens; an explicitly cleared key stays
  cleared; stale ids are pruned.
- `PenBoxSheet` gained a star toggle, a compact favorites section and
  left/right reorder controls. Rename, delete and the last-preset safeguard are
  preserved.
- `FavoritePenStrip` shows up to six slots plus `+`: original ink-stroke
  previews, short pen-type labels, an active ring, 56 dp targets, tap to apply,
  long-press to edit.
- The strip is available in the note style sheet and in the PDF viewer's
  `주석 도구` sheet; the viewer can also open the full registry sheet. Viewer
  presets apply tool/color/width/opacity (viewer has no pen-style state yet).

## Skipped

Cloud sync, accounts, Samsung Cloud, Galaxy AI, Bixby, S-Pen/Air Command,
Screen-off Memo, reminders/widgets, licensed sticker packs, Flexcil skins/shop,
premium caps, ads and any network permission.

## Verification

- JVM: **570 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` pass.
- No `INTERNET` permission; arm64-only build.
- Instrumentation was compiled but not executed (no adb device here).

## QA follow-up before release

- The viewer keeps the favorite-pen strip visible even when the registry is
  empty, so the `+` slot can always reopen the registry.
- Memo-card help text now states that cards are included in PDF export and that
  the on-page hide switch is display-only; delete copy matches the undoable
  behavior. The per-card hide icon is labeled as hiding all memo cards.
- Viewer favorite slots now keep the pen style of the last applied preset, so
  fountain/brush/pencil favorites show the correct active state.
