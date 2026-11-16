package app.selene.domain.model

import java.time.LocalDate

enum class Reliability { LOW, MEDIUM, GOOD, VERY_GOOD }

/** Statistics over the most recent valid cycles (at most 12). */
data class CycleStats(
    /** Weighted mean cycle length, in days. */
    val averageCycleLength: Double,
    /** Weighted standard deviation, null when using defaults. */
    val standardDeviation: Double?,
    val averagePeriodLength: Int,
    /** Number of valid cycles used (0–12). */
    val validCycleCount: Int,
    /** True when fewer than 2 valid cycles: defaults (28 / 5 days) are used. */
    val usesDefaults: Boolean,
)

/** PMS signal: first low-mood day in the 7 days before each past period. */
data class PmsSignal(
    /** Past cycles that have at least one mood or irritability entry in the 7 days before the next period. */
    val cyclesAnalyzed: Int,
    /** Cycles among those with a low-mood day ([SymptomLog.isPmsSignalDay]) in that window. */
    val cyclesWithSignal: Int,
    /** Median gap, in days, between the first low mood and the period start. */
    val medianOffsetDays: Double?,
    val standardDeviation: Double?,
    val isReliable: Boolean,
) {
    companion object {
        val NONE = PmsSignal(0, 0, null, null, false)
    }
}

/** An estimated date with its uncertainty (± [marginDays]). */
data class DateEstimate(val date: LocalDate, val marginDays: Int) {
    val range: DateRange get() = DateRange(date.minusDays(marginDays.toLong()), date.plusDays(marginDays.toLong()))
}

data class FertileWindow(val ovulation: LocalDate, val range: DateRange)

enum class CyclePhase { MENSTRUATION, FOLLICULAR, FERTILE, OVULATION, LUTEAL, PMS, LATE }

data class CurrentCycle(
    val start: LocalDate,
    /** Logged period days of the current cycle. */
    val episode: PeriodEpisode,
    /** 1-based day of the cycle for "today". */
    val dayOfCycle: Int,
    /** Expected total length: days between [start] and the next period estimate (≥ [dayOfCycle]). */
    val expectedLength: Int,
    /** Next period estimate (refined by mood when applicable, moved to today when late). */
    val nextPeriod: DateEstimate,
    /** Purely statistical estimate, before mood refinement and lateness. */
    val statisticalNextPeriod: LocalDate,
    /** True when the PMS mood signal was used to refine [nextPeriod]. */
    val refinedByMood: Boolean,
    /** Low-mood day of the current cycle used for the refinement. */
    val refinementMoodDate: LocalDate?,
    /** Days since the expected date with no period logged; 0 when not late. */
    val lateByDays: Int,
    /** Fertile window of the current cycle (ovulation = next period − 14 days). */
    val fertileWindow: FertileWindow,
    /** "Probable PMS" days at the end of the cycle. */
    val pmsRange: DateRange?,
    /** True when [pmsRange] comes from a reliable mood signal, false when it is the 5-day default. */
    val pmsFromSignal: Boolean,
    val phase: CyclePhase,
) {
    val isLate: Boolean get() = lateByDays > 0
}

data class Prediction(
    val today: LocalDate,
    /** All cycles, oldest first, including invalid ones (displayed but not used in statistics). */
    val cycles: List<Cycle>,
    val stats: CycleStats,
    val reliability: Reliability,
    val pmsSignal: PmsSignal,
    /** Null when no period has ever been logged. */
    val current: CurrentCycle?,
    /** Predicted future periods, the first one being [CurrentCycle.nextPeriod]. */
    val upcomingPeriods: List<DateRange>,
    /** Fertile windows of the current cycle and the following predicted cycles. */
    val fertileWindows: List<FertileWindow>,
) {
    val hasData: Boolean get() = current != null

    /**
     * Next predicted period (used for the calendar event). Starts today at the earliest: when the
     * period is late, the prediction slides to today.
     */
    val nextPeriodRange: DateRange?
        get() = upcomingPeriods.firstOrNull()

    /** First fertile window that is not over yet. */
    val nextFertileWindow: FertileWindow?
        get() = fertileWindows.firstOrNull { !it.range.endInclusive.isBefore(today) }

    /** Next ovulation estimate, today or later. */
    val nextOvulation: LocalDate?
        get() = fertileWindows.firstOrNull { !it.ovulation.isBefore(today) }?.ovulation
}
