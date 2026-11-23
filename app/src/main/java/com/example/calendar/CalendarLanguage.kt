package com.example.calendar

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

internal enum class CalendarLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский"),
    UKRAINIAN("uk", "Українська");

    companion object {
        fun fromTag(tag: String?): CalendarLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

internal fun Context.withCalendarLanguage(): Context {
    val language = CalendarLanguage.fromTag(
        getSharedPreferences("settings", Context.MODE_PRIVATE).getString("language", null)
    )
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(language.tag))
    return createConfigurationContext(config)
}

internal fun Context.saveCalendarLanguage(language: CalendarLanguage) {
    getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putString("language", language.tag) }
}

