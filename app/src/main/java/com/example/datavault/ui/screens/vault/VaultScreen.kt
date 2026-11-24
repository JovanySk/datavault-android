package com.example.datavault.ui.screens.vault

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.datavault.data.local.entity.VaultFile
import com.example.datavault.data.storage.FileOperations
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: VaultViewModel = viewModel { VaultViewModel(application) }
    val uiState by viewModel.uiState.collectAsState()
    val fileOps = remember { FileOperations() }

    LaunchedEffect(Unit) {
        if (!uiState.isAuthenticated) {
            authenticate(context, viewModel)
        }
    }

    // Pantalla de bloqueo
    if (!uiState.isAuthenticated) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Bóveda Segura") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Bóveda Bloqueada",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Bóveda Bloqueada",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Confirma tu identidad para acceder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { authenticate(context, viewModel) }) {
                        Text("Desbloquear")
                    }
                }
            }
        }
        return
    }

    // Pantalla principal de la bóveda (autenticado)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bóveda Segura") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setAuthenticated(false) }) {
                        Icon(Icons.Filled.LockOpen, contentDescription = "Bloquear bóveda")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.vaultFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "La bóveda está vacía",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Mueve archivos aquí desde el Explorador",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.vaultFiles) { file ->
                    VaultFileCard(
                        file = file,
                        fileOps = fileOps,
                        onRestore = {
                            viewModel.restoreFile(file)
                            Toast.makeText(context, "\"${file.originalFileName}\" restaurado a su ubicación original", Toast.LENGTH_SHORT).show()
                        },
                        onViewTemp = { openTemporarily(context, viewModel, file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultFileCard(
    file: VaultFile,
    fileOps: FileOperations,
    onRestore: () -> Unit,
    onViewTemp: () -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showOptions = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.originalFileName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = fileOps.formatFileSize(file.fileSize) + " · Cifrado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(file.originalFileName, maxLines = 1) },
            text = { Text("¿Qué deseas hacer con este archivo?") },
            confirmButton = {
                // Botón principal: Restaurar / Sacar de la bóveda
                Button(
                    onClick = {
                        showOptions = false
                        onRestore()
                    }
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sacar de la Bóveda")
                }
            },
            dismissButton = {
                // Botón secundario: Ver temporalmente
                TextButton(
                    onClick = {
                        showOptions = false
                        onViewTemp()
                    }
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver (sin sacar)")
                }
            }
        )
    }
}

private fun authenticate(context: Context, viewModel: VaultViewModel) {
    val activity = context as? FragmentActivity
    if (activity == null) {
        Toast.makeText(context, "Error de actividad", Toast.LENGTH_SHORT).show()
        return
    }

    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val biometricManager = BiometricManager.from(context)

    if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
        Toast.makeText(
            context,
            "Configura un PIN, Patrón o Huella en tu dispositivo para proteger la bóveda.",
            Toast.LENGTH_LONG
        ).show()
        val enrollIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticators)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        context.startActivity(enrollIntent)
        return
    }

    val executor = ContextCompat.getMainExecutor(context)
    val biometricPrompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Toast.makeText(context, "Error: $errString", Toast.LENGTH_SHORT).show()
            }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.setAuthenticated(true)
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(context, "Fallo al autenticar", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloquear Bóveda Segura")
        .setSubtitle("Confirma tu identidad para acceder a tus archivos privados")
        .setAllowedAuthenticators(authenticators)
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@OptIn(DelicateCoroutinesApi::class)
private fun openTemporarily(
    context: Context,
    viewModel: VaultViewModel,
    vaultFile: VaultFile
) {
    GlobalScope.launch(Dispatchers.IO) {
        val tempFile = viewModel.vaultOperations.decryptToTemp(vaultFile)
        if (tempFile != null) {
            withContext(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tempFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, vaultFile.mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al abrir archivo", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error al descifrar", Toast.LENGTH_SHORT).show()
            }
        }
    }
}