package com.example.datavault.ui.screens.pin

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.datavault.data.preferences.PinRepository
import kotlinx.coroutines.launch

// Step 1: Enter new PIN, Step 2: Confirm PIN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onBack: () -> Unit,
    onPinSaved: () -> Unit
) {
    val context = LocalContext.current
    val pinRepo = remember { PinRepository(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) }
    var firstPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (step == 1) "Crear PIN" else "Confirmar PIN") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step == 2) { step = 1; firstPin = "" }
                        else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            if (step == 1) {
                PinScreen(
                    title = "Elige un PIN de 4 dígitos",
                    subtitle = "Lo usarás para abrir la aplicación",
                    onPinEntered = { pin ->
                        firstPin = pin
                        step = 2
                        true // always "success" in step 1 to advance
                    },
                    onSuccess = { /* step managed internally */ }
                )
            } else {
                PinScreen(
                    title = "Confirma tu PIN",
                    subtitle = "Vuelve a ingresar los 4 dígitos",
                    onPinEntered = { pin ->
                        if (pin == firstPin) {
                            scope.launch {
                                pinRepo.setPin(pin)
                                onPinSaved()
                            }
                            true
                        } else {
                            errorMessage = "Los PINs no coinciden"
                            false
                        }
                    },
                    onSuccess = { /* managed by onPinSaved */ }
                )
            }
        }
    }
}
