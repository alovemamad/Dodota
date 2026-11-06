package ir.mmd.dodota.ui.screens.chichi

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.mmd.dodota.data.model.ChandRoozCategory
import ir.mmd.dodota.data.model.ChandRoozRecordEntity
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.theme.LocalDodotaGlassStyle
import ir.mmd.dodota.util.PersianDate
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ChandRoozScreen(
    repository: DodotaRepository,
    userPreferences: UserPreferences,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val allRecords by repository.allChandRoozRecords.collectAsState(initial = emptyList())
    val glassStyle = LocalDodotaGlassStyle.current

    var selectedFilterCategory by remember { mutableStateOf<ChandRoozCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var recordToEdit by remember { mutableStateOf<ChandRoozRecordEntity?>(null) }
    var recordToDelete by remember { mutableStateOf<ChandRoozRecordEntity?>(null) }

    // Filtered records
    val filteredRecords = remember(allRecords, selectedFilterCategory, searchQuery) {
        allRecords.filter { record ->
            val matchesCategory = selectedFilterCategory == null || record.category == selectedFilterCategory
            val matchesSearch = searchQuery.isBlank() ||
                    record.title.contains(searchQuery, ignoreCase = true) ||
                    record.location.contains(searchQuery, ignoreCase = true) ||
                    record.note.contains(searchQuery, ignoreCase = true) ||
                    record.customCategory.contains(searchQuery, ignoreCase = true) ||
                    record.category.title.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // Statistics calculations for the requested categories
    val totalMissionDays = remember(allRecords) {
        allRecords.filter { it.category == ChandRoozCategory.MISSION }.sumOf { it.daysCount }
    }
    val totalLeaveDays = remember(allRecords) {
        allRecords.filter { it.category == ChandRoozCategory.LEAVE }.sumOf { it.daysCount }
    }
    val totalSickLeaveDays = remember(allRecords) {
        allRecords.filter { it.category == ChandRoozCategory.SICK_LEAVE }.sumOf { it.daysCount }
    }
    val totalBankCount = remember(allRecords) {
        allRecords.count { it.category == ChandRoozCategory.BANK }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            DodotaTopBar(
                title = "چندروز؟ (چی‌چی پلاس)",
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    recordToEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("ثبت رویداد جدید", fontWeight = FontWeight.Bold) },
                modifier = Modifier
                    .testTag("chand_rooz_add_fab")
                    .padding(bottom = 12.dp)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Header Banner & Intro
            item {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            )
                                        )
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "سامانه چندروز؟",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "چی‌چی پلاس",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ثبت و بایگانی دقیق روزهای ماموریت، مرخصی، استعلاجی، امور بانکی و رویدادهای بازه‌ای",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Category Stats Grid (4 columns)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatPill(
                                title = "ماموریت",
                                value = "${PersianNumberFormatter.formatDigits(totalMissionDays)} روز",
                                color = Color(0xFF2563EB),
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                title = "مرخصی",
                                value = "${PersianNumberFormatter.formatDigits(totalLeaveDays)} روز",
                                color = Color(0xFF10B981),
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                title = "استعلاجی",
                                value = "${PersianNumberFormatter.formatDigits(totalSickLeaveDays)} روز",
                                color = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f)
                            )
                            StatPill(
                                title = "امور بانکی",
                                value = "${PersianNumberFormatter.formatDigits(totalBankCount)} مورد",
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 2. Search & Dropdown Filter Row (فیلتر کشویی در بالا)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Search TextField
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("جستجو در عنوان، محل، یادداشت...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "پاک کردن", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chand_rooz_search_field")
                    )

                    // Dropdown Filter Button (فیلتر کشویی)
                    var isDropdownExpanded by remember { mutableStateOf(false) }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedFilterCategory != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selectedFilterCategory != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .height(56.dp)
                                .clickable { isDropdownExpanded = true }
                                .testTag("chand_rooz_filter_dropdown_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "فیلتر دسته‌بندی",
                                    tint = if (selectedFilterCategory != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = selectedFilterCategory?.title ?: "همه موارد",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedFilterCategory != null) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (selectedFilterCategory != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = if (isDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .widthIn(min = 210.dp)
                        ) {
                            // Option: All categories
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = "همه موارد",
                                                fontWeight = if (selectedFilterCategory == null) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = PersianNumberFormatter.formatDigits(allRecords.size),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                trailingIcon = {
                                    if (selectedFilterCategory == null) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    selectedFilterCategory = null
                                    isDropdownExpanded = false
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Options: 5 specific categories
                            ChandRoozCategory.entries.forEach { category ->
                                val isCatSelected = selectedFilterCategory == category
                                val count = allRecords.count { it.category == category }
                                val catColor = try {
                                    Color(android.graphics.Color.parseColor(category.defaultColorHex))
                                } catch (_: Exception) {
                                    MaterialTheme.colorScheme.primary
                                }

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(catColor)
                                                )
                                                Text(
                                                    text = category.title,
                                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = catColor.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = PersianNumberFormatter.formatDigits(count),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = catColor,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    },
                                    trailingIcon = {
                                        if (isCatSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = catColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedFilterCategory = category
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Active Filter Chip if selected
            if (selectedFilterCategory != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "فیلتر فعال:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text(selectedFilterCategory!!.title, fontWeight = FontWeight.Bold) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "حذف فیلتر",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { selectedFilterCategory = null }
                                    )
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Text(
                            text = "${PersianNumberFormatter.formatDigits(filteredRecords.size)} رویداد پیدا شد",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. Records List or Empty State
            if (filteredRecords.isEmpty()) {
                item {
                    GlassCard(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty() || selectedFilterCategory != null)
                                    "هیچ موردی با این فیلتر یافت نشد"
                                else "هنوز رویدادی در «چندروز؟» ثبت نکرده‌اید",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "مثلا ثبت کنید: «۱۰ روز ماموریت شیراز از ۱۰ تا ۲۰ آبان»، «استعلاجی ۳ روزه»، «مراجعه به بانک» یا «مرخصی» تا همیشه سابقه دقیق آن را داشته باشید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    recordToEdit = null
                                    showAddEditDialog = true
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ثبت اولین رویداد")
                            }
                        }
                    }
                }
            } else {
                items(filteredRecords, key = { it.id }) { record ->
                    ChandRoozRecordCard(
                        record = record,
                        onEdit = {
                            recordToEdit = record
                            showAddEditDialog = true
                        },
                        onDelete = {
                            recordToDelete = record
                        }
                    )
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddEditDialog) {
        AddEditChandRoozDialog(
            record = recordToEdit,
            onDismiss = {
                showAddEditDialog = false
                recordToEdit = null
            },
            onSave = { updatedRecord ->
                coroutineScope.launch {
                    if (updatedRecord.id == 0L) {
                        repository.insertChandRoozRecord(updatedRecord)
                    } else {
                        repository.updateChandRoozRecord(updatedRecord)
                    }
                    showAddEditDialog = false
                    recordToEdit = null
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (recordToDelete != null) {
        val target = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("حذف رویداد", fontWeight = FontWeight.Bold) },
            text = {
                Text("آیا از حذف رویداد «${target.title}» مطمئن هستید؟ این عملیات قابل بازگشت نیست.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteChandRoozRecord(target)
                            recordToDelete = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun StatPill(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

@Composable
fun ChandRoozRecordCard(
    record: ChandRoozRecordEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = record.category
    val categoryColor = try {
        Color(android.graphics.Color.parseColor(record.colorHex))
    } catch (_: Exception) {
        Color(android.graphics.Color.parseColor(category.defaultColorHex))
    }

    val iconVector = when (category) {
        ChandRoozCategory.MISSION -> Icons.Default.FlightTakeoff
        ChandRoozCategory.LEAVE -> Icons.Default.BeachAccess
        ChandRoozCategory.SICK_LEAVE -> Icons.Default.MedicalServices
        ChandRoozCategory.BANK -> Icons.Default.AccountBalance
        ChandRoozCategory.OTHER -> Icons.Default.EditNote
    }

    // Display title for category: if OTHER and customCategory is set, show custom label!
    val displayCategoryTitle = if (category == ChandRoozCategory.OTHER && record.customCategory.isNotBlank()) {
        record.customCategory
    } else {
        category.title
    }

    GlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chand_rooz_card_${record.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Row 1: Category badge + Days count badge + Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = categoryColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = categoryColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = displayCategoryTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                        }
                    }

                    // Days Tag
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = if (record.isRange) {
                                "${PersianNumberFormatter.formatDigits(record.daysCount)} روز"
                            } else {
                                "تک‌روزه (۱ روز)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Excluded days badge if any
                    val excludedCount = remember(record.excludedDays) {
                        if (record.excludedDays.isBlank()) 0
                        else record.excludedDays.split(",").filter { it.isNotBlank() }.size
                    }
                    if (record.isRange && excludedCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "${PersianNumberFormatter.formatDigits(excludedCount)} روز کسر شده",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Edit & Delete icons
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "ویرایش",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "حذف",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Title
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Dates presentation
            val startDateFormatted = PersianDateHelper.formatToPersianDate(record.startDateMillis)
            val endDateFormatted = PersianDateHelper.formatToPersianDate(record.endDateMillis)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (record.isRange) {
                        "از $startDateFormatted تا $endDateFormatted"
                    } else {
                        "تاریخ: $startDateFormatted"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Excluded dates details if any
            if (record.isRange && record.excludedDays.isNotBlank()) {
                val excludedList = remember(record.excludedDays) {
                    record.excludedDays.split(",").map { it.trim() }.filter { it.isNotBlank() }
                }
                if (excludedList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "عدم احتساب در محاسبه: ${PersianNumberFormatter.formatDigits(excludedList.size)} روز (${excludedList.joinToString("، ") { PersianNumberFormatter.formatDigits(it) }})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    )
                }
            }

            // Location if provided
            if (record.location.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "محل: ${record.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Note if provided
            if (record.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = record.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

data class ChandRoozDayItem(
    val year: Int,
    val month: Int,
    val day: Int,
    val dateKey: String,
    val weekdayIndex: Int,
    val weekdayName: String,
    val displayDate: String,
    val isFriday: Boolean,
    val isThursday: Boolean,
    val millis: Long
)

private fun generateDaysInRange(
    startYear: Int, startMonth: Int, startDay: Int,
    endYear: Int, endMonth: Int, endDay: Int
): List<ChandRoozDayItem> {
    val startMillis = PersianDateHelper.toMillis(startYear, startMonth, startDay, 12, 0)
    val endMillis = PersianDateHelper.toMillis(endYear, endMonth, endDay, 12, 0)
    if (endMillis < startMillis) {
        val wIndex = getPersianDayOfWeekIndex(startYear, startMonth, startDay)
        val monthName = PersianDateHelper.PERSIAN_MONTHS.getOrElse(startMonth - 1) { "" }
        return listOf(
            ChandRoozDayItem(
                year = startYear,
                month = startMonth,
                day = startDay,
                dateKey = "$startYear/${startMonth.toString().padStart(2, '0')}/${startDay.toString().padStart(2, '0')}",
                weekdayIndex = wIndex,
                weekdayName = PersianDateHelper.PERSIAN_WEEKDAYS.getOrElse(wIndex) { "" },
                displayDate = "$startDay $monthName",
                isFriday = wIndex == 6,
                isThursday = wIndex == 5,
                millis = startMillis
            )
        )
    }

    val list = mutableListOf<ChandRoozDayItem>()
    val oneDayMillis = 24 * 60 * 60 * 1000L
    var currentMillis = startMillis
    var counter = 0
    while (currentMillis <= endMillis + (oneDayMillis / 2) && counter < 366) {
        counter++
        val pDate = PersianDateHelper.fromMillis(currentMillis)
        val wIndex = getPersianDayOfWeekIndex(pDate.year, pDate.month, pDate.day)
        val monthName = PersianDateHelper.PERSIAN_MONTHS.getOrElse(pDate.month - 1) { "" }
        val dateKey = "${pDate.year}/${pDate.month.toString().padStart(2, '0')}/${pDate.day.toString().padStart(2, '0')}"
        list.add(
            ChandRoozDayItem(
                year = pDate.year,
                month = pDate.month,
                day = pDate.day,
                dateKey = dateKey,
                weekdayIndex = wIndex,
                weekdayName = PersianDateHelper.PERSIAN_WEEKDAYS.getOrElse(wIndex) { "" },
                displayDate = "${pDate.day} $monthName",
                isFriday = wIndex == 6,
                isThursday = wIndex == 5,
                millis = currentMillis
            )
        )
        currentMillis += oneDayMillis
    }
    return list
}

@Composable
fun AddEditChandRoozDialog(
    record: ChandRoozRecordEntity?,
    onDismiss: () -> Unit,
    onSave: (ChandRoozRecordEntity) -> Unit
) {
    val isEdit = record != null

    var title by remember { mutableStateOf(record?.title ?: "") }
    var selectedCategory by remember { mutableStateOf(record?.category ?: ChandRoozCategory.MISSION) }
    var customCategory by remember { mutableStateOf(record?.customCategory ?: "") }
    var isRange by remember { mutableStateOf(record?.isRange ?: true) }
    var location by remember { mutableStateOf(record?.location ?: "") }
    var note by remember { mutableStateOf(record?.note ?: "") }

    // Jalali Date states
    val initialStartPDate = remember(record) {
        if (record != null) PersianDateHelper.fromMillis(record.startDateMillis)
        else PersianDateHelper.getCurrentPersianDate()
    }
    val initialEndPDate = remember(record) {
        if (record != null) PersianDateHelper.fromMillis(record.endDateMillis)
        else {
            val now = PersianDateHelper.getCurrentPersianDate()
            val maxDays = PersianDateHelper.getMonthLength(now.year, now.month)
            val newDay = (now.day + 9).coerceAtMost(maxDays)
            PersianDate(now.year, now.month, newDay)
        }
    }

    var startYear by remember { mutableIntStateOf(initialStartPDate.year) }
    var startMonth by remember { mutableIntStateOf(initialStartPDate.month) }
    var startDay by remember { mutableIntStateOf(initialStartPDate.day) }

    var endYear by remember { mutableIntStateOf(initialEndPDate.year) }
    var endMonth by remember { mutableIntStateOf(initialEndPDate.month) }
    var endDay by remember { mutableIntStateOf(initialEndPDate.day) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Calculate dates millis
    val startMillis = remember(startYear, startMonth, startDay) {
        PersianDateHelper.toMillis(startYear, startMonth, startDay, 8, 0)
    }

    val endMillis = remember(endYear, endMonth, endDay) {
        PersianDateHelper.toMillis(endYear, endMonth, endDay, 20, 0)
    }

    // Excluded days state (e.g. Fridays or holidays excluded by the user)
    var excludedDaysSet by remember(record) {
        mutableStateOf(
            record?.excludedDays?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        )
    }

    // Generate list of all individual days in the selected range
    val daysInRange = remember(startYear, startMonth, startDay, endYear, endMonth, endDay) {
        generateDaysInRange(startYear, startMonth, startDay, endYear, endMonth, endDay)
    }

    // Number of days selected (included) in the range
    val selectedDaysCount = remember(daysInRange, excludedDaysSet) {
        daysInRange.count { it.dateKey !in excludedDaysSet }
    }

    val currentDays = if (isRange) selectedDaysCount.coerceAtLeast(1) else 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            shape = RoundedCornerShape(26.dp),
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
                    Text(
                        text = if (isEdit) "ویرایش رویداد در چندروز؟" else "ثبت رویداد جدید در چندروز؟",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان رویداد یا ماموریت") },
                    placeholder = { Text("مثال: ۱۰ روز ماموریت شیراز، مراجعه به بانک ملت، مرخصی...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("chand_rooz_title_input")
                )

                // 5 Categories Selector (ماموریت، مرخصی، استعلاجی، بانک، سایر موارد)
                Text(
                    text = "دسته‌بندی رویداد:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChandRoozCategory.entries.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        val catColor = try {
                            Color(android.graphics.Color.parseColor(cat.defaultColorHex))
                        } catch (_: Exception) {
                            MaterialTheme.colorScheme.primary
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // If "سایر موارد" is chosen -> show custom category textfield
                AnimatedVisibility(visible = selectedCategory == ChandRoozCategory.OTHER) {
                    OutlinedTextField(
                        value = customCategory,
                        onValueChange = { customCategory = it },
                        label = { Text("عنوان دسته سفارشی (تکمیل توسط شما)") },
                        placeholder = { Text("مثال: دوره آموزشی، دادگاه، تعمیرات...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        leadingIcon = {
                            Icon(Icons.Default.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chand_rooz_custom_category_input")
                    )
                }

                // Range vs Single Day Toggle
                Text(
                    text = "نوع مدت زمان:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isRange) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isRange) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isRange = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            RadioButton(selected = isRange, onClick = { isRange = true })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "بازه چندروزه (مثلا ۱۰ روز)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isRange) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (!isRange) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (!isRange) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isRange = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            RadioButton(selected = !isRange, onClick = { isRange = false })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تک‌روزه (۱ روز)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (!isRange) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Interactive Persian Calendar Date Selectors (انتخاب با تقویم شمسی زیبا)
                if (isRange) {
                    // Start Date Card
                    Text(
                        text = "تاریخ شروع بازه:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    CalendarDateSelectorCard(
                        label = "شروع بازه",
                        year = startYear,
                        month = startMonth,
                        day = startDay,
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.testTag("chand_rooz_start_date_card")
                    )

                    // End Date Card
                    Text(
                        text = "تاریخ پایان بازه:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    CalendarDateSelectorCard(
                        label = "پایان بازه",
                        year = endYear,
                        month = endMonth,
                        day = endDay,
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.testTag("chand_rooz_end_date_card")
                    )

                    // Interactive Day Customization Section
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
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
                                Column {
                                    Text(
                                        text = "انتخاب روزهای محاسبه‌شده:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "می‌توانید جمعه‌ها یا روزهای غیرکاری را حذف کنید",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "${PersianNumberFormatter.formatDigits(selectedDaysCount)} از ${PersianNumberFormatter.formatDigits(daysInRange.size)} روز",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Quick Filter Chips Row (حذف جمعه‌ها / حذف ۵شنبه و جمعه / انتخاب همه)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val fridayKeys = remember(daysInRange) { daysInRange.filter { it.isFriday }.map { it.dateKey }.toSet() }
                                val areAllFridaysExcluded = fridayKeys.isNotEmpty() && fridayKeys.all { it in excludedDaysSet }

                                FilterChip(
                                    selected = areAllFridaysExcluded,
                                    onClick = {
                                        excludedDaysSet = if (areAllFridaysExcluded) {
                                            excludedDaysSet - fridayKeys
                                        } else {
                                            excludedDaysSet + fridayKeys
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (areAllFridaysExcluded) "جمعه‌ها کسر شد ✓" else "حذف جمعه‌ها",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (areAllFridaysExcluded) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (areAllFridaysExcluded) Icons.Default.Check else Icons.Default.EventBusy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (areAllFridaysExcluded) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )

                                val weekendKeys = remember(daysInRange) { daysInRange.filter { it.isFriday || it.isThursday }.map { it.dateKey }.toSet() }
                                val areAllWeekendExcluded = weekendKeys.isNotEmpty() && weekendKeys.all { it in excludedDaysSet }
                                FilterChip(
                                    selected = areAllWeekendExcluded,
                                    onClick = {
                                        excludedDaysSet = if (areAllWeekendExcluded) {
                                            excludedDaysSet - weekendKeys
                                        } else {
                                            excludedDaysSet + weekendKeys
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (areAllWeekendExcluded) "۵شنبه و جمعه کسر شد ✓" else "حذف ۵شنبه و جمعه",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )

                                if (excludedDaysSet.isNotEmpty()) {
                                    TextButton(
                                        onClick = { excludedDaysSet = emptySet() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("انتخاب همه روزها", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Text(
                                text = "برای کسر یا اضافه کردن هر روز، روی آن ضربه بزنید:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            // Interactive Days List
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                daysInRange.forEach { dayItem ->
                                    val isIncluded = dayItem.dateKey !in excludedDaysSet
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isIncluded) {
                                            if (dayItem.isFriday) Color(0xFFEF4444).copy(alpha = 0.12f)
                                            else MaterialTheme.colorScheme.surface
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                        },
                                        border = BorderStroke(
                                            width = if (isIncluded) 1.dp else 0.8.dp,
                                            color = if (isIncluded) {
                                                if (dayItem.isFriday) Color(0xFFEF4444).copy(alpha = 0.45f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            } else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                excludedDaysSet = if (isIncluded) {
                                                    excludedDaysSet + dayItem.dateKey
                                                } else {
                                                    excludedDaysSet - dayItem.dateKey
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Checkbox(
                                                    checked = isIncluded,
                                                    onCheckedChange = {
                                                        excludedDaysSet = if (it) {
                                                            excludedDaysSet - dayItem.dateKey
                                                        } else {
                                                            excludedDaysSet + dayItem.dateKey
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = dayItem.weekdayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (dayItem.isFriday) Color(0xFFEF4444)
                                                    else if (isIncluded) MaterialTheme.colorScheme.onSurface
                                                    else MaterialTheme.colorScheme.outline
                                                )
                                                Text(
                                                    text = PersianNumberFormatter.formatDigits(dayItem.displayDate),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isIncluded) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.outline
                                                )
                                            }

                                            if (dayItem.isFriday) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFEF4444).copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = if (isIncluded) "جمعه (محاسبه می‌شود)" else "جمعه (تعطیل - کسر شده)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFEF4444),
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else if (!isIncluded) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = "محاسبه نمی‌شود",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Duration Badge Counter
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.HourglassBottom,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "مجموع روزهای محاسبه‌شده:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "${PersianNumberFormatter.formatDigits(currentDays)} روز",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            val excludedCount = daysInRange.size - selectedDaysCount
                            if (excludedCount > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "💡 ${PersianNumberFormatter.formatDigits(excludedCount)} روز (جمعه‌ها یا روزهای انتخابی شما) کسر شده و محاسبه نمی‌شود.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                } else {
                    // Single day selector
                    Text(
                        text = "تاریخ رویداد:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    CalendarDateSelectorCard(
                        label = "تاریخ رویداد",
                        year = startYear,
                        month = startMonth,
                        day = startDay,
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.testTag("chand_rooz_single_date_card")
                    )
                }

                // Location Input (Optional)
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("محل رویداد یا ماموریت (اختیاری)") },
                    placeholder = { Text("مثال: شعبه مرکزی، تهران، سالن همایش، شرکت...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Note Input (Optional)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات و یادداشت (اختیاری)") },
                    placeholder = { Text("مثال: هماهنگ‌شده با مدیر بخش، تسویه مدارک...") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("انصراف")
                    }

                    Button(
                        onClick = {
                            val resolvedCustomCat = if (selectedCategory == ChandRoozCategory.OTHER) customCategory.trim() else ""
                            val defaultTitle = if (selectedCategory == ChandRoozCategory.OTHER && resolvedCustomCat.isNotBlank()) {
                                resolvedCustomCat
                            } else {
                                selectedCategory.title
                            }

                            val resolvedTitle = if (title.isBlank()) {
                                if (isRange) "$defaultTitle (${currentDays} روز)"
                                else defaultTitle
                            } else title.trim()

                            val effectiveEndMillis = if (isRange) endMillis else startMillis

                            val newRecord = (record ?: ChandRoozRecordEntity(
                                title = resolvedTitle
                            )).copy(
                                title = resolvedTitle,
                                category = selectedCategory,
                                isRange = isRange,
                                startDateMillis = startMillis,
                                endDateMillis = effectiveEndMillis,
                                daysCount = currentDays,
                                location = location.trim(),
                                note = note.trim(),
                                colorHex = selectedCategory.defaultColorHex,
                                customCategory = resolvedCustomCat,
                                excludedDays = if (isRange) excludedDaysSet.sorted().joinToString(",") else ""
                            )
                            onSave(newRecord)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chand_rooz_save_button")
                    ) {
                        Text(if (isEdit) "بروزرسانی" else "ثبت رویداد", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Start Date Persian Calendar Picker Dialog
    if (showStartDatePicker) {
        PersianCalendarPickerDialog(
            title = if (isRange) "انتخاب تاریخ شروع بازه" else "انتخاب تاریخ رویداد",
            initialYear = startYear,
            initialMonth = startMonth,
            initialDay = startDay,
            onDismiss = { showStartDatePicker = false },
            onConfirm = { y, m, d ->
                startYear = y
                startMonth = m
                startDay = d
                // If end date precedes start date in range mode, adjust end date
                val newStartMillis = PersianDateHelper.toMillis(y, m, d)
                val currEndMillis = PersianDateHelper.toMillis(endYear, endMonth, endDay)
                if (currEndMillis < newStartMillis) {
                    endYear = y
                    endMonth = m
                    endDay = d
                }
            }
        )
    }

    // End Date Persian Calendar Picker Dialog
    if (showEndDatePicker) {
        PersianCalendarPickerDialog(
            title = "انتخاب تاریخ پایان بازه",
            initialYear = endYear,
            initialMonth = endMonth,
            initialDay = endDay,
            onDismiss = { showEndDatePicker = false },
            onConfirm = { y, m, d ->
                val newEndMillis = PersianDateHelper.toMillis(y, m, d)
                val currStartMillis = PersianDateHelper.toMillis(startYear, startMonth, startDay)
                if (newEndMillis < currStartMillis) {
                    startYear = y
                    startMonth = m
                    startDay = d
                }
                endYear = y
                endMonth = m
                endDay = d
            }
        )
    }
}

@Composable
private fun CalendarDateSelectorCard(
    label: String,
    year: Int,
    month: Int,
    day: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weekday = PersianDateHelper.PERSIAN_WEEKDAYS[getPersianDayOfWeekIndex(year, month, day)]
    val monthName = PersianDateHelper.getMonthName(month)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "$weekday، ${PersianNumberFormatter.formatDigits(day)} $monthName ${PersianNumberFormatter.formatDigits(year)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            FilledTonalButton(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("تقویم", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PersianCalendarPickerDialog(
    title: String,
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int, day: Int) -> Unit
) {
    var pickerYear by remember { mutableIntStateOf(initialYear) }
    var pickerMonth by remember { mutableIntStateOf(initialMonth) }
    var pickerDay by remember { mutableIntStateOf(initialDay) }
    var isYearPickerOpen by remember { mutableStateOf(false) }

    val today = remember { PersianDateHelper.getCurrentPersianDate() }
    val daysInMonth = remember(pickerYear, pickerMonth) {
        PersianDateHelper.getDaysInPersianMonth(pickerYear, pickerMonth)
    }

    LaunchedEffect(daysInMonth) {
        if (pickerDay > daysInMonth) {
            pickerDay = daysInMonth
        }
    }

    val firstDayOffset = remember(pickerYear, pickerMonth) {
        getPersianDayOfWeekIndex(pickerYear, pickerMonth, 1)
    }

    val selectedWeekdayName = remember(pickerYear, pickerMonth, pickerDay) {
        val safeDay = pickerDay.coerceIn(1, daysInMonth)
        PersianDateHelper.PERSIAN_WEEKDAYS[getPersianDayOfWeekIndex(pickerYear, pickerMonth, safeDay)]
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dialog Title & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "بستن", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Highlight Selected Date Banner
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = selectedWeekdayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${PersianNumberFormatter.formatDigits(pickerDay)} ${PersianDateHelper.getMonthName(pickerMonth)} ${PersianNumberFormatter.formatDigits(pickerYear)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        FilledTonalIconButton(
                            onClick = {
                                pickerYear = today.year
                                pickerMonth = today.month
                                pickerDay = today.day
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Today, contentDescription = "امروز", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Month & Year Header with Navigation Arrows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Right arrow (Month before in RTL)
                    FilledTonalIconButton(
                        onClick = {
                            if (pickerMonth > 1) {
                                pickerMonth--
                            } else {
                                if (pickerYear > 1400) {
                                    pickerYear--
                                    pickerMonth = 12
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "ماه قبل", modifier = Modifier.size(18.dp))
                    }

                    // Month & Year Clickable
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isYearPickerOpen) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.clickable { isYearPickerOpen = !isYearPickerOpen }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${PersianDateHelper.getMonthName(pickerMonth)} ${PersianNumberFormatter.formatDigits(pickerYear)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Icon(
                                if (isYearPickerOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Left arrow (Month next in RTL)
                    FilledTonalIconButton(
                        onClick = {
                            if (pickerMonth < 12) {
                                pickerMonth++
                            } else {
                                if (pickerYear < 1415) {
                                    pickerYear++
                                    pickerMonth = 1
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ماه بعد", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Year selector grid if toggled
                if (isYearPickerOpen) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "انتخاب سال تقویم:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val yearsList = (1400..1415).toList()
                        val rowCount = (yearsList.size + 3) / 4
                        for (r in 0 until rowCount) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (c in 0 until 4) {
                                    val index = r * 4 + c
                                    if (index < yearsList.size) {
                                        val y = yearsList[index]
                                        val isSelected = y == pickerYear
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    pickerYear = y
                                                    isYearPickerOpen = false
                                                }
                                        ) {
                                            Text(
                                                text = PersianNumberFormatter.formatDigits(y),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Persian Calendar Days Grid
                    // Weekday headers: ش، ی، د، س، چ، پ، ج
                    val weekdayShort = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        weekdayShort.forEachIndexed { idx, dayName ->
                            val isFriday = idx == 6
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isFriday) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // 7 Columns Grid of Days
                    val totalCells = firstDayOffset + daysInMonth
                    val rowCount = (totalCells + 6) / 7

                    for (r in 0 until rowCount) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (c in 0 until 7) {
                                val cellIndex = r * 7 + c
                                if (cellIndex in firstDayOffset until (firstDayOffset + daysInMonth)) {
                                    val dayNum = cellIndex - firstDayOffset + 1
                                    val isSelected = dayNum == pickerDay
                                    val isToday = dayNum == today.day && pickerMonth == today.month && pickerYear == today.year
                                    val isFriday = c == 6

                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                } else Modifier
                                            )
                                            .clickable { pickerDay = dayNum },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = PersianNumberFormatter.formatDigits(dayNum),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> Color.White
                                                isFriday -> Color(0xFFEF4444)
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                } else {
                                    // Empty slot
                                    Spacer(modifier = Modifier.size(38.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons: Cancel & Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("انصراف")
                    }
                    Button(
                        onClick = {
                            onConfirm(pickerYear, pickerMonth, pickerDay)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تایید تاریخ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun getPersianDayOfWeekIndex(year: Int, month: Int, day: Int): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = PersianDateHelper.toMillis(year, month, day, 12, 0)
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> 0
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 3
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY -> 5
        Calendar.FRIDAY -> 6
        else -> 0
    }
}
