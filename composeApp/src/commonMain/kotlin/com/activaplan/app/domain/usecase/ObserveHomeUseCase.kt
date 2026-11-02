package com.activaplan.app.domain.usecase

import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.data.repository.WeeklyPlanRepository
import com.activaplan.app.domain.i18n.Language
import com.activaplan.app.domain.i18n.resolve
import com.activaplan.app.domain.model.GreetingPeriod
import com.activaplan.app.domain.model.HomeData
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.ProgramProgress
import com.activaplan.app.domain.model.SessionLog
import com.activaplan.app.domain.model.programWeekId
import com.activaplan.app.domain.model.Settings
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.validation.UserDataValidation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

private const val MINUTE_MILLIS = 60_000L
private const val DAYS_IN_PROGRAM_WEEK = 7

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
    private val plans: WeeklyPlanRepository? = null,
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
        val language = s.settings.language.resolve()
        val templates = exercises.templates(language)
        return HomeData(
            greeting = GreetingPeriod.at(now.hour),
            today = getToday(s.validated.schedule, s.validated.routines, modes, now),
            week = calculateWeekStreak(s.validated.schedule, s.logs, now.date),
            recommendations = recommendTemplates(
                templates, s.validated.routines, s.logs, byId,
                startLevel = s.settings.startLevel,
                ownedModes = s.settings.ownedModes,
            ),
            program = programProgress(s.settings, now.date, language),
        )
    }

    /** Kemajuan program aktif; null bila tidak ada, programnya sudah tidak di katalog, atau katalog program tidak tersedia. */
    private suspend fun programProgress(settings: Settings, today: LocalDate, language: Language): ProgramProgress? {
        val active = settings.activeProgram ?: return null
        val program = plans?.program(active.programId, language) ?: return null
        val current = program.weeks.firstOrNull { it.week == active.week } ?: return null
        val started = runCatching { LocalDate.parse(active.startedOn) }.getOrNull() ?: today
        val daysSince = maxOf(0, started.daysUntil(today))
        val next = program.weeks.firstOrNull { it.week == active.week + 1 }
        return ProgramProgress(
            programId = program.id,
            programName = program.name,
            week = current.week,
            totalWeeks = program.weeks.size,
            weekTitle = current.title,
            dayInWeek = daysSince + 1,
            weekCompleted = daysSince >= DAYS_IN_PROGRAM_WEEK,
            nextWeek = next?.week,
            nextPlanId = next?.let { programWeekId(program.id, it.week) },
        )
    }
}
