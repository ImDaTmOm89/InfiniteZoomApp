package com.example.infinitedraw

import com.example.infinitedraw.render.dabHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 22: the pencil/charcoal grain must be a pure function of
 * (strokeId, dabIndex) so the dab pattern is identical every frame and
 * across app runs — "grain stable while pan/zoom".
 */
class DabHashTest {

    @Test
    fun deterministicPerStrokeAndIndex() {
        assertEquals(dabHash(7L, 3), dabHash(7L, 3))
        assertEquals(dabHash(-1L, 0), dabHash(-1L, 0))     // -1 = in-progress preview stroke id
        assertEquals(dabHash(123L, 2999), dabHash(123L, 2999))
    }

    @Test
    fun variesWithStrokeAndIndex() {
        val a = dabHash(7L, 3)
        assertTrue("consecutive dabs must differ", a != dabHash(7L, 4))
        assertTrue("different strokes must differ", a != dabHash(8L, 3))
    }

    @Test
    fun jitterFieldsVary() {
        // A degenerate (stuck) hash would pin the grain to one jitter pattern.
        val seen = (0 until 256).map { dabHash(1L, it) and 0xFF }.toHashSet()
        assertTrue("hash output looks degenerate: ${seen.size} distinct low bytes", seen.size >= 32)
    }
}
