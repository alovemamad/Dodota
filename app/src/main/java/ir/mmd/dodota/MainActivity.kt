package ir.mmd.dodota

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import ir.mmd.dodota.data.local.DodotaDatabase
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.preferences.UserPreferencesRepository
import ir.mmd.dodota.data.repository.BackupRepository
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.DodotaApp
import ir.mmd.dodota.ui.theme.DodotaTheme

class MainActivity : FragmentActivity() {

    private lateinit var database: DodotaDatabase
    private lateinit var repository: DodotaRepository
    private lateinit var preferencesRepo: UserPreferencesRepository
    private lateinit var backupRepository: BackupRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = DodotaDatabase.getDatabase(applicationContext)
        repository = DodotaRepository(database)
        preferencesRepo = UserPreferencesRepository(applicationContext)
        backupRepository = BackupRepository(applicationContext, database)

        setContent {
            val userPreferences by preferencesRepo.userPreferencesFlow.collectAsState(initial = UserPreferences())

            DodotaTheme(userPreferences = userPreferences) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DodotaApp(
                        repository = repository,
                        preferencesRepo = preferencesRepo,
                        backupRepository = backupRepository
                    )
                }
            }
        }
    }
}
