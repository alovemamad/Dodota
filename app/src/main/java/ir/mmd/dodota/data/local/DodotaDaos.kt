package ir.mmd.dodota.data.local

import androidx.room.*
import ir.mmd.dodota.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY id ASC")
    fun getAllActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun getAllAccountsSync(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun getAccountByIdFlow(id: Long): Flow<AccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET balance = balance + :amountDiff WHERE id = :id")
    suspend fun adjustBalance(id: Long, amountDiff: Long)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = 0 WHERE id = :id")
    suspend fun deactivateAccount(id: Long)

    @Query("DELETE FROM accounts")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY isPinned DESC, dateMillis DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY dateMillis DESC")
    suspend fun getAllTransactionsSync(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE isDeleted = 1 ORDER BY deletedAtMillis DESC")
    fun getTrashTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND (accountId = :accId OR targetAccountId = :accId) ORDER BY dateMillis DESC")
    fun getTransactionsForAccount(accId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND dateMillis BETWEEN :startMillis AND :endMillis ORDER BY dateMillis DESC")
    fun getTransactionsInRange(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 AND dateMillis BETWEEN :startMillis AND :endMillis ORDER BY dateMillis DESC")
    suspend fun getTransactionsInRangeSync(startMillis: Long, endMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY dateMillis DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET isDeleted = 1, deletedAtMillis = :deletedAt WHERE id = :id")
    suspend fun moveToTrash(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET isDeleted = 0, deletedAtMillis = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM transactions WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("DELETE FROM transactions")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isDefault DESC, id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY isDefault DESC, id ASC")
    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesSync(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE parentCategoryId = :parentId")
    fun getSubcategories(parentId: Long): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE isDeleted = 0 ORDER BY dueDateMillis ASC, id DESC")
    fun getAllDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE isDeleted = 0 AND type = :type ORDER BY status ASC, dueDateMillis ASC")
    fun getDebtsByType(type: DebtType): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts")
    suspend fun getAllDebtsSync(): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebtById(id: Long): DebtEntity?

    @Query("SELECT * FROM debts WHERE id = :id")
    fun getDebtByIdFlow(id: Long): Flow<DebtEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity): Long

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Query("UPDATE debts SET paidAmount = paidAmount + :amount WHERE id = :debtId")
    suspend fun addPaidAmount(debtId: Long, amount: Long)

    @Query("UPDATE debts SET status = :status WHERE id = :debtId")
    suspend fun updateDebtStatus(debtId: Long, status: DebtStatus)

    @Delete
    suspend fun deleteDebt(debt: DebtEntity)

    // Debt Payments
    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY dateMillis DESC")
    fun getPaymentsForDebt(debtId: Long): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments")
    suspend fun getAllDebtPaymentsSync(): List<DebtPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(payment: DebtPaymentEntity): Long

    @Delete
    suspend fun deleteDebtPayment(payment: DebtPaymentEntity)

    @Query("DELETE FROM debts")
    suspend fun clearAll()

    @Query("DELETE FROM debt_payments")
    suspend fun clearAllPayments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDebts(debts: List<DebtEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPayments(payments: List<DebtPaymentEntity>)
}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installment_plans ORDER BY isCompleted ASC, id DESC")
    fun getAllPlans(): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plans")
    suspend fun getAllPlansSync(): List<InstallmentPlanEntity>

    @Query("SELECT * FROM installment_plans WHERE id = :id")
    suspend fun getPlanById(id: Long): InstallmentPlanEntity?

    @Query("SELECT * FROM installment_plans WHERE id = :id")
    fun getPlanByIdFlow(id: Long): Flow<InstallmentPlanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: InstallmentPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: InstallmentPlanEntity)

    @Delete
    suspend fun deletePlan(plan: InstallmentPlanEntity)

    // Installments
    @Query("SELECT * FROM installments ORDER BY planId ASC, installmentIndex ASC")
    fun getAllInstallments(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE planId = :planId ORDER BY installmentIndex ASC")
    fun getInstallmentsForPlan(planId: Long): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE isPaid = 0 ORDER BY dueDateMillis ASC")
    fun getUpcomingInstallments(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments")
    suspend fun getAllInstallmentsSync(): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE id = :id")
    suspend fun getInstallmentById(id: Long): InstallmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallments(installments: List<InstallmentEntity>)

    @Update
    suspend fun updateInstallment(installment: InstallmentEntity)

    @Query("SELECT * FROM installments WHERE planId = :planId ORDER BY installmentIndex ASC")
    suspend fun getInstallmentsForPlanSync(planId: Long): List<InstallmentEntity>

    @Query("DELETE FROM installments WHERE planId = :planId")
    suspend fun deleteInstallmentsForPlan(planId: Long)

    @Query("DELETE FROM installment_plans")
    suspend fun clearAllPlans()

    @Query("DELETE FROM installments")
    suspend fun clearAllInstallments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPlans(plans: List<InstallmentPlanEntity>)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_transactions ORDER BY isActive DESC, id DESC")
    fun getAllRecurring(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 ORDER BY nextDueDateMillis ASC")
    fun getActiveRecurring(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions")
    suspend fun getAllRecurringSync(): List<RecurringTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurring(recurring: RecurringTransactionEntity): Long

    @Update
    suspend fun updateRecurring(recurring: RecurringTransactionEntity)

    @Delete
    suspend fun deleteRecurring(recurring: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RecurringTransactionEntity>)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isEnabled = 1 AND isCompleted = 0 ORDER BY dueDateTimeMillis ASC")
    fun getActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, dueDateTimeMillis ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersSync(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE targetType = :targetType AND targetId = :targetId")
    suspend fun deleteRemindersForTarget(targetType: ReminderTargetType, targetId: Long)

    @Query("DELETE FROM reminders")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ReminderEntity>)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    fun getBudgetsForMonth(monthKey: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    suspend fun getBudgetsForMonthSync(monthKey: String): List<BudgetEntity>

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsSync(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND monthKey = :monthKey LIMIT 1")
    suspend fun getBudget(categoryId: Long, monthKey: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<BudgetEntity>)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM financial_goals ORDER BY isCompleted ASC, id DESC")
    fun getAllGoals(): Flow<List<FinancialGoalEntity>>

    @Query("SELECT * FROM financial_goals")
    suspend fun getAllGoalsSync(): List<FinancialGoalEntity>

    @Query("SELECT * FROM financial_goals WHERE id = :id")
    suspend fun getGoalById(id: Long): FinancialGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: FinancialGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: FinancialGoalEntity)

    @Query("UPDATE financial_goals SET currentAmount = currentAmount + :diff WHERE id = :id")
    suspend fun adjustGoalAmount(id: Long, diff: Long)

    @Delete
    suspend fun deleteGoal(goal: FinancialGoalEntity)

    @Query("DELETE FROM financial_goals")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<FinancialGoalEntity>)
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY estimatedValue DESC, id DESC")
    fun getAllAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets")
    suspend fun getAllAssetsSync(): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getAssetById(id: Long): AssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity): Long

    @Update
    suspend fun updateAsset(asset: AssetEntity)

    @Delete
    suspend fun deleteAsset(asset: AssetEntity)

    @Query("DELETE FROM assets")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<AssetEntity>)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklists WHERE isArchived = 0 ORDER BY isPinned DESC, createdAtMillis DESC")
    fun getAllActiveChecklists(): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists ORDER BY createdAtMillis DESC")
    suspend fun getAllChecklistsSync(): List<ChecklistEntity>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getChecklistById(id: Long): ChecklistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: ChecklistEntity): Long

    @Update
    suspend fun updateChecklist(checklist: ChecklistEntity)

    @Delete
    suspend fun deleteChecklist(checklist: ChecklistEntity)

    @Query("DELETE FROM checklists")
    suspend fun clearAllChecklists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChecklists(list: List<ChecklistEntity>)

    // Checklist Items
    @Query("SELECT * FROM checklist_items WHERE checklistId = :checklistId ORDER BY isChecked ASC, id ASC")
    fun getItemsForChecklist(checklistId: Long): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items ORDER BY id ASC")
    fun getAllChecklistItems(): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items")
    suspend fun getAllChecklistItemsSync(): List<ChecklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistItem(item: ChecklistItemEntity): Long

    @Update
    suspend fun updateChecklistItem(item: ChecklistItemEntity)

    @Delete
    suspend fun deleteChecklistItem(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE checklistId = :checklistId AND isChecked = 1")
    suspend fun clearCheckedItems(checklistId: Long)

    @Query("UPDATE checklist_items SET isChecked = :isChecked WHERE id = :itemId")
    suspend fun toggleItemChecked(itemId: Long, isChecked: Boolean)

    @Query("DELETE FROM checklist_items")
    suspend fun clearAllChecklistItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChecklistItems(list: List<ChecklistItemEntity>)
}

