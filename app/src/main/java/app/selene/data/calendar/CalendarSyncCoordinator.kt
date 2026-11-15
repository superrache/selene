package app.selene.data.calendar

import app.selene.data.AppSettings
import app.selene.data.SettingsRepository
import app.selene.domain.model.DateRange
import app.selene.domain.model.Prediction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the single calendar event on the next predicted period, whenever the data,
 * the date or the settings change. Never duplicates the event; recreates it if it was deleted.
 * If the permission was revoked, the setting is silently turned off.
 */
class CalendarSyncCoordinator(
    private val sync: CalendarEventGateway,
    private val settings: SettingsRepository,
    private val prediction: Flow<Prediction?>,
    private val defaultTitle: String,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val ticks = MutableStateFlow(0)
    private var started = false

    private data class Config(val enabled: Boolean, val calendarId: Long?, val title: String?)

    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) {
            combine(
                prediction.filterNotNull().map { it.nextPeriodRange }.distinctUntilChanged(),
                settings.settings.map { Config(it.calendarSyncEnabled, it.calendarId, it.calendarEventTitle) }.distinctUntilChanged(),
                ticks,
            ) { window, config, _ -> window to config }
                .collectLatest { (window, config) -> if (config.enabled) syncNow(window) }
        }
    }

    /** Re-checks the event (e.g. deleted from the calendar app, or the window is over). */
    fun refresh() {
        ticks.value++
    }

    private suspend fun syncNow(window: DateRange?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val s: AppSettings = settings.current()
            if (!s.calendarSyncEnabled) return@withLock
            val calendarId = s.calendarId
            if (!sync.hasPermission() || calendarId == null) {
                settings.setCalendarSync(false, null)
                return@withLock
            }
            runCatching {
                if (window == null) {
                    s.calendarEventId?.let(sync::deleteEvent)
                    settings.setCalendarEventId(null)
                } else {
                    val id = sync.upsertEvent(calendarId, s.calendarEventId, window, s.calendarEventTitle ?: defaultTitle)
                    if (id != s.calendarEventId) settings.setCalendarEventId(id)
                }
            }.onFailure {
                if (it is SecurityException) settings.setCalendarSync(false, null)
            }
        }
    }

    /** Turns sync off and deletes the event. */
    suspend fun disable() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val s = settings.current()
            if (sync.hasPermission()) s.calendarEventId?.let { id -> runCatching { sync.deleteEvent(id) } }
            settings.setCalendarSync(false, null)
        }
    }

    suspend fun enable(calendarId: Long) {
        settings.setCalendarSync(true, calendarId)
        refresh()
    }
}
