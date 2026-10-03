package com.snapnet

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.snapnet.util.NotificationUtil
import com.snapnet.util.NotificationUtil.SERVICE_NOTIFICATION_ID

private const val TAG = "DownloadService"

/**
 * Keeps SnapNet alive while downloads are in flight.
 *
 * This is a genuine **foreground** service: it is started with
 * [androidx.core.content.ContextCompat.startForegroundService] and immediately calls
 * [startForeground], so Android keeps the process (and therefore the child yt-dlp processes) alive
 * when the user leaves the app or turns the screen off.
 *
 * The previous implementation only called `startForeground` from `onBind`. A purely *bound* service
 * is not exempt from background-process reclamation and is torn down with the client, so downloads
 * could be killed as soon as the app was backgrounded. Binding is still supported — the download
 * engine lives in the app process and uses the binder only as a liveness signal — but the service's
 * lifecycle is no longer tied to the binding.
 *
 * The service holds no work of its own; it exists purely to hold the process priority, and is
 * stopped as soon as no task is running (see [stopForegroundCompat]) so no permanent notification is
 * ever shown.
 */
class DownloadService : Service() {

    /** Guards against calling `startForeground` twice, which is a no-op but noisy in logs. */
    private var isForeground = false

    override fun onBind(intent: Intent): IBinder {
        promoteToForeground()
        return DownloadServiceBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        // The task queue lives in the application scope, so there is nothing to restart here.
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        if (isForeground) return
        runCatching {
                val pendingIntent =
                    Intent(this, MainActivity::class.java).let {
                        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
                    }
                val notification = NotificationUtil.makeServiceNotification(pendingIntent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        SERVICE_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                } else {
                    startForeground(SERVICE_NOTIFICATION_ID, notification)
                }
                isForeground = true
                Log.d(TAG, "promoted to foreground")
            }
            .onFailure { Log.w(TAG, "could not promote the download service to foreground", it) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Deliberately do *not* stop here: the caller (App.stopService) stops the service once the
        // last task finishes, which is the only point at which the notification should disappear.
        Log.d(TAG, "onUnbind")
        return super.onUnbind(intent)
    }

    /** Removes the foreground notification and stops the service. Idempotent. */
    fun stopForegroundCompat() {
        runCatching {
                if (isForeground) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION") stopForeground(true)
                    }
                    isForeground = false
                }
                stopSelf()
            }
            .onFailure { Log.w(TAG, "could not stop the download service cleanly", it) }
    }

    override fun onDestroy() {
        isForeground = false
        super.onDestroy()
    }

    inner class DownloadServiceBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
}
