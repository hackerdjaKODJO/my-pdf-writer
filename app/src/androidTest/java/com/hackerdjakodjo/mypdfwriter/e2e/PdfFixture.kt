package com.hackerdjakodjo.mypdfwriter.e2e

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Shared infrastructure for the real-PDF end-to-end tests.
 *
 * Everything here works on an actual, on-disk PDF generated with PDFBox-Android:
 * no mocked ContentResolver, no fake ViewModel. The fixture is written under the
 * app's private [Context.getFilesDir] so it is reachable through the app's own
 * FileProvider (authority `${applicationId}.fileprovider`, `files-path path="."`).
 */
object PdfFixture {

    /** The fixture always has three A4 pages, each with the word FIXTURE in it. */
    const val PAGE_COUNT = 3

    /** Generous ceiling for IO-heavy operations (render, assemble, export) on an emulator. */
    const val DEFAULT_TIMEOUT_MS = 15_000L

    private const val POLL_INTERVAL_MS = 50L

    /** Must be called once from `@BeforeClass` before any PDDocument is touched. */
    fun init(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /**
     * Delivers an import Intent through MainActivity's real singleTop
     * `onNewIntent` path. The method is protected, so tests reach it reflectively
     * (same production code path as the OS redelivery).
     */
    fun deliverIntent(activity: Activity, intent: Intent) {
        // Invoke the Activity override (MainActivity) via its declared method.
        val declared = activity.javaClass.getDeclaredMethod("onNewIntent", Intent::class.java)
        declared.isAccessible = true
        declared.invoke(activity, intent)
    }

    /**
     * Drives MainActivity's real ACTION_VIEW/ACTION_SEND import path *without*
     * replacing the rule-owned Activity's stored intent. `deliverIntent` goes
     * through `onNewIntent`, which also calls `setIntent`; leaving a synthetic
     * external import intent on the Activity keeps ActivityScenarioRule from
     * moving it cleanly to DESTROYED during teardown. `handleViewIntent` is the
     * same production entry point that both `onCreate` and `onNewIntent`
     * delegate to (including carrier clearing and the recents policy), so the
     * behaviour under test is unchanged — only the lifecycle side effect is gone.
     */
    fun deliverViewIntent(activity: Activity, intent: Intent) {
        val declared = activity.javaClass.getDeclaredMethod("handleViewIntent", Intent::class.java)
        declared.isAccessible = true
        declared.invoke(activity, intent)
    }

    /**
     * Writes a real 3-page PDF. Each page carries selectable text so the app's
     * PDFBox text stripper can find "FIXTURE" on all three pages.
     */
    fun writeFixture(file: File) {
        file.parentFile?.mkdirs()
        PDDocument().use { doc ->
            repeat(PAGE_COUNT) { index ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 20f)
                    cs.newLineAtOffset(72f, PDRectangle.A4.height - 72f)
                    cs.showText("FIXTURE PAGE ${index + 1}")
                    cs.endText()
                }
            }
            doc.save(file)
        }
    }

    /** A content URI the app can read (and write, for export) through its own provider. */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Removes all stored data but keeps the top-level directories the ViewModel's
     * stores created in their constructors. Several stores write without calling
     * `mkdirs()` again, so deleting e.g. `files/docstates` itself would silently
     * break every later sidecar write.
     */
    fun emptyDirectoryTree(dir: File) {
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                child.listFiles()?.forEach { it.deleteRecursively() }
            } else {
                child.delete()
            }
        }
    }

    /**
     * Deadline-bounded polling helper. The caller runs on the instrumentation
     * thread while the ViewModel's coroutines run on the main looper, so sleeping
     * here does not block state publication.
     */
    fun awaitUntil(
        message: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $message")
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    /** Waits until an output file exists and has bytes (export writes are async). */
    fun awaitFile(file: File, minBytes: Long = 1L) {
        awaitUntil("file ${file.absolutePath} to exist with >= $minBytes bytes") {
            file.exists() && file.length() >= minBytes
        }
    }

    /**
     * Verifies the two independent properties of a flattened export:
     *  1. PDFBox sees the expected page count and **no /Annots** on any page;
     *  2. Android PdfRenderer sees actual ink in the middle of the annotated page
     *     (the fixture text sits near the top, so this band isolates the overlay).
     */
    fun assertFlattenedPdf(
        file: File,
        expectedPages: Int = PAGE_COUNT,
        annotatedPage: Int = 0,
    ) {
        assertTrue("exported file must exist: ${file.absolutePath}", file.exists())
        PDDocument.load(file).use { doc ->
            assertEquals("exported page count", expectedPages, doc.numberOfPages)
            for (index in 0 until doc.numberOfPages) {
                assertTrue(
                    "page $index must have no /Annots (ink is flattened into content)",
                    doc.getPage(index).annotations.isEmpty(),
                )
            }
        }

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals("PdfRenderer page count", expectedPages, renderer.pageCount)
                renderer.openPage(annotatedPage).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    try {
                        assertTrue(
                            "annotated page $annotatedPage must contain flattened ink pixels",
                            hasNonWhitePixelInBand(bitmap, 0.45f, 0.85f, 0.10f, 0.90f),
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    /** Scans a normalized rectangle of a bitmap (origin top-left) for a non-white pixel. */
    fun hasNonWhitePixelInBand(
        bitmap: Bitmap,
        topFraction: Float,
        bottomFraction: Float,
        leftFraction: Float,
        rightFraction: Float,
    ): Boolean {
        val x0 = (bitmap.width * leftFraction).toInt().coerceIn(0, bitmap.width)
        val x1 = (bitmap.width * rightFraction).toInt().coerceIn(x0, bitmap.width)
        val y0 = (bitmap.height * topFraction).toInt().coerceIn(0, bitmap.height)
        val y1 = (bitmap.height * bottomFraction).toInt().coerceIn(y0, bitmap.height)
        val row = IntArray(bitmap.width)
        for (y in y0 until y1) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in x0 until x1) {
                if (row[x] != Color.WHITE) return true
            }
        }
        return false
    }
}
