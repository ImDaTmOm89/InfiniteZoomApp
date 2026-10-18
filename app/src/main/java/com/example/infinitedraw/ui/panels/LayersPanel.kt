package com.example.infinitedraw.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.infinitedraw.model.LayerBlend
import com.example.infinitedraw.ui.DrawingViewModel

@Composable
fun LayersPanel(vm: DrawingViewModel) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Layers", style = MaterialTheme.typography.titleMedium)

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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // eye toggle
                    Text(if (layer.visible) "👁" else "🚫", Modifier.clickable { vm.toggleLayerVisible(layer.id) })
                    Text(layer.name, Modifier.weight(1f))
                    Text("▲", Modifier.clickable { vm.moveLayer(layer.id, up = true) })
                    Text("▼", Modifier.clickable { vm.moveLayer(layer.id, up = false) })
                    Text("🗑", Modifier.clickable { vm.removeLayer(layer.id) })
                }
                Text("Opacity ${(layer.opacity * 100).toInt()}%")
                Slider(
                    value = layer.opacity,
                    onValueChange = { vm.setLayerOpacity(layer.id, it) },
                    modifier = Modifier.height(28.dp)
                )
                Text("Blend: ${layer.blend.name.lowercase().replaceFirstChar { it.uppercase() }}")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LayerBlend.entries.forEach { b ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (layer.blend == b) Color(0x332196F3) else Color.Transparent)
                                .clickable {
                                    layer.blend = b
                                    vm.redraw?.invoke()
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(b.name.take(2), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("+ Add Layer", Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x222196F3))
            .clickable { vm.addLayer() }
            .padding(8.dp))
    }
}
