package ir.mmd.dodota.ui.screens.installments

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

@Composable
fun InstallmentDetailScreen(
    planId: Long,
    repository: DodotaRepository,
    userPreferences: UserPreferences,
    accounts: List<AccountEntity>,
    onBack: () -> Unit
) {
    val planFlow = remember(planId) { repository.getInstallmentPlanFlow(planId) }
    val plan by planFlow.collectAsState(initial = null)

    val installmentsFlow = remember(planId) { repository.getInstallmentsForPlan(planId) }
    val installments by installmentsFlow.collectAsState(initial = emptyList())

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val accentColor = MaterialTheme.colorScheme.primary
    val accentLight = MaterialTheme.colorScheme.secondary

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            DodotaTopBar(
                title = plan?.title ?: "جزئیات اقساط",
                onBackClick = onBack
            )
        }
    ) { paddingValues ->
        if (plan == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val p = plan!!
            val paidCount = if (installments.isNotEmpty()) installments.count { it.isPaid } else p.paidInstallments
            val totalCount = p.numberOfInstallments.coerceAtLeast(1)
            val progress = (paidCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
            val percentPaid = (progress * 100).toInt()

            val paidSoFarAmount = if (installments.isNotEmpty()) {
                installments.filter { it.isPaid }.sumOf { it.amount }
            } else {
                paidCount * p.installmentAmount
            }
            val totalPlanAmount = if (installments.isNotEmpty()) {
                installments.sumOf { it.amount }
            } else {
                totalCount * p.installmentAmount
            }
            val remainingAmount = (totalPlanAmount - paidSoFarAmount).coerceAtLeast(0L)

            val endDateMillis = installments.maxByOrNull { it.installmentIndex }?.dueDateMillis
                ?: PersianDateHelper.addMonthsToPersianDate(
                    startYear = PersianDateHelper.fromMillis(p.startDateMillis).year,
                    startMonth = PersianDateHelper.fromMillis(p.startDateMillis).month,
                    dayOfMonth = p.dueDayOfMonth,
                    monthsToAdd = (p.numberOfInstallments - 1).coerceAtLeast(0)
                )

            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                label = "detail_gauge"
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 50.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Iconic Graphical Outlook Header Card
                item {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.Transparent,
                        border = BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(
                                colors = listOf(accentColor, IncomeGreen.copy(alpha = 0.7f))
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
                                            accentColor.copy(alpha = 0.16f),
                                            MaterialTheme.colorScheme.surfaceContainer
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Circular Gauge Ring
                                Box(
                                    modifier = Modifier.size(86.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokePx = 8.dp.toPx()
                                        val inset = strokePx / 2f + 2f
                                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

                                        drawCircle(
                                            color = accentColor.copy(alpha = 0.1f),
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
                                        if (animatedProgress > 0.01f) {
                                            drawArc(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(accentColor, accentLight, IncomeGreen)
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
                                            text = "${PersianNumberFormatter.formatDigits(percentPaid)}٪",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (percentPaid >= 100) IncomeGreen else accentColor
                                        )
                                        Text(
                                            text = "پرداخت‌شده",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = p.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "طرف حساب: ${p.creditorOrCompany.ifBlank { "مشخص نشده" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = accentColor.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                                        ) {
                                            Text(
                                                text = "${PersianNumberFormatter.formatDigits(paidCount)} از ${PersianNumberFormatter.formatDigits(totalCount)} قسط پاس شده",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = accentColor,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }

                                        val overdueCount = installments.count { !it.isPaid && it.dueDateMillis < System.currentTimeMillis() }
                                        if (overdueCount > 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = ExpenseRed.copy(alpha = 0.16f),
                                                border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.55f))
                                            ) {
                                                Text(
                                                    text = "${PersianNumberFormatter.formatDigits(overdueCount)} قسط معوقه",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = ExpenseRed,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Total Loan Amount & Per-Installment Amount Row
                            val displayLoanTotal = if (p.totalLoanAmount > 0L) p.totalLoanAmount else totalPlanAmount
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "مبلغ کل وام:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(displayLoanTotal, userPreferences.currency),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = accentColor
                                        )
                                    }
                                    Text(
                                        text = "هر قسط: ${PersianNumberFormatter.formatCurrency(p.installmentAmount, userPreferences.currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
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
                                    shape = RoundedCornerShape(14.dp),
                                    color = IncomeGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.45f)),
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
                                                modifier = Modifier.size(14.dp)
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
                                            text = PersianNumberFormatter.formatCurrency(paidSoFarAmount, userPreferences.currency),
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
                                    color = accentColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
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
                                                modifier = Modifier.size(14.dp)
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

                            Spacer(modifier = Modifier.height(12.dp))

                            // Start ➔ End Date Roadmap
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "شروع: ${PersianDateHelper.formatToPersianDate(p.startDateMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = IncomeGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "اتمام: ${PersianDateHelper.formatToPersianDate(endDateMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            if (installments.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                val maxSeg = installments.size.coerceAtMost(24)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    installments.take(maxSeg).forEach { inst ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (inst.isPaid) IncomeGreen
                                                    else accentColor.copy(alpha = 0.25f)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "جدول اقساط",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                items(installments, key = { it.id }) { inst ->
                    val now = System.currentTimeMillis()
                    val isOverdue = !inst.isPaid && inst.dueDateMillis < now

                    val toggleInstallmentPayment = {
                        coroutineScope.launch {
                            if (inst.isPaid) {
                                repository.unpayInstallment(p, inst)
                                Toast.makeText(context, "قسط به حالت پرداخت‌نشده برگشت", Toast.LENGTH_SHORT).show()
                            } else {
                                repository.payInstallment(
                                    plan = p,
                                    installment = inst,
                                    accountId = p.accountId ?: accounts.firstOrNull()?.id
                                )
                                Toast.makeText(context, "قسط ${PersianNumberFormatter.formatDigits(inst.installmentIndex)} پرداخت شد ✓", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    GlassCard(
                        shape = RoundedCornerShape(20.dp),
                        borderColor = when {
                            inst.isPaid -> IncomeGreen.copy(alpha = 0.65f)
                            isOverdue -> ExpenseRed.copy(alpha = 0.85f)
                            else -> accentColor.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { toggleInstallmentPayment() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        inst.isPaid -> Brush.horizontalGradient(
                                            colors = listOf(IncomeGreen.copy(alpha = 0.14f), Color.Transparent)
                                        )
                                        isOverdue -> Brush.horizontalGradient(
                                            colors = listOf(ExpenseRed.copy(alpha = 0.12f), Color.Transparent)
                                        )
                                        else -> Brush.horizontalGradient(
                                            colors = listOf(accentColor.copy(alpha = 0.06f), Color.Transparent)
                                        )
                                    }
                                )
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Graphical dual-ring check avatar
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            when {
                                                inst.isPaid -> Brush.linearGradient(
                                                    colors = listOf(IncomeGreen, IncomeGreen.copy(alpha = 0.75f))
                                                )
                                                isOverdue -> Brush.linearGradient(
                                                    colors = listOf(ExpenseRed.copy(alpha = 0.25f), ExpenseRed.copy(alpha = 0.12f))
                                                )
                                                else -> Brush.linearGradient(
                                                    colors = listOf(accentColor.copy(alpha = 0.18f), accentColor.copy(alpha = 0.08f))
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (inst.isPaid) {
                                        Icon(
                                            Icons.Default.TaskAlt,
                                            contentDescription = "پرداخت شده",
                                            tint = Color.Black,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else if (isOverdue) {
                                        Icon(
                                            Icons.Default.PriorityHigh,
                                            contentDescription = "قسط معوقه",
                                            tint = ExpenseRed,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = PersianNumberFormatter.formatDigits(inst.installmentIndex),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = accentColor
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "قسط ${PersianNumberFormatter.formatDigits(inst.installmentIndex)} از ${PersianNumberFormatter.formatDigits(p.numberOfInstallments)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        textDecoration = if (inst.isPaid) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (inst.isPaid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.CalendarMonth,
                                            contentDescription = null,
                                            tint = if (isOverdue) ExpenseRed.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "سررسید: ${PersianDateHelper.formatToPersianDate(inst.dueDateMillis)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isOverdue) ExpenseRed.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    if (isOverdue) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = ExpenseRed,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "قسط معوقه",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = ExpenseRed
                                            )
                                        }
                                    }
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = PersianNumberFormatter.formatCurrency(inst.amount, userPreferences.currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = when {
                                        inst.isPaid -> IncomeGreen
                                        isOverdue -> ExpenseRed
                                        else -> accentColor
                                    }
                                )

                                // Graphical Check / Tick Pill Button
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        inst.isPaid -> IncomeGreen
                                        isOverdue -> ExpenseRed.copy(alpha = 0.16f)
                                        else -> accentColor.copy(alpha = 0.14f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            inst.isPaid -> IncomeGreen
                                            isOverdue -> ExpenseRed.copy(alpha = 0.65f)
                                            else -> accentColor.copy(alpha = 0.55f)
                                        }
                                    ),
                                    modifier = Modifier.clickable { toggleInstallmentPayment() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                inst.isPaid -> Icons.Default.CheckCircle
                                                isOverdue -> Icons.Default.WarningAmber
                                                else -> Icons.Default.RadioButtonUnchecked
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                inst.isPaid -> Color.Black
                                                isOverdue -> ExpenseRed
                                                else -> accentColor
                                            },
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = when {
                                                inst.isPaid -> "پاس شد"
                                                isOverdue -> "قسط معوقه"
                                                else -> "ثبت تیک"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = when {
                                                inst.isPaid -> Color.Black
                                                isOverdue -> ExpenseRed
                                                else -> accentColor
                                            }
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
