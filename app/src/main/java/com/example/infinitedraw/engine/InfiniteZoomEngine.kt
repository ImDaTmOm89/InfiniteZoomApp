package com.example.infinitedraw.engine
import com.example.infinitedraw.model.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

/** Step 24: `rotation` in radians — the world appears rotated about the screen center. */
class Camera(var level: ZoomLevel, var cx: Double = 0.0, var cy: Double = 0.0, var zoom: Double = 1.0,
             var rotation: Double = 0.0)

class InfiniteZoomEngine {
    companion object { const val DESCEND_AT = 100.0; const val ASCEND_BELOW = 0.5; const val CHILD_REACH = 500.0 }
    var root = ZoomLevel(0, null, 0.0, 0.0)
    val levels = HashMap<Int, ZoomLevel>().also { it[0] = root }
    val camera = Camera(root)
    var nextLevelId = 1
    var nextStrokeId = 1L

    /**
     * Step 24: screen -> local with rotation. The screen shows the world rotated
     * by `rotation`, so a screen delta maps to a world delta via R(-rotation):
     * [c, s; -s, c] with c = cos(r), s = sin(r).
     */
    fun screenToLocal(sx: Float, sy: Float, w: Int, h: Int): DoubleArray {
        val c = cos(camera.rotation); val s = sin(camera.rotation)
        val px = sx - w / 2.0; val py = sy - h / 2.0
        return doubleArrayOf(
            camera.cx + (c * px + s * py) / camera.zoom,
            camera.cy + (-s * px + c * py) / camera.zoom)
    }

    /** Step 24: screen drag -> camera shift, so content follows the finger under rotation. */
    fun panBy(dxPx: Float, dyPx: Float) {
        val c = cos(camera.rotation); val s = sin(camera.rotation)
        camera.cx -= (c * dxPx + s * dyPx) / camera.zoom
        camera.cy -= (-s * dxPx + c * dyPx) / camera.zoom
    }

    /**
     * Step 24: factor > 1 zooms in, delta > 0 rotates counter-clockwise; both keep
     * the local point under (sx,sy) fixed. One anchored transform for pinch + twist.
     */
    fun transformAt(factor: Double, delta: Double, sx: Float, sy: Float, w: Int, h: Int) {
        val p = screenToLocal(sx, sy, w, h)
        camera.zoom *= factor
        camera.rotation = normalizeAngle(camera.rotation + delta)
        val c = cos(camera.rotation); val s = sin(camera.rotation)
        val px = sx - w / 2.0; val py = sy - h / 2.0
        camera.cx = p[0] - (c * px + s * py) / camera.zoom
        camera.cy = p[1] - (-s * px + c * py) / camera.zoom
        normalize()
    }

    /** factor > 1 zooms in. Keeps the local point under (sx,sy) fixed. */
    fun zoomAt(factor: Double, sx: Float, sy: Float, w: Int, h: Int) = transformAt(factor, 0.0, sx, sy, w, h)

    /** Step 24: wrap to [-PI, PI) so long twist streams never lose precision. */
    private fun normalizeAngle(a: Double): Double {
        val t = (a + PI) % (2 * PI)
        return if (t < 0) t + PI else t - PI
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
