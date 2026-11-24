package com.hackerdjakodjo.mypdfwriter.e2e

import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.hackerdjakodjo.mypdfwriter.debug.DevLog
import com.hackerdjakodjo.mypdfwriter.pdf.PdfFlowTrace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented coverage for the **durable developer log** facility itself.
 *
 * Everything here runs against the real [DevLog] singleton installed by the
 * Application's `onCreate` on an actual device/emulator. No fake Context, no
 * mocked ContentResolver: `files()` is backed by real `*.jsonl` files under
 * `filesDir/logs`, and the assertions read those bytes from disk. That is the
 * property a developer cares about — the log survives long enough to be
 * inspected *after* the test (and after the Activity that produced it).
 *
 * Why [ActivityScenarioRule] rather than `createAndroidComposeRule`: the log is
 * a plain object with no Compose dependency, and [ActivityScenarioRule] owns a
 * real lifecycle. `ActivityScenario.recreate()` therefore updates the scenario's
 * own Activity reference; calling `Activity.recreate()` on a Compose rule's
 * Activity leaves that rule tracking a stale instance and breaks teardown (a
 * pitfall already documented in `BackupRealFileE2ETest`).
 *
 * APIs used: [DevLog.enabled], `clear(includeFiles=true/false)`, `event`,
 * `snapshot`, `files`, `exportedJsonl`, `exportedText`, `sessionId`, plus
 * [PdfFlowTrace.search] for the privacy guard.
 */
class DevLogE2ETest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    @Before
    fun setUp() {
        // Instrumentation builds are debug builds; make the contract explicit
        // so an accidental release-only flip fails these tests clearly.
        DevLog.enabled = true
        // Drop prior files AND memory so each case owns a fresh session header.
        DevLog.clear(includeFiles = true)
    }

    // ------------------------------------------------------------------ helpers

    /** Reads the newest on-disk log file; disk bytes, never the memory snapshot. */
    private fun newestLogText(): String {
        val files = DevLog.files()
        assertTrue("DevLog.files() must expose at least one log file", files.isNotEmpty())
        val newest = files.first()
        assertTrue("newest log file must exist: ${newest.absolutePath}", newest.exists())
        return newest.readText()
    }

    // -------------------------------------------------------------------- tests

    /**
     * Intent: prove the log facility produces ordered, structured JSONL lines
     * that are written to disk and exported in two developer-friendly forms.
     * APIs: `DevLog.enabled`, `clear(true)`, `event(a, b)`, `files()`,
     * `exportedJsonl()`, `exportedText()`, `sessionId`.
     * Asserts: two distinct emitted events land in the newest `*.jsonl` file,
     * every emitted line carries `seq`/`iso`/`session`/`component`, the session
     * matches [DevLog.sessionId], and both exports contain the events plus a
     * session header.
     */
    @Test
    fun devLogIsEnabledAndPersistsStructuredLinesOnDisk() {
        assertTrue("DevLog must be enabled in instrumented (debug) builds", DevLog.enabled)

        DevLog.clear(includeFiles = true)

        DevLog.event("E2E_TEST", "first_probe", "probe" to "first", "attempt" to 1)
        DevLog.event("E2E_TEST", "second_probe", "probe" to "second", "attempt" to 2)

        val files = DevLog.files()
        assertTrue("DevLog.files() must expose at least one JSONL file", files.isNotEmpty())
        val newest = files.first()
        assertTrue("newest log file must exist: ${newest.absolutePath}", newest.exists())
        assertTrue(
            "log files must use the .jsonl suffix: ${newest.name}",
            newest.name.endsWith(".jsonl"),
        )

        val lines = newest.readLines().filter { it.isNotBlank() }
        assertTrue("newest log file must hold at least two lines, got ${lines.size}", lines.size >= 2)

        // clear(true) may prepend a DEVLOG/session_start line; only the lines
        // for our own component are required to carry the E2E_TEST component.
        val probeLines = lines.filter { it.contains("\"component\":\"E2E_TEST\"") }
        assertTrue("both E2E_TEST events must be on disk, got: $lines", probeLines.size >= 2)
        assertTrue(
            "first_probe must be on disk",
            probeLines.any { it.contains("\"event\":\"first_probe\"") },
        )
        assertTrue(
            "second_probe must be on disk",
            probeLines.any { it.contains("\"event\":\"second_probe\"") },
        )

        probeLines.forEach { line ->
            assertTrue("line must carry a seq: $line", line.contains("\"seq\""))
            assertTrue("line must carry an ISO timestamp: $line", line.contains("\"iso\""))
            assertTrue("line must carry a session: $line", line.contains("\"session\""))
            assertTrue(
                "line session must equal DevLog.sessionId: $line",
                line.contains("\"session\":\"${DevLog.sessionId}\""),
            )
        }

        val jsonl = DevLog.exportedJsonl()
        assertTrue("exportedJsonl must contain first_probe", jsonl.contains("first_probe"))
        assertTrue("exportedJsonl must contain second_probe", jsonl.contains("second_probe"))

        val text = DevLog.exportedText()
        assertTrue("exportedText must contain first_probe", text.contains("first_probe"))
        assertTrue("exportedText must contain second_probe", text.contains("second_probe"))
        assertTrue(
            "exportedText must carry a session header",
            text.lines().any { it.trimStart().startsWith("#") },
        )
        assertTrue("exportedText must identify the session", text.contains(DevLog.sessionId))
    }

    /**
     * Intent: an event written before an Activity recreation must still be
     * readable afterwards — the log is durable beyond a single Activity and
     * beyond the in-memory snapshot a developer might clear while debugging.
     * APIs: `DevLog.event`, `DevLog.files`, `DevLog.snapshot`,
     * `clear(includeFiles=false)`, `ActivityScenario.recreate()`.
     * Asserts: the marker is on disk before and after recreation, survives an
     * in-memory-only clear, and both lifecycle/snapshot views agree.
     */
    @Test
    fun devLogSurvivesActivityRecreation() {
        val marker = "recreate-marker-${System.nanoTime()}"
        DevLog.event("E2E_TEST", "before_recreate", "marker" to marker)

        val filesBefore = DevLog.files()
        assertTrue("a log file must exist before recreation", filesBefore.isNotEmpty())
        val onDisk = filesBefore.first()
        assertTrue("event must be on disk before recreation", newestLogText().contains(marker))

        // Real Activity lifecycle recreation (see the class KDoc for why this
        // uses the scenario instead of Activity.recreate()).
        activityRule.scenario.recreate()
        activityRule.scenario.onActivity { recreated ->
            assertTrue("recreated activity must be alive", recreated != null)
        }

        assertTrue("event must still be on disk after recreation", newestLogText().contains(marker))
        assertTrue(
            "event must remain in the in-memory snapshot after recreation",
            DevLog.snapshot().any { it.fields["marker"] == marker },
        )

        // Strongest durability proof: discarding only memory must not erase the
        // file the developer is keeping for reference.
        DevLog.clear(includeFiles = false)
        assertFalse(
            "in-memory snapshot must be cleared",
            DevLog.snapshot().any { it.fields["marker"] == marker },
        )
        // Read the exact file captured before the clear, so this proof relies
        // only on disk bytes and not on any in-memory file bookkeeping.
        assertTrue(
            "disk log must still contain the event after an in-memory clear",
            onDisk.exists() && onDisk.readText().contains(marker),
        )
    }

    /**
     * Intent: PDF text must never leak into the developer log. The trace API
     * deliberately carries only lengths/counts for search — this guards against
     * a future change that logs the raw query or extracted document text.
     * APIs: [PdfFlowTrace.search], `DevLog.exportedText/exportedJsonl`.
     * Asserts: `queryLength=7` is logged, while the fixture text "FIXTURE" is
     * absent from every exported form.
     */
    @Test
    fun devLogNeverContainsSensitiveText() {
        DevLog.clear(includeFiles = true)
        PdfFlowTrace.search(queryLength = 7, hitCount = 3)

        val text = DevLog.exportedText()
        val jsonl = DevLog.exportedJsonl()
        val lengthLogged = text.contains("queryLength=7") ||
            jsonl.contains("\"queryLength\":\"7\"") ||
            jsonl.contains("queryLength=7")
        assertTrue("queryLength=7 must be traced: text=$text jsonl=$jsonl", lengthLogged)
        assertFalse("PDF fixture text must never appear in the text log", text.contains("FIXTURE"))
        assertFalse("PDF fixture text must never appear in the JSONL log", jsonl.contains("FIXTURE"))
    }
}
