package com.activaplan.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.activaplan.app.designsystem.ActivaPlanTheme
import com.activaplan.app.domain.model.CardioSegment
import com.activaplan.app.domain.model.SegmentKind

/** Warna segmen menurut perannya; kerja = primary, pemulihan = success, pemanasan/pendinginan = redup. */
@Composable
fun segmentColor(kind: SegmentKind): Color {
    val c = ActivaPlanTheme.colors
    return when (kind) {
        SegmentKind.WORK, SegmentKind.STEADY -> c.primary
        SegmentKind.RECOVERY -> c.success
        SegmentKind.WARMUP, SegmentKind.COOLDOWN -> c.textMuted
    }
}

/**
 * Grafik batang struktur sesi: lebar batang sebanding dengan durasi (dengan lebar minimum agar segmen pendek
 * tetap terlihat), tinggi sebanding dengan usaha (RPE). Warna tidak sendirian: legenda memberi nama tiap peran
 * dan [description] dibaca pembaca layar sebagai ringkasan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardioSegmentChart(
    segments: List<CardioSegment>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    val c = ActivaPlanTheme.colors
    val strings = ActivaPlanTheme.strings
    val total = segments.sumOf { it.durationSeconds }.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(height).semantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            segments.forEach { s ->
                // Bobot memakai durasi sebenarnya dengan dasar 3% agar batang 30 detik di sesi 70 menit tetap tampak.
                val weight = (s.durationSeconds.toFloat() / total).coerceAtLeast(MIN_WEIGHT)
                Box(
                    Modifier.weight(weight)
                        .height(height * (s.rpe.coerceIn(1, 10) / 10f))
                        .background(segmentColor(s.kind), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                )
            }
        }
        FlowRow(
            Modifier.clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            segments.map { it.kind }.distinct().forEach { kind ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(segmentColor(kind), RoundedCornerShape(3.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.label(kind), style = ActivaPlanTheme.type.labelSmall, color = c.textMuted)
                }
            }
        }
    }
}

private const val MIN_WEIGHT = 0.03f
