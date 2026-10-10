package ir.mmd.dodota.ui.screens.debts

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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

// Distinct Iconic Palette for Check, Installment, Personal Debt, and Receivable
private val CheckGoldColor = Color(0xFFF59E0B)          // طلایی درخشان ویژه چک صیادی
private val CheckGoldLight = Color(0xFFFBBF24)
private val InstallmentPurpleColor = Color(0xFFA855F7)  // بنفش نئونی ویژه خرید قسطی و اقساط
private val InstallmentPurpleLight = Color(0xFFC084FC)
private val PersonalDebtRedColor = ExpenseRed           // قرمز یاقوتی ویژه بدهی به شخص
private val PersonalReceivableGreenColor = IncomeGreen  // سبز زمردی ویژه طلب از شخص

enum class ObligationSortMode(val title: String, val icon: ImageVector) {
    BY_TIME("زمان", Icons.Default.Schedule),
    PRIORITY_CHECK("چک", Icons.Default.FactCheck),
    PRIORITY_INSTALLMENT("قسط", Icons.Default.EventRepeat),
    PRIORITY_DEBT("بدهی", Icons.Default.AccountBalanceWallet)
}

enum class ObligationCategoryFilter(val title: String) {
    ALL("همه موارد (چک، قسط، بدهی و طلب)"),
    CHECK_ONLY("فقط چک‌های صیادی و بانکی"),
    INSTALLMENT_ONLY("فقط خریدهای قسطی و اقساط"),
    DEBT_ONLY("فقط بدهی به اشخاص"),
    RECEIVABLE_ONLY("فقط طلب از اشخاص")
}

private sealed class UnifiedObligationItem {
    abstract val sortDueDateMillis: Long
    abstract val isSettled: Boolean

    data class DebtOrCheckItem(
        val debt: DebtEntity,
        val activeReminder: ReminderEntity?
    ) : UnifiedObligationItem() {
        val isCheckItem: Boolean
            get() = debt.isCheck || debt.personOrCompanyName.contains("چک") || debt.notes.contains("چک")
        override val sortDueDateMillis: Long
            get() = debt.dueDateMillis ?: Long.MAX_VALUE
        override val isSettled: Boolean
            get() = debt.status == DebtStatus.SETTLED
    }

    data class InstallmentPlanItem(
        val plan: InstallmentPlanEntity,
        val installments: List<InstallmentEntity>,
        val activeReminder: ReminderEntity?
    ) : UnifiedObligationItem() {
        val nextUnpaidDueMillis: Long?
            get() = installments.filter { !it.isPaid }.minByOrNull { it.dueDateMillis }?.dueDateMillis
        val endDateMillis: Long
            get() = installments.maxByOrNull { it.installmentIndex }?.dueDateMillis
                ?: PersianDateHelper.addMonthsToPersianDate(
                    startYear = PersianDateHelper.fromMillis(plan.startDateMillis).year,
                    startMonth = PersianDateHelper.fromMillis(plan.startDateMillis).month,
                    dayOfMonth = plan.dueDayOfMonth,
                    monthsToAdd = (plan.numberOfInstallments - 1).coerceAtLeast(0)
                )
        override val sortDueDateMillis: Long
            get() = nextUnpaidDueMillis ?: endDateMillis
        override val isSettled: Boolean
            get() = plan.isCompleted || (plan.numberOfInstallments > 0 && plan.paidInstallments >= plan.numberOfInstallments)
    }
}

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
    var selectedCategoryFilter by remember { mutableStateOf(ObligationCategoryFilter.ALL) }
    var selectedSortMode by remember { mutableStateOf(ObligationSortMode.BY_TIME) }
    var showSettledItems by remember { mutableStateOf(true) }

    var showAddDebtDialog by remember { mutableStateOf(false) }
    var initialAddIsCheck by remember { mutableStateOf(false) }
    var showAddInstallmentDialog by remember { mutableStateOf(false) }

    var debtToEdit by remember { mutableStateOf<DebtEntity?>(null) }
    var debtToDelete by remember { mutableStateOf<DebtEntity?>(null) }
    var debtForQuickReminder by remember { mutableStateOf<DebtEntity?>(null) }
    var debtForQuickPayment by remember { mutableStateOf<DebtEntity?>(null) }
    var planToDelete by remember { mutableStateOf<InstallmentPlanEntity?>(null) }
    var planForQuickReminder by remember { mutableStateOf<InstallmentPlanEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Group reminders by target
    val debtReminderMap = remember(activeReminders) {
        activeReminders.filter { it.targetType == ReminderTargetType.DEBT && it.targetId != null }
            .associateBy { it.targetId!! }
    }
    val installmentReminderMap = remember(activeReminders) {
        activeReminders.filter { it.targetType == ReminderTargetType.INSTALLMENT && it.targetId != null }
            .associateBy { it.targetId!! }
    }

    // Group installments by planId
    val installmentsByPlan = remember(allInstallments) {
        allInstallments.groupBy { it.planId }
    }

    // ---------------------------------------------------------
    // FINANCIAL OUTLOOK CALCULATIONS (چشم‌انداز مالی: تا الان چقدر پرداخت شده و چقدر مانده)
    // ---------------------------------------------------------
    val checkDebts = remember(debts) {
        debts.filter { it.isCheck || it.personOrCompanyName.contains("چک") || it.notes.contains("چک") }
    }
    val personalDebts = remember(debts) {
        debts.filter { !(it.isCheck || it.personOrCompanyName.contains("چک") || it.notes.contains("چک")) && it.type == DebtType.DEBT_I_OWE }
    }
    val personalReceivables = remember(debts) {
        debts.filter { !(it.isCheck || it.personOrCompanyName.contains("چک") || it.notes.contains("چک")) && it.type == DebtType.RECEIVABLE_OWED_TO_ME }
    }

    // Checks totals
    val totalChecksAll = checkDebts.sumOf { it.totalAmount }
    val totalChecksPaid = checkDebts.sumOf { it.paidAmount.coerceAtMost(it.totalAmount) }
    val totalChecksRemaining = (totalChecksAll - totalChecksPaid).coerceAtLeast(0L)

    // Installments totals
    val totalInstallmentsAll = installmentPlans.sumOf { plan ->
        val planInsts = installmentsByPlan[plan.id] ?: emptyList()
        if (planInsts.isNotEmpty()) planInsts.sumOf { it.amount }
        else plan.numberOfInstallments * plan.installmentAmount
    }
    val totalInstallmentsPaid = installmentPlans.sumOf { plan ->
        val planInsts = installmentsByPlan[plan.id] ?: emptyList()
        if (planInsts.isNotEmpty()) planInsts.filter { it.isPaid }.sumOf { it.amount }
        else plan.paidInstallments * plan.installmentAmount
    }
    val totalInstallmentsRemaining = (totalInstallmentsAll - totalInstallmentsPaid).coerceAtLeast(0L)

    // Personal Debts totals
    val totalPersonalDebtsAll = personalDebts.sumOf { it.totalAmount }
    val totalPersonalDebtsPaid = personalDebts.sumOf { it.paidAmount.coerceAtMost(it.totalAmount) }
    val totalPersonalDebtsRemaining = (totalPersonalDebtsAll - totalPersonalDebtsPaid).coerceAtLeast(0L)

    // Personal Receivables totals
    val totalReceivablesAll = personalReceivables.sumOf { it.totalAmount }
    val totalReceivablesPaid = personalReceivables.sumOf { it.paidAmount.coerceAtMost(it.totalAmount) }
    val totalReceivablesRemaining = (totalReceivablesAll - totalReceivablesPaid).coerceAtLeast(0L)

    // Grand Financial Outlook (Total Obligations: Checks + Installments + Personal Debts)
    val grandObligationsTotal = totalChecksAll + totalInstallmentsAll + totalPersonalDebtsAll
    val grandPaidSoFar = totalChecksPaid + totalInstallmentsPaid + totalPersonalDebtsPaid
    val grandRemaining = (grandObligationsTotal - grandPaidSoFar).coerceAtLeast(0L)
    val grandProgress = if (grandObligationsTotal > 0L) {
        (grandPaidSoFar.toFloat() / grandObligationsTotal.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Build unified list of Checks, Installments, Debts, and Receivables
    val unifiedItems = remember(
        debts,
        installmentPlans,
        installmentsByPlan,
        debtReminderMap,
        installmentReminderMap,
        selectedCategoryFilter,
        selectedSortMode,
        showSettledItems
    ) {
        val list = mutableListOf<UnifiedObligationItem>()

        debts.forEach { d ->
            val item = UnifiedObligationItem.DebtOrCheckItem(
                debt = d,
                activeReminder = debtReminderMap[d.id]
            )
            val matchesFilter = when (selectedCategoryFilter) {
                ObligationCategoryFilter.ALL -> true
                ObligationCategoryFilter.CHECK_ONLY -> item.isCheckItem
                ObligationCategoryFilter.INSTALLMENT_ONLY -> false
                ObligationCategoryFilter.DEBT_ONLY -> !item.isCheckItem && d.type == DebtType.DEBT_I_OWE
                ObligationCategoryFilter.RECEIVABLE_ONLY -> !item.isCheckItem && d.type == DebtType.RECEIVABLE_OWED_TO_ME
            }
            if (matchesFilter && (showSettledItems || !item.isSettled)) {
                list.add(item)
            }
        }

        installmentPlans.forEach { p ->
            val item = UnifiedObligationItem.InstallmentPlanItem(
                plan = p,
                installments = installmentsByPlan[p.id] ?: emptyList(),
                activeReminder = installmentReminderMap[p.id]
            )
            val matchesFilter = when (selectedCategoryFilter) {
                ObligationCategoryFilter.ALL, ObligationCategoryFilter.INSTALLMENT_ONLY -> true
                else -> false
            }
            if (matchesFilter && (showSettledItems || !item.isSettled)) {
                list.add(item)
            }
        }

        list.sortedWith(
            compareBy<UnifiedObligationItem> { it.isSettled }
                .thenBy { item ->
                    when (selectedSortMode) {
                        ObligationSortMode.BY_TIME -> 0
                        ObligationSortMode.PRIORITY_CHECK -> when {
                            item is UnifiedObligationItem.DebtOrCheckItem && item.isCheckItem -> 0
                            item is UnifiedObligationItem.InstallmentPlanItem -> 1
                            else -> 2
                        }
                        ObligationSortMode.PRIORITY_INSTALLMENT -> when {
                            item is UnifiedObligationItem.InstallmentPlanItem -> 0
                            item is UnifiedObligationItem.DebtOrCheckItem && item.isCheckItem -> 1
                            else -> 2
                        }
                        ObligationSortMode.PRIORITY_DEBT -> when {
                            item is UnifiedObligationItem.DebtOrCheckItem && !item.isCheckItem -> 0
                            item is UnifiedObligationItem.DebtOrCheckItem && item.isCheckItem -> 1
                            else -> 2
                        }
                    }
                }
                .thenBy { it.sortDueDateMillis }
        )
    }

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "چشم‌انداز تعهدات، چک و اقساط",
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. ICONIC FINANCIAL OUTLOOK HERO CARD (چشم‌انداز مالی: تا الان چقدر پرداخت کردم و چقدر مانده)
            item {
                FinancialOutlookHeroCard(
                    grandTotal = grandObligationsTotal,
                    grandPaidSoFar = grandPaidSoFar,
                    grandRemaining = grandRemaining,
                    grandProgress = grandProgress,
                    checksTotal = totalChecksAll,
                    checksPaid = totalChecksPaid,
                    checksRemaining = totalChecksRemaining,
                    installmentsTotal = totalInstallmentsAll,
                    installmentsPaid = totalInstallmentsPaid,
                    installmentsRemaining = totalInstallmentsRemaining,
                    debtsTotal = totalPersonalDebtsAll,
                    debtsPaid = totalPersonalDebtsPaid,
                    debtsRemaining = totalPersonalDebtsRemaining,
                    receivablesTotal = totalReceivablesAll,
                    receivablesPaid = totalReceivablesPaid,
                    receivablesRemaining = totalReceivablesRemaining,
                    selectedFilter = selectedCategoryFilter,
                    currency = userPreferences.currency,
                    onSelectFilter = { filter ->
                        selectedCategoryFilter = if (selectedCategoryFilter == filter) {
                            ObligationCategoryFilter.ALL
                        } else {
                            filter
                        }
                    }
                )
            }

            // 2. Iconic Quick Creation Action Cards (ثبت بدهی/طلب | ثبت چک صیادی | ثبت خرید قسطی)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconicCreateButton(
                        title = "ثبت بدهی / طلب",
                        subtitle = "شخصی + آلارم",
                        icon = Icons.Default.Handshake,
                        accentColor = PersonalDebtRedColor,
                        onClick = {
                            initialAddIsCheck = false
                            showAddDebtDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconicCreateButton(
                        title = "ثبت چک صیادی",
                        subtitle = "تایم و سررسید",
                        icon = Icons.Default.FactCheck,
                        accentColor = CheckGoldColor,
                        onClick = {
                            initialAddIsCheck = true
                            showAddDebtDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconicCreateButton(
                        title = "خرید قسطی",
                        subtitle = "تیک هر قسط",
                        icon = Icons.Default.EventRepeat,
                        accentColor = InstallmentPurpleColor,
                        onClick = { showAddInstallmentDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Priority & Sorting Bar (مرتب کردن و اولویت‌بندی بر اساس: چک | قسط | بدهی | زمان)
            item {
                GlassCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesomeMosaic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "اولویت‌بندی نمایش بر اساس:",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${PersianNumberFormatter.toPersianDigits(unifiedItems.size)} مورد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ObligationSortMode.values().forEach { mode ->
                                val isSelected = selectedSortMode == mode
                                val chipColor = when (mode) {
                                    ObligationSortMode.PRIORITY_CHECK -> CheckGoldColor
                                    ObligationSortMode.PRIORITY_INSTALLMENT -> InstallmentPurpleColor
                                    ObligationSortMode.PRIORITY_DEBT -> PersonalDebtRedColor
                                    ObligationSortMode.BY_TIME -> MaterialTheme.colorScheme.primary
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) chipColor.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) chipColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedSortMode = mode }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = mode.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) chipColor else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = mode.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) chipColor else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Collapsible Category & Status Filter
            item {
                DodotaExpandableSelector(
                    label = "فیلتر دسته‌بندی و وضعیت تسویه",
                    selectedTitle = selectedCategoryFilter.title,
                    selectedSubtitle = if (showSettledItems) "نمایش موارد فعال و تسویه‌شده" else "فقط موارد در جریان",
                    leadingIcon = {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                ) { closeSelector ->
                    ObligationCategoryFilter.values().forEach { filter ->
                        val isSelected = selectedCategoryFilter == filter
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCategoryFilter = filter
                                    closeSelector()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = filter.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "نمایش موارد تسویه‌شده و پایان‌یافته",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = showSettledItems,
                            onCheckedChange = { showSettledItems = it }
                        )
                    }
                }
            }

            // 5. Unified Obligations List
            if (unifiedItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "هنوز موردی در این بخش ثبت نشده است",
                        description = "با دکمه‌های بالای صفحه می‌توانید خرید قسطی، چک صیادی یا بدهی/طلب جدید ثبت کنید و چشم‌انداز گرافیکی پرداخت‌های خود را ببینید.",
                        icon = Icons.Outlined.DonutLarge
                    )
                }
            } else {
                items(
                    items = unifiedItems,
                    key = { item ->
                        when (item) {
                            is UnifiedObligationItem.DebtOrCheckItem -> "debt_${item.debt.id}"
                            is UnifiedObligationItem.InstallmentPlanItem -> "plan_${item.plan.id}"
                        }
                    }
                ) { item ->
                    when (item) {
                        is UnifiedObligationItem.DebtOrCheckItem -> {
                            DebtOrCheckCard(
                                item = item,
                                currency = userPreferences.currency,
                                onCardClick = { onNavigateToDetail(item.debt.id) },
                                onRecordPayment = { debtForQuickPayment = item.debt },
                                onConfigureReminder = { debtForQuickReminder = item.debt },
                                onEdit = { debtToEdit = item.debt },
                                onDelete = { debtToDelete = item.debt }
                            )
                        }

                        is UnifiedObligationItem.InstallmentPlanItem -> {
                            InstallmentPurchaseCard(
                                item = item,
                                currency = userPreferences.currency,
                                onToggleInstallment = { installment ->
                                    coroutineScope.launch {
                                        if (installment.isPaid) {
                                            repository.unpayInstallment(item.plan, installment)
                                            Toast.makeText(
                                                context,
                                                "قسط ${PersianNumberFormatter.toPersianDigits(installment.installmentIndex)} به حالت پرداخت‌نشده برگشت",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            repository.payInstallment(
                                                item.plan,
                                                installment,
                                                item.plan.accountId ?: accounts.firstOrNull()?.id
                                            )
                                            Toast.makeText(
                                                context,
                                                "قسط ${PersianNumberFormatter.toPersianDigits(installment.installmentIndex)} پرداخت شد ✓",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onConfigureReminder = { planForQuickReminder = item.plan },
                                onDelete = { planToDelete = item.plan },
                                onNavigateToDetail = {
                                    onNavigateToInstallmentDetail?.invoke(item.plan.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // DIALOG 0: Quick Record Payment for Debt / Check / Receivable
    // ---------------------------------------------------------
    debtForQuickPayment?.let { debt ->
        val remaining = (debt.totalAmount - debt.paidAmount).coerceAtLeast(0L)
        QuickRecordDebtPaymentDialog(
            debt = debt,
            remainingAmount = remaining,
            currency = userPreferences.currency,
            accounts = accounts,
            onDismiss = { debtForQuickPayment = null },
            onConfirm = { payAmount, selectedAccountId, note ->
                coroutineScope.launch {
                    repository.recordDebtPayment(
                        debt = debt,
                        amount = payAmount,
                        accountId = selectedAccountId,
                        notes = note
                    )
                    Toast.makeText(context, "پرداخت ثبت شد و چشم‌انداز مالی بروزرسانی گردید ✓", Toast.LENGTH_SHORT).show()
                    debtForQuickPayment = null
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DIALOG 1: Add Debt / Receivable / Check Dialog
    // ---------------------------------------------------------
    if (showAddDebtDialog) {
        AddOrEditDebtCheckDialog(
            existingDebt = null,
            existingReminder = null,
            initialIsCheck = initialAddIsCheck,
            currency = userPreferences.currency,
            onDismiss = { showAddDebtDialog = false },
            onSave = { person, totalAmount, initialPaidAmount, dueDateMillis, notes, type, isCheck, checkNumber, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    val effectiveDueDate = if (isCheck || enableReminder) {
                        dueDateMillis ?: System.currentTimeMillis()
                    } else {
                        dueDateMillis
                    }
                    val safePaid = initialPaidAmount.coerceIn(0L, totalAmount)
                    val initialStatus = if (safePaid >= totalAmount && totalAmount > 0L) DebtStatus.SETTLED else DebtStatus.ACTIVE

                    val debtId = repository.addDebt(
                        DebtEntity(
                            type = type,
                            personOrCompanyName = person,
                            totalAmount = totalAmount,
                            paidAmount = safePaid,
                            dueDateMillis = effectiveDueDate,
                            notes = notes,
                            isCheck = isCheck,
                            checkNumber = checkNumber,
                            status = initialStatus
                        )
                    )

                    if (effectiveDueDate != null && enableReminder && initialStatus != DebtStatus.SETTLED) {
                        val triggerMillis = computeObligationTriggerMillis(effectiveDueDate, offsetDays, reminderHour, reminderMinute)
                        val typePrefix = when {
                            isCheck && type == DebtType.DEBT_I_OWE -> "سررسید چک پرداختی به $person"
                            isCheck && type == DebtType.RECEIVABLE_OWED_TO_ME -> "سررسید چک دریافتی از $person"
                            type == DebtType.DEBT_I_OWE -> "سررسید بدهی به $person"
                            else -> "سررسید طلب از $person"
                        }
                        val checkInfo = if (isCheck && checkNumber.isNotBlank()) " • چک: $checkNumber" else ""
                        val remDesc = "مانده: ${PersianNumberFormatter.formatCurrency(totalAmount - safePaid, userPreferences.currency)}$checkInfo"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.DEBT,
                            targetId = debtId,
                            title = typePrefix,
                            description = remDesc,
                            dueDateTimeMillis = triggerMillis,
                            offsetDays = offsetDays,
                            enabled = true
                        )
                        if (remId != null) {
                            AlarmHelper.scheduleExactAlarm(context, remId, typePrefix, remDesc, triggerMillis)
                        }
                    }

                    Toast.makeText(
                        context,
                        if (isCheck) "چک همراه با تنظیمات یادآور ثبت شد" else "مورد با موفقیت ثبت شد",
                        Toast.LENGTH_SHORT
                    ).show()
                    showAddDebtDialog = false
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DIALOG 2: Edit Debt / Receivable / Check Dialog
    // ---------------------------------------------------------
    debtToEdit?.let { debt ->
        AddOrEditDebtCheckDialog(
            existingDebt = debt,
            existingReminder = debtReminderMap[debt.id],
            initialIsCheck = debt.isCheck,
            currency = userPreferences.currency,
            onDismiss = { debtToEdit = null },
            onSave = { person, totalAmount, paidAmount, dueDateMillis, notes, type, isCheck, checkNumber, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    val safePaid = paidAmount.coerceIn(0L, totalAmount)
                    val status = if (safePaid >= totalAmount && totalAmount > 0L) DebtStatus.SETTLED else DebtStatus.ACTIVE
                    val effectiveDueDate = if (isCheck || enableReminder) {
                        dueDateMillis ?: debt.dueDateMillis ?: System.currentTimeMillis()
                    } else {
                        dueDateMillis
                    }

                    repository.updateDebt(
                        debt.copy(
                            personOrCompanyName = person,
                            totalAmount = totalAmount,
                            paidAmount = safePaid,
                            dueDateMillis = effectiveDueDate,
                            notes = notes,
                            type = type,
                            isCheck = isCheck,
                            checkNumber = checkNumber,
                            status = status
                        )
                    )

                    if (effectiveDueDate != null && enableReminder && status != DebtStatus.SETTLED) {
                        val triggerMillis = computeObligationTriggerMillis(effectiveDueDate, offsetDays, reminderHour, reminderMinute)
                        val typePrefix = when {
                            isCheck && type == DebtType.DEBT_I_OWE -> "سررسید چک پرداختی به $person"
                            isCheck && type == DebtType.RECEIVABLE_OWED_TO_ME -> "سررسید چک دریافتی از $person"
                            type == DebtType.DEBT_I_OWE -> "سررسید بدهی به $person"
                            else -> "سررسید طلب از $person"
                        }
                        val checkInfo = if (isCheck && checkNumber.isNotBlank()) " • چک: $checkNumber" else ""
                        val remDesc = "مانده: ${PersianNumberFormatter.formatCurrency(totalAmount - safePaid, userPreferences.currency)}$checkInfo"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.DEBT,
                            targetId = debt.id,
                            title = typePrefix,
                            description = remDesc,
                            dueDateTimeMillis = triggerMillis,
                            offsetDays = offsetDays,
                            enabled = true
                        )
                        if (remId != null) {
                            AlarmHelper.scheduleExactAlarm(context, remId, typePrefix, remDesc, triggerMillis)
                        }
                    } else {
                        repository.removeTargetReminders(ReminderTargetType.DEBT, debt.id)
                    }

                    Toast.makeText(context, "اطلاعات و یادآور بروزرسانی شد", Toast.LENGTH_SHORT).show()
                    debtToEdit = null
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DIALOG 3: Quick Reminder / Alarm Config for Existing Debt/Check
    // ---------------------------------------------------------
    debtForQuickReminder?.let { debt ->
        QuickObligationReminderDialog(
            title = "تنظیم یادآور و آلارم: ${debt.personOrCompanyName}",
            initialDueDateMillis = debt.dueDateMillis ?: System.currentTimeMillis(),
            existingReminder = debtReminderMap[debt.id],
            onDismiss = { debtForQuickReminder = null },
            onSave = { dueMillis, enabled, offsetDays, hour, minute ->
                coroutineScope.launch {
                    repository.updateDebt(debt.copy(dueDateMillis = dueMillis))
                    if (enabled) {
                        val triggerMillis = computeObligationTriggerMillis(dueMillis, offsetDays, hour, minute)
                        val remTitle = if (debt.isCheck) "سررسید چک: ${debt.personOrCompanyName}"
                        else if (debt.type == DebtType.DEBT_I_OWE) "سررسید بدهی به ${debt.personOrCompanyName}"
                        else "سررسید طلب از ${debt.personOrCompanyName}"
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
                        Toast.makeText(context, "آلارم سررسید فعال شد ⏰", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.removeTargetReminders(ReminderTargetType.DEBT, debt.id)
                        Toast.makeText(context, "یادآور غیرفعال شد", Toast.LENGTH_SHORT).show()
                    }
                    debtForQuickReminder = null
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DIALOG 4: Quick Reminder / Alarm Config for Installment Plan
    // ---------------------------------------------------------
    planForQuickReminder?.let { plan ->
        val nextDue = installmentsByPlan[plan.id]?.filter { !it.isPaid }?.minByOrNull { it.dueDateMillis }?.dueDateMillis
            ?: plan.startDateMillis
        QuickObligationReminderDialog(
            title = "تنظیم یادآور قسط: ${plan.title}",
            initialDueDateMillis = nextDue,
            existingReminder = installmentReminderMap[plan.id],
            onDismiss = { planForQuickReminder = null },
            onSave = { dueMillis, enabled, offsetDays, hour, minute ->
                coroutineScope.launch {
                    if (enabled) {
                        val triggerMillis = computeObligationTriggerMillis(dueMillis, offsetDays, hour, minute)
                        val remTitle = "سررسید قسط: ${plan.title}"
                        val remDesc = "مبلغ قسط: ${PersianNumberFormatter.formatCurrency(plan.installmentAmount, userPreferences.currency)}"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.INSTALLMENT,
                            targetId = plan.id,
                            title = remTitle,
                            description = remDesc,
                            dueDateTimeMillis = triggerMillis,
                            offsetDays = offsetDays,
                            enabled = true
                        )
                        if (remId != null) {
                            AlarmHelper.scheduleExactAlarm(context, remId, remTitle, remDesc, triggerMillis)
                        }
                        Toast.makeText(context, "آلارم قسط تنظیم شد ⏰", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.removeTargetReminders(ReminderTargetType.INSTALLMENT, plan.id)
                        Toast.makeText(context, "یادآور قسط غیرفعال شد", Toast.LENGTH_SHORT).show()
                    }
                    planForQuickReminder = null
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DIALOG 5: Add Installment Purchase Dialog
    // ---------------------------------------------------------
    if (showAddInstallmentDialog) {
        AddInstallmentPurchaseDialog(
            currency = userPreferences.currency,
            accounts = accounts,
            onDismiss = { showAddInstallmentDialog = false },
            onSave = { title, seller, installmentAmount, totalCount, initialPaidCount, startDateMillis, dueDay, accountId, autoExpense, enableReminder, offsetDays, hour, minute ->
                coroutineScope.launch {
                    val safePaid = initialPaidCount.coerceIn(0, totalCount)
                    val totalAmount = installmentAmount * totalCount
                    val planId = repository.createInstallmentPlan(
                        plan = InstallmentPlanEntity(
                            title = title,
                            creditorOrCompany = seller,
                            totalAmount = totalAmount,
                            numberOfInstallments = totalCount,
                            installmentAmount = installmentAmount,
                            paidInstallments = safePaid,
                            startDateMillis = startDateMillis,
                            dueDayOfMonth = dueDay,
                            accountId = accountId,
                            autoCreateExpense = autoExpense,
                            isCompleted = safePaid >= totalCount
                        ),
                        startDateMillis = startDateMillis,
                        initialPaidCount = safePaid
                    )

                    if (enableReminder && safePaid < totalCount) {
                        val baseP = PersianDateHelper.fromMillis(startDateMillis)
                        val nextUnpaidMillis = PersianDateHelper.addMonthsToPersianDate(
                            startYear = baseP.year,
                            startMonth = baseP.month,
                            dayOfMonth = dueDay,
                            monthsToAdd = safePaid
                        )
                        val triggerMillis = computeObligationTriggerMillis(nextUnpaidMillis, offsetDays, hour, minute)
                        val remTitle = "سررسید قسط: $title"
                        val remDesc = "قسط شماره ${PersianNumberFormatter.toPersianDigits(safePaid + 1)} به مبلغ ${PersianNumberFormatter.formatCurrency(installmentAmount, userPreferences.currency)}"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.INSTALLMENT,
                            targetId = planId,
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

                    Toast.makeText(context, "خرید قسطی با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                    showAddInstallmentDialog = false
                }
            }
        )
    }

    // ---------------------------------------------------------
    // DELETE CONFIRMATIONS
    // ---------------------------------------------------------
    debtToDelete?.let { debt ->
        AlertDialog(
            onDismissRequest = { debtToDelete = null },
            title = { Text(if (debt.isCheck) "حذف چک" else "حذف بدهی/طلب") },
            text = { Text("آیا از حذف «${debt.personOrCompanyName}» به مبلغ ${PersianNumberFormatter.formatCurrency(debt.totalAmount, userPreferences.currency)} اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteDebt(debt)
                            Toast.makeText(context, "مورد با موفقیت حذف شد", Toast.LENGTH_SHORT).show()
                            debtToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { debtToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("حذف خرید قسطی") },
            text = { Text("آیا از حذف خرید قسطی «${plan.title}» و تمام اقساط آن اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteInstallmentPlan(plan)
                            Toast.makeText(context, "خرید قسطی حذف شد", Toast.LENGTH_SHORT).show()
                            planToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

// =========================================================================
// ICONIC GRAPHICAL COMPONENTS: FINANCIAL OUTLOOK & CIRCULAR GAUGES
// =========================================================================

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

            // Subtle ambient inner circle glow
            drawCircle(
                color = primaryColor.copy(alpha = 0.08f),
                radius = (this.size.minDimension / 2f) - strokePx
            )

            // Track ring
            drawArc(
                color = primaryColor.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active progress arc
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

/**
 * Hero Dashboard Card: Shows Total Paid So Far vs Remaining Obligations, Circular Gauge,
 * Multi-Segment Graphical Bar, and 4 Iconic Mini-Cards for Check, Installment, Debt, Receivable.
 */
@Composable
private fun FinancialOutlookHeroCard(
    grandTotal: Long,
    grandPaidSoFar: Long,
    grandRemaining: Long,
    grandProgress: Float,
    checksTotal: Long,
    checksPaid: Long,
    checksRemaining: Long,
    installmentsTotal: Long,
    installmentsPaid: Long,
    installmentsRemaining: Long,
    debtsTotal: Long,
    debtsPaid: Long,
    debtsRemaining: Long,
    receivablesTotal: Long,
    receivablesPaid: Long,
    receivablesRemaining: Long,
    selectedFilter: ObligationCategoryFilter,
    currency: AppCurrency,
    onSelectFilter: (ObligationCategoryFilter) -> Unit
) {
    val percentPaid = (grandProgress * 100).toInt()

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    InstallmentPurpleColor.copy(alpha = 0.7f),
                    CheckGoldColor.copy(alpha = 0.5f),
                    PersonalDebtRedColor.copy(alpha = 0.6f)
                )
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
                            Color(0xFF1F122B),
                            Color(0xFF15131E),
                            Color(0xFF111118)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            // Top Title Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(InstallmentPurpleColor, PersonalDebtRedColor)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Insights,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "چشم‌انداز هوشمند تعهدات و اقساط",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "گزارش گرافیکی مبالغ پرداخت‌شده تا الان و مانده تعهدات",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Gauge + Paid So Far vs Remaining Outlook
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressGauge(
                    progress = grandProgress,
                    primaryColor = InstallmentPurpleColor,
                    secondaryColor = CheckGoldColor,
                    size = 92.dp,
                    strokeWidth = 9.dp,
                    centerLabel = "${PersianNumberFormatter.toPersianDigits(percentPaid)}٪",
                    subLabel = "پرداخت‌شده"
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Paid So Far Pill (تا الان پرداخت کردم)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = IncomeGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "تا الان پرداخت شده:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            Text(
                                text = PersianNumberFormatter.formatCurrency(grandPaidSoFar, currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = IncomeGreen
                            )
                        }
                    }

                    // Remaining Pill (مانده کل تعهدات)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PersonalDebtRedColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, PersonalDebtRedColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.PendingActions,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B81),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "مانده قابل پرداخت:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            Text(
                                text = PersianNumberFormatter.formatCurrency(grandRemaining, currency),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFF6B81)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Iconic Category Breakdown Mini-Cards with Mini Gauges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlookMiniCategoryCard(
                    title = "چک‌ها",
                    icon = Icons.Default.FactCheck,
                    paidAmount = checksPaid,
                    remainingAmount = checksRemaining,
                    totalAmount = checksTotal,
                    color = CheckGoldColor,
                    currency = currency,
                    isSelected = selectedFilter == ObligationCategoryFilter.CHECK_ONLY,
                    onClick = { onSelectFilter(ObligationCategoryFilter.CHECK_ONLY) },
                    modifier = Modifier.weight(1f)
                )
                OutlookMiniCategoryCard(
                    title = "اقساط",
                    icon = Icons.Default.EventRepeat,
                    paidAmount = installmentsPaid,
                    remainingAmount = installmentsRemaining,
                    totalAmount = installmentsTotal,
                    color = InstallmentPurpleColor,
                    currency = currency,
                    isSelected = selectedFilter == ObligationCategoryFilter.INSTALLMENT_ONLY,
                    onClick = { onSelectFilter(ObligationCategoryFilter.INSTALLMENT_ONLY) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlookMiniCategoryCard(
                    title = "بدهی شخصی",
                    icon = Icons.Default.PersonRemove,
                    paidAmount = debtsPaid,
                    remainingAmount = debtsRemaining,
                    totalAmount = debtsTotal,
                    color = PersonalDebtRedColor,
                    currency = currency,
                    isSelected = selectedFilter == ObligationCategoryFilter.DEBT_ONLY,
                    onClick = { onSelectFilter(ObligationCategoryFilter.DEBT_ONLY) },
                    modifier = Modifier.weight(1f)
                )
                OutlookMiniCategoryCard(
                    title = "طلب از دیگران",
                    icon = Icons.Default.PersonAdd,
                    paidAmount = receivablesPaid,
                    remainingAmount = receivablesRemaining,
                    totalAmount = receivablesTotal,
                    color = PersonalReceivableGreenColor,
                    currency = currency,
                    isSelected = selectedFilter == ObligationCategoryFilter.RECEIVABLE_ONLY,
                    onClick = { onSelectFilter(ObligationCategoryFilter.RECEIVABLE_ONLY) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OutlookMiniCategoryCard(
    title: String,
    icon: ImageVector,
    paidAmount: Long,
    remainingAmount: Long,
    totalAmount: Long,
    color: Color,
    currency: AppCurrency,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalAmount > 0L) (paidAmount.toFloat() / totalAmount.toFloat()).coerceIn(0f, 1f) else 0f
    val percent = (progress * 100).toInt()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) color.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else color.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                }
                Text(
                    text = "${PersianNumberFormatter.toPersianDigits(percent)}٪",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (percent >= 100 && totalAmount > 0) IncomeGreen else Color.White
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Paid vs Remaining mini texts
            Text(
                text = "پرداخت‌شده: ${PersianNumberFormatter.formatNumberWithCommas(paidAmount)}",
                style = MaterialTheme.typography.labelSmall,
                color = IncomeGreen,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "مانده: ${PersianNumberFormatter.formatCurrency(remainingAmount, currency)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (percent >= 100 && totalAmount > 0) IncomeGreen else color,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
private fun IconicCreateButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.55f)),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.65f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (accentColor == CheckGoldColor) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
        }
    }
}

/**
 * Iconic Card for Personal Debt (Red), Personal Receivable (Green), or Bank Check (Gold/Amber)
 * Shows Circular Progress Gauge + "تا الان پرداخت شده" vs "مانده" + Quick Payment & Alarm buttons
 */
@Composable
private fun DebtOrCheckCard(
    item: UnifiedObligationItem.DebtOrCheckItem,
    currency: AppCurrency,
    onCardClick: () -> Unit,
    onRecordPayment: () -> Unit,
    onConfigureReminder: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val debt = item.debt
    val isCheck = item.isCheckItem
    val accentColor = when {
        isCheck -> CheckGoldColor
        debt.type == DebtType.DEBT_I_OWE -> PersonalDebtRedColor
        else -> PersonalReceivableGreenColor
    }
    val typeBadgeTitle = when {
        isCheck && debt.type == DebtType.DEBT_I_OWE -> "چک صیادی پرداختی"
        isCheck && debt.type == DebtType.RECEIVABLE_OWED_TO_ME -> "چک صیادی دریافتی"
        debt.type == DebtType.DEBT_I_OWE -> "بدهی به شخص"
        else -> "طلب از شخص"
    }
    val iconVector = when {
        isCheck -> Icons.Default.FactCheck
        debt.type == DebtType.DEBT_I_OWE -> Icons.Default.PersonRemove
        else -> Icons.Default.PersonAdd
    }

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
            // Top Row: Iconic Circular Gauge + Title + Actions
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
                        centerLabel = "${PersianNumberFormatter.toPersianDigits(percentPaid)}٪"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = typeBadgeTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = accentColor
                                    )
                                }
                            }
                            if (debt.checkNumber.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "#${PersianNumberFormatter.toPersianDigits(debt.checkNumber)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CheckGoldColor,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = debt.personOrCompanyName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onConfigureReminder,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (item.activeReminder != null) accentColor.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(
                            imageVector = if (item.activeReminder != null) Icons.Default.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = "تنظیم یادآور و آلارم",
                            tint = if (item.activeReminder != null) accentColor else MaterialTheme.colorScheme.outline,
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

            // Graphical Financial Outlook Box: "تا الان پرداخت شده" vs "مانده"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Paid So Far Box
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
                                text = if (debt.type == DebtType.RECEIVABLE_OWED_TO_ME) "تا الان دریافت شده" else "تا الان پرداخت شده",
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

                // Remaining Box
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
                                text = "مانده از کل (${PersianNumberFormatter.formatNumberWithCommas(debt.totalAmount)})",
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

            // Glowing Progress Bar
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

            // Bottom Row: Due Date + Quick Payment Button + Alarm Status
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
                    val rem = item.activeReminder
                    if (rem != null) {
                        val cal = Calendar.getInstance().apply { timeInMillis = rem.dueDateTimeMillis }
                        val h = PersianNumberFormatter.toPersianDigits(String.format("%02d", cal.get(Calendar.HOUR_OF_DAY)))
                        val m = PersianNumberFormatter.toPersianDigits(String.format("%02d", cal.get(Calendar.MINUTE)))
                        Text(
                            text = "⏰ آلارم فعال: ساعت $h:$m",
                            style = MaterialTheme.typography.labelSmall,
                            color = IncomeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

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
                                    text = if (isCheck) "پاس شدن چک" else "ثبت پرداخت",
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
                            text = if (item.activeReminder != null) "ویرایش آلارم" else "+ آلارم",
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

/**
 * Iconic Installment Purchase Card (Purple) with:
 * - Circular Gauge Ring showing % Paid
 * - Visual Financial Outlook Box ("تا الان پرداخت کردم" vs "مانده اقساط")
 * - Segmented Visual Installment Bar (each installment is a glowing capsule segment)
 * - Interactive Checkboxes/Tickets for ticking each installment
 * - Start Date ➔ End Date Roadmap
 */
@Composable
private fun InstallmentPurchaseCard(
    item: UnifiedObligationItem.InstallmentPlanItem,
    currency: AppCurrency,
    onToggleInstallment: (InstallmentEntity) -> Unit,
    onConfigureReminder: () -> Unit,
    onDelete: () -> Unit,
    onNavigateToDetail: () -> Unit
) {
    val plan = item.plan
    val installments = item.installments
    val paidCount = if (installments.isNotEmpty()) installments.count { it.isPaid } else plan.paidInstallments
    val totalCount = plan.numberOfInstallments.coerceAtLeast(1)
    val progress = (paidCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    val percentPaid = (progress * 100).toInt()

    val paidSoFarAmount = if (installments.isNotEmpty()) {
        installments.filter { it.isPaid }.sumOf { it.amount }
    } else {
        paidCount * plan.installmentAmount
    }
    val totalPlanAmount = if (installments.isNotEmpty()) {
        installments.sumOf { it.amount }
    } else {
        totalCount * plan.installmentAmount
    }
    val remainingAmount = (totalPlanAmount - paidSoFarAmount).coerceAtLeast(0L)

    GlassCard(
        shape = RoundedCornerShape(26.dp),
        borderColor = InstallmentPurpleColor.copy(alpha = 0.7f),
        isHighlighted = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = onNavigateToDetail
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Header with Iconic Circular Gauge + Title + Actions
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
                        primaryColor = InstallmentPurpleColor,
                        secondaryColor = InstallmentPurpleLight,
                        size = 62.dp,
                        strokeWidth = 6.5.dp,
                        centerLabel = "${PersianNumberFormatter.toPersianDigits(percentPaid)}٪"
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = InstallmentPurpleColor.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, InstallmentPurpleColor.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.EventRepeat,
                                        contentDescription = null,
                                        tint = InstallmentPurpleColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "خرید اقساطی • ${PersianNumberFormatter.toPersianDigits(paidCount)}/${PersianNumberFormatter.toPersianDigits(totalCount)} قسط",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = InstallmentPurpleColor
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "هر قسط: ${PersianNumberFormatter.formatCurrency(plan.installmentAmount, currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InstallmentPurpleLight,
                            fontWeight = FontWeight.Bold
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
                                if (item.activeReminder != null) InstallmentPurpleColor.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                    ) {
                        Icon(
                            imageVector = if (item.activeReminder != null) Icons.Default.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = "تنظیم آلارم قسط",
                            tint = if (item.activeReminder != null) InstallmentPurpleColor else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "حذف اقساط",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Graphical Financial Outlook Row ("تا الان پرداخت شده" vs "مانده اقساط")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = IncomeGreen.copy(alpha = 0.13f),
                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = IncomeGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تا الان پرداخت کردم",
                                style = MaterialTheme.typography.labelSmall,
                                color = IncomeGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = PersianNumberFormatter.formatCurrency(paidSoFarAmount, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = IncomeGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${PersianNumberFormatter.toPersianDigits(paidCount)} قسط پاس‌شده",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = InstallmentPurpleColor.copy(alpha = 0.13f),
                    border = BorderStroke(1.dp, InstallmentPurpleColor.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.HourglassBottom,
                                contentDescription = null,
                                tint = InstallmentPurpleLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "مانده اقساط",
                                style = MaterialTheme.typography.labelSmall,
                                color = InstallmentPurpleLight,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = PersianNumberFormatter.formatCurrency(remainingAmount, currency),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = InstallmentPurpleLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${PersianNumberFormatter.toPersianDigits((totalCount - paidCount).coerceAtLeast(0))} قسط باقی‌مانده",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Start Date ➔ End Date Visual Timeline Roadmap
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            tint = IncomeGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "شروع: ${PersianDateHelper.formatToPersianDate(plan.startDateMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        Icons.Default.TrendingFlat,
                        contentDescription = null,
                        tint = InstallmentPurpleColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FlagCircle,
                            contentDescription = null,
                            tint = InstallmentPurpleColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "اتمام: ${PersianDateHelper.formatToPersianDate(item.endDateMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = InstallmentPurpleLight
                        )
                    }
                }
            }

            // 4. Segmented Visual Bar + Interactive Tick Cards for Each Installment
            if (installments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Capsule Bar (Visual representation of all installments)
                val maxSegments = installments.size.coerceAtMost(24)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    installments.take(maxSegments).forEach { inst ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (inst.isPaid) IncomeGreen
                                    else InstallmentPurpleColor.copy(alpha = 0.25f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "کارت‌های تیک زدن اقساط (لمس برای ثبت پرداخت):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${PersianNumberFormatter.toPersianDigits(percentPaid)}٪ پرداخت شده",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (percentPaid >= 100) IncomeGreen else InstallmentPurpleLight
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(installments, key = { it.id }) { inst ->
                        val isPaid = inst.isPaid
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isPaid) IncomeGreen.copy(alpha = 0.18f)
                            else InstallmentPurpleColor.copy(alpha = 0.1f),
                            border = BorderStroke(
                                width = 1.4.dp,
                                color = if (isPaid) IncomeGreen else InstallmentPurpleColor.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .width(118.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onToggleInstallment(inst) }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isPaid) IncomeGreen else InstallmentPurpleColor.copy(alpha = 0.25f),
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (isPaid) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = PersianNumberFormatter.toPersianDigits(inst.installmentIndex),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = InstallmentPurpleLight
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = if (isPaid) "پاس شد ✓" else "تیک بزنید",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPaid) IncomeGreen else InstallmentPurpleLight
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "قسط ${PersianNumberFormatter.toPersianDigits(inst.installmentIndex)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isPaid) IncomeGreen else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (isPaid) TextDecoration.LineThrough else TextDecoration.None
                                )

                                Text(
                                    text = PersianDateHelper.formatToPersianDate(inst.dueDateMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick Dialog to Record a Partial or Full Payment for a Debt / Check / Receivable
 * so the user can immediately see "تا الان چقدر پرداخت کردم" update graphically!
 */
@Composable
private fun QuickRecordDebtPaymentDialog(
    debt: DebtEntity,
    remainingAmount: Long,
    currency: AppCurrency,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, accountId: Long?, note: String) -> Unit
) {
    var amountText by remember { mutableStateOf(remainingAmount.toString()) }
    var selectedAccountId by remember { mutableStateOf<Long?>(accounts.firstOrNull()?.id) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ثبت پرداخت: ${debt.personOrCompanyName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    label = "مبلغ پرداختی جدید"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = { amountText = (remainingAmount / 2).coerceAtLeast(1L).toString() },
                        label = { Text("نصف مانده (۵۰٪)") }
                    )
                    AssistChip(
                        onClick = { amountText = remainingAmount.toString() },
                        label = { Text("کل مانده (تسویه کامل)") }
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح پرداخت (اختیاری)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = PersianNumberFormatter.parseAmount(amountText)
                    if (amt > 0L) {
                        onConfirm(amt, selectedAccountId, note.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen)
            ) {
                Text("ثبت پرداخت ✓", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

/**
 * Dialog for Adding or Editing a Personal Debt, Receivable, or Check
 * Includes "مبلغ پرداخت‌شده از قبل (اختیاری)" so user can immediately see their paid-so-far outlook!
 */
@Composable
private fun AddOrEditDebtCheckDialog(
    existingDebt: DebtEntity?,
    existingReminder: ReminderEntity?,
    initialIsCheck: Boolean,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (
        person: String,
        totalAmount: Long,
        paidAmount: Long,
        dueDateMillis: Long?,
        notes: String,
        type: DebtType,
        isCheck: Boolean,
        checkNumber: String,
        enableReminder: Boolean,
        offsetDays: Int,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Unit
) {
    var selectedType by remember { mutableStateOf(existingDebt?.type ?: DebtType.DEBT_I_OWE) }
    var isCheck by remember { mutableStateOf(existingDebt?.isCheck ?: initialIsCheck) }
    var checkNumber by remember { mutableStateOf(existingDebt?.checkNumber ?: "") }
    var person by remember { mutableStateOf(existingDebt?.personOrCompanyName ?: "") }
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
    var enableReminder by remember { mutableStateOf(existingReminder != null || existingDebt == null || isCheck) }
    var offsetDays by remember { mutableIntStateOf(existingReminder?.reminderOffsetDays ?: 0) }
    var reminderHour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var reminderMinute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    existingDebt != null && isCheck -> "ویرایش چک و آلارم"
                    existingDebt != null -> "ویرایش بدهی / طلب و یادآور"
                    isCheck -> "ثبت چک جدید و آلارم سررسید"
                    else -> "ثبت بدهی / طلب جدید"
                },
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == DebtType.DEBT_I_OWE,
                        onClick = { selectedType = DebtType.DEBT_I_OWE },
                        label = { Text(if (isCheck) "چک پرداختی من" else "بدهی من به شخص") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == DebtType.RECEIVABLE_OWED_TO_ME,
                        onClick = { selectedType = DebtType.RECEIVABLE_OWED_TO_ME },
                        label = { Text(if (isCheck) "چک دریافتی از شخص" else "طلب من از شخص") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Check Option Toggle Card (گزینه چک)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCheck) CheckGoldColor.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(
                        width = if (isCheck) 1.5.dp else 1.dp,
                        color = if (isCheck) CheckGoldColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.FactCheck,
                                    contentDescription = null,
                                    tint = if (isCheck) CheckGoldColor else MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "گزینه چک (چک صیادی / بانکی)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isCheck) CheckGoldColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "رنگ اختصاصی طلایی + تنظیم آلارم و سررسید چک",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Switch(
                                checked = isCheck,
                                onCheckedChange = { checked ->
                                    isCheck = checked
                                    if (checked) {
                                        enableReminder = true
                                        if (dueDateMillis == null) {
                                            dueDateMillis = System.currentTimeMillis()
                                        }
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(visible = isCheck) {
                            OutlinedTextField(
                                value = checkNumber,
                                onValueChange = { checkNumber = it },
                                label = { Text("شماره صیادی یا نام بانک چک (اختیاری)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    label = { Text(if (isCheck) "در وجه / صادرکننده چک" else "نام شخص یا شرکت") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                PersianAmountInput(
                    value = amountText,
                    onValueChange = { amountText = it },
                    currency = currency,
                    label = if (isCheck) "مبلغ کل چک" else "مبلغ کل بدهی / طلب"
                )

                PersianAmountInput(
                    value = paidAmountText,
                    onValueChange = { paidAmountText = it },
                    currency = currency,
                    label = "مبلغ پرداخت‌شده تا الان (اختیاری)"
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isCheck) CheckGoldColor else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = if (isCheck) CheckGoldColor else MaterialTheme.colorScheme.primary,
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
                        color = if (isCheck) CheckGoldColor else MaterialTheme.colorScheme.primary
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
                    title = if (isCheck) "آلارم و تایم یادآوری چک" else "یادآور و ساعت هشدار بدهی/طلب"
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("توضیحات (اختیاری)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = PersianNumberFormatter.parseAmount(amountText)
                    val paidAmt = PersianNumberFormatter.parseAmount(paidAmountText)
                    if (person.isNotBlank() && amount > 0L) {
                        onSave(
                            person.trim(),
                            amount,
                            paidAmt,
                            dueDateMillis,
                            notes.trim(),
                            selectedType,
                            isCheck,
                            checkNumber.trim(),
                            enableReminder,
                            offsetDays,
                            reminderHour,
                            reminderMinute
                        )
                    }
                }
            ) {
                Text(if (existingDebt == null) "ثبت و ذخیره" else "بروزرسانی")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )

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

/**
 * Dialog for Adding an Installment Purchase (خرید قسطی / وام)
 * Includes Live Graphical Preview of "تا الان پرداخت شده", "مانده", and "تاریخ اتمام اقساط"
 */
@Composable
private fun AddInstallmentPurchaseDialog(
    currency: AppCurrency,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        seller: String,
        installmentAmount: Long,
        totalCount: Int,
        initialPaidCount: Int,
        startDateMillis: Long,
        dueDay: Int,
        accountId: Long?,
        autoExpense: Boolean,
        enableReminder: Boolean,
        offsetDays: Int,
        hour: Int,
        minute: Int
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var seller by remember { mutableStateOf("") }
    var installmentAmountText by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("12") }
    var alreadyPaidText by remember { mutableStateOf("0") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showStartDatePicker by remember { mutableStateOf(false) }

    val startPDate = remember(startDateMillis) { PersianDateHelper.fromMillis(startDateMillis) }
    var dueDayText by remember(startPDate.day) { mutableStateOf(startPDate.day.toString()) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var autoExpense by remember { mutableStateOf(true) }

    val defaultInstCal = remember {
        Calendar.getInstance().apply { add(Calendar.MINUTE, 2) }
    }
    var enableReminder by remember { mutableStateOf(true) }
    var offsetDays by remember { mutableIntStateOf(0) }
    var reminderHour by remember { mutableIntStateOf(defaultInstCal.get(Calendar.HOUR_OF_DAY)) }
    var reminderMinute by remember { mutableIntStateOf(defaultInstCal.get(Calendar.MINUTE)) }

    val parsedInstallmentAmount = PersianNumberFormatter.parseAmount(installmentAmountText)
    val parsedCount = PersianNumberFormatter.toEnglishDigits(countText).toIntOrNull()?.coerceAtLeast(1) ?: 1
    val parsedPaid = (PersianNumberFormatter.toEnglishDigits(alreadyPaidText).toIntOrNull() ?: 0).coerceIn(0, parsedCount)
    val parsedDueDay = (PersianNumberFormatter.toEnglishDigits(dueDayText).toIntOrNull() ?: startPDate.day).coerceIn(1, 31)
    val previewProgress = (parsedPaid.toFloat() / parsedCount.toFloat()).coerceIn(0f, 1f)

    val endDateMillis = remember(startDateMillis, parsedCount, parsedDueDay) {
        PersianDateHelper.addMonthsToPersianDate(
            startYear = startPDate.year,
            startMonth = startPDate.month,
            dayOfMonth = parsedDueDay,
            monthsToAdd = (parsedCount - 1).coerceAtLeast(0)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ثبت خرید قسطی / اقساط جدید",
                fontWeight = FontWeight.ExtraBold,
                color = InstallmentPurpleColor
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان خرید قسطی (مثلاً: موبایل، لپ‌تاپ، وام)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = seller,
                    onValueChange = { seller = it },
                    label = { Text("فروشگاه / بانک / طرف حساب (اختیاری)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                PersianAmountInput(
                    value = installmentAmountText,
                    onValueChange = { installmentAmountText = it },
                    currency = currency,
                    label = "مبلغ هر قسط"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = PersianNumberFormatter.toPersianDigits(countText),
                        onValueChange = {
                            val en = PersianNumberFormatter.toEnglishDigits(it)
                            if (en.all { c -> c.isDigit() }) countText = en
                        },
                        label = { Text("تعداد کل اقساط") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = PersianNumberFormatter.toPersianDigits(alreadyPaidText),
                        onValueChange = {
                            val en = PersianNumberFormatter.toEnglishDigits(it)
                            if (en.all { c -> c.isDigit() }) alreadyPaidText = en
                        },
                        label = { Text("پرداخت‌شده از قبل") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, InstallmentPurpleColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = InstallmentPurpleColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تاریخ شروع پرداخت اقساط: ${PersianDateHelper.formatToPersianDate(startDateMillis)}",
                        color = InstallmentPurpleColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Live Graphical Outlook Preview inside Dialog
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = InstallmentPurpleColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, InstallmentPurpleColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressGauge(
                            progress = previewProgress,
                            primaryColor = InstallmentPurpleColor,
                            size = 54.dp,
                            strokeWidth = 6.dp,
                            centerLabel = "${PersianNumberFormatter.toPersianDigits((previewProgress * 100).toInt())}٪"
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "اتمام اقساط: ${PersianDateHelper.formatToPersianDate(endDateMillis)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = InstallmentPurpleLight
                            )
                            if (parsedInstallmentAmount > 0L) {
                                Text(
                                    text = "تا الان پرداخت‌شده: ${PersianNumberFormatter.formatCurrency(parsedInstallmentAmount * parsedPaid, currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IncomeGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "مانده: ${PersianNumberFormatter.formatCurrency(parsedInstallmentAmount * (parsedCount - parsedPaid), currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                ReminderConfigSection(
                    isEnabled = enableReminder,
                    onEnabledChange = { enableReminder = it },
                    offsetDays = offsetDays,
                    onOffsetDaysChange = { offsetDays = it },
                    selectedHour = reminderHour,
                    onHourChange = { reminderHour = it },
                    selectedMinute = reminderMinute,
                    onMinuteChange = { reminderMinute = it },
                    title = "یادآور و ساعت آلارم سررسید قسط"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && parsedInstallmentAmount > 0L && parsedCount > 0) {
                        onSave(
                            title.trim(),
                            seller.trim(),
                            parsedInstallmentAmount,
                            parsedCount,
                            parsedPaid,
                            startDateMillis,
                            parsedDueDay,
                            selectedAccountId,
                            autoExpense,
                            enableReminder,
                            offsetDays,
                            reminderHour,
                            reminderMinute
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = InstallmentPurpleColor)
            ) {
                Text("ثبت خرید قسطی", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )

    if (showStartDatePicker) {
        PersianDatePickerDialog(
            initialDateMillis = startDateMillis,
            onDateSelected = {
                startDateMillis = it
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    selectedMinute = minute,
                    onMinuteChange = { minute = it },
                    title = "فعال‌سازی آلارم و ساعت هشدار"
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(dueDateMillis, enabled, offsetDays, hour, minute) }) {
                Text("ذخیره آلارم")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )

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
