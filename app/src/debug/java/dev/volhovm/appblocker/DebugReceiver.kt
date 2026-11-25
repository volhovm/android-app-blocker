package dev.volhovm.appblocker

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Debug builds only. Examples:
 *   adb shell am broadcast -n dev.volhovm.appblocker/.DebugReceiver --es key absent
 *   adb shell am broadcast -n dev.volhovm.appblocker/.DebugReceiver --es key present
 *   adb shell am broadcast -n dev.volhovm.appblocker/.DebugReceiver --es key real
 *   adb shell am broadcast -n dev.volhovm.appblocker/.DebugReceiver --es cmd release
 *   adb shell am broadcast -n dev.volhovm.appblocker/.DebugReceiver --es cmd status
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("cmd")) {
            "release" -> return ModeController.release(context)
            "status" -> return status(context)
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

    /** Logs the state and what dumb mode would do. It changes nothing, so it also works before the device owner setup. */
    private fun status(context: Context) {
        val pm = context.packageManager
        val devices = context.getSystemService(UsbManager::class.java)?.deviceList?.values.orEmpty()
        log("USB devices: " + devices.joinToString { "%04x:%04x %s %s".format(it.vendorId, it.productId, it.manufacturerName, it.productName) })
        log("Key present (real USB): ${Policy.isKeyPresent(devices.map { it.vendorId })}, override: ${Prefs(context).keyOverride}")
        log("Device owner: ${context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)}, mode: ${Prefs(context).mode}")
        val homes = Apps.homeApps(pm)
        log("Launchers: ${homes.map { it.flatName }}, current: ${Apps.currentHome(pm)}")
        log("Allowlist: ${Apps.allowlist(context).sorted()}")
        log("Keyboards: ${Apps.inputMethods(context).sorted()}")
        log("Dumb mode suspends: ${ModeController.suspendTargets(context, homes).sorted()}")
    }

    private fun log(message: String) = Log.i("AppBlocker", message)
}
