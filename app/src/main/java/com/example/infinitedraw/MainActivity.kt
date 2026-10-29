package com.example.infinitedraw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.infinitedraw.input.DrawingView
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.theme.InfiniteDrawTheme
import com.example.infinitedraw.ui.components.FloatingToolPanel
import com.example.infinitedraw.ui.components.TopActionPill
import com.example.infinitedraw.ui.components.ZoomChip
import com.example.infinitedraw.ui.components.FirstRunHint
import com.example.infinitedraw.ui.components.PlaceCard
import com.example.infinitedraw.ui.components.Breadcrumb
import com.example.infinitedraw.ui.components.PanelSurface
import com.example.infinitedraw.ui.theme.textPrimary
import com.example.infinitedraw.ui.panels.BrushSettingsPanel
import com.example.infinitedraw.ui.panels.ColorPickerPanel
import com.example.infinitedraw.ui.panels.LayersPanel
import com.example.infinitedraw.ui.panels.PaperPanel
import com.example.infinitedraw.ui.panels.SelectionPanel
import com.example.infinitedraw.ui.panels.SettingsPanel
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.infinitedraw.storage.ProjectStorage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class PanelType { COLOR, BRUSH, LAYERS, PAPER, SELECTION, SETTINGS }

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
    // U9: rememberSaveable so the open panel survives rotation (checklist #5).
    var panel by rememberSaveable { mutableStateOf<PanelType?>(null) }
    // U11: place UI state.
    var renamePlace by remember { mutableStateOf(false) }
    var arrival by remember { mutableStateOf<String?>(null) }
    var arrivalShownFor by remember { mutableStateOf<Int?>(null) }
    var nudgeVisible by remember { mutableStateOf(false) }
    val nudgedLevels = remember { mutableSetOf<Int>() }
    var lastNudgeAt by remember { mutableStateOf(0L) }
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
        // U6: all chrome fades while drawing (removed from composition when hidden).
        AnimatedVisibility(visible = vm.uiVisible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            FloatingToolPanel(
                vm, containerW, containerH,
                onOpenColor = { vm.flyoutOpen = false; panel = PanelType.COLOR },
                colorPanelOpen = panel == PanelType.COLOR,
                onCloseColor = { panel = null },
            )
        }

        // U5: top action pill (undo/redo/layers/menu). Settings arrives with U9;
        // My worlds / New world / Places arrive with U12 / U18 (noted in PROGRESS.md).
        AnimatedVisibility(
            visible = vm.uiVisible,
            enter = fadeIn(tween(150)), exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        ) {
            TopActionPill(
                vm = vm,
                layersOpen = panel == PanelType.LAYERS,
                settingsOpen = panel == PanelType.SETTINGS,
                onUndo = { vm.undo() },
                onRedo = { vm.redo() },
                onToggleLayers = { panel = if (panel == PanelType.LAYERS) null else PanelType.LAYERS },
                onToggleSettings = { panel = if (panel == PanelType.SETTINGS) null else PanelType.SETTINGS },
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
            )
        }

        // U6: zoom indicator at the bottom center (self-managed visibility).
        // U6: the zoom chip hides for good when Settings turns it off.
        if (vm.showZoomChip) {
            ZoomChip(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        }

        // ---- U11: places ----
        // Place card + breadcrumb, top-left; hides with the chrome while drawing.
        AnimatedVisibility(
            visible = vm.uiVisible,
            enter = fadeIn(tween(150)), exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp),
        ) {
            Column {
                PlaceCard(vm, onRename = { renamePlace = true })
                Spacer(Modifier.height(6.dp))
                Breadcrumb(vm)
            }
        }

        // Arrival title: entering a level with a non-empty name shows it big,
        // centered — fade in 250ms, hold 1500ms, fade out 400ms. Not while drawing.
        LaunchedEffect(vm.cameraTick) {
            val lvl = vm.engine.camera.level
            if (lvl.id != arrivalShownFor) {
                arrivalShownFor = lvl.id
                if (lvl.name.isNotBlank() && vm.uiVisible) arrival = lvl.name
            }
        }
        LaunchedEffect(arrival) {
            if (arrival != null) { kotlinx.coroutines.delay(1750); arrival = null }
        }
        AnimatedVisibility(
            visible = arrival != null,
            enter = fadeIn(tween(250)), exit = fadeOut(tween(400)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (containerH * 0.22f).roundToInt()) },
        ) {
            Text(
                arrival ?: "",
                fontSize = 28.sp,
                color = textPrimary.copy(alpha = 0.85f),
            )
        }

        // Naming nudge: 1.2s in an unnamed level created this session by zooming
        // shows "Name this place?" for 4s. Once per level, once per 30s.
        LaunchedEffect(vm.cameraTick) {
            val lvl = vm.engine.camera.level
            if (!nudgeVisible && lvl.name.isBlank() && lvl.id in vm.engine.createdThisSession
                && lvl.id !in nudgedLevels
                && System.currentTimeMillis() - lastNudgeAt > 30000 && vm.uiVisible) {
                val id = lvl.id
                kotlinx.coroutines.delay(1200)
                if (vm.engine.camera.level.id == id && vm.engine.camera.level.name.isBlank() && vm.uiVisible) {
                    nudgedLevels.add(id)
                    lastNudgeAt = System.currentTimeMillis()
                    nudgeVisible = true
                }
            }
        }
        LaunchedEffect(nudgeVisible) {
            if (nudgeVisible) { kotlinx.coroutines.delay(4000); nudgeVisible = false }
        }
        if (nudgeVisible) {
            PanelSurface(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)) {
                Text(
                    "Name this place?",
                    fontSize = 14.sp,
                    color = textPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { nudgeVisible = false; renamePlace = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        // Place rename dialog (card tap / nudge tap).
        if (renamePlace) {
            var placeText by remember { mutableStateOf(vm.engine.camera.level.name) }
            AlertDialog(
                onDismissRequest = { renamePlace = false },
                title = { Text("Name this place", color = textPrimary) },
                text = {
                    OutlinedTextField(
                        value = placeText,
                        onValueChange = { placeText = it.take(40) },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    Text("OK", Modifier.clickable {
                        vm.renameLevel(vm.engine.camera.level.id, placeText)
                        renamePlace = false
                    })
                },
                dismissButton = { Text("Cancel", Modifier.clickable { renamePlace = false }) },
            )
        }

        // U6: first-run hint — any tap or 6 seconds dismisses it.
        if (vm.hintVisible) {
            FirstRunHint(
                onDismiss = { vm.dismissHint() },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        LaunchedEffect(vm.hintVisible) {
            if (vm.hintVisible) { kotlinx.coroutines.delay(6000); vm.dismissHint() }
        }

        // Right panel
        AnimatedVisibility(
            visible = vm.uiVisible,
            enter = fadeIn(tween(150)), exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp),
        ) {
            panel?.let { p ->
                when (p) {
                    // U8: the layers panel has its own PanelSurface — no Card wrap.
                    PanelType.LAYERS -> LayersPanel(vm)
                    // U9: the settings panel, same floating style.
                    PanelType.SETTINGS -> SettingsPanel(vm)
                    PanelType.COLOR -> {}   // U7: the color panel floats beside the tool panel
                    else -> Card(
                        modifier = Modifier.width(280.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        when (p) {
                            PanelType.COLOR, PanelType.LAYERS, PanelType.SETTINGS -> {}
                            PanelType.BRUSH -> BrushSettingsPanel(vm)
                            PanelType.PAPER -> PaperPanel(vm)
                            PanelType.SELECTION -> SelectionPanel(vm)
                        }
                    }
                }
            }
        }
    }
}