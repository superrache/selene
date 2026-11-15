package app.selene.ui.cycle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.selene.ui.theme.SeleneColors
import kotlin.math.cos
import kotlin.math.sin

/** Day-based description of the ring. Days are 1-based positions in the cycle. */
data class RingModel(
    val totalDays: Int,
    val currentDay: Int,
    val periodDays: IntRange,
    val fertileDays: IntRange?,
    val ovulationDay: Int?,
    val pmsDays: IntRange?,
    val pmsFromSignal: Boolean,
)

/**
 * The cycle ring: period arc from the top (full width), fertile window (thinner arc, with
 * the ovulation as a dot), "probable PMS" (dashed arc), white cursor on today.
 * Shapes differ, not only colours. Null [model] draws a neutral ring (empty state).
 */
@Composable
fun CycleRing(model: RingModel?, description: String, modifier: Modifier = Modifier, center: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .aspectRatio(1f)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawRing(model) }
        center()
    }
}

private fun DrawScope.drawRing(model: RingModel?) {
    val stroke = 22.dp.toPx()
    val radius = size.minDimension / 2 - stroke
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    drawCircle(SeleneColors.SurfaceHigh, radius, center, style = Stroke(stroke))
    if (model == null || model.totalDays <= 0) return

    val perDay = 360f / model.totalDays
    fun startAngle(day: Int) = -90f + (day - 1) * perDay
    fun clamp(range: IntRange): IntRange? {
        val first = range.first.coerceAtLeast(1)
        val last = range.last.coerceAtMost(model.totalDays)
        return if (first <= last) first..last else null
    }
    fun arc(range: IntRange, color: androidx.compose.ui.graphics.Color, width: Float, effect: PathEffect? = null, alpha: Float = 1f) {
        val r = clamp(range) ?: return
        val inset = perDay * 0.08f
        drawArc(
            color = color,
            startAngle = startAngle(r.first) + inset,
            sweepAngle = (r.last - r.first + 1) * perDay - 2 * inset,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            alpha = alpha,
            style = Stroke(width, cap = if (effect == null) StrokeCap.Round else StrokeCap.Butt, pathEffect = effect),
        )
    }

    model.pmsDays?.let {
        val dash = 4.dp.toPx()
        arc(it, SeleneColors.Pms, stroke * 0.55f, PathEffect.dashPathEffect(floatArrayOf(dash, dash)), if (model.pmsFromSignal) 1f else 0.75f)
    }
    model.fertileDays?.let { arc(it, SeleneColors.Fertile, stroke * 0.5f) }
    arc(model.periodDays, SeleneColors.Period, stroke)

    fun pointFor(day: Int): Offset {
        val angle = Math.toRadians((startAngle(day) + perDay / 2).toDouble())
        return Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
    }
    model.ovulationDay?.takeIf { it in 1..model.totalDays }?.let {
        drawCircle(SeleneColors.Fertile, stroke * 0.42f, pointFor(it))
    }
    val cursor = pointFor(model.currentDay.coerceIn(1, model.totalDays))
    drawCircle(SeleneColors.Background, stroke * 0.68f, cursor)
    drawCircle(SeleneColors.TextPrimary, stroke * 0.52f, cursor)
}
