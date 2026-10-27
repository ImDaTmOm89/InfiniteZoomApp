package com.example.infinitedraw

import com.example.infinitedraw.engine.AddLayer
import com.example.infinitedraw.engine.InfiniteZoomEngine
import com.example.infinitedraw.engine.UndoStack
import com.example.infinitedraw.model.Layer
import com.example.infinitedraw.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U8 regression: layer ids must stay unique across a relaunch plus an add.
 * The original bug: the VM's layer-id counter was hard-coded and reset on
 * relaunch, so addLayer() on a restored project re-issued an existing id — the
 * duplicate LazyColumn key silently dropped a layer row from the panel, and
 * the corrupt state persisted to project.json. Project.nextLayerId() derives
 * ids from the project itself, so restore state can never collide.
 */
class LayerIdTest {
    @Test
    fun `add after restore keeps layer ids unique`() {
        // A project that already has layers 1 and 2 (as restored from disk).
        val project = Project("t", mutableListOf(Layer(1L, "Layer 1"), Layer(2L, "Layer 2")))

        // The pre-fix VM code produced id 2 here (colliding with "Layer 2").
        val newId = project.nextLayerId()
        assertEquals(3L, newId)

        val stack = UndoStack()
        stack.exec(AddLayer(Layer(newId, "Layer 3"), project.layers.size), InfiniteZoomEngine(), project)
        assertEquals(3, project.layers.size)
        assertTrue(
            "layer ids must be unique: ${project.layers.map { it.id }}",
            project.layers.map { it.id }.distinct().size == project.layers.size
        )
    }

    @Test
    fun `nextLayerId on empty project starts at 1`() {
        assertEquals(1L, Project("e", mutableListOf()).nextLayerId())
    }
}
