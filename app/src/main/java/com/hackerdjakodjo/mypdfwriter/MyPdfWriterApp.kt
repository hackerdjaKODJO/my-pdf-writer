package com.hackerdjakodjo.mypdfwriter

import android.app.Application
import com.hackerdjakodjo.mypdfwriter.debug.DevLog
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MyPdfWriterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // Durable, debug-only structured log installed once per process. Every
        // E2E flow can be replayed from files/logs/*.jsonl after a restart.
        DevLog.install(this)
    }
}
