package ir.mmd.dodota.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.preferences.UserPreferencesRepository
import ir.mmd.dodota.ui.components.DodotaTopBar
import kotlinx.coroutines.launch

@Composable
fun RecoveryScreen(
    userPreferences: UserPreferences,
    preferencesRepo: UserPreferencesRepository,
    onBack: () -> Unit,
    onRecovered: () -> Unit
) {
    var enteredCode by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "بازیابی رمز عبور",
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ورود با کد بازیابی اضطراری",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "کد بازیابی که در زمان راه‌اندازی برنامه دریافت کردید را وارد کنید (مانند: DODOTA-XXXX-XXXX).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                OutlinedTextField(
                    value = enteredCode,
                    onValueChange = { enteredCode = it.uppercase() },
                    label = { Text("کد بازیابی") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = {
                    val cleanEntered = enteredCode.trim().uppercase()
                    val cleanStored = userPreferences.recoveryCode.trim().uppercase()

                    if (cleanEntered.isNotEmpty() && (cleanEntered == cleanStored || cleanStored.isEmpty())) {
                        coroutineScope.launch {
                            // Disable lock so user can access and set new PIN in Settings
                            preferencesRepo.setAppLock(
                                enabled = false,
                                pinHash = "",
                                recoveryCode = cleanStored,
                                biometric = false
                            )
                            Toast.makeText(context, "قفل برنامه با موفقیت بازنشانی شد", Toast.LENGTH_SHORT).show()
                            onRecovered()
                        }
                    } else {
                        Toast.makeText(context, "کد بازیابی وارد شده معتبر نیست", Toast.LENGTH_LONG).show()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("تأیید و ورود به برنامه")
            }
        }
    }
}
