package com.example.infinitedraw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.infinitedraw.input.DrawingView
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.theme.InfiniteDrawTheme
import com.example.infinitedraw.ui.components.FloatingToolPanel
import com.example.infinitedraw.ui.components.TopActionPill
import com.example.infinitedraw.ui.panels.BrushSettingsPanel
import com.example.infinitedraw.ui.panels.ColorPickerPanel
import com.example.infinitedraw.ui.panels.LayersPanel
import com.example.infinitedraw.ui.panels.PaperPanel
import com.example.infinitedraw.ui.panels.SelectionPanel
import com.example.infinitedraw.storage.ProjectStorage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class PanelType { COLOR, BRUSH, LAYERS, PAPER, SELECTION }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // U1: edge-to-edge, hide the system bars (swiping from an edge shows them briefly).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            InfiniteDrawTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen(vm: DrawingViewModel = viewModel()) {
    var panel by remember { mutableStateOf<PanelType?>(null) }
    var viewW by remember { mutableStateOf(1080) }
    var viewH by remember { mutableStateOf(1920) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.addImage(uri, vm.viewW.takeIf { it > 0 } ?: 1440, vm.viewH.takeIf { it > 0 } ?: 2304) { error ->
                android.widget.Toast.makeText(context,
                    error ?: "Image imported",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // Step 23: .uzw export / import launchers.
    val uzwExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            vm.exportUzw(uri) { err ->
                android.widget.Toast.makeText(context,
                    err ?: "Project exported",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val uzwImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importUzw(uri) { err ->
                android.widget.Toast.makeText(context,
                    err ?: "Project imported",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        val last = ProjectStorage.lastProjectName(context)
        if (last != null) ProjectStorage.load(context, last, vm.project, vm.engine, vm.bitmaps)
        vm.activeLayerId = vm.project.activeLayerId   // testing fix: restore the active layer
        vm.redraw?.invoke()
    }
    // Step 21: the Selection panel follows the selection (opens on pick, closes on deselect).
    LaunchedEffect(vm.selection) {
        if (vm.selection != null) panel = PanelType.SELECTION
        else if (panel == PanelType.SELECTION) panel = null
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) vm.saveNow()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); vm.saveNow() }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val containerW = constraints.maxWidth
        val containerH = constraints.maxHeight
        AndroidView(
            factory = { ctx -> DrawingView(ctx, vm) },
            modifier = Modifier.fillMaxSize(),
            update = { v -> viewW = v.width; viewH = v.height }
        )

        // U4: the floating tool panel replaces the old left strip + size slider.
        FloatingToolPanel(vm, containerW, containerH, onOpenColor = { panel = PanelType.COLOR })

        // U5: top action pill (undo/redo/layers/menu). Settings arrives with U9;
        // My worlds / New world / Places arrive with U12 / U18 (noted in PROGRESS.md).
        TopActionPill(
            vm = vm,
            layersOpen = panel == PanelType.LAYERS,
            onUndo = { vm.undo() },
            onRedo = { vm.redo() },
            onToggleLayers = { panel = if (panel == PanelType.LAYERS) null else PanelType.LAYERS },
            onInsertImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPaper = { panel = if (panel == PanelType.PAPER) null else PanelType.PAPER },
            onExportPicture = {
                vm.exportPng(vm.viewW.takeIf { it > 0 } ?: 1440, vm.viewH.takeIf { it > 0 } ?: 2304) { path ->
                    android.widget.Toast.makeText(context,
                        if (path != null) "Picture saved" else "Export failed",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onExportWorldFile = { uzwExport.launch("${vm.project.name}.uzw") },
            onImportWorldFile = { uzwImport.launch(arrayOf("*/*")) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        )

        // Right panel
        panel?.let { p ->
            Card(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(8.dp)
                    .width(280.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                when (p) {
                    PanelType.COLOR -> ColorPickerPanel(vm)
                    PanelType.BRUSH -> BrushSettingsPanel(vm)
                    PanelType.LAYERS -> LayersPanel(vm)
                    PanelType.PAPER -> PaperPanel(vm)
                    PanelType.SELECTION -> SelectionPanel(vm)
                }
            }
        }
    }
}