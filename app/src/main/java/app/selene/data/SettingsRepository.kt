package app.selene.data

import app.selene.data.db.SettingEntity
import app.selene.data.db.SettingsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class AppSettings(
    val calendarSyncEnabled: Boolean = false,
    val calendarId: Long? = null,
    val calendarEventId: Long? = null,
    /** Null means the default, discreet title. */
    val calendarEventTitle: String? = null,
    val biometricLock: Boolean = false,
)

/** Settings live in the encrypted database (never in plain SharedPreferences). */
class SettingsRepository(private val dao: SettingsDao) {
    val settings: Flow<AppSettings> = dao.observeAll()
        .map { rows -> parse(rows.associate { it.key to it.value }) }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = parse(
        KEYS.mapNotNull { key -> dao.get(key)?.let { key to it } }.toMap(),
    )

    suspend fun setCalendarSync(enabled: Boolean, calendarId: Long?) {
        put(KEY_CALENDAR_SYNC, enabled.toString())
        put(KEY_CALENDAR_ID, calendarId?.toString())
        if (!enabled) put(KEY_CALENDAR_EVENT_ID, null)
    }

    suspend fun setCalendarEventId(id: Long?) = put(KEY_CALENDAR_EVENT_ID, id?.toString())

    suspend fun setCalendarEventTitle(title: String?) = put(KEY_CALENDAR_EVENT_TITLE, title?.trim()?.takeIf { it.isNotEmpty() })

    suspend fun setBiometricLock(enabled: Boolean) = put(KEY_BIOMETRIC_LOCK, enabled.toString())

    suspend fun clear() = dao.deleteAll()

    private suspend fun put(key: String, value: String?) {
        if (value == null) dao.delete(key) else dao.put(SettingEntity(key, value))
    }

    private fun parse(map: Map<String, String>) = AppSettings(
        calendarSyncEnabled = map[KEY_CALENDAR_SYNC]?.toBooleanStrictOrNull() ?: false,
        calendarId = map[KEY_CALENDAR_ID]?.toLongOrNull(),
        calendarEventId = map[KEY_CALENDAR_EVENT_ID]?.toLongOrNull(),
        calendarEventTitle = map[KEY_CALENDAR_EVENT_TITLE],
        biometricLock = map[KEY_BIOMETRIC_LOCK]?.toBooleanStrictOrNull() ?: false,
    )

    companion object {
        const val KEY_CALENDAR_SYNC = "calendar_sync_enabled"
        const val KEY_CALENDAR_ID = "calendar_id"
        const val KEY_CALENDAR_EVENT_ID = "calendar_event_id"
        const val KEY_CALENDAR_EVENT_TITLE = "calendar_event_title"
        const val KEY_BIOMETRIC_LOCK = "biometric_lock"
        private val KEYS = listOf(KEY_CALENDAR_SYNC, KEY_CALENDAR_ID, KEY_CALENDAR_EVENT_ID, KEY_CALENDAR_EVENT_TITLE, KEY_BIOMETRIC_LOCK)
    }
}
