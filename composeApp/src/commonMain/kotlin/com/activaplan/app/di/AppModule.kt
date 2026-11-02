package com.activaplan.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.activaplan.app.data.local.UserDataStore
import com.activaplan.app.data.local.createDataStore
import com.activaplan.app.data.repository.CardioRepository
import com.activaplan.app.data.repository.ComposeResourceExerciseDataSource
import com.activaplan.app.data.repository.ExerciseDataSource
import com.activaplan.app.data.repository.ExerciseRepository
import com.activaplan.app.data.repository.UserDataRepository
import com.activaplan.app.data.repository.WeeklyPlanRepository
import com.activaplan.app.domain.reminder.ReminderSynchronizer
import com.activaplan.app.domain.time.LocalClock
import com.activaplan.app.domain.time.SystemLocalClock
import com.activaplan.app.domain.usecase.AddExerciseToRoutineUseCase
import com.activaplan.app.domain.usecase.ApplyWeeklyPlanUseCase
import com.activaplan.app.domain.usecase.ClearAllDataUseCase
import com.activaplan.app.domain.usecase.ClearSessionHistoryUseCase
import com.activaplan.app.domain.usecase.CompleteOnboardingUseCase
import com.activaplan.app.domain.usecase.CreateRoutineFromTemplateUseCase
import com.activaplan.app.domain.usecase.DeleteRoutineUseCase
import com.activaplan.app.domain.usecase.DeleteScheduleEntryUseCase
import com.activaplan.app.domain.usecase.EstimateRoutineMinutesUseCase
import com.activaplan.app.domain.usecase.FilterCardioUseCase
import com.activaplan.app.domain.usecase.FilterExercisesUseCase
import com.activaplan.app.domain.usecase.FinishSessionUseCase
import com.activaplan.app.domain.usecase.GetCardioDetailUseCase
import com.activaplan.app.domain.usecase.GetExerciseDetailUseCase
import com.activaplan.app.domain.usecase.GetTemplateDetailUseCase
import com.activaplan.app.domain.usecase.GetProgramDetailUseCase
import com.activaplan.app.domain.usecase.GetWeeklyPlanDetailUseCase
import com.activaplan.app.domain.usecase.LoadRoutineDraftUseCase
import com.activaplan.app.domain.usecase.LoadSessionPlanUseCase
import com.activaplan.app.domain.usecase.ObserveHistoryUseCase
import com.activaplan.app.domain.usecase.ObserveHomeUseCase
import com.activaplan.app.domain.usecase.ObserveRoutinesUseCase
import com.activaplan.app.domain.usecase.ObserveScheduleUseCase
import com.activaplan.app.domain.usecase.ObserveSettingsUseCase
import com.activaplan.app.domain.usecase.PruneScheduleUseCase
import com.activaplan.app.domain.usecase.SaveRoutineUseCase
import com.activaplan.app.domain.usecase.SaveScheduleUseCase
import com.activaplan.app.domain.usecase.SetAgeUseCase
import com.activaplan.app.domain.usecase.SetDefaultTimeUseCase
import com.activaplan.app.domain.usecase.SetGpsTrackingUseCase
import com.activaplan.app.domain.usecase.SetKeepHistoryUseCase
import com.activaplan.app.domain.usecase.SetLanguageUseCase
import com.activaplan.app.domain.usecase.SetRemindersEnabledUseCase
import com.activaplan.app.domain.usecase.SetScheduleReminderUseCase
import com.activaplan.app.domain.usecase.SetThemeUseCase
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

fun interface DataStorePathProvider {
    fun path(): String
}

/** Modul spesifik platform; menyediakan path file DataStore. */
expect fun platformModule(): Module

val commonModule = module {
    single { Json { ignoreUnknownKeys = true; encodeDefaults = true } }
    single<DataStore<Preferences>> { createDataStore(get<DataStorePathProvider>()) }
    single<ExerciseDataSource> { ComposeResourceExerciseDataSource() }
    single { ExerciseRepository(get(), get()) }
    single { CardioRepository(get(), get()) }
    single { WeeklyPlanRepository(get(), get()) }
    single { UserDataStore(get(), get()) }
    single { UserDataRepository(get(), get(), get()) }
    single<LocalClock> { SystemLocalClock }
    factory { ObserveHomeUseCase(get(), get(), get(), plans = get()) }
    factory { FilterExercisesUseCase() }
    factory { FilterCardioUseCase() }
    factory { GetCardioDetailUseCase(get()) }
    factory { GetExerciseDetailUseCase(get()) }
    factory { AddExerciseToRoutineUseCase(get()) }
    factory { EstimateRoutineMinutesUseCase() }
    factory { ObserveRoutinesUseCase(get(), get(), get(), get(), get()) }
    factory { GetWeeklyPlanDetailUseCase(get(), get(), get()) }
    factory { GetProgramDetailUseCase(get(), get(), get(), userData = get()) }
    factory { ApplyWeeklyPlanUseCase(get(), get(), get(), get(), clock = get()) }
    factory { LoadRoutineDraftUseCase(get(), get(), cardio = get()) }
    factory { ObserveHistoryUseCase(get()) }
    factory { SaveRoutineUseCase(get()) }
    factory { DeleteRoutineUseCase(get()) }
    factory { CreateRoutineFromTemplateUseCase(get(), get()) }
    factory { GetTemplateDetailUseCase(get(), get()) }
    factory { LoadSessionPlanUseCase(get(), get(), get()) }
    factory { FinishSessionUseCase(get(), get()) }
    factory { ObserveScheduleUseCase(get(), get(), get(), cardio = get()) }
    factory { SaveScheduleUseCase(get()) }
    factory { DeleteScheduleEntryUseCase(get()) }
    factory { SetScheduleReminderUseCase(get()) }
    factory { PruneScheduleUseCase(get()) }
    factory { ObserveSettingsUseCase(get()) }
    factory { SetThemeUseCase(get()) }
    factory { SetLanguageUseCase(get()) }
    factory { SetRemindersEnabledUseCase(get()) }
    factory { SetDefaultTimeUseCase(get()) }
    factory { SetKeepHistoryUseCase(get()) }
    factory { SetGpsTrackingUseCase(get()) }
    factory { SetAgeUseCase(get()) }
    factory { CompleteOnboardingUseCase(get()) }
    factory { ClearSessionHistoryUseCase(get()) }
    factory { ClearAllDataUseCase(get()) }
    single { ReminderSynchronizer(get(), get(), get()) }
}

fun appModules(): List<Module> = listOf(platformModule(), commonModule)

/** Aman dipanggil berulang; hanya memulai Koin sekali. */
fun initKoin(config: KoinAppDeclaration? = null) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin {
        config?.invoke(this)
        modules(appModules())
    }
}
