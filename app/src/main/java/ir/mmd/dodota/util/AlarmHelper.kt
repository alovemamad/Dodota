package ir.mmd.dodota.util

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import ir.mmd.dodota.MainActivity
import ir.mmd.dodota.data.local.DodotaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

object AlarmHelper {

    const val ACTION_REMINDER_ALARM = "ir.mmd.dodota.ACTION_REMINDER_ALARM"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
    const val EXTRA_REMINDER_DESC = "extra_reminder_desc"

    // Deduplication cache to avoid firing the same reminder twice within 45 seconds
    private val recentlyTriggeredMap = ConcurrentHashMap<Long, Long>()

    fun markReminderTriggered(reminderId: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = recentlyTriggeredMap[reminderId]
        if (last != null && (now - last) < 45_000L) {
            return false
        }
        recentlyTriggeredMap[reminderId] = now
        return true
    }

    /**
     * Returns all runtime permissions required on startup for Notifications/Reminders & Backup.
     */
    fun getRequiredStartupPermissions(): List<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return list
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBackupPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppNotificationSettings(context)
            }
        }
    }

    fun openAppNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Computes the trigger timestamp intelligently:
     * 1. Applies -offsetDays and hour:minute.
     * 2. If offsetDays > 0 caused the timestamp to fall in the past while the due date itself at hour:minute
     *    is still in the future, falls back to the due date at hour:minute.
     * 3. If the user selected TODAY and the hour:minute is the current minute (or earlier today),
     *    schedules it for 5 seconds from now so the user receives the alarm reliably instead of losing it.
     */
    fun computeSmartTriggerMillis(
        dueDateMillis: Long,
        offsetDays: Int,
        hour: Int,
        minute: Int
    ): Long {
        val now = System.currentTimeMillis()

        val withOffsetCal = Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            add(Calendar.DAY_OF_YEAR, -offsetDays)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (withOffsetCal.timeInMillis > now) {
            return withOffsetCal.timeInMillis
        }

        // If offsetDays > 0 pushed it into the past, try the exact dueDate without offset
        if (offsetDays > 0) {
            val exactDueCal = Calendar.getInstance().apply {
                timeInMillis = dueDateMillis
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (exactDueCal.timeInMillis > now) {
                return exactDueCal.timeInMillis
            }
        }

        // If the user is scheduling a reminder for today and the selected time is right now or earlier today,
        // set it to 5 seconds in the future so AlarmManager fires immediately and reliably.
        return now + 5_000L
    }

    /**
     * Schedules an exact alarm that fires even if the app is completely closed or device in Doze mode.
     */
    fun scheduleExactAlarm(
        context: Context,
        reminderId: Long,
        title: String,
        description: String,
        triggerAtMillis: Long,
        isFromBoot: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        if (isFromBoot && triggerAtMillis <= now) return

        // If scheduled interactively by user and triggerAtMillis is <= now, fire in 4 seconds
        val effectiveTriggerMillis = if (triggerAtMillis <= now) {
            now + 4_000L
        } else {
            triggerAtMillis
        }

        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_REMINDER_TITLE, title)
            putExtra(EXTRA_REMINDER_DESC, description)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val showIntent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val showPendingIntent = PendingIntent.getActivity(
                    appContext,
                    reminderId.toInt(),
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmClockInfo = AlarmManager.AlarmClockInfo(effectiveTriggerMillis, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, effectiveTriggerMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, effectiveTriggerMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, effectiveTriggerMillis, pendingIntent)
                }
            } catch (_: Exception) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, effectiveTriggerMillis, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, effectiveTriggerMillis, pendingIntent)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun cancelAlarm(context: Context, reminderId: Long) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.cancel(pendingIntent)
        } catch (_: Exception) {}
    }

    /**
     * Re-schedules all active future reminders from Room database on app launch or boot.
     */
    fun rescheduleAllActiveReminders(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = DodotaDatabase.getDatabase(appContext)
                val activeReminders = db.reminderDao().getAllRemindersSync().filter { !it.isCompleted && it.isEnabled }
                val now = System.currentTimeMillis()
                for (reminder in activeReminders) {
                    if (reminder.dueDateTimeMillis > now) {
                        scheduleExactAlarm(
                            context = appContext,
                            reminderId = reminder.id,
                            title = reminder.title,
                            description = reminder.description.ifEmpty { "سررسید موعد فرا رسیده است" },
                            triggerAtMillis = reminder.dueDateTimeMillis,
                            isFromBoot = true
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
