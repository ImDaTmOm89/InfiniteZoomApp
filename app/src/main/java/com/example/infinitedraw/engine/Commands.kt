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
