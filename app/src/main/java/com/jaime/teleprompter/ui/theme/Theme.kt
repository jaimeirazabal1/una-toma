package com.jaime.teleprompter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFFF5A5F),
    onPrimary = Color.White,
    background = Color(0xFF0E0E10),
    surface = Color(0xFF18181B),
    surfaceVariant = Color(0xFF26262B),
)

@Composable
fun TeleprompterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
