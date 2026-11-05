package ir.mmd.dodota

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import ir.mmd.dodota.data.local.DodotaDatabase
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.preferences.UserPreferencesRepository
import ir.mmd.dodota.data.repository.BackupRepository
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.DodotaApp
import ir.mmd.dodota.ui.theme.DodotaTheme
import ir.mmd.dodota.util.AlarmHelper
import ir.mmd.dodota.util.AlarmSoundManager
import ir.mmd.dodota.util.PersianNumberFormatter
import ir.mmd.dodota.util.ReminderAlarmReceiver

class MainActivity : FragmentActivity() {

    private lateinit var database: DodotaDatabase
    private lateinit var repository: DodotaRepository
    private lateinit var preferencesRepo: UserPreferencesRepository
    private lateinit var backupRepository: BackupRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        database = DodotaDatabase.getDatabase(applicationContext)
        repository = DodotaRepository(database)
        preferencesRepo = UserPreferencesRepository(applicationContext)
        backupRepository = BackupRepository(applicationContext, database)

        handleAlarmIntent(intent)

        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL
        val faLocale = java.util.Locale("fa", "IR")
        java.util.Locale.setDefault(faLocale)

        setContent {
            val userPreferences by preferencesRepo.userPreferencesFlow.collectAsState(initial = UserPreferences())
            PersianNumberFormatter.globalUseEnglishDigits = userPreferences.currency.isEnglishDigits

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
            ) {
                DodotaTheme(userPreferences = userPreferences) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        DodotaApp(
                            repository = repository,
                            preferencesRepo = preferencesRepo,
                            backupRepository = backupRepository,
                            userPreferences = userPreferences
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmIntent(intent)
    }

    private fun handleAlarmIntent(intent: Intent?) {
        if (intent == null) return
        val notifId = intent.getIntExtra(ReminderAlarmReceiver.EXTRA_NOTIFICATION_ID, 0)
        if (notifId != 0) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notifId)
        }
        val reminderId = intent.getLongExtra(AlarmHelper.EXTRA_REMINDER_ID, 0L)
        if (reminderId > 0L && intent.action == ReminderAlarmReceiver.ACTION_OPEN_ALARM) {
            val title = intent.getStringExtra(AlarmHelper.EXTRA_REMINDER_TITLE) ?: "یادآور مالی"
            val desc = intent.getStringExtra(AlarmHelper.EXTRA_REMINDER_DESC) ?: ""
            // Ensure AlarmSoundManager active alarm is populated for the dialog if it wasn't already
            if (AlarmSoundManager.activeAlarmState.value == null) {
                AlarmSoundManager.startAlarm(this, reminderId, notifId, title, desc)
            }
        }
    }
}
