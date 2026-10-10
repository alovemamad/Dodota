package ir.mmd.dodota.ui.screens.installments

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditScore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val totalAllPlansAmount = plans.sumOf { it.numberOfInstallments * it.installmentAmount }
    val totalPaidSoFarAmount = plans.sumOf { it.paidInstallments.coerceAtMost(it.numberOfInstallments) * it.installmentAmount }
    val totalRemainingAmount = (totalAllPlansAmount - totalPaidSoFarAmount).coerceAtLeast(0L)
    val overallProgress = if (totalAllPlansAmount > 0L) (totalPaidSoFarAmount.toFloat() / totalAllPlansAmount.toFloat()).coerceIn(0f, 1f) else 0f
    val overallPercent = (overallProgress * 100).toInt()

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "چشم‌انداز و مدیریت اقساط",
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = InstallmentScreenPurple,
                contentColor = Color.White,
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
            // Iconic Financial Outlook Summary Card
            item {
                val animOverall by animateFloatAsState(
                    targetValue = overallProgress,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    label = "inst_overall"
                )
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(colors = listOf(InstallmentScreenPurple, IncomeGreen.copy(alpha = 0.7f)))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF211332), Color(0xFF14121D))
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(84.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokePx = 8.dp.toPx()
                                    val inset = strokePx / 2f + 2f
                                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

                                    drawCircle(
                                        color = InstallmentScreenPurple.copy(alpha = 0.1f),
                                        radius = (size.minDimension / 2f) - strokePx
                                    )
                                    drawArc(
                                        color = InstallmentScreenPurple.copy(alpha = 0.2f),
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
                                                colors = listOf(InstallmentScreenPurple, InstallmentScreenPurpleLight, IncomeGreen)
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
                                        text = "${PersianNumberFormatter.toPersianDigits(overallPercent)}٪",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (overallPercent >= 100 && totalAllPlansAmount > 0) IncomeGreen else InstallmentScreenPurpleLight
                                    )
                                    Text(
                                        text = "پرداخت‌شده",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = IncomeGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "تا الان پرداخت شده:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IncomeGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(totalPaidSoFarAmount, userPreferences.currency),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = IncomeGreen
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = InstallmentScreenPurple.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, InstallmentScreenPurple.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "مانده کل اقساط:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = InstallmentScreenPurpleLight,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(totalRemainingAmount, userPreferences.currency),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (plans.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "هیچ برنامه قسطی ثبت نشده است",
                        description = "برای ثبت اقساط وام یا خریدهای قسطی خود (با امکان تعیین تاریخ شروع و اقساط پرداخت‌شده قبلی) روی + کلیک کنید.",
                        icon = Icons.Outlined.CreditScore
                    )
                }
            } else {
                items(plans, key = { it.id }) { plan ->
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
                        borderColor = InstallmentScreenPurple.copy(alpha = 0.65f),
                        isHighlighted = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToDetail(plan.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                                    Text(
                                        text = "شروع: ${PersianDateHelper.formatToPersianDate(plan.startDateMillis)} • اتمام: ${PersianDateHelper.formatToPersianDate(endDateMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InstallmentScreenPurpleLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (plan.isCompleted) IncomeGreen.copy(alpha = 0.15f) else InstallmentScreenPurple.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = "${PersianNumberFormatter.toPersianDigits(percentPaid)}٪ پرداخت شده",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (plan.isCompleted) IncomeGreen else InstallmentScreenPurpleLight,
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

                            // Paid So Far vs Remaining Outlook Boxes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = IncomeGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.35f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Verified,
                                                contentDescription = null,
                                                tint = IncomeGreen,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "تا الان پرداخت کردم",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IncomeGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
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
                                    color = InstallmentScreenPurple.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, InstallmentScreenPurple.copy(alpha = 0.35f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.HourglassBottom,
                                                contentDescription = null,
                                                tint = InstallmentScreenPurpleLight,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "مانده اقساط",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = InstallmentScreenPurpleLight,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(remainingAmount, userPreferences.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = InstallmentScreenPurpleLight,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (percentPaid >= 100) IncomeGreen else InstallmentScreenPurple,
                                trackColor = MaterialTheme.colorScheme.surface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "هر قسط: ${PersianNumberFormatter.formatCurrency(plan.installmentAmount, userPreferences.currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${PersianNumberFormatter.toPersianDigits(plan.paidInstallments)} از ${PersianNumberFormatter.toPersianDigits(plan.numberOfInstallments)} قسط پاس شده",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InstallmentScreenPurpleLight,
                                    fontWeight = FontWeight.ExtraBold
                                )
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
            onSave = { title, creditor, count, alreadyPaidCount, amount, day, startDateMillis, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    val total = count * amount
                    val safePaid = alreadyPaidCount.coerceIn(0, count)
                    val planId = repository.createInstallmentPlan(
                        plan = InstallmentPlanEntity(
                            title = title,
                            creditorOrCompany = creditor,
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
                        val remDesc = "قسط شماره ${PersianNumberFormatter.toPersianDigits(safePaid + 1)} به مبلغ ${PersianNumberFormatter.formatCurrency(amount, userPreferences.currency)}"
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

                    Toast.makeText(context, "برنامه اقساط با موفقیت ایجاد شد", Toast.LENGTH_SHORT).show()
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
            onSave = { newTitle, newCreditor, newDay, newStartDateMillis, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    repository.updateInstallmentPlan(
                        plan.copy(
                            title = newTitle,
                            creditorOrCompany = newCreditor,
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
                    Toast.makeText(context, "برنامه اقساط بروزرسانی شد", Toast.LENGTH_SHORT).show()
                    planToEdit = null
                }
            }
        )
    }

    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("حذف برنامه اقساط") },
            text = { Text("آیا از حذف برنامه اقساط «${plan.title}» اطمینان دارید؟ تمامی اقساط و یادآورهای مربوطه نیز حذف خواهند شد.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteInstallmentPlan(plan)
                            Toast.makeText(context, "برنامه اقساط حذف شد", Toast.LENGTH_SHORT).show()
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
    var countText by remember { mutableStateOf("12") }
    var alreadyPaidText by remember { mutableStateOf("0") }
    var amountText by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf("1") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showStartDatePicker by remember { mutableStateOf(false) }

    var enableReminder by remember { mutableStateOf(true) }
    var offsetDays by remember { mutableIntStateOf(1) }
    var reminderHour by remember { mutableIntStateOf(9) }
    var reminderMinute by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن وام یا برنامه اقساط", fontWeight = FontWeight.Bold) },
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
                    label = { Text("عنوان وام یا کالا (مثال: وام مسکن، خرید لپ‌تاپ)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = creditor,
                    onValueChange = { creditor = it },
                    label = { Text("طرف حساب / بانک") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Start Date Button (Scrollable Wheel Persian Date Picker)
                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تاریخ شروع اقساط: ${PersianDateHelper.formatToPersianDate(startDateMillis)}")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
                        label = { Text("کل اقساط") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = alreadyPaidText,
                        onValueChange = { alreadyPaidText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
                        label = { Text("پرداخت‌شده از قبل") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
                    label = { Text("روز سررسید در هر ماه (۱ تا ۳۱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                PersianAmountInput(
                    value = amountText,
                    onValueChange = { amountText = it },
                    currency = currency,
                    label = "مبلغ هر قسط"
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
                    title = "یادآور و آلارم سررسید قسط"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = countText.toIntOrNull() ?: 0
                    val paidBefore = alreadyPaidText.toIntOrNull() ?: 0
                    val amount = PersianNumberFormatter.parseAmount(amountText)
                    val day = dayText.toIntOrNull()?.coerceIn(1, 31) ?: 1
                    if (title.isNotBlank() && count > 0 && amount > 0L) {
                        onSave(
                            title,
                            creditor,
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
                }
            ) {
                Text("ایجاد اقساط")
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
    var dayText by remember { mutableStateOf(plan.dueDayOfMonth.toString()) }
    var startDateMillis by remember { mutableLongStateOf(plan.startDateMillis) }
    var showStartDatePicker by remember { mutableStateOf(false) }

    var enableReminder by remember { mutableStateOf(true) }
    var offsetDays by remember { mutableIntStateOf(1) }
    var reminderHour by remember { mutableIntStateOf(9) }
    var reminderMinute by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش برنامه اقساط", fontWeight = FontWeight.Bold) },
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
                    label = { Text("عنوان وام یا کالا") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = creditor,
                    onValueChange = { creditor = it },
                    label = { Text("طرف حساب / بانک") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تاریخ شروع اقساط: ${PersianDateHelper.formatToPersianDate(startDateMillis)}")
                }
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = PersianNumberFormatter.toEnglishDigits(it).filter { c -> c.isDigit() } },
                    label = { Text("روز ماه سررسید (۱ تا ۳۱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
                    title = "یادآور و آلارم سررسید قسط"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val day = dayText.toIntOrNull()?.coerceIn(1, 31) ?: plan.dueDayOfMonth
                    if (title.isNotBlank()) {
                        onSave(title, creditor, day, startDateMillis, enableReminder, offsetDays, reminderHour, reminderMinute)
                    }
                }
            ) {
                Text("بروزرسانی")
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
