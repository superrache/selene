package app.selene.domain

import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.PeriodLog
import java.time.LocalDate

/** Small DSL to build histories in tests. */
class TestHistory {
    private val periods = mutableSetOf<LocalDate>()
    private val moods = mutableMapOf<LocalDate, Mood>()

    fun period(start: LocalDate, days: Int = 5, skip: Set<Int> = emptySet()) {
        repeat(days) { i ->
            if (i !in skip) {
                val d = start.plusDays(i.toLong())
                periods += d
                moods.putIfAbsent(d, Mood.NEUTRAL)
            }
        }
    }

    /** Periods starting at [first] then after each of [lengths]. Returns the start dates. */
    fun cycles(first: LocalDate, lengths: List<Int>, periodDays: Int = 5): List<LocalDate> {
        val starts = lengths.runningFold(first) { d, l -> d.plusDays(l.toLong()) }
        starts.forEach { period(it, periodDays) }
        return starts
    }

    fun mood(date: LocalDate, mood: Mood) {
        moods[date] = mood
    }

    fun build(): List<DayLog> = (periods + moods.keys).sorted().map { d ->
        DayLog(
            date = d,
            period = if (d in periods) PeriodLog(d, flow = 2, pain = 1, fatigue = 1) else null,
            mood = moods[d],
        )
    }
}

fun history(block: TestHistory.() -> Unit): List<DayLog> = TestHistory().apply(block).build()

fun date(s: String): LocalDate = LocalDate.parse(s)
