package com.example.infinitedraw.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.theme.textPrimary
import kotlinx.coroutines.delay
import kotlin.math.log10

/**
 * U6: "Depth n - x<mag>" chip at the bottom center. Appears when depth or the
 * zoom bucket changes (cameraTick drives recomposition) and fades out 1.5 s
 * after the last change. It hides itself while drawing (reads uiVisible but is
 * NOT wrapped in it, so it can appear mid-gesture when panning/zooming).
 */
@Composable
fun ZoomChip(vm: DrawingViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val tick = vm.cameraTick   // subscribe to camera movement
    val depth = vm.engine.depth()
    val mag = vm.engine.zoomInRoot()
    val zoomBucket = (log10(mag) * 20).toInt()

    var shown by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(depth, zoomBucket) {
        if (!initialized) { initialized = true; return@LaunchedEffect }   // don't show at launch
        shown = true
        delay(1500)
        shown = false
    }

    if (!shown || !vm.uiVisible) return
    val magText = if (mag < 1000) String.format("%.1f", mag)
        else String.format("%.1e", mag).replace(Regex("e\\+?0*(\\d+)"), "e$1")
    PanelSurface(modifier = modifier) {
        Text(
            "Depth $depth - x$magText",
            fontSize = 12.sp,
            color = textPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
