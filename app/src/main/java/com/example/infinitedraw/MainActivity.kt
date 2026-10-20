package com.example.infinitedraw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.example.infinitedraw.ui.panels.BrushSettingsPanel
import com.example.infinitedraw.ui.panels.ColorPickerPanel
import com.example.infinitedraw.ui.panels.LayersPanel
import com.example.infinitedraw.ui.panels.PaperPanel
import com.example.infinitedraw.storage.ProjectStorage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class PanelType { COLOR, BRUSH, LAYERS, PAPER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen(vm: DrawingViewModel = viewModel()) {
    var fingerDraws by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<PanelType?>(null) }
    var size by remember { mutableStateOf(vm.brushSizePx) }
    var viewW by remember { mutableStateOf(1080) }
    var viewH by remember { mutableStateOf(1920) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addImage(uri, viewW, viewH)
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        val last = ProjectStorage.lastProjectName(context)
        if (last != null) ProjectStorage.load(context, last, vm.project, vm.engine)
        vm.redraw?.invoke()
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) vm.saveNow()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); vm.saveNow() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> DrawingView(ctx, vm) },
            modifier = Modifier.fillMaxSize(),
            update = { v -> viewW = v.width; viewH = v.height }
        )

        // Toolbar (top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("U", Modifier
                .padding(8.dp)
                .clickable { vm.undo() })
            Text("R", Modifier
                .padding(8.dp)
                .clickable { vm.redo() })
            Text("C", Modifier
                .padding(8.dp)
                .clickable { panel = if (panel == PanelType.COLOR) null else PanelType.COLOR })
            Text("B", Modifier
                .padding(8.dp)
                .clickable { panel = if (panel == PanelType.BRUSH) null else PanelType.BRUSH })
            Text("L", Modifier
                .padding(8.dp)
                .clickable { panel = if (panel == PanelType.LAYERS) null else PanelType.LAYERS })
            Text("P", Modifier
                .padding(8.dp)
                .clickable { panel = if (panel == PanelType.PAPER) null else PanelType.PAPER })
            Text(if (fingerDraws) "F+" else "F-", Modifier
                .padding(8.dp)
                .clickable { fingerDraws = !fingerDraws; vm.fingerDraws = fingerDraws })
            Text("⟲", Modifier
                .padding(8.dp)
                .clickable {
                    vm.engine.camera.zoom = 1.0; vm.engine.camera.cx = 0.0; vm.engine.camera.cy = 0.0
                    vm.redraw?.invoke()
                })
            Text("Img", Modifier
                .padding(8.dp)
                .clickable {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            Text("⬇", Modifier
                .padding(8.dp)
                .clickable {
                    vm.exportPng(viewW, viewH) { path ->
                        if (path != null) android.widget.Toast.makeText(context, "Saved: $path", android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
        }

        // Left strip: size slider
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(8.dp)
        ) {
            Text("Size")
            Slider(
                value = size,
                onValueChange = { size = it; vm.brushSizePx = it },
                valueRange = 1f..80f,
                modifier = Modifier.width(120.dp)
            )
        }

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
                }
            }
        }
    }
}