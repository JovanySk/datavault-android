package com.example.datavault

import android.app.Application
import com.example.datavault.data.local.AppDatabase
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class DataVaultApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
