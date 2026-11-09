package app.floatdeck.data

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SceneJson] — the optional "layers" / "particles" sections of template.json.
 *
 * Everything is optional and lenient: plain templates parse to empty lists, bad entries are
 * skipped, and file names that could escape the template folder are rejected.
 */
class SceneJsonTest {
    @Test
    fun `plain template has no layers or particles`() {
        val json = JSONObject("""{"id":"a","wallpaper":"w.webp","portraits":{"left":[],"right":[]}}""")
        assertTrue(SceneJson.parseLayers(json).isEmpty())
        assertTrue(SceneJson.parseEmitters(json).isEmpty())
    }

    @Test
    fun `layer fields are parsed with defaults`() {
        val json =
            JSONObject(
                """
                {"layers":[
                  {"id":"base","file":"base.webp","opaque":true},
                  {"file":"fog.webp","rect":[0,0.5,1,1],"depth":1.4,"alpha":0.4,
                   "drift":{"x":0.01},"pulse":{"amount":0.3,"period":9},
                   "sway":{"y":0.004,"period":0.5,"bounce":true}}
                ]}
                """.trimIndent(),
            )
        val layers = SceneJson.parseLayers(json)
        assertEquals(2, layers.size)
        assertEquals("base", layers[0].id)
        assertTrue(layers[0].opaque)
        assertEquals(1f, layers[0].depth, 0.0001f)
        assertNull(layers[0].sway)

        val fog = layers[1]
        assertEquals("layer1", fog.id)
        assertEquals(0.5f, fog.rect[1], 0.0001f)
        assertEquals(1.4f, fog.depth, 0.0001f)
        assertEquals(0.01f, fog.driftX, 0.0001f)
        assertEquals(0f, fog.driftY, 0.0001f)
        assertEquals(0.3f, fog.pulseAmount, 0.0001f)
        assertTrue(fog.sway!!.bounce)
        assertEquals(0.004f, fog.sway!!.y, 0.0001f)
    }

    @Test
    fun `unsafe or malformed entries are skipped`() {
        val json =
            JSONObject(
                """
                {"layers":[
                  {"file":"../escape.webp"},
                  {"file":"sub/dir.webp"},
                  {"nofile":true},
                  {"file":"ok.webp"}
                ],
                "particles":[{"file":"../x.png"},{"file":"dust.png","life":[2,1],"burst":{"period":0.5,"count":4}}]}
                """.trimIndent(),
            )
        val layers = SceneJson.parseLayers(json)
        assertEquals(1, layers.size)
        assertEquals("ok.webp", layers[0].path)

        val emitters = SceneJson.parseEmitters(json)
        assertEquals(1, emitters.size)
        val dust = emitters[0]
        // reversed ranges are normalized to (min, max)
        assertEquals(1f, dust.lifeMin, 0.0001f)
        assertEquals(2f, dust.lifeMax, 0.0001f)
        assertEquals(0.5f, dust.burstPeriod, 0.0001f)
        assertEquals(4, dust.burstCount)
    }

    @Test
    fun `scene settings and contained flag`() {
        val json =
            JSONObject(
                """
                {"scene":{"recenter":2.5,"parallax":1.4},
                 "layers":[{"file":"johnny.webp","depth":1.3,"contained":true},{"file":"base.webp"}]}
                """.trimIndent(),
            )
        val settings = SceneJson.parseSettings(json)
        assertEquals(2.5f, settings.recenterSeconds, 0.0001f)
        assertEquals(1.4f, settings.parallaxScale, 0.0001f)
        val layers = SceneJson.parseLayers(json)
        assertTrue(layers[0].contained)
        assertFalse(layers[1].contained)
    }

    @Test
    fun `missing scene settings keep absolute tilt`() {
        val settings = SceneJson.parseSettings(JSONObject("{}"))
        assertEquals(0f, settings.recenterSeconds, 0.0001f)
        assertEquals(1f, settings.parallaxScale, 0.0001f)
    }

    @Test
    fun `safe file names`() {
        assertTrue(SceneJson.isSafeFileName("gyro_ride-2.webp"))
        assertFalse(SceneJson.isSafeFileName("../a.webp"))
        assertFalse(SceneJson.isSafeFileName("a/b.webp"))
        assertFalse(SceneJson.isSafeFileName(""))
    }
}
