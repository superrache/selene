package app.selene.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selene.data.LogRepository
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class EntryKind { PERIOD, MOOD, EVENT }

data class EntryUiState(
    val isEdit: Boolean,
    val today: LocalDate,
    val date: LocalDate,
    val kind: EntryKind,
    val flow: Int = DEFAULT_FLOW,
    val pain: Int = 0,
    val fatigue: Int = 0,
    val mood: Mood? = null,
    val title: String = "",
    val eventId: String? = null,
    val existingPeriod: Boolean = false,
    val existingMood: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: Boolean = false,
) {
    val titleValid: Boolean get() = title.trim().length in 1..NotableEvent.TITLE_MAX_LENGTH

    val canSave: Boolean
        get() = !saving && !date.isAfter(today) && when (kind) {
            EntryKind.PERIOD, EntryKind.MOOD -> mood != null
            EntryKind.EVENT -> titleValid
        }

    val replacesExisting: Boolean
        get() = !isEdit && ((kind == EntryKind.PERIOD && existingPeriod) || (kind == EntryKind.MOOD && existingMood))

    companion object {
        const val DEFAULT_FLOW = 2
    }
}

/**
 * Add / edit sheet. In edit mode the date and the type are fixed; in add mode, picking a date
 * that already has data pre-fills the fields so the user edits rather than overwrites blindly.
 */
class EntryViewModel(
    private val repository: LogRepository,
    today: LocalDate,
    initialDate: LocalDate?,
    initialKind: EntryKind?,
    eventId: String?,
    isEdit: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(
        EntryUiState(
            isEdit = isEdit,
            today = today,
            date = (initialDate ?: today).coerceAtMost(today, isEdit),
            kind = initialKind ?: EntryKind.PERIOD,
            eventId = eventId,
        ),
    )
    val state: StateFlow<EntryUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun setDate(date: LocalDate) {
        if (_state.value.isEdit || date.isAfter(_state.value.today)) return
        _state.update { it.copy(date = date) }
        load()
    }

    fun setKind(kind: EntryKind) {
        if (_state.value.isEdit) return
        _state.update { it.copy(kind = kind) }
        load()
    }

    fun setFlow(flow: Int) = _state.update { it.copy(flow = flow) }
    fun setPain(level: Int) = _state.update { it.copy(pain = level) }
    fun setFatigue(level: Int) = _state.update { it.copy(fatigue = level) }
    fun setMood(mood: Mood) = _state.update { it.copy(mood = mood) }
    fun setTitle(title: String) = _state.update { it.copy(title = title.take(NotableEvent.TITLE_MAX_LENGTH)) }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            val day = repository.getDay(s.date)
            _state.update { current ->
                var next = current.copy(existingPeriod = day.period != null, existingMood = day.mood != null)
                when (current.kind) {
                    EntryKind.PERIOD -> next = next.copy(
                        flow = day.period?.flow ?: EntryUiState.DEFAULT_FLOW,
                        pain = day.period?.pain ?: 0,
                        fatigue = day.period?.fatigue ?: 0,
                        mood = day.mood,
                    )
                    EntryKind.MOOD -> next = next.copy(mood = day.mood)
                    EntryKind.EVENT -> {
                        val event = current.eventId?.let { id -> day.events.firstOrNull { it.id == id } }
                        if (event != null) next = next.copy(title = event.title)
                    }
                }
                next
            }
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, error = false) }
        viewModelScope.launch {
            val result = runCatching {
                when (s.kind) {
                    EntryKind.PERIOD -> repository.savePeriodDay(PeriodLog(s.date, s.flow, s.pain, s.fatigue), s.mood!!)
                    EntryKind.MOOD -> repository.saveMood(s.date, s.mood!!)
                    EntryKind.EVENT ->
                        if (s.eventId != null) repository.updateEvent(s.eventId, s.title) else repository.addEvent(s.date, s.title)
                }
            }
            _state.update { it.copy(saving = false, done = result.isSuccess, error = result.isFailure) }
        }
    }
}

private fun LocalDate.coerceAtMost(max: LocalDate, allowFuture: Boolean) = if (!allowFuture && isAfter(max)) max else this
