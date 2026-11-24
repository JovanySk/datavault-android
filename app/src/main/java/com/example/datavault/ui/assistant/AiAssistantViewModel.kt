package com.example.datavault.ui.assistant

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.datavault.data.ai.AiFileAssistant
import com.example.datavault.data.files.FileOperationsManager
import com.example.datavault.data.model.AiActionPlan
import com.example.datavault.data.model.AiMessage
import com.example.datavault.data.model.AiResult
import com.example.datavault.data.model.ExecutionReport
import com.example.datavault.data.model.FileModel
import com.example.datavault.data.model.MessageRole
import com.example.datavault.data.model.UndoSnapshot
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AiAssistantUiState(
    val currentFile: FileModel? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastExecutionReport: ExecutionReport? = null
)

enum class QuickAction { SUMMARIZE, PARAPHRASE, KEY_POINTS, EXPLAIN, SUGGEST_NAME, ORGANIZE }

class AiAssistantViewModel(
    private val assistant: AiFileAssistant,
    private val fileOpsManager: FileOperationsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _pendingAction = MutableStateFlow<AiActionPlan?>(null)
    val pendingAction: StateFlow<AiActionPlan?> = _pendingAction.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var _lastUndoSnapshot: UndoSnapshot? = null
    private val gson = Gson()

    fun setCurrentFile(file: FileModel, context: Context) {
        _uiState.update { it.copy(currentFile = file, error = null) }
        _messages.value = emptyList()
        _pendingAction.value = null
        
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = assistant.analyzeFile(file, context)) {
                is AiResult.Success -> {
                    addMessage(AiMessage(content = result.data, role = MessageRole.ASSISTANT))
                }
                is AiResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
                AiResult.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun sendMessage(userMessage: String, context: Context) {
        if (userMessage.isBlank()) return
        addMessage(AiMessage(content = userMessage, role = MessageRole.USER))
        
        viewModelScope.launch {
            _isLoading.value = true
            val history = _messages.value
            val currentFile = _uiState.value.currentFile

            when (val result = assistant.chat(userMessage, currentFile, history, context)) {
                is AiResult.Success -> {
                    val text = result.data
                    if (text.contains("foldersToCreate") || text.contains("suggestions")) {
                        try {
                            val cleanText = cleanJson(text)
                            if (cleanText.contains("foldersToCreate")) {
                                val plan = gson.fromJson(cleanText, AiActionPlan.OrganizePlan::class.java)
                                _pendingAction.value = plan
                                addMessage(AiMessage(content = "Plan generado:\n${plan.summary}", role = MessageRole.ASSISTANT, actionPlan = plan))
                            } else if (cleanText.contains("suggestions")) {
                                addMessage(AiMessage(content = "He encontrado estas sugerencias:\n$cleanText", role = MessageRole.ASSISTANT))
                            } else {
                                addMessage(AiMessage(content = text, role = MessageRole.ASSISTANT))
                            }
                        } catch (e: Exception) {
                            addMessage(AiMessage(content = text, role = MessageRole.ASSISTANT))
                        }
                    } else {
                        addMessage(AiMessage(content = text, role = MessageRole.ASSISTANT))
                    }
                }
                is AiResult.Error -> {
                    addMessage(AiMessage(content = "Error: ${result.message}", role = MessageRole.SYSTEM))
                }
                AiResult.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun quickAction(action: QuickAction, context: Context) {
        val file = _uiState.value.currentFile ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            when (action) {
                QuickAction.SUMMARIZE, QuickAction.PARAPHRASE, QuickAction.KEY_POINTS, QuickAction.EXPLAIN -> {
                    val taskName = when(action) {
                        QuickAction.SUMMARIZE -> "resumir"
                        QuickAction.PARAPHRASE -> "parafrasear"
                        QuickAction.KEY_POINTS -> "puntos_clave"
                        QuickAction.EXPLAIN -> "explicar"
                        else -> "resumir"
                    }
                    when (val result = assistant.summarize(file, taskName, context)) {
                        is AiResult.Success -> addMessage(AiMessage(content = result.data, role = MessageRole.ASSISTANT))
                        is AiResult.Error -> addMessage(AiMessage(content = "Error: ${result.message}", role = MessageRole.SYSTEM))
                        AiResult.Loading -> {}
                    }
                }
                QuickAction.SUGGEST_NAME -> {
                    when (val result = assistant.getRenamesuggestions(file, context)) {
                        is AiResult.Success -> {
                            val msg = "Sugerencias de nombre:\n" + result.data.joinToString("\n") { "- $it" }
                            addMessage(AiMessage(content = msg, role = MessageRole.ASSISTANT))
                        }
                        is AiResult.Error -> addMessage(AiMessage(content = "Error: ${result.message}", role = MessageRole.SYSTEM))
                        AiResult.Loading -> {}
                    }
                }
                QuickAction.ORGANIZE -> {
                    when (val result = assistant.organizeFiles(listOf(file), context)) {
                        is AiResult.Success -> {
                            val plan = result.data
                            _pendingAction.value = plan
                            addMessage(AiMessage(content = "Plan de organización:\n${plan.summary}", role = MessageRole.ASSISTANT, actionPlan = plan))
                        }
                        is AiResult.Error -> addMessage(AiMessage(content = "Error: ${result.message}", role = MessageRole.SYSTEM))
                        AiResult.Loading -> {}
                    }
                }
            }
            _isLoading.value = false
        }
    }

    fun confirmAction(plan: AiActionPlan, baseDir: File, context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            when (plan) {
                is AiActionPlan.OrganizePlan -> {
                    when (val result = fileOpsManager.executeOrganizePlan(plan, baseDir)) {
                        is AiResult.Success -> {
                            val report = result.data
                            _lastUndoSnapshot = report.undoSnapshot
                            _uiState.update { it.copy(lastExecutionReport = report) }
                            addMessage(AiMessage(content = "✓ Listo. Se organizaron ${report.successful.size} archivos y se crearon ${report.foldersCreated.size} carpetas.", role = MessageRole.ASSISTANT))
                        }
                        is AiResult.Error -> addMessage(AiMessage(content = "Error organizando: ${result.message}", role = MessageRole.SYSTEM))
                        AiResult.Loading -> {}
                    }
                }
                is AiActionPlan.RenamePlan -> {
                    when (val result = fileOpsManager.renameFile(plan.file, plan.newName)) {
                        is AiResult.Success -> {
                            addMessage(AiMessage(content = "✓ Listo. Archivo renombrado a ${result.data.name}.", role = MessageRole.ASSISTANT))
                            _uiState.update { it.copy(currentFile = result.data) }
                        }
                        is AiResult.Error -> addMessage(AiMessage(content = "Error renombrando: ${result.message}", role = MessageRole.SYSTEM))
                        AiResult.Loading -> {}
                    }
                }
                is AiActionPlan.MovePlan -> {
                    val destDir = File(baseDir, plan.destinationPath)
                    val success = fileOpsManager.moveFile(File(plan.file.absolutePath), File(destDir, plan.file.name))
                    if (success) {
                        addMessage(AiMessage(content = "✓ Listo. Archivo movido a ${plan.destinationPath}.", role = MessageRole.ASSISTANT))
                    } else {
                        addMessage(AiMessage(content = "Error moviendo archivo.", role = MessageRole.SYSTEM))
                    }
                }
            }
            _pendingAction.value = null
            _isLoading.value = false
        }
    }

    fun cancelAction() {
        _pendingAction.value = null
        addMessage(AiMessage(content = "Acción cancelada.", role = MessageRole.SYSTEM))
    }

    fun undo(context: Context) {
        val snapshot = _lastUndoSnapshot
        if (snapshot == null) {
            addMessage(AiMessage(content = "No hay acciones para deshacer.", role = MessageRole.SYSTEM))
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = fileOpsManager.undo(snapshot)) {
                is AiResult.Success -> {
                    _lastUndoSnapshot = null
                    addMessage(AiMessage(content = "✓ Listo. Acción deshecha con éxito.", role = MessageRole.ASSISTANT))
                }
                is AiResult.Error -> {
                    addMessage(AiMessage(content = "Error al deshacer: ${result.message}", role = MessageRole.SYSTEM))
                }
                AiResult.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    fun saveLastResponseAsFile(context: Context) {
        val currentFile = _uiState.value.currentFile ?: return
        val lastAssistantMsg = _messages.value.lastOrNull { it.role == MessageRole.ASSISTANT }?.content ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = fileOpsManager.saveTextAsFile(lastAssistantMsg, currentFile)) {
                is AiResult.Success -> {
                    addMessage(AiMessage(content = "✓ Respuesta guardada en: ${result.data.absolutePath}", role = MessageRole.ASSISTANT))
                }
                is AiResult.Error -> {
                    addMessage(AiMessage(content = "Error al guardar: ${result.message}", role = MessageRole.SYSTEM))
                }
                AiResult.Loading -> {}
            }
            _isLoading.value = false
        }
    }

    private fun addMessage(message: AiMessage) {
        _messages.update { it + message }
    }

    private fun cleanJson(json: String): String {
        var clean = json.trim()
        if (clean.startsWith("```json")) clean = clean.substringAfter("```json")
        if (clean.startsWith("```")) clean = clean.substringAfter("```")
        if (clean.endsWith("```")) clean = clean.substringBeforeLast("```")
        return clean.trim()
    }
}

class AiAssistantViewModelFactory(
    private val assistant: AiFileAssistant,
    private val fileOpsManager: FileOperationsManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiAssistantViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiAssistantViewModel(assistant, fileOpsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
