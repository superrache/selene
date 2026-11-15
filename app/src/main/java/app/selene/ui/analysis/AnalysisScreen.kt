package app.selene.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selene.R
import app.selene.domain.model.Cycle
import app.selene.domain.model.Prediction
import app.selene.ui.DateFormats
import app.selene.ui.components.ScreenHeader
import app.selene.ui.components.SectionTitle
import app.selene.ui.components.SeleneCard
import app.selene.ui.components.SeleneIcons
import app.selene.ui.formatDecimal
import app.selene.ui.labelRes
import app.selene.ui.plural
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens
import java.time.YearMonth

/** Number of recent cycles shown in the bar chart. */
private const val CHART_CYCLES = 12

@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel) {
    val prediction by viewModel.prediction.collectAsStateWithLifecycle()
    AnalysisContent(prediction)
}

@Composable
fun AnalysisContent(prediction: Prediction?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.analysis_title))
        Column(
            Modifier.padding(horizontal = SeleneDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (prediction != null) {
                val stats = prediction.stats
                if (stats.usesDefaults) {
                    InfoCard(stringResource(if (prediction.hasData) R.string.analysis_defaults_note else R.string.analysis_empty))
                }
                StatTiles(prediction)
                val chartCycles = prediction.cycles.filter { it.isComplete }.takeLast(CHART_CYCLES)
                if (chartCycles.isNotEmpty()) CycleChart(chartCycles, if (stats.usesDefaults) null else stats.averageCycleLength)
                if (prediction.hasData) ForecastCard(prediction)
                MoodSignalCard(prediction)
            }
            DisclaimerCard()
            Spacer(Modifier.height(SeleneDimens.BottomBarClearance))
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    SeleneCard(Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextPrimary)
    }
}

@Composable
private fun StatTiles(p: Prediction) {
    val s = p.stats
    val dash = "—"
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(
            stringResource(R.string.analysis_avg_cycle),
            if (s.usesDefaults) dash else stringResource(R.string.analysis_value_days, formatDecimal(s.averageCycleLength)),
            Modifier.weight(1f).fillMaxHeight(),
        )
        StatTile(
            stringResource(R.string.analysis_variability),
            s.standardDeviation?.let { stringResource(R.string.analysis_value_margin, formatDecimal(it)) } ?: dash,
            Modifier.weight(1f).fillMaxHeight(),
        )
        StatTile(
            stringResource(R.string.analysis_period_length),
            if (s.usesDefaults) dash else stringResource(R.string.analysis_value_days, s.averagePeriodLength.toString()),
            Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    SeleneCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SeleneColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = SeleneColors.TextPrimary, maxLines = 1)
    }
}

@Composable
private fun CycleChart(cycles: List<Cycle>, average: Double?) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = SeleneColors.TextSecondary)
    val excludedLabel = stringResource(R.string.analysis_chart_item_excluded)
    val items = cycles.map { c -> stringResource(R.string.analysis_chart_item, plural(R.plurals.days_count, c.length!!), if (c.isValid) "" else excludedLabel) }
    val description = stringResource(
        R.string.analysis_chart_description,
        items.joinToString(", "),
        average?.let(::formatDecimal) ?: "—",
    )
    SeleneCard(Modifier.fillMaxWidth()) {
        SectionTitle(
            stringResource(R.string.analysis_cycles_chart),
            trailing = average?.let { stringResource(R.string.analysis_chart_average, formatDecimal(it)) },
        )
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clearAndSetSemantics { contentDescription = description },
        ) {
            val labelHeight = 30.dp.toPx()
            val chartHeight = size.height - labelHeight
            val maxValue = maxOf(cycles.maxOf { it.length!! }, (average ?: 0.0).toInt(), 35).toFloat()
            val slot = size.width / CHART_CYCLES.coerceAtLeast(cycles.size)
            val barWidth = slot * 0.56f
            cycles.forEachIndexed { i, cycle ->
                val length = cycle.length!!
                val h = chartHeight * (length.coerceAtMost(maxValue.toInt()) / maxValue)
                val x = i * slot + (slot - barWidth) / 2
                val top = chartHeight - h
                if (cycle.isValid) {
                    drawRoundRect(SeleneColors.Period, Offset(x, top), Size(barWidth, h), CornerRadius(6.dp.toPx()))
                } else {
                    // Excluded cycle: outlined and hatched, not just a different colour.
                    drawRoundRect(SeleneColors.SurfaceHigh, Offset(x, top), Size(barWidth, h), CornerRadius(6.dp.toPx()))
                    clipRect(x, top, x + barWidth, chartHeight) {
                        var y = top - barWidth
                        while (y < chartHeight) {
                            drawLine(SeleneColors.TextSecondary, Offset(x, y + barWidth), Offset(x + barWidth, y), 1.dp.toPx())
                            y += 6.dp.toPx()
                        }
                    }
                }
                val value = measurer.measure(length.toString(), labelStyle)
                drawText(value, topLeft = Offset(x + barWidth / 2 - value.size.width / 2, (top - value.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
                val month = measurer.measure(DateFormats.shortMonth(YearMonth.from(cycle.start)).take(4), labelStyle)
                drawText(month, topLeft = Offset(x + barWidth / 2 - month.size.width / 2, chartHeight + 6.dp.toPx()))
            }
            if (average != null) {
                val y = chartHeight - chartHeight * (average.toFloat() / maxValue)
                drawLine(
                    SeleneColors.TextPrimary,
                    Offset(0f, y),
                    Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }
        }
        if (cycles.any { !it.isValid }) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.analysis_chart_excluded), style = MaterialTheme.typography.bodySmall, color = SeleneColors.TextSecondary)
        }
    }
}

@Composable
private fun ForecastCard(p: Prediction) {
    val c = p.current ?: return
    SeleneCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.analysis_forecast))
        Spacer(Modifier.height(12.dp))
        val period = c.nextPeriod
        ForecastLine(
            stringResource(R.string.analysis_next_period),
            DateFormats.weekdayDayMonth(period.date),
            stringResource(R.string.analysis_interval, DateFormats.dayMonth(period.range.start), DateFormats.dayMonth(period.range.endInclusive)),
        )
        p.nextFertileWindow?.let { w ->
            ForecastLine(
                stringResource(R.string.analysis_ovulation),
                DateFormats.weekdayDayMonth(w.ovulation),
                stringResource(R.string.analysis_interval, DateFormats.dayMonth(w.ovulation.minusDays(period.marginDays.toLong())), DateFormats.dayMonth(w.ovulation.plusDays(period.marginDays.toLong()))),
            )
            ForecastLine(
                stringResource(R.string.analysis_fertile_window),
                stringResource(R.string.analysis_range, DateFormats.dayMonth(w.range.start), DateFormats.dayMonth(w.range.endInclusive)),
                null,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.analysis_reliability) + " : " + stringResource(p.reliability.labelRes()),
            style = MaterialTheme.typography.titleSmall,
            color = SeleneColors.TextPrimary,
        )
        Text(
            stringResource(R.string.analysis_reliability_basis, plural(R.plurals.cycles_count, p.stats.validCycleCount)),
            style = MaterialTheme.typography.bodySmall,
            color = SeleneColors.TextSecondary,
        )
    }
}

@Composable
private fun ForecastLine(label: String, value: String, detail: String?) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SeleneColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = SeleneColors.TextPrimary)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = SeleneColors.TextSecondary)
    }
}

@Composable
private fun MoodSignalCard(p: Prediction) {
    val s = p.pmsSignal
    val c = p.current
    val text = when {
        c != null && c.refinedByMood && c.refinementMoodDate != null -> stringResource(
            R.string.analysis_mood_used,
            DateFormats.dayMonth(c.refinementMoodDate),
            DateFormats.dayMonth(c.statisticalNextPeriod),
            plural(R.plurals.cycles_count, s.cyclesWithSignal),
            s.cyclesAnalyzed,
            formatDecimal(s.medianOffsetDays ?: 0.0),
        )
        s.isReliable -> stringResource(R.string.analysis_mood_reliable, formatDecimal(s.medianOffsetDays ?: 0.0), plural(R.plurals.cycles_count, s.cyclesWithSignal), s.cyclesAnalyzed)
        s.cyclesAnalyzed > 0 -> stringResource(R.string.analysis_mood_unreliable, plural(R.plurals.cycles_count, s.cyclesWithSignal), s.cyclesAnalyzed)
        else -> stringResource(R.string.analysis_mood_none)
    }
    SeleneCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.analysis_mood_signal))
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextPrimary)
    }
}

@Composable
private fun DisclaimerCard() {
    SeleneCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(SeleneIcons.Warning, contentDescription = null, tint = SeleneColors.Event, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.analysis_disclaimer), style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextPrimary)
        }
    }
}
