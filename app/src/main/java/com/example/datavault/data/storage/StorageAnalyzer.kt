package com.example.datavault.data.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StorageInfo(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long
)

data class CategoryInfo(
    val name: String,
    val fileCount: Int,
    val totalSize: Long,
    val icon: String
)

data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val extension: String
)

data class DuplicateGroup(
    val size: Long,
    val name: String,
    val files: List<FileInfo>
)

class StorageAnalyzer(private val context: Context) {

    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
    private val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp")
    private val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma")
    private val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf")
    private val apkExtensions = setOf("apk", "xapk")

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        var totalBytes: Long
        var freeBytes: Long

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val uuid = StorageManager.UUID_DEFAULT
                totalBytes = storageStatsManager.getTotalBytes(uuid)
                freeBytes = storageStatsManager.getFreeBytes(uuid)
            } else {
                val stat = StatFs(Environment.getDataDirectory().path)
                totalBytes = stat.totalBytes
                freeBytes = stat.freeBytes
            }
        } catch (e: Exception) {
            val stat = StatFs(Environment.getDataDirectory().path)
            totalBytes = stat.totalBytes
            freeBytes = stat.freeBytes
        }

        val usedBytes = totalBytes - freeBytes

        StorageInfo(
            totalBytes = totalBytes,
            usedBytes = usedBytes,
            freeBytes = freeBytes
        )
    }

    suspend fun getCategorySizes(): List<CategoryInfo> = withContext(Dispatchers.IO) {
        val categories = mutableMapOf(
            "Images" to MutableCategoryData("🖼️"),
            "Videos" to MutableCategoryData("🎬"),
            "Audio" to MutableCategoryData("🎵"),
            "Documents" to MutableCategoryData("📄"),
            "APKs" to MutableCategoryData("📦"),
            "Others" to MutableCategoryData("📁")
        )

        val root = Environment.getExternalStorageDirectory()
        try {
            root.walkTopDown()
                .onEnter { dir -> !dir.name.startsWith(".") }
                .filter { it.isFile }
                .forEach { file ->
                    val ext = file.extension.lowercase()
                    val category = categorizeExtension(ext)
                    categories[category]?.let { data ->
                        data.fileCount++
                        data.totalSize += file.length()
                    }
                }
        } catch (_: Exception) {
        }

        categories.map { (name, data) ->
            CategoryInfo(
                name = name,
                fileCount = data.fileCount,
                totalSize = data.totalSize,
                icon = data.icon
            )
        }
    }

    suspend fun getLargestFiles(limit: Int): List<FileInfo> = withContext(Dispatchers.IO) {
        val files = mutableListOf<FileInfo>()
        val root = Environment.getExternalStorageDirectory()

        try {
            root.walkTopDown()
                .onEnter { dir -> !dir.name.startsWith(".") }
                .filter { it.isFile }
                .forEach { file ->
                    files.add(file.toFileInfo())
                }
        } catch (_: Exception) {
        }

        files.sortedByDescending { it.size }.take(limit)
    }

    private fun getFileHash(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            file.name + "_" + file.length()
        }
    }

    suspend fun findDuplicates(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory()
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        
        try {
            root.walkTopDown()
                .onEnter { dir -> !dir.name.startsWith(".") && dir.name != "Android" } // Skip Android & hidden
                .filter { it.isFile && it.length() > 1024 * 20 } // Files > 20KB (captures almost all photos)
                .forEach { file ->
                    val size = file.length()
                    sizeMap.getOrPut(size) { mutableListOf() }.add(file)
                }
        } catch (_: Exception) {}

        val duplicates = mutableListOf<DuplicateGroup>()
        sizeMap.filter { it.value.size > 1 }.forEach { (size, files) ->
            // Group files by MD5 hash
            val hashGroups = files.groupBy { getFileHash(it) }
            hashGroups.filter { it.value.size > 1 }.forEach { (_, duplicateFiles) ->
                duplicates.add(DuplicateGroup(
                    size = size,
                    name = duplicateFiles.first().name,
                    files = duplicateFiles.map { it.toFileInfo() }
                ))
            }
        }
        duplicates.sortedByDescending { it.size * (it.files.size - 1) }.take(20) // Top 20 groups
    }

    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        var size = 0L
        try {
            context.cacheDir?.let { size += calculateDirSize(it) }
            context.externalCacheDir?.let { size += calculateDirSize(it) }
        } catch (_: Exception) {
        }
        size
    }

    suspend fun getTempFilesSize(): Long = withContext(Dispatchers.IO) {
        var size = 0L
        val tempExtensions = setOf("tmp", "temp", "bak", "log")
        val root = Environment.getExternalStorageDirectory()

        try {
            root.walkTopDown()
                .onEnter { dir -> !dir.name.startsWith(".") }
                .filter { it.isFile && it.extension.lowercase() in tempExtensions }
                .forEach { file ->
                    size += file.length()
                }
        } catch (_: Exception) {
        }
        size
    }

    private fun categorizeExtension(ext: String): String {
        return when (ext) {
            in imageExtensions -> "Images"
            in videoExtensions -> "Videos"
            in audioExtensions -> "Audio"
            in documentExtensions -> "Documents"
            in apkExtensions -> "APKs"
            else -> "Others"
        }
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        try {
            dir.walkTopDown().filter { it.isFile }.forEach { size += it.length() }
        } catch (_: Exception) {
        }
        return size
    }

    private fun File.toFileInfo(): FileInfo {
        return FileInfo(
            name = name,
            path = absolutePath,
            size = length(),
            lastModified = lastModified(),
            isDirectory = isDirectory,
            extension = extension.lowercase()
        )
    }

    private class MutableCategoryData(val icon: String) {
        var fileCount: Int = 0
        var totalSize: Long = 0L
    }
}
