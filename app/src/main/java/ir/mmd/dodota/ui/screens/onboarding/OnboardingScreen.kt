package ir.mmd.dodota.ui.screens.onboarding

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mmd.dodota.R
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.model.AccountType
import ir.mmd.dodota.data.model.AppCurrency
import ir.mmd.dodota.data.model.AppThemeMode
import ir.mmd.dodota.data.preferences.UserPreferencesRepository
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.components.PersianAmountInput
import ir.mmd.dodota.util.PersianNumberFormatter
import ir.mmd.dodota.util.SecurityHelper
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    repository: DodotaRepository,
    preferencesRepo: UserPreferencesRepository,
    onFinished: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // Steps 1 to 5
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // State collected across steps
    var selectedCurrency by remember { mutableStateOf(AppCurrency.TOMAN) }
    var selectedTheme by remember { mutableStateOf(AppThemeMode.VIP_RED) }
    var accountName by remember { mutableStateOf("کارت بانکی اصلی") }
    var selectedAccountType by remember { mutableStateOf(AccountType.BANK_CARD) }
    var initialBalanceText by remember { mutableStateOf("") }
    var enableAppLock by remember { mutableStateOf(false) }
    var pinText by remember { mutableStateOf("") }
    var confirmPinText by remember { mutableStateOf("") }
    var generatedRecoveryCode by remember { mutableStateOf(SecurityHelper.generateRecoveryCode()) }
    var enableBiometric by remember { mutableStateOf(false) }
    var enableWeeklyBackup by remember { mutableStateOf(true) }

    val completeFast = {
        coroutineScope.launch {
            preferencesRepo.setCurrency(selectedCurrency)
            preferencesRepo.setThemeMode(selectedTheme)
            val balance = PersianNumberFormatter.parseAmount(initialBalanceText)
            repository.insertAccount(
                AccountEntity(
                    name = accountName.ifBlank { "کارت بانکی اصلی" },
                    type = selectedAccountType,
                    balance = balance,
                    bankName = "بانک اصلی"
                )
            )
            preferencesRepo.setOnboardingCompleted(true)
            onFinished()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header with Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "گام ${PersianNumberFormatter.toPersianDigits(step.toString())} از ۵",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { completeFast() }) {
                    Text("رد شدن و شروع سریع")
                }
            }

            // Top Progress Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                (1..5).forEach { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(6.dp)
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (i <= step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            // Step Content Animated
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn() + slideInHorizontally { width -> if (targetState > initialState) width else -width } togetherWith
                            fadeOut() + slideOutHorizontally { width -> if (targetState > initialState) -width else width }
                },
                label = "onboarding_step"
            ) { currentStep ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (currentStep) {
                        1 -> {
                            // Step 1: Welcome, Currency & VIP Red Theme
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.dodota_app_icon_v2),
                                    contentDescription = "دودوتا",
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "به «دودوتا» خوش آمدید",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "حساب و کتاب دخل و خرج، اقساط، چک‌ها و بودجه‌بندی با طراحی شیشه‌ای VIP Red",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "واحد پول پیش‌فرض را انتخاب کنید:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CurrencyOptionCard(
                                        title = "تومان",
                                        subtitle = "با ارقام فارسی (۱۲,۰۰۰)",
                                        isSelected = selectedCurrency == AppCurrency.TOMAN,
                                        onClick = { selectedCurrency = AppCurrency.TOMAN },
                                        modifier = Modifier.weight(1f)
                                    )
                                    CurrencyOptionCard(
                                        title = "ریال",
                                        subtitle = "با ارقام فارسی (۱۲۰,۰۰۰)",
                                        isSelected = selectedCurrency == AppCurrency.RIAL,
                                        onClick = { selectedCurrency = AppCurrency.RIAL },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CurrencyOptionCard(
                                        title = "تومان (EN)",
                                        subtitle = "با ارقام انگلیسی (12,000)",
                                        isSelected = selectedCurrency == AppCurrency.TOMAN_ENGLISH,
                                        onClick = { selectedCurrency = AppCurrency.TOMAN_ENGLISH },
                                        modifier = Modifier.weight(1f)
                                    )
                                    CurrencyOptionCard(
                                        title = "ریال (EN)",
                                        subtitle = "با ارقام انگلیسی (120,000)",
                                        isSelected = selectedCurrency == AppCurrency.RIAL_ENGLISH,
                                        onClick = { selectedCurrency = AppCurrency.RIAL_ENGLISH },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        2 -> {
                            // Step 2: First Account & Initial Balance
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "ایجاد اولین حساب مالی",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "نام حساب یا کارت اصلی و موجودی فعلی آن را وارد نمایید.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedTextField(
                                value = accountName,
                                onValueChange = { accountName = it },
                                label = { Text("نام حساب یا کارت") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            PersianAmountInput(
                                value = initialBalanceText,
                                onValueChange = { initialBalanceText = it },
                                currency = selectedCurrency,
                                label = "موجودی اولیه (اختیاری)"
                            )
                        }

                        3 -> {
                            // Step 3: Security & PIN
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "قفل و امنیت برنامه",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "آیا مایلید ورود به برنامه با رمز عبور یا اثر انگشت محافظت شود؟",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            GlassCard(
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { enableAppLock = !enableAppLock }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "فعال‌سازی قفل ورود به دودوتا",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "درخواست رمز هنگام باز شدن برنامه",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Switch(
                                        checked = enableAppLock,
                                        onCheckedChange = { enableAppLock = it }
                                    )
                                }
                            }

                            if (enableAppLock) {
                                Spacer(modifier = Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = pinText,
                                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinText = it },
                                    label = { Text("رمز عبور (۴ تا ۶ رقم)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = confirmPinText,
                                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPinText = it },
                                    label = { Text("تکرار رمز عبور") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        4 -> {
                            // Step 4: Recovery Code
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "کد بازیابی اضطراری",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "اگر رمز عبور خود را فراموش کنید، فقط با این کد می‌توانید قفل را باز کنید. لطفاً آن را در جایی امن یادداشت یا کپی کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = generatedRecoveryCode,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 2.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(generatedRecoveryCode))
                                            Toast.makeText(context, "کد بازیابی در حافظه کپی شد", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("کپی کد بازیابی")
                                    }
                                }
                            }
                        }

                        5 -> {
                            // Step 5: Optional Features & Finish
                            Text(
                                text = "امکانات تکمیلی و شروع",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "می‌توانید قابلیت‌های زیر را فعال یا بعداً از تنظیمات تغییر دهید.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            FeatureToggleCard(
                                icon = Icons.Default.Fingerprint,
                                title = "ورود با اثر انگشت / بیومتریک",
                                description = "بازگشایی سریع و امن با حسگر بیومتریک گوشی",
                                isChecked = enableBiometric,
                                onCheckedChange = { enableBiometric = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            FeatureToggleCard(
                                icon = Icons.Default.Backup,
                                title = "پشتیبان‌گیری هفتگی خودکار",
                                description = "ذخیره خودکار یک نسخه پشتیبان در حافظه محلی گوشی",
                                isChecked = enableWeeklyBackup,
                                onCheckedChange = { enableWeeklyBackup = it }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Buttons (Next / Skip / Finish)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1) {
                    TextButton(
                        onClick = { step -= 1 },
                        modifier = Modifier.testTag("onboarding_prev_button")
                    ) {
                        Text("قبلی")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (step == 3 && enableAppLock) {
                            if (pinText.length < 4) {
                                Toast.makeText(context, "لطفاً رمزی حداقل ۴ رقمی وارد کنید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (pinText != confirmPinText) {
                                Toast.makeText(context, "رمز عبور با تکرار آن همخوانی ندارد", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }

                        if (step < 5) {
                            step += 1
                        } else {
                            // Finish Onboarding
                            coroutineScope.launch {
                                preferencesRepo.setCurrency(selectedCurrency)
                                preferencesRepo.setThemeMode(selectedTheme)

                                val balance = PersianNumberFormatter.parseAmount(initialBalanceText)
                                repository.insertAccount(
                                    AccountEntity(
                                        name = accountName.ifBlank { "کارت بانکی اصلی" },
                                        type = selectedAccountType,
                                        balance = balance,
                                        bankName = "بانک اصلی"
                                    )
                                )

                                if (enableAppLock && pinText.isNotEmpty()) {
                                    val pinHash = SecurityHelper.hashPin(pinText)
                                    preferencesRepo.setAppLock(
                                        enabled = true,
                                        pinHash = pinHash,
                                        recoveryCode = generatedRecoveryCode,
                                        biometric = enableBiometric
                                    )
                                } else {
                                    preferencesRepo.setAppLock(
                                        enabled = false,
                                        pinHash = "",
                                        recoveryCode = generatedRecoveryCode,
                                        biometric = false
                                    )
                                }

                                preferencesRepo.setWeeklyBackup(enableWeeklyBackup)
                                preferencesRepo.setOnboardingCompleted(true)
                                onFinished()
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("onboarding_next_button")
                ) {
                    Text(if (step == 5) "شروع و ورود به برنامه" else "مرحله بعد")
                }
            }
        }
    }
}

@Composable
fun CurrencyOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FeatureToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!isChecked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
        }
    }
}
