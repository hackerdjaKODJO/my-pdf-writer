package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import com.hackerdjakodjo.mypdfwriter.data.PenStyle

/** Original nib silhouettes drawn by [PenStyleGlyph] — no third-party artwork. */
internal enum class PenNibKind { BALL, SPLIT, BRISTLE, GRAPHITE }

/** Visible stroke character of each pen, used by the glyph preview. */
internal enum class StrokeTexture { EVEN, TAPERED, WASH, GRAIN }

internal data class PenStyleGlyphSpec(
    val label: String,
    val hint: String,
    val nib: PenNibKind,
    val texture: StrokeTexture,
)

internal fun penStyleGlyphSpec(style: PenStyle): PenStyleGlyphSpec = when (style) {
    PenStyle.BALLPOINT -> PenStyleGlyphSpec(
        penStyleLabel(style), "일정한 굵기", PenNibKind.BALL, StrokeTexture.EVEN,
    )
    PenStyle.FOUNTAIN -> PenStyleGlyphSpec(
        penStyleLabel(style), "압력에 따라 부드럽게", PenNibKind.SPLIT, StrokeTexture.TAPERED,
    )
    PenStyle.BRUSH -> PenStyleGlyphSpec(
        penStyleLabel(style), "넓고 번지는 획", PenNibKind.BRISTLE, StrokeTexture.WASH,
    )
    PenStyle.PENCIL -> PenStyleGlyphSpec(
        penStyleLabel(style), "가루처럼 거친 획", PenNibKind.GRAPHITE, StrokeTexture.GRAIN,
    )
}

/** TalkBack label reused by both style sheets and the favourite strip. */
internal fun penStyleTalkBackLabel(style: PenStyle, selected: Boolean): String =
    stateLabel(penStyleGlyphSpec(style).label, selected)

internal fun SemanticsPropertyReceiver.penStyleDescription(style: PenStyle, selected: Boolean) {
    contentDescription = penStyleTalkBackLabel(style, selected)
}
