package com.hackerdjakodjo.mypdfwriter

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class MyPdfWriterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
