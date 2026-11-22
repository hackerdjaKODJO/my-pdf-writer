package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.PaperTint

/**
 * Deterministic, asset-free paper fibre marks.
 *
 * The same marks are drawn by the editor, thumbnails, template studio and PDF
 * export through the shared [PaperBackground], so what you see while writing is
 * what ends up in the exported page. Count and positions are independent of the
 * canvas size; only scaling differs.
 */
internal data class ToothMark(
    val x: Float,
    val y: Float,
    val radius: Float,
)

internal object PaperTooth {
    /** Fixed count: deriving it from canvas size would desync editor and export. */
    const val MARK_COUNT = 420

    private const val SEED = 0x5DEECE66DL
    private const val X_MIX = 0x2545F4914F6CDD1DL
    private const val Y_MIX = 0x6AC690C5D3B4A8EFL
    private const val R_MIX = 0x3C79AC492BA7B653L

    /** Subtle by design; WHITE stays closest to a clean sheet. */
    fun alpha(tint: PaperTint, dark: Boolean): Int {
        val base = when (tint) {
            PaperTint.WARM -> 15
            PaperTint.PALE_YELLOW -> 12
            PaperTint.LIGHT_GRAY -> 9
            PaperTint.WHITE -> 6
        }
        return if (dark) (base * 0.6f).toInt().coerceAtLeast(0) else base
    }

    /** splitmix64 finalizer, mapped to 0..1 without any randomness source. */
    private fun hash01(value: Long): Float {
        var z = value + SEED
        z = (z xor (z ushr 30)) * R_MIX
        z = (z xor (z ushr 27)) * Y_MIX
        z = z xor (z ushr 31)
        val mantissa = (z ushr 11).toFloat()
        val scale = (1L shl 53).toFloat()
        return (mantissa / scale).coerceIn(0f, 1f)
    }

    fun marks(): List<ToothMark> = List(MARK_COUNT) { index ->
        val density = hash01(index * R_MIX)
        ToothMark(
            x = hash01(index * X_MIX),
            y = hash01(index * Y_MIX),
            radius = 0.35f + 0.75f * density,
        )
    }
}
