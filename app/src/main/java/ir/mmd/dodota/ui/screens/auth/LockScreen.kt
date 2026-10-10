package ir.mmd.dodota.ui.screens.auth

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.util.PersianNumberFormatter
import ir.mmd.dodota.util.SecurityHelper

@Composable
fun LockScreen(
    userPreferences: UserPreferences,
    onUnlocked: () -> Unit,
    onNavigateToRecovery: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Trigger biometric prompt on launch if enabled
    LaunchedEffect(userPreferences.isBiometricEnabled) {
        if (userPreferences.isBiometricEnabled && context is FragmentActivity) {
            try {
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(
                    context,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onUnlocked()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("ورود به دودوتا")
                    .setSubtitle("احراز هویت بیومتریک")
                    .setNegativeButtonText("ورود با رمز")
                    .build()

                biometricPrompt.authenticate(promptInfo)
            } catch (_: Exception) {
            }
        }
    }

    fun handleDigitPress(digit: String) {
        if (enteredPin.length < 6) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            isError = false

            if (newPin.length >= 4) {
                if (SecurityHelper.verifyPin(newPin, userPreferences.pinHash)) {
                    onUnlocked()
                } else if (newPin.length == 6) {
                    isError = true
                    Toast.makeText(context, "رمز عبور نادرست است", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            isError = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Lock Icon & Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "رمز عبور را وارد کنید",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isError) "رمز وارد شده نادرست است" else "ورود امن به حساب‌های مالی",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    (0 until 4).forEach { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isError -> MaterialTheme.colorScheme.error
                                        isFilled -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )
                    }
                }
            }

            // Numeric Keypad (Left-To-Right with English digits)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keypadRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("BIO", "0", "DEL")
                    )

                    keypadRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(0.85f),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    "DEL" -> {
                                        IconButton(
                                            onClick = { handleBackspace() },
                                            modifier = Modifier.size(68.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                                contentDescription = "پاک کردن",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    "BIO" -> {
                                        if (userPreferences.isBiometricEnabled) {
                                            IconButton(
                                                onClick = {
                                                    if (context is FragmentActivity) {
                                                        val executor = ContextCompat.getMainExecutor(context)
                                                        val biometricPrompt = BiometricPrompt(
                                                            context,
                                                            executor,
                                                            object : BiometricPrompt.AuthenticationCallback() {
                                                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                                    super.onAuthenticationSucceeded(result)
                                                                    onUnlocked()
                                                                }
                                                            }
                                                        )
                                                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                                            .setTitle("ورود به دودوتا")
                                                            .setNegativeButtonText("ورود با رمز")
                                                            .build()
                                                        biometricPrompt.authenticate(promptInfo)
                                                    }
                                                },
                                                modifier = Modifier.size(68.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "اثر انگشت",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(68.dp))
                                        }
                                    }
                                    else -> {
                                        KeypadButton(
                                            text = key,
                                            onClick = { handleDigitPress(key) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Forgot PIN / Recovery Button
            TextButton(
                onClick = onNavigateToRecovery,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = "رمز عبور را فراموش کرده‌اید؟ (ورود با کد بازیابی)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .size(68.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
