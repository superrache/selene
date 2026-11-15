package app.selene.data.json

import kotlinx.serialization.Serializable

/**
 * JSON export format, version 1 (see CLAUDE.md « Format JSON d'export / import »).
 * Any schema change bumps [ExportFormat.CURRENT_VERSION] and adds a read migration in [JsonImport].
 */
object ExportFormat {
    const val FORMAT = "selene-export"
    const val CURRENT_VERSION = 1
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
    val mood: Int? = null,
    val events: List<ExportEvent>? = null,
)

@Serializable
data class ExportPeriod(val flow: Int, val pain: Int, val fatigue: Int)

@Serializable
data class ExportEvent(val id: String, val title: String)
