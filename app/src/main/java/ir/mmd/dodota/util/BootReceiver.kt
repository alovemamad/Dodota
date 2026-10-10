package ir.mmd.dodota.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.mmd.dodota.data.local.DodotaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = DodotaDatabase.getDatabase(context)
                    val activeReminders = db.reminderDao().getAllRemindersSync().filter { !it.isCompleted && it.isEnabled }
                    val now = System.currentTimeMillis()
                    for (reminder in activeReminders) {
                        if (reminder.dueDateTimeMillis > now) {
                            AlarmHelper.scheduleExactAlarm(
                                context = context,
                                reminderId = reminder.id,
                                title = reminder.title,
                                description = reminder.description.ifEmpty { "سررسید موعد فرا رسیده است" },
                                triggerAtMillis = reminder.dueDateTimeMillis,
                                isFromBoot = true
                            )
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
