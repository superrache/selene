package app.selene.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import app.selene.domain.model.DateRange
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
) {
    /** Local calendars never leave the device; account calendars are synced to a server. */
    val isLocal: Boolean get() = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL
}

/** What the sync coordinator needs from the calendar (fakeable in tests). */
interface CalendarEventGateway {
    fun hasPermission(): Boolean
    fun upsertEvent(calendarId: Long, existingEventId: Long?, window: DateRange, title: String): Long
    fun deleteEvent(eventId: Long)
}

/**
 * Thin wrapper over CalendarContract. Only reads the calendar list and the single event the
 * app created (by id): no other event is ever queried.
 */
class PeriodCalendarSync(private val context: Context) : CalendarEventGateway {
    private val resolver get() = context.contentResolver

    override fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Writable calendars, local ones first. */
    fun writableCalendars(): List<CalendarInfo> {
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE)
        val selection = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val result = mutableListOf<CalendarInfo>()
        resolver.query(Calendars.CONTENT_URI, projection, selection, args, null)?.use { c ->
            while (c.moveToNext()) {
                result += CalendarInfo(
                    id = c.getLong(0),
                    displayName = c.getString(1).orEmpty(),
                    accountName = c.getString(2).orEmpty(),
                    accountType = c.getString(3).orEmpty(),
                )
            }
        }
        return result.sortedWith(compareBy({ !it.isLocal }, { it.displayName.lowercase() }))
    }

    fun calendar(id: Long): CalendarInfo? = writableCalendars().firstOrNull { it.id == id }

    /** Returns the app's local calendar, creating it if needed. */
    fun getOrCreateLocalCalendar(displayName: String): Long {
        writableCalendars().firstOrNull { it.isLocal && it.accountName == LOCAL_ACCOUNT_NAME }?.let { return it.id }
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, LOCAL_ACCOUNT_NAME)
            put(Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        val created = resolver.insert(uri, values) ?: error("calendar not created")
        return ContentUris.parseId(created)
    }

    /**
     * Creates or updates the single all-day event covering [window], without description.
     * Recreates it if it was deleted from the calendar app or if the calendar changed.
     * Returns the event id to keep in settings.
     */
    override fun upsertEvent(calendarId: Long, existingEventId: Long?, window: DateRange, title: String): Long {
        val values = ContentValues().apply {
            put(Events.TITLE, title)
            put(Events.DTSTART, utcMidnight(window.start))
            put(Events.DTEND, utcMidnight(window.endInclusive.plusDays(1)))
            put(Events.ALL_DAY, 1)
            put(Events.EVENT_TIMEZONE, "UTC")
            put(Events.HAS_ALARM, 0)
            put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
            put(Events.ACCESS_LEVEL, Events.ACCESS_PRIVATE)
        }
        if (existingEventId != null) {
            when (eventCalendar(existingEventId)) {
                calendarId -> {
                    resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, existingEventId), values, null, null)
                    return existingEventId
                }
                null -> Unit // deleted from the calendar app: recreate
                else -> deleteEvent(existingEventId) // calendar changed: move
            }
        }
        values.put(Events.CALENDAR_ID, calendarId)
        val uri = resolver.insert(Events.CONTENT_URI, values) ?: error("event not created")
        return ContentUris.parseId(uri)
    }

    override fun deleteEvent(eventId: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
    }

    /** Calendar id of our event, or null if it no longer exists. Reads only that event. */
    private fun eventCalendar(eventId: Long): Long? {
        val projection = arrayOf(Events.CALENDAR_ID, Events.DELETED)
        resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), projection, null, null, null)?.use { c ->
            if (c.moveToFirst() && c.getInt(1) == 0) return c.getLong(0)
        }
        return null
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        const val LOCAL_ACCOUNT_NAME = "Sélène"
        private const val CALENDAR_COLOR = 0xFF7C68C4.toInt()

        /** All-day events are stored at UTC midnight. */
        fun utcMidnight(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}
