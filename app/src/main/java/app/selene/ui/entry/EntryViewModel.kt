package app.selene.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selene.data.LogRepository
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.Symptom
import app.selene.domain.model.SymptomLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class EntryKind { PERIOD, SYMPTOMS, EVENT }

data class EntryUiState(
    val isEdit: Boolean,
    val today: LocalDate,
    val date: LocalDate,
    val kind: EntryKind,
    val flow: Int = DEFAULT_FLOW,
    val pain: Int = 0,
    val moods: Set<Mood> = emptySet(),
    val symptoms: Set<Symptom> = emptySet(),
    val irritability: Int? = null,
    val fatigue: Int? = null,
    val note: String = "",
    val title: String = "",
    val eventId: String? = null,
    val existingPeriod: Boolean = false,
    val existingSymptoms: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    val error: Boolean = false,
) {
    val titleValid: Boolean get() = title.trim().length in 1..NotableEvent.TITLE_MAX_LENGTH

    val canSave: Boolean
        get() = !saving && !date.isAfter(today) && when (kind) {
            EntryKind.PERIOD -> true
            // An empty sheet is only useful to clear symptoms that exist.
            EntryKind.SYMPTOMS -> !symptomLog.isEmpty || existingSymptoms
            EntryKind.EVENT -> titleValid
        }

    val symptomLog: SymptomLog
        get() = SymptomLog(date, moods, symptoms, irritability, fatigue, SymptomLog.normalizeNote(note))

    /** Only for periods: the symptom sheet is pre-filled with the day's symptoms, so it edits them. */
    val replacesExisting: Boolean
        get() = !isEdit && kind == EntryKind.PERIOD && existingPeriod

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
    fun toggleMood(mood: Mood) = _state.update { it.copy(moods = it.moods.toggle(mood)) }
    fun toggleSymptom(symptom: Symptom) = _state.update { it.copy(symptoms = it.symptoms.toggle(symptom)) }
    fun setIrritability(level: Int?) = _state.update { it.copy(irritability = level) }
    fun setFatigue(level: Int?) = _state.update { it.copy(fatigue = level) }
    fun setNote(note: String) = _state.update { it.copy(note = note.take(SymptomLog.NOTE_MAX_LENGTH)) }
    fun setTitle(title: String) = _state.update { it.copy(title = title.take(NotableEvent.TITLE_MAX_LENGTH)) }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            val day = repository.getDay(s.date)
            _state.update { current ->
                var next = current.copy(existingPeriod = day.period != null, existingSymptoms = day.symptoms != null)
                when (current.kind) {
                    EntryKind.PERIOD -> next = next.copy(
                        flow = day.period?.flow ?: EntryUiState.DEFAULT_FLOW,
                        pain = day.period?.pain ?: 0,
                    )
                    EntryKind.SYMPTOMS -> {
                        val s = day.symptoms
                        next = next.copy(
                            moods = s?.moods.orEmpty(),
                            symptoms = s?.symptoms.orEmpty(),
                            irritability = s?.irritability,
                            fatigue = s?.fatigue,
                            note = s?.note.orEmpty(),
                        )
                    }
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
                    EntryKind.PERIOD -> repository.savePeriod(PeriodLog(s.date, s.flow, s.pain))
                    EntryKind.SYMPTOMS -> repository.saveSymptoms(s.symptomLog)
                    EntryKind.EVENT ->
                        if (s.eventId != null) repository.updateEvent(s.eventId, s.title) else repository.addEvent(s.date, s.title)
                }
            }
            _state.update { it.copy(saving = false, done = result.isSuccess, error = result.isFailure) }
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

private fun LocalDate.coerceAtMost(max: LocalDate, allowFuture: Boolean) = if (!allowFuture && isAfter(max)) max else this
