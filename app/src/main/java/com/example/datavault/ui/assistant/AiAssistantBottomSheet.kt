package com.example.datavault.ui.assistant

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.datavault.data.model.AiActionPlan
import com.example.datavault.data.model.AiMessage
import com.example.datavault.data.model.FileModel
import com.example.datavault.data.model.MessageRole
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantBottomSheet(
    file: FileModel?,
    viewModel: AiAssistantViewModel,
    baseDir: File,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val uiState by viewModel.uiState.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val pendingAction by viewModel.pendingAction.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(file) {
        if (file != null && uiState.currentFile?.absolutePath != file.absolutePath) {
            viewModel.setCurrentFile(file, context)
        }
    }

    // Detector de guardar
    LaunchedEffect(messages) {
        val lastMsg = messages.lastOrNull()
        if (lastMsg != null && lastMsg.role == MessageRole.ASSISTANT && lastMsg.content.contains("OFRECER_GUARDAR")) {
            val result = snackbarHostState.showSnackbar(
                message = "¿Guardar como archivo de texto?",
                actionLabel = "Guardar",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.saveLastResponseAsFile(context)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = file?.name ?: "Asistente Inteligente", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (file != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = file.mimeType,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                // Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { AssistChip(onClick = { viewModel.quickAction(QuickAction.SUMMARIZE, context) }, label = { Text("📄 Resumir") }, enabled = !isLoading) }
                    item { AssistChip(onClick = { viewModel.quickAction(QuickAction.PARAPHRASE, context) }, label = { Text("✏️ Parafrasear") }, enabled = !isLoading) }
                    item { AssistChip(onClick = { viewModel.quickAction(QuickAction.KEY_POINTS, context) }, label = { Text("💡 Puntos clave") }, enabled = !isLoading) }
                    item { AssistChip(onClick = { viewModel.quickAction(QuickAction.SUGGEST_NAME, context) }, label = { Text("🔤 Renombrar") }, enabled = !isLoading) }
                    item { AssistChip(onClick = { viewModel.quickAction(QuickAction.ORGANIZE, context) }, label = { Text("📁 Organizar") }, enabled = !isLoading) }
                }

                HorizontalDivider()

                AnimatedVisibility(visible = isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Chat
                val listState = rememberLazyListState()
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { msg ->
                        if (msg.role == MessageRole.USER) {
                            UserMessageItem(msg)
                        } else if (msg.role == MessageRole.ASSISTANT) {
                            AssistantMessageItem(msg) {
                                // En Compose, pendingAction se emite al ViewModel directamente
                            }
                        } else {
                            Text(
                                text = msg.content,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Input Bar
                var inputText by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escribe algo...") },
                        enabled = !isLoading,
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText, context)
                                inputText = ""
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Enviar")
                    }
                }
            }
        }
    }

    if (pendingAction != null) {
        ConfirmationDialog(
            plan = pendingAction!!,
            onConfirm = { viewModel.confirmAction(pendingAction!!, baseDir, context) },
            onCancel = { viewModel.cancelAction() }
        )
    }
}

@Composable
fun UserMessageItem(msg: AiMessage) {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = formatter.format(Date(msg.timestamp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(12.dp)
        ) {
            Text(text = msg.content, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = timeString,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun AssistantMessageItem(msg: AiMessage, onActionClick: () -> Unit) {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = formatter.format(Date(msg.timestamp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            Text(text = msg.content.replace("OFRECER_GUARDAR:", ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (msg.actionPlan != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onActionClick, modifier = Modifier.align(Alignment.End)) {
                    Text("Ver Plan")
                }
            }
            Text(
                text = timeString,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun ConfirmationDialog(
    plan: AiActionPlan,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                when (plan) {
                    is AiActionPlan.OrganizePlan -> "Confirmar Organización"
                    is AiActionPlan.RenamePlan -> "Confirmar Renombrado"
                    is AiActionPlan.MovePlan -> "Confirmar Movimiento"
                }
            )
        },
        text = {
            LazyColumn {
                when (plan) {
                    is AiActionPlan.OrganizePlan -> {
                        item { Text(plan.summary, fontWeight = FontWeight.Bold) }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        items(plan.moves) { move ->
                            Text("• ${move.fileName} ➔ ${move.destination}", fontSize = 14.sp)
                        }
                    }
                    is AiActionPlan.RenamePlan -> {
                        item { Text("Renombrar: ${plan.file.name} ➔ ${plan.newName}") }
                        item { Text("Razón: ${plan.reason}", fontSize = 14.sp) }
                    }
                    is AiActionPlan.MovePlan -> {
                        item { Text("Mover: ${plan.file.name} ➔ ${plan.destinationPath}") }
                        item { Text("Razón: ${plan.reason}", fontSize = 14.sp) }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Ejecutar") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancelar") }
        }
    )
}
