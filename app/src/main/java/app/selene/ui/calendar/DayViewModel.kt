package app.selene.ui.calendar

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selene.R
import app.selene.data.LogRepository
import app.selene.domain.model.DayLog
import app.selene.domain.model.Prediction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayUiState(
    val date: LocalDate,
    val today: LocalDate,
    val day: DayLog,
    val predictedPeriod: Boolean = false,
    val fertile: Boolean = false,
    val ovulation: Boolean = false,
    val loaded: Boolean = false,
) {
    val isFuture: Boolean get() = date.isAfter(today)
}

/** A deletion that can be undone from the snackbar. */
data class UndoableDeletion(@StringRes val message: Int, val deleted: DayLog)

class DayViewModel(
    private val repository: LogRepository,
    prediction: Flow<Prediction?>,
    today: StateFlow<LocalDate>,
    val date: LocalDate,
) : ViewModel() {
    val state: StateFlow<DayUiState> = combine(repository.day(date), prediction, today) { day, p, t ->
        DayUiState(
            date = date,
            today = t,
            day = day,
            predictedPeriod = day.period == null && date.isAfter(t) && p?.upcomingPeriods?.any { date in it } == true,
            fertile = p?.fertileWindows?.any { date in it.range } == true,
            ovulation = p?.fertileWindows?.any { it.ovulation == date } == true,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState(date, today.value, DayLog(date)))

    private val _deletions = Channel<UndoableDeletion>(Channel.BUFFERED)
    val deletions: Flow<UndoableDeletion> = _deletions.receiveAsFlow()

    fun deletePeriod(alsoDeleteMood: Boolean) = viewModelScope.launch {
        val deleted = repository.deletePeriodDay(date, alsoDeleteMood)
        _deletions.send(UndoableDeletion(R.string.day_deleted_period, deleted))
    }

    fun deleteMood() = viewModelScope.launch {
        runCatching { repository.deleteMood(date) }.onSuccess {
            _deletions.send(UndoableDeletion(R.string.day_deleted_mood, it))
        }
    }

    fun deleteEvent(id: String) = viewModelScope.launch {
        repository.deleteEvent(id)?.let { _deletions.send(UndoableDeletion(R.string.day_deleted_event, it)) }
    }

    fun undo(deletion: UndoableDeletion) = viewModelScope.launch {
        runCatching { repository.restore(deletion.deleted) }
    }
}
