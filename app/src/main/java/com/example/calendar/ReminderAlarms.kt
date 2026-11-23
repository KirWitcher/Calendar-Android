package com.example.calendar

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

internal class ReminderAlarms(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val planned = context.getSharedPreferences("planned_reminders", Context.MODE_PRIVATE)

    fun createChannel() {
        val strings = context.withCalendarLanguage()
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, strings.getString(R.string.reminders), NotificationManager.IMPORTANCE_HIGH).apply {
                description = strings.getString(R.string.channel_description)
            }
        )
    }

    fun notificationsEnabled(): Boolean = notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun exactEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun restore(entries: List<Entry>, clockChanged: Boolean = false) {
        entries.filter { it.reminders.isNotEmpty() }.forEach { entry ->
            schedule(entry, keepPlanned = !clockChanged)
        }
    }

    fun replace(entry: Entry) {
        cancel(entry.id)
        schedule(entry)
    }

    fun cancel(id: String) {
        val pending = PendingIntent.getBroadcast(
            context, 0, alarmIntent(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pending?.let {
            alarms.cancel(it)
            it.cancel()
        }
        planned.edit { remove(id) }
        notifications.cancel(id, 0)
    }

    private fun schedule(entry: Entry, keepPlanned: Boolean = false) {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val previous = planned.getLong(entry.id, 0)
        if (previous == DELIVERED) return
        val trigger = previous.takeIf {
            keepPlanned && it > now.minus(Duration.ofHours(1)).toEpochMilli() &&
                entry.nextReminder(Instant.ofEpochMilli(it - 1), zone)?.at?.toEpochMilli() == it
        } ?: entry.nextReminder(now, zone)?.at?.toEpochMilli()
        if (trigger == null) {
            if (previous != 0L) {
                cancel(entry.id)
                if (entry.noteReminder == Reminder.ONCE) {
                    planned.edit { putLong(entry.id, DELIVERED) }
                }
            }
            return
        }
        val pending = PendingIntent.getBroadcast(
            context, 0, alarmIntent(entry.id).putExtra(EXTRA_TRIGGER, trigger),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        planned.edit { putLong(entry.id, trigger) }
        if (exactEnabled()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                return
            } catch (_: SecurityException) {
                // Special access can change between the check and scheduling.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    fun deliver(entry: Entry, trigger: Long) {
        if (trigger <= 0 || planned.getLong(entry.id, 0) != trigger) return
        val occurrence = entry.nextReminder(Instant.ofEpochMilli(trigger - 1), ZoneId.systemDefault()) ?: return
        if (occurrence.at.toEpochMilli() != trigger) return
        if (occurrence.option == Reminder.ONCE) {
            planned.edit { putLong(entry.id, DELIVERED) }
        } else {
            planned.edit { remove(entry.id) }
            schedule(entry)
        }
        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (!notificationsEnabled()) return

        val openEntry = Intent(context, MainActivity::class.java)
            .setData("daymark://entry/${entry.id}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, openEntry, PendingIntent.FLAG_IMMUTABLE)
        val strings = context.withCalendarLanguage()
        val title = if (entry.kind == EntryKind.BIRTHDAY) {
            strings.getString(R.string.birthday_notification, entry.title)
        } else entry.title
        val body = if (entry.kind.repeatsYearly) {
            val lead = if (occurrence.option == Reminder.CUSTOM) {
                strings.resources.getQuantityString(R.plurals.in_days, entry.customDays, entry.customDays)
            } else strings.getString(when (occurrence.option) {
                Reminder.DAY_BEFORE -> R.string.tomorrow
                Reminder.WEEK_BEFORE -> R.string.in_one_week
                else -> R.string.today
            })
            if (entry.text.isBlank()) lead else "$lead\n${entry.text}"
        } else {
            entry.text.ifBlank { strings.getString(entry.kind.label) }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        notifications.notify(entry.id, 0, notification)
    }

    private fun alarmIntent(id: String) =
        Intent(context, ReminderReceiver::class.java).setData("daymark://alarm/$id".toUri())

    companion object {
        const val CHANNEL = "reminders"
        const val EXTRA_TRIGGER = "trigger"
        private const val DELIVERED = -1L
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = EntryStore(context)
        val reminders = ReminderAlarms(context)
        val id = intent.data?.lastPathSegment ?: return
        val entry = store.get(id) ?: return
        reminders.deliver(entry, intent.getLongExtra(ReminderAlarms.EXTRA_TRIGGER, 0))
    }
}

class RestoreRemindersReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clockChanged = when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> true
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> false
            else -> return
        }
        ReminderAlarms(context).restore(EntryStore(context).all(), clockChanged)
    }
}

