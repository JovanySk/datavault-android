package com.example.datavault.data.backup

import android.os.Environment
import android.webkit.MimeTypeMap
import com.example.datavault.data.model.FileModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

enum class FileType {
    ALL, DOCS, IMAGES, FOLDERS, AUDIO, VIDEO, OTHERS
}

object SmartFileSearcher {

    private val ignoredDirs = setOf(
        "Android/data", "Android/obb", ".thumbnails", ".cache",
        ".thumbnail", "LOST.DIR"
    )

    fun search(query: String, filterType: FileType = FileType.ALL): Flow<List<FileModel>> = flow {
        val root = Environment.getExternalStorageDirectory()
        val results = mutableListOf<FileModel>()
        val lowerQuery = query.trim().lowercase()

        try {
            root.walkTopDown()
                .onEnter { dir ->
                    val relativePath = dir.absolutePath.removePrefix(root.absolutePath)
                    ignoredDirs.none { ignored -> relativePath.contains(ignored, ignoreCase = true) }
                }
                .forEach { file ->
                    if (lowerQuery.isNotEmpty() && !file.name.lowercase().contains(lowerQuery)) return@forEach

                    val ext = file.extension.lowercase()
                    val passesFilter = when (filterType) {
                        FileType.ALL -> true
                        FileType.DOCS -> ext in documentExtensions
                        FileType.IMAGES -> ext in imageExtensions
                        FileType.FOLDERS -> file.isDirectory
                        FileType.AUDIO -> ext in audioExtensions
                        FileType.VIDEO -> ext in videoExtensions
                        FileType.OTHERS -> !file.isDirectory &&
                                ext !in documentExtensions &&
                                ext !in imageExtensions &&
                                ext !in audioExtensions &&
                                ext !in videoExtensions
                    }
                    if (!passesFilter) return@forEach

                    results.add(file.toFileModel())

                    // Emit progressively every 20 items
                    if (results.size % 20 == 0) {
                        emit(results.sortedWith(fileComparator(lowerQuery)))
                    }
                }
        } catch (_: Exception) {}

        emit(results.sortedWith(fileComparator(lowerQuery)))
    }.flowOn(Dispatchers.IO)

    private fun fileComparator(query: String) = compareBy<FileModel>(
        { !it.isDirectory },                          // Folders first
        { !it.name.lowercase().startsWith(query) },   // Starts-with match first
        { it.name.lowercase() }                       // Alphabetical
    )

    private fun File.toFileModel(): FileModel {
        val ext = extension.lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        return FileModel(
            name = name,
            path = path,
            absolutePath = absolutePath,
            mimeType = mimeType,
            size = if (isFile) length() else 0L,
            lastModified = lastModified(),
            isDirectory = isDirectory,
            extension = ext
        )
    }

    val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "svg", "tiff", "raw")
    val videoExtensions = setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp", "webm", "m4v")
    val audioExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "amr")
    val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "rtf", "odt", "ods")

    // Special/unknown formats that warrant a warning
    val specialFormats = mapOf(
        "psd" to "Adobe Photoshop",
        "psb" to "Adobe Photoshop",
        "ai" to "Adobe Illustrator",
        "eps" to "Adobe Illustrator",
        "indd" to "Adobe InDesign",
        "indt" to "Adobe InDesign",
        "prproj" to "Adobe Premiere",
        "aep" to "Adobe After Effects",
        "xd" to "Adobe XD",
        "blend" to "Blender",
        "clip" to "Clip Studio Paint",
        "csp" to "Clip Studio Paint",
        "fig" to "Figma",
        "sketch" to "Sketch",
        "json" to "Archivo de datos",
        "xml" to "Archivo de datos",
        "db" to "Base de datos",
        "sqlite" to "Base de datos",
        "sqlite3" to "Base de datos"
    )

    fun isSpecialFormat(file: FileModel): Boolean {
        if (file.isDirectory) return false
        if (file.extension.isEmpty()) return true  // No extension = unknown
        return file.extension in specialFormats
    }

    fun getSpecialFormatLabel(file: FileModel): String {
        if (file.extension.isEmpty()) return "Formato desconocido"
        return specialFormats[file.extension] ?: "Formato especial"
    }
}
