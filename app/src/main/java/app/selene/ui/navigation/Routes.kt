package app.selene.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object CycleRoute
@Serializable data object CalendarRoute
@Serializable data object AnalysisRoute
@Serializable data object SettingsRoute

/** Detail of one day. [date] is ISO yyyy-MM-dd. */
@Serializable data class DayRoute(val date: String)

/**
 * Add / edit sheet. [date] ISO or null for today; [kind] an EntryKind name;
 * [eventId] for editing an event; [edit] locks date and type.
 */
@Serializable data class EntryRoute(
    val date: String? = null,
    val kind: String? = null,
    val eventId: String? = null,
    val edit: Boolean = false,
)
