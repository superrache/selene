package app.selene.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selene.data.db.MoodLogEntity
import app.selene.data.db.SeleneDatabase
import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LogRepositoryTest {
    private lateinit var db: SeleneDatabase
    private lateinit var repo: LogRepository
    private val day = LocalDate.of(2026, 11, 9)

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repo = LogRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun savingAPeriodDayWritesPeriodAndMoodTogether() = runTest {
        repo.savePeriodDay(PeriodLog(day, 2, 2, 3), Mood.BAD)
        val d = repo.getDay(day)
        assertEquals(PeriodLog(day, 2, 2, 3), d.period)
        assertEquals(Mood.BAD, d.mood)
        assertEquals(listOf(d), repo.days.first())
    }

    @Test
    fun theMoodOfAPeriodDayCanBeChangedButNotDeletedAlone() = runTest {
        repo.savePeriodDay(PeriodLog(day, 2, 0, 0), Mood.BAD)
        repo.saveMood(day, Mood.GOOD)
        assertEquals(Mood.GOOD, repo.getDay(day).mood)
        assertFailsWith<MoodInvariantException> { repo.deleteMood(day) }
        assertEquals(Mood.GOOD, repo.getDay(day).mood)
    }

    @Test
    fun aMoodAloneCanBeDeleted() = runTest {
        repo.saveMood(day, Mood.NEUTRAL)
        val deleted = repo.deleteMood(day)
        assertNull(repo.getDay(day).mood)
        repo.restore(deleted)
        assertEquals(Mood.NEUTRAL, repo.getDay(day).mood)
    }

    @Test
    fun deletingAPeriodDayDeletesItsMoodByDefaultAndCanBeUndone() = runTest {
        repo.savePeriodDay(PeriodLog(day, 3, 1, 1), Mood.VERY_BAD)
        val deleted = repo.deletePeriodDay(day)
        assertTrue(repo.getDay(day).isEmpty)
        repo.restore(deleted)
        assertEquals(PeriodLog(day, 3, 1, 1), repo.getDay(day).period)
        assertEquals(Mood.VERY_BAD, repo.getDay(day).mood)
    }

    @Test
    fun deletingAPeriodDayCanKeepTheMood() = runTest {
        repo.savePeriodDay(PeriodLog(day, 3, 1, 1), Mood.VERY_BAD)
        val deleted = repo.deletePeriodDay(day, alsoDeleteMood = false)
        assertNull(repo.getDay(day).period)
        assertEquals(Mood.VERY_BAD, repo.getDay(day).mood)
        repo.restore(deleted)
        assertEquals(PeriodLog(day, 3, 1, 1), repo.getDay(day).period)
    }

    @Test
    fun aPeriodCannotBeRestoredWithoutMood() = runTest {
        assertFailsWith<MoodInvariantException> { repo.restore(DayLog(day, period = PeriodLog(day, 1, 0, 0))) }
        assertTrue(repo.getDay(day).isEmpty)
    }

    @Test
    fun eventsCanBeAddedEditedDeletedAndRestored() = runTest {
        val a = repo.addEvent(day, "  Nuit blanche ")
        val b = repo.addEvent(day, "Sport")
        assertEquals("Nuit blanche", a.title)
        assertEquals(2, repo.getDay(day).events.size)
        repo.updateEvent(a.id, "Nuit courte")
        assertEquals(setOf("Nuit courte", "Sport"), repo.getDay(day).events.map { it.title }.toSet())
        val deleted = repo.deleteEvent(b.id)!!
        assertEquals(listOf("Nuit courte"), repo.getDay(day).events.map { it.title })
        repo.restore(deleted)
        assertEquals(2, repo.getDay(day).events.size)
    }

    @Test
    fun eventTitlesAreValidated() = runTest {
        assertFailsWith<IllegalArgumentException> { repo.addEvent(day, "   ") }
        assertFailsWith<IllegalArgumentException> { repo.addEvent(day, "x".repeat(81)) }
        repo.addEvent(day, "x".repeat(80))
    }

    @Test
    fun importRefusesPeriodDaysWithoutMoodAndWritesNothing() = runTest {
        repo.saveMood(day, Mood.GOOD)
        val other = day.plusDays(1)
        val bad = listOf(
            DayLog(other, mood = Mood.BAD),
            DayLog(other.plusDays(1), period = PeriodLog(other.plusDays(1), 1, 0, 0)),
        )
        assertFailsWith<MoodInvariantException> { repo.import(bad, LogRepository.ImportMode.REPLACE_ALL) }
        assertEquals(listOf(DayLog(day, mood = Mood.GOOD)), repo.snapshot())
    }

    @Test
    fun importMergeLetsTheFileWinDayByDay() = runTest {
        repo.savePeriodDay(PeriodLog(day, 2, 0, 0), Mood.BAD)
        repo.addEvent(day, "Ancien")
        repo.saveMood(day.plusDays(5), Mood.GOOD)
        val fileDay = DayLog(day, mood = Mood.VERY_GOOD, events = listOf(NotableEvent("e1", day, "Nouveau")))
        repo.import(listOf(fileDay), LogRepository.ImportMode.MERGE)
        assertEquals(fileDay, repo.getDay(day))
        assertEquals(Mood.GOOD, repo.getDay(day.plusDays(5)).mood)
    }

    @Test
    fun importReplaceAllRemovesEverythingElse() = runTest {
        repo.saveMood(day.plusDays(5), Mood.GOOD)
        val fileDay = DayLog(day, period = PeriodLog(day, 4, 4, 4), mood = Mood.VERY_BAD)
        repo.import(listOf(fileDay), LogRepository.ImportMode.REPLACE_ALL)
        assertEquals(listOf(fileDay), repo.snapshot())
    }

    @Test
    fun corruptedRowsAreSkippedWhenReading() = runTest {
        db.logDao().upsertMood(MoodLogEntity(day, 9))
        assertNull(repo.getDay(day).mood)
    }

    @Test
    fun deleteAllClearsLogs() = runTest {
        repo.savePeriodDay(PeriodLog(day, 2, 0, 0), Mood.BAD)
        repo.addEvent(day, "Test")
        repo.deleteAll()
        assertTrue(repo.snapshot().isEmpty())
    }
}
