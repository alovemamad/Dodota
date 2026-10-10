package ir.mmd.dodota.ui.screens.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.CategoryEntity
import ir.mmd.dodota.data.model.TransactionType
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.CategoryIconView
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.EmptyStateCard
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.ui.theme.TransferBlue
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

@Composable
fun AccountDetailScreen(
    accountId: Long,
    repository: DodotaRepository,
    userPreferences: UserPreferences,
    categories: List<CategoryEntity>,
    onBack: () -> Unit,
    onNavigateToEditTransaction: (Long) -> Unit = {}
) {
    val accountFlow = remember(accountId) { repository.getAccountFlow(accountId) }
    val account by accountFlow.collectAsState(initial = null)

    val transactionsFlow = remember(accountId) { repository.getTransactionsForAccount(accountId) }
    val transactions by transactionsFlow.collectAsState(initial = emptyList())

    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    var showEditAccountDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                title = account?.name ?: "جزئیات حساب",
                onBackClick = onBack,
                actions = {
                    if (account != null) {
                        IconButton(onClick = { showEditAccountDialog = true }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "ویرایش حساب",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (account == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    GlassCard(
                        shape = RoundedCornerShape(22.dp),
                        isHighlighted = true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = account!!.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${account!!.type.title} • ${account!!.bankName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                IconButton(onClick = { showEditAccountDialog = true }) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = "ویرایش حساب",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = PersianNumberFormatter.formatCurrency(account!!.balance, userPreferences.currency),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "گردش حساب",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (transactions.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "تراکنشی برای این حساب وجود ندارد",
                            description = "تراکنش‌های مرتبط با این حساب در این بخش نمایش داده می‌شوند."
                        )
                    }
                } else {
                    items(transactions, key = { it.id }) { tx ->
                        val category = tx.categoryId?.let { categoryMap[it] }
                        GlassCard(
                            shape = RoundedCornerShape(18.dp),
                            onClick = { onNavigateToEditTransaction(tx.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryIconView(
                                    iconName = category?.iconName ?: "payments",
                                    colorHex = category?.colorHex ?: "#10B981",
                                    size = 42.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.title.ifBlank { category?.name ?: "تراکنش" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = PersianDateHelper.formatToPersianDate(tx.dateMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val amountColor = when (tx.type) {
                                    TransactionType.INCOME -> IncomeGreen
                                    TransactionType.EXPENSE -> ExpenseRed
                                    TransactionType.TRANSFER -> TransferBlue
                                }
                                Text(
                                    text = PersianNumberFormatter.formatCurrency(tx.amount, userPreferences.currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = amountColor
                                )
                                IconButton(
                                    onClick = { onNavigateToEditTransaction(tx.id) },
                                    modifier = Modifier.size(34.dp)
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

    if (showEditAccountDialog && account != null) {
        EditAccountDialog(
            account = account!!,
            currency = userPreferences.currency,
            onDismiss = { showEditAccountDialog = false },
            onSave = { newName, newType, newBank, newBalance, newColor ->
                coroutineScope.launch {
                    repository.updateAccount(
                        account!!.copy(
                            name = newName,
                            type = newType,
                            bankName = newBank,
                            balance = newBalance,
                            colorHex = newColor
                        )
                    )
                    showEditAccountDialog = false
                }
            }
        )
    }
}
