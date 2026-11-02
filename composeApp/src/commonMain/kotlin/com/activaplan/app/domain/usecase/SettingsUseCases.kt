package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.cardio.HeartRateZones
import com.activaplan.app.domain.model.AppLanguage
import com.activaplan.app.domain.model.OnboardingChoices
import com.activaplan.app.domain.model.Settings
import com.activaplan.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase(private val userData: UserDataRepository) {
    operator fun invoke(): Flow<Settings> = userData.settings
}

class SetThemeUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(theme: ThemeMode) = userData.updateSettings { it.copy(theme = theme) }
}

/** Mengganti bahasa UI. Teks, konten latihan, dan isi notifikasi pengingat ikut berganti karena semuanya membaca `Settings.language`. */
class SetLanguageUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(language: AppLanguage) = userData.updateSettings { it.copy(language = language) }
}

/** Menyalakan atau mematikan semua pengingat; alarm diselaraskan oleh `ReminderSynchronizer` yang mengamati setelan. */
class SetRemindersEnabledUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(enabled: Boolean) = userData.updateSettings { it.copy(remindersEnabled = enabled) }
}

/** Nilai awal jam saat membuat jadwal baru; jadwal yang sudah ada tidak berubah. */
class SetDefaultTimeUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(timeMinutes: Int) {
        require(timeMinutes in 0..1439) { "timeMinutes di luar 0..1439: $timeMinutes" }
        userData.updateSettings { it.copy(defaultTimeMinutes = timeMinutes) }
    }
}

/** Mematikan tidak menghapus log lama; penghapusan lewat [ClearSessionHistoryUseCase]. */
class SetKeepHistoryUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(keep: Boolean) = userData.updateSettings { it.copy(keepHistory = keep) }
}

/**
 * Menandai Perkenalan dan Onboarding selesai. [choices] null = "Nanti saja": hanya menandai, setelan lain tidak berubah.
 * Pengingat hanya dinyalakan bila diminta; memilih tidak menyalakan tidak mematikan apa pun.
 */
class CompleteOnboardingUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(choices: OnboardingChoices?) {
        if (choices != null) require(choices.isValid) { "Pilihan onboarding tidak valid: $choices" }
        userData.updateSettings { s ->
            when {
                choices == null -> s.copy(onboardingDone = true)
                else -> s.copy(
                    onboardingDone = true,
                    startLevel = choices.startLevel,
                    ownedModes = choices.ownedModes,
                    remindersEnabled = if (choices.remindersOn) true else s.remindersEnabled,
                    defaultTimeMinutes = if (choices.remindersOn) choices.reminderTimeMinutes else s.defaultTimeMinutes,
                )
            }
        }
    }
}

class ClearSessionHistoryUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke() = userData.clearSessionLogs()
}

/** Mengosongkan rutinitas, jadwal, dan riwayat sesi. Setelan dan katalog latihan bawaan tidak tersentuh. */
class ClearAllDataUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke() = userData.clearAllData()
}

/** Menyalakan atau mematikan pelacakan GPS kardio. Izin lokasi diperiksa oleh pemanggil (ViewModel Setelan). */
class SetGpsTrackingUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(enabled: Boolean) = userData.updateSettings { it.copy(gpsTracking = enabled) }
}

/** Mengisi usia (10-90) untuk perkiraan zona detak jantung; null menghapusnya. */
class SetAgeUseCase(private val userData: UserDataRepository) {
    suspend operator fun invoke(age: Int?) {
        if (age != null) require(age in HeartRateZones.AGE) { "Usia di luar ${HeartRateZones.AGE}: $age" }
        userData.updateSettings { it.copy(age = age) }
    }
}
