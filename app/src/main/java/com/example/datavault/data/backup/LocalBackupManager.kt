package com.example.datavault.data.backup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.example.datavault.data.model.AiResult
import com.example.datavault.data.model.FileModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class LocalBackupResult(
    val savedFiles: Int,
    val failedFiles: List<String>,
    val totalSize: Long,
    val backupPath: String
)

class LocalBackupManager(private val context: Context) {

    private fun getUniqueFile(dir: File, baseName: String, extension: String): File {
        var file = File(dir, "$baseName$extension")
        var counter = 1
        while (file.exists()) {
            file = File(dir, "$baseName ($counter)$extension")
            counter++
        }
        return file
    }

    private fun zipDirectory(dir: File, destDir: File): File {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val zipFile = getUniqueFile(destDir, "${dir.name}_backup_$dateStr", ".zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(dir.parentFile!!).path
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return zipFile
    }

    private fun compressImage(file: File, destDir: File): File {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return file
        val destFile = getUniqueFile(destDir, "compressed_${file.nameWithoutExtension}", ".jpg")
        FileOutputStream(destFile).use { out ->
            original.compress(Bitmap.CompressFormat.JPEG, 70, out)
        }
        return destFile
    }

    suspend fun saveFiles(
        files: List<FileModel>,
        compressImages: Boolean,
        onProgress: (current: Int, total: Int, currentFileName: String) -> Unit
    ): AiResult<LocalBackupResult> = withContext(Dispatchers.IO) {
        try {
            val basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath + "/DataVault_Backups"
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val backupDir = File("$basePath/$dateStr")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val failed = mutableListOf<String>()
            var saved = 0
            var totalSaved = 0L

            val total = files.size
            for ((index, fileModel) in files.withIndex()) {
                if (!isActive) throw CancellationException("Cancelled by user")

                onProgress(index + 1, total, fileModel.name)
                val realFile = File(fileModel.absolutePath)
                if (!realFile.exists()) {
                    failed.add(fileModel.name)
                    continue
                }

                try {
                    when {
                        realFile.isDirectory -> {
                            val zip = zipDirectory(realFile, backupDir)
                            totalSaved += zip.length()
                        }
                        compressImages && fileModel.extension in SmartFileSearcher.imageExtensions -> {
                            val compressed = compressImage(realFile, backupDir)
                            if (compressed == realFile) {
                                // Fallback
                                val dest = getUniqueFile(backupDir, realFile.nameWithoutExtension, ".${realFile.extension}")
                                realFile.copyTo(dest)
                                totalSaved += dest.length()
                            } else {
                                totalSaved += compressed.length()
                            }
                        }
                        else -> {
                            val ext = if (realFile.extension.isNotEmpty()) ".${realFile.extension}" else ""
                            val dest = getUniqueFile(backupDir, realFile.nameWithoutExtension, ext)
                            realFile.copyTo(dest)
                            totalSaved += dest.length()
                        }
                    }
                    saved++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed.add(fileModel.name)
                }
            }

            AiResult.Success(
                LocalBackupResult(
                    savedFiles = saved,
                    failedFiles = failed,
                    totalSize = totalSaved,
                    backupPath = backupDir.absolutePath
                )
            )
        } catch (e: CancellationException) {
            AiResult.Error("Copia cancelada por el usuario")
        } catch (e: Exception) {
            AiResult.Error("Error al guardar archivos: ${e.message}")
        }
    }
}
