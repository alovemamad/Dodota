package ir.mmd.dodota.ui.screens.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.model.AccountType
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.EmptyStateCard
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.components.PersianAmountInput
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

@Composable
fun CardsManagerScreen(
    accounts: List<AccountEntity>,
    userPreferences: UserPreferences,
    repository: DodotaRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val activeCards = remember(accounts, searchQuery) {
        accounts.filter { it.isActive }.filter { acc ->
            if (searchQuery.isBlank()) true
            else acc.name.contains(searchQuery, ignoreCase = true) ||
                    acc.bankName.contains(searchQuery, ignoreCase = true) ||
                    acc.cardNumber.contains(searchQuery, ignoreCase = true) ||
                    acc.cardHolderName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                title = "مدیریت کارت‌های بانکی",
                onBackClick = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            editingAccount = null
                            showAddOrEditDialog = true
                        }
                    ) {
                        Icon(
                            Icons.Default.AddCard,
                            contentDescription = "افزودن کارت جدید",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingAccount = null
                    showAddOrEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Default.AddCard, contentDescription = null) },
                text = { Text("ثبت کارت جدید", fontWeight = FontWeight.ExtraBold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("جستجوی نام کارت، بانک، شماره کارت یا صاحب کارت...") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (activeCards.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "هنوز کارتی ثبت نشده است",
                        description = "با زدن دکمه «ثبت کارت جدید» اطلاعات کامل کارت‌های بانکی (شماره کارت، شبا، CVV2، تاریخ انقضا و...) را ثبت کنید تا در هر زمان با یک لمس کپی شوند.",
                        icon = Icons.Outlined.CreditCard
                    )
                }
            } else {
                items(activeCards, key = { it.id }) { cardAccount ->
                    BankCardFullItem(
                        account = cardAccount,
                        currency = userPreferences.currency,
                        onCopyField = { label, textToCopy ->
                            copyToClipboard(context, label, textToCopy)
                        },
                        onEdit = {
                            editingAccount = cardAccount
                            showAddOrEditDialog = true
                        },
                        onDelete = {
                            deletingAccount = cardAccount
                        }
                    )
                }
            }
        }
    }

    if (showAddOrEditDialog) {
        AddOrEditBankCardDialog(
            initialAccount = editingAccount,
            currency = userPreferences.currency,
            onDismiss = {
                showAddOrEditDialog = false
                editingAccount = null
            },
            onSave = { updatedEntity ->
                coroutineScope.launch {
                    if (editingAccount == null) {
                        repository.insertAccount(updatedEntity)
                        Toast.makeText(context, "کارت جدید با موفقیت ثبت شد", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.updateAccount(updatedEntity)
                        Toast.makeText(context, "اطلاعات کارت ویرایش شد", Toast.LENGTH_SHORT).show()
                    }
                    showAddOrEditDialog = false
                    editingAccount = null
                }
            }
        )
    }

    deletingAccount?.let { acc ->
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            title = { Text("حذف کارت بانکی", fontWeight = FontWeight.ExtraBold) },
            text = { Text("آیا از حذف کارت «${acc.name}» اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteAccountSafely(acc)
                            Toast.makeText(context, "کارت حذف شد", Toast.LENGTH_SHORT).show()
                            deletingAccount = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAccount = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    if (value.isBlank()) {
        Toast.makeText(context, "$label ثبت نشده است", Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label کپی شد ✅", Toast.LENGTH_SHORT).show()
}

private fun formatCardNumberGroups(raw: String, useEnglish: Boolean): String {
    val clean = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() }.take(16)
    if (clean.isEmpty()) return "----  ----  ----  ----"
    val grouped = clean.chunked(4).joinToString(" - ")
    return PersianNumberFormatter.formatDigits(grouped, useEnglish)
}

@Composable
private fun BankCardFullItem(
    account: AccountEntity,
    currency: ir.mmd.dodota.data.model.AppCurrency,
    onCopyField: (String, String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColor = remember(account.colorHex) {
        try {
            Color(android.graphics.Color.parseColor(account.colorHex))
        } catch (_: Exception) {
            Color(0xFF2563EB)
        }
    }

    GlassCard(
        shape = RoundedCornerShape(26.dp),
        borderColor = cardColor.copy(alpha = 0.65f),
        isHighlighted = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Graphical Credit Card Visual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                cardColor.copy(alpha = 0.36f),
                                cardColor.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(cardColor.copy(alpha = 0.25f))
                                    .border(1.dp, cardColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreditCard,
                                    contentDescription = null,
                                    tint = cardColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = account.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = account.bankName.ifBlank { account.type.title },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = "ویرایش کارت",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "حذف کارت",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Card Number Banner with 1-tap copy
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, cardColor.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCopyField("شماره کارت", account.cardNumber)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "شماره کارت (لمس برای کپی)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatCardNumberGroups(account.cardNumber, currency.isEnglishDigits),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        letterSpacing = 1.2.sp,
                                        textDirection = TextDirection.Ltr
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = cardColor.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "کپی شماره کارت",
                                        tint = cardColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "کپی",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = cardColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Detailed Fields Grid (CVV2, Expiry Date, Sheba, Account Number, Holder Name, Balance)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CopyableFieldBox(
                        label = "رمز دوم / CVV2",
                        value = if (account.cvv2.isBlank()) "ثبت نشده" else PersianNumberFormatter.formatDigits(account.cvv2),
                        rawCopyValue = account.cvv2,
                        icon = Icons.Outlined.Password,
                        onCopy = { onCopyField("CVV2", account.cvv2) },
                        modifier = Modifier.weight(1f)
                    )
                    CopyableFieldBox(
                        label = "تاریخ انقضا",
                        value = if (account.expiryDate.isBlank()) "ثبت نشده" else PersianNumberFormatter.formatDigits(account.expiryDate),
                        rawCopyValue = account.expiryDate,
                        icon = Icons.Outlined.Event,
                        onCopy = { onCopyField("تاریخ انقضا", account.expiryDate) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Sheba (IBAN)
                CopyableFieldBox(
                    label = "شماره شبا (IBAN)",
                    value = if (account.shebaNumber.isBlank()) "ثبت نشده" else {
                        val normalized = if (account.shebaNumber.uppercase().startsWith("IR")) account.shebaNumber.uppercase() else "IR${account.shebaNumber}"
                        PersianNumberFormatter.formatDigits(normalized)
                    },
                    rawCopyValue = if (account.shebaNumber.isBlank()) "" else {
                        if (account.shebaNumber.uppercase().startsWith("IR")) account.shebaNumber.uppercase() else "IR${account.shebaNumber}"
                    },
                    icon = Icons.Outlined.AccountBalance,
                    onCopy = {
                        val normalized = if (account.shebaNumber.isBlank()) "" else if (account.shebaNumber.uppercase().startsWith("IR")) account.shebaNumber.uppercase() else "IR${account.shebaNumber}"
                        onCopyField("شماره شبا", normalized)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CopyableFieldBox(
                        label = "شماره حساب",
                        value = if (account.accountNumber.isBlank()) "ثبت نشده" else PersianNumberFormatter.formatDigits(account.accountNumber),
                        rawCopyValue = account.accountNumber,
                        icon = Icons.Outlined.Numbers,
                        onCopy = { onCopyField("شماره حساب", account.accountNumber) },
                        modifier = Modifier.weight(1f)
                    )
                    CopyableFieldBox(
                        label = "نام صاحب کارت",
                        value = account.cardHolderName.ifBlank { "ثبت نشده" },
                        rawCopyValue = account.cardHolderName,
                        icon = Icons.Outlined.Person,
                        onCopy = { onCopyField("نام صاحب کارت", account.cardHolderName) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (account.cardOrAccountDescription.isNotBlank()) {
                    CopyableFieldBox(
                        label = "سایر اطلاعات / توضیحات",
                        value = account.cardOrAccountDescription,
                        rawCopyValue = account.cardOrAccountDescription,
                        icon = Icons.Outlined.Description,
                        onCopy = { onCopyField("توضیحات کارت", account.cardOrAccountDescription) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Bottom Bar: Balance + Copy All Info Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "موجودی فعلی:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = PersianNumberFormatter.formatCurrency(account.balance, currency),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            val fullInfo = buildString {
                                append("بانک: ${account.bankName.ifBlank { account.name }}\n")
                                if (account.cardHolderName.isNotBlank()) append("به نام: ${account.cardHolderName}\n")
                                if (account.cardNumber.isNotBlank()) append("شماره کارت: ${account.cardNumber}\n")
                                if (account.shebaNumber.isNotBlank()) {
                                    val ir = if (account.shebaNumber.uppercase().startsWith("IR")) account.shebaNumber.uppercase() else "IR${account.shebaNumber}"
                                    append("شماره شبا: $ir\n")
                                }
                                if (account.accountNumber.isNotBlank()) append("شماره حساب: ${account.accountNumber}\n")
                                if (account.expiryDate.isNotBlank()) append("تاریخ انقضا: ${account.expiryDate}\n")
                                if (account.cvv2.isNotBlank()) append("CVV2: ${account.cvv2}")
                            }.trim()
                            onCopyField("اطلاعات کامل کارت", fullInfo)
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CopyAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("کپی کل اطلاعات کارت", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyableFieldBox(
    label: String,
    value: String,
    rawCopyValue: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier.clickable { onCopy() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (rawCopyValue.isNotBlank()) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "کپی $label",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
fun AddOrEditBankCardDialog(
    initialAccount: AccountEntity?,
    currency: ir.mmd.dodota.data.model.AppCurrency,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit
) {
    var name by remember { mutableStateOf(initialAccount?.name ?: "") }
    var bankName by remember { mutableStateOf(initialAccount?.bankName ?: "") }
    var cardHolderName by remember { mutableStateOf(initialAccount?.cardHolderName ?: "") }
    var cardNumber by remember { mutableStateOf(initialAccount?.cardNumber ?: "") }
    var cvv2 by remember { mutableStateOf(initialAccount?.cvv2 ?: "") }
    var expiryDate by remember { mutableStateOf(initialAccount?.expiryDate ?: "") }
    var shebaNumber by remember { mutableStateOf(initialAccount?.shebaNumber ?: "") }
    var accountNumber by remember { mutableStateOf(initialAccount?.accountNumber ?: "") }
    var description by remember { mutableStateOf(initialAccount?.cardOrAccountDescription ?: "") }
    var balanceText by remember { mutableStateOf(initialAccount?.balance?.toString() ?: "") }
    val colors = listOf("#2563EB", "#10B981", "#8B5CF6", "#E11D48", "#F59E0B", "#06B6D4")
    var selectedColor by remember { mutableStateOf(initialAccount?.colorHex ?: colors[0]) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            shape = RoundedCornerShape(26.dp),
            isHighlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (initialAccount == null) "ثبت کارت بانکی جدید" else "ویرایش اطلاعات کارت",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("عنوان کارت (مثال: کارت حقوق، کارت پس‌انداز)") },
                    leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("نام بانک") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = cardHolderName,
                        onValueChange = { cardHolderName = it },
                        label = { Text("نام صاحب کارت") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = PersianNumberFormatter.formatDigits(cardNumber),
                    onValueChange = { raw ->
                        val digits = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() }.take(16)
                        cardNumber = digits
                    },
                    label = { Text("شماره کارت ۱۶ رقمی") },
                    leadingIcon = { Icon(Icons.Default.Dialpad, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = PersianNumberFormatter.formatDigits(cvv2),
                        onValueChange = { raw ->
                            cvv2 = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() }.take(4)
                        },
                        label = { Text("CVV2") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = PersianNumberFormatter.formatDigits(expiryDate),
                        onValueChange = { raw ->
                            val clean = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() || it == '/' }.take(7)
                            expiryDate = clean
                        },
                        label = { Text("تاریخ انقضا (05/08)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = PersianNumberFormatter.formatDigits(shebaNumber),
                    onValueChange = { raw ->
                        val clean = PersianNumberFormatter.toEnglishDigits(raw)
                            .uppercase()
                            .filter { it.isDigit() || it == 'I' || it == 'R' }
                            .take(26)
                        shebaNumber = clean
                    },
                    label = { Text("شماره شبا (بدون یا با IR)") },
                    prefix = {
                        if (!shebaNumber.uppercase().startsWith("IR")) {
                            Text("IR-", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = PersianNumberFormatter.formatDigits(accountNumber),
                    onValueChange = { raw ->
                        accountNumber = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() || it == '.' || it == '-' }.take(24)
                    },
                    label = { Text("شماره حساب") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                PersianAmountInput(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    currency = currency,
                    label = "موجودی کارت"
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("سایر اطلاعات (شعبه، رمز، توضیحات و...)") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("رنگ ظاهری کارت:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(colors) { hex ->
                        val c = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalName = name.trim().ifEmpty { bankName.trim().ifEmpty { "کارت بانکی" } }
                            val finalBalance = PersianNumberFormatter.parseAmount(balanceText)
                            val entity = (initialAccount ?: AccountEntity(name = finalName, type = AccountType.BANK_CARD)).copy(
                                name = finalName,
                                bankName = bankName.trim(),
                                cardHolderName = cardHolderName.trim(),
                                cardNumber = cardNumber.trim(),
                                cvv2 = cvv2.trim(),
                                expiryDate = expiryDate.trim(),
                                shebaNumber = shebaNumber.trim(),
                                accountNumber = accountNumber.trim(),
                                cardOrAccountDescription = description.trim(),
                                balance = finalBalance,
                                colorHex = selectedColor,
                                iconName = "card"
                            )
                            onSave(entity)
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (initialAccount == null) "ثبت کارت" else "ذخیره تغییرات", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
