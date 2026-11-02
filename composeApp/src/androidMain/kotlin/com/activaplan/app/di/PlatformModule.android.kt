package com.activaplan.app.di

import android.content.Context
import com.activaplan.app.data.local.DATASTORE_FILE_NAME
import com.activaplan.app.cue.AndroidCuePlayer
import com.activaplan.app.domain.cue.CuePlayer
import com.activaplan.app.domain.info.AppInfo
import com.activaplan.app.domain.tracking.LocationTracker
import com.activaplan.app.tracking.AndroidLocationTracker
import com.activaplan.app.domain.reminder.NotificationPermission
import com.activaplan.app.domain.reminder.ReminderScheduler
import com.activaplan.app.info.AndroidAppInfo
import com.activaplan.app.reminder.AndroidNotificationPermission
import com.activaplan.app.reminder.AndroidReminderScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single {
        val context = get<Context>()
        DataStorePathProvider { context.filesDir.resolve(DATASTORE_FILE_NAME).absolutePath }
    }
    single<NotificationPermission> { AndroidNotificationPermission(get()) }
    single<ReminderScheduler> { AndroidReminderScheduler(get()) }
    single<AppInfo> { AndroidAppInfo(get()) }
    single<LocationTracker> { AndroidLocationTracker(get()) }
    single<CuePlayer> { AndroidCuePlayer(get()) }
}
