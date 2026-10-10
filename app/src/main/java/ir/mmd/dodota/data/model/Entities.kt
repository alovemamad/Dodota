package ir.mmd.dodota.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
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

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = false,
    val parentCategoryId: Long? = null
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val targetAccountId: Long? = null,
    val externalDestinationInfo: String = "",
    val categoryId: Long? = null,
    val subcategoryId: Long? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val timeString: String = "",
    val title: String = "",
    val note: String = "",
    val receiptUri: String? = null,
    val tags: String = "",
    val isPinned: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringId: Long? = null,
    val installmentId: Long? = null,
    val debtPaymentId: Long? = null,
    val isDeleted: Boolean = false,
    val deletedAtMillis: Long? = null
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: DebtType,
    val personOrCompanyName: String,
    val totalAmount: Long,
    val paidAmount: Long = 0L,
    val startDateMillis: Long = System.currentTimeMillis(),
    val dueDateMillis: Long? = null,
    val notes: String = "",
    val accountId: Long? = null,
    val status: DebtStatus = DebtStatus.ACTIVE,
    val isCheck: Boolean = false,
    val checkNumber: String = "",
    val isDeleted: Boolean = false
)

@Entity(tableName = "debt_payments")
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amount: Long,
    val dateMillis: Long = System.currentTimeMillis(),
    val accountId: Long? = null,
    val notes: String = ""
)

@Entity(tableName = "installment_plans")
data class InstallmentPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val creditorOrCompany: String = "",
    val totalLoanAmount: Long = 0L,
    val totalAmount: Long,
    val numberOfInstallments: Int,
    val installmentAmount: Long,
    val paidInstallments: Int = 0,
    val startDateMillis: Long = System.currentTimeMillis(),
    val dueDayOfMonth: Int = 1,
    val notes: String = "",
    val autoCreateExpense: Boolean = true,
    val accountId: Long? = null,
    val isCompleted: Boolean = false
)

@Entity(tableName = "installments")
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val installmentIndex: Int,
    val amount: Long,
    val dueDateMillis: Long,
    val paidDateMillis: Long? = null,
    val isPaid: Boolean = false,
    val transactionId: Long? = null
)

@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: TransactionType,
    val amount: Long,
    val categoryId: Long? = null,
    val accountId: Long,
    val interval: RecurrenceInterval,
    val startDateMillis: Long = System.currentTimeMillis(),
    val nextDueDateMillis: Long = System.currentTimeMillis(),
    val lastProcessedDateMillis: Long? = null,
    val isActive: Boolean = true,
    val note: String = ""
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val targetType: ReminderTargetType,
    val targetId: Long? = null,
    val dueDateTimeMillis: Long,
    val reminderOffsetDays: Int = 1,
    val isCompleted: Boolean = false,
    val isEnabled: Boolean = true
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val monthlyAmount: Long,
    val monthKey: String,
    val warningThresholdPercent: Int = 80
)

@Entity(tableName = "financial_goals")
data class FinancialGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AssetType,
    val estimatedValue: Long,
    val notes: String = "",
    val lastUpdateMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "checklists")
data class ChecklistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "خرید روزانه",
    val colorHex: String = "#10B981",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isArchived: Boolean = false
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["checklistId"])]
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checklistId: Long,
    val text: String,
    val estimatedAmount: Long = 0L,
    val isChecked: Boolean = false,
    val isExpenseConverted: Boolean = false,
    val transactionId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)
