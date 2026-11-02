package com.activaplan.app.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.components.ActivaBottomNav
import com.activaplan.app.designsystem.components.ActivaBottomNavItem
import com.activaplan.app.presentation.navigation.ActivaNavHost
import com.activaplan.app.presentation.navigation.ExerciseDetailRoute
import com.activaplan.app.presentation.navigation.ExercisePickerRoute
import com.activaplan.app.presentation.navigation.HistoryRoute
import com.activaplan.app.presentation.navigation.IntroReplayRoute
import com.activaplan.app.presentation.navigation.RoutineBuilderRoute
import com.activaplan.app.presentation.navigation.SessionRoute
import com.activaplan.app.presentation.navigation.TemplatePreviewRoute
import com.activaplan.app.presentation.navigation.TopLevelDestination
import com.activaplan.app.presentation.navigation.navigateToTab

/** Kerangka aplikasi: NavHost lima tab dan bottom nav di bawahnya. */
@Composable
fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = TopLevelDestination.fromRoute(backStackEntry?.destination?.route)
    val strings = ActivaPlanTheme.strings
    // Naik tiap pengguna meninggalkan tab Jelajah ke tab lain; Jelajah memakainya untuk mulai dari daftar paling atas.
    // Membuka Detail tidak menaikkannya (`current` null di sana), jadi kembali dari Detail tetap di posisi semula.
    var exploreScrollResetToken by rememberSaveable { mutableIntStateOf(0) }
    var previousTab by remember { mutableStateOf<TopLevelDestination?>(null) }
    LaunchedEffect(current) {
        if (current != null) {
            if (previousTab == TopLevelDestination.EXPLORE && current != TopLevelDestination.EXPLORE) exploreScrollResetToken++
            previousTab = current
        }
    }
    // Detail, Builder, mode pilih, pratinjau template, sesi, riwayat, dan pengantar ulang tampil tanpa bottom nav, seperti di Figma.
    val destination = backStackEntry?.destination
    val showBottomNav = listOf(
        destination?.hasRoute<ExerciseDetailRoute>(),
        destination?.hasRoute<RoutineBuilderRoute>(),
        destination?.hasRoute<ExercisePickerRoute>(),
        destination?.hasRoute<TemplatePreviewRoute>(),
        destination?.hasRoute<SessionRoute>(),
        destination?.hasRoute<IntroReplayRoute>(),
        destination?.hasRoute<HistoryRoute>(),
    ).none { it == true }
    Scaffold(
        containerColor = ActivaPlanTheme.colors.bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomNav) {
                ActivaBottomNav(
                    TopLevelDestination.entries.map { tab ->
                        ActivaBottomNavItem(strings[tab.label], tab.icon, tab == current) { navController.navigateToTab(tab) }
                    },
                )
            }
        },
    ) { padding ->
        ActivaNavHost(navController, Modifier.padding(padding), exploreScrollResetToken)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MainScaffoldLightPreview() = ActivaPlanTheme(darkTheme = false) { MainScaffold() }

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun MainScaffoldDarkPreview() = ActivaPlanTheme(darkTheme = true) { MainScaffold() }
