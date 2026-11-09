package app.floatdeck.gl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [FloatDeckRenderer.sceneOverscan] and [SceneRenderer.wrapOffset].
 */
class SceneOverscanTest {
    @Test
    fun `no tilt needs no overscan`() {
        val o = FloatDeckRenderer.sceneOverscan(0f, 0f, 2f, 1200f, 2000f, 0.7f)
        assertEquals(1f, o, 0.0001f)
    }

    @Test
    fun `deepest layer still covers the screen`() {
        val w = 1200f
        val h = 2000f
        val aspect = 0.6f
        val px = 30f
        val py = 40f
        val depth = 1.9f
        val o = FloatDeckRenderer.sceneOverscan(px, py, depth, w, h, aspect)
        val drawW = h * aspect * o
        val drawH = h * o
        assertTrue(drawW / 2f >= w / 2f + px * depth)
        assertTrue(drawH / 2f >= h / 2f + py * depth)
    }

    @Test
    fun `horizontal slack of a wide image absorbs horizontal tilt`() {
        // 0.75-aspect art on a 0.6 screen is ~250px wider than needed
        val o = FloatDeckRenderer.sceneOverscan(50f, 0f, 2f, 1200f, 2000f, 0.75f)
        assertEquals(1f, o, 0.0001f)
    }

    @Test
    fun `scroll offset wraps into unit range`() {
        assertEquals(0.25f, SceneRenderer.wrapOffset(-0.75), 0.0001f)
        assertEquals(0.5f, SceneRenderer.wrapOffset(12.5), 0.0001f)
        assertEquals(0f, SceneRenderer.wrapOffset(0.0), 0.0001f)
    }
}
