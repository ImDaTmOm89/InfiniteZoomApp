package com.example.infinitedraw.model

class CommandStack(private val limit: Int = 200) {
    private val undoStack = ArrayList<Command>(limit)
    private val redoStack = ArrayList<Command>(limit)

    fun push(cmd: Command) {
        if (undoStack.size >= limit) undoStack.removeAt(0)
        undoStack.add(cmd)
        redoStack.clear()
        cmd.redo()
    }
    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    fun undo(): Boolean {
        val c = undoStack.removeLastOrNull() ?: return false
        c.undo(); redoStack.add(c); return true
    }
    fun redo(): Boolean {
        val c = redoStack.removeLastOrNull() ?: return false
        c.redo(); undoStack.add(c); return true
    }
    fun clear() { undoStack.clear(); redoStack.clear() }
}

interface Command {
    fun undo()
    fun redo()
}

class AddStrokeCommand(private val level: ZoomLevel, private val stroke: Stroke) : Command {
    override fun undo() { level.strokes.remove(stroke) }
    override fun redo() { if (!level.strokes.contains(stroke)) level.strokes.add(stroke) }
}

class RemoveStrokeCommand(private val level: ZoomLevel, private val stroke: Stroke) : Command {
    override fun undo() { if (!level.strokes.contains(stroke)) level.strokes.add(stroke) }
    override fun redo() { level.strokes.remove(stroke) }
}

class AddLayerCommand(private val project: Project, private val layer: Layer) : Command {
    override fun undo() { project.layers.remove(layer) }
    override fun redo() { if (!project.layers.contains(layer)) project.layers.add(layer) }
}

class RemoveLayerCommand(private val project: Project, private val layer: Layer) : Command {
    override fun undo() { if (!project.layers.contains(layer)) project.layers.add(layer) }
    override fun redo() { project.layers.remove(layer) }
}

class ReorderLayerCommand(private val project: Project, private val layer: Layer, private val from: Int, private val to: Int) : Command {
    override fun undo() { project.layers.removeAt(to); project.layers.add(from, layer) }
    override fun redo() { project.layers.removeAt(from); project.layers.add(to, layer) }
}

class AddImageCommand(private val level: ZoomLevel, private val image: PlacedImage) : Command {
    override fun undo() { level.images.remove(image) }
    override fun redo() { if (!level.images.contains(image)) level.images.add(image) }
}
