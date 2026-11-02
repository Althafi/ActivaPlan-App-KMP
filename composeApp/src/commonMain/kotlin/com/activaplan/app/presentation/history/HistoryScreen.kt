package com.activaplan.app.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.components.ActivaBackButton
import com.activaplan.app.designsystem.components.ActivaButton
import com.activaplan.app.designsystem.components.ActivaCard
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.designsystem.components.TextBadge
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.i18n.Strings
import com.activaplan.app.domain.model.HistoryData
import com.activaplan.app.domain.model.HistoryItem
import com.activaplan.app.domain.model.HistoryMonth
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.usecase.ObserveHistoryUseCase
import com.activaplan.app.presentation.SkeletonCard
import com.activaplan.app.presentation.SkeletonGroup
import com.activaplan.app.presentation.rememberShowSkeleton
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/** Riwayat sesi, terbaru di atas dan dikelompokkan per bulan. Dibuka dari layar Selesai. */
@Composable
fun HistoryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val observe = koinInject<ObserveHistoryUseCase>()
    val viewModel = viewModel { HistoryViewModel(observe) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryContent(state, onBack, viewModel::reload, modifier)
}

@Composable
fun HistoryContent(state: HistoryUiState, onBack: () -> Unit, onReload: () -> Unit, modifier: Modifier = Modifier) {
    val strings = ActivaPlanTheme.strings
    Column(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg).statusBarsPadding()) {
        Row(
            Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActivaBackButton(onBack)
            Text(strings[StringKey.HISTORY_TITLE], style = ActivaPlanTheme.type.screenTitle, color = ActivaPlanTheme.colors.text)
        }
        if (rememberShowSkeleton(state == HistoryUiState.Loading, state is HistoryUiState.Ready)) {
            SkeletonGroup(Modifier.fillMaxSize().padding(horizontal = ActivaDimens.screenPadding)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(4) { SkeletonCard() } }
            }
        } else {
            when (state) {
                HistoryUiState.Loading -> Unit
                HistoryUiState.Error -> LoadError(onReload)
                is HistoryUiState.Ready ->
                    if (state.data.isEmpty) EmptyState(state.data.keepHistory) else HistoryList(state.data)
            }
        }
    }
}

@Composable
private fun HistoryList(data: HistoryData) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ActivaDimens.screenPadding, end = ActivaDimens.screenPadding, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            Text(
                strings.format(StringKey.HISTORY_SUMMARY, data.totalSessions, data.completedSessions),
                style = ActivaPlanTheme.type.body,
                color = c.textMuted,
            )
        }
        data.months.forEach { month ->
            item(key = "month-${month.year}-${month.month}") {
                Text(
                    monthHeader(strings, month),
                    style = ActivaPlanTheme.type.cardTitle,
                    color = c.text,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            items(month.items) { HistoryRow(it) }
        }
        item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(4.dp)) }
    }
}

@Composable
private fun HistoryRow(item: HistoryItem) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    ActivaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Text(
                    item.routineName ?: strings[StringKey.HISTORY_ROUTINE_DELETED],
                    style = ActivaPlanTheme.type.cardTitle,
                    color = if (item.routineName == null) c.textMuted else c.text,
                    modifier = Modifier.weight(1f),
                )
                TextBadge(strings.label(item.status))
            }
            Text(dateText(strings, item.date), style = ActivaPlanTheme.type.body, color = c.textMuted)
            Text(detailText(strings, item), style = ActivaPlanTheme.type.body, color = c.textMuted)
        }
    }
}

private fun monthHeader(strings: Strings, month: HistoryMonth): String =
    strings.format(StringKey.HISTORY_MONTH_HEADER, strings.monthName(month.month), month.year)

private fun dateText(strings: Strings, date: LocalDate): String =
    strings.format(StringKey.HISTORY_DATE, strings.dayShort(date.dayOfWeek.isoDayNumber), date.day, strings.monthName(date.month.ordinal + 1))

/** "3/4 set" dan, bila ada jarak GPS, "3/4 set · 1,25 km". */
private fun detailText(strings: Strings, item: HistoryItem): String {
    val sets = strings.format(StringKey.HISTORY_SETS, item.completedSets, item.totalSets)
    return if (item.distanceMeters > 0) "$sets · ${strings.distance(item.distanceMeters)}" else sets
}

@Composable
private fun EmptyState(keepHistory: Boolean) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(
        Modifier.fillMaxSize().padding(ActivaDimens.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActivaIcon(ActivaIconKind.CLOCK, c.textMuted, size = 32.dp)
        Spacer(Modifier.height(12.dp))
        Text(strings[StringKey.HISTORY_EMPTY_TITLE], style = ActivaPlanTheme.type.cardTitle, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            strings[if (keepHistory) StringKey.HISTORY_EMPTY_BODY else StringKey.HISTORY_OFF_BODY],
            style = ActivaPlanTheme.type.body,
            color = c.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadError(onReload: () -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(
        Modifier.fillMaxSize().padding(ActivaDimens.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActivaIcon(ActivaIconKind.WARNING, c.textMuted, size = 32.dp)
        Spacer(Modifier.height(12.dp))
        Text(strings[StringKey.ALERT_LOAD_FAILED], style = ActivaPlanTheme.type.body, color = c.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        ActivaButton(strings[StringKey.ACTION_RELOAD], onReload, style = ButtonStyle.SOFT)
    }
}

private val previewData = HistoryData(
    months = listOf(
        HistoryMonth(
            2026, 10,
            listOf(
                HistoryItem(LocalDate(2026, 10, 25), "Dada & Triceps", SessionStatus.COMPLETED, 12, 12, 0),
                HistoryItem(LocalDate(2026, 10, 23), "Jalan-Lari 5K", SessionStatus.PARTIAL, 3, 5, 1250),
                HistoryItem(LocalDate(2026, 10, 20), null, SessionStatus.ABANDONED, 1, 9, 0),
            ),
        ),
    ),
    keepHistory = true,
)

@Preview(widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun HistoryLightPreview() = ActivaPlanTheme(darkTheme = false) {
    HistoryContent(HistoryUiState.Ready(previewData), {}, {})
}

@Preview(widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun HistoryDarkPreview() = ActivaPlanTheme(darkTheme = true) {
    HistoryContent(HistoryUiState.Ready(previewData), {}, {})
}

@Preview(widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun HistoryEmptyPreview() = ActivaPlanTheme(darkTheme = false) {
    HistoryContent(HistoryUiState.Ready(HistoryData(emptyList(), keepHistory = false)), {}, {})
}
