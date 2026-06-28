package com.personalagent.shared.age

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgeGateTest {

    private val today = CalendarDate(2026, 6, 28)

    @Test
    fun ageInYears_basic() {
        assertEquals(26, ageInYears(CalendarDate(2000, 1, 1), today))
        assertEquals(0, ageInYears(CalendarDate(2026, 1, 1), today))
    }

    @Test
    fun ageInYears_birthday_not_yet_this_year() {
        // Born 2000-12-31; on 2026-06-28 the birthday hasn't happened yet → 25.
        assertEquals(25, ageInYears(CalendarDate(2000, 12, 31), today))
    }

    @Test
    fun ageInYears_birthday_today_counts() {
        // Born exactly 18 years ago today → 18.
        assertEquals(18, ageInYears(CalendarDate(2008, 6, 28), today))
    }

    @Test
    fun boundary_exactly_18_today_is_allowed() {
        assertTrue(meetsMinimumAge(CalendarDate(2008, 6, 28), today))
    }

    @Test
    fun boundary_18th_birthday_tomorrow_is_blocked() {
        // 18th birthday is 2026-06-29 → still 17 today.
        assertFalse(meetsMinimumAge(CalendarDate(2008, 6, 29), today))
    }

    @Test
    fun clearly_under_18_is_blocked() {
        assertFalse(meetsMinimumAge(CalendarDate(2015, 1, 1), today))
    }

    @Test
    fun clearly_over_18_is_allowed() {
        assertTrue(meetsMinimumAge(CalendarDate(1990, 3, 15), today))
    }

    @Test
    fun future_dob_is_blocked() {
        assertFalse(meetsMinimumAge(CalendarDate(2030, 1, 1), today))
    }

    @Test
    fun implausible_dob_fails_closed() {
        assertFalse(meetsMinimumAge(CalendarDate(2000, 13, 40), today))
        assertFalse(meetsMinimumAge(CalendarDate(2000, 0, 0), today))
    }

    @Test
    fun minimum_age_constant_is_18() {
        assertEquals(18, MINIMUM_AGE_YEARS)
    }
}
