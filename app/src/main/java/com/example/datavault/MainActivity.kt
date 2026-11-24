package com.example.datavault

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.datavault.data.preferences.PinRepository
import com.example.datavault.data.preferences.UserPreferencesRepository
import com.example.datavault.theme.DataVaultTheme
import com.example.datavault.ui.screens.pin.PinScreen
import com.example.datavault.ui.screens.splash.SplashScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private var hasStoragePermission by mutableStateOf(false)
    private var isAppLocked by mutableStateOf(true) // Empieza bloqueado siempre
    private var isPinEnabled by mutableStateOf(false)
    private var showSplash by mutableStateOf(true)
    private var lastBackgroundTime = 0L
    private val LOCK_TIMEOUT_MS = 3 * 60 * 1000L // 3 minutos

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()

        // Observe PIN enabled state
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PinRepository(applicationContext).isPinEnabled.collect { enabled ->
                    isPinEnabled = enabled
                }
            }
        }

        // Lock app immediately when user exits using the Back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPinEnabled) {
                    isAppLocked = true
                    lastBackgroundTime = 0L
                }
                showSplash = true // Reset splash screen for next launch
                // Temporarily disable this callback to allow the default exit behavior
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        setContent {
            val repository = remember {
                UserPreferencesRepository(applicationContext)
            }
            val pinRepo = remember { PinRepository(applicationContext) }
            val scope = rememberCoroutineScope()
            val themeMode by repository.themeMode.collectAsState(initial = "system")
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            DataVaultTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Show Splash Screen first
                    if (showSplash) {
                        SplashScreen(
                            onTimeout = {
                                showSplash = false
                            }
                        )
                        return@Surface
                    }

                    // Show PIN screen if app is locked
                    if (isAppLocked && isPinEnabled) {
                        PinScreen(
                            title = "Desbloquear App",
                            subtitle = "Ingresa tu PIN para continuar",
                            onPinEntered = { pin ->
                                pinRepo.verifyPin(pin)
                            },
                            onForgotPin = {
                                scope.launch {
                                    // Reset PIN — user loses PIN protection but keeps vault
                                    pinRepo.clearPin()
                                    isPinEnabled = false
                                    isAppLocked = false
                                }
                            },
                            onSuccess = {
                                isAppLocked = false
                            }
                        )
                        return@Surface
                    }

                    AnimatedVisibility(
                        visible = hasStoragePermission,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        MainNavigation()
                    }

                    AnimatedVisibility(
                        visible = !hasStoragePermission,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Almacenamiento",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Administración de Almacenamiento",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "Para funcionar correctamente, Almacenamiento necesita acceso a tus archivos.",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { requestStoragePermission() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "Conceder Permisos",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        
        // Lock app if it has been in background for more than 3 minutes
        if (isPinEnabled && lastBackgroundTime > 0) {
            val timeInBackground = System.currentTimeMillis() - lastBackgroundTime
            if (timeInBackground >= LOCK_TIMEOUT_MS) {
                isAppLocked = true
            }
        }
        lastBackgroundTime = 0L
    }

    override fun onStop() {
        super.onStop()
        // Record the time the app went to the background
        if (isPinEnabled && !isAppLocked) {
            lastBackgroundTime = System.currentTimeMillis()
        }
    }

    private fun checkPermissions() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
}