package com.example.infinitedraw.engine

import com.example.infinitedraw.model.*

interface Command { fun apply(e: InfiniteZoomEngine, p: Project); fun revert(e: InfiniteZoomEngine, p: Project) }

class AddStroke(val levelId: Int, val stroke: Stroke) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.strokes.add(stroke) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.strokes.remove(stroke) }
}

class RemoveStrokes(val levelId: Int, val strokes: List<Stroke>) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.strokes.removeAll(strokes.toSet()) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.strokes.addAll(strokes) }
}

class AddImage(val levelId: Int, val image: PlacedImage) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.images.add(image) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.images.remove(image) }
}

class RemoveImage(val levelId: Int, val image: PlacedImage) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.images.remove(image) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { if (!e.levels[levelId]!!.images.contains(image)) e.levels[levelId]!!.images.add(image) }
}

/** Step 21: move one stroke by (dx,dy) in level-local units. Immutable copy; keeps list index (draw order). */
class MoveStrokes(val levelId: Int, val strokeId: Long, val dx: Float, val dy: Float) : Command {
    private lateinit var old: Stroke
    override fun apply(e: InfiniteZoomEngine, p: Project) {
        val list = e.levels[levelId]!!.strokes
        val i = list.indexOfFirst { it.id == strokeId }
        if (i < 0) return
        val s = list[i]
        old = s
        val moved = Stroke(s.id, s.layerId, s.brush, s.color, s.width, s.usePressure,
            s.points.map { StrokePoint(it.x + dx, it.y + dy, it.p) })
        list[i] = moved
    }
    override fun revert(e: InfiniteZoomEngine, p: Project) {
        val list = e.levels[levelId]!!.strokes
        val i = list.indexOfFirst { it.id == strokeId }
        if (i >= 0) list[i] = old
    }
}

/** Step 21: move one image by (dx,dy) in level-local units. Immutable copy; keeps list index. */
class MoveImage(val levelId: Int, val imageId: Long, val dx: Float, val dy: Float) : Command {
    private lateinit var old: PlacedImage
    override fun apply(e: InfiniteZoomEngine, p: Project) {
        val list = e.levels[levelId]!!.images
        val i = list.indexOfFirst { it.id == imageId }
        if (i < 0) return
        val img = list[i]
        old = img
        list[i] = PlacedImage(img.id, img.layerId, img.file, img.x + dx, img.y + dy, img.w, img.h)
    }
    override fun revert(e: InfiniteZoomEngine, p: Project) {
        val list = e.levels[levelId]!!.images
        val i = list.indexOfFirst { it.id == imageId }
        if (i >= 0) list[i] = old
    }
}

/**
 * Step 21: scale a selection (stroke or image) around the center of its own bounds.
 * factor 1.0 = unchanged. Strokes: points and width scale. Images: x,y,w,h scale around the center.
 */
class ScaleSelection(val levelId: Int, val strokeId: Long?, val imageId: Long?, val factor: Float) : Command {
    private lateinit var oldStroke: Stroke
    private lateinit var newStroke: Stroke
    private lateinit var oldImage: PlacedImage
    private lateinit var newImage: PlacedImage
    private var isStroke = false
    private var applied = false
    override fun apply(e: InfiniteZoomEngine, p: Project) {
        val f = factor.toDouble()
        if (strokeId != null) {
            val list = e.levels[levelId]!!.strokes
            val i = list.indexOfFirst { it.id == strokeId }
            if (i < 0) return
            val s = list[i]; oldStroke = s; isStroke = true
            val cx = (s.bounds[0] + s.bounds[2]) / 2.0
            val cy = (s.bounds[1] + s.bounds[3]) / 2.0
            newStroke = Stroke(s.id, s.layerId, s.brush, s.color, (s.width * f).toFloat(), s.usePressure,
                s.points.map { StrokePoint((cx + (it.x - cx) * f).toFloat(), (cy + (it.y - cy) * f).toFloat(), it.p) })
            list[i] = newStroke
            applied = true
        } else if (imageId != null) {
            val list = e.levels[levelId]!!.images
            val i = list.indexOfFirst { it.id == imageId }
            if (i < 0) return
            val img = list[i]; oldImage = img; isStroke = false
            val cx = img.x + img.w / 2.0
            val cy = img.y + img.h / 2.0
            val nw = img.w * f; val nh = img.h * f
            newImage = PlacedImage(img.id, img.layerId, img.file,
                (cx - nw / 2).toFloat(), (cy - nh / 2).toFloat(), nw.toFloat(), nh.toFloat())
            list[i] = newImage
            applied = true
        }
    }
    override fun revert(e: InfiniteZoomEngine, p: Project) {
        if (!applied) return
        if (isStroke) {
            val list = e.levels[levelId]!!.strokes
            val i = list.indexOfFirst { it.id == strokeId }
            if (i >= 0) list[i] = oldStroke
        } else {
            val list = e.levels[levelId]!!.images
            val i = list.indexOfFirst { it.id == imageId }
            if (i >= 0) list[i] = oldImage
        }
    }
}

class AddLayer(val layer: Layer, val index: Int) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) { if (!p.layers.contains(layer)) p.layers.add(index.coerceIn(0, p.layers.size), layer) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { p.layers.remove(layer) }
}

class DeleteLayer(val layer: Layer, val index: Int, val removedStrokes: Map<Int, List<Stroke>>) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) {
        p.layers.remove(layer)
        for ((levelId, strokes) in removedStrokes) e.levels[levelId]?.strokes?.removeAll(strokes.toSet())
    }
    override fun revert(e: InfiniteZoomEngine, p: Project) {
        p.layers.add(index.coerceIn(0, p.layers.size), layer)
        for ((levelId, strokes) in removedStrokes) e.levels[levelId]?.strokes?.addAll(strokes)
    }
}

class SetLayerProps(val layerId: Long, val old: Props, val new: Props) : Command {
    class Props(val name: String, val visible: Boolean, val locked: Boolean, val opacity: Float, val blend: LayerBlend)
    override fun apply(e: InfiniteZoomEngine, p: Project) { set(p, new) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { set(p, old) }
    private fun set(p: Project, v: Props) {
        p.layers.firstOrNull { it.id == layerId }?.let {
            it.name = v.name; it.visible = v.visible; it.locked = v.locked
            it.opacity = v.opacity; it.blend = v.blend
        }
    }
}

class MoveLayer(val from: Int, val to: Int) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) { move(p, from, to) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { move(p, to, from) }
    private fun move(p: Project, from: Int, to: Int) {
        if (from < 0 || from >= p.layers.size) return
        val layer = p.layers.removeAt(from)
        p.layers.add(to.coerceIn(0, p.layers.size), layer)
    }
}

/**
 * Phase 2b: undo/redo for raster tile painting. Raster dabs are applied live
 * to tile bitmaps, so undo/redo swaps between captured 'before' and 'after'
 * pixel snapshots instead of re-stamping dabs (robust against overlapping
 * strokes on the same region).
 */
class RasterCommand(
    val before: List<com.example.infinitedraw.render.RasterTiles.TileSnapshot>,
    val after: List<com.example.infinitedraw.render.RasterTiles.TileSnapshot>
) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) {   // redo
        com.example.infinitedraw.render.RasterTiles.restore(after)
    }
    override fun revert(e: InfiniteZoomEngine, p: Project) {  // undo
        com.example.infinitedraw.render.RasterTiles.restore(before)
    }
}

class UndoStack {
    private val undo = ArrayDeque<Command>(); private val redo = ArrayDeque<Command>()
    fun exec(c: Command, e: InfiniteZoomEngine, p: Project) { c.apply(e, p); undo.addLast(c); redo.clear() }
    fun undo(e: InfiniteZoomEngine, p: Project) { undo.removeLastOrNull()?.let { it.revert(e, p); redo.addLast(it) } }
    fun redo(e: InfiniteZoomEngine, p: Project) { redo.removeLastOrNull()?.let { it.apply(e, p); undo.addLast(it) } }
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun clear() { undo.clear(); redo.clear() }
}
