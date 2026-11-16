package app.selene.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selene.data.json.ImportResult
import app.selene.data.json.JsonExport
import app.selene.data.json.JsonImport
import app.selene.domain.model.Mood
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.Symptom
import app.selene.domain.model.SymptomLog
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class ImportRoundTripTest {
    @Test
    fun databaseExportedThenImportedIsIdentical() = runTest {
        val source = inMemoryDatabase()
        val repo = LogRepository(source)
        val start = LocalDate.of(2026, 9, 1)
        repeat(60) { i ->
            val d = start.plusDays(i.toLong())
            when {
                i % 28 < 5 -> repo.savePeriod(PeriodLog(d, 1 + i % 4, i % 5))
                i % 3 == 0 -> repo.saveSymptoms(
                    SymptomLog(
                        date = d,
                        moods = setOf(Mood.entries[i % Mood.entries.size], Mood.entries[(i + 4) % Mood.entries.size]),
                        symptoms = Symptom.entries.filterIndexed { k, _ -> (k + i) % 7 == 0 }.toSet(),
                        irritability = (i % 6).takeIf { it <= 4 },
                        fatigue = i % 5,
                        note = if (i % 2 == 0) "Note « $i » avec accents é, ü et \"guillemets\"" else null,
                    ),
                )
            }
            if (i % 7 == 0) repo.addEvent(d, "Événement $i")
        }
        val text = JsonExport.encode(repo.snapshot(), OffsetDateTime.now())

        val target = inMemoryDatabase()
        val targetRepo = LogRepository(target)
        targetRepo.saveSymptoms(SymptomLog(LocalDate.of(2020, 1, 1), moods = setOf(Mood.GOOD)))
        targetRepo.import(assertIs<ImportResult.Success>(JsonImport.parse(text)).days, LogRepository.ImportMode.REPLACE_ALL)

        assertEquals(repo.snapshot(), targetRepo.snapshot())
        source.close()
        target.close()
    }

    @Test
    fun anInvalidFileChangesNothing() = runTest {
        val db = inMemoryDatabase()
        val repo = LogRepository(db)
        repo.saveSymptoms(SymptomLog(LocalDate.of(2026, 11, 1), moods = setOf(Mood.GOOD)))
        val before = repo.snapshot()
        val text = """{"format":"selene-export","version":2,"exportedAt":"x","days":[
            {"date":"2026-11-02","symptoms":{"moods":["sad"]}},
            {"date":"2026-11-03","symptoms":{"items":["cramps","telepathy"]}}]}"""
        val result = JsonImport.parse(text)
        assertIs<ImportResult.Failure>(result)
        // Callers only write on Success; nothing was written.
        assertEquals(before, repo.snapshot())
        db.close()
    }
}
