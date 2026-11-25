package dev.volhovm.appblocker

import android.content.Context

/** Persistent state. It survives restarts, so that the app can undo its own suspensions. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("state", Context.MODE_PRIVATE)

    var mode: Mode?
        get() = sp.getString(KEY_MODE, null)?.let(Mode::valueOf)
        set(value) = sp.edit().putString(KEY_MODE, value?.name).apply()

    /** The packages that this app suspended. Only these are unsuspended again. */
    var suspended: Set<String>
        get() = sp.getStringSet(KEY_SUSPENDED, null).orEmpty().toSet()
        set(value) = sp.edit().putStringSet(KEY_SUSPENDED, value).apply()

    /** The launcher for full mode, as "package/class". */
    var fullLauncher: String?
        get() = sp.getString(KEY_FULL_LAUNCHER, null)
        set(value) = sp.edit().putString(KEY_FULL_LAUNCHER, value).apply()

    /** Debug builds only: replaces the USB check. null means "use the real USB state". */
    var keyOverride: Boolean?
        get() = if (sp.contains(KEY_OVERRIDE)) sp.getBoolean(KEY_OVERRIDE, false) else null
        set(value) {
            if (value == null) sp.edit().remove(KEY_OVERRIDE).apply() else sp.edit().putBoolean(KEY_OVERRIDE, value).apply()
        }

    fun clear() = sp.edit().clear().commit()

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_SUSPENDED = "suspended"
        const val KEY_FULL_LAUNCHER = "full_launcher"
        const val KEY_OVERRIDE = "key_override"
    }
}
