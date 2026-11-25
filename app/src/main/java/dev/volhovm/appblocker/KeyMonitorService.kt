package dev.volhovm.appblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

/**
 * Keeps a process alive with runtime receivers: the system does not deliver USB attach and detach broadcasts to
 * receivers in the manifest. Screen-on and unlock checks catch an event that the receiver did not get.
 */
class KeyMonitorService : Service() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "Event: ${intent.action}")
            sync()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Key monitor", NotificationManager.IMPORTANCE_LOW))
        val notification = notification(Prefs(this).mode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // These are protected broadcasts: only the system can send them, so an exported receiver is safe.
        val events = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(receiver, events, RECEIVER_EXPORTED)
        // A newly installed app must be suspended at once in dumb mode.
        val packages = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        registerReceiver(receiver, packages, RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sync()
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun sync() {
        val mode = ModeController.sync(this) ?: return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(mode))
    }

    private fun notification(mode: Mode?): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(
                when (mode) {
                    Mode.FULL -> "Key present: full mode"
                    Mode.DUMB -> "Key absent: dumb mode"
                    null -> "Key monitor"
                },
            )
            .setOngoing(true)
            .build()

    companion object {
        private const val TAG = "AppBlocker"
        private const val CHANNEL = "monitor"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, KeyMonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeyMonitorService::class.java))
        }
    }
}
