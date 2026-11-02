package com.activaplan.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RoutineItem(
    val exerciseId: String,
    val sets: Int,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val restSeconds: Int,
)

@Serializable
data class Routine(
    val id: String,
    val name: String,
    val items: List<RoutineItem>,
)

/** Batas rutinitas dari `docs/features/03-routines.md`. */
object RoutineRules {
    const val MIN_ITEMS = 1
    const val MAX_ITEMS = 12
    const val MAX_NAME_LENGTH = 40

    val SETS = 1..6
    val REPS = 1..50

    /** Latihan durasi, dalam langkah [SECONDS_STEP]. */
    val SECONDS = 5..180
    const val SECONDS_STEP = 5
    val REST_SECONDS = 15..180

    fun isValidName(name: String): Boolean = name.trim().length in 1..MAX_NAME_LENGTH
}

/** [dayOfWeek]: 1 = Senin .. 7 = Minggu. [timeMinutes]: menit sejak 00:00. */
@Serializable
data class ScheduleEntry(
    val id: String,
    val dayOfWeek: Int,
    val timeMinutes: Int,
    val routineId: String,
    val reminder: Boolean,
)

@Serializable
data class SessionLog(
    val date: String,
    val routineId: String,
    val status: SessionStatus,
    val completedSets: Int,
    val totalSets: Int,
    /** Jarak GPS dalam meter; 0 bila GPS mati atau sesi tanpa kardio. */
    val distanceMeters: Int = 0,
)

/** Pekan program yang terakhir diterapkan; [startedOn] adalah tanggal ISO (yyyy-MM-dd) saat pekan itu diterapkan. */
@Serializable
data class ActiveProgram(val programId: String, val week: Int, val startedOn: String)

@Serializable
data class Settings(
    val theme: ThemeMode,
    val remindersEnabled: Boolean,
    val defaultTimeMinutes: Int,
    val keepHistory: Boolean,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** False sampai Perkenalan dan Onboarding selesai atau dilewati; data lama yang sudah berisi dianggap selesai (lihat `UserDataStore`). */
    val onboardingDone: Boolean = false,
    /** Level awal dari Onboarding; dipakai rekomendasi Beranda sampai ada sesi selesai. */
    val startLevel: Difficulty = Difficulty.EASY,
    /** Mode alat yang dimiliki; menyaring rekomendasi Beranda dan menentukan mode awal Jelajah. Tidak pernah kosong. */
    val ownedModes: Set<Mode> = setOf(Mode.NO_EQUIPMENT),
    /** Lacak jarak dan pace dengan GPS saat kardio. Hanya menyala bila izin lokasi diberikan; data tetap di perangkat. */
    val gpsTracking: Boolean = false,
    /** Usia untuk perkiraan zona detak jantung; null = belum diisi (zona tampil dalam persen). */
    val age: Int? = null,
    /** Program bertahap yang sedang dijalani; null bila belum ada. Diisi saat pekan program diterapkan, dihapus bersama semua data. */
    val activeProgram: ActiveProgram? = null,
) {
    companion object {
        /** Nilai awal sebelum pengguna mengubah setelan; waktu default 16.30. */
        val DEFAULT = Settings(
            theme = ThemeMode.SYSTEM,
            remindersEnabled = true,
            defaultTimeMinutes = 16 * 60 + 30,
            keepHistory = true,
        )
    }
}

/** Seluruh data pengguna yang tersimpan, apa adanya (belum divalidasi terhadap katalog latihan). */
data class UserData(
    val routines: List<Routine> = emptyList(),
    val schedule: List<ScheduleEntry> = emptyList(),
    val sessionLogs: List<SessionLog> = emptyList(),
    val settings: Settings = Settings.DEFAULT,
)
