package com.example.infinitedraw.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.theme.surface
import com.example.infinitedraw.ui.theme.textPrimary

/**
 * U5: top action pill — UNDO / REDO / LAYERS / MENU in a small floating panel
 * at the top-center. Menu shows existing actions only (world vocabulary);
 * My worlds / New world / Places / Settings arrive with U9 / U12 / U18.
 */
@Composable
fun TopActionPill(
    vm: DrawingViewModel,
    layersOpen: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleLayers: () -> Unit,
    onInsertImage: () -> Unit,
    onPaper: () -> Unit,
    onExportPicture: () -> Unit,
    onExportWorldFile: () -> Unit,
    onImportWorldFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    PanelSurface(modifier = modifier) {
        Row {
            ToolButton(icon = UiIcon.UNDO, label = "Undo", selected = false,
                enabled = vm.canUndo, onClick = onUndo)
            ToolButton(icon = UiIcon.REDO, label = "Redo", selected = false,
                enabled = vm.canRedo, onClick = onRedo)
            ToolButton(icon = UiIcon.LAYERS, label = "Layers", selected = layersOpen,
                onClick = onToggleLayers)
            androidx.compose.foundation.layout.Box {
                ToolButton(icon = UiIcon.MENU, label = "Menu", selected = menuOpen,
                    onClick = { menuOpen = true })
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = surface,
                ) {
                    DropdownMenuItem(text = { Text("Insert image", fontSize = 14.sp, color = textPrimary) },
                        onClick = { menuOpen = false; onInsertImage() })
                    DropdownMenuItem(text = { Text("Paper", fontSize = 14.sp, color = textPrimary) },
                        onClick = { menuOpen = false; onPaper() })
                    DropdownMenuItem(text = { Text("Export picture", fontSize = 14.sp, color = textPrimary) },
                        onClick = { menuOpen = false; onExportPicture() })
                    DropdownMenuItem(text = { Text("Export world file", fontSize = 14.sp, color = textPrimary) },
                        onClick = { menuOpen = false; onExportWorldFile() })
                    DropdownMenuItem(text = { Text("Import world file", fontSize = 14.sp, color = textPrimary) },
                        onClick = { menuOpen = false; onImportWorldFile() })
                }
            }
        }
    }
}
