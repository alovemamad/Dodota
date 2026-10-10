package ir.mmd.dodota.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import ir.mmd.dodota.data.local.DodotaDatabase
import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.util.ReceiptImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BackupRepository(
    private val context: Context,
    private val db: DodotaDatabase
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(DodotaBackupPayload::class.java)

    private val backupDir: File
        get() {
            val docsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val primaryBackupDir = File(docsDir, "MyFinanceBackup")
            if (primaryBackupDir.exists() || primaryBackupDir.mkdirs()) {
                return primaryBackupDir
            }
            val appExtDir = context.getExternalFilesDir(null)
            val secondaryBackupDir = File(appExtDir, "MyFinanceBackup")
            if (secondaryBackupDir.exists() || secondaryBackupDir.mkdirs()) {
                return secondaryBackupDir
            }
            return File(context.filesDir, "MyFinanceBackup").apply { if (!exists()) mkdirs() }
        }

    fun getBackupFolder(): File = backupDir

    fun listBackupFiles(): List<File> {
        return try {
            backupDir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveBackupToFile(customFileName: String? = null): File = withContext(Dispatchers.IO) {
        val json = createManualBackupJson()
        val sdf = java.text.SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", java.util.Locale.US)
        val timeStamp = sdf.format(java.util.Date())
        val name = customFileName ?: "MyFinance_Backup_$timeStamp.json"
        val file = File(backupDir, name)
        file.writeText(json, Charsets.UTF_8)
        file
    }

    suspend fun deleteBackupFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) file.delete() else false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreFromFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("فایل یافت نشد."))
            }
            val content = file.readText(Charsets.UTF_8)
            restoreFromJson(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val autoWeeklyBackupFile: File
        get() = File(backupDir, "dodota_auto_nightly_backup.json")

    /**
     * Builds backup payload from current database, including Base64-encoded receipt images and bank card info.
     */
    suspend fun createBackupPayload(): DodotaBackupPayload = withContext(Dispatchers.IO) {
        val accounts = db.accountDao().getAllAccountsSync().map {
            AccountBackupItem(
                id = it.id,
                name = it.name,
                type = it.type.name,
                balance = it.balance,
                bankName = it.bankName,
                cardOrAccountDescription = it.cardOrAccountDescription,
                cardNumber = it.cardNumber,
                shebaNumber = it.shebaNumber,
                accountNumber = it.accountNumber,
                cvv2 = it.cvv2,
                expiryDate = it.expiryDate,
                cardHolderName = it.cardHolderName,
                colorHex = it.colorHex,
                iconName = it.iconName,
                createdAtMillis = it.createdAtMillis,
                isActive = it.isActive
            )
        }
        val categories = db.categoryDao().getAllCategoriesSync().map {
            CategoryBackupItem(it.id, it.name, it.type.name, it.iconName, it.colorHex, it.isDefault, it.parentCategoryId)
        }
        val transactions = db.transactionDao().getAllTransactionsSync().map {
            val base64Img = ReceiptImageHelper.encodeFileToBase64(context, it.receiptUri)
            TransactionBackupItem(
                id = it.id,
                type = it.type.name,
                amount = it.amount,
                accountId = it.accountId,
                targetAccountId = it.targetAccountId,
                externalDestinationInfo = it.externalDestinationInfo,
                categoryId = it.categoryId,
                subcategoryId = it.subcategoryId,
                dateMillis = it.dateMillis,
                timeString = it.timeString,
                title = it.title,
                note = it.note,
                receiptUri = it.receiptUri,
                receiptImageBase64 = base64Img,
                tags = it.tags,
                isPinned = it.isPinned,
                isRecurring = it.isRecurring,
                recurringId = it.recurringId,
                installmentId = it.installmentId,
                debtPaymentId = it.debtPaymentId,
                isDeleted = it.isDeleted,
                deletedAtMillis = it.deletedAtMillis
            )
        }
        val debts = db.debtDao().getAllDebtsSync().map {
            DebtBackupItem(it.id, it.type.name, it.personOrCompanyName, it.totalAmount, it.paidAmount, it.startDateMillis, it.dueDateMillis, it.notes, it.accountId, it.status.name, it.isDeleted, it.isCheck, it.checkNumber)
        }
        val debtPayments = db.debtDao().getAllDebtPaymentsSync().map {
            DebtPaymentBackupItem(it.id, it.debtId, it.amount, it.dateMillis, it.accountId, it.notes)
        }
        val installmentPlans = db.installmentDao().getAllPlansSync().map {
            InstallmentPlanBackupItem(
                id = it.id,
                title = it.title,
                creditorOrCompany = it.creditorOrCompany,
                totalLoanAmount = it.totalLoanAmount,
                totalAmount = it.totalAmount,
                numberOfInstallments = it.numberOfInstallments,
                installmentAmount = it.installmentAmount,
                paidInstallments = it.paidInstallments,
                startDateMillis = it.startDateMillis,
                dueDayOfMonth = it.dueDayOfMonth,
                notes = it.notes,
                autoCreateExpense = it.autoCreateExpense,
                accountId = it.accountId,
                isCompleted = it.isCompleted
            )
        }
        val installments = db.installmentDao().getAllInstallmentsSync().map {
            InstallmentBackupItem(it.id, it.planId, it.installmentIndex, it.amount, it.dueDateMillis, it.paidDateMillis, it.isPaid, it.transactionId)
        }
        val recurring = db.recurringDao().getAllRecurringSync().map {
            RecurringTransactionBackupItem(it.id, it.title, it.type.name, it.amount, it.categoryId, it.accountId, it.interval.name, it.startDateMillis, it.nextDueDateMillis, it.lastProcessedDateMillis, it.isActive, it.note)
        }
        val reminders = db.reminderDao().getAllRemindersSync().map {
            ReminderBackupItem(it.id, it.title, it.description, it.targetType.name, it.targetId, it.dueDateTimeMillis, it.reminderOffsetDays, it.isCompleted, it.isEnabled)
        }
        val budgets = db.budgetDao().getAllBudgetsSync().map {
            BudgetBackupItem(it.id, it.categoryId, it.monthlyAmount, it.monthKey, it.warningThresholdPercent)
        }
        val goals = db.goalDao().getAllGoalsSync().map {
            GoalBackupItem(it.id, it.title, it.targetAmount, it.currentAmount, it.deadlineMillis, it.note, it.iconName, it.colorHex, it.accountId, it.isCompleted)
        }
        val assets = db.assetDao().getAllAssetsSync().map {
            AssetBackupItem(it.id, it.name, it.type.name, it.estimatedValue, it.notes, it.lastUpdateMillis)
        }
        val checklists = db.checklistDao().getAllChecklistsSync().map {
            ChecklistBackupItem(it.id, it.title, it.category, it.colorHex, it.createdAtMillis, it.isPinned, it.isArchived)
        }
        val checklistItems = db.checklistDao().getAllChecklistItemsSync().map {
            ChecklistItemBackupItem(it.id, it.checklistId, it.text, it.estimatedAmount, it.isChecked, it.isExpenseConverted, it.transactionId, it.createdAtMillis)
        }

        DodotaBackupPayload(
            version = 2,
            exportedAtMillis = System.currentTimeMillis(),
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            debts = debts,
            debtPayments = debtPayments,
            installmentPlans = installmentPlans,
            installments = installments,
            recurringTransactions = recurring,
            reminders = reminders,
            budgets = budgets,
            goals = goals,
            assets = assets,
            checklists = checklists,
            checklistItems = checklistItems
        )
    }

    /**
     * Creates manual backup JSON string.
     */
    suspend fun createManualBackupJson(): String = withContext(Dispatchers.IO) {
        val payload = createBackupPayload()
        adapter.toJson(payload)
    }

    /**
     * Executes automatic weekly backup by overwriting/updating the single weekly backup file.
     * Prevents endless storage bloat.
     */
    suspend fun performWeeklyAutoBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = createManualBackupJson()
            autoWeeklyBackupFile.writeText(json, Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasWeeklyBackup(): Boolean = autoWeeklyBackupFile.exists() && autoWeeklyBackupFile.length() > 0

    fun getWeeklyBackupLastModified(): Long = if (hasWeeklyBackup()) autoWeeklyBackupFile.lastModified() else 0L

    /**
     * Validates JSON and restores all entities safely, including receipt images from Base64.
     */
    suspend fun restoreFromJson(jsonString: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (jsonString.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("فایل پشتیبان خالی است."))
            }

            val payload = adapter.fromJson(jsonString)
                ?: return@withContext Result.failure(IllegalArgumentException("فرمت فایل پشتیبان معتبر نیست."))

            if (payload.version <= 0) {
                return@withContext Result.failure(IllegalArgumentException("نسخه پشتیبان پشتیبانی نمی‌شود."))
            }

            // Create temporary safety backup of current state
            val safetyBackupJson = createManualBackupJson()
            val safetyFile = File(backupDir, "dodota_pre_restore_safety.json")
            safetyFile.writeText(safetyBackupJson, Charsets.UTF_8)

            db.withTransaction {
                // Clear existing tables
                db.accountDao().clearAll()
                db.categoryDao().clearAll()
                db.transactionDao().clearAll()
                db.debtDao().clearAll()
                db.debtDao().clearAllPayments()
                db.installmentDao().clearAllPlans()
                db.installmentDao().clearAllInstallments()
                db.recurringDao().clearAll()
                db.reminderDao().clearAll()
                db.budgetDao().clearAll()
                db.goalDao().clearAll()
                db.assetDao().clearAll()
                db.checklistDao().clearAllChecklists()
                db.checklistDao().clearAllChecklistItems()

                // Insert accounts
                val accounts = payload.accounts.map {
                    AccountEntity(
                        id = it.id,
                        name = it.name,
                        type = try { AccountType.valueOf(it.type) } catch (_: Exception) { AccountType.BANK_CARD },
                        balance = it.balance,
                        bankName = it.bankName,
                        cardOrAccountDescription = it.cardOrAccountDescription,
                        cardNumber = it.cardNumber,
                        shebaNumber = it.shebaNumber,
                        accountNumber = it.accountNumber,
                        cvv2 = it.cvv2,
                        expiryDate = it.expiryDate,
                        cardHolderName = it.cardHolderName,
                        colorHex = it.colorHex,
                        iconName = it.iconName,
                        createdAtMillis = it.createdAtMillis,
                        isActive = it.isActive
                    )
                }
                db.accountDao().insertAll(accounts)

                // Insert categories
                val categories = payload.categories.map {
                    CategoryEntity(
                        id = it.id,
                        name = it.name,
                        type = try { TransactionType.valueOf(it.type) } catch (_: Exception) { TransactionType.EXPENSE },
                        iconName = it.iconName,
                        colorHex = it.colorHex,
                        isDefault = it.isDefault,
                        parentCategoryId = it.parentCategoryId
                    )
                }
                db.categoryDao().insertAll(categories)

                // Insert transactions & restore receipt images if present
                val transactions = payload.transactions.map {
                    val restoredPath = if (!it.receiptImageBase64.isNullOrBlank()) {
                        ReceiptImageHelper.saveBase64ImageToInternalStorage(context, it.receiptImageBase64) ?: it.receiptUri
                    } else {
                        it.receiptUri
                    }
                    TransactionEntity(
                        id = it.id,
                        type = try { TransactionType.valueOf(it.type) } catch (_: Exception) { TransactionType.EXPENSE },
                        amount = it.amount,
                        accountId = it.accountId,
                        targetAccountId = it.targetAccountId,
                        externalDestinationInfo = it.externalDestinationInfo,
                        categoryId = it.categoryId,
                        subcategoryId = it.subcategoryId,
                        dateMillis = it.dateMillis,
                        timeString = it.timeString,
                        title = it.title,
                        note = it.note,
                        receiptUri = restoredPath,
                        tags = it.tags,
                        isPinned = it.isPinned,
                        isRecurring = it.isRecurring,
                        recurringId = it.recurringId,
                        installmentId = it.installmentId,
                        debtPaymentId = it.debtPaymentId,
                        isDeleted = it.isDeleted,
                        deletedAtMillis = it.deletedAtMillis
                    )
                }
                db.transactionDao().insertAll(transactions)

                // Insert debts & payments
                val debts = payload.debts.map {
                    DebtEntity(
                        id = it.id,
                        type = try { DebtType.valueOf(it.type) } catch (_: Exception) { DebtType.DEBT_I_OWE },
                        personOrCompanyName = it.personOrCompanyName,
                        totalAmount = it.totalAmount,
                        paidAmount = it.paidAmount,
                        startDateMillis = it.startDateMillis,
                        dueDateMillis = it.dueDateMillis,
                        notes = it.notes,
                        accountId = it.accountId,
                        status = try { DebtStatus.valueOf(it.status) } catch (_: Exception) { DebtStatus.ACTIVE },
                        isCheck = it.isCheck,
                        checkNumber = it.checkNumber,
                        isDeleted = it.isDeleted
                    )
                }
                db.debtDao().insertAllDebts(debts)

                val debtPayments = payload.debtPayments.map {
                    DebtPaymentEntity(
                        id = it.id,
                        debtId = it.debtId,
                        amount = it.amount,
                        dateMillis = it.dateMillis,
                        accountId = it.accountId,
                        notes = it.notes
                    )
                }
                db.debtDao().insertAllPayments(debtPayments)

                // Insert installment plans & installments
                val plans = payload.installmentPlans.map {
                    InstallmentPlanEntity(
                        id = it.id,
                        title = it.title,
                        creditorOrCompany = it.creditorOrCompany,
                        totalLoanAmount = it.totalLoanAmount,
                        totalAmount = it.totalAmount,
                        numberOfInstallments = it.numberOfInstallments,
                        installmentAmount = it.installmentAmount,
                        paidInstallments = it.paidInstallments,
                        startDateMillis = it.startDateMillis,
                        dueDayOfMonth = it.dueDayOfMonth,
                        notes = it.notes,
                        autoCreateExpense = it.autoCreateExpense,
                        accountId = it.accountId,
                        isCompleted = it.isCompleted
                    )
                }
                db.installmentDao().insertAllPlans(plans)

                val installments = payload.installments.map {
                    InstallmentEntity(
                        id = it.id,
                        planId = it.planId,
                        installmentIndex = it.installmentIndex,
                        amount = it.amount,
                        dueDateMillis = it.dueDateMillis,
                        paidDateMillis = it.paidDateMillis,
                        isPaid = it.isPaid,
                        transactionId = it.transactionId
                    )
                }
                db.installmentDao().insertInstallments(installments)

                // Insert recurring
                val recurring = payload.recurringTransactions.map {
                    RecurringTransactionEntity(
                        id = it.id,
                        title = it.title,
                        type = try { TransactionType.valueOf(it.type) } catch (_: Exception) { TransactionType.EXPENSE },
                        amount = it.amount,
                        categoryId = it.categoryId,
                        accountId = it.accountId,
                        interval = try { RecurrenceInterval.valueOf(it.interval) } catch (_: Exception) { RecurrenceInterval.MONTHLY },
                        startDateMillis = it.startDateMillis,
                        nextDueDateMillis = it.nextDueDateMillis,
                        lastProcessedDateMillis = it.lastProcessedDateMillis,
                        isActive = it.isActive,
                        note = it.note
                    )
                }
                db.recurringDao().insertAll(recurring)

                // Insert reminders
                val reminders = payload.reminders.map {
                    ReminderEntity(
                        id = it.id,
                        title = it.title,
                        description = it.description,
                        targetType = try { ReminderTargetType.valueOf(it.targetType) } catch (_: Exception) { ReminderTargetType.CUSTOM },
                        targetId = it.targetId,
                        dueDateTimeMillis = it.dueDateTimeMillis,
                        reminderOffsetDays = it.reminderOffsetDays,
                        isCompleted = it.isCompleted,
                        isEnabled = it.isEnabled
                    )
                }
                db.reminderDao().insertAll(reminders)

                // Insert budgets
                val budgets = payload.budgets.map {
                    BudgetEntity(
                        id = it.id,
                        categoryId = it.categoryId,
                        monthlyAmount = it.monthlyAmount,
                        monthKey = it.monthKey,
                        warningThresholdPercent = it.warningThresholdPercent
                    )
                }
                db.budgetDao().insertAll(budgets)

                // Insert goals
                val goals = payload.goals.map {
                    FinancialGoalEntity(
                        id = it.id,
                        title = it.title,
                        targetAmount = it.targetAmount,
                        currentAmount = it.currentAmount,
                        deadlineMillis = it.deadlineMillis,
                        note = it.note,
                        iconName = it.iconName,
                        colorHex = it.colorHex,
                        accountId = it.accountId,
                        isCompleted = it.isCompleted
                    )
                }
                db.goalDao().insertAll(goals)

                // Insert assets
                val assets = payload.assets.map {
                    AssetEntity(
                        id = it.id,
                        name = it.name,
                        type = try { AssetType.valueOf(it.type) } catch (_: Exception) { AssetType.OTHER },
                        estimatedValue = it.estimatedValue,
                        notes = it.notes,
                        lastUpdateMillis = it.lastUpdateMillis
                    )
                }
                db.assetDao().insertAll(assets)

                // Insert checklists
                if (payload.checklists.isNotEmpty()) {
                    val checklists = payload.checklists.map {
                        ChecklistEntity(
                            id = it.id,
                            title = it.title,
                            category = it.category,
                            colorHex = it.colorHex,
                            createdAtMillis = it.createdAtMillis,
                            isPinned = it.isPinned,
                            isArchived = it.isArchived
                        )
                    }
                    db.checklistDao().insertAllChecklists(checklists)
                }

                // Insert checklist items
                if (payload.checklistItems.isNotEmpty()) {
                    val checklistItems = payload.checklistItems.map {
                        ChecklistItemEntity(
                            id = it.id,
                            checklistId = it.checklistId,
                            text = it.text,
                            estimatedAmount = it.estimatedAmount,
                            isChecked = it.isChecked,
                            isExpenseConverted = it.isExpenseConverted,
                            transactionId = it.transactionId,
                            createdAtMillis = it.createdAtMillis
                        )
                    }
                    db.checklistDao().insertAllChecklistItems(checklistItems)
                }
            }

            Result.success("اطلاعات و تصاویر اسناد با موفقیت بازیابی شد (${payload.transactions.size} تراکنش و ${payload.accounts.size} حساب).")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
