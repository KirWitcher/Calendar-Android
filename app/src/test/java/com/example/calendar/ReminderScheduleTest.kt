package com.example.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderScheduleTest {
    @Test
    fun publicHolidayKeepsItsExactDateAndCustomLeadTime() {
        val holiday = Entry(
            EntryKind.HOLIDAY, "Christmas", "Buy a gift",
            setOf(Reminder.ON_BIRTHDAY, Reminder.DAY_BEFORE, Reminder.WEEK_BEFORE, Reminder.CUSTOM),
            time = LocalTime.of(12, 0), date = LocalDate.of(2026, 12, 25),
            customDays = 365, publicCountry = "DE"
        )
        val custom = Instant.parse("2025-12-25T12:00:00Z")
        assertEquals(ScheduledReminder(Reminder.CUSTOM, custom),
            holiday.nextReminder(custom.minusSeconds(1), ZoneOffset.UTC))
        assertEquals(Instant.parse("2026-12-18T12:00:00Z"),
            holiday.nextReminder(custom, ZoneOffset.UTC)?.at)
        assertEquals(Instant.parse("2026-12-24T12:00:00Z"),
            holiday.nextReminder(Instant.parse("2026-12-18T12:00:00Z"), ZoneOffset.UTC)?.at)
        assertEquals(Instant.parse("2026-12-25T12:00:00Z"),
            holiday.nextReminder(Instant.parse("2026-12-24T12:00:00Z"), ZoneOffset.UTC)?.at)
        assertNull(holiday.nextReminder(Instant.parse("2026-12-25T12:00:00Z"), ZoneOffset.UTC))
    }

    @Test(expected = IllegalArgumentException::class)
    fun customReminderCannotExceedOneYear() {
        Entry(EntryKind.HOLIDAY, "Holiday", "", emptySet(), customDays = 366)
    }

    @Test
    fun dailyReminderKeepsItsLocalTimeAfterDaylightSavingStarts() {
        val entry = Entry(EntryKind.QUICK_NOTE, "Water plants", "", setOf(Reminder.DAILY), LocalTime.of(9, 0))
        val next = entry.nextReminder(Instant.parse("2026-03-28T09:00:00Z"), ZoneId.of("Europe/Berlin"))

        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), next?.at)
    }

    @Test
    fun pastOneTimeReminderDoesNotRepeat() {
        val entry = Entry(
            EntryKind.LONG_NOTE, "Call Sam", "", setOf(Reminder.ONCE),
            time = LocalTime.of(14, 0), date = LocalDate.of(2026, 11, 10)
        )

        assertEquals(
            Instant.parse("2026-11-10T14:00:00Z"),
            entry.nextReminder(Instant.parse("2026-11-10T13:59:00Z"), ZoneOffset.UTC)?.at
        )
        assertNull(entry.nextReminder(Instant.parse("2026-11-10T14:00:00Z"), ZoneOffset.UTC))
        assertNull(entry.copy(reminders = emptySet()).nextReminder(Instant.EPOCH, ZoneOffset.UTC))
    }

    @Test
    fun januaryBirthdayReminderCanFallInDecember() {
        val entry = Entry(
            EntryKind.BIRTHDAY, "Sam", "", setOf(Reminder.WEEK_BEFORE),
            time = LocalTime.of(9, 0), date = LocalDate.of(2000, 1, 2)
        )

        assertEquals(
            Instant.parse("2026-12-26T09:00:00Z"),
            entry.nextReminder(Instant.parse("2026-12-25T12:00:00Z"), ZoneOffset.UTC)?.at
        )
        assertEquals(
            Instant.parse("2027-12-26T09:00:00Z"),
            entry.nextReminder(Instant.parse("2026-12-26T12:00:00Z"), ZoneOffset.UTC)?.at
        )
    }

    @Test
    fun leapDayBirthdayUsesFebruary28InNonLeapYears() {
        val entry = Entry(
            EntryKind.BIRTHDAY, "Sam", "", setOf(Reminder.ON_BIRTHDAY), date = LocalDate.of(2000, 2, 29)
        )

        assertEquals(LocalDate.of(2027, 2, 28), entry.nextOccurrenceDate(LocalDate.of(2027, 1, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), entry.nextOccurrenceDate(LocalDate.of(2028, 1, 1)))
    }

    @Test
    fun selectedBirthdayRemindersFireInOrderAndRepeatNextYear() {
        val entry = Entry(
            EntryKind.BIRTHDAY, "Sam", "", setOf(Reminder.DAY_BEFORE, Reminder.WEEK_BEFORE),
            time = LocalTime.of(12, 0), date = LocalDate.of(2000, 1, 2)
        )
        val weekBefore = Instant.parse("2026-12-26T12:00:00Z")
        val dayBefore = Instant.parse("2027-01-01T12:00:00Z")

        assertEquals(
            ScheduledReminder(Reminder.WEEK_BEFORE, weekBefore),
            entry.nextReminder(weekBefore.minusSeconds(1), ZoneOffset.UTC)
        )
        assertEquals(
            ScheduledReminder(Reminder.DAY_BEFORE, dayBefore),
            entry.nextReminder(weekBefore, ZoneOffset.UTC)
        )
        assertEquals(
            ScheduledReminder(Reminder.WEEK_BEFORE, Instant.parse("2027-12-26T12:00:00Z")),
            entry.nextReminder(dayBefore, ZoneOffset.UTC)
        )
    }

    @Test
    fun personalHolidayUsesTheSelectedTimeAndRepeatsNextYear() {
        val holiday = Entry(
            EntryKind.HOLIDAY, "Family Day", "", setOf(Reminder.ON_BIRTHDAY),
            time = LocalTime.of(12, 30), date = LocalDate.of(2000, 11, 10)
        )
        val zone = ZoneId.of("Europe/Berlin")
        val first = Instant.parse("2026-11-10T11:30:00Z")
        assertEquals(first, holiday.nextReminder(first.minusSeconds(1), zone)?.at)
        assertEquals(Instant.parse("2027-11-10T11:30:00Z"), holiday.nextReminder(first, zone)?.at)
    }
}

