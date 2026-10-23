package com.example.infinitedraw.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.infinitedraw.ui.DrawingViewModel

/**
 * Step 21: Selection panel. The scale slider commits ONE ScaleSelection command
 * when released (the % label is the live feedback), then snaps back to 100%, so
 * repeated slider releases compound as separate undo steps.
 */
@Composable
fun SelectionPanel(vm: DrawingViewModel) {
    var scale by remember { mutableStateOf(1f) }
    val sel = vm.selection

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Selection", style = MaterialTheme.typography.titleMedium)
        if (sel == null) {
            Text("Tap an item with the Select tool to select it.")
        } else {
            Text(
                if (sel.strokeId != null) "Stroke on level ${sel.levelId}"
                else "Image on level ${sel.levelId}"
            )
            Text("Scale ${(scale * 100).toInt()}%")
            Slider(
                value = scale,
                onValueChange = { scale = it },
                onValueChangeFinished = {
                    if (scale != 1f) { vm.scaleSelection(scale); scale = 1f }
                },
                valueRange = 0.25f..4f
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color(0x33F44336))
                    .clickable(enabled = sel != null) { vm.deleteSelection() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Delete") }
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color(0x332196F3))
                    .clickable(enabled = sel != null) { vm.deselect() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Deselect") }
        }
    }
}
