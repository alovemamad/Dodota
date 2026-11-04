package ir.mmd.dodota.data.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
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
    val checklistItems: List<ChecklistItemBackupItem> = emptyList(),
    val chandRoozRecords: List<ChandRoozBackupItem> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class AccountBackupItem(
    val id: Long = 0,
    val name: String = "",
    val type: String = "BANK_CARD",
    val balance: Long = 0L,
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

@Keep
@JsonClass(generateAdapter = true)
data class CategoryBackupItem(
    val id: Long = 0,
    val name: String = "",
    val type: String = "EXPENSE",
    val iconName: String = "category",
    val colorHex: String = "#0EA5E9",
    val isDefault: Boolean = false,
    val parentCategoryId: Long? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class TransactionBackupItem(
    val id: Long = 0,
    val type: String = "EXPENSE",
    val amount: Long = 0L,
    val accountId: Long = 0L,
    val targetAccountId: Long? = null,
    val externalDestinationInfo: String = "",
    val categoryId: Long? = null,
    val subcategoryId: Long? = null,
    val dateMillis: Long = System.currentTimeMillis(),
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

@Keep
@JsonClass(generateAdapter = true)
data class DebtBackupItem(
    val id: Long = 0,
    val type: String = "DEBT_I_OWE",
    val personOrCompanyName: String = "",
    val totalAmount: Long = 0L,
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

@Keep
@JsonClass(generateAdapter = true)
data class DebtPaymentBackupItem(
    val id: Long = 0,
    val debtId: Long = 0L,
    val amount: Long = 0L,
    val dateMillis: Long = System.currentTimeMillis(),
    val accountId: Long? = null,
    val notes: String = ""
)

@Keep
@JsonClass(generateAdapter = true)
data class InstallmentPlanBackupItem(
    val id: Long = 0,
    val title: String = "",
    val creditorOrCompany: String = "",
    val totalLoanAmount: Long = 0L,
    val totalAmount: Long = 0L,
    val numberOfInstallments: Int = 1,
    val installmentAmount: Long = 0L,
    val paidInstallments: Int = 0,
    val startDateMillis: Long = System.currentTimeMillis(),
    val dueDayOfMonth: Int = 1,
    val notes: String = "",
    val autoCreateExpense: Boolean = true,
    val accountId: Long? = null,
    val isCompleted: Boolean = false
)

@Keep
@JsonClass(generateAdapter = true)
data class InstallmentBackupItem(
    val id: Long = 0,
    val planId: Long = 0L,
    val installmentIndex: Int = 1,
    val amount: Long = 0L,
    val dueDateMillis: Long = System.currentTimeMillis(),
    val paidDateMillis: Long? = null,
    val isPaid: Boolean = false,
    val transactionId: Long? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class RecurringTransactionBackupItem(
    val id: Long = 0,
    val title: String = "",
    val type: String = "EXPENSE",
    val amount: Long = 0L,
    val categoryId: Long? = null,
    val accountId: Long = 0L,
    val interval: String = "MONTHLY",
    val startDateMillis: Long = System.currentTimeMillis(),
    val nextDueDateMillis: Long = System.currentTimeMillis(),
    val lastProcessedDateMillis: Long? = null,
    val isActive: Boolean = true,
    val note: String = ""
)

@Keep
@JsonClass(generateAdapter = true)
data class ReminderBackupItem(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val targetType: String = "CUSTOM",
    val targetId: Long? = null,
    val dueDateTimeMillis: Long = System.currentTimeMillis(),
    val reminderOffsetDays: Int = 0,
    val isCompleted: Boolean = false,
    val isEnabled: Boolean = true
)

@Keep
@JsonClass(generateAdapter = true)
data class BudgetBackupItem(
    val id: Long = 0,
    val categoryId: Long = 0L,
    val monthlyAmount: Long = 0L,
    val monthKey: String = "",
    val warningThresholdPercent: Int = 80
)

@Keep
@JsonClass(generateAdapter = true)
data class GoalBackupItem(
    val id: Long = 0,
    val title: String = "",
    val targetAmount: Long = 0L,
    val currentAmount: Long = 0L,
    val deadlineMillis: Long? = null,
    val note: String = "",
    val iconName: String = "savings",
    val colorHex: String = "#10B981",
    val accountId: Long? = null,
    val isCompleted: Boolean = false
)

@Keep
@JsonClass(generateAdapter = true)
data class AssetBackupItem(
    val id: Long = 0,
    val name: String = "",
    val type: String = "OTHER",
    val estimatedValue: Long = 0L,
    val notes: String = "",
    val lastUpdateMillis: Long = System.currentTimeMillis()
)

@Keep
@JsonClass(generateAdapter = true)
data class ChecklistBackupItem(
    val id: Long = 0,
    val title: String = "",
    val category: String = "خرید روزانه",
    val colorHex: String = "#10B981",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

@Keep
@JsonClass(generateAdapter = true)
data class ChecklistItemBackupItem(
    val id: Long = 0,
    val checklistId: Long = 0L,
    val text: String = "",
    val estimatedAmount: Long = 0L,
    val isChecked: Boolean = false,
    val isExpenseConverted: Boolean = false,
    val transactionId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Keep
@JsonClass(generateAdapter = true)
data class ChandRoozBackupItem(
    val id: Long = 0,
    val title: String = "",
    val category: String = "OTHER",
    val customCategory: String = "",
    val isRange: Boolean = false,
    val startDateMillis: Long = System.currentTimeMillis(),
    val endDateMillis: Long = System.currentTimeMillis(),
    val daysCount: Int = 1,
    val location: String = "",
    val note: String = "",
    val colorHex: String = "#F97316",
    val createdAtMillis: Long = System.currentTimeMillis()
)
