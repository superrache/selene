package app.selene.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Dates are stored as ISO yyyy-MM-dd text (see [Converters]). */
@Entity(tableName = "period_log")
data class PeriodLogEntity(
    @PrimaryKey val date: LocalDate,
    val flow: Int,
    val pain: Int,
)

/**
 * Symptoms of the day. [moods] and [symptoms] are comma-separated stable codes
 * (see [app.selene.domain.model.Mood.code], [app.selene.domain.model.Symptom.code]), empty when none.
 */
@Entity(tableName = "symptom_log")
data class SymptomLogEntity(
    @PrimaryKey val date: LocalDate,
    val moods: String,
    val symptoms: String,
    val irritability: Int?,
    val fatigue: Int?,
    val note: String?,
)

@Entity(tableName = "notable_event", indices = [Index("date")])
data class NotableEventEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val title: String,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value") val value: String,
)
