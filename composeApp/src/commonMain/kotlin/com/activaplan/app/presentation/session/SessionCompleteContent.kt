package com.activaplan.app.presentation.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
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
import com.activaplan.app.presentation.rememberReduceMotion
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val motion = rememberCompleteMotion()
    val particles = rememberConfettiParticles()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = ActivaDimens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SuccessBadge(motion, particles)
                Spacer(Modifier.height(24.dp))
                Text(
                    strings[StringKey.SESSION_COMPLETE_TITLE],
                    style = ActivaPlanTheme.type.largeTitle.copy(fontSize = 30.sp),
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.reveal(motion, 0.15f),
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
                    modifier = Modifier.reveal(motion, 0.3f),
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth().reveal(motion, 0.45f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(summary.completedExercises, strings[StringKey.SESSION_STAT_EXERCISES], Modifier.weight(1f))
                    StatTile(summary.completedSets, strings[StringKey.SESSION_STAT_SETS], Modifier.weight(1f))
                    StatTile(summary.minutes, strings[StringKey.SESSION_STAT_MINUTES], Modifier.weight(1f))
                }
                if (summary.distanceMeters > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().reveal(motion, 0.5f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextStatTile(strings.distance(summary.distanceMeters), strings[StringKey.SESSION_STAT_DISTANCE], Modifier.weight(1f))
                        TextStatTile(strings.pace(averagePaceSecPerKm), strings[StringKey.SESSION_STAT_AVG_PACE], Modifier.weight(1f))
                    }
                }
                if (summary.status == SessionStatus.COMPLETED && streak > 0) {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.reveal(motion, 0.6f)) { StreakChip(streak) }
                }
            }
            Column(
                Modifier.padding(horizontal = ActivaDimens.screenPadding).padding(top = 8.dp, bottom = 16.dp)
                    .reveal(motion, 0.65f),
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

// region Animasi

/**
 * Nilai animasi layar Selesai, dibaca di fase gambar/graphicsLayer supaya tidak memicu komposisi ulang tiap frame.
 * Semua bernilai 1 (kondisi akhir) bila gerak dikurangi atau animasi sudah pernah diputar (mis. setelah rotasi layar).
 */
private class CompleteMotion(
    val soft: Animatable<Float, AnimationVector1D>,
    val badge: Animatable<Float, AnimationVector1D>,
    val check: Animatable<Float, AnimationVector1D>,
    val pulse: Animatable<Float, AnimationVector1D>,
    val burst: Animatable<Float, AnimationVector1D>,
    val reveal: Animatable<Float, AnimationVector1D>,
)

@Composable
private fun rememberCompleteMotion(): CompleteMotion {
    val reduceMotion = rememberReduceMotion()
    var played by rememberSaveable { mutableStateOf(false) }
    // Pratinjau statis tidak menjalankan efek, jadi tampilkan kondisi akhir.
    val instant = reduceMotion || played || LocalInspectionMode.current
    val motion = remember {
        val start = if (instant) 1f else 0f
        CompleteMotion(
            soft = Animatable(start), badge = Animatable(start), check = Animatable(start),
            pulse = Animatable(start), burst = Animatable(start), reveal = Animatable(start),
        )
    }
    LaunchedEffect(Unit) {
        if (instant) return@LaunchedEffect
        played = true
        launch { motion.reveal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
        launch { motion.soft.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
        launch {
            delay(120)
            motion.badge.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
        }
        launch {
            delay(380)
            motion.check.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
        launch {
            delay(450)
            motion.pulse.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
        }
        launch {
            delay(350)
            motion.burst.animateTo(1f, tween(2200, easing = LinearEasing))
        }
    }
    return motion
}

/** Muncul dari bawah sambil memudar masuk; [start] (0..1) = kapan mulai dalam rentang [CompleteMotion.reveal]. */
private fun Modifier.reveal(motion: CompleteMotion, start: Float): Modifier = graphicsLayer {
    val p = ((motion.reveal.value - start) / REVEAL_SPAN).coerceIn(0f, 1f)
    alpha = p
    translationY = (1f - p) * 24.dp.toPx()
}

private const val REVEAL_SPAN = 0.35f

/** Lingkaran sukses: membesar dengan pantulan, tanda centang tergambar, cincin memancar, lalu konfeti menyebar. */
@Composable
private fun SuccessBadge(motion: CompleteMotion, particles: List<ConfettiParticle>) {
    val c = ActivaPlanTheme.colors
    Box(
        Modifier.size(128.dp).drawBehind {
            drawPulse(motion.pulse.value, c.success)
            drawConfetti(motion.burst.value, particles)
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(128.dp)
                .graphicsLayer { scaleX = motion.soft.value; scaleY = motion.soft.value }
                .background(c.successSoft, CircleShape),
        )
        Box(
            Modifier.size(88.dp)
                .graphicsLayer { scaleX = motion.badge.value; scaleY = motion.badge.value }
                .background(c.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedCheck({ motion.check.value }, c.onSuccess, Modifier.size(44.dp))
        }
    }
}

/** Centang yang digambar bertahap: garis pendek turun dulu, lalu garis panjang naik. */
@Composable
private fun AnimatedCheck(progress: () -> Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val a = Offset(w * 0.2f, w * 0.52f)
        val b = Offset(w * 0.42f, w * 0.72f)
        val d = Offset(w * 0.8f, w * 0.3f)
        val stroke = 4.dp.toPx()
        val p = progress().coerceIn(0f, 1f)
        val first = (p / CHECK_SPLIT).coerceIn(0f, 1f)
        if (first > 0f) drawLine(color, a, a + (b - a) * first, stroke, StrokeCap.Round)
        if (p > CHECK_SPLIT) {
            val second = ((p - CHECK_SPLIT) / (1f - CHECK_SPLIT)).coerceIn(0f, 1f)
            drawLine(color, b, b + (d - b) * second, stroke, StrokeCap.Round)
        }
    }
}

private const val CHECK_SPLIT = 0.35f

private fun DrawScope.drawPulse(progress: Float, color: Color) {
    if (progress <= 0f || progress >= 1f) return
    val radius = 44.dp.toPx() + (60.dp.toPx() * progress)
    drawCircle(color.copy(alpha = (1f - progress) * 0.45f), radius, style = Stroke(3.dp.toPx()))
}

/** Satu keping konfeti: arah [angle] (radian), jarak sebaran [speed] (dp), ukuran [size] (dp), dan putaran [spin] (derajat). */
private class ConfettiParticle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val spin: Float,
    val color: Color,
    val square: Boolean,
)

/** Keping konfeti tetap (benih acak tetap) berwarna zona, supaya tampilannya sama setiap kali. */
@Composable
private fun rememberConfettiParticles(): List<ConfettiParticle> {
    val c = ActivaPlanTheme.colors
    return remember(c) {
        val palette = listOf(c.zoneBlue, c.primary, c.zoneYellow, c.zoneGreen, c.zoneRed)
        val random = Random(CONFETTI_SEED)
        List(CONFETTI_COUNT) { i ->
            ConfettiParticle(
                angle = (i * 2f * PI_F / CONFETTI_COUNT) + random.nextFloat() * 0.35f,
                speed = 90f + random.nextFloat() * 130f,
                size = 3f + random.nextFloat() * 3f,
                spin = random.nextFloat() * 360f,
                color = palette[i % palette.size],
                square = i % 2 == 0,
            )
        }
    }
}

private const val CONFETTI_COUNT = 28
private const val CONFETTI_SEED = 7
private const val PI_F = 3.1415927f
private const val CONFETTI_GRAVITY_DP = 180f

/** Menyebar keluar dari pusat lalu jatuh pelan dan memudar di sepertiga akhir. */
private fun DrawScope.drawConfetti(progress: Float, particles: List<ConfettiParticle>) {
    if (progress <= 0f || progress >= 1f) return
    val center = Offset(size.width / 2, size.height / 2)
    val outward = 1f - (1f - progress) * (1f - progress)
    val alpha = 1f - ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)
    particles.forEach { p ->
        val distance = (p.speed * outward).dp.toPx()
        val fall = (CONFETTI_GRAVITY_DP * progress * progress).dp.toPx()
        val at = Offset(center.x + cos(p.angle) * distance, center.y + sin(p.angle) * distance + fall)
        val color = p.color.copy(alpha = alpha)
        val edge = p.size.dp.toPx()
        if (p.square) {
            rotate(p.spin + progress * 360f, pivot = at) {
                drawRect(color, topLeft = Offset(at.x - edge, at.y - edge), size = Size(edge * 2, edge * 2))
            }
        } else {
            drawCircle(color, radius = edge, center = at)
        }
    }
}

// endregion
