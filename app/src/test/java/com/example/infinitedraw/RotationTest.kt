package com.example.infinitedraw

import com.example.infinitedraw.engine.InfiniteZoomEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI

/**
 * Step 24: rotation-aware camera transforms. Pure engine, like EngineTest.
 * The renderer's worldToScreen is the inverse of screenToLocal by construction.
 */
class RotationTest {

    @Test
    fun screenCenterMapsToCameraCenter() {
        val e = InfiniteZoomEngine()
        e.camera.rotation = PI / 2
        e.camera.cx = 5.0; e.camera.cy = -3.0
        val l = e.screenToLocal(500f, 500f, 1000, 1000)
        assertEquals(5.0, l[0], 1e-9)
        assertEquals(-3.0, l[1], 1e-9)
    }

    @Test
    fun panByFollowsFingerUnderRotation() {
        val e = InfiniteZoomEngine()
        e.camera.rotation = PI / 2
        e.camera.zoom = 2.0
        e.panBy(10f, 0f)
        // at 90 deg, screen +x is world -y, so the camera must move +y for content to follow
        assertEquals(0.0, e.camera.cx, 1e-9)
        assertEquals(5.0, e.camera.cy, 1e-9)
    }

    @Test
    fun transformAtKeepsAnchorFixed() {
        val e = InfiniteZoomEngine()
        e.zoomAt(37.0, 300f, 700f, 1000, 1000)   // arbitrary camera state (levels may descend)
        e.camera.rotation = 0.7
        val before = e.screenToLocal(300f, 700f, 1000, 1000)
        e.transformAt(1.25, 0.31, 300f, 700f, 1000, 1000)
        val after = e.screenToLocal(300f, 700f, 1000, 1000)
        assertEquals(before[0], after[0], 1e-9)
        assertEquals(before[1], after[1], 1e-9)
        assertEquals(1.01, e.camera.rotation, 1e-9)
    }

    @Test
    fun rotationStaysNormalized() {
        val e = InfiniteZoomEngine()
        repeat(50) { e.transformAt(1.0, 0.5, 500f, 500f, 1000, 1000) }
        assertTrue("rotation not wrapped: ${e.camera.rotation}", abs(e.camera.rotation) <= PI + 1e-9)
    }
}
