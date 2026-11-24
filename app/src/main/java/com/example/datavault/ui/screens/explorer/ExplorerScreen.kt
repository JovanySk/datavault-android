package com.example.datavault.ui.screens.explorer

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.datavault.data.storage.FileOperations
import com.example.datavault.ui.components.BreadcrumbBar
import com.example.datavault.ui.components.FileItemRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(onOpenAI: (filePath: String, mimeType: String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: ExplorerViewModel = viewModel { ExplorerViewModel(application) }

    val currentPath by viewModel.currentPath.collectAsState()
    val files by viewModel.files.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val viewType by viewModel.viewType.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val clipboardFiles by viewModel.clipboardFiles.collectAsState()
    val clipboardAction by viewModel.clipboardAction.collectAsState()

    var fileToRename by remember { mutableStateOf<String?>(null) }
    var fileToShowInfo by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedFileActions by remember { mutableStateOf<com.example.datavault.data.storage.FileInfo?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val fileOps = remember { FileOperations() }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }

    val aiAssistantViewModel: com.example.datavault.ui.assistant.AiAssistantViewModel = viewModel(
        factory = com.example.datavault.ui.assistant.AiAssistantViewModelFactory(
            com.example.datavault.data.ai.AiAssistantProvider.assistant,
            com.example.datavault.data.ai.AiAssistantProvider.fileOperationsManager
        )
    )
    var showAiSheet by remember { mutableStateOf(false) }
    var fileForAiAssistant by remember { mutableStateOf<com.example.datavault.data.model.FileModel?>(null) }


    BackHandler(enabled = true) {
        viewModel.navigateUp()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Buscar archivos...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Limpiar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                BreadcrumbBar(
                    currentPath = currentPath,
                    onNavigate = { viewModel.navigateTo(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            FilledTonalButton(onClick = { showSortMenu = true }) {
                                Text(
                                    text = sortLabel(sortBy),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                listOf("name" to "Nombre", "size" to "Tamaño", "date" to "Fecha", "type" to "Tipo").forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                fontWeight = if (sortBy == key) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setSortBy(key)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            Icon(
                                imageVector = if (sortAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = "Orden",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { viewModel.setViewType("list") }) {
                            Icon(
                                imageVector = Icons.Filled.ViewList,
                                contentDescription = "Lista",
                                tint = if (viewType == "list") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.setViewType("grid") }) {
                            Icon(
                                imageVector = Icons.Filled.GridView,
                                contentDescription = "Cuadrícula",
                                tint = if (viewType == "grid") MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Sin resultados para \"$searchQuery\""
                            else "Carpeta vacía",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (viewType == "grid") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.path }) { file ->
                        FileGridItem(
                            file = file,
                            isSelected = selectedFiles.contains(file.path),
                            fileOps = fileOps,
                            dateFormat = dateFormat,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(file.path)
                                } else if (file.isDirectory) {
                                    viewModel.navigateTo(file.path)
                                } else {
                                    openFile(context, file, fileOps.getMimeType(file.path))
                                }
                            },
                            onLongClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(file.path)
                                } else {
                                    selectedFileActions = file
                                }
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    items(files, key = { it.path }) { file ->
                        FileItemRow(
                            name = file.name,
                            size = fileOps.formatFileSize(file.size),
                            lastModified = dateFormat.format(Date(file.lastModified)),
                            isDirectory = file.isDirectory,
                            extension = file.extension,
                            isSelected = selectedFiles.contains(file.path),
                            onItemClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(file.path)
                                } else if (file.isDirectory) {
                                    viewModel.navigateTo(file.path)
                                } else {
                                    openFile(context, file, fileOps.getMimeType(file.path))
                                }
                            },
                            onLongClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(file.path)
                                } else {
                                    selectedFileActions = file
                                }
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isSelectionMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedFiles.size} seleccionados",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { viewModel.setClipboard("copy") }) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = "Copiar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.setClipboard("move") }) {
                        Icon(
                            Icons.Filled.DriveFileMove,
                            contentDescription = "Mover",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedFiles.size == 1) {
                        IconButton(onClick = { viewModel.toggleFavorite(selectedFiles.first()) }) {
                            Icon(Icons.Filled.Star, contentDescription = "Favorito", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { fileToRename = selectedFiles.first() }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Renombrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { fileToShowInfo = selectedFiles.first() }) {
                            Icon(Icons.Filled.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { shareFiles(context, selectedFiles) }) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = "Compartir",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        viewModel.moveToVault(selectedFiles.toList())
                        viewModel.clearSelection()
                        android.widget.Toast.makeText(context, "Movido a la Bóveda", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Mover a Bóveda",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cancelar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { 
                if (isSelectionMode && selectedFiles.isNotEmpty()) {
                    val filesList = files.filter { selectedFiles.contains(it.path) }.map {
                        com.example.datavault.data.model.FileModel(
                            name = it.name, path = it.path, absolutePath = it.path,
                            mimeType = fileOps.getMimeType(it.path) ?: "application/octet-stream",
                            size = it.size, lastModified = it.lastModified,
                            isDirectory = it.isDirectory, extension = it.extension
                        )
                    }
                    if (filesList.isNotEmpty()) {
                        fileForAiAssistant = filesList.first()
                        showAiSheet = true
                        aiAssistantViewModel.quickAction(com.example.datavault.ui.assistant.QuickAction.ORGANIZE, context)
                    }
                } else {
                    fileForAiAssistant = null
                    showAiSheet = true 
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (clipboardFiles.isNotEmpty()) 80.dp else 24.dp, end = 16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "Asistente IA")
        }

        AnimatedVisibility(
            visible = clipboardFiles.isNotEmpty() && !isSelectionMode,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${clipboardFiles.size} en portapapeles",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                FilledTonalButton(onClick = { viewModel.pasteClipboard() }) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = "Pegar", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pegar")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { viewModel.clearClipboard() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancelar", modifier = Modifier.size(18.dp))
                }
            }
        }

        if (fileToRename != null) {
            val originalName = fileToRename!!.substringAfterLast('/')
            var newName by remember { mutableStateOf(originalName) }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { fileToRename = null },
                title = { Text("Renombrar") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        if (newName.isNotBlank() && newName != originalName) {
                            viewModel.renameFile(fileToRename!!, newName)
                        }
                        fileToRename = null
                    }) { Text("Guardar") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { fileToRename = null }) { Text("Cancelar") }
                }
            )
        }

        if (fileToShowInfo != null) {
            val file = files.find { it.path == fileToShowInfo } ?: fileOps.run {
                java.io.File(fileToShowInfo!!).let { com.example.datavault.data.storage.FileInfo(it.name, it.absolutePath, it.length(), it.lastModified(), it.isDirectory, it.extension) }
            }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { fileToShowInfo = null },
                title = { Text("Información") },
                text = {
                    Column {
                        Text("Nombre: ${file.name}", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ruta: ${file.path}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Tamaño: ${fileOps.formatFileSize(file.size)}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Modificado: ${dateFormat.format(Date(file.lastModified))}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Row {
                        if (!file.isDirectory) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    val mime = fileOps.getMimeType(file.path) ?: "application/octet-stream"
                                    fileToShowInfo = null
                                    onOpenAI(file.path, mime)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Analizar con IA")
                            }
                        }
                        androidx.compose.material3.TextButton(onClick = { fileToShowInfo = null }) { Text("Cerrar") }
                    }
                }
            )
        }

        if (showDeleteConfirmDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Eliminar archivos") },
                text = { Text("¿Estás seguro de que deseas eliminar permanentemente ${selectedFiles.size} archivos?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirmDialog = false
                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancelar") }
                }
            )
        }

        if (showAiSheet) {
            com.example.datavault.ui.assistant.AiAssistantBottomSheet(
                file = fileForAiAssistant,
                viewModel = aiAssistantViewModel,
                baseDir = java.io.File(currentPath),
                onDismiss = { showAiSheet = false }
            )
        }

        if (selectedFileActions != null) {
            val file = selectedFileActions!!
            ModalBottomSheet(
                onDismissRequest = { selectedFileActions = null },
                sheetState = sheetState
            ) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    
                    DropdownMenuItem(
                        text = { Text("Seleccionar") },
                        onClick = { 
                            viewModel.toggleSelection(file.path)
                            selectedFileActions = null 
                        },
                        leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Información") },
                        onClick = { fileToShowInfo = file.path; selectedFileActions = null },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        onClick = { fileToRename = file.path; selectedFileActions = null },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir / Subir") },
                        onClick = { 
                            shareFiles(context, setOf(file.path))
                            selectedFileActions = null 
                        },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Añadir a Favoritos") },
                        onClick = { 
                            viewModel.toggleFavorite(file.path)
                            android.widget.Toast.makeText(context, "Favorito actualizado", android.widget.Toast.LENGTH_SHORT).show()
                            selectedFileActions = null 
                        },
                        leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover a Bóveda") },
                        onClick = { 
                            viewModel.moveToVault(listOf(file.path))
                            android.widget.Toast.makeText(context, "Movido a la Bóveda", android.widget.Toast.LENGTH_SHORT).show()
                            selectedFileActions = null 
                        },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        onClick = { 
                            viewModel.toggleSelection(file.path)
                            showDeleteConfirmDialog = true
                            selectedFileActions = null 
                        },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(
    file: com.example.datavault.data.storage.FileInfo,
    isSelected: Boolean,
    fileOps: FileOperations,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Filled.FolderOpen else Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!file.isDirectory) {
                Text(
                    text = fileOps.formatFileSize(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun sortLabel(sortBy: String): String = when (sortBy) {
    "name" -> "Nombre"
    "size" -> "Tamaño"
    "date" -> "Fecha"
    "type" -> "Tipo"
    else -> "Nombre"
}

private fun openFile(context: android.content.Context, file: com.example.datavault.data.storage.FileInfo, mimeType: String?) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", java.io.File(file.path))
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, "Abrir con...")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun shareFiles(context: android.content.Context, filePaths: Set<String>) {
    try {
        val uris = filePaths.map { path ->
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", java.io.File(path))
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, java.util.ArrayList(uris))
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Compartir archivos"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al compartir", android.widget.Toast.LENGTH_SHORT).show()
    }
}
