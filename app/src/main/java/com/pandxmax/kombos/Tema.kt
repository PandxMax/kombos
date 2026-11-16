package com.pandxmax.kombos

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Claro = lightColorScheme(
    primary = Color(0xFF0F6B5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8E1),
    onPrimaryContainer = Color(0xFF07201B),
    secondaryContainer = Color(0xFFCDE8E1),
    onSecondaryContainer = Color(0xFF07201B),
    background = Color(0xFFEEF0EC),
    onBackground = Color(0xFF16211F),
    surface = Color.White,
    onSurface = Color(0xFF16211F),
    onSurfaceVariant = Color(0xFF5D6B67),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F7F5),
    surfaceContainer = Color(0xFFF2F4F1),
    surfaceContainerHigh = Color(0xFFF2F4F1),
    surfaceContainerHighest = Color(0xFFE6EAE6),
    outline = Color(0xFF8E9B97),
    outlineVariant = Color(0xFFD3D9D4),
    error = Color(0xFFB3392F)
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF3FB8A2),
    onPrimary = Color(0xFF07201B),
    primaryContainer = Color(0xFF0F4F44),
    onPrimaryContainer = Color(0xFFCDE8E1),
    secondaryContainer = Color(0xFF0F4F44),
    onSecondaryContainer = Color(0xFFCDE8E1),
    background = Color(0xFF101816),
    onBackground = Color(0xFFE8EEE9),
    surface = Color(0xFF18231F),
    onSurface = Color(0xFFE8EEE9),
    onSurfaceVariant = Color(0xFF9AAAA4),
    surfaceContainerLowest = Color(0xFF0C1311),
    surfaceContainerLow = Color(0xFF151F1C),
    surfaceContainer = Color(0xFF1C2824),
    surfaceContainerHigh = Color(0xFF1C2824),
    surfaceContainerHighest = Color(0xFF24322D),
    outline = Color(0xFF5B6B66),
    outlineVariant = Color(0xFF2B3A35),
    error = Color(0xFFEF7D72)
)

@Composable
fun dorado(): Color = if (isSystemInDarkTheme()) Color(0xFFE0B04A) else Color(0xFFB8861B)

@Composable
fun KombosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        content = content
    )
}
