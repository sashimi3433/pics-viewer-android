package dev.sashimi.picsviewer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI

/** カウントダウン側の分割数（下のアンカーと合わせて9分割） */
private const val COUNT_SEGMENTS = 8

/** 下の長いセグメント（常に信号色）の角度 */
private const val ANCHOR_SWEEP = 36f
private const val ANCHOR_START = 72f

/**
 * 信号の残り時間リング。
 *
 * - 9分割：下の1つは長めの「アンカー」で、常に現在の信号色（青／赤）で点灯
 * - 残り8分割は M3E の primary 色で、レベルに応じて左から右へ減っていく
 * - セグメントは角丸（StrokeCap.Round）
 */
@Composable
fun SignalRing(
    level: Int?,
    signalColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 196.dp,
    strokeFraction: Float = 0.075f,
    indeterminate: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val filled = ((level ?: -1) + 1).coerceIn(0, COUNT_SEGMENTS)
    val progressColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "progressColor",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // リング自体は装飾。意味は呼び出し側の説明文で伝える
        Canvas(Modifier.size(size).clearAndSetSemantics {}) {
            val stroke = this.size.minDimension * strokeFraction
            val radius = (this.size.minDimension - stroke) / 2f
            // 丸キャップが隣とくっつかないよう、キャップ分＋余白をギャップにする
            val capDeg = (stroke / 2f) / radius * (180f / PI.toFloat())
            val gap = capDeg * 2f + 6f

            val arcTopLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val arcStyle = Stroke(width = stroke, cap = StrokeCap.Round)

            // 下のアンカー：常に信号色
            drawArc(
                color = signalColor,
                startAngle = ANCHOR_START,
                sweepAngle = ANCHOR_SWEEP,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = arcStyle,
            )

            // 残り8分割：M3E の primary で減っていく
            val span = 360f - ANCHOR_SWEEP - gap * 2f
            val sweep = (span - gap * (COUNT_SEGMENTS - 1)) / COUNT_SEGMENTS
            var start = ANCHOR_START + ANCHOR_SWEEP + gap

            repeat(COUNT_SEGMENTS) { index ->
                val segmentColor = when {
                    indeterminate -> progressColor.copy(alpha = 0.35f)
                    index < filled -> progressColor
                    else -> trackColor
                }
                drawArc(
                    color = segmentColor,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = arcStyle,
                )
                start += sweep + gap
            }
        }
        content()
    }
}
