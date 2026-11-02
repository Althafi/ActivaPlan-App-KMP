package com.activaplan.app.presentation.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.activaplan.app.designsystem.components.LevelBadge
import com.activaplan.app.designsystem.components.TextBadge
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.model.ProgramDetail
import com.activaplan.app.domain.model.ProgramWeekCard
import com.activaplan.app.domain.usecase.GetProgramDetailUseCase
import org.koin.compose.koinInject

/**
 * Layar program bertahap: ringkasan, cara memakai, dan kartu tiap pekan. Mengetuk pekan membuka pratinjau rencana pekan itu
 * ([onOpenWeek] menerima ID rencana pekan), tempat jadwal diterapkan.
 */
@Composable
fun ProgramPreviewScreen(
    id: String,
    onBack: () -> Unit,
    onOpenWeek: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val getDetail = koinInject<GetProgramDetailUseCase>()
    val strings = ActivaPlanTheme.strings
    val viewModel = viewModel(key = "program-$id-${strings.language.code}") {
        ProgramPreviewViewModel(id, strings.language, getDetail)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgramPreviewContent(state, onBack, viewModel::reload, onOpenWeek, modifier)
}

@Composable
fun ProgramPreviewContent(
    state: ProgramPreviewUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onOpenWeek: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = ActivaPlanTheme.strings
    Box(modifier.fillMaxSize().background(ActivaPlanTheme.colors.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp, bottom = 12.dp)) {
                ActivaBackButton(onBack)
            }
            when (state) {
                ProgramPreviewUiState.Loading -> Unit
                ProgramPreviewUiState.Error -> Message(strings[StringKey.ALERT_LOAD_FAILED], strings[StringKey.ACTION_RELOAD], onReload)
                ProgramPreviewUiState.NotFound -> Message(strings[StringKey.PROGRAM_NOT_FOUND])
                is ProgramPreviewUiState.Ready -> ReadyContent(state.detail, onOpenWeek)
            }
        }
    }
}

@Composable
private fun ReadyContent(detail: ProgramDetail, onOpenWeek: (String) -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = ActivaDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(detail.name, style = ActivaPlanTheme.type.largeTitle, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(detail.difficulty)
            if (detail.modes.isNotEmpty()) {
                TextBadge(strings.format(StringKey.PLAN_NEEDS, detail.modes.joinToString(", ") { strings.label(it) }))
            }
        }
        Text(detail.summary, style = ActivaPlanTheme.type.body, color = c.textMuted)
        if (detail.tips.isNotEmpty()) {
            Text(strings[StringKey.PROGRAM_TIPS_TITLE], style = ActivaPlanTheme.type.cardTitle, color = c.text)
            detail.tips.forEach { Text("• $it", style = ActivaPlanTheme.type.body, color = c.textMuted) }
        }
        Spacer(Modifier.height(4.dp))
        Text(strings[StringKey.PROGRAM_WEEKS_TITLE], style = ActivaPlanTheme.type.cardTitle, color = c.text)
        detail.weeks.forEach { WeekCard(it, onOpenWeek) }
        Spacer(Modifier.height(16.dp))
    }
}

/** Kartu pekan: judul, fokus, tujuh titik hari berwarna, statistik, dan tombol membuka pratinjau pekan. */
@Composable
private fun WeekCard(week: ProgramWeekCard, onOpenWeek: (String) -> Unit) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val plan = week.plan
    val weekDescription = (1..7).zip(plan.days).joinToString(", ") { (day, type) -> "${strings.dayName(day)}: ${strings.label(type)}" }
    ActivaCard(Modifier.fillMaxWidth(), onClick = { onOpenWeek(plan.id) }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.name, style = ActivaPlanTheme.type.cardTitle, color = c.text)
            Text(week.focus, style = ActivaPlanTheme.type.body, color = c.textMuted)
            Row(Modifier.semantics { contentDescription = weekDescription }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.days.forEachIndexed { index, type ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(12.dp).background(planDayColor(type), CircleShape))
                        Text(strings.dayShort(index + 1), style = ActivaPlanTheme.type.labelSmall.copy(fontSize = 10.sp), color = c.textMuted)
                    }
                }
            }
            Text(
                strings.format(StringKey.PLAN_CARD_META, plan.trainingDays, plan.strengthDays, plan.cardioDays),
                style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = c.textMuted,
            )
            Text(
                strings.format(StringKey.PLAN_CARD_MINUTES, plan.weeklyMinutes),
                style = ActivaPlanTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = c.textMuted,
            )
            ActivaButton(strings[StringKey.PROGRAM_WEEK_OPEN], { onOpenWeek(plan.id) }, Modifier.fillMaxWidth(), style = ButtonStyle.SOFT, compact = true)
        }
    }
}

@Composable
private fun Message(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val c = ActivaPlanTheme.colors
    Column(
        Modifier.fillMaxSize().padding(ActivaDimens.screenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActivaIcon(ActivaIconKind.WARNING, c.textMuted, size = 32.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, style = ActivaPlanTheme.type.body, color = c.textMuted, textAlign = TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            ActivaButton(actionLabel, onAction, style = ButtonStyle.SOFT)
        }
    }
}
