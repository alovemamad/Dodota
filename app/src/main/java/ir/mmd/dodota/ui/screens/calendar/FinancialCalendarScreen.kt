package ir.mmd.dodota.ui.screens.calendar

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val yearsList = remember(currentPDate.year) {
        (currentPDate.year - 20..currentPDate.year + 20).toList()
    }
    val yearListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (yearsList.indexOf(selectedYear) - 2).coerceAtLeast(0)
    )

    LaunchedEffect(selectedYear) {
        val idx = yearsList.indexOf(selectedYear)
        if (idx >= 0) {
            yearListState.animateScrollToItem((idx - 2).coerceAtLeast(0))
        }
    }

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
    val allObligationEvents = remember(debts, allInstallments, planMap, userPreferences.currency) {
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
                debt.type == DebtType.DEBT_I_OWE -> "بدهی"
                else -> "طلب"
            }
            val remaining = (debt.totalAmount - debt.paidAmount).coerceAtLeast(0L)
            events.add(
                CalendarObligationEvent(
                    idKey = "debt_${debt.id}",
                    title = debt.personOrCompanyName,
                    subtitle = if (debt.checkNumber.isNotBlank()) "شماره چک: ${PersianNumberFormatter.formatDigits(debt.checkNumber, userPreferences.currency)}" else debt.notes.ifBlank { badge },
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
            val planTitle = plan?.title ?: "اقساط"
            val totalInst = plan?.numberOfInstallments ?: inst.installmentIndex
            events.add(
                CalendarObligationEvent(
                    idKey = "inst_${inst.id}",
                    title = "$planTitle (قسط ${PersianNumberFormatter.formatDigits(inst.installmentIndex, userPreferences.currency)} از ${PersianNumberFormatter.formatDigits(totalInst, userPreferences.currency)})",
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
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // Item 16 & 19: Clean title "تقویم مالی" without "(چک، قسط...)" and without "امروز" in topBar
            DodotaTopBar(
                title = "تقویم مالی",
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 50.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Dedicated Year Switcher Card + Quick Month Strip (Item 17 & Item 21)
            item {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    isHighlighted = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        var isYearPickerOpen by remember { mutableStateOf(false) }
                        var yearCardDragOffset by remember { mutableFloatStateOf(0f) }

                        // Year Navigation Row با قابلیت لمس روی سال برای باز شدن فهرست سال‌ها و همچنین سوایپ
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(selectedYear) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { yearCardDragOffset = 0f },
                                        onDragEnd = {
                                            if (yearCardDragOffset > 50f) {
                                                if (selectedYear > yearsList.first()) selectedYear--
                                            } else if (yearCardDragOffset < -50f) {
                                                if (selectedYear < yearsList.last()) selectedYear++
                                            }
                                            yearCardDragOffset = 0f
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            yearCardDragOffset += dragAmount
                                        }
                                    )
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { if (selectedYear > yearsList.first()) selectedYear-- },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "سال قبل",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Center Year Display - با زدن روی آن فهرست سال‌ها باز می‌شود
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isYearPickerOpen) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                border = BorderStroke(
                                    if (isYearPickerOpen) 2.dp else 1.5.dp,
                                    MaterialTheme.colorScheme.primary
                                ),
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
                                        modifier = Modifier.size(20.dp)
                                    )
                                    AnimatedContent(
                                        targetState = selectedYear,
                                        transitionSpec = {
                                            if (targetState > initialState) {
                                                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                                            } else {
                                                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                                            }
                                        },
                                        label = "selected_year_anim"
                                    ) { targetYear ->
                                        Text(
                                            text = PersianNumberFormatter.formatDigits(targetYear, userPreferences.currency),
                                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "باز کردن فهرست سال‌ها",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .rotate(if (isYearPickerOpen) 180f else 0f)
                                    )
                                }
                            }

                            if (selectedYear != currentPDate.year) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier.clickable { selectedYear = currentPDate.year }
                                ) {
                                    Text(
                                        text = "امسال",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            FilledTonalIconButton(
                                onClick = { if (selectedYear < yearsList.last()) selectedYear++ },
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

                        // باز شدن گرید انتخاب سال‌ها با انیمیشن
                        AnimatedVisibility(
                            visible = isYearPickerOpen,
                            enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
                            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "انتخاب سال تقویم:",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            IconButton(
                                                onClick = { isYearPickerOpen = false },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "بستن",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        val gridState = rememberLazyGridState(
                                            initialFirstVisibleItemIndex = (yearsList.indexOf(selectedYear) - 4).coerceAtLeast(0)
                                        )

                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(4),
                                            state = gridState,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 200.dp)
                                        ) {
                                            items(yearsList, key = { it }) { year ->
                                                val isSelected = year == selectedYear
                                                val isCurrentYear = year == currentPDate.year
                                                val hasObligations = allObligationEvents.any { it.year == year }

                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else if (isCurrentYear) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                    border = BorderStroke(
                                                        if (isSelected) 1.5.dp else if (isCurrentYear) 1.2.dp else 1.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else if (isCurrentYear) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                                    ),
                                                    modifier = Modifier.clickable {
                                                        selectedYear = year
                                                        isYearPickerOpen = false
                                                    }
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 10.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Text(
                                                                text = PersianNumberFormatter.formatDigits(year, userPreferences.currency),
                                                                style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            if (isCurrentYear && !isSelected) {
                                                                Text(
                                                                    text = "امسال",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                            if (hasObligations && !isSelected) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .padding(top = 2.dp)
                                                                        .size(5.dp)
                                                                        .clip(CircleShape)
                                                                        .background(CalendarCheckGold)
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
                        }

                        if (!isYearPickerOpen) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isYearPickerOpen = true }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "برای باز شدن سال‌ها روی سال لمس کنید",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Yearly Summary Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ماه‌های سال ${PersianNumberFormatter.formatDigits(selectedYear, userPreferences.currency)}:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (yearObligations.isNotEmpty()) {
                                    "${PersianNumberFormatter.formatDigits(yearObligations.size, userPreferences.currency)} سررسید در سال ${PersianNumberFormatter.formatDigits(selectedYear, userPreferences.currency)}"
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
                                                    text = PersianNumberFormatter.formatDigits(countInMonth, userPreferences.currency),
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

            // 3. Calendar Grid Card با قابلیت سوایپ بین ماه‌ها و سال‌ها
            item {
                var gridDragOffset by remember { mutableFloatStateOf(0f) }
                GlassCard(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(selectedMonth, selectedYear) {
                            detectHorizontalDragGestures(
                                onDragStart = { gridDragOffset = 0f },
                                onDragEnd = {
                                    if (gridDragOffset > 50f) {
                                        // کشیدن به راست -> ماه قبل (یا سال قبل)
                                        if (selectedMonth > 1) {
                                            selectedMonth--
                                        } else if (selectedYear > yearsList.first()) {
                                            selectedYear--
                                            selectedMonth = 12
                                        }
                                    } else if (gridDragOffset < -50f) {
                                        // کشیدن به چپ -> ماه بعد (یا سال بعد)
                                        if (selectedMonth < 12) {
                                            selectedMonth++
                                        } else if (selectedYear < yearsList.last()) {
                                            selectedYear++
                                            selectedMonth = 1
                                        }
                                    }
                                    gridDragOffset = 0f
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    gridDragOffset += dragAmount
                                }
                            )
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header without duplicate picker/selection option (Item 21), larger Year (Item 17), and clean obligation count without parentheses (Item 18)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "${PersianDateHelper.PERSIAN_MONTHS[selectedMonth - 1]} ${PersianNumberFormatter.formatDigits(selectedYear, userPreferences.currency)}",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (monthObligations.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${PersianNumberFormatter.formatDigits(monthObligations.size, userPreferences.currency)} سررسید در این ماه",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CalendarCheckGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Grid of days
                        val columns = 7
                        val rows = (daysInMonth + columns - 1) / columns

                        for (r in 0 until rows) {
                            val isLastRow = r == rows - 1
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isLastRow) {
                                    for (c in 0 until columns) {
                                        val dayNum = r * columns + c + 1
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CalendarDayCell(
                                                dayNum = dayNum,
                                                isSelected = dayNum == selectedDay,
                                                isTodayCell = selectedYear == currentPDate.year &&
                                                    selectedMonth == currentPDate.month &&
                                                    dayNum == currentPDate.day,
                                                dayObs = monthObligations.filter { it.dayOfMonth == dayNum },
                                                hasTx = monthTransactions.any { tx ->
                                                    val p = PersianDateHelper.gregorianMillisToPersian(tx.dateMillis)
                                                    p.day == dayNum
                                                },
                                                currency = userPreferences.currency,
                                                onSelectDay = { selectedDay = dayNum }
                                            )
                                        }
                                    }
                                } else {
                                    // Item 19: Last row has days 29, 30 (and 31), and opposite 30/31 on its left side is the "امروز" button/text
                                    val daysInLastRow = daysInMonth - r * columns
                                    for (c in 0 until daysInLastRow) {
                                        val dayNum = r * columns + c + 1
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CalendarDayCell(
                                                dayNum = dayNum,
                                                isSelected = dayNum == selectedDay,
                                                isTodayCell = selectedYear == currentPDate.year &&
                                                    selectedMonth == currentPDate.month &&
                                                    dayNum == currentPDate.day,
                                                dayObs = monthObligations.filter { it.dayOfMonth == dayNum },
                                                hasTx = monthTransactions.any { tx ->
                                                    val p = PersianDateHelper.gregorianMillisToPersian(tx.dateMillis)
                                                    p.day == dayNum
                                                },
                                                currency = userPreferences.currency,
                                                onSelectDay = { selectedDay = dayNum }
                                            )
                                        }
                                    }

                                    val remainingCols = (columns - daysInLastRow).coerceAtLeast(1)
                                    Box(
                                        modifier = Modifier
                                            .weight(remainingCols.toFloat())
                                            .height(42.dp)
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isViewingToday) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            } else {
                                                MaterialTheme.colorScheme.primary
                                            },
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier
                                                .clickable {
                                                    selectedYear = currentPDate.year
                                                    selectedMonth = currentPDate.month
                                                    selectedDay = currentPDate.day
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Today,
                                                    contentDescription = "امروز",
                                                    tint = if (isViewingToday) MaterialTheme.colorScheme.primary else Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "امروز",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = if (isViewingToday) MaterialTheme.colorScheme.primary else Color.White
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

            // Selected Day Header
            item {
                Text(
                    text = "سررسیدها و تراکنش‌های ${PersianNumberFormatter.formatDigits(selectedDay, userPreferences.currency)} ${PersianDateHelper.PERSIAN_MONTHS[selectedMonth - 1]} ${PersianNumberFormatter.formatDigits(selectedYear, userPreferences.currency)}",
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

            // Item 20: Custom smaller-font message when nothing is registered for the selected day
            if (dayObligations.isEmpty() && dayTransactions.isEmpty()) {
                item {
                    GlassCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "برای امروز چیزی ثبت نشده برو عشق کن❤️🔥",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // All Obligations of Current Month Section
            if (monthObligations.isNotEmpty()) {
                item {
                    DodotaExpandableSection(
                        title = "همه سررسیدهای ${PersianDateHelper.PERSIAN_MONTHS[selectedMonth - 1]} ${PersianNumberFormatter.formatDigits(selectedYear, userPreferences.currency)}",
                        subtitle = "${PersianNumberFormatter.formatDigits(monthObligations.size, userPreferences.currency)} مورد در این ماه",
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
}

@Composable
private fun CalendarDayCell(
    dayNum: Int,
    isSelected: Boolean,
    isTodayCell: Boolean,
    dayObs: List<CalendarObligationEvent>,
    hasTx: Boolean,
    currency: AppCurrency,
    onSelectDay: () -> Unit
) {
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
            .clickable { onSelectDay() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = PersianNumberFormatter.formatDigits(dayNum, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || dayObs.isNotEmpty() || isTodayCell) FontWeight.ExtraBold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    primaryEventColor != null -> primaryEventColor
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
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
                        text = PersianNumberFormatter.formatDigits(event.dayOfMonth, currency),
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
