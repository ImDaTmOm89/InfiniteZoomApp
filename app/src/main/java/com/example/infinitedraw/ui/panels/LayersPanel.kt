package com.example.infinitedraw.ui.panels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infinitedraw.model.Layer
import com.example.infinitedraw.model.LayerBlend
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.components.PanelSurface
import com.example.infinitedraw.ui.components.SectionTitle
import com.example.infinitedraw.ui.components.ToolButton
import com.example.infinitedraw.ui.components.UiIcon
import com.example.infinitedraw.ui.theme.accent
import com.example.infinitedraw.ui.theme.accentSoft
import com.example.infinitedraw.ui.theme.surface
import com.example.infinitedraw.ui.theme.textPrimary
import com.example.infinitedraw.ui.theme.textSecondary

/**
 * U8: the layers panel — 300dp PanelSurface, LazyColumn capped at 60% of the
 * screen. Each row: eye + lock ToolButtons, the name (tap = active, long-press
 * = rename) with a 2dp accent outline + accentSoft background on the active
 * row, and an opacity readout on the right that expands the row (one at a time)
 * to the opacity slider + blend dropdown. Footer: add / delete (confirm when
 * the layer has strokes) / move up / move down. Every edit still goes through
 * the Step 10 undoable commands.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LayersPanel(vm: DrawingViewModel) {
    @Suppress("UNUSED_VARIABLE") val ver = vm.layersVersion   // subscribe: every layer edit recomposes
    var renameTarget by remember { mutableStateOf<Layer?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f - 100f).dp   // 60% cap minus title+footer
    val activeHasStrokes = remember(vm.layersVersion) {
        vm.engine.levels.values.any { lvl -> lvl.strokes.any { it.layerId == vm.activeLayerId } }
    }

    PanelSurface(modifier = Modifier.width(300.dp)) {
        Column(Modifier.width(280.dp)) {

            SectionTitle("Layers")

            // U8 testing fix: no weight() — a weighted child inside a wrapping
            // Column only composed ~2 rows regardless of the item count. Cap the
            // list itself; the panel wraps content and scrolls only past 60%.
            // U8 testing fix: LazyColumn would not re-flow its rows on reorders
            // (inserts/edits updated, moves stayed visually stale until relaunch —
            // both with plain keys and version-suffixed keys). The old panel's
            // proven pattern instead: a scrollable Column fully rebuilt on every
            // layer edit via key(). Scroll + 60% cap behave the same.
            Column(
                Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                key(vm.layersVersion) {
                    vm.project.layers.reversed().forEach { layer ->
                        LayerRow(
                            vm = vm,
                            layer = layer,
                            selected = layer.id == vm.activeLayerId,
                            expanded = expandedId == layer.id,
                            onToggleExpand = { expandedId = if (expandedId == layer.id) null else layer.id },
                            onRename = { renameTarget = it },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolButton(icon = UiIcon.PLUS, label = "Add layer", selected = false,
                    onClick = { vm.addLayer() })
                ToolButton(icon = UiIcon.TRASH, label = "Delete layer", selected = false,
                    onClick = { if (activeHasStrokes) confirmDelete = true else vm.removeLayer(vm.activeLayerId) })
                ToolButton(icon = UiIcon.CHEVRON_RIGHT, label = "Move layer up", selected = false,
                    onClick = { vm.moveLayer(vm.activeLayerId, up = true) }, modifier = Modifier.rotate(-90f))
                ToolButton(icon = UiIcon.CHEVRON_RIGHT, label = "Move layer down", selected = false,
                    onClick = { vm.moveLayer(vm.activeLayerId, up = false) }, modifier = Modifier.rotate(90f))
            }
        }
    }

    // Long-press rename dialog.
    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename layer", color = textPrimary) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { Text("OK", Modifier.clickable { vm.renameLayer(target.id, text); renameTarget = null }) },
            dismissButton = { Text("Cancel", Modifier.clickable { renameTarget = null }) },
        )
    }

    // Delete confirmation for a layer that has strokes.
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete layer?", color = textPrimary) },
            text = { Text("This layer has strokes.", color = textSecondary) },
            confirmButton = {
                Text("Delete", Modifier.clickable { vm.removeLayer(vm.activeLayerId); confirmDelete = false })
            },
            dismissButton = { Text("Cancel", Modifier.clickable { confirmDelete = false }) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LayerRow(
    vm: DrawingViewModel,
    layer: Layer,
    selected: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRename: (Layer) -> Unit,
) {
    Column {
        Row(Modifier.height(56.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ToolButton(
                icon = if (layer.visible) UiIcon.EYE else UiIcon.EYE_OFF,
                label = if (layer.visible) "Hide layer" else "Show layer",
                selected = false,
                onClick = { vm.toggleLayerVisible(layer.id) },
            )
            ToolButton(
                icon = if (layer.locked) UiIcon.LOCK else UiIcon.UNLOCK,
                label = if (layer.locked) "Unlock layer" else "Lock layer",
                selected = false,
                onClick = { vm.toggleLayerLocked(layer.id) },
            )
            // Name: tap = make active, long-press = rename. Active row styled.
            Box(
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) accentSoft else Color.Transparent)
                    .border(2.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = { vm.activeLayerId = layer.id },
                        onLongClick = { onRename(layer) },
                    ),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    " " + layer.name,
                    fontSize = 14.sp,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Opacity readout: tap the right side to expand.
            Text(
                "${(layer.opacity * 100).toInt()}%",
                fontSize = 12.sp,
                color = textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 52.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Slider(
                    value = layer.opacity,
                    onValueChange = { vm.setLayerOpacity(layer.id, it) },
                    onValueChangeFinished = { vm.commitLayerOpacity() },
                    modifier = Modifier.height(44.dp),   // testing fix: M3 touch target height (drags are swallowed without it)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Blend", fontSize = 12.sp, color = textSecondary)
                    Box {
                        var blendOpen by remember { mutableStateOf(false) }
                        Text(
                            layer.blend.name.lowercase().replaceFirstChar { it.uppercase() },
                            fontSize = 14.sp,
                            color = textPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { blendOpen = true }
                                .padding(8.dp),
                        )
                        DropdownMenu(
                            expanded = blendOpen,
                            onDismissRequest = { blendOpen = false },
                            containerColor = surface,
                        ) {
                            LayerBlend.entries.forEach { b ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            b.name.lowercase().replaceFirstChar { it.uppercase() },
                                            color = textPrimary,
                                            fontSize = 14.sp,
                                        )
                                    },
                                    onClick = { blendOpen = false; vm.setLayerBlend(layer.id, b) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
