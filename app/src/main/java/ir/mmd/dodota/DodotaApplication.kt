package ir.mmd.dodota

import android.app.Application
import ir.mmd.dodota.util.AlarmHelper
import ir.mmd.dodota.util.NotificationHelper
import ir.mmd.dodota.util.ReminderAlarmReceiver
import ir.mmd.dodota.worker.NightlyBackupWorker

class DodotaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        ReminderAlarmReceiver.ensureAlarmChannel(this)
        AlarmHelper.rescheduleAllActiveReminders(this)
        NightlyBackupWorker.schedule(this)
    }
}
