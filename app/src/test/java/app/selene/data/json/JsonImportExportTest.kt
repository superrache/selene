package app.selene.data.json

import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JsonImportExportTest {
    private val d1 = LocalDate.of(2026, 11, 9)
    private val d2 = LocalDate.of(2026, 11, 10)
    private val exportedAt = OffsetDateTime.of(2026, 11, 15, 13, 47, 0, 0, ZoneOffset.ofHours(1))

    private val sample = listOf(
        DayLog(d2, mood = Mood.GOOD),
        DayLog(
            d1,
            period = PeriodLog(d1, 2, 2, 3),
            mood = Mood.BAD,
            events = listOf(NotableEvent("6f1c", d1, "Nuit blanche")),
        ),
        DayLog(LocalDate.of(2026, 11, 11)), // empty: not exported
    )

    @Test
    fun exportMatchesTheDocumentedFormat() {
        val text = JsonExport.encode(sample, exportedAt)
        assertTrue("\"format\": \"selene-export\"" in text)
        assertTrue("\"version\": 1" in text)
        assertTrue("\"exportedAt\": \"2026-11-15T13:47:00+01:00\"" in text)
        assertTrue(text.indexOf("2026-11-09") < text.indexOf("2026-11-10"), "days sorted")
        assertTrue("2026-11-11" !in text)
        assertTrue("\"period\": null" !in text && "null" !in text, "optional fields omitted")
        assertEquals("selene-2026-11-15.json", JsonExport.fileName(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun exportThenImportIsIdentical() {
        val result = JsonImport.parse(JsonExport.encode(sample, exportedAt))
        assertIs<ImportResult.Success>(result)
        assertEquals(sample.filterNot { it.isEmpty }.sortedBy { it.date }, result.days)
    }

    @Test
    fun theDocumentedExampleIsAccepted() {
        val text = """
            {
              "format": "selene-export",
              "version": 1,
              "exportedAt": "2026-11-15T13:47:00+01:00",
              "days": [
                {
                  "date": "2026-11-09",
                  "period": { "flow": 2, "pain": 2, "fatigue": 3 },
                  "mood": 2,
                  "events": [{ "id": "6f1c", "title": "Nuit blanche" }]
                }
              ]
            }
        """.trimIndent()
        val result = assertIs<ImportResult.Success>(JsonImport.parse(text))
        assertEquals(1, result.days.size)
    }

    private fun file(days: String, version: String = "1", format: String = "\"selene-export\"") =
        """{"format":$format,"version":$version,"exportedAt":"2026-11-15T13:47:00+01:00","days":[$days]}"""

    private fun errorOf(text: String): ImportError = assertIs<ImportResult.Failure>(JsonImport.parse(text)).error

    @Test
    fun invalidFilesAreRefusedWithPreciseErrors() {
        assertEquals(ImportError.NotJson, errorOf("not json"))
        assertEquals(ImportError.NotJson, errorOf("[1,2]"))
        assertEquals(ImportError.NotSeleneExport, errorOf(file("", format = "\"other\"")))
        assertEquals(ImportError.MissingVersion, errorOf(file("", version = "\"x\"")))
        assertEquals(ImportError.UnsupportedVersion(2, 1), errorOf(file("", version = "2")))
        assertIs<ImportError.Structure>(errorOf(file("""{"mood":3}""")))
        assertEquals(ImportError.InvalidDate(1, "2026-02-30"), errorOf(file("""{"date":"2026-02-30","mood":3}""")))
        assertEquals(ImportError.InvalidDate(1, "2026-2-3"), errorOf(file("""{"date":"2026-2-3","mood":3}""")))
        assertEquals(ImportError.DuplicateDate(d1), errorOf(file("""{"date":"2026-11-09","mood":3},{"date":"2026-11-09","mood":2}""")))
        assertEquals(
            ImportError.OutOfRange(d1, ImportError.Field.FLOW, 5, 1..4),
            errorOf(file("""{"date":"2026-11-09","period":{"flow":5,"pain":0,"fatigue":0},"mood":3}""")),
        )
        assertEquals(
            ImportError.OutOfRange(d1, ImportError.Field.PAIN, -1, 0..4),
            errorOf(file("""{"date":"2026-11-09","period":{"flow":1,"pain":-1,"fatigue":0},"mood":3}""")),
        )
        assertEquals(ImportError.OutOfRange(d1, ImportError.Field.MOOD, 0, 1..5), errorOf(file("""{"date":"2026-11-09","mood":0}""")))
        assertEquals(
            ImportError.PeriodWithoutMood(d1),
            errorOf(file("""{"date":"2026-11-09","period":{"flow":1,"pain":0,"fatigue":0}}""")),
        )
        assertEquals(ImportError.InvalidEventTitle(d1), errorOf(file("""{"date":"2026-11-09","events":[{"id":"a","title":"  "}]}""")))
        assertEquals(
            ImportError.InvalidEventId(d2, "a"),
            errorOf(file("""{"date":"2026-11-09","events":[{"id":"a","title":"x"}]},{"date":"2026-11-10","events":[{"id":"a","title":"y"}]}""")),
        )
    }

    @Test
    fun unknownFieldsAreIgnoredAndEmptyDaysSkipped() {
        val result = JsonImport.parse(file("""{"date":"2026-11-09","mood":3,"future":true},{"date":"2026-11-10"}"""))
        assertEquals(listOf(DayLog(d1, mood = Mood.NEUTRAL)), assertIs<ImportResult.Success>(result).days)
    }
}
