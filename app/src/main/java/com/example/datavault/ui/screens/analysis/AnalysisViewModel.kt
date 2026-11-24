package com.example.datavault.ui.screens.analysis

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datavault.data.storage.CategoryInfo
import com.example.datavault.data.storage.DuplicateGroup
import com.example.datavault.data.storage.FileInfo
import com.example.datavault.data.storage.StorageAnalyzer
import com.example.datavault.data.storage.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.datavault.DataVaultApp
import com.example.datavault.data.local.entity.FileBookmark
import com.example.datavault.data.storage.FileOperations
import com.example.datavault.data.security.VaultOperations

data class AnalysisUiState(
    val storageInfo: StorageInfo = StorageInfo(0L, 0L, 0L),
    val largestFiles: List<FileInfo> = emptyList(),
    val categoryDistribution: List<CategoryInfo> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val cacheSize: Long = 0L,
    val tempFilesSize: Long = 0L,
    val isScanning: Boolean = true
)

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val storageAnalyzer = StorageAnalyzer(application)
    private val database = (application as DataVaultApp).database
    private val fileOps = FileOperations()
    private val vaultOps = VaultOperations(application)

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            try {
                val storageInfo = storageAnalyzer.getStorageInfo()
                val largestFiles = storageAnalyzer.getLargestFiles(20)
                val scannedCategories = storageAnalyzer.getCategorySizes()
                val scannedSize = scannedCategories.sumOf { it.totalSize }
                val systemSize = (storageInfo.usedBytes - scannedSize).coerceAtLeast(0L)
                val categories = scannedCategories.toMutableList()
                if (systemSize > 0) {
                    categories.add(CategoryInfo("Sistema y Apps", 0, systemSize, "⚙️"))
                }
                val duplicates = storageAnalyzer.findDuplicates()
                val cacheSize = storageAnalyzer.getCacheSize()
                val tempSize = storageAnalyzer.getTempFilesSize()

                _uiState.value = AnalysisUiState(
                    storageInfo = storageInfo,
                    largestFiles = largestFiles,
                    categoryDistribution = categories,
                    duplicateGroups = duplicates,
                    cacheSize = cacheSize,
                    tempFilesSize = tempSize,
                    isScanning = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                app.cacheDir?.deleteRecursively()
                app.externalCacheDir?.deleteRecursively()
                scan()
            } catch (_: Exception) { }
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch {
            try {
                val file = File(path)
                if (file.exists() && file.isFile) {
                    file.delete()
                    scan()
                }
            } catch (_: Exception) { }
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            try {
                fileOps.renameFile(oldPath, newName)
                scan()
            } catch (_: Exception) { }
        }
    }

    fun toggleFavorite(path: String) {
        viewModelScope.launch {
            val bookmarkDao = database.fileBookmarkDao()
            val existing = bookmarkDao.getBookmarkByPath(path)
            if (existing != null) {
                bookmarkDao.delete(existing)
            } else {
                val file = fileOps.getFileProperties(path)
                bookmarkDao.insert(
                    FileBookmark(
                        filePath = file.path,
                        fileName = file.name,
                        fileSize = file.size,
                        fileType = fileOps.getMimeType(file.path) ?: "unknown"
                    )
                )
            }
        }
    }

    fun moveToVault(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            vaultOps.moveToVault(path)
            withContext(Dispatchers.Main) {
                scan()
            }
        }
    }

    private fun File.deleteRecursively() {
        if (isDirectory) {
            listFiles()?.forEach { it.deleteRecursively() }
        }
        delete()
    }
}
