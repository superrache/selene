package app.selene.data.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selene.data.SettingsRepository
import app.selene.data.inMemoryDatabase
import app.selene.domain.model.DateRange
import app.selene.domain.model.Prediction
import app.selene.domain.Predictor
import app.selene.domain.history
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class CalendarSyncCoordinatorTest {
    /** In-memory calendar: event id → (calendar, window, title). */
    private class FakeCalendar : CalendarEventGateway {
        var permission = true
        var nextId = 100L
        val events = mutableMapOf<Long, Triple<Long, DateRange, String>>()

        override fun hasPermission() = permission
        override fun upsertEvent(calendarId: Long, existingEventId: Long?, window: DateRange, title: String): Long {
            if (existingEventId != null && events[existingEventId]?.first == calendarId) {
                events[existingEventId] = Triple(calendarId, window, title)
                return existingEventId
            }
            existingEventId?.let(events::remove)
            val id = nextId++
            events[id] = Triple(calendarId, window, title)
            return id
        }
        override fun deleteEvent(eventId: Long) {
            events.remove(eventId)
        }
    }

    private val db = inMemoryDatabase()
    private val settings = SettingsRepository(db.settingsDao())
    private val calendar = FakeCalendar()
    private val today = LocalDate.of(2026, 11, 15)
    private val prediction = MutableStateFlow<Prediction?>(null)
    private val scope = CoroutineScope(SupervisorJob())
    private val coordinator = CalendarSyncCoordinator(calendar, settings, prediction, "Sélène", scope)

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun predictionWithPeriod(start: LocalDate): Prediction {
        val base = Predictor.predict(emptyList(), today)
        return base.copy(upcomingPeriods = listOf(DateRange(start, start.plusDays(4))))
    }

    private fun eventually(check: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!check()) delay(20) }
    }

    @Test
    fun keepsASingleEventOnTheNextPredictedPeriod() = runBlocking {
        prediction.value = predictionWithPeriod(today.plusDays(10))
        coordinator.start()
        coordinator.enable(calendarId = 1L)
        eventually { calendar.events.size == 1 && settingsEventId() != null }
        val id = settingsEventId()!!

        prediction.value = predictionWithPeriod(today.plusDays(12))
        eventually { calendar.events[id]?.second == DateRange(today.plusDays(12), today.plusDays(16)) }
        assertEquals(1, calendar.events.size)
        assertEquals("Sélène", calendar.events[id]!!.third)

        settings.setCalendarEventTitle("Note")
        eventually { calendar.events[id]?.third == "Note" }
        assertEquals(1, calendar.events.size)
    }

    @Test
    fun eventCoversThePredictedPeriodFromTheRealPredictor() = runBlocking {
        // Regular 28-day cycles with 5-day periods; last period started on 2026-10-26.
        val logs = history { listOf(0L, 28L, 56L).forEach { period(LocalDate.of(2026, 8, 31).plusDays(it)) } }
        prediction.value = Predictor.predict(logs, today)
        coordinator.start()
        coordinator.enable(calendarId = 1L)
        eventually { calendar.events.size == 1 }
        assertEquals(DateRange(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 27)), calendar.events.values.single().second)
    }

    @Test
    fun recreatesTheEventWhenDeletedFromTheCalendarApp() = runBlocking {
        prediction.value = predictionWithPeriod(today.plusDays(10))
        coordinator.start()
        coordinator.enable(calendarId = 1L)
        eventually { settingsEventId() != null }
        calendar.events.clear()
        coordinator.refresh()
        eventually { calendar.events.size == 1 }
    }

    @Test
    fun revokedPermissionSilentlyDisablesTheSetting() = runBlocking {
        prediction.value = predictionWithPeriod(today.plusDays(10))
        coordinator.start()
        calendar.permission = false
        coordinator.enable(calendarId = 1L)
        eventually { runBlocking { !settings.current().calendarSyncEnabled } }
        assertEquals(0, calendar.events.size)
    }

    @Test
    fun disablingDeletesTheEvent() = runBlocking {
        prediction.value = predictionWithPeriod(today.plusDays(10))
        coordinator.start()
        coordinator.enable(calendarId = 1L)
        eventually { calendar.events.size == 1 }
        coordinator.disable()
        assertEquals(0, calendar.events.size)
        assertFalse(settings.current().calendarSyncEnabled)
        assertNull(settingsEventId())
    }

    private fun settingsEventId(): Long? = runBlocking { settings.current().calendarEventId }
}
