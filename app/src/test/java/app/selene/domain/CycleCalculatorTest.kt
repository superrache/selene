package app.selene.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CycleCalculatorTest {
    @Test
    fun `empty history has no episodes nor cycles`() {
        assertTrue(CycleCalculator.episodes(emptyList()).isEmpty())
        assertTrue(CycleCalculator.cycles(emptyList()).isEmpty())
    }

    @Test
    fun `consecutive days form one episode`() {
        val days = (0L..4L).map { date("2026-01-01").plusDays(it) }
        val episodes = CycleCalculator.episodes(days)
        assertEquals(1, episodes.size)
        assertEquals(date("2026-01-01"), episodes[0].start)
        assertEquals(date("2026-01-05"), episodes[0].end)
        assertEquals(5, episodes[0].length)
    }

    @Test
    fun `a one-day gap is tolerated`() {
        val days = listOf("2026-01-01", "2026-01-02", "2026-01-04", "2026-01-05").map(::date)
        val episodes = CycleCalculator.episodes(days)
        assertEquals(1, episodes.size)
        assertEquals(5, episodes[0].length)
    }

    @Test
    fun `a two-day gap starts a new episode`() {
        val days = listOf("2026-01-01", "2026-01-02", "2026-01-05").map(::date)
        val episodes = CycleCalculator.episodes(days)
        assertEquals(2, episodes.size)
        assertEquals(date("2026-01-05"), episodes[1].start)
    }

    @Test
    fun `input order and duplicates do not matter`() {
        val days = listOf("2026-02-03", "2026-01-01", "2026-02-02", "2026-01-01", "2026-01-02").map(::date)
        val episodes = CycleCalculator.episodes(days)
        assertEquals(listOf(date("2026-01-01"), date("2026-02-02")), episodes.map { it.start })
    }

    @Test
    fun `cycles go from one start to the next and the last one is open`() {
        val days = history { cycles(date("2026-01-01"), listOf(28, 30)) }.map { it.date }
        val cycles = CycleCalculator.cycles(days)
        assertEquals(3, cycles.size)
        assertEquals(28, cycles[0].length)
        assertEquals(date("2026-01-28"), cycles[0].end)
        assertEquals(30, cycles[1].length)
        assertNull(cycles[2].length)
        assertFalse(cycles[2].isComplete)
        assertFalse(cycles[2].isValid)
    }

    @Test
    fun `cycles shorter than 15 or longer than 60 days are invalid`() {
        val days = history { cycles(date("2026-01-01"), listOf(14, 15, 60, 61), periodDays = 2) }.map { it.date }
        val validity = CycleCalculator.cycles(days).dropLast(1).map { it.isValid }
        assertEquals(listOf(false, true, true, false), validity)
    }
}
