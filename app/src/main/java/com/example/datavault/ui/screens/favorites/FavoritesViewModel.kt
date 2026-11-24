package com.example.datavault.ui.screens.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datavault.DataVaultApp
import com.example.datavault.data.local.entity.FileBookmark
import com.example.datavault.data.storage.FileInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as DataVaultApp).database
    private val bookmarkDao = database.fileBookmarkDao()
    private val actionDao = database.fileActionDao()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    val bookmarks = bookmarkDao.getAllBookmarks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val history = actionDao.getAllActions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setTab(index: Int) {
        _selectedTab.value = index
    }

    fun addBookmark(fileInfo: FileInfo, note: String = "") {
        viewModelScope.launch {
            bookmarkDao.insert(
                FileBookmark(
                    filePath = fileInfo.path,
                    fileName = fileInfo.name,
                    fileSize = fileInfo.size,
                    fileType = fileInfo.extension,
                    note = note
                )
            )
        }
    }

    fun removeBookmark(path: String) {
        viewModelScope.launch {
            bookmarkDao.deleteByPath(path)
        }
    }

    fun deleteBookmark(bookmark: FileBookmark) {
        viewModelScope.launch {
            bookmarkDao.delete(bookmark)
        }
    }

    fun updateNote(id: Int, note: String) {
        viewModelScope.launch {
            val bookmark = bookmarkDao.getBookmarkById(id)
            if (bookmark != null) {
                bookmarkDao.update(bookmark.copy(note = note))
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            actionDao.deleteAll()
        }
    }
}
