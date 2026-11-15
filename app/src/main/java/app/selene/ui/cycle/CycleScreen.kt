package app.selene.ui.cycle

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selene.R
import app.selene.domain.model.CurrentCycle
import app.selene.domain.model.Prediction
import app.selene.domain.model.daysBetween
import app.selene.ui.DateFormats
import app.selene.ui.components.PrimaryButton
import app.selene.ui.components.ScreenHeader
import app.selene.ui.components.SeleneCard
import app.selene.ui.labelRes
import app.selene.ui.plural
import app.selene.ui.relativeDays
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens
import java.time.LocalDate

@Composable
fun CycleScreen(viewModel: CycleViewModel, onAddPeriod: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CycleContent(state, onAddPeriod)
}

@Composable
fun CycleContent(state: CycleUiState, onAddPeriod: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = stringResource(R.string.today),
            subtitle = DateFormats.weekdayDayMonth(state.today),
        )
        Column(
            Modifier.padding(horizontal = SeleneDimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val prediction = state.prediction
            val current = prediction?.current
            if (prediction == null) {
                CycleRing(null, "", Modifier.fillMaxWidth(0.82f)) {}
            } else if (current == null) {
                EmptyCycle(onAddPeriod)
            } else {
                val model = ringModel(current)
                CycleRing(model, ringDescription(current, model), Modifier.fillMaxWidth(0.82f)) { RingCenter(current) }
                Legend()
                PredictionCards(prediction, current, state.today)
                Text(
                    stringResource(R.string.estimate_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = SeleneColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(SeleneDimens.BottomBarClearance))
        }
    }
}

internal fun ringModel(c: CurrentCycle): RingModel {
    fun day(date: LocalDate) = daysBetween(c.start, date) + 1
    return RingModel(
        totalDays = c.expectedLength,
        currentDay = c.dayOfCycle,
        periodDays = 1..c.episode.length,
        fertileDays = day(c.fertileWindow.range.start)..day(c.fertileWindow.range.endInclusive),
        ovulationDay = day(c.fertileWindow.ovulation),
        pmsDays = c.pmsRange?.let { day(it.start)..day(it.endInclusive) },
        pmsFromSignal = c.pmsFromSignal,
    )
}

@Composable
private fun ringDescription(c: CurrentCycle, m: RingModel): String {
    val pms = m.pmsDays?.let { stringResource(R.string.cycle_ring_pms_description, it.first) } ?: ""
    return stringResource(
        R.string.cycle_ring_description,
        c.dayOfCycle,
        c.expectedLength,
        stringResource(c.phase.labelRes()),
        m.periodDays.last,
        m.fertileDays?.first ?: 0,
        m.fertileDays?.last ?: 0,
        m.ovulationDay ?: 0,
        pms,
    )
}

@Composable
private fun RingCenter(c: CurrentCycle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.cycle_day, c.dayOfCycle), style = MaterialTheme.typography.displayMedium, color = SeleneColors.TextPrimary)
        Text(stringResource(R.string.cycle_of, c.expectedLength), style = MaterialTheme.typography.bodyLarge, color = SeleneColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        val label = if (c.isLate) plural(R.plurals.late_days, c.lateByDays) else stringResource(c.phase.labelRes())
        Text(label, style = MaterialTheme.typography.titleMedium, color = phaseColor(c), textAlign = TextAlign.Center)
    }
}

private fun phaseColor(c: CurrentCycle): Color = when (c.phase) {
    app.selene.domain.model.CyclePhase.MENSTRUATION, app.selene.domain.model.CyclePhase.LATE -> SeleneColors.Period
    app.selene.domain.model.CyclePhase.FERTILE, app.selene.domain.model.CyclePhase.OVULATION -> SeleneColors.Fertile
    app.selene.domain.model.CyclePhase.PMS -> SeleneColors.TextPrimary
    else -> SeleneColors.TextSecondary
}

@Composable
private fun EmptyCycle(onAddPeriod: () -> Unit) {
    CycleRing(null, stringResource(R.string.cycle_ring_empty_description), Modifier.fillMaxWidth(0.82f)) {
        Column(Modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.cycle_empty_title), style = MaterialTheme.typography.headlineSmall, color = SeleneColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.cycle_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = SeleneColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
    PrimaryButton(stringResource(R.string.cycle_empty_action), onClick = onAddPeriod)
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(stringResource(R.string.cycle_legend_period), SeleneColors.Period, 1f, dashed = false)
        LegendItem(stringResource(R.string.cycle_legend_fertile), SeleneColors.Fertile, 0.5f, dashed = false)
        LegendItem(stringResource(R.string.cycle_legend_pms), SeleneColors.Pms, 0.55f, dashed = true)
    }
}

@Composable
private fun LegendItem(label: String, color: Color, thickness: Float, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
            val w = size.height * thickness
            drawLine(
                color,
                Offset(w / 2, center.y),
                Offset(size.width - w / 2, center.y),
                strokeWidth = w,
                cap = if (dashed) StrokeCap.Butt else StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = SeleneColors.TextSecondary)
    }
}

@Composable
private fun PredictionCards(p: Prediction, c: CurrentCycle, today: LocalDate) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val ovulation = p.nextOvulation
        EstimateCard(
            title = stringResource(R.string.cycle_ovulation_card),
            accent = SeleneColors.Fertile,
            date = ovulation,
            today = today,
            margin = c.nextPeriod.marginDays,
            note = null,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        EstimateCard(
            title = stringResource(R.string.cycle_next_period_card),
            accent = SeleneColors.Period,
            date = c.nextPeriod.date,
            today = today,
            margin = c.nextPeriod.marginDays,
            note = if (c.isLate) plural(R.plurals.late_days, c.lateByDays) else null,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
    if (c.isLate) {
        Text(stringResource(R.string.cycle_late_hint), style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextSecondary)
    }
}

@Composable
private fun EstimateCard(
    title: String,
    accent: Color,
    date: LocalDate?,
    today: LocalDate,
    margin: Int,
    note: String?,
    modifier: Modifier,
) {
    SeleneCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(10.dp)) { drawCircle(accent) }
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = SeleneColors.TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        if (date != null) {
            Text(DateFormats.short(date), style = MaterialTheme.typography.titleLarge, color = SeleneColors.TextPrimary)
            Text(relativeDays(today, date), style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextPrimary)
            Text(plural(R.plurals.margin_days, margin), style = MaterialTheme.typography.bodySmall, color = SeleneColors.TextSecondary)
        }
        if (note != null) {
            Spacer(Modifier.height(4.dp))
            Text(note, style = MaterialTheme.typography.labelMedium, color = accent)
        }
    }
}
