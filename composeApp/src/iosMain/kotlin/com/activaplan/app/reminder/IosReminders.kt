package com.activaplan.app.reminder

import com.activaplan.app.domain.reminder.NotificationPermission
import com.activaplan.app.domain.reminder.PermissionState
import com.activaplan.app.domain.reminder.Reminder
import com.activaplan.app.domain.reminder.ReminderScheduler
import com.activaplan.app.domain.reminder.nearestReminders
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.time.SystemLocalClock
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDateComponents
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** iOS menyimpan paling banyak 64 notifikasi tertunda; yang paling dekat bunyi didahulukan. */
private const val PENDING_LIMIT = 64

/** Tanpa delegate, iOS menyembunyikan notifikasi yang tiba saat aplikasi sedang terbuka. */
private class ForegroundPresenter : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound,
        )
    }
}

// Delegate iOS hanya direferensikan lemah, jadi instance ini harus dipegang di sini.
private val foregroundPresenter = ForegroundPresenter()

internal fun installForegroundPresenter() {
    UNUserNotificationCenter.currentNotificationCenter().delegate = foregroundPresenter
}

class IosNotificationPermission : NotificationPermission {

    init {
        installForegroundPresenter()
    }

    override suspend fun status(): PermissionState = suspendCoroutine { continuation ->
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            continuation.resume(
                when (settings?.authorizationStatus) {
                    UNAuthorizationStatusNotDetermined -> PermissionState.UNDETERMINED
                    UNAuthorizationStatusDenied, null -> PermissionState.DENIED
                    else -> PermissionState.GRANTED
                },
            )
        }
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
    }
}

/**
 * Pengingat mingguan lewat trigger kalender berulang (hari + jam); tiap pengingat satu permintaan bernama ID entri
 * jadwal. Dipasang ulang penuh setiap [replaceAll], termasuk saat aplikasi dibuka.
 */
@OptIn(ExperimentalForeignApi::class)
class IosReminderScheduler(private val clock: LocalClock = SystemLocalClock) : ReminderScheduler {

    init {
        installForegroundPresenter()
    }

    override suspend fun replaceAll(reminders: List<Reminder>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()
        nearestReminders(reminders, clock.now(), PENDING_LIMIT).forEach { reminder ->
            val content = UNMutableNotificationContent().apply {
                setTitle(reminder.title)
                setBody(reminder.body)
                setSound(UNNotificationSound.defaultSound())
            }
            val components = NSDateComponents().apply {
                // NSCalendar Gregorian: 1 = Minggu, 2 = Senin .. 7 = Sabtu.
                weekday = (reminder.dayOfWeek % 7 + 1).toLong()
                hour = (reminder.timeMinutes / 60).toLong()
                minute = (reminder.timeMinutes % 60).toLong()
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(reminder.id, content, trigger),
                withCompletionHandler = null,
            )
        }
    }
}
