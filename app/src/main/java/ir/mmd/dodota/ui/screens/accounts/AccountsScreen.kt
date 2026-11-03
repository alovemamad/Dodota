package ir.mmd.dodota.ui.screens.accounts

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.model.AccountType
import ir.mmd.dodota.data.model.TransactionEntity
import ir.mmd.dodota.data.model.TransactionType
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.CategoryIconView
import ir.mmd.dodota.ui.components.DodotaDropdownField
import ir.mmd.dodota.ui.components.DodotaTextField
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.components.GraphicalFormDialog
import ir.mmd.dodota.ui.components.PersianAmountInput
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    accounts: List<AccountEntity>,
    userPreferences: UserPreferences,
    repository: DodotaRepository,
    onBack: () -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToCardsManager: () -> Unit = {}
) {
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showInternalTransferDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activeAccounts = remember(accounts) { accounts.filter { it.isActive } }
    val totalBalance = activeAccounts.sumOf { it.balance }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                title = "مدیریت حساب‌ها",
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = onNavigateToCardsManager) {
                        Icon(
                            Icons.Default.CreditCard,
                            contentDescription = "مدیریت کارت‌ها",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddAccountDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن حساب")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Total Summary Card + Quick Actions (Internal Transfer & Cards Manager)
            item {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    elevation = 8.dp,
                    isHighlighted = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "مجموع دارایی حساب‌ها",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = PersianNumberFormatter.formatCurrency(totalBalance, userPreferences.currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (activeAccounts.size < 2) {
                                        Toast.makeText(
                                            context,
                                            "برای انتقال داخلی بین حساب‌های خودتان، حداقل ۲ حساب فعال نیاز است",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        showInternalTransferDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("انتقال بین حساب‌های خودم", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onNavigateToCardsManager,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اطلاعات کارت‌ها", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            items(activeAccounts, key = { it.id }) { account ->
                val resolvedBank = remember(account.bankName, account.cardNumber) {
                    IranianBanksCatalog.resolveBank(account.bankName, account.cardNumber)
                }
                val cardBorder = resolvedBank?.accentColor?.copy(alpha = 0.45f) 
                    ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                GlassCard(
                    shape = RoundedCornerShape(20.dp),
                    borderColor = cardBorder,
                    elevation = 6.dp,
                    onClick = { onNavigateToDetail(account.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (resolvedBank != null) {
                            IranianBankLogoBadge(
                                bankInfo = resolvedBank,
                                size = 48.dp
                            )
                        } else {
                            CategoryIconView(
                                iconName = account.iconName,
                                colorHex = account.colorHex,
                                size = 48.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${account.type.title} • ${account.bankName.ifBlank { "بانک" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            if (account.cardNumber.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = PersianNumberFormatter.formatDigits(
                                        account.cardNumber.chunked(4).joinToString("-"),
                                        userPreferences.currency
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = PersianNumberFormatter.formatCurrency(account.balance, userPreferences.currency),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { accountToEdit = account },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "ویرایش حساب",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { accountToDelete = account },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "حذف حساب",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInternalTransferDialog && activeAccounts.size >= 2) {
        InternalAccountTransferDialog(
            accounts = activeAccounts,
            currency = userPreferences.currency,
            onDismiss = { showInternalTransferDialog = false },
            onTransfer = { sourceAcc, targetAcc, amount, note ->
                coroutineScope.launch {
                    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    repository.addTransaction(
                        TransactionEntity(
                            type = TransactionType.TRANSFER,
                            amount = amount,
                            accountId = sourceAcc.id,
                            targetAccountId = targetAcc.id,
                            title = "انتقال داخلی از ${sourceAcc.name} به ${targetAcc.name}",
                            note = note,
                            dateMillis = System.currentTimeMillis(),
                            timeString = timeFormat.format(java.util.Date())
                        )
                    )
                    Toast.makeText(context, "انتقال داخلی بین حساب‌های شما با موفقیت انجام شد", Toast.LENGTH_SHORT).show()
                    showInternalTransferDialog = false
                }
            }
        )
    }

    if (showAddAccountDialog) {
        AddAccountDialog(
            currency = userPreferences.currency,
            onDismiss = { showAddAccountDialog = false },
            onSave = { name, type, bank, balance, color ->
                coroutineScope.launch {
                    repository.insertAccount(
                        AccountEntity(
                            name = name,
                            type = type,
                            bankName = bank,
                            balance = balance,
                            colorHex = color
                        )
                    )
                    Toast.makeText(context, "حساب با موفقیت اضافه شد", Toast.LENGTH_SHORT).show()
                    showAddAccountDialog = false
                }
            }
        )
    }

    accountToEdit?.let { account ->
        EditAccountDialog(
            account = account,
            currency = userPreferences.currency,
            onDismiss = { accountToEdit = null },
            onSave = { newName, newType, newBank, newBalance, newColor ->
                coroutineScope.launch {
                    repository.updateAccount(
                        account.copy(
                            name = newName,
                            type = newType,
                            bankName = newBank,
                            balance = newBalance,
                            colorHex = newColor
                        )
                    )
                    Toast.makeText(context, "حساب با موفقیت بروزرسانی شد", Toast.LENGTH_SHORT).show()
                    accountToEdit = null
                }
            }
        )
    }

    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("حذف حساب") },
            text = { Text("آیا از حذف حساب «${account.name}» اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteAccountSafely(account)
                            Toast.makeText(context, "حساب غیرفعال و حذف شد", Toast.LENGTH_SHORT).show()
                            accountToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun InternalAccountTransferDialog(
    accounts: List<AccountEntity>,
    currency: ir.mmd.dodota.data.model.AppCurrency,
    onDismiss: () -> Unit,
    onTransfer: (source: AccountEntity, target: AccountEntity, amount: Long, note: String) -> Unit
) {
    var sourceAccount by remember { mutableStateOf(accounts.first()) }
    var targetAccount by remember {
        mutableStateOf(accounts.getOrElse(1) { accounts.first() })
    }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("انتقال داخلی بین حساب‌های خودم", fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DodotaDropdownField(
                    label = "از حساب مبدأ (حساب خودم)",
                    selectedText = "${sourceAccount.name} (${PersianNumberFormatter.formatCurrency(sourceAccount.balance, currency)})",
                    items = accounts,
                    itemText = { it.name },
                    itemSubtitle = { "موجودی: ${PersianNumberFormatter.formatCurrency(it.balance, currency)}" },
                    onItemSelected = {
                        sourceAccount = it
                        if (targetAccount.id == it.id) {
                            accounts.firstOrNull { acc -> acc.id != it.id }?.let { other -> targetAccount = other }
                        }
                    },
                    leadingIcon = Icons.Default.AccountBalanceWallet
                )

                DodotaDropdownField(
                    label = "به حساب مقصد (حساب خودم)",
                    selectedText = "${targetAccount.name} (${PersianNumberFormatter.formatCurrency(targetAccount.balance, currency)})",
                    items = accounts.filter { it.id != sourceAccount.id },
                    itemText = { it.name },
                    itemSubtitle = { "موجودی: ${PersianNumberFormatter.formatCurrency(it.balance, currency)}" },
                    onItemSelected = { targetAccount = it },
                    leadingIcon = Icons.Default.AccountBalance
                )

                PersianAmountInput(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    currency = currency,
                    label = "مبلغ انتقال",
                    isError = errorText != null,
                    errorMessage = errorText
                )

                DodotaTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "توضیحات انتقال داخلی",
                    leadingIcon = Icons.Default.EditNote,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = PersianNumberFormatter.parseAmount(amountText)
                    if (amt <= 0L) {
                        errorText = "لطفاً مبلغ معتبر وارد کنید"
                    } else if (sourceAccount.id == targetAccount.id) {
                        errorText = "حساب مبدأ و مقصد نمی‌توانند یکسان باشند"
                    } else {
                        onTransfer(sourceAccount, targetAccount, amt, note.trim())
                    }
                },
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("انجام انتقال داخلی", fontWeight = FontWeight.Bold)
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
fun AddAccountDialog(
    currency: ir.mmd.dodota.data.model.AppCurrency,
    onDismiss: () -> Unit,
    onSave: (name: String, type: AccountType, bank: String, balance: Long, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("بانک سپه") }
    var balanceText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AccountType.BANK_CARD) }
    val colors = listOf("#475569", "#10B981", "#3B82F6", "#8B5CF6", "#F59E0B", "#EF4444", "#06B6D4")
    var selectedColor by remember { mutableStateOf(colors[0]) }
    var showBankPicker by remember { mutableStateOf(false) }

    val selectedBankInfo = remember(bankName) {
        IranianBanksCatalog.resolveBank(bankName)
    }

    GraphicalFormDialog(
        onDismissRequest = onDismiss,
        title = "افزودن حساب جدید",
        icon = Icons.Default.AccountBalanceWallet,
        accentColor = MaterialTheme.colorScheme.primary,
        confirmText = "ذخیره",
        dismissText = "انصراف",
        onConfirm = {
            val finalName = name.trim().ifEmpty { bankName.trim().ifEmpty { "حساب بانکی" } }
            val balance = PersianNumberFormatter.parseAmount(balanceText)
            onSave(finalName, selectedType, bankName, balance, selectedColor)
        }
    ) {
        // Graphical Bank Selector
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { showBankPicker = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IranianBankLogoBadge(bankInfo = selectedBankInfo, size = 38.dp)
                    Column {
                        Text(
                            text = "انتخاب بانک",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedBankInfo?.persianName ?: bankName.ifBlank { "انتخاب از پک بانک‌ها" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        DodotaTextField(
            value = name,
            onValueChange = { name = it },
            label = "نام حساب یا کارت",
            leadingIcon = Icons.Default.CreditCard,
            modifier = Modifier.fillMaxWidth()
        )

        Text("نوع حساب:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AccountType.values()) { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.title) }
                )
            }
        }

        PersianAmountInput(
            value = balanceText,
            onValueChange = { balanceText = it },
            currency = currency,
            label = "موجودی اولیه"
        )

        Text("رنگ حساب:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(colors) { hex ->
                val c = Color(android.graphics.Color.parseColor(hex))
                val isSelected = selectedColor.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (isSelected) 2.5.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedColor = hex }
                )
            }
        }
    }

    if (showBankPicker) {
        IranianBankPickerDialog(
            selectedBankName = bankName,
            onSelectBank = { chosen ->
                bankName = chosen.persianName
                selectedColor = chosen.primaryColorHex
                if (name.isBlank()) name = chosen.persianName
            },
            onDismiss = { showBankPicker = false }
        )
    }
}

@Composable
fun EditAccountDialog(
    account: AccountEntity,
    currency: ir.mmd.dodota.data.model.AppCurrency,
    onDismiss: () -> Unit,
    onSave: (name: String, type: AccountType, bank: String, balance: Long, color: String) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var bankName by remember { mutableStateOf(account.bankName) }
    var balanceText by remember { mutableStateOf(account.balance.toString()) }
    var selectedType by remember { mutableStateOf(account.type) }
    val colors = listOf("#475569", "#10B981", "#3B82F6", "#8B5CF6", "#F59E0B", "#EF4444", "#06B6D4")
    var selectedColor by remember { mutableStateOf(account.colorHex) }
    var showBankPicker by remember { mutableStateOf(false) }

    val selectedBankInfo = remember(bankName) {
        IranianBanksCatalog.resolveBank(bankName)
    }

    GraphicalFormDialog(
        onDismissRequest = onDismiss,
        title = "ویرایش حساب",
        icon = Icons.Outlined.Edit,
        accentColor = MaterialTheme.colorScheme.primary,
        confirmText = "بروزرسانی",
        dismissText = "انصراف",
        onConfirm = {
            if (name.isNotBlank()) {
                val balance = PersianNumberFormatter.parseAmount(balanceText)
                onSave(name, selectedType, bankName, balance, selectedColor)
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { showBankPicker = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IranianBankLogoBadge(bankInfo = selectedBankInfo, size = 38.dp)
                    Column {
                        Text(
                            text = "انتخاب بانک",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedBankInfo?.persianName ?: bankName.ifBlank { "انتخاب از پک بانک‌ها" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        DodotaTextField(
            value = name,
            onValueChange = { name = it },
            label = "نام حساب یا کارت",
            leadingIcon = Icons.Default.CreditCard,
            modifier = Modifier.fillMaxWidth()
        )

        Text("نوع حساب:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AccountType.values()) { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text(type.title) }
                )
            }
        }

        PersianAmountInput(
            value = balanceText,
            onValueChange = { balanceText = it },
            currency = currency,
            label = "موجودی حساب"
        )

        Text("رنگ حساب:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(colors) { hex ->
                val c = Color(android.graphics.Color.parseColor(hex))
                val isSelected = selectedColor.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (isSelected) 2.5.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedColor = hex }
                )
            }
        }
    }

    if (showBankPicker) {
        IranianBankPickerDialog(
            selectedBankName = bankName,
            onSelectBank = { chosen ->
                bankName = chosen.persianName
                selectedColor = chosen.primaryColorHex
            },
            onDismiss = { showBankPicker = false }
        )
    }
}
