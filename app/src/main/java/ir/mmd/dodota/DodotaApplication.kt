package ir.mmd.dodota

import android.app.Application
import ir.mmd.dodota.util.AlarmHelper
import ir.mmd.dodota.util.NotificationHelper
import ir.mmd.dodota.util.ReminderAlarmReceiver
import ir.mmd.dodota.worker.NightlyBackupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DodotaApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        ReminderAlarmReceiver.ensureAlarmChannel(this)
        AlarmHelper.rescheduleAllActiveReminders(this)
        appScope.launch {
            try {
                NightlyBackupWorker.schedule(this@DodotaApplication)
            } catch (_: Throwable) {}
        }
    }
}
