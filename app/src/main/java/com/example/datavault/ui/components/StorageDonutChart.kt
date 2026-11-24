package com.example.datavault.ui.components

import android.text.TextPaint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun StorageDonutChart(
    usedBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    categoryBreakdown: List<Pair<Color, Float>> = emptyList()
) {
    val usedFraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val percentage = (usedFraction * 100).toInt()

    var animationTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(usedBytes, totalBytes) {
        animationTarget = 1f
    }

    val animationProgress by animateFloatAsState(
        targetValue = animationTarget,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "donut_animation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val strokeWidth = 28.dp.toPx()
            val glowStrokeWidth = strokeWidth + 8.dp.toPx()
            val padding = glowStrokeWidth / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - padding * 2, size.height - padding * 2)
            val arcTopLeft = Offset(padding, padding)
            val gapDegrees = 2f
            val startAngle = -90f

            // Background ring
            drawArc(
                color = surfaceVariantColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (categoryBreakdown.isNotEmpty()) {
                val totalProportion = categoryBreakdown.sumOf { it.second.toDouble() }.toFloat()
                val scale = if (totalProportion > 0f) usedFraction / totalProportion else 0f
                var currentAngle = startAngle

                categoryBreakdown.forEach { (color, proportion) ->
                    val sweepAngle = (proportion * scale * 360f - gapDegrees)
                        .coerceAtLeast(0f) * animationProgress

                    if (sweepAngle > 0.1f) {
                        // Glow layer
                        drawArc(
                            color = color.copy(alpha = 0.25f),
                            startAngle = currentAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round)
                        )

                        drawArc(
                            color = color,
                            startAngle = currentAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    currentAngle += (proportion * scale * 360f).coerceAtLeast(0f) * animationProgress
                }
            } else {
                val usedSweep = usedFraction * 360f * animationProgress

                if (usedSweep > 0.1f) {
                    drawArc(
                        color = primaryColor.copy(alpha = 0.25f),
                        startAngle = startAngle,
                        sweepAngle = usedSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round)
                    )

                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                primaryColor,
                                primaryColor.copy(alpha = 0.8f),
                                primaryColor
                            )
                        ),
                        startAngle = startAngle,
                        sweepAngle = usedSweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            // Subtle gradient overlay for depth
            rotate(degrees = animationProgress * 360f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1000.0)).toInt()
        .coerceIn(0, units.lastIndex)
    val value = bytes / Math.pow(1000.0, digitGroups.toDouble())
    return "%.1f %s".format(value, units[digitGroups])
}
