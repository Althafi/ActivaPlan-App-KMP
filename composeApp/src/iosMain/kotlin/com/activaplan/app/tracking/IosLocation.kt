package com.activaplan.app.tracking

import com.activaplan.app.domain.reminder.PermissionState
import com.activaplan.app.domain.tracking.LocationTracker
import com.activaplan.app.domain.tracking.TrackPoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.CoreLocation.CLActivityTypeFitness
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLDistanceFilterNone
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.NSObject

/**
 * Satu `CLLocationManager` untuk seluruh aplikasi: penerima titik, dan juga penjawab dialog izin. Memakai penerima GPS
 * perangkat (tanpa internet). Pembaruan latar butuh `UIBackgroundModes = location` di Info.plist.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosLocationHub : NSObject(), CLLocationManagerDelegateProtocol {
    private val manager = CLLocationManager()
    private var pendingAuthorization: (() -> Unit)? = null

    val points = MutableSharedFlow<TrackPoint>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        manager.delegate = this
    }

    fun permission(): PermissionState = when (manager.authorizationStatus) {
        kCLAuthorizationStatusNotDetermined -> PermissionState.UNDETERMINED
        kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> PermissionState.GRANTED
        else -> PermissionState.DENIED
    }

    /** Menampilkan dialog izin bila belum pernah ditanyakan; [done] dipanggil setelah pengguna menjawab. */
    fun requestAuthorization(done: () -> Unit) {
        if (manager.authorizationStatus != kCLAuthorizationStatusNotDetermined) {
            done()
            return
        }
        pendingAuthorization = done
        manager.requestWhenInUseAuthorization()
    }

    fun start() {
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        manager.activityType = CLActivityTypeFitness
        manager.pausesLocationUpdatesAutomatically = false
        manager.allowsBackgroundLocationUpdates = true
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
    }

    fun stop() {
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        didUpdateLocations.forEach { item ->
            val location = item as? CLLocation ?: return@forEach
            val coordinate = location.coordinate.useContents { latitude to longitude }
            points.tryEmit(
                TrackPoint(
                    latitude = coordinate.first,
                    longitude = coordinate.second,
                    timeMillis = (location.timestamp.timeIntervalSince1970 * 1000).toLong(),
                    accuracyMeters = location.horizontalAccuracy.takeIf { it >= 0 }?.toFloat(),
                ),
            )
        }
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (manager.authorizationStatus != kCLAuthorizationStatusNotDetermined) {
            pendingAuthorization?.invoke()
            pendingAuthorization = null
        }
    }
}

class IosLocationTracker : LocationTracker {
    override val points: Flow<TrackPoint> = IosLocationHub.points

    override suspend fun permission(): PermissionState = IosLocationHub.permission()

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
    }

    override fun start(title: String, text: String) = IosLocationHub.start()

    override fun stop() = IosLocationHub.stop()
}
