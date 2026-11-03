package ir.mmd.dodota.ui.screens.checklist

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.DodotaTextField
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.EmptyStateCard
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.components.GraphicalFormDialog
import ir.mmd.dodota.ui.components.PersianAmountInput
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

private data class ChecklistCategoryStyle(
    val title: String,
    val icon: ImageVector,
    val defaultColorHex: String,
    val accentColor: Color
)

private val CHECKLIST_CATEGORY_STYLES = listOf(
    ChecklistCategoryStyle("همه", Icons.Default.GridView, "#3B82F6", Color(0xFF3B82F6)),
    ChecklistCategoryStyle("خرید روزمره", Icons.Default.ShoppingCart, "#10B981", Color(0xFF10B981)),
    ChecklistCategoryStyle("کارهای مالی", Icons.Default.AccountBalanceWallet, "#3B82F6", Color(0xFF3B82F6)),
    ChecklistCategoryStyle("اقلام منزل", Icons.Default.Home, "#F59E0B", Color(0xFFF59E0B)),
    ChecklistCategoryStyle("سفر و گردشگری", Icons.Default.FlightTakeoff, "#8B5CF6", Color(0xFF8B5CF6)),
    ChecklistCategoryStyle("تسویه و بدهی", Icons.Default.Handshake, "#FF1E42", Color(0xFFFF1E42)),
    ChecklistCategoryStyle("سایر", Icons.Default.AutoAwesome, "#06B6D4", Color(0xFF06B6D4))
)

private val COLOR_OPTIONS = listOf(
    "#FF1E42", "#10B981", "#3B82F6", "#F59E0B", "#8B5CF6", "#EC4899", "#06B6D4"
)

private enum class ChecklistItemFilter(val title: String) {
    ALL("همه اقلام"),
    PENDING("باقی‌مانده"),
    COMPLETED("انجام‌شده")
}

private fun getCategoryStyle(category: String): ChecklistCategoryStyle {
    return CHECKLIST_CATEGORY_STYLES.firstOrNull { it.title == category }
        ?: CHECKLIST_CATEGORY_STYLES.last()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistsScreen(
    repository: DodotaRepository,
    userPreferences: UserPreferences,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onBack: () -> Unit
) {
    val checklists by repository.allActiveChecklists.collectAsState(initial = emptyList())
    val allItems by repository.allChecklistItems.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedCategoryFilter by remember { mutableStateOf("همه") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var checklistToEdit by remember { mutableStateOf<ChecklistEntity?>(null) }
    var itemToEdit by remember { mutableStateOf<ChecklistItemEntity?>(null) }
    var itemToConvertToExpense by remember { mutableStateOf<Pair<ChecklistItemEntity, String>?>(null) }

    val filteredChecklists = remember(checklists, selectedCategoryFilter) {
        val base = if (selectedCategoryFilter == "همه") checklists
        else checklists.filter { it.category == selectedCategoryFilter }
        base.sortedWith(compareByDescending<ChecklistEntity> { it.isPinned }.thenByDescending { it.createdAtMillis })
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                title = "چک‌لیست",
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Default.AddTask, contentDescription = null) },
                text = { Text("چک‌لیست جدید", fontWeight = FontWeight.ExtraBold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Horizontal Category Filter Strip
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CHECKLIST_CATEGORY_STYLES) { catStyle ->
                        val isSelected = selectedCategoryFilter == catStyle.title
                        val count = if (catStyle.title == "همه") checklists.size
                        else checklists.count { it.category == catStyle.title }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) catStyle.accentColor.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) catStyle.accentColor
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.clickable { selectedCategoryFilter = catStyle.title }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(catStyle.accentColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = catStyle.icon,
                                        contentDescription = null,
                                        tint = catStyle.accentColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = catStyle.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isSelected) catStyle.accentColor else MaterialTheme.colorScheme.onSurface
                                )
                                if (count > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) catStyle.accentColor else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                            text = PersianNumberFormatter.toPersianDigits(count),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Checklists List
            if (filteredChecklists.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "چک‌لیستی ثبت نشده",
                        description = "",
                        icon = Icons.Outlined.Checklist
                    )
                }
            } else {
                items(filteredChecklists, key = { it.id }) { checklist ->
                    val rawItems = allItems.filter { it.checklistId == checklist.id }
                    ChecklistCard(
                        checklist = checklist,
                        allChecklistItems = rawItems,
                        visibleItems = rawItems,
                        userPreferences = userPreferences,
                        onTogglePin = {
                            coroutineScope.launch {
                                repository.updateChecklist(checklist.copy(isPinned = !checklist.isPinned))
                            }
                        },
                        onEditChecklist = { checklistToEdit = checklist },
                        onToggleItem = { item ->
                            coroutineScope.launch {
                                repository.toggleItemChecked(item.id, !item.isChecked)
                            }
                        },
                        onAddItem = { text, amount ->
                            coroutineScope.launch {
                                repository.insertChecklistItem(
                                    ChecklistItemEntity(
                                        checklistId = checklist.id,
                                        text = text,
                                        estimatedAmount = amount
                                    )
                                )
                            }
                        },
                        onEditItem = { item -> itemToEdit = item },
                        onDeleteItem = { item ->
                            coroutineScope.launch {
                                repository.deleteChecklistItem(item)
                            }
                        },
                        onClearCompleted = {
                            coroutineScope.launch {
                                repository.clearCheckedItems(checklist.id)
                                Toast.makeText(context, "موارد انجام‌شده پاک‌سازی شدند", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeleteChecklist = {
                            coroutineScope.launch {
                                repository.deleteChecklist(checklist)
                                Toast.makeText(context, "چک‌لیست حذف شد", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShare = {
                            val shareText = buildString {
                                appendLine("📋 ${checklist.title} (${checklist.category})")
                                appendLine("--------------------------")
                                rawItems.forEach { item ->
                                    val mark = if (item.isChecked) "✅" else "⬜"
                                    val price = if (item.estimatedAmount > 0) " (${PersianNumberFormatter.formatCurrency(item.estimatedAmount, userPreferences.currency)})" else ""
                                    appendLine("$mark ${item.text}$price")
                                }
                                appendLine("--------------------------")
                                appendLine("اپلیکیشن مدیریت مالی دودوتا")
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری چک‌لیست"))
                        },
                        onConvertToExpense = { item ->
                            itemToConvertToExpense = item to checklist.title
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrEditChecklistDialog(
            existingChecklist = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, category, colorHex ->
                coroutineScope.launch {
                    repository.insertChecklist(
                        ChecklistEntity(
                            title = title,
                            category = category,
                            colorHex = colorHex
                        )
                    )
                    showCreateDialog = false
                    Toast.makeText(context, "چک‌لیست جدید با موفقیت ساخته شد ✨", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    checklistToEdit?.let { cl ->
        CreateOrEditChecklistDialog(
            existingChecklist = cl,
            onDismiss = { checklistToEdit = null },
            onConfirm = { title, category, colorHex ->
                coroutineScope.launch {
                    repository.updateChecklist(
                        cl.copy(
                            title = title,
                            category = category,
                            colorHex = colorHex
                        )
                    )
                    checklistToEdit = null
                    Toast.makeText(context, "چک‌لیست بروزرسانی شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    itemToEdit?.let { item ->
        EditChecklistItemDialog(
            item = item,
            currency = userPreferences.currency,
            onDismiss = { itemToEdit = null },
            onSave = { newText, newAmount ->
                coroutineScope.launch {
                    repository.updateChecklistItem(
                        item.copy(
                            text = newText,
                            estimatedAmount = newAmount
                        )
                    )
                    itemToEdit = null
                    Toast.makeText(context, "آیتم بروزرسانی شد", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    itemToConvertToExpense?.let { (item, checklistTitle) ->
        ConvertToExpenseDialog(
            item = item,
            checklistTitle = checklistTitle,
            accounts = accounts,
            categories = categories,
            userPreferences = userPreferences,
            onDismiss = { itemToConvertToExpense = null },
            onConfirm = { accountId, categoryId ->
                coroutineScope.launch {
                    repository.convertChecklistItemToExpense(
                        item = item,
                        accountId = accountId,
                        categoryId = categoryId,
                        checklistTitle = checklistTitle
                    )
                    Toast.makeText(context, "به عنوان هزینه در حساب ثبت شد ✓", Toast.LENGTH_SHORT).show()
                    itemToConvertToExpense = null
                }
            }
        )
    }
}



@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChecklistCard(
    checklist: ChecklistEntity,
    allChecklistItems: List<ChecklistItemEntity>,
    visibleItems: List<ChecklistItemEntity>,
    userPreferences: UserPreferences,
    onTogglePin: () -> Unit,
    onEditChecklist: () -> Unit,
    onToggleItem: (ChecklistItemEntity) -> Unit,
    onAddItem: (String, Long) -> Unit,
    onEditItem: (ChecklistItemEntity) -> Unit,
    onDeleteItem: (ChecklistItemEntity) -> Unit,
    onClearCompleted: () -> Unit,
    onDeleteChecklist: () -> Unit,
    onShare: () -> Unit,
    onConvertToExpense: (ChecklistItemEntity) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    var newItemText by remember { mutableStateOf("") }
    var newItemAmountText by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val totalCount = allChecklistItems.size
    val completedCount = allChecklistItems.count { it.isChecked }
    val progress by animateFloatAsState(
        targetValue = if (totalCount > 0) (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "card_progress"
    )
    val percentPaid = (progress * 100).toInt()

    val listTotalAmount = allChecklistItems.sumOf { it.estimatedAmount }
    val listCompletedAmount = allChecklistItems.filter { it.isChecked }.sumOf { it.estimatedAmount }
    val listRemainingAmount = (listTotalAmount - listCompletedAmount).coerceAtLeast(0L)

    val parsedColor = try {
        Color(android.graphics.Color.parseColor(checklist.colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val categoryStyle = getCategoryStyle(checklist.category)

    GlassCard(
        shape = RoundedCornerShape(26.dp),
        borderColor = parsedColor.copy(alpha = 0.65f),
        isHighlighted = checklist.isPinned,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Iconic Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Category Icon with Mini Circular Progress Ring
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 3.5.dp.toPx()
                            drawCircle(
                                color = parsedColor.copy(alpha = 0.15f),
                                radius = size.minDimension / 2f
                            )
                            drawArc(
                                color = parsedColor.copy(alpha = 0.25f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                            if (progress > 0.01f) {
                                drawArc(
                                    color = if (percentPaid >= 100 && totalCount > 0) IncomeGreen else parsedColor,
                                    startAngle = -90f,
                                    sweepAngle = progress * 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                )
                            }
                        }
                        Icon(
                            imageVector = categoryStyle.icon,
                            contentDescription = null,
                            tint = if (percentPaid >= 100 && totalCount > 0) IncomeGreen else parsedColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (checklist.isPinned) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = "سنجاق‌شده",
                                    tint = parsedColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = checklist.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = parsedColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = checklist.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = parsedColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${PersianNumberFormatter.toPersianDigits(completedCount)} از ${PersianNumberFormatter.toPersianDigits(totalCount)} (${PersianNumberFormatter.toPersianDigits(percentPaid)}٪)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (percentPaid >= 100 && totalCount > 0) IncomeGreen else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (checklist.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "سنجاق",
                            tint = if (checklist.isPinned) parsedColor else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onEditChecklist, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "ویرایش لیست",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "اشتراک‌گذاری",
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "حذف لیست",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "بستن" else "باز کردن"
                        )
                    }
                }
            }

            // Progress Bar + Per-List Financial Strip
            if (totalCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(parsedColor.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(50))
                            .background(if (percentPaid >= 100) IncomeGreen else parsedColor)
                    )
                }

                if (listTotalAmount > 0L) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "انجام‌شده: ${PersianNumberFormatter.formatCurrency(listCompletedAmount, userPreferences.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = IncomeGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مانده: ${PersianNumberFormatter.formatCurrency(listRemainingAmount, userPreferences.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = parsedColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    // Well-proportioned Quick Add Item Box
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, parsedColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val itemBringIntoViewRequester = remember { BringIntoViewRequester() }
                        val itemCoroutineScope = rememberCoroutineScope()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .bringIntoViewRequester(itemBringIntoViewRequester),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DodotaTextField(
                                value = newItemText,
                                onValueChange = { newItemText = it },
                                label = "عنوان مورد جدید",
                                placeholder = "مثلاً: خرید نان، پرداخت قبض...",
                                leadingIcon = Icons.Default.AddTask,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusEvent { state ->
                                        if (state.isFocused) {
                                            itemCoroutineScope.launch {
                                                kotlinx.coroutines.delay(220)
                                                itemBringIntoViewRequester.bringIntoView()
                                            }
                                        }
                                    }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DodotaTextField(
                                    value = newItemAmountText,
                                    onValueChange = {
                                        newItemAmountText = PersianNumberFormatter.toEnglishDigits(it).filter { ch -> ch.isDigit() }
                                    },
                                    label = "مبلغ (${userPreferences.currency.unitName})",
                                    placeholder = "اختیاری",
                                    leadingIcon = Icons.Default.Payments,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    formatNumericWithCommas = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusEvent { state ->
                                            if (state.isFocused) {
                                                itemCoroutineScope.launch {
                                                    kotlinx.coroutines.delay(220)
                                                    itemBringIntoViewRequester.bringIntoView()
                                                }
                                            }
                                        }
                                )
                                Button(
                                    onClick = {
                                        if (newItemText.isNotBlank()) {
                                            val amount = newItemAmountText.toLongOrNull() ?: 0L
                                            onAddItem(newItemText.trim(), amount)
                                            newItemText = ""
                                            newItemAmountText = ""
                                        }
                                    },
                                    enabled = newItemText.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = parsedColor,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "افزودن قلم",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "افزودن",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            val previewAmt = newItemAmountText.toLongOrNull() ?: 0L
                            if (previewAmt > 0L) {
                                Text(
                                    text = "برآورد: ${PersianNumberFormatter.formatCurrency(previewAmt, userPreferences.currency)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = parsedColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Checklist Items List
                    if (visibleItems.isEmpty()) {
                        if (allChecklistItems.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "موردی یافت نشد",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visibleItems.forEachIndexed { index, item ->
                                ChecklistItemRowCard(
                                    index = index + 1,
                                    item = item,
                                    accentColor = parsedColor,
                                    currency = userPreferences.currency,
                                    onToggle = { onToggleItem(item) },
                                    onEdit = { onEditItem(item) },
                                    onDelete = { onDeleteItem(item) },
                                    onConvertToExpense = { onConvertToExpense(item) }
                                )
                            }
                        }

                        if (completedCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = onClearCompleted) {
                                    Icon(
                                        Icons.Outlined.CleaningServices,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "پاک‌سازی موارد انجام‌شده (${PersianNumberFormatter.formatDigits(completedCount, userPreferences.currency)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف چک‌لیست", fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف چک‌لیست «${checklist.title}» و تمامی اقلام آن اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteChecklist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun ChecklistItemRowCard(
    index: Int,
    item: ChecklistItemEntity,
    accentColor: Color,
    currency: AppCurrency,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConvertToExpense: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (item.isChecked) IncomeGreen.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        border = BorderStroke(
            width = 1.dp,
            color = if (item.isChecked) IncomeGreen.copy(alpha = 0.55f)
            else accentColor.copy(alpha = 0.28f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle() }
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
                modifier = Modifier.weight(1f)
            ) {
                // Single Clean Icon for Ticking
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (item.isChecked) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (item.isChecked) "انجام شد" else "تیک زدن",
                        tint = if (item.isChecked) IncomeGreen else accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (item.estimatedAmount > 0L) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = PersianNumberFormatter.formatCurrency(item.estimatedAmount, currency),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (item.isChecked) IncomeGreen else accentColor
                            )
                            if (!item.isExpenseConverted) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = accentColor.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.45f)),
                                    modifier = Modifier.clickable { onConvertToExpense() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.ReceiptLong,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "ثبت هزینه",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = accentColor
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "✓ ثبت در هزینه",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IncomeGreen
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "ویرایش آیتم",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "حذف آیتم",
                        tint = ExpenseRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CreateOrEditChecklistDialog(
    existingChecklist: ChecklistEntity?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: String, colorHex: String) -> Unit
) {
    var title by remember { mutableStateOf(existingChecklist?.title ?: "") }
    var selectedCategory by remember {
        mutableStateOf(existingChecklist?.category ?: CHECKLIST_CATEGORY_STYLES[1].title)
    }
    var selectedColor by remember {
        mutableStateOf(existingChecklist?.colorHex ?: COLOR_OPTIONS[1])
    }
    val titleBringIntoViewRequester = remember { BringIntoViewRequester() }
    val dialogScope = rememberCoroutineScope()

    val accentColor = try {
        Color(android.graphics.Color.parseColor(selectedColor))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    GraphicalFormDialog(
        title = if (existingChecklist == null) "چک‌لیست جدید" else "ویرایش چک‌لیست",
        icon = Icons.Default.Checklist,
        accentColor = accentColor,
        confirmText = if (existingChecklist == null) "ایجاد" else "ذخیره",
        onConfirm = {
            if (title.isNotBlank()) {
                onConfirm(title.trim(), selectedCategory, selectedColor)
            }
        },
        onDismiss = onDismiss,
        confirmEnabled = title.isNotBlank()
    ) {
        DodotaTextField(
            value = title,
            onValueChange = { title = it },
            label = "عنوان لیست",
            leadingIcon = Icons.Default.Checklist,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(titleBringIntoViewRequester)
                .onFocusEvent { state ->
                    if (state.isFocused) {
                        dialogScope.launch {
                            kotlinx.coroutines.delay(220)
                            titleBringIntoViewRequester.bringIntoView()
                        }
                    }
                }
        )

        Text(
            text = "دسته‌بندی:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(CHECKLIST_CATEGORY_STYLES.drop(1)) { style ->
                val isSel = selectedCategory == style.title
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSel) style.accentColor.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (isSel) style.accentColor else Color.Transparent
                    ),
                    modifier = Modifier.clickable {
                        selectedCategory = style.title
                        if (existingChecklist == null) {
                            selectedColor = style.defaultColorHex
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            style.icon,
                            contentDescription = null,
                            tint = style.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = style.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) style.accentColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Text(
            text = "رنگ کارت:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            COLOR_OPTIONS.forEach { hex ->
                val color = try {
                    Color(android.graphics.Color.parseColor(hex))
                } catch (_: Exception) {
                    MaterialTheme.colorScheme.primary
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selectedColor == hex) 2.5.dp else 0.dp,
                            color = if (selectedColor == hex) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedColor = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedColor == hex) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditChecklistItemDialog(
    item: ChecklistItemEntity,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    var text by remember { mutableStateOf(item.text) }
    var amountText by remember {
        mutableStateOf(if (item.estimatedAmount > 0) item.estimatedAmount.toString() else "")
    }

    GraphicalFormDialog(
        title = "ویرایش مورد",
        icon = Icons.Default.AddTask,
        onConfirm = {
            if (text.isNotBlank()) {
                onSave(text.trim(), PersianNumberFormatter.parseAmount(amountText))
            }
        },
        onDismiss = onDismiss,
        confirmEnabled = text.isNotBlank()
    ) {
        DodotaTextField(
            value = text,
            onValueChange = { text = it },
            label = "نام:",
            leadingIcon = Icons.Default.AddTask,
            modifier = Modifier.fillMaxWidth()
        )
        PersianAmountInput(
            value = amountText,
            onValueChange = { amountText = it },
            currency = currency,
            label = "مبلغ"
        )
    }
}

@Composable
fun ConvertToExpenseDialog(
    item: ChecklistItemEntity,
    checklistTitle: String,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    userPreferences: UserPreferences,
    onDismiss: () -> Unit,
    onConfirm: (accountId: Long, categoryId: Long?) -> Unit
) {
    var selectedAccountId by remember { mutableLongStateOf(accounts.firstOrNull()?.id ?: 0L) }
    var selectedCategoryId by remember {
        mutableStateOf<Long?>(categories.firstOrNull { it.type == TransactionType.EXPENSE }?.id)
    }

    GraphicalFormDialog(
        title = "ثبت هزینه",
        icon = Icons.Default.ReceiptLong,
        accentColor = IncomeGreen,
        confirmText = "ثبت",
        onConfirm = {
            if (selectedAccountId != 0L) {
                onConfirm(selectedAccountId, selectedCategoryId)
            }
        },
        onDismiss = onDismiss,
        confirmEnabled = selectedAccountId != 0L
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = IncomeGreen.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${PersianNumberFormatter.formatCurrency(item.estimatedAmount, userPreferences.currency)} • $checklistTitle",
                    style = MaterialTheme.typography.labelSmall,
                    color = IncomeGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text("حساب:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(accounts) { acc ->
                FilterChip(
                    selected = selectedAccountId == acc.id,
                    onClick = { selectedAccountId = acc.id },
                    label = { Text(acc.name) }
                )
            }
        }

        Text("دسته‌بندی:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories.filter { it.type == TransactionType.EXPENSE }) { cat ->
                FilterChip(
                    selected = selectedCategoryId == cat.id,
                    onClick = { selectedCategoryId = cat.id },
                    label = { Text(cat.name) }
                )
            }
        }
    }
}
