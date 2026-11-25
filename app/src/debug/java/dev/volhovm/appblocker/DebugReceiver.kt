package dev.volhovm.appblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug builds only. Examples:
 *   adb shell am broadcast -n dev.volhovm.appblocker/.DebugReceiver --es key absent
 *   adb shell am broadcast -n dev.volhovm.appblocker/.DebugReceiver --es key present
 *   adb shell am broadcast -n dev.volhovm.appblocker/.DebugReceiver --es key real
 *   adb shell am broadcast -n dev.volhovm.appblocker/.DebugReceiver --es cmd release
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra("cmd") == "release") {
            ModeController.release(context)
            return
        }
        val override = when (intent.getStringExtra("key")) {
            "present" -> true
            "absent" -> false
            "real" -> null
            else -> {
                Log.w("AppBlocker", "Unknown command: ${intent.extras}")
                return
            }
        }
        Prefs(context).keyOverride = override
        Log.i("AppBlocker", "Key override: $override")
        ModeController.sync(context)
    }
}
