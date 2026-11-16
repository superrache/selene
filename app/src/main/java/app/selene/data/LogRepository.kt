package app.selene.data

import androidx.room.withTransaction
import app.selene.data.db.LogDao
import app.selene.data.db.NotableEventEntity
import app.selene.data.db.PeriodLogEntity
import app.selene.data.db.SeleneDatabase
import app.selene.data.db.SymptomLogEntity
import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.Symptom
import app.selene.domain.model.SymptomLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/** The only entry point to the logs. */
class LogRepository(private val db: SeleneDatabase) {
    private val dao: LogDao = db.logDao()

    enum class ImportMode { MERGE, REPLACE_ALL }

    /** All logged days, sorted by date. */
    val days: Flow<List<DayLog>> =
        combine(dao.observePeriods(), dao.observeSymptoms(), dao.observeEvents(), ::assemble)

    fun day(date: LocalDate): Flow<DayLog> =
        days.map { all -> all.firstOrNull { it.date == date } ?: DayLog(date) }.distinctUntilChanged()

    suspend fun snapshot(): List<DayLog> = db.withTransaction {
        assemble(dao.allPeriods(), dao.allSymptoms(), dao.allEvents())
    }

    suspend fun getDay(date: LocalDate): DayLog = db.withTransaction {
        DayLog(
            date = date,
            period = dao.period(date)?.toDomain(),
            symptoms = dao.symptoms(date)?.toDomain(),
            events = dao.events(date).map { it.toDomain() },
        )
    }

    // --- Period ----------------------------------------------------------------------------------

    suspend fun savePeriod(period: PeriodLog) {
        dao.upsertPeriod(period.toEntity())
    }

    /** Deletes a period day. Returns what was deleted, to be passed to [restore] for "undo". */
    suspend fun deletePeriod(date: LocalDate): DayLog = db.withTransaction {
        val period = dao.period(date)?.toDomain()
        dao.deletePeriod(date)
        DayLog(date, period = period)
    }

    // --- Symptoms of the day ---------------------------------------------------------------------

    /** Replaces the symptoms of the day; an empty record deletes them. */
    suspend fun saveSymptoms(symptoms: SymptomLog) {
        val normalized = symptoms.copy(note = SymptomLog.normalizeNote(symptoms.note))
        if (normalized.isEmpty) dao.deleteSymptoms(symptoms.date) else dao.upsertSymptoms(normalized.toEntity())
    }

    suspend fun deleteSymptoms(date: LocalDate): DayLog = db.withTransaction {
        val before = dao.symptoms(date)?.toDomain()
        dao.deleteSymptoms(date)
        DayLog(date, symptoms = before)
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

    /** Re-inserts what a delete returned. */
    suspend fun restore(deleted: DayLog) {
        db.withTransaction {
            deleted.symptoms?.let { dao.upsertSymptoms(it.toEntity()) }
            deleted.period?.let { dao.upsertPeriod(it.toEntity()) }
            deleted.events.forEach { dao.upsertEvent(it.toEntity()) }
        }
    }

    /**
     * Writes imported days in one transaction. MERGE: for each day of [days], the file wins
     * (the day is replaced as a whole). REPLACE_ALL: everything is deleted first.
     * Callers must validate first (see [app.selene.data.json.JsonImport]).
     */
    suspend fun import(days: List<DayLog>, mode: ImportMode) {
        require(days.map { it.date }.toSet().size == days.size) { "duplicate days" }
        db.withTransaction {
            if (mode == ImportMode.REPLACE_ALL) clearLogs()
            for (day in days) {
                if (mode == ImportMode.MERGE) {
                    dao.deletePeriod(day.date)
                    dao.deleteSymptoms(day.date)
                    dao.deleteEventsOn(day.date)
                }
            }
            dao.upsertSymptomLogs(days.mapNotNull { it.symptoms?.toEntity() })
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
        dao.deleteAllSymptoms()
    }

    companion object {
        fun normalizeTitle(title: String): String {
            val t = title.trim()
            require(t.isNotEmpty() && t.length <= NotableEvent.TITLE_MAX_LENGTH) { "invalid title" }
            return t
        }

        internal fun assemble(
            periods: List<PeriodLogEntity>,
            symptoms: List<SymptomLogEntity>,
            events: List<NotableEventEntity>,
        ): List<DayLog> {
            val periodBy = periods.associateBy { it.date }
            val symptomsBy = symptoms.associateBy { it.date }
            val eventsBy = events.groupBy { it.date }
            val dates = (periodBy.keys + symptomsBy.keys + eventsBy.keys).toSortedSet()
            return dates.map { date ->
                DayLog(
                    date = date,
                    period = periodBy[date]?.let { runCatching { it.toDomain() }.getOrNull() },
                    symptoms = symptomsBy[date]?.toDomain(),
                    events = eventsBy[date].orEmpty().mapNotNull { runCatching { it.toDomain() }.getOrNull() },
                )
            }
        }
    }
}

private fun PeriodLogEntity.toDomain() = PeriodLog(date, flow, pain)
private fun PeriodLog.toEntity() = PeriodLogEntity(date, flow, pain)
private fun NotableEventEntity.toDomain() = NotableEvent(id, date, title)
private fun NotableEvent.toEntity() = NotableEventEntity(id, date, title)

private fun String.codes(): List<String> = split(',').filter { it.isNotEmpty() }

/** Lenient read: unknown codes and out-of-range values are dropped rather than crashing. Null when nothing is left. */
private fun SymptomLogEntity.toDomain(): SymptomLog? = runCatching {
    SymptomLog(
        date = date,
        moods = moods.codes().mapNotNull(Mood::fromCode).toSet(),
        symptoms = symptoms.codes().mapNotNull(Symptom::fromCode).toSet(),
        irritability = irritability?.takeIf { it in SymptomLog.LEVEL_RANGE },
        fatigue = fatigue?.takeIf { it in SymptomLog.LEVEL_RANGE },
        note = SymptomLog.normalizeNote(note),
    )
}.getOrNull()?.takeIf { !it.isEmpty }

private fun SymptomLog.toEntity() = SymptomLogEntity(
    date = date,
    // Stored in declaration order, so equal sets give equal rows.
    moods = Mood.entries.filter { it in moods }.joinToString(",") { it.code },
    symptoms = Symptom.entries.filter { it in symptoms }.joinToString(",") { it.code },
    irritability = irritability,
    fatigue = fatigue,
    note = note,
)
