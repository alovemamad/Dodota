package ir.mmd.dodota.ui.screens.debts

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.*
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.util.AlarmHelper
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch
import java.util.Calendar

private val DebtSideRedColor = ExpenseRed
private val ReceivableSideGreenColor = IncomeGreen

enum class DebtStatusFilter(val title: String) {
    ALL("همه موارد"),
    ACTIVE_ONLY("فقط موارد در جریان"),
    SETTLED_ONLY("فقط موارد تسویه‌شده")
}

enum class DebtSortOption(val title: String, val icon: ImageVector) {
    BY_DUE_DATE("نزدیک‌ترین سررسید", Icons.Default.Schedule),
    BY_REMAINING_DESC("بیشترین مبلغ مانده", Icons.Default.TrendingUp),
    BY_TOTAL_DESC("بیشترین مبلغ کل", Icons.Default.AccountBalanceWallet)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtsScreen(
    debts: List<DebtEntity>,
    userPreferences: UserPreferences,
    repository: DodotaRepository,
    accounts: List<AccountEntity>,
    installmentPlans: List<InstallmentPlanEntity> = emptyList(),
    allInstallments: List<InstallmentEntity> = emptyList(),
    activeReminders: List<ReminderEntity> = emptyList(),
    onBack: (() -> Unit)? = null,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToInstallmentDetail: ((Long) -> Unit)? = null
) {
    // Item 8: Page strictly has two sides: «بدهی» and «طلب»
    var selectedSide by remember { mutableStateOf(DebtType.DEBT_I_OWE) }
    val isDebtSide = selectedSide == DebtType.DEBT_I_OWE
    val sideTitle = if (isDebtSide) "بدهی" else "طلب"
    val sideAccentColor = if (isDebtSide) DebtSideRedColor else ReceivableSideGreenColor

    // Item 12: Filter state controlled via top-bar icon and ModalBottomSheet
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var selectedStatusFilter by remember { mutableStateOf(DebtStatusFilter.ALL) }
    var selectedSortOption by remember { mutableStateOf(DebtSortOption.BY_DUE_DATE) }
    val isFilterCustomized = selectedStatusFilter != DebtStatusFilter.ALL || selectedSortOption != DebtSortOption.BY_DUE_DATE

    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<DebtEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<DebtEntity?>(null) }
    var itemForQuickReminder by remember { mutableStateOf<DebtEntity?>(null) }
    var itemForQuickPayment by remember { mutableStateOf<DebtEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val debtReminderMap = remember(activeReminders) {
        activeReminders.filter { it.targetType == ReminderTargetType.DEBT && it.targetId != null }
            .associateBy { it.targetId!! }
    }

    // Item 11: Exclude checks from Debts & Receivables screen
    val nonCheckDebts = remember(debts) {
        debts.filter { !(it.isCheck || it.personOrCompanyName.contains("چک") || it.notes.contains("چک")) }
    }

    val sideAllItems = remember(nonCheckDebts, selectedSide) {
        nonCheckDebts.filter { it.type == selectedSide }
    }

    val sideTotalAmount = sideAllItems.sumOf { it.totalAmount }
    val sidePaidAmount = sideAllItems.sumOf { it.paidAmount.coerceAtMost(it.totalAmount) }
    val sideRemainingAmount = (sideTotalAmount - sidePaidAmount).coerceAtLeast(0L)
    val sideProgress = if (sideTotalAmount > 0L) {
        (sidePaidAmount.toFloat() / sideTotalAmount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val filteredItems = remember(sideAllItems, selectedStatusFilter, selectedSortOption) {
        sideAllItems
            .filter { d ->
                val isSettled = d.status == DebtStatus.SETTLED || (d.totalAmount > 0L && d.paidAmount >= d.totalAmount)
                when (selectedStatusFilter) {
                    DebtStatusFilter.ALL -> true
                    DebtStatusFilter.ACTIVE_ONLY -> !isSettled
                    DebtStatusFilter.SETTLED_ONLY -> isSettled
                }
            }
            .sortedWith(
                compareBy<DebtEntity> { d ->
                    d.status == DebtStatus.SETTLED || (d.totalAmount > 0L && d.paidAmount >= d.totalAmount)
                }.thenComparator { a, b ->
                    when (selectedSortOption) {
                        DebtSortOption.BY_DUE_DATE -> {
                            val da = a.dueDateMillis ?: Long.MAX_VALUE
                            val db = b.dueDateMillis ?: Long.MAX_VALUE
                            da.compareTo(db)
                        }
                        DebtSortOption.BY_REMAINING_DESC -> {
                            val ra = (a.totalAmount - a.paidAmount).coerceAtLeast(0L)
                            val rb = (b.totalAmount - b.paidAmount).coerceAtLeast(0L)
                            rb.compareTo(ra)
                        }
                        DebtSortOption.BY_TOTAL_DESC -> b.totalAmount.compareTo(a.totalAmount)
                    }
                }
            )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                // Item 9: Each section only shows its own title («بدهی» or «طلب»)
                title = sideTitle,
                onBackClick = onBack,
                actions = {
                    // Item 12: Only a small filter icon in the top bar
                    Box(modifier = Modifier.padding(end = 6.dp)) {
                        IconButton(
                            onClick = { showFilterBottomSheet = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isFilterCustomized) sideAccentColor.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                )
                                .testTag("debts_filter_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "فیلتر",
                                tint = if (isFilterCustomized) sideAccentColor else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (isFilterCustomized) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(sideAccentColor)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = sideAccentColor,
                contentColor = if (isDebtSide) Color.White else Color.Black,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.testTag("add_debt_or_receivable_fab")
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (isDebtSide) "ثبت بدهی" else "ثبت طلب"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Two-Sided Switcher: Only «بدهی» and «طلب» (Item 8)
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SideTabButton(
                            title = "بدهی",
                            selected = isDebtSide,
                            accentColor = DebtSideRedColor,
                            onClick = { selectedSide = DebtType.DEBT_I_OWE },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_side_debt")
                        )
                        SideTabButton(
                            title = "طلب",
                            selected = !isDebtSide,
                            accentColor = ReceivableSideGreenColor,
                            onClick = { selectedSide = DebtType.RECEIVABLE_OWED_TO_ME },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_side_receivable")
                        )
                    }
                }
            }

            // 2. Single-Side Summary Card (Item 9: Only shows title of active side)
            item {
                SideOutlookSummaryCard(
                    isDebtSide = isDebtSide,
                    totalAmount = sideTotalAmount,
                    paidAmount = sidePaidAmount,
                    remainingAmount = sideRemainingAmount,
                    progress = sideProgress,
                    currency = userPreferences.currency,
                    onAddClick = { showAddDialog = true }
                )
            }

            // 3. List of Items for Active Side
            if (filteredItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = if (isDebtSide) "بدهی ثبت نشده است" else "طلبی ثبت نشده است",
                        description = "",
                        icon = Icons.Outlined.AccountBalanceWallet
                    )
                }
            } else {
                items(
                    items = filteredItems,
                    key = { "debt_${it.id}" }
                ) { debt ->
                    DebtOrReceivableCard(
                        debt = debt,
                        activeReminder = debtReminderMap[debt.id],
                        currency = userPreferences.currency,
                        onCardClick = { onNavigateToDetail(debt.id) },
                        onRecordPayment = { itemForQuickPayment = debt },
                        onConfigureReminder = { itemForQuickReminder = debt },
                        onEdit = { itemToEdit = debt },
                        onDelete = { itemToDelete = debt }
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // FILTER POPUP / BOTTOM SHEET (Item 11 & Item 12)
    // ---------------------------------------------------------
    if (showFilterBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterBottomSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = sideAccentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDebtSide) "فیلتر و مرتب‌سازی بدهی" else "فیلتر و مرتب‌سازی طلب",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    if (isFilterCustomized) {
                        TextButton(
                            onClick = {
                                selectedStatusFilter = DebtStatusFilter.ALL
                                selectedSortOption = DebtSortOption.BY_DUE_DATE
                            }
                        ) {
                            Text("بازنشانی", color = sideAccentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "وضعیت تسویه:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebtStatusFilter.values().forEach { filter ->
                        val selected = selectedStatusFilter == filter
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) sideAccentColor.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) sideAccentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedStatusFilter = filter }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = filter.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (selected) sideAccentColor else MaterialTheme.colorScheme.onSurface
                                )
                                if (selected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = sideAccentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "مرتب‌سازی بر اساس:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DebtSortOption.values().forEach { sortOption ->
                        val selected = selectedSortOption == sortOption
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) sideAccentColor.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) sideAccentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedSortOption = sortOption }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = sortOption.icon,
                                        contentDescription = null,
                                        tint = if (selected) sideAccentColor else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = sortOption.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (selected) sideAccentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = sideAccentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { showFilterBottomSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = sideAccentColor),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = "اعمال فیلتر",
                        color = if (isDebtSide) Color.White else Color.Black,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // DIALOG 0: Quick Record Payment / Receipt
    // ---------------------------------------------------------
    itemForQuickPayment?.let { debt ->
        val remaining = (debt.totalAmount - debt.paidAmount).coerceAtLeast(0L)
        QuickRecordDebtPaymentDialog(
            debt = debt,
            remainingAmount = remaining,
            currency = userPreferences.currency,
            accounts = accounts,
            onDismiss = { itemForQuickPayment = null },
            onConfirm = { payAmount, selectedAccountId, note ->
                coroutineScope.launch {
                    repository.recordDebtPayment(
                        debt = debt,
                        amount = payAmount,
                        accountId = selectedAccountId,
                        notes = note
                    )
                    val msg = if (debt.type == DebtType.DEBT_I_OWE) "پرداخت ثبت شد ✓" else "دریافت ثبت شد ✓"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    itemForQuickPayment = null
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DIALOG 1: Add Debt or Receivable (strictly for active side)
    // ---------------------------------------------------------
    if (showAddDialog) {
        AddOrEditDebtDialog(
            existingDebt = null,
            existingReminder = null,
            sideType = selectedSide,
            currency = userPreferences.currency,
            onDismiss = { showAddDialog = false },
            onSave = { titleOrName, totalAmount, initialPaidAmount, dueDateMillis, notes, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    val effectiveDueDate = if (enableReminder) {
                        dueDateMillis ?: System.currentTimeMillis()
                    } else {
                        dueDateMillis
                    }
                    val safePaid = initialPaidAmount.coerceIn(0L, totalAmount)
                    val initialStatus = if (safePaid >= totalAmount && totalAmount > 0L) DebtStatus.SETTLED else DebtStatus.ACTIVE

                    val debtId = repository.addDebt(
                        DebtEntity(
                            type = selectedSide,
                            personOrCompanyName = titleOrName,
                            totalAmount = totalAmount,
                            paidAmount = safePaid,
                            dueDateMillis = effectiveDueDate,
                            notes = notes,
                            isCheck = false,
                            checkNumber = "",
                            status = initialStatus
                        )
                    )

                    if (effectiveDueDate != null && enableReminder && initialStatus != DebtStatus.SETTLED) {
                        val triggerMillis = computeObligationTriggerMillis(effectiveDueDate, offsetDays, reminderHour, reminderMinute)
                        val remTitle = if (selectedSide == DebtType.DEBT_I_OWE) {
                            "سررسید بدهی: $titleOrName"
                        } else {
                            "سررسید طلب: $titleOrName"
                        }
                        val remDesc = "مانده: ${PersianNumberFormatter.formatCurrency(totalAmount - safePaid, userPreferences.currency)}"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.DEBT,
                            targetId = debtId,
                            title = remTitle,
                            description = remDesc,
                            dueDateTimeMillis = triggerMillis,
                            offsetDays = offsetDays,
                            enabled = true
                        )
                        if (remId != null) {
                            AlarmHelper.scheduleExactAlarm(context, remId, remTitle, remDesc, triggerMillis)
                        }
                    }

                    Toast.makeText(
                        context,
                        if (selectedSide == DebtType.DEBT_I_OWE) "بدهی با موفقیت ثبت شد" else "طلب با موفقیت ثبت شد",
                        Toast.LENGTH_SHORT
                    ).show()
                    showAddDialog = false
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DIALOG 2: Edit Debt or Receivable
    // ---------------------------------------------------------
    itemToEdit?.let { debt ->
        AddOrEditDebtDialog(
            existingDebt = debt,
            existingReminder = debtReminderMap[debt.id],
            sideType = debt.type,
            currency = userPreferences.currency,
            onDismiss = { itemToEdit = null },
            onSave = { titleOrName, totalAmount, paidAmount, dueDateMillis, notes, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    val safePaid = paidAmount.coerceIn(0L, totalAmount)
                    val status = if (safePaid >= totalAmount && totalAmount > 0L) DebtStatus.SETTLED else DebtStatus.ACTIVE
                    val effectiveDueDate = if (enableReminder) {
                        dueDateMillis ?: debt.dueDateMillis ?: System.currentTimeMillis()
                    } else {
                        dueDateMillis
                    }

                    repository.updateDebt(
                        debt.copy(
                            personOrCompanyName = titleOrName,
                            totalAmount = totalAmount,
                            paidAmount = safePaid,
                            dueDateMillis = effectiveDueDate,
                            notes = notes,
                            isCheck = false,
                            status = status
                        )
                    )

                    if (effectiveDueDate != null && enableReminder && status != DebtStatus.SETTLED) {
                        val triggerMillis = computeObligationTriggerMillis(effectiveDueDate, offsetDays, reminderHour, reminderMinute)
                        val remTitle = if (debt.type == DebtType.DEBT_I_OWE) {
                            "سررسید بدهی: $titleOrName"
                        } else {
                            "سررسید طلب: $titleOrName"
                        }
                        val remDesc = "مانده: ${PersianNumberFormatter.formatCurrency(totalAmount - safePaid, userPreferences.currency)}"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.DEBT,
                            targetId = debt.id,
                            title = remTitle,
                            description = remDesc,
                            dueDateTimeMillis = triggerMillis,
                            offsetDays = offsetDays,
                            enabled = true
                        )
                        if (remId != null) {
                            AlarmHelper.scheduleExactAlarm(context, remId, remTitle, remDesc, triggerMillis)
                        }
                    } else {
                        repository.removeTargetReminders(ReminderTargetType.DEBT, debt.id)
                    }

                    Toast.makeText(
                        context,
                        if (debt.type == DebtType.DEBT_I_OWE) "بدهی بروزرسانی شد" else "طلب بروزرسانی شد",
                        Toast.LENGTH_SHORT
                    ).show()
                    itemToEdit = null
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DIALOG 3: Quick Reminder Config
    // ---------------------------------------------------------
    itemForQuickReminder?.let { debt ->
        val isOwe = debt.type == DebtType.DEBT_I_OWE
        QuickObligationReminderDialog(
            title = if (isOwe) "تنظیم یادآور بدهی: ${debt.personOrCompanyName}" else "تنظیم یادآور طلب: ${debt.personOrCompanyName}",
            initialDueDateMillis = debt.dueDateMillis ?: System.currentTimeMillis(),
            existingReminder = debtReminderMap[debt.id],
            onDismiss = { itemForQuickReminder = null },
            onSave = { dueMillis, enabled, offsetDays, hour, minute ->
                coroutineScope.launch {
                    repository.updateDebt(debt.copy(dueDateMillis = dueMillis))
                    if (enabled) {
                        val triggerMillis = computeObligationTriggerMillis(dueMillis, offsetDays, hour, minute)
                        val remTitle = if (isOwe) "سررسید بدهی: ${debt.personOrCompanyName}"
                        else "سررسید طلب: ${debt.personOrCompanyName}"
                        val remDesc = "مبلغ: ${PersianNumberFormatter.formatCurrency(debt.totalAmount - debt.paidAmount, userPreferences.currency)}"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.DEBT,
                            targetId = debt.id,
                            title = remTitle,
                            description = remDesc,
                            dueDateTimeMillis = triggerMillis,
                            offsetDays = offsetDays,
                            enabled = true
                        )
                        if (remId != null) {
                            AlarmHelper.scheduleExactAlarm(context, remId, remTitle, remDesc, triggerMillis)
                        }
                        Toast.makeText(context, "یادآور فعال شد ⏰", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.removeTargetReminders(ReminderTargetType.DEBT, debt.id)
                        Toast.makeText(context, "یادآور غیرفعال شد", Toast.LENGTH_SHORT).show()
                    }
                    itemForQuickReminder = null
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DELETE CONFIRMATION (Item 9: Only shows title of active side)
    // ---------------------------------------------------------
    itemToDelete?.let { debt ->
        val isOwe = debt.type == DebtType.DEBT_I_OWE
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(if (isOwe) "حذف بدهی" else "حذف طلب") },
            text = {
                Text(
                    "آیا از حذف «${debt.personOrCompanyName}» به مبلغ ${PersianNumberFormatter.formatCurrency(debt.totalAmount, userPreferences.currency)} اطمینان دارید؟"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteDebt(debt)
                            Toast.makeText(
                                context,
                                if (isOwe) "بدهی حذف شد" else "طلب حذف شد",
                                Toast.LENGTH_SHORT
                            ).show()
                            itemToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun SideTabButton(
    title: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accentColor.copy(alpha = 0.22f) else Color.Transparent,
        border = if (selected) BorderStroke(1.5.dp, accentColor) else null,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CircularProgressGauge(
    progress: Float,
    primaryColor: Color,
    secondaryColor: Color = primaryColor.copy(alpha = 0.6f),
    size: Dp = 74.dp,
    strokeWidth: Dp = 8.dp,
    centerLabel: String,
    subLabel: String? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "gauge_anim"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f + 2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)

            drawCircle(
                color = primaryColor.copy(alpha = 0.08f),
                radius = (this.size.minDimension / 2f) - strokePx
            )

            drawArc(
                color = primaryColor.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (animatedProgress > 0.01f) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(primaryColor, secondaryColor, IncomeGreen)
                    ),
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerLabel,
                style = if (size >= 70.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (progress >= 1f) IncomeGreen else primaryColor
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SideOutlookSummaryCard(
    isDebtSide: Boolean,
    totalAmount: Long,
    paidAmount: Long,
    remainingAmount: Long,
    progress: Float,
    currency: AppCurrency,
    onAddClick: () -> Unit
) {
    val accentColor = if (isDebtSide) DebtSideRedColor else ReceivableSideGreenColor
    val percentPaid = (progress * 100).toInt()

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(accentColor.copy(alpha = 0.75f), accentColor.copy(alpha = 0.3f))
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    // Item 9 & 34: Only shows its own title («بدهی» or «طلب») without «چشم‌انداز»
                    text = if (isDebtSide) "بدهی" else "طلب",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAddClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isDebtSide) "ثبت بدهی" else "ثبت طلب",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressGauge(
                    progress = progress,
                    primaryColor = accentColor,
                    size = 86.dp,
                    strokeWidth = 8.dp,
                    centerLabel = "${PersianNumberFormatter.formatDigits(percentPaid, currency)}٪",
                    subLabel = if (isDebtSide) "پرداخت‌شده" else "دریافت‌شده"
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = IncomeGreen.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isDebtSide) "پرداخت‌شده:" else "دریافت‌شده:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = PersianNumberFormatter.formatCurrency(paidAmount, currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = IncomeGreen
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isDebtSide) "مانده بدهی:" else "مانده طلب:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = PersianNumberFormatter.formatCurrency(remainingAmount, currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtOrReceivableCard(
    debt: DebtEntity,
    activeReminder: ReminderEntity?,
    currency: AppCurrency,
    onCardClick: () -> Unit,
    onRecordPayment: () -> Unit,
    onConfigureReminder: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isDebtSide = debt.type == DebtType.DEBT_I_OWE
    val accentColor = if (isDebtSide) DebtSideRedColor else ReceivableSideGreenColor
    // Item 8 & Item 9: Only «بدهی» or «طلب» (no «شخص» and no cross-label)
    val badgeTitle = if (isDebtSide) "بدهی" else "طلب"

    val paidSoFar = debt.paidAmount.coerceIn(0L, debt.totalAmount)
    val remaining = (debt.totalAmount - paidSoFar).coerceAtLeast(0L)
    val progress = if (debt.totalAmount > 0L) (paidSoFar.toFloat() / debt.totalAmount.toFloat()).coerceIn(0f, 1f) else 0f
    val percentPaid = (progress * 100).toInt()

    GlassCard(
        shape = RoundedCornerShape(24.dp),
        borderColor = accentColor.copy(alpha = 0.6f),
        isHighlighted = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = onCardClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CircularProgressGauge(
                        progress = progress,
                        primaryColor = accentColor,
                        size = 56.dp,
                        strokeWidth = 6.dp,
                        centerLabel = "${PersianNumberFormatter.formatDigits(percentPaid, currency)}٪"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                        ) {
                            Text(
                                text = badgeTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = debt.personOrCompanyName.ifBlank { badgeTitle },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onConfigureReminder,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (activeReminder != null) accentColor.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(
                            imageVector = if (activeReminder != null) Icons.Default.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = "تنظیم یادآور",
                            tint = if (activeReminder != null) accentColor else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
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
                        modifier = Modifier.size(32.dp)
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = IncomeGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = IncomeGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDebtSide) "پرداخت‌شده" else "دریافت‌شده",
                                style = MaterialTheme.typography.labelSmall,
                                color = IncomeGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = PersianNumberFormatter.formatCurrency(paidSoFar, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = IncomeGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "مانده از ${PersianNumberFormatter.formatNumberWithCommas(debt.totalAmount, currency)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = PersianNumberFormatter.formatCurrency(remaining, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (percentPaid >= 100) IncomeGreen else accentColor,
                trackColor = MaterialTheme.colorScheme.surface
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (debt.dueDateMillis != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "سررسید: ${PersianDateHelper.formatToPersianDate(debt.dueDateMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (activeReminder != null) {
                        val cal = Calendar.getInstance().apply { timeInMillis = activeReminder.dueDateTimeMillis }
                        val h = PersianNumberFormatter.formatDigits(String.format("%02d", cal.get(Calendar.HOUR_OF_DAY)), currency)
                        val m = PersianNumberFormatter.formatDigits(String.format("%02d", cal.get(Calendar.MINUTE)), currency)
                        Text(
                            text = "⏰ یادآور: ساعت $h:$m",
                            style = MaterialTheme.typography.labelSmall,
                            color = IncomeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Item 13: No "لمس برای پرداخت" text; payment button & card tap work directly
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (remaining > 0L) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IncomeGreen.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { onRecordPayment() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = IncomeGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isDebtSide) "ثبت پرداخت" else "ثبت دریافت",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = IncomeGreen
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { onConfigureReminder() }
                    ) {
                        Text(
                            text = if (activeReminder != null) "ویرایش یادآور" else "+ یادآور",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickRecordDebtPaymentDialog(
    debt: DebtEntity,
    remainingAmount: Long,
    currency: AppCurrency,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, accountId: Long?, note: String) -> Unit
) {
    val isDebtSide = debt.type == DebtType.DEBT_I_OWE
    var amountText by remember { mutableStateOf(remainingAmount.toString()) }
    var selectedAccountId by remember { mutableStateOf<Long?>(accounts.firstOrNull()?.id) }
    var note by remember { mutableStateOf("") }

    GraphicalFormDialog(
        onDismissRequest = onDismiss,
        title = if (isDebtSide) "ثبت پرداخت بدهی" else "ثبت دریافت طلب",
        icon = Icons.Default.Payments,
        accentColor = IncomeGreen,
        confirmText = if (isDebtSide) "ثبت پرداخت ✓" else "ثبت دریافت ✓",
        onConfirm = {
            val amt = PersianNumberFormatter.parseAmount(amountText)
            if (amt > 0L) {
                onConfirm(amt, selectedAccountId, note.trim())
            }
        }
    ) {
        Text(
            text = "مانده فعلی: ${PersianNumberFormatter.formatCurrency(remainingAmount, currency)}",
            style = MaterialTheme.typography.bodySmall,
            color = IncomeGreen,
            fontWeight = FontWeight.Bold
        )

        PersianAmountInput(
            value = amountText,
            onValueChange = { amountText = it },
            currency = currency,
            label = if (isDebtSide) "مبلغ پرداخت‌شده" else "مبلغ دریافت‌شده"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(
                onClick = { amountText = (remainingAmount / 2).coerceAtLeast(1L).toString() },
                label = { Text("نصف مانده (${PersianNumberFormatter.formatDigits("50%", currency)})") }
            )
            AssistChip(
                onClick = { amountText = remainingAmount.toString() },
                label = { Text("کل مانده (تسویه کامل)") }
            )
        }

        DodotaTextField(
            value = note,
            onValueChange = { note = it },
            label = "توضیحات",
            leadingIcon = Icons.Default.Notes,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Dialog for Adding or Editing a Debt («بدهی») or Receivable («طلب»)
 * - Item 8: In «طلب», the «شخص» section is removed; only «بدهی» and «طلب» exist.
 * - Item 9: «بدهی» dialog only shows «بدهی» texts; «طلب» dialog only shows «طلب» texts.
 * - Item 10: In «مبلغ پرداخت‌شده», the «اختیاری» label is removed while the field remains optional.
 * - Item 11: No «چک» option in the Debt/Receivable form.
 */
@Composable
private fun AddOrEditDebtDialog(
    existingDebt: DebtEntity?,
    existingReminder: ReminderEntity?,
    sideType: DebtType,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (
        titleOrName: String,
        totalAmount: Long,
        paidAmount: Long,
        dueDateMillis: Long?,
        notes: String,
        enableReminder: Boolean,
        offsetDays: Int,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Unit
) {
    val isDebtSide = sideType == DebtType.DEBT_I_OWE
    val sideAccent = if (isDebtSide) DebtSideRedColor else ReceivableSideGreenColor
    var titleText by remember { mutableStateOf(existingDebt?.personOrCompanyName ?: "") }
    var amountText by remember { mutableStateOf(existingDebt?.totalAmount?.toString() ?: "") }
    var paidAmountText by remember { mutableStateOf(existingDebt?.paidAmount?.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember { mutableStateOf(existingDebt?.notes ?: "") }
    var dueDateMillis by remember {
        mutableStateOf<Long?>(existingDebt?.dueDateMillis ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }

    val initialCal = remember(existingReminder) {
        Calendar.getInstance().apply {
            if (existingReminder != null) {
                timeInMillis = existingReminder.dueDateTimeMillis
            } else {
                add(Calendar.MINUTE, 2)
            }
        }
    }
    var enableReminder by remember { mutableStateOf(existingReminder != null || existingDebt == null) }
    var offsetDays by remember { mutableIntStateOf(existingReminder?.reminderOffsetDays ?: 0) }
    var reminderHour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var reminderMinute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }

    GraphicalFormDialog(
        onDismissRequest = onDismiss,
        title = when {
            existingDebt != null && isDebtSide -> "ویرایش بدهی"
            existingDebt != null -> "ویرایش طلب"
            isDebtSide -> "ثبت بدهی جدید"
            else -> "ثبت طلب جدید"
        },
        icon = Icons.Default.AccountBalanceWallet,
        accentColor = sideAccent,
        confirmText = if (existingDebt == null) "ثبت و ذخیره" else "بروزرسانی",
        onConfirm = {
            val amount = PersianNumberFormatter.parseAmount(amountText)
            val paidAmt = PersianNumberFormatter.parseAmount(paidAmountText)
            if (amount > 0L) {
                val effectiveTitle = if (isDebtSide) {
                    titleText.trim().ifBlank { notes.trim().ifBlank { "بدهی" } }
                } else {
                    notes.trim().ifBlank { titleText.trim().ifBlank { "طلب" } }
                }
                onSave(
                    effectiveTitle,
                    amount,
                    paidAmt,
                    dueDateMillis,
                    notes.trim(),
                    enableReminder,
                    offsetDays,
                    reminderHour,
                    reminderMinute
                )
            }
        }
    ) {
        if (isDebtSide) {
            DodotaTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = "عنوان بدهی",
                leadingIcon = Icons.Default.Description,
                modifier = Modifier.fillMaxWidth()
            )
        }

        PersianAmountInput(
            value = amountText,
            onValueChange = { amountText = it },
            currency = currency,
            label = if (isDebtSide) "مبلغ کل بدهی" else "مبلغ کل طلب"
        )

        PersianAmountInput(
            value = paidAmountText,
            onValueChange = { paidAmountText = it },
            currency = currency,
            label = if (isDebtSide) "مبلغ پرداخت‌شده" else "مبلغ دریافت‌شده"
        )

        OutlinedButton(
            onClick = { showDatePicker = true },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, sideAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = sideAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (dueDateMillis != null) {
                    "تاریخ سررسید: ${PersianDateHelper.formatToPersianDate(dueDateMillis!!)}"
                } else {
                    "انتخاب تاریخ سررسید"
                },
                fontWeight = FontWeight.Bold,
                color = sideAccent
            )
        }

        ReminderConfigSection(
            isEnabled = enableReminder,
            onEnabledChange = { checked ->
                enableReminder = checked
                if (checked && dueDateMillis == null) {
                    dueDateMillis = System.currentTimeMillis()
                }
            },
            offsetDays = offsetDays,
            onOffsetDaysChange = { offsetDays = it },
            selectedHour = reminderHour,
            onHourChange = { reminderHour = it },
            selectedMinute = reminderMinute,
            onMinuteChange = { reminderMinute = it },
            title = if (isDebtSide) "یادآور و ساعت هشدار بدهی" else "یادآور و ساعت هشدار طلب",
            dueDateMillis = dueDateMillis ?: System.currentTimeMillis(),
            onDueDateChange = { dueDateMillis = it }
        )

        DodotaTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "توضیحات",
            leadingIcon = Icons.Default.Notes,
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (showDatePicker) {
        PersianDatePickerDialog(
            initialDateMillis = dueDateMillis ?: System.currentTimeMillis(),
            onDateSelected = {
                dueDateMillis = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun QuickObligationReminderDialog(
    title: String,
    initialDueDateMillis: Long,
    existingReminder: ReminderEntity?,
    onDismiss: () -> Unit,
    onSave: (dueMillis: Long, enabled: Boolean, offsetDays: Int, hour: Int, minute: Int) -> Unit
) {
    var dueDateMillis by remember { mutableLongStateOf(initialDueDateMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    val initialCal = remember(existingReminder) {
        Calendar.getInstance().apply {
            if (existingReminder != null) {
                timeInMillis = existingReminder.dueDateTimeMillis
            } else {
                add(Calendar.MINUTE, 2)
            }
        }
    }
    var enabled by remember { mutableStateOf(true) }
    var offsetDays by remember { mutableIntStateOf(existingReminder?.reminderOffsetDays ?: 0) }
    var hour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }

    GraphicalFormDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Default.NotificationsActive,
        confirmText = "ذخیره",
        onConfirm = { onSave(dueDateMillis, enabled, offsetDays, hour, minute) }
    ) {
        OutlinedButton(
            onClick = { showDatePicker = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("تاریخ سررسید: ${PersianDateHelper.formatToPersianDate(dueDateMillis)}")
        }

        ReminderConfigSection(
            isEnabled = enabled,
            onEnabledChange = { enabled = it },
            offsetDays = offsetDays,
            onOffsetDaysChange = { offsetDays = it },
            selectedHour = hour,
            onHourChange = { hour = it },
            selectedMinute = reminderMinute(minute),
            onMinuteChange = { minute = it },
            title = "فعال‌سازی آلارم و ساعت هشدار",
            dueDateMillis = dueDateMillis,
            onDueDateChange = { dueDateMillis = it }
        )
    }

    if (showDatePicker) {
        PersianDatePickerDialog(
            initialDateMillis = dueDateMillis,
            onDateSelected = {
                dueDateMillis = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

private fun reminderMinute(m: Int): Int = m

private fun computeObligationTriggerMillis(
    dueDateMillis: Long,
    offsetDays: Int,
    hour: Int,
    minute: Int
): Long {
    return AlarmHelper.computeSmartTriggerMillis(
        dueDateMillis = dueDateMillis,
        offsetDays = offsetDays,
        hour = hour,
        minute = minute
    )
}
