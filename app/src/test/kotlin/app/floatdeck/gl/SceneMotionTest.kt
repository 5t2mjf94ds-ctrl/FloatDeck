package app.floatdeck.gl

import app.floatdeck.data.LayerRock
import app.floatdeck.data.SceneLayerConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SceneRenderer.pulseFactor] and [SceneRenderer.rockAt].
 */
class SceneMotionTest {
    private fun layer(
        amount: Float,
        phase: Float = 0f,
        sharpness: Float = 1f,
        flash: Boolean = false,
    ) = SceneLayerConfig(
        id = "l",
        path = "l.png",
        isRemote = false,
        pulseAmount = amount,
        pulsePeriod = 4f,
        pulsePhase = phase,
        pulseSharpness = sharpness,
        pulseFlash = flash,
    )

    @Test
    fun `classic pulse dips by the amount at the peak`() {
        // t = 1 s is the peak of a 4 s wave
        assertEquals(0.6f, SceneRenderer.pulseFactor(layer(0.4f), 1.0), 0.0001f)
        assertEquals(1f, SceneRenderer.pulseFactor(layer(0.4f), 3.0), 0.0001f)
    }

    @Test
    fun `flash pulse is dark except near its peak`() {
        val flash = layer(1f, sharpness = 6f, flash = true)
        assertEquals(1f, SceneRenderer.pulseFactor(flash, 1.0), 0.0001f)
        assertTrue(SceneRenderer.pulseFactor(flash, 0.0) < 0.05f)
        assertEquals(0f, SceneRenderer.pulseFactor(flash, 3.0), 0.0001f)
    }

    @Test
    fun `phase shifts the wave in time`() {
        val shifted = layer(0.4f, phase = Math.PI.toFloat())
        assertEquals(1f, SceneRenderer.pulseFactor(shifted, 1.0), 0.0001f)
        assertEquals(0.6f, SceneRenderer.pulseFactor(shifted, 3.0), 0.0001f)
    }

    @Test
    fun `rock stays within its amplitude and stops when sway is off`() {
        val rock = LayerRock(angle = 8f, period = 3f, scale = 0.05f)
        for (i in 0 until 300) {
            val (angle, grow) = SceneRenderer.rockAt(rock, i * 0.05, 1f)
            assertTrue(kotlin.math.abs(angle) <= 8f + 0.0001f)
            assertTrue(grow in 0.95f..1.05f)
        }
        val (still, size) = SceneRenderer.rockAt(rock, 1.3, 0f)
        assertEquals(0f, still, 0.0001f)
        assertEquals(1f, size, 0.0001f)
    }

    @Test
    fun `droop leans toward real-world down`() {
        val sideways = LayerRock(droop = 8f, dir = 0f)
        // held upright: a leaf pointing right droops fully, clockwise
        assertEquals(8f, SceneRenderer.rockAt(sideways, 0.0, 1f, 0f, 1f).first, 0.0001f)
        // lying flat: no pull in the screen plane
        assertEquals(0f, SceneRenderer.rockAt(sideways, 0.0, 1f, 0f, 0f).first, 0.0001f)
        // hanging straight down: already at rest
        val hanging = LayerRock(droop = 8f, dir = 90f)
        assertEquals(0f, SceneRenderer.rockAt(hanging, 0.0, 1f, 0f, 1f).first, 0.0001f)
        // pointing up with the right edge dipped 30 degrees: tips lean right (clockwise)
        val up = LayerRock(droop = 8f, dir = -90f)
        assertEquals(4f, SceneRenderer.rockAt(up, 0.0, 1f, 0.5f, 0.866f).first, 0.001f)
    }

    @Test
    fun `screen gravity fades when lying flat`() {
        val (ux, uy) = FloatDeckRenderer.screenGravity(0f, 0f)
        assertEquals(0f, ux, 0.0001f)
        assertEquals(1f, uy, 0.0001f)
        val (fx, fy) = FloatDeckRenderer.screenGravity(0f, -1f)
        assertEquals(0f, fx, 0.0001f)
        assertEquals(0f, fy, 0.0001f)
        val (bx, by) = FloatDeckRenderer.screenGravity(0.5f, 0f)
        assertEquals(0.5f, bx, 0.0001f)
        assertEquals(0.866f, by, 0.001f)
    }
}
