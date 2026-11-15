package app.selene.ui.settings

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.selene.AppContainer
import app.selene.R
import app.selene.data.AppSettings
import app.selene.data.LogRepository
import app.selene.data.calendar.CalendarInfo
import app.selene.data.json.ImportError
import app.selene.data.json.ImportResult
import app.selene.data.json.JsonExport
import app.selene.data.json.JsonImport
import app.selene.domain.model.DayLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.OffsetDateTime

data class PendingImport(val fileName: String, val days: List<DayLog>)

data class SettingsDialogs(
    val calendars: List<CalendarInfo>? = null,
    val pendingImport: PendingImport? = null,
    val importError: ImportFailure? = null,
)

/** Import failure: either a validation error or an I/O problem. */
sealed interface ImportFailure {
    data class Invalid(val error: ImportError) : ImportFailure
    data object Unreadable : ImportFailure
    data object TooLarge : ImportFailure
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val logs = container.logRepository
    private val settingsRepo = container.settingsRepository
    private val calendarSync = container.calendarSync
    private val coordinator = container.calendarCoordinator
    private val resolver get() = container.appContext.contentResolver

    val settings: StateFlow<AppSettings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _dialogs = MutableStateFlow(SettingsDialogs())
    val dialogs: StateFlow<SettingsDialogs> = _dialogs.asStateFlow()

    private val _calendarName = MutableStateFlow<String?>(null)
    val calendarName: StateFlow<String?> = _calendarName.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    val exportFileName: String get() = JsonExport.fileName(container.today.value)

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { s -> refreshCalendarName(s) }
        }
    }

    private suspend fun refreshCalendarName(s: AppSettings) {
        val id = s.calendarId
        _calendarName.value = if (s.calendarSyncEnabled && id != null && calendarSync.hasPermission()) {
            withContext(Dispatchers.IO) { runCatching { calendarSync.calendar(id)?.displayName }.getOrNull() }
        } else {
            null
        }
    }

    private fun message(@StringRes id: Int) {
        _messages.trySend(id)
    }

    // --- Export / import ---------------------------------------------------------------------

    fun export(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val text = JsonExport.encode(logs.snapshot(), OffsetDateTime.now())
                resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException()
            }.isSuccess
        }
        message(if (ok) R.string.settings_export_done else R.string.settings_export_failed)
    }

    fun readImport(uri: Uri, displayName: String) = viewModelScope.launch {
        val failure: ImportFailure?
        val result = withContext(Dispatchers.IO) {
            runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readNBytesCompat(JsonImport.MAX_BYTES + 1)
                    if (bytes.size > JsonImport.MAX_BYTES) null else JsonImport.parse(bytes.toString(Charsets.UTF_8))
                } ?: throw IOException()
            }
        }
        failure = when {
            result.isFailure -> ImportFailure.Unreadable
            result.getOrNull() == null -> ImportFailure.TooLarge
            else -> (result.getOrNull() as? ImportResult.Failure)?.let { ImportFailure.Invalid(it.error) }
        }
        val success = result.getOrNull() as? ImportResult.Success
        _dialogs.update {
            it.copy(
                importError = failure,
                pendingImport = success?.let { s -> PendingImport(displayName, s.days) },
            )
        }
    }

    fun confirmImport(mode: LogRepository.ImportMode) {
        val pending = _dialogs.value.pendingImport ?: return
        _dialogs.update { it.copy(pendingImport = null) }
        viewModelScope.launch {
            val ok = runCatching { logs.import(pending.days, mode) }.isSuccess
            message(if (ok) R.string.settings_import_done else R.string.import_error_unreadable)
        }
    }

    fun dismissImport() = _dialogs.update { it.copy(pendingImport = null, importError = null) }

    // --- Calendar ------------------------------------------------------------------------------

    fun hasCalendarPermission(): Boolean = calendarSync.hasPermission()

    fun openCalendarChooser() = viewModelScope.launch {
        val list = withContext(Dispatchers.IO) { runCatching { calendarSync.writableCalendars() }.getOrDefault(emptyList()) }
        _dialogs.update { it.copy(calendars = list) }
    }

    fun dismissCalendarChooser() = _dialogs.update { it.copy(calendars = null) }

    fun chooseCalendar(calendar: CalendarInfo?) {
        _dialogs.update { it.copy(calendars = null) }
        viewModelScope.launch {
            val id = calendar?.id ?: withContext(Dispatchers.IO) {
                runCatching {
                    calendarSync.getOrCreateLocalCalendar(container.appContext.getString(R.string.settings_calendar_local_name))
                }.getOrNull()
            } ?: return@launch
            coordinator.enable(id)
        }
    }

    fun permissionDenied() = message(R.string.settings_calendar_permission_denied)

    fun disableCalendar() = viewModelScope.launch { coordinator.disable() }

    fun setEventTitle(title: String) = viewModelScope.launch {
        settingsRepo.setCalendarEventTitle(title.take(80))
        coordinator.refresh()
    }

    // --- Security ------------------------------------------------------------------------------

    fun setBiometricLock(enabled: Boolean) = viewModelScope.launch { settingsRepo.setBiometricLock(enabled) }

    fun eraseAll() = viewModelScope.launch {
        coordinator.disable()
        logs.deleteAll()
        settingsRepo.clear()
        message(R.string.settings_erase_done)
    }
}

/** InputStream.readNBytes exists only from API 33. */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buffer, 0, minOf(buffer.size, max - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}
