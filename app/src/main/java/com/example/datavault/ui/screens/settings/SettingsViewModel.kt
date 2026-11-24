package com.example.datavault.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datavault.data.preferences.UserPreferencesRepository
import com.example.datavault.DataVaultApp
import com.example.datavault.data.backup.DriveBackupFile
import com.example.datavault.data.backup.DriveBackupManager
import com.example.datavault.data.backup.GoogleAuthManager
import com.example.datavault.data.model.AiResult
import com.example.datavault.data.model.FileModel
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class BackupUiState {
    object Idle : BackupUiState()
    object Loading : BackupUiState()
    data class Success(val message: String, val link: String? = null) : BackupUiState()
    data class Error(val error: String) : BackupUiState()
    data class BackupList(val list: List<DriveBackupFile>) : BackupUiState()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferencesRepository(application)
    private val database = (application as DataVaultApp).database
    
    private val authManager = GoogleAuthManager(application)
    private val backupManager = DriveBackupManager(application)

    private val _connectedAccount = MutableStateFlow<GoogleSignInAccount?>(authManager.getSignedInAccount())
    val connectedAccount: StateFlow<GoogleSignInAccount?> = _connectedAccount.asStateFlow()

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    val themeMode = prefs.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val viewType = prefs.viewType.stateIn(viewModelScope, SharingStarted.Eagerly, "list")
    val sortBy = prefs.sortBy.stateIn(viewModelScope, SharingStarted.Eagerly, "name")
    val sortAscending = prefs.sortAscending.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val showHiddenFiles = prefs.showHiddenFiles.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setThemeMode(mode: String) { viewModelScope.launch { prefs.setThemeMode(mode) } }
    fun setViewType(type: String) { viewModelScope.launch { prefs.setViewType(type) } }
    fun setSortBy(sort: String) { viewModelScope.launch { prefs.setSortBy(sort) } }
    fun setSortAscending(asc: Boolean) { viewModelScope.launch { prefs.setSortAscending(asc) } }
    fun setShowHiddenFiles(show: Boolean) { viewModelScope.launch { prefs.setShowHiddenFiles(show) } }

    fun handleSignInResult(data: Intent?) {
        _connectedAccount.value = authManager.handleSignInResult(data)
    }

    fun getSignInIntent(): Intent = authManager.getSignInIntent()

    fun signOut(onComplete: () -> Unit = {}) {
        authManager.signOut {
            _connectedAccount.value = null
            onComplete()
        }
    }

    fun resetBackupState() {
        _backupState.value = BackupUiState.Idle
    }

    private fun getAllFilesToBackup(context: Context): List<FileModel> {
        val filesToBackup = mutableListOf<File>()

        // 1. Database
        val dbFile = context.getDatabasePath("datavault_database")
        if (dbFile.exists()) filesToBackup.add(dbFile)
        val dbWal = context.getDatabasePath("datavault_database-wal")
        if (dbWal.exists()) filesToBackup.add(dbWal)
        val dbShm = context.getDatabasePath("datavault_database-shm")
        if (dbShm.exists()) filesToBackup.add(dbShm)

        // 2. Vault Files
        val vaultDir = File(context.filesDir, "vault")
        if (vaultDir.exists()) {
            filesToBackup.addAll(vaultDir.walkTopDown().filter { it.isFile })
        }

        // 3. Datastore Preferences
        val prefsDir = File(context.filesDir.parent, "datastore")
        if (prefsDir.exists()) {
            filesToBackup.addAll(prefsDir.walkTopDown().filter { it.isFile })
        }

        // Si la lista está vacía y queremos que permita crear un backup vacío (por ejemplo recién instalada)
        // en DriveBackupManager dará error de "No hay archivos". Así que retornamos la lista mapeada.
        return filesToBackup.map { file -> 
            FileModel(
                name = file.name,
                path = file.path,
                absolutePath = file.absolutePath,
                mimeType = "application/octet-stream",
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                extension = file.extension
            )
        }
    }

    fun createDriveBackup(context: Context) {
        val account = _connectedAccount.value
        if (account == null) {
            _backupState.value = BackupUiState.Error("No hay cuenta conectada")
            return
        }

        _backupState.value = BackupUiState.Loading
        viewModelScope.launch {
            val files = getAllFilesToBackup(context)
            when (val result = backupManager.uploadBackup(files, account)) {
                is AiResult.Success -> {
                    _backupState.value = BackupUiState.Success("Copia subida exitosamente", result.data)
                }
                is AiResult.Error -> {
                    _backupState.value = BackupUiState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    fun uploadLocalZipToDrive(context: Context, uri: Uri) {
        val account = _connectedAccount.value
        if (account == null) {
            _backupState.value = BackupUiState.Error("No hay cuenta conectada")
            return
        }

        _backupState.value = BackupUiState.Loading
        viewModelScope.launch {
            try {
                // Copiamos el archivo del Uri a la caché para subirlo
                val tempFile = File(context.cacheDir, "temp_upload.zip")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                when (val result = backupManager.uploadSingleZip(tempFile, account)) {
                    is AiResult.Success -> {
                        _backupState.value = BackupUiState.Success("Copia local subida exitosamente", result.data)
                    }
                    is AiResult.Error -> {
                        _backupState.value = BackupUiState.Error(result.message)
                    }
                    else -> {}
                }
                
                tempFile.delete() // Limpiar caché
            } catch (e: Exception) {
                e.printStackTrace()
                _backupState.value = BackupUiState.Error("Error al leer el archivo ZIP: ${e.message}")
            }
        }
    }

    fun loadDriveBackups() {
        val account = _connectedAccount.value
        if (account == null) {
            _backupState.value = BackupUiState.Error("No hay cuenta conectada")
            return
        }

        _backupState.value = BackupUiState.Loading
        viewModelScope.launch {
            when (val result = backupManager.listBackups(account)) {
                is AiResult.Success -> {
                    _backupState.value = BackupUiState.BackupList(result.data)
                }
                is AiResult.Error -> {
                    _backupState.value = BackupUiState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    fun deleteBackup(fileId: String) {
        val account = _connectedAccount.value ?: return
        viewModelScope.launch {
            _backupState.value = BackupUiState.Loading
            when (val result = backupManager.deleteBackup(fileId, account)) {
                is AiResult.Success -> {
                    // Reload list
                    loadDriveBackups()
                }
                is AiResult.Error -> {
                    _backupState.value = BackupUiState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    fun createLocalBackup(context: Context) {
        _backupState.value = BackupUiState.Loading
        viewModelScope.launch {
            try {
                val files = getAllFilesToBackup(context)
                if (files.isEmpty()) {
                    _backupState.value = BackupUiState.Error("No hay archivos para respaldar")
                    return@launch
                }

                val exportDir = File(Environment.getExternalStorageDirectory(), "DataVault/Backups")
                if (!exportDir.exists()) exportDir.mkdirs()

                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val zipFile = File(exportDir, "backup_$timeStamp.zip")

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
                _backupState.value = BackupUiState.Success("Copia local creada en: ${zipFile.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
                _backupState.value = BackupUiState.Error("Error al crear copia local: ${e.message}")
            }
        }
    }

    fun exportData() {
        // Redirigimos exportData anterior a createLocalBackup
        // o mantenemos la función anterior si queremos (no fue solicitada para borrar)
    }

    fun clearAllData() {
        viewModelScope.launch {
            database.fileActionDao().deleteAll()
            database.fileBookmarkDao().deleteAll()
        }
    }
}
