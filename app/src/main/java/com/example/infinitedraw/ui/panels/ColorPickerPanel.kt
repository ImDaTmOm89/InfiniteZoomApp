package com.example.infinitedraw.ui.panels

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.infinitedraw.ui.DrawingViewModel
import kotlin.math.roundToInt

@Composable
fun ColorPickerPanel(vm: DrawingViewModel) {
    val initial = vm.color
    var hue by remember { mutableStateOf(hsvOf(initial)[0]) }
    var sat by remember { mutableStateOf(hsvOf(initial)[1]) }
    var `val` by remember { mutableStateOf(hsvOf(initial)[2]) }
    var alpha by remember { mutableStateOf((initial ushr 24) / 255f) }

    val argb = Color.HSVToColor((alpha * 255).roundToInt(), floatArrayOf(hue, sat, `val`))

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Color", style = MaterialTheme.typography.titleMedium)

        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(argb.toInt().toComposeColor())
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        )

        Text("Hue ${hue.roundToInt()}°")
        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
        Text("Saturation ${(sat * 100).roundToInt()}%")
        Slider(value = sat, onValueChange = { sat = it })
        Text("Value ${(`val` * 100).roundToInt()}%")
        Slider(value = `val`, onValueChange = { `val` = it })
        Text("Alpha ${(alpha * 100).roundToInt()}%")
        Slider(value = alpha, onValueChange = { alpha = it })

        Spacer(Modifier.height(8.dp))
        Text("Presets")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PresetSwatches { c ->
                val hsv = hsvOf(c)
                hue = hsv[0]; sat = hsv[1]; `val` = hsv[2]
                alpha = (c ushr 24) / 255f
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Recent")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.recentColors.forEach { c ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(c.toInt().toComposeColor())
                        .clickable {
                            val hsv = hsvOf(c)
                            hue = hsv[0]; sat = hsv[1]; `val` = hsv[2]
                            alpha = (c ushr 24) / 255f
                        }
                )
            }
        }
    }

    // Push live changes into the VM
    vm.color = argb
}

private fun hsvOf(color: Int): FloatArray {
    val hsv = FloatArray(3)
    Color.colorToHSV(color, hsv)
    return hsv
}

private fun Int.toComposeColor(): androidx.compose.ui.graphics.Color =
    androidx.compose.ui.graphics.Color(this)

@Composable
private fun PresetSwatches(onPick: (Int) -> Unit) {
    val presets = listOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFF44336.toInt(), 0xFF4CAF50.toInt(),
        0xFF2196F3.toInt(), 0xFFFFC107.toInt(), 0xFF9C27B0.toInt(), 0xFF795548.toInt()
    )
    presets.forEach { c ->
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(c.toComposeColor())
                .clickable { onPick(c) }
        )
    }
}
