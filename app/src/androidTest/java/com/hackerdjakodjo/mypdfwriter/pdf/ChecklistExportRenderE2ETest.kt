package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hackerdjakodjo.mypdfwriter.data.ChecklistCard
import com.hackerdjakodjo.mypdfwriter.data.ChecklistItem
import com.hackerdjakodjo.mypdfwriter.data.NoteLayer
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Pixel-level coverage for the checklist visibility option used by note exports. */
@RunWith(AndroidJUnit4::class)
class ChecklistExportRenderE2ETest {

    @Test
    fun includingChecklistsChangesRenderedPixels() {
        val page = checklistPage()
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val included = NoteExporter.renderPage(
                page = page,
                width = WIDTH,
                height = HEIGHT,
                includeChecklists = true,
            ).also { bitmaps.add(it) }
            val excluded = NoteExporter.renderPage(
                page = page,
                width = WIDTH,
                height = HEIGHT,
                includeChecklists = false,
            ).also { bitmaps.add(it) }

            assertEquals(included.width, excluded.width)
            assertEquals(included.height, excluded.height)
            assertFalse(
                "Including the checklist must change the exported page pixels",
                pixelDigest(included).contentEquals(pixelDigest(excluded)),
            )
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    @Test
    fun excludingChecklistsMatchesChecklistFreePage() {
        val page = checklistPage()
        val bitmaps = mutableListOf<Bitmap>()
        try {
            val excluded = NoteExporter.renderPage(
                page = page,
                width = WIDTH,
                height = HEIGHT,
                includeChecklists = false,
            ).also { bitmaps.add(it) }
            val checklistFree = NoteExporter.renderPage(
                page = page.copy(checklists = emptyList()),
                width = WIDTH,
                height = HEIGHT,
                includeChecklists = true,
            ).also { bitmaps.add(it) }

            assertEquals(excluded.width, checklistFree.width)
            assertEquals(excluded.height, checklistFree.height)
            assertArrayEquals(
                "Excluding checklists must match an otherwise identical checklist-free page",
                pixelDigest(checklistFree),
                pixelDigest(excluded),
            )
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    /** Fixed ids, geometry and timestamp keep both renders independent of clock or UUID state. */
    private fun checklistPage(): NotePage = NotePage(
        id = "checklist-export-page",
        template = PaperTemplate.BLANK,
        tint = PaperTint.WHITE,
        layers = listOf(NoteLayer(id = "ink-layer", name = "잉크")),
        checklists = listOf(
            ChecklistCard(
                id = "study-checklist",
                title = "오늘 공부",
                items = listOf(
                    ChecklistItem(id = "review", text = "복습하기", done = true),
                    ChecklistItem(id = "practice", text = "문제 풀기", done = false),
                ),
                x = 0.2f,
                y = 0.2f,
                w = 0.6f,
                h = 0.6f,
                createdAt = 1L,
            ),
        ),
    )

    private fun pixelDigest(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return MessageDigest.getInstance("SHA-256").run {
            val bytes = ByteBuffer.allocate(pixels.size * Int.SIZE_BYTES).apply {
                pixels.forEach { putInt(it) }
            }.array()
            update(bytes)
            digest()
        }
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 448
    }
}
