package ir.mmd.dodota.ui.screens.transactions

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.*
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.ui.theme.WarningAmber
import ir.mmd.dodota.util.NotificationHelper
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import ir.mmd.dodota.util.ReceiptImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionScreen(
    transactionId: Long?,
    initialTypeString: String?,
    repository: DodotaRepository,
    userPreferences: UserPreferences,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    allTransactions: List<TransactionEntity>,
    allBudgets: List<BudgetEntity>,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedType by remember {
        mutableStateOf(
            when (initialTypeString?.uppercase()) {
                "INCOME" -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }
        )
    }

    var existingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var otherCategoryDescription by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var isPinned by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(accounts.firstOrNull { it.isActive }?.id ?: accounts.firstOrNull()?.id) }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var receiptUriString by remember { mutableStateOf<String?>(null) }
    var showFullReceiptPreview by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Ensure "انتقال به شخص دیگر" exists in Expense categories per Item 14
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && categories.none { it.type == TransactionType.EXPENSE && it.name == "انتقال به شخص دیگر" }) {
            repository.insertCategory(
                CategoryEntity(
                    name = "انتقال به شخص دیگر",
                    type = TransactionType.EXPENSE,
                    iconName = "swap_horiz",
                    colorHex = "#0EA5E9",
                    isDefault = true
                )
            )
        }
    }

    // Media Picker for Receipt — persists image into internal storage for display & backup
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    ReceiptImageHelper.saveReceiptImageToInternalStorage(context, uri)
                }
                if (savedPath != null) {
                    receiptUriString = savedPath
                    Toast.makeText(context, "تصویر سند ثبت و ذخیره شد ✅", Toast.LENGTH_SHORT).show()
                } else {
                    receiptUriString = uri.toString()
                }
            }
        }
    }

    val receiptBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, receiptUriString) {
        value = if (!receiptUriString.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                ReceiptImageHelper.loadBitmap(context, receiptUriString)
            }
        } else null
    }

    // Load existing transaction if editing
    LaunchedEffect(transactionId) {
        if (transactionId != null && transactionId > 0) {
            val tx = repository.getTransaction(transactionId)
            if (tx != null) {
                existingTransaction = tx
                selectedType = if (tx.type == TransactionType.TRANSFER) TransactionType.EXPENSE else tx.type
                amountText = tx.amount.toString()
                title = tx.title
                otherCategoryDescription = tx.title
                note = tx.note
                tags = tx.tags
                isPinned = tx.isPinned
                selectedCategoryId = tx.categoryId
                selectedAccountId = tx.accountId
                selectedDateMillis = tx.dateMillis
                receiptUriString = tx.receiptUri
            }
        }
    }

    // Select first category matching type if none selected
    LaunchedEffect(selectedType, categories) {
        if (selectedCategoryId == null || categories.none { it.id == selectedCategoryId && it.type == selectedType }) {
            selectedCategoryId = categories.firstOrNull { it.type == selectedType }?.id
        }
    }

    val matchingCategories = remember(categories, selectedType) {
        categories.filter { it.type == selectedType }
    }
    val selectedCatObj = remember(matchingCategories, selectedCategoryId) {
        matchingCategories.firstOrNull { it.id == selectedCategoryId }
    }

    // Item 24: Check if selected category is "سایر درآمدها" or "سایر هزینه‌ها" or "انتقال به شخص دیگر"
    val isOtherOrTransferCategory = remember(selectedCatObj) {
        val name = selectedCatObj?.name ?: ""
        name.contains("سایر") || name.contains("انتقال به شخص دیگر")
    }

    // Current Persian month key based on selectedDateMillis
    val txPersianDate = remember(selectedDateMillis) { PersianDateHelper.fromMillis(selectedDateMillis) }
    val txMonthKey = remember(txPersianDate) {
        "${txPersianDate.year}-${txPersianDate.month.toString().padStart(2, '0')}"
    }
    val startOfTxMonth = remember(txPersianDate) {
        PersianDateHelper.getStartOfMonthMillis(txPersianDate.year, txPersianDate.month)
    }
    val endOfTxMonth = remember(txPersianDate) {
        PersianDateHelper.getEndOfMonthMillis(txPersianDate.year, txPersianDate.month)
    }

    val categoryBudget = remember(allBudgets, selectedCategoryId, txMonthKey, selectedType) {
        if (selectedType == TransactionType.EXPENSE && selectedCategoryId != null) {
            allBudgets.firstOrNull { it.categoryId == selectedCategoryId && it.monthKey == txMonthKey }
        } else null
    }

    val spentInCategoryBeforeThis = remember(allTransactions, selectedCategoryId, startOfTxMonth, endOfTxMonth, existingTransaction) {
        if (selectedCategoryId == null) 0L
        else {
            allTransactions.filter {
                it.type == TransactionType.EXPENSE &&
                        it.categoryId == selectedCategoryId &&
                        it.dateMillis in startOfTxMonth..endOfTxMonth &&
                        it.id != (existingTransaction?.id ?: -1L)
            }.sumOf { it.amount }
        }
    }

    val enteredAmount = remember(amountText) { PersianNumberFormatter.parseAmount(amountText) }

    // Item 27: Header strictly shows only the active section's own title
    val screenHeaderTitle = when {
        existingTransaction != null && selectedType == TransactionType.INCOME -> "ویرایش درآمد"
        existingTransaction != null -> "ویرایش هزینه"
        selectedType == TransactionType.INCOME -> "ثبت درآمد"
        else -> "ثبت هزینه‌ها"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                title = screenHeaderTitle,
                onBackClick = onBack,
                actions = {
                    if (existingTransaction != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "حذف به سطل زباله",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Category Selection FIRST (Item 23: ابتدا دسته‌بندی انتخاب شود، بعد مبلغ وارد شود)
            DodotaExpandableSelector(
                label = "دسته‌بندی",
                selectedTitle = selectedCatObj?.name ?: "انتخاب دسته‌بندی",
                selectedSubtitle = if (selectedType == TransactionType.EXPENSE) "دسته‌بندی هزینه" else "منبع درآمد",
                leadingIcon = {
                    CategoryIconView(
                        iconName = selectedCatObj?.iconName ?: "category",
                        colorHex = selectedCatObj?.colorHex ?: "#FF1E42",
                        size = 40.dp
                    )
                }
            ) { closeSelector ->
                matchingCategories.forEach { cat ->
                    val isSelected = cat.id == selectedCategoryId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedCategoryId = cat.id
                                closeSelector()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconView(
                                iconName = cat.iconName,
                                colorHex = cat.colorHex,
                                size = 32.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Item 24: In "سایر درآمدها" or "سایر هزینه‌ها" (or "انتقال به شخص دیگر"), open a description field
            AnimatedVisibility(visible = isOtherOrTransferCategory) {
                val fieldLabel = when {
                    selectedCatObj?.name?.contains("انتقال به شخص دیگر") == true -> "توضیحات انتقال (نام شخص یا شماره کارت مقصد)"
                    selectedType == TransactionType.INCOME -> "توضیحات سایر درآمدها (بنویسید این درآمد چیست)"
                    else -> "توضیحات سایر هزینه‌ها (بنویسید این هزینه چیست)"
                }
                OutlinedTextField(
                    value = otherCategoryDescription,
                    onValueChange = {
                        otherCategoryDescription = it
                        title = it
                    },
                    label = { Text(fieldLabel) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Amount Input SECOND (Item 22 & 23: بعد مبلغ وارد شود و در پرانتز تومان فارسی باشد)
            PersianAmountInput(
                value = amountText,
                onValueChange = { amountText = it },
                currency = userPreferences.currency,
                label = "مبلغ"
            )

            // Live Graphical Remaining Budget Card when recording an Expense in a Category with a Budget
            AnimatedVisibility(visible = categoryBudget != null) {
                categoryBudget?.let { budget ->
                    val catName = selectedCatObj?.name ?: "این دسته‌بندی"
                    val totalProjected = spentInCategoryBeforeThis + enteredAmount
                    val remaining = budget.monthlyAmount - totalProjected
                    val rawPercent = if (budget.monthlyAmount > 0) {
                        ((totalProjected.toDouble() / budget.monthlyAmount.toDouble()) * 100).toInt()
                    } else 0
                    val animatedProgress by animateFloatAsState(
                        targetValue = (rawPercent.toFloat() / 100f).coerceIn(0f, 1f),
                        label = "budget_live_progress"
                    )
                    val isExceeded = totalProjected > budget.monthlyAmount
                    val isWarning = rawPercent >= budget.warningThresholdPercent
                    val statusColor = when {
                        isExceeded -> ExpenseRed
                        isWarning -> WarningAmber
                        else -> IncomeGreen
                    }

                    GlassCard(
                        shape = RoundedCornerShape(20.dp),
                        borderColor = statusColor.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isExceeded || isWarning) Icons.Default.WarningAmber else Icons.Default.DonutLarge,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "وضعیت بودجه «$catName» در ${PersianDateHelper.PERSIAN_MONTHS[txPersianDate.month - 1]}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = statusColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${PersianNumberFormatter.toPersianDigits(rawPercent)}٪ مصرف",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = statusColor,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = if (remaining >= 0) "باقی‌مانده بودجه (پس از ثبت):" else "میزان عبور از سقف بودجه:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = PersianNumberFormatter.formatCurrency(kotlin.math.abs(remaining), userPreferences.currency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = statusColor
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "سقف تعیین‌شده ماهانه:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = PersianNumberFormatter.formatCurrency(budget.monthlyAmount, userPreferences.currency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Account Selection
            val activeAccounts = remember(accounts) { accounts.filter { it.isActive } }
            val selectedAccountObj = activeAccounts.firstOrNull { it.id == selectedAccountId }
            DodotaExpandableSelector(
                label = "حساب یا کارت بانکی",
                selectedTitle = selectedAccountObj?.name ?: "انتخاب حساب",
                selectedSubtitle = selectedAccountObj?.let {
                    "موجودی: ${PersianNumberFormatter.formatCurrency(it.balance, userPreferences.currency)}"
                },
                leadingIcon = {
                    CategoryIconView(
                        iconName = selectedAccountObj?.iconName ?: "account_balance_wallet",
                        colorHex = selectedAccountObj?.colorHex ?: "#3B82F6",
                        size = 40.dp
                    )
                }
            ) { closeSelector ->
                activeAccounts.forEach { acc ->
                    val isSelected = acc.id == selectedAccountId
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedAccountId = acc.id
                                closeSelector()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "موجودی: ${PersianNumberFormatter.formatCurrency(acc.balance, userPreferences.currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Date Picker Card
            GlassCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = { showDatePicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "تاریخ ثبت",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = PersianDateHelper.formatToPersianDate(selectedDateMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("تغییر تاریخ")
                    }
                }
            }

            // 5. Title / Subject Input (if not already using the "Other" category description box)
            if (!isOtherOrTransferCategory) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان یا شرح کوتاه") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 6. Item 25 & 26: "ثبت سند" section (renamed from "جزئیات تکمیلی") with persistent receipt image preview
            DodotaExpandableSection(
                title = "ثبت سند",
                subtitle = if (!receiptUriString.isNullOrBlank()) "تصویر سند پیوست شده است ✅" else null,
                icon = Icons.Default.Receipt,
                initiallyExpanded = !receiptUriString.isNullOrBlank() || note.isNotBlank() || tags.isNotBlank()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Receipt Image Picker & Persistent Preview (Item 26)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        border = BorderStroke(
                            1.dp,
                            if (receiptBitmap != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        Icons.Outlined.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (receiptBitmap != null) "تصویر سند / رسید ثبت شده" else "انتخاب عکس رسید یا سند از گالری",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (receiptBitmap != null) "تغییر عکس" else "انتخاب عکس")
                                    }
                                    if (!receiptUriString.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                ReceiptImageHelper.deleteReceiptImage(receiptUriString)
                                                receiptUriString = null
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "حذف عکس رسید",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }

                            // Display the selected receipt image directly inside the app (Item 26)
                            receiptBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                        .clickable { showFullReceiptPreview = true }
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "تصویر رسید",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(bottomStart = 12.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.ZoomIn,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "بزرگ‌نمایی سند",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("یادداشت و توضیحات سند") },
                        minLines = 2,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("برچسب‌ها (با ویرگول جدا کنید)") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPinned = !isPinned }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "سنجاق کردن (پین) در بالای فهرست",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = isPinned,
                            onCheckedChange = { isPinned = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Save Button
            Button(
                onClick = {
                    val amount = PersianNumberFormatter.parseAmount(amountText)
                    if (amount <= 0) {
                        Toast.makeText(context, "لطفاً مبلغ معتبر وارد کنید", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val accId = selectedAccountId
                    if (accId == null) {
                        Toast.makeText(context, "لطفاً حساب مورد نظر را انتخاب کنید", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    coroutineScope.launch {
                        val finalCategory = categories.firstOrNull { it.id == selectedCategoryId }
                        val defaultTitle = finalCategory?.name ?: "تراکنش"
                        val resolvedTitle = if (isOtherOrTransferCategory) {
                            otherCategoryDescription.trim().ifBlank { defaultTitle }
                        } else {
                            title.trim().ifBlank { defaultTitle }
                        }

                        val newTx = TransactionEntity(
                            id = existingTransaction?.id ?: 0L,
                            type = selectedType,
                            amount = amount,
                            accountId = accId,
                            targetAccountId = null,
                            externalDestinationInfo = "",
                            categoryId = selectedCategoryId,
                            dateMillis = selectedDateMillis,
                            title = resolvedTitle,
                            note = note.trim(),
                            tags = tags.trim(),
                            isPinned = isPinned,
                            receiptUri = receiptUriString
                        )

                        if (existingTransaction != null) {
                            repository.updateTransaction(existingTransaction!!, newTx)
                        } else {
                            repository.addTransaction(newTx)
                        }

                        // Real Budget Threshold Warning Alert Check!
                        if (selectedType == TransactionType.EXPENSE && categoryBudget != null) {
                            val totalSpentNow = spentInCategoryBeforeThis + amount
                            val newPercent = if (categoryBudget.monthlyAmount > 0) {
                                ((totalSpentNow.toDouble() / categoryBudget.monthlyAmount.toDouble()) * 100).toInt()
                            } else 0

                            if (newPercent >= categoryBudget.warningThresholdPercent) {
                                val catTitle = finalCategory?.name ?: "دسته‌بندی"
                                val remainingAfter = categoryBudget.monthlyAmount - totalSpentNow
                                val alertTitle = if (totalSpentNow > categoryBudget.monthlyAmount) {
                                    "⚠️ عبور از سقف بودجه «$catTitle»!"
                                } else {
                                    "⚠️ هشدار مصرف بودجه «$catTitle» (${PersianNumberFormatter.toPersianDigits(newPercent)}٪)"
                                }
                                val alertBody = if (remainingAfter >= 0) {
                                    "مصرف شما در دسته «$catTitle» به ${PersianNumberFormatter.toPersianDigits(newPercent)}٪ رسید. باقی‌مانده بودجه: ${PersianNumberFormatter.formatCurrency(remainingAfter, userPreferences.currency)}"
                                } else {
                                    "مصرف شما در دسته «$catTitle» به ${PersianNumberFormatter.toPersianDigits(newPercent)}٪ رسید و به مبلغ ${PersianNumberFormatter.formatCurrency(kotlin.math.abs(remainingAfter), userPreferences.currency)} از سقف بودجه رد شد!"
                                }

                                NotificationHelper.showFinancialNotification(
                                    context = context,
                                    notificationId = (10000 + (selectedCategoryId ?: 1L)).toInt(),
                                    title = alertTitle,
                                    message = alertBody
                                )
                                Toast.makeText(context, "$alertTitle\n$alertBody", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                        }

                        onBack()
                    }
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_transaction_button")
            ) {
                Text(
                    text = if (existingTransaction != null) "ذخیره تغییرات" else screenHeaderTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showFullReceiptPreview && receiptBitmap != null) {
        Dialog(onDismissRequest = { showFullReceiptPreview = false }) {
            GlassCard(
                shape = RoundedCornerShape(24.dp),
                isHighlighted = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "پیش‌نمایش تصویر سند",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Image(
                        bitmap = receiptBitmap!!.asImageBitmap(),
                        contentDescription = "تصویر کامل سند",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 460.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showFullReceiptPreview = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بستن")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        PersianDatePickerDialog(
            initialDateMillis = selectedDateMillis,
            onDateSelected = { millis ->
                selectedDateMillis = millis
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showDeleteConfirmDialog && existingTransaction != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("انتقال به سطل زباله") },
            text = { Text("آیا مایلید این مورد به سطل زباله منتقل شود؟ (اثر آن از موجودی حساب برگردانده می‌شود)") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.moveToTrash(existingTransaction!!)
                            Toast.makeText(context, "به سطل زباله منتقل شد", Toast.LENGTH_SHORT).show()
                            showDeleteConfirmDialog = false
                            onBack()
                        }
                    }
                ) {
                    Text("انتقال به زباله‌دان", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
