package ir.mmd.dodota.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.mmd.dodota.BuildConfig
import ir.mmd.dodota.data.model.AppCurrency
import ir.mmd.dodota.data.model.AppFontFamilyOption
import ir.mmd.dodota.data.model.AppFontSizePreset
import ir.mmd.dodota.data.model.AppThemeMode
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.preferences.UserPreferencesRepository
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.ui.theme.getDodotaFontFamily
import ir.mmd.dodota.util.ApkDownloadState
import ir.mmd.dodota.util.GitHubUpdateHelper
import ir.mmd.dodota.util.GitHubUpdateResult
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    userPreferences: UserPreferences,
    preferencesRepo: UserPreferencesRepository,
    onNavigateToSecurity: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showTypographyDialog by remember { mutableStateOf(false) }
    var showGithubUpdateDialog by remember { mutableStateOf(false) }

    val fontPercent = (userPreferences.fontScale * 100f).roundToInt()
    val fontSizePresetTitle = AppFontSizePreset.fromScale(userPreferences.fontScale).title

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                title = "تنظیمات و شخصی‌سازی",
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // General & Appearance Settings Section
            item {
                Text(
                    text = "تنظیمات عمومی، فونت و ظاهر",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Palette,
                    title = "تم برنامه",
                    subtitle = userPreferences.themeMode.title,
                    onClick = { showThemeModeDialog = true },
                    testTag = "settings_theme_card"
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.FontDownload,
                    title = "فونت و اندازه نوشته",
                    subtitle = "${userPreferences.fontFamily.title} (${PersianNumberFormatter.formatDigits(fontPercent, userPreferences.currency)}٪)",
                    onClick = { showTypographyDialog = true },
                    testTag = "settings_typography_card"
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Payments,
                    title = "واحد پول",
                    subtitle = userPreferences.currency.title,
                    onClick = { showCurrencyDialog = true },
                    testTag = "settings_currency_card"
                )
            }

            // Security & Privacy Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "امنیت و قفل برنامه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Lock,
                    title = "رمز عبور و اثر انگشت",
                    subtitle = if (userPreferences.isPinEnabled) "فعال" else "غیرفعال",
                    onClick = onNavigateToSecurity,
                    testTag = "settings_security_card"
                )
            }

            // Chichi Plus Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "چی‌چی پلاس (قابلیت‌های هوشمند)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "جدید",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            item {
                SettingsSwitchCard(
                    icon = Icons.Outlined.DateRange,
                    title = "چی‌چی پلاس",
                    subtitle = "فعال‌سازی ماژول «چندروز؟» در داشبورد برای ثبت و شمارش روزهای ماموریت، مرخصی و فعالیت‌ها",
                    checked = userPreferences.isChichiPlusEnabled,
                    onCheckedChange = { isChecked ->
                        coroutineScope.launch {
                            preferencesRepo.setChichiPlusEnabled(isChecked)
                        }
                    },
                    testTag = "settings_chichi_plus_switch"
                )
            }

            // Data & Storage Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "داده‌ها و نگهداری",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Backup,
                    title = "پشتیبان‌گیری و بازیابی",
                    subtitle = "",
                    onClick = onNavigateToBackup,
                    testTag = "settings_backup_card"
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Delete,
                    title = "سطل زباله",
                    subtitle = "",
                    onClick = onNavigateToTrash,
                    testTag = "settings_trash_card"
                )
            }

            // Support & Updates & About Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "بروزرسانی و درباره دودوتا",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                val updateSubtitle = if (userPreferences.lastUpdateCheckMillis > 0L) {
                    PersianNumberFormatter.formatDigits(
                        "نسخه فعلی: ${BuildConfig.VERSION_NAME} • آخرین بررسی: ${PersianDateHelper.formatToPersianDate(userPreferences.lastUpdateCheckMillis)}",
                        userPreferences.currency
                    )
                } else {
                    PersianNumberFormatter.formatDigits(
                        "نسخه فعلی: ${BuildConfig.VERSION_NAME} • بررسی و دانلود مستقیم نسخه جدید",
                        userPreferences.currency
                    )
                }
                SettingsItemCard(
                    icon = Icons.Outlined.SystemUpdate,
                    title = "بررسی بروزرسانی برنامه",
                    subtitle = updateSubtitle,
                    onClick = { showGithubUpdateDialog = true },
                    testTag = "settings_github_update_card"
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Info,
                    title = "درباره «دودوتا»",
                    subtitle = PersianNumberFormatter.formatDigits("نسخه ${BuildConfig.VERSION_NAME}", userPreferences.currency),
                    onClick = onNavigateToAbout,
                    testTag = "settings_about_card"
                )
            }
        }
    }

    if (showGithubUpdateDialog) {
        GitHubUpdateDialog(
            userPreferences = userPreferences,
            onRecordCheck = { version ->
                coroutineScope.launch {
                    preferencesRepo.updateLastUpdateCheck(System.currentTimeMillis(), version)
                }
            },
            onDismiss = { showGithubUpdateDialog = false }
        )
    }

    if (showTypographyDialog) {
        TypographyCustomizationDialog(
            userPreferences = userPreferences,
            onSelectFontFamily = { font ->
                coroutineScope.launch {
                    preferencesRepo.setFontFamily(font)
                }
            },
            onChangeFontScale = { scale ->
                coroutineScope.launch {
                    preferencesRepo.setFontScale(scale)
                }
            },
            onDismiss = { showTypographyDialog = false }
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("انتخاب واحد پول و نمایش ارقام", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppCurrency.values().forEach { curr ->
                        val isSelected = userPreferences.currency == curr
                        val sampleText = PersianNumberFormatter.formatCurrency(2500000L, curr)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        PersianNumberFormatter.globalUseEnglishDigits = curr.isEnglishDigits
                                        preferencesRepo.setCurrency(curr)
                                        showCurrencyDialog = false
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        coroutineScope.launch {
                                            PersianNumberFormatter.globalUseEnglishDigits = curr.isEnglishDigits
                                            preferencesRepo.setCurrency(curr)
                                            showCurrencyDialog = false
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = curr.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "نمونه نمایش: $sampleText",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("بستن")
                }
            }
        )
    }

    if (showThemeModeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeModeDialog = false },
            title = { Text("انتخاب تم و پالت رنگی", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.values().forEach { mode ->
                        val isSelected = userPreferences.themeMode == mode
                        val accentBorderColor = when (mode) {
                            AppThemeMode.VIP_RED -> Color(0xFFFF1E42)
                            AppThemeMode.LIGHT -> Color(0xFFF59E0B)
                            AppThemeMode.DARK -> Color(0xFF38BDF8)
                            AppThemeMode.SYSTEM -> Color(0xFF0EA5E9)
                            AppThemeMode.BLUBANK -> Color(0xFF3B82F6)
                            AppThemeMode.EMERALD -> Color(0xFF10B981)
                            AppThemeMode.SUNSET -> Color(0xFFC084FC)
                            AppThemeMode.GOLD -> Color(0xFFF59E0B)
                            AppThemeMode.PURPLE -> Color(0xFFA855F7)
                            AppThemeMode.BLACK -> Color(0xFF38BDF8)
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) BorderStroke(1.5.dp, accentBorderColor) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        preferencesRepo.setThemeMode(mode)
                                        showThemeModeDialog = false
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ThemeModePreviewCircle(mode = mode)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (mode.subtitle.isNotBlank()) {
                                        Text(
                                            text = mode.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        coroutineScope.launch {
                                            preferencesRepo.setThemeMode(mode)
                                            showThemeModeDialog = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeModeDialog = false }) {
                    Text("بستن")
                }
            }
        )
    }
}

@Composable
private fun TypographyCustomizationDialog(
    userPreferences: UserPreferences,
    onSelectFontFamily: (AppFontFamilyOption) -> Unit,
    onChangeFontScale: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var activeFont by remember(userPreferences.fontFamily) { mutableStateOf(userPreferences.fontFamily) }
    var activeScale by remember(userPreferences.fontScale) { mutableStateOf(userPreferences.fontScale) }
    val currentScale = activeScale
    val currentPercent = (currentScale * 100f).roundToInt()
    val currentFont = activeFont
    val previewFontFamily = getDodotaFontFamily(currentFont)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            elevation = 10.dp,
            isHighlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "تنظیمات فونت فارسی و سایز متن",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "تغییر زنده فونت و مقیاس نوشته‌های کل برنامه",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Preview Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "پیش‌نمایش زنده فونت و سایز",
                                fontFamily = previewFontFamily,
                                fontSize = (12f * currentScale).sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "${currentFont.title} • ${PersianNumberFormatter.formatDigits(currentPercent, userPreferences.currency)}٪",
                                    fontFamily = previewFontFamily,
                                    fontSize = (11f * currentScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "واریز حقوق و پاداش ماهانه",
                                    fontFamily = previewFontFamily,
                                    fontSize = (15f * currentScale).sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = PersianNumberFormatter.formatDigits("حساب بلو بانک • 25 مهر 1404", userPreferences.currency),
                                    fontFamily = previewFontFamily,
                                    fontSize = (12f * currentScale).sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                text = "+ ${PersianNumberFormatter.formatCurrency(18500000L, userPreferences.currency)}",
                                fontFamily = previewFontFamily,
                                fontSize = (14f * currentScale).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = IncomeGreen
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Body: Font Size Controls + Font Family List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Font Size Section
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "اندازه نوشته‌ها (سایز فونت)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${PersianNumberFormatter.formatDigits(currentPercent, userPreferences.currency)}٪",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (currentPercent != 100) {
                                        TextButton(
                                            onClick = {
                                                activeScale = 1.0f
                                                onChangeFontScale(1.0f)
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("بازنشانی", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Slider with - / + buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        val newScale = (activeScale - 0.04f).coerceIn(0.80f, 1.35f)
                                        activeScale = newScale
                                        onChangeFontScale(newScale)
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("font_scale_decrease_button")
                                 ) {
                                     Icon(Icons.Default.Remove, contentDescription = "کوچک‌تر", modifier = Modifier.size(18.dp))
                                 }

                                 Slider(
                                     value = activeScale,
                                     onValueChange = {
                                         activeScale = it
                                         onChangeFontScale(it)
                                     },
                                     valueRange = 0.80f..1.35f,
                                     steps = 10,
                                     modifier = Modifier
                                         .weight(1f)
                                         .testTag("font_scale_slider")
                                 )

                                 FilledTonalIconButton(
                                     onClick = {
                                         val newScale = (activeScale + 0.04f).coerceIn(0.80f, 1.35f)
                                         activeScale = newScale
                                         onChangeFontScale(newScale)
                                     },
                                     modifier = Modifier
                                         .size(36.dp)
                                         .testTag("font_scale_increase_button")
                                 ) {
                                     Icon(Icons.Default.Add, contentDescription = "بزرگ‌تر", modifier = Modifier.size(18.dp))
                                 }
                             }

                             // Quick Presets Row
                             Row(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .horizontalScroll(rememberScrollState()),
                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                             ) {
                                 AppFontSizePreset.entries.forEach { preset ->
                                     val isPresetSelected = kotlin.math.abs(activeScale - preset.scale) < 0.03f
                                     FilterChip(
                                         selected = isPresetSelected,
                                         onClick = {
                                             activeScale = preset.scale
                                             onChangeFontScale(preset.scale)
                                         },
                                        label = {
                                            Text(
                                                text = preset.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isPresetSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Font Family List Section
                    Text(
                        text = PersianNumberFormatter.formatDigits("انتخاب فونت فارسی (8 فونت استاندارد و آفلاین)", userPreferences.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AppFontFamilyOption.entries.forEach { fontOption ->
                        val isSelected = currentFont == fontOption
                        val optionFontFamily = getDodotaFontFamily(fontOption)

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    activeFont = fontOption
                                    onSelectFontFamily(fontOption)
                                }
                                .testTag("font_option_${fontOption.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Font badge icon
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ف",
                                        fontFamily = optionFontFamily,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = fontOption.title,
                                            fontFamily = optionFontFamily,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = fontOption.badge,
                                                fontFamily = optionFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = fontOption.subtitle,
                                        fontFamily = optionFontFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "نمونه ارقام: ${PersianNumberFormatter.formatCurrency(12450000L, userPreferences.currency)} • مدیریت هوشمند مالی",
                                        fontFamily = optionFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        activeFont = fontOption
                                        onSelectFontFamily(fontOption)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تأیید و اعمال تغییرات", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThemeModePreviewCircle(mode: AppThemeMode) {
    when (mode) {
        AppThemeMode.LIGHT -> {
            // Day (Light): Bright white circle with golden sun icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFFFFF))
                    .border(1.5.dp, Color(0xFFF59E0B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AppThemeMode.DARK -> {
            // Night (Dark): Deep midnight slate circle with crescent moon icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DarkMode,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        AppThemeMode.SYSTEM -> {
            // Auto (System Day/Night): Diagonal half-Day (bright white/gold) & half-Night (deep dark/blue) circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Right/Top half: Bright Day White
                    drawArc(
                        color = Color(0xFFFFFFFF),
                        startAngle = -45f,
                        sweepAngle = 180f,
                        useCenter = true
                    )
                    // Left/Bottom half: Deep Night Navy
                    drawArc(
                        color = Color(0xFF0F172A),
                        startAngle = 135f,
                        sweepAngle = 180f,
                        useCenter = true
                    )
                    // Small sun dot on Day half
                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = size.minDimension * 0.14f,
                        center = androidx.compose.ui.geometry.Offset(
                            x = size.width * 0.66f,
                            y = size.height * 0.34f
                        )
                    )
                    // Small moon dot on Night half
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = size.minDimension * 0.14f,
                        center = androidx.compose.ui.geometry.Offset(
                            x = size.width * 0.34f,
                            y = size.height * 0.66f
                        )
                    )
                }
            }
        }

        AppThemeMode.SUNSET -> {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF9333EA), Color(0xFFF97316))
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            )
        }

        AppThemeMode.BLACK -> {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF000000))
                    .border(1.5.dp, Color(0xFF71717A), CircleShape)
            )
        }

        else -> {
            val previewColor = when (mode) {
                AppThemeMode.VIP_RED -> Color(0xFFFF1E42)
                AppThemeMode.BLUBANK -> Color(0xFF3B82F6)
                AppThemeMode.EMERALD -> Color(0xFF10B981)
                AppThemeMode.GOLD -> Color(0xFFF59E0B)
                AppThemeMode.PURPLE -> Color(0xFFA855F7)
                else -> Color(0xFF3B82F6)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            )
        }
    }
}

@Composable
fun SettingsItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null
) {
    GlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    GlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it },
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(if (testTag != null) "${testTag}_switch" else "settings_switch")
            )
        }
    }
}

@Composable
private fun GitHubUpdateDialog(
    userPreferences: UserPreferences,
    onRecordCheck: (latestVersion: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<GitHubUpdateResult>(GitHubUpdateResult.Loading) }
    var downloadState by remember { mutableStateOf<ApkDownloadState>(ApkDownloadState.Idle) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun runUpdateCheck() {
        updateState = GitHubUpdateResult.Loading
        coroutineScope.launch {
            val result = GitHubUpdateHelper.checkForUpdates(GitHubUpdateHelper.effectiveRepoSlug())
            updateState = result
            when (result) {
                is GitHubUpdateResult.UpdateAvailable -> onRecordCheck(result.release.cleanVersion)
                is GitHubUpdateResult.UpToDate -> onRecordCheck(result.release.cleanVersion)
                is GitHubUpdateResult.TagOnlyInfo -> onRecordCheck(GitHubUpdateHelper.cleanVersionName(result.latestTag))
                is GitHubUpdateResult.NoReleaseYet -> onRecordCheck("")
                else -> {}
            }
        }
    }

    fun startApkDownload(downloadUrl: String, versionTag: String, preferredFileName: String?) {
        val started = GitHubUpdateHelper.startDirectApkDownload(
            context = context,
            downloadUrl = downloadUrl,
            versionTag = versionTag,
            preferredFileName = preferredFileName
        )
        if (started == null) {
            // Fallback to opening direct link in browser if DownloadManager unavailable
            GitHubUpdateHelper.openUrlInBrowser(context, downloadUrl)
            return
        }
        val (downloadId, fileName) = started
        downloadJob?.cancel()
        downloadJob = coroutineScope.launch {
            GitHubUpdateHelper.observeDownloadProgress(context, downloadId, fileName).collect { state ->
                downloadState = state
            }
        }
    }

    LaunchedEffect(Unit) {
        runUpdateCheck()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            elevation = 10.dp,
            isHighlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "بروزرسانی برنامه",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = PersianNumberFormatter.formatDigits("نسخه فعلی: ${BuildConfig.VERSION_NAME}", userPreferences.currency),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                // State Content
                when (val result = updateState) {
                    GitHubUpdateResult.Idle, GitHubUpdateResult.Loading -> {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
                                Text(
                                    text = "در حال بررسی آخرین نسخه در گیت‌هاب...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    is GitHubUpdateResult.UpdateAvailable -> {
                        val rel = result.release
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = IncomeGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.5.dp, IncomeGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.NewReleases,
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "نسخه جدید آماده دریافت است!",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = IncomeGreen
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = IncomeGreen
                                    ) {
                                        Text(
                                            text = PersianNumberFormatter.formatDigits(rel.versionTag, userPreferences.currency),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (rel.publishedPersianDate.isNotBlank()) {
                                    Text(
                                        text = "تاریخ انتشار: ${rel.publishedPersianDate}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (rel.notes.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = rel.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 140.dp)
                                                .verticalScroll(rememberScrollState())
                                                .padding(10.dp)
                                        )
                                    }
                                }

                                // Direct Download Progress & Confirmation
                                when (val dl = downloadState) {
                                    ApkDownloadState.Idle -> {
                                        val sizeStr = GitHubUpdateHelper.formatFileSizeMb(rel.apkSizeBytes)
                                        Text(
                                            text = if (rel.apkDownloadUrl != null) {
                                                "آیا مایل به دانلود مستقیم فایل نصب" +
                                                    (if (sizeStr.isNotBlank()) " (${PersianNumberFormatter.formatDigits(sizeStr, userPreferences.currency)})" else "") +
                                                    " هستید؟"
                                            } else {
                                                "برای دریافت نسخه جدید به صفحه انتشار هدایت خواهید شد."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = onDismiss,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(0.8f)
                                            ) {
                                                Text("فعلاً نه")
                                            }
                                            Button(
                                                onClick = {
                                                    val apkUrl = rel.apkDownloadUrl
                                                    if (apkUrl != null) {
                                                        startApkDownload(apkUrl, rel.versionTag, rel.apkFileName)
                                                    } else {
                                                        GitHubUpdateHelper.openUrlInBrowser(context, rel.releasePageUrl)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1.3f)
                                                    .testTag("github_download_update_button")
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("تایید و دانلود مستقیم", fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }

                                    is ApkDownloadState.Downloading -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "در حال دانلود فایل نصب...",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = IncomeGreen
                                                    )
                                                    Text(
                                                        text = "${PersianNumberFormatter.formatDigits(dl.progressPercent, userPreferences.currency)}٪",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = IncomeGreen
                                                    )
                                                }
                                                LinearProgressIndicator(
                                                    progress = { (dl.progressPercent / 100f).coerceIn(0f, 1f) },
                                                    color = IncomeGreen,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                )
                                                if (dl.totalBytes > 0L) {
                                                    Text(
                                                        text = PersianNumberFormatter.formatDigits(
                                                            "${GitHubUpdateHelper.formatFileSizeMb(dl.downloadedBytes)} از ${GitHubUpdateHelper.formatFileSizeMb(dl.totalBytes)}",
                                                            userPreferences.currency
                                                        ),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                                TextButton(
                                                    onClick = { GitHubUpdateHelper.openUrlInBrowser(context, rel.apkDownloadUrl ?: rel.releasePageUrl) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Text("دریافت از مرورگر", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }

                                    is ApkDownloadState.Completed -> {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                            border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "دانلود با موفقیت کامل شد ✓",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = IncomeGreen
                                                )
                                                Text(
                                                    text = "فایل «${dl.fileName}» ذخیره شد. برای نصب، روی اعلان دانلود در نوار بالای گوشی بزنید یا پوشه دانلودها را باز کنید.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Button(
                                                    onClick = { GitHubUpdateHelper.openSystemDownloads(context) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("باز کردن دانلودها برای نصب", fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }

                                    is ApkDownloadState.Failed -> {
                                        Text(
                                            text = dl.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { GitHubUpdateHelper.openUrlInBrowser(context, rel.apkDownloadUrl ?: rel.releasePageUrl) },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("دانلود از مرورگر", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Button(
                                                onClick = {
                                                    val apkUrl = rel.apkDownloadUrl
                                                    if (apkUrl != null) {
                                                        startApkDownload(apkUrl, rel.versionTag, rel.apkFileName)
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("تلاش مجدد", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is GitHubUpdateResult.UpToDate -> {
                        val rel = result.release
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = IncomeGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "برنامه شما به‌روز است ✓",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = IncomeGreen
                                    )
                                }
                                Text(
                                    text = PersianNumberFormatter.formatDigits(
                                        "آخرین نسخه منتشرشده: ${rel.versionTag}" +
                                            if (rel.publishedPersianDate.isNotBlank()) " (${rel.publishedPersianDate})" else "",
                                        userPreferences.currency
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is GitHubUpdateResult.TagOnlyInfo -> {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (result.isNewer) IncomeGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, (if (result.isNewer) IncomeGreen else MaterialTheme.colorScheme.outline).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (result.isNewer) {
                                        "برچسب نسخه جدید (${PersianNumberFormatter.formatDigits(result.latestTag, userPreferences.currency)}) در گیت‌هاب ثبت شده است"
                                    } else {
                                        "برنامه با آخرین نسخه گیت‌هاب (${PersianNumberFormatter.formatDigits(result.latestTag, userPreferences.currency)}) هماهنگ است ✓"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (result.isNewer) {
                                    Text(
                                        text = "برای دریافت فایل نصب از صفحه انتشارها یا بیلدهای Actions استفاده کنید.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Button(
                                        onClick = { GitHubUpdateHelper.openUrlInBrowser(context, result.releasesPageUrl) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("باز کردن صفحه دریافت نسخه", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    is GitHubUpdateResult.NoReleaseYet -> {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "فعلاً نسخه جدیدتری منتشر نشده است ✓",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val buildDetails = if (result.latestRunNumber != null) {
                                    "آخرین بیلد موفق در گیت‌هاب (#${PersianNumberFormatter.formatDigits(result.latestRunNumber, userPreferences.currency)})" +
                                        (result.latestRunDatePersian?.takeIf { it.isNotBlank() }?.let { " در تاریخ $it" } ?: "") +
                                        " ثبت شده است، اما هنوز Release جدیدی منتشر نشده است."
                                } else {
                                    "هنوز نسخه رسمی (Release) جدیدی در گیت‌هاب منتشر نشده است."
                                }
                                Text(
                                    text = buildDetails,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is GitHubUpdateResult.Error -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Bottom Re-check & Close actions (when not showing the UpdateAvailable confirmation step)
                if (updateState !is GitHubUpdateResult.UpdateAvailable && updateState !is GitHubUpdateResult.Loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("بستن")
                        }
                        Button(
                            onClick = { runUpdateCheck() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("github_check_updates_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بررسی مجدد", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}



