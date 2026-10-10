package ir.mmd.dodota.ui.screens.recurring

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.*
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

@Composable
fun RecurringScreen(
    repository: DodotaRepository,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    userPreferences: UserPreferences,
    onBack: () -> Unit
) {
    val recurringList by repository.allRecurring.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "تراکنش‌های دوره‌ای و تکرارشونده",
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن تراکنش دوره‌ای")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (recurringList.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "تراکنش تکرارشونده‌ای ثبت نشده است",
                        description = "هزینه‌ها یا درآمدهای ثابت (مثل اجاره، حقوق ماهانه، قبوض یا اشتراک‌ها) را همراه با ساعت یادآوری تعریف کنید.",
                        icon = Icons.Outlined.Repeat
                    )
                }
            } else {
                items(recurringList, key = { it.id }) { item ->
                    GlassCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { itemToEdit = item }
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
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${item.interval.title} • سررسید بعدی: ${PersianDateHelper.formatToPersianDate(item.nextDueDateMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${if (item.type == TransactionType.INCOME) "+ " else "- "}${PersianNumberFormatter.formatCurrency(item.amount, userPreferences.currency)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.type == TransactionType.INCOME) IncomeGreen else ExpenseRed
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = { itemToEdit = item },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "ویرایش",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { itemToDelete = item },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "حذف",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRecurringDialog(
            accounts = accounts,
            currency = userPreferences.currency,
            onDismiss = { showAddDialog = false },
            onSave = { title, type, amount, accountId, interval, nextDueMillis, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    val recurring = RecurringTransactionEntity(
                        title = title,
                        type = type,
                        amount = amount,
                        accountId = accountId,
                        interval = interval,
                        startDateMillis = nextDueMillis,
                        nextDueDateMillis = nextDueMillis
                    )
                    val id = repository.insertRecurring(recurring)
                    if (enableReminder) {
                        val triggerMillis = AlarmHelper.computeSmartTriggerMillis(
                            dueDateMillis = nextDueMillis,
                            offsetDays = offsetDays,
                            hour = reminderHour,
                            minute = reminderMinute
                        )
                        val remTitle = "یادآور دوره‌ای: $title"
                        val remDesc = "${interval.title} • مبلغ: ${PersianNumberFormatter.formatCurrency(amount, userPreferences.currency)}"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.RECURRING,
                            targetId = id,
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
                    showAddDialog = false
                    Toast.makeText(context, "تراکنش دوره‌ای با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    itemToEdit?.let { item ->
        EditRecurringDialog(
            item = item,
            accounts = accounts,
            currency = userPreferences.currency,
            onDismiss = { itemToEdit = null },
            onSave = { newTitle, newType, newAmount, newAccountId, newInterval, nextDueMillis, enableReminder, offsetDays, reminderHour, reminderMinute ->
                coroutineScope.launch {
                    repository.updateRecurring(
                        item.copy(
                            title = newTitle,
                            type = newType,
                            amount = newAmount,
                            accountId = newAccountId,
                            interval = newInterval,
                            nextDueDateMillis = nextDueMillis
                        )
                    )
                    if (enableReminder) {
                        val triggerMillis = AlarmHelper.computeSmartTriggerMillis(
                            dueDateMillis = nextDueMillis,
                            offsetDays = offsetDays,
                            hour = reminderHour,
                            minute = reminderMinute
                        )
                        val remTitle = "یادآور دوره‌ای: $newTitle"
                        val remDesc = "${newInterval.title} • مبلغ: ${PersianNumberFormatter.formatCurrency(newAmount, userPreferences.currency)}"
                        val remId = repository.syncTargetReminder(
                            targetType = ReminderTargetType.RECURRING,
                            targetId = item.id,
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
                        repository.removeTargetReminders(ReminderTargetType.RECURRING, item.id)
                    }
                    Toast.makeText(context, "تراکنش دوره‌ای با موفقیت ویرایش شد", Toast.LENGTH_SHORT).show()
                    itemToEdit = null
                }
            }
        )
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("حذف تراکنش دوره‌ای") },
            text = { Text("آیا از حذف تراکنش دوره‌ای «${item.title}» اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteRecurring(item)
                            Toast.makeText(context, "تراکنش دوره‌ای حذف شد", Toast.LENGTH_SHORT).show()
                            itemToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss@{ itemToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
fun AddRecurringDialog(
    accounts: List<AccountEntity>,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        type: TransactionType,
        amount: Long,
        accountId: Long,
        interval: RecurrenceInterval,
        nextDueMillis: Long,
        enableReminder: Boolean,
        offsetDays: Int,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedInterval by remember { mutableStateOf(RecurrenceInterval.MONTHLY) }
    var selectedAccountId by remember { mutableLongStateOf(accounts.firstOrNull()?.id ?: 1L) }
    var nextDueMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var enableReminder by remember { mutableStateOf(true) }
    var offsetDays by remember { mutableIntStateOf(1) }
    var reminderHour by remember { mutableIntStateOf(9) }
    var reminderMinute by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("افزودن تراکنش دوره‌ای", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان (مثلاً اجاره خانه، حقوق)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                PersianAmountInput(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "مبلغ",
                    currency = currency
                )

                // Type Toggle
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = { selectedType = TransactionType.EXPENSE },
                        label = { Text("هزینه") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        label = { Text("درآمد") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Collapsible Interval Selector
                DodotaExpandableSelector(
                    label = "دوره تکرار",
                    selectedTitle = selectedInterval.title
                ) { closeSelector ->
                    RecurrenceInterval.values().forEach { interval ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedInterval == interval) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedInterval = interval
                                    closeSelector()
                                }
                        ) {
                            Text(interval.title, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تاریخ سررسید: ${PersianDateHelper.formatToPersianDate(nextDueMillis)}")
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
                    title = "یادآور و ساعت هشدار سررسید"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = PersianNumberFormatter.parseAmount(amountText)
                    if (title.isBlank()) {
                        Toast.makeText(context, "لطفاً عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (amount <= 0L) {
                        Toast.makeText(context, "لطفاً مبلغ معتبری وارد کنید", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(title, selectedType, amount, selectedAccountId, selectedInterval, nextDueMillis, enableReminder, offsetDays, reminderHour, reminderMinute)
                }
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )

    if (showDatePicker) {
        PersianDatePickerDialog(
            initialDateMillis = nextDueMillis,
            onDateSelected = {
                nextDueMillis = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
fun EditRecurringDialog(
    item: RecurringTransactionEntity,
    accounts: List<AccountEntity>,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        type: TransactionType,
        amount: Long,
        accountId: Long,
        interval: RecurrenceInterval,
        nextDueMillis: Long,
        enableReminder: Boolean,
        offsetDays: Int,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var amountText by remember { mutableStateOf(item.amount.toString()) }
    var selectedType by remember { mutableStateOf(item.type) }
    var selectedInterval by remember { mutableStateOf(item.interval) }
    var selectedAccountId by remember { mutableLongStateOf(item.accountId) }
    var nextDueMillis by remember { mutableLongStateOf(item.nextDueDateMillis) }
    var showDatePicker by remember { mutableStateOf(false) }

    var enableReminder by remember { mutableStateOf(true) }
    var offsetDays by remember { mutableIntStateOf(1) }
    var reminderHour by remember { mutableIntStateOf(9) }
    var reminderMinute by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش تراکنش دوره‌ای", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                PersianAmountInput(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "مبلغ",
                    currency = currency
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = { selectedType = TransactionType.EXPENSE },
                        label = { Text("هزینه") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        label = { Text("درآمد") },
                        modifier = Modifier.weight(1f)
                    )
                }

                DodotaExpandableSelector(
                    label = "دوره تکرار",
                    selectedTitle = selectedInterval.title
                ) { closeSelector ->
                    RecurrenceInterval.values().forEach { interval ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedInterval == interval) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedInterval = interval
                                    closeSelector()
                                }
                        ) {
                            Text(interval.title, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تاریخ سررسید: ${PersianDateHelper.formatToPersianDate(nextDueMillis)}")
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
                    title = "یادآور و ساعت هشدار سررسید"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = PersianNumberFormatter.parseAmount(amountText)
                    if (title.isBlank()) {
                        Toast.makeText(context, "لطفاً عنوان را وارد کنید", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (amount <= 0L) {
                        Toast.makeText(context, "لطفاً مبلغ معتبری وارد کنید", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(title, selectedType, amount, selectedAccountId, selectedInterval, nextDueMillis, enableReminder, offsetDays, reminderHour, reminderMinute)
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

    if (showDatePicker) {
        PersianDatePickerDialog(
            initialDateMillis = nextDueMillis,
            onDateSelected = {
                nextDueMillis = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}
