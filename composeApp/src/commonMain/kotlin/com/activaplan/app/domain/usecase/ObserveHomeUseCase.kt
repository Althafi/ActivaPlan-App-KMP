package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.domain.i18n.resolve
import com.activaplan.app.domain.model.GreetingPeriod
import com.activaplan.app.domain.model.HomeData
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.Settings
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.validation.UserDataValidation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private const val MINUTE_MILLIS = 60_000L

/** Satu ketukan tiap menit agar "belum lewat" dan pergantian hari ikut diperbarui. */
fun minuteTicks(): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(MINUTE_MILLIS)
    }
}

/**
 * Data Beranda yang selalu mengikuti perubahan rutinitas, jadwal, riwayat, setelan, dan waktu ([ticks]).
 * Entri jadwal atau rutinitas yang rusak diabaikan lewat [UserDataRepository.validated].
 * Melempar bila katalog latihan gagal dimuat.
 */
class ObserveHomeUseCase(
    private val userData: UserDataRepository,
    private val exercises: ExerciseRepository,
    private val clock: LocalClock,
    private val ticks: Flow<Unit> = minuteTicks(),
    private val getToday: GetTodayWorkoutUseCase = GetTodayWorkoutUseCase(),
    private val calculateWeekStreak: CalculateWeekStreakUseCase = CalculateWeekStreakUseCase(),
    private val recommendTemplates: RecommendTemplatesUseCase = RecommendTemplatesUseCase(),
) {

    operator fun invoke(): Flow<HomeData> =
        combine(userData.validated, userData.sessionLogs, userData.settings, ticks) { validated, logs, settings, _ ->
            Snapshot(validated, logs, settings)
        }.map { build(it) }

    private class Snapshot(val validated: UserDataValidation, val logs: List<SessionLog>, val settings: Settings)

    private suspend fun build(s: Snapshot): HomeData {
        val catalog = exercises.catalog()
        val byId = catalog.byId
        val modes: Map<String, Mode> = catalog.modeById
        val now = clock.now()
        val templates = exercises.templates(s.settings.language.resolve())
        return HomeData(
            greeting = GreetingPeriod.at(now.hour),
            today = getToday(s.validated.schedule, s.validated.routines, modes, now),
            week = calculateWeekStreak(s.validated.schedule, s.logs, now.date),
            recommendations = recommendTemplates(
                templates, s.validated.routines, s.logs, byId,
                startLevel = s.settings.startLevel,
                ownedModes = s.settings.ownedModes,
            ),
        )
    }
}
