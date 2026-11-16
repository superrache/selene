package app.selene.data.json

import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.Symptom
import app.selene.domain.model.SymptomLog
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
        DayLog(d2, symptoms = SymptomLog(d2, moods = setOf(Mood.GOOD))),
        DayLog(
            d1,
            period = PeriodLog(d1, 2, 2),
            symptoms = SymptomLog(
                d1,
                moods = setOf(Mood.SAD, Mood.FRAGILE),
                symptoms = setOf(Symptom.CRAMPS, Symptom.VOMITING),
                irritability = 2,
                fatigue = 3,
                note = "Nuit agitée",
            ),
            events = listOf(NotableEvent("6f1c", d1, "Nuit blanche")),
        ),
        DayLog(LocalDate.of(2026, 11, 11)), // empty: not exported
    )

    @Test
    fun exportMatchesTheDocumentedFormat() {
        val text = JsonExport.encode(sample, exportedAt)
        assertTrue("\"format\": \"selene-export\"" in text)
        assertTrue("\"version\": 2" in text)
        assertTrue("\"exportedAt\": \"2026-11-15T13:47:00+01:00\"" in text)
        assertTrue(text.indexOf("2026-11-09") < text.indexOf("2026-11-10"), "days sorted")
        assertTrue("2026-11-11" !in text)
        assertTrue("null" !in text, "optional fields omitted")
        assertTrue("\"moods\": [\n" in text && "\"sad\"" in text && "\"cramps\"" in text)
        assertTrue(text.indexOf("\"sad\"") < text.indexOf("\"fragile\""), "codes in stable order")
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
              "version": 2,
              "exportedAt": "2026-11-15T13:47:00+01:00",
              "days": [
                {
                  "date": "2026-11-09",
                  "period": { "flow": 2, "pain": 2 },
                  "symptoms": {
                    "moods": ["sad"],
                    "items": ["cramps", "nausea"],
                    "irritability": 2,
                    "fatigue": 3,
                    "note": "Mal au genou"
                  },
                  "events": [{ "id": "6f1c", "title": "Nuit blanche" }]
                }
              ]
            }
        """.trimIndent()
        val result = assertIs<ImportResult.Success>(JsonImport.parse(text))
        assertEquals(
            SymptomLog(d1, setOf(Mood.SAD), setOf(Symptom.CRAMPS, Symptom.NAUSEA), 2, 3, "Mal au genou"),
            result.days.single().symptoms,
        )
    }

    @Test
    fun version1FilesAreMigrated() {
        val text = """
            {
              "format": "selene-export",
              "version": 1,
              "exportedAt": "2026-11-15T13:47:00+01:00",
              "days": [
                { "date": "2026-11-09", "period": { "flow": 2, "pain": 2, "fatigue": 3 }, "mood": 2,
                  "events": [{ "id": "6f1c", "title": "Nuit blanche" }] },
                { "date": "2026-11-10", "period": { "flow": 1, "pain": 0, "fatigue": 0 }, "mood": 5 },
                { "date": "2026-11-11", "period": { "flow": 1, "pain": 0, "fatigue": 0 } }
              ]
            }
        """.trimIndent()
        val days = assertIs<ImportResult.Success>(JsonImport.parse(text)).days
        val d3 = LocalDate.of(2026, 11, 11)
        assertEquals(
            listOf(
                DayLog(
                    d1,
                    PeriodLog(d1, 2, 2),
                    SymptomLog(d1, moods = setOf(Mood.SAD), fatigue = 3),
                    listOf(NotableEvent("6f1c", d1, "Nuit blanche")),
                ),
                DayLog(d2, PeriodLog(d2, 1, 0), SymptomLog(d2, moods = setOf(Mood.JOYFUL))),
                DayLog(d3, PeriodLog(d3, 1, 0)),
            ),
            days,
        )
        assertEquals(
            listOf(Mood.CRYING, Mood.SAD, Mood.NEUTRAL, Mood.GOOD, Mood.JOYFUL),
            (1..5).map { Mood.fromLegacyValue(it) },
        )
    }

    @Test
    fun invalidVersion1ValuesAreStillReported() {
        assertEquals(
            ImportError.UnknownMood(d1, "0"),
            errorOf(file("""{"date":"2026-11-09","mood":0}""", version = "1")),
        )
        assertEquals(
            ImportError.OutOfRange(d1, ImportError.Field.FATIGUE, 7, 0..4),
            errorOf(file("""{"date":"2026-11-09","period":{"flow":1,"pain":0,"fatigue":7},"mood":3}""", version = "1")),
        )
    }

    private fun file(days: String, version: String = "2", format: String = "\"selene-export\"") =
        """{"format":$format,"version":$version,"exportedAt":"2026-11-15T13:47:00+01:00","days":[$days]}"""

    private fun errorOf(text: String): ImportError = assertIs<ImportResult.Failure>(JsonImport.parse(text)).error

    @Test
    fun invalidFilesAreRefusedWithPreciseErrors() {
        assertEquals(ImportError.NotJson, errorOf("not json"))
        assertEquals(ImportError.NotJson, errorOf("[1,2]"))
        assertEquals(ImportError.NotSeleneExport, errorOf(file("", format = "\"other\"")))
        assertEquals(ImportError.MissingVersion, errorOf(file("", version = "\"x\"")))
        assertEquals(ImportError.UnsupportedVersion(3, 2), errorOf(file("", version = "3")))
        assertIs<ImportError.Structure>(errorOf(file("""{"symptoms":{"moods":["sad"]}}""")))
        assertEquals(ImportError.InvalidDate(1, "2026-02-30"), errorOf(file("""{"date":"2026-02-30"}""")))
        assertEquals(ImportError.InvalidDate(1, "2026-2-3"), errorOf(file("""{"date":"2026-2-3"}""")))
        assertEquals(ImportError.DuplicateDate(d1), errorOf(file("""{"date":"2026-11-09"},{"date":"2026-11-09"}""")))
        assertEquals(
            ImportError.OutOfRange(d1, ImportError.Field.FLOW, 5, 1..4),
            errorOf(file("""{"date":"2026-11-09","period":{"flow":5,"pain":0}}""")),
        )
        assertEquals(
            ImportError.OutOfRange(d1, ImportError.Field.PAIN, -1, 0..4),
            errorOf(file("""{"date":"2026-11-09","period":{"flow":1,"pain":-1}}""")),
        )
        assertEquals(
            ImportError.OutOfRange(d1, ImportError.Field.IRRITABILITY, 5, 0..4),
            errorOf(file("""{"date":"2026-11-09","symptoms":{"irritability":5}}""")),
        )
        assertEquals(ImportError.UnknownMood(d1, "grumpy"), errorOf(file("""{"date":"2026-11-09","symptoms":{"moods":["grumpy"]}}""")))
        assertEquals(ImportError.UnknownSymptom(d1, "x"), errorOf(file("""{"date":"2026-11-09","symptoms":{"items":["cramps","x"]}}""")))
        assertEquals(
            ImportError.InvalidNote(d1),
            errorOf(file("""{"date":"2026-11-09","symptoms":{"note":"${"a".repeat(201)}"}}""")),
        )
        assertEquals(ImportError.InvalidEventTitle(d1), errorOf(file("""{"date":"2026-11-09","events":[{"id":"a","title":"  "}]}""")))
        assertEquals(
            ImportError.InvalidEventId(d2, "a"),
            errorOf(file("""{"date":"2026-11-09","events":[{"id":"a","title":"x"}]},{"date":"2026-11-10","events":[{"id":"a","title":"y"}]}""")),
        )
    }

    @Test
    fun unknownFieldsAreIgnoredAndEmptyDaysSkipped() {
        val result = JsonImport.parse(
            file("""{"date":"2026-11-09","symptoms":{"moods":["neutral"]},"future":true},{"date":"2026-11-10","symptoms":{"moods":[],"note":" "}}"""),
        )
        assertEquals(
            listOf(DayLog(d1, symptoms = SymptomLog(d1, moods = setOf(Mood.NEUTRAL)))),
            assertIs<ImportResult.Success>(result).days,
        )
    }
}
