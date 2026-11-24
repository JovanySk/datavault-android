package com.example.datavault.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.datavault.data.preferences.PinRepository
import com.example.datavault.ui.screens.settings.BackupDestination
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.datavault.ui.screens.settings.BackupUiState
import com.example.datavault.ui.screens.pin.PinScreen
import com.example.datavault.ui.screens.pin.PinSetupScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: SettingsViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    )
    val pinRepo = remember { PinRepository(context) }
    val scope = rememberCoroutineScope()

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val viewType by viewModel.viewType.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
    val sortAscending by viewModel.sortAscending.collectAsStateWithLifecycle()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsStateWithLifecycle()
    val isPinEnabled by pinRepo.isPinEnabled.collectAsState(initial = false)
    val hasPinSet by pinRepo.hasPinSet.collectAsState(initial = false)

    var showClearDialog by remember { mutableStateOf(false) }
    var showDisablePinDialog by remember { mutableStateOf(false) }
    var showPinSetup by remember { mutableStateOf(false) }
    var showChangePinVerify by remember { mutableStateOf(false) }
    
    val connectedAccount by viewModel.connectedAccount.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.handleSignInResult(result.data)
    }

    var showBackupList by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf<BackupUiState.Success?>(null) }
    var activeBackupDestination by remember { mutableStateOf<BackupDestination?>(null) }
    var backupSuccessLink by remember { mutableStateOf<String?>(null) }
    var localBackupSuccessPath by remember { mutableStateOf<String?>(null) }
    var showDriveOptionsDialog by remember { mutableStateOf(false) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadLocalZipToDrive(context, uri)
        }
    }
    
    LaunchedEffect(backupState) {
        when (val state = backupState) {
            is BackupUiState.Success -> {
                if (state.link != null) {
                    showSuccessDialog = state
                } else {
                    snackbarHostState.showSnackbar(state.message)
                    viewModel.resetBackupState()
                }
            }
            is BackupUiState.Error -> {
                snackbarHostState.showSnackbar(state.error)
                viewModel.resetBackupState()
            }
            is BackupUiState.BackupList -> {
                showBackupList = true
            }
            else -> {}
        }
    }

    // Navigate to PIN setup screen
    if (showPinSetup) {
        PinSetupScreen(
            onBack = { showPinSetup = false },
            onPinSaved = { showPinSetup = false }
        )
        return
    }

    // Verify current PIN before changing it
    if (showChangePinVerify) {
        PinScreen(
            title = "Verifica tu PIN actual",
            subtitle = "Ingresa tu PIN actual para poder cambiarlo",
            onPinEntered = { pin -> pinRepo.verifyPin(pin) },
            onSuccess = {
                showChangePinVerify = false
                showPinSetup = true
            }
        )
        return
    }

    if (showDriveOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showDriveOptionsDialog = false },
            title = { Text("Opciones de Respaldo en Drive") },
            text = { Text("¿Qué tipo de copia deseas subir a la nube?") },
            confirmButton = {
                TextButton(onClick = {
                    showDriveOptionsDialog = false
                    activeBackupDestination = BackupDestination.DRIVE
                }) {
                    Text("Crear nueva copia")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDriveOptionsDialog = false
                    zipPickerLauncher.launch("application/zip")
                }) {
                    Text("Subir copia local .zip")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ─── PRIVACIDAD Y SEGURIDAD ───────────────────────────────────
            item { SettingsHeader("Privacidad y Seguridad") }

            item {
                ListItem(
                    headlineContent = { Text("Contraseña de la aplicación") },
                    supportingContent = {
                        Text(
                            if (isPinEnabled) "PIN activo — toca para desactivar"
                            else "Protege la app con un PIN de 4 dígitos"
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (isPinEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isPinEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = isPinEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showPinSetup = true
                                } else {
                                    showDisablePinDialog = true
                                }
                            }
                        )
                    }
                )
            }

            if (hasPinSet) {
                item {
                    ListItem(
                        headlineContent = { Text("Cambiar PIN") },
                        supportingContent = { Text("Reemplaza tu PIN actual por uno nuevo") },
                        leadingContent = { Icon(Icons.Default.Password, contentDescription = null) },
                        modifier = Modifier.clickable {
                            showChangePinVerify = true
                        }
                    )
                }
            }

            item { Divider() }

            // ─── APARIENCIA ───────────────────────────────────────────────
            item { SettingsHeader("Apariencia") }
            item {
                SettingsThemeSelector(
                    currentTheme = themeMode,
                    onThemeSelected = { viewModel.setThemeMode(it) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Vista predeterminada") },
                    supportingContent = { Text(if (viewType == "list") "Lista" else "Cuadrícula") },
                    leadingContent = { Icon(Icons.Default.ViewList, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.setViewType(if (viewType == "list") "grid" else "list")
                    }
                )
            }

            item { Divider() }

            // ─── ARCHIVOS ─────────────────────────────────────────────────
            item { SettingsHeader("Archivos") }
            item {
                ListItem(
                    headlineContent = { Text("Ordenar por") },
                    supportingContent = {
                        Text(
                            when (sortBy) {
                                "name" -> "Nombre"
                                "size" -> "Tamaño"
                                "date" -> "Fecha"
                                "type" -> "Tipo"
                                else -> sortBy.replaceFirstChar { it.uppercase() }
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Sort, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val nextSort = when (sortBy) {
                            "name" -> "size"
                            "size" -> "date"
                            "date" -> "type"
                            else -> "name"
                        }
                        viewModel.setSortBy(nextSort)
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Orden") },
                    supportingContent = { Text(if (sortAscending) "Ascendente (A → Z)" else "Descendente (Z → A)") },
                    leadingContent = {
                        Icon(
                            if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setSortAscending(!sortAscending) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Mostrar archivos ocultos") },
                    supportingContent = { Text("Archivos que comienzan con punto (.)") },
                    leadingContent = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = showHiddenFiles,
                            onCheckedChange = { viewModel.setShowHiddenFiles(it) }
                        )
                    }
                )
            }

            item { Divider() }

            // ─── DATOS ────────────────────────────────────────────────────
            item { SettingsHeader("Datos") }
            item {
                ListItem(
                    headlineContent = { Text("Copia en Google Drive") },
                    supportingContent = { 
                        if (connectedAccount != null) {
                            Column {
                                Text("Conectado como: ${connectedAccount?.email}\nToca para crear copia")
                                TextButton(
                                    onClick = { viewModel.loadDriveBackups() },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.padding(top = 4.dp).height(24.dp)
                                ) {
                                    Text("Ver copias anteriores", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Text("Inicia sesión para respaldar en la nube")
                        }
                    },
                    leadingContent = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                    trailingContent = {
                        if (connectedAccount != null) {
                            TextButton(onClick = {
                                viewModel.signOut {
                                    signInLauncher.launch(viewModel.getSignInIntent())
                                }
                            }) {
                                Text("Cambiar cuenta")
                            }
                        }
                    },
                    modifier = Modifier.clickable { 
                        if (connectedAccount == null) {
                            signInLauncher.launch(viewModel.getSignInIntent())
                        } else {
                            showDriveOptionsDialog = true
                        }
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Copia local") },
                    supportingContent = { Text("Crear copia de seguridad en el dispositivo") },
                    leadingContent = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                    modifier = Modifier.clickable { activeBackupDestination = BackupDestination.LOCAL }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Limpiar todos los datos") },
                    supportingContent = { Text("Borrar historial y favoritos") },
                    leadingContent = {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable { showClearDialog = true }
                )
            }

            item { Divider() }



            // ─── ACERCA DE ────────────────────────────────────────────────
            item { SettingsHeader("Acerca de") }
            item {
                ListItem(
                    headlineContent = { Text("DataVault") },
                    supportingContent = { Text("Versión 1.0.0\nDesarrollada por Jovany Morales Del Ángel") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // ─── DIALOGS ──────────────────────────────────────────────────────────

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Limpiar todos los datos") },
            text = { Text("¿Estás seguro? Se borrará el historial y los favoritos. La bóveda segura NO se verá afectada.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDialog = false
                }) {
                    Text("Limpiar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDisablePinDialog) {
        AlertDialog(
            onDismissRequest = { showDisablePinDialog = false },
            icon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
            title = { Text("Desactivar PIN") },
            text = { Text("¿Estás seguro? La aplicación quedará sin protección de contraseña. La bóveda mantendrá su cifrado.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { pinRepo.clearPin() }
                        showDisablePinDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Desactivar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisablePinDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (backupState is BackupUiState.Loading) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Procesando copia de seguridad") },
            text = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Por favor, espera...")
                }
            },
            confirmButton = { }
        )
    }

    showSuccessDialog?.let { successState ->
        AlertDialog(
            onDismissRequest = { 
                showSuccessDialog = null
                viewModel.resetBackupState()
            },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("✓ Copia subida") },
            text = { Text(successState.message) },
            confirmButton = {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(successState.link))
                    context.startActivity(intent)
                    showSuccessDialog = null
                    viewModel.resetBackupState()
                }) {
                    Text("Ver en Drive")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSuccessDialog = null
                    viewModel.resetBackupState()
                }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showBackupList && backupState is BackupUiState.BackupList) {
        val list = (backupState as BackupUiState.BackupList).list
        ModalBottomSheet(onDismissRequest = { 
            showBackupList = false
            viewModel.resetBackupState()
        }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Mis copias en Drive", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                if (list.isEmpty()) {
                    Text("No hay copias de seguridad en Drive.")
                } else {
                    LazyColumn {
                        items(list.size) { index ->
                            val backup = list[index]
                            ListItem(
                                headlineContent = { Text(backup.name) },
                                supportingContent = { 
                                    val desc = if (backup.description.isNotEmpty()) backup.description else "Tamaño: ${backup.size / 1024} KB"
                                    Text("Fecha: ${backup.createdTime}\n$desc") 
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.deleteBackup(backup.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // ─── BACKUP SELECTION BOTTOM SHEET ───────────────────────────────
    activeBackupDestination?.let { dest ->
        BackupSelectionBottomSheet(
            destination = dest,
            account = connectedAccount,
            onDismiss = { activeBackupDestination = null },
            onBackupDone = { linkOrPath ->
                activeBackupDestination = null
                if (dest == BackupDestination.DRIVE) {
                    backupSuccessLink = linkOrPath
                } else {
                    localBackupSuccessPath = linkOrPath
                }
                scope.launch {
                    val msg = if (dest == BackupDestination.DRIVE) "✓ Copia subida a Google Drive" else "✓ Copia local creada"
                    snackbarHostState.showSnackbar(msg)
                }
            }
        )
    }

    // Show result dialog if there's a drive link
    backupSuccessLink?.let { link ->
        AlertDialog(
            onDismissRequest = { backupSuccessLink = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("✓ Copia subida correctamente") },
            text = { Text("Tus archivos han sido guardados en Google Drive.") },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    backupSuccessLink = null
                }) { Text("Ver en Drive") }
            },
            dismissButton = {
                TextButton(onClick = { backupSuccessLink = null }) { Text("Cerrar") }
            }
        )
    }

    // Show result dialog if there's a local backup path
    localBackupSuccessPath?.let { path ->
        AlertDialog(
            onDismissRequest = { localBackupSuccessPath = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("✓ Copia local creada") },
            text = { Text("Tus archivos han sido guardados en:\n\n$path") },
            confirmButton = {
                Button(onClick = {
                    localBackupSuccessPath = null
                }) { Text("Aceptar") }
            }
        )
    }

}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsThemeSelector(currentTheme: String, onThemeSelected: (String) -> Unit) {
    Column {
        ListItem(headlineContent = { Text("Tema de la aplicación") })
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThemeOption("Sistema", currentTheme == "system") { onThemeSelected("system") }
            ThemeOption("Claro", currentTheme == "light") { onThemeSelected("light") }
            ThemeOption("Oscuro", currentTheme == "dark") { onThemeSelected("dark") }
        }
    }
}

@Composable
fun ThemeOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onClick() }) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
