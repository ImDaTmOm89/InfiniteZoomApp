package com.example.infinitedraw.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** U1: the app's dark minimal theme. */
@Composable
fun InfiniteDrawTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            background = Color.Black,
            surface = surfaceRaised,
            onSurface = textPrimary,
            onBackground = textPrimary,
            error = danger,
        ),
        content = content
    )
}
