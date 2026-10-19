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
import com.example.infinitedraw.ui.Tool

@Composable
fun BrushSettingsPanel(vm: DrawingViewModel) {
    var size by remember { mutableStateOf(vm.brushSizePx) }
    var pressure by remember { mutableStateOf(vm.usePressure) }

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Brush", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(BrushType.PEN, BrushType.MARKER, BrushType.AIRBRUSH).forEach { t ->
                val tool = when (t) {
                    BrushType.MARKER -> Tool.MARKER
                    BrushType.AIRBRUSH -> Tool.AIRBRUSH
                    else -> Tool.PEN
                }
                val selected = vm.tool == tool
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) androidx.compose.ui.graphics.Color(0x332196F3)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                        .clickable { vm.tool = tool }
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
        Text("Tools", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val eraserSelected = vm.tool == Tool.ERASER
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (eraserSelected) androidx.compose.ui.graphics.Color(0x332196F3)
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .clickable { vm.tool = if (eraserSelected) Tool.PEN else Tool.ERASER }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Eraser") }
        }
    }
}

private fun Float.roundToInt(): Int = this.toInt()
