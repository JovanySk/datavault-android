package com.example.datavault.ui.screens.explorer

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datavault.DataVaultApp
import com.example.datavault.data.local.entity.FileAction
import com.example.datavault.data.preferences.UserPreferencesRepository
import com.example.datavault.data.storage.FileInfo
import com.example.datavault.data.storage.FileOperations
import com.example.datavault.data.storage.StorageAnalyzer
import com.example.datavault.data.security.VaultOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.datavault.data.local.entity.FileBookmark

class ExplorerViewModel(application: Application) : AndroidViewModel(application) {

    private val fileOps = FileOperations()
    private val storageAnalyzer = StorageAnalyzer(application)
    private val prefsRepo = UserPreferencesRepository(application)
    private val database = (application as DataVaultApp).database
    private val actionDao = database.fileActionDao()
    private val vaultOps = VaultOperations(application)

    private var searchJob: kotlinx.coroutines.Job? = null

    private val rootPath = Environment.getExternalStorageDirectory().absolutePath

    private val _currentPath = MutableStateFlow(rootPath)
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _files = MutableStateFlow<List<FileInfo>>(emptyList())
    val files: StateFlow<List<FileInfo>> = _files.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _clipboardFiles = MutableStateFlow<List<String>>(emptyList())
    val clipboardFiles: StateFlow<List<String>> = _clipboardFiles.asStateFlow()

    private val _clipboardAction = MutableStateFlow("")
    val clipboardAction: StateFlow<String> = _clipboardAction.asStateFlow()

    val viewType = prefsRepo.viewType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "list")

    val sortBy = prefsRepo.sortBy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "name")

    val sortAscending = prefsRepo.sortAscending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        refresh()
    }

    fun navigateTo(path: String) {
        _currentPath.value = path
        clearSelection()
        _searchQuery.value = ""
        refresh()
    }

    fun navigateUp(): Boolean {
        val current = _currentPath.value
        if (current == rootPath) return false
        val parent = current.substringBeforeLast('/', rootPath)
        navigateTo(if (parent.isEmpty()) rootPath else parent)
        return true
    }

    fun toggleSelection(path: String) {
        val current = _selectedFiles.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _selectedFiles.value = current
        _isSelectionMode.value = current.isNotEmpty()
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
        _isSelectionMode.value = false
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val paths = _selectedFiles.value.toList()
            paths.forEach { path ->
                val success = fileOps.deleteFile(path)
                if (success) {
                    val name = path.substringAfterLast('/')
                    actionDao.insert(
                        FileAction(
                            actionType = "delete",
                            filePath = path,
                            fileName = name,
                            details = "Deleted from explorer"
                        )
                    )
                }
            }
            clearSelection()
            refresh()
        }
    }

    fun setClipboard(action: String) {
        _clipboardFiles.value = _selectedFiles.value.toList()
        _clipboardAction.value = action
        clearSelection()
    }

    fun clearClipboard() {
        _clipboardFiles.value = emptyList()
        _clipboardAction.value = ""
    }

    fun pasteClipboard() {
        viewModelScope.launch {
            _isLoading.value = true
            val action = _clipboardAction.value
            val destDir = _currentPath.value
            _clipboardFiles.value.forEach { sourcePath ->
                val fileName = sourcePath.substringAfterLast('/')
                val destPath = "$destDir/$fileName"
                if (action == "copy") {
                    fileOps.copyFile(sourcePath, destPath)
                } else if (action == "move") {
                    fileOps.moveFile(sourcePath, destPath)
                }
            }
            if (action == "move") {
                clearClipboard()
            }
            refresh()
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            fileOps.renameFile(oldPath, newName)
            refresh()
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

    fun refresh() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                val showHidden = prefsRepo.showHiddenFiles.first()
                val sort = prefsRepo.sortBy.first()
                val ascending = prefsRepo.sortAscending.first()
                val query = _searchQuery.value
                
                val fetchedFiles = if (query.isBlank()) {
                    fileOps.listFiles(_currentPath.value, showHidden, sort, ascending)
                } else {
                    fileOps.searchFiles(_currentPath.value, query, showHidden)
                }
                
                if (isActive) {
                    _files.value = fetchedFiles
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _files.value = emptyList()
                }
            } finally {
                if (isActive) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        refresh()
    }

    fun deleteFiles(paths: List<String>) {
        viewModelScope.launch {
            paths.forEach { fileOps.deleteFile(it) }
            refresh()
        }
    }

    fun moveToVault(paths: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { vaultOps.moveToVault(it) }
            withContext(Dispatchers.Main) {
                refresh()
            }
        }
    }

    fun setSortBy(sort: String) {
        viewModelScope.launch {
            prefsRepo.setSortBy(sort)
            refresh()
        }
    }

    fun setViewType(type: String) {
        viewModelScope.launch {
            prefsRepo.setViewType(type)
        }
    }

    fun toggleSortOrder() {
        viewModelScope.launch {
            val current = prefsRepo.sortAscending.first()
            prefsRepo.setSortAscending(!current)
            refresh()
        }
    }
}
