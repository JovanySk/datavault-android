package com.example.datavault.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.datavault.data.backup.FileType
import com.example.datavault.data.backup.SmartFileSearcher
import com.example.datavault.data.model.FileModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSelectionBottomSheet(
    destination: BackupDestination,
    account: com.google.android.gms.auth.api.signin.GoogleSignInAccount? = null,
    onDismiss: () -> Unit,
    onBackupDone: (driveLink: String?) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: BackupSelectionViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    )

    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedFiles by viewModel.selectedFiles.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val activeFilter by viewModel.activeFilter.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    // Dialogs state
    var showCompressDialog by remember { mutableStateOf(false) }
    var showSpecialFilesDialog by remember { mutableStateOf(false) }
    var pendingCompressImages by remember { mutableStateOf(false) }

    // Handle upload progress state transitions
    LaunchedEffect(uploadProgress) {
        when (val p = uploadProgress) {
            is UploadProgress.Done -> {
                onBackupDone(p.result.driveLink)
                viewModel.resetUpload()
                onDismiss()
            }
            is UploadProgress.LocalDone -> {
                onBackupDone(p.result.backupPath)
                viewModel.resetUpload()
                onDismiss()
            }
            else -> {}
        }
    }

    // -- UPLOAD IN PROGRESS DIALOG --
    when (val progress = uploadProgress) {
        is UploadProgress.Uploading -> {
            val dialogTitle = if (destination == BackupDestination.DRIVE) "Subiendo a Google Drive" else "Guardando copia local"
            UploadProgressDialog(
                title = dialogTitle,
                current = progress.current,
                total = progress.total,
                currentFileName = progress.currentFileName,
                onCancel = { viewModel.cancelUpload() }
            )
        }
        is UploadProgress.Failed -> {
            AlertDialog(
                onDismissRequest = { viewModel.resetUpload() },
                title = { Text("Error al subir") },
                text = { Text(progress.message) },
                confirmButton = { TextButton(onClick = { viewModel.resetUpload() }) { Text("OK") } }
            )
        }
        else -> {}
    }

    // -- COMPRESS IMAGES DIALOG --
    if (showCompressDialog) {
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("¿Comprimir imágenes?") },
            text = {
                Column {
                    Text("Se seleccionaron más de 5 imágenes. ¿Deseas comprimirlas para ahorrar espacio en Drive?")
                    Spacer(Modifier.height(8.dp))
                    Text("• Comprimir: ahorra ~60% del espacio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Originales: mantiene la calidad completa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showCompressDialog = false
                    pendingCompressImages = true
                    if (analysis.specialFiles.isNotEmpty()) {
                        showSpecialFilesDialog = true
                    } else {
                        viewModel.executeBackup(destination, true, account)
                    }
                }) { Text("Comprimir — ahorra ~60%") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCompressDialog = false
                    pendingCompressImages = false
                    if (analysis.specialFiles.isNotEmpty()) {
                        showSpecialFilesDialog = true
                    } else {
                        viewModel.executeBackup(destination, false, account)
                    }
                }) { Text("Subir originales") }
            }
        )
    }

    // -- SPECIAL FILES WARNING DIALOG --
    if (showSpecialFilesDialog) {
        AlertDialog(
            onDismissRequest = { showSpecialFilesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Archivos especiales detectados")
                }
            },
            text = {
                Column {
                    Text("Se encontraron ${analysis.specialFiles.size} archivos de formato especial:")
                    Spacer(Modifier.height(8.dp))
                    analysis.specialFiles.take(5).forEach { file ->
                        Text(
                            "• ${file.name} (${SmartFileSearcher.getSpecialFormatLabel(file)})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (analysis.specialFiles.size > 5) {
                        Text("...y ${analysis.specialFiles.size - 5} más", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Estos archivos solo podrán abrirse con su programa original.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSpecialFilesDialog = false
                    viewModel.executeBackup(destination, pendingCompressImages, account)
                }) { Text("Incluirlos") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSpecialFilesDialog = false
                    // Remove special files from selection and upload
                    analysis.specialFiles.forEach { viewModel.toggleSelection(it) }
                    viewModel.executeBackup(destination, pendingCompressImages, account)
                }) { Text("Excluirlos") }
            }
        )
    }

    // -- MAIN BOTTOM SHEET --
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                val title = if (destination == BackupDestination.DRIVE) "Seleccionar archivos para Drive" else "Seleccionar archivos — Copia local"
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.search(it)
                },
                placeholder = { Text("Buscar archivos o carpetas...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.search("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    } else if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    FileType.ALL to "🔍 Todo",
                    FileType.DOCS to "📄 Docs",
                    FileType.IMAGES to "🖼️ Imágenes",
                    FileType.FOLDERS to "📁 Carpetas",
                    FileType.AUDIO to "🎵 Audio",
                    FileType.VIDEO to "🎬 Video",
                    FileType.OTHERS to "⚙️ Otros"
                )
                itemsIndexed(filters) { _, pair ->
                    FilterChip(
                        selected = activeFilter == pair.first,
                        onClick = { viewModel.filterByType(pair.first) },
                        label = { Text(pair.second, fontSize = 12.sp) }
                    )
                }
            }

            // Select All row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.selectAll() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SelectAll,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Seleccionar todo (${searchResults.size} resultados)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // File list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (searchQuery.isEmpty() && searchResults.isEmpty() && !isSearching) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("Escribe para buscar archivos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("o filtra por tipo arriba", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (searchResults.isEmpty() && !isSearching) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Sin resultados para \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                items(searchResults, key = { it.absolutePath }) { file ->
                    val isSelected = selectedFiles.contains(file)
                    BackupFileItem(
                        file = file,
                        isSelected = isSelected,
                        onClick = { viewModel.toggleSelection(file) }
                    )
                }
            }

            HorizontalDivider()

            // Bottom bar: counter + button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                if (selectedFiles.isNotEmpty()) {
                    val totalSize = analysis.totalSize
                    Text(
                        text = "${selectedFiles.size} archivos seleccionados — ${formatSize(totalSize)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(
                    onClick = {
                        // Decide flow based on analysis
                        when {
                            analysis.hasImages && !analysis.hasFolders -> showCompressDialog = true
                            analysis.specialFiles.isNotEmpty() -> showSpecialFilesDialog = true
                            else -> viewModel.executeBackup(destination, false, account)
                        }
                    },
                    enabled = selectedFiles.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Crear copia")
                }
            }
        }
    }
}

@Composable
private fun BackupFileItem(
    file: FileModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isSpecial = SmartFileSearcher.isSpecialFormat(file)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File type icon
        val icon = when {
            file.isDirectory -> Icons.Default.Folder
            file.extension in SmartFileSearcher.imageExtensions -> Icons.Default.Image
            file.extension in SmartFileSearcher.videoExtensions -> Icons.Default.VideoFile
            file.extension in SmartFileSearcher.audioExtensions -> Icons.Default.AudioFile
            file.extension in SmartFileSearcher.documentExtensions -> Icons.Default.Description
            else -> Icons.Default.InsertDriveFile
        }
        val iconTint = when {
            file.isDirectory -> Color(0xFFFFB300)
            file.extension in SmartFileSearcher.imageExtensions -> Color(0xFF4CAF50)
            file.extension in SmartFileSearcher.videoExtensions -> Color(0xFF2196F3)
            file.extension in SmartFileSearcher.audioExtensions -> Color(0xFF9C27B0)
            file.extension in SmartFileSearcher.documentExtensions -> Color(0xFFF44336)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSpecial) {
                    Spacer(Modifier.width(4.dp))
                    Text("⚠️", fontSize = 12.sp)
                }
            }
            Text(
                text = file.path.removePrefix("/storage/emulated/0"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!file.isDirectory) {
                Text(
                    text = formatSize(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onClick() }
        )
    }
}

@Composable
private fun UploadProgressDialog(
    title: String,
    current: Int,
    total: Int,
    currentFileName: String,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                val progress = if (total > 0) current.toFloat() / total else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "$current de $total archivos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentFileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancelar")
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1000 -> "$bytes B"
        bytes < 1_000_000 -> "%.1f KB".format(bytes / 1000.0)
        bytes < 1_000_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        else -> "%.2f GB".format(bytes / 1_000_000_000.0)
    }
}
