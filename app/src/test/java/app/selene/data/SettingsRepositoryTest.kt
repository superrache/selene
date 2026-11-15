package app.selene.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    @Test
    fun settingsRoundTrip() = runTest {
        val db = inMemoryDatabase()
        val repo = SettingsRepository(db.settingsDao())
        assertEquals(AppSettings(), repo.current())
        repo.setCalendarSync(true, 7L)
        repo.setCalendarEventId(42L)
        repo.setCalendarEventTitle("  Note  ")
        repo.setBiometricLock(true)
        assertEquals(AppSettings(true, 7L, 42L, "Note", true), repo.settings.first())
        repo.setCalendarSync(false, null)
        val s = repo.current()
        assertEquals(false, s.calendarSyncEnabled)
        assertNull(s.calendarEventId)
        repo.setCalendarEventTitle(" ")
        assertNull(repo.current().calendarEventTitle)
        db.close()
    }
}
