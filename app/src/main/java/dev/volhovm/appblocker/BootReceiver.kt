package dev.volhovm.appblocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the monitor after a reboot and after each update of this app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KeyMonitorService.start(context)
    }
}
