package com.example.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UkrainianHolidaysTest {
    @Test
    fun article73DatesFor2027IncludeOnlyTheStatutoryHolidays() {
        val expected = listOf(
            "2027-01-01", "2027-03-08", "2027-05-01", "2027-05-02", "2027-05-08",
            "2027-06-20", "2027-06-28", "2027-07-15", "2027-08-24", "2027-10-01", "2027-12-25"
        ).map(LocalDate::parse)
        assertEquals(expected, ukrainianHolidays(2027).map { it.date })
    }

    @Test
    fun easterAndPentecostMoveWithTheYear() {
        val holidays = ukrainianHolidays(2026)
        assertEquals(LocalDate.of(2026, 4, 12), holidays.single { it.title == R.string.ua_easter }.date)
        assertEquals(LocalDate.of(2026, 5, 31), holidays.single { it.title == R.string.ua_pentecost }.date)
    }
}

