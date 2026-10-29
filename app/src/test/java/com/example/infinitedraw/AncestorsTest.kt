package com.example.infinitedraw

import com.example.infinitedraw.engine.InfiniteZoomEngine
import com.example.infinitedraw.model.F
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * U11: the plan's ancestors() unit test — "for every frame, converting back
 * down gives the same centerInRoot()". Converting a frame back down = taking
 * the frame's (x, y) as the camera center in that ancestor and walking up to
 * the root with the same math as centerInRoot().
 */
class AncestorsTest {
    private fun frameToRoot(e: InfiniteZoomEngine, f: InfiniteZoomEngine.AncestorFrame): Pair<Double, Double> {
        var x = f.x; var y = f.y; var l = f.level
        while (l.parent != null) { x = l.originX + x / F; y = l.originY + y / F; l = l.parent!! }
        return x to y
    }

    @Test
    fun `every ancestor frame converts back to the same centerInRoot`() {
        val e = InfiniteZoomEngine()
        // Walk the camera around: pan, zoom in repeatedly (creates levels), pan inside.
        repeat(4) {
            e.camera.cx += 300.0 * (it + 1)
            e.camera.cy -= 200.0 * (it + 1)
            repeat(3) {   // zoom in deep enough to descend into new levels
                e.camera.zoom *= F
                e.normalize()
            }
            e.camera.cx += 50.0; e.camera.cy += 80.0
        }
        // Also zoom back out above the original root.
        repeat(3) { e.camera.zoom /= F; e.normalize() }

        val (rx, ry) = e.centerInRoot()
        val frames = e.ancestors()
        assert(frames.isNotEmpty()) { "expected ancestors after zooming in/out" }
        for (f in frames) {
            val (fx, fy) = frameToRoot(e, f)
            assert(abs(fx - rx) < 1e-6) { "x mismatch at depth ${e.depth(f.level)}: $fx vs $rx" }
            assert(abs(fy - ry) < 1e-6) { "y mismatch at depth ${e.depth(f.level)}: $fy vs $ry" }
        }
    }

    @Test
    fun `depth of any level matches the ancestors chain`() {
        val e = InfiniteZoomEngine()
        repeat(3) { e.camera.zoom *= F; e.normalize() }
        val frames = e.ancestors()
        assertEquals(3, frames.size)
        assertEquals(3, e.depth())
        frames.forEachIndexed { i, f -> assertEquals(frames.size - 1 - i, e.depth(f.level)) }
    }
}
