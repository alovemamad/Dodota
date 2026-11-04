package ir.mmd.dodota.ui

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Snooze
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.preferences.UserPreferencesRepository
import ir.mmd.dodota.data.repository.BackupRepository
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.navigation.BottomNavItem
import ir.mmd.dodota.ui.navigation.Screen
import ir.mmd.dodota.ui.screens.accounts.AccountDetailScreen
import ir.mmd.dodota.ui.screens.accounts.AccountsScreen
import ir.mmd.dodota.ui.screens.accounts.CardsManagerScreen
import ir.mmd.dodota.ui.screens.assets.AssetsScreen
import ir.mmd.dodota.ui.screens.auth.LockScreen
import ir.mmd.dodota.ui.screens.auth.RecoveryScreen
import ir.mmd.dodota.ui.screens.backup.BackupRestoreScreen
import ir.mmd.dodota.ui.screens.budgets.BudgetsScreen
import ir.mmd.dodota.ui.screens.calendar.FinancialCalendarScreen
import ir.mmd.dodota.ui.screens.checklist.ChecklistsScreen
import ir.mmd.dodota.ui.screens.chichi.ChandRoozScreen
import ir.mmd.dodota.ui.screens.debts.ChecksScreen
import ir.mmd.dodota.ui.screens.debts.DebtDetailScreen
import ir.mmd.dodota.ui.screens.debts.DebtsScreen
import ir.mmd.dodota.ui.screens.goals.GoalsScreen
import ir.mmd.dodota.ui.screens.home.HomeScreen
import ir.mmd.dodota.ui.screens.installments.InstallmentDetailScreen
import ir.mmd.dodota.ui.screens.installments.InstallmentsScreen
import ir.mmd.dodota.ui.screens.onboarding.OnboardingScreen
import ir.mmd.dodota.ui.screens.reminders.RemindersScreen
import ir.mmd.dodota.ui.screens.reports.ReportsScreen
import ir.mmd.dodota.ui.screens.settings.AboutScreen
import ir.mmd.dodota.ui.screens.settings.SecuritySettingsScreen
import ir.mmd.dodota.ui.screens.settings.SettingsScreen
import ir.mmd.dodota.ui.screens.splash.SplashScreen
import ir.mmd.dodota.ui.screens.tools.BahesabScreen
import ir.mmd.dodota.ui.screens.transactions.AddEditTransactionScreen
import ir.mmd.dodota.ui.screens.transactions.TransactionsScreen
import ir.mmd.dodota.ui.screens.trash.TrashScreen
import androidx.compose.ui.platform.testTag
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.ui.theme.LocalDodotaGlassStyle
import ir.mmd.dodota.util.AlarmHelper
import ir.mmd.dodota.util.AlarmSoundManager
import ir.mmd.dodota.util.ReminderAlarmReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DodotaApp(
    repository: DodotaRepository,
    preferencesRepo: UserPreferencesRepository,
    backupRepository: BackupRepository,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val userPreferences by preferencesRepo.userPreferencesFlow.collectAsState(initial = UserPreferences())
    val accounts by repository.allAccounts.collectAsState(initial = emptyList())
    val transactions by repository.allTransactions.collectAsState(initial = emptyList())
    val categories by repository.allCategories.collectAsState(initial = emptyList())
    val debts by repository.allDebts.collectAsState(initial = emptyList())
    val installmentPlans by repository.allInstallmentPlans.collectAsState(initial = emptyList())
    val allInstallments by repository.allInstallments.collectAsState(initial = emptyList())
    val upcomingInstallments by repository.upcomingInstallments.collectAsState(initial = emptyList())
    val activeReminders by repository.activeReminders.collectAsState(initial = emptyList())
    val goals by repository.allGoals.collectAsState(initial = emptyList())
    val assets by repository.allAssets.collectAsState(initial = emptyList())
    val netWorth by repository.netWorth.collectAsState(initial = 0L)
    val allBudgets by repository.allBudgets.collectAsState(initial = emptyList())
    val activeAlarm by AlarmSoundManager.activeAlarmState.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val coroutineScope = rememberCoroutineScope()
    val glassStyle = LocalDodotaGlassStyle.current

    // ---------------------------------------------------------
    // 1. AUTOMATIC STARTUP PERMISSIONS REQUEST (Notifications, Reminders & Backup)
    // ---------------------------------------------------------
    var hasRequestedStartupPermissions by remember { mutableStateOf(false) }
    var showPermissionsGuideDialog by remember { mutableStateOf(false) }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val notifGranted = AlarmHelper.isNotificationPermissionGranted(context)
        val exactAlarmGranted = AlarmHelper.canScheduleExactAlarms(context)
        if (!notifGranted || !exactAlarmGranted) {
            showPermissionsGuideDialog = true
        }
    }

    LaunchedEffect(currentRoute) {
        if (!hasRequestedStartupPermissions && currentRoute == Screen.Home.route) {
            hasRequestedStartupPermissions = true
            val requiredPerms = AlarmHelper.getRequiredStartupPermissions()
            val missingPerms = requiredPerms.filter { perm ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPerms.isNotEmpty()) {
                try {
                    multiplePermissionsLauncher.launch(missingPerms.toTypedArray())
                } catch (_: Exception) {}
            } else if (!AlarmHelper.canScheduleExactAlarms(context)) {
                showPermissionsGuideDialog = true
            }
        }
    }

    // ---------------------------------------------------------
    // 2. SCHEDULE ALL ACTIVE REMINDERS WITH ALARM MANAGER
    // Ensures all scheduled reminders ring & notify reliably on time
    // ---------------------------------------------------------
    LaunchedEffect(activeReminders) {
        val nowInit = System.currentTimeMillis()
        activeReminders.forEach { rem ->
            if (rem.isEnabled && !rem.isCompleted && rem.dueDateTimeMillis > nowInit) {
                AlarmHelper.scheduleExactAlarm(
                    context = context,
                    reminderId = rem.id,
                    title = rem.title,
                    description = rem.description.ifEmpty { "موعد ${rem.targetType.title} فرا رسیده است." },
                    triggerAtMillis = rem.dueDateTimeMillis,
                    isFromBoot = true
                )
            }
        }
    }

    if (showPermissionsGuideDialog) {
        val notifGranted = AlarmHelper.isNotificationPermissionGranted(context)
        val exactAlarmGranted = AlarmHelper.canScheduleExactAlarms(context)
        val backupGranted = AlarmHelper.isBackupPermissionGranted(context)

        AlertDialog(
            onDismissRequest = { showPermissionsGuideDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("مجوزهای ضروری یادآور و پشتیبان‌گیری", fontWeight = FontWeight.ExtraBold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "برای ارسال به‌موقع یادآورهای چک، قسط و بدهی و همچنین ذخیره خودکار فایل‌های بک‌آپ، مجوزهای زیر مورد نیاز است:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (notifGranted) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (notifGranted) IncomeGreen else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ارسال اعلان و یادآورها", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("نمایش هشدار سررسید چک، قسط و بدهی", style = MaterialTheme.typography.labelSmall)
                            }
                            if (!notifGranted) {
                                TextButton(onClick = { AlarmHelper.openAppNotificationSettings(context) }) {
                                    Text("فعال‌سازی")
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (exactAlarmGranted) Icons.Default.CheckCircle else Icons.Default.AlarmOn,
                                contentDescription = null,
                                tint = if (exactAlarmGranted) IncomeGreen else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("زنگ هشدار و آلارم دقیق", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("پخش آلارم سر ساعت حتی در حالت بسته بودن برنامه", style = MaterialTheme.typography.labelSmall)
                            }
                            if (!exactAlarmGranted) {
                                TextButton(onClick = { AlarmHelper.openExactAlarmSettings(context) }) {
                                    Text("فعال‌سازی")
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (backupGranted) Icons.Default.CheckCircle else Icons.Default.Backup,
                                contentDescription = null,
                                tint = if (backupGranted) IncomeGreen else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("دسترسی پوشه پشتیبان‌گیری", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("ذخیره فایل‌های JSON در پوشه MyFinanceBackup", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!notifGranted) {
                            val perms = AlarmHelper.getRequiredStartupPermissions()
                            if (perms.isNotEmpty()) {
                                try {
                                    multiplePermissionsLauncher.launch(perms.toTypedArray())
                                } catch (_: Exception) {
                                    AlarmHelper.openAppNotificationSettings(context)
                                }
                            }
                        } else if (!exactAlarmGranted) {
                            AlarmHelper.openExactAlarmSettings(context)
                        }
                        showPermissionsGuideDialog = false
                    }
                ) {
                    Text("تأیید و اعطای مجوز")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionsGuideDialog = false }) {
                    Text("متوجه شدم")
                }
            }
        )
    }

    // Active Reminder Alarm Full Dialog
    if (activeAlarm != null) {
        val currentAlarm = activeAlarm!!
        AlertDialog(
            onDismissRequest = { /* Require user to click Stop or Snooze */ },
            icon = {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "🔔 سررسید موعد یادآور",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = currentAlarm.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (currentAlarm.desc.isNotBlank()) {
                        Text(
                            text = currentAlarm.desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AlarmOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "صدای زنگ و آلارم در حال پخش است.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        AlarmSoundManager.stopAlarm(context, currentAlarm.notifId)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stop_alarm_button")
                ) {
                    Icon(Icons.Default.AlarmOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("قطع زنگ و بستن", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        AlarmSoundManager.snoozeAlarm(
                            context = context,
                            reminderId = currentAlarm.reminderId,
                            title = currentAlarm.title,
                            desc = currentAlarm.desc,
                            notifId = currentAlarm.notifId,
                            minutes = 10
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("snooze_alarm_button")
                ) {
                    Icon(Icons.Default.Snooze, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("۱۰ دقیقه بعد یادآوری کن (تعویق)")
                }
            }
        )
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Transactions.route,
        Screen.Reports.route,
        Screen.Debts.route,
        Screen.Settings.route
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 150))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .border(
                            BorderStroke(1.dp, glassStyle.cardBorderColor),
                            RoundedCornerShape(26.dp)
                        ),
                    color = glassStyle.cardContainerColor,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        BottomNavItem.values().forEach { item ->
                            val isSelected = currentRoute == item.route
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.18f else 1.0f,
                                animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                                label = "nav_icon_scale"
                            )
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(Screen.Home.route)
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding(),
            enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.97f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
            },
            exitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.97f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
            }
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    userPreferences = userPreferences,
                    onNavigateToOnboarding = {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToLock = {
                        navController.navigate(Screen.Lock.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToMain = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    repository = repository,
                    preferencesRepo = preferencesRepo,
                    onFinished = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Lock.route) {
                LockScreen(
                    userPreferences = userPreferences,
                    onUnlocked = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Lock.route) { inclusive = true }
                        }
                    },
                    onNavigateToRecovery = {
                        navController.navigate(Screen.Recovery.route)
                    }
                )
            }

            composable(Screen.Recovery.route) {
                RecoveryScreen(
                    userPreferences = userPreferences,
                    preferencesRepo = preferencesRepo,
                    onRecovered = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Recovery.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // Main Dashboard
            composable(Screen.Home.route) {
                HomeScreen(
                    userPreferences = userPreferences,
                    accounts = accounts,
                    allTransactions = transactions,
                    categories = categories,
                    upcomingInstallments = upcomingInstallments,
                    activeReminders = activeReminders,
                    netWorth = netWorth,
                    onNavigateToAddTransaction = { type ->
                        navController.navigate(Screen.AddTransaction.createRoute(type))
                    },
                    onNavigateToAccounts = {
                        navController.navigate(Screen.Accounts.route)
                    },
                    onNavigateToCardsManager = {
                        navController.navigate(Screen.CardsManager.route)
                    },
                    onNavigateToInstallments = {
                        navController.navigate(Screen.Installments.route)
                    },
                    onNavigateToReminders = {
                        navController.navigate(Screen.Reminders.route)
                    },
                    onNavigateToBudgets = {
                        navController.navigate(Screen.Budgets.route)
                    },
                    onNavigateToGoals = {
                        navController.navigate(Screen.Goals.route)
                    },
                    onNavigateToAssets = {
                        navController.navigate(Screen.Assets.route)
                    },
                    onNavigateToCalendar = {
                        navController.navigate(Screen.FinancialCalendar.route)
                    },
                    onNavigateToChecklists = {
                        navController.navigate(Screen.Checklists.route)
                    },
                    onNavigateToChecks = {
                        navController.navigate(Screen.Checks.route)
                    },
                    onNavigateToBahesab = {
                        navController.navigate(Screen.Bahesab.route)
                    },
                    onNavigateToChandRooz = {
                        navController.navigate(Screen.ChandRooz.route)
                    },
                    onToggleBalanceVisibility = {
                        coroutineScope.launch {
                            preferencesRepo.setBalanceHidden(!userPreferences.isBalanceHidden)
                        }
                    }
                )
            }

            // Transactions Tab
            composable(Screen.Transactions.route) {
                TransactionsScreen(
                    userPreferences = userPreferences,
                    transactions = transactions,
                    accounts = accounts,
                    categories = categories,
                    onNavigateToAdd = { type ->
                        navController.navigate(Screen.AddTransaction.createRoute(type.name))
                    },
                    onNavigateToEdit = { txId ->
                        navController.navigate(Screen.EditTransaction.createRoute(txId))
                    },
                    onDeleteToTrash = { tx ->
                        coroutineScope.launch {
                            repository.moveToTrash(tx)
                        }
                    }
                )
            }

            // Add Transaction
            composable(
                route = Screen.AddTransaction.route,
                arguments = listOf(navArgument("type") {
                    type = NavType.StringType
                    defaultValue = "EXPENSE"
                })
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "EXPENSE"
                AddEditTransactionScreen(
                    transactionId = null,
                    initialTypeString = type,
                    repository = repository,
                    userPreferences = userPreferences,
                    accounts = accounts,
                    categories = categories,
                    allTransactions = transactions,
                    allBudgets = allBudgets,
                    onBack = { navController.popBackStack() }
                )
            }

            // Edit Transaction
            composable(
                route = Screen.EditTransaction.route,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val txId = backStackEntry.arguments?.getLong("transactionId") ?: 0L
                AddEditTransactionScreen(
                    transactionId = txId,
                    initialTypeString = null,
                    repository = repository,
                    userPreferences = userPreferences,
                    accounts = accounts,
                    categories = categories,
                    allTransactions = transactions,
                    allBudgets = allBudgets,
                    onBack = { navController.popBackStack() }
                )
            }

            // Accounts
            composable(Screen.Accounts.route) {
                AccountsScreen(
                    accounts = accounts,
                    userPreferences = userPreferences,
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { accountId ->
                        navController.navigate(Screen.AccountDetail.createRoute(accountId))
                    },
                    onNavigateToCardsManager = {
                        navController.navigate(Screen.CardsManager.route)
                    }
                )
            }

            // Cards Manager
            composable(Screen.CardsManager.route) {
                CardsManagerScreen(
                    accounts = accounts,
                    userPreferences = userPreferences,
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.AccountDetail.route,
                arguments = listOf(navArgument("accountId") { type = NavType.LongType })
            ) { backStackEntry ->
                val accountId = backStackEntry.arguments?.getLong("accountId") ?: 0L
                AccountDetailScreen(
                    accountId = accountId,
                    repository = repository,
                    userPreferences = userPreferences,
                    categories = categories,
                    onBack = { navController.popBackStack() },
                    onNavigateToEditTransaction = { txId ->
                        navController.navigate(Screen.EditTransaction.createRoute(txId))
                    }
                )
            }

            // Debts, Checks & Installments Hub
            composable(Screen.Debts.route) {
                DebtsScreen(
                    debts = debts,
                    userPreferences = userPreferences,
                    repository = repository,
                    accounts = accounts,
                    installmentPlans = installmentPlans,
                    allInstallments = allInstallments,
                    activeReminders = activeReminders,
                    onBack = if (currentRoute == Screen.Debts.route) null else ({ navController.popBackStack() }),
                    onNavigateToDetail = { debtId ->
                        navController.navigate(Screen.DebtDetail.createRoute(debtId))
                    },
                    onNavigateToInstallmentDetail = { planId ->
                        navController.navigate(Screen.InstallmentDetail.createRoute(planId))
                    }
                )
            }

            composable(Screen.Checks.route) {
                ChecksScreen(
                    debts = debts,
                    userPreferences = userPreferences,
                    repository = repository,
                    accounts = accounts,
                    activeReminders = activeReminders,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { debtId ->
                        navController.navigate(Screen.DebtDetail.createRoute(debtId))
                    }
                )
            }

            composable(
                route = Screen.DebtDetail.route,
                arguments = listOf(navArgument("debtId") { type = NavType.LongType })
            ) { backStackEntry ->
                val debtId = backStackEntry.arguments?.getLong("debtId") ?: 0L
                DebtDetailScreen(
                    debtId = debtId,
                    repository = repository,
                    userPreferences = userPreferences,
                    accounts = accounts,
                    onBack = { navController.popBackStack() }
                )
            }

            // Installments
            composable(Screen.Installments.route) {
                InstallmentsScreen(
                    plans = installmentPlans,
                    userPreferences = userPreferences,
                    repository = repository,
                    accounts = accounts,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { planId ->
                        navController.navigate(Screen.InstallmentDetail.createRoute(planId))
                    }
                )
            }

            composable(
                route = Screen.InstallmentDetail.route,
                arguments = listOf(navArgument("planId") { type = NavType.LongType })
            ) { backStackEntry ->
                val planId = backStackEntry.arguments?.getLong("planId") ?: 0L
                InstallmentDetailScreen(
                    planId = planId,
                    repository = repository,
                    userPreferences = userPreferences,
                    accounts = accounts,
                    onBack = { navController.popBackStack() }
                )
            }

            // Reports
            composable(Screen.Reports.route) {
                ReportsScreen(
                    transactions = transactions,
                    categories = categories,
                    userPreferences = userPreferences,
                    onBack = null
                )
            }

            // Budgets
            composable(Screen.Budgets.route) {
                BudgetsScreen(
                    repository = repository,
                    categories = categories,
                    transactions = transactions,
                    userPreferences = userPreferences,
                    onBack = { navController.popBackStack() }
                )
            }

            // Goals
            composable(Screen.Goals.route) {
                GoalsScreen(
                    goals = goals,
                    accounts = accounts,
                    userPreferences = userPreferences,
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }

            // Assets
            composable(Screen.Assets.route) {
                AssetsScreen(
                    assets = assets,
                    netWorth = netWorth,
                    userPreferences = userPreferences,
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }

            // Financial Calendar
            composable(Screen.FinancialCalendar.route) {
                FinancialCalendarScreen(
                    transactions = transactions,
                    categories = categories,
                    userPreferences = userPreferences,
                    debts = debts,
                    installmentPlans = installmentPlans,
                    allInstallments = allInstallments,
                    onBack = { navController.popBackStack() }
                )
            }

            // Reminders
            composable(Screen.Reminders.route) {
                RemindersScreen(
                    repository = repository,
                    userPreferences = userPreferences,
                    onBack = { navController.popBackStack() }
                )
            }

            // Checklists
            composable(Screen.Checklists.route) {
                ChecklistsScreen(
                    repository = repository,
                    userPreferences = userPreferences,
                    accounts = accounts,
                    categories = categories,
                    onBack = { navController.popBackStack() }
                )
            }

            // Bahesab (وام و سپرده)
            composable(Screen.Bahesab.route) {
                BahesabScreen(
                    userPreferences = userPreferences,
                    onBack = { navController.popBackStack() }
                )
            }

            // Chand Rooz (Chichi Plus)
            composable(Screen.ChandRooz.route) {
                ChandRoozScreen(
                    repository = repository,
                    userPreferences = userPreferences,
                    onBack = { navController.popBackStack() }
                )
            }

            // Backup & Restore
            composable(Screen.BackupRestore.route) {
                BackupRestoreScreen(
                    backupRepository = backupRepository,
                    userPreferences = userPreferences,
                    preferencesRepo = preferencesRepo,
                    onBack = { navController.popBackStack() }
                )
            }

            // Trash
            composable(Screen.Trash.route) {
                TrashScreen(
                    repository = repository,
                    userPreferences = userPreferences,
                    categories = categories,
                    onBack = { navController.popBackStack() }
                )
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen(
                    userPreferences = userPreferences,
                    preferencesRepo = preferencesRepo,
                    onNavigateToSecurity = { navController.navigate(Screen.SecuritySettings.route) },
                    onNavigateToBackup = { navController.navigate(Screen.BackupRestore.route) },
                    onNavigateToTrash = { navController.navigate(Screen.Trash.route) },
                    onNavigateToAbout = { navController.navigate(Screen.About.route) },
                    onBack = null
                )
            }

            composable(Screen.SecuritySettings.route) {
                SecuritySettingsScreen(
                    userPreferences = userPreferences,
                    preferencesRepo = preferencesRepo,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.About.route) {
                AboutScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
