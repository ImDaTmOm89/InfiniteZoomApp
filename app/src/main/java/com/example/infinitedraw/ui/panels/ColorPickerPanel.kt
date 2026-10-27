package com.example.infinitedraw.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.components.GradientSlider
import com.example.infinitedraw.ui.components.PanelSurface
import com.example.infinitedraw.ui.components.SectionTitle
import com.example.infinitedraw.ui.components.UiIcon
import com.example.infinitedraw.ui.theme.accent
import com.example.infinitedraw.ui.theme.textPrimary

private fun toHsvA(argb: Int): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb and 0xFFFFFF, hsv)
    return floatArrayOf(hsv[0], hsv[1], hsv[2], (argb ushr 24) / 255f)
}

private fun hsvToArgb(h: Float, s: Float, v: Float, a: Float): Int =
    (((a * 255).toInt().coerceIn(0, 255)) shl 24) or
        (android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) and 0xFFFFFF)

private fun hueColor(deg: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(deg, 1f, 1f)))

private val basics = listOf(
    0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFFFF9800.toInt(),
    0xFFFFEB3B.toInt(), 0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFF9C27B0.toInt(),
)

/** U7: the color panel — checkerboard preview + hex, four gradient sliders, recent + basics swatches. */
@Composable
fun ColorPickerPanel(vm: DrawingViewModel, onClose: () -> Unit) {
    val init = remember { toHsvA(vm.color) }
    var h by remember { mutableStateOf(init[0]) }
    var s by remember { mutableStateOf(init[1]) }
    var v by remember { mutableStateOf(init[2]) }
    var a by remember { mutableStateOf(init[3]) }

    fun apply(nh: Float, ns: Float, nv: Float, na: Float) {
        h = nh; s = ns; v = nv; a = na
        vm.color = hsvToArgb(nh, ns, nv, na)
    }
    fun pick(argb: Int) {
        val e = toHsvA(argb)
        h = e[0]; s = e[1]; v = e[2]; a = e[3]
        vm.color = argb
    }

    val opaque = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    val argb = hsvToArgb(h, s, v, a)
    val hex = String.format("#%06X", opaque and 0xFFFFFF)
    val alphaPct = (a * 100).toInt()
    val hexLabel = if (alphaPct < 100) "$hex  ${alphaPct}%" else hex

    PanelSurface(modifier = Modifier.width(300.dp)) {
        Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // 1. Preview swatch over a checkerboard + hex readout + close button.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .drawBehind {   // 8dp checkerboard so transparency is visible
                            val sq = 8.dp.toPx()
                            var row = 0
                            var y = 0f
                            while (y < size.height) {
                                var col = 0
                                var x = 0f
                                while (x < size.width) {
                                    drawRect(
                                        if ((row + col) % 2 == 0) Color(0xFFC8C8C8) else Color(0xFFF0F0F0),
                                        topLeft = Offset(x, y),
                                        size = Size(minOf(sq, size.width - x), minOf(sq, size.height - y)),
                                    )
                                    x += sq; col++
                                }
                                y += sq; row++
                            }
                        }
                ) {
                    Box(Modifier.fillMaxSize().background(Color(argb)))
                }
                Spacer(Modifier.size(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(hexLabel, fontSize = 12.sp, color = textPrimary)
                    Box(
                        // U9 checklist: 48dp touch target (was 32dp).
                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        UiIcon(icon = UiIcon.CLOSE, tint = textPrimary, contentDescription = "Close color panel")
                    }
                }
            }

            // 2. Four gradient sliders.
            SectionTitle("Hue")
            GradientSlider(
                value = (h / 360f).coerceIn(0f, 1f),
                onChange = { apply(it * 360f, s, v, a) },
                brush = Brush.horizontalGradient(listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { hueColor(it) }),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("Saturation")
            GradientSlider(
                value = s,
                onChange = { apply(h, it, v, a) },
                brush = Brush.horizontalGradient(listOf(Color.White, hueColor(h))),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("Brightness")
            GradientSlider(
                value = v,
                onChange = { apply(h, s, it, a) },
                brush = Brush.horizontalGradient(listOf(Color.Black, hueColor(h))),
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("Opacity")
            GradientSlider(
                value = a,
                onChange = { apply(h, s, v, it) },
                brush = Brush.horizontalGradient(listOf(Color(opaque).copy(alpha = 0f), Color(opaque))),
                modifier = Modifier.fillMaxWidth(),
            )

            // 3. Recent + basics swatch rows (32dp circles, 6dp gaps, accent ring on the selected).
            if (vm.recentColors.isNotEmpty()) {
                SectionTitle("Recent")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.recentColors.take(8).forEach { c -> Swatch(c, c == vm.color, onPick = { pick(c) }) }
                }
            }
            SectionTitle("Basics")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                basics.forEach { c -> Swatch(c, c == vm.color, onPick = { pick(c) }) }
            }
        }
    }
}

@Composable
private fun Swatch(argb: Int, selected: Boolean, onPick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(2.dp, if (selected) accent else Color.Transparent, CircleShape)
            .clickable(onClick = onPick)
    )
}
