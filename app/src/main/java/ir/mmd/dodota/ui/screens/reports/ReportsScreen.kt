package ir.mmd.dodota.ui.screens.reports

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.components.charts.*
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.ui.theme.TransferBlue
import ir.mmd.dodota.ui.theme.WarningAmber
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter

enum class ReportPeriod(val title: String) {
    THIS_MONTH("این ماه"),
    LAST_THREE_MONTHS("۳ ماه گذشته"),
    THIS_YEAR("امسال"),
    ALL("همه")
}

enum class ChartViewType(val title: String, val icon: ImageVector) {
    PIE("سهم دایره‌ای", Icons.Default.DonutLarge),
    BAR("مقایسه میله‌ای", Icons.Default.BarChart),
    LINE("روند خطی", Icons.Default.ShowChart)
}

@Composable
fun ReportsScreen(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    userPreferences: UserPreferences,
    onBack: (() -> Unit)? = null
) {
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.THIS_MONTH) }
    var selectedChartType by remember { mutableStateOf(ChartViewType.PIE) }
    var pieCategoryType by remember { mutableStateOf(TransactionType.EXPENSE) }
    val currentPDate = PersianDateHelper.getCurrentPersianDate()

    val filteredTransactions = remember(transactions, selectedPeriod) {
        when (selectedPeriod) {
            ReportPeriod.THIS_MONTH -> {
                val start = PersianDateHelper.getStartOfMonthMillis(currentPDate.year, currentPDate.month)
                val end = PersianDateHelper.getEndOfMonthMillis(currentPDate.year, currentPDate.month)
                transactions.filter { it.dateMillis in start..end }
            }
            ReportPeriod.LAST_THREE_MONTHS -> {
                val startMonth = (currentPDate.month - 2).coerceAtLeast(1)
                val start = PersianDateHelper.getStartOfMonthMillis(currentPDate.year, startMonth)
                val end = PersianDateHelper.getEndOfMonthMillis(currentPDate.year, currentPDate.month)
                transactions.filter { it.dateMillis in start..end }
            }
            ReportPeriod.THIS_YEAR -> {
                val start = PersianDateHelper.getStartOfMonthMillis(currentPDate.year, 1)
                val end = PersianDateHelper.getEndOfMonthMillis(currentPDate.year, 12)
                transactions.filter { it.dateMillis in start..end }
            }
            ReportPeriod.ALL -> transactions
        }
    }

    val totalIncome = filteredTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpense = filteredTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val netSavings = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0) {
        ((netSavings.coerceAtLeast(0).toDouble() / totalIncome.toDouble()) * 100).toInt()
    } else 0
    val expenseRatio = if (totalIncome > 0) {
        (totalExpense.toFloat() / totalIncome.toFloat()).coerceIn(0f, 1f)
    } else if (totalExpense > 0) 1f else 0f

    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    // Smart KPI Analytics
    val expenseTxs = remember(filteredTransactions) {
        filteredTransactions.filter { it.type == TransactionType.EXPENSE }
    }
    val biggestExpenseTx = remember(expenseTxs) {
        expenseTxs.maxByOrNull { it.amount }
    }
    val activeDaysCount = remember(filteredTransactions) {
        filteredTransactions.map {
            val d = PersianDateHelper.fromMillis(it.dateMillis)
            "${d.year}-${d.month}-${d.day}"
        }.distinct().size.coerceAtLeast(1)
    }
    val dailyAvgExpense = if (totalExpense > 0) totalExpense / activeDaysCount else 0L

    // Slices for Pie Chart
    val pieSlices = remember(filteredTransactions, categories, pieCategoryType) {
        val defaultHexColors = listOf(
            "#EF4444", "#3B82F6", "#10B981", "#F59E0B", "#8B5CF6",
            "#06B6D4", "#EC4899", "#6366F1", "#EAB308", "#14B8A6"
        )
        filteredTransactions
            .filter { it.type == pieCategoryType }
            .groupBy { it.categoryId }
            .entries
            .mapIndexed { idx, (catId, txs) ->
                val cat = catId?.let { categoryMap[it] }
                val name = cat?.name ?: if (pieCategoryType == TransactionType.EXPENSE) "سایر هزینه‌ها" else "سایر درآمدها"
                val sum = txs.sumOf { it.amount }
                val hex = cat?.colorHex ?: defaultHexColors[idx % defaultHexColors.size]
                val color = try {
                    Color(android.graphics.Color.parseColor(hex))
                } catch (_: Exception) {
                    Color(0xFFEF4444)
                }
                val iconName = cat?.iconName ?: if (pieCategoryType == TransactionType.EXPENSE) "shopping_cart" else "payments"
                ChartSlice(
                    id = catId,
                    name = name,
                    amount = sum,
                    color = color,
                    iconName = iconName,
                    colorHex = hex,
                    transactionCount = txs.size
                )
            }
            .sortedByDescending { it.amount }
    }

    // Groups for Bar Comparison Chart (Last 6 Months)
    val barGroups = remember(transactions, selectedPeriod) {
        val monthsList = (1..currentPDate.month).toList().takeLast(6)
        monthsList.map { m ->
            val start = PersianDateHelper.getStartOfMonthMillis(currentPDate.year, m)
            val end = PersianDateHelper.getEndOfMonthMillis(currentPDate.year, m)
            val monthTxs = transactions.filter { it.dateMillis in start..end }
            val inc = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val exp = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val label = PersianDateHelper.PERSIAN_MONTHS.getOrNull(m - 1) ?: "ماه $m"
            BarComparisonGroup(label = label, incomeAmount = inc, expenseAmount = exp)
        }
    }

    // Points for Trend Line Chart
    val trendPoints = remember(filteredTransactions, selectedPeriod) {
        if (selectedPeriod == ReportPeriod.THIS_MONTH) {
            val groupedByDay = filteredTransactions
                .groupBy {
                    val pDate = PersianDateHelper.fromMillis(it.dateMillis)
                    pDate.day
                }
                .toSortedMap()

            if (groupedByDay.isEmpty()) {
                emptyList()
            } else {
                var runningNet = 0L
                groupedByDay.map { (day, txs) ->
                    val dayNet = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } -
                            txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    runningNet += dayNet
                    TrendPoint(
                        label = "${PersianNumberFormatter.formatDigits(day, userPreferences.currency)} ${PersianDateHelper.PERSIAN_MONTHS[currentPDate.month - 1]}",
                        value = runningNet
                    )
                }
            }
        } else {
            barGroups.map { group ->
                TrendPoint(label = group.label, value = group.incomeAmount - group.expenseAmount)
            }
        }
    }

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "آمار و نمودارهای هوشمند",
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Timeframe Segmented Control
            item {
                GlassCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ReportPeriod.values().forEach { period ->
                            val isSelected = selectedPeriod == period
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedPeriod = period }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = period.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Iconic Financial Health & Balance Outlook Hero Card
            item {
                val healthColor = when {
                    savingsRate >= 30 -> IncomeGreen
                    netSavings >= 0 -> TransferBlue
                    else -> ExpenseRed
                }
                val animatedSavingsProgress by animateFloatAsState(
                    targetValue = (savingsRate / 100f).coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                    label = "savings_gauge"
                )

                GlassCard(
                    shape = RoundedCornerShape(26.dp),
                    isHighlighted = true,
                    borderColor = healthColor.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        healthColor.copy(alpha = 0.16f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular Savings Rate Gauge
                            Box(
                                modifier = Modifier.size(76.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokePx = 8.dp.toPx()
                                    drawArc(
                                        color = healthColor.copy(alpha = 0.16f),
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        color = healthColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * animatedSavingsProgress,
                                        useCenter = false,
                                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${PersianNumberFormatter.formatDigits(savingsRate, userPreferences.currency)}٪",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = healthColor
                                    )
                                    Text(
                                        text = "پس‌انداز",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Insights,
                                        contentDescription = null,
                                        tint = healthColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تراز خالص (${selectedPeriod.title})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = healthColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = PersianNumberFormatter.formatCurrency(netSavings, userPreferences.currency, showSign = true),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (netSavings >= 0) IncomeGreen else ExpenseRed
                                )
                                Text(
                                    text = "${PersianNumberFormatter.formatDigits(filteredTransactions.size, userPreferences.currency)} تراکنش در این بازه",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Income vs Expense Split Boxes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = IncomeGreen.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.35f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.SouthWest,
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "مجموع ورودی (درآمد)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IncomeGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "+ ${PersianNumberFormatter.formatCurrency(totalIncome, userPreferences.currency)}",
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
                                color = ExpenseRed.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.35f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.NorthEast,
                                            contentDescription = null,
                                            tint = ExpenseRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "مجموع خروجی (هزینه)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ExpenseRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "- ${PersianNumberFormatter.formatCurrency(totalExpense, userPreferences.currency)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ExpenseRed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Expense to Income Ratio Bar
                        if (totalIncome > 0 || totalExpense > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "نسبت مصرف به درآمد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "${PersianNumberFormatter.formatDigits((expenseRatio * 100).toInt(), userPreferences.currency)}٪ مصرف‌شده",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (expenseRatio > 0.85f) ExpenseRed else WarningAmber
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            LinearProgressIndicator(
                                progress = { expenseRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (expenseRatio > 0.85f) ExpenseRed else WarningAmber,
                                trackColor = IncomeGreen.copy(alpha = 0.22f)
                            )
                        }
                    }
                }
            }

            // 3. Smart KPI Mini Cards (Daily Avg Expense & Top Expense)
            if (expenseTxs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassCard(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(WarningAmber.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = WarningAmber,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "میانگین روزهای خرج",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = PersianNumberFormatter.formatCurrency(dailyAvgExpense, userPreferences.currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        GlassCard(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ExpenseRed.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = ExpenseRed,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "بزرگ‌ترین هزینه",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = PersianNumberFormatter.formatCurrency(biggestExpenseTx?.amount ?: 0L, userPreferences.currency),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ExpenseRed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Graphical Chart Type Switcher Tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChartViewType.values().forEach { chartType ->
                        val isSelected = selectedChartType == chartType
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedChartType = chartType }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = chartType.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = chartType.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 5. Active Chart Display Glass Card
            item {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    isHighlighted = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        when (selectedChartType) {
                            ChartViewType.PIE -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (pieCategoryType == TransactionType.EXPENSE) "کالبدشکافی هزینه‌ها" else "کالبدشکافی منابع درآمد",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "برای مشاهده جزئیات، روی هر بخش نمودار ضربه بزنید",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    // Expense vs Income Pill Toggle
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = if (pieCategoryType == TransactionType.EXPENSE) ExpenseRed else Color.Transparent,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(9.dp))
                                                .clickable { pieCategoryType = TransactionType.EXPENSE }
                                        ) {
                                            Text(
                                                text = "هزینه‌ها",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (pieCategoryType == TransactionType.EXPENSE) Color.White else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = if (pieCategoryType == TransactionType.INCOME) IncomeGreen else Color.Transparent,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(9.dp))
                                                .clickable { pieCategoryType = TransactionType.INCOME }
                                        ) {
                                            Text(
                                                text = "درآمدها",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (pieCategoryType == TransactionType.INCOME) Color.White else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                val targetTotal = if (pieCategoryType == TransactionType.EXPENSE) totalExpense else totalIncome
                                InteractiveDonutChart(
                                    slices = pieSlices,
                                    totalAmount = targetTotal,
                                    currency = userPreferences.currency
                                )
                            }

                            ChartViewType.BAR -> {
                                Text(
                                    text = "مقایسه میله‌ای درآمد و هزینه ماه‌های اخیر",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "برای مشاهده تراز دقیق هر ماه، روی ستون آن ماه ضربه بزنید",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                BarComparisonChart(
                                    groups = barGroups,
                                    currency = userPreferences.currency
                                )
                            }

                            ChartViewType.LINE -> {
                                Text(
                                    text = "نمودار خطی روند تراز مالی",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "نمایش شیب تغییرات موجودی و پس‌انداز انباشته در طول زمان",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                TrendLineChart(
                                    points = trendPoints,
                                    currency = userPreferences.currency,
                                    lineColor = if (netSavings >= 0) IncomeGreen else ExpenseRed,
                                    chartTitle = "تراز انباشته دوره"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
