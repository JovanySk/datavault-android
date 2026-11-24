package com.example.datavault.ui.screens.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.datavault.data.ai.GeminiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

data class FileAIUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val fileName: String = "",
    val filePath: String = "",
    val mimeType: String = "",
    val fileSize: Long = 0L,
    val isGeneralMode: Boolean = false
)

class FileAIViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FileAIUiState())
    val uiState: StateFlow<FileAIUiState> = _uiState

    fun loadFile(filePath: String, mimeType: String) {
        val file = File(filePath)
        if (file.isDirectory || mimeType == "directory") {
            _uiState.update {
                it.copy(
                    fileName = file.name.ifEmpty { "Carpeta Actual" },
                    filePath = filePath,
                    mimeType = "directory",
                    isGeneralMode = true
                )
            }
            _uiState.update {
                it.copy(
                    messages = listOf(ChatMessage("Sugiere cómo organizar los archivos en esta carpeta.", isUser = true)),
                    isLoading = true
                )
            }
            viewModelScope.launch {
                val fileList = file.listFiles()?.map { it.name + if (it.isDirectory) " (Carpeta)" else "" } ?: emptyList()
                if (fileList.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage("No hay archivos en esta carpeta para organizar.", isUser = false),
                            isLoading = false
                        )
                    }
                    return@launch
                }
                val gemini = GeminiRepository()
                val response = try {
                    gemini.suggestOrganization(fileList)
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(response, isUser = false),
                        isLoading = false
                    )
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    fileName = file.name,
                    filePath = filePath,
                    mimeType = mimeType,
                    fileSize = file.length(),
                    isGeneralMode = false
                )
            }
            sendMessage("Analiza brevemente este archivo y dime qué contiene.")
        }
    }

    fun sendMessage(userText: String) {
        viewModelScope.launch {
            val gemini = GeminiRepository()
            val state = _uiState.value

            // Add user message
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(userText, isUser = true),
                    isLoading = true
                )
            }

            val modifiedDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(Date(File(state.filePath).lastModified()))

            val response = try {
                if (state.isGeneralMode) {
                    gemini.chat(userText, "Gestión de carpeta: ${state.fileName}")
                } else {
                    gemini.analyzeFile(
                        fileName = state.fileName,
                        filePath = state.filePath,
                        mimeType = state.mimeType,
                        fileSize = state.fileSize,
                        modifiedDate = modifiedDate,
                        userQuestion = userText
                    )
                }
            } catch (e: Exception) {
                "Error al conectar con Gemini: ${e.message}"
            }

            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(response, isUser = false),
                    isLoading = false
                )
            }
        }
    }

    fun suggestRename() {
        viewModelScope.launch {
            val gemini = GeminiRepository()
            val state = _uiState.value

            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage("Sugiere un nombre mejor para este archivo.", isUser = true),
                    isLoading = true
                )
            }

            val response = try {
                gemini.suggestRename(state.fileName, state.filePath, state.mimeType)
            } catch (e: Exception) {
                "Error: ${e.message}"
            }

            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage("Nombre sugerido: **$response**", isUser = false),
                    isLoading = false
                )
            }
        }
    }
}
