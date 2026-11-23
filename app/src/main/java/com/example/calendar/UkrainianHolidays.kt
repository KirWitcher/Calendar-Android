package com.example.calendar

import androidx.annotation.StringRes
import java.time.LocalDate

internal data class StatutoryHoliday(@get:StringRes val title: Int, val date: LocalDate)

// Labor Code of Ukraine, Article 73, under ordinary rules.
internal fun ukrainianHolidays(year: Int): List<StatutoryHoliday> {
    val easter = orthodoxEaster(year)
    return listOf(
        StatutoryHoliday(R.string.ua_new_year, LocalDate.of(year, 1, 1)),
        StatutoryHoliday(R.string.ua_womens_day, LocalDate.of(year, 3, 8)),
        StatutoryHoliday(R.string.ua_labour_day, LocalDate.of(year, 5, 1)),
        StatutoryHoliday(R.string.ua_victory_day, LocalDate.of(year, 5, 8)),
        StatutoryHoliday(R.string.ua_constitution_day, LocalDate.of(year, 6, 28)),
        StatutoryHoliday(R.string.ua_statehood_day, LocalDate.of(year, 7, 15)),
        StatutoryHoliday(R.string.ua_independence_day, LocalDate.of(year, 8, 24)),
        StatutoryHoliday(R.string.ua_defenders_day, LocalDate.of(year, 10, 1)),
        StatutoryHoliday(R.string.ua_christmas, LocalDate.of(year, 12, 25)),
        StatutoryHoliday(R.string.ua_easter, easter),
        StatutoryHoliday(R.string.ua_pentecost, easter.plusDays(49))
    ).sortedBy { it.date }
}

private fun orthodoxEaster(year: Int): LocalDate {
    val lunarCorrection = (19 * (year % 19) + 15) % 30
    val weekdayCorrection = (2 * (year % 4) + 4 * (year % 7) - lunarCorrection + 34) % 7
    val paschalDate = lunarCorrection + weekdayCorrection + 114
    val julianDate = LocalDate.of(year, paschalDate / 31, paschalDate % 31 + 1)
    val calendarOffset = year / 100 - year / 400 - 2
    return julianDate.plusDays(calendarOffset.toLong())
}

