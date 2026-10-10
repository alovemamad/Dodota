package ir.mmd.dodota.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Lock : Screen("lock")
    object Recovery : Screen("recovery")
    object Home : Screen("home")
    object Transactions : Screen("transactions")

    object AddTransaction : Screen("add_transaction?type={type}") {
        fun createRoute(type: String = "EXPENSE"): String = "add_transaction?type=$type"
    }

    object EditTransaction : Screen("edit_transaction/{transactionId}") {
        fun createRoute(transactionId: Long): String = "edit_transaction/$transactionId"
    }

    object Accounts : Screen("accounts")
    object CardsManager : Screen("cards_manager")
    object AccountDetail : Screen("account_detail/{accountId}") {
        fun createRoute(accountId: Long): String = "account_detail/$accountId"
    }

    object Debts : Screen("debts")
    object DebtDetail : Screen("debt_detail/{debtId}") {
        fun createRoute(debtId: Long): String = "debt_detail/$debtId"
    }

    object Installments : Screen("installments")
    object InstallmentDetail : Screen("installment_detail/{planId}") {
        fun createRoute(planId: Long): String = "installment_detail/$planId"
    }

    object Reports : Screen("reports")
    object Budgets : Screen("budgets")
    object Goals : Screen("goals")
    object Assets : Screen("assets")
    object FinancialCalendar : Screen("financial_calendar")
    object Recurring : Screen("recurring")
    object Reminders : Screen("reminders")
    object Checklists : Screen("checklists")
    object BackupRestore : Screen("backup_restore")
    object Trash : Screen("trash")
    object Settings : Screen("settings")
    object SecuritySettings : Screen("security_settings")
    object About : Screen("about")
}

enum class BottomNavItem(
    val title: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("ویترین", Screen.Home.route, Icons.Default.Dashboard, Icons.Outlined.Dashboard),
    TRANSACTIONS("تراکنش‌ها", Screen.Transactions.route, Icons.Default.ReceiptLong, Icons.Outlined.ReceiptLong),
    REPORTS("آمار و نمودار", Screen.Reports.route, Icons.Default.DonutLarge, Icons.Outlined.DonutLarge),
    DEBTS("بدهی و طلب", Screen.Debts.route, Icons.Default.Handshake, Icons.Outlined.Handshake),
    SETTINGS("تنظیمات", Screen.Settings.route, Icons.Default.Tune, Icons.Outlined.Tune)
}
