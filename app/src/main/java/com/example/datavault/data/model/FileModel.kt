package com.example.datavault.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

data class FileModel(
    val name: String,
    val path: String,
    val absolutePath: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String
) {
    fun readableSize(): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
    }

    fun readableDate(): String {
        val format = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        return format.format(Date(lastModified))
    }
}
