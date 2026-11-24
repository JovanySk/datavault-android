package com.example.datavault

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.datavault.ui.screens.ai.FileAIScreen
import com.example.datavault.ui.screens.analysis.AnalysisScreen
import com.example.datavault.ui.screens.dashboard.DashboardScreen
import com.example.datavault.ui.screens.explorer.ExplorerScreen
import com.example.datavault.ui.screens.favorites.FavoritesScreen
import com.example.datavault.ui.screens.settings.SettingsScreen
import com.example.datavault.ui.screens.vault.VaultScreen

// Represents an AI navigation destination with file info embedded in route
private const val ROUTE_AI_PREFIX = "ai/"

@Composable
fun MainNavigation() {
    var selectedScreen by rememberSaveable { mutableStateOf(Screen.Dashboard.route) }
    val context = LocalContext.current
    val application = context.applicationContext as Application

    // Routes where the bottom nav bar should be hidden
    val hideNavBar = selectedScreen == Screen.Vault.route ||
            selectedScreen.startsWith(ROUTE_AI_PREFIX)

    Scaffold(
        bottomBar = {
            if (!hideNavBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Screen.items.forEach { screen ->
                        val selected = selectedScreen == screen.route
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            selected = selected,
                            onClick = { selectedScreen = screen.route },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedScreen,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 4 }) togetherWith
                            (fadeOut() + slideOutHorizontally { -it / 4 })
                },
                label = "screen_transition"
            ) { route ->
                when {
                    route == Screen.Dashboard.route ->
                        DashboardScreen(onNavigateToVault = { selectedScreen = Screen.Vault.route })
                    route == Screen.Explorer.route ->
                        ExplorerScreen(
                            onOpenAI = { filePath, mimeType ->
                                // Encode path in route: ai/mimeType|filePath
                                selectedScreen = "$ROUTE_AI_PREFIX${mimeType}|${filePath}"
                            }
                        )
                    route == Screen.Analysis.route -> AnalysisScreen()
                    route == Screen.Favorites.route -> FavoritesScreen()
                    route == Screen.Settings.route -> SettingsScreen()
                    route == Screen.Vault.route ->
                        VaultScreen(onBack = { selectedScreen = Screen.Dashboard.route })
                    route.startsWith(ROUTE_AI_PREFIX) -> {
                        val payload = route.removePrefix(ROUTE_AI_PREFIX)
                        val separatorIndex = payload.indexOf('|')
                        val mimeType = if (separatorIndex >= 0) payload.substring(0, separatorIndex) else "application/octet-stream"
                        val filePath = if (separatorIndex >= 0) payload.substring(separatorIndex + 1) else ""
                        FileAIScreen(
                            filePath = filePath,
                            mimeType = mimeType,
                            onBack = { selectedScreen = Screen.Explorer.route }
                        )
                    }
                    else -> DashboardScreen(onNavigateToVault = { selectedScreen = Screen.Vault.route })
                }
            }
        }
    }
}
