package com.activaplan.app.di

import com.activaplan.app.data.local.DATASTORE_FILE_NAME
import com.activaplan.app.cue.IosCuePlayer
import com.activaplan.app.domain.cue.CuePlayer
import com.activaplan.app.domain.info.AppInfo
import com.activaplan.app.domain.tracking.LocationTracker
import com.activaplan.app.tracking.IosLocationTracker
import com.activaplan.app.domain.reminder.NotificationPermission
import com.activaplan.app.domain.reminder.ReminderScheduler
import com.activaplan.app.info.IosAppInfo
import com.activaplan.app.reminder.IosNotificationPermission
import com.activaplan.app.reminder.IosReminderScheduler
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun platformModule(): Module = module {
    single {
        DataStorePathProvider {
            val dir = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
            requireNotNull(dir).path + "/" + DATASTORE_FILE_NAME
        }
    }
    single<NotificationPermission> { IosNotificationPermission() }
    single<ReminderScheduler> { IosReminderScheduler() }
    single<AppInfo> { IosAppInfo() }
    single<LocationTracker> { IosLocationTracker() }
    single<CuePlayer> { IosCuePlayer() }
}
