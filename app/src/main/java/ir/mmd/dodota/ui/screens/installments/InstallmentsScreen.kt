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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.model.InstallmentPlanEntity
import ir.mmd.dodota.data.model.ReminderTargetType
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
    var currentInstallmentsSubMode by remember { mutableIntStateOf(0) } // 0: اقساط پرداخت‌نشده این ماه, 1: همه طرح‌های اقساط (وام‌ها)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val allInstallmentsFlow = remember { repository.allInstallments }
    val allInstallments by allInstallmentsFlow.collectAsState(initial = emptyList())

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

    val selectedMonthInstallments = remember(allInstallments, plans, startOfMonthMillis, endOfMonthMillis) {
        val activePlanIds = plans.map { it.id }.toSet()
        allInstallments.filter { it.planId in activePlanIds && it.dueDateMillis in startOfMonthMillis..endOfMonthMillis }
    }

    // Fallback monthly installment calculation directly from active plans if individual installments aren't yet loaded
    val fallbackMonthlyAmount = remember(plans, selectedYear, selectedMonth) {
        plans.filter { !it.isCompleted }.filter { plan ->
            val startP = PersianDateHelper.fromMillis(plan.startDateMillis)
            val monthDiff = (selectedYear - startP.year) * 12 + (selectedMonth - startP.month)
            monthDiff in 0 until plan.numberOfInstallments
        }.sumOf { it.installmentAmount }
    }

    val selectedMonthTotalAmount = if (selectedMonthInstallments.isNotEmpty()) {
        selectedMonthInstallments.sumOf { it.amount }
    } else {
        fallbackMonthlyAmount
    }

    val selectedMonthPaidAmount = selectedMonthInstallments.filter { it.isPaid }.sumOf { it.amount }
    val selectedMonthRemainingAmount = (selectedMonthTotalAmount - selectedMonthPaidAmount).coerceAtLeast(0L)
    val selectedMonthTotalCount = if (selectedMonthInstallments.isNotEmpty()) {
        selectedMonthInstallments.size
    } else {
        plans.count { !it.isCompleted && run {
            val startP = PersianDateHelper.fromMillis(it.startDateMillis)
            val diff = (selectedYear - startP.year) * 12 + (selectedMonth - startP.month)
            diff in 0 until it.numberOfInstallments
        }}
    }
    val overdueCount = remember(selectedMonthInstallments) {
        val now = System.currentTimeMillis()
        selectedMonthInstallments.count { !it.isPaid && it.dueDateMillis < now }
    }

    // Current Persian Month start & end millis for the unpaid installments view
    val currentPersianMonthStartMillis = remember(currentPersianDate) {
        PersianDateHelper.getStartOfMonthMillis(currentPersianDate.year, currentPersianDate.month)
    }
    val currentPersianMonthEndMillis = remember(currentPersianDate) {
        PersianDateHelper.getEndOfMonthMillis(currentPersianDate.year, currentPersianDate.month)
    }

    // Unpaid installments due in the current Persian month
    val currentMonthUnpaidInstallments = remember(allInstallments, activePlansMap, currentPersianMonthStartMillis, currentPersianMonthEndMillis) {
        allInstallments.filter { inst ->
            inst.planId in activePlansMap &&
            !inst.isPaid &&
            inst.dueDateMillis in currentPersianMonthStartMillis..currentPersianMonthEndMillis
        }.sortedBy { it.dueDateMillis }
    }

    // Past overdue installments from previous months (if any)
    val pastOverdueInstallments = remember(allInstallments, activePlansMap, currentPersianMonthStartMillis) {
        allInstallments.filter { inst ->
            inst.planId in activePlansMap &&
            !inst.isPaid &&
            inst.dueDateMillis < currentPersianMonthStartMillis
        }.sortedBy { it.dueDateMillis }
    }

    // Counts of installments per month for the selectedYear to show small badges on the month chips
    val monthInstallmentCounts = remember(allInstallments, plans, selectedYear) {
        val activePlanIds = plans.map { it.id }.toSet()
        val counts = IntArray(13)
        allInstallments.forEach { inst ->
            if (inst.planId in activePlanIds) {
                val p = PersianDateHelper.fromMillis(inst.dueDateMillis)
                if (p.year == selectedYear && p.month in 1..12) {
                    counts[p.month]++
                }
            }
        }
        if (counts.all { it == 0 }) {
            plans.filter { !it.isCompleted }.forEach { plan ->
                val startP = PersianDateHelper.fromMillis(plan.startDateMillis)
                for (m in 1..12) {
                    val diff = (selectedYear - startP.year) * 12 + (m - startP.month)
                    if (diff in 0 until plan.numberOfInstallments) {
                        counts[m]++
                    }
                }
            }
        }
        counts
    }

    val totalAllPlansAmount = plans.sumOf { it.numberOfInstallments * it.installmentAmount }
    val totalPaidSoFarAmount = plans.sumOf { it.paidInstallments.coerceAtMost(it.numberOfInstallments) * it.installmentAmount }
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
                onBackClick = onBack
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
                                                .heightIn(max = 350.dp)
                                        ) {
                                            // دکمه پرش به ماه جاری در صورت انتخاب ماه دیگر
                                            if (!isCurrentMonthSelected) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "بازگشت به ماه جاری (${PersianDateHelper.getMonthName(currentPersianDate.month)})",
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

                            // Sub-mode 0: Unpaid of this month
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
                                        text = "پرداخت‌نشده این ماه (${PersianNumberFormatter.formatDigits(currentMonthUnpaidInstallments.size)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (currentInstallmentsSubMode == 0) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }

                            // Sub-mode 1: All active plans
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
                                        text = "طرح‌های وام (${PersianNumberFormatter.formatDigits(activePlans.size)})",
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
                // VIEW 1: Unpaid Installments for the Current Month & Overdue
                if (currentMonthUnpaidInstallments.isEmpty() && pastOverdueInstallments.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "تمام اقساط این ماه پرداخت شده‌اند 🎉",
                            description = "هیچ قسط معوق یا پرداخت‌نشده‌ای برای این ماه باقی نمانده است. برای مشاهده جزئیات کلی وام‌ها به تب «طرح‌های وام» مراجعه فرمایید.",
                            icon = Icons.Default.CheckCircle
                        )
                    }
                } else {
                    // Past overdue installments warning & items
                    if (pastOverdueInstallments.isNotEmpty()) {
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
                                                                accountId = plan.accountId ?: accounts.firstOrNull()?.id
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

                    // Current month unpaid installments
                    if (currentMonthUnpaidInstallments.isNotEmpty()) {
                        if (pastOverdueInstallments.isNotEmpty()) {
                            item {
                                Text(
                                    text = "اقساط سررسید این ماه (${selectedMonthName}):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        items(currentMonthUnpaidInstallments, key = { "curr_${it.id}" }) { inst ->
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
                                                                accountId = plan.accountId ?: accounts.firstOrNull()?.id
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
                }
            } else {
                // VIEW 2: All Loan Plans or Archive
                if (displayedPlans.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = if (selectedTab == 0) "قسط جاری فعالی وجود ندارد" else "هنوز قسط تکمیل‌شده‌ای در بایگانی وجود ندارد",
                            description = if (selectedTab == 0) "با زدن دکمه + می‌توانید وام یا قسط جدید تعریف کنید" else "اقساطی که تمام پرداخت‌های آن‌ها تکمیل شود، به صورت خودکار به بخش بایگانی منتقل می‌شوند.",
                            icon = if (selectedTab == 0) Icons.Outlined.CreditScore else Icons.Default.Archive
                        )
                    }
                } else {
                    items(displayedPlans, key = { it.id }) { plan ->
                    val now = System.currentTimeMillis()
                    val planOverdueCount = allInstallments.count { it.planId == plan.id && !it.isPaid && it.dueDateMillis < now }
                    val hasOverdue = planOverdueCount > 0

                    val progress = if (plan.numberOfInstallments > 0) plan.paidInstallments.toFloat() / plan.numberOfInstallments.toFloat() else 0f
                    val percentPaid = (progress * 100).toInt()
                    val paidSoFar = plan.paidInstallments.coerceAtMost(plan.numberOfInstallments) * plan.installmentAmount
                    val remainingAmount = ((plan.numberOfInstallments - plan.paidInstallments).coerceAtLeast(0)) * plan.installmentAmount
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
            onDismiss = { showAddDialog = false },
            onSave = { title, creditor, totalLoanAmount, count, alreadyPaidCount, amount, day, startDateMillis, enableReminder, offsetDays, reminderHour, reminderMinute ->
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
        EditInstallmentPlanDialog(
            plan = plan,
            currency = userPreferences.currency,
            onDismiss = { planToEdit = null },
            onSave = { newTitle, newCreditor, newTotalLoanAmount, newDay, newStartDateMillis, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    val effectiveLoanTotal = if (newTotalLoanAmount > 0L) newTotalLoanAmount else plan.totalAmount
                    repository.updateInstallmentPlan(
                        plan.copy(
                            title = newTitle,
                            creditorOrCompany = newCreditor,
                            totalLoanAmount = effectiveLoanTotal,
                            dueDayOfMonth = newDay,
                            startDateMillis = newStartDateMillis
                        )
                    )
                    if (enableReminder && !plan.isCompleted) {
                        val basePersianDate = PersianDateHelper.fromMillis(newStartDateMillis)
                        val nextUnpaidDueMillis = PersianDateHelper.addMonthsToPersianDate(
                            startYear = basePersianDate.year,
                            startMonth = basePersianDate.month,
                            dayOfMonth = newDay,
                            monthsToAdd = plan.paidInstallments
                        )
                        val triggerMillis = computeTriggerTimeMillis(
                            dueDateMillis = nextUnpaidDueMillis,
                            offsetDays = offsetDays,
                            hour = reminderHour,
                            minute = reminderMinute
                        )
                        val remTitle = "یادآور قسط: $newTitle"
                        val remDesc = "مبلغ هر قسط: ${PersianNumberFormatter.formatCurrency(plan.installmentAmount, userPreferences.currency)}"
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
                    } else if (!enableReminder) {
                        repository.removeTargetReminders(ReminderTargetType.INSTALLMENT, plan.id)
                    }
                    Toast.makeText(context, "اقساط بروزرسانی شد", Toast.LENGTH_SHORT).show()
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
}

private fun computeTriggerTimeMillis(
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
        enableReminder: Boolean,
        offsetDays: Int,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var creditor by remember { mutableStateOf("") }
    var totalLoanAmountText by remember { mutableStateOf("") }
    var countText by remember { mutableStateOf("12") }
    var alreadyPaidText by remember { mutableStateOf("0") }
    var amountText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("1") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showStartDatePicker by remember { mutableStateOf(false) }

    var enableReminder by remember { mutableStateOf(true) }
    var offsetDays by remember { mutableIntStateOf(0) }
    var reminderHour by remember { mutableIntStateOf(9) }
    var reminderMinute by remember { mutableIntStateOf(0) }

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
            if (title.isNotBlank() && count > 0 && amount > 0L) {
                val effectiveTotalLoan = if (totalLoan > 0L) totalLoan else (count * amount)
                onSave(
                    title,
                    creditor,
                    effectiveTotalLoan,
                    count,
                    paidBefore.coerceIn(0, count),
                    amount,
                    day,
                    startDateMillis,
                    enableReminder,
                    offsetDays,
                    reminderHour,
                    reminderMinute
                )
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
            Text("تاریخ شروع: ${PersianDateHelper.formatToPersianDate(startDateMillis)}")
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
            label = "روز سررسید ماهانه",
            leadingIcon = Icons.Outlined.CalendarMonth,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
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
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        creditor: String,
        totalLoanAmount: Long,
        day: Int,
        startDateMillis: Long,
        enableReminder: Boolean,
        offsetDays: Int,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Unit
) {
    var title by remember { mutableStateOf(plan.title) }
    var creditor by remember { mutableStateOf(plan.creditorOrCompany) }
    var totalLoanAmountText by remember {
        mutableStateOf(
            (if (plan.totalLoanAmount > 0L) plan.totalLoanAmount else plan.totalAmount).toString()
        )
    }
    var dayText by remember { mutableStateOf(plan.dueDayOfMonth.toString()) }
    var startDateMillis by remember { mutableLongStateOf(plan.startDateMillis) }
    var showStartDatePicker by remember { mutableStateOf(false) }

    var enableReminder by remember { mutableStateOf(true) }
    var offsetDays by remember { mutableIntStateOf(0) }
    var reminderHour by remember { mutableIntStateOf(9) }
    var reminderMinute by remember { mutableIntStateOf(0) }

    GraphicalFormDialog(
        title = "ویرایش اقساط",
        icon = Icons.Default.EventRepeat,
        confirmText = "بروزرسانی",
        onConfirm = {
            val day = dayText.toIntOrNull()?.coerceIn(1, 31) ?: plan.dueDayOfMonth
            val parsedLoanTotal = PersianNumberFormatter.parseAmount(totalLoanAmountText)
            if (title.isNotBlank()) {
                onSave(
                    title,
                    creditor,
                    if (parsedLoanTotal > 0L) parsedLoanTotal else plan.totalAmount,
                    day,
                    startDateMillis,
                    enableReminder,
                    offsetDays,
                    reminderHour,
                    reminderMinute
                )
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
        OutlinedButton(
            onClick = { showStartDatePicker = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("تاریخ شروع: ${PersianDateHelper.formatToPersianDate(startDateMillis)}")
        }
        DodotaTextField(
            value = PersianNumberFormatter.formatDigits(dayText, currency),
            onValueChange = { dayText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
            label = "روز سررسید ماهانه",
            leadingIcon = Icons.Outlined.CalendarMonth,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
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
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }
}

