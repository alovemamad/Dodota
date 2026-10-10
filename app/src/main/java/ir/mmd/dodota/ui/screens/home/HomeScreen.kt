package ir.mmd.dodota.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.ui.components.CategoryIconView
import ir.mmd.dodota.ui.components.DodotaExpandableSection
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.theme.*
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter

@Composable
fun HomeScreen(
    userPreferences: UserPreferences,
    accounts: List<AccountEntity>,
    recentTransactions: List<TransactionEntity>,
    allTransactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    upcomingInstallments: List<InstallmentEntity>,
    activeReminders: List<ReminderEntity>,
    netWorth: Long,
    onNavigateToAddTransaction: (type: String) -> Unit,
    onNavigateToTransactionDetail: (id: Long) -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCardsManager: () -> Unit = {},
    onNavigateToInstallments: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToAssets: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToChecklists: () -> Unit,
    onNavigateToRecurring: () -> Unit = {}
) {
    var isBalanceVisible by remember { mutableStateOf(true) }
    val activeAccounts = remember(accounts) { accounts.filter { it.isActive } }
    val totalBalance = activeAccounts.sumOf { it.balance }
    val glassStyle = LocalDodotaGlassStyle.current

    // Calculate current Persian month income & expense from allTransactions
    val currentPersianDate = PersianDateHelper.getCurrentPersianDate()
    val startOfMonthMillis = PersianDateHelper.getStartOfMonthMillis(currentPersianDate.year, currentPersianDate.month)
    val endOfMonthMillis = PersianDateHelper.getEndOfMonthMillis(currentPersianDate.year, currentPersianDate.month)

    val currentMonthTransactions = remember(allTransactions, startOfMonthMillis, endOfMonthMillis) {
        allTransactions.filter { it.dateMillis in startOfMonthMillis..endOfMonthMillis }
    }
    val currentMonthIncome = currentMonthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val currentMonthExpense = currentMonthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient VIP Red / Theme Glow in Top Background
        if (glassStyle.isGlassEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                glassStyle.accentGlowColor.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Header: Brand + Persian Date + Calendar Shortcut
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "دودوتا",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = if (userPreferences.themeMode == AppThemeMode.VIP_RED) "VIP RED" else "PRO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = PersianDateHelper.getFormattedTodayPersian(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = glassStyle.cardContainerColor,
                            border = BorderStroke(1.dp, glassStyle.cardBorderColor),
                            modifier = Modifier
                                .size(46.dp)
                                .clickable { onNavigateToCalendar() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = "تقویم مالی",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 2. Main VIP Glassmorphic Balance Hero Card
            item {
                val heroGradient = if (userPreferences.themeMode == AppThemeMode.VIP_RED) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4C0815),
                            Color(0xFF22050B),
                            Color(0xFF121218)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                        )
                    )
                }

                val totalMonthlyFlow = (currentMonthIncome + currentMonthExpense).coerceAtLeast(1L)
                val incomeFlowRatio = if (currentMonthIncome + currentMonthExpense > 0L) {
                    (currentMonthIncome.toFloat() / totalMonthlyFlow.toFloat()).coerceIn(0.08f, 0.92f)
                } else 0.5f

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.5.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                Color.White.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(heroGradient)
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.15f, size.height * 0.2f),
                                    radius = size.minDimension * 0.65f
                                ),
                                radius = size.minDimension * 0.65f,
                                center = Offset(size.width * 0.15f, size.height * 0.2f)
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        IncomeGreen.copy(alpha = 0.10f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.85f, size.height * 0.85f),
                                    radius = size.minDimension * 0.55f
                                ),
                                radius = size.minDimension * 0.55f,
                                center = Offset(size.width * 0.85f, size.height * 0.85f)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ShieldMoon,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "موجودی کل حساب‌ها",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Text(
                                            text = "${PersianNumberFormatter.toPersianDigits(activeAccounts.size)} حساب فعال",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { isBalanceVisible = !isBalanceVisible },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.14f))
                                    ) {
                                        Icon(
                                            imageVector = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "نمایش/مخفی‌سازی موجودی",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (isBalanceVisible) {
                                    PersianNumberFormatter.formatCurrency(totalBalance, userPreferences.currency)
                                } else "•••••••• ${userPreferences.currency.unitName}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            if (netWorth != totalBalance) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBalanceVisible) {
                                        "خالص دارایی و ثروت کل: ${PersianNumberFormatter.formatCurrency(netWorth, userPreferences.currency)}"
                                    } else "خالص دارایی و ثروت کل: ••••••••",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Income & Expense Glassmorphic Sub-Pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Monthly Income
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White.copy(alpha = 0.09f),
                                    border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.4f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(IncomeGreen.copy(alpha = 0.22f))
                                                .border(1.dp, IncomeGreen.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.SouthWest,
                                                contentDescription = null,
                                                tint = IncomeGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "درآمد این ماه",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.78f)
                                            )
                                            Text(
                                                text = if (isBalanceVisible) PersianNumberFormatter.formatNumberWithCommas(currentMonthIncome, userPreferences.currency) else "•••",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = IncomeGreen,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Monthly Expense
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White.copy(alpha = 0.09f),
                                    border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.4f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(ExpenseRed.copy(alpha = 0.22f))
                                                .border(1.dp, ExpenseRed.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.NorthEast,
                                                contentDescription = null,
                                                tint = ExpenseRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "هزینه این ماه",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.78f)
                                            )
                                            Text(
                                                text = if (isBalanceVisible) PersianNumberFormatter.formatNumberWithCommas(currentMonthExpense, userPreferences.currency) else "•••",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF6B81),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (currentMonthIncome > 0L || currentMonthExpense > 0L) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(incomeFlowRatio)
                                            .fillMaxHeight()
                                            .background(IncomeGreen)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight((1f - incomeFlowRatio).coerceAtLeast(0.08f))
                                            .fillMaxHeight()
                                            .background(Color(0xFFFF6B81))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Quick Action Glass Buttons (Cards Manager replaces Transfer per Items 14 & 15)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickActionButton(
                        title = "ثبت درآمد",
                        icon = Icons.Default.AddCard,
                        color = IncomeGreen,
                        onClick = { onNavigateToAddTransaction("INCOME") }
                    )
                    QuickActionButton(
                        title = "ثبت هزینه",
                        icon = Icons.Default.LocalMall,
                        color = ExpenseRed,
                        onClick = { onNavigateToAddTransaction("EXPENSE") }
                    )
                    QuickActionButton(
                        title = "کارت‌های من",
                        icon = Icons.Default.CreditCard,
                        color = TransferBlue,
                        onClick = onNavigateToCardsManager
                    )
                    QuickActionButton(
                        title = "تقویم مالی",
                        icon = Icons.Default.CalendarMonth,
                        color = WarningAmber,
                        onClick = onNavigateToCalendar
                    )
                }
            }

            // 4. Accounts Horizontal Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "حساب‌ها و کارت‌های من",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    TextButton(onClick = onNavigateToAccounts) {
                        Text("مدیریت حساب‌ها", fontWeight = FontWeight.Bold)
                    }
                }

                if (activeAccounts.isEmpty()) {
                    GlassCard(
                        onClick = onNavigateToAccounts,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("هنوز حسابی ثبت نشده است. برای افزودن حساب ضربه بزنید.")
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(activeAccounts) { account ->
                            val accColor = try {
                                Color(android.graphics.Color.parseColor(account.colorHex))
                            } catch (_: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            GlassCard(
                                shape = RoundedCornerShape(22.dp),
                                borderColor = accColor.copy(alpha = 0.5f),
                                onClick = onNavigateToAccounts,
                                modifier = Modifier.width(215.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CategoryIconView(
                                            iconName = account.iconName,
                                            colorHex = account.colorHex,
                                            size = 38.dp
                                        )
                                        Text(
                                            text = account.type.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (account.bankName.isNotBlank()) {
                                        Text(
                                            text = account.bankName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isBalanceVisible) {
                                            PersianNumberFormatter.formatCurrency(account.balance, userPreferences.currency)
                                        } else "••••••",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Financial Tools Grid (Collapsible Section without subtitle per Item 29, renamed items per 31, 32, 33)
            item {
                DodotaExpandableSection(
                    title = "ابزارهای پیشرفته مالی",
                    subtitle = null,
                    icon = Icons.Default.DashboardCustomize,
                    initiallyExpanded = true
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            ToolChip(
                                title = "اقساط",
                                icon = Icons.Default.EventRepeat,
                                color = Color(0xFF8B5CF6),
                                onClick = onNavigateToInstallments
                            )
                        }
                        item {
                            ToolChip(
                                title = "اهداف",
                                icon = Icons.Default.Savings,
                                color = Color(0xFF06B6D4),
                                onClick = onNavigateToGoals
                            )
                        }
                        item {
                            ToolChip(
                                title = "چک‌لیست",
                                icon = Icons.Default.Checklist,
                                color = Color(0xFF14B8A6),
                                onClick = onNavigateToChecklists
                            )
                        }
                        item {
                            ToolChip(
                                title = "بودجه‌بندی",
                                icon = Icons.Default.PieChart,
                                color = Color(0xFF10B981),
                                onClick = onNavigateToBudgets
                            )
                        }
                        item {
                            ToolChip(
                                title = "دارایی و ثروت",
                                icon = Icons.Default.Diamond,
                                color = Color(0xFFF59E0B),
                                onClick = onNavigateToAssets
                            )
                        }
                        item {
                            ToolChip(
                                title = "یادآورها",
                                icon = Icons.Default.NotificationsActive,
                                color = Color(0xFFEC4899),
                                onClick = onNavigateToReminders
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            color.copy(alpha = 0.25f),
                            color.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ToolChip(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    val glassStyle = LocalDodotaGlassStyle.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = glassStyle.cardContainerColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
