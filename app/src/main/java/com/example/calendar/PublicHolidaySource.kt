package com.example.calendar

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class HolidayCountry(val code: String, val name: String) {
    fun displayName(locale: Locale): String = if (locale.language == "en") name else
        Locale.forLanguageTag("und-$code").getDisplayCountry(locale).takeUnless { it.isBlank() || it == code } ?: name
}

internal data class PublicHoliday(val name: String, val date: LocalDate, val stableKey: String = name) {
    fun entry(country: String): Entry = Entry(
        kind = EntryKind.HOLIDAY, title = name, text = "", reminders = emptySet(), date = date,
        id = java.util.UUID.nameUUIDFromBytes("public:$country:$date:$stableKey".toByteArray(Charsets.UTF_8)).toString(),
        publicCountry = country
    )
}

internal class PublicHolidaySource(context: Context) {
    private val strings = context.resources
    private val prefs = context.getSharedPreferences("public_holidays", Context.MODE_PRIVATE)
    val countries: List<HolidayCountry> = context.assets.open("holiday-countries.json").bufferedReader().use {
        val json = JSONArray(it.readText())
        List(json.length()) { index ->
            val country = json.getJSONObject(index)
            HolidayCountry(country.getString("countryCode"), country.getString("name"))
        }
    }

    fun selectedCountry(): HolidayCountry {
        val code = prefs.getString("country", "DE")
        return countries.firstOrNull { it.code == code } ?: countries.first { it.code == "DE" }
    }

    fun selectCountry(country: HolidayCountry) {
        prefs.edit { putString("country", country.code) }
    }

    fun cached(country: HolidayCountry, year: Int): List<PublicHoliday> {
        if (country.code == "UA") return years(year).flatMap { dateYear ->
            ukrainianHolidays(dateYear).map {
                PublicHoliday(strings.getString(it.title), it.date, strings.getResourceEntryName(it.title))
            }
        }
        return years(year).flatMap { dateYear ->
            prefs.getString(cacheKey(country, dateYear), null)?.let {
                runCatching { parsePublicHolidays(it) }.getOrNull()
            }.orEmpty()
        }.distinct().sortedBy { it.date }
    }

    suspend fun refresh(country: HolidayCountry, year: Int, force: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (country.code == "UA") return@withContext true
            var allYearsAvailable = true
            for (dateYear in years(year)) {
                ensureActive()
                val key = cacheKey(country, dateYear)
                val timestamp = System.currentTimeMillis()
                val age = timestamp - prefs.getLong("$key.updated", 0)
                if (!force && prefs.contains(key) && age in 0 until TimeUnit.DAYS.toMillis(1)) continue
                val updated = runCatching {
                    val response = fetch(country, dateYear)
                    parsePublicHolidays(response)
                    prefs.edit {
                        putString(key, response)
                        putLong("$key.updated", timestamp)
                    }
                }.isSuccess
                allYearsAvailable = allYearsAvailable && updated
            }
            allYearsAvailable
        }

    private fun cacheKey(country: HolidayCountry, year: Int) = "${country.code}.$year"

    private fun years(year: Int) = year..year + 1

    private fun fetch(country: HolidayCountry, year: Int): String {
        val url = URL("https://nagerholidays.com/api/v4/Holidays/${country.code}/$year")
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("Holiday service returned $status")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

internal fun parsePublicHolidays(response: String): List<PublicHoliday> {
    val json = JSONArray(response)
    return (0 until json.length()).mapNotNull { index ->
        val holiday = json.getJSONObject(index)
        val types = holiday.getJSONArray("holidayTypes")
        val public = (0 until types.length()).any { types.getString(it) == "Public" }
        val subdivisions = holiday.optJSONArray("subdivisionCodes")
        if (holiday.getBoolean("nationalHoliday") && public && (subdivisions == null || subdivisions.length() == 0)) {
            PublicHoliday(holiday.getString("name"), LocalDate.parse(holiday.getString("date")))
        } else {
            null
        }
    }
}

