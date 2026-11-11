package app.floatdeck.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses the optional "layers" and "particles" sections of template.json.
 *
 * Everything here is optional and lenient: a malformed entry is skipped instead of
 * failing the whole template, so a layered template still loads (as a plain
 * wallpaper + portraits template) when something is off. Paths in the returned
 * configs are the bare file names; [Templates] resolves them against the template dir.
 */
object SceneJson {
    private val SAFE_FILE_NAME = Regex("[A-Za-z0-9_.-]+")

    /** Only plain file names inside the template folder are accepted (no separators, no ".."). */
    internal fun isSafeFileName(name: String): Boolean = name.matches(SAFE_FILE_NAME) && !name.contains("..")

    /** Optional top-level "scene": {"recenter": seconds, "parallax": multiplier}. */
    fun parseSettings(json: JSONObject): SceneSettings {
        val obj = json.optJSONObject("scene") ?: return SceneSettings()
        return SceneSettings(
            recenterSeconds = obj.optFloat("recenter", 0f).coerceIn(0f, 60f),
            parallaxScale = obj.optFloat("parallax", 1f).coerceIn(0f, 4f),
        )
    }

    fun parseLayers(json: JSONObject): List<SceneLayerConfig> {
        val arr = json.optJSONArray("layers") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            try {
                parseLayer(arr.getJSONObject(i), i)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun parseEmitters(json: JSONObject): List<ParticleEmitterConfig> {
        val arr = json.optJSONArray("particles") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            try {
                parseEmitter(arr.getJSONObject(i))
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseLayer(
        obj: JSONObject,
        index: Int,
    ): SceneLayerConfig? {
        val file = obj.getString("file")
        if (!isSafeFileName(file)) return null
        val swayObj = obj.optJSONObject("sway")
        val sway =
            swayObj?.let {
                LayerSway(
                    x = it.optFloat("x", 0f),
                    y = it.optFloat("y", 0f),
                    period = it.optFloat("period", 6f).coerceAtLeast(0.05f),
                    phase = it.optFloat("phase", 0f),
                    bounce = it.optBoolean("bounce", false),
                )
            }
        val rockObj = obj.optJSONObject("rock")
        val rock =
            rockObj?.let {
                val pivot = it.optJSONArray("pivot")
                LayerRock(
                    angle = it.optFloat("angle", 0f).coerceIn(-90f, 90f),
                    period = it.optFloat("period", 4f).coerceAtLeast(0.05f),
                    phase = it.optFloat("phase", 0f),
                    pivotX = (pivot?.optDouble(0, 0.5) ?: 0.5).toFloat(),
                    pivotY = (pivot?.optDouble(1, 0.5) ?: 0.5).toFloat(),
                    scale = it.optFloat("scale", 0f).coerceIn(-0.5f, 0.5f),
                    droop = it.optFloat("droop", 0f).coerceIn(-45f, 45f),
                    dir = it.optFloat("dir", 0f),
                )
            }
        val drift = obj.optJSONObject("drift")
        val pulse = obj.optJSONObject("pulse")
        val frames = obj.optJSONObject("frames")
        val cols = (frames?.optInt("cols", 1) ?: 1).coerceIn(1, 64)
        val rows = (frames?.optInt("rows", 1) ?: 1).coerceIn(1, 64)
        return SceneLayerConfig(
            id = obj.optString("id", "layer$index"),
            path = file,
            isRemote = false,
            rect = rectOf(obj.optJSONArray("rect"), floatArrayOf(0f, 0f, 1f, 1f)),
            depth = obj.optFloat("depth", 1f).coerceIn(0f, 4f),
            alpha = obj.optFloat("alpha", 1f).coerceIn(0f, 1f),
            sway = sway,
            driftX = drift?.optFloat("x", 0f) ?: 0f,
            driftY = drift?.optFloat("y", 0f) ?: 0f,
            pulseAmount = (pulse?.optFloat("amount", 0f) ?: 0f).coerceIn(0f, 1f),
            pulsePeriod = (pulse?.optFloat("period", 8f) ?: 8f).coerceAtLeast(0.1f),
            pulsePhase = pulse?.optFloat("phase", 0f) ?: 0f,
            pulseSharpness = (pulse?.optFloat("sharpness", 1f) ?: 1f).coerceIn(0.1f, 64f),
            pulseFlash = pulse?.optBoolean("flash", false) ?: false,
            additive = obj.optBoolean("additive", false),
            opaque = obj.optBoolean("opaque", false),
            contained = obj.optBoolean("contained", false),
            follow = obj.optString("follow", "").ifBlank { null },
            spinDegPerSec = obj.optFloat("spin", 0f).coerceIn(-7200f, 7200f),
            frameCols = cols,
            frameRows = rows,
            frameCount = (frames?.optInt("count", cols * rows) ?: 1).coerceIn(1, cols * rows),
            frameFps = (frames?.optFloat("fps", 0f) ?: 0f).coerceIn(0f, 120f),
            rock = rock,
        )
    }

    private fun parseEmitter(obj: JSONObject): ParticleEmitterConfig? {
        val file = obj.getString("file")
        if (!isSafeFileName(file)) return null
        val burst = obj.optJSONObject("burst")
        val life = rangeOf(obj.optJSONArray("life"), 1f, 2f)
        val size = rangeOf(obj.optJSONArray("size"), 0.02f, 0.04f)
        val vx = rangeOf(obj.optJSONArray("vx"), 0f, 0f)
        val vy = rangeOf(obj.optJSONArray("vy"), 0f, 0f)
        val spin = rangeOf(obj.optJSONArray("spin"), 0f, 0f)
        return ParticleEmitterConfig(
            path = file,
            isRemote = false,
            follow = obj.optString("follow", "").ifBlank { null },
            depth = obj.optFloat("depth", 1f).coerceIn(0f, 4f),
            area = rectOf(obj.optJSONArray("area"), floatArrayOf(0.4f, 0.4f, 0.6f, 0.6f)),
            rate = obj.optFloat("rate", 0f).coerceIn(0f, 200f),
            burstPeriod = (burst?.optFloat("period", 0f) ?: 0f).coerceAtLeast(0f),
            burstCount = (burst?.optInt("count", 0) ?: 0).coerceIn(0, 64),
            burstPhase = burst?.optFloat("phase", 0f) ?: 0f,
            lifeMin = life.first.coerceAtLeast(0.05f),
            lifeMax = life.second.coerceAtLeast(life.first.coerceAtLeast(0.05f)),
            sizeMin = size.first,
            sizeMax = size.second,
            grow = obj.optFloat("grow", 1f),
            vxMin = vx.first,
            vxMax = vx.second,
            vyMin = vy.first,
            vyMax = vy.second,
            gravity = obj.optFloat("gravity", 0f),
            drag = obj.optFloat("drag", 0f).coerceAtLeast(0f),
            alpha = obj.optFloat("alpha", 1f).coerceIn(0f, 1f),
            fadeIn = obj.optFloat("fadeIn", 0.15f).coerceIn(0f, 0.9f),
            spinMin = spin.first,
            spinMax = spin.second,
            additive = obj.optBoolean("additive", false),
            maxParticles = obj.optInt("max", 160).coerceIn(1, 400),
        )
    }

    private fun JSONObject.optFloat(
        key: String,
        fallback: Float,
    ): Float = optDouble(key, fallback.toDouble()).toFloat()

    private fun rectOf(
        arr: JSONArray?,
        fallback: FloatArray,
    ): FloatArray {
        if (arr == null || arr.length() != 4) return fallback
        return FloatArray(4) { arr.getDouble(it).toFloat() }
    }

    private fun rangeOf(
        arr: JSONArray?,
        min: Float,
        max: Float,
    ): Pair<Float, Float> {
        if (arr == null || arr.length() != 2) return Pair(min, max)
        val a = arr.getDouble(0).toFloat()
        val b = arr.getDouble(1).toFloat()
        return Pair(minOf(a, b), maxOf(a, b))
    }
}
