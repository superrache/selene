package app.selene.data.json

import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.Symptom
import app.selene.domain.model.SymptomLog
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Why a file was refused. Mapped to precise French messages by the UI. */
sealed interface ImportError {
    data object NotJson : ImportError
    data object NotSeleneExport : ImportError
    data object MissingVersion : ImportError
    data class UnsupportedVersion(val found: Int, val supported: Int) : ImportError
    data class Structure(val detail: String) : ImportError
    data class InvalidDate(val index: Int, val value: String) : ImportError
    data class DuplicateDate(val date: LocalDate) : ImportError
    data class OutOfRange(val date: LocalDate, val field: Field, val value: Int, val range: IntRange) : ImportError
    data class UnknownMood(val date: LocalDate, val code: String) : ImportError
    data class UnknownSymptom(val date: LocalDate, val code: String) : ImportError
    data class InvalidNote(val date: LocalDate) : ImportError
    data class InvalidEventTitle(val date: LocalDate) : ImportError
    data class InvalidEventId(val date: LocalDate, val id: String) : ImportError

    enum class Field { FLOW, PAIN, IRRITABILITY, FATIGUE }
}

sealed interface ImportResult {
    data class Success(val days: List<DayLog>) : ImportResult
    data class Failure(val error: ImportError) : ImportResult
}

/**
 * Parses and fully validates an export before anything is written:
 * format, version, dates, bounds, mood and symptom codes, note, event ids and titles.
 */
object JsonImport {
    const val MAX_BYTES = 10 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    /** Read migrations: version n → n+1, applied on the raw JSON until the current version. */
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = mapOf(1 to ::migrate1to2)

    /**
     * v1 → v2: `mood` (1–5) becomes `symptoms.moods` ([Mood.fromLegacyValue]); `period.fatigue`
     * moves to `symptoms.fatigue` (dropped when 0, the former default). Invalid values are carried
     * over unchanged so that validation reports them.
     */
    private fun migrate1to2(root: JsonObject): JsonObject {
        val days = root["days"] as? JsonArray ?: return root
        val migrated = days.map { element ->
            val day = element as? JsonObject ?: return@map element
            val period = day["period"] as? JsonObject
            val fatigue = period?.get("fatigue")
            val mood = day["mood"]
            val symptoms = buildMap<String, JsonElement> {
                if (mood != null && mood !is JsonNull) {
                    val legacy = (mood as? JsonPrimitive)?.intOrNull
                    val code = legacy?.let { Mood.fromLegacyValue(it)?.code } ?: (mood as? JsonPrimitive)?.content ?: mood.toString()
                    put("moods", JsonArray(listOf(JsonPrimitive(code))))
                }
                if (fatigue != null && fatigue !is JsonNull && (fatigue as? JsonPrimitive)?.intOrNull != 0) put("fatigue", fatigue)
            }
            JsonObject(
                buildMap {
                    putAll(day)
                    remove("mood")
                    if (period != null) put("period", JsonObject(period - "fatigue"))
                    if (symptoms.isNotEmpty()) put("symptoms", JsonObject(symptoms))
                },
            )
        }
        return JsonObject(root + ("days" to JsonArray(migrated)))
    }

    fun parse(text: String): ImportResult {
        val root: JsonElement = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return fail(ImportError.NotJson)
        }
        if (root !is JsonObject) return fail(ImportError.NotJson)
        val format = runCatching { root["format"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        if (format != ExportFormat.FORMAT) return fail(ImportError.NotSeleneExport)
        val version = runCatching { root["version"]?.jsonPrimitive?.intOrNull }.getOrNull()
            ?: return fail(ImportError.MissingVersion)
        if (version < 1) return fail(ImportError.MissingVersion)
        if (version > ExportFormat.CURRENT_VERSION) {
            return fail(ImportError.UnsupportedVersion(version, ExportFormat.CURRENT_VERSION))
        }
        var current: JsonObject = root
        for (v in version until ExportFormat.CURRENT_VERSION) {
            current = migrations.getValue(v)(current)
        }
        val file = try {
            json.decodeFromJsonElement(ExportFile.serializer(), current)
        } catch (e: SerializationException) {
            return fail(ImportError.Structure(e.message?.lineSequence()?.firstOrNull().orEmpty()))
        } catch (e: IllegalArgumentException) {
            return fail(ImportError.Structure(e.message?.lineSequence()?.firstOrNull().orEmpty()))
        }
        return validate(file.days)
    }

    private fun validate(days: List<ExportDay>): ImportResult {
        val result = mutableListOf<DayLog>()
        val seenDates = mutableSetOf<LocalDate>()
        val seenEventIds = mutableSetOf<String>()
        days.forEachIndexed { index, raw ->
            val date = try {
                require(raw.date.length == 10)
                LocalDate.parse(raw.date)
            } catch (e: DateTimeParseException) {
                return fail(ImportError.InvalidDate(index + 1, raw.date))
            } catch (e: IllegalArgumentException) {
                return fail(ImportError.InvalidDate(index + 1, raw.date))
            }
            if (!seenDates.add(date)) return fail(ImportError.DuplicateDate(date))

            val period = raw.period?.let { p ->
                checkRange(date, ImportError.Field.FLOW, p.flow, PeriodLog.FLOW_RANGE)?.let { return it }
                checkRange(date, ImportError.Field.PAIN, p.pain, PeriodLog.LEVEL_RANGE)?.let { return it }
                PeriodLog(date, p.flow, p.pain)
            }
            val symptoms = raw.symptoms?.let { s ->
                val moods = s.moods.orEmpty().map { Mood.fromCode(it) ?: return fail(ImportError.UnknownMood(date, it)) }
                val items = s.items.orEmpty().map { Symptom.fromCode(it) ?: return fail(ImportError.UnknownSymptom(date, it)) }
                s.irritability?.let { v -> checkRange(date, ImportError.Field.IRRITABILITY, v, SymptomLog.LEVEL_RANGE)?.let { return it } }
                s.fatigue?.let { v -> checkRange(date, ImportError.Field.FATIGUE, v, SymptomLog.LEVEL_RANGE)?.let { return it } }
                if (s.note != null && s.note.trim().length > SymptomLog.NOTE_MAX_LENGTH) return fail(ImportError.InvalidNote(date))
                SymptomLog(date, moods.toSet(), items.toSet(), s.irritability, s.fatigue, SymptomLog.normalizeNote(s.note))
                    .takeIf { !it.isEmpty }
            }

            val events = raw.events.orEmpty().map { e ->
                val title = e.title.trim()
                if (title.isEmpty() || title.length > NotableEvent.TITLE_MAX_LENGTH) return fail(ImportError.InvalidEventTitle(date))
                if (e.id.isBlank() || e.id.length > 64 || !seenEventIds.add(e.id)) return fail(ImportError.InvalidEventId(date, e.id))
                NotableEvent(e.id, date, title)
            }
            val day = DayLog(date, period, symptoms, events)
            if (!day.isEmpty) result += day
        }
        return ImportResult.Success(result.sortedBy { it.date })
    }

    private fun checkRange(date: LocalDate, field: ImportError.Field, value: Int, range: IntRange): ImportResult.Failure? =
        if (value in range) null else ImportResult.Failure(ImportError.OutOfRange(date, field, value, range))

    private fun fail(error: ImportError) = ImportResult.Failure(error)
}
