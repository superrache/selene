package app.selene.domain

import app.selene.domain.model.CyclePhase
import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.Reliability
import app.selene.domain.model.SymptomLog
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PredictorTest {
    private val start = date("2026-01-05")

    // --- Empty / few cycles ---------------------------------------------------------------------

    @Test
    fun `empty history gives no current cycle and default statistics`() {
        val p = Predictor.predict(emptyList(), date("2026-11-15"))
        assertFalse(p.hasData)
        assertNull(p.current)
        assertTrue(p.stats.usesDefaults)
        assertEquals(28.0, p.stats.averageCycleLength)
        assertEquals(5, p.stats.averagePeriodLength)
        assertEquals(Reliability.LOW, p.reliability)
        assertTrue(p.upcomingPeriods.isEmpty())
        assertNull(p.nextFertileWindow)
    }

    @Test
    fun `a single period uses defaults with a 3-day margin`() {
        val logs = history { period(start) }
        val p = Predictor.predict(logs, start.plusDays(10))
        val c = assertNotNull(p.current)
        assertEquals(start.plusDays(28), c.nextPeriod.date)
        assertEquals(3, c.nextPeriod.marginDays)
        assertEquals(Reliability.LOW, p.reliability)
        assertEquals(11, c.dayOfCycle)
        assertEquals(28, c.expectedLength)
        assertEquals(start.plusDays(14), c.fertileWindow.ovulation)
        assertEquals(start.plusDays(9), c.fertileWindow.range.start)
        assertEquals(6, c.fertileWindow.range.lengthInDays)
    }

    @Test
    fun `one valid cycle is not enough for statistics`() {
        val logs = history { cycles(start, listOf(32)) }
        val p = Predictor.predict(logs, start.plusDays(35))
        assertTrue(p.stats.usesDefaults)
        assertEquals(1, p.stats.validCycleCount)
        assertEquals(start.plusDays(32 + 28), p.current!!.nextPeriod.date)
    }

    // --- Regular / irregular --------------------------------------------------------------------

    @Test
    fun `regular cycles give an exact prediction with a one-day margin`() {
        val starts = TestHistory().cycles(start, List(6) { 28 })
        val logs = history { cycles(start, List(6) { 28 }) }
        val last = starts.last()
        val p = Predictor.predict(logs, last.plusDays(3))
        assertEquals(28.0, p.stats.averageCycleLength, 1e-9)
        assertEquals(0.0, p.stats.standardDeviation!!, 1e-9)
        assertEquals(5, p.stats.averagePeriodLength)
        assertEquals(Reliability.VERY_GOOD, p.reliability)
        val c = p.current!!
        assertEquals(last.plusDays(28), c.nextPeriod.date)
        assertEquals(1, c.nextPeriod.marginDays)
        assertEquals(CyclePhase.MENSTRUATION, c.phase)
        assertEquals(4, c.dayOfCycle)
    }

    @Test
    fun `the formula shown on the Analysis screen gives the weighted mean`() {
        val lengths = listOf(27, 28, 30, 28, 27, 29)
        val logs = history { cycles(start, lengths) }
        val p = Predictor.predict(logs, start.plusDays(lengths.sum() + 3L))
        val kept = Predictor.statisticsCycles(p.cycles).map { it.length!! }
        val weights = Predictor.cycleWeights(kept.size)
        assertEquals(lengths, kept)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), weights)
        val shown = kept.zip(weights).sumOf { (l, w) -> l * w }.toDouble() / weights.sum()
        assertEquals(p.stats.averageCycleLength, shown, 1e-9)
        assertEquals(594.0 / 21, shown, 1e-9) // shown as 28,3
    }

    @Test
    fun `recent cycles weigh more`() {
        // weights 1,2,3,4: (30 + 60 + 90 + 4*26) / 10 = 28.4
        val logs = history { cycles(start, listOf(30, 30, 30, 26)) }
        val p = Predictor.predict(logs, date("2026-06-01"))
        assertEquals(28.4, p.stats.averageCycleLength, 1e-9)
    }

    @Test
    fun `irregular cycles widen the margin and lower the reliability`() {
        val lengths = listOf(24, 36, 25, 35, 26, 34)
        val logs = history { cycles(start, lengths) }
        val p = Predictor.predict(logs, date("2026-07-20"))
        val sd = p.stats.standardDeviation!!
        assertTrue(sd > 4.0, "sd=$sd")
        assertEquals(Reliability.MEDIUM, p.reliability)
        assertEquals(maxOf(1, Math.round(sd).toInt()), p.current!!.nextPeriod.marginDays)
    }

    @Test
    fun `reliability thresholds`() {
        fun rel(lengths: List<Int>) = Predictor.predict(history { cycles(start, lengths) }, date("2027-06-01")).reliability
        assertEquals(Reliability.MEDIUM, rel(listOf(28, 28)))
        assertEquals(Reliability.MEDIUM, rel(listOf(28, 28, 28)))
        assertEquals(Reliability.GOOD, rel(listOf(28, 28, 28, 28)))
        assertEquals(Reliability.GOOD, rel(listOf(25, 31, 25, 31, 25, 31))) // sd 3
        assertEquals(Reliability.VERY_GOOD, rel(listOf(27, 29, 27, 29, 27, 29))) // sd 1
    }

    @Test
    fun `outliers are displayed but excluded from statistics`() {
        val logs = history { cycles(start, listOf(28, 10, 28, 90, 28)) }
        val p = Predictor.predict(logs, date("2026-08-01"))
        assertEquals(6, p.cycles.size)
        assertEquals(listOf(28, 10, 28, 90, 28), p.cycles.mapNotNull { it.length })
        assertEquals(3, p.stats.validCycleCount)
        assertEquals(28.0, p.stats.averageCycleLength, 1e-9)
    }

    @Test
    fun `only the last 12 valid cycles are used`() {
        val lengths = List(5) { 40 } + List(12) { 28 }
        val p = Predictor.predict(history { cycles(start, lengths) }, date("2028-01-01"))
        assertEquals(12, p.stats.validCycleCount)
        assertEquals(28.0, p.stats.averageCycleLength, 1e-9)
    }

    @Test
    fun `a one-day gap in a period keeps a single episode`() {
        val logs = history {
            period(start, days = 5, skip = setOf(2))
            period(start.plusDays(28), days = 5, skip = setOf(1))
            period(start.plusDays(56), days = 5)
        }
        val p = Predictor.predict(logs, start.plusDays(60))
        assertEquals(3, p.cycles.size)
        assertEquals(5, p.stats.averagePeriodLength)
        assertEquals(28.0, p.stats.averageCycleLength, 1e-9)
    }

    // --- Lateness, phases, forecast -------------------------------------------------------------

    @Test
    fun `a late period slides the prediction to today`() {
        val starts = TestHistory().cycles(start, List(4) { 28 })
        val logs = history { cycles(start, List(4) { 28 }) }
        val expected = starts.last().plusDays(28)
        val today = expected.plusDays(3)
        val c = Predictor.predict(logs, today).current!!
        assertEquals(today, c.nextPeriod.date)
        assertEquals(3, c.lateByDays)
        assertTrue(c.isLate)
        assertEquals(CyclePhase.LATE, c.phase)
        assertEquals(c.dayOfCycle, c.expectedLength)
        assertEquals(expected, c.statisticalNextPeriod)
    }

    @Test
    fun `the expected day itself is not late`() {
        val starts = TestHistory().cycles(start, List(4) { 28 })
        val logs = history { cycles(start, List(4) { 28 }) }
        val expected = starts.last().plusDays(28)
        val c = Predictor.predict(logs, expected).current!!
        assertEquals(0, c.lateByDays)
        assertEquals(expected, c.nextPeriod.date)
    }

    @Test
    fun `phases follow the cycle`() {
        val logs = history { cycles(start, List(4) { 28 }) }
        val s = TestHistory().cycles(start, List(4) { 28 }).last()
        fun phase(day: Int) = Predictor.predict(logs, s.plusDays(day - 1L)).current!!.phase
        assertEquals(CyclePhase.MENSTRUATION, phase(1))
        assertEquals(CyclePhase.FOLLICULAR, phase(7))
        assertEquals(CyclePhase.FERTILE, phase(10))
        assertEquals(CyclePhase.OVULATION, phase(15))
        assertEquals(CyclePhase.LUTEAL, phase(18))
        assertEquals(CyclePhase.PMS, phase(26))
    }

    @Test
    fun `forecast lists upcoming periods and fertile windows`() {
        val logs = history { cycles(start, List(4) { 28 }) }
        val s = TestHistory().cycles(start, List(4) { 28 }).last()
        val p = Predictor.predict(logs, s.plusDays(20))
        assertEquals(Predictor.FORECAST_CYCLES, p.upcomingPeriods.size)
        assertEquals(s.plusDays(28), p.upcomingPeriods[0].start)
        assertEquals(s.plusDays(32), p.upcomingPeriods[0].endInclusive)
        assertEquals(s.plusDays(56), p.upcomingPeriods[1].start)
        // The current window (days 10–15) is over: next one is in the following cycle.
        assertEquals(s.plusDays(28 + 14), p.nextOvulation)
        assertEquals(s.plusDays(28 + 9), p.nextFertileWindow!!.range.start)
    }

    @Test
    fun `default PMS range is the last 5 days`() {
        val logs = history { cycles(start, List(4) { 28 }) }
        val s = TestHistory().cycles(start, List(4) { 28 }).last()
        val c = Predictor.predict(logs, s.plusDays(3)).current!!
        assertFalse(c.pmsFromSignal)
        assertEquals(s.plusDays(23), c.pmsRange!!.start)
        assertEquals(s.plusDays(27), c.pmsRange.endInclusive)
    }

    // --- PMS mood signal ------------------------------------------------------------------------

    /** 4 regular cycles of 28 days, low mood [offset] days before each period start. */
    private fun pmsHistory(offsets: List<Int?>, extra: TestHistory.(List<LocalDate>) -> Unit = {}): List<DayLog> {
        val h = TestHistory()
        val starts = h.cycles(start, List(offsets.size) { 28 })
        offsets.forEachIndexed { i, offset ->
            val next = starts[i + 1]
            // a neutral mood in the window so the cycle counts as analysed
            h.mood(next.minusDays(7), Mood.NEUTRAL)
            if (offset != null) h.mood(next.minusDays(offset.toLong()), Mood.SAD)
        }
        h.extra(starts)
        return h.build()
    }

    @Test
    fun `reliable PMS signal is detected`() {
        val p = Predictor.predict(pmsHistory(listOf(2, 2, 2, 2)), date("2026-06-01"))
        val s = p.pmsSignal
        assertEquals(4, s.cyclesAnalyzed)
        assertEquals(4, s.cyclesWithSignal)
        assertEquals(2.0, s.medianOffsetDays)
        assertTrue(s.isReliable)
    }

    @Test
    fun `the first low mood of the window is used`() {
        val logs = pmsHistory(listOf(3, 3, 3)) { starts ->
            starts.drop(1).forEach { mood(it.minusDays(1), Mood.CRYING) }
        }
        val s = Predictor.predict(logs, date("2026-06-01")).pmsSignal
        assertEquals(3.0, s.medianOffsetDays)
    }

    @Test
    fun `a day counts as low mood when any of its moods is low`() {
        val h = TestHistory()
        val starts = h.cycles(start, List(4) { 28 })
        starts.drop(1).forEach { next ->
            h.mood(next.minusDays(4), Mood.ANNOYED) // not low: analysed only
            h.mood(next.minusDays(2), Mood.JOYFUL, Mood.ANXIOUS)
        }
        val s = Predictor.predict(h.build(), date("2026-06-01")).pmsSignal
        assertEquals(4, s.cyclesAnalyzed)
        assertEquals(2.0, s.medianOffsetDays)
        assertTrue(s.isReliable)
        assertEquals(
            setOf(Mood.SAD, Mood.DRAINED, Mood.FRAGILE, Mood.CRYING, Mood.ANXIOUS),
            Mood.entries.filter { it.isLow }.toSet(),
        )
    }

    @Test
    fun `a moderate irritability counts as low mood, a slight one does not`() {
        val h = TestHistory()
        val starts = h.cycles(start, List(4) { 28 })
        starts.drop(1).forEach { next ->
            h.irritability(next.minusDays(5), 1) // slight: analysed only
            h.irritability(next.minusDays(3), SymptomLog.PMS_IRRITABILITY_THRESHOLD)
        }
        val s = Predictor.predict(h.build(), date("2026-06-01")).pmsSignal
        assertEquals(4, s.cyclesAnalyzed)
        assertEquals(4, s.cyclesWithSignal)
        assertEquals(3.0, s.medianOffsetDays)
        assertTrue(s.isReliable)
    }

    @Test
    fun `a noted irritability of 0 makes a cycle analysed`() {
        val logs = history {
            cycles(start, List(4) { 28 }).drop(1).forEach { irritability(it.minusDays(2), 0) }
        }
        val s = Predictor.predict(logs, date("2026-06-01")).pmsSignal
        assertEquals(4, s.cyclesAnalyzed)
        assertEquals(0, s.cyclesWithSignal)
    }

    @Test
    fun `PMS signal needs at least 3 cycles`() {
        val s = Predictor.predict(pmsHistory(listOf(2, 2)), date("2026-06-01")).pmsSignal
        assertEquals(2, s.cyclesWithSignal)
        assertFalse(s.isReliable)
    }

    @Test
    fun `PMS signal needs at least half of the analysed cycles`() {
        val s = Predictor.predict(pmsHistory(listOf(2, 2, 2, null, null, null, null)), date("2027-01-01")).pmsSignal
        assertEquals(7, s.cyclesAnalyzed)
        assertEquals(3, s.cyclesWithSignal)
        assertFalse(s.isReliable)
        val s2 = Predictor.predict(pmsHistory(listOf(2, 2, 2, null, null, null)), date("2027-01-01")).pmsSignal
        assertTrue(s2.isReliable)
    }

    @Test
    fun `scattered PMS signal is not reliable`() {
        val s = Predictor.predict(pmsHistory(listOf(1, 4, 7, 2)), date("2026-06-01")).pmsSignal
        assertEquals(4, s.cyclesWithSignal)
        assertTrue(s.standardDeviation!! > Predictor.PMS_MAX_STD_DEV)
        assertFalse(s.isReliable)
    }

    @Test
    fun `cycles without any mood in the window are not analysed`() {
        val logs = history { cycles(start, List(4) { 28 }) }
        val s = Predictor.predict(logs, date("2026-06-01")).pmsSignal
        assertEquals(0, s.cyclesAnalyzed)
        assertFalse(s.isReliable)
    }

    @Test
    fun `a reliable signal and a low mood refine the next period`() {
        var s = start
        val logs = pmsHistory(listOf(2, 2, 2, 2)) { starts ->
            s = starts.last()
            mood(s.plusDays(24), Mood.SAD) // statistical date is s+28, mood estimate s+26
        }
        val p = Predictor.predict(logs, s.plusDays(25))
        val c = p.current!!
        assertTrue(c.refinedByMood)
        assertEquals(s.plusDays(24), c.refinementMoodDate)
        assertEquals(s.plusDays(28), c.statisticalNextPeriod)
        // both variances are floored to 0.25 → plain average of s+28 and s+26
        assertEquals(s.plusDays(27), c.nextPeriod.date)
        assertEquals(1, c.nextPeriod.marginDays)
        // the following cycle's ovulation moves too
        assertEquals(s.plusDays(27 + 28 - 14), p.fertileWindows[1].ovulation)
        // PMS range follows the signal (median 2 days)
        assertTrue(c.pmsFromSignal)
        assertEquals(s.plusDays(25), c.pmsRange!!.start)
    }

    @Test
    fun `an irritable day at the end of the cycle refines the next period`() {
        var s = start
        val logs = pmsHistory(listOf(2, 2, 2, 2)) { starts ->
            s = starts.last()
            irritability(s.plusDays(24), 3)
        }
        val c = Predictor.predict(logs, s.plusDays(25)).current!!
        assertTrue(c.refinedByMood)
        assertEquals(s.plusDays(24), c.refinementMoodDate)
        assertEquals(s.plusDays(27), c.nextPeriod.date)
    }

    @Test
    fun `a low mood before the end-of-cycle window is ignored`() {
        var s = start
        val logs = pmsHistory(listOf(2, 2, 2, 2)) { starts ->
            s = starts.last()
            mood(s.plusDays(15), Mood.SAD) // before s+28-8
        }
        val c = Predictor.predict(logs, s.plusDays(22)).current!!
        assertFalse(c.refinedByMood)
        assertEquals(s.plusDays(28), c.nextPeriod.date)
    }

    @Test
    fun `an unreliable signal never refines`() {
        var s = start
        val logs = pmsHistory(listOf(2, 2)) { starts ->
            s = starts.last()
            mood(s.plusDays(24), Mood.SAD)
        }
        val c = Predictor.predict(logs, s.plusDays(25)).current!!
        assertFalse(c.refinedByMood)
    }

    @Test
    fun `prediction is deterministic`() {
        val logs = pmsHistory(listOf(2, 3, 2, 2))
        val today = date("2026-06-01")
        assertEquals(Predictor.predict(logs, today), Predictor.predict(logs.shuffled(java.util.Random(4)), today))
    }
}
