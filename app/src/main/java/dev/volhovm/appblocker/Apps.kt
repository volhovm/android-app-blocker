package dev.volhovm.appblocker

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/** Package manager queries. */
object Apps {
    val homeIntent: Intent get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun homeApps(pm: PackageManager): List<HomeApp> =
        pm.queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(0)).map {
            val ai = it.activityInfo
            HomeApp(ai.packageName, ai.name, (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0, it.priority)
        }

    fun currentHome(pm: PackageManager): String? =
        pm.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            ?.activityInfo?.packageName

    fun launchablePackages(pm: PackageManager): Set<String> =
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.ResolveInfoFlags.of(0),
        ).map { it.activityInfo.packageName }.toSet()

    fun inputMethods(context: Context): Set<String> =
        context.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()

    /** Phone, contacts, SMS, clock, camera, maps and Settings. The roles are resolved, so the list works on any device. */
    fun allowlist(context: Context): Set<String> {
        val pm = context.packageManager
        return buildSet {
            context.getSystemService(TelecomManager::class.java).defaultDialerPackage?.let(::add)
            Telephony.Sms.getDefaultSmsPackage(context)?.let(::add)
            addAll(handlers(pm, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)))
            addAll(handlers(pm, Intent(AlarmClock.ACTION_SHOW_ALARMS)))
            addAll(handlers(pm, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)))
            addAll(handlers(pm, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))))
            // Settings stays usable: Wi-Fi, Bluetooth and similar settings are necessary on a dumb phone too.
            addAll(handlers(pm, Intent(Settings.ACTION_SETTINGS)))
        }
    }

    /** The handlers of the intent that the allowlist permits. See [Policy.allowedHandlers]. */
    private fun handlers(pm: PackageManager, intent: Intent): Set<String> {
        val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        // "android" is the system chooser: the user did not select a default handler.
        val default = pm.resolveActivity(intent, flags)?.activityInfo?.packageName?.takeIf { it != "android" }
        val handlers = pm.queryIntentActivities(intent, flags).map {
            Handler(it.activityInfo.packageName, (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
        }
        return Policy.allowedHandlers(default, handlers)
    }
}
