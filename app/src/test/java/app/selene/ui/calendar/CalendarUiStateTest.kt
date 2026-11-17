package app.selene.ui.calendar

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarUiStateTest {
    @Test
    fun `full moons cover every displayed month`() {
        val today = LocalDate.of(2026, 11, 16)
        val state = CalendarUiState.build(emptyList(), null, today, ZoneId.of("Europe/Paris"))
        assertTrue(LocalDate.of(2026, 11, 24) in state.fullMoonDays)
        // 19 months shown, one full moon each, plus a second one ("blue moon") on 2026-05-31.
        assertEquals(state.months.size + 1, state.fullMoonDays.size, "${state.fullMoonDays.sorted()}")
        assertTrue(LocalDate.of(2026, 5, 1) in state.fullMoonDays && LocalDate.of(2026, 5, 31) in state.fullMoonDays)
        assertTrue(state.fullMoonDays.all { java.time.YearMonth.from(it) in state.months })
    }
}
