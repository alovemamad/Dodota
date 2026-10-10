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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.mmd.dodota.MainActivity
import ir.mmd.dodota.R

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(AlarmHelper.EXTRA_REMINDER_ID, System.currentTimeMillis())
        val title = intent.getStringExtra(AlarmHelper.EXTRA_REMINDER_TITLE) ?: "یادآور موعد مالی و چک"
        val desc = intent.getStringExtra(AlarmHelper.EXTRA_REMINDER_DESC) ?: "سررسید چک، قسط یا بدهی فرا رسیده است."

        if (AlarmHelper.markReminderTriggered(reminderId)) {
            showAlarmNotification(context, reminderId.toInt(), title, desc)
        }
    }

    companion object {
        const val ALARM_CHANNEL_ID = "dodota_alarm_high_priority_channel_v2"
        const val ALARM_CHANNEL_NAME = "هشدار و آلارم فوری سررسید"

        fun ensureAlarmChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                val channel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    ALARM_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "آلارم موعد چک‌ها، اقساط و بدهی‌ها حتی هنگام بسته بودن برنامه"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 600)
                    setSound(soundUri, audioAttributes)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun showAlarmNotification(context: Context, notifId: Int, title: String, message: String) {
            ensureAlarmChannel(context)

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Trigger physical vibration as well for immediate feedback
            try {
                val pattern = longArrayOf(0, 500, 200, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v?.vibrate(pattern, -1)
                    }
                }
            } catch (_: Exception) {}

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
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
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 600, 250, 600, 250, 600))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            try {
                NotificationManagerCompat.from(context).notify(notifId, builder.build())
            } catch (_: Exception) {}
        }
    }
}
