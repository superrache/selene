package app.selene.ui.cycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selene.domain.model.Prediction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class CycleUiState(val today: LocalDate, val prediction: Prediction?) {
    val loading: Boolean get() = prediction == null
}

class CycleViewModel(prediction: StateFlow<Prediction?>, today: StateFlow<LocalDate>) : ViewModel() {
    val state: StateFlow<CycleUiState> = combine(prediction, today) { p, t -> CycleUiState(t, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleUiState(today.value, prediction.value))
}
