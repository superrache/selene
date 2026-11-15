package app.selene.domain.model

import java.time.LocalDate

/** Period data for one day. flow 1–4 (light … very heavy), pain and fatigue 0–4. */
data class PeriodLog(
    val date: LocalDate,
    val flow: Int,
    val pain: Int,
    val fatigue: Int,
) {
    init {
        require(flow in FLOW_RANGE) { "flow out of range" }
        require(pain in LEVEL_RANGE) { "pain out of range" }
        require(fatigue in LEVEL_RANGE) { "fatigue out of range" }
    }

    companion object {
        val FLOW_RANGE = 1..4
        val LEVEL_RANGE = 0..4
    }
}

/** Mood of the day, 1 (very bad) … 5 (very good). */
enum class Mood(val value: Int) {
    VERY_BAD(1),
    BAD(2),
    NEUTRAL(3),
    GOOD(4),
    VERY_GOOD(5),
    ;

    /** "Low mood" as used by the PMS signal (mood ≤ 2). */
    val isLow: Boolean get() = value <= LOW_THRESHOLD

    companion object {
        const val LOW_THRESHOLD = 2
        fun fromValue(value: Int): Mood? = entries.firstOrNull { it.value == value }
    }
}

data class MoodLog(val date: LocalDate, val mood: Mood)

/** A notable event ("événement remarquable"), several per day allowed. */
data class NotableEvent(
    val id: String,
    val date: LocalDate,
    val title: String,
) {
    init {
        require(title.isNotBlank() && title.length <= TITLE_MAX_LENGTH) { "invalid title length" }
    }

    companion object {
        const val TITLE_MAX_LENGTH = 80
    }
}

/** Everything logged on one day. */
data class DayLog(
    val date: LocalDate,
    val period: PeriodLog? = null,
    val mood: Mood? = null,
    val events: List<NotableEvent> = emptyList(),
) {
    init {
        require(period == null || period.date == date) { "period date mismatch" }
        require(events.all { it.date == date }) { "event date mismatch" }
    }

    val isEmpty: Boolean get() = period == null && mood == null && events.isEmpty()

    /** Central invariant: a period day always has a mood. */
    val respectsMoodInvariant: Boolean get() = period == null || mood != null
}
