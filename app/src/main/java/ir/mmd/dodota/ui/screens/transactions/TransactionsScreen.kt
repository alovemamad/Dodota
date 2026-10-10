package ir.mmd.dodota.ui.screens.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.ui.components.CategoryIconView
import ir.mmd.dodota.ui.components.DodotaExpandableSelector
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.EmptyStateCard
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.ui.theme.TransferBlue
import ir.mmd.dodota.util.NaturalLanguageQueryParser
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter

enum class TransactionSortOrder(val title: String) {
    NEWEST("جدیدترین تاریخ"),
    OLDEST("قدیمی‌ترین تاریخ"),
    HIGHEST_AMOUNT("بیشترین مبلغ"),
    LOWEST_AMOUNT("کمترین مبلغ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    userPreferences: UserPreferences,
    transactions: List<TransactionEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onDeleteToTrash: (TransactionEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<TransactionType?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var sortOrder by remember { mutableStateOf(TransactionSortOrder.NEWEST) }
    var showSortAndFilterDialog by remember { mutableStateOf(false) }

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    val parsedNlpQuery = remember(searchQuery) {
        NaturalLanguageQueryParser.parse(searchQuery)
    }

    val filteredTransactions = remember(
        transactions,
        searchQuery,
        selectedTypeFilter,
        selectedAccountId,
        selectedCategoryId,
        sortOrder,
        categoryMap,
        accountMap
    ) {
        val base = transactions.filter { tx ->
            val cat = tx.categoryId?.let { categoryMap[it] }
            val acc = accountMap[tx.accountId]

            val matchesSearch = NaturalLanguageQueryParser.matches(
                tx = tx,
                category = cat,
                accountName = acc?.name,
                rawQuery = searchQuery
            )

            val matchesType = selectedTypeFilter == null || tx.type == selectedTypeFilter
            val matchesAccount = selectedAccountId == null || tx.accountId == selectedAccountId || tx.targetAccountId == selectedAccountId
            val matchesCategory = selectedCategoryId == null || tx.categoryId == selectedCategoryId

            matchesSearch && matchesType && matchesAccount && matchesCategory
        }

        // Pinned items always stay on top, then sort by chosen order
        val comparator = when (sortOrder) {
            TransactionSortOrder.NEWEST -> compareByDescending<TransactionEntity> { it.isPinned }.thenByDescending { it.dateMillis }
            TransactionSortOrder.OLDEST -> compareByDescending<TransactionEntity> { it.isPinned }.thenBy { it.dateMillis }
            TransactionSortOrder.HIGHEST_AMOUNT -> compareByDescending<TransactionEntity> { it.isPinned }.thenByDescending { it.amount }
            TransactionSortOrder.LOWEST_AMOUNT -> compareByDescending<TransactionEntity> { it.isPinned }.thenBy { it.amount }
        }
        base.sortedWith(comparator)
    }

    val hasActiveFilterOrSort = selectedTypeFilter != null ||
            selectedAccountId != null ||
            selectedCategoryId != null ||
            sortOrder != TransactionSortOrder.NEWEST

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                title = "تراکنش‌ها",
                actions = {
                    BadgedBox(
                        badge = {
                            if (hasActiveFilterOrSort) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                            }
                        }
                    ) {
                        IconButton(
                            onClick = { showSortAndFilterDialog = true },
                            modifier = Modifier.testTag("sort_filter_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "مرتب‌سازی و فیلتر",
                                tint = if (hasActiveFilterOrSort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .testTag("add_transaction_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن تراکنش")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Smart Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجو (مثلاً: خرید های بالای ۵۰۰ هزار، خوراک...)") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Show Smart Query Interpretation Pill if user typed a numeric range query
            if (parsedNlpQuery.minAmount != null || parsedNlpQuery.maxAmount != null || parsedNlpQuery.exactAmount != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val filterDesc = buildString {
                            append("فیلتر هوشمند مبلغ: ")
                            if (parsedNlpQuery.minAmount != null && parsedNlpQuery.maxAmount != null) {
                                append("بین ${PersianNumberFormatter.formatNumberWithCommas(parsedNlpQuery.minAmount, userPreferences.currency)} تا ${PersianNumberFormatter.formatNumberWithCommas(parsedNlpQuery.maxAmount, userPreferences.currency)}")
                            } else if (parsedNlpQuery.minAmount != null) {
                                append("بالای ${PersianNumberFormatter.formatCurrency(parsedNlpQuery.minAmount, userPreferences.currency)}")
                            } else if (parsedNlpQuery.maxAmount != null) {
                                append("زیر ${PersianNumberFormatter.formatCurrency(parsedNlpQuery.maxAmount, userPreferences.currency)}")
                            } else if (parsedNlpQuery.exactAmount != null) {
                                append("معادل ${PersianNumberFormatter.formatCurrency(parsedNlpQuery.exactAmount, userPreferences.currency)}")
                            }
                            append(" (${PersianNumberFormatter.formatDigits(filteredTransactions.size, userPreferences.currency)} مورد)")
                        }
                        Text(
                            text = filterDesc,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Quick Type Filter Chips (Sort option is in TopBar)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedTypeFilter == null,
                        onClick = { selectedTypeFilter = null },
                        label = { Text("همه") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == TransactionType.EXPENSE,
                        onClick = { selectedTypeFilter = if (selectedTypeFilter == TransactionType.EXPENSE) null else TransactionType.EXPENSE },
                        label = { Text("هزینه‌ها") },
                        leadingIcon = {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ExpenseRed))
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == TransactionType.INCOME,
                        onClick = { selectedTypeFilter = if (selectedTypeFilter == TransactionType.INCOME) null else TransactionType.INCOME },
                        label = { Text("درآمدها") },
                        leadingIcon = {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IncomeGreen))
                        }
                    )
                }
            }

            // Transaction List
            if (filteredTransactions.isEmpty()) {
                EmptyStateCard(
                    title = "تراکنشی یافت نشد",
                    description = "تراکنشی مطابق با عبارت جستجو یا فیلترهای انتخابی وجود ندارد. می‌توانید عباراتی مثل «خرید های بالای ۱۰۰ هزار» را امتحان کنید.",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTransactions, key = { it.id }) { tx ->
                        val category = tx.categoryId?.let { categoryMap[it] }
                        val account = accountMap[tx.accountId]
                        val targetAccount = tx.targetAccountId?.let { accountMap[it] }

                        GlassCard(
                            shape = RoundedCornerShape(20.dp),
                            borderColor = if (tx.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onNavigateToEdit(tx.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryIconView(
                                    iconName = category?.iconName ?: when (tx.type) {
                                        TransactionType.INCOME -> "payments"
                                        TransactionType.EXPENSE -> "shopping_cart"
                                        TransactionType.TRANSFER -> "swap_horiz"
                                    },
                                    colorHex = category?.colorHex ?: when (tx.type) {
                                        TransactionType.INCOME -> "#10B981"
                                        TransactionType.EXPENSE -> "#FF1E42"
                                        TransactionType.TRANSFER -> "#3B82F6"
                                    },
                                    size = 44.dp
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (tx.isPinned) {
                                            Icon(
                                                Icons.Default.PushPin,
                                                contentDescription = "پین شده",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = tx.title.ifBlank { category?.name ?: "تراکنش" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    val destDisplay = targetAccount?.name
                                        ?: tx.externalDestinationInfo.ifBlank { "مقصد" }
                                    val subtitle = when (tx.type) {
                                        TransactionType.TRANSFER -> "${account?.name ?: "مبدأ"} ➔ $destDisplay • ${PersianDateHelper.formatToPersianDate(tx.dateMillis)}"
                                        else -> "${account?.name ?: "حساب"} • ${PersianDateHelper.formatToPersianDate(tx.dateMillis)}"
                                    }

                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                val amountColor = when (tx.type) {
                                    TransactionType.INCOME -> IncomeGreen
                                    TransactionType.EXPENSE -> ExpenseRed
                                    TransactionType.TRANSFER -> TransferBlue
                                }
                                val prefix = when (tx.type) {
                                    TransactionType.INCOME -> "+ "
                                    TransactionType.EXPENSE -> "- "
                                    TransactionType.TRANSFER -> ""
                                }

                                Text(
                                    text = "$prefix${PersianNumberFormatter.formatCurrency(tx.amount, userPreferences.currency)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = amountColor
                                )

                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onNavigateToEdit(tx.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "ویرایش تراکنش",
                                        tint = MaterialTheme.colorScheme.primary,
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

    if (showSortAndFilterDialog) {
        AlertDialog(
            onDismissRequest = { showSortAndFilterDialog = false },
            title = { Text("مرتب‌سازی و فیلتر پیشرفته", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "مرتب‌سازی بر اساس:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    TransactionSortOrder.values().forEach { order ->
                        val isSelected = sortOrder == order
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sortOrder = order }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { sortOrder = order }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = order.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Collapsible Account Filter
                    val selectedAccObj = accounts.firstOrNull { it.id == selectedAccountId }
                    DodotaExpandableSelector(
                        label = "فیلتر بر اساس حساب",
                        selectedTitle = selectedAccObj?.name ?: "همه حساب‌ها"
                    ) { closeSelector ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedAccountId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAccountId = null
                                    closeSelector()
                                }
                        ) {
                            Text("همه حساب‌ها", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                        }
                        accounts.forEach { acc ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedAccountId == acc.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedAccountId = acc.id
                                        closeSelector()
                                    }
                            ) {
                                Text(acc.name, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }

                    // Collapsible Category Filter
                    val selectedCatObj = categories.firstOrNull { it.id == selectedCategoryId }
                    DodotaExpandableSelector(
                        label = "فیلتر بر اساس دسته‌بندی",
                        selectedTitle = selectedCatObj?.name ?: "همه دسته‌بندی‌ها"
                    ) { closeSelector ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedCategoryId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCategoryId = null
                                    closeSelector()
                                }
                        ) {
                            Text("همه دسته‌بندی‌ها", modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                        }
                        categories.forEach { cat ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedCategoryId == cat.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategoryId = cat.id
                                        closeSelector()
                                    }
                            ) {
                                Text(cat.name, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSortAndFilterDialog = false }) {
                    Text("اعمال")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        sortOrder = TransactionSortOrder.NEWEST
                        selectedAccountId = null
                        selectedCategoryId = null
                        selectedTypeFilter = null
                        showSortAndFilterDialog = false
                    }
                ) {
                    Text("حذف فیلترها")
                }
            }
        )
    }
}
