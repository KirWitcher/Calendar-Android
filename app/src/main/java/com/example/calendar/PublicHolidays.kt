package com.example.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.text.Collator

internal data class PublicHolidayCalendar(
    val country: HolidayCountry,
    val holidays: List<PublicHoliday>,
    val loading: Boolean,
    val refreshFailed: Boolean,
    val onChooseCountry: () -> Unit,
    val onRetry: () -> Unit
)

@Composable
internal fun rememberPublicHolidayCalendar(today: LocalDate): PublicHolidayCalendar {
    val context = LocalContext.current
    val source = remember(context) { PublicHolidaySource(context) }
    var country by remember(source) { mutableStateOf(source.selectedCountry()) }
    var choosingCountry by rememberSaveable { mutableStateOf(false) }
    var attempt by remember(country) { mutableIntStateOf(0) }
    var holidays by remember(country, today.year) { mutableStateOf(source.cached(country, today.year)) }
    var loading by remember(country, today.year, attempt) { mutableStateOf(true) }
    var refreshFailed by remember(country, today.year, attempt) { mutableStateOf(false) }

    LaunchedEffect(country, today.year, attempt) {
        val selected = country
        val refreshed = source.refresh(selected, today.year, force = attempt > 0)
        holidays = source.cached(selected, today.year)
        refreshFailed = !refreshed
        loading = false
    }

    if (choosingCountry) CountryPicker(
        countries = source.countries,
        onDismiss = { choosingCountry = false },
        onSelect = {
            source.selectCountry(it)
            country = it
            choosingCountry = false
        }
    )

    return PublicHolidayCalendar(
        country, holidays.filter { it.date >= today }, loading, refreshFailed,
        onChooseCountry = { choosingCountry = true },
        onRetry = { attempt++ }
    )
}

@Composable
internal fun HolidayCountryCard(calendar: PublicHolidayCalendar) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = calendar.onChooseCountry,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(R.drawable.ic_calendar, colors.primaryContainer, colors.primary, size = 38)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.holiday_country), style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant)
                Text(calendar.country.displayName(calendarLocale()), style = MaterialTheme.typography.titleMedium)
            }
            Glyph(R.drawable.ic_chevron, tint = colors.onSurfaceVariant, size = 18)
        }
    }
}

internal fun LazyListScope.publicHolidayItems(
    calendar: PublicHolidayCalendar,
    entries: List<Entry>,
    onSelect: (PublicHoliday) -> Unit
) {
    val holidays = calendar.holidays
    if (calendar.refreshFailed) item {
        val message = stringResource(if (holidays.isEmpty()) R.string.public_load_error else R.string.public_refresh_error)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = calendar.onRetry) { Text(stringResource(R.string.retry)) }
        }
    } else if (holidays.isEmpty()) item {
        Text(
            stringResource(if (calendar.loading) R.string.public_loading else R.string.public_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    items(holidays, key = { "public:${it.date}:${it.name}" }) { holiday ->
        val colors = MaterialTheme.colorScheme
        Surface(
            onClick = { onSelect(holiday) },
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainer,
            border = BorderStroke(1.dp, colors.outlineVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(13.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTile(R.drawable.ic_holiday, colors.primaryContainer, colors.primary, size = 38)
                Column {
                    Text(holiday.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(formatDate(holiday.date, includeYear = true), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                    val saved = entries.firstOrNull { it.id == holiday.entry(calendar.country.code).id }
                    if (saved != null) {
                        if (saved.text.isNotBlank()) Text(saved.text, style = MaterialTheme.typography.bodySmall)
                        if (saved.reminders.isNotEmpty()) Text(
                            saved.reminders.sorted().map {
                                if (it == Reminder.CUSTOM) stringResource(R.string.days_before_value, saved.customDays)
                                else stringResource(it.label)
                            }.joinToString(" · ") + " · " + saved.time.format(timeFormat),
                            style = MaterialTheme.typography.labelSmall, color = colors.primary
                        )
                    }
                }
            }
        }
    }
    if (holidays.isNotEmpty()) item {
        Text(stringResource(if (calendar.country.code == "UA") R.string.ukraine_source else R.string.public_source),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CountryPicker(
    countries: List<HolidayCountry>,
    onDismiss: () -> Unit,
    onSelect: (HolidayCountry) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val locale = calendarLocale()
    val names = remember(countries, locale) {
        val collator = Collator.getInstance(locale)
        countries.map { it to it.displayName(locale) }.sortedWith { left, right ->
            collator.compare(left.second, right.second)
        }
    }
    val results = remember(names, query) {
        val term = query.trim()
        names.filter { (country, name) ->
            name.contains(term, ignoreCase = true) || country.name.contains(term, ignoreCase = true) ||
                country.code.equals(term, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_country)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_countries)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    if (results.isEmpty()) item { Text(stringResource(R.string.no_matching_country)) }
                    items(results, key = { it.first.code }) { (country, name) ->
                        Text(
                            name,
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(country) }
                                .padding(horizontal = 8.dp, vertical = 13.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

