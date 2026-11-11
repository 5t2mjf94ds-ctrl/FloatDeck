package app.floatdeck.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ScreenSlots]: which theme each wallpaper reads, and what applying a theme
 * to the home screen, the lock screen or both writes.
 */
class ScreenSlotsTest {
    private val home = ScreenSlots.FLAG_SYSTEM
    private val lock = ScreenSlots.FLAG_LOCK
    private val both = ScreenSlots.FLAG_BOTH

    @Test
    fun `each wallpaper reads its own screen`() {
        assertEquals(ScreenSlots.KEY_SELECTED, ScreenSlots.keyFor(lock, isPreview = true))
        assertEquals(ScreenSlots.KEY_LOCK, ScreenSlots.keyFor(lock, isPreview = false))
        assertEquals(ScreenSlots.KEY_HOME, ScreenSlots.keyFor(home, isPreview = false))
        assertEquals(ScreenSlots.KEY_HOME, ScreenSlots.keyFor(both, isPreview = false))
        assertEquals(ScreenSlots.KEY_HOME, ScreenSlots.keyFor(0, isPreview = false))
    }

    @Test
    fun `system picker choice maps to slots`() {
        assertEquals(listOf(ScreenSlots.KEY_HOME), ScreenSlots.keysForFlags(home))
        assertEquals(listOf(ScreenSlots.KEY_LOCK), ScreenSlots.keysForFlags(lock))
        assertEquals(listOf(ScreenSlots.KEY_HOME, ScreenSlots.KEY_LOCK), ScreenSlots.keysForFlags(both))
    }

    @Test
    fun `separate wallpapers update in place`() {
        val running = listOf(home, lock)
        val toLock = ScreenSlots.plan(ScreenSlots.Target.LOCK, "seal", running, "grove")
        assertEquals(mapOf(ScreenSlots.KEY_LOCK to "seal"), toLock.writes)
        assertFalse(toLock.needsPicker)
        val toHome = ScreenSlots.plan(ScreenSlots.Target.HOME, "seal", running, "grove")
        assertEquals(mapOf(ScreenSlots.KEY_HOME to "seal"), toHome.writes)
        assertFalse(toHome.needsPicker)
        assertFalse(ScreenSlots.plan(ScreenSlots.Target.BOTH, "seal", running, "grove").needsPicker)
    }

    @Test
    fun `a shared wallpaper is split through the picker and the other screen keeps its theme`() {
        val running = listOf(both)
        val toHome = ScreenSlots.plan(ScreenSlots.Target.HOME, "seal", running, "grove")
        assertEquals(mapOf(ScreenSlots.KEY_HOME to "seal", ScreenSlots.KEY_LOCK to "grove"), toHome.writes)
        assertTrue(toHome.needsPicker)
        val toLock = ScreenSlots.plan(ScreenSlots.Target.LOCK, "seal", running, "grove")
        assertEquals(mapOf(ScreenSlots.KEY_LOCK to "seal"), toLock.writes)
        assertTrue(toLock.needsPicker)
        assertFalse(ScreenSlots.plan(ScreenSlots.Target.BOTH, "seal", running, "grove").needsPicker)
    }

    @Test
    fun `not running yet always opens the picker`() {
        for (target in ScreenSlots.Target.values()) {
            assertTrue(ScreenSlots.plan(target, "seal", emptyList(), "").needsPicker)
        }
    }
}
