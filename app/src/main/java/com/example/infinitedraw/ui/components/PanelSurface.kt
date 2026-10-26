package com.example.infinitedraw.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.infinitedraw.ui.theme.outline
import com.example.infinitedraw.ui.theme.surface

/** U3: the app's floating panel surface — dark, rounded, shadowed, outlined. */
@Composable
fun PanelSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = surface,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, outline),
    ) {
        Box(Modifier.padding(8.dp)) { content() }
    }
}
