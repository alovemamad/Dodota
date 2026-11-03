package ir.mmd.dodota.ui.screens.debts

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.*
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.util.AlarmHelper
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ChecksScreen(
    debts: List<DebtEntity>,
    userPreferences: UserPreferences,
    repository: DodotaRepository,
    accounts: List<AccountEntity>,
    activeReminders: List<ReminderEntity> = emptyList(),
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val accentLight = MaterialTheme.colorScheme.secondary

    val checkItems = remember(debts) {
        debts.filter { it.isCheck || it.personOrCompanyName.contains("چک") || it.notes.contains("چک") }
            .sortedWith(
                compareBy<DebtEntity> { it.status == DebtStatus.SETTLED }
                    .thenBy { it.dueDateMillis ?: Long.MAX_VALUE }
            )
    }

    val reminderMap = remember(activeReminders) {
        activeReminders.filter { it.targetType == ReminderTargetType.DEBT && it.targetId != null }
            .associateBy { it.targetId!! }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var checkToEdit by remember { mutableStateOf<DebtEntity?>(null) }
    var checkToDelete by remember { mutableStateOf<DebtEntity?>(null) }
    var checkForQuickPay by remember { mutableStateOf<DebtEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val totalAmountAll = checkItems.sumOf { it.totalAmount }
    val totalPaidAll = checkItems.sumOf { it.paidAmount.coerceAtMost(it.totalAmount) }
    val totalRemainingAll = (totalAmountAll - totalPaidAll).coerceAtLeast(0L)
    val progress = if (totalAmountAll > 0L) (totalPaidAll.toFloat() / totalAmountAll.toFloat()).coerceIn(0f, 1f) else 0f
    val percentPaid = (progress * 100).toInt()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            DodotaTopBar(
                title = "چک‌ها",
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = accentColor,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_check_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "ثبت چک جدید")
            }
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
            item {
                val animProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    label = "check_gauge"
                )
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
                                        accentColor.copy(alpha = 0.15f),
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
                            Box(
                                modifier = Modifier.size(80.dp),
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
                                    if (animProgress > 0.01f) {
                                        drawArc(
                                            brush = Brush.linearGradient(colors = listOf(accentColor, IncomeGreen)),
                                            startAngle = -90f,
                                            sweepAngle = animProgress * 360f,
                                            useCenter = false,
                                            topLeft = Offset(inset, inset),
                                            size = arcSize,
                                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                                        )
                                    }
                                }
                                Text(
                                    text = "${PersianNumberFormatter.formatDigits(percentPaid, userPreferences.currency)}٪",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accentColor
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = IncomeGreen.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "پاس‌شده:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = IncomeGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(totalPaidAll, userPreferences.currency),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = IncomeGreen
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = accentColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "مانده چک‌ها:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(totalRemainingAll, userPreferences.currency),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (checkItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "چکی ثبت نشده است",
                        description = "",
                        icon = Icons.Outlined.FactCheck
                    )
                }
            } else {
                items(checkItems, key = { it.id }) { check ->
                    val paidSoFar = check.paidAmount.coerceIn(0L, check.totalAmount)
                    val remaining = (check.totalAmount - paidSoFar).coerceAtLeast(0L)
                    val itemProgress = if (check.totalAmount > 0L) (paidSoFar.toFloat() / check.totalAmount.toFloat()).coerceIn(0f, 1f) else 0f

                    GlassCard(
                        shape = RoundedCornerShape(22.dp),
                        borderColor = accentColor.copy(alpha = 0.55f),
                        isHighlighted = true,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToDetail(check.id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = accentColor.copy(alpha = 0.18f),
                                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                                        ) {
                                            Text(
                                                text = if (check.type == DebtType.DEBT_I_OWE) "چک پرداختی" else "چک دریافتی",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = accentColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        if (check.checkNumber.isNotBlank()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "#${PersianNumberFormatter.formatDigits(check.checkNumber, userPreferences.currency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentLight,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = check.personOrCompanyName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { checkToEdit = check },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "ویرایش چک",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { checkToDelete = check },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "حذف چک",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "مبلغ کل: ${PersianNumberFormatter.formatCurrency(check.totalAmount, userPreferences.currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "مانده: ${PersianNumberFormatter.formatCurrency(remaining, userPreferences.currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (remaining == 0L) IncomeGreen else accentColor
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { itemProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (remaining == 0L) IncomeGreen else accentColor,
                                trackColor = MaterialTheme.colorScheme.surface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (check.dueDateMillis != null) {
                                    Text(
                                        text = "سررسید: ${PersianDateHelper.formatToPersianDate(check.dueDateMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                if (remaining > 0L) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = IncomeGreen.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable { checkForQuickPay = check }
                                    ) {
                                        Text(
                                            text = "پاس شدن چک",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = IncomeGreen,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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

    if (showAddDialog || checkToEdit != null) {
        val editing = checkToEdit
        AddOrEditCheckDialog(
            existingCheck = editing,
            existingReminder = editing?.let { reminderMap[it.id] },
            currency = userPreferences.currency,
            onDismiss = {
                showAddDialog = false
                checkToEdit = null
            },
            onSave = { title, totalAmount, paidAmount, dueDateMillis, notes, type, checkNumber, enableReminder, offsetDays, hour, minute ->
                coroutineScope.launch {
                    val safePaid = paidAmount.coerceIn(0L, totalAmount)
                    val status = if (safePaid >= totalAmount && totalAmount > 0L) DebtStatus.SETTLED else DebtStatus.ACTIVE
                    val effectiveDueDate = dueDateMillis ?: System.currentTimeMillis()

                    val savedId = if (editing == null) {
                        repository.addDebt(
                            DebtEntity(
                                type = type,
                                personOrCompanyName = title,
                                totalAmount = totalAmount,
                                paidAmount = safePaid,
                                dueDateMillis = effectiveDueDate,
                                notes = notes,
                                isCheck = true,
                                checkNumber = checkNumber,
                                status = status
                            )
                        )
                    } else {
                        repository.updateDebt(
                            editing.copy(
                                type = type,
                                personOrCompanyName = title,
                                totalAmount = totalAmount,
                                paidAmount = safePaid,
                                dueDateMillis = effectiveDueDate,
                                notes = notes,
                                isCheck = true,
                                checkNumber = checkNumber,
                                status = status
                            )
                        )
                        editing.id
                    }

                    if (enableReminder && status != DebtStatus.SETTLED) {
                        val triggerMillis = AlarmHelper.computeSmartTriggerMillis(effectiveDueDate, offsetDays, hour, minute)
                        val remTitle = if (type == DebtType.DEBT_I_OWE) "سررسید چک پرداختی: $title" else "سررسید چک دریافتی: $title"
                        val remDesc = "مانده: ${PersianNumberFormatter.formatCurrency(totalAmount - safePaid, userPreferences.currency)}"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.DEBT,
                            targetId = savedId,
                            title = remTitle,
                            description = remDesc,
                            dueDateTimeMillis = triggerMillis,
                            offsetDays = offsetDays,
                            enabled = true
                        )
                        if (remId != null) {
                            AlarmHelper.scheduleExactAlarm(context, remId, remTitle, remDesc, triggerMillis)
                        }
                    } else if (editing != null) {
                        repository.removeTargetReminders(ReminderTargetType.DEBT, editing.id)
                    }

                    Toast.makeText(context, "چک با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                    checkToEdit = null
                }
            }
        )
    }

    checkForQuickPay?.let { check ->
        val remaining = (check.totalAmount - check.paidAmount).coerceAtLeast(0L)
        var amountText by remember { mutableStateOf(remaining.toString()) }
        var note by remember { mutableStateOf("") }
        GraphicalFormDialog(
            onDismissRequest = { checkForQuickPay = null },
            title = "پاس شدن چک: ${check.personOrCompanyName}",
            icon = Icons.Default.FactCheck,
            accentColor = IncomeGreen,
            confirmText = "ثبت",
            onConfirm = {
                val amt = PersianNumberFormatter.parseAmount(amountText)
                if (amt > 0L) {
                    coroutineScope.launch {
                        repository.recordDebtPayment(
                            debt = check,
                            amount = amt,
                            accountId = accounts.firstOrNull()?.id,
                            notes = note.trim()
                        )
                        Toast.makeText(context, "پاس شدن چک ثبت شد ✓", Toast.LENGTH_SHORT).show()
                        checkForQuickPay = null
                    }
                }
            }
        ) {
            PersianAmountInput(
                value = amountText,
                onValueChange = { amountText = it },
                currency = userPreferences.currency,
                label = "مبلغ پاس‌شده"
            )
            DodotaTextField(
                value = note,
                onValueChange = { note = it },
                label = "توضیحات",
                leadingIcon = Icons.Default.Notes,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    checkToDelete?.let { check ->
        AlertDialog(
            onDismissRequest = { checkToDelete = null },
            title = { Text("حذف چک") },
            text = { Text("آیا از حذف چک «${check.personOrCompanyName}» اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteDebt(check)
                            Toast.makeText(context, "چک حذف شد", Toast.LENGTH_SHORT).show()
                            checkToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { checkToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun AddOrEditCheckDialog(
    existingCheck: DebtEntity?,
    existingReminder: ReminderEntity?,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        totalAmount: Long,
        paidAmount: Long,
        dueDateMillis: Long?,
        notes: String,
        type: DebtType,
        checkNumber: String,
        enableReminder: Boolean,
        offsetDays: Int,
        hour: Int,
        minute: Int
    ) -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    var selectedType by remember { mutableStateOf(existingCheck?.type ?: DebtType.DEBT_I_OWE) }
    var checkNumber by remember { mutableStateOf(existingCheck?.checkNumber ?: "") }
    var title by remember { mutableStateOf(existingCheck?.personOrCompanyName ?: "") }
    var amountText by remember { mutableStateOf(existingCheck?.totalAmount?.toString() ?: "") }
    var paidAmountText by remember { mutableStateOf(existingCheck?.paidAmount?.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember { mutableStateOf(existingCheck?.notes ?: "") }
    var dueDateMillis by remember { mutableStateOf<Long?>(existingCheck?.dueDateMillis ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val initialCal = remember(existingReminder) {
        Calendar.getInstance().apply {
            if (existingReminder != null) timeInMillis = existingReminder.dueDateTimeMillis
            else add(Calendar.MINUTE, 2)
        }
    }
    var enableReminder by remember { mutableStateOf(existingReminder != null || existingCheck == null) }
    var offsetDays by remember { mutableIntStateOf(existingReminder?.reminderOffsetDays ?: 0) }
    var reminderHour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var reminderMinute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }

    GraphicalFormDialog(
        onDismissRequest = onDismiss,
        title = if (existingCheck == null) "ثبت چک جدید" else "ویرایش چک",
        icon = Icons.Default.FactCheck,
        accentColor = accentColor,
        confirmText = if (existingCheck == null) "ثبت چک" else "بروزرسانی",
        onConfirm = {
            val amount = PersianNumberFormatter.parseAmount(amountText)
            val paidAmt = PersianNumberFormatter.parseAmount(paidAmountText)
            if (amount > 0L) {
                val effectiveTitle = title.trim().ifBlank { "چک" }
                onSave(
                    effectiveTitle,
                    amount,
                    paidAmt,
                    dueDateMillis,
                    notes.trim(),
                    selectedType,
                    checkNumber.trim(),
                    enableReminder,
                    offsetDays,
                    reminderHour,
                    reminderMinute
                )
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedType == DebtType.DEBT_I_OWE,
                onClick = { selectedType = DebtType.DEBT_I_OWE },
                label = { Text("چک پرداختی") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedType == DebtType.RECEIVABLE_OWED_TO_ME,
                onClick = { selectedType = DebtType.RECEIVABLE_OWED_TO_ME },
                label = { Text("چک دریافتی") },
                modifier = Modifier.weight(1f)
            )
        }

        DodotaTextField(
            value = checkNumber,
            onValueChange = { checkNumber = PersianNumberFormatter.formatDigits(it, currency) },
            label = "شماره صیادی یا نام بانک چک",
            leadingIcon = Icons.Default.FactCheck,
            modifier = Modifier.fillMaxWidth()
        )

        DodotaTextField(
            value = title,
            onValueChange = { title = it },
            label = "عنوان چک / در وجه",
            leadingIcon = Icons.Default.Description,
            modifier = Modifier.fillMaxWidth()
        )

        PersianAmountInput(
            value = amountText,
            onValueChange = { amountText = it },
            currency = currency,
            label = "مبلغ کل چک"
        )

        PersianAmountInput(
            value = paidAmountText,
            onValueChange = { paidAmountText = it },
            currency = currency,
            label = "مبلغ پاس‌شده"
        )

        OutlinedButton(
            onClick = { showDatePicker = true },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, accentColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "تاریخ سررسید چک: ${PersianDateHelper.formatToPersianDate(dueDateMillis ?: System.currentTimeMillis())}",
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
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
            title = "یادآور و ساعت هشدار چک",
            dueDateMillis = dueDateMillis ?: System.currentTimeMillis(),
            onDueDateChange = { dueDateMillis = it }
        )

        DodotaTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "توضیحات",
            leadingIcon = Icons.Default.Notes,
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
    }

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
