package app.floatdeck.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Separate themes for the home screen and the lock screen.
 *
 * The app's selected template ([KEY_SELECTED]) is what the gallery shows and what the
 * system picker previews. The running wallpapers each read their own slot: a lock-screen
 * wallpaper reads [KEY_LOCK], a home-screen (or shared home + lock) wallpaper reads
 * [KEY_HOME]. Slots are filled when a theme is applied to a screen; an empty slot falls
 * back to the selected template, so older installs keep working unchanged.
 */
object ScreenSlots {
    const val PREFS_NAME = "settings_prefs"
    const val KEY_SELECTED = "template_id"
    const val KEY_HOME = "template_id_home"
    const val KEY_LOCK = "template_id_lock"

    // Same values as WallpaperManager.FLAG_SYSTEM / FLAG_LOCK
    const val FLAG_SYSTEM = 1
    const val FLAG_LOCK = 2
    const val FLAG_BOTH = FLAG_SYSTEM or FLAG_LOCK

    /** Where the user wants a theme to go. */
    enum class Target { HOME, LOCK, BOTH }

    /**
     * Preference key a wallpaper reads: previews follow the app's selection, a lock-only
     * wallpaper reads the lock slot and everything else reads the home slot.
     */
    fun keyFor(
        flags: Int,
        isPreview: Boolean,
    ): String =
        when {
            isPreview -> KEY_SELECTED
            flags == FLAG_LOCK -> KEY_LOCK
            else -> KEY_HOME
        }

    /** Slot keys for the screens in [which] (a WallpaperManager flag combination). */
    fun keysForFlags(which: Int): List<String> =
        buildList {
            if (which and FLAG_SYSTEM != 0) add(KEY_HOME)
            if (which and FLAG_LOCK != 0) add(KEY_LOCK)
        }

    /**
     * What applying a theme does.
     *
     * @param writes slot keys to set, and their template ids
     * @param needsPicker true when FloatDeck isn't yet running on the chosen screen(s) on its
     *   own, so the system wallpaper picker still has to be opened
     */
    data class Plan(
        val writes: Map<String, String>,
        val needsPicker: Boolean,
    )

    /**
     * Plans applying [templateId] to [target], given the screen flags of the FloatDeck
     * wallpapers running right now ([active]) and the home slot's current theme.
     *
     * When one wallpaper is shared by home and lock, giving only one screen a new theme
     * needs the picker to split them; the other screen keeps the theme it shows now.
     */
    fun plan(
        target: Target,
        templateId: String,
        active: Collection<Int>,
        currentHome: String,
    ): Plan {
        val homeOnly = FLAG_SYSTEM in active
        val lockOnly = FLAG_LOCK in active
        val shared = FLAG_BOTH in active
        return when (target) {
            Target.HOME -> {
                val writes = linkedMapOf(KEY_HOME to templateId)
                if (!homeOnly && shared && currentHome.isNotBlank()) writes[KEY_LOCK] = currentHome
                Plan(writes, needsPicker = !homeOnly)
            }
            Target.LOCK -> Plan(linkedMapOf(KEY_LOCK to templateId), needsPicker = !lockOnly)
            Target.BOTH ->
                Plan(
                    linkedMapOf(KEY_HOME to templateId, KEY_LOCK to templateId),
                    needsPicker = !(shared || (homeOnly && lockOnly)),
                )
        }
    }

    // Running (non-preview) wallpapers in this process and the screens they draw on
    private val active = ConcurrentHashMap<Any, Int>()

    fun register(
        engine: Any,
        flags: Int,
    ) {
        active[engine] = flags
    }

    fun unregister(engine: Any) {
        active.remove(engine)
    }

    fun activeFlags(): List<Int> = active.values.toList()
}
