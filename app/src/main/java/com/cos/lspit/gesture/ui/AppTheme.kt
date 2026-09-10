package com.cos.lspit.gesture.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.cos.lspit.gesture.config.UIPreferences
import com.cos.lspit.gesture.config.UIPreferences.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }
val LocalThemeModeSetter = staticCompositionLocalOf<(ThemeMode) -> Unit> { {} }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(UIPreferences.loadThemeMode(context)) }
    val setMode: (ThemeMode) -> Unit = { newMode ->
        mode = newMode
        UIPreferences.saveThemeMode(context, newMode)
    }
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val controller = remember(mode) {
        ThemeController(
            colorSchemeMode = when (mode) {
                ThemeMode.SYSTEM -> ColorSchemeMode.System
                ThemeMode.LIGHT -> ColorSchemeMode.Light
                ThemeMode.DARK -> ColorSchemeMode.Dark
            }
        )
    }
    CompositionLocalProvider(
        LocalThemeMode provides mode,
        LocalThemeModeSetter provides setMode
    ) {
        MiuixTheme(controller = controller) {
            MaterialTheme(
                colorScheme = if (dark) darkColorScheme() else lightColorScheme()
            ) { content() }
        }
    }
}