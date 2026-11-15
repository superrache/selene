package app.selene.domain

import app.selene.domain.model.Cycle
import app.selene.domain.model.PeriodEpisode
import java.time.LocalDate

/** Splits the period history into episodes and cycles. Pure and deterministic. */
object CycleCalculator {
    /** Two period days at most this many days apart belong to the same episode (one missing day tolerated). */
    const val MAX_DAYS_BETWEEN_PERIOD_DAYS = 2L

    fun episodes(periodDays: Collection<LocalDate>): List<PeriodEpisode> {
        val sorted = periodDays.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val result = mutableListOf<PeriodEpisode>()
        var start = sorted.first()
        var end = start
        for (day in sorted.drop(1)) {
            if (!day.isAfter(end.plusDays(MAX_DAYS_BETWEEN_PERIOD_DAYS))) {
                end = day
            } else {
                result += PeriodEpisode(start, end)
                start = day
                end = day
            }
        }
        result += PeriodEpisode(start, end)
        return result
    }

    /** One cycle per episode, oldest first; the last one is open (current cycle). */
    fun cycles(periodDays: Collection<LocalDate>): List<Cycle> {
        val episodes = episodes(periodDays)
        return episodes.mapIndexed { index, episode ->
            Cycle(episode, episodes.getOrNull(index + 1)?.start)
        }
    }
}
