package com.activaplan.app.presentation.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.activaplan.app.designsystem.ActivaDimens
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.designsystem.components.ActivaButton
import com.activaplan.app.designsystem.components.ActivaIcon
import com.activaplan.app.designsystem.components.ActivaIconKind
import com.activaplan.app.designsystem.components.ButtonStyle
import com.activaplan.app.domain.i18n.StringKey
import com.activaplan.app.domain.model.SessionStatus
import com.activaplan.app.domain.model.SessionSummary

/**
 * Layar "Latihan Selesai!". [streak] 0 berarti belum diketahui atau tidak ada, dan chip disembunyikan.
 * Bila [SessionSummary.distanceMeters] terisi (GPS), baris kedua menampilkan jarak dan [averagePaceSecPerKm].
 * Bila ada set yang tidak selesai, kalimat pembuka menyebut jumlah set, bukan "semua latihan".
 */
@Composable
internal fun CompleteContent(
    summary: SessionSummary,
    streak: Int,
    actions: SessionActions,
    averagePaceSecPerKm: Int? = null,
) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    Box(Modifier.fillMaxSize()) {
        Confetti()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = ActivaDimens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(128.dp).background(c.successSoft, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(88.dp).background(c.success, CircleShape), contentAlignment = Alignment.Center) {
                        ActivaIcon(ActivaIconKind.CHECK, c.onSuccess, size = 44.dp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    strings[StringKey.SESSION_COMPLETE_TITLE],
                    style = ActivaPlanTheme.type.largeTitle.copy(fontSize = 30.sp),
                    color = c.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (summary.completedSets >= summary.totalSets) {
                        strings[StringKey.SESSION_COMPLETE_BODY]
                    } else {
                        strings.format(StringKey.SESSION_COMPLETE_PARTIAL_BODY, summary.completedSets, summary.totalSets)
                    },
                    style = ActivaPlanTheme.type.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = c.textMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(summary.completedExercises, strings[StringKey.SESSION_STAT_EXERCISES], Modifier.weight(1f))
                    StatTile(summary.completedSets, strings[StringKey.SESSION_STAT_SETS], Modifier.weight(1f))
                    StatTile(summary.minutes, strings[StringKey.SESSION_STAT_MINUTES], Modifier.weight(1f))
                }
                if (summary.distanceMeters > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextStatTile(strings.distance(summary.distanceMeters), strings[StringKey.SESSION_STAT_DISTANCE], Modifier.weight(1f))
                        TextStatTile(strings.pace(averagePaceSecPerKm), strings[StringKey.SESSION_STAT_AVG_PACE], Modifier.weight(1f))
                    }
                }
                if (summary.status == SessionStatus.COMPLETED && streak > 0) {
                    Spacer(Modifier.height(16.dp))
                    StreakChip(streak)
                }
            }
            Column(
                Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActivaButton(strings[StringKey.SESSION_DONE], actions.onDone, Modifier.fillMaxWidth(), icon = ActivaIconKind.CHECK)
                ActivaButton(strings[StringKey.SESSION_HISTORY], actions.onOpenHistory, Modifier.fillMaxWidth(), ButtonStyle.SECONDARY)
            }
        }
    }
}

@Composable
private fun StatTile(value: Int, label: String, modifier: Modifier = Modifier) = TextStatTile(value.toString(), label, modifier)

@Composable
private fun TextStatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val c = ActivaPlanTheme.colors
    Surface(
        color = c.surface, contentColor = c.text, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, c.border), modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = ActivaPlanTheme.type.largeTitle.copy(fontSize = 24.sp, lineHeight = 30.sp), color = c.text)
            Text(label, style = ActivaPlanTheme.type.labelSmall.copy(fontSize = 12.sp), color = c.textMuted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StreakChip(streak: Int) {
    val c = ActivaPlanTheme.colors
    Row(
        Modifier.background(c.primarySoft, CircleShape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActivaIcon(ActivaIconKind.FLAME, c.primary, size = 18.dp)
        Text(
            ActivaPlanTheme.strings.format(StringKey.SESSION_STREAK, streak),
            style = ActivaPlanTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
            color = c.text,
        )
    }
}

/** Titik warna dekoratif di belakang ikon sukses; posisi tetap (pecahan lebar dan tinggi) dan warnanya warna zona. */
@Composable
private fun Confetti() {
    val c = ActivaPlanTheme.colors
    val dots = listOf(
        Triple(0.11f, 0.15f, c.zoneBlue), Triple(0.52f, 0.11f, c.primary), Triple(0.86f, 0.19f, c.zoneYellow),
        Triple(0.19f, 0.28f, c.zoneGreen), Triple(0.81f, 0.30f, c.zoneRed), Triple(0.07f, 0.40f, c.zoneYellow),
        Triple(0.93f, 0.41f, c.zoneBlue),
    )
    Canvas(Modifier.fillMaxSize()) {
        dots.forEach { (x, y, color: Color) ->
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width * x, size.height * y))
        }
    }
}
