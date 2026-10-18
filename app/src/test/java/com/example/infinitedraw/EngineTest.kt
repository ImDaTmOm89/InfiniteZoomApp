package com.example.infinitedraw

import com.example.infinitedraw.engine.InfiniteZoomEngine
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class EngineTest {
    @Test
    fun testZoomIn() {
        val engine = InfiniteZoomEngine()
        val initialLevelId = engine.camera.level.id
        val initialRootCenter = engine.centerInRoot()
        val initialRootZoom = engine.zoomInRoot()

        // Zoom in 150x
        engine.zoomAt(150.0, 500f, 500f, 1000, 1000)

        // Expect level change
        assertNotEquals(initialLevelId, engine.camera.level.id)

        // Expect camera.zoom between 1 and 2
        assertTrue("Camera zoom should be between 1 and 2, was ${engine.camera.zoom}", 
                   engine.camera.zoom >= 1.0 && engine.camera.zoom <= 2.0)

        // Expect zoomInRoot() = 150 × its old value
        val newRootZoom = engine.zoomInRoot()
        val ratio = newRootZoom / initialRootZoom
        assertTrue("Root zoom ratio should be ~150, was $ratio", 
                   abs(ratio - 150.0) < 0.01)

        // Expect centerInRoot() unchanged (relative tolerance 1e-9)
        val newRootCenter = engine.centerInRoot()
        val dx = abs(newRootCenter[0] - initialRootCenter[0])
        val dy = abs(newRootCenter[1] - initialRootCenter[1])
        val tolerance = 1e-9 * (abs(initialRootCenter[0]) + abs(initialRootCenter[1]) + 1.0)
        assertTrue("Root center should be unchanged, dx=$dx dy=$dy", dx < tolerance && dy < tolerance)
    }

    @Test
    fun testZoomOut() {
        val engine = InfiniteZoomEngine()
        val initialRoot = engine.root
        val initialRootCenter = engine.centerInRoot()

        // Zoom out 1000x
        engine.zoomAt(0.001, 500f, 500f, 1000, 1000)

        // Expect a new root above the old one
        assertNotEquals(initialRoot, engine.root)
        assertTrue("Old root should be a descendant of new root", 
                   engine.root.children.any { isDescendant(it, initialRoot) })

        // Expect centerInRoot() unchanged
        val newRootCenter = engine.centerInRoot()
        val dx = abs(newRootCenter[0] - initialRootCenter[0])
        val dy = abs(newRootCenter[1] - initialRootCenter[1])
        val tolerance = 1e-9 * (abs(initialRootCenter[0]) + abs(initialRootCenter[1]) + 1.0)
        assertTrue("Root center should be unchanged, dx=$dx dy=$dy", dx < tolerance && dy < tolerance)

        // Expect camera.zoom between 0.5 and 100
        assertTrue("Camera zoom should be between 0.5 and 100, was ${engine.camera.zoom}", 
                   engine.camera.zoom >= 0.5 && engine.camera.zoom <= 100.0)
    }

    private fun isDescendant(node: com.example.infinitedraw.model.ZoomLevel, target: com.example.infinitedraw.model.ZoomLevel): Boolean {
        if (node === target) return true
        return node.children.any { isDescendant(it, target) }
    }
}
