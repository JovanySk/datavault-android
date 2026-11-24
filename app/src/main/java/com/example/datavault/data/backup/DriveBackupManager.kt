package com.example.datavault.data.backup

import android.content.Context
import com.example.datavault.data.model.AiResult
import com.example.datavault.data.model.FileModel
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DriveBackupFile(
    val id: String,
    val name: String,
    val size: Long,
    val createdTime: String,
    val webViewLink: String,
    val description: String = ""
)

class DriveBackupManager(private val context: Context) {

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
        .setApplicationName("DataVault Backup")
        .build()
    }

    private fun createZipFile(files: List<FileModel>): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipFileName = "backup_$timeStamp.zip"
        val zipFile = File(context.cacheDir, zipFileName)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for (file in files) {
                val realFile = File(file.absolutePath)
                if (!realFile.exists() || !realFile.isFile) continue
                
                val entry = ZipEntry(realFile.name)
                zos.putNextEntry(entry)
                FileInputStream(realFile).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
        return zipFile
    }

    private fun getOrCreateBackupFolder(drive: Drive): String? {
        val folderName = "DataVault Backups"
        val query = "mimeType='application/vnd.google-apps.folder' and name='$folderName' and trashed=false"
        val fileList = drive.files().list().setQ(query).setSpaces("drive").execute()

        if (fileList.files.isNotEmpty()) {
            return fileList.files[0].id
        }

        val folderMetadata = com.google.api.services.drive.model.File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
        }

        val folder = drive.files().create(folderMetadata).setFields("id").execute()
        return folder.id
    }

    suspend fun uploadBackup(files: List<FileModel>, account: GoogleSignInAccount): AiResult<String> = withContext(Dispatchers.IO) {
        try {
            if (files.isEmpty()) {
                return@withContext AiResult.Error("No hay archivos para respaldar")
            }

            val driveService = getDriveService(account)
            val folderId = getOrCreateBackupFolder(driveService)
                ?: return@withContext AiResult.Error("No se pudo crear o encontrar la carpeta en Drive")

            val zipFile = createZipFile(files)

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = zipFile.name
                parents = listOf(folderId)
            }

            val mediaContent = FileContent("application/zip", zipFile)
            val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute()

            zipFile.delete() // Cleanup local cache

            AiResult.Success(uploadedFile.webViewLink)
        } catch (e: Exception) {
            e.printStackTrace()
            AiResult.Error("Error al subir copia de seguridad: ${e.message}")
        }
    }

    suspend fun listBackups(account: GoogleSignInAccount): AiResult<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(account)
            val folderId = getOrCreateBackupFolder(driveService)
                ?: return@withContext AiResult.Error("No se pudo encontrar la carpeta de backups")

            val query = "'$folderId' in parents and trashed=false"
            val result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name, size, createdTime, webViewLink, description)")
                .execute()

            val backups = result.files.map { file ->
                DriveBackupFile(
                    id = file.id,
                    name = file.name,
                    size = file.getSize()?.toLong() ?: 0L,
                    createdTime = file.createdTime?.toString() ?: "",
                    webViewLink = file.webViewLink ?: "",
                    description = file.description ?: ""
                )
            }.sortedByDescending { it.createdTime }

            AiResult.Success(backups)
        } catch (e: Exception) {
            e.printStackTrace()
            AiResult.Error("Error al listar backups: ${e.message}")
        }
    }

    suspend fun deleteBackup(fileId: String, account: GoogleSignInAccount): AiResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val driveService = getDriveService(account)
            driveService.files().delete(fileId).execute()
            AiResult.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            AiResult.Error("Error al eliminar backup: ${e.message}")
        }
    }

    suspend fun uploadSingleZip(zipFile: File, account: GoogleSignInAccount): AiResult<String> = withContext(Dispatchers.IO) {
        try {
            if (!zipFile.exists() || !zipFile.isFile) {
                return@withContext AiResult.Error("El archivo no es válido")
            }

            val driveService = getDriveService(account)
            val folderId = getOrCreateBackupFolder(driveService)
                ?: return@withContext AiResult.Error("No se pudo crear o encontrar la carpeta en Drive")

            val sizeMb = zipFile.length() / (1000.0 * 1000.0)
            val sizeFormatted = String.format(Locale.getDefault(), "%.2f MB", sizeMb)
            val desc = "Copia Local | Tamaño total: $sizeFormatted"

            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = zipFile.name
                parents = listOf(folderId)
                description = desc
            }

            val mediaContent = FileContent("application/zip", zipFile)
            val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, webViewLink")
                .execute()

            AiResult.Success(uploadedFile.webViewLink)
        } catch (e: Exception) {
            e.printStackTrace()
            AiResult.Error("Error al subir archivo ZIP: ${e.message}")
        }
    }
}
