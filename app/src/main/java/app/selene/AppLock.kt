package app.selene

import app.selene.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Optional biometric lock: locked at start and whenever the app goes to the background. */
class AppLock(settings: SettingsRepository, scope: CoroutineScope) {
    enum class State { CHECKING, LOCKED, UNLOCKED }

    private val enabled: StateFlow<Boolean?> = settings.settings.map { it.biometricLock }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(State.CHECKING)
    val state: StateFlow<State> = _state

    /** Set while the app opens a system screen of its own (file picker…): don't lock on return. */
    @Volatile
    private var skipNextBackground = false

    init {
        scope.launch {
            val on = enabled.filterNotNull().first()
            if (_state.value == State.CHECKING) _state.value = if (on) State.LOCKED else State.UNLOCKED
        }
    }

    fun onBackground() {
        if (skipNextBackground) {
            skipNextBackground = false
            return
        }
        if (enabled.value == true) _state.value = State.LOCKED
    }

    fun allowExternalScreen() {
        skipNextBackground = true
    }

    fun unlock() {
        _state.value = State.UNLOCKED
    }
}
