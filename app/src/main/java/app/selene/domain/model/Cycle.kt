package app.selene.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Inclusive range of dates. */
data class DateRange(val start: LocalDate, val endInclusive: LocalDate) {
    init {
        require(!endInclusive.isBefore(start)) { "empty range" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    val lengthInDays: Int get() = daysBetween(start, endInclusive) + 1

    fun dates(): Sequence<LocalDate> = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(endInclusive) }
}

/** Consecutive period days (a one-day gap is tolerated). */
data class PeriodEpisode(val start: LocalDate, val end: LocalDate) {
    val length: Int get() = daysBetween(start, end) + 1
    val range: DateRange get() = DateRange(start, end)
}

/**
 * A cycle runs from the first day of an episode to the day before the next episode.
 * The last cycle of the history is the current one: [nextStart] is null.
 */
data class Cycle(
    val episode: PeriodEpisode,
    val nextStart: LocalDate?,
) {
    val start: LocalDate get() = episode.start
    val end: LocalDate? get() = nextStart?.minusDays(1)
    val length: Int? get() = nextStart?.let { daysBetween(start, it) }
    val periodLength: Int get() = episode.length
    val isComplete: Boolean get() = nextStart != null

    /** Complete cycles whose length is plausible; others are likely missing entries. */
    val isValid: Boolean get() = length?.let { it in VALID_LENGTH } ?: false

    companion object {
        val VALID_LENGTH = 15..60
    }
}

internal fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()
