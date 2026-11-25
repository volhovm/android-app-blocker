package dev.volhovm.appblocker

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log

/** Applies the mode for the current key state. All device owner calls are here. */
object ModeController {
    private const val TAG = "AppBlocker"

    /** Returns the applied mode, or null if this app is not the device owner. */
    fun sync(context: Context): Mode? {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not the device owner: no change")
            return null
        }
        val admin = AdminReceiver.component(context)
        val pm = context.packageManager
        val prefs = Prefs(context)
        dpm.setPermissionGrantState(
            admin, context.packageName, Manifest.permission.POST_NOTIFICATIONS,
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
        )

        val homes = Apps.homeApps(pm)
        // Remember the launcher of the user before this app replaces it.
        val full = Policy.chooseFullLauncher(homes, prefs.fullLauncher, Apps.currentHome(pm), context.packageName)
        full?.let { prefs.fullLauncher = it.flatName }

        val mode = if (isKeyPresent(context, prefs)) Mode.FULL else Mode.DUMB
        // Order: suspend first, then the launcher, then the home screen. Thus no blocked app stays on screen.
        when (mode) {
            Mode.DUMB -> {
                val target = Policy.packagesToSuspend(
                    launchable = Apps.launchablePackages(pm),
                    allowlist = Apps.allowlist(context),
                    homePackages = homes.map { it.packageName }.toSet(),
                    inputMethods = Apps.inputMethods(context),
                    self = context.packageName,
                )
                setSuspended(dpm, admin, prefs.suspended - target, false)
                val failed = setSuspended(dpm, admin, target, true)
                prefs.suspended = target - failed
                setHome(dpm, admin, homes, ComponentName(context, MinimalLauncherActivity::class.java))
            }
            Mode.FULL -> {
                setSuspended(dpm, admin, prefs.suspended, false)
                prefs.suspended = emptySet()
                if (full != null) {
                    setHome(dpm, admin, homes, ComponentName(full.packageName, full.className))
                } else {
                    Log.w(TAG, "No full launcher found: the system will ask the user")
                    clearHome(dpm, admin, homes)
                }
            }
        }

        if (prefs.mode != mode) {
            Log.i(TAG, "Mode ${prefs.mode} -> $mode")
            prefs.mode = mode
            context.startActivity(Apps.homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return mode
    }

    /** Undoes all changes and removes the device owner. After this, you can uninstall the app. */
    fun release(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val admin = AdminReceiver.component(context)
        val prefs = Prefs(context)
        KeyMonitorService.stop(context)
        setSuspended(dpm, admin, prefs.suspended, false)
        clearHome(dpm, admin, Apps.homeApps(context.packageManager))
        prefs.clear()
        dpm.clearDeviceOwnerApp(context.packageName)
        Log.i(TAG, "Released: no longer the device owner")
    }

    private fun isKeyPresent(context: Context, prefs: Prefs): Boolean {
        if (BuildConfig.DEBUG) prefs.keyOverride?.let { return it }
        val usb = context.getSystemService(UsbManager::class.java) ?: return false
        return Policy.isKeyPresent(usb.deviceList.values.map { it.vendorId })
    }

    /** Returns the packages that the system refused to change, for example the default dialer. */
    private fun setSuspended(dpm: DevicePolicyManager, admin: ComponentName, packages: Set<String>, suspended: Boolean): Set<String> {
        if (packages.isEmpty()) return emptySet()
        val failed = dpm.setPackagesSuspended(admin, packages.toTypedArray(), suspended).toSet()
        Log.i(TAG, "suspended=$suspended: ${packages - failed}" + if (failed.isEmpty()) "" else ", refused: $failed")
        return failed
    }

    private fun setHome(dpm: DevicePolicyManager, admin: ComponentName, homes: List<HomeApp>, target: ComponentName) {
        clearHome(dpm, admin, homes)
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, filter, target)
    }

    private fun clearHome(dpm: DevicePolicyManager, admin: ComponentName, homes: List<HomeApp>) {
        homes.map { it.packageName }.distinct().forEach { dpm.clearPackagePersistentPreferredActivities(admin, it) }
    }
}
