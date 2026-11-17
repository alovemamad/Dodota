package ir.mmd.dodota.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter

enum class PrintDatePreset(val title: String) {
    ALL("همه تاریخ‌ها"),
    THIS_MONTH("این ماه"),
    LAST_MONTH("ماه قبل"),
    LAST_3_MONTHS("۳ ماه اخیر"),
    THIS_YEAR("امسال"),
    CUSTOM("بازه دلخواه")
}

data class PrintableSubSection(
    val key: String,
    val title: String,
    val subtitle: String = "",
    val defaultSelected: Boolean = true
)

data class PrintConfirmationPayload(
    val startMillis: Long?,
    val endMillis: Long?,
    val dateRangeLabel: String,
    val selectedSections: Set<String>,
    val includeSummary: Boolean,
    val includeFrame: Boolean,
    val includeNotes: Boolean
)

/**
 * Compact, unobtrusive small printer icon button for screen headers & top bars.
 * Keeps a 48dp accessible touch target while rendering a sleek 28dp pill with a 15dp glyph.
 */
@Composable
fun DodotaPrintIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    testTag: String = "print_icon_button"
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.10f))
                .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Print,
                contentDescription = "چاپ گزارش",
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun DodotaPrintDialog(
    sectionTitle: String,
    userPreferences: UserPreferences,
    supportsDateRange: Boolean = true,
    initialPreset: PrintDatePreset = if (supportsDateRange) PrintDatePreset.THIS_MONTH else PrintDatePreset.ALL,
    initialCustomStartMillis: Long? = null,
    initialCustomEndMillis: Long? = null,
    subSections: List<PrintableSubSection> = emptyList(),
    countProvider: (key: String, startMillis: Long?, endMillis: Long?) -> Int? = { _, _, _ -> null },
    showNotesToggle: Boolean = true,
    onDismiss: () -> Unit,
    onConfirmPrint: (PrintConfirmationPayload) -> Unit
) {
    val context = LocalContext.current
    val isEn = userPreferences.currency.isEnglishDigits
    val currentPDate = remember { PersianDateHelper.getCurrentPersianDate() }

    val defaultMonthStart = remember(currentPDate) {
        PersianDateHelper.getStartOfMonthMillis(currentPDate.year, currentPDate.month)
    }
    val defaultMonthEnd = remember(currentPDate) {
        PersianDateHelper.getEndOfMonthMillis(currentPDate.year, currentPDate.month)
    }

    var selectedPreset by remember { mutableStateOf(initialPreset) }
    var customStartMillis by remember {
        mutableLongStateOf(initialCustomStartMillis ?: defaultMonthStart)
    }
    var customEndMillis by remember {
        mutableLongStateOf(initialCustomEndMillis ?: defaultMonthEnd)
    }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val selectedSectionKeys = remember(subSections) {
        mutableStateMapOf<String, Boolean>().apply {
            subSections.forEach { put(it.key, it.defaultSelected) }
        }
    }

    var includeFrame by remember { mutableStateOf(true) }
    var includeSummary by remember { mutableStateOf(true) }
    var includeNotes by remember { mutableStateOf(true) }

    val resolvedRange: Pair<Long?, Long?> = remember(
        supportsDateRange,
        selectedPreset,
        customStartMillis,
        customEndMillis,
        currentPDate
    ) {
        if (!supportsDateRange) {
            null to null
        } else {
            when (selectedPreset) {
                PrintDatePreset.ALL -> null to null
                PrintDatePreset.THIS_MONTH -> {
                    PersianDateHelper.getStartOfMonthMillis(currentPDate.year, currentPDate.month) to
                        PersianDateHelper.getEndOfMonthMillis(currentPDate.year, currentPDate.month)
                }
                PrintDatePreset.LAST_MONTH -> {
                    val (prevY, prevM) = PersianDateHelper.getPreviousPersianMonth(currentPDate.year, currentPDate.month)
                    PersianDateHelper.getStartOfMonthMillis(prevY, prevM) to
                        PersianDateHelper.getEndOfMonthMillis(prevY, prevM)
                }
                PrintDatePreset.LAST_3_MONTHS -> {
                    var startY = currentPDate.year
                    var startM = currentPDate.month - 2
                    if (startM <= 0) {
                        startM += 12
                        startY -= 1
                    }
                    PersianDateHelper.getStartOfMonthMillis(startY, startM) to
                        PersianDateHelper.getEndOfMonthMillis(currentPDate.year, currentPDate.month)
                }
                PrintDatePreset.THIS_YEAR -> {
                    PersianDateHelper.getStartOfMonthMillis(currentPDate.year, 1) to
                        PersianDateHelper.getEndOfMonthMillis(currentPDate.year, 12)
                }
                PrintDatePreset.CUSTOM -> {
                    val s = minOf(customStartMillis, customEndMillis)
                    val e = maxOf(customStartMillis, customEndMillis)
                    PersianDateHelper.getStartOfDayMillis(s) to PersianDateHelper.getEndOfDayMillis(e)
                }
            }
        }
    }

    val rangeLabel = remember(resolvedRange, selectedPreset, isEn) {
        val (s, e) = resolvedRange
        if (s == null || e == null) {
            "همه تاریخ‌ها"
        } else {
            val fromTxt = PersianDateHelper.formatToPersianDateNumeric(s, isEn)
            val toTxt = PersianDateHelper.formatToPersianDateNumeric(e, isEn)
            "از $fromTxt تا $toTxt"
        }
    }

    if (showStartPicker) {
        PersianDatePickerDialog(
            initialDateMillis = customStartMillis,
            onDateSelected = { picked ->
                customStartMillis = PersianDateHelper.getStartOfDayMillis(picked)
                if (customStartMillis > customEndMillis) {
                    customEndMillis = PersianDateHelper.getEndOfDayMillis(picked)
                }
                selectedPreset = PrintDatePreset.CUSTOM
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        PersianDatePickerDialog(
            initialDateMillis = customEndMillis,
            onDateSelected = { picked ->
                customEndMillis = PersianDateHelper.getEndOfDayMillis(picked)
                if (customEndMillis < customStartMillis) {
                    customStartMillis = PersianDateHelper.getStartOfDayMillis(picked)
                }
                selectedPreset = PrintDatePreset.CUSTOM
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            shape = RoundedCornerShape(26.dp),
            elevation = 10.dp,
            isHighlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Print,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "تنظیمات چاپ • $sectionTitle",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "چاپ منظم و کادربندی‌شده (A4)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                // 1. Date range selection
                if (supportsDateRange) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "بازه زمانی چاپ",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = rangeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PrintDatePreset.entries.forEach { preset ->
                                    val isSelected = selectedPreset == preset
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedPreset = preset },
                                        label = {
                                            Text(
                                                text = preset.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val displayStart = resolvedRange.first ?: customStartMillis
                                val displayEnd = resolvedRange.second ?: customEndMillis
                                DateSelectChipCard(
                                    label = "از تاریخ",
                                    dateText = PersianDateHelper.formatToPersianDateNumeric(displayStart, isEn),
                                    isActive = selectedPreset != PrintDatePreset.ALL,
                                    onClick = { showStartPicker = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("print_from_date_button")
                                )
                                DateSelectChipCard(
                                    label = "تا تاریخ",
                                    dateText = PersianDateHelper.formatToPersianDateNumeric(displayEnd, isEn),
                                    isActive = selectedPreset != PrintDatePreset.ALL,
                                    onClick = { showEndPicker = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("print_to_date_button")
                                )
                            }
                        }
                    }
                }

                // 2. Selectable Sub-sections
                if (subSections.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val allSelected = subSections.all { selectedSectionKeys[it.key] == true }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "بخش‌های انتخاب‌شده برای چاپ",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = {
                                    val nextVal = !allSelected
                                    subSections.forEach { sec ->
                                        selectedSectionKeys[sec.key] = nextVal
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = if (allSelected) "لغو انتخاب همه" else "انتخاب همه",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        subSections.forEach { sec ->
                            val isChecked = selectedSectionKeys[sec.key] == true
                            val liveCount = countProvider(sec.key, resolvedRange.first, resolvedRange.second)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    width = if (isChecked) 1.2.dp else 1.dp,
                                    color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedSectionKeys[sec.key] = !isChecked }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { selectedSectionKeys[sec.key] = it },
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sec.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium
                                        )
                                        if (sec.subtitle.isNotBlank()) {
                                            Text(
                                                text = sec.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                    if (liveCount != null) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "${PersianNumberFormatter.formatDigits(liveCount, isEn)} مورد",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Layout & Frame toggles
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PrintOptionToggleRow(
                            label = "چاپ با چهارچوب و کادر رسمی دور صفحه",
                            checked = includeFrame,
                            onCheckedChange = { includeFrame = it }
                        )
                        PrintOptionToggleRow(
                            label = "درج خلاصه آماری در بالای برگ",
                            checked = includeSummary,
                            onCheckedChange = { includeSummary = it }
                        )
                        if (showNotesToggle) {
                            PrintOptionToggleRow(
                                label = "درج توضیحات و یادداشت‌ها",
                                checked = includeNotes,
                                onCheckedChange = { includeNotes = it }
                            )
                        }
                    }
                }

                // Confirm / Cancel actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("انصراف")
                    }
                    Button(
                        onClick = {
                            val activeKeys = selectedSectionKeys.filterValues { it }.keys
                            if (subSections.isNotEmpty() && activeKeys.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "لطفاً حداقل یک بخش را برای چاپ انتخاب کنید.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            onConfirmPrint(
                                PrintConfirmationPayload(
                                    startMillis = resolvedRange.first,
                                    endMillis = resolvedRange.second,
                                    dateRangeLabel = rangeLabel,
                                    selectedSections = activeKeys,
                                    includeSummary = includeSummary,
                                    includeFrame = includeFrame,
                                    includeNotes = includeNotes
                                )
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("confirm_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تایید و ارسال به پرینتر", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DateSelectChipCard(
    label: String,
    dateText: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = if (isActive) dateText else "انتخاب تاریخ",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
            }
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun PrintOptionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(22.dp)
        )
    }
}
