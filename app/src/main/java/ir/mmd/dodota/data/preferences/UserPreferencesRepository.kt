package ir.mmd.dodota.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import ir.mmd.dodota.data.model.AppCurrency
import ir.mmd.dodota.data.model.AppFontFamilyOption
import ir.mmd.dodota.data.model.AppThemeMode
import ir.mmd.dodota.util.SecurityHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "dodota_user_prefs")

data class UserPreferences(
    val currency: AppCurrency = AppCurrency.TOMAN,
    val themeMode: AppThemeMode = AppThemeMode.VIP_RED,
    val fontFamily: AppFontFamilyOption = AppFontFamilyOption.VAZIRMATN,
    val fontScale: Float = 1.0f,
    val isAppLockEnabled: Boolean = false,
    val pinHash: String = "",
    val recoveryCode: String = "",
    val isBiometricEnabled: Boolean = false,
    val autoLockTimeoutMinutes: Int = 1,
    val isWeeklyBackupEnabled: Boolean = true,
    val lastBackupMillis: Long = 0L,
    val isOnboardingCompleted: Boolean = false
) {
    val isPinEnabled: Boolean get() = isAppLockEnabled && pinHash.isNotBlank()
    val colorTheme: AppThemeMode get() = themeMode
}

class UserPreferencesRepository(private val context: Context) {

    private val dataStore = context.userDataStore

    private object PreferencesKeys {
        val CURRENCY = stringPreferencesKey("app_currency")
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val FONT_FAMILY = stringPreferencesKey("app_font_family")
        val FONT_SCALE = floatPreferencesKey("app_font_scale")
        val IS_APP_LOCK_ENABLED = booleanPreferencesKey("is_app_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val RECOVERY_CODE = stringPreferencesKey("recovery_code")
        val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        val AUTO_LOCK_TIMEOUT_MINUTES = intPreferencesKey("auto_lock_timeout_minutes")
        val IS_WEEKLY_BACKUP_ENABLED = booleanPreferencesKey("is_weekly_backup_enabled")
        val LAST_BACKUP_MILLIS = longPreferencesKey("last_backup_millis")
        val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val currencyStr = preferences[PreferencesKeys.CURRENCY] ?: AppCurrency.TOMAN.name
            val themeStr = preferences[PreferencesKeys.THEME_MODE] ?: AppThemeMode.VIP_RED.name
            val fontStr = preferences[PreferencesKeys.FONT_FAMILY] ?: AppFontFamilyOption.VAZIRMATN.name
            val fontScale = (preferences[PreferencesKeys.FONT_SCALE] ?: 1.0f).coerceIn(0.80f, 1.35f)
            val currency = try { AppCurrency.valueOf(currencyStr) } catch (_: Exception) { AppCurrency.TOMAN }
            val theme = try {
                when (themeStr) {
                    "GREEN" -> AppThemeMode.EMERALD
                    "BLUE" -> AppThemeMode.BLUBANK
                    else -> AppThemeMode.valueOf(themeStr)
                }
            } catch (_: Exception) { AppThemeMode.VIP_RED }
            val fontFamily = try {
                AppFontFamilyOption.valueOf(fontStr)
            } catch (_: Exception) {
                AppFontFamilyOption.VAZIRMATN
            }

            UserPreferences(
                currency = currency,
                themeMode = theme,
                fontFamily = fontFamily,
                fontScale = fontScale,
                isAppLockEnabled = preferences[PreferencesKeys.IS_APP_LOCK_ENABLED] ?: false,
                pinHash = preferences[PreferencesKeys.PIN_HASH] ?: "",
                recoveryCode = preferences[PreferencesKeys.RECOVERY_CODE] ?: "",
                isBiometricEnabled = preferences[PreferencesKeys.IS_BIOMETRIC_ENABLED] ?: false,
                autoLockTimeoutMinutes = preferences[PreferencesKeys.AUTO_LOCK_TIMEOUT_MINUTES] ?: 1,
                isWeeklyBackupEnabled = preferences[PreferencesKeys.IS_WEEKLY_BACKUP_ENABLED] ?: true,
                lastBackupMillis = preferences[PreferencesKeys.LAST_BACKUP_MILLIS] ?: 0L,
                isOnboardingCompleted = preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] ?: false
            )
        }

    suspend fun setCurrency(currency: AppCurrency) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENCY] = currency.name
        }
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setFontFamily(fontFamily: AppFontFamilyOption) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_FAMILY] = fontFamily.name
        }
    }

    suspend fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.80f, 1.35f)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_SCALE] = clamped
        }
    }

    suspend fun setColorTheme(colorTheme: AppThemeMode) {
        setThemeMode(colorTheme)
    }

    suspend fun setPin(pin: String, recoveryCode: String) {
        val hash = SecurityHelper.hashPin(pin)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_APP_LOCK_ENABLED] = true
            preferences[PreferencesKeys.PIN_HASH] = hash
            preferences[PreferencesKeys.RECOVERY_CODE] = recoveryCode
        }
    }

    suspend fun disablePin() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_APP_LOCK_ENABLED] = false
            preferences[PreferencesKeys.PIN_HASH] = ""
            preferences[PreferencesKeys.IS_BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setAppLock(enabled: Boolean, pinHash: String, recoveryCode: String, biometric: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_APP_LOCK_ENABLED] = enabled
            preferences[PreferencesKeys.PIN_HASH] = pinHash
            if (recoveryCode.isNotEmpty()) {
                preferences[PreferencesKeys.RECOVERY_CODE] = recoveryCode
            }
            preferences[PreferencesKeys.IS_BIOMETRIC_ENABLED] = biometric
        }
    }

    suspend fun updateBiometric(enabled: Boolean) {
        setBiometricEnabled(enabled)
    }

    suspend fun updatePin(newPinHash: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PIN_HASH] = newPinHash
        }
    }

    suspend fun updateRecoveryCode(newCode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECOVERY_CODE] = newCode
        }
    }

    suspend fun setAutoLockTimeout(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_LOCK_TIMEOUT_MINUTES] = minutes
        }
    }

    suspend fun setWeeklyBackup(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_WEEKLY_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun updateLastBackupTime(millis: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_BACKUP_MILLIS] = millis
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun clearAllPreferences() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
