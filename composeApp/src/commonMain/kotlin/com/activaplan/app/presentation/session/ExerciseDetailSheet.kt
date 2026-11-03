package com.activaplan.app.presentation.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.components.ActivaBottomSheet
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.BoardDiagram
import com.activaplan.app.designsystem.components.ExerciseImageView
import com.activaplan.app.designsystem.components.LevelBadge
import com.activaplan.app.designsystem.components.TextBadge
import com.activaplan.app.designsystem.components.ZoneChip
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.model.Mode
import com.activaplan.app.domain.model.SessionExercise
import com.activaplan.app.presentation.detail.Mistakes
import com.activaplan.app.presentation.detail.StatTile
import com.activaplan.app.presentation.detail.Steps
import com.activaplan.app.presentation.detail.Tips

/**
 * Detail gerakan saat sesi berjalan: gambar atau diagram, set/rep/istirahat dari rutinitas, langkah, tips, dan kesalahan umum.
 * Timer dijeda selama sheet terbuka dan dilanjutkan saat ditutup, kecuali sesi memang sedang dijeda ([paused]).
 */
@Composable
internal fun ExerciseDetailSheet(
    item: SessionExercise,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Dikunci pada komposisi sheet: jeda hanya dibatalkan bila sheet sendiri yang menjedanya.
    DisposableEffect(Unit) {
        val pausedBySheet = !paused
        if (pausedBySheet) onTogglePause()
        onDispose { if (pausedBySheet) onTogglePause() }
    }
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val exercise = item.exercise
    ActivaBottomSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(exercise.name, style = ActivaPlanTheme.type.largeTitle.copy(fontSize = 24.sp), color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(exercise.difficulty)
                val zone = exercise.boardZone
                if (zone != null) ZoneChip(zone) else TextBadge(strings.label(exercise.category))
            }
            ExerciseImageView(exercise.images.firstOrNull(), Modifier.clip(RoundedCornerShape(16.dp))) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceVar),
                    contentAlignment = Alignment.Center,
                ) {
                    if (exercise.mode == Mode.NO_EQUIPMENT) {
                        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                            ActivaIcon(ActivaIconKind.DUMBBELL, c.textMuted, size = 48.dp)
                        }
                    } else {
                        BoardDiagram(exercise.boardZone, Modifier.fillMaxWidth(0.886f))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(item.sets.toString(), strings[StringKey.DETAIL_UNIT_SET], Modifier.weight(1f))
                StatTile(
                    (item.reps ?: item.durationSeconds ?: 0).toString(),
                    strings[if (item.isTimed) StringKey.DETAIL_UNIT_SECONDS else StringKey.DETAIL_UNIT_REP],
                    Modifier.weight(1f),
                )
                StatTile(item.restSeconds.toString(), strings[StringKey.DETAIL_UNIT_REST], Modifier.weight(1f))
            }
            if (exercise.steps.isNotEmpty()) Steps(exercise.steps)
            if (exercise.tips.isNotEmpty()) Tips(exercise.tips)
            if (exercise.commonMistakes.isNotEmpty()) Mistakes(exercise.commonMistakes)
        }
    }
}
