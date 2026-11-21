package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** Pure SAF handoff tests: exact UTF-8 payload and a clear on every outcome. */
class SummarySafWriterTest {

    @Test
    fun writesTheSummaryAsUtf8WithoutChangingItsText() {
        val text = "# 주석 요약\n- 형광펜 — 중요\n- 도장 · 승인\n"
        val out = ByteArrayOutputStream()

        assertTrue(SummarySafWriter.write(out, text))

        assertEquals(text.toByteArray(Charsets.UTF_8).toList(), out.toByteArray().toList())
        assertEquals(text, String(out.toByteArray(), Charsets.UTF_8))
        assertEquals(SummarySafWriter.utf8(text).toList(), out.toByteArray().toList())
    }

    @Test
    fun cancelledPickerOrBlankPayloadAlwaysClearsThePendingText() {
        assertEquals(SummarySafWriter.Outcome.CLEAR, SummarySafWriter.outcomeForResult(false, "요약"))
        assertEquals(SummarySafWriter.Outcome.CLEAR, SummarySafWriter.outcomeForResult(false, ""))
        assertEquals(SummarySafWriter.Outcome.CLEAR, SummarySafWriter.outcomeForResult(true, "  \n "))
        assertEquals(SummarySafWriter.Outcome.WRITE, SummarySafWriter.outcomeForResult(true, "요약"))
    }

    @Test
    fun aFailingStreamIsReported() {
        val failing = object : OutputStream() {
            override fun write(byte: Int) = throw IOException("no space")
        }
        assertFalse(SummarySafWriter.write(failing, "요약"))
    }
}
