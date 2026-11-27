# Foxit PDF Editor static analysis (2026.6.0.1026.0325)

## Target and method

- APK: `/sdcard/Download/apk_for_pdf/Foxit PDF Editor_2026.6.0.1026.0325.apk`
- Package: `com.foxit.mobile.pdf.lite`
- Version: `2026.6.0.1026.0325` (versionCode 407)
- SHA-256: `4649186ddde016be0146f06fbbb737bdb3ac0ade568c5d8b4e3e20e165d949a3`
- Size: 145,324,911 bytes
- REA archive inspection Evidence: `ev_4ab31930b35d9b314c0553ed72cf698df6c2c5a16a3f0063fbd0cf17aec2606e`
  (summary view `ev_1801b8211350636a2f123d9b43921e331c29bdd85f71a7f819103a1aeede86b4`)
- Inventory: 4,977 APK entries, 4,591 content-addressed nodes, no integrity
  contradictions reported by REA.
- AAPT2 exposed 3,143 default string resources and six DEX files.

REA's Android/JADX provider cannot run on `android/arm64` in this environment.
The analysis therefore uses read-only AAPT2 resource/manifest dumps and a local
DEX metadata parser for class/method names. The APK was not launched. Static
strings and SDK method names establish available declarations, not that every
feature is enabled, functional, free, or reachable in this particular build.

No Foxit code, resources, fonts, images, templates or branding are copied into
My PDF Writer. The reference is used only for feature and interaction analysis.

## Observed feature groups

### 1. Comment and annotation tools

Resource evidence:

- `annot_tool_display_name_pencil`, `annot_tool_display_name_textbox`,
  `annot_tool_display_name_typewriter`, `annot_tool_display_name_callout`,
  `annot_tool_display_name_stamp`, `annot_tool_attachment`
- `fx_string_highlight`, `fx_string_area_highlight`, `fx_string_underline`,
  `fx_string_strikeout`, `fx_string_strickout`, `fx_string_squiggly`
- `fx_string_rectangle`, `fx_string_oval`, `fx_string_line`, `fx_string_arrow`,
  `fx_string_polygon`, `fx_string_polyLine`, `fx_string_place_cloud`
- `fx_string_eraser`, `fx_string_redaction`, `fx_string_fileattachment`
- `edit_add_image_prompt`, `edit_add_audio_toast_content`,
  `edit_add_video_toast_content`, `edit_add_link_toast_content`
- `annot_stamp_standard`, `annot_stamp_dynamic`, `annot_stamp_signhere`,
  `add_custom_text_stamp_title`, `custom_stamp_image_type`
- `atb_annot_reply_*`, `annot_continue_create`, `annot_single_create`

SDK declarations include `com.foxit.sdk.pdf.annots.Ink`, `Line`, `Link`, `Note`
and `com.foxit.sdk.addon.Redaction` (`markRedactAnnot`, `startApply`, `apply`).

The resource IDs expose customizable tool-set slots:

- `add_tools_comment_1` through `add_tools_comment_15`
- `add_tools_drawing_toolbar_1` through `add_tools_drawing_toolbar_10`
- `add_tools_edit_1` through `add_tools_edit_5`
- `add_tools_form_toolbar_1` through `add_tools_form_toolbar_7`
- `add_tools_protect_toolbar_1` through `add_tools_protect_toolbar_8`

These counts are observed UI capacity, not proof of the exact tool assigned to
each slot.

### 2. Direct PDF content editing

Resource evidence:

- `account_upgrade_edit_small`: edit text object, image object or properties
- `fx_string_insert_text`, `fx_string_replace_text`, `fx_string_add_text`,
  `fx_string_add_image`
- `editor_nav_bar_font_name`, `editor_nav_bar_font_size`,
  `editor_nav_bar_font_color`, `editor_nav_bar_font_style`,
  `editor_nav_bar_font_style_bold`, `editor_nav_bar_font_style_italic`
- `edit_textobj_cannot_edit`: rotated, sheared or flipped text objects are not
  editable
- `edit_outline`, `edit_permissions`

SDK declarations:

- `com.foxit.sdk.pdf.Editor`: `loadEditorPage`, `undo`, `redo`, `killFocus`
- `com.foxit.sdk.pdf.EditorPage`: `addNewParagraphAtPos`, `deleteActiveParagraph`,
  `commitResizeRect`, `startRealEditing`, `endEditing`
- `EditorModuleJNI` exposes paragraph font, size, color, bold, italic,
  alignment, underline, list number/symbol, insert word, copy/paste/select.

This is genuine content-object editing, not merely drawing a text annotation on
top of the page. It is the largest parity gap for a PDFBox-based implementation.

### 3. Page and document organization

Resource evidence:

- `account_upgrade_organize_document_big`: insert, extract, rotate, reorder
- `atb_ppo_add_page`, `atb_ppo_copy`, `atb_ppo_paste`, `atb_ppo_delete`,
  `atb_ppo_extract`, `atb_ppo_rotate_left`, `atb_ppo_rotate_right`
- `crop_menu_auto_crop`, `crop_menu_hand_crop`, `crop_menu_remove_crop`,
  `crop_setting_all_pages`, `crop_setting_odd_even`
- `add_outline`, `edit_outline`, `move_outline`, `fx_renamebook_title`
- `menu_more_pages`, `menu_more_document_size`, `menu_more_pdf_version`

SDK declarations:

- `PDFDoc.insertPage`, `insertDocument`, `movePageTo`, `movePagesTo`,
  `removePage`, `startExtractPages`, `startImportPages`
- `PDFPage.setRotation`, `setBox`, `setSize`, `setClipRect`, `transform`
- `PDFDoc.addTableOfContents`, `createRootBookmark`, `removeBookmark`

### 4. Forms and fill/sign

Resource evidence:

- `form_tool_text_type`, `form_tool_check_type`, `form_tool_radio_type`,
  `form_tool_combo_type`, `form_tool_list_type`, `form_tool_image_type`,
  `form_tool_signature_type`
- `form_filling_suggestions`, `form_submit_success`, `form_submit_failed`
- `menu_more_item_export`, `menu_more_item_import`, `menu_more_item_reset`
- `PDFDoc.startRecognizeForm`

SDK declarations:

- `PDFDoc.hasForm`, `isXFA`, `isStaticXFA`, `removeXFA`
- `PDFDoc.exportFormFieldsToFDF`, `importFromFDF`, `exportToFDF`
- `com.foxit.sdk.pdf.FillSign.addObject`, `addTextObject`, `removeObject`
- `FillSignModuleJNI` exposes text and signature fill-sign objects.

### 5. Signatures, redaction and protection

Resource evidence:

- `esign_draw_signature`, `esign_create_by_type`, `esign_create_by_camera`,
  `esign_create_by_gallery`, `esign_request_title`
- `sign_list_signer_dsg_group_title`, `sign_list_signer_handwriting_group_title`
- `menu_more_redact_pages`, `atb_search_redact`, `fx_string_redaction`
- `fx_string_watermark`, `rv_doc_encrpty_standard`, `menu_more_file_encryption`,
  `menu_more_item_remove_encrytion`, `menu_more_item_cert_protect`
- `fx_flatten_doc_toast`, `fx_flatten_toast`

SDK declarations:

- `Signature.startSign`, `startVerify`, `detectModification`, `setBitmap`,
  `setImage`, `getCertificateInfo`, `getSignTime`, `getState`
- `PDFDoc.removeHiddenInfo`, `sanitize`, `removeSecurity`, `getUserPermissions`
- `PDFPage.flatten`, `flattenAnnot`
- `PDFDoc.addHeaderFooter`, `updateHeaderFooter`, `removeAllHeaderFooters`
- `PDFDoc.startAddTiledWatermark`; `Watermark.insertToPage` and watermark
  settings for position, rotation, scale, opacity, font, size and style.

Certificate signing and RMS/AIP depend on external identity/key infrastructure.
They are not part of an offline-only parity target.

### 6. Create, convert, scan and export

Resource evidence:
- `convert_createpdf_filetopdf`, `convert_createpdf_gallery`,
  `convert_createpdf_mergepdf`, `convert_createpdf_webtopdf`
- `merge_pdfs_tip`, `createpdf_merge_extract`
- `scan_tip`, scan crop/rotate/filter/page-size/continuous capture controls
- `menu_more_print`, `menu_more_screen`, `menu_more_item_annot_export`,
  `menu_more_summarize_comments`, `menu_more_reduce_files_warning_content`
- `ocr_searchable_text_img`, `ocr_searchable_text_img_describe`
- `hm_comparison_title`, `hm_comparison_mode_overlay`,
  `hm_comparison_mode_side_by_side`, `compare_progress`
- `nui_reduce_file`, `rv_doc_reduce_file_size`, `rv_saveas_optimize`

SDK declarations include FDF annotation/form export and import, metadata,
page labels, attachments, layers, header/footer and watermark operations.

Web-to-PDF, cloud conversion, AI assistants, cloud storage, ConnectedPDF and
subscription activation are online services and are intentionally excluded from
My PDF Writer's offline contract. The APK also contains explicit subscription
copy for Edit & Organize, Create & Convert, Secure & Sign and XFA; static
analysis cannot determine the exact free/paid feature matrix.

## Parity interpretation

Foxit combines four distinct layers:

1. A native PDF SDK with document/content mutation APIs.
2. A customizable toolbar containing comment, drawing, edit, form and protect
   tool sets.
3. Document workflows for organize, create/convert/scan, protect and export.
4. Online account, cloud, AI, subscription and rights-management services.

My PDF Writer should target layers 1–3 only where they can be implemented
locally with PDFBox-Android, Android PDF rendering and Compose. Matching the
visual toolbar alone would not match the editor; matching the toolbar requires
real persistence, undo/redo and flattened export behind every action.

## Reproduction notes

The fallback analysis used:

- `aapt2 dump badging`, `permissions`, `strings`, `resources`
- `unzip` for the six DEX files
- a read-only Python DEX metadata parser for class/method names

Intermediate files are under
`/data/data/com.termux/files/usr/tmp/opencode/foxit-analysis/`. They contain
target-derived data and are not part of the application repository.

## Limitations

- No runtime observation, touch validation or feature eligibility testing was
  performed.
- JADX decompilation was unavailable on this host; obfuscated application
  classes were not reconstructed.
- SDK method declarations may wrap native code that is downloaded or loaded
  separately; this APK inventory did not establish native-library packaging.
- Resource strings can include dormant, subscription-gated or platform-specific
  features. Their presence is not proof of current availability.
