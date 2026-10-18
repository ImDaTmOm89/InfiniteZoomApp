package com.example.infinitedraw.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.launch
import com.example.infinitedraw.engine.InfiniteZoomEngine
import com.example.infinitedraw.model.AddLayerCommand
import com.example.infinitedraw.model.AddImageCommand
import com.example.infinitedraw.model.AddStrokeCommand
import com.example.infinitedraw.model.BrushType
import com.example.infinitedraw.model.CommandStack
import com.example.infinitedraw.model.Layer
import com.example.infinitedraw.model.Project
import com.example.infinitedraw.model.RemoveLayerCommand
import com.example.infinitedraw.model.ReorderLayerCommand
import com.example.infinitedraw.model.Stroke
import com.example.infinitedraw.model.StrokePoint
import com.example.infinitedraw.model.ZoomLevel

class DrawingViewModel(app: Application) : AndroidViewModel(app) {
    val engine = InfiniteZoomEngine()
    val project = Project("Untitled", mutableListOf(Layer(1, "Layer 1")))
    val commands = CommandStack()
    var fingerDraws = false
    var redraw: (() -> Unit)? = null

    var brushType = BrushType.PEN
    var brushSizePx = 6f
    var color: Int = android.graphics.Color.BLACK
    val recentColors = mutableListOf<Int>()
    var activeLayerId = 1L
        set(value) { field = value; redraw?.invoke() }
    var usePressure = true
    var eraserMode = false          // toggled by UI or hardware eraser

    fun activeLayer(): Layer? = project.layers.firstOrNull { it.id == activeLayerId }

    var active: ArrayList<StrokePoint>? = null
    var activeLevel: ZoomLevel? = null

    fun beginStroke(sx: Float, sy: Float, p: Float, w: Int, h: Int) {
        val l = engine.screenToLocal(sx, sy, w, h)
        activeLevel = engine.camera.level
        active = ArrayList<StrokePoint>().also { it.add(StrokePoint(l[0].toFloat(), l[1].toFloat(), p)) }
        redraw?.invoke()
    }

    fun addPoint(sx: Float, sy: Float, p: Float, w: Int, h: Int) {
        val a = active ?: return
        val l = engine.screenToLocal(sx, sy, w, h)
        val last = a.lastOrNull() ?: return
        val dx = l[0] - last.x; val dy = l[1] - last.y
        val minDist = 1.5 / engine.camera.zoom
        if (dx * dx + dy * dy < minDist * minDist) return
        a.add(StrokePoint(l[0].toFloat(), l[1].toFloat(), p))
        redraw?.invoke()
    }

    fun endStroke() {
        val a = active; val lvl = activeLevel
        active = null
        if (a != null && lvl != null && a.isNotEmpty()) {
            val type = brushType
            val stroke = Stroke(
                engine.nextStrokeId++, activeLayerId,
                type,
                color, brushSizePx / engine.camera.zoom.toFloat(), usePressure, a.toList()
            )
            commands.push(AddStrokeCommand(lvl, stroke))
            if (color !in recentColors) {
                recentColors.add(0, color)
                if (recentColors.size > 8) recentColors.removeAt(recentColors.size - 1)
            }
        }
        redraw?.invoke()
    }

    fun cancelStroke() { active = null; redraw?.invoke() }

    fun undo() { if (commands.undo()) redraw?.invoke() }
    fun redo() { if (commands.redo()) redraw?.invoke() }

    // ---- images ----
    val bitmaps = HashMap<Long, android.graphics.Bitmap>()
    private var nextImageId = 1L

    fun addImage(uri: android.net.Uri, w: Int, h: Int, onDone: () -> Unit = {}) {
        val ctx = getApplication<Application>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        scope.launch {
            try {
                val resolver = ctx.contentResolver
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                while (maxSide / sample > 2048) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = resolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                }
                if (bmp != null) {
                    val imgId = nextImageId++
                    val dir = java.io.File(ctx.filesDir, "projects/${project.name}/images").apply { mkdirs() }
                    java.io.File(dir, "$imgId.png").outputStream().use {
                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmaps[imgId] = bmp
                    val cam = engine.camera
                    val aspect = if (bmp.height > 0) bmp.width.toDouble() / bmp.height else 1.0
                    val imgW = w * 0.5 / cam.zoom
                    val imgH = imgW / aspect
                    val l = engine.screenToLocal(w / 2f, h / 2f, w, h)
                    val img = com.example.infinitedraw.model.PlacedImage(
                        imgId, activeLayerId, "$imgId.png",
                        (l[0] - imgW / 2).toFloat(), (l[1] - imgH / 2).toFloat(),
                        imgW.toFloat(), imgH.toFloat()
                    )
                    commands.push(AddImageCommand(cam.level, img))
                    redraw?.invoke()
                }
            } catch (_: Exception) {
            }
            onDone()
        }
    }

    // ---- layer management ----
    private var nextLayerId = 2L

    fun addLayer() {
        val layer = Layer(nextLayerId++, "Layer ${project.layers.size + 1}")
        commands.push(AddLayerCommand(project, layer))
        activeLayerId = layer.id
    }

    fun removeLayer(id: Long) {
        if (project.layers.size <= 1) return     // must keep at least one
        val layer = project.layers.firstOrNull { it.id == id } ?: return
        commands.push(RemoveLayerCommand(project, layer))
        if (activeLayerId == id) activeLayerId = project.layers.last().id
    }

    fun toggleLayerVisible(id: Long) {
        val l = project.layers.firstOrNull { it.id == id } ?: return
        l.visible = !l.visible
        redraw?.invoke()
    }

    fun setLayerOpacity(id: Long, opacity: Float) {
        val l = project.layers.firstOrNull { it.id == id } ?: return
        l.opacity = opacity.coerceIn(0f, 1f)
        redraw?.invoke()
    }

    fun moveLayer(id: Long, up: Boolean) {
        val idx = project.layers.indexOfFirst { it.id == id }
        if (idx < 0) return
        val target = if (up) idx + 1 else idx - 1
        if (target < 0 || target >= project.layers.size) return
        val layer = project.layers[idx]
        commands.push(ReorderLayerCommand(project, layer, idx, target))
    }
}
