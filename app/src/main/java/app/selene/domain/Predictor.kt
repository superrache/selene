package app.selene.domain

import app.selene.domain.model.Cycle
import app.selene.domain.model.CycleStats
import app.selene.domain.model.CurrentCycle
import app.selene.domain.model.CyclePhase
import app.selene.domain.model.DateEstimate
import app.selene.domain.model.DateRange
import app.selene.domain.model.DayLog
import app.selene.domain.model.FertileWindow
import app.selene.domain.model.Mood
import app.selene.domain.model.PmsSignal
import app.selene.domain.model.Prediction
import app.selene.domain.model.Reliability
import app.selene.domain.model.daysBetween
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Cycle statistics and predictions. Pure, deterministic, no side effects:
 * the same history and the same "today" always give the same [Prediction].
 *
 * Choices not fixed by CLAUDE.md, validated with the product owner:
 * - recent cycles weigh more: linear weights, oldest = 1 … most recent = n;
 * - the standard deviation uses the same weights;
 * - reliability: LOW < 2 valid cycles; VERY_GOOD ≥ 6 cycles and σ ≤ 2 d; GOOD ≥ 4 cycles and σ ≤ 4 d; MEDIUM otherwise;
 * - margin when defaults are used: ± 3 days;
 * - "probable PMS" arc: from the median mood offset when the signal is reliable, else the last 5 days of the cycle.
 */
object Predictor {
    const val LUTEAL_DAYS = 14
    const val FERTILE_DAYS_BEFORE_OVULATION = 5
    const val DEFAULT_CYCLE_LENGTH = 28
    const val DEFAULT_PERIOD_LENGTH = 5
    const val DEFAULT_MARGIN_DAYS = 3
    const val MAX_CYCLES_FOR_STATS = 12
    const val MIN_CYCLES_FOR_STATS = 2

    const val PMS_LOOKBACK_DAYS = 7
    const val PMS_MIN_CYCLES = 3
    const val PMS_MAX_STD_DEV = 1.5
    const val PMS_CURRENT_CYCLE_WINDOW_DAYS = 8
    const val DEFAULT_PMS_DAYS = 5

    /** Number of future cycles forecast for the calendar. */
    const val FORECAST_CYCLES = 6

    /** Floor for variances in the inverse-variance combination (avoids dividing by zero). */
    private const val MIN_VARIANCE = 0.25

    fun predict(logs: List<DayLog>, today: LocalDate): Prediction {
        val periodDays = logs.filter { it.period != null }.map { it.date }
        val moods: Map<LocalDate, Mood> = logs.mapNotNull { log -> log.mood?.let { log.date to it } }.toMap()
        val cycles = CycleCalculator.cycles(periodDays)
        val statCycles = cycles.filter { it.isValid }.takeLast(MAX_CYCLES_FOR_STATS)
        val stats = statistics(statCycles)
        val reliability = reliability(stats)
        val pmsSignal = pmsSignal(statCycles, moods)

        val currentCycle = cycles.lastOrNull()
            ?: return Prediction(today, cycles, stats, reliability, pmsSignal, null, emptyList(), emptyList())

        val current = currentCycle(currentCycle, stats, pmsSignal, moods, today)
        val cycleLength = stats.averageCycleLength.roundToInt()
        val nextStart = current.nextPeriod.date
        val upcomingPeriods = (0 until FORECAST_CYCLES).map { k ->
            val start = nextStart.plusDays(k.toLong() * cycleLength)
            DateRange(start, start.plusDays(stats.averagePeriodLength - 1L))
        }
        val fertileWindows = listOf(current.fertileWindow) + (1..FORECAST_CYCLES).map { k ->
            fertileWindowBefore(nextStart.plusDays(k.toLong() * cycleLength))
        }
        return Prediction(today, cycles, stats, reliability, pmsSignal, current, upcomingPeriods, fertileWindows)
    }

    // --- Statistics -----------------------------------------------------------------------------

    internal fun statistics(validCycles: List<Cycle>): CycleStats {
        if (validCycles.size < MIN_CYCLES_FOR_STATS) {
            return CycleStats(
                averageCycleLength = DEFAULT_CYCLE_LENGTH.toDouble(),
                standardDeviation = null,
                averagePeriodLength = DEFAULT_PERIOD_LENGTH,
                validCycleCount = validCycles.size,
                usesDefaults = true,
            )
        }
        val lengths = validCycles.map { it.length!!.toDouble() }
        val weights = lengths.indices.map { (it + 1).toDouble() }
        val totalWeight = weights.sum()
        val mean = lengths.zip(weights).sumOf { (l, w) -> l * w } / totalWeight
        val variance = lengths.zip(weights).sumOf { (l, w) -> w * (l - mean) * (l - mean) } / totalWeight
        val periodLength = validCycles.map { it.periodLength }.average().roundToInt().coerceAtLeast(1)
        return CycleStats(
            averageCycleLength = mean,
            standardDeviation = sqrt(variance),
            averagePeriodLength = periodLength,
            validCycleCount = validCycles.size,
            usesDefaults = false,
        )
    }

    internal fun reliability(stats: CycleStats): Reliability {
        val sd = stats.standardDeviation
        val n = stats.validCycleCount
        return when {
            stats.usesDefaults || sd == null -> Reliability.LOW
            n >= 6 && sd <= 2.0 -> Reliability.VERY_GOOD
            n >= 4 && sd <= 4.0 -> Reliability.GOOD
            else -> Reliability.MEDIUM
        }
    }

    // --- PMS mood signal ------------------------------------------------------------------------

    internal fun pmsSignal(pastCycles: List<Cycle>, moods: Map<LocalDate, Mood>): PmsSignal {
        var analyzed = 0
        val offsets = mutableListOf<Int>()
        for (cycle in pastCycles) {
            val periodStart = cycle.nextStart ?: continue
            val window = (PMS_LOOKBACK_DAYS downTo 1).map { periodStart.minusDays(it.toLong()) }
            if (window.none { it in moods }) continue
            analyzed++
            val firstLow = window.firstOrNull { moods[it]?.isLow == true } ?: continue
            offsets += daysBetween(firstLow, periodStart)
        }
        if (offsets.isEmpty()) return PmsSignal(analyzed, 0, null, null, false)
        val median = median(offsets)
        val mean = offsets.average()
        val sd = sqrt(offsets.sumOf { (it - mean) * (it - mean) } / offsets.size)
        val reliable = offsets.size >= PMS_MIN_CYCLES && offsets.size * 2 >= analyzed && sd <= PMS_MAX_STD_DEV
        return PmsSignal(analyzed, offsets.size, median, sd, reliable)
    }

    private fun median(values: List<Int>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    // --- Current cycle --------------------------------------------------------------------------

    private fun currentCycle(
        cycle: Cycle,
        stats: CycleStats,
        signal: PmsSignal,
        moods: Map<LocalDate, Mood>,
        today: LocalDate,
    ): CurrentCycle {
        val start = cycle.start
        val statDate = start.plusDays(stats.averageCycleLength.roundToLong())
        val statVariance = stats.standardDeviation?.let { it * it }
        val statMargin = stats.standardDeviation?.let { max(1, it.roundToInt()) } ?: DEFAULT_MARGIN_DAYS

        var estimate = DateEstimate(statDate, statMargin)
        var moodDate: LocalDate? = null
        if (signal.isReliable && statVariance != null) {
            val windowStart = maxOf(statDate.minusDays(PMS_CURRENT_CYCLE_WINDOW_DAYS.toLong()), cycle.episode.end.plusDays(1))
            moodDate = moods.keys
                .filter { !it.isBefore(windowStart) && !it.isAfter(today) && moods[it]!!.isLow }
                .minOrNull()
            if (moodDate != null) {
                val moodEstimate = moodDate.toEpochDay() + signal.medianOffsetDays!!
                val vStat = max(statVariance, MIN_VARIANCE)
                val vMood = max(signal.standardDeviation!! * signal.standardDeviation, MIN_VARIANCE)
                val combined = (statDate.toEpochDay() / vStat + moodEstimate / vMood) / (1 / vStat + 1 / vMood)
                val combinedSd = sqrt(1 / (1 / vStat + 1 / vMood))
                estimate = DateEstimate(LocalDate.ofEpochDay(combined.roundToLong()), max(1, combinedSd.roundToInt()))
            }
        }

        val lateBy = if (estimate.date.isBefore(today)) daysBetween(estimate.date, today) else 0
        if (lateBy > 0) estimate = estimate.copy(date = today)

        val dayOfCycle = max(1, daysBetween(start, today) + 1)
        val expectedLength = max(dayOfCycle, daysBetween(start, estimate.date))
        val fertile = fertileWindowBefore(estimate.date)
        val pmsFromSignal = signal.isReliable && signal.medianOffsetDays != null
        val pmsDays = if (pmsFromSignal) signal.medianOffsetDays.roundToInt().coerceAtLeast(1) else DEFAULT_PMS_DAYS
        val pmsStart = maxOf(estimate.date.minusDays(pmsDays.toLong()), cycle.episode.end.plusDays(1))
        val pmsEnd = estimate.date.minusDays(1)
        val pmsRange = if (pmsStart.isAfter(pmsEnd)) null else DateRange(pmsStart, pmsEnd)

        val phase = when {
            today in cycle.episode.range -> CyclePhase.MENSTRUATION
            lateBy > 0 -> CyclePhase.LATE
            today == fertile.ovulation -> CyclePhase.OVULATION
            today in fertile.range -> CyclePhase.FERTILE
            pmsRange != null && today in pmsRange -> CyclePhase.PMS
            today.isBefore(fertile.range.start) -> CyclePhase.FOLLICULAR
            else -> CyclePhase.LUTEAL
        }

        return CurrentCycle(
            start = start,
            episode = cycle.episode,
            dayOfCycle = dayOfCycle,
            expectedLength = expectedLength,
            nextPeriod = estimate,
            statisticalNextPeriod = statDate,
            refinedByMood = moodDate != null,
            refinementMoodDate = moodDate,
            lateByDays = lateBy,
            fertileWindow = fertile,
            pmsRange = pmsRange,
            pmsFromSignal = pmsFromSignal,
            phase = phase,
        )
    }

    /** Fertile window of the cycle that ends just before [nextPeriodStart]. */
    internal fun fertileWindowBefore(nextPeriodStart: LocalDate): FertileWindow {
        val ovulation = nextPeriodStart.minusDays(LUTEAL_DAYS.toLong())
        return FertileWindow(ovulation, DateRange(ovulation.minusDays(FERTILE_DAYS_BEFORE_OVULATION.toLong()), ovulation))
    }
}
