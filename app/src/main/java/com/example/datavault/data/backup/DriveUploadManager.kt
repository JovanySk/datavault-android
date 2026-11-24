package com.example.datavault.data.backup

import com.example.datavault.data.model.AiResult
import com.example.datavault.data.model.FileModel
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BackupResult(
    val uploadedFiles: Int,
    val failedFiles: List<String>,
    val totalSize: Long,
    val driveLink: String
)

// Extended manager replacing the old DriveBackupManager
class DriveUploadManager(private val context: Context) {

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("DataVault Backup").build()
    }

    private fun getOrCreateFolder(drive: Drive, name: String, parentId: String? = null): String {
        val parentClause = if (parentId != null) " and '$parentId' in parents" else ""
        val query = "mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false$parentClause"
        val existing = drive.files().list().setQ(query).setSpaces("drive").execute()
        if (existing.files.isNotEmpty()) return existing.files[0].id

        val meta = com.google.api.services.drive.model.File().apply {
            this.name = name
            mimeType = "application/vnd.google-apps.folder"
            if (parentId != null) parents = listOf(parentId)
        }
        return drive.files().create(meta).setFields("id").execute().id
    }

    private fun zipDirectory(dir: File): File {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val zipFile = File(context.cacheDir, "${dir.name}_backup_$dateStr.zip")
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

    private fun compressImage(file: File): File {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return file
        val compressed = File(context.cacheDir, "compressed_${file.name}.jpg")
        FileOutputStream(compressed).use { out ->
            original.compress(Bitmap.CompressFormat.JPEG, 70, out)
        }
        return compressed
    }

    suspend fun uploadFiles(
        files: List<FileModel>,
        compressImages: Boolean,
        onProgress: (current: Int, total: Int, currentFileName: String) -> Unit,
        account: GoogleSignInAccount
    ): AiResult<BackupResult> = withContext(Dispatchers.IO) {
        try {
            val drive = getDriveService(account)
            val rootFolderId = getOrCreateFolder(drive, "DataVault Backups")
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dateFolderId = getOrCreateFolder(drive, dateStr, rootFolderId)

            val failed = mutableListOf<String>()
            var uploaded = 0
            var totalUploaded = 0L
            var lastLink = ""

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
                    val (uploadFile, mimeType, cleanup) = when {
                        realFile.isDirectory -> {
                            val zip = zipDirectory(realFile)
                            Triple(zip, "application/zip", true)
                        }
                        compressImages && fileModel.extension in SmartFileSearcher.imageExtensions -> {
                            val compressed = compressImage(realFile)
                            Triple(compressed, "image/jpeg", compressed != realFile)
                        }
                        else -> Triple(realFile, fileModel.mimeType.ifEmpty { "application/octet-stream" }, false)
                    }

                    val meta = com.google.api.services.drive.model.File().apply {
                        name = uploadFile.name
                        parents = listOf(dateFolderId)
                    }
                    val result = drive.files().create(meta, FileContent(mimeType, uploadFile))
                        .setFields("id, webViewLink").execute()

                    lastLink = result.webViewLink ?: ""
                    totalUploaded += uploadFile.length()
                    uploaded++
                    if (cleanup) uploadFile.delete()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed.add(fileModel.name)
                }
            }

            // Update the folder metadata with summary
            val sizeMb = totalUploaded / (1000.0 * 1000.0)
            val sizeFormatted = String.format(Locale.getDefault(), "%.2f MB", sizeMb)
            val desc = "Archivos: $uploaded | Tamaño total: $sizeFormatted"
            val updateMeta = com.google.api.services.drive.model.File().apply {
                description = desc
            }
            drive.files().update(dateFolderId, updateMeta).execute()

            // Get link to date folder
            val folderMeta = drive.files().get(dateFolderId).setFields("webViewLink").execute()
            val folderLink = folderMeta.webViewLink ?: lastLink

            AiResult.Success(
                BackupResult(
                    uploadedFiles = uploaded,
                    failedFiles = failed,
                    totalSize = totalUploaded,
                    driveLink = folderLink
                )
            )
        } catch (e: CancellationException) {
            AiResult.Error("Subida cancelada por el usuario")
        } catch (e: Exception) {
            AiResult.Error("Error al subir archivos: ${e.message}")
        }
    }
}
