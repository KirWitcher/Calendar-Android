package com.example.calendar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import com.example.calendar.ui.theme.CalendarTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withCalendarLanguage())
    }

    private val store by lazy { EntryStore(this) }
    private val alarms by lazy { ReminderAlarms(this) }
    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var entries by mutableStateOf(emptyList<Entry>())
    private var notificationsEnabled by mutableStateOf(false)
    private var exactEnabled by mutableStateOf(false)
    private var today by mutableStateOf(LocalDate.now())
    private var openEntryId by mutableStateOf<String?>(null)
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val background = android.graphics.Color.rgb(19, 23, 25)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(background)
        )
        alarms.createChannel()
        entries = store.all()
        exactEnabled = alarms.exactEnabled()
        alarms.restore(entries)
        refreshPermissions()
        if (savedInstanceState == null) openFromNotification(intent)
        setContent {
            CalendarTheme {
                Daymark(
                    entries = entries,
                    today = today,
                    notificationsEnabled = notificationsEnabled,
                    exactEnabled = exactEnabled,
                    openEntryId = openEntryId,
                    onOpenEntryHandled = { openEntryId = null },
                    onEnableNotifications = ::enableNotifications,
                    onEnableExact = ::enableExact,
                    onLanguageChange = { language ->
                        saveCalendarLanguage(language)
                        recreate()
                    },
                    onSave = ::save,
                    onDelete = ::delete
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        today = LocalDate.now()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromNotification(intent)
    }

    private fun openFromNotification(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == "daymark" && uri.host == "entry") openEntryId = uri.lastPathSegment
    }

    private fun save(entry: Entry): Boolean {
        if (!store.save(entry)) return false
        alarms.replace(entry)
        entries = entries.filterNot { it.id == entry.id } + entry
        if (entry.reminders.isNotEmpty() && !notificationsEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !settings.getBoolean("asked_notifications", false)
        ) enableNotifications()
        return true
    }

    private fun delete(id: String): Boolean {
        if (!store.delete(id)) return false
        alarms.cancel(id)
        entries = entries.filterNot { it.id == id }
        return true
    }

    private fun refreshPermissions() {
        notificationsEnabled = alarms.notificationsEnabled()
        val allowed = alarms.exactEnabled()
        if (exactEnabled != allowed) {
            exactEnabled = allowed
            alarms.restore(entries)
        }
    }

    private fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            (!settings.getBoolean("asked_notifications", false) ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            settings.edit { putBoolean("asked_notifications", true) }
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun enableExact() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", packageName, null))
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }
}

