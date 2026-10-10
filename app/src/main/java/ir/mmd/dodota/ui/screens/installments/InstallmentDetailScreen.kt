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

private val InstallmentAccentPurple = Color(0xFFA855F7)
private val InstallmentAccentPurpleLight = Color(0xFFC084FC)

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

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = plan?.title ?: "چشم‌انداز و جزئیات اقساط",
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
                                colors = listOf(InstallmentAccentPurple, IncomeGreen.copy(alpha = 0.7f))
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
                                            Color(0xFF211332),
                                            Color(0xFF14121D)
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
                                            color = InstallmentAccentPurple.copy(alpha = 0.1f),
                                            radius = (size.minDimension / 2f) - strokePx
                                        )
                                        drawArc(
                                            color = InstallmentAccentPurple.copy(alpha = 0.2f),
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
                                                    colors = listOf(InstallmentAccentPurple, InstallmentAccentPurpleLight, IncomeGreen)
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
                                            text = "${PersianNumberFormatter.toPersianDigits(percentPaid)}٪",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (percentPaid >= 100) IncomeGreen else InstallmentAccentPurpleLight
                                        )
                                        Text(
                                            text = "پرداخت‌شده",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = p.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "طرف حساب: ${p.creditorOrCompany.ifBlank { "مشخص نشده" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = InstallmentAccentPurple.copy(alpha = 0.22f),
                                        border = BorderStroke(1.dp, InstallmentAccentPurple.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "${PersianNumberFormatter.toPersianDigits(paidCount)} از ${PersianNumberFormatter.toPersianDigits(totalCount)} قسط پاس شده",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = InstallmentAccentPurpleLight,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Paid So Far vs Remaining Outlook Boxes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = IncomeGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.45f)),
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
                                    color = InstallmentAccentPurple.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, InstallmentAccentPurple.copy(alpha = 0.45f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.HourglassBottom,
                                                contentDescription = null,
                                                tint = InstallmentAccentPurpleLight,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "مانده کل اقساط",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = InstallmentAccentPurpleLight,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(remainingAmount, userPreferences.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
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
                                    color = InstallmentAccentPurpleLight,
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
                                                    else InstallmentAccentPurple.copy(alpha = 0.25f)
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
                        text = "جدول اقساط (برای تیک زدن یا لغو پرداخت روی هر قسط بزنید)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                items(installments, key = { it.id }) { inst ->
                    GlassCard(
                        shape = RoundedCornerShape(18.dp),
                        borderColor = if (inst.isPaid) IncomeGreen.copy(alpha = 0.6f)
                        else InstallmentAccentPurple.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
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
                                    Toast.makeText(context, "قسط ${PersianNumberFormatter.toPersianDigits(inst.installmentIndex)} پرداخت شد ✓", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (inst.isPaid) IncomeGreen else InstallmentAccentPurple.copy(alpha = 0.2f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (inst.isPaid) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Text(
                                                text = PersianNumberFormatter.toPersianDigits(inst.installmentIndex),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = InstallmentAccentPurpleLight
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "قسط ${PersianNumberFormatter.toPersianDigits(inst.installmentIndex)} از ${PersianNumberFormatter.toPersianDigits(p.numberOfInstallments)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        textDecoration = if (inst.isPaid) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.CalendarMonth,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "سررسید: ${PersianDateHelper.formatToPersianDate(inst.dueDateMillis)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = PersianNumberFormatter.formatCurrency(inst.amount, userPreferences.currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (inst.isPaid) IncomeGreen else InstallmentAccentPurpleLight
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Checkbox(
                                    checked = inst.isPaid,
                                    onCheckedChange = { checked ->
                                        coroutineScope.launch {
                                            if (checked && !inst.isPaid) {
                                                repository.payInstallment(
                                                    plan = p,
                                                    installment = inst,
                                                    accountId = p.accountId ?: accounts.firstOrNull()?.id
                                                )
                                                Toast.makeText(context, "قسط پرداخت شد ✓", Toast.LENGTH_SHORT).show()
                                            } else if (!checked && inst.isPaid) {
                                                repository.unpayInstallment(p, inst)
                                                Toast.makeText(context, "پرداخت قسط لغو شد", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = IncomeGreen,
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = InstallmentAccentPurple
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
