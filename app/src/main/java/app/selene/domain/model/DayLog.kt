package app.selene.domain.model

import java.time.LocalDate

/** Period data for one day. flow 1–4 (light … very heavy), pain 0–4. */
data class PeriodLog(
    val date: LocalDate,
    val flow: Int,
    val pain: Int,
) {
    init {
        require(flow in FLOW_RANGE) { "flow out of range" }
        require(pain in LEVEL_RANGE) { "pain out of range" }
    }

    companion object {
        val FLOW_RANGE = 1..4
        val LEVEL_RANGE = 0..4
    }
}

/**
 * Moods of the day (several allowed), each drawn as a vector face.
 * [code] is the stable identifier stored in the database and in exports: never rename it.
 * [isLow] marks the "low mood" used by the PMS signal.
 */
enum class Mood(val code: String, val isLow: Boolean) {
    JOYFUL("joyful", false), // 😁
    GOOD("good", false), // 🙂
    NEUTRAL("neutral", false), // 😐
    ANNOYED("annoyed", false), // 😒
    SAD("sad", true), // 😔
    DRAINED("drained", true), // 😫
    FRAGILE("fragile", true), // 🥺
    CRYING("crying", true), // 😭
    ANXIOUS("anxious", true), // 😰
    ;

    companion object {
        fun fromCode(code: String): Mood? = entries.firstOrNull { it.code == code }

        /** Read migration of the former 1–5 scale (JSON v1, database v1). */
        fun fromLegacyValue(value: Int): Mood? = when (value) {
            1 -> CRYING
            2 -> SAD
            3 -> NEUTRAL
            4 -> GOOD
            5 -> JOYFUL
            else -> null
        }
    }
}

/** Groups shown on the "symptoms of the day" page, in display order. */
enum class SymptomCategory { PAIN, SEX, SKIN, APPETITE, DISCHARGE, SLEEP, BODY }

/**
 * Yes/no symptoms. [code] is the stable identifier stored in the database and in exports:
 * never rename it. Display order follows declaration order.
 */
enum class Symptom(val code: String, val category: SymptomCategory) {
    CRAMPS("cramps", SymptomCategory.PAIN),
    HEADACHE("headache", SymptomCategory.PAIN),
    MIGRAINE("migraine", SymptomCategory.PAIN),
    BACK_PAIN("back_pain", SymptomCategory.PAIN),
    TENDER_BREASTS("tender_breasts", SymptomCategory.PAIN),

    LOW_LIBIDO("low_libido", SymptomCategory.SEX),
    HIGH_LIBIDO("high_libido", SymptomCategory.SEX),
    SEX("sex", SymptomCategory.SEX),

    OILY_SKIN("oily_skin", SymptomCategory.SKIN),
    PIMPLES("pimples", SymptomCategory.SKIN),
    MOUTH_ULCERS("mouth_ulcers", SymptomCategory.SKIN),
    DRY_SKIN("dry_skin", SymptomCategory.SKIN),

    CRAVINGS("cravings", SymptomCategory.APPETITE),
    LOSS_OF_APPETITE("loss_of_appetite", SymptomCategory.APPETITE),
    NAUSEA("nausea", SymptomCategory.APPETITE),
    VOMITING("vomiting", SymptomCategory.APPETITE),
    BLOATING("bloating", SymptomCategory.APPETITE),
    CONSTIPATION("constipation", SymptomCategory.APPETITE),
    DIARRHEA("diarrhea", SymptomCategory.APPETITE),

    SPOTTING("spotting", SymptomCategory.DISCHARGE),
    DISCHARGE_DRY("discharge_dry", SymptomCategory.DISCHARGE),
    DISCHARGE_CREAMY("discharge_creamy", SymptomCategory.DISCHARGE),
    DISCHARGE_EGG_WHITE("discharge_egg_white", SymptomCategory.DISCHARGE),
    DISCHARGE_UNUSUAL("discharge_unusual", SymptomCategory.DISCHARGE),

    INSOMNIA("insomnia", SymptomCategory.SLEEP),
    RESTLESS_SLEEP("restless_sleep", SymptomCategory.SLEEP),
    DROWSINESS("drowsiness", SymptomCategory.SLEEP),

    WATER_RETENTION("water_retention", SymptomCategory.BODY),
    HOT_FLASHES("hot_flashes", SymptomCategory.BODY),
    DIZZINESS("dizziness", SymptomCategory.BODY),
    ;

    companion object {
        fun fromCode(code: String): Symptom? = entries.firstOrNull { it.code == code }
    }
}

/**
 * "Symptoms of the day": at most one record per day, holding several moods and symptoms,
 * two 0–4 levels (null = not noted) and a free note ("autre").
 */
data class SymptomLog(
    val date: LocalDate,
    val moods: Set<Mood> = emptySet(),
    val symptoms: Set<Symptom> = emptySet(),
    val irritability: Int? = null,
    val fatigue: Int? = null,
    val note: String? = null,
) {
    init {
        require(irritability == null || irritability in LEVEL_RANGE) { "irritability out of range" }
        require(fatigue == null || fatigue in LEVEL_RANGE) { "fatigue out of range" }
        require(note == null || (note.isNotBlank() && note.length <= NOTE_MAX_LENGTH)) { "invalid note" }
    }

    val isEmpty: Boolean get() = moods.isEmpty() && symptoms.isEmpty() && irritability == null && fatigue == null && note == null

    val hasLowMood: Boolean get() = moods.any { it.isLow }

    /** True when the mood side of the day was noted: at least one mood, or an irritability level (even 0). */
    val hasMoodEntry: Boolean get() = moods.isNotEmpty() || irritability != null

    /**
     * "Low mood" for the PMS signal: a low mood ([Mood.isLow]) or an irritability of at least
     * [PMS_IRRITABILITY_THRESHOLD] (moderate).
     */
    val isPmsSignalDay: Boolean get() = hasLowMood || (irritability ?: 0) >= PMS_IRRITABILITY_THRESHOLD

    companion object {
        val LEVEL_RANGE = 0..4
        const val NOTE_MAX_LENGTH = 200

        /** Irritability level from which a day counts as "low mood" for the PMS signal (2 = moderate). */
        const val PMS_IRRITABILITY_THRESHOLD = 2

        /** Trims the note; blank becomes null. */
        fun normalizeNote(note: String?): String? = note?.trim()?.takeIf { it.isNotEmpty() }?.take(NOTE_MAX_LENGTH)
    }
}

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
    val symptoms: SymptomLog? = null,
    val events: List<NotableEvent> = emptyList(),
) {
    init {
        require(period == null || period.date == date) { "period date mismatch" }
        require(symptoms == null || (symptoms.date == date && !symptoms.isEmpty)) { "invalid symptoms" }
        require(events.all { it.date == date }) { "event date mismatch" }
    }

    val isEmpty: Boolean get() = period == null && symptoms == null && events.isEmpty()

    val moods: Set<Mood> get() = symptoms?.moods.orEmpty()
}
