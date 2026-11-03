package ir.mmd.dodota.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.mmd.dodota.MainActivity
import ir.mmd.dodota.R

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val reminderId = intent.getLongExtra(AlarmHelper.EXTRA_REMINDER_ID, System.currentTimeMillis())
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, reminderId.toInt())
        val title = intent.getStringExtra(AlarmHelper.EXTRA_REMINDER_TITLE) ?: "یادآور موعد مالی و چک"
        val desc = intent.getStringExtra(AlarmHelper.EXTRA_REMINDER_DESC) ?: "سررسید چک، قسط یا بدهی فرا رسیده است."

        when (action) {
            ACTION_DISMISS_ALARM -> {
                AlarmSoundManager.stopAlarm(context, notifId)
            }
            ACTION_SNOOZE_ALARM -> {
                AlarmSoundManager.snoozeAlarm(context, reminderId, title, desc, notifId, minutes = 10)
            }
            else -> {
                if (AlarmHelper.markReminderTriggered(reminderId)) {
                    // Start sound & in-app ringing manager
                    AlarmSoundManager.startAlarm(context, reminderId, notifId, title, desc)
                    showAlarmNotification(context, reminderId, notifId, title, desc)
                }
            }
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID = "dodota_alarm_high_priority_channel_v3"
        const val ALARM_CHANNEL_NAME = "هشدار و آلارم فوری سررسید"
        const val ACTION_DISMISS_ALARM = "ir.mmd.dodota.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "ir.mmd.dodota.ACTION_SNOOZE_ALARM"
        const val ACTION_OPEN_ALARM = "ir.mmd.dodota.ACTION_OPEN_ALARM"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val DEFAULT_ALARM_NOTIF_ID = 998877

        fun ensureAlarmChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    ALARM_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "آلارم موعد چک‌ها، اقساط و بدهی‌ها حتی هنگام بسته بودن برنامه"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 600)
                    // Audio stream is managed exclusively by AlarmSoundManager for instant stop & no duplicate echo
                    setSound(null, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun showAlarmNotification(
            context: Context,
            reminderId: Long = 0L,
            notifId: Int,
            title: String,
            message: String
        ) {
            ensureAlarmChannel(context)
            val effectiveReminderId = if (reminderId > 0L) reminderId else notifId.toLong()

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // 1. Action: Stop Alarm / Dismiss
            val dismissIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_DISMISS_ALARM
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
                putExtra(AlarmHelper.EXTRA_REMINDER_ID, effectiveReminderId)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                notifId * 3 + 1,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 2. Action: Snooze 10 minutes
            val snoozeIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE_ALARM
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
                putExtra(AlarmHelper.EXTRA_REMINDER_ID, effectiveReminderId)
                putExtra(AlarmHelper.EXTRA_REMINDER_TITLE, title)
                putExtra(AlarmHelper.EXTRA_REMINDER_DESC, message)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                notifId * 3 + 2,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 3. Open App Intent
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_ALARM
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NOTIFICATION_ID, notifId)
                putExtra(AlarmHelper.EXTRA_REMINDER_ID, effectiveReminderId)
                putExtra(AlarmHelper.EXTRA_REMINDER_TITLE, title)
                putExtra(AlarmHelper.EXTRA_REMINDER_DESC, message)
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(longArrayOf(0, 600, 250, 600, 250, 600))
                .setContentIntent(contentPendingIntent)
                .setDeleteIntent(dismissPendingIntent)
                .setAutoCancel(true)
                .addAction(
                    R.drawable.ic_alarm_off,
                    "قطع زنگ",
                    dismissPendingIntent
                )
                .addAction(
                    R.drawable.ic_snooze,
                    "۱۰ دقیقه تعویق",
                    snoozePendingIntent
                )

            try {
                NotificationManagerCompat.from(context).notify(notifId, builder.build())
            } catch (_: Exception) {}
        }
    }
}
