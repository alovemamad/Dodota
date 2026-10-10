package ir.mmd.dodota.ui.screens.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.ui.components.*
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter

private val CalendarCheckGold = Color(0xFFF59E0B)
private val CalendarInstallmentPurple = Color(0xFFA855F7)
private val CalendarDebtRed = ExpenseRed
private val CalendarReceivableGreen = IncomeGreen

private data class CalendarObligationEvent(
    val idKey: String,
    val title: String,
    val subtitle: String,
    val amount: Long,
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val dateMillis: Long,
    val color: Color,
    val typeBadge: String,
    val isCompleted: Boolean
)

@Composable
fun FinancialCalendarScreen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    userPreferences: UserPreferences,
    debts: List<DebtEntity> = emptyList(),
    installmentPlans: List<InstallmentPlanEntity> = emptyList(),
    allInstallments: List<InstallmentEntity> = emptyList(),
    onBack: () -> Unit
) {
    val currentPDate = remember { PersianDateHelper.getCurrentPersianDate() }
    var selectedYear by remember { mutableIntStateOf(currentPDate.year) }
    var selectedMonth by remember { mutableIntStateOf(currentPDate.month) }
    var selectedDay by remember { mutableIntStateOf(currentPDate.day) }
    var showYearMonthPickerDialog by remember { mutableStateOf(false) }

    val daysInMonth = remember(selectedYear, selectedMonth) {
        PersianDateHelper.getDaysInPersianMonth(selectedYear, selectedMonth)
    }

    // Clamp selectedDay if switching to a month/year with fewer days (e.g. Esfand 29)
    LaunchedEffect(daysInMonth) {
        if (selectedDay > daysInMonth) {
            selectedDay = daysInMonth
        }
    }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val planMap = remember(installmentPlans) { installmentPlans.associateBy { it.id } }

    // Build all Obligation Events across all years so we can filter by selectedYear & selectedMonth
    val allObligationEvents = remember(debts, allInstallments, planMap) {
        val events = mutableListOf<CalendarObligationEvent>()

        // 1. Debts, Receivables & Checks
        debts.forEach { debt ->
            val due = debt.dueDateMillis ?: return@forEach
            val pDate = PersianDateHelper.gregorianMillisToPersian(due)
            val isCheck = debt.isCheck || debt.personOrCompanyName.contains("چک") || debt.notes.contains("چک")
            val color = when {
                isCheck -> CalendarCheckGold
                debt.type == DebtType.DEBT_I_OWE -> CalendarDebtRed
                else -> CalendarReceivableGreen
            }
            val badge = when {
                isCheck && debt.type == DebtType.DEBT_I_OWE -> "چک پرداختی"
                isCheck && debt.type == DebtType.RECEIVABLE_OWED_TO_ME -> "چک دریافتی"
                debt.type == DebtType.DEBT_I_OWE -> "بدهی به شخص"
                else -> "طلب از شخص"
            }
            val remaining = (debt.totalAmount - debt.paidAmount).coerceAtLeast(0L)
            events.add(
                CalendarObligationEvent(
                    idKey = "debt_${debt.id}",
                    title = debt.personOrCompanyName,
                    subtitle = if (debt.checkNumber.isNotBlank()) "شماره چک: ${debt.checkNumber}" else debt.notes.ifBlank { badge },
                    amount = if (remaining > 0) remaining else debt.totalAmount,
                    year = pDate.year,
                    month = pDate.month,
                    dayOfMonth = pDate.day,
                    dateMillis = due,
                    color = color,
                    typeBadge = badge,
                    isCompleted = debt.status == DebtStatus.SETTLED
                )
            )
        }

        // 2. Installments
        allInstallments.forEach { inst ->
            val pDate = PersianDateHelper.gregorianMillisToPersian(inst.dueDateMillis)
            val plan = planMap[inst.planId]
            val planTitle = plan?.title ?: "خرید قسطی"
            val totalInst = plan?.numberOfInstallments ?: inst.installmentIndex
            events.add(
                CalendarObligationEvent(
                    idKey = "inst_${inst.id}",
                    title = "$planTitle (قسط ${PersianNumberFormatter.toPersianDigits(inst.installmentIndex)} از ${PersianNumberFormatter.toPersianDigits(totalInst)})",
                    subtitle = plan?.creditorOrCompany?.ifBlank { "سررسید قسط" } ?: "سررسید قسط",
                    amount = inst.amount,
                    year = pDate.year,
                    month = pDate.month,
                    dayOfMonth = pDate.day,
                    dateMillis = inst.dueDateMillis,
                    color = CalendarInstallmentPurple,
                    typeBadge = "قسط",
                    isCompleted = inst.isPaid
                )
            )
        }

        events
    }

    // Filter obligations for the selected year
    val yearObligations = remember(allObligationEvents, selectedYear) {
        allObligationEvents.filter { it.year == selectedYear }
    }

    // Count obligations per month in the selected year (1..12)
    val monthObligationCounts = remember(yearObligations) {
        (1..12).associateWith { m -> yearObligations.count { it.month == m } }
    }

    // Filter obligations for the selected month
    val monthObligations = remember(yearObligations, selectedMonth) {
        yearObligations.filter { it.month == selectedMonth }.sortedBy { it.dayOfMonth }
    }

    // Filter transactions for the selected month
    val monthTransactions = remember(transactions, selectedYear, selectedMonth) {
        val start = PersianDateHelper.getStartOfMonthMillis(selectedYear, selectedMonth)
        val end = PersianDateHelper.getEndOfMonthMillis(selectedYear, selectedMonth)
        transactions.filter { it.dateMillis in start..end }
    }

    val dayObligations = remember(monthObligations, selectedDay) {
        monthObligations.filter { it.dayOfMonth == selectedDay }
    }

    val dayTransactions = remember(transactions, selectedYear, selectedMonth, selectedDay) {
        transactions.filter { tx ->
            val pDate = PersianDateHelper.gregorianMillisToPersian(tx.dateMillis)
            pDate.year == selectedYear && pDate.month == selectedMonth && pDate.day == selectedDay
        }
    }

    val isViewingToday = selectedYear == currentPDate.year &&
        selectedMonth == currentPDate.month &&
        selectedDay == currentPDate.day

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "تقویم مالی (چک، قسط، بدهی و تراکنش)",
                onBackClick = onBack,
                actions = {
                    if (!isViewingToday) {
                        TextButton(
                            onClick = {
                                selectedYear = currentPDate.year
                                selectedMonth = currentPDate.month
                                selectedDay = currentPDate.day
                            }
                        ) {
                            Icon(
                                Icons.Default.Today,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "امروز",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 50.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Dedicated Year Switcher Card + Quick Month Strip
            item {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    isHighlighted = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Year Navigation Row (جابجایی بین سال‌ها)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { selectedYear-- },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "سال قبل",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Quick Year Chips + Center Year Selector Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Previous Year Pill
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                    modifier = Modifier.clickable { selectedYear-- }
                                ) {
                                    Text(
                                        text = PersianNumberFormatter.toPersianDigits(selectedYear - 1),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                // Current Selected Year Badge (Clickable to open full Year/Month picker)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.clickable { showYearMonthPickerDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "سال ${PersianNumberFormatter.toPersianDigits(selectedYear)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "انتخاب سال",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Next Year Pill
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                    modifier = Modifier.clickable { selectedYear++ }
                                ) {
                                    Text(
                                        text = PersianNumberFormatter.toPersianDigits(selectedYear + 1),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { selectedYear++ },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "سال بعد",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Yearly Summary Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ماه‌های سال ${PersianNumberFormatter.toPersianDigits(selectedYear)}:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (yearObligations.isNotEmpty()) {
                                    "${PersianNumberFormatter.toPersianDigits(yearObligations.size)} سررسید در سال ${PersianNumberFormatter.toPersianDigits(selectedYear)}"
                                } else {
                                    "بدون سررسید ثبت‌شده در این سال"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (yearObligations.isNotEmpty()) CalendarCheckGold else MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 12-Month Horizontal Selector for the Selected Year
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(PersianDateHelper.PERSIAN_MONTHS) { index, monthName ->
                                val monthNum = index + 1
                                val isMonthSelected = monthNum == selectedMonth
                                val countInMonth = monthObligationCounts[monthNum] ?: 0
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isMonthSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            isMonthSelected -> MaterialTheme.colorScheme.primary
                                            countInMonth > 0 -> CalendarCheckGold.copy(alpha = 0.6f)
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                        }
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedMonth = monthNum
                                        selectedDay = 1
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = monthName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isMonthSelected || countInMonth > 0) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = if (isMonthSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (countInMonth > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isMonthSelected) Color.White else CalendarCheckGold),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = PersianNumberFormatter.toPersianDigits(countInMonth),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isMonthSelected) MaterialTheme.colorScheme.primary else Color.Black
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Color Legend Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendDotItem(label = "چک", color = CalendarCheckGold)
                    LegendDotItem(label = "قسط", color = CalendarInstallmentPurple)
                    LegendDotItem(label = "بدهی", color = CalendarDebtRed)
                    LegendDotItem(label = "طلب", color = CalendarReceivableGreen)
                    LegendDotItem(label = "تراکنش", color = MaterialTheme.colorScheme.primary)
                }
            }

            // 3. Month Navigation & Calendar Grid
            item {
                GlassCard(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedMonth > 1) {
                                        selectedMonth--
                                    } else {
                                        selectedMonth = 12
                                        selectedYear--
                                    }
                                    selectedDay = 1
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "ماه قبل")
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showYearMonthPickerDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${PersianDateHelper.PERSIAN_MONTHS[selectedMonth - 1]} ${PersianNumberFormatter.toPersianDigits(selectedYear)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.EditCalendar,
                                        contentDescription = "تغییر سال و ماه",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (monthObligations.isNotEmpty()) {
                                    Text(
                                        text = "${PersianNumberFormatter.toPersianDigits(monthObligations.size)} سررسید (چک/قسط/بدهی) در این ماه",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CalendarCheckGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (selectedMonth < 12) {
                                        selectedMonth++
                                    } else {
                                        selectedMonth = 1
                                        selectedYear++
                                    }
                                    selectedDay = 1
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ماه بعد")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Grid of days
                        val columns = 7
                        val rows = (daysInMonth + columns - 1) / columns

                        for (r in 0 until rows) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (c in 0 until columns) {
                                    val dayNum = r * columns + c + 1
                                    if (dayNum <= daysInMonth) {
                                        val isSelected = dayNum == selectedDay
                                        val isTodayCell = selectedYear == currentPDate.year &&
                                            selectedMonth == currentPDate.month &&
                                            dayNum == currentPDate.day
                                        val dayObs = monthObligations.filter { it.dayOfMonth == dayNum }
                                        val hasTx = monthTransactions.any { tx ->
                                            val p = PersianDateHelper.gregorianMillisToPersian(tx.dateMillis)
                                            p.day == dayNum
                                        }
                                        val primaryEventColor = dayObs.firstOrNull()?.color

                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        primaryEventColor != null -> primaryEventColor.copy(alpha = 0.16f)
                                                        isTodayCell -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    width = when {
                                                        primaryEventColor != null && !isSelected -> 1.dp
                                                        isTodayCell && !isSelected -> 1.dp
                                                        else -> 0.dp
                                                    },
                                                    color = when {
                                                        primaryEventColor != null && !isSelected -> primaryEventColor.copy(alpha = 0.55f)
                                                        isTodayCell && !isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                        else -> Color.Transparent
                                                    },
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable { selectedDay = dayNum },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = PersianNumberFormatter.toPersianDigits(dayNum),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected || dayObs.isNotEmpty() || isTodayCell) FontWeight.ExtraBold else FontWeight.Normal,
                                                    color = when {
                                                        isSelected -> Color.White
                                                        primaryEventColor != null -> primaryEventColor
                                                        else -> MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                // Indicator Dots Row for Check / Installment / Debt / Transaction
                                                if (!isSelected && (dayObs.isNotEmpty() || hasTx)) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        dayObs.map { it.color }.distinct().take(3).forEach { dotColor ->
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(5.dp)
                                                                    .clip(CircleShape)
                                                                    .background(dotColor)
                                                            )
                                                        }
                                                        if (hasTx && dayObs.isEmpty()) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.primary)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(42.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Day Header
            item {
                Text(
                    text = "سررسیدها و تراکنش‌های ${PersianNumberFormatter.toPersianDigits(selectedDay)} ${PersianDateHelper.PERSIAN_MONTHS[selectedMonth - 1]} ${PersianNumberFormatter.toPersianDigits(selectedYear)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Selected Day Obligations (Checks, Installments, Debts, Receivables)
            if (dayObligations.isNotEmpty()) {
                items(dayObligations, key = { it.idKey }) { ev ->
                    ObligationCalendarCard(
                        event = ev,
                        currency = userPreferences.currency
                    )
                }
            }

            // Selected Day Transactions
            if (dayTransactions.isNotEmpty()) {
                items(dayTransactions, key = { "tx_${it.id}" }) { tx ->
                    val cat = tx.categoryId?.let { categoryMap[it] }
                    GlassCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconView(
                                iconName = cat?.iconName ?: "payments",
                                colorHex = cat?.colorHex ?: "#10B981",
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.title.ifBlank { cat?.name ?: "تراکنش" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = tx.type.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            val amountColor = when (tx.type) {
                                TransactionType.INCOME -> IncomeGreen
                                TransactionType.EXPENSE -> ExpenseRed
                                TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
                            }
                            Text(
                                text = PersianNumberFormatter.formatCurrency(tx.amount, userPreferences.currency),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = amountColor
                            )
                        }
                    }
                }
            }

            if (dayObligations.isEmpty() && dayTransactions.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "در این روز چک، قسط، بدهی یا تراکنشی ثبت نشده است",
                        description = "می‌توانید از نوار بالای صفحه بین سال‌ها و ماه‌های مختلف جابجا شوید."
                    )
                }
            }

            // All Obligations of Current Month Section
            if (monthObligations.isNotEmpty()) {
                item {
                    DodotaExpandableSection(
                        title = "همه سررسیدهای ${PersianDateHelper.PERSIAN_MONTHS[selectedMonth - 1]} ${PersianNumberFormatter.toPersianDigits(selectedYear)}",
                        subtitle = "${PersianNumberFormatter.toPersianDigits(monthObligations.size)} مورد در این ماه",
                        icon = Icons.Default.EventNote,
                        initiallyExpanded = true
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            monthObligations.forEach { ev ->
                                ObligationCalendarCard(
                                    event = ev,
                                    currency = userPreferences.currency,
                                    showDayBadge = true,
                                    onClick = { selectedDay = ev.dayOfMonth }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Fast Year & Month Jump Dialog
    if (showYearMonthPickerDialog) {
        var tempYear by remember { mutableIntStateOf(selectedYear) }
        var tempMonth by remember { mutableIntStateOf(selectedMonth) }
        val yearList = remember(currentPDate.year) {
            ((currentPDate.year - 15)..(currentPDate.year + 15)).toList()
        }

        AlertDialog(
            onDismissRequest = { showYearMonthPickerDialog = false },
            title = {
                Text(
                    text = "جابجایی سریع بین سال‌ها و ماه‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "انتخاب سال شمسی:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(yearList) { yr ->
                            val isSelectedYr = yr == tempYear
                            val yrEventsCount = allObligationEvents.count { it.year == yr }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelectedYr) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelectedYr) MaterialTheme.colorScheme.primary
                                    else if (yrEventsCount > 0) CalendarCheckGold.copy(alpha = 0.6f)
                                    else Color.Transparent
                                ),
                                modifier = Modifier.clickable { tempYear = yr }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = PersianNumberFormatter.toPersianDigits(yr),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelectedYr) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (yrEventsCount > 0) {
                                        Text(
                                            text = "${PersianNumberFormatter.toPersianDigits(yrEventsCount)} مورد",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelectedYr) Color.White.copy(alpha = 0.9f) else CalendarCheckGold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "انتخاب ماه:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // 3x4 Grid of Months
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0 until 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (col in 0 until 3) {
                                    val mIdx = row * 3 + col
                                    val mNum = mIdx + 1
                                    val mName = PersianDateHelper.PERSIAN_MONTHS[mIdx]
                                    val isSelM = tempMonth == mNum
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelM) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { tempMonth = mNum }
                                    ) {
                                        Text(
                                            text = mName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelM) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelM) Color.White else MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedYear = tempYear
                        selectedMonth = tempMonth
                        selectedDay = 1
                        showYearMonthPickerDialog = false
                    }
                ) {
                    Text("مشاهده تقویم", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showYearMonthPickerDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun ObligationCalendarCard(
    event: CalendarObligationEvent,
    currency: AppCurrency,
    showDayBadge: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        borderColor = event.color.copy(alpha = 0.55f),
        isHighlighted = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(event.color.copy(alpha = 0.18f))
                    .border(1.dp, event.color.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (showDayBadge) {
                    Text(
                        text = PersianNumberFormatter.toPersianDigits(event.dayOfMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = event.color
                    )
                } else {
                    Icon(
                        imageVector = when (event.typeBadge) {
                            "قسط" -> Icons.Default.EventRepeat
                            "چک پرداختی", "چک دریافتی" -> Icons.Default.FactCheck
                            else -> Icons.Default.Handshake
                        },
                        contentDescription = null,
                        tint = event.color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = event.color.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = event.typeBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = event.color,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    if (event.isCompleted) {
                        Text(
                            text = "✓ تسویه شده",
                            style = MaterialTheme.typography.labelSmall,
                            color = IncomeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (event.subtitle.isNotBlank()) {
                    Text(
                        text = event.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = PersianNumberFormatter.formatCurrency(event.amount, currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = event.color
            )
        }
    }
}

@Composable
private fun LegendDotItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
