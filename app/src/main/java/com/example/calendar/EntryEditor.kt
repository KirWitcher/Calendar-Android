package com.example.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.TextStyle

@Composable
internal fun EntryEditor(
    kind: EntryKind,
    entry: Entry?,
    onClose: () -> Unit,
    onSave: (Entry) -> Boolean,
    onDelete: (String) -> Boolean
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val birthday = kind == EntryKind.BIRTHDAY
    val annual = kind.repeatsYearly
    val currentDay = LocalDate.now()
    var title by rememberSaveable { mutableStateOf(entry?.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(entry?.text.orEmpty()) }
    val initialReminders = entry?.reminders ?: if (annual) setOf(Reminder.ON_BIRTHDAY) else emptySet()
    var noteReminder by rememberSaveable { mutableStateOf(entry?.noteReminder ?: Reminder.NEVER) }
    var annualReminders by rememberSaveable {
        mutableStateOf(ArrayList(if (annual) initialReminders else emptySet()))
    }
    var dateValue by rememberSaveable { mutableStateOf(entry?.date?.toString()) }
    var timeValue by rememberSaveable { mutableStateOf((entry?.time ?: LocalTime.of(9, 0)).toString()) }
    var customDays by rememberSaveable { mutableStateOf((entry?.customDays ?: 1).toString()) }
    var error by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var choosingAnnualDate by rememberSaveable { mutableStateOf(false) }
    val date = dateValue?.let(LocalDate::parse)
    val time = LocalTime.parse(timeValue)
    val reminders = when {
        annual -> annualReminders.toSet()
        noteReminder == Reminder.NEVER -> emptySet()
        else -> setOf(noteReminder)
    }
    val changed = title != entry?.title.orEmpty() || description != entry?.text.orEmpty() ||
        date != entry?.date || time != (entry?.time ?: LocalTime.of(9, 0)) ||
        reminders != initialReminders || customDays != (entry?.customDays ?: 1).toString()

    fun close() {
        if (changed) confirmDiscard = true else onClose()
    }

    fun submit() {
        val savedDate = if (annual || noteReminder == Reminder.ONCE) date else null
        val unchangedAlarm = entry?.noteReminder == Reminder.ONCE && entry.date == savedDate && entry.time == time
        error = when {
            Reminder.CUSTOM in reminders && customDays.toIntOrNull() !in 1..365 -> R.string.custom_days_error
            title.isBlank() -> if (birthday) R.string.enter_name else R.string.enter_title
            annual && savedDate == null -> R.string.choose_month_day
            noteReminder == Reminder.ONCE && savedDate == null -> R.string.choose_reminder_date
            noteReminder == Reminder.ONCE && savedDate != null && !unchangedAlarm &&
                !savedDate.atTime(time).isFuture() -> R.string.choose_future
            else -> null
        }
        if (error != null) return
        var updated = entry?.copy(
            title = title.trim(), text = description.trim(), reminders = reminders, time = time, date = savedDate
        ) ?: Entry(kind, title.trim(), description.trim(), reminders, time, savedDate)
        updated = updated.copy(customDays = customDays.toIntOrNull()?.coerceIn(1, 365) ?: 1)
        if (onSave(updated)) onClose() else error = R.string.save_failed
    }

    BackHandler(onBack = ::close)
    if (choosingAnnualDate) AnnualDatePicker(
        title = stringResource(if (birthday) R.string.choose_birthday else R.string.choose_holiday),
        initial = MonthDay.from(date ?: currentDay),
        onDismiss = { choosingAnnualDate = false },
        onSelect = { selected ->
            if (date == null || selected != MonthDay.from(date)) {
                // Preserve the existing date storage format and allow February 29.
                dateValue = selected.atYear(2000).toString()
            }
            error = null
            choosingAnnualDate = false
        }
    )
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_text)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) } }
    )
    if (confirmDelete && entry != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(when (kind) {
            EntryKind.BIRTHDAY -> R.string.delete_birthday
            EntryKind.HOLIDAY -> R.string.delete_holiday
            else -> R.string.delete_note
        })) },
        text = { Text(stringResource(R.string.delete_entry_text)) },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                if (onDelete(entry.id)) onClose() else error = R.string.delete_failed
            }) { Text(stringResource(R.string.delete), color = colors.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
    )

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = colors.background,
        bottomBar = {
            Surface(color = colors.surface) {
                Column(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    error?.let {
                        Text(stringResource(it), color = colors.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = ::submit, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(when {
                                entry != null -> R.string.save_changes
                                birthday -> R.string.save_birthday
                                annual -> R.string.save_holiday
                                else -> R.string.save_note
                            }),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = ::close) { Glyph(R.drawable.ic_back, stringResource(R.string.back)) }
                Text(
                    stringResource(when (kind) {
                        EntryKind.BIRTHDAY -> if (entry == null) R.string.new_birthday else R.string.edit_birthday
                        EntryKind.HOLIDAY -> if (entry == null) R.string.new_holiday else R.string.edit_holiday
                        else -> if (entry == null) R.string.new_note else R.string.edit_note
                    }),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (entry != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete), color = colors.error) }
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Text(
                    stringResource(kind.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = when (kind) {
                        EntryKind.LONG_NOTE -> colors.primary
                        EntryKind.QUICK_NOTE -> colors.secondary
                        EntryKind.BIRTHDAY -> colors.tertiary
                        EntryKind.HOLIDAY -> colors.primary
                    }
                )
                OutlinedTextField(
                    value = title,
                    readOnly = entry?.publicCountry != null,
                    onValueChange = { title = it; error = null },
                    label = { Text(stringResource(if (birthday) R.string.name else R.string.title)) },
                    placeholder = {
                        Text(stringResource(when (kind) {
                            EntryKind.BIRTHDAY -> R.string.name_example
                            EntryKind.HOLIDAY -> R.string.holiday_example
                            else -> R.string.note_title_hint
                        }))
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (entry?.publicCountry != null && date != null) {
                    Text(formatDate(date, includeYear = true), style = MaterialTheme.typography.titleMedium)
                } else if (annual) PickerField(
                    R.drawable.ic_calendar, stringResource(if (birthday) R.string.birthday_date else R.string.holiday_date),
                    date?.let { formatDate(it) } ?: stringResource(R.string.choose_month_day)
                ) {
                    focusManager.clearFocus()
                    choosingAnnualDate = true
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; error = null },
                    label = { Text(stringResource(if (annual) R.string.optional_description else R.string.optional_note)) },
                    placeholder = { Text(stringResource(if (annual) R.string.event_description_hint else R.string.note_description_hint)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (annual || kind == EntryKind.QUICK_NOTE) 2 else 4
                )
                Text(stringResource(if (annual) R.string.reminders else R.string.reminder),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = colors.surfaceContainer,
                    border = BorderStroke(1.dp, colors.outlineVariant)
                ) {
                    val options = if (annual) {
                        listOf(Reminder.ON_BIRTHDAY, Reminder.DAY_BEFORE, Reminder.WEEK_BEFORE, Reminder.CUSTOM)
                    } else {
                        listOf(Reminder.NEVER, Reminder.DAILY, Reminder.ONCE)
                    }
                    Column(if (annual) Modifier else Modifier.selectableGroup()) {
                        options.forEachIndexed { index, option ->
                            if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                            val selected = if (annual) option in reminders else noteReminder == option
                            val selection = if (annual) {
                                Modifier.toggleable(selected, role = Role.Checkbox) { checked ->
                                    annualReminders = ArrayList(
                                        if (checked) annualReminders + option else annualReminders - option
                                    )
                                }
                            } else {
                                Modifier.selectable(selected, role = Role.RadioButton) {
                                    noteReminder = option
                                    error = null
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().then(selection)
                                    .padding(horizontal = 13.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = null, modifier = Modifier.size(24.dp))
                                Text(stringResource(option.label), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (Reminder.CUSTOM in reminders) OutlinedTextField(
                    value = customDays,
                    onValueChange = { value ->
                        if (value.length <= 3 && value.all { it in '0'..'9' }) {
                            customDays = value
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.custom_days_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!annual && noteReminder == Reminder.ONCE) PickerField(
                    R.drawable.ic_calendar, stringResource(R.string.reminder_date),
                    date?.let { formatDate(it, includeYear = true) } ?: stringResource(R.string.choose_date)
                ) {
                    pickDate(context, date ?: currentDay, earliest = currentDay) {
                        dateValue = it.toString()
                        error = null
                    }
                }
                if (reminders.isNotEmpty()) PickerField(
                    R.drawable.ic_clock, stringResource(R.string.time), time.format(timeFormat)
                ) {
                    TimePickerDialog(
                        context, R.style.Theme_Calendar_Picker,
                        { _, hour, minute ->
                            timeValue = LocalTime.of(hour, minute).toString()
                            error = null
                        }, time.hour, time.minute, true
                    ).show()
                }
                if (annual && entry?.publicCountry == null && reminders.isNotEmpty()) Text(
                    stringResource(R.string.repeats_yearly),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AnnualDatePicker(
    title: String,
    initial: MonthDay,
    onDismiss: () -> Unit,
    onSelect: (MonthDay) -> Unit
) {
    var monthNumber by rememberSaveable { mutableStateOf(initial.monthValue) }
    var day by rememberSaveable { mutableStateOf(initial.dayOfMonth) }
    val month = Month.of(monthNumber)
    val locale = calendarLocale()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 12.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.month), style = MaterialTheme.typography.labelLarge)
                AnnualDateGrid(
                    options = Month.values().map { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale) },
                    selected = monthNumber,
                    columns = 4
                ) { number ->
                    monthNumber = number
                    day = minOf(day, Month.of(monthNumber).maxLength())
                }
                Text(stringResource(R.string.day), style = MaterialTheme.typography.labelLarge)
                AnnualDateGrid(
                    options = (1..month.maxLength()).map { it.toString() },
                    selected = day,
                    columns = 7
                ) { number ->
                    day = number
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(MonthDay.of(month, day)) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun AnnualDateGrid(
    options: List<String>,
    selected: Int,
    columns: Int,
    onSelect: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        options.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                row.forEachIndexed { columnIndex, label ->
                    val number = rowIndex * columns + columnIndex + 1
                    val isSelected = number == selected
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) colors.primary else colors.surfaceVariant)
                            .selectable(isSelected, role = Role.RadioButton) { onSelect(number) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PickerField(@DrawableRes icon: Int, label: String, value: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, shape = RoundedCornerShape(15.dp), color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(icon, colors.primaryContainer, colors.primary, size = 34)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            Glyph(R.drawable.ic_chevron, tint = colors.onSurfaceVariant, size = 18)
        }
    }
}

private fun pickDate(
    context: Context,
    initial: LocalDate,
    earliest: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val start = maxOf(initial, earliest)
    DatePickerDialog(
        context, R.style.Theme_Calendar_Picker,
        { _, year, month, day -> onSelect(LocalDate.of(year, month + 1, day)) },
        start.year, start.monthValue - 1, start.dayOfMonth
    ).apply {
        datePicker.minDate = earliest.atStartOfDay(zone).toInstant().toEpochMilli()
    }.show()
}


