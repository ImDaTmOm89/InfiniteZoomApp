package com.example.infinitedraw.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.infinitedraw.engine.InfiniteZoomEngine
import com.example.infinitedraw.engine.AddImage
import com.example.infinitedraw.engine.AddLayer
import com.example.infinitedraw.engine.AddStroke
import com.example.infinitedraw.engine.DeleteLayer
import com.example.infinitedraw.engine.MoveLayer
import com.example.infinitedraw.engine.RasterCommand
import com.example.infinitedraw.engine.RemoveStrokes
import com.example.infinitedraw.engine.UndoStack
import com.example.infinitedraw.model.BrushType
import com.example.infinitedraw.model.Layer
import com.example.infinitedraw.model.Project
import com.example.infinitedraw.model.Stroke
import com.example.infinitedraw.model.StrokePoint
import com.example.infinitedraw.model.ZoomLevel
import com.example.infinitedraw.storage.Autosave
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlin.math.hypot

enum class Tool { PEN, MARKER, AIRBRUSH, ERASER, OIL, MIX, SMUDGE, RERASER }

/** Raster (Phase 2) tools paint into 256x256 tiles instead of creating vector strokes. */
fun Tool.isRaster(): Boolean = this == Tool.OIL || this == Tool.MIX || this == Tool.SMUDGE || this == Tool.RERASER

class DrawingViewModel(app: Application) : AndroidViewModel(app) {
    val engine = InfiniteZoomEngine()
    val project = Project("Untitled", mutableListOf(Layer(1, "Layer 1")))
    val commands = UndoStack()
    val autosave = Autosave(app, project, engine)
    var fingerDraws = false
    var redraw: (() -> Unit)? = null

    var brushType = BrushType.PEN
    var brushSizePx = 6f
    var color: Int = android.graphics.Color.BLACK
    val recentColors = mutableListOf<Int>()
    var activeLayerId = 1L
        set(value) { field = value; redraw?.invoke() }
    var usePressure = true
    private var preEraserTool = Tool.PEN   // tool to restore after hardware eraser lifts
    var eraserMode: Boolean
        get() = tool == Tool.ERASER
        set(v) {   // hardware eraser toggle (vector tools)
            if (v) { preEraserTool = if (tool.isRaster()) Tool.PEN else tool; tool = Tool.ERASER }
            else tool = preEraserTool
        }
    var rasterEraserMode: Boolean
        get() = tool == Tool.RERASER
        set(v) {   // hardware eraser in raster tools
            if (v) { preEraserTool = if (!tool.isRaster() && tool != Tool.ERASER) tool else Tool.OIL; tool = Tool.RERASER }
            else tool = preEraserTool
        }
    var tool: Tool by androidx.compose.runtime.mutableStateOf(Tool.PEN)   // active tool; ERASER clears strokes
    val pendingRemoved = mutableListOf<Stroke>()   // strokes marked by the eraser, hidden until commit

    /** Phase 2b: pre-dab pixel snapshots collected while a raster stroke is active. */
    val rasterBefore = mutableListOf<com.example.infinitedraw.render.RasterTiles.TileSnapshot>()

    fun activeLayer(): Layer? = project.layers.firstOrNull { it.id == activeLayerId }

    var active: ArrayList<StrokePoint>? = null
    var activeLevel: ZoomLevel? = null

    fun beginStroke(sx: Float, sy: Float, p: Float, w: Int, h: Int) {
        // Plan Step 10: the active layer must be visible and unlocked to draw.
        val layer = activeLayer()
        if (layer == null || !layer.visible || layer.locked) {
            active = null
            return
        }
        val l = engine.screenToLocal(sx, sy, w, h)
        activeLevel = engine.camera.level
        active = ArrayList<StrokePoint>().also { it.add(StrokePoint(l[0].toFloat(), l[1].toFloat(), p)) }
        pendingRemoved.clear()
        rasterBefore.clear()
        if (tool.isRaster()) {
            rasterDab(l[0].toFloat(), l[1].toFloat(), p)
        } else if (tool == Tool.ERASER) {
            eraseAt(l[0].toFloat(), l[1].toFloat())
        }
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
        if (tool.isRaster()) {
            rasterDab(l[0].toFloat(), l[1].toFloat(), p)
        } else if (tool == Tool.ERASER) {
            eraseAt(l[0].toFloat(), l[1].toFloat())
        }
        redraw?.invoke()
    }

    /** Mark every stroke in the current level (on an unlocked, visible layer) that the eraser touches. */
    private fun eraseAt(lx: Float, ly: Float) {
        val lvl = engine.camera.level
        val rad = brushSizePx / engine.camera.zoom.toFloat() / 2f
        val layerIds = project.layers.filter { it.visible && !it.locked }.map { it.id }.toHashSet()
        for (s in lvl.strokes) {
            if (s.layerId !in layerIds || s in pendingRemoved) continue
            if (s.width <= 0f) continue
            val hitR = rad + s.width / 2f
            val b = s.bounds
            if (lx < b[0] - hitR || lx > b[2] + hitR || ly < b[1] - hitR || ly > b[3] + hitR) continue  // bounds cull
            var hit = false
            if (s.points.size == 1) {
                val q = s.points[0]
                hit = hypot(lx - q.x, ly - q.y) < hitR
            } else {
                for (i in 0 until s.points.size - 1) {
                    val a = s.points[i]; val c = s.points[i + 1]
                    if (distPointSeg(lx, ly, a.x, a.y, c.x, c.y) < hitR) { hit = true; break }
                }
            }
            if (hit) pendingRemoved.add(s)
        }
    }

    fun distPointSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    fun endStroke() {
        val a = active; val lvl = activeLevel
        active = null
        if (a != null && lvl != null && a.isNotEmpty()) {
            // Eraser and raster tools never create vector strokes.
            if (tool != Tool.ERASER && !tool.isRaster()) {
                val type = when (tool) {
                    Tool.MARKER -> BrushType.MARKER
                    Tool.AIRBRUSH -> BrushType.AIRBRUSH
                    else -> BrushType.PEN
                }
                val stroke = Stroke(
                    engine.nextStrokeId++, activeLayerId,
                    type,
                    color,   // marker/airbrush alpha is applied at render time
                    brushSizePx / engine.camera.zoom.toFloat(),
                    usePressure && tool == Tool.PEN, a.toList()
                )
                commands.exec(AddStroke(lvl.id, stroke), engine, project)
                autosave.schedule()
                if (color !in recentColors) {
                    recentColors.add(0, color)
                    if (recentColors.size > 8) recentColors.removeAt(recentColors.size - 1)
                }
            }
        }
        if (pendingRemoved.isNotEmpty() && lvl != null) {
            val removed = pendingRemoved.toList()
            commands.exec(RemoveStrokes(lvl.id, removed), engine, project)
            pendingRemoved.clear()
            autosave.schedule()
        }
        if (rasterBefore.isNotEmpty()) {
            // Phase 2b: capture post-stroke pixels over the same rects, then push undo.
            val after = rasterBefore.mapNotNull { s ->
                com.example.infinitedraw.render.RasterTiles.snapshot(s.key, s.cx, s.cy, s.r)
            }
            if (after.isNotEmpty()) commands.exec(
                RasterCommand(rasterBefore.toList(), after), engine, project)
            rasterBefore.clear()
            autosave.schedule()   // flush raster tiles to PNGs
        }
        redraw?.invoke()
    }

    fun cancelStroke() {
        active = null; pendingRemoved.clear()
        // Cancelled raster stroke: roll tiles back to the pre-stroke state.
        if (rasterBefore.isNotEmpty()) {
            com.example.infinitedraw.render.RasterTiles.restore(rasterBefore)
            rasterBefore.clear()
        }
        redraw?.invoke()
    }

    /**
     * Stamp a raster dab at level-local (lx, ly). Radius: brushSizePx converts
     * screen px to level-local units via /zoom; pressure modulates it for OIL/MIX.
     * Tile pixels are in level-local units (1 unit = 1 px).
     */
    private fun rasterDab(lx: Float, ly: Float, pressure: Float) {
        val lvl = activeLevel ?: return
        val zoom = engine.camera.zoom.toFloat()
        val r = (brushSizePx / zoom / 2f) * if (tool == Tool.OIL || tool == Tool.MIX) 0.15f + 0.85f * pressure else 1f
        val before = if (tool == Tool.SMUDGE) null else rasterBefore   // smudge not undoable (drifts)
        when (tool) {
            Tool.OIL, Tool.MIX -> com.example.infinitedraw.render.RasterTiles.dab(
                lvl, activeLayerId, lx, ly, r, color, 0, before)
            Tool.RERASER -> com.example.infinitedraw.render.RasterTiles.erase(
                lvl.id, activeLayerId, lx, ly, r, before)
            Tool.SMUDGE -> com.example.infinitedraw.render.RasterTiles.dab(
                lvl, activeLayerId, lx, ly, r, color, 1)
            else -> {}
        }
    }

    fun undo() { commands.undo(engine, project); redraw?.invoke(); autosave.schedule() }
    fun redo() { commands.redo(engine, project); redraw?.invoke(); autosave.schedule() }

    fun saveNow() = autosave.flushNow()

    // ---- images ----
    val bitmaps = HashMap<Long, android.graphics.Bitmap>()
    private var nextImageId = 1L

    /** Imports an image. Reports a user-facing error message on failure, or null on success. */
    fun addImage(uri: android.net.Uri, w: Int, h: Int, onDone: (String?) -> Unit = {}) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            var errorMsg: String? = null
            try {
                val resolver = ctx.contentResolver
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                while (maxSide / sample > 2048) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    errorMsg = "Could not read the image. The file may be corrupt or in an unsupported format."
                    return@launch
                }
                val bmp = resolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                }
                if (bmp == null) {
                    errorMsg = "Could not decode the image. The file may be corrupt or in an unsupported format."
                    return@launch
                }
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
                commands.exec(AddImage(cam.level.id, img), engine, project)
                redraw?.invoke()
            } catch (t: Throwable) {
                android.util.Log.e("DrawingViewModel", "addImage failed", t)
                errorMsg = "Image import failed: ${t.message ?: t.javaClass.simpleName}"
            }
            onDone(errorMsg)
        }
    }

    // ---- layer management ----
    private var nextLayerId = 2L

    fun addLayer() {
        val layer = Layer(nextLayerId++, "Layer ${project.layers.size + 1}")
        commands.exec(AddLayer(layer, project.layers.size), engine, project)
        activeLayerId = layer.id
    }

    fun removeLayer(id: Long) {
        if (project.layers.size <= 1) return     // must keep at least one
        val layer = project.layers.firstOrNull { it.id == id } ?: return
        val removedStrokes = engine.levels.values.associate { it.id to it.strokes.filter { s -> s.layerId == layer.id } }
        commands.exec(DeleteLayer(layer, project.layers.indexOf(layer), removedStrokes), engine, project)
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
        commands.exec(MoveLayer(idx, target), engine, project)
    }

    /** Step 17: export the current view as a PNG into the app's Pictures directory. */
    fun exportPng(w: Int, h: Int, onDone: (String?) -> Unit = {}) {
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            var path: String? = null
            try {
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                com.example.infinitedraw.render.CanvasRenderer().render(c, this@DrawingViewModel, w, h, debug = false)
                val dir = java.io.File(ctx.getExternalFilesDir(null), "Pictures").apply { mkdirs() }
                val f = java.io.File(dir, "export_${System.currentTimeMillis()}.png")
                f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                path = f.absolutePath
            } catch (t: Throwable) {
                android.util.Log.e("DrawingViewModel", "exportPng failed", t)
            }
            onDone(path)
        }
    }
}
