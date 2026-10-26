package com.example.infinitedraw.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infinitedraw.ui.theme.textPrimary
import com.example.infinitedraw.ui.theme.textSecondary

/** U6: the one-time "how this app works" card. Any tap dismisses it. */
@Composable
fun FirstRunHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    PanelSurface(modifier = modifier.clickable(onClick = onDismiss)) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Pen draws", fontSize = 16.sp, color = textPrimary)
            Text("One finger pans", fontSize = 16.sp, color = textSecondary)
            Text("Two fingers zoom", fontSize = 16.sp, color = textSecondary)
        }
    }
}
