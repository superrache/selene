package app.selene.ui.analysis

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.selene.R
import app.selene.domain.Predictor
import app.selene.domain.model.Prediction
import app.selene.ui.components.SeleneCard
import app.selene.ui.components.SeleneIcons
import app.selene.ui.formatDecimal
import app.selene.ui.plural
import app.selene.ui.theme.SeleneColors

/** Above this many cycles, the full weighted sum no longer fits on a line: a generic formula is shown. */
private const val FULL_FORMULA_MAX_CYCLES = 6

/** "How is it calculated?": explains the method with the user's own numbers. */
@Composable
fun MethodCard(prediction: Prediction) {
    SeleneCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.method_title),
            style = MaterialTheme.typography.titleMedium,
            color = SeleneColors.TextPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        MethodSection(stringResource(R.string.method_average_title), initiallyExpanded = true) {
            BodyText(stringResource(R.string.method_average_body))
            Spacer(Modifier.height(12.dp))
            AverageExplanation(prediction)
        }
        MethodSection(stringResource(R.string.method_cycles_title)) { BodyText(stringResource(R.string.method_cycles_body)) }
        MethodSection(stringResource(R.string.method_excluded_title)) { BodyText(stringResource(R.string.method_excluded_body)) }
        MethodSection(stringResource(R.string.method_pms_title)) { BodyText(stringResource(R.string.method_pms_body)) }
        MethodSection(stringResource(R.string.method_privacy_title), divider = false) {
            BodyText(stringResource(R.string.method_privacy_body))
        }
    }
}

@Composable
private fun AverageExplanation(prediction: Prediction) {
    val stats = prediction.stats
    if (stats.usesDefaults) {
        BodyText(stringResource(R.string.method_average_defaults), color = SeleneColors.TextSecondary)
        return
    }
    val lengths = Predictor.statisticsCycles(prediction.cycles).map { it.length!! }
    val weights = Predictor.cycleWeights(lengths.size)
    val weighted = stats.averageCycleLength
    val simple = lengths.average()
    WeightChart(lengths, weights, weighted, simple)
    Spacer(Modifier.height(10.dp))
    val formula = if (lengths.size <= FULL_FORMULA_MAX_CYCLES) {
        val terms = lengths.zip(weights).map { (l, w) -> stringResource(R.string.method_average_term, l, w) }.joinToString(" + ")
        stringResource(R.string.method_average_formula, terms, weights.sum(), formatDecimal(weighted))
    } else {
        stringResource(R.string.method_average_formula_long, formatDecimal(weighted))
    }
    Text(formula, style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextPrimary)
    Spacer(Modifier.height(4.dp))
    BodyText(stringResource(R.string.method_average_simple, formatDecimal(simple)), color = SeleneColors.TextSecondary)
}

/**
 * Didactic chart: one bar per cycle kept for the statistics, oldest first. The coral gets more
 * intense with the weight, written under each bar. Dashed line: weighted mean; dotted: simple mean.
 * The axis starts just below the shortest cycle so that differences of a few days stay visible
 * (each bar carries its value).
 */
@Composable
private fun WeightChart(lengths: List<Int>, weights: List<Int>, weighted: Double, simple: Double) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = SeleneColors.TextSecondary)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = SeleneColors.TextPrimary)
    val items = lengths.zip(weights).map { (l, w) -> stringResource(R.string.method_chart_item, plural(R.plurals.days_count, l), w) }
    val description = stringResource(R.string.method_chart_description, items.joinToString(", "), formatDecimal(weighted), formatDecimal(simple))
    val weightLabels = weights.map { stringResource(R.string.method_chart_weight, it) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val top = 18.dp.toPx()
        val bottomLabels = 22.dp.toPx()
        val base = size.height - bottomLabels
        val low = minOf(lengths.min().toDouble(), simple, weighted) - 3
        val high = maxOf(lengths.max().toDouble(), simple, weighted) + 1
        fun y(v: Double) = (base - (v - low) / (high - low) * (base - top)).toFloat()
        val slot = size.width / lengths.size
        val barWidth = (slot * 0.6f).coerceAtMost(36.dp.toPx())
        val n = weights.max()
        lengths.forEachIndexed { i, length ->
            val x = i * slot + (slot - barWidth) / 2
            val barTop = y(length.toDouble())
            val alpha = 0.25f + 0.75f * weights[i] / n
            drawRoundRect(
                SeleneColors.Period.copy(alpha = alpha),
                Offset(x, barTop),
                Size(barWidth, base - barTop),
                CornerRadius(6.dp.toPx()),
            )
            val value = measurer.measure(length.toString(), valueStyle)
            drawText(value, topLeft = Offset(x + barWidth / 2 - value.size.width / 2, barTop - value.size.height - 2.dp.toPx()))
            val weight = measurer.measure(weightLabels[i], labelStyle)
            drawText(weight, topLeft = Offset(x + barWidth / 2 - weight.size.width / 2, base + 6.dp.toPx()))
        }
        drawLine(
            SeleneColors.TextSecondary,
            Offset(0f, y(simple)),
            Offset(size.width, y(simple)),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx())),
        )
        drawLine(
            SeleneColors.TextPrimary,
            Offset(0f, y(weighted)),
            Offset(size.width, y(weighted)),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LineLegend(stringResource(R.string.method_chart_weighted, formatDecimal(weighted)), SeleneColors.TextPrimary, dotted = false)
        LineLegend(stringResource(R.string.method_chart_simple, formatDecimal(simple)), SeleneColors.TextSecondary, dotted = true)
    }
}

@Composable
private fun LineLegend(label: String, color: Color, dotted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 20.dp, height = 8.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 1.5.dp.toPx(),
                cap = if (dotted) StrokeCap.Round else StrokeCap.Butt,
                pathEffect = if (dotted) {
                    PathEffect.dashPathEffect(floatArrayOf(0.1f, 4.dp.toPx()))
                } else {
                    PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                },
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = SeleneColors.TextSecondary)
    }
}

@Composable
private fun MethodSection(
    title: String,
    initiallyExpanded: Boolean = false,
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val state = stringResource(if (expanded) R.string.method_expanded else R.string.method_collapsed)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics(mergeDescendants = true) { stateDescription = state },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = SeleneColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                SeleneIcons.ChevronRight,
                contentDescription = null,
                tint = SeleneColors.TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(bottom = 14.dp), content = content)
        }
        if (divider) HorizontalDivider(color = SeleneColors.Divider)
    }
}

@Composable
private fun BodyText(text: String, color: Color = SeleneColors.TextPrimary) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}
