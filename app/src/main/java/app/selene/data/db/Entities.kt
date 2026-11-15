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
    val fatigue: Int,
)

@Entity(tableName = "mood_log")
data class MoodLogEntity(
    @PrimaryKey val date: LocalDate,
    val mood: Int,
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
