package app.selene.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selene.R
import app.selene.ui.DateFormats
import app.selene.ui.components.ScreenHeader
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

const val CALENDAR_LIST_TAG = "calendar_months"

private val CellHeight = 48.dp
private val MarkSize = 36.dp

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onDayClick: (LocalDate) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state ?: return
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = s.currentMonthIndex)
    CalendarContent(s, listState, onDayClick)
}

@Composable
fun CalendarContent(state: CalendarUiState, listState: LazyListState, onDayClick: (LocalDate) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.tab_calendar),
            actions = {
                TextButton(onClick = { scope.launch { listState.animateScrollToItem(state.currentMonthIndex) } }) {
                    Text(stringResource(R.string.calendar_today), color = SeleneColors.TextPrimary)
                }
            },
        )
        Legend()
        WeekdayHeader()
        HorizontalDivider(color = SeleneColors.Divider)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = SeleneDimens.BottomBarClearance),
            modifier = Modifier
                .fillMaxSize()
                .testTag(CALENDAR_LIST_TAG)
                .padding(horizontal = 12.dp),
        ) {
            itemsIndexed(state.months, key = { _, m -> m.toString() }) { _, month ->
                MonthGrid(month, state, onDayClick)
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val letters = stringResource(R.string.calendar_weekdays).split(",")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clearAndSetSemantics { },
    ) {
        letters.forEach {
            Text(
                it,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = SeleneColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, state: CalendarUiState, onDayClick: (LocalDate) -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            DateFormats.monthYear(month),
            style = MaterialTheme.typography.titleMedium,
            color = SeleneColors.TextPrimary,
            modifier = Modifier
                .padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
                .semantics { heading() },
        )
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val totalCells = offset + month.lengthOfMonth()
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = row * 7 + col - offset + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(CellHeight))
                    } else {
                        DayCell(month.atDay(dayNumber), state, onDayClick, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, state: CalendarUiState, onDayClick: (LocalDate) -> Unit, modifier: Modifier) {
    val isPeriod = date in state.periodDays
    val isPredicted = !isPeriod && date in state.predictedPeriodDays
    val isFertile = date in state.fertileDays
    val isOvulation = !isPeriod && date in state.ovulationDays
    val isToday = date == state.today
    val hasEvent = date in state.eventDays
    val fertileBefore = date.minusDays(1) in state.fertileDays && date.dayOfWeek != DayOfWeek.MONDAY && date.dayOfMonth != 1
    val fertileAfter = date.plusDays(1) in state.fertileDays && date.dayOfWeek != DayOfWeek.SUNDAY &&
        date.dayOfMonth != date.lengthOfMonth()

    val description = buildList {
        add(DateFormats.dayMonth(date))
        if (isToday) add(stringResource(R.string.cal_desc_today))
        if (isPeriod) add(stringResource(R.string.cal_desc_period))
        if (isPredicted) add(stringResource(R.string.cal_desc_predicted))
        if (isOvulation) add(stringResource(R.string.cal_desc_ovulation))
        else if (isFertile) add(stringResource(R.string.cal_desc_fertile))
        if (date in state.moodDays && !isPeriod) add(stringResource(R.string.cal_desc_mood))
        if (hasEvent) add(stringResource(R.string.cal_desc_event))
    }.joinToString(", ")

    val textColor = when {
        isPeriod || isOvulation -> SeleneColors.OnPeriod
        date.isAfter(state.today) -> SeleneColors.TextSecondary
        else -> SeleneColors.TextPrimary
    }
    Box(
        modifier
            .height(CellHeight)
            .clickable(role = Role.Button) { onDayClick(date) }
            .clearAndSetSemantics { contentDescription = description }
            .drawBehind {
                val mark = MarkSize.toPx()
                val cy = size.height / 2
                if (isFertile) {
                    val h = mark
                    val left = if (fertileBefore) 0f else (size.width - mark) / 2
                    val right = if (fertileAfter) size.width else (size.width + mark) / 2
                    drawRoundRect(
                        SeleneColors.Fertile.copy(alpha = 0.16f),
                        topLeft = Offset(left, cy - h / 2),
                        size = Size(right - left, h),
                        cornerRadius = CornerRadius(if (fertileBefore && fertileAfter) 0f else h / 2),
                    )
                }
                val c = Offset(size.width / 2, cy)
                when {
                    isPeriod -> drawCircle(SeleneColors.Period, mark / 2, c)
                    isOvulation -> drawCircle(SeleneColors.Fertile, mark / 2, c)
                    isPredicted -> drawCircle(
                        SeleneColors.Period,
                        mark / 2 - 1.dp.toPx(),
                        c,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
                if (isToday) drawCircle(SeleneColors.TextPrimary, mark / 2 + 2.5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                if (hasEvent) {
                    drawCircle(SeleneColors.Background, 3.5.dp.toPx(), Offset(c.x, c.y + mark / 2 + 1.dp.toPx()))
                    drawCircle(SeleneColors.Event, 2.6.dp.toPx(), Offset(c.x, c.y + mark / 2 + 1.dp.toPx()))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || isPeriod) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
    }
}

@Composable
private fun Legend() {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SeleneDimens.ScreenPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendMark(stringResource(R.string.cycle_legend_period)) { drawCircle(SeleneColors.Period) }
        LegendMark(stringResource(R.string.cal_legend_predicted)) {
            drawCircle(SeleneColors.Period, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))))
        }
        LegendMark(stringResource(R.string.cal_legend_ovulation)) { drawCircle(SeleneColors.Fertile) }
        LegendMark(stringResource(R.string.cycle_legend_fertile)) {
            drawRoundRect(SeleneColors.Fertile.copy(alpha = 0.3f), cornerRadius = CornerRadius(size.height / 2))
        }
        LegendMark(stringResource(R.string.cal_legend_event)) { drawCircle(SeleneColors.Event, size.minDimension / 4) }
    }
}

@Composable
private fun LegendMark(label: String, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(12.dp), onDraw = draw)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = SeleneColors.TextSecondary, maxLines = 1)
    }
}

