package ir.mmd.dodota.ui.screens.budgets

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.*
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.ui.theme.WarningAmber
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

@Composable
fun BudgetsScreen(
    repository: DodotaRepository,
    categories: List<CategoryEntity>,
    transactions: List<TransactionEntity>,
    userPreferences: UserPreferences,
    onBack: () -> Unit
) {
    val currentPDate = PersianDateHelper.getCurrentPersianDate()
    val monthKey = "${currentPDate.year}-${currentPDate.month.toString().padStart(2, '0')}"

    val budgetsFlow = remember(monthKey) { repository.getBudgetsForMonth(monthKey) }
    val budgets by budgetsFlow.collectAsState(initial = emptyList())

    val startOfMonth = PersianDateHelper.getStartOfMonthMillis(currentPDate.year, currentPDate.month)
    val endOfMonth = PersianDateHelper.getEndOfMonthMillis(currentPDate.year, currentPDate.month)
    val monthlyExpenses = transactions.filter { it.type == TransactionType.EXPENSE && it.dateMillis in startOfMonth..endOfMonth }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    var showAddDialog by remember { mutableStateOf(false) }
    var budgetToEdit by remember { mutableStateOf<BudgetEntity?>(null) }
    var budgetToDelete by remember { mutableStateOf<BudgetEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "بودجه‌بندی ماهانه (${PersianDateHelper.PERSIAN_MONTHS[currentPDate.month - 1]})",
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
                Icon(Icons.Default.Add, contentDescription = "تعریف بودجه")
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
            if (budgets.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "هنوز برای این ماه بودجه‌ای تعریف نکرده‌اید",
                        description = "برای دسته‌بندی‌های مختلف (مثلاً خوراک، تفریح، خرید) سقف بودجه و درصد هشدار تعیین کنید تا هنگام ثبت هزینه باقی‌مانده بودجه نمایش داده شود.",
                        icon = Icons.Outlined.AccountBalanceWallet
                    )
                }
            } else {
                items(budgets, key = { it.id }) { budget ->
                    val cat = categoryMap[budget.categoryId]
                    val spent = monthlyExpenses.filter { it.categoryId == budget.categoryId }.sumOf { it.amount }
                    val remaining = budget.monthlyAmount - spent
                    val percent = if (budget.monthlyAmount > 0) ((spent.toDouble() / budget.monthlyAmount.toDouble()) * 100).toInt() else 0
                    val isExceeded = spent > budget.monthlyAmount
                    val isWarning = percent >= budget.warningThresholdPercent

                    GlassCard(
                        shape = RoundedCornerShape(22.dp),
                        borderColor = when {
                            isExceeded -> ExpenseRed.copy(alpha = 0.6f)
                            isWarning -> WarningAmber.copy(alpha = 0.6f)
                            else -> null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { budgetToEdit = budget }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    CategoryIconView(
                                        iconName = cat?.iconName ?: "category",
                                        colorHex = cat?.colorHex ?: "#FF1E42",
                                        size = 40.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = cat?.name ?: "دسته‌بندی",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "هشدار در ${PersianNumberFormatter.toPersianDigits(budget.warningThresholdPercent)}٪",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isExceeded -> ExpenseRed.copy(alpha = 0.15f)
                                            isWarning -> WarningAmber.copy(alpha = 0.15f)
                                            else -> IncomeGreen.copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = "${PersianNumberFormatter.toPersianDigits(percent)}٪",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isExceeded -> ExpenseRed
                                                isWarning -> WarningAmber
                                                else -> IncomeGreen
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { budgetToEdit = budget },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "ویرایش بودجه",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { budgetToDelete = budget },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "حذف بودجه",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                progress = { (percent.toFloat() / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = when {
                                    isExceeded -> ExpenseRed
                                    isWarning -> WarningAmber
                                    else -> IncomeGreen
                                },
                                trackColor = MaterialTheme.colorScheme.surface
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "مصرف: ${PersianNumberFormatter.formatCurrency(spent, userPreferences.currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (remaining >= 0) {
                                        "باقی‌مانده: ${PersianNumberFormatter.formatCurrency(remaining, userPreferences.currency)}"
                                    } else {
                                        "عبور از سقف: ${PersianNumberFormatter.formatCurrency(kotlin.math.abs(remaining), userPreferences.currency)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remaining >= 0) IncomeGreen else ExpenseRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddBudgetDialog(
            categories = categories.filter { it.type == TransactionType.EXPENSE },
            currency = userPreferences.currency,
            onDismiss = { showAddDialog = false },
            onSave = { categoryId, amount, warningThreshold ->
                coroutineScope.launch {
                    repository.saveBudget(
                        BudgetEntity(
                            categoryId = categoryId,
                            monthlyAmount = amount,
                            monthKey = monthKey,
                            warningThresholdPercent = warningThreshold
                        )
                    )
                    Toast.makeText(context, "بودجه ثبت شد", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            }
        )
    }

    budgetToEdit?.let { budget ->
        val cat = categoryMap[budget.categoryId]
        EditBudgetDialog(
            budget = budget,
            categoryName = cat?.name ?: "دسته‌بندی",
            currency = userPreferences.currency,
            onDismiss = { budgetToEdit = null },
            onSave = { newAmount, warningThreshold ->
                coroutineScope.launch {
                    repository.saveBudget(
                        budget.copy(
                            monthlyAmount = newAmount,
                            warningThresholdPercent = warningThreshold
                        )
                    )
                    Toast.makeText(context, "بودجه با موفقیت ویرایش شد", Toast.LENGTH_SHORT).show()
                    budgetToEdit = null
                }
            }
        )
    }

    budgetToDelete?.let { budget ->
        val cat = categoryMap[budget.categoryId]
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title = { Text("حذف بودجه") },
            text = { Text("آیا مطمئن هستید که می‌خواهید بودجه دسته‌بندی «${cat?.name ?: "انتخاب شده"}» برای این ماه حذف شود؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteBudget(budget)
                            Toast.makeText(context, "بودجه حذف شد", Toast.LENGTH_SHORT).show()
                            budgetToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { budgetToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
fun AddBudgetDialog(
    categories: List<CategoryEntity>,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (categoryId: Long, amount: Long, warningThreshold: Int) -> Unit
) {
    var selectedCatId by remember { mutableStateOf<Long?>(categories.firstOrNull()?.id) }
    var amountText by remember { mutableStateOf("") }
    var warningThreshold by remember { mutableIntStateOf(55) }

    val selectedCat = categories.firstOrNull { it.id == selectedCatId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعریف سقف بودجه ماهانه", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DodotaExpandableSelector(
                    label = "انتخاب دسته‌بندی هزینه",
                    selectedTitle = selectedCat?.name ?: "انتخاب دسته‌بندی",
                    leadingIcon = {
                        CategoryIconView(
                            iconName = selectedCat?.iconName ?: "category",
                            colorHex = selectedCat?.colorHex ?: "#FF1E42",
                            size = 34.dp
                        )
                    }
                ) { closeSelector ->
                    categories.forEach { cat ->
                        val isSelected = cat.id == selectedCatId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCatId = cat.id
                                    closeSelector()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryIconView(
                                    iconName = cat.iconName,
                                    colorHex = cat.colorHex,
                                    size = 28.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cat.name,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                PersianAmountInput(
                    value = amountText,
                    onValueChange = { amountText = it },
                    currency = currency,
                    label = "سقف بودجه ماهانه"
                )

                Text(
                    text = "درصد هشدار مصرف: ${PersianNumberFormatter.toPersianDigits(warningThreshold)}٪",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = warningThreshold.toFloat(),
                    onValueChange = { warningThreshold = it.toInt() },
                    valueRange = 30f..100f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = PersianNumberFormatter.parseAmount(amountText)
                    if (selectedCatId != null && amount > 0L) {
                        onSave(selectedCatId!!, amount, warningThreshold)
                    }
                }
            ) {
                Text("ذخیره بودجه")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@Composable
fun EditBudgetDialog(
    budget: BudgetEntity,
    categoryName: String,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (newAmount: Long, warningThreshold: Int) -> Unit
) {
    var amountText by remember { mutableStateOf(budget.monthlyAmount.toString()) }
    var warningThreshold by remember { mutableIntStateOf(budget.warningThresholdPercent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش بودجه «$categoryName»", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PersianAmountInput(
                    value = amountText,
                    onValueChange = { amountText = it },
                    currency = currency,
                    label = "سقف بودجه ماهانه جدید"
                )

                Text(
                    text = "درصد هشدار مصرف: ${PersianNumberFormatter.toPersianDigits(warningThreshold)}٪",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = warningThreshold.toFloat(),
                    onValueChange = { warningThreshold = it.toInt() },
                    valueRange = 30f..100f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = PersianNumberFormatter.parseAmount(amountText)
                    if (amount > 0L) {
                        onSave(amount, warningThreshold)
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
}
