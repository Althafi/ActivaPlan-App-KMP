package com.activaplan.app.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.activaplan.app.domain.tracking.TrackPoint

/**
 * Layanan latar untuk pelacakan GPS saat layar terkunci: notifikasi tetap, kunci CPU parsial, dan pembaruan lokasi
 * satu per detik dari penerima GPS perangkat (tanpa internet, tanpa layanan Google). Titik diteruskan ke [TrackingHub].
 */
class TrackingService : Service() {

    private var manager: LocationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            TrackingHub.emit(
                TrackPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timeMillis = location.time,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                ),
            )
        }

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Tidak dipanggil di Android 29 ke atas; ada agar antarmuka lengkap di Android 26-28.")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification(intent?.getStringExtra(EXTRA_TITLE).orEmpty(), intent?.getStringExtra(EXTRA_TEXT).orEmpty())
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            // Izin lokasi dicabut tepat sebelum layanan mulai: tidak ada yang bisa dilacak.
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        startUpdates()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        manager?.removeUpdates(listener)
        manager = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun startUpdates() {
        if (manager != null) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MILLIS, 0f, listener, Looper.getMainLooper())
                manager = lm
            }
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ActivaPlan:tracking").apply { acquire(MAX_WAKE_MILLIS) }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val channel = NotificationChannel(CHANNEL_ID, title.ifBlank { "ActivaPlan" }, NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(applicationInfo.icon)
            .setContentTitle(title.ifBlank { "ActivaPlan" })
            .setContentText(text)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "workout_tracking"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "com.activaplan.app.action.STOP_TRACKING"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val UPDATE_INTERVAL_MILLIS = 1_000L
        private const val MAX_WAKE_MILLIS = 4 * 60 * 60 * 1_000L

        fun start(context: Context, title: String, text: String) {
            val intent = Intent(context, TrackingService::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }
}
