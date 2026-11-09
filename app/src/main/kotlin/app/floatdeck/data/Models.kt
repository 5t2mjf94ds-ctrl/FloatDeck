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
 * One extra image layer drawn between the wallpaper and the portrait cards.
 *
 * @param path absolute file path (imported templates) or asset path
 * @param rect where the layer sits on the wallpaper image, normalized [x0, y0, x1, y1]
 * @param depth parallax multiplier relative to the wallpaper (1 = moves with it, >1 = closer)
 * @param driftX horizontal texture scroll in layer widths per second (wraps; texture must tile)
 * @param driftY vertical texture scroll in layer heights per second (wraps; texture must tile)
 * @param pulseAmount 0..1 opacity breathing depth; [pulsePeriod] in seconds
 * @param opaque the layer fully covers the wallpaper, so the wallpaper itself is not drawn
 * @param contained the layer's content fades out before its edges, so it never has to cover
 *   the screen and does not force the wallpaper to zoom in when it moves
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
    val additive: Boolean = false,
    val opaque: Boolean = false,
    val contained: Boolean = false,
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
