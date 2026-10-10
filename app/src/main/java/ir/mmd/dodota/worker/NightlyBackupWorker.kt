package ir.mmd.dodota.worker

import android.content.Context
import androidx.work.*
import ir.mmd.dodota.data.local.DodotaDatabase
import ir.mmd.dodota.data.preferences.UserPreferencesRepository
import ir.mmd.dodota.data.repository.BackupRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class NightlyBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefsRepo = UserPreferencesRepository(context)
            val prefs = prefsRepo.userPreferencesFlow.first()

            if (prefs.isWeeklyBackupEnabled) {
                val db = DodotaDatabase.getDatabase(context)
                val backupRepo = BackupRepository(context, db)
                // Save JSON file in MyFinanceBackup folder
                val file = backupRepo.saveBackupToFile("MyFinance_AutoBackup_Nightly.json")
                if (file.exists() && file.length() > 0) {
                    prefsRepo.updateLastBackupTime(System.currentTimeMillis())
                    Result.success()
                } else {
                    Result.retry()
                }
            } else {
                Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "dodota_nightly_backup_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            // Calculate initial delay until 03:00 AM
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(currentDate)) {
                    add(Calendar.HOUR_OF_DAY, 24)
                }
            }
            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

            val nightlyWorkRequest = PeriodicWorkRequestBuilder<NightlyBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                nightlyWorkRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
