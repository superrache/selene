package app.selene.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.selene.R
import app.selene.domain.model.CyclePhase
import app.selene.domain.model.Mood
import app.selene.domain.model.Reliability
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** French date formatting. "1er" for the first day of the month, as written in French. */
object DateFormats {
    private val locale = Locale.FRENCH
    private val month = DateTimeFormatter.ofPattern("MMMM", locale)
    private val shortMonth = DateTimeFormatter.ofPattern("MMM", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", locale)
    private val shortWeekday = DateTimeFormatter.ofPattern("EEE", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    private fun day(date: LocalDate) = if (date.dayOfMonth == 1) "1er" else date.dayOfMonth.toString()

    /** "9 novembre" */
    fun dayMonth(date: LocalDate): String = "${day(date)} ${month.format(date)}"

    /** "9 novembre 2026" */
    fun dayMonthYear(date: LocalDate): String = "${dayMonth(date)} ${date.year}"

    /** "lundi 9 novembre" */
    fun weekdayDayMonth(date: LocalDate): String = "${weekday.format(date)} ${dayMonth(date)}"

    /** "Lundi 9 novembre 2026" */
    fun full(date: LocalDate): String = "${weekday.format(date)} ${dayMonthYear(date)}".capitalized()

    /** "mar. 25 nov." */
    fun short(date: LocalDate): String = "${shortWeekday.format(date)} ${day(date)} ${shortMonth.format(date)}"

    /** "9 nov." */
    fun dayShortMonth(date: LocalDate): String = "${day(date)} ${shortMonth.format(date)}"

    /** "Novembre 2026" */
    fun monthYear(month: YearMonth): String = monthYear.format(month).capitalized()

    fun shortMonth(month: YearMonth): String = shortMonth.format(month)

    private fun String.capitalized() = replaceFirstChar { it.titlecase(locale) }
}

/** "9,4" with French decimal comma, one decimal at most. */
fun formatDecimal(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded == Math.floor(rounded)) rounded.toLong().toString()
    else String.format(Locale.FRENCH, "%.1f", rounded)
}

fun daysUntil(today: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

/** "aujourd’hui", "dans 3 jours", "il y a 2 jours". */
@Composable
fun relativeDays(today: LocalDate, date: LocalDate): String {
    val n = daysUntil(today, date).toInt()
    return when {
        n == 0 -> stringResource(R.string.today_relative)
        n > 0 -> pluralStringResource(R.plurals.in_days, n, n)
        else -> pluralStringResource(R.plurals.days_ago, -n, -n)
    }
}

@StringRes
fun Mood.labelRes(): Int = when (this) {
    Mood.VERY_BAD -> R.string.mood_1
    Mood.BAD -> R.string.mood_2
    Mood.NEUTRAL -> R.string.mood_3
    Mood.GOOD -> R.string.mood_4
    Mood.VERY_GOOD -> R.string.mood_5
}

@StringRes
fun flowLabelRes(flow: Int): Int = when (flow) {
    1 -> R.string.flow_1
    2 -> R.string.flow_2
    3 -> R.string.flow_3
    else -> R.string.flow_4
}

@StringRes
fun levelLabelRes(level: Int): Int = when (level) {
    0 -> R.string.level_0
    1 -> R.string.level_1
    2 -> R.string.level_2
    3 -> R.string.level_3
    else -> R.string.level_4
}

@StringRes
fun CyclePhase.labelRes(): Int = when (this) {
    CyclePhase.MENSTRUATION -> R.string.phase_menstruation
    CyclePhase.FOLLICULAR -> R.string.phase_follicular
    CyclePhase.FERTILE -> R.string.phase_fertile
    CyclePhase.OVULATION -> R.string.phase_ovulation
    CyclePhase.LUTEAL -> R.string.phase_luteal
    CyclePhase.PMS -> R.string.phase_pms
    CyclePhase.LATE -> R.string.phase_late
}

@StringRes
fun Reliability.labelRes(): Int = when (this) {
    Reliability.LOW -> R.string.reliability_low
    Reliability.MEDIUM -> R.string.reliability_medium
    Reliability.GOOD -> R.string.reliability_good
    Reliability.VERY_GOOD -> R.string.reliability_very_good
}

@Composable
fun plural(@PluralsRes id: Int, count: Int): String = pluralStringResource(id, count, count)

@Composable
fun appContainer(): app.selene.AppContainer = LocalAppContainer.current ?: (LocalContext.current.applicationContext as app.selene.SeleneApp).container
