package ir.mmd.dodota.ui.screens.reminders

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.mmd.dodota.data.model.ReminderEntity
import ir.mmd.dodota.data.model.ReminderTargetType
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
fun RemindersScreen(
    repository: DodotaRepository,
    userPreferences: UserPreferences,
    onBack: () -> Unit
) {
    val remindersFlow = remember { repository.allReminders }
    val reminders by remindersFlow.collectAsState(initial = emptyList())
    var selectedTypeFilter by remember { mutableStateOf<ReminderTargetType?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<ReminderEntity?>(null) }
    var reminderToDelete by remember { mutableStateOf<ReminderEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val filteredReminders = remember(reminders, selectedTypeFilter) {
        if (selectedTypeFilter == null) reminders
        else reminders.filter { it.targetType == selectedTypeFilter }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "برای پخش آلارم و هشدارها، لطفاً مجوز اعلان را در تنظیمات فعال نمایید", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                try {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "مرکز یادآورها و آلارم سررسید",
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
                Icon(Icons.Default.Add, contentDescription = "افزودن یادآور")
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
            // Alarm banner info
            item {
                GlassCard(
                    shape = RoundedCornerShape(20.dp),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AlarmOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "مرکز یکپارچه یادآور چک، قسط و بدهی",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "یادآورهایی که در بخش بدهی‌ها، اقساط، تراکنش‌های دوره‌ای یا چک‌ها فعال کرده‌اید در اینجا جمع شده و سر ساعت زنگ می‌زنند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Collapsible Category Filter for Reminders
            item {
                DodotaExpandableSelector(
                    label = "فیلتر دسته‌بندی یادآورها",
                    selectedTitle = selectedTypeFilter?.title ?: "همه یادآورها (چک، قسط، بدهی و دوره‌ای)",
                    selectedSubtitle = "${PersianNumberFormatter.toPersianDigits(filteredReminders.size)} یادآور ثبت‌شده",
                    leadingIcon = {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                ) { closeSelector ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedTypeFilter == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTypeFilter = null
                                closeSelector()
                            }
                    ) {
                        Text("همه یادآورها", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                    }
                    ReminderTargetType.values().forEach { type ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedTypeFilter == type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTypeFilter = type
                                    closeSelector()
                                }
                        ) {
                            Text(type.title, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }

            if (filteredReminders.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "یادآوری در این بخش وجود ندارد",
                        description = "می‌توانید برای موعد چک‌ها، پرداخت اقساط وام، بدهی‌ها و قبوض (حتی برای چند روز قبل از سررسید) ساعت دقیق آلارم تنظیم کنید.",
                        icon = Icons.Outlined.Notifications
                    )
                }
            } else {
                items(filteredReminders, key = { it.id }) { reminder ->
                    GlassCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = reminder.targetType.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (reminder.reminderOffsetDays > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = IncomeGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "${PersianNumberFormatter.toPersianDigits(reminder.reminderOffsetDays)} روز قبل از موعد",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = IncomeGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = reminder.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (reminder.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = reminder.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                val cal = Calendar.getInstance().apply { timeInMillis = reminder.dueDateTimeMillis }
                                val timeStr = PersianNumberFormatter.toPersianDigits(
                                    String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                                )
                                Text(
                                    text = "زمان زنگ هشدار: ${PersianDateHelper.formatToPersianDate(reminder.dueDateTimeMillis)} • ساعت $timeStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!reminder.isCompleted) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                repository.updateReminder(reminder.copy(isCompleted = true))
                                                AlarmHelper.cancelAlarm(context, reminder.id)
                                                Toast.makeText(context, "یادآور انجام شد", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "تکمیل", tint = IncomeGreen, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }

                                IconButton(
                                    onClick = { reminderToEdit = reminder },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "ویرایش", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }

                                IconButton(
                                    onClick = { reminderToDelete = reminder },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, desc, targetType, triggerDateMillis, offsetDays ->
                coroutineScope.launch {
                    val id = repository.insertReminder(
                        ReminderEntity(
                            title = title,
                            description = desc,
                            targetType = targetType,
                            dueDateTimeMillis = triggerDateMillis,
                            reminderOffsetDays = offsetDays,
                            isEnabled = true
                        )
                    )
                    AlarmHelper.scheduleExactAlarm(
                        context = context,
                        reminderId = id,
                        title = title,
                        description = desc.ifEmpty { "موعد ${targetType.title} فرا رسیده است." },
                        triggerAtMillis = triggerDateMillis
                    )
                    Toast.makeText(context, "یادآور و آلارم با موفقیت تنظیم شد", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            }
        )
    }

    reminderToEdit?.let { rem ->
        EditReminderDialog(
            reminder = rem,
            onDismiss = { reminderToEdit = null },
            onSave = { title, desc, targetType, triggerDateMillis, offsetDays ->
                coroutineScope.launch {
                    val updated = rem.copy(
                        title = title,
                        description = desc,
                        targetType = targetType,
                        dueDateTimeMillis = triggerDateMillis,
                        reminderOffsetDays = offsetDays
                    )
                    repository.updateReminder(updated)
                    AlarmHelper.cancelAlarm(context, rem.id)
                    AlarmHelper.scheduleExactAlarm(
                        context = context,
                        reminderId = rem.id,
                        title = title,
                        description = desc.ifEmpty { "موعد ${targetType.title} فرا رسیده است." },
                        triggerAtMillis = triggerDateMillis
                    )
                    Toast.makeText(context, "یادآور بروزرسانی شد", Toast.LENGTH_SHORT).show()
                    reminderToEdit = null
                }
            }
        )
    }

    reminderToDelete?.let { rem ->
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            title = { Text("حذف یادآور") },
            text = { Text("آیا از حذف یادآور «${rem.title}» اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            AlarmHelper.cancelAlarm(context, rem.id)
                            repository.deleteReminder(rem)
                            Toast.makeText(context, "یادآور حذف شد", Toast.LENGTH_SHORT).show()
                            reminderToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, targetType: ReminderTargetType, triggerDateMillis: Long, offsetDays: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ReminderTargetType.CUSTOM) }
    var dueDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val defaultCal = remember { Calendar.getInstance().apply { add(Calendar.MINUTE, 2) } }
    var offsetDays by remember { mutableIntStateOf(0) }
    var selectedHour by remember { mutableIntStateOf(defaultCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(defaultCal.get(Calendar.MINUTE)) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیم یادآور و آلارم جدید", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Collapsible Type selector
                DodotaExpandableSelector(
                    label = "نوع یادآور",
                    selectedTitle = selectedType.title
                ) { closeSelector ->
                    ReminderTargetType.values().forEach { type ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedType == type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedType = type
                                    closeSelector()
                                }
                        ) {
                            Text(type.title, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان یادآور (مثال: چک صیادی، قسط وام)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("توضیحات (مبلغ یا نام شخص)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تاریخ سررسید اصلی: ${PersianDateHelper.formatToPersianDate(dueDateMillis)}")
                }

                ReminderConfigSection(
                    isEnabled = true,
                    onEnabledChange = {},
                    offsetDays = offsetDays,
                    onOffsetDaysChange = { offsetDays = it },
                    selectedHour = selectedHour,
                    onHourChange = { selectedHour = it },
                    selectedMinute = selectedMinute,
                    onMinuteChange = { selectedMinute = it },
                    title = "زمان و ساعت دقیق پخش آلارم"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val triggerMillis = AlarmHelper.computeSmartTriggerMillis(
                            dueDateMillis = dueDateMillis,
                            offsetDays = offsetDays,
                            hour = selectedHour,
                            minute = selectedMinute
                        )
                        onSave(title, desc, selectedType, triggerMillis, offsetDays)
                    }
                }
            ) {
                Text("ثبت و فعال‌سازی آلارم")
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
            initialDateMillis = dueDateMillis,
            onDateSelected = {
                dueDateMillis = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
fun EditReminderDialog(
    reminder: ReminderEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, targetType: ReminderTargetType, triggerDateMillis: Long, offsetDays: Int) -> Unit
) {
    val initialCal = Calendar.getInstance().apply { timeInMillis = reminder.dueDateTimeMillis }
    var title by remember { mutableStateOf(reminder.title) }
    var desc by remember { mutableStateOf(reminder.description) }
    var selectedType by remember { mutableStateOf(reminder.targetType) }
    var dueDateMillis by remember {
        mutableLongStateOf(
            Calendar.getInstance().apply {
                timeInMillis = reminder.dueDateTimeMillis
                add(Calendar.DAY_OF_YEAR, reminder.reminderOffsetDays)
            }.timeInMillis
        )
    }
    var offsetDays by remember { mutableIntStateOf(reminder.reminderOffsetDays) }
    var selectedHour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش یادآور و ساعت آلارم", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DodotaExpandableSelector(
                    label = "نوع یادآور",
                    selectedTitle = selectedType.title
                ) { closeSelector ->
                    ReminderTargetType.values().forEach { type ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedType == type) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedType = type
                                    closeSelector()
                                }
                        ) {
                            Text(type.title, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان یادآور") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("توضیحات (مبلغ یا نام شخص)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تاریخ سررسید اصلی: ${PersianDateHelper.formatToPersianDate(dueDateMillis)}")
                }

                ReminderConfigSection(
                    isEnabled = true,
                    onEnabledChange = {},
                    offsetDays = offsetDays,
                    onOffsetDaysChange = { offsetDays = it },
                    selectedHour = selectedHour,
                    onHourChange = { selectedHour = it },
                    selectedMinute = selectedMinute,
                    onMinuteChange = { selectedMinute = it },
                    title = "زمان و ساعت دقیق پخش آلارم"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val triggerMillis = AlarmHelper.computeSmartTriggerMillis(
                            dueDateMillis = dueDateMillis,
                            offsetDays = offsetDays,
                            hour = selectedHour,
                            minute = selectedMinute
                        )
                        onSave(title, desc, selectedType, triggerMillis, offsetDays)
                    }
                }
            ) {
                Text("ذخیره تغییرات")
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
            initialDateMillis = dueDateMillis,
            onDateSelected = {
                dueDateMillis = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}
