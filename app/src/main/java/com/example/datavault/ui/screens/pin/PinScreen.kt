package com.example.datavault.ui.screens.pin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PinScreen(
    title: String = "Ingresa tu PIN",
    subtitle: String = "Confirma tu identidad para continuar",
    onPinEntered: suspend (String) -> Boolean,
    onForgotPin: (() -> Unit)? = null,
    onSuccess: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var failCount by remember { mutableStateOf(0) }
    var lockedOut by remember { mutableStateOf(false) }
    var lockSeconds by remember { mutableStateOf(0) }
    var shakeError by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Countdown timer for lockout
    LaunchedEffect(lockedOut) {
        if (lockedOut) {
            lockSeconds = 30
            while (lockSeconds > 0) {
                delay(1000)
                lockSeconds--
            }
            lockedOut = false
            failCount = 0
            pin = ""
            errorMessage = ""
        }
    }

    // Auto-verify when 4 digits entered
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            val correct = onPinEntered(pin)
            if (correct) {
                onSuccess()
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                shakeError = true
                delay(400)
                shakeError = false
                failCount++
                errorMessage = when (failCount) {
                    1 -> "PIN incorrecto. ${3 - failCount} intentos restantes"
                    2 -> "PIN incorrecto. 1 intento restante"
                    else -> {
                        lockedOut = true
                        "Demasiados intentos fallidos"
                    }
                }
                pin = ""
            }
        }
    }

    val dotsOffsetX by animateFloatAsState(
        targetValue = if (shakeError) 10f else 0f,
        animationSpec = keyframes {
            durationMillis = 400
            0f at 0
            10f at 50
            -10f at 100
            10f at 150
            -10f at 200
            10f at 250
            0f at 400
        },
        label = "shake"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(36.dp))

            // PIN dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.offset(x = dotsOffsetX.dp)
            ) {
                repeat(4) { index ->
                    val filled = index < pin.length
                    val dotColor by animateColorAsState(
                        targetValue = if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        label = "dot_color"
                    )
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(dotColor, CircleShape)
                            .border(
                                width = 2.dp,
                                color = if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error / lockout message
            AnimatedVisibility(visible = errorMessage.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = if (lockedOut) "Espera $lockSeconds segundos..." else errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Numeric keypad
            val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
            val gridSize = 3

            keys.chunked(gridSize).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { key ->
                        when {
                            key.isEmpty() -> Spacer(modifier = Modifier.size(72.dp))
                            key == "⌫" -> {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = !lockedOut && pin.isNotEmpty()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            pin = pin.dropLast(1)
                                            errorMessage = ""
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Backspace,
                                        contentDescription = "Borrar",
                                        tint = if (pin.isNotEmpty() && !lockedOut)
                                            MaterialTheme.colorScheme.onBackground
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            shape = CircleShape
                                        )
                                        .clip(CircleShape)
                                        .clickable(enabled = !lockedOut && pin.length < 4) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            pin += key
                                            errorMessage = ""
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (!lockedOut) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Forgot PIN button
            if (onForgotPin != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onForgotPin) {
                    Text("¿Olvidaste tu PIN?", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
