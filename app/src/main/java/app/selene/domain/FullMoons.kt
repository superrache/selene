package app.selene.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Full moon instants, computed locally with the lunar phase algorithm from Jean Meeus,
 * "Astronomical Algorithms" (2nd ed., ch. 49), accurate to well under a minute for current dates.
 *
 * The full moon is a single instant for the whole Earth: no location is needed, only the time zone
 * to decide on which calendar day it falls.
 */
object FullMoons {

    private const val SYNODIC_MONTH = 29.530588861
    private const val JD_UNIX_EPOCH = 2440587.5
    private const val SECONDS_PER_DAY = 86_400.0

    /** Full moons whose local date (in [zone]) lies in [from]..[to], keyed by that date. */
    fun between(from: LocalDate, to: LocalDate, zone: ZoneId): Map<LocalDate, ZonedDateTime> {
        if (to.isBefore(from)) return emptyMap()
        // Lunation number (k = 0 is the new moon of 2000-01-06); full moons are at k + 0.5.
        val firstK = floor(lunation(from.minusDays(2))) - 1
        val result = LinkedHashMap<LocalDate, ZonedDateTime>()
        var k = firstK
        while (true) {
            val moment = instant(k + 0.5).atZone(zone)
            val date = moment.toLocalDate()
            if (date.isAfter(to)) break
            if (!date.isBefore(from)) result[date] = moment
            k += 1
        }
        return result
    }

    /** The full moon falling on [date] in [zone], if any. */
    fun on(date: LocalDate, zone: ZoneId): ZonedDateTime? = between(date, date, zone)[date]

    /** Approximate (fractional) lunation number for a date; only used to pick a starting point. */
    private fun lunation(date: LocalDate): Double {
        val year = date.year + (date.dayOfYear - 0.5) / date.lengthOfYear()
        return (year - 2000) * 12.3685
    }

    /** Instant of the phase with lunation number [k] (k + 0.5 = full moon). Meeus 49.1 and table 49.A. */
    internal fun instant(k: Double): Instant {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t
        var jde = 2451550.09766 + SYNODIC_MONTH * k + 0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t4

        val e = 1 - 0.002516 * t - 0.0000074 * t2
        val m = rad(2.5534 + 29.10535670 * k - 0.0000014 * t2 - 0.00000011 * t3)
        val mp = rad(201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4)
        val f = rad(160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4)
        val om = rad(124.7746 - 1.56375588 * k + 0.0020672 * t2 + 0.00000215 * t3)

        jde += -0.40614 * sin(mp) +
            0.17302 * e * sin(m) +
            0.01614 * sin(2 * mp) +
            0.01043 * sin(2 * f) +
            0.00734 * e * sin(mp - m) -
            0.00515 * e * sin(mp + m) +
            0.00209 * e * e * sin(2 * m) -
            0.00111 * sin(mp - 2 * f) -
            0.00057 * sin(mp + 2 * f) +
            0.00056 * e * sin(2 * mp + m) -
            0.00042 * sin(3 * mp) +
            0.00042 * e * sin(m + 2 * f) +
            0.00038 * e * sin(m - 2 * f) -
            0.00024 * e * sin(2 * mp - m) -
            0.00017 * sin(om) -
            0.00007 * sin(mp + 2 * m) +
            0.00004 * sin(2 * mp - 2 * f) +
            0.00004 * sin(3 * m) +
            0.00003 * sin(mp + m - 2 * f) +
            0.00003 * sin(2 * mp + 2 * f) -
            0.00003 * sin(mp + m + 2 * f) +
            0.00003 * sin(mp - m + 2 * f) -
            0.00002 * sin(mp - m - 2 * f) -
            0.00002 * sin(3 * mp + m) +
            0.00002 * sin(4 * mp)

        // Planetary arguments, common to all phases.
        jde += 0.000325 * sin(rad(299.77 + 0.107408 * k - 0.009173 * t2)) +
            0.000165 * sin(rad(251.88 + 0.016321 * k)) +
            0.000164 * sin(rad(251.83 + 26.651886 * k)) +
            0.000126 * sin(rad(349.42 + 36.412478 * k)) +
            0.000110 * sin(rad(84.66 + 18.206239 * k)) +
            0.000062 * sin(rad(141.74 + 53.303771 * k)) +
            0.000060 * sin(rad(207.14 + 2.453732 * k)) +
            0.000056 * sin(rad(154.84 + 7.306860 * k)) +
            0.000047 * sin(rad(34.52 + 27.261239 * k)) +
            0.000042 * sin(rad(207.19 + 0.121824 * k)) +
            0.000040 * sin(rad(291.34 + 1.844379 * k)) +
            0.000037 * sin(rad(161.72 + 24.198154 * k)) +
            0.000035 * sin(rad(239.56 + 25.513099 * k)) +
            0.000023 * sin(rad(331.55 + 3.592518 * k))

        // JDE is in Terrestrial Time; convert to UT with ΔT.
        val year = 2000 + k / 12.3685
        val jdUt = jde - deltaTSeconds(year) / SECONDS_PER_DAY
        return Instant.ofEpochSecond(((jdUt - JD_UNIX_EPOCH) * SECONDS_PER_DAY).roundToLong())
    }

    /**
     * ΔT = TT − UT, Espenak & Meeus polynomial for 2005–2050. Outside that range the error stays
     * around a minute for the years this app can show, which is far below a day.
     */
    private fun deltaTSeconds(year: Double): Double {
        val t = year - 2000
        return 62.92 + 0.32217 * t + 0.005589 * t * t
    }

    private fun rad(degrees: Double) = Math.toRadians(degrees % 360.0)
}
