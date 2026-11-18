package ir.mmd.dodota.ui.screens.installments

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.model.InstallmentEntity
import ir.mmd.dodota.data.model.InstallmentPlanEntity
import ir.mmd.dodota.data.model.ReminderEntity
import ir.mmd.dodota.data.model.ReminderTargetType
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.*
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.util.AlarmHelper
import ir.mmd.dodota.util.DodotaPrintBuilders
import ir.mmd.dodota.util.DodotaPrintHelper
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch
import java.util.Calendar

private val InstallmentScreenPurple = Color(0xFFA855F7)
private val InstallmentScreenPurpleLight = Color(0xFFC084FC)

@Composable
fun InstallmentsScreen(
    plans: List<InstallmentPlanEntity>,
    userPreferences: UserPreferences,
    repository: DodotaRepository,
    accounts: List<AccountEntity>,
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var planToEdit by remember { mutableStateOf<InstallmentPlanEntity?>(null) }
    var planToDelete by remember { mutableStateOf<InstallmentPlanEntity?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: اقساط جاری, 1: بایگانی
    var currentInstallmentsSubMode by remember { mutableIntStateOf(0) } // 0: پرداخت‌نشده ماه مربوطه, 1: اقساط ماه انتخاب‌شده
    var showPrintDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val allInstallmentsFlow = remember { repository.allInstallments }
    val allInstallments by allInstallmentsFlow.collectAsState(initial = emptyList())

    val allRemindersFlow = remember { repository.allReminders }
    val allReminders by allRemindersFlow.collectAsState(initial = emptyList())
    val installmentRemindersMap = remember(allReminders) {
        allReminders.filter { it.targetType == ReminderTargetType.INSTALLMENT && it.targetId != null }
            .associateBy { it.targetId!! }
    }

    val currentPersianDate = remember { PersianDateHelper.getCurrentPersianDate() }
    var selectedYear by remember { mutableIntStateOf(currentPersianDate.year) }
    var selectedMonth by remember { mutableIntStateOf(currentPersianDate.month) }

    val isCurrentMonthSelected = selectedYear == currentPersianDate.year && selectedMonth == currentPersianDate.month
    val selectedMonthName = remember(selectedMonth) { PersianDateHelper.getMonthName(selectedMonth) }

    val startOfMonthMillis = remember(selectedYear, selectedMonth) {
        PersianDateHelper.getStartOfMonthMillis(selectedYear, selectedMonth)
    }
    val endOfMonthMillis = remember(selectedYear, selectedMonth) {
        PersianDateHelper.getEndOfMonthMillis(selectedYear, selectedMonth)
    }

    val plansMap = remember(plans) {
        plans.associateBy { it.id }
    }
    val activePlansMap = remember(plans) {
        plans.filter { !it.isCompleted }.associateBy { it.id }
    }

    val planIdsWithInstallments = remember(allInstallments) {
        allInstallments.map { it.planId }.toSet()
    }

    val selectedMonthInstallments = remember(allInstallments, plansMap, startOfMonthMillis, endOfMonthMillis) {
        allInstallments.filter { it.planId in plansMap && it.dueDateMillis in startOfMonthMillis..endOfMonthMillis }
    }
    val selectedMonthOrderedInstallments = remember(selectedMonthInstallments) {
        selectedMonthInstallments.sortedWith(
            compareBy<InstallmentEntity> { it.isPaid }.thenBy { it.dueDateMillis }
        )
    }

    // Fallback monthly installment calculation ONLY for plans that don't have individual InstallmentEntity rows yet
    val fallbackPlansForSelectedMonth = remember(plans, planIdsWithInstallments, selectedYear, selectedMonth) {
        plans.filter { !it.isCompleted && it.id !in planIdsWithInstallments }.filter { plan ->
            val startP = PersianDateHelper.fromMillis(plan.startDateMillis)
            val monthDiff = (selectedYear - startP.year) * 12 + (selectedMonth - startP.month)
            monthDiff in 0 until plan.numberOfInstallments
        }
    }

    val selectedMonthTotalAmount = selectedMonthInstallments.sumOf { it.amount } +
        fallbackPlansForSelectedMonth.sumOf { it.installmentAmount }

    val selectedMonthPaidAmount = selectedMonthInstallments.filter { it.isPaid }.sumOf { it.amount }
    val selectedMonthRemainingAmount = (selectedMonthTotalAmount - selectedMonthPaidAmount).coerceAtLeast(0L)
    val selectedMonthTotalCount = selectedMonthInstallments.size + fallbackPlansForSelectedMonth.size
    val overdueCount = remember(selectedMonthInstallments) {
        val now = System.currentTimeMillis()
        selectedMonthInstallments.count { !it.isPaid && it.dueDateMillis < now }
    }

    // Current Persian Month start millis (for detecting past overdue installments)
    val currentPersianMonthStartMillis = remember(currentPersianDate) {
        PersianDateHelper.getStartOfMonthMillis(currentPersianDate.year, currentPersianDate.month)
    }

    // Unpaid installments due strictly in the SELECTED Persian month (selectedYear, selectedMonth)
    val selectedMonthUnpaidInstallments = remember(allInstallments, activePlansMap, startOfMonthMillis, endOfMonthMillis) {
        allInstallments.filter { inst ->
            inst.planId in activePlansMap &&
            !inst.isPaid &&
            inst.dueDateMillis in startOfMonthMillis..endOfMonthMillis
        }.sortedBy { it.dueDateMillis }
    }

    // Paid installments due strictly in the SELECTED Persian month
    val selectedMonthPaidInstallments = remember(allInstallments, plansMap, startOfMonthMillis, endOfMonthMillis) {
        allInstallments.filter { inst ->
            inst.planId in plansMap &&
            inst.isPaid &&
            inst.dueDateMillis in startOfMonthMillis..endOfMonthMillis
        }.sortedBy { it.dueDateMillis }
    }

    // Past overdue installments from months BEFORE the current real Persian month (shown only when viewing current month)
    val pastOverdueInstallments = remember(allInstallments, activePlansMap, currentPersianMonthStartMillis) {
        allInstallments.filter { inst ->
            inst.planId in activePlansMap &&
            !inst.isPaid &&
            inst.dueDateMillis < currentPersianMonthStartMillis
        }.sortedBy { it.dueDateMillis }
    }

    // Counts of installments per month for the selectedYear to show small badges on the month dropdown items
    val monthInstallmentCounts = remember(allInstallments, plans, planIdsWithInstallments, plansMap, selectedYear) {
        val counts = IntArray(13)
        allInstallments.forEach { inst ->
            if (inst.planId in plansMap) {
                val p = PersianDateHelper.fromMillis(inst.dueDateMillis)
                if (p.year == selectedYear && p.month in 1..12) {
                    counts[p.month]++
                }
            }
        }
        plans.filter { !it.isCompleted && it.id !in planIdsWithInstallments }.forEach { plan ->
            val startP = PersianDateHelper.fromMillis(plan.startDateMillis)
            for (m in 1..12) {
                val diff = (selectedYear - startP.year) * 12 + (m - startP.month)
                if (diff in 0 until plan.numberOfInstallments) {
                    counts[m]++
                }
            }
        }
        counts
    }

    val totalAllPlansAmount = remember(plans, allInstallments) {
        val byPlan = allInstallments.groupBy { it.planId }
        plans.sumOf { p ->
            val items = byPlan[p.id]
            if (!items.isNullOrEmpty()) items.sumOf { it.amount }
            else p.numberOfInstallments * p.installmentAmount
        }
    }
    val totalPaidSoFarAmount = remember(plans, allInstallments) {
        val byPlan = allInstallments.groupBy { it.planId }
        plans.sumOf { p ->
            val items = byPlan[p.id]
            if (!items.isNullOrEmpty()) items.filter { it.isPaid }.sumOf { it.amount }
            else p.paidInstallments.coerceAtMost(p.numberOfInstallments) * p.installmentAmount
        }
    }
    val totalRemainingAmount = (totalAllPlansAmount - totalPaidSoFarAmount).coerceAtLeast(0L)
    val overallProgress = if (totalAllPlansAmount > 0L) (totalPaidSoFarAmount.toFloat() / totalAllPlansAmount.toFloat()).coerceIn(0f, 1f) else 0f
    val overallPercent = (overallProgress * 100).toInt()

    val activePlans = remember(plans) {
        plans.filter { !it.isCompleted && it.paidInstallments < it.numberOfInstallments }
    }
    val archivedPlans = remember(plans) {
        plans.filter { it.isCompleted || it.paidInstallments >= it.numberOfInstallments }
    }
    val displayedPlans = if (selectedTab == 0) activePlans else archivedPlans

    val accentColor = MaterialTheme.colorScheme.primary
    val accentLight = MaterialTheme.colorScheme.secondary

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                title = "اقساط",
                onBackClick = onBack,
                actions = {
                    DodotaPrintIconButton(
                        onClick = { showPrintDialog = true },
                        testTag = "installments_print_button"
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = accentColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن وام/قسط")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Unified Graphical Hero & Status Card (ترکیب مجموع کل اقساط و وضعیت در یک بخش مرتب، جمع‌وجور و گرافیکی)
            item {
                val animOverall by animateFloatAsState(
                    targetValue = overallProgress,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    label = "inst_overall"
                )

                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    borderColor = accentColor.copy(alpha = 0.5f),
                    isHighlighted = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.16f),
                                        accentLight.copy(alpha = 0.05f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                                )
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // بخش بالایی: اطلاعات اقساط ماه انتخابی همراه با کلید کشویی کوچک انتخاب ماه، در کنار چارت دایره‌ای
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "مجموع اقساط",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // کلید کشویی کوچک و شیک انتخاب ماه (Dropdown)
                                    var isMonthDropdownOpen by remember { mutableStateOf(false) }
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = accentColor.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                                            modifier = Modifier.clickable { isMonthDropdownOpen = true }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = "$selectedMonthName ${PersianNumberFormatter.formatDigits(selectedYear)}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = accentColor
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = "انتخاب ماه",
                                                    tint = accentColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = isMonthDropdownOpen,
                                            onDismissRequest = { isMonthDropdownOpen = false },
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surface)
                                                .heightIn(max = 380.dp)
                                        ) {
                                            // انتخابگر سال در بالای منو برای جابجایی بین سال‌ها
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { selectedYear-- },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ChevronRight,
                                                        contentDescription = "سال قبل",
                                                        tint = accentColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "سال ${PersianNumberFormatter.formatDigits(selectedYear)}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = accentColor
                                                )
                                                IconButton(
                                                    onClick = { selectedYear++ },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ChevronLeft,
                                                        contentDescription = "سال بعد",
                                                        tint = accentColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                            // دکمه پرش به ماه جاری در صورت انتخاب ماه دیگر
                                            if (!isCurrentMonthSelected) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "بازگشت به ماه جاری (${PersianDateHelper.getMonthName(currentPersianDate.month)} ${PersianNumberFormatter.formatDigits(currentPersianDate.year)})",
                                                            fontWeight = FontWeight.Bold,
                                                            color = accentColor
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Default.Today,
                                                            contentDescription = null,
                                                            tint = accentColor,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    onClick = {
                                                        selectedYear = currentPersianDate.year
                                                        selectedMonth = currentPersianDate.month
                                                        isMonthDropdownOpen = false
                                                    }
                                                )
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                            }

                                            // فهرست ماه‌های سال شمسی
                                            PersianDateHelper.PERSIAN_MONTHS.forEachIndexed { index, mName ->
                                                val mNum = index + 1
                                                val isSelected = mNum == selectedMonth
                                                val isTodayMonth = (mNum == currentPersianDate.month && selectedYear == currentPersianDate.year)
                                                val countInM = monthInstallmentCounts.getOrElse(mNum) { 0 }

                                                DropdownMenuItem(
                                                    text = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = mName,
                                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                if (isTodayMonth) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = accentColor.copy(alpha = 0.12f)
                                                                    ) {
                                                                        Text(
                                                                            text = "ماه جاری",
                                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                                            color = accentColor,
                                                                            fontWeight = FontWeight.Bold,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                                if (countInM > 0) {
                                                                    Surface(
                                                                        shape = RoundedCornerShape(4.dp),
                                                                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceVariant
                                                                    ) {
                                                                        Text(
                                                                            text = "${PersianNumberFormatter.formatDigits(countInM)} قسط",
                                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                            fontWeight = FontWeight.Bold,
                                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                    leadingIcon = if (isSelected) {
                                                        {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = accentColor,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    } else null,
                                                    onClick = {
                                                        selectedMonth = mNum
                                                        isMonthDropdownOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                AnimatedContent(
                                    targetState = selectedMonthTotalAmount,
                                    label = "month_amount_anim"
                                ) { totalAmt ->
                                    Text(
                                        text = PersianNumberFormatter.formatCurrency(totalAmt, userPreferences.currency),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (selectedMonthPaidAmount > 0) Icons.Default.CheckCircle else Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = if (selectedMonthPaidAmount > 0) IncomeGreen else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    AnimatedContent(
                                        targetState = selectedMonthPaidAmount,
                                        label = "month_paid_anim"
                                    ) { paidAmt ->
                                        Text(
                                            text = if (paidAmt > 0) {
                                                "${PersianNumberFormatter.formatCurrency(paidAmt, userPreferences.currency)} پرداخت شده"
                                            } else {
                                                "بدون پرداخت در این ماه"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (paidAmt > 0) IncomeGreen else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    if (selectedMonthRemainingAmount > 0L) {
                                        AnimatedContent(
                                            targetState = selectedMonthRemainingAmount,
                                            label = "month_remaining_anim"
                                        ) { remAmt ->
                                            Text(
                                                text = "(${PersianNumberFormatter.formatCurrency(remAmt, userPreferences.currency)} مانده)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = accentColor.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                                        modifier = Modifier.clickable {
                                            selectedTab = 0
                                            currentInstallmentsSubMode = 0
                                        }
                                    ) {
                                        Text(
                                            text = if (isCurrentMonthSelected) {
                                                "${PersianNumberFormatter.formatDigits(selectedMonthTotalCount)} قسط این ماه"
                                            } else {
                                                "${PersianNumberFormatter.formatDigits(selectedMonthTotalCount)} قسط در این ماه"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (overdueCount > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ExpenseRed.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = ExpenseRed,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "${PersianNumberFormatter.formatDigits(overdueCount)} معوقه",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = ExpenseRed
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // چارت دایره‌ای گرافیکی تسویه کل وام‌ها
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokePx = 6.dp.toPx()
                                    val inset = strokePx / 2f + 1f
                                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

                                    drawCircle(
                                        color = accentColor.copy(alpha = 0.12f),
                                        radius = (size.minDimension / 2f) - strokePx
                                    )
                                    drawArc(
                                        color = accentColor.copy(alpha = 0.2f),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = arcSize,
                                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                    )
                                    if (animOverall > 0.01f) {
                                        drawArc(
                                            brush = Brush.linearGradient(
                                                colors = listOf(accentColor, accentLight, IncomeGreen)
                                            ),
                                            startAngle = -90f,
                                            sweepAngle = animOverall * 360f,
                                            useCenter = false,
                                            topLeft = Offset(inset, inset),
                                            size = arcSize,
                                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${PersianNumberFormatter.formatDigits(overallPercent)}٪",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (overallPercent >= 100 && totalAllPlansAmount > 0) IncomeGreen else accentColor
                                    )
                                    Text(
                                        text = "تسویه کل",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // خط جداکننده نرم
                        HorizontalDivider(
                            color = accentColor.copy(alpha = 0.18f),
                            thickness = 1.dp
                        )

                        // بخش پایینی: دو باکس متقارن و متناسب برای وضعیت اقساط ماه انتخابی
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedMonthPaidAmount > 0) IncomeGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, if (selectedMonthPaidAmount > 0) IncomeGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = if (selectedMonthPaidAmount > 0) IncomeGreen else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (isCurrentMonthSelected) "پرداخت‌شده این ماه" else "پرداخت‌شده $selectedMonthName",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selectedMonthPaidAmount > 0) IncomeGreen else MaterialTheme.colorScheme.outline,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = PersianNumberFormatter.formatCurrency(selectedMonthPaidAmount, userPreferences.currency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (selectedMonthPaidAmount > 0) IncomeGreen else MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = accentColor.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.HourglassBottom,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (isCurrentMonthSelected) "مانده این ماه" else "مانده $selectedMonthName",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = PersianNumberFormatter.formatCurrency(selectedMonthRemainingAmount, userPreferences.currency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // خلاصه کلی تسویه کل وام‌ها در نوار زیرین
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مجموع پرداختی کل: ${PersianNumberFormatter.formatCurrency(totalPaidSoFarAmount, userPreferences.currency)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "مانده کل وام‌ها: ${PersianNumberFormatter.formatCurrency(totalRemainingAmount, userPreferences.currency)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Top Tab Selector: اقساط جاری vs بایگانی
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val activeTab0Bg by animateColorAsState(
                            targetValue = if (selectedTab == 0) accentColor else Color.Transparent,
                            animationSpec = tween(durationMillis = 200),
                            label = "tab0_bg"
                        )
                        val activeTab0Fg by animateColorAsState(
                            targetValue = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(durationMillis = 200),
                            label = "tab0_fg"
                        )
                        val activeTab1Bg by animateColorAsState(
                            targetValue = if (selectedTab == 1) accentColor else Color.Transparent,
                            animationSpec = tween(durationMillis = 200),
                            label = "tab1_bg"
                        )
                        val activeTab1Fg by animateColorAsState(
                            targetValue = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(durationMillis = 200),
                            label = "tab1_fg"
                        )

                        // Tab 0: Active Installments
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = activeTab0Bg,
                            contentColor = activeTab0Fg,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTab = 0 }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "اقساط جاری (${PersianNumberFormatter.formatDigits(activePlans.size)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == 0) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }

                        // Tab 1: Archive (Completed Installments)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = activeTab1Bg,
                            contentColor = activeTab1Fg,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedTab = 1 }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "بایگانی (${PersianNumberFormatter.formatDigits(archivedPlans.size)})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == 1) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Two-state switch for "اقساط جاری": 0 = اقساط پرداخت‌نشده این ماه | 1 = همه وام‌ها و طرح‌ها
            if (selectedTab == 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val sub0Bg by animateColorAsState(
                                targetValue = if (currentInstallmentsSubMode == 0) accentColor else Color.Transparent,
                                animationSpec = tween(durationMillis = 200),
                                label = "sub0_bg"
                            )
                            val sub0Fg by animateColorAsState(
                                targetValue = if (currentInstallmentsSubMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(durationMillis = 200),
                                label = "sub0_fg"
                            )
                            val sub1Bg by animateColorAsState(
                                targetValue = if (currentInstallmentsSubMode == 1) accentColor else Color.Transparent,
                                animationSpec = tween(durationMillis = 200),
                                label = "sub1_bg"
                            )
                            val sub1Fg by animateColorAsState(
                                targetValue = if (currentInstallmentsSubMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(durationMillis = 200),
                                label = "sub1_fg"
                            )

                            // Sub-mode 0: Unpaid of selected month
                            val selectedMonthUnpaidCount = selectedMonthUnpaidInstallments.size + fallbackPlansForSelectedMonth.size
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = sub0Bg,
                                contentColor = sub0Fg,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .clickable { currentInstallmentsSubMode = 0 }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EventBusy,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "پرداخت‌نشده (${PersianNumberFormatter.formatDigits(selectedMonthUnpaidCount)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (currentInstallmentsSubMode == 0) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }

                            // Sub-mode 1: Installments of the selected month at the top
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = sub1Bg,
                                contentColor = sub1Fg,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .clickable { currentInstallmentsSubMode = 1 }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FormatListBulleted,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "اقساط (${PersianNumberFormatter.formatDigits(selectedMonthTotalCount)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (currentInstallmentsSubMode == 1) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Rendering content based on selectedTab and currentInstallmentsSubMode
            if (selectedTab == 0 && currentInstallmentsSubMode == 0) {
                val showPastOverdueInView = isCurrentMonthSelected && pastOverdueInstallments.isNotEmpty()
                val hasUnpaidInMonthView = selectedMonthUnpaidInstallments.isNotEmpty() ||
                    fallbackPlansForSelectedMonth.isNotEmpty() ||
                    showPastOverdueInView

                if (!hasUnpaidInMonthView) {
                    item {
                        EmptyStateCard(
                            title = if (selectedMonthTotalCount == 0) {
                                "هیچ قسطی در $selectedMonthName ${PersianNumberFormatter.formatDigits(selectedYear)} وجود ندارد"
                            } else {
                                "تمام اقساط $selectedMonthName پرداخت شده‌اند 🎉"
                            },
                            description = if (selectedMonthTotalCount == 0) {
                                "در این ماه سررسید قسطی ثبت نشده است. با انتخاب ماه دیگر از بالا یا لمس دکمه + می‌توانید اقساط را مدیریت کنید."
                            } else {
                                "هیچ قسط پرداخت‌نشده‌ای برای $selectedMonthName باقی نمانده است. برای مشاهده اقساط پرداخت‌شده به تب «اقساط» مراجعه کنید."
                            },
                            icon = if (selectedMonthTotalCount == 0) Icons.Outlined.EventAvailable else Icons.Default.CheckCircle
                        )
                    }
                } else {
                    // Past overdue installments warning & items (ONLY shown in current month view)
                    if (showPastOverdueInView) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ExpenseRed.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = ExpenseRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${PersianNumberFormatter.formatDigits(pastOverdueInstallments.size)} قسط معوقه از ماه‌های گذشته وجود دارد:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ExpenseRed
                                    )
                                }
                            }
                        }

                        items(pastOverdueInstallments, key = { "past_${it.id}" }) { inst ->
                            val plan = plansMap[inst.planId]
                            if (plan != null) {
                                GlassCard(
                                    shape = RoundedCornerShape(20.dp),
                                    borderColor = ExpenseRed.copy(alpha = 0.85f),
                                    isHighlighted = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(),
                                    onClick = { onNavigateToDetail(plan.id) }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = plan.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "قسط ${PersianNumberFormatter.formatDigits(inst.installmentIndex)} از ${PersianNumberFormatter.formatDigits(plan.numberOfInstallments)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = ExpenseRed.copy(alpha = 0.16f),
                                                    border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.55f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.PriorityHigh,
                                                            contentDescription = null,
                                                            tint = ExpenseRed,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = "معوقه (${PersianDateHelper.formatToPersianDate(inst.dueDateMillis)})",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = ExpenseRed
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { planToEdit = plan },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Edit,
                                                        contentDescription = "ویرایش",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "مبلغ قسط",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                Text(
                                                    text = PersianNumberFormatter.formatCurrency(inst.amount, userPreferences.currency),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { onNavigateToDetail(plan.id) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text("جزئیات وام", style = MaterialTheme.typography.labelSmall)
                                                }

                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            repository.payInstallment(
                                                                plan = plan,
                                                                installment = inst,
                                                                accountId = plan.accountId ?: accounts.firstOrNull { it.isActive }?.id ?: accounts.firstOrNull()?.id
                                                            )
                                                            Toast.makeText(context, "قسط معوقه ${PersianNumberFormatter.formatDigits(inst.installmentIndex)} با موفقیت پرداخت شد ✓", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp),
                                                        tint = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "پرداخت قسط",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Selected month unpaid installments
                    if (selectedMonthUnpaidInstallments.isNotEmpty()) {
                        if (showPastOverdueInView || !isCurrentMonthSelected) {
                            item {
                                Text(
                                    text = "اقساط سررسید $selectedMonthName ${PersianNumberFormatter.formatDigits(selectedYear)}:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        items(selectedMonthUnpaidInstallments, key = { "curr_${it.id}" }) { inst ->
                            val plan = plansMap[inst.planId]
                            if (plan != null) {
                                val now = System.currentTimeMillis()
                                val isOverdue = inst.dueDateMillis < now

                                GlassCard(
                                    shape = RoundedCornerShape(20.dp),
                                    borderColor = if (isOverdue) ExpenseRed.copy(alpha = 0.85f) else accentColor.copy(alpha = 0.55f),
                                    isHighlighted = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(),
                                    onClick = { onNavigateToDetail(plan.id) }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = plan.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "قسط ${PersianNumberFormatter.formatDigits(inst.installmentIndex)} از ${PersianNumberFormatter.formatDigits(plan.numberOfInstallments)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isOverdue) ExpenseRed.copy(alpha = 0.16f) else accentColor.copy(alpha = 0.12f),
                                                    border = BorderStroke(1.dp, if (isOverdue) ExpenseRed.copy(alpha = 0.55f) else accentColor.copy(alpha = 0.35f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isOverdue) Icons.Default.PriorityHigh else Icons.Default.CalendarToday,
                                                            contentDescription = null,
                                                            tint = if (isOverdue) ExpenseRed else accentColor,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Text(
                                                            text = if (isOverdue) "معوقه (${PersianDateHelper.formatToPersianDate(inst.dueDateMillis)})"
                                                            else "سررسید: ${PersianDateHelper.formatToPersianDate(inst.dueDateMillis)}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isOverdue) ExpenseRed else accentColor
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { planToEdit = plan },
                                                    modifier = Modifier.size(30.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Outlined.Edit,
                                                        contentDescription = "ویرایش",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "مبلغ قسط",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                Text(
                                                    text = PersianNumberFormatter.formatCurrency(inst.amount, userPreferences.currency),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { onNavigateToDetail(plan.id) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Text("جزئیات وام", style = MaterialTheme.typography.labelSmall)
                                                }

                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            repository.payInstallment(
                                                                plan = plan,
                                                                installment = inst,
                                                                accountId = plan.accountId ?: accounts.firstOrNull { it.isActive }?.id ?: accounts.firstOrNull()?.id
                                                            )
                                                            Toast.makeText(context, "قسط ${PersianNumberFormatter.formatDigits(inst.installmentIndex)} با موفقیت پرداخت شد ✓", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp),
                                                        tint = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "پرداخت قسط",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Fallback unpaid plans without InstallmentEntity rows
                    if (fallbackPlansForSelectedMonth.isNotEmpty()) {
                        items(fallbackPlansForSelectedMonth, key = { "fallback_unpaid_${it.id}" }) { plan ->
                            GlassCard(
                                shape = RoundedCornerShape(20.dp),
                                borderColor = accentColor.copy(alpha = 0.55f),
                                isHighlighted = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                onClick = { onNavigateToDetail(plan.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = plan.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "سررسید روز ${PersianNumberFormatter.formatDigits(plan.dueDayOfMonth)} $selectedMonthName",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(plan.installmentAmount, userPreferences.currency),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${PersianNumberFormatter.formatDigits(plan.paidInstallments)} از ${PersianNumberFormatter.formatDigits(plan.numberOfInstallments)} قسط",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == 0 && currentInstallmentsSubMode == 1) {
                // VIEW 2: اقساط ماه انتخاب‌شده در بالا (هم پرداخت‌شده و هم پرداخت‌نشده)
                if (selectedMonthOrderedInstallments.isEmpty() && fallbackPlansForSelectedMonth.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "هیچ قسطی در $selectedMonthName ${PersianNumberFormatter.formatDigits(selectedYear)} وجود ندارد",
                            description = "برای ماه انتخاب‌شده در بالا قسطی ثبت نشده است. می‌توانید ماه دیگری را از منوی بالا انتخاب کنید یا با دکمه + وام جدید بسازید.",
                            icon = Icons.Outlined.EventAvailable
                        )
                    }
                } else {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "اقساط $selectedMonthName ${PersianNumberFormatter.formatDigits(selectedYear)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${PersianNumberFormatter.formatDigits(selectedMonthPaidInstallments.size)} پرداخت‌شده • ${PersianNumberFormatter.formatDigits(selectedMonthUnpaidInstallments.size)} باقی‌مانده",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    items(selectedMonthOrderedInstallments, key = { "month_inst_${it.id}" }) { inst ->
                        val plan = plansMap[inst.planId]
                        if (plan != null) {
                            val now = System.currentTimeMillis()
                            val isOverdue = !inst.isPaid && inst.dueDateMillis < now
                            val cardBorder = when {
                                inst.isPaid -> IncomeGreen.copy(alpha = 0.55f)
                                isOverdue -> ExpenseRed.copy(alpha = 0.85f)
                                else -> accentColor.copy(alpha = 0.55f)
                            }

                            GlassCard(
                                shape = RoundedCornerShape(20.dp),
                                borderColor = cardBorder,
                                isHighlighted = !inst.isPaid,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                onClick = { onNavigateToDetail(plan.id) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = plan.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                textDecoration = if (inst.isPaid) TextDecoration.LineThrough else TextDecoration.None,
                                                color = if (inst.isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "قسط ${PersianNumberFormatter.formatDigits(inst.installmentIndex)} از ${PersianNumberFormatter.formatDigits(plan.numberOfInstallments)}" +
                                                    if (plan.creditorOrCompany.isNotBlank()) " • ${plan.creditorOrCompany}" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val statusColor = when {
                                                inst.isPaid -> IncomeGreen
                                                isOverdue -> ExpenseRed
                                                else -> accentColor
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = statusColor.copy(alpha = 0.14f),
                                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = when {
                                                            inst.isPaid -> Icons.Default.CheckCircle
                                                            isOverdue -> Icons.Default.PriorityHigh
                                                            else -> Icons.Default.CalendarToday
                                                        },
                                                        contentDescription = null,
                                                        tint = statusColor,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = when {
                                                            inst.isPaid -> "پرداخت شده (${PersianDateHelper.formatToPersianDate(inst.dueDateMillis)})"
                                                            isOverdue -> "معوقه (${PersianDateHelper.formatToPersianDate(inst.dueDateMillis)})"
                                                            else -> "سررسید: ${PersianDateHelper.formatToPersianDate(inst.dueDateMillis)}"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = statusColor
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { planToEdit = plan },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Edit,
                                                    contentDescription = "ویرایش",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "مبلغ قسط",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Text(
                                                text = PersianNumberFormatter.formatCurrency(inst.amount, userPreferences.currency),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (inst.isPaid) IncomeGreen else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { onNavigateToDetail(plan.id) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Text("جزئیات وام", style = MaterialTheme.typography.labelSmall)
                                            }

                                            if (inst.isPaid) {
                                                OutlinedButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            repository.unpayInstallment(plan = plan, installment = inst)
                                                            Toast.makeText(
                                                                context,
                                                                "پرداخت قسط ${PersianNumberFormatter.formatDigits(inst.installmentIndex)} لغو شد",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    },
                                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.5f)),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Undo,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = IncomeGreen
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "لغو پرداخت",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = IncomeGreen
                                                    )
                                                }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            repository.payInstallment(
                                                                plan = plan,
                                                                installment = inst,
                                                                accountId = plan.accountId ?: accounts.firstOrNull { it.isActive }?.id ?: accounts.firstOrNull()?.id
                                                            )
                                                            Toast.makeText(
                                                                context,
                                                                "قسط ${PersianNumberFormatter.formatDigits(inst.installmentIndex)} با موفقیت پرداخت شد ✓",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.height(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp),
                                                        tint = Color.White
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "پرداخت قسط",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (fallbackPlansForSelectedMonth.isNotEmpty()) {
                        items(fallbackPlansForSelectedMonth, key = { "month_fallback_${it.id}" }) { plan ->
                            GlassCard(
                                shape = RoundedCornerShape(20.dp),
                                borderColor = accentColor.copy(alpha = 0.55f),
                                isHighlighted = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                onClick = { onNavigateToDetail(plan.id) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = plan.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "سررسید روز ${PersianNumberFormatter.formatDigits(plan.dueDayOfMonth)} $selectedMonthName",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(plan.installmentAmount, userPreferences.currency),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "${PersianNumberFormatter.formatDigits(plan.paidInstallments)} از ${PersianNumberFormatter.formatDigits(plan.numberOfInstallments)} قسط",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // VIEW 3: Archive (Completed Loan Plans)
                if (archivedPlans.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "هنوز قسط تکمیل‌شده‌ای در بایگانی وجود ندارد",
                            description = "اقساطی که تمام پرداخت‌های آن‌ها تکمیل شود، به صورت خودکار به بخش بایگانی منتقل می‌شوند.",
                            icon = Icons.Default.Archive
                        )
                    }
                } else {
                    items(archivedPlans, key = { it.id }) { plan ->
                    val now = System.currentTimeMillis()
                    val planItems = allInstallments.filter { it.planId == plan.id }
                    val planOverdueCount = planItems.count { !it.isPaid && it.dueDateMillis < now }
                    val hasOverdue = planOverdueCount > 0

                    val progress = if (plan.numberOfInstallments > 0) plan.paidInstallments.toFloat() / plan.numberOfInstallments.toFloat() else 0f
                    val percentPaid = (progress * 100).toInt()
                    val paidSoFar = if (planItems.isNotEmpty()) planItems.filter { it.isPaid }.sumOf { it.amount }
                    else plan.paidInstallments.coerceAtMost(plan.numberOfInstallments) * plan.installmentAmount
                    val remainingAmount = if (planItems.isNotEmpty()) planItems.filter { !it.isPaid }.sumOf { it.amount }
                    else ((plan.numberOfInstallments - plan.paidInstallments).coerceAtLeast(0)) * plan.installmentAmount
                    val endDateMillis = PersianDateHelper.addMonthsToPersianDate(
                        startYear = PersianDateHelper.fromMillis(plan.startDateMillis).year,
                        startMonth = PersianDateHelper.fromMillis(plan.startDateMillis).month,
                        dayOfMonth = plan.dueDayOfMonth,
                        monthsToAdd = (plan.numberOfInstallments - 1).coerceAtLeast(0)
                    )

                    GlassCard(
                        shape = RoundedCornerShape(24.dp),
                        borderColor = when {
                            hasOverdue -> ExpenseRed.copy(alpha = 0.85f)
                            plan.isCompleted -> IncomeGreen.copy(alpha = 0.55f)
                            else -> accentColor.copy(alpha = 0.55f)
                        },
                        isHighlighted = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        onClick = { onNavigateToDetail(plan.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Overdue warning banner / Completed Archive Badge
                            if (hasOverdue) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ExpenseRed.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.6f)),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = "اخطار قسط معوقه",
                                            tint = ExpenseRed,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "هشدار: ${PersianNumberFormatter.formatDigits(planOverdueCount)} قسط معوقه دارد!",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ExpenseRed
                                        )
                                    }
                                }
                            } else if (plan.isCompleted) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IncomeGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.4f)),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "تسویه کامل (بایگانی‌شده)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = IncomeGreen
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = plan.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        if (hasOverdue) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = ExpenseRed,
                                                contentColor = Color.White
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.PriorityHigh,
                                                        contentDescription = "معوقه",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Text(
                                                        text = "معوقه",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${PersianDateHelper.formatToPersianDate(plan.startDateMillis)} تا ${PersianDateHelper.formatToPersianDate(endDateMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (plan.isCompleted) IncomeGreen.copy(alpha = 0.16f) else accentColor.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "${PersianNumberFormatter.formatDigits(percentPaid)}٪",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (plan.isCompleted) IncomeGreen else accentColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { planToEdit = plan },
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
                                        onClick = { planToDelete = plan },
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

                            Spacer(modifier = Modifier.height(10.dp))

                            // Paid So Far vs Remaining Outlook Boxes - کاملاً متناسب و هم‌اندازه
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = IncomeGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Verified,
                                                contentDescription = null,
                                                tint = IncomeGreen,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "پرداخت شده",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IncomeGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(paidSoFar, userPreferences.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = IncomeGreen,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = accentColor.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.HourglassBottom,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "مانده اقساط",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(remainingAmount, userPreferences.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Graphical Check-Tiles Preview Row (tappable to open detail / check installments)
                            val previewTotal = plan.numberOfInstallments.coerceIn(1, 12)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (idx in 1..previewTotal) {
                                    val isTicked = idx <= plan.paidInstallments
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(26.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isTicked) {
                                                    Brush.linearGradient(
                                                        colors = listOf(IncomeGreen, IncomeGreen.copy(alpha = 0.75f))
                                                    )
                                                } else {
                                                    Brush.linearGradient(
                                                        colors = listOf(
                                                            accentColor.copy(alpha = 0.18f),
                                                            accentColor.copy(alpha = 0.08f)
                                                        )
                                                    )
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isTicked) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        } else {
                                            Text(
                                                text = PersianNumberFormatter.formatDigits(idx),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = accentColor
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "هر قسط: ${PersianNumberFormatter.formatCurrency(plan.installmentAmount, userPreferences.currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (plan.isCompleted) IncomeGreen.copy(alpha = 0.18f) else accentColor.copy(alpha = 0.16f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (plan.isCompleted) Icons.Default.CheckCircle else Icons.Default.TouchApp,
                                            contentDescription = null,
                                            tint = if (plan.isCompleted) IncomeGreen else accentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${PersianNumberFormatter.formatDigits(plan.paidInstallments)} از ${PersianNumberFormatter.formatDigits(plan.numberOfInstallments)} قسط",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (plan.isCompleted) IncomeGreen else accentColor,
                                            fontWeight = FontWeight.ExtraBold
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

    if (showAddDialog) {
        AddInstallmentDialog(
            currency = userPreferences.currency,
            accounts = accounts,
            onDismiss = { showAddDialog = false },
            onSave = { title, creditor, totalLoanAmount, count, alreadyPaidCount, amount, day, startDateMillis, notes, autoCreateExpense, accountId, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    val total = count * amount
                    val effectiveLoanTotal = if (totalLoanAmount > 0L) totalLoanAmount else total
                    val safePaid = alreadyPaidCount.coerceIn(0, count)
                    val planId = repository.createInstallmentPlan(
                        plan = InstallmentPlanEntity(
                            title = title,
                            creditorOrCompany = creditor,
                            totalLoanAmount = effectiveLoanTotal,
                            totalAmount = total,
                            numberOfInstallments = count,
                            installmentAmount = amount,
                            paidInstallments = safePaid,
                            dueDayOfMonth = day,
                            startDateMillis = startDateMillis,
                            notes = notes,
                            autoCreateExpense = autoCreateExpense,
                            accountId = accountId,
                            isCompleted = safePaid >= count
                        ),
                        startDateMillis = startDateMillis,
                        initialPaidCount = safePaid
                    )

                    if (enableReminder && safePaid < count) {
                        val basePersianDate = PersianDateHelper.fromMillis(startDateMillis)
                        val nextUnpaidDueMillis = PersianDateHelper.addMonthsToPersianDate(
                            startYear = basePersianDate.year,
                            startMonth = basePersianDate.month,
                            dayOfMonth = day,
                            monthsToAdd = safePaid
                        )
                        val triggerMillis = computeTriggerTimeMillis(
                            dueDateMillis = nextUnpaidDueMillis,
                            offsetDays = offsetDays,
                            hour = reminderHour,
                            minute = reminderMinute
                        )
                        val remTitle = "یادآور قسط: $title"
                        val remDesc = "قسط شماره ${PersianNumberFormatter.formatDigits(safePaid + 1)} به مبلغ ${PersianNumberFormatter.formatCurrency(amount, userPreferences.currency)}"
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

                    Toast.makeText(context, "اقساط ثبت شد", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            }
        )
    }

    planToEdit?.let { plan ->
        val existingReminder = installmentRemindersMap[plan.id]
        EditInstallmentPlanDialog(
            plan = plan,
            currency = userPreferences.currency,
            accounts = accounts,
            existingReminder = existingReminder,
            onDismiss = { planToEdit = null },
            onSave = { updatedPlan, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    repository.updateInstallmentPlan(updatedPlan)
                    if (enableReminder && !updatedPlan.isCompleted && updatedPlan.paidInstallments < updatedPlan.numberOfInstallments) {
                        val basePersianDate = PersianDateHelper.fromMillis(updatedPlan.startDateMillis)
                        val nextUnpaidDueMillis = PersianDateHelper.addMonthsToPersianDate(
                            startYear = basePersianDate.year,
                            startMonth = basePersianDate.month,
                            dayOfMonth = updatedPlan.dueDayOfMonth,
                            monthsToAdd = updatedPlan.paidInstallments
                        )
                        val triggerMillis = computeTriggerTimeMillis(
                            dueDateMillis = nextUnpaidDueMillis,
                            offsetDays = offsetDays,
                            hour = reminderHour,
                            minute = reminderMinute
                        )
                        val remTitle = "یادآور قسط: ${updatedPlan.title}"
                        val remDesc = "مبلغ هر قسط: ${PersianNumberFormatter.formatCurrency(updatedPlan.installmentAmount, userPreferences.currency)}"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.INSTALLMENT,
                            targetId = updatedPlan.id,
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
                        repository.removeTargetReminders(ReminderTargetType.INSTALLMENT, updatedPlan.id)
                    }
                    Toast.makeText(context, "اقساط بروزرسانی شد ✓", Toast.LENGTH_SHORT).show()
                    planToEdit = null
                }
            }
        )
    }

    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("حذف اقساط") },
            text = { Text("آیا از حذف «${plan.title}» اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteInstallmentPlan(plan)
                            Toast.makeText(context, "حذف شد", Toast.LENGTH_SHORT).show()
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

    if (showPrintDialog) {
        val subSections = remember(currentInstallmentsSubMode) {
            listOf(
                PrintableSubSection("unpaid", "اقساط پرداخت‌نشده", "اقساط سررسیددار در انتظار پرداخت"),
                PrintableSubSection("paid", "اقساط پرداخت‌شده", "اقساط تسویه‌شده در بازه", defaultSelected = currentInstallmentsSubMode == 1),
                PrintableSubSection("plans_summary", "خلاصه طرح‌های وام و قسط", "جمع کل، مانده و تعداد اقساط هر طرح")
            )
        }
        DodotaPrintDialog(
            sectionTitle = "اقساط",
            userPreferences = userPreferences,
            supportsDateRange = true,
            initialPreset = PrintDatePreset.CUSTOM,
            initialCustomStartMillis = startOfMonthMillis,
            initialCustomEndMillis = endOfMonthMillis,
            subSections = subSections,
            showNotesToggle = false,
            countProvider = { key, s, e ->
                fun inRange(ms: Long) = (s == null || ms >= s) && (e == null || ms <= e)
                when (key) {
                    "unpaid" -> allInstallments.count { it.planId in plansMap && !it.isPaid && inRange(it.dueDateMillis) }
                    "paid" -> allInstallments.count { it.planId in plansMap && it.isPaid && inRange(it.dueDateMillis) }
                    "plans_summary" -> plans.size
                    else -> null
                }
            },
            onDismiss = { showPrintDialog = false },
            onConfirmPrint = { payload ->
                val doc = DodotaPrintBuilders.buildInstallmentsDocument(
                    payload = payload,
                    plans = plans,
                    allInstallments = allInstallments,
                    userPreferences = userPreferences
                )
                DodotaPrintHelper.printDocument(context, doc)
            }
        )
    }
}

internal fun computeTriggerTimeMillis(
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

@Composable
fun AddInstallmentDialog(
    currency: ir.mmd.dodota.data.model.AppCurrency,
    accounts: List<AccountEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        creditor: String,
        totalLoanAmount: Long,
        count: Int,
        alreadyPaidCount: Int,
        amount: Long,
        day: Int,
        startDateMillis: Long,
        notes: String,
        autoCreateExpense: Boolean,
        accountId: Long?,
        enableReminder: Boolean,
        offsetDays: Int,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var creditor by remember { mutableStateOf("") }
    var totalLoanAmountText by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("12") }
    var alreadyPaidText by remember { mutableStateOf("0") }
    var amountText by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var dayText by remember { mutableStateOf(PersianDateHelper.fromMillis(startDateMillis).day.toString()) }
    var notes by remember { mutableStateOf("") }
    var autoCreateExpense by remember { mutableStateOf(true) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull { it.isActive }?.id ?: accounts.firstOrNull()?.id) }
    var showStartDatePicker by remember { mutableStateOf(false) }

    var enableReminder by remember { mutableStateOf(true) }
    var offsetDays by remember { mutableIntStateOf(0) }
    var reminderHour by remember { mutableIntStateOf(9) }
    var reminderMinute by remember { mutableIntStateOf(0) }

    val parsedCount = countText.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val parsedAmount = PersianNumberFormatter.parseAmount(amountText)
    val parsedPaid = (alreadyPaidText.toIntOrNull() ?: 0).coerceIn(0, parsedCount)
    val computedTotal = parsedCount * parsedAmount

    GraphicalFormDialog(
        title = "افزودن قسط یا وام",
        icon = Icons.Default.EventRepeat,
        confirmText = "ثبت اقساط",
        onConfirm = {
            val count = countText.toIntOrNull() ?: 0
            val paidBefore = alreadyPaidText.toIntOrNull() ?: 0
            val amount = PersianNumberFormatter.parseAmount(amountText)
            val totalLoan = PersianNumberFormatter.parseAmount(totalLoanAmountText)
            val day = dayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
            when {
                title.isBlank() -> Toast.makeText(context, "لطفاً عنوان وام یا قسط را وارد کنید", Toast.LENGTH_SHORT).show()
                count <= 0 -> Toast.makeText(context, "تعداد اقساط باید بیشتر از صفر باشد", Toast.LENGTH_SHORT).show()
                amount <= 0L -> Toast.makeText(context, "لطفاً مبلغ معتبری وارد کنید", Toast.LENGTH_SHORT).show()
                else -> {
                    val effectiveTotalLoan = if (totalLoan > 0L) totalLoan else (count * amount)
                    onSave(
                        title.trim(),
                        creditor.trim(),
                        effectiveTotalLoan,
                        count,
                        paidBefore.coerceIn(0, count),
                        amount,
                        day,
                        startDateMillis,
                        notes.trim(),
                        autoCreateExpense,
                        if (autoCreateExpense) selectedAccountId else null,
                        enableReminder,
                        offsetDays,
                        reminderHour,
                        reminderMinute
                    )
                }
            }
        },
        onDismiss = onDismiss
    ) {
        DodotaTextField(
            value = title,
            onValueChange = { title = it },
            label = "عنوان وام یا قسط",
            leadingIcon = Icons.Default.EventRepeat,
            modifier = Modifier.fillMaxWidth()
        )
        DodotaTextField(
            value = creditor,
            onValueChange = { creditor = it },
            label = "طرف حساب / بانک",
            leadingIcon = Icons.Default.AccountBalance,
            modifier = Modifier.fillMaxWidth()
        )

        PersianAmountInput(
            value = totalLoanAmountText,
            onValueChange = { totalLoanAmountText = it },
            currency = currency,
            label = "مبلغ کل وام (اختیاری)",
            leadingIcon = Icons.Default.AccountBalanceWallet
        )

        PersianAmountInput(
            value = amountText,
            onValueChange = { amountText = it },
            currency = currency,
            label = "مبلغ هر قسط"
        )

        OutlinedButton(
            onClick = { showStartDatePicker = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("تاریخ شروع اولین قسط: ${PersianDateHelper.formatToPersianDate(startDateMillis, currency.isEnglishDigits)}")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DodotaTextField(
                value = PersianNumberFormatter.formatDigits(countText, currency),
                onValueChange = { countText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
                label = "کل اقساط",
                leadingIcon = Icons.Default.Numbers,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            DodotaTextField(
                value = PersianNumberFormatter.formatDigits(alreadyPaidText, currency),
                onValueChange = { alreadyPaidText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
                label = "پرداخت‌شده",
                leadingIcon = Icons.Default.CheckCircleOutline,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        DodotaTextField(
            value = PersianNumberFormatter.formatDigits(dayText, currency),
            onValueChange = { dayText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
            label = "روز سررسید ماهانه (۱ تا ۳۱)",
            leadingIcon = Icons.Outlined.CalendarMonth,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (computedTotal > 0L) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "جمع اقساط: ${PersianNumberFormatter.formatCurrency(computedTotal, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "مانده: ${PersianNumberFormatter.formatDigits(parsedCount - parsedPaid, currency)} قسط",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        InstallmentAccountAndNotesSection(
            accounts = accounts,
            autoCreateExpense = autoCreateExpense,
            onAutoCreateExpenseChange = { autoCreateExpense = it },
            selectedAccountId = selectedAccountId,
            onAccountSelected = { selectedAccountId = it },
            notes = notes,
            onNotesChange = { notes = it }
        )

        ReminderConfigSection(
            isEnabled = enableReminder,
            onEnabledChange = { enableReminder = it },
            offsetDays = offsetDays,
            onOffsetDaysChange = { offsetDays = it },
            selectedHour = reminderHour,
            onHourChange = { reminderHour = it },
            selectedMinute = reminderMinute,
            onMinuteChange = { reminderMinute = it },
            title = "یادآور قسط",
            dueDateMillis = startDateMillis
        )
    }

    if (showStartDatePicker) {
        PersianDatePickerDialog(
            initialDateMillis = startDateMillis,
            onDateSelected = {
                startDateMillis = it
                val pDate = PersianDateHelper.fromMillis(it)
                dayText = pDate.day.toString()
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }
}

@Composable
fun EditInstallmentPlanDialog(
    plan: InstallmentPlanEntity,
    currency: ir.mmd.dodota.data.model.AppCurrency,
    accounts: List<AccountEntity> = emptyList(),
    existingReminder: ReminderEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        updatedPlan: InstallmentPlanEntity,
        enableReminder: Boolean,
        offsetDays: Int,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember(plan.id) { mutableStateOf(plan.title) }
    var creditor by remember(plan.id) { mutableStateOf(plan.creditorOrCompany) }
    var totalLoanAmountText by remember(plan.id) {
        mutableStateOf(
            (if (plan.totalLoanAmount > 0L) plan.totalLoanAmount else plan.totalAmount).toString()
        )
    }
    var amountText by remember(plan.id) { mutableStateOf(plan.installmentAmount.toString()) }
    var countText by remember(plan.id) { mutableStateOf(plan.numberOfInstallments.toString()) }
    var paidCountText by remember(plan.id) { mutableStateOf(plan.paidInstallments.toString()) }
    var dayText by remember(plan.id) { mutableStateOf(plan.dueDayOfMonth.toString()) }
    var startDateMillis by remember(plan.id) { mutableLongStateOf(plan.startDateMillis) }
    var notes by remember(plan.id) { mutableStateOf(plan.notes) }
    var autoCreateExpense by remember(plan.id) { mutableStateOf(plan.autoCreateExpense) }
    var selectedAccountId by remember(plan.id) {
        mutableStateOf(plan.accountId ?: accounts.firstOrNull { it.isActive }?.id ?: accounts.firstOrNull()?.id)
    }
    var showStartDatePicker by remember { mutableStateOf(false) }

    val initCal = remember(existingReminder) {
        Calendar.getInstance().apply {
            if (existingReminder != null) {
                timeInMillis = existingReminder.dueDateTimeMillis
            } else {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
            }
        }
    }
    var enableReminder by remember(plan.id, existingReminder) {
        mutableStateOf(existingReminder?.isEnabled ?: false)
    }
    var offsetDays by remember(plan.id, existingReminder) {
        mutableIntStateOf(existingReminder?.reminderOffsetDays ?: 0)
    }
    var reminderHour by remember(plan.id, existingReminder) {
        mutableIntStateOf(initCal.get(Calendar.HOUR_OF_DAY))
    }
    var reminderMinute by remember(plan.id, existingReminder) {
        mutableIntStateOf(initCal.get(Calendar.MINUTE))
    }

    val parsedCount = (countText.toIntOrNull() ?: plan.numberOfInstallments).coerceAtLeast(1)
    val parsedPaid = (paidCountText.toIntOrNull() ?: 0).coerceIn(0, parsedCount)
    val parsedAmount = PersianNumberFormatter.parseAmount(amountText)
    val computedTotal = parsedCount * parsedAmount

    GraphicalFormDialog(
        title = "ویرایش کامل اقساط",
        icon = Icons.Default.Edit,
        confirmText = "ذخیره تغییرات",
        onConfirm = {
            val finalCount = (countText.toIntOrNull() ?: plan.numberOfInstallments).coerceAtLeast(1)
            val finalPaid = (paidCountText.toIntOrNull() ?: 0).coerceIn(0, finalCount)
            val finalAmount = PersianNumberFormatter.parseAmount(amountText).let {
                if (it > 0L) it else plan.installmentAmount
            }
            val finalTotalAmount = finalCount * finalAmount
            val parsedLoanTotal = PersianNumberFormatter.parseAmount(totalLoanAmountText)
            val effectiveLoanTotal = if (parsedLoanTotal > 0L) parsedLoanTotal else finalTotalAmount
            val finalDay = dayText.toIntOrNull()?.coerceIn(1, 31) ?: plan.dueDayOfMonth

            if (title.isNotBlank()) {
                val updatedPlan = plan.copy(
                    title = title.trim(),
                    creditorOrCompany = creditor.trim(),
                    totalLoanAmount = effectiveLoanTotal,
                    totalAmount = finalTotalAmount,
                    numberOfInstallments = finalCount,
                    installmentAmount = finalAmount,
                    paidInstallments = finalPaid,
                    startDateMillis = startDateMillis,
                    dueDayOfMonth = finalDay,
                    notes = notes.trim(),
                    autoCreateExpense = autoCreateExpense,
                    accountId = if (autoCreateExpense) selectedAccountId else null,
                    isCompleted = finalPaid >= finalCount
                )
                onSave(
                    updatedPlan,
                    enableReminder,
                    offsetDays,
                    reminderHour,
                    reminderMinute
                )
            } else {
                Toast.makeText(context, "لطفاً عنوان وام یا قسط را وارد کنید", Toast.LENGTH_SHORT).show()
            }
        },
        onDismiss = onDismiss
    ) {
        DodotaTextField(
            value = title,
            onValueChange = { title = it },
            label = "عنوان وام یا قسط",
            leadingIcon = Icons.Default.EventRepeat,
            modifier = Modifier.fillMaxWidth()
        )
        DodotaTextField(
            value = creditor,
            onValueChange = { creditor = it },
            label = "طرف حساب / بانک",
            leadingIcon = Icons.Default.AccountBalance,
            modifier = Modifier.fillMaxWidth()
        )
        PersianAmountInput(
            value = totalLoanAmountText,
            onValueChange = { totalLoanAmountText = it },
            currency = currency,
            label = "مبلغ کل وام",
            leadingIcon = Icons.Default.AccountBalanceWallet
        )
        PersianAmountInput(
            value = amountText,
            onValueChange = { amountText = it },
            currency = currency,
            label = "مبلغ هر قسط"
        )
        OutlinedButton(
            onClick = { showStartDatePicker = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("تاریخ شروع اولین قسط: ${PersianDateHelper.formatToPersianDate(startDateMillis, currency.isEnglishDigits)}")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DodotaTextField(
                value = PersianNumberFormatter.formatDigits(countText, currency),
                onValueChange = { countText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
                label = "کل اقساط",
                leadingIcon = Icons.Default.Numbers,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            DodotaTextField(
                value = PersianNumberFormatter.formatDigits(paidCountText, currency),
                onValueChange = { paidCountText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
                label = "پرداخت‌شده",
                leadingIcon = Icons.Default.CheckCircleOutline,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        DodotaTextField(
            value = PersianNumberFormatter.formatDigits(dayText, currency),
            onValueChange = { dayText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
            label = "روز سررسید ماهانه (۱ تا ۳۱)",
            leadingIcon = Icons.Outlined.CalendarMonth,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (computedTotal > 0L) {
            val remainingCount = (parsedCount - parsedPaid).coerceAtLeast(0)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "جمع اقساط: ${PersianNumberFormatter.formatCurrency(computedTotal, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "مانده: ${PersianNumberFormatter.formatDigits(remainingCount, currency)} قسط",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingCount == 0) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        InstallmentAccountAndNotesSection(
            accounts = accounts,
            autoCreateExpense = autoCreateExpense,
            onAutoCreateExpenseChange = { autoCreateExpense = it },
            selectedAccountId = selectedAccountId,
            onAccountSelected = { selectedAccountId = it },
            notes = notes,
            onNotesChange = { notes = it }
        )

        ReminderConfigSection(
            isEnabled = enableReminder,
            onEnabledChange = { enableReminder = it },
            offsetDays = offsetDays,
            onOffsetDaysChange = { offsetDays = it },
            selectedHour = reminderHour,
            onHourChange = { reminderHour = it },
            selectedMinute = reminderMinute,
            onMinuteChange = { reminderMinute = it },
            title = "یادآور قسط",
            dueDateMillis = startDateMillis
        )
    }

    if (showStartDatePicker) {
        PersianDatePickerDialog(
            initialDateMillis = startDateMillis,
            onDateSelected = {
                startDateMillis = it
                val pDate = PersianDateHelper.fromMillis(it)
                dayText = pDate.day.toString()
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }
}

@Composable
private fun InstallmentAccountAndNotesSection(
    accounts: List<AccountEntity>,
    autoCreateExpense: Boolean,
    onAutoCreateExpenseChange: (Boolean) -> Unit,
    selectedAccountId: Long?,
    onAccountSelected: (Long?) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit
) {
    val activeAccounts = remember(accounts) { accounts.filter { it.isActive }.ifEmpty { accounts } }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "کسر خودکار از حساب هنگام پرداخت",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ثبت خودکار تراکنش هزینه هنگام زدن تیک پرداخت",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = autoCreateExpense,
                    onCheckedChange = onAutoCreateExpenseChange
                )
            }

            if (autoCreateExpense && activeAccounts.isNotEmpty()) {
                Text(
                    text = "حساب پیش‌فرض پرداخت:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeAccounts.forEach { acc ->
                        val isSel = selectedAccountId == acc.id
                        FilterChip(
                            selected = isSel,
                            onClick = { onAccountSelected(acc.id) },
                            label = {
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    DodotaTextField(
        value = notes,
        onValueChange = onNotesChange,
        label = "یادداشت و توضیحات (اختیاری)",
        leadingIcon = Icons.Default.Description,
        singleLine = false,
        maxLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
}

