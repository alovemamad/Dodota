package ir.mmd.dodota.data.model

data class DodotaBackupPayload(
    val version: Int = 2,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val accounts: List<AccountBackupItem> = emptyList(),
    val categories: List<CategoryBackupItem> = emptyList(),
    val transactions: List<TransactionBackupItem> = emptyList(),
    val debts: List<DebtBackupItem> = emptyList(),
    val debtPayments: List<DebtPaymentBackupItem> = emptyList(),
    val installmentPlans: List<InstallmentPlanBackupItem> = emptyList(),
    val installments: List<InstallmentBackupItem> = emptyList(),
    val recurringTransactions: List<RecurringTransactionBackupItem> = emptyList(),
    val reminders: List<ReminderBackupItem> = emptyList(),
    val budgets: List<BudgetBackupItem> = emptyList(),
    val goals: List<GoalBackupItem> = emptyList(),
    val assets: List<AssetBackupItem> = emptyList(),
    val checklists: List<ChecklistBackupItem> = emptyList(),
    val checklistItems: List<ChecklistItemBackupItem> = emptyList()
)

data class AccountBackupItem(
    val id: Long,
    val name: String,
    val type: String,
    val balance: Long,
    val bankName: String = "",
    val cardOrAccountDescription: String = "",
    val cardNumber: String = "",
    val shebaNumber: String = "",
    val accountNumber: String = "",
    val cvv2: String = "",
    val expiryDate: String = "",
    val cardHolderName: String = "",
    val colorHex: String = "#E50914",
    val iconName: String = "card",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

data class CategoryBackupItem(
    val id: Long,
    val name: String,
    val type: String,
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = false,
    val parentCategoryId: Long? = null
)

data class TransactionBackupItem(
    val id: Long,
    val type: String,
    val amount: Long,
    val accountId: Long,
    val targetAccountId: Long? = null,
    val externalDestinationInfo: String = "",
    val categoryId: Long? = null,
    val subcategoryId: Long? = null,
    val dateMillis: Long,
    val timeString: String = "",
    val title: String = "",
    val note: String = "",
    val receiptUri: String? = null,
    val receiptImageBase64: String? = null,
    val tags: String = "",
    val isPinned: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringId: Long? = null,
    val installmentId: Long? = null,
    val debtPaymentId: Long? = null,
    val isDeleted: Boolean = false,
    val deletedAtMillis: Long? = null
)

data class DebtBackupItem(
    val id: Long,
    val type: String,
    val personOrCompanyName: String,
    val totalAmount: Long,
    val paidAmount: Long = 0L,
    val startDateMillis: Long = System.currentTimeMillis(),
    val dueDateMillis: Long? = null,
    val notes: String = "",
    val accountId: Long? = null,
    val status: String = "ACTIVE",
    val isDeleted: Boolean = false,
    val isCheck: Boolean = false,
    val checkNumber: String = ""
)

data class DebtPaymentBackupItem(
    val id: Long,
    val debtId: Long,
    val amount: Long,
    val dateMillis: Long,
    val accountId: Long? = null,
    val notes: String = ""
)

data class InstallmentPlanBackupItem(
    val id: Long,
    val title: String,
    val creditorOrCompany: String = "",
    val totalLoanAmount: Long = 0L,
    val totalAmount: Long,
    val numberOfInstallments: Int,
    val installmentAmount: Long,
    val paidInstallments: Int = 0,
    val startDateMillis: Long,
    val dueDayOfMonth: Int = 1,
    val notes: String = "",
    val autoCreateExpense: Boolean = true,
    val accountId: Long? = null,
    val isCompleted: Boolean = false
)

data class InstallmentBackupItem(
    val id: Long,
    val planId: Long,
    val installmentIndex: Int,
    val amount: Long,
    val dueDateMillis: Long,
    val paidDateMillis: Long? = null,
    val isPaid: Boolean = false,
    val transactionId: Long? = null
)

data class RecurringTransactionBackupItem(
    val id: Long,
    val title: String,
    val type: String,
    val amount: Long,
    val categoryId: Long? = null,
    val accountId: Long,
    val interval: String,
    val startDateMillis: Long,
    val nextDueDateMillis: Long,
    val lastProcessedDateMillis: Long? = null,
    val isActive: Boolean = true,
    val note: String = ""
)

data class ReminderBackupItem(
    val id: Long,
    val title: String,
    val description: String = "",
    val targetType: String,
    val targetId: Long? = null,
    val dueDateTimeMillis: Long,
    val reminderOffsetDays: Int = 1,
    val isCompleted: Boolean = false,
    val isEnabled: Boolean = true
)

data class BudgetBackupItem(
    val id: Long,
    val categoryId: Long,
    val monthlyAmount: Long,
    val monthKey: String,
    val warningThresholdPercent: Int = 80
)

data class GoalBackupItem(
    val id: Long,
    val title: String,
    val targetAmount: Long,
    val currentAmount: Long = 0L,
    val deadlineMillis: Long? = null,
    val note: String = "",
    val iconName: String = "savings",
    val colorHex: String = "#10B981",
    val accountId: Long? = null,
    val isCompleted: Boolean = false
)

data class AssetBackupItem(
    val id: Long,
    val name: String,
    val type: String,
    val estimatedValue: Long,
    val notes: String = "",
    val lastUpdateMillis: Long = System.currentTimeMillis()
)

data class ChecklistBackupItem(
    val id: Long,
    val title: String,
    val category: String = "خرید روزانه",
    val colorHex: String = "#10B981",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

data class ChecklistItemBackupItem(
    val id: Long,
    val checklistId: Long,
    val text: String,
    val estimatedAmount: Long = 0L,
    val isChecked: Boolean = false,
    val isExpenseConverted: Boolean = false,
    val transactionId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)
