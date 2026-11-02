package com.activaplan.app.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ikon garis (kotak 24) untuk tombol dan alert. Dekoratif: teks di sebelahnya yang dibaca screen reader. */
enum class ActivaIconKind {
    CHECK, CLOSE, PLUS, TRASH, CALENDAR, WARNING, INFO, HOME, SEARCH, LIST, SLIDERS, BELL, BELL_OFF, PLAY, DUMBBELL, CLOCK, FLAME,
    CHEVRON_LEFT, ARROW_DOWN, ARROW_UP, MINUS, DRAG_HANDLE, PAUSE, SKIP_NEXT, REFRESH, PULSE,
}

@Composable
fun ActivaIcon(kind: ActivaIconKind, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 24f
        val stroke = Stroke(width = 1.9f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(x * k, y * k)
        fun DrawScope.line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, o(x1, y1), o(x2, y2), stroke.width, StrokeCap.Round)
        fun DrawScope.dot(x: Float, y: Float) = drawCircle(tint, 1.2f * k, o(x, y))
        when (kind) {
            ActivaIconKind.CHECK -> drawPath(
                Path().apply { moveTo(5 * k, 12.5f * k); lineTo(10 * k, 17 * k); lineTo(19 * k, 7.5f * k) }, tint, style = stroke,
            )
            ActivaIconKind.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            ActivaIconKind.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            ActivaIconKind.MINUS -> line(5f, 12f, 19f, 12f)
            ActivaIconKind.DRAG_HANDLE -> listOf(6f, 12f, 18f).forEach { y -> dot(9f, y); dot(15f, y) }
            ActivaIconKind.TRASH -> {
                line(4f, 7f, 20f, 7f)
                drawPath(
                    Path().apply { moveTo(9 * k, 7 * k); lineTo(9 * k, 4.5f * k); lineTo(15 * k, 4.5f * k); lineTo(15 * k, 7 * k) },
                    tint, style = stroke,
                )
                drawPath(
                    Path().apply { moveTo(6 * k, 7 * k); lineTo(7 * k, 20 * k); lineTo(17 * k, 20 * k); lineTo(18 * k, 7 * k) },
                    tint, style = stroke,
                )
                line(10f, 11f, 10f, 16f); line(14f, 11f, 14f, 16f)
            }
            ActivaIconKind.CALENDAR -> {
                drawRoundRect(tint, o(4f, 5f), Size(16 * k, 15 * k), CornerRadius(3 * k), style = stroke)
                line(4f, 10f, 20f, 10f); line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f)
            }
            ActivaIconKind.WARNING -> {
                drawPath(
                    Path().apply { moveTo(12 * k, 3.5f * k); lineTo(21.5f * k, 20 * k); lineTo(2.5f * k, 20 * k); close() },
                    tint, style = stroke,
                )
                line(12f, 9.5f, 12f, 14f); dot(12f, 17f)
            }
            ActivaIconKind.INFO -> {
                drawCircle(tint, 9f * k, o(12f, 12f), style = stroke)
                line(12f, 11f, 12f, 16.5f); dot(12f, 7.8f)
            }
            ActivaIconKind.HOME -> drawPath(
                Path().apply {
                    moveTo(3.5f * k, 11f * k); lineTo(12 * k, 3.5f * k); lineTo(20.5f * k, 11f * k)
                    moveTo(5.5f * k, 9.5f * k); lineTo(5.5f * k, 20 * k); lineTo(9.5f * k, 20 * k)
                    lineTo(9.5f * k, 14 * k); lineTo(14.5f * k, 14 * k); lineTo(14.5f * k, 20 * k)
                    lineTo(18.5f * k, 20 * k); lineTo(18.5f * k, 9.5f * k)
                },
                tint, style = stroke,
            )
            ActivaIconKind.SEARCH -> { drawCircle(tint, 6.5f * k, o(10.5f, 10.5f), style = stroke); line(15.3f, 15.3f, 20f, 20f) }
            ActivaIconKind.LIST -> {
                line(9f, 7f, 20f, 7f); line(9f, 12f, 20f, 12f); line(9f, 17f, 20f, 17f)
                dot(4.8f, 7f); dot(4.8f, 12f); dot(4.8f, 17f)
            }
            ActivaIconKind.SLIDERS -> {
                line(6f, 3.5f, 6f, 9f); line(6f, 15f, 6f, 20.5f)
                line(12f, 3.5f, 12f, 5f); line(12f, 11f, 12f, 20.5f)
                line(18f, 3.5f, 18f, 12f); line(18f, 18f, 18f, 20.5f)
                drawCircle(tint, 3f * k, o(6f, 12f), style = stroke)
                drawCircle(tint, 3f * k, o(12f, 8f), style = stroke)
                drawCircle(tint, 3f * k, o(18f, 15f), style = stroke)
            }
            ActivaIconKind.BELL, ActivaIconKind.BELL_OFF -> {
                drawPath(
                    Path().apply {
                        moveTo(6.5f * k, 17.5f * k); lineTo(6.5f * k, 11f * k)
                        cubicTo(6.5f * k, 7.4f * k, 9f * k, 5f * k, 12f * k, 5f * k)
                        cubicTo(15f * k, 5f * k, 17.5f * k, 7.4f * k, 17.5f * k, 11f * k)
                        lineTo(17.5f * k, 17.5f * k)
                    },
                    tint, style = stroke,
                )
                line(4.5f, 17.5f, 19.5f, 17.5f); line(12f, 3.5f, 12f, 5f)
                drawPath(
                    Path().apply {
                        moveTo(10f * k, 20f * k)
                        cubicTo(10.4f * k, 21.3f * k, 13.6f * k, 21.3f * k, 14f * k, 20f * k)
                    },
                    tint, style = stroke,
                )
                if (kind == ActivaIconKind.BELL_OFF) line(4f, 3.5f, 20f, 20.5f)
            }
            ActivaIconKind.PLAY -> drawPath(
                Path().apply { moveTo(7.5f * k, 5f * k); lineTo(19f * k, 12f * k); lineTo(7.5f * k, 19f * k); close() },
                tint, style = stroke,
            )
            ActivaIconKind.DUMBBELL -> {
                line(6f, 12f, 18f, 12f)
                line(6f, 8f, 6f, 16f); line(18f, 8f, 18f, 16f)
                line(3f, 10f, 3f, 14f); line(21f, 10f, 21f, 14f)
            }
            ActivaIconKind.CLOCK -> {
                drawCircle(tint, 9f * k, o(12f, 12f), style = stroke)
                drawPath(Path().apply { moveTo(12 * k, 7 * k); lineTo(12 * k, 12 * k); lineTo(15.5f * k, 14 * k) }, tint, style = stroke)
            }
            ActivaIconKind.CHEVRON_LEFT -> drawPath(
                Path().apply { moveTo(14.5f * k, 5.5f * k); lineTo(8f * k, 12f * k); lineTo(14.5f * k, 18.5f * k) }, tint, style = stroke,
            )
            ActivaIconKind.ARROW_DOWN -> {
                line(12f, 5f, 12f, 19f)
                drawPath(Path().apply { moveTo(6.5f * k, 13.5f * k); lineTo(12f * k, 19f * k); lineTo(17.5f * k, 13.5f * k) }, tint, style = stroke)
            }
            ActivaIconKind.ARROW_UP -> {
                line(12f, 5f, 12f, 19f)
                drawPath(Path().apply { moveTo(6.5f * k, 10.5f * k); lineTo(12f * k, 5f * k); lineTo(17.5f * k, 10.5f * k) }, tint, style = stroke)
            }
            ActivaIconKind.PAUSE -> { line(9f, 5.5f, 9f, 18.5f); line(15f, 5.5f, 15f, 18.5f) }
            ActivaIconKind.SKIP_NEXT -> {
                drawPath(
                    Path().apply { moveTo(6f * k, 5.5f * k); lineTo(15f * k, 12f * k); lineTo(6f * k, 18.5f * k); close() },
                    tint, style = stroke,
                )
                line(18.5f, 5.5f, 18.5f, 18.5f)
            }
            ActivaIconKind.REFRESH -> {
                drawArc(
                    tint, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                    topLeft = o(5f, 5f), size = Size(14 * k, 14 * k), style = stroke,
                )
                drawPath(
                    Path().apply { moveTo(12.5f * k, 2.5f * k); lineTo(16.5f * k, 5.2f * k); lineTo(12.8f * k, 8.2f * k) },
                    tint, style = stroke,
                )
            }
            ActivaIconKind.PULSE -> drawPath(
                Path().apply {
                    moveTo(3 * k, 12 * k); lineTo(8 * k, 12 * k); lineTo(10.5f * k, 5.5f * k)
                    lineTo(13.5f * k, 18.5f * k); lineTo(16 * k, 12 * k); lineTo(21 * k, 12 * k)
                },
                tint, style = stroke,
            )
            ActivaIconKind.FLAME -> drawPath(
                Path().apply {
                    moveTo(12f * k, 3f * k)
                    cubicTo(13f * k, 6f * k, 17.5f * k, 8.5f * k, 17.5f * k, 14f * k)
                    cubicTo(17.5f * k, 17.6f * k, 15f * k, 20.5f * k, 12f * k, 20.5f * k)
                    cubicTo(9f * k, 20.5f * k, 6.5f * k, 17.6f * k, 6.5f * k, 14f * k)
                    cubicTo(6.5f * k, 12f * k, 7.5f * k, 10.5f * k, 8.5f * k, 9.5f * k)
                    cubicTo(9f * k, 11f * k, 9.5f * k, 11.8f * k, 10.5f * k, 12f * k)
                    cubicTo(10.2f * k, 9f * k, 10.8f * k, 5.5f * k, 12f * k, 3f * k)
                    close()
                },
                tint, style = stroke,
            )
        }
    }
}
