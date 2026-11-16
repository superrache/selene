package app.selene.data.json

import kotlinx.serialization.Serializable

/**
 * JSON export format, version 2 (see CLAUDE.md « Format JSON d'export / import »).
 * Any schema change bumps [ExportFormat.CURRENT_VERSION] and adds a read migration in [JsonImport].
 */
object ExportFormat {
    const val FORMAT = "selene-export"
    const val CURRENT_VERSION = 2
}

@Serializable
data class ExportFile(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val days: List<ExportDay>,
)

@Serializable
data class ExportDay(
    val date: String,
    val period: ExportPeriod? = null,
    val symptoms: ExportSymptoms? = null,
    val events: List<ExportEvent>? = null,
)

@Serializable
data class ExportPeriod(val flow: Int, val pain: Int)

/** Version 2. Moods and symptoms are stable codes (see [app.selene.domain.model.Mood.code], [app.selene.domain.model.Symptom.code]). */
@Serializable
data class ExportSymptoms(
    val moods: List<String>? = null,
    val items: List<String>? = null,
    val irritability: Int? = null,
    val fatigue: Int? = null,
    val note: String? = null,
)

@Serializable
data class ExportEvent(val id: String, val title: String)
