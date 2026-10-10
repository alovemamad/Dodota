package ir.mmd.dodota.data.repository

import androidx.room.withTransaction
import ir.mmd.dodota.data.local.DodotaDatabase
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.util.PersianDateHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class DodotaRepository(private val db: DodotaDatabase) {

    private val accountDao = db.accountDao()
    private val transactionDao = db.transactionDao()
    private val categoryDao = db.categoryDao()
    private val debtDao = db.debtDao()
    private val installmentDao = db.installmentDao()
    private val recurringDao = db.recurringDao()
    private val reminderDao = db.reminderDao()
    private val budgetDao = db.budgetDao()
    private val goalDao = db.goalDao()
    private val assetDao = db.assetDao()

    // ----------------------------------------------------
    // ACCOUNTS
    // ----------------------------------------------------
    val allActiveAccounts: Flow<List<AccountEntity>> = accountDao.getAllActiveAccounts()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    fun getAccountFlow(id: Long): Flow<AccountEntity?> = accountDao.getAccountByIdFlow(id)

    suspend fun getAccount(id: Long): AccountEntity? = accountDao.getAccountById(id)

    suspend fun insertAccount(account: AccountEntity): Long = accountDao.insertAccount(account)

    suspend fun updateAccount(account: AccountEntity) = accountDao.updateAccount(account)

    suspend fun deleteAccountSafely(account: AccountEntity) {
        db.withTransaction {
            accountDao.deactivateAccount(account.id)
        }
    }

    suspend fun deleteAccount(account: AccountEntity) {
        accountDao.deleteAccount(account)
    }

    // ----------------------------------------------------
    // TRANSACTIONS & BALANCE CONSISTENCY
    // ----------------------------------------------------
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val trashTransactions: Flow<List<TransactionEntity>> = transactionDao.getTrashTransactions()
    val recentTransactions: Flow<List<TransactionEntity>> = transactionDao.getRecentTransactions(10)

    suspend fun getTransaction(id: Long): TransactionEntity? = transactionDao.getTransactionById(id)

    fun getTransactionsForAccount(accId: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsForAccount(accId)

    fun getTransactionsInRange(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsInRange(startMillis, endMillis)

    suspend fun addTransaction(transaction: TransactionEntity): Long {
        return db.withTransaction {
            val id = transactionDao.insertTransaction(transaction)
            // Adjust balance transactionally
            when (transaction.type) {
                TransactionType.INCOME -> {
                    accountDao.adjustBalance(transaction.accountId, transaction.amount)
                }
                TransactionType.EXPENSE -> {
                    accountDao.adjustBalance(transaction.accountId, -transaction.amount)
                }
                TransactionType.TRANSFER -> {
                    accountDao.adjustBalance(transaction.accountId, -transaction.amount)
                    transaction.targetAccountId?.let { targetId ->
                        accountDao.adjustBalance(targetId, transaction.amount)
                    }
                }
            }
            id
        }
    }

    suspend fun updateTransaction(oldTx: TransactionEntity, newTx: TransactionEntity) {
        db.withTransaction {
            // Revert old transaction balance impact
            when (oldTx.type) {
                TransactionType.INCOME -> accountDao.adjustBalance(oldTx.accountId, -oldTx.amount)
                TransactionType.EXPENSE -> accountDao.adjustBalance(oldTx.accountId, oldTx.amount)
                TransactionType.TRANSFER -> {
                    accountDao.adjustBalance(oldTx.accountId, oldTx.amount)
                    oldTx.targetAccountId?.let { targetId ->
                        accountDao.adjustBalance(targetId, -oldTx.amount)
                    }
                }
            }
            // Apply new transaction balance impact
            when (newTx.type) {
                TransactionType.INCOME -> accountDao.adjustBalance(newTx.accountId, newTx.amount)
                TransactionType.EXPENSE -> accountDao.adjustBalance(newTx.accountId, -newTx.amount)
                TransactionType.TRANSFER -> {
                    accountDao.adjustBalance(newTx.accountId, -newTx.amount)
                    newTx.targetAccountId?.let { targetId ->
                        accountDao.adjustBalance(targetId, newTx.amount)
                    }
                }
            }
            transactionDao.updateTransaction(newTx)
        }
    }

    suspend fun moveToTrash(tx: TransactionEntity) {
        db.withTransaction {
            // When moving to trash, revert the balance effect
            when (tx.type) {
                TransactionType.INCOME -> accountDao.adjustBalance(tx.accountId, -tx.amount)
                TransactionType.EXPENSE -> accountDao.adjustBalance(tx.accountId, tx.amount)
                TransactionType.TRANSFER -> {
                    accountDao.adjustBalance(tx.accountId, tx.amount)
                    tx.targetAccountId?.let { targetId ->
                        accountDao.adjustBalance(targetId, -tx.amount)
                    }
                }
            }
            transactionDao.moveToTrash(tx.id)
        }
    }

    suspend fun restoreFromTrash(tx: TransactionEntity) {
        db.withTransaction {
            // Re-apply balance impact
            when (tx.type) {
                TransactionType.INCOME -> accountDao.adjustBalance(tx.accountId, tx.amount)
                TransactionType.EXPENSE -> accountDao.adjustBalance(tx.accountId, -tx.amount)
                TransactionType.TRANSFER -> {
                    accountDao.adjustBalance(tx.accountId, -tx.amount)
                    tx.targetAccountId?.let { targetId ->
                        accountDao.adjustBalance(targetId, tx.amount)
                    }
                }
            }
            transactionDao.restoreFromTrash(tx.id)
        }
    }

    suspend fun deletePermanently(txId: Long) {
        transactionDao.deletePermanently(txId)
    }

    suspend fun emptyTrash() {
        transactionDao.emptyTrash()
    }

    // ----------------------------------------------------
    // CATEGORIES
    // ----------------------------------------------------
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByType(type)

    suspend fun insertCategory(category: CategoryEntity): Long = categoryDao.insertCategory(category)

    suspend fun updateCategory(category: CategoryEntity) = categoryDao.updateCategory(category)

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.deleteCategory(category)

    // ----------------------------------------------------
    // DEBTS & RECEIVABLES
    // ----------------------------------------------------
    val allDebts: Flow<List<DebtEntity>> = debtDao.getAllDebts()

    fun getDebtsByType(type: DebtType): Flow<List<DebtEntity>> = debtDao.getDebtsByType(type)

    fun getDebtFlow(id: Long): Flow<DebtEntity?> = debtDao.getDebtByIdFlow(id)

    fun getDebtPayments(debtId: Long): Flow<List<DebtPaymentEntity>> = debtDao.getPaymentsForDebt(debtId)

    suspend fun addDebt(debt: DebtEntity): Long = debtDao.insertDebt(debt)

    suspend fun updateDebt(debt: DebtEntity) = debtDao.updateDebt(debt)

    suspend fun recordDebtPayment(
        debt: DebtEntity,
        amount: Long,
        accountId: Long?,
        notes: String
    ) {
        db.withTransaction {
            val payment = DebtPaymentEntity(
                debtId = debt.id,
                amount = amount,
                dateMillis = System.currentTimeMillis(),
                accountId = accountId,
                notes = notes
            )
            debtDao.insertDebtPayment(payment)
            debtDao.addPaidAmount(debt.id, amount)

            val updatedDebt = debtDao.getDebtById(debt.id)
            if (updatedDebt != null && updatedDebt.paidAmount >= updatedDebt.totalAmount) {
                debtDao.updateDebtStatus(debt.id, DebtStatus.SETTLED)
            }

            // Adjust selected account balance if provided
            if (accountId != null && accountId > 0) {
                if (debt.type == DebtType.DEBT_I_OWE) {
                    // Paying my debt reduces my account balance
                    accountDao.adjustBalance(accountId, -amount)
                } else {
                    // Someone paying their debt to me increases my account balance
                    accountDao.adjustBalance(accountId, amount)
                }
            }
        }
    }

    suspend fun deleteDebt(debt: DebtEntity) {
        db.withTransaction {
            reminderDao.deleteRemindersForTarget(ReminderTargetType.DEBT, debt.id)
            debtDao.deleteDebt(debt)
        }
    }

    suspend fun deleteDebtPayment(debt: DebtEntity, payment: DebtPaymentEntity) {
        db.withTransaction {
            debtDao.deleteDebtPayment(payment)
            debtDao.addPaidAmount(debt.id, -payment.amount)
            val updatedDebt = debtDao.getDebtById(debt.id)
            if (updatedDebt != null && updatedDebt.paidAmount < updatedDebt.totalAmount) {
                debtDao.updateDebtStatus(debt.id, DebtStatus.ACTIVE)
            }
            if (payment.accountId != null && payment.accountId > 0) {
                if (debt.type == DebtType.DEBT_I_OWE) {
                    accountDao.adjustBalance(payment.accountId, payment.amount)
                } else {
                    accountDao.adjustBalance(payment.accountId, -payment.amount)
                }
            }
        }
    }

    // ----------------------------------------------------
    // INSTALLMENTS
    // ----------------------------------------------------
    val allInstallmentPlans: Flow<List<InstallmentPlanEntity>> = installmentDao.getAllPlans()
    val allInstallments: Flow<List<InstallmentEntity>> = installmentDao.getAllInstallments()
    val upcomingInstallments: Flow<List<InstallmentEntity>> = installmentDao.getUpcomingInstallments()

    fun getInstallmentPlanFlow(id: Long): Flow<InstallmentPlanEntity?> = installmentDao.getPlanByIdFlow(id)

    fun getInstallmentsForPlan(planId: Long): Flow<List<InstallmentEntity>> =
        installmentDao.getInstallmentsForPlan(planId)

    suspend fun updateInstallmentPlan(plan: InstallmentPlanEntity) {
        db.withTransaction {
            val existingInstallments = installmentDao.getInstallmentsForPlanSync(plan.id)
            val paidMap = existingInstallments.filter { it.isPaid }.associateBy { it.installmentIndex }
            val targetPaidCount = plan.paidInstallments.coerceIn(0, plan.numberOfInstallments)

            installmentDao.deleteInstallmentsForPlan(plan.id)

            val basePersianDate = PersianDateHelper.fromMillis(plan.startDateMillis)
            val newInstallments = (1..plan.numberOfInstallments).map { index ->
                var m = basePersianDate.month + (index - 1)
                var y = basePersianDate.year
                while (m > 12) {
                    m -= 12
                    y += 1
                }
                val maxDays = PersianDateHelper.getDaysInPersianMonth(y, m)
                val day = plan.dueDayOfMonth.coerceIn(1, maxDays)
                val dueMillis = PersianDateHelper.toMillis(y, m, day, 10, 0)
                val prevPaid = paidMap[index]
                val isPaidNow = prevPaid != null || index <= targetPaidCount

                InstallmentEntity(
                    planId = plan.id,
                    installmentIndex = index,
                    amount = plan.installmentAmount,
                    dueDateMillis = dueMillis,
                    isPaid = isPaidNow,
                    paidDateMillis = prevPaid?.paidDateMillis ?: if (isPaidNow) dueMillis else null,
                    transactionId = prevPaid?.transactionId
                )
            }
            val actualPaidCount = newInstallments.count { it.isPaid }
            val finalPlan = plan.copy(
                paidInstallments = actualPaidCount,
                isCompleted = actualPaidCount >= plan.numberOfInstallments && plan.numberOfInstallments > 0
            )
            installmentDao.updatePlan(finalPlan)
            installmentDao.insertInstallments(newInstallments)
        }
    }

    suspend fun updateInstallmentItem(installment: InstallmentEntity) {
        installmentDao.updateInstallment(installment)
    }

    suspend fun unpayInstallment(plan: InstallmentPlanEntity, installment: InstallmentEntity) {
        db.withTransaction {
            if (installment.transactionId != null) {
                val tx = transactionDao.getTransactionById(installment.transactionId)
                if (tx != null) {
                    transactionDao.deletePermanently(tx.id)
                    if (tx.accountId > 0) {
                        accountDao.adjustBalance(tx.accountId, tx.amount)
                    }
                }
            }
            val updatedInstallment = installment.copy(
                isPaid = false,
                paidDateMillis = null,
                transactionId = null
            )
            installmentDao.updateInstallment(updatedInstallment)
            val updatedPlan = plan.copy(
                paidInstallments = (plan.paidInstallments - 1).coerceAtLeast(0),
                isCompleted = false
            )
            installmentDao.updatePlan(updatedPlan)
        }
    }

    suspend fun createInstallmentPlan(
        plan: InstallmentPlanEntity,
        startDateMillis: Long,
        initialPaidCount: Int = 0
    ): Long {
        return db.withTransaction {
            val safePaid = initialPaidCount.coerceIn(0, plan.numberOfInstallments)
            val planToInsert = plan.copy(
                paidInstallments = safePaid,
                isCompleted = safePaid >= plan.numberOfInstallments && plan.numberOfInstallments > 0,
                startDateMillis = startDateMillis
            )
            val planId = installmentDao.insertPlan(planToInsert)
            val basePersianDate = PersianDateHelper.fromMillis(startDateMillis)

            val installments = (1..plan.numberOfInstallments).map { index ->
                var m = basePersianDate.month + (index - 1)
                var y = basePersianDate.year
                while (m > 12) {
                    m -= 12
                    y += 1
                }
                val maxDays = PersianDateHelper.getDaysInPersianMonth(y, m)
                val day = plan.dueDayOfMonth.coerceIn(1, maxDays)
                val dueMillis = PersianDateHelper.toMillis(y, m, day, 10, 0)
                val isAlreadyPaid = index <= safePaid

                InstallmentEntity(
                    planId = planId,
                    installmentIndex = index,
                    amount = plan.installmentAmount,
                    dueDateMillis = dueMillis,
                    isPaid = isAlreadyPaid,
                    paidDateMillis = if (isAlreadyPaid) dueMillis else null
                )
            }
            installmentDao.insertInstallments(installments)
            planId
        }
    }

    suspend fun payInstallment(
        plan: InstallmentPlanEntity,
        installment: InstallmentEntity,
        accountId: Long?
    ) {
        db.withTransaction {
            var txId: Long? = null
            if (plan.autoCreateExpense && accountId != null && accountId > 0) {
                val tx = TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = installment.amount,
                    accountId = accountId,
                    title = "قسط ${installment.installmentIndex} از ${plan.numberOfInstallments} (${plan.title})",
                    dateMillis = System.currentTimeMillis(),
                    installmentId = installment.id
                )
                txId = transactionDao.insertTransaction(tx)
                accountDao.adjustBalance(accountId, -installment.amount)
            }

            val updatedInstallment = installment.copy(
                isPaid = true,
                paidDateMillis = System.currentTimeMillis(),
                transactionId = txId
            )
            installmentDao.updateInstallment(updatedInstallment)

            val updatedPlan = plan.copy(
                paidInstallments = plan.paidInstallments + 1,
                isCompleted = (plan.paidInstallments + 1) >= plan.numberOfInstallments
            )
            installmentDao.updatePlan(updatedPlan)
        }
    }

    suspend fun deleteInstallmentPlan(plan: InstallmentPlanEntity) {
        db.withTransaction {
            reminderDao.deleteRemindersForTarget(ReminderTargetType.INSTALLMENT, plan.id)
            installmentDao.deletePlan(plan)
        }
    }

    // ----------------------------------------------------
    // RECURRING TRANSACTIONS
    // ----------------------------------------------------
    val allRecurring: Flow<List<RecurringTransactionEntity>> = recurringDao.getAllRecurring()
    val allRecurringTransactions: Flow<List<RecurringTransactionEntity>> get() = allRecurring
    val activeRecurring: Flow<List<RecurringTransactionEntity>> = recurringDao.getActiveRecurring()

    suspend fun insertRecurring(item: RecurringTransactionEntity): Long = recurringDao.insertRecurring(item)
    suspend fun updateRecurring(item: RecurringTransactionEntity) = recurringDao.updateRecurring(item)
    suspend fun deleteRecurring(item: RecurringTransactionEntity) {
        db.withTransaction {
            reminderDao.deleteRemindersForTarget(ReminderTargetType.RECURRING, item.id)
            recurringDao.deleteRecurring(item)
        }
    }

    // ----------------------------------------------------
    // REMINDERS
    // ----------------------------------------------------
    val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()
    val activeReminders: Flow<List<ReminderEntity>> = reminderDao.getActiveReminders()

    suspend fun insertReminder(reminder: ReminderEntity): Long = reminderDao.insertReminder(reminder)
    suspend fun updateReminder(reminder: ReminderEntity) = reminderDao.updateReminder(reminder)
    suspend fun deleteReminder(reminder: ReminderEntity) = reminderDao.deleteReminder(reminder)
    suspend fun deleteRemindersForTarget(targetType: ReminderTargetType, targetId: Long) =
        reminderDao.deleteRemindersForTarget(targetType, targetId)

    suspend fun removeTargetReminders(targetType: ReminderTargetType, targetId: Long) =
        reminderDao.deleteRemindersForTarget(targetType, targetId)

    suspend fun syncTargetReminder(
        targetType: ReminderTargetType,
        targetId: Long,
        title: String,
        description: String,
        dueDateTimeMillis: Long,
        offsetDays: Int = 0,
        enabled: Boolean = true
    ): Long? {
        return db.withTransaction {
            reminderDao.deleteRemindersForTarget(targetType, targetId)
            if (enabled) {
                reminderDao.insertReminder(
                    ReminderEntity(
                        title = title,
                        description = description,
                        targetType = targetType,
                        targetId = targetId,
                        dueDateTimeMillis = dueDateTimeMillis,
                        reminderOffsetDays = offsetDays,
                        isEnabled = true,
                        isCompleted = false
                    )
                )
            } else {
                null
            }
        }
    }

    // ----------------------------------------------------
    // BUDGETS
    // ----------------------------------------------------
    val allBudgets: Flow<List<BudgetEntity>> = budgetDao.getAllBudgets()

    fun getBudgetsForMonth(monthKey: String): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForMonth(monthKey)

    suspend fun getBudgetForCategorySync(categoryId: Long, monthKey: String): BudgetEntity? =
        budgetDao.getBudget(categoryId, monthKey)

    suspend fun getTransactionsInRangeSync(startMillis: Long, endMillis: Long): List<TransactionEntity> =
        transactionDao.getTransactionsInRangeSync(startMillis, endMillis)

    suspend fun saveBudget(budget: BudgetEntity): Long = budgetDao.insertBudget(budget)
    suspend fun deleteBudget(budget: BudgetEntity) = budgetDao.deleteBudget(budget)

    // ----------------------------------------------------
    // FINANCIAL GOALS
    // ----------------------------------------------------
    val allGoals: Flow<List<FinancialGoalEntity>> = goalDao.getAllGoals()

    suspend fun insertGoal(goal: FinancialGoalEntity): Long = goalDao.insertGoal(goal)
    suspend fun updateGoal(goal: FinancialGoalEntity) = goalDao.updateGoal(goal)
    suspend fun deleteGoal(goal: FinancialGoalEntity) = goalDao.deleteGoal(goal)

    suspend fun depositToGoal(goal: FinancialGoalEntity, amount: Long, accountId: Long?) {
        db.withTransaction {
            if (accountId != null && accountId > 0) {
                accountDao.adjustBalance(accountId, -amount)
                val tx = TransactionEntity(
                    type = TransactionType.EXPENSE,
                    amount = amount,
                    accountId = accountId,
                    title = "واریز به هدف پس‌انداز: ${goal.title}",
                    dateMillis = System.currentTimeMillis()
                )
                transactionDao.insertTransaction(tx)
            }
            goalDao.adjustGoalAmount(goal.id, amount)
            val updated = goalDao.getGoalById(goal.id)
            if (updated != null && updated.currentAmount >= updated.targetAmount) {
                goalDao.updateGoal(updated.copy(isCompleted = true))
            }
        }
    }

    suspend fun withdrawFromGoal(goal: FinancialGoalEntity, amount: Long, accountId: Long?) {
        db.withTransaction {
            if (accountId != null && accountId > 0) {
                accountDao.adjustBalance(accountId, amount)
                val tx = TransactionEntity(
                    type = TransactionType.INCOME,
                    amount = amount,
                    accountId = accountId,
                    title = "برداشت از هدف پس‌انداز: ${goal.title}",
                    dateMillis = System.currentTimeMillis()
                )
                transactionDao.insertTransaction(tx)
            }
            goalDao.adjustGoalAmount(goal.id, -amount)
            val updated = goalDao.getGoalById(goal.id)
            if (updated != null) {
                val safeCurrent = updated.currentAmount.coerceAtLeast(0L)
                val isStillCompleted = safeCurrent >= updated.targetAmount && updated.targetAmount > 0L
                goalDao.updateGoal(updated.copy(currentAmount = safeCurrent, isCompleted = isStillCompleted))
            }
        }
    }

    // ----------------------------------------------------
    // ASSETS & NET WORTH
    // ----------------------------------------------------
    val allAssets: Flow<List<AssetEntity>> = assetDao.getAllAssets()

    suspend fun insertAsset(asset: AssetEntity): Long = assetDao.insertAsset(asset)
    suspend fun updateAsset(asset: AssetEntity) = assetDao.updateAsset(asset)
    suspend fun deleteAsset(asset: AssetEntity) = assetDao.deleteAsset(asset)

    // Net Worth = Assets + Total Account Balances - Debts I Owe
    val netWorthFlow: Flow<Long> = combine(
        allActiveAccounts,
        allAssets,
        allDebts
    ) { accounts, assets, debts ->
        val accountsTotal = accounts.sumOf { it.balance }
        val assetsTotal = assets.sumOf { it.estimatedValue }
        val debtsTotal = debts.filter { it.type == DebtType.DEBT_I_OWE && it.status != DebtStatus.SETTLED }
            .sumOf { it.totalAmount - it.paidAmount }
        (accountsTotal + assetsTotal) - debtsTotal
    }
    val netWorth: Flow<Long> get() = netWorthFlow

    // ----------------------------------------------------
    // CHECKLISTS
    // ----------------------------------------------------
    private val checklistDao = db.checklistDao()

    val allActiveChecklists: Flow<List<ChecklistEntity>> = checklistDao.getAllActiveChecklists()
    val allChecklistItems: Flow<List<ChecklistItemEntity>> = checklistDao.getAllChecklistItems()

    fun getItemsForChecklist(checklistId: Long): Flow<List<ChecklistItemEntity>> =
        checklistDao.getItemsForChecklist(checklistId)

    suspend fun insertChecklist(checklist: ChecklistEntity): Long = checklistDao.insertChecklist(checklist)
    suspend fun updateChecklist(checklist: ChecklistEntity) = checklistDao.updateChecklist(checklist)
    suspend fun deleteChecklist(checklist: ChecklistEntity) = checklistDao.deleteChecklist(checklist)

    suspend fun insertChecklistItem(item: ChecklistItemEntity): Long = checklistDao.insertChecklistItem(item)
    suspend fun updateChecklistItem(item: ChecklistItemEntity) = checklistDao.updateChecklistItem(item)
    suspend fun deleteChecklistItem(item: ChecklistItemEntity) = checklistDao.deleteChecklistItem(item)
    suspend fun clearCheckedItems(checklistId: Long) = checklistDao.clearCheckedItems(checklistId)
    suspend fun toggleItemChecked(itemId: Long, isChecked: Boolean) = checklistDao.toggleItemChecked(itemId, isChecked)

    // Convert a checklist item into an actual expense transaction
    suspend fun convertChecklistItemToExpense(
        item: ChecklistItemEntity,
        accountId: Long,
        categoryId: Long?,
        checklistTitle: String
    ): Long {
        return db.withTransaction {
            val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val tx = TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = item.estimatedAmount,
                accountId = accountId,
                categoryId = categoryId,
                title = item.text,
                note = "از چک‌لیست: $checklistTitle",
                dateMillis = System.currentTimeMillis(),
                timeString = timeFormat.format(java.util.Date())
            )
            val txId = addTransaction(tx)
            checklistDao.updateChecklistItem(
                item.copy(
                    isChecked = true,
                    isExpenseConverted = true,
                    transactionId = txId
                )
            )
            txId
        }
    }

    // Reset database completely
    suspend fun clearDatabase() {
        db.withTransaction {
            accountDao.clearAll()
            transactionDao.clearAll()
            categoryDao.clearAll()
            debtDao.clearAll()
            debtDao.clearAllPayments()
            installmentDao.clearAllPlans()
            installmentDao.clearAllInstallments()
            recurringDao.clearAll()
            reminderDao.clearAll()
            budgetDao.clearAll()
            goalDao.clearAll()
            assetDao.clearAll()
            checklistDao.clearAllChecklists()
            checklistDao.clearAllChecklistItems()
        }
    }
}
