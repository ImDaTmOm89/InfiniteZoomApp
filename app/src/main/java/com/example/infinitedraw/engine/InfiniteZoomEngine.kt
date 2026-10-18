package com.example.infinitedraw.engine
import com.example.infinitedraw.model.*
import kotlin.math.abs

class Camera(var level: ZoomLevel, var cx: Double = 0.0, var cy: Double = 0.0, var zoom: Double = 1.0)

class InfiniteZoomEngine {
    companion object { const val DESCEND_AT = 100.0; const val ASCEND_BELOW = 0.5; const val CHILD_REACH = 500.0 }
    var root = ZoomLevel(0, null, 0.0, 0.0)
    val levels = HashMap<Int, ZoomLevel>().also { it[0] = root }
    val camera = Camera(root)
    var nextLevelId = 1
    var nextStrokeId = 1L

    fun screenToLocal(sx: Float, sy: Float, w: Int, h: Int): DoubleArray = doubleArrayOf(
        camera.cx + (sx - w / 2.0) / camera.zoom, camera.cy + (sy - h / 2.0) / camera.zoom)

    fun panBy(dxPx: Float, dyPx: Float) { camera.cx -= dxPx / camera.zoom; camera.cy -= dyPx / camera.zoom }

    /** factor > 1 zooms in. Keeps the local point under (sx,sy) fixed. */
    fun zoomAt(factor: Double, sx: Float, sy: Float, w: Int, h: Int) {
        val p = screenToLocal(sx, sy, w, h)
        camera.zoom *= factor
        camera.cx = p[0] - (sx - w / 2.0) / camera.zoom
        camera.cy = p[1] - (sy - h / 2.0) / camera.zoom
        normalize()
    }

    fun normalize() {
        var guard = 0
        while (guard++ < 64) {
            if (camera.zoom >= DESCEND_AT) descend()
            else if (camera.zoom < ASCEND_BELOW) ascend()
            else break
        }
    }

    private fun descend() {
        val c = camera; val lvl = c.level
        val child = lvl.children.firstOrNull {
            abs((c.cx - it.originX) * F) < CHILD_REACH && abs((c.cy - it.originY) * F) < CHILD_REACH
        } ?: ZoomLevel(nextLevelId++, lvl, c.cx, c.cy).also { lvl.children.add(it); levels[it.id] = it }
        c.cx = (c.cx - child.originX) * F; c.cy = (c.cy - child.originY) * F
        c.zoom /= F; c.level = child
    }

    private fun ascend() {
        val c = camera; val lvl = c.level
        val p = lvl.parent ?: createRootParent()
        c.cx = lvl.originX + c.cx / F; c.cy = lvl.originY + c.cy / F
        c.zoom *= F; c.level = p
    }

    private fun createRootParent(): ZoomLevel {
        val np = ZoomLevel(nextLevelId++, null, 0.0, 0.0)
        root.parent = np; root.originX = 0.0; root.originY = 0.0
        np.children.add(root); levels[np.id] = np; root = np
        return np
    }

    /** Camera center in ROOT units (tests / persistence checks). */
    fun centerInRoot(): DoubleArray {
        var x = camera.cx; var y = camera.cy; var l = camera.level
        while (l.parent != null) { x = l.originX + x / F; y = l.originY + y / F; l = l.parent!! }
        return doubleArrayOf(x, y)
    }
    /** Pixels per ROOT unit: zoom * F^depth. */
    fun zoomInRoot(): Double { var z = camera.zoom; var l = camera.level; while (l.parent != null) { z *= F; l = l.parent!! }; return z }
}
