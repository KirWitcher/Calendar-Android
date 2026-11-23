package com.example.calendar

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal enum class EntryKind(@get:StringRes val label: Int, val repeatsYearly: Boolean = false) {
    LONG_NOTE(R.string.long_notes),
    QUICK_NOTE(R.string.quick_notes),
    BIRTHDAY(R.string.birthdays, repeatsYearly = true),
    HOLIDAY(R.string.holidays, repeatsYearly = true)
}

internal enum class Reminder(@get:StringRes val label: Int) {
    NEVER(R.string.never),
    DAILY(R.string.every_day),
    ONCE(R.string.date_and_time),
    ON_BIRTHDAY(R.string.on_the_day),
    DAY_BEFORE(R.string.day_before),
    WEEK_BEFORE(R.string.week_before),
    CUSTOM(R.string.custom_days);

    val daysBeforeEvent: Long
        get() = when (this) {
            DAY_BEFORE -> 1
            WEEK_BEFORE -> 7
            else -> 0
        }
}

internal data class Entry(
    val kind: EntryKind,
    val title: String,
    val text: String,
    val reminders: Set<Reminder>,
    val time: LocalTime = LocalTime.of(9, 0),
    val date: LocalDate? = null,
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val customDays: Int = 1,
    val publicCountry: String? = null
) {
    init { require(customDays in 1..365) }

    val isAnnual: Boolean get() = kind.repeatsYearly && publicCountry == null
    fun leadDays(option: Reminder): Long =
        if (option == Reminder.CUSTOM) customDays.toLong() else option.daysBeforeEvent
    val noteReminder: Reminder
        get() = reminders.singleOrNull() ?: Reminder.NEVER

    fun nextReminder(after: Instant, zone: ZoneId): ScheduledReminder? =
        reminders.mapNotNull { option ->
            nextTrigger(option, after, zone)?.let { ScheduledReminder(option, it) }
        }.minByOrNull { it.at }

    private fun nextTrigger(reminder: Reminder, after: Instant, zone: ZoneId): Instant? {
        val today = after.atZone(zone).toLocalDate()
        fun atTime(day: LocalDate) = day.atTime(time).atZone(zone).toInstant()

        return when (reminder) {
            Reminder.NEVER -> null
            Reminder.ONCE -> date?.let(::atTime)?.takeIf { it > after }
            Reminder.DAILY -> atTime(today).takeIf { it > after } ?: atTime(today.plusDays(1))
            else -> {
                val eventDate = requireNotNull(date)
                if (publicCountry != null) {
                    return atTime(eventDate.minusDays(leadDays(reminder))).takeIf { it > after }
                }
                // In January, next year's reminder can already fall in December.
                (today.year..today.year + 2).firstNotNullOfOrNull { year ->
                    atTime(eventDate.withYear(year).minusDays(leadDays(reminder)))
                        .takeIf { it > after }
                }
            }
        }
    }

    fun nextOccurrenceDate(today: LocalDate): LocalDate {
        val eventDate = requireNotNull(date)
        val thisYear = eventDate.withYear(today.year)
        return if (thisYear >= today) thisYear else eventDate.withYear(today.year + 1)
    }
}

internal data class ScheduledReminder(val option: Reminder, val at: Instant)

internal class EntryStore(context: Context) {
    private val prefs = context.getSharedPreferences("entries", Context.MODE_PRIVATE)

    fun all(): List<Entry> = prefs.all.mapNotNull { (id, json) -> decode(id, json as? String) }

    fun get(id: String): Entry? = decode(id, prefs.getString(id, null))

    fun save(entry: Entry): Boolean {
        val json = JSONObject()
            .put("kind", entry.kind.name)
            .put("title", entry.title)
            .put("text", entry.text)
            .put("reminders", JSONArray(entry.reminders.map { it.name }))
            .put("time", entry.time.toString())
            .put("date", entry.date?.toString())
            .put("createdAt", entry.createdAt)
            .put("customDays", entry.customDays)
            .put("publicCountry", entry.publicCountry)
        return prefs.edit().putString(entry.id, json.toString()).commit()
    }

    fun delete(id: String): Boolean = prefs.edit().remove(id).commit()

    private fun decode(id: String, data: String?): Entry? = data?.let {
        runCatching {
            val json = JSONObject(it)
            Entry(
                id = id,
                kind = EntryKind.valueOf(json.getString("kind")),
                title = json.getString("title"),
                text = json.getString("text"),
                reminders = readReminders(json),
                time = LocalTime.parse(json.getString("time")),
                date = if (json.has("date")) LocalDate.parse(json.getString("date")) else null,
                createdAt = json.getLong("createdAt"),
                customDays = json.optInt("customDays", 1),
                publicCountry = if (json.has("publicCountry")) json.getString("publicCountry") else null
            )
        }.getOrNull()
    }

    private fun readReminders(json: JSONObject): Set<Reminder> {
        val saved = json.optJSONArray("reminders")
        if (saved != null) {
            return (0 until saved.length()).map { Reminder.valueOf(saved.getString(it)) }.toSet()
        }
        val legacy = Reminder.valueOf(json.getString("reminder"))
        return if (legacy == Reminder.NEVER) emptySet() else setOf(legacy)
    }
}

internal fun LocalDateTime.isFuture(): Boolean =
    atZone(ZoneId.systemDefault()).toInstant() > Instant.now()

