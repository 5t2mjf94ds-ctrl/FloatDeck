package app.floatdeck.gl

import android.opengl.GLES30
import android.opengl.Matrix
import app.floatdeck.data.ParticleEmitterConfig
import app.floatdeck.data.SceneLayerConfig
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Where the wallpaper image sits on screen this frame. Scene layers and particles are
 * positioned relative to it so they stay registered with the artwork.
 *
 * @param centerX screen-space center of the wallpaper before parallax
 * @param drawWidth on-screen width of the full wallpaper image (cover + overscan + zoom)
 * @param parallaxX wallpaper parallax offset in pixels; a layer moves by this × its depth
 */
data class SceneFrame(
    val centerX: Float,
    val centerY: Float,
    val drawWidth: Float,
    val drawHeight: Float,
    val parallaxX: Float,
    val parallaxY: Float,
    val imageWidth: Float,
    val imageHeight: Float,
    val swayScale: Float,
)

/** Uniform locations of the background program, which scene layers and particles reuse. */
data class SpriteProgram(
    val program: Int,
    val mvp: Int,
    val texture: Int,
    val alpha: Int,
    val uvOffset: Int,
    val uvScale: Int,
)

/**
 * Draws a template's optional depth layers (cut-out characters, drifting fog) and sprite
 * particles (hoof dust, motes) between the wallpaper and the portrait cards.
 */
class SceneRenderer {
    private class LayerState(
        val config: SceneLayerConfig,
        val textureId: Int,
    )

    private class Emitter(
        val config: ParticleEmitterConfig,
        val textureId: Int,
        val aspect: Float,
    ) {
        val cap = config.maxParticles
        val x = FloatArray(cap)
        val y = FloatArray(cap)
        val vx = FloatArray(cap)
        val vy = FloatArray(cap)
        val age = FloatArray(cap)
        val life = FloatArray(cap)
        val size = FloatArray(cap)
        val rot = FloatArray(cap)
        val spin = FloatArray(cap)
        var count = 0
        var spawnAccumulator = 0f
        var lastBurstIndex = Long.MIN_VALUE
    }

    private val layers = mutableListOf<LayerState>()
    private val emitters = mutableListOf<Emitter>()
    private var timeSeconds = 0.0
    private val random = Random(System.nanoTime())

    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    /** True when the first layer is an opaque base, so the wallpaper underneath need not be drawn. */
    val hasOpaqueBase: Boolean get() = layers.firstOrNull()?.config?.opaque == true

    val isEmpty: Boolean get() = layers.isEmpty() && emitters.isEmpty()

    /**
     * Largest parallax depth among layers that must keep covering the screen edges (the
     * wallpaper itself counts as depth 1 unless an opaque base replaces it). Contained layers
     * fade out before their edges, so they are left out and never force a zoom-in.
     */
    val edgeDepth: Float
        get() {
            val wallpaperDepth = if (hasOpaqueBase) 0f else 1f
            val layerDepth = layers.filter { !it.config.contained }.maxOfOrNull { it.config.depth } ?: 0f
            return maxOf(wallpaperDepth, layerDepth)
        }

    /**
     * Loads layer and particle textures. [loadTexture] returns (textureId, width, height) or null.
     */
    fun load(
        layerConfigs: List<SceneLayerConfig>,
        emitterConfigs: List<ParticleEmitterConfig>,
        loadTexture: (path: String, isRemote: Boolean) -> Triple<Int, Int, Int>?,
    ) {
        release()
        layerConfigs.forEach { cfg ->
            val tex = loadTexture(cfg.path, cfg.isRemote) ?: return@forEach
            if (cfg.driftX != 0f || cfg.driftY != 0f) setRepeat(tex.first, cfg.driftX != 0f, cfg.driftY != 0f)
            layers.add(LayerState(cfg, tex.first))
        }
        emitterConfigs.forEach { cfg ->
            val tex = loadTexture(cfg.path, cfg.isRemote) ?: return@forEach
            val aspect = if (tex.second > 0) tex.third.toFloat() / tex.second.toFloat() else 1f
            emitters.add(Emitter(cfg, tex.first, aspect))
        }
        timeSeconds = 0.0
    }

    fun release() {
        layers.forEach { TextureLoader.deleteTexture(it.textureId) }
        emitters.forEach { TextureLoader.deleteTexture(it.textureId) }
        layers.clear()
        emitters.clear()
    }

    /** Advances time and particle simulation; image size converts emitter units to wallpaper pixels. */
    fun update(
        deltaSeconds: Float,
        imageWidth: Float,
        imageHeight: Float,
    ) {
        timeSeconds += deltaSeconds
        emitters.forEach { stepEmitter(it, deltaSeconds, imageWidth, imageHeight) }
    }

    fun draw(
        frame: SceneFrame,
        sprite: SpriteProgram,
        ortho: FloatArray,
        quad: FloatBuffer,
    ) {
        if (isEmpty) return
        GLES30.glUseProgram(sprite.program)
        bindQuad(quad)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(sprite.texture, 0)

        val drawn = BooleanArray(emitters.size)
        layers.forEach { layer ->
            drawLayer(layer, frame, sprite, ortho)
            val id = layer.config.id
            emitters.forEachIndexed { i, e ->
                if (!drawn[i] && e.config.follow == id) {
                    drawEmitter(e, frame, sprite, ortho)
                    drawn[i] = true
                }
            }
        }
        emitters.forEachIndexed { i, e -> if (!drawn[i]) drawEmitter(e, frame, sprite, ortho) }

        GLES30.glUniform2f(sprite.uvOffset, 0f, 0f)
        GLES30.glUniform2f(sprite.uvScale, 1f, 1f)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    // ------------------------------------------------------------------
    // Layers
    // ------------------------------------------------------------------

    private fun drawLayer(
        layer: LayerState,
        frame: SceneFrame,
        sprite: SpriteProgram,
        ortho: FloatArray,
    ) {
        val cfg = layer.config
        var alpha = cfg.alpha
        if (cfg.pulseAmount > 0f) {
            val wave = 0.5f + 0.5f * sin((2.0 * PI * timeSeconds / cfg.pulsePeriod)).toFloat()
            alpha *= 1f - cfg.pulseAmount * wave
        }
        if (alpha <= 0.001f) return

        var (swayX, swayY) = swayOffset(cfg, frame.swayScale)
        cfg.follow?.let { parentId ->
            layers.firstOrNull { it.config.id == parentId }?.let { parent ->
                val (px, py) = swayOffset(parent.config, frame.swayScale)
                swayX += px
                swayY += py
            }
        }
        val left = frame.centerX - frame.drawWidth / 2f + frame.parallaxX * cfg.depth + swayX * frame.drawWidth
        val top = frame.centerY - frame.drawHeight / 2f + frame.parallaxY * cfg.depth + swayY * frame.drawHeight
        val r = cfg.rect
        val cx = left + (r[0] + r[2]) / 2f * frame.drawWidth
        val cy = top + (r[1] + r[3]) / 2f * frame.drawHeight
        val halfW = (r[2] - r[0]) / 2f * frame.drawWidth
        val halfH = (r[3] - r[1]) / 2f * frame.drawHeight

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, cx, cy, 0f)
        if (cfg.spinDegPerSec != 0f) {
            val angle = ((cfg.spinDegPerSec * timeSeconds) % 360.0).toFloat()
            Matrix.rotateM(model, 0, angle, 0f, 0f, 1f)
        }
        Matrix.scaleM(model, 0, halfW, halfH, 1f)
        Matrix.multiplyMM(mvp, 0, ortho, 0, model, 0)

        setBlend(cfg.additive)
        GLES30.glUniformMatrix4fv(sprite.mvp, 1, false, mvp, 0)
        GLES30.glUniform1f(sprite.alpha, alpha)
        if (cfg.frameCount > 1 && cfg.frameFps > 0f) {
            val index = frameIndex(timeSeconds, cfg.frameFps, cfg.frameCount)
            val col = index % cfg.frameCols
            val row = index / cfg.frameCols
            GLES30.glUniform2f(sprite.uvScale, 1f / cfg.frameCols, 1f / cfg.frameRows)
            GLES30.glUniform2f(sprite.uvOffset, col.toFloat() / cfg.frameCols, row.toFloat() / cfg.frameRows)
        } else {
            GLES30.glUniform2f(sprite.uvScale, 1f, 1f)
            GLES30.glUniform2f(
                sprite.uvOffset,
                wrapOffset(-cfg.driftX * timeSeconds),
                wrapOffset(-cfg.driftY * timeSeconds),
            )
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, layer.textureId)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Idle offset as fractions of the wallpaper image (x of width, y of height). */
    private fun swayOffset(
        cfg: SceneLayerConfig,
        scale: Float,
    ): Pair<Float, Float> {
        val s = cfg.sway ?: return Pair(0f, 0f)
        if (scale <= 0f) return Pair(0f, 0f)
        val phase = 2.0 * PI * (timeSeconds / s.period) + s.phase
        val x = s.x * sin(phase).toFloat()
        val y =
            if (s.bounce) {
                // gallop hop: lifts off and lands once per period
                -s.y * abs(sin(phase / 2.0)).toFloat()
            } else {
                s.y * sin(phase * 0.83 + 1.1).toFloat()
            }
        return Pair(x * scale, y * scale)
    }

    // ------------------------------------------------------------------
    // Particles
    // ------------------------------------------------------------------

    private fun stepEmitter(
        e: Emitter,
        dt: Float,
        imageWidth: Float,
        imageHeight: Float,
    ) {
        val c = e.config
        // simulate existing particles
        val damping = if (c.drag > 0f) exp(-c.drag * dt) else 1f
        var i = 0
        while (i < e.count) {
            e.age[i] += dt
            if (e.age[i] >= e.life[i]) {
                removeAt(e, i)
                continue
            }
            e.vy[i] += c.gravity * imageWidth * dt
            e.vx[i] *= damping
            e.vy[i] *= damping
            e.x[i] += e.vx[i] * dt
            e.y[i] += e.vy[i] * dt
            e.rot[i] += e.spin[i] * dt
            i++
        }
        // continuous emission
        if (c.rate > 0f) {
            e.spawnAccumulator += c.rate * dt
            while (e.spawnAccumulator >= 1f) {
                spawn(e, imageWidth, imageHeight)
                e.spawnAccumulator -= 1f
            }
        }
        // bursts (e.g. synced to a gallop)
        if (c.burstPeriod > 0f && c.burstCount > 0) {
            val index = floor((timeSeconds - c.burstPhase) / c.burstPeriod).toLong()
            if (index != e.lastBurstIndex) {
                if (e.lastBurstIndex != Long.MIN_VALUE) repeat(c.burstCount) { spawn(e, imageWidth, imageHeight) }
                e.lastBurstIndex = index
            }
        }
    }

    private fun spawn(
        e: Emitter,
        imageWidth: Float,
        imageHeight: Float,
    ) {
        if (e.count >= e.cap) return
        val c = e.config
        val n = e.count
        e.x[n] = rand(c.area[0], c.area[2]) * imageWidth
        e.y[n] = rand(c.area[1], c.area[3]) * imageHeight
        e.vx[n] = rand(c.vxMin, c.vxMax) * imageWidth
        e.vy[n] = rand(c.vyMin, c.vyMax) * imageWidth
        e.age[n] = 0f
        e.life[n] = rand(c.lifeMin, c.lifeMax)
        e.size[n] = rand(c.sizeMin, c.sizeMax) * imageWidth
        e.rot[n] = rand(0f, 360f)
        e.spin[n] = rand(c.spinMin, c.spinMax) * (180f / PI.toFloat())
        e.count++
    }

    private fun removeAt(
        e: Emitter,
        i: Int,
    ) {
        val last = e.count - 1
        e.x[i] = e.x[last]
        e.y[i] = e.y[last]
        e.vx[i] = e.vx[last]
        e.vy[i] = e.vy[last]
        e.age[i] = e.age[last]
        e.life[i] = e.life[last]
        e.size[i] = e.size[last]
        e.rot[i] = e.rot[last]
        e.spin[i] = e.spin[last]
        e.count = last
    }

    private fun drawEmitter(
        e: Emitter,
        frame: SceneFrame,
        sprite: SpriteProgram,
        ortho: FloatArray,
    ) {
        if (e.count == 0) return
        val c = e.config
        val depth = c.follow?.let { id -> layers.firstOrNull { it.config.id == id }?.config?.depth } ?: c.depth
        val scale = frame.drawWidth / frame.imageWidth
        val left = frame.centerX - frame.drawWidth / 2f + frame.parallaxX * depth
        val top = frame.centerY - frame.drawHeight / 2f + frame.parallaxY * depth

        setBlend(c.additive)
        GLES30.glUniform2f(sprite.uvOffset, 0f, 0f)
        GLES30.glUniform2f(sprite.uvScale, 1f, 1f)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, e.textureId)
        for (i in 0 until e.count) {
            val t = e.age[i] / e.life[i]
            val fadeIn = if (c.fadeIn > 0f) (t / c.fadeIn).coerceIn(0f, 1f) else 1f
            val fadeOut = ((1f - t) / 0.45f).coerceIn(0f, 1f)
            val alpha = c.alpha * fadeIn * fadeOut
            if (alpha <= 0.002f) continue
            val sizePx = e.size[i] * scale * (1f + (c.grow - 1f) * t)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, left + e.x[i] * scale, top + e.y[i] * scale, 0f)
            Matrix.rotateM(model, 0, e.rot[i], 0f, 0f, 1f)
            Matrix.scaleM(model, 0, sizePx / 2f, sizePx / 2f * e.aspect, 1f)
            Matrix.multiplyMM(mvp, 0, ortho, 0, model, 0)
            GLES30.glUniformMatrix4fv(sprite.mvp, 1, false, mvp, 0)
            GLES30.glUniform1f(sprite.alpha, alpha)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    // ------------------------------------------------------------------
    // GL helpers
    // ------------------------------------------------------------------

    private fun setBlend(additive: Boolean) {
        if (additive) {
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        } else {
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        }
    }

    private fun bindQuad(buffer: FloatBuffer) {
        buffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, Quad.getStride(), buffer)
        buffer.position(Quad.getUvOffset() / 4)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, Quad.getStride(), buffer)
    }

    private fun setRepeat(
        textureId: Int,
        repeatS: Boolean,
        repeatT: Boolean,
    ) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        if (repeatS) GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        if (repeatT) GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    private fun rand(
        a: Float,
        b: Float,
    ): Float = if (b <= a) a else a + random.nextFloat() * (b - a)

    companion object {
        /** Keeps a scroll offset in [0, 1) so float precision holds over long uptimes. */
        internal fun wrapOffset(value: Double): Float {
            val f = value - floor(value)
            return f.toFloat()
        }

        /** Sprite-sheet frame to show at [time] seconds; loops over [count] frames at [fps]. */
        internal fun frameIndex(
            time: Double,
            fps: Float,
            count: Int,
        ): Int {
            if (count <= 1 || fps <= 0f) return 0
            val i = floor(time * fps).toLong() % count
            return (if (i < 0) i + count else i).toInt()
        }
    }
}
