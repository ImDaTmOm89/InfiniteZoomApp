package com.example.infinitedraw.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** U3: a 28dp gradient slider with a white-ringed thumb; used by the color panel. */
@Composable
fun GradientSlider(
    value: Float,
    onChange: (Float) -> Unit,
    brush: Brush,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    var barWidth by remember { mutableIntStateOf(0) }
    val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }

    Box(
        modifier
            .height(28.dp)
            .onSizeChanged { barWidth = it.width }
            .drawBehind {
                drawRoundRect(brush = brush, cornerRadius = CornerRadius(size.height / 2f))
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    currentOnChange((pos.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    currentOnChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        // White-bordered circular thumb, 24dp, sliding across the bar.
        Box(
            Modifier
                .offset { IntOffset((value * (barWidth - thumbPx)).roundToInt().coerceAtLeast(0), 2.dp.roundToPx()) }
                .size(24.dp)
                .border(3.dp, Color.White, CircleShape)
        )
    }
}
