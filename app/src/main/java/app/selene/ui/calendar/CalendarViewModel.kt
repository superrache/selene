package app.selene.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selene.data.LogRepository
import app.selene.domain.model.DayLog
import app.selene.domain.model.Prediction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** What to draw on each day. Sets are cheap to query from the grid. */
data class CalendarUiState(
    val today: LocalDate,
    val months: List<YearMonth>,
    val periodDays: Set<LocalDate> = emptySet(),
    val predictedPeriodDays: Set<LocalDate> = emptySet(),
    val fertileDays: Set<LocalDate> = emptySet(),
    val ovulationDays: Set<LocalDate> = emptySet(),
    val eventDays: Set<LocalDate> = emptySet(),
    val moodDays: Set<LocalDate> = emptySet(),
) {
    val currentMonthIndex: Int get() = months.indexOf(YearMonth.from(today)).coerceAtLeast(0)

    companion object {
        const val MONTHS_BEFORE = 12L
        const val MONTHS_AFTER = 6L

        fun build(days: List<DayLog>, prediction: Prediction?, today: LocalDate): CalendarUiState {
            val current = YearMonth.from(today)
            val first = listOfNotNull(days.firstOrNull()?.date?.let(YearMonth::from), current.minusMonths(MONTHS_BEFORE)).min()
            val last = current.plusMonths(MONTHS_AFTER)
            val months = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList()
            val periodDays = days.filter { it.period != null }.map { it.date }.toSet()
            val predicted = prediction?.upcomingPeriods.orEmpty()
                .flatMap { it.dates().toList() }
                .filter { it.isAfter(today) || (it == today && it !in periodDays) }
                .filterNot { it in periodDays }
                .toSet()
            val fertile = prediction?.fertileWindows.orEmpty().flatMap { it.range.dates().toList() }.toSet()
            val ovulation = prediction?.fertileWindows.orEmpty().map { it.ovulation }.toSet()
            return CalendarUiState(
                today = today,
                months = months,
                periodDays = periodDays,
                predictedPeriodDays = predicted,
                fertileDays = fertile,
                ovulationDays = ovulation,
                eventDays = days.filter { it.events.isNotEmpty() }.map { it.date }.toSet(),
                moodDays = days.filter { it.mood != null }.map { it.date }.toSet(),
            )
        }
    }
}

class CalendarViewModel(
    repository: LogRepository,
    prediction: Flow<Prediction?>,
    today: StateFlow<LocalDate>,
) : ViewModel() {
    val state: StateFlow<CalendarUiState?> = combine(repository.days, prediction, today) { days, p, t ->
        CalendarUiState.build(days, p, t)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
