package com.example.calendar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

@Composable
internal fun calendarLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun formatDate(date: LocalDate, includeYear: Boolean = false): String {
    val locale = calendarLocale()
    val pattern = when {
        locale.language == "en" -> if (includeYear) "MMM d, uuuu" else "MMM d"
        else -> if (includeYear) "d MMM uuuu" else "d MMM"
    }
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

private enum class CalendarTab(
    @get:StringRes val label: Int,
    @get:DrawableRes val icon: Int,
    val eventKind: EntryKind? = null
) {
    NOTES(R.string.notes, R.drawable.ic_note),
    BIRTHDAYS(R.string.birthdays, R.drawable.ic_cake, EntryKind.BIRTHDAY),
    HOLIDAYS(R.string.holidays, R.drawable.ic_holiday, EntryKind.HOLIDAY)
}

@Composable
internal fun Daymark(
    entries: List<Entry>,
    today: LocalDate,
    notificationsEnabled: Boolean,
    exactEnabled: Boolean,
    openEntryId: String?,
    onOpenEntryHandled: () -> Unit,
    onEnableNotifications: () -> Unit,
    onEnableExact: () -> Unit,
    onLanguageChange: (CalendarLanguage) -> Unit,
    onSave: (Entry) -> Boolean,
    onDelete: (String) -> Boolean
) {
    var tab by rememberSaveable { mutableStateOf(CalendarTab.NOTES) }
    var editorKind by rememberSaveable { mutableStateOf<EntryKind?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var publicName by rememberSaveable { mutableStateOf<String?>(null) }
    var publicDate by rememberSaveable { mutableStateOf<String?>(null) }
    var publicCountry by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    val locale = calendarLocale()
    val longNotes = remember(entries) {
        entries.filter { it.kind == EntryKind.LONG_NOTE }.sortedByDescending { it.createdAt }
    }
    val quickNotes = remember(entries) {
        entries.filter { it.kind == EntryKind.QUICK_NOTE }.sortedByDescending { it.createdAt }
    }
    val eventKind = tab.eventKind
    val holidays = tab == CalendarTab.HOLIDAYS
    val annualEntries = remember(entries, today, eventKind) {
        entries.filter { it.kind == eventKind && it.publicCountry == null }
            .sortedWith(compareBy<Entry> { it.nextOccurrenceDate(today) }.thenBy { it.title.lowercase(Locale.ROOT) })
    }
    val longState = rememberLazyListState()
    val quickState = rememberLazyListState()
    val birthdayState = rememberLazyListState()
    val holidayState = rememberLazyListState()
    val activeReminders = entries.any { it.reminders.isNotEmpty() }

    fun closeEditor() {
        editorKind = null
        editingId = null
        publicName = null
        publicDate = null
        publicCountry = null
    }

    fun edit(entry: Entry) {
        editingId = entry.id
        editorKind = entry.kind
    }

    LaunchedEffect(openEntryId) {
        if (openEntryId != null) {
            entries.firstOrNull { it.id == openEntryId }?.let {
                tab = CalendarTab.entries.firstOrNull { tab -> tab.eventKind == it.kind } ?: CalendarTab.NOTES
                edit(it)
            }
            onOpenEntryHandled()
        }
    }

    val kind = editorKind
    if (kind != null) {
        key(kind, editingId) {
            EntryEditor(
                kind = kind,
                entry = entries.firstOrNull { it.id == editingId } ?: publicName?.let { name ->
                    PublicHoliday(name, LocalDate.parse(publicDate)).entry(requireNotNull(publicCountry))
                        .copy(id = requireNotNull(editingId))
                },
                onClose = ::closeEditor,
                onSave = onSave,
                onDelete = onDelete
            )
        }
        return
    }

    val publicCalendar = if (holidays) rememberPublicHolidayCalendar(today) else null
    if (choosingLanguage) AlertDialog(
        onDismissRequest = { choosingLanguage = false },
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                CalendarLanguage.entries.forEach { language ->
                    TextButton(
                        onClick = {
                            choosingLanguage = false
                            if (language.tag != locale.language) onLanguageChange(language)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(language.nativeName) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosingLanguage = false }) {
            Text(stringResource(R.string.cancel))
        } }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
                CalendarTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Glyph(item.icon) },
                        label = { Text(stringResource(item.label)) },
                        colors = colors
                    )
                }
            }
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 18.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                IconTile(R.drawable.ic_calendar, MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.primary, size = 32)
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatDate(today).uppercase(locale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.sp
                )
                TextButton(onClick = { choosingLanguage = true }) {
                    Text(locale.language.uppercase(Locale.ROOT))
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(when (tab) {
                            CalendarTab.NOTES -> R.string.notes_hint
                            CalendarTab.BIRTHDAYS -> R.string.birthdays_hint
                            CalendarTab.HOLIDAYS -> R.string.holidays_hint
                        }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (eventKind != null) {
                    FilledIconButton(
                        onClick = { editorKind = eventKind },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Glyph(R.drawable.ic_add, stringResource(if (holidays) R.string.add_holiday else R.string.add_birthday))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (activeReminders && (!notificationsEnabled || !exactEnabled)) {
                val canNotify = notificationsEnabled
                ReminderNotice(
                    message = stringResource(if (canNotify) R.string.allow_exact else R.string.enable_notifications),
                    action = stringResource(if (canNotify) R.string.allow else R.string.enable),
                    onClick = if (canNotify) onEnableExact else onEnableNotifications
                )
                Spacer(Modifier.height(12.dp))
            }

            if (eventKind != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = if (holidays) holidayState else birthdayState,
                    contentPadding = PaddingValues(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (publicCalendar != null) {
                        item { HolidayCountryCard(publicCalendar) }
                        item { EventSectionLabel(R.string.my_holidays) }
                    }
                    if (annualEntries.isEmpty()) item {
                        EmptyPanel(
                            icon = tab.icon,
                            title = stringResource(if (holidays) R.string.no_personal_holidays else R.string.no_birthdays),
                            description = stringResource(if (holidays) R.string.personal_holiday_hint else R.string.birthday_empty_hint),
                            tint = if (holidays) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            onClick = { editorKind = eventKind }
                        )
                    } else {
                        if (!holidays) item { EventSectionLabel(R.string.upcoming) }
                        items(annualEntries, key = { it.id }) { event ->
                            AnnualEventCard(event, today, tab.icon, onClick = { edit(event) })
                        }
                    }
                    if (publicCalendar != null) {
                        item { EventSectionLabel(R.string.public_holidays) }
                        publicHolidayItems(publicCalendar, entries) { holiday ->
                            val draft = holiday.entry(publicCalendar.country.code)
                            publicName = holiday.name
                            publicDate = holiday.date.toString()
                            publicCountry = publicCalendar.country.code
                            editingId = draft.id
                            editorKind = EntryKind.HOLIDAY
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NoteColumn(EntryKind.LONG_NOTE, longNotes, longState, Modifier.weight(1f),
                        onAdd = { editorKind = EntryKind.LONG_NOTE }, onEdit = ::edit)
                    NoteColumn(EntryKind.QUICK_NOTE, quickNotes, quickState, Modifier.weight(1f),
                        onAdd = { editorKind = EntryKind.QUICK_NOTE }, onEdit = ::edit)
                }
            }
        }
    }
}

@Composable
private fun NoteColumn(
    kind: EntryKind,
    entries: List<Entry>,
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (Entry) -> Unit
) {
    val long = kind == EntryKind.LONG_NOTE
    val colors = MaterialTheme.colorScheme
    val accent = if (long) colors.primary else colors.secondary
    val container = if (long) colors.primaryContainer else colors.secondaryContainer
    val icon = if (long) R.drawable.ic_note else R.drawable.ic_bolt
    Column(modifier.fillMaxHeight()) {
        Surface(color = container, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, bottom = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(icon, tint = accent)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onAdd) {
                        Glyph(R.drawable.ic_add, stringResource(if (long) R.string.add_long_note else R.string.add_quick_note), tint = accent)
                    }
                }
                Text(stringResource(kind.label), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = accent)
                Text(
                    pluralStringResource(R.plurals.note_count, entries.size, entries.size),
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(top = 9.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entries.isEmpty()) item {
                EmptyPanel(R.drawable.ic_add, stringResource(R.string.add_note),
                    stringResource(if (long) R.string.long_note_hint else R.string.quick_note_hint),
                    accent, onAdd)
            }
            items(entries, key = { it.id }) { entry ->
                Surface(
                    onClick = { onEdit(entry) },
                    shape = RoundedCornerShape(15.dp),
                    color = colors.surfaceContainer,
                    border = BorderStroke(1.dp, colors.outlineVariant)
                ) {
                    Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (entry.text.isNotBlank()) Text(
                            entry.text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                            maxLines = 4, overflow = TextOverflow.Ellipsis
                        )
                        if (entry.reminders.isNotEmpty()) Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Glyph(R.drawable.ic_notification, tint = accent, size = 13)
                            val reminder = if (entry.noteReminder == Reminder.DAILY) {
                                stringResource(R.string.daily)
                            } else entry.date?.let { formatDate(it) }.orEmpty()
                            Text(
                                "$reminder · ${entry.time.format(timeFormat)}",
                                style = MaterialTheme.typography.labelSmall, color = accent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnualEventCard(event: Entry, today: LocalDate, @DrawableRes icon: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val holiday = event.kind == EntryKind.HOLIDAY
    val accent = if (holiday) colors.primary else colors.tertiary
    val accentContainer = if (holiday) colors.primaryContainer else colors.tertiaryContainer
    val next = event.nextOccurrenceDate(today)
    val days = ChronoUnit.DAYS.between(today, next)
    val relative = when (days) {
        0L -> stringResource(R.string.today)
        1L -> stringResource(R.string.tomorrow)
        else -> pluralStringResource(R.plurals.in_days, days.toInt(), days)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, if (days == 0L) accent else colors.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(icon, accentContainer, accent, size = 42)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDate(next) + " · " + relative, style = MaterialTheme.typography.bodySmall,
                    color = accent)
                Text(
                    if (event.reminders.isEmpty()) stringResource(R.string.no_reminders) else
                        event.reminders.sorted().map {
                            if (it == Reminder.CUSTOM) stringResource(R.string.days_before_value, event.customDays)
                            else stringResource(it.label)
                        }.joinToString(" · ") +
                            " · ${event.time.format(timeFormat)}",
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant
                )
                if (event.text.isNotBlank()) Text(
                    event.text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Glyph(R.drawable.ic_chevron, tint = colors.onSurfaceVariant, size = 18)
        }
    }
}

@Composable
private fun EventSectionLabel(@StringRes label: Int) {
    Text(
        stringResource(label).uppercase(calendarLocale()),
        modifier = Modifier.padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EmptyPanel(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Glyph(icon, tint = tint, size = 20)
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReminderNotice(message: String, action: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.primaryContainer, shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Glyph(R.drawable.ic_notification, tint = colors.primary, size = 18)
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = colors.onPrimaryContainer)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
internal fun Glyph(
    @DrawableRes icon: Int,
    description: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Int = 24
) {
    Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(size.dp))
}

@Composable
internal fun IconTile(@DrawableRes icon: Int, background: Color, tint: Color, size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(background),
        contentAlignment = Alignment.Center
    ) {
        Glyph(icon, tint = tint, size = if (size < 40) 18 else 22)
    }
}

