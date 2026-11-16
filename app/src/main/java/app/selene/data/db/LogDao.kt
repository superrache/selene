package app.selene.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface LogDao {
    // --- Observation ---
    @Query("SELECT * FROM period_log ORDER BY date")
    fun observePeriods(): Flow<List<PeriodLogEntity>>

    @Query("SELECT * FROM symptom_log ORDER BY date")
    fun observeSymptoms(): Flow<List<SymptomLogEntity>>

    @Query("SELECT * FROM notable_event ORDER BY date, title")
    fun observeEvents(): Flow<List<NotableEventEntity>>

    // --- Snapshots ---
    @Query("SELECT * FROM period_log ORDER BY date")
    suspend fun allPeriods(): List<PeriodLogEntity>

    @Query("SELECT * FROM symptom_log ORDER BY date")
    suspend fun allSymptoms(): List<SymptomLogEntity>

    @Query("SELECT * FROM notable_event ORDER BY date, title")
    suspend fun allEvents(): List<NotableEventEntity>

    @Query("SELECT * FROM period_log WHERE date = :date")
    suspend fun period(date: LocalDate): PeriodLogEntity?

    @Query("SELECT * FROM symptom_log WHERE date = :date")
    suspend fun symptoms(date: LocalDate): SymptomLogEntity?

    @Query("SELECT * FROM notable_event WHERE id = :id")
    suspend fun event(id: String): NotableEventEntity?

    @Query("SELECT * FROM notable_event WHERE date = :date ORDER BY title")
    suspend fun events(date: LocalDate): List<NotableEventEntity>

    // --- Writes ---
    @Upsert
    suspend fun upsertPeriod(entity: PeriodLogEntity)

    @Upsert
    suspend fun upsertPeriods(entities: List<PeriodLogEntity>)

    @Upsert
    suspend fun upsertSymptoms(entity: SymptomLogEntity)

    @Upsert
    suspend fun upsertSymptomLogs(entities: List<SymptomLogEntity>)

    @Upsert
    suspend fun upsertEvent(entity: NotableEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(entities: List<NotableEventEntity>)

    @Query("DELETE FROM period_log WHERE date = :date")
    suspend fun deletePeriod(date: LocalDate)

    @Query("DELETE FROM symptom_log WHERE date = :date")
    suspend fun deleteSymptoms(date: LocalDate)

    @Query("DELETE FROM notable_event WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("DELETE FROM notable_event WHERE date = :date")
    suspend fun deleteEventsOn(date: LocalDate)

    @Query("DELETE FROM period_log")
    suspend fun deleteAllPeriods()

    @Query("DELETE FROM symptom_log")
    suspend fun deleteAllSymptoms()

    @Query("DELETE FROM notable_event")
    suspend fun deleteAllEvents()
}
