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
import com.example.infinitedraw.engine.MoveImage
import com.example.infinitedraw.engine.MoveLayer
import com.example.infinitedraw.engine.MoveStrokes
import com.example.infinitedraw.engine.RasterCommand
import com.example.infinitedraw.engine.RemoveImage
import com.example.infinitedraw.engine.RemoveStrokes
import com.example.infinitedraw.engine.ScaleSelection
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
import kotlin.math.pow

enum class Tool { PEN, MARKER, AIRBRUSH, ERASER, OIL, MIX, SMUDGE, RERASER, SELECT }

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

    // ---- Step 20: stroke smoothing + pressure curve (persisted in SharedPreferences("settings")) ----
    var stabilizer: Float = 0.3f
        set(value) {
            field = value.coerceIn(0f, 1f)
            prefs().edit().putFloat("stabilizer", field).apply()
        }
    var pressureGamma: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            prefs().edit().putFloat("pressureGamma", field).apply()
        }
    private fun prefs() =
        getApplication<Application>().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    init {
        val sp = prefs()
        stabilizer = sp.getFloat("stabilizer", 0.3f)
        pressureGamma = sp.getFloat("pressureGamma", 1.0f)
    }

    // Smoothing state (SCREEN coordinates, reset at stroke start)
    private var smX = 0f; private var smY = 0f; private var smP = 1f
    private var lastRawX = 0f; private var lastRawY = 0f; private var lastRawP = 1f
    private var haveFirstPoint = false
    private var smoothingActive = false
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

    // ---- Step 21: selection (single item, current zoom level only) ----
    var selection by mutableStateOf<Selection?>(null)
    var previewDx = 0f; var previewDy = 0f   // local units, preview offset while dragging
    private var selectMoving = false          // true after "begin inside selection"
    private var selectStartX = 0f; private var selectStartY = 0f
    private var selectCurX = 0f; private var selectCurY = 0f
    private var selectionSnapshot: Selection? = null // selection at drag start (for preview bounds)

    data class Selection(val levelId: Int, val strokeId: Long?, val imageId: Long?)

    /** Bounds of the selected item in level-local units (of its own level). */
    fun selectionBounds(s: Selection): FloatArray? {
        val lvl = engine.levels[s.levelId] ?: return null
        if (s.strokeId != null) {
            val st = lvl.strokes.firstOrNull { it.id == s.strokeId } ?: return null
            return st.bounds
        }
        val img = lvl.images.firstOrNull { it.id == s.imageId } ?: return null
        return floatArrayOf(img.x, img.y, img.x + img.w, img.y + img.h)
    }

    /** Step 21: hit test in the CURRENT level, topmost first; visible+unlocked layers only. */
    fun hitTest(lx: Float, ly: Float): Selection? {
        val lvl = engine.camera.level
        val layerIds = project.layers.filter { it.visible && !it.locked }.map { it.id }.toHashSet()
        val zoom = engine.camera.zoom
        val tol = (12f / zoom.toFloat())
        for (st in lvl.strokes.asReversed()) {
            if (st.layerId !in layerIds) continue
            val hitR = tol + st.width / 2f
            val b = st.bounds
            if (lx < b[0] - hitR || lx > b[2] + hitR || ly < b[1] - hitR || ly > b[3] + hitR) continue
            var hit = false
            if (st.points.size == 1) {
                val q = st.points[0]
                hit = hypot(lx - q.x, ly - q.y) < hitR
            } else {
                for (i in 0 until st.points.size - 1) {
                    val a = st.points[i]; val c = st.points[i + 1]
                    if (distPointSeg(lx, ly, a.x, a.y, c.x, c.y) < hitR) { hit = true; break }
                }
            }
            if (hit) return Selection(lvl.id, st.id, null)
        }
        for (img in lvl.images.asReversed()) {
            if (img.layerId !in layerIds) continue
            if (lx >= img.x && lx <= img.x + img.w && ly >= img.y && ly <= img.y + img.h)
                return Selection(lvl.id, null, img.id)
        }
        return null
    }

    /** Step 21: SELECT tool enter/exit hooks (via beginStroke/addPoint/endStroke). */
    private fun selectBegin(sx: Float, sy: Float, w: Int, h: Int) {
        selectStartX = sx; selectStartY = sy; selectCurX = sx; selectCurY = sy
        previewDx = 0f; previewDy = 0f
        val sel = selection
        if (sel != null) {
            val l = engine.screenToLocal(sx, sy, w, h)
            val b = selectionBounds(sel)
            if (b != null && l[0] >= b[0] && l[0] <= b[2] && l[1] >= b[1] && l[1] <= b[3]) {
                selectMoving = true
                selectionSnapshot = sel
                return
            }
        }
        selectMoving = false
    }

    private fun selectMove(sx: Float, sy: Float, w: Int, h: Int) {
        selectCurX = sx; selectCurY = sy
        if (selectMoving) {
            previewDx = (sx - selectStartX).toFloat() / engine.camera.zoom.toFloat()
            previewDy = (sy - selectStartY).toFloat() / engine.camera.zoom.toFloat()
            redraw?.invoke()
        }
    }

    private fun selectEnd(w: Int, h: Int) {
        if (selectMoving) {
            val sel = selectionSnapshot
            val movedPx = hypot(selectCurX - selectStartX, selectCurY - selectStartY)
            if (sel != null && movedPx >= 3f) {
                val dx = previewDx; val dy = previewDy
                if (sel.strokeId != null)
                    commands.exec(MoveStrokes(sel.levelId, sel.strokeId, dx, dy), engine, project)
                else if (sel.imageId != null)
                    commands.exec(MoveImage(sel.levelId, sel.imageId, dx, dy), engine, project)
                autosave.schedule()
            }
            selectMoving = false
            selectionSnapshot = null
            previewDx = 0f; previewDy = 0f
        } else {
            // Pick: only if the pen stayed in place (< 10 px)
            val movedPx = hypot(selectCurX - selectStartX, selectCurY - selectStartY)
            if (movedPx < 10f) {
                val l = engine.screenToLocal(selectStartX, selectStartY, w, h)
                selection = hitTest(l[0].toFloat(), l[1].toFloat())
            }
        }
        redraw?.invoke()
    }

    fun scaleSelection(factor: Float) {
        val sel = selection ?: return
        commands.exec(ScaleSelection(sel.levelId, sel, factor), engine, project)
        autosave.schedule()
        redraw?.invoke()
    }

    fun deleteSelection() {
        val sel = selection ?: return
        val lvl = engine.levels[sel.levelId] ?: return
        if (sel.strokeId != null) {
            val st = lvl.strokes.firstOrNull { it.id == sel.strokeId } ?: return
            commands.exec(RemoveStrokes(sel.levelId, listOf(st)), engine, project)
        } else if (sel.imageId != null) {
            val img = lvl.images.firstOrNull { it.id == sel.imageId } ?: return
            commands.exec(RemoveImage(sel.levelId, img), engine, project)
        }
        selection = null
        autosave.schedule()
        redraw?.invoke()
    }

    fun deselect() { selection = null; redraw?.invoke() }

    // ---- Step 21: selection (single item, current level only) ----
    data class Selection(val levelId: Int, val strokeId: Long? = null, val imageId: Long? = null)
    var selection: Selection? by androidx.compose.runtime.mutableStateOf(null)
    var previewDx: Float = 0f      // local units, applied only for drawing while dragging
    var previewDy: Float = 0f
    // select-gesture state
    private enum class SelectMode { NONE, PICK, MOVE }
    private var selectMode = SelectMode.NONE
    private var pickStartX = 0f; private var pickStartY = 0f   // screen
    private var moveStartX = 0f; private var moveStartY = 0f   // screen
    private var selItem: Selection? = null                     // item being moved

    fun deselect() { selection = null; previewDx = 0f; previewDy = 0f; redraw?.invoke() }

    /** Selected item's bounds in level-local units (x0,y0,x1,y1), or null. */
    fun selectionBounds(b: FloatArray): Boolean {
        val s = selection ?: return false
        val lvl = engine.levels[s.levelId] ?: return false
        if (s.strokeId != null) {
            val st = lvl.strokes.firstOrNull { it.id == s.strokeId } ?: return false
            val sb = st.bounds
            b[0] = sb[0]; b[1] = sb[1]; b[2] = sb[2]; b[3] = sb[3]
            return true
        }
        if (s.imageId != null) {
            val img = lvl.images.firstOrNull { it.id == s.imageId } ?: return false
            b[0] = img.x; b[1] = img.y; b[2] = img.x + img.w; b[3] = img.y + img.h
            return true
        }
        return false
    }

    /** Step 21: hit test at level-local (lx,ly), last drawn first, visible+unlocked layers only. */
    fun hitTest(lx: Float, ly: Float): Selection? {
        val lvl = engine.camera.level
        val okLayers = project.layers.filter { it.visible && !it.locked }.map { it.id }.toHashSet()
        val zoom = engine.camera.zoom
        for (s in lvl.strokes.asReversed()) {
            if (s.layerId !in okLayers) continue
            val hitR = 12f / zoom.toFloat() + s.width / 2f
            val b = s.bounds
            if (lx < b[0] - hitR || lx > b[2] + hitR || ly < b[1] - hitR || ly > b[3] + hitR) continue
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
            if (hit) return Selection(lvl.id, strokeId = s.id)
        }
        for (img in lvl.images.asReversed()) {
            if (img.layerId !in okLayers) continue
            if (lx >= img.x && lx <= img.x + img.w && ly >= img.y && ly <= img.y + img.h)
                return Selection(lvl.id, imageId = img.id)
        }
        return null
    }

    private fun selectBegin(sx: Float, sy: Float, w: Int, h: Int) {
        val sel = selection
        if (sel != null) {
            // inside the selection's bounds (with a small margin) -> start a MOVE
            val b = FloatArray(4)
            if (selectionBounds(b)) {
                val zoom = engine.camera.zoom
                val l = engine.screenToLocal(sx, sy, w, h)
                val m = 8.0 / zoom
                if (l[0] >= b[0] - m && l[0] <= b[2] + m && l[1] >= b[1] - m && l[1] <= b[3] + m) {
                    selectMode = SelectMode.MOVE
                    moveStartX = sx; moveStartY = sy
                    return
                }
            }
        }
        selectMode = SelectMode.PICK
        pickStartX = sx; pickStartY = sy
    }

    private fun selectMove(sx: Float, sy: Float, w: Int, h: Int) {
        if (selectMode != SelectMode.MOVE) return
        val zoom = engine.camera.zoom
        previewDx = ((sx - moveStartX) / zoom).toFloat()
        previewDy = ((sy - moveStartY) / zoom).toFloat()
        redraw?.invoke()
    }

    private fun selectEnd(w: Int, h: Int) {
        when (selectMode) {
            SelectMode.MOVE -> {
                val sel = selItem ?: selection
                val zoom = engine.camera.zoom
                val dx = previewDx; val dy = previewDy
                previewDx = 0f; previewDy = 0f
                selectMode = SelectMode.NONE
                if (sel != null && hypot(dx * zoom, dy * zoom) >= 3f) {
                    if (sel.strokeId != null) commands.exec(MoveStrokes(sel.levelId, sel.strokeId, dx, dy), engine, project)
                    else if (sel.imageId != null) commands.exec(MoveImage(sel.levelId, sel.imageId, dx, dy), engine, project)
                    autosave.schedule()
                }
            }
            SelectMode.PICK -> {
                val moved = hypot(pickStartX - lastDownX, pickStartY - lastDownY) >= 10f
                selectMode = SelectMode.NONE
                if (!moved) {
                    val l = engine.screenToLocal(pickStartX, pickStartY, w, h)
                    selection = hitTest(l[0].toFloat(), l[1].toFloat())
                    redraw?.invoke()
                }
            }
            SelectMode.NONE -> {}
        }
        selectMode = SelectMode.NONE
    }

    private var lastDownX = 0f; private var lastDownY = 0f


    fun activeLayer(): Layer? = project.layers.firstOrNull { it.id == activeLayerId }

    var active: ArrayList<StrokePoint>? = null
    var activeLevel: ZoomLevel? = null

    // View dimensions captured at stroke start (for the endStroke raw-point conversion)
    private var w0 = 0; private var h0 = 0

    fun beginStroke(sx: Float, sy: Float, p: Float, w: Int, h: Int) {
        // Plan Step 10: the active layer must be visible and unlocked to draw.
        val layer = activeLayer()
        if (layer == null || !layer.visible || layer.locked) {
            active = null
            return
        }
        w0 = w; h0 = h
        val l = engine.screenToLocal(sx, sy, w, h)
        activeLevel = engine.camera.level
        active = ArrayList<StrokePoint>().also { it.add(StrokePoint(l[0].toFloat(), l[1].toFloat(), p)) }
        pendingRemoved.clear()
        rasterBefore.clear()
        // Step 20: smoothing is applied to PEN/MARKER/AIRBRUSH only (eraser + raster exempt).
        // SELECT uses the select gesture instead of drawing.
        lastDownX = sx; lastDownY = sy
        if (tool == Tool.SELECT) { selectBegin(sx, sy, w, h); return }
        smoothingActive = !tool.isRaster() && tool != Tool.ERASER
        haveFirstPoint = false
        if (smoothingActive) {
            lastRawX = sx; lastRawY = sy; lastRawP = p
            haveFirstPoint = true
        }
        if (tool.isRaster()) {
            rasterDab(l[0].toFloat(), l[1].toFloat(), p)
        } else if (tool == Tool.ERASER) {
            eraseAt(l[0].toFloat(), l[1].toFloat())
        }
        redraw?.invoke()
    }

    fun addPoint(sx: Float, sy: Float, p: Float, w: Int, h: Int) {
        if (tool == Tool.SELECT) { selectMove(sx, sy, w, h); return }
        val a = active ?: return
        var sx = sx; var sy = sy; var p = p
        if (smoothingActive && haveFirstPoint) {
            // Step 20: exponential smoothing in SCREEN space, before the local conversion.
            // p is gamma-curved first, then smoothed.
            val curved = p.coerceIn(0f, 1f).pow(pressureGamma)
            val al = 1f - 0.8f * stabilizer
            smX += al * (sx - smX); smY += al * (sy - smY); smP += al * (curved - smP)
            lastRawX = sx; lastRawY = sy; lastRawP = curved
            sx = smX; sy = smY; p = smP
        }
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
        if (tool == Tool.SELECT) { selectEnd(w0, h0); return }
        if (a != null && lvl != null && a.isNotEmpty()) {
            // Step 20: if the pen lifted away from the last smoothed point, append the
            // raw final point (converted) so the stroke ends exactly where the pen did.
            if (smoothingActive && haveFirstPoint) {
                val l = engine.screenToLocal(lastRawX, lastRawY, w0, h0)
                if (hypot(lastRawX - smX, lastRawY - smY) > 1f) {
                    a.add(StrokePoint(l[0].toFloat(), l[1].toFloat(), lastRawP))
                }
            }
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
