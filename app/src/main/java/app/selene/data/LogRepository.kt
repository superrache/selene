package app.selene.data

import androidx.room.withTransaction
import app.selene.data.db.LogDao
import app.selene.data.db.MoodLogEntity
import app.selene.data.db.NotableEventEntity
import app.selene.data.db.PeriodLogEntity
import app.selene.data.db.SeleneDatabase
import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/** Thrown when a write would leave a period day without a mood. */
class MoodInvariantException(message: String) : IllegalStateException(message)

/**
 * The only entry point to the logs. Enforces the central invariant
 * "a period day always has a mood" for every write, whatever the caller.
 */
class LogRepository(private val db: SeleneDatabase) {
    private val dao: LogDao = db.logDao()

    enum class ImportMode { MERGE, REPLACE_ALL }

    /** All logged days, sorted by date. */
    val days: Flow<List<DayLog>> =
        combine(dao.observePeriods(), dao.observeMoods(), dao.observeEvents(), ::assemble)

    fun day(date: LocalDate): Flow<DayLog> =
        days.map { all -> all.firstOrNull { it.date == date } ?: DayLog(date) }.distinctUntilChanged()

    suspend fun snapshot(): List<DayLog> = db.withTransaction {
        assemble(dao.allPeriods(), dao.allMoods(), dao.allEvents())
    }

    suspend fun getDay(date: LocalDate): DayLog = db.withTransaction {
        DayLog(
            date = date,
            period = dao.period(date)?.toDomain(),
            mood = dao.mood(date)?.let { Mood.fromValue(it.mood) },
            events = dao.events(date).map { it.toDomain() },
        )
    }

    // --- Period & mood ---------------------------------------------------------------------------

    /** Writes a period day and its (mandatory) mood in one transaction. */
    suspend fun savePeriodDay(period: PeriodLog, mood: Mood) {
        db.withTransaction {
            dao.upsertPeriod(period.toEntity())
            dao.upsertMood(MoodLogEntity(period.date, mood.value))
        }
    }

    suspend fun saveMood(date: LocalDate, mood: Mood) {
        dao.upsertMood(MoodLogEntity(date, mood.value))
    }

    /** Deletes a mood alone. Refused on a period day: delete the period day instead. */
    suspend fun deleteMood(date: LocalDate): DayLog = db.withTransaction {
        if (dao.period(date) != null) throw MoodInvariantException("a period day must keep its mood")
        val before = dao.mood(date)?.let { Mood.fromValue(it.mood) }
        dao.deleteMood(date)
        DayLog(date, mood = before)
    }

    /**
     * Deletes a period day, and its mood when [alsoDeleteMood] (default: yes).
     * Returns what was deleted, to be passed to [restore] for "undo".
     */
    suspend fun deletePeriodDay(date: LocalDate, alsoDeleteMood: Boolean = true): DayLog = db.withTransaction {
        val period = dao.period(date)?.toDomain()
        val mood = dao.mood(date)?.let { Mood.fromValue(it.mood) }
        dao.deletePeriod(date)
        if (alsoDeleteMood) dao.deleteMood(date)
        DayLog(date, period = period, mood = if (alsoDeleteMood) mood else null)
    }

    // --- Notable events --------------------------------------------------------------------------

    suspend fun addEvent(date: LocalDate, title: String): NotableEvent {
        val event = NotableEvent(UUID.randomUUID().toString(), date, normalizeTitle(title))
        dao.upsertEvent(event.toEntity())
        return event
    }

    suspend fun updateEvent(id: String, title: String) {
        val existing = dao.event(id) ?: return
        dao.upsertEvent(existing.copy(title = normalizeTitle(title)))
    }

    suspend fun deleteEvent(id: String): DayLog? = db.withTransaction {
        val existing = dao.event(id) ?: return@withTransaction null
        dao.deleteEvent(id)
        DayLog(existing.date, events = listOf(existing.toDomain()))
    }

    // --- Undo / import ---------------------------------------------------------------------------

    /** Re-inserts what a delete returned. Keeps the invariant: a period is only restored with a mood. */
    suspend fun restore(deleted: DayLog) {
        db.withTransaction {
            deleted.mood?.let { dao.upsertMood(MoodLogEntity(deleted.date, it.value)) }
            deleted.period?.let {
                if (dao.mood(deleted.date) == null) throw MoodInvariantException("cannot restore a period without mood")
                dao.upsertPeriod(it.toEntity())
            }
            deleted.events.forEach { dao.upsertEvent(it.toEntity()) }
        }
    }

    /**
     * Writes imported days in one transaction. MERGE: for each day of [days], the file wins
     * (the day is replaced as a whole). REPLACE_ALL: everything is deleted first.
     * Callers must validate first; the invariant is checked again here before writing anything.
     */
    suspend fun import(days: List<DayLog>, mode: ImportMode) {
        val broken = days.firstOrNull { !it.respectsMoodInvariant }
        if (broken != null) throw MoodInvariantException("period day without mood: ${broken.date}")
        require(days.map { it.date }.toSet().size == days.size) { "duplicate days" }
        db.withTransaction {
            if (mode == ImportMode.REPLACE_ALL) clearLogs()
            for (day in days) {
                if (mode == ImportMode.MERGE) {
                    dao.deletePeriod(day.date)
                    dao.deleteMood(day.date)
                    dao.deleteEventsOn(day.date)
                }
            }
            dao.upsertMoods(days.mapNotNull { d -> d.mood?.let { MoodLogEntity(d.date, it.value) } })
            dao.upsertPeriods(days.mapNotNull { it.period?.toEntity() })
            dao.upsertEvents(days.flatMap { d -> d.events.map { it.toEntity() } })
        }
    }

    suspend fun deleteAll() {
        db.withTransaction { clearLogs() }
    }

    private suspend fun clearLogs() {
        dao.deleteAllEvents()
        dao.deleteAllPeriods()
        dao.deleteAllMoods()
    }

    companion object {
        fun normalizeTitle(title: String): String {
            val t = title.trim()
            require(t.isNotEmpty() && t.length <= NotableEvent.TITLE_MAX_LENGTH) { "invalid title" }
            return t
        }

        internal fun assemble(
            periods: List<PeriodLogEntity>,
            moods: List<MoodLogEntity>,
            events: List<NotableEventEntity>,
        ): List<DayLog> {
            val periodBy = periods.associateBy { it.date }
            val moodBy = moods.associateBy { it.date }
            val eventsBy = events.groupBy { it.date }
            val dates = (periodBy.keys + moodBy.keys + eventsBy.keys).toSortedSet()
            return dates.map { date ->
                DayLog(
                    date = date,
                    period = periodBy[date]?.let { runCatching { it.toDomain() }.getOrNull() },
                    mood = moodBy[date]?.let { Mood.fromValue(it.mood) },
                    events = eventsBy[date].orEmpty().mapNotNull { runCatching { it.toDomain() }.getOrNull() },
                )
            }
        }
    }
}

private fun PeriodLogEntity.toDomain() = PeriodLog(date, flow, pain, fatigue)
private fun PeriodLog.toEntity() = PeriodLogEntity(date, flow, pain, fatigue)
private fun NotableEventEntity.toDomain() = NotableEvent(id, date, title)
private fun NotableEvent.toEntity() = NotableEventEntity(id, date, title)
