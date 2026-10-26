package com.example.infinitedraw.ui.panels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.infinitedraw.model.Layer
import com.example.infinitedraw.model.LayerBlend
import com.example.infinitedraw.ui.DrawingViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayersPanel(vm: DrawingViewModel) {
    // Step 13: long-press a layer name to rename it (dialog).
    var renameTarget by remember { mutableStateOf<Layer?>(null) }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Layers", style = MaterialTheme.typography.titleMedium)

        // Testing fix: key on layersVersion so every layer edit recomposes the rows
        // (the panel otherwise shows stale icons/labels — engine state is not Compose state).
        key(vm.layersVersion) {
            vm.project.layers.asReversed().forEach { layer ->
            val selected = layer.id == vm.activeLayerId
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color(0x222196F3) else Color.Transparent)
                    .clickable { vm.activeLayerId = layer.id }
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // eye toggle (undoable via SetLayerProps)
                    Text(if (layer.visible) "👁" else "🚫", Modifier.clickable { vm.toggleLayerVisible(layer.id) })
                    // Step 13 lock toggle (was missing from the UI until the testing pass)
                    Text(if (layer.locked) "🔒" else "🔓", Modifier.clickable { vm.toggleLayerLocked(layer.id) })
                    Text(layerName(layer), Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = { vm.activeLayerId = layer.id },
                            onLongClick = { renameTarget = layer }
                        ))
                    Text("▲", Modifier.clickable { vm.moveLayer(layer.id, up = true) })
                    Text("▼", Modifier.clickable { vm.moveLayer(layer.id, up = false) })
                    Text("🗑", Modifier.clickable { vm.removeLayer(layer.id) })
                }
                Text("Opacity ${(layer.opacity * 100).toInt()}%")
                Slider(
                    value = layer.opacity,
                    onValueChange = { vm.setLayerOpacity(layer.id, it) },
                    onValueChangeFinished = { vm.commitLayerOpacity() },
                    modifier = Modifier.height(44.dp)   // testing fix: M3 touch target height
                )
                Text("Blend: ${layer.blend.name.lowercase().replaceFirstChar { it.uppercase() }}")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LayerBlend.entries.forEach { b ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (layer.blend == b) Color(0x332196F3) else Color.Transparent)
                                .clickable { vm.setLayerBlend(layer.id, b) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(b.name.take(2), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("+ Add Layer", Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x222196F3))
            .clickable { vm.addLayer() }
            .padding(8.dp))
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename layer") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { Text("OK", Modifier.clickable { vm.renameLayer(target.id, text); renameTarget = null }) },
            dismissButton = { Text("Cancel", Modifier.clickable { renameTarget = null }) }
        )
    }
}

private fun layerName(l: Layer): String = l.name
