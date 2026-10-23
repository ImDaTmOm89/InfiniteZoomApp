package com.example.infinitedraw

import com.example.infinitedraw.engine.InfiniteZoomEngine
import com.example.infinitedraw.engine.MoveImage
import com.example.infinitedraw.engine.MoveStrokes
import com.example.infinitedraw.engine.ScaleSelection
import com.example.infinitedraw.engine.UndoStack
import com.example.infinitedraw.model.BrushType
import com.example.infinitedraw.model.Layer
import com.example.infinitedraw.model.PlacedImage
import com.example.infinitedraw.model.Project
import com.example.infinitedraw.model.Stroke
import com.example.infinitedraw.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Step 21: select/move/scale commands. These run on the pure-Kotlin engine
 * (no Android classes), like EngineTest.
 */
class SelectCommandsTest {

    private fun stroke(id: Long = 1L) = Stroke(
        id, 1L, BrushType.PEN, 0xFF000000.toInt(), 5f, false,
        listOf(StrokePoint(0f, 0f, 1f), StrokePoint(10f, 0f, 1f)))

    private fun env(): Triple<InfiniteZoomEngine, Project, UndoStack> {
        val e = InfiniteZoomEngine()
        val p = Project("T", mutableListOf(Layer(1, "L1")))
        return Triple(e, p, UndoStack())
    }

    @Test
    fun moveStrokeAndUndo() {
        val (e, p, u) = env()
        e.levels[0]!!.strokes.add(stroke())
        u.exec(MoveStrokes(0, 1L, 5f, 7f), e, p)
        val m = e.levels[0]!!.strokes[0]
        assertEquals(5f, m.points[0].x, 0f)
        assertEquals(7f, m.points[0].y, 0f)
        assertEquals(15f, m.points[1].x, 0f)
        assertEquals(1L, m.id)   // id stable -> a live Selection survives the move
        u.undo(e, p)
        val r = e.levels[0]!!.strokes[0]
        assertEquals(0f, r.points[0].x, 0f)
        assertEquals(0f, r.points[0].y, 0f)
        assertEquals(10f, r.points[1].x, 0f)
    }

    @Test
    fun scaleStrokeAndUndo() {
        val (e, p, u) = env()
        e.levels[0]!!.strokes.add(stroke())
        u.exec(ScaleSelection(0, 1L, null, 2f), e, p)
        val s2 = e.levels[0]!!.strokes[0]
        assertEquals(10f, s2.width, 0f)   // width scales with the points
        // original bounds center: x in [-2.5, 12.5] -> cx = 5
        assertEquals(-5f, s2.points[0].x, 1e-4f)
        assertEquals(15f, s2.points[1].x, 1e-4f)
        u.undo(e, p)
        val s1 = e.levels[0]!!.strokes[0]
        assertEquals(5f, s1.width, 0f)
        assertEquals(0f, s1.points[0].x, 0f)
        assertEquals(10f, s1.points[1].x, 0f)
    }

    @Test
    fun moveImageAndUndo() {
        val (e, p, u) = env()
        e.levels[0]!!.images.add(PlacedImage(1L, 1L, "1.png", 0f, 0f, 20f, 10f))
        u.exec(MoveImage(0, 1L, 3f, 4f), e, p)
        val img = e.levels[0]!!.images[0]
        assertEquals(3f, img.x, 0f)
        assertEquals(4f, img.y, 0f)
        assertEquals(20f, img.w, 0f)   // move never resizes
        assertEquals(10f, img.h, 0f)
        u.undo(e, p)
        val o = e.levels[0]!!.images[0]
        assertEquals(0f, o.x, 0f)
        assertEquals(0f, o.y, 0f)
    }

    @Test
    fun scaleImageAndUndo() {
        val (e, p, u) = env()
        e.levels[0]!!.images.add(PlacedImage(1L, 1L, "1.png", 10f, 10f, 20f, 10f))
        u.exec(ScaleSelection(0, null, 1L, 0.5f), e, p)
        val img = e.levels[0]!!.images[0]
        // center (20, 15) preserved; w,h halved
        assertEquals(10f, img.w, 1e-3f)
        assertEquals(5f, img.h, 1e-3f)
        assertEquals(15f, img.x, 1e-3f)
        assertEquals(12.5f, img.y, 1e-3f)
        u.undo(e, p)
        val o = e.levels[0]!!.images[0]
        assertEquals(20f, o.w, 0f)
        assertEquals(10f, o.h, 0f)
        assertEquals(10f, o.x, 0f)
        assertEquals(10f, o.y, 0f)
    }
}
