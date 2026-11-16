package app.selene.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selene.data.db.SeleneDatabase
import app.selene.data.db.SymptomLogEntity
import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.Symptom
import app.selene.domain.model.SymptomLog
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

    private fun symptoms(date: LocalDate = day) = SymptomLog(
        date = date,
        moods = setOf(Mood.SAD, Mood.ANXIOUS),
        symptoms = setOf(Symptom.CRAMPS, Symptom.NAUSEA, Symptom.SPOTTING),
        irritability = 3,
        fatigue = 0,
        note = "Mal au genou",
    )

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repo = LogRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aPeriodDayNoLongerNeedsAMood() = runTest {
        repo.savePeriod(PeriodLog(day, 2, 3))
        val d = repo.getDay(day)
        assertEquals(PeriodLog(day, 2, 3), d.period)
        assertNull(d.symptoms)
        assertEquals(listOf(d), repo.days.first())
    }

    @Test
    fun symptomsAreSavedReadAndReplaced() = runTest {
        repo.saveSymptoms(symptoms())
        assertEquals(symptoms(), repo.getDay(day).symptoms)
        val changed = SymptomLog(day, moods = setOf(Mood.JOYFUL), symptoms = setOf(Symptom.HIGH_LIBIDO))
        repo.saveSymptoms(changed)
        assertEquals(changed, repo.getDay(day).symptoms)
    }

    @Test
    fun theNoteIsTrimmedAndBlankNotesAreDropped() = runTest {
        repo.saveSymptoms(SymptomLog(day, moods = setOf(Mood.GOOD), note = "  Migraine ophtalmique  "))
        assertEquals("Migraine ophtalmique", repo.getDay(day).symptoms?.note)
        assertFailsWith<IllegalArgumentException> { SymptomLog(day, note = "   ") }
        assertEquals(null, SymptomLog.normalizeNote("   "))
    }

    @Test
    fun savingEmptySymptomsDeletesThem() = runTest {
        repo.saveSymptoms(symptoms())
        repo.saveSymptoms(SymptomLog(day))
        assertTrue(repo.getDay(day).isEmpty)
    }

    @Test
    fun symptomsCanBeDeletedAndRestoredIndependentlyOfThePeriod() = runTest {
        repo.savePeriod(PeriodLog(day, 3, 1))
        repo.saveSymptoms(symptoms())
        val deleted = repo.deleteSymptoms(day)
        assertNull(repo.getDay(day).symptoms)
        assertEquals(PeriodLog(day, 3, 1), repo.getDay(day).period)
        repo.restore(deleted)
        assertEquals(symptoms(), repo.getDay(day).symptoms)
    }

    @Test
    fun deletingAPeriodDayKeepsTheSymptomsAndCanBeUndone() = runTest {
        repo.savePeriod(PeriodLog(day, 3, 1))
        repo.saveSymptoms(symptoms())
        val deleted = repo.deletePeriod(day)
        assertNull(repo.getDay(day).period)
        assertEquals(symptoms(), repo.getDay(day).symptoms)
        repo.restore(deleted)
        assertEquals(PeriodLog(day, 3, 1), repo.getDay(day).period)
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
    fun importMergeLetsTheFileWinDayByDay() = runTest {
        repo.savePeriod(PeriodLog(day, 2, 0))
        repo.saveSymptoms(symptoms())
        repo.addEvent(day, "Ancien")
        repo.saveSymptoms(SymptomLog(day.plusDays(5), moods = setOf(Mood.GOOD)))
        val fileDay = DayLog(
            day,
            symptoms = SymptomLog(day, moods = setOf(Mood.JOYFUL)),
            events = listOf(NotableEvent("e1", day, "Nouveau")),
        )
        repo.import(listOf(fileDay), LogRepository.ImportMode.MERGE)
        assertEquals(fileDay, repo.getDay(day))
        assertEquals(setOf(Mood.GOOD), repo.getDay(day.plusDays(5)).moods)
    }

    @Test
    fun importReplaceAllRemovesEverythingElse() = runTest {
        repo.saveSymptoms(SymptomLog(day.plusDays(5), moods = setOf(Mood.GOOD)))
        val fileDay = DayLog(day, period = PeriodLog(day, 4, 4), symptoms = symptoms())
        repo.import(listOf(fileDay), LogRepository.ImportMode.REPLACE_ALL)
        assertEquals(listOf(fileDay), repo.snapshot())
    }

    @Test
    fun unknownCodesAndOutOfRangeValuesAreSkippedWhenReading() = runTest {
        db.logDao().upsertSymptoms(SymptomLogEntity(day, "sad,unknown", "cramps,???", 9, 2, null))
        assertEquals(SymptomLog(day, setOf(Mood.SAD), setOf(Symptom.CRAMPS), null, 2, null), repo.getDay(day).symptoms)
        db.logDao().upsertSymptoms(SymptomLogEntity(day, "unknown", "", null, null, "  "))
        assertNull(repo.getDay(day).symptoms)
    }

    @Test
    fun deleteAllClearsLogs() = runTest {
        repo.savePeriod(PeriodLog(day, 2, 0))
        repo.saveSymptoms(symptoms())
        repo.addEvent(day, "Test")
        repo.deleteAll()
        assertTrue(repo.snapshot().isEmpty())
    }
}
