package ir.mmd.dodota.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBars
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.model.TransactionEntity
import ir.mmd.dodota.data.model.TransactionType
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.ui.components.CategoryIconView
import ir.mmd.dodota.ui.components.parseHexColorCached
import ir.mmd.dodota.ui.screens.accounts.IranianBankLogoBadge
import ir.mmd.dodota.ui.screens.accounts.IranianBanksCatalog
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter

@Composable
fun GlassDodotaHome(
    userPreferences: UserPreferences,
    activeAccounts: List<AccountEntity>,
    allTransactions: List<TransactionEntity>,
    totalBalance: Long,
    netWorth: Long,
    currentMonthIncome: Long,
    currentMonthExpense: Long,
    formattedToday: String,
    upcomingInstallmentCount: Int,
    activeReminderCount: Int,
    onOpenPrintDialog: () -> Unit,
    onNavigateToAddTransaction: (type: String) -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCardsManager: () -> Unit,
    onNavigateToInstallments: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToAssets: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToChecklists: () -> Unit,
    onNavigateToChecks: () -> Unit,
    onNavigateToBahesab: () -> Unit,
    onNavigateToChandRooz: () -> Unit,
    onToggleBalanceVisibility: () -> Unit,
) {
    val c = Glass.colors
    val prefs = Glass.prefs
    val backdrop = LocalGlassBackdrop.current
    val tilt = LocalGlassTilt.current
    val isBalanceVisible = !userPreferences.isBalanceHidden
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = glassBottomExtraPadding() + navBottom + 20.dp

    val currentDate = remember { PersianDateHelper.getCurrentPersianDate() }
    val monthStartMillis = remember(currentDate.year, currentDate.month) {
        PersianDateHelper.getStartOfMonthMillis(currentDate.year, currentDate.month)
    }
    val monthEndMillis = remember(currentDate.year, currentDate.month) {
        PersianDateHelper.getEndOfMonthMillis(currentDate.year, currentDate.month)
    }

    val (dailyIncomeSeries, dailyExpenseSeries) = remember(allTransactions, monthStartMillis, monthEndMillis, currentDate.day) {
        val daysCount = currentDate.day.coerceIn(7, 31)
        val incBuckets = FloatArray(daysCount)
        val expBuckets = FloatArray(daysCount)
        for (tx in allTransactions) {
            if (tx.dateMillis in monthStartMillis..monthEndMillis) {
                val dayIdx = (PersianDateHelper.fromMillis(tx.dateMillis).day - 1).coerceIn(0, daysCount - 1)
                when (tx.type) {
                    TransactionType.INCOME -> incBuckets[dayIdx] += tx.amount.toFloat()
                    TransactionType.EXPENSE -> expBuckets[dayIdx] += tx.amount.toFloat()
                    TransactionType.TRANSFER -> Unit
                }
            }
        }
        incBuckets.toList() to expBuckets.toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. iOS-style Large Title + Glass Tool Capsule
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "دودوتا",
                            style = GlassType.largeTitle(),
                            color = c.label,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formattedToday,
                            style = GlassType.subhead(),
                            color = c.label2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GlassToolGroup(
                        items = listOf(
                            GlassToolItem(
                                icon = Icons.Default.Print,
                                label = "چاپ گزارش مالی",
                                onClick = onOpenPrintDialog,
                                modifier = Modifier.testTag("home_print_button"),
                            ),
                            GlassToolItem(
                                icon = Icons.Default.CalendarMonth,
                                label = "تقویم مالی",
                                onClick = onNavigateToCalendar,
                                modifier = Modifier.testTag("home_calendar_button"),
                            ),
                        ),
                        backdrop = backdrop,
                        tilt = tilt,
                    )
                }
            }
        }

        // 2. Hero Frosted Net Worth & Monthly Flow Module
        item {
            val totalFlow = (currentMonthIncome + currentMonthExpense).coerceAtLeast(1L)
            val incomeRatio = if (currentMonthIncome + currentMonthExpense > 0L) {
                (currentMonthIncome.toFloat() / totalFlow.toFloat()).coerceIn(0.08f, 0.92f)
            } else 0.5f

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .glassModule(c, GlassShapes.cell)
                    .padding(18.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassBadge(color = c.tint, icon = GlassIcons.Shield, size = 28.dp)
                        Column {
                            Text("موجودی کل حساب‌ها", style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold), color = c.label2)
                            Text(
                                "${PersianNumberFormatter.formatDigits(activeAccounts.size, userPreferences.currency)} حساب فعال",
                                style = GlassType.caption(),
                                color = c.label3,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(c.fill2)
                            .testTag("glass_home_privacy_toggle")
                            .semantics { contentDescription = "نمایش یا مخفی‌سازی موجودی" }
                            .clickable(role = Role.Button, onClick = onToggleBalanceVisibility),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = c.label,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = if (isBalanceVisible) {
                        PersianNumberFormatter.formatCurrency(totalBalance, userPreferences.currency)
                    } else "•••••••• ${userPreferences.currency.unitName}",
                    style = GlassType.largeTitle().copy(fontSize = 27.sp, lineHeight = 34.sp),
                    color = c.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (netWorth != totalBalance) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isBalanceVisible) {
                            "خالص دارایی و ثروت کل: ${PersianNumberFormatter.formatCurrency(netWorth, userPreferences.currency)}"
                        } else "خالص دارایی و ثروت کل: ••••••••",
                        style = GlassType.footnote(),
                        color = c.label2,
                    )
                }

                if (prefs.homeGauges) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.green.copy(alpha = if (c.dark) 0.16f else 0.12f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Column {
                                Text("درآمد ماه", style = GlassType.caption(), color = c.label2)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isBalanceVisible) PersianNumberFormatter.formatNumberWithCommas(currentMonthIncome, userPreferences.currency) else "•••",
                                    style = GlassType.headline().copy(fontSize = 14.sp),
                                    color = c.green,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.red.copy(alpha = if (c.dark) 0.16f else 0.12f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Column {
                                Text("هزینه ماه", style = GlassType.caption(), color = c.label2)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (isBalanceVisible) PersianNumberFormatter.formatNumberWithCommas(currentMonthExpense, userPreferences.currency) else "•••",
                                    style = GlassType.headline().copy(fontSize = 14.sp),
                                    color = c.red,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    if (currentMonthIncome > 0L || currentMonthExpense > 0L) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(GlassShapes.capsule)
                                .background(c.fill2),
                        ) {
                            Box(
                                Modifier
                                    .weight(incomeRatio)
                                    .fillMaxHeight()
                                    .background(c.green),
                            )
                            Spacer(Modifier.width(2.dp))
                            Box(
                                Modifier
                                    .weight((1f - incomeRatio).coerceAtLeast(0.08f))
                                    .fillMaxHeight()
                                    .background(c.red),
                            )
                        }
                    }
                }
            }
        }

        // 3. Sparklines Trend Modules (Income / Expense daily trends this Persian month)
        if (prefs.homeSparklines) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlassSparkModule(
                        title = "روند درآمد",
                        icon = GlassIcons.Down,
                        amountText = if (isBalanceVisible) PersianNumberFormatter.formatNumberWithCommas(currentMonthIncome, userPreferences.currency) else "•••",
                        unit = userPreferences.currency.unitName,
                        points = dailyIncomeSeries,
                        color = c.green,
                        modifier = Modifier.weight(1f),
                    )
                    GlassSparkModule(
                        title = "روند هزینه",
                        icon = GlassIcons.Up,
                        amountText = if (isBalanceVisible) PersianNumberFormatter.formatNumberWithCommas(currentMonthExpense, userPreferences.currency) else "•••",
                        unit = userPreferences.currency.unitName,
                        points = dailyExpenseSeries,
                        color = c.red,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 4. Quick Actions Glass Modules
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassQuickTile(
                    title = "ثبت درآمد",
                    icon = Icons.Default.AddCard,
                    badgeColor = c.green,
                    onClick = { onNavigateToAddTransaction("INCOME") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("glass_quick_income"),
                )
                GlassQuickTile(
                    title = "ثبت هزینه",
                    icon = Icons.Default.LocalMall,
                    badgeColor = c.red,
                    onClick = { onNavigateToAddTransaction("EXPENSE") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("glass_quick_expense"),
                )
                GlassQuickTile(
                    title = "کارت‌ها",
                    icon = Icons.Default.CreditCard,
                    badgeColor = c.tint,
                    onClick = onNavigateToCardsManager,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("glass_quick_cards"),
                )
            }
        }

        // 5. Accounts Horizontal Strip
        if (prefs.homeAccountsStrip) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("حساب‌ها و کارت‌های من", style = GlassType.headline(), color = c.label)
                    GlassCapsuleButton(
                        text = "مدیریت",
                        onClick = onNavigateToAccounts,
                        modifier = Modifier.testTag("glass_manage_accounts_button"),
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (activeAccounts.isEmpty()) {
                    GlassGroup {
                        GlassRow(
                            title = "افزودن اولین حساب یا کارت",
                            subtitle = "برای مدیریت موجودی و ثبت تراکنش کلیک کنید",
                            leading = {
                                GlassBadge(color = c.tint, icon = Icons.Default.AccountBalanceWallet, size = 34.dp)
                            },
                            chevron = true,
                            onClick = onNavigateToAccounts,
                        )
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(activeAccounts, key = { it.id }) { account ->
                            val resolvedBank = remember(account.bankName, account.cardNumber) {
                                IranianBanksCatalog.resolveBank(account.bankName, account.cardNumber)
                            }
                            val fallback = c.tint
                            val accColor = resolvedBank?.primaryColor ?: remember(account.colorHex, fallback) {
                                parseHexColorCached(account.colorHex, fallback)
                            }
                            Column(
                                Modifier
                                    .width(196.dp)
                                    .glassModule(c, GlassShapes.module)
                                    .clickable(onClick = onNavigateToAccounts)
                                    .padding(14.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (resolvedBank != null) {
                                        IranianBankLogoBadge(bankInfo = resolvedBank, size = 36.dp)
                                    } else {
                                        CategoryIconView(
                                            iconName = account.iconName,
                                            colorHex = account.colorHex,
                                            size = 36.dp,
                                        )
                                    }
                                    Text(
                                        text = account.type.title,
                                        style = GlassType.caption(),
                                        color = c.label2,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = account.name,
                                    style = GlassType.headline().copy(fontSize = 15.sp),
                                    color = c.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (account.bankName.isNotBlank()) {
                                    Text(
                                        text = account.bankName,
                                        style = GlassType.caption(),
                                        color = c.label2,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (isBalanceVisible) {
                                        PersianNumberFormatter.formatCurrency(account.balance, userPreferences.currency)
                                    } else "••••••",
                                    style = GlassType.subhead().copy(fontWeight = FontWeight.Bold),
                                    color = accColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Financial Tools Grouped Section (iOS Settings-like Clean Grouped List)
        if (prefs.homeToolsGrid) {
            item {
                GlassSectionHeader("ابزارهای پیشرفته مالی")
                GlassGroup(inset = 58.dp) {
                    GlassRow(
                        title = "اقساط و وام‌ها",
                        value = if (upcomingInstallmentCount > 0) "${PersianNumberFormatter.formatDigits(upcomingInstallmentCount, userPreferences.currency)} سررسید" else null,
                        leading = { GlassBadge(IosColors.blue(c.dark), icon = Icons.Default.EventRepeat) },
                        chevron = true,
                        modifier = Modifier.testTag("glass_tool_installments"),
                        onClick = onNavigateToInstallments,
                    )
                    GlassRow(
                        title = "چک‌های دریافتی و پرداختی",
                        leading = { GlassBadge(IosColors.indigo(c.dark), icon = Icons.Default.FactCheck) },
                        chevron = true,
                        modifier = Modifier.testTag("glass_tool_checks"),
                        onClick = onNavigateToChecks,
                    )
                    GlassRow(
                        title = "بودجه‌بندی ماهانه",
                        leading = { GlassBadge(IosColors.orange(c.dark), icon = Icons.Default.PieChart) },
                        chevron = true,
                        modifier = Modifier.testTag("glass_tool_budgets"),
                        onClick = onNavigateToBudgets,
                    )
                    GlassRow(
                        title = "اهداف پس‌انداز",
                        leading = { GlassBadge(IosColors.green(c.dark), icon = Icons.Default.Savings) },
                        chevron = true,
                        modifier = Modifier.testTag("glass_tool_goals"),
                        onClick = onNavigateToGoals,
                    )
                    GlassRow(
                        title = "دارایی‌ها و ارزش خالص",
                        leading = { GlassBadge(IosColors.purple(c.dark), icon = Icons.Default.Diamond) },
                        chevron = true,
                        modifier = Modifier.testTag("glass_tool_assets"),
                        onClick = onNavigateToAssets,
                    )
                    GlassRow(
                        title = "یادآورهای هوشمند",
                        value = if (activeReminderCount > 0) "${PersianNumberFormatter.formatDigits(activeReminderCount, userPreferences.currency)} فعال" else null,
                        leading = { GlassBadge(IosColors.pink(c.dark), icon = Icons.Default.NotificationsActive) },
                        chevron = true,
                        modifier = Modifier.testTag("glass_tool_reminders"),
                        onClick = onNavigateToReminders,
                    )
                    GlassRow(
                        title = "چک‌لیست خرید و کارها",
                        leading = { GlassBadge(IosColors.teal(c.dark), icon = Icons.Default.Checklist) },
                        chevron = true,
                        modifier = Modifier.testTag("glass_tool_checklists"),
                        onClick = onNavigateToChecklists,
                    )
                    GlassRow(
                        title = "ماشین‌حساب بانکی (باحساب)",
                        leading = { GlassBadge(IosColors.mint(c.dark), icon = Icons.Default.Calculate) },
                        chevron = true,
                        modifier = Modifier.testTag("glass_tool_bahesab"),
                        onClick = onNavigateToBahesab,
                    )
                    if (userPreferences.isChichiPlusEnabled) {
                        GlassRow(
                            title = "چندروز؟ (ماموریت و رویدادها)",
                            leading = { GlassBadge(IosColors.yellow(c.dark), icon = Icons.Default.DateRange) },
                            chevron = true,
                            modifier = Modifier.testTag("glass_tool_chandrooz"),
                            onClick = onNavigateToChandRooz,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassSparkModule(
    title: String,
    icon: ImageVector,
    amountText: String,
    unit: String,
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    Box(
        modifier
            .height(116.dp)
            .glassModule(c, GlassShapes.cell),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter),
        ) {
            val peak = (points.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val n = if (points.size < 2) 10 else points.size
            val line = Path()
            val topPad = 6.dp.toPx()
            val usable = size.height - topPad
            for (i in 0 until n) {
                val v = if (points.size < 2) 0.06f else (points[i] / peak).coerceIn(0f, 1f) * 0.9f + 0.06f
                val x = size.width * i / (n - 1).coerceAtLeast(1)
                val y = topPad + usable - v * usable
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
                Text(
                    text = title,
                    style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold),
                    color = c.label2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = amountText,
                style = GlassType.title2().copy(fontSize = 18.sp),
                color = c.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = unit,
                style = GlassType.caption(),
                color = c.label2,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GlassQuickTile(
    title: String,
    icon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Glass.colors
    Column(
        modifier
            .glassModule(c, GlassShapes.module)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassBadge(color = badgeColor, icon = icon, size = 38.dp)
        Text(
            text = title,
            style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold),
            color = c.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
