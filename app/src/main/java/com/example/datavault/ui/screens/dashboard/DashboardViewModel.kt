package com.example.datavault.ui.screens.dashboard

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datavault.DataVaultApp
import com.example.datavault.data.local.entity.FileAction
import com.example.datavault.data.storage.CategoryInfo
import com.example.datavault.data.storage.StorageAnalyzer
import com.example.datavault.data.storage.StorageInfo
import com.example.datavault.theme.CategoryApks
import com.example.datavault.theme.CategoryAudio
import com.example.datavault.theme.CategoryDocuments
import com.example.datavault.theme.CategoryImages
import com.example.datavault.theme.CategoryOthers
import com.example.datavault.theme.CategoryVideos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DashboardUiState(
    val storageInfo: StorageInfo = StorageInfo(0L, 0L, 0L),
    val categories: List<CategoryInfo> = emptyList(),
    val recentActions: List<FileAction> = emptyList(),
    val bookmarkCount: Int = 0,
    val isLoading: Boolean = true
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val storageAnalyzer = StorageAnalyzer(application)
    private val database = (application as DataVaultApp).database
    private val bookmarkDao = database.fileBookmarkDao()
    private val actionDao = database.fileActionDao()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val storageInfo = storageAnalyzer.getStorageInfo()
                val scannedCategories = storageAnalyzer.getCategorySizes()
                val scannedSize = scannedCategories.sumOf { it.totalSize }
                val systemSize = (storageInfo.usedBytes - scannedSize).coerceAtLeast(0L)
                
                val categories = scannedCategories.toMutableList()
                if (systemSize > 0) {
                    categories.add(CategoryInfo("Sistema y Apps", 0, systemSize, "⚙️"))
                }
                
                val recentActions = actionDao.getRecentActions(5).first()
                val bookmarkCount = bookmarkDao.getCount().first()

                _uiState.value = DashboardUiState(
                    storageInfo = storageInfo,
                    categories = categories,
                    recentActions = recentActions,
                    bookmarkCount = bookmarkCount,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    companion object {
        fun categoryColor(name: String): Color = when (name) {
            "Images" -> CategoryImages
            "Videos" -> CategoryVideos
            "Audio" -> CategoryAudio
            "Documents" -> CategoryDocuments
            "APKs" -> CategoryApks
            "Sistema y Apps" -> Color.LightGray
            else -> CategoryOthers
        }

        fun categoryIcon(name: String): ImageVector = when (name) {
            "Images" -> Icons.Filled.Image
            "Videos" -> Icons.Filled.VideoFile
            "Audio" -> Icons.Filled.AudioFile
            "Documents" -> Icons.Filled.Description
            "APKs" -> Icons.Filled.Android
            "Sistema y Apps" -> Icons.Filled.Android // Fallback, could use Settings
            else -> Icons.Filled.Folder
        }
    }
}
