package ir.mmd.dodota.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveAlarmInfo(
    val reminderId: Long,
    val notifId: Int,
    val title: String,
    val desc: String,
    val triggerTimeMillis: Long = System.currentTimeMillis()
)

object AlarmSoundManager {

    private val _activeAlarmState = MutableStateFlow<ActiveAlarmInfo?>(null)
    val activeAlarmState: StateFlow<ActiveAlarmInfo?> = _activeAlarmState.asStateFlow()

    private var currentRingtone: Ringtone? = null
    private var currentVibrator: Vibrator? = null
    private var timeoutJob: Job? = null
    private var lastAlarmStartTime: Long = 0L

    /**
     * Starts continuous ringing, vibration, and in-app state for an active alarm.
     */
    fun startAlarm(
        context: Context,
        reminderId: Long,
        notifId: Int,
        title: String,
        desc: String
    ) {
        val appContext = context.applicationContext

        // Set active alarm state for In-App dialog
        _activeAlarmState.value = ActiveAlarmInfo(
            reminderId = reminderId,
            notifId = notifId,
            title = title,
            desc = desc
        )

        val now = System.currentTimeMillis()
        if (currentRingtone?.isPlaying == true && (now - lastAlarmStartTime) < 15_000L) {
            // Already ringing! Do not start another audio stream concurrently
            return
        }
        lastAlarmStartTime = now

        // Stop any previous instance first
        stopAudio()
        stopVibration()

        // 1. Play Alarm Ringtone
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI

            val ringtone = RingtoneManager.getRingtone(appContext, soundUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                ringtone?.audioAttributes = audioAttributes
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
            currentRingtone = ringtone
        } catch (_: Exception) {}

        // 2. Play Repeating Vibration
        try {
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val v = vm?.defaultVibrator
                currentVibrator = v
                v?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val v = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                currentVibrator = v
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(pattern, 0)
                }
            }
        } catch (_: Exception) {}

        // 3. Auto-stop after 60 seconds to prevent indefinite ringing
        timeoutJob?.cancel()
        timeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(60_000L)
            stopAlarm(appContext, notifId)
        }
    }

    /**
     * Completely stops ringing, vibration, dismisses the notification, and clears active state.
     */
    fun stopAlarm(context: Context, notifId: Int? = null) {
        timeoutJob?.cancel()
        timeoutJob = null

        stopAudio()
        stopVibration()

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notifId != null && notifId != 0) {
                nm.cancel(notifId)
            } else {
                val current = _activeAlarmState.value
                if (current != null && current.notifId != 0) {
                    nm.cancel(current.notifId)
                }
            }
        } catch (_: Exception) {}

        _activeAlarmState.value = null
    }

    /**
     * Stops current alarm sound and schedules snooze for 10 minutes.
     */
    fun snoozeAlarm(
        context: Context,
        reminderId: Long,
        title: String,
        desc: String,
        notifId: Int? = null,
        minutes: Int = 10
    ) {
        val appContext = context.applicationContext
        stopAlarm(appContext, notifId)

        val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val validReminderId = if (reminderId > 0) reminderId else System.currentTimeMillis()

        AlarmHelper.scheduleExactAlarm(
            context = appContext,
            reminderId = validReminderId,
            title = title,
            description = desc,
            triggerAtMillis = triggerTime
        )

        try {
            val msg = "یادآور به مدت ${PersianNumberFormatter.formatDigits(minutes)} دقیقه به تعویق افتاد"
            Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {}
    }

    private fun stopAudio() {
        try {
            if (currentRingtone?.isPlaying == true) {
                currentRingtone?.stop()
            }
            currentRingtone = null
        } catch (_: Exception) {}
    }

    private fun stopVibration() {
        try {
            currentVibrator?.cancel()
            currentVibrator = null
        } catch (_: Exception) {}
    }
}
