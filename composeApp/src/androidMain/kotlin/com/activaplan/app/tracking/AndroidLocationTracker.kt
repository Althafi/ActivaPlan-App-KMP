package com.activaplan.app.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.activaplan.app.domain.reminder.PermissionState
import com.activaplan.app.domain.tracking.LocationTracker
import com.activaplan.app.domain.tracking.TrackPoint
import kotlinx.coroutines.flow.Flow

/** GPS Android lewat [TrackingService] dan `LocationManager` bawaan: offline, tanpa Google Play Services. */
class AndroidLocationTracker(private val context: Context) : LocationTracker {

    override val points: Flow<TrackPoint> = TrackingHub.points

    override suspend fun permission(): PermissionState = when {
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> PermissionState.GRANTED
        !LocationPrefs.wasAsked(context) -> PermissionState.UNDETERMINED
        else -> PermissionState.DENIED
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    override fun start(title: String, text: String) = TrackingService.start(context, title, text)

    override fun stop() = TrackingService.stop(context)
}
