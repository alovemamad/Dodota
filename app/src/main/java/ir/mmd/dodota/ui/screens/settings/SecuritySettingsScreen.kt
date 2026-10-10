package ir.mmd.dodota.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.preferences.UserPreferencesRepository
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.FeatureToggleCard
import ir.mmd.dodota.util.SecurityHelper
import kotlinx.coroutines.launch

@Composable
fun SecuritySettingsScreen(
    userPreferences: UserPreferences,
    preferencesRepo: UserPreferencesRepository,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showSetPinDialog by remember { mutableStateOf(false) }
    var generatedRecoveryCode by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "امنیت و قفل برنامه",
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                FeatureToggleCard(
                    icon = Icons.Default.Lock,
                    title = "فعال‌سازی قفل ورود (PIN)",
                    description = if (userPreferences.isPinEnabled) "رمز ۴ تا ۶ رقمی فعال است" else "برای ورود به برنامه نیاز به رمز است",
                    isChecked = userPreferences.isPinEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showSetPinDialog = true
                        } else {
                            coroutineScope.launch {
                                preferencesRepo.disablePin()
                                Toast.makeText(context, "قفل برنامه غیرفعال شد", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            if (userPreferences.isPinEnabled) {
                item {
                    FeatureToggleCard(
                        icon = Icons.Default.Fingerprint,
                        title = "ورود با اثر انگشت / بیومتریک",
                        description = "باز کردن سریع برنامه بدون نیاز به تایپ پین",
                        isChecked = userPreferences.isBiometricEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                preferencesRepo.setBiometricEnabled(enabled)
                            }
                        }
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "مدیریت رمز و کد بازیابی",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "می‌توانید پین ورود جدید تنظیم کرده یا کد بازیابی اضطراری را ذخیره و کپی کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showSetPinDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("تغییر PIN")
                                }

                                if (userPreferences.recoveryCode.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Recovery Code", userPreferences.recoveryCode)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "کد بازیابی کپی شد: ${userPreferences.recoveryCode}", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("کپی کد بازیابی")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSetPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text("تعیین رمز ۴ تا ۶ رقمی") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it },
                        label = { Text("رمز عبور جدید") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 6) confirmPinInput = it },
                        label = { Text("تکرار رمز عبور") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length < 4) {
                            Toast.makeText(context, "رمز باید حداقل ۴ رقم باشد", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (pinInput != confirmPinInput) {
                            Toast.makeText(context, "رمز و تکرار آن یکسان نیستند", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val recoveryCode = SecurityHelper.generateRecoveryCode()
                        coroutineScope.launch {
                            preferencesRepo.setPin(pinInput, recoveryCode)
                            generatedRecoveryCode = recoveryCode
                            showSetPinDialog = false
                        }
                    }
                ) {
                    Text("تأیید و فعال‌سازی")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    if (generatedRecoveryCode != null) {
        AlertDialog(
            onDismissRequest = { generatedRecoveryCode = null },
            title = { Text("کد بازیابی اضطراری شما") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "این کد را در جای امنی یادداشت کنید. در صورت فراموشی پین، تنها راه ورود استفاده از این کد خواهد بود:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = generatedRecoveryCode!!,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Recovery Code", generatedRecoveryCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "کد بازیابی با موفقیت کپی شد", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("کپی کردن کد بازیابی")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { generatedRecoveryCode = null }) {
                    Text("کد را یادداشت کردم")
                }
            }
        )
    }
}
