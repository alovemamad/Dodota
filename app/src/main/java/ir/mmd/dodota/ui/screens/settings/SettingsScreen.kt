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
import ir.mmd.dodota.util.PersianNumberFormatter
import ir.mmd.dodota.util.TelegramHelper
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

    val fontPercent = (userPreferences.fontScale * 100f).roundToInt()
    val fontSizePresetTitle = AppFontSizePreset.fromScale(userPreferences.fontScale).title

    Scaffold(
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
            // Visual Customization Hero Banner
            item {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    isHighlighted = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                    )
                                )
                            )
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "استودیو شخصی‌سازی ظاهر دودوتا",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "تم: ${userPreferences.themeMode.title} • فونت: ${userPreferences.fontFamily.title} (${PersianNumberFormatter.toPersianDigits(fontPercent)}٪)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

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
                    title = "تم و استایل گرافیکی برنامه",
                    subtitle = "${userPreferences.themeMode.title} • ${userPreferences.themeMode.subtitle}",
                    onClick = { showThemeModeDialog = true },
                    testTag = "settings_theme_card"
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.FontDownload,
                    title = "فونت فارسی و اندازه نوشته‌ها",
                    subtitle = "فونت ${userPreferences.fontFamily.title} • اندازه $fontSizePresetTitle (${PersianNumberFormatter.toPersianDigits(fontPercent)}٪)",
                    onClick = { showTypographyDialog = true },
                    testTag = "settings_typography_card"
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Payments,
                    title = "واحد پول و فرمت نمایش ارقام",
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
                    subtitle = if (userPreferences.isPinEnabled) "قفل با پین فعال است" else "غیرفعال",
                    onClick = onNavigateToSecurity,
                    testTag = "settings_security_card"
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
                    title = "پشتیبان‌گیری و بازیابی داده‌ها",
                    subtitle = "خروجی JSON آفلاین و پشتیبان هفتگی",
                    onClick = onNavigateToBackup,
                    testTag = "settings_backup_card"
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Delete,
                    title = "سطل زباله",
                    subtitle = "مشاهده و بازیابی تراکنش‌های حذف شده",
                    onClick = onNavigateToTrash,
                    testTag = "settings_trash_card"
                )
            }

            // Support & About Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "پشتیبانی و درباره دودوتا",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Send,
                    title = "ارتباط با پشتیبانی در تلگرام",
                    subtitle = "@momo1f",
                    onClick = {
                        TelegramHelper.openTelegramProfile(context, "momo1f")
                    },
                    testTag = "settings_telegram_card"
                )
            }

            item {
                SettingsItemCard(
                    icon = Icons.Outlined.Info,
                    title = "درباره «دودوتا» و توسعه‌دهنده",
                    subtitle = "نسخه ۱.۰.۰ • توسعه‌دهنده: Mmd",
                    onClick = onNavigateToAbout,
                    testTag = "settings_about_card"
                )
            }
        }
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
    val currentScale = userPreferences.fontScale
    val currentPercent = (currentScale * 100f).roundToInt()
    val currentFont = userPreferences.fontFamily
    val previewFontFamily = getDodotaFontFamily(currentFont)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            isHighlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
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
                                    text = "${currentFont.title} • ${PersianNumberFormatter.toPersianDigits(currentPercent)}٪",
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
                                    text = "حساب بلو بانک • ۲۵ مهر ۱۴۰۴",
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
                                        text = "${PersianNumberFormatter.toPersianDigits(currentPercent)}٪",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (currentPercent != 100) {
                                        TextButton(
                                            onClick = { onChangeFontScale(1.0f) },
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
                                        onChangeFontScale((currentScale - 0.04f).coerceIn(0.80f, 1.35f))
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("font_scale_decrease_button")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "کوچک‌تر", modifier = Modifier.size(18.dp))
                                }

                                Slider(
                                    value = currentScale,
                                    onValueChange = { onChangeFontScale(it) },
                                    valueRange = 0.80f..1.30f,
                                    steps = 9,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("font_scale_slider")
                                )

                                FilledTonalIconButton(
                                    onClick = {
                                        onChangeFontScale((currentScale + 0.04f).coerceIn(0.80f, 1.35f))
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
                                    val isPresetSelected = kotlin.math.abs(currentScale - preset.scale) < 0.03f
                                    FilterChip(
                                        selected = isPresetSelected,
                                        onClick = { onChangeFontScale(preset.scale) },
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
                        text = "انتخاب فونت فارسی (۸ فونت استاندارد و آفلاین)",
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
                                .clickable { onSelectFontFamily(fontOption) }
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
                                        text = "نمونه ارقام: ۱۲,۴۵۰,۰۰۰ تومان • مدیریت هوشمند مالی",
                                        fontFamily = optionFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    )
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectFontFamily(fontOption) }
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
