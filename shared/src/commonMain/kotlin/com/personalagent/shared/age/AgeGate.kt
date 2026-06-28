package com.personalagent.shared.age

/**
 * 18+ age-gate logic. Pure, platform-free, and unit-tested so the boundary is
 * provably correct on every target.
 *
 * The app is restricted to adults (18 or older). The platform UIs collect a date
 * of birth (or an explicit affirmation) and use [meetsMinimumAge] to decide
 * whether to let the user proceed; under-18 users are blocked, not advanced.
 */
const val MINIMUM_AGE_YEARS: Int = 18

/**
 * A plain calendar date (no time zone, no time of day). Both a date of birth and
 * "today" are expressed with this so age can be computed deterministically.
 *
 * @param year full year, e.g. 2007.
 * @param month 1–12.
 * @param day 1–31.
 */
data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    /** True for a structurally plausible Gregorian date (cheap, not a full calendar check). */
    fun isPlausible(): Boolean = month in 1..12 && day in 1..31 && year in 1..9999
}

/**
 * Completed years from [dob] to [today] (i.e. the person's age in whole years).
 * Returns a value that has NOT yet incremented until the birthday has occurred in
 * [today]'s year — so someone whose 18th birthday is tomorrow is still 17.
 *
 * If [today] is before [dob] (a future birth date), the result is negative; callers
 * should treat a non-plausible or future DOB as "not eligible".
 */
fun ageInYears(dob: CalendarDate, today: CalendarDate): Int {
    var age = today.year - dob.year
    val hadBirthdayThisYear =
        today.month > dob.month || (today.month == dob.month && today.day >= dob.day)
    if (!hadBirthdayThisYear) age -= 1
    return age
}

/**
 * True when a person born on [dob] is at least [minAge] on [today]. A
 * non-plausible date fails closed (returns false).
 */
fun meetsMinimumAge(dob: CalendarDate, today: CalendarDate, minAge: Int = MINIMUM_AGE_YEARS): Boolean {
    if (!dob.isPlausible() || !today.isPlausible()) return false
    return ageInYears(dob, today) >= minAge
}
