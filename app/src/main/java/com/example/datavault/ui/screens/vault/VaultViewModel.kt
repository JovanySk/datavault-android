package com.example.datavault.ui.screens.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datavault.data.local.AppDatabase
import com.example.datavault.data.local.entity.VaultFile
import com.example.datavault.data.security.VaultOperations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class VaultUiState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val vaultFiles: List<VaultFile> = emptyList(),
    val error: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val vaultDao = AppDatabase.getDatabase(application).vaultFileDao()
    val vaultOperations = VaultOperations(application)

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        loadVaultFiles()
    }

    fun setAuthenticated(auth: Boolean) {
        _uiState.value = _uiState.value.copy(isAuthenticated = auth)
    }

    private fun loadVaultFiles() {
        viewModelScope.launch {
            vaultDao.getAllVaultFiles()
                .catch { e -> _uiState.value = _uiState.value.copy(error = e.message) }
                .collect { files ->
                    _uiState.value = _uiState.value.copy(vaultFiles = files)
                }
        }
    }

    fun restoreFile(vaultFile: VaultFile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val success = vaultOperations.restoreFromVault(vaultFile)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = if (!success) "Error al restaurar archivo" else null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clear decrypted temp files when leaving the vault
        vaultOperations.clearTempFiles()
    }
}
