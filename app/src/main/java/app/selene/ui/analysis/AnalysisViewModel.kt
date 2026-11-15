package app.selene.ui.analysis

import androidx.lifecycle.ViewModel
import app.selene.domain.model.Prediction
import kotlinx.coroutines.flow.StateFlow

class AnalysisViewModel(val prediction: StateFlow<Prediction?>) : ViewModel()
