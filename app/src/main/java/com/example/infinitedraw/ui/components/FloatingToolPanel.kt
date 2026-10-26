package com.example.infinitedraw.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.Tool
import com.example.infinitedraw.ui.theme.accentSoft
import com.example.infinitedraw.ui.theme.outline
import com.example.infinitedraw.ui.theme.textPrimary
import com.example.infinitedraw.ui.theme.textSecondary
import kotlin.math.roundToInt

/**
 * U4: the floating tool panel — draggable anywhere, snaps to the nearest left or
 * right edge with a spring, collapsible to a single button, with a size flyout.
 * The panel and flyout only occupy their own bounds (no fillMaxSize input layer).
 */
@Composable
fun FloatingToolPanel(
    vm: DrawingViewModel,
    containerW: Int,
    containerH: Int,
    onOpenColor: () -> Unit,
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.toPx() }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val freeW = (containerW - panelSize.width - 2 * marginPx).coerceAtLeast(1f)
    val freeH = (containerH - panelSize.height - 2 * marginPx).coerceAtLeast(1f)
    var dragging by remember { mutableStateOf(false) }
    val shownX by animateFloatAsState(vm.panelFracX, spring(dampingRatio = 0.8f, stiffness = 400f), label = "px")
    val fx = if (dragging) vm.panelFracX else shownX
    val offsetX = marginPx + fx * freeW
    val offsetY = marginPx + vm.panelFracY * freeH
    val freeWState by rememberUpdatedState(freeW)
    val freeHState by rememberUpdatedState(freeH)

    val toolIcon = mapOf(
        Tool.PEN to UiIcon.PEN, Tool.MARKER to UiIcon.MARKER, Tool.AIRBRUSH to UiIcon.AIRBRUSH,
        Tool.PENCIL to UiIcon.PENCIL, Tool.CHARCOAL to UiIcon.CHARCOAL,
        Tool.ERASER to UiIcon.ERASER, Tool.SELECT to UiIcon.SELECT,
    )
    val panelTools = listOf(
        Tool.PEN, Tool.MARKER, Tool.AIRBRUSH, Tool.PENCIL, Tool.CHARCOAL, Tool.ERASER, Tool.SELECT
    )

    PanelSurface(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .onSizeChanged { panelSize = it }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 1. drag handle: 36x6dp pill inside a 48x20dp touch area.
            Box(
                Modifier
                    .size(48.dp, 20.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                vm.panelFracX = if (vm.panelFracX < 0.5f) 0f else 1f
                                vm.savePanelPrefs()
                            },
                            onDragCancel = { dragging = false },
                        ) { change, drag ->
                            change.consume()
                            vm.panelFracX = (vm.panelFracX + drag.x / freeWState).coerceIn(0f, 1f)
                            vm.panelFracY = (vm.panelFracY + drag.y / freeHState).coerceIn(0f, 1f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(outline))
            }

            // Collapsed: a single button with the current tool; tapping expands.
            AnimatedVisibility(visible = vm.panelCollapsed, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                val current = vm.tool
                ToolButton(
                    icon = toolIcon[current] ?: UiIcon.PEN,
                    label = current.name,
                    selected = false,
                    onClick = { vm.panelCollapsed = false; vm.savePanelPrefs() },
                )
            }

            // Expanded: tools, divider, color, size, collapse.
            AnimatedVisibility(
                visible = !vm.panelCollapsed,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    panelTools.forEach { t ->
                        val selected = vm.tool == t
                        ToolButton(
                            icon = toolIcon.getValue(t),
                            label = t.name,
                            selected = selected,
                            onClick = { if (selected) vm.flyoutOpen = !vm.flyoutOpen else vm.tool = t },
                        )
                    }
                    // 1dp divider, fixed width (fillMaxWidth would stretch the panel to screen width).
                    Box(Modifier.size(40.dp, 1.dp).background(outline))
                    // Color button: 36dp circle in the current color with a light ring.
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onOpenColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(vm.color))
                                .border(2.dp, Color(0x88FFFFFF), CircleShape)
                        )
                    }
                    // Size dot: current brush size (6..40dp) in a 48dp box; tap toggles the flyout.
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.flyoutOpen = !vm.flyoutOpen },
                        contentAlignment = Alignment.Center,
                    ) {
                        val d = vm.brushSizePx.coerceIn(6f, 40f)
                        Box(Modifier.size(d.dp).clip(CircleShape).background(textPrimary))
                    }
                    // Collapse button: chevron points toward the nearest edge side.
                    Box(
                        Modifier.size(48.dp, 32.dp).clip(RoundedCornerShape(14.dp))
                            .clickable { vm.panelCollapsed = true; vm.savePanelPrefs() },
                        contentAlignment = Alignment.Center,
                    ) {
                        UiIcon(
                            icon = if (vm.panelFracX >= 0.5f) UiIcon.CHEVRON_LEFT else UiIcon.CHEVRON_RIGHT,
                            tint = textPrimary,
                            contentDescription = "Collapse panel",
                        )
                    }
                }
            }
        }
    }

    // Flyout: 240dp PanelSurface beside the panel, same top.
    if (vm.flyoutOpen) {
        val gapPx = with(density) { 8.dp.toPx() }
        val flyoutWpx = with(density) { 240.dp.toPx() }
        val flyoutX = if (offsetX + panelSize.width / 2f < containerW / 2f)
            offsetX + panelSize.width + gapPx
        else
            offsetX - flyoutWpx - gapPx
        PanelSurface(
            modifier = Modifier
                .offset { IntOffset(flyoutX.roundToInt(), offsetY.roundToInt()) }
                .width(240.dp)
        ) {
            var size by remember { mutableStateOf(vm.brushSizePx) }
            var pressure by remember { mutableStateOf(vm.usePressure) }
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Size")
                Slider(value = size, onValueChange = { size = it; vm.brushSizePx = it }, valueRange = 1f..80f)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(size.coerceIn(1f, 80f).dp)
                            .clip(CircleShape)
                            .background(Color(vm.color))
                    )
                }
                // Pen-like tools (strokes that honor pressure) get the pressure switch.
                if (vm.tool == Tool.PEN || vm.tool == Tool.PENCIL || vm.tool == Tool.CHARCOAL) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pressure", fontSize = 14.sp, color = textPrimary)
                        Switch(checked = pressure, onCheckedChange = { pressure = it; vm.usePressure = it })
                    }
                }
            }
        }
    }
}
