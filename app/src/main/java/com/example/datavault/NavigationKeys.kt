package com.example.datavault

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Dashboard : Screen("dashboard", "Inicio", Icons.Default.Dashboard)
    object Explorer : Screen("explorer", "Archivos", Icons.Default.Folder)
    object Analysis : Screen("analysis", "Análisis", Icons.Default.Analytics)
    object Favorites : Screen("favorites", "Favoritos", Icons.Default.Star)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
    object Vault : Screen("vault", "Bóveda", Icons.Default.Lock)

    companion object {
        val items get() = listOf(Dashboard, Explorer, Analysis, Favorites, Settings)
    }
}
