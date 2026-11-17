package app.selene.domain

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FullMoonsTest {

    private val utc = ZoneOffset.UTC
    private val paris = ZoneId.of("Europe/Paris")

    private fun assertFullMoonNear(expectedUtc: String) {
        val expected = ZonedDateTime.parse(expectedUtc)
        val actual = FullMoons.on(expected.toLocalDate(), utc)
        requireNotNull(actual) { "no full moon found on ${expected.toLocalDate()}" }
        val diff = Duration.between(expected.toInstant(), actual.toInstant()).abs()
        assertTrue(diff <= Duration.ofMinutes(2), "expected $expected, got $actual")
    }

    @Test
    fun `matches published full moons of lunar eclipses`() {
        assertFullMoonNear("2000-01-21T04:40Z")
        assertFullMoonNear("2022-11-08T11:02Z")
        assertFullMoonNear("2025-09-07T18:09Z")
        assertFullMoonNear("2026-03-03T11:38Z")
    }

    @Test
    fun `one full moon per synodic month, none missing over decades`() {
        val moons = FullMoons.between(LocalDate.of(1990, 1, 1), LocalDate.of(2040, 12, 31), utc).values.toList()
        // 51 years ≈ 630.7 lunations.
        assertTrue(moons.size in 630..632, "got ${moons.size}")
        moons.zipWithNext().forEach { (a, b) ->
            val days = Duration.between(a, b).toHours() / 24.0
            assertTrue(days in 29.2..29.9, "gap of $days days between $a and $b")
        }
    }

    @Test
    fun `range bounds are inclusive and use the local date`() {
        val moons = FullMoons.between(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 3), utc)
        assertEquals(setOf(LocalDate.of(2026, 3, 3)), moons.keys)
        assertTrue(FullMoons.between(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 30), utc).isEmpty())
        assertTrue(FullMoons.between(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 1), utc).isEmpty())
    }

    @Test
    fun `the day depends on the time zone, not on the location`() {
        // 2026-04-02 around 02:12 UTC: still April 1st in New York, April 2nd in Paris.
        val newYork = ZoneId.of("America/New_York")
        assertEquals(LocalDate.of(2026, 4, 2), FullMoons.on(LocalDate.of(2026, 4, 2), paris)?.toLocalDate())
        assertEquals(LocalDate.of(2026, 4, 1), FullMoons.on(LocalDate.of(2026, 4, 1), newYork)?.toLocalDate())
        assertNull(FullMoons.on(LocalDate.of(2026, 4, 2), newYork))
    }
}
