package com.example.datavault.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datavault.data.backup.BackupResult
import com.example.datavault.data.backup.DriveUploadManager
import com.example.datavault.data.backup.FileType
import com.example.datavault.data.backup.SmartFileSearcher
import com.example.datavault.data.model.AiResult
import com.example.datavault.data.model.FileModel
import com.example.datavault.data.backup.LocalBackupManager
import com.example.datavault.data.backup.LocalBackupResult
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BackupDestination { DRIVE, LOCAL }

data class BackupAnalysis(
    val totalSize: Long = 0L,
    val hasImages: Boolean = false,
    val hasFolders: Boolean = false,
    val specialFiles: List<FileModel> = emptyList(),
    val estimatedZipSize: Long = 0L,
    val folderFiles: List<FileModel> = emptyList(),
    val looseFiles: List<FileModel> = emptyList()
)

sealed class UploadProgress {
    object Idle : UploadProgress()
    data class Uploading(
        val current: Int,
        val total: Int,
        val currentFileName: String
    ) : UploadProgress()
    data class Done(val result: BackupResult) : UploadProgress()
    data class LocalDone(val result: LocalBackupResult) : UploadProgress()
    data class Failed(val message: String) : UploadProgress()
    object Cancelled : UploadProgress()
}

class BackupSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val uploadManager = DriveUploadManager(application)
    private val localBackupManager = LocalBackupManager(application)
    private var searchJob: Job? = null
    private var uploadJob: Job? = null

    private val _searchResults = MutableStateFlow<List<FileModel>>(emptyList())
    val searchResults: StateFlow<List<FileModel>> = _searchResults.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<FileModel>>(emptySet())
    val selectedFiles: StateFlow<Set<FileModel>> = _selectedFiles.asStateFlow()

    private val _analysis = MutableStateFlow(BackupAnalysis())
    val analysis: StateFlow<BackupAnalysis> = _analysis.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _activeFilter = MutableStateFlow(FileType.ALL)
    val activeFilter: StateFlow<FileType> = _activeFilter.asStateFlow()

    private val _uploadProgress = MutableStateFlow<UploadProgress>(UploadProgress.Idle)
    val uploadProgress: StateFlow<UploadProgress> = _uploadProgress.asStateFlow()

    private var currentQuery = ""

    fun search(query: String) {
        currentQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _isSearching.value = true
            _searchResults.value = emptyList()
            try {
                SmartFileSearcher.search(query, _activeFilter.value).collect { results ->
                    _searchResults.value = results
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun filterByType(type: FileType) {
        _activeFilter.value = type
        search(currentQuery)
    }

    fun toggleSelection(file: FileModel) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(file)) current.remove(file) else current.add(file)
        _selectedFiles.value = current
        recomputeAnalysis()
    }

    fun selectAll() {
        _selectedFiles.value = _searchResults.value.toSet()
        recomputeAnalysis()
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
        recomputeAnalysis()
    }

    private fun recomputeAnalysis() {
        val files = _selectedFiles.value.toList()
        val folders = files.filter { it.isDirectory }
        val loose = files.filter { !it.isDirectory }
        val special = files.filter { SmartFileSearcher.isSpecialFormat(it) }
        val images = loose.filter { it.extension in SmartFileSearcher.imageExtensions }

        // Rough zip size estimate: 50% of originals for folders, same for others
        val folderSize = folders.sumOf { it.size }
        val looseSize = loose.sumOf { it.size }
        val estimatedZip = (folderSize * 0.5 + looseSize * 0.9).toLong()

        _analysis.value = BackupAnalysis(
            totalSize = files.sumOf { it.size },
            hasImages = images.size > 5,
            hasFolders = folders.isNotEmpty(),
            specialFiles = special,
            estimatedZipSize = estimatedZip,
            folderFiles = folders,
            looseFiles = loose
        )
    }

    fun executeBackup(
        destination: BackupDestination,
        compressImages: Boolean,
        account: GoogleSignInAccount? = null
    ) {
        val files = _selectedFiles.value.toList()
        if (files.isEmpty()) return

        uploadJob = viewModelScope.launch {
            _uploadProgress.value = UploadProgress.Uploading(0, files.size, "Preparando...")
            
            when (destination) {
                BackupDestination.DRIVE -> {
                    if (account == null) {
                        _uploadProgress.value = UploadProgress.Failed("Cuenta no configurada")
                        return@launch
                    }
                    val result = uploadManager.uploadFiles(
                        files = files,
                        compressImages = compressImages,
                        onProgress = { cur, total, name ->
                            _uploadProgress.value = UploadProgress.Uploading(cur, total, name)
                        },
                        account = account
                    )
                    _uploadProgress.value = when (result) {
                        is AiResult.Success -> UploadProgress.Done(result.data)
                        is AiResult.Error -> if (result.message.contains("cancelada"))
                            UploadProgress.Cancelled else UploadProgress.Failed(result.message)
                        else -> UploadProgress.Failed("Error desconocido")
                    }
                }
                BackupDestination.LOCAL -> {
                    val result = localBackupManager.saveFiles(
                        files = files,
                        compressImages = compressImages,
                        onProgress = { cur, total, name ->
                            _uploadProgress.value = UploadProgress.Uploading(cur, total, name)
                        }
                    )
                    _uploadProgress.value = when (result) {
                        is AiResult.Success -> UploadProgress.LocalDone(result.data)
                        is AiResult.Error -> if (result.message.contains("cancelada"))
                            UploadProgress.Cancelled else UploadProgress.Failed(result.message)
                        else -> UploadProgress.Failed("Error desconocido")
                    }
                }
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        _uploadProgress.value = UploadProgress.Cancelled
    }

    fun resetUpload() {
        _uploadProgress.value = UploadProgress.Idle
    }
}
