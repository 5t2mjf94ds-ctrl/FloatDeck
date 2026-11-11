package app.floatdeck.data

import app.floatdeck.R

/** 立绘特效类型。 */
enum class PortraitEffect(
    val key: String,
    val labelResId: Int,
) {
    NONE("none", R.string.effect_none),
    ICE("ice", R.string.effect_ice),
    HOLO("holo", R.string.effect_holo),
    ;

    companion object {
        fun fromKey(key: String): PortraitEffect = entries.find { it.key == key } ?: NONE
    }
}

/**
 * 肖像卡片位置参数（所有坐标为屏幕比例 0~1）。
 *
 * @param xRatio 水平位置比例
 * @param yRatio 垂直位置比例
 * @param rotation 旋转角度（度）
 * @param scale 缩放倍数
 * @param tiltOffset 视差灵敏度系数（越大跟随倾斜越明显）
 */
data class PortraitPosition(
    val xRatio: Float,
    val yRatio: Float,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val tiltOffset: Float = 1f,
)

/** 单张肖像卡片配置：资源路径 + 锁屏/解锁两种状态的位置。 */
data class PortraitConfig(
    val id: String,
    val assetPath: String,
    val label: String,
    val lockedPosition: PortraitPosition,
    val unlockedPosition: PortraitPosition,
    val isRemote: Boolean = false,
)

/** 模板配置：包含背景图和一组肖像卡片。 */
data class TemplateConfig(
    val id: String,
    val name: String,
    val wallpaperAsset: String?,
    val portraits: List<PortraitConfig>,
    val isRemote: Boolean = false,
    val effect: PortraitEffect = PortraitEffect.NONE,
    val layers: List<SceneLayerConfig> = emptyList(),
    val emitters: List<ParticleEmitterConfig> = emptyList(),
    val scene: SceneSettings = SceneSettings(),
)

/**
 * Idle motion for a scene layer. Amplitudes are fractions of the wallpaper image
 * (x of its width, y of its height); [period] is in seconds.
 *
 * @param bounce when true the vertical motion is a gallop-style hop (|sin|) instead of a smooth bob
 */
data class LayerSway(
    val x: Float = 0f,
    val y: Float = 0f,
    val period: Float = 6f,
    val phase: Float = 0f,
    val bounce: Boolean = false,
)

/**
 * Back-and-forth rotation of a scene layer around a pivot, like a leaf or a hanging olive
 * moving in the wind. The swing mixes in a faster, smaller second wave so it never looks
 * mechanical.
 *
 * @param angle swing amplitude in degrees
 * @param period seconds per full swing
 * @param phase radians; offset neighboring layers to make a wave travel along a branch
 * @param pivotX pivot inside the layer's rect (0 = left edge, 1 = right edge)
 * @param pivotY pivot inside the layer's rect (0 = top edge, 1 = bottom edge)
 * @param scale optional size breathing around the same pivot, as a fraction (0.04 = ±4 %)
 * @param droop how far (degrees) the layer leans toward real-world down when gravity pulls
 *   straight across it; it eases into the lean as the device is tilted (0 = ignores gravity)
 * @param dir direction the layer points from its pivot (e.g. pivot to leaf tip), in screen
 *   degrees: 0 = right, 90 = down, 180 = left, -90 = up
 */
data class LayerRock(
    val angle: Float = 0f,
    val period: Float = 4f,
    val phase: Float = 0f,
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
    val scale: Float = 0f,
    val droop: Float = 0f,
    val dir: Float = 0f,
)

/**
 * One extra image layer drawn between the wallpaper and the portrait cards.
 *
 * @param path absolute file path (imported templates) or asset path
 * @param rect where the layer sits on the wallpaper image, normalized [x0, y0, x1, y1]
 * @param depth parallax multiplier relative to the wallpaper (1 = moves with it, >1 = closer)
 * @param driftX horizontal texture scroll in layer widths per second (wraps; texture must tile)
 * @param driftY vertical texture scroll in layer heights per second (wraps; texture must tile)
 * @param pulseAmount 0..1 opacity breathing depth; [pulsePeriod] in seconds
 * @param pulsePhase radians; offset layers so a highlight travels across them
 * @param pulseSharpness exponent on the breathing wave; higher values make short, sharp peaks
 * @param pulseFlash when true the layer is mostly faded and lights up at the wave's peak
 *   (a sweeping highlight) instead of mostly showing and dipping
 * @param opaque the layer fully covers the wallpaper, so the wallpaper itself is not drawn
 * @param contained the layer's content fades out before its edges, so it never has to cover
 *   the screen and does not force the wallpaper to zoom in when it moves
 * @param follow id of a parent layer whose idle sway is added to this layer's own
 * @param spinDegPerSec continuous rotation around the layer's center (clockwise on screen)
 * @param rock optional back-and-forth swing around a pivot (leaves, hanging fruit)
 * @param frameCount number of animation frames in a sprite-sheet texture laid out as
 *   [frameCols] × [frameRows], left-to-right then top-to-bottom, played at [frameFps]
 */
data class SceneLayerConfig(
    val id: String,
    val path: String,
    val isRemote: Boolean,
    val rect: FloatArray = floatArrayOf(0f, 0f, 1f, 1f),
    val depth: Float = 1f,
    val alpha: Float = 1f,
    val sway: LayerSway? = null,
    val driftX: Float = 0f,
    val driftY: Float = 0f,
    val pulseAmount: Float = 0f,
    val pulsePeriod: Float = 8f,
    val pulsePhase: Float = 0f,
    val pulseSharpness: Float = 1f,
    val pulseFlash: Boolean = false,
    val additive: Boolean = false,
    val opaque: Boolean = false,
    val contained: Boolean = false,
    val follow: String? = null,
    val spinDegPerSec: Float = 0f,
    val frameCols: Int = 1,
    val frameRows: Int = 1,
    val frameCount: Int = 1,
    val frameFps: Float = 0f,
    val rock: LayerRock? = null,
)

/**
 * Template-wide motion settings for layered templates.
 *
 * @param recenterSeconds when > 0, tilt is measured relative to a slowly following rest
 *   pose (time constant in seconds), so the scene settles back to center whenever the
 *   device is held still at any angle; 0 keeps the absolute-tilt behavior
 * @param parallaxScale multiplier on top of the user's tilt-parallax setting
 */
data class SceneSettings(
    val recenterSeconds: Float = 0f,
    val parallaxScale: Float = 1f,
)

/**
 * A sprite particle emitter (dust, sparks, motes). Positions are normalized to the
 * wallpaper image; sizes and velocities are in wallpaper-image widths (per second).
 *
 * @param follow id of a scene layer whose parallax depth the particles share
 * @param burstPeriod when > 0, emits [burstCount] particles every [burstPeriod] seconds
 * @param gravity vertical acceleration (negative rises)
 * @param drag exponential velocity damping per second
 */
data class ParticleEmitterConfig(
    val path: String,
    val isRemote: Boolean,
    val follow: String? = null,
    val depth: Float = 1f,
    val area: FloatArray = floatArrayOf(0.4f, 0.4f, 0.6f, 0.6f),
    val rate: Float = 0f,
    val burstPeriod: Float = 0f,
    val burstCount: Int = 0,
    val burstPhase: Float = 0f,
    val lifeMin: Float = 1f,
    val lifeMax: Float = 2f,
    val sizeMin: Float = 0.02f,
    val sizeMax: Float = 0.04f,
    val grow: Float = 1f,
    val vxMin: Float = 0f,
    val vxMax: Float = 0f,
    val vyMin: Float = 0f,
    val vyMax: Float = 0f,
    val gravity: Float = 0f,
    val drag: Float = 0f,
    val alpha: Float = 1f,
    val fadeIn: Float = 0.15f,
    val spinMin: Float = 0f,
    val spinMax: Float = 0f,
    val additive: Boolean = false,
    val maxParticles: Int = 160,
)
