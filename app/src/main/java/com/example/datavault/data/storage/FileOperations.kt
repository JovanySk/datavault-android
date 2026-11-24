package com.example.datavault.data.storage

import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File

class FileOperations {

    suspend fun listFiles(
        path: String,
        showHidden: Boolean,
        sortBy: String,
        ascending: Boolean
    ): List<FileInfo> = withContext(Dispatchers.IO) {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()

        val files = directory.listFiles()?.filter { file ->
            showHidden || !file.name.startsWith(".")
        }?.map { it.toFileInfo() } ?: emptyList()

        val sorted = when (sortBy) {
            "name" -> files.sortedBy { it.name.lowercase() }
            "size" -> files.sortedBy { it.size }
            "date" -> files.sortedBy { it.lastModified }
            "type" -> files.sortedBy { it.extension.lowercase() }
            else -> files.sortedBy { it.name.lowercase() }
        }

        val directoriesFirst = sorted.sortedByDescending { it.isDirectory }

        if (ascending) directoriesFirst else directoriesFirst.reversed()
    }

    suspend fun searchFiles(
        path: String,
        query: String,
        showHidden: Boolean
    ): List<FileInfo> = withContext(Dispatchers.IO) {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) return@withContext emptyList()
        val results = mutableListOf<FileInfo>()
        try {
            root.walkTopDown()
                .onEnter { isActive && (showHidden || !it.name.startsWith(".")) }
                .filter { showHidden || !it.name.startsWith(".") }
                .filter { it.name.contains(query, ignoreCase = true) }
                .forEach { 
                    if (!isActive) return@forEach
                    results.add(it.toFileInfo()) 
                }
        } catch (_: Exception) {
        }
        results
    }

    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun renameFile(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(oldPath)
            val dest = File(source.parentFile, newName)
            if (dest.exists()) return@withContext false
            source.renameTo(dest)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun copyFile(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val dest = File(destPath)
            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = false)
            } else {
                source.copyTo(dest, overwrite = false)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun moveFile(sourcePath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val copied = copyFile(sourcePath, destPath)
            if (copied) {
                val source = File(sourcePath)
                if (source.isDirectory) source.deleteRecursively() else source.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getFileProperties(path: String): FileInfo = withContext(Dispatchers.IO) {
        File(path).toFileInfo()
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1000 -> "$bytes B"
            bytes < 1000 * 1000 -> "%.1f KB".format(bytes / 1000.0)
            bytes < 1000 * 1000 * 1000 -> "%.1f MB".format(bytes / (1000.0 * 1000))
            else -> "%.2f GB".format(bytes / (1000.0 * 1000 * 1000))
        }
    }

    fun getFileExtension(path: String): String {
        return File(path).extension.lowercase()
    }

    fun getMimeType(path: String): String? {
        val extension = getFileExtension(path)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun File.toFileInfo(): FileInfo {
        return FileInfo(
            name = name,
            path = absolutePath,
            size = if (isFile) length() else 0L,
            lastModified = lastModified(),
            isDirectory = isDirectory,
            extension = extension.lowercase()
        )
    }
}
