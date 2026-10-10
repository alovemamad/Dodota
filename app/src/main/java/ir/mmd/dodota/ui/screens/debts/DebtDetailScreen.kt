package ir.mmd.dodota.ui.screens.debts

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.model.DebtPaymentEntity
import ir.mmd.dodota.data.model.DebtType
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.EmptyStateCard
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.components.PersianAmountInput
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

private val DetailCheckGold = Color(0xFFF59E0B)

@Composable
fun DebtDetailScreen(
    debtId: Long,
    repository: DodotaRepository,
    userPreferences: UserPreferences,
    accounts: List<AccountEntity>,
    onBack: () -> Unit
) {
    val debtFlow = remember(debtId) { repository.getDebtFlow(debtId) }
    val debt by debtFlow.collectAsState(initial = null)

    val paymentsFlow = remember(debtId) { repository.getDebtPayments(debtId) }
    val payments by paymentsFlow.collectAsState(initial = emptyList())

    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentToDelete by remember { mutableStateOf<DebtPaymentEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = debt?.personOrCompanyName ?: "جزئیات و چشم‌انداز تسویه",
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            if (debt != null && (debt!!.totalAmount - debt!!.paidAmount) > 0) {
                FloatingActionButton(
                    onClick = { showPaymentDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "ثبت پرداخت/دریافت")
                }
            }
        }
    ) { paddingValues ->
        if (debt == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val d = debt!!
            val paidSoFar = d.paidAmount.coerceIn(0L, d.totalAmount)
            val remaining = (d.totalAmount - paidSoFar).coerceAtLeast(0L)
            val isOwe = d.type == DebtType.DEBT_I_OWE
            val isCheck = d.isCheck || d.personOrCompanyName.contains("چک") || d.notes.contains("چک")
            val accentColor = when {
                isCheck -> DetailCheckGold
                isOwe -> ExpenseRed
                else -> IncomeGreen
            }
            val progress = if (d.totalAmount > 0L) (paidSoFar.toFloat() / d.totalAmount.toFloat()).coerceIn(0f, 1f) else 0f
            val percentPaid = (progress * 100).toInt()
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                label = "debt_detail_gauge"
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Iconic Financial Outlook Header Card
                item {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.Transparent,
                        border = BorderStroke(
                            1.5.dp,
                            Brush.linearGradient(colors = listOf(accentColor, IncomeGreen.copy(alpha = 0.7f)))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF1E1420),
                                            Color(0xFF12121A)
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
                                // Circular Gauge
                                Box(
                                    modifier = Modifier.size(84.dp),
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
                                                brush = Brush.linearGradient(colors = listOf(accentColor, IncomeGreen)),
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
                                            color = if (percentPaid >= 100) IncomeGreen else accentColor
                                        )
                                        Text(
                                            text = "تسویه‌شده",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = accentColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = if (isCheck) "چک صیادی / بانکی" else d.type.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = d.personOrCompanyName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "مبلغ کل: ${PersianNumberFormatter.formatCurrency(d.totalAmount, userPreferences.currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Paid So Far vs Remaining Boxes
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
                                                text = if (isOwe) "تا الان پرداخت کردم" else "تا الان دریافت کردم",
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
                                    shape = RoundedCornerShape(14.dp),
                                    color = accentColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
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
                                                text = "مانده تسویه‌نشده",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentColor,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(remaining, userPreferences.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
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
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "تاریخچه پرداخت‌ها / تسویه‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (payments.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "پرداختی ثبت نشده است",
                            description = "برای ثبت پرداخت یا تسویه جزئی از دکمه + پایین صفحه استفاده کنید."
                        )
                    }
                } else {
                    items(payments, key = { it.id }) { payment ->
                        GlassCard(
                            shape = RoundedCornerShape(16.dp),
                            borderColor = IncomeGreen.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = payment.notes.ifBlank { "پرداخت ثبت‌شده" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = PersianDateHelper.formatToPersianDate(payment.dateMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = PersianNumberFormatter.formatCurrency(payment.amount, userPreferences.currency),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = IncomeGreen
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { paymentToDelete = payment },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "حذف پرداخت",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
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

    if (showPaymentDialog && debt != null) {
        var paymentAmountText by remember { mutableStateOf("") }
        var paymentNote by remember { mutableStateOf("") }
        var selectedAccId by remember { mutableStateOf<Long?>(accounts.firstOrNull()?.id) }

        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text(if (debt!!.type == DebtType.DEBT_I_OWE) "ثبت پرداخت بدهی / چک" else "ثبت دریافت طلب") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PersianAmountInput(
                        value = paymentAmountText,
                        onValueChange = { paymentAmountText = it },
                        currency = userPreferences.currency,
                        label = "مبلغ پرداختی"
                    )
                    OutlinedTextField(
                        value = paymentNote,
                        onValueChange = { paymentNote = it },
                        label = { Text("توضیحات (اختیاری)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = PersianNumberFormatter.parseAmount(paymentAmountText)
                        if (amount > 0) {
                            coroutineScope.launch {
                                repository.recordDebtPayment(
                                    debt = debt!!,
                                    amount = amount,
                                    accountId = selectedAccId,
                                    notes = paymentNote
                                )
                                Toast.makeText(context, "پرداخت با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                                showPaymentDialog = false
                            }
                        }
                    }
                ) {
                    Text("ثبت پرداخت")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    paymentToDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("حذف پرداخت") },
            text = { Text("آیا از حذف این پرداخت به مبلغ ${PersianNumberFormatter.formatCurrency(payment.amount, userPreferences.currency)} اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        if (debt != null) {
                            coroutineScope.launch {
                                repository.deleteDebtPayment(debt!!, payment)
                                Toast.makeText(context, "پرداخت حذف شد", Toast.LENGTH_SHORT).show()
                                paymentToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}
