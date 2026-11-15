package app.selene.data.json

import app.selene.domain.model.DayLog
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object JsonExport {
    private val json = Json {
        prettyPrint = true
        explicitNulls = false
    }

    /** Suggested file name: selene-YYYY-MM-DD.json */
    fun fileName(today: LocalDate): String = "selene-$today.json"

    /** One object per day with data, sorted by date. Predictions and settings are not exported. */
    fun encode(days: List<DayLog>, exportedAt: OffsetDateTime): String {
        val file = ExportFile(
            format = ExportFormat.FORMAT,
            version = ExportFormat.CURRENT_VERSION,
            exportedAt = exportedAt.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            days = days.filterNot { it.isEmpty }.sortedBy { it.date }.map { day ->
                ExportDay(
                    date = day.date.toString(),
                    period = day.period?.let { ExportPeriod(it.flow, it.pain, it.fatigue) },
                    mood = day.mood?.value,
                    events = day.events.takeIf { it.isNotEmpty() }?.sortedBy { it.title }?.map { ExportEvent(it.id, it.title) },
                )
            },
        )
        return json.encodeToString(ExportFile.serializer(), file)
    }
}
