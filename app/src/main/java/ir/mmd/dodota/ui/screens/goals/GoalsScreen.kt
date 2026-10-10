package ir.mmd.dodota.ui.screens.goals

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.model.AppCurrency
import ir.mmd.dodota.data.model.FinancialGoalEntity
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.*
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.ui.theme.TransferBlue
import ir.mmd.dodota.ui.theme.WarningAmber
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

@Composable
fun GoalsScreen(
    goals: List<FinancialGoalEntity>,
    accounts: List<AccountEntity>,
    userPreferences: UserPreferences,
    repository: DodotaRepository,
    onBack: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var goalToEdit by remember { mutableStateOf<FinancialGoalEntity?>(null) }
    var goalToDelete by remember { mutableStateOf<FinancialGoalEntity?>(null) }
    var selectedGoalForDeposit by remember { mutableStateOf<Pair<FinancialGoalEntity, Boolean>?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val totalTargetAmount = remember(goals) { goals.sumOf { it.targetAmount } }
    val totalSavedAmount = remember(goals) { goals.sumOf { it.currentAmount } }
    val totalRemainingAmount = (totalTargetAmount - totalSavedAmount).coerceAtLeast(0L)
    val overallProgress = if (totalTargetAmount > 0L) {
        (totalSavedAmount.toFloat() / totalTargetAmount.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val completedCount = remember(goals) { goals.count { it.isCompleted || (it.targetAmount > 0 && it.currentAmount >= it.targetAmount) } }

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "اهداف پس‌انداز و سرمایه‌گذاری",
                onBackClick = onBack
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("هدف جدید", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (goals.isNotEmpty()) {
                item {
                    SavingsGoalsOutlookHeroCard(
                        totalSaved = totalSavedAmount,
                        totalRemaining = totalRemainingAmount,
                        totalTarget = totalTargetAmount,
                        overallProgress = overallProgress,
                        goalsCount = goals.size,
                        completedCount = completedCount,
                        currency = userPreferences.currency
                    )
                }
            }

            if (goals.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "هنوز هدف پس‌اندازی تعریف نکرده‌اید",
                        description = "برای اهدافی مانند خرید خودرو، سفر، پس‌انداز اضطراری یا خرید لپ‌تاپ هدف مالی بسازید و با واریز و برداشت هوشمند پیشرفت آن را دنبال کنید.",
                        icon = Icons.Outlined.TrackChanges
                    )
                }
            } else {
                items(goals, key = { it.id }) { goal ->
                    val progress = if (goal.targetAmount > 0) {
                        (goal.currentAmount.toFloat() / goal.targetAmount.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    val percent = (progress * 100).toInt()
                    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0L)
                    val isDone = goal.isCompleted || (goal.targetAmount > 0 && goal.currentAmount >= goal.targetAmount)
                    val accentColor = if (isDone) IncomeGreen else MaterialTheme.colorScheme.primary

                    GlassCard(
                        shape = RoundedCornerShape(24.dp),
                        borderColor = accentColor.copy(alpha = 0.45f),
                        isHighlighted = isDone,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { selectedGoalForDeposit = goal to true }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row: Circular Gauge + Title + Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GoalCircularGauge(
                                    progress = progress,
                                    accentColor = accentColor,
                                    currency = userPreferences.currency,
                                    size = 62.dp,
                                    strokeWidth = 6.dp
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = accentColor.copy(alpha = 0.16f),
                                            border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isDone) Icons.Default.EmojiEvents else Icons.Default.Savings,
                                                    contentDescription = null,
                                                    tint = accentColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isDone) "تکمیل شده 🎉" else "در حال پس‌انداز",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = accentColor
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = goal.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (goal.note.isNotBlank()) {
                                        Text(
                                            text = goal.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { goalToEdit = goal },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "ویرایش هدف",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { goalToDelete = goal },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "حذف هدف",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Financial Comparison Boxes: Saved So Far vs Remaining
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = IncomeGreen.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.3f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = IncomeGreen,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "ذخیره‌شده تا الان",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = IncomeGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = PersianNumberFormatter.formatCurrency(goal.currentAmount, userPreferences.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = IncomeGreen,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Flag,
                                                contentDescription = null,
                                                tint = WarningAmber,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "مانده تا هدف کل",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (remaining == 0L) "تکمیل شده ✨"
                                            else PersianNumberFormatter.formatCurrency(remaining, userPreferences.currency),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (remaining == 0L) IncomeGreen else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Segmented Milestone Bar
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "پیشرفت: ${PersianNumberFormatter.formatDigits(percent, userPreferences.currency)}٪",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "هدف نهایی: ${PersianNumberFormatter.formatCurrency(goal.targetAmount, userPreferences.currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = accentColor,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Dual Iconic Action Buttons: Deposit (Green) & Withdraw (Amber/Red)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { selectedGoalForDeposit = goal to true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = IncomeGreen,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1.15f)
                                ) {
                                    Icon(
                                        Icons.Default.SouthWest,
                                        contentDescription = null,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "واریز به پس‌انداز",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { selectedGoalForDeposit = goal to false },
                                    enabled = goal.currentAmount > 0L,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (goal.currentAmount > 0L) ExpenseRed.copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = ExpenseRed
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(0.95f)
                                ) {
                                    Icon(
                                        Icons.Default.NorthEast,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "برداشت وجه",
                                        style = MaterialTheme.typography.labelMedium,
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

    if (showAddDialog) {
        AddGoalDialog(
            currency = userPreferences.currency,
            onDismiss = { showAddDialog = false },
            onSave = { title, targetAmount, note ->
                coroutineScope.launch {
                    repository.insertGoal(
                        FinancialGoalEntity(
                            title = title,
                            targetAmount = targetAmount,
                            note = note
                        )
                    )
                    Toast.makeText(context, "هدف پس‌انداز ثبت شد", Toast.LENGTH_SHORT).show()
                    showAddDialog = false
                }
            }
        )
    }

    goalToEdit?.let { goal ->
        EditGoalDialog(
            goal = goal,
            currency = userPreferences.currency,
            onDismiss = { goalToEdit = null },
            onSave = { newTitle, newTarget, newCurrent, newNote ->
                coroutineScope.launch {
                    repository.updateGoal(
                        goal.copy(
                            title = newTitle,
                            targetAmount = newTarget,
                            currentAmount = newCurrent,
                            note = newNote,
                            isCompleted = newCurrent >= newTarget && newTarget > 0
                        )
                    )
                    Toast.makeText(context, "هدف با موفقیت ویرایش شد", Toast.LENGTH_SHORT).show()
                    goalToEdit = null
                }
            }
        )
    }

    goalToDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text("حذف هدف پس‌انداز", fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف هدف «${goal.title}» اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteGoal(goal)
                            Toast.makeText(context, "هدف پس‌انداز حذف شد", Toast.LENGTH_SHORT).show()
                            goalToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { goalToDelete = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    selectedGoalForDeposit?.let { (goal, initialIsDeposit) ->
        GoalDepositDialog(
            goal = goal,
            initialIsDeposit = initialIsDeposit,
            accounts = accounts,
            currency = userPreferences.currency,
            onDismiss = { selectedGoalForDeposit = null },
            onAction = { isDeposit, amount, accountId ->
                coroutineScope.launch {
                    if (isDeposit) {
                        repository.depositToGoal(goal, amount, accountId)
                        Toast.makeText(context, "مبلغ با موفقیت به پس‌انداز اضافه شد 🎉", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.withdrawFromGoal(goal, amount, accountId)
                        Toast.makeText(context, "مبلغ از هدف پس‌انداز برداشت شد", Toast.LENGTH_SHORT).show()
                    }
                    selectedGoalForDeposit = null
                }
            }
        )
    }
}

@Composable
private fun SavingsGoalsOutlookHeroCard(
    totalSaved: Long,
    totalRemaining: Long,
    totalTarget: Long,
    overallProgress: Float,
    goalsCount: Int,
    completedCount: Int,
    currency: AppCurrency
) {
    GlassCard(
        shape = RoundedCornerShape(26.dp),
        isHighlighted = true,
        borderColor = IncomeGreen.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            IncomeGreen.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoalCircularGauge(
                    progress = overallProgress,
                    accentColor = IncomeGreen,
                    currency = currency,
                    size = 76.dp,
                    strokeWidth = 8.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = IncomeGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "چشم‌انداز کل اهداف پس‌انداز",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = IncomeGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = PersianNumberFormatter.formatCurrency(totalSaved, currency),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = "${PersianNumberFormatter.formatDigits(completedCount, currency)} از ${PersianNumberFormatter.formatDigits(goalsCount, currency)} هدف تکمیل شده",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "ارزش کل اهداف",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = PersianNumberFormatter.formatCurrency(totalTarget, currency),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "مجموع مانده تا تکمیل",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = PersianNumberFormatter.formatCurrency(totalRemaining, currency),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalCircularGauge(
    progress: Float,
    accentColor: Color,
    currency: AppCurrency,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "goal_gauge"
    )
    val percent = (progress.coerceIn(0f, 1f) * 100).toInt()

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            drawArc(
                color = accentColor.copy(alpha = 0.16f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.75f),
                        accentColor,
                        accentColor.copy(alpha = 0.9f)
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${PersianNumberFormatter.formatDigits(percent, currency)}٪",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
        }
    }
}

@Composable
fun AddGoalDialog(
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (title: String, targetAmount: Long, note: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعریف هدف پس‌انداز جدید", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان هدف (مثال: خرید آیفون، سفر، طلا)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                PersianAmountInput(
                    value = targetText,
                    onValueChange = { targetText = it },
                    currency = currency,
                    label = "مبلغ کل هدف"
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("یادداشت یا انگیزه هدف (اختیاری)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = PersianNumberFormatter.parseAmount(targetText)
                    if (title.isNotBlank() && target > 0L) {
                        onSave(title, target, note)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ثبت هدف", fontWeight = FontWeight.Bold)
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
fun EditGoalDialog(
    goal: FinancialGoalEntity,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onSave: (newTitle: String, newTarget: Long, newCurrent: Long, newNote: String) -> Unit
) {
    var title by remember { mutableStateOf(goal.title) }
    var targetText by remember { mutableStateOf(goal.targetAmount.toString()) }
    var currentText by remember { mutableStateOf(goal.currentAmount.toString()) }
    var note by remember { mutableStateOf(goal.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش هدف پس‌انداز", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان هدف") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                PersianAmountInput(
                    value = targetText,
                    onValueChange = { targetText = it },
                    currency = currency,
                    label = "مبلغ کل هدف"
                )
                PersianAmountInput(
                    value = currentText,
                    onValueChange = { currentText = it },
                    currency = currency,
                    label = "موجودی فعلی جمع‌شده"
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("یادداشت (اختیاری)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = PersianNumberFormatter.parseAmount(targetText)
                    val current = PersianNumberFormatter.parseAmount(currentText)
                    if (title.isNotBlank() && target > 0L) {
                        onSave(title, target, current, note)
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("بروزرسانی", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

/**
 * Iconic Glassmorphic Deposit & Withdrawal Modal for Savings Goals
 * Features:
 * - Animated Deposit (Green) vs Withdraw (Crimson) Mode Switcher
 * - Live Before -> After Circular Progress & Balance Preview
 * - Quick Percentage / Remaining Amount Chips
 * - Optional Bank Account Synchronization (Deduct from or Return to Account)
 */
@Composable
fun GoalDepositDialog(
    goal: FinancialGoalEntity,
    initialIsDeposit: Boolean = true,
    accounts: List<AccountEntity>,
    currency: AppCurrency,
    onDismiss: () -> Unit,
    onAction: (isDeposit: Boolean, amount: Long, accountId: Long?) -> Unit
) {
    val context = LocalContext.current
    var isDeposit by remember { mutableStateOf(initialIsDeposit) }
    var amountText by remember { mutableStateOf("") }
    var linkToAccount by remember { mutableStateOf(accounts.isNotEmpty()) }
    var selectedAccId by remember { mutableStateOf<Long?>(accounts.firstOrNull()?.id) }

    val typedAmount = PersianNumberFormatter.parseAmount(amountText)
    val remainingToTarget = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0L)

    val projectedAmount = if (isDeposit) {
        goal.currentAmount + typedAmount
    } else {
        (goal.currentAmount - typedAmount).coerceAtLeast(0L)
    }

    val currentProgress = if (goal.targetAmount > 0L) {
        (goal.currentAmount.toFloat() / goal.targetAmount.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val projectedProgress = if (goal.targetAmount > 0L) {
        (projectedAmount.toFloat() / goal.targetAmount.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val activeColor = if (isDeposit) IncomeGreen else ExpenseRed

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            isHighlighted = true,
            borderColor = activeColor.copy(alpha = 0.65f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Header with Icon Badge + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(activeColor.copy(alpha = 0.16f))
                                .border(1.dp, activeColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDeposit) Icons.Default.Savings else Icons.Default.Output,
                                contentDescription = null,
                                tint = activeColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isDeposit) "واریز به هدف پس‌انداز" else "برداشت از پس‌انداز",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = activeColor
                            )
                            Text(
                                text = goal.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                // Segmented Mode Switcher: Deposit vs Withdraw
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDeposit) IncomeGreen else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isDeposit = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SouthWest,
                                contentDescription = null,
                                tint = if (isDeposit) Color.White else IncomeGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "افزایش پس‌انداز (واریز)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDeposit) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isDeposit) ExpenseRed else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isDeposit = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NorthEast,
                                contentDescription = null,
                                tint = if (!isDeposit) Color.White else ExpenseRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "کسر از هدف (برداشت)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (!isDeposit) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Live Interactive Before -> After Visual Preview Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = activeColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GoalCircularGauge(
                            progress = projectedProgress,
                            accentColor = activeColor,
                            currency = currency,
                            size = 64.dp,
                            strokeWidth = 6.dp
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (typedAmount > 0L) "وضعیت پس‌انداز پس از ثبت:" else "موجودی فعلی این هدف:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = PersianNumberFormatter.formatCurrency(projectedAmount, currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = activeColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val currentPct = (currentProgress * 100).toInt()
                            val newPct = (projectedProgress * 100).toInt()
                            Text(
                                text = if (typedAmount > 0L) {
                                    "تغییر پیشرفت: ${PersianNumberFormatter.formatDigits(currentPct, currency)}٪ ⬅ ${PersianNumberFormatter.formatDigits(newPct, currency)}٪"
                                } else {
                                    "مانده تا سقف هدف: ${PersianNumberFormatter.formatCurrency(remainingToTarget, currency)}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Amount Input
                PersianAmountInput(
                    value = amountText,
                    onValueChange = { amountText = it },
                    currency = currency,
                    label = if (isDeposit) "مبلغ واریزی به هدف" else "مبلغ برداشتی از هدف"
                )

                // Quick Preset Amount Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "انتخاب سریع مبلغ:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isDeposit) {
                            if (remainingToTarget > 0L) {
                                item {
                                    QuickPresetChip(
                                        label = "تکمیل کل مانده (${PersianNumberFormatter.formatNumberWithCommas(remainingToTarget, currency)})",
                                        color = IncomeGreen,
                                        onClick = { amountText = remainingToTarget.toString() }
                                    )
                                }
                                val quarterTarget = (goal.targetAmount / 4).coerceAtLeast(10000L)
                                if (quarterTarget < remainingToTarget) {
                                    item {
                                        QuickPresetChip(
                                            label = "۲۵٪ هدف (${PersianNumberFormatter.formatNumberWithCommas(quarterTarget, currency)})",
                                            color = TransferBlue,
                                            onClick = { amountText = quarterTarget.toString() }
                                        )
                                    }
                                }
                            }
                            val stepPresets = listOf(500_000L, 1_000_000L, 2_000_000L, 5_000_000L)
                            items(stepPresets) { preset ->
                                QuickPresetChip(
                                    label = "+ ${PersianNumberFormatter.formatNumberWithCommas(preset, currency)}",
                                    color = IncomeGreen,
                                    onClick = {
                                        val cur = PersianNumberFormatter.parseAmount(amountText)
                                        amountText = (cur + preset).toString()
                                    }
                                )
                            }
                        } else {
                            // Withdraw presets based on current saved amount
                            if (goal.currentAmount > 0L) {
                                item {
                                    QuickPresetChip(
                                        label = "کل موجودی (${PersianNumberFormatter.formatNumberWithCommas(goal.currentAmount, currency)})",
                                        color = ExpenseRed,
                                        onClick = { amountText = goal.currentAmount.toString() }
                                    )
                                }
                                val halfSaved = goal.currentAmount / 2
                                if (halfSaved > 0L) {
                                    item {
                                        QuickPresetChip(
                                            label = "نصف موجودی (${PersianNumberFormatter.formatNumberWithCommas(halfSaved, currency)})",
                                            color = WarningAmber,
                                            onClick = { amountText = halfSaved.toString() }
                                        )
                                    }
                                }
                                val quarterSaved = goal.currentAmount / 4
                                if (quarterSaved > 0L) {
                                    item {
                                        QuickPresetChip(
                                            label = "۲۵٪ موجودی (${PersianNumberFormatter.formatNumberWithCommas(quarterSaved, currency)})",
                                            color = TransferBlue,
                                            onClick = { amountText = quarterSaved.toString() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Account Synchronization Toggle & Selector
                if (accounts.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (isDeposit) "کسر همزمان از موجودی حساب بانکی" else "بازگشت وجه به حساب بانکی",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (linkToAccount) "یک تراکنش خودکار هم در حساب ثبت می‌شود" else "بدون تغییر در موجودی کارت‌ها",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                                Switch(
                                    checked = linkToAccount,
                                    onCheckedChange = { linkToAccount = it }
                                )
                            }

                            AnimatedVisibility(visible = linkToAccount) {
                                val selectedAccObj = accounts.firstOrNull { it.id == selectedAccId } ?: accounts.first()
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    DodotaDropdownField(
                                        label = if (isDeposit) "انتخاب حساب مبدأ واریز" else "انتخاب حساب مقصد برداشت",
                                        selectedText = "${selectedAccObj.name} (${PersianNumberFormatter.formatCurrency(selectedAccObj.balance, currency)})",
                                        items = accounts,
                                        itemText = { it.name },
                                        itemSubtitle = { "موجودی: ${PersianNumberFormatter.formatCurrency(it.balance, currency)}" },
                                        onItemSelected = { selectedAccId = it.id },
                                        leadingIcon = Icons.Default.CreditCard
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(0.9f)
                    ) {
                        Text("انصراف")
                    }

                    Button(
                        onClick = {
                            if (typedAmount <= 0L) {
                                Toast.makeText(context, "لطفاً مبلغ معتبری وارد کنید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!isDeposit && typedAmount > goal.currentAmount) {
                                Toast.makeText(context, "مبلغ برداشت بیشتر از موجودی پس‌انداز است", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onAction(
                                isDeposit,
                                typedAmount,
                                if (linkToAccount) selectedAccId else null
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(
                            imageVector = if (isDeposit) Icons.Default.CheckCircle else Icons.Default.Output,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDeposit) "ثبت واریز به هدف" else "ثبت برداشت وجه",
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPresetChip(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}
