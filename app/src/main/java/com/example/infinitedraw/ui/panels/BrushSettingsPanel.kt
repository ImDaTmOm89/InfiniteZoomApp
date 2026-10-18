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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.infinitedraw.model.BrushType
import com.example.infinitedraw.ui.DrawingViewModel

@Composable
fun BrushSettingsPanel(vm: DrawingViewModel) {
    var type by remember { mutableStateOf(vm.brushType) }
    var size by remember { mutableStateOf(vm.brushSizePx) }
    var pressure by remember { mutableStateOf(vm.usePressure) }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Brush", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(BrushType.PEN, BrushType.MARKER, BrushType.AIRBRUSH).forEach { t ->
                val selected = t == type
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) androidx.compose.ui.graphics.Color(0x332196F3)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable { type = t; vm.brushType = t }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }

        Text("Size ${size.roundToInt()}px")
        Slider(value = size, onValueChange = { size = it; vm.brushSizePx = it }, valueRange = 1f..80f)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pressure-sensitive", Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = pressure,
                onCheckedChange = { pressure = it; vm.usePressure = it }
            )
        }

        Spacer(Modifier.height(4.dp))
        Text("Eraser", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use eraser", Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = vm.eraserMode,
                onCheckedChange = { vm.eraserMode = it }
            )
        }
    }
}

private fun Float.roundToInt(): Int = this.toInt()
