package com.activaplan.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.activaplan.app.presentation.detail.CardioDetailScreen
import com.activaplan.app.presentation.detail.ExerciseDetailScreen
import com.activaplan.app.presentation.explore.ExploreScreen
import com.activaplan.app.presentation.history.HistoryScreen
import com.activaplan.app.presentation.home.HomeScreen
import com.activaplan.app.presentation.onboarding.IntroReplayScreen
import com.activaplan.app.presentation.routines.PlanPreviewScreen
import com.activaplan.app.presentation.routines.ProgramPreviewScreen
import com.activaplan.app.presentation.routines.RoutineBuilderScreen
import com.activaplan.app.presentation.routines.RoutinesScreen
import com.activaplan.app.presentation.routines.TemplatePreviewScreen
import com.activaplan.app.presentation.schedule.ScheduleScreen
import com.activaplan.app.presentation.session.SessionScreen
import com.activaplan.app.presentation.settings.SettingsScreen

@Composable
fun ActivaNavHost(navController: NavHostController, modifier: Modifier = Modifier, exploreScrollResetToken: Int = 0) {
    NavHost(navController, startDestination = TopLevelDestination.start.route, modifier = modifier) {
        composable(TopLevelDestination.HOME.route) { entry ->
            val dataCleared by entry.savedStateHandle.getStateFlow(DATA_CLEARED_KEY, false).collectAsStateWithLifecycle()
            HomeScreen(
                onOpenSchedule = { navController.navigateToTab(TopLevelDestination.SCHEDULE) },
                onStartWorkout = { navController.navigate(SessionRoute(it.routineId)) { launchSingleTop = true } },
                onOpenTemplate = { navController.navigate(TemplatePreviewRoute(it)) },
                onOpenPlan = { navController.navigate(PlanPreviewRoute(it)) },
                onOpenProgram = { navController.navigate(ProgramPreviewRoute(it)) },
                dataClearedNotice = dataCleared,
                onDataClearedNoticeShown = { entry.savedStateHandle[DATA_CLEARED_KEY] = false },
            )
        }
        composable(TopLevelDestination.EXPLORE.route) {
            ExploreScreen(
                onOpenExercise = { navController.navigate(ExerciseDetailRoute(it)) },
                scrollResetToken = exploreScrollResetToken,
                onOpenCardio = { navController.navigate(CardioDetailRoute(it)) },
            )
        }
        composable<CardioDetailRoute> { entry ->
            CardioDetailScreen(
                id = entry.toRoute<CardioDetailRoute>().id,
                onBack = { navController.popBackStack() },
                // Turun/Naik level menggantikan layar ini agar Kembali tetap menuju Jelajah.
                onOpenCardio = { id ->
                    navController.navigate(CardioDetailRoute(id)) { popUpTo<CardioDetailRoute> { inclusive = true } }
                },
            )
        }
        composable<ExerciseDetailRoute> { entry ->
            ExerciseDetailScreen(
                id = entry.toRoute<ExerciseDetailRoute>().id,
                onBack = { navController.popBackStack() },
                // Turun/Naik level menggantikan layar ini agar Kembali tetap menuju Jelajah.
                onOpenExercise = { id ->
                    navController.navigate(ExerciseDetailRoute(id)) { popUpTo<ExerciseDetailRoute> { inclusive = true } }
                },
            )
        }
        composable(TopLevelDestination.ROUTINES.route) { entry ->
            val saved by entry.savedStateHandle.getStateFlow(ROUTINE_SAVED_KEY, false).collectAsStateWithLifecycle()
            RoutinesScreen(
                onCreate = { navController.navigate(RoutineBuilderRoute()) },
                onEdit = { navController.navigate(RoutineBuilderRoute(it)) },
                onStart = { navController.navigate(SessionRoute(it)) { launchSingleTop = true } },
                onUseTemplate = { navController.navigate(RoutineBuilderRoute(it)) },
                onOpenTemplate = { navController.navigate(TemplatePreviewRoute(it)) },
                onOpenPlan = { navController.navigate(PlanPreviewRoute(it)) },
                onOpenProgram = { navController.navigate(ProgramPreviewRoute(it)) },
                savedNotice = saved,
                onSavedNoticeShown = { entry.savedStateHandle[ROUTINE_SAVED_KEY] = false },
            )
        }
        composable<RoutineBuilderRoute> { entry ->
            val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_EXERCISE_KEY, null).collectAsStateWithLifecycle()
            RoutineBuilderScreen(
                routineId = entry.toRoute<RoutineBuilderRoute>().routineId,
                pickedExerciseId = picked,
                onPickedConsumed = { entry.savedStateHandle[PICKED_EXERCISE_KEY] = null },
                onBack = { navController.popBackStack() },
                onAddExercise = { navController.navigate(ExercisePickerRoute) },
                onSaved = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(ROUTINE_SAVED_KEY, true)
                    navController.popBackStack()
                },
            )
        }
        composable<ExercisePickerRoute> {
            ExploreScreen(
                onOpenExercise = { id ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISE_KEY, id)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<TemplatePreviewRoute> { entry ->
            TemplatePreviewScreen(
                id = entry.toRoute<TemplatePreviewRoute>().id,
                onBack = { navController.popBackStack() },
                // Salinan sudah tersimpan; Builder menggantikan pratinjau agar Kembali menuju layar asal.
                onUsed = { routineId ->
                    navController.navigate(RoutineBuilderRoute(routineId)) { popUpTo<TemplatePreviewRoute> { inclusive = true } }
                },
            )
        }
        composable<ProgramPreviewRoute> { entry ->
            ProgramPreviewScreen(
                id = entry.toRoute<ProgramPreviewRoute>().id,
                onBack = { navController.popBackStack() },
                // Rencana pekan dibuka di atas program agar Kembali menuju daftar pekan.
                onOpenWeek = { navController.navigate(PlanPreviewRoute(it)) },
            )
        }
        composable<PlanPreviewRoute> { entry ->
            PlanPreviewScreen(
                id = entry.toRoute<PlanPreviewRoute>().id,
                onBack = { navController.popBackStack() },
                // Rencana sudah menjadi rutinitas dan jadwal; tab Jadwal menampilkan hasilnya.
                onApplied = {
                    navController.popBackStack()
                    navController.navigateToTab(TopLevelDestination.SCHEDULE)
                },
            )
        }
        composable<SessionRoute> { entry ->
            SessionScreen(
                routineId = entry.toRoute<SessionRoute>().routineId,
                onExit = { navController.popBackStack() },
                // Sesi ditutup lebih dulu agar Kembali dari Riwayat menuju layar sebelum sesi, bukan ke sesi yang sudah selesai.
                onOpenHistory = {
                    navController.popBackStack()
                    navController.navigate(HistoryRoute) { launchSingleTop = true }
                },
            )
        }
        composable<HistoryRoute> {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
        composable<IntroReplayRoute> {
            IntroReplayScreen(onClose = { navController.popBackStack() })
        }
        composable(TopLevelDestination.SCHEDULE.route) {
            ScheduleScreen(onCreateRoutine = { navController.navigate(RoutineBuilderRoute()) })
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(
                onReplayIntro = { navController.navigate(IntroReplayRoute) { launchSingleTop = true } },
                onOpenHistory = { navController.navigate(HistoryRoute) { launchSingleTop = true } },
                onDataCleared = {
                    // Beranda selalu ada di dasar back stack; bendera dibaca saat entrinya tampil kembali.
                    navController.getBackStackEntry(TopLevelDestination.HOME.route).savedStateHandle[DATA_CLEARED_KEY] = true
                    navController.navigateToTab(TopLevelDestination.HOME)
                },
            )
        }
    }
}

/** Pindah tab: satu salinan per tab, state tab tersimpan, dan Back dari tab mana pun kembali ke Beranda. */
fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
