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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BackupRepository(
    private val context: Context,
    private val db: DodotaDatabase
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(DodotaBackupPayload::class.java)

    private fun candidateBackupDirs(): List<File> {
        val dirs = mutableListOf<File>()
        try {
            val docsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null) {
                dirs.add(File(docsDir, "MyFinanceBackup"))
            }
        } catch (_: Exception) {}
        try {
            val appExtDir = context.getExternalFilesDir(null)
            if (appExtDir != null) {
                dirs.add(File(appExtDir, "MyFinanceBackup"))
            }
        } catch (_: Exception) {}
        dirs.add(File(context.filesDir, "MyFinanceBackup"))
        return dirs
    }

    private fun isDirWritable(dir: File): Boolean {
        return try {
            if (!dir.exists() && !dir.mkdirs()) return false
            val probe = File(dir, ".write_probe_${System.nanoTime()}")
            probe.writeText("ok", Charsets.UTF_8)
            probe.delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    private val backupDir: File
        get() {
            for (dir in candidateBackupDirs()) {
                if (isDirWritable(dir)) return dir
            }
            return File(context.filesDir, "MyFinanceBackup").apply { if (!exists()) mkdirs() }
        }

    fun getBackupFolder(): File = backupDir

    fun listBackupFiles(): List<File> {
        val seenPaths = mutableSetOf<String>()
        val result = mutableListOf<File>()
        for (dir in candidateBackupDirs()) {
            try {
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles { file ->
                        file.isFile && file.extension.equals("json", ignoreCase = true)
                    } ?: emptyArray()
                    for (f in files) {
                        val key = try { f.canonicalPath } catch (_: Exception) { f.absolutePath }
                        if (seenPaths.add(key)) {
                            result.add(f)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.lastModified() }
    }

    suspend fun saveBackupToFile(customFileName: String? = null): File = withContext(Dispatchers.IO) {
        val json = createManualBackupJson()
        val sdf = java.text.SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", java.util.Locale.US)
        val timeStamp = sdf.format(java.util.Date())
        val name = customFileName ?: "MyFinance_Backup_$timeStamp.json"
        var lastError: Exception? = null
        for (dir in candidateBackupDirs()) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                file.writeText(json, Charsets.UTF_8)
                return@withContext file
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw (lastError ?: java.io.IOException("خطا در ذخیره‌سازی فایل پشتیبان"))
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
            val content = try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                return@withContext Result.failure(
                    IllegalArgumentException("دسترسی به فایل در حافظه داده نشد (${e.localizedMessage}). لطفاً دکمه «انتخاب فایل JSON» را بزنید و فایل را انتخاب کنید.")
                )
            }
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
        val chandRooz = try {
            db.chandRoozDao().getAllRecordsSync().map {
                ChandRoozBackupItem(
                    id = it.id,
                    title = it.title,
                    category = it.category.name,
                    customCategory = it.customCategory,
                    isRange = it.isRange,
                    startDateMillis = it.startDateMillis,
                    endDateMillis = it.endDateMillis,
                    daysCount = it.daysCount,
                    location = it.location,
                    note = it.note,
                    colorHex = it.colorHex,
                    createdAtMillis = it.createdAtMillis
                )
            }
        } catch (_: Exception) {
            emptyList()
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
            checklistItems = checklistItems,
            chandRoozRecords = chandRooz
        )
    }

    /**
     * Creates manual backup JSON string.
     */
    suspend fun createManualBackupJson(): String = withContext(Dispatchers.IO) {
        val payload = createBackupPayload()
        try {
            adapter.toJson(payload)
        } catch (_: Exception) {
            // Fallback to manual serialization
            serializePayloadManually(payload)
        }
    }

    private fun serializePayloadManually(payload: DodotaBackupPayload): String {
        val root = JSONObject()
        root.put("version", payload.version)
        root.put("exportedAtMillis", payload.exportedAtMillis)

        val accountsArr = JSONArray()
        for (acc in payload.accounts) {
            val o = JSONObject()
            o.put("id", acc.id)
            o.put("name", acc.name)
            o.put("type", acc.type)
            o.put("balance", acc.balance)
            o.put("bankName", acc.bankName)
            o.put("cardOrAccountDescription", acc.cardOrAccountDescription)
            o.put("cardNumber", acc.cardNumber)
            o.put("shebaNumber", acc.shebaNumber)
            o.put("accountNumber", acc.accountNumber)
            o.put("cvv2", acc.cvv2)
            o.put("expiryDate", acc.expiryDate)
            o.put("cardHolderName", acc.cardHolderName)
            o.put("colorHex", acc.colorHex)
            o.put("iconName", acc.iconName)
            o.put("createdAtMillis", acc.createdAtMillis)
            o.put("isActive", acc.isActive)
            accountsArr.put(o)
        }
        root.put("accounts", accountsArr)

        val categoriesArr = JSONArray()
        for (cat in payload.categories) {
            val o = JSONObject()
            o.put("id", cat.id)
            o.put("name", cat.name)
            o.put("type", cat.type)
            o.put("iconName", cat.iconName)
            o.put("colorHex", cat.colorHex)
            o.put("isDefault", cat.isDefault)
            if (cat.parentCategoryId != null) o.put("parentCategoryId", cat.parentCategoryId)
            categoriesArr.put(o)
        }
        root.put("categories", categoriesArr)

        val txArr = JSONArray()
        for (tx in payload.transactions) {
            val o = JSONObject()
            o.put("id", tx.id)
            o.put("type", tx.type)
            o.put("amount", tx.amount)
            o.put("accountId", tx.accountId)
            if (tx.targetAccountId != null) o.put("targetAccountId", tx.targetAccountId)
            o.put("externalDestinationInfo", tx.externalDestinationInfo)
            if (tx.categoryId != null) o.put("categoryId", tx.categoryId)
            if (tx.subcategoryId != null) o.put("subcategoryId", tx.subcategoryId)
            o.put("dateMillis", tx.dateMillis)
            o.put("timeString", tx.timeString)
            o.put("title", tx.title)
            o.put("note", tx.note)
            if (tx.receiptUri != null) o.put("receiptUri", tx.receiptUri)
            if (tx.receiptImageBase64 != null) o.put("receiptImageBase64", tx.receiptImageBase64)
            o.put("tags", tx.tags)
            o.put("isPinned", tx.isPinned)
            o.put("isRecurring", tx.isRecurring)
            if (tx.recurringId != null) o.put("recurringId", tx.recurringId)
            if (tx.installmentId != null) o.put("installmentId", tx.installmentId)
            if (tx.debtPaymentId != null) o.put("debtPaymentId", tx.debtPaymentId)
            o.put("isDeleted", tx.isDeleted)
            if (tx.deletedAtMillis != null) o.put("deletedAtMillis", tx.deletedAtMillis)
            txArr.put(o)
        }
        root.put("transactions", txArr)

        val debtsArr = JSONArray()
        for (d in payload.debts) {
            val o = JSONObject()
            o.put("id", d.id)
            o.put("type", d.type)
            o.put("personOrCompanyName", d.personOrCompanyName)
            o.put("totalAmount", d.totalAmount)
            o.put("paidAmount", d.paidAmount)
            o.put("startDateMillis", d.startDateMillis)
            if (d.dueDateMillis != null) o.put("dueDateMillis", d.dueDateMillis)
            o.put("notes", d.notes)
            if (d.accountId != null) o.put("accountId", d.accountId)
            o.put("status", d.status)
            o.put("isDeleted", d.isDeleted)
            o.put("isCheck", d.isCheck)
            o.put("checkNumber", d.checkNumber)
            debtsArr.put(o)
        }
        root.put("debts", debtsArr)

        val debtPaymentsArr = JSONArray()
        for (dp in payload.debtPayments) {
            val o = JSONObject()
            o.put("id", dp.id)
            o.put("debtId", dp.debtId)
            o.put("amount", dp.amount)
            o.put("dateMillis", dp.dateMillis)
            if (dp.accountId != null) o.put("accountId", dp.accountId)
            o.put("notes", dp.notes)
            debtPaymentsArr.put(o)
        }
        root.put("debtPayments", debtPaymentsArr)

        val plansArr = JSONArray()
        for (p in payload.installmentPlans) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("title", p.title)
            o.put("creditorOrCompany", p.creditorOrCompany)
            o.put("totalLoanAmount", p.totalLoanAmount)
            o.put("totalAmount", p.totalAmount)
            o.put("numberOfInstallments", p.numberOfInstallments)
            o.put("installmentAmount", p.installmentAmount)
            o.put("paidInstallments", p.paidInstallments)
            o.put("startDateMillis", p.startDateMillis)
            o.put("dueDayOfMonth", p.dueDayOfMonth)
            o.put("notes", p.notes)
            o.put("autoCreateExpense", p.autoCreateExpense)
            if (p.accountId != null) o.put("accountId", p.accountId)
            o.put("isCompleted", p.isCompleted)
            plansArr.put(o)
        }
        root.put("installmentPlans", plansArr)

        val installmentsArr = JSONArray()
        for (ins in payload.installments) {
            val o = JSONObject()
            o.put("id", ins.id)
            o.put("planId", ins.planId)
            o.put("installmentIndex", ins.installmentIndex)
            o.put("amount", ins.amount)
            o.put("dueDateMillis", ins.dueDateMillis)
            if (ins.paidDateMillis != null) o.put("paidDateMillis", ins.paidDateMillis)
            o.put("isPaid", ins.isPaid)
            if (ins.transactionId != null) o.put("transactionId", ins.transactionId)
            installmentsArr.put(o)
        }
        root.put("installments", installmentsArr)

        val recArr = JSONArray()
        for (r in payload.recurringTransactions) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("title", r.title)
            o.put("type", r.type)
            o.put("amount", r.amount)
            if (r.categoryId != null) o.put("categoryId", r.categoryId)
            o.put("accountId", r.accountId)
            o.put("interval", r.interval)
            o.put("startDateMillis", r.startDateMillis)
            o.put("nextDueDateMillis", r.nextDueDateMillis)
            if (r.lastProcessedDateMillis != null) o.put("lastProcessedDateMillis", r.lastProcessedDateMillis)
            o.put("isActive", r.isActive)
            o.put("note", r.note)
            recArr.put(o)
        }
        root.put("recurringTransactions", recArr)

        val remArr = JSONArray()
        for (rem in payload.reminders) {
            val o = JSONObject()
            o.put("id", rem.id)
            o.put("title", rem.title)
            o.put("description", rem.description)
            o.put("targetType", rem.targetType)
            if (rem.targetId != null) o.put("targetId", rem.targetId)
            o.put("dueDateTimeMillis", rem.dueDateTimeMillis)
            o.put("reminderOffsetDays", rem.reminderOffsetDays)
            o.put("isCompleted", rem.isCompleted)
            o.put("isEnabled", rem.isEnabled)
            remArr.put(o)
        }
        root.put("reminders", remArr)

        val budgetsArr = JSONArray()
        for (b in payload.budgets) {
            val o = JSONObject()
            o.put("id", b.id)
            o.put("categoryId", b.categoryId)
            o.put("monthlyAmount", b.monthlyAmount)
            o.put("monthKey", b.monthKey)
            o.put("warningThresholdPercent", b.warningThresholdPercent)
            budgetsArr.put(o)
        }
        root.put("budgets", budgetsArr)

        val goalsArr = JSONArray()
        for (g in payload.goals) {
            val o = JSONObject()
            o.put("id", g.id)
            o.put("title", g.title)
            o.put("targetAmount", g.targetAmount)
            o.put("currentAmount", g.currentAmount)
            if (g.deadlineMillis != null) o.put("deadlineMillis", g.deadlineMillis)
            o.put("note", g.note)
            o.put("iconName", g.iconName)
            o.put("colorHex", g.colorHex)
            if (g.accountId != null) o.put("accountId", g.accountId)
            o.put("isCompleted", g.isCompleted)
            goalsArr.put(o)
        }
        root.put("goals", goalsArr)

        val assetsArr = JSONArray()
        for (a in payload.assets) {
            val o = JSONObject()
            o.put("id", a.id)
            o.put("name", a.name)
            o.put("type", a.type)
            o.put("estimatedValue", a.estimatedValue)
            o.put("notes", a.notes)
            o.put("lastUpdateMillis", a.lastUpdateMillis)
            assetsArr.put(o)
        }
        root.put("assets", assetsArr)

        val checklistsArr = JSONArray()
        for (c in payload.checklists) {
            val o = JSONObject()
            o.put("id", c.id)
            o.put("title", c.title)
            o.put("category", c.category)
            o.put("colorHex", c.colorHex)
            o.put("createdAtMillis", c.createdAtMillis)
            o.put("isPinned", c.isPinned)
            o.put("isArchived", c.isArchived)
            checklistsArr.put(o)
        }
        root.put("checklists", checklistsArr)

        val checklistItemsArr = JSONArray()
        for (ci in payload.checklistItems) {
            val o = JSONObject()
            o.put("id", ci.id)
            o.put("checklistId", ci.checklistId)
            o.put("text", ci.text)
            o.put("estimatedAmount", ci.estimatedAmount)
            o.put("isChecked", ci.isChecked)
            o.put("isExpenseConverted", ci.isExpenseConverted)
            if (ci.transactionId != null) o.put("transactionId", ci.transactionId)
            o.put("createdAtMillis", ci.createdAtMillis)
            checklistItemsArr.put(o)
        }
        root.put("checklistItems", checklistItemsArr)

        val chandRoozArr = JSONArray()
        for (cr in payload.chandRoozRecords) {
            val o = JSONObject()
            o.put("id", cr.id)
            o.put("title", cr.title)
            o.put("category", cr.category)
            o.put("customCategory", cr.customCategory)
            o.put("isRange", cr.isRange)
            o.put("startDateMillis", cr.startDateMillis)
            o.put("endDateMillis", cr.endDateMillis)
            o.put("daysCount", cr.daysCount)
            o.put("location", cr.location)
            o.put("note", cr.note)
            o.put("colorHex", cr.colorHex)
            o.put("createdAtMillis", cr.createdAtMillis)
            chandRoozArr.put(o)
        }
        root.put("chandRoozRecords", chandRoozArr)

        return root.toString(2)
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
     * Safe JSON helpers tolerant of nulls, strings, ints, longs, doubles and missing fields.
     */
    private fun optSafeLong(obj: JSONObject, key: String, default: Long = 0L): Long {
        if (!obj.has(key) || obj.isNull(key)) return default
        val v = obj.opt(key) ?: return default
        return when (v) {
            is Number -> v.toLong()
            is String -> v.trim().toLongOrNull() ?: default
            else -> default
        }
    }

    private fun optNullableLong(obj: JSONObject, key: String): Long? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.opt(key) ?: return null
        return when (v) {
            is Number -> v.toLong()
            is String -> v.trim().toLongOrNull()
            else -> null
        }
    }

    private fun optSafeInt(obj: JSONObject, key: String, default: Int = 0): Int {
        if (!obj.has(key) || obj.isNull(key)) return default
        val v = obj.opt(key) ?: return default
        return when (v) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull() ?: default
            else -> default
        }
    }

    private fun optSafeString(obj: JSONObject, key: String, default: String = ""): String {
        if (!obj.has(key) || obj.isNull(key)) return default
        val v = obj.opt(key) ?: return default
        return v.toString()
    }

    private fun optNullableString(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.opt(key) ?: return null
        val s = v.toString()
        return if (s.isBlank() && obj.isNull(key)) null else s
    }

    private fun optSafeBoolean(obj: JSONObject, key: String, default: Boolean = false): Boolean {
        if (!obj.has(key) || obj.isNull(key)) return default
        val v = obj.opt(key) ?: return default
        return when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v.trim().equals("true", ignoreCase = true) || v.trim() == "1"
            else -> default
        }
    }

    /**
     * Resilient parser using standard org.json with zero reflection or metadata dependency.
     * Parses old version 1 backups, new version 2 backups, and corrupted/partial JSON safely.
     */
    fun parsePayloadResilient(jsonString: String): DodotaBackupPayload {
        val root = JSONObject(jsonString)
        val version = root.optInt("version", root.optInt("v", 2))
        val exportedAt = optSafeLong(root, "exportedAtMillis", optSafeLong(root, "exportedAt", System.currentTimeMillis()))

        val accountsList = mutableListOf<AccountBackupItem>()
        val accountsArray = root.optJSONArray("accounts")
        if (accountsArray != null) {
            for (i in 0 until accountsArray.length()) {
                val obj = accountsArray.optJSONObject(i) ?: continue
                accountsList.add(
                    AccountBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        name = optSafeString(obj, "name", "حساب ${i + 1}"),
                        type = optSafeString(obj, "type", "BANK_CARD"),
                        balance = optSafeLong(obj, "balance", 0L),
                        bankName = optSafeString(obj, "bankName", ""),
                        cardOrAccountDescription = optSafeString(obj, "cardOrAccountDescription", ""),
                        cardNumber = optSafeString(obj, "cardNumber", ""),
                        shebaNumber = optSafeString(obj, "shebaNumber", ""),
                        accountNumber = optSafeString(obj, "accountNumber", ""),
                        cvv2 = optSafeString(obj, "cvv2", ""),
                        expiryDate = optSafeString(obj, "expiryDate", ""),
                        cardHolderName = optSafeString(obj, "cardHolderName", ""),
                        colorHex = optSafeString(obj, "colorHex", "#E50914"),
                        iconName = optSafeString(obj, "iconName", "card"),
                        createdAtMillis = optSafeLong(obj, "createdAtMillis", System.currentTimeMillis()),
                        isActive = optSafeBoolean(obj, "isActive", true)
                    )
                )
            }
        }

        val categoriesList = mutableListOf<CategoryBackupItem>()
        val categoriesArray = root.optJSONArray("categories")
        if (categoriesArray != null) {
            for (i in 0 until categoriesArray.length()) {
                val obj = categoriesArray.optJSONObject(i) ?: continue
                categoriesList.add(
                    CategoryBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        name = optSafeString(obj, "name", "دسته‌بندی ${i + 1}"),
                        type = optSafeString(obj, "type", "EXPENSE"),
                        iconName = optSafeString(obj, "iconName", "category"),
                        colorHex = optSafeString(obj, "colorHex", "#0EA5E9"),
                        isDefault = optSafeBoolean(obj, "isDefault", false),
                        parentCategoryId = optNullableLong(obj, "parentCategoryId")
                    )
                )
            }
        }

        val transactionsList = mutableListOf<TransactionBackupItem>()
        val transactionsArray = root.optJSONArray("transactions")
        if (transactionsArray != null) {
            for (i in 0 until transactionsArray.length()) {
                val obj = transactionsArray.optJSONObject(i) ?: continue
                transactionsList.add(
                    TransactionBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        type = optSafeString(obj, "type", "EXPENSE"),
                        amount = optSafeLong(obj, "amount", 0L),
                        accountId = optSafeLong(obj, "accountId", 1L),
                        targetAccountId = optNullableLong(obj, "targetAccountId"),
                        externalDestinationInfo = optSafeString(obj, "externalDestinationInfo", ""),
                        categoryId = optNullableLong(obj, "categoryId"),
                        subcategoryId = optNullableLong(obj, "subcategoryId"),
                        dateMillis = optSafeLong(obj, "dateMillis", System.currentTimeMillis()),
                        timeString = optSafeString(obj, "timeString", ""),
                        title = optSafeString(obj, "title", ""),
                        note = optSafeString(obj, "note", ""),
                        receiptUri = optNullableString(obj, "receiptUri"),
                        receiptImageBase64 = optNullableString(obj, "receiptImageBase64"),
                        tags = optSafeString(obj, "tags", ""),
                        isPinned = optSafeBoolean(obj, "isPinned", false),
                        isRecurring = optSafeBoolean(obj, "isRecurring", false),
                        recurringId = optNullableLong(obj, "recurringId"),
                        installmentId = optNullableLong(obj, "installmentId"),
                        debtPaymentId = optNullableLong(obj, "debtPaymentId"),
                        isDeleted = optSafeBoolean(obj, "isDeleted", false),
                        deletedAtMillis = optNullableLong(obj, "deletedAtMillis")
                    )
                )
            }
        }

        val debtsList = mutableListOf<DebtBackupItem>()
        val debtsArray = root.optJSONArray("debts")
        if (debtsArray != null) {
            for (i in 0 until debtsArray.length()) {
                val obj = debtsArray.optJSONObject(i) ?: continue
                debtsList.add(
                    DebtBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        type = optSafeString(obj, "type", "DEBT_I_OWE"),
                        personOrCompanyName = optSafeString(obj, "personOrCompanyName", ""),
                        totalAmount = optSafeLong(obj, "totalAmount", 0L),
                        paidAmount = optSafeLong(obj, "paidAmount", 0L),
                        startDateMillis = optSafeLong(obj, "startDateMillis", System.currentTimeMillis()),
                        dueDateMillis = optNullableLong(obj, "dueDateMillis"),
                        notes = optSafeString(obj, "notes", ""),
                        accountId = optNullableLong(obj, "accountId"),
                        status = optSafeString(obj, "status", "ACTIVE"),
                        isDeleted = optSafeBoolean(obj, "isDeleted", false),
                        isCheck = optSafeBoolean(obj, "isCheck", false),
                        checkNumber = optSafeString(obj, "checkNumber", "")
                    )
                )
            }
        }

        val debtPaymentsList = mutableListOf<DebtPaymentBackupItem>()
        val debtPaymentsArray = root.optJSONArray("debtPayments")
        if (debtPaymentsArray != null) {
            for (i in 0 until debtPaymentsArray.length()) {
                val obj = debtPaymentsArray.optJSONObject(i) ?: continue
                debtPaymentsList.add(
                    DebtPaymentBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        debtId = optSafeLong(obj, "debtId", 0L),
                        amount = optSafeLong(obj, "amount", 0L),
                        dateMillis = optSafeLong(obj, "dateMillis", System.currentTimeMillis()),
                        accountId = optNullableLong(obj, "accountId"),
                        notes = optSafeString(obj, "notes", "")
                    )
                )
            }
        }

        val plansList = mutableListOf<InstallmentPlanBackupItem>()
        val plansArray = root.optJSONArray("installmentPlans") ?: root.optJSONArray("plans")
        if (plansArray != null) {
            for (i in 0 until plansArray.length()) {
                val obj = plansArray.optJSONObject(i) ?: continue
                plansList.add(
                    InstallmentPlanBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        title = optSafeString(obj, "title", "وام / قسط"),
                        creditorOrCompany = optSafeString(obj, "creditorOrCompany", ""),
                        totalLoanAmount = optSafeLong(obj, "totalLoanAmount", 0L),
                        totalAmount = optSafeLong(obj, "totalAmount", 0L),
                        numberOfInstallments = optSafeInt(obj, "numberOfInstallments", 1).coerceAtLeast(1),
                        installmentAmount = optSafeLong(obj, "installmentAmount", 0L),
                        paidInstallments = optSafeInt(obj, "paidInstallments", 0),
                        startDateMillis = optSafeLong(obj, "startDateMillis", System.currentTimeMillis()),
                        dueDayOfMonth = optSafeInt(obj, "dueDayOfMonth", 1),
                        notes = optSafeString(obj, "notes", ""),
                        autoCreateExpense = optSafeBoolean(obj, "autoCreateExpense", true),
                        accountId = optNullableLong(obj, "accountId"),
                        isCompleted = optSafeBoolean(obj, "isCompleted", false)
                    )
                )
            }
        }

        val installmentsList = mutableListOf<InstallmentBackupItem>()
        val installmentsArray = root.optJSONArray("installments")
        if (installmentsArray != null) {
            for (i in 0 until installmentsArray.length()) {
                val obj = installmentsArray.optJSONObject(i) ?: continue
                installmentsList.add(
                    InstallmentBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        planId = optSafeLong(obj, "planId", 0L),
                        installmentIndex = optSafeInt(obj, "installmentIndex", i + 1),
                        amount = optSafeLong(obj, "amount", 0L),
                        dueDateMillis = optSafeLong(obj, "dueDateMillis", System.currentTimeMillis()),
                        paidDateMillis = optNullableLong(obj, "paidDateMillis"),
                        isPaid = optSafeBoolean(obj, "isPaid", false),
                        transactionId = optNullableLong(obj, "transactionId")
                    )
                )
            }
        }

        val recList = mutableListOf<RecurringTransactionBackupItem>()
        val recArray = root.optJSONArray("recurringTransactions") ?: root.optJSONArray("recurring")
        if (recArray != null) {
            for (i in 0 until recArray.length()) {
                val obj = recArray.optJSONObject(i) ?: continue
                recList.add(
                    RecurringTransactionBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        title = optSafeString(obj, "title", ""),
                        type = optSafeString(obj, "type", "EXPENSE"),
                        amount = optSafeLong(obj, "amount", 0L),
                        categoryId = optNullableLong(obj, "categoryId"),
                        accountId = optSafeLong(obj, "accountId", 1L),
                        interval = optSafeString(obj, "interval", "MONTHLY"),
                        startDateMillis = optSafeLong(obj, "startDateMillis", System.currentTimeMillis()),
                        nextDueDateMillis = optSafeLong(obj, "nextDueDateMillis", System.currentTimeMillis()),
                        lastProcessedDateMillis = optNullableLong(obj, "lastProcessedDateMillis"),
                        isActive = optSafeBoolean(obj, "isActive", true),
                        note = optSafeString(obj, "note", "")
                    )
                )
            }
        }

        val remList = mutableListOf<ReminderBackupItem>()
        val remArray = root.optJSONArray("reminders")
        if (remArray != null) {
            for (i in 0 until remArray.length()) {
                val obj = remArray.optJSONObject(i) ?: continue
                remList.add(
                    ReminderBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        title = optSafeString(obj, "title", ""),
                        description = optSafeString(obj, "description", ""),
                        targetType = optSafeString(obj, "targetType", "CUSTOM"),
                        targetId = optNullableLong(obj, "targetId"),
                        dueDateTimeMillis = optSafeLong(obj, "dueDateTimeMillis", System.currentTimeMillis()),
                        reminderOffsetDays = optSafeInt(obj, "reminderOffsetDays", 0),
                        isCompleted = optSafeBoolean(obj, "isCompleted", false),
                        isEnabled = optSafeBoolean(obj, "isEnabled", true)
                    )
                )
            }
        }

        val budgetsList = mutableListOf<BudgetBackupItem>()
        val budgetsArray = root.optJSONArray("budgets")
        if (budgetsArray != null) {
            for (i in 0 until budgetsArray.length()) {
                val obj = budgetsArray.optJSONObject(i) ?: continue
                budgetsList.add(
                    BudgetBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        categoryId = optSafeLong(obj, "categoryId", 0L),
                        monthlyAmount = optSafeLong(obj, "monthlyAmount", 0L),
                        monthKey = optSafeString(obj, "monthKey", ""),
                        warningThresholdPercent = optSafeInt(obj, "warningThresholdPercent", 80)
                    )
                )
            }
        }

        val goalsList = mutableListOf<GoalBackupItem>()
        val goalsArray = root.optJSONArray("goals")
        if (goalsArray != null) {
            for (i in 0 until goalsArray.length()) {
                val obj = goalsArray.optJSONObject(i) ?: continue
                goalsList.add(
                    GoalBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        title = optSafeString(obj, "title", ""),
                        targetAmount = optSafeLong(obj, "targetAmount", 0L),
                        currentAmount = optSafeLong(obj, "currentAmount", 0L),
                        deadlineMillis = optNullableLong(obj, "deadlineMillis"),
                        note = optSafeString(obj, "note", ""),
                        iconName = optSafeString(obj, "iconName", "savings"),
                        colorHex = optSafeString(obj, "colorHex", "#10B981"),
                        accountId = optNullableLong(obj, "accountId"),
                        isCompleted = optSafeBoolean(obj, "isCompleted", false)
                    )
                )
            }
        }

        val assetsList = mutableListOf<AssetBackupItem>()
        val assetsArray = root.optJSONArray("assets")
        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val obj = assetsArray.optJSONObject(i) ?: continue
                assetsList.add(
                    AssetBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        name = optSafeString(obj, "name", ""),
                        type = optSafeString(obj, "type", "OTHER"),
                        estimatedValue = optSafeLong(obj, "estimatedValue", 0L),
                        notes = optSafeString(obj, "notes", ""),
                        lastUpdateMillis = optSafeLong(obj, "lastUpdateMillis", System.currentTimeMillis())
                    )
                )
            }
        }

        val checklistsList = mutableListOf<ChecklistBackupItem>()
        val checklistsArray = root.optJSONArray("checklists")
        if (checklistsArray != null) {
            for (i in 0 until checklistsArray.length()) {
                val obj = checklistsArray.optJSONObject(i) ?: continue
                checklistsList.add(
                    ChecklistBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        title = optSafeString(obj, "title", "چک‌لیست"),
                        category = optSafeString(obj, "category", "خرید روزانه"),
                        colorHex = optSafeString(obj, "colorHex", "#10B981"),
                        createdAtMillis = optSafeLong(obj, "createdAtMillis", System.currentTimeMillis()),
                        isPinned = optSafeBoolean(obj, "isPinned", false),
                        isArchived = optSafeBoolean(obj, "isArchived", false)
                    )
                )
            }
        }

        val checklistItemsList = mutableListOf<ChecklistItemBackupItem>()
        val checklistItemsArray = root.optJSONArray("checklistItems")
        if (checklistItemsArray != null) {
            for (i in 0 until checklistItemsArray.length()) {
                val obj = checklistItemsArray.optJSONObject(i) ?: continue
                checklistItemsList.add(
                    ChecklistItemBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        checklistId = optSafeLong(obj, "checklistId", 0L),
                        text = optSafeString(obj, "text", ""),
                        estimatedAmount = optSafeLong(obj, "estimatedAmount", 0L),
                        isChecked = optSafeBoolean(obj, "isChecked", false),
                        isExpenseConverted = optSafeBoolean(obj, "isExpenseConverted", false),
                        transactionId = optNullableLong(obj, "transactionId"),
                        createdAtMillis = optSafeLong(obj, "createdAtMillis", System.currentTimeMillis())
                    )
                )
            }
        }

        val chandRoozList = mutableListOf<ChandRoozBackupItem>()
        val chandRoozArray = root.optJSONArray("chandRoozRecords")
        if (chandRoozArray != null) {
            for (i in 0 until chandRoozArray.length()) {
                val obj = chandRoozArray.optJSONObject(i) ?: continue
                chandRoozList.add(
                    ChandRoozBackupItem(
                        id = optSafeLong(obj, "id", (i + 1).toLong()),
                        title = optSafeString(obj, "title", ""),
                        category = optSafeString(obj, "category", "OTHER"),
                        customCategory = optSafeString(obj, "customCategory", ""),
                        isRange = optSafeBoolean(obj, "isRange", false),
                        startDateMillis = optSafeLong(obj, "startDateMillis", System.currentTimeMillis()),
                        endDateMillis = optSafeLong(obj, "endDateMillis", System.currentTimeMillis()),
                        daysCount = optSafeInt(obj, "daysCount", 1).coerceAtLeast(1),
                        location = optSafeString(obj, "location", ""),
                        note = optSafeString(obj, "note", ""),
                        colorHex = optSafeString(obj, "colorHex", "#F97316"),
                        createdAtMillis = optSafeLong(obj, "createdAtMillis", System.currentTimeMillis())
                    )
                )
            }
        }

        return DodotaBackupPayload(
            version = version,
            exportedAtMillis = exportedAt,
            accounts = accountsList,
            categories = categoriesList,
            transactions = transactionsList,
            debts = debtsList,
            debtPayments = debtPaymentsList,
            installmentPlans = plansList,
            installments = installmentsList,
            recurringTransactions = recList,
            reminders = remList,
            budgets = budgetsList,
            goals = goalsList,
            assets = assetsList,
            checklists = checklistsList,
            checklistItems = checklistItemsList,
            chandRoozRecords = chandRoozList
        )
    }

    /**
     * Validates JSON and restores all entities safely, including receipt images from Base64.
     */
    suspend fun restoreFromJson(jsonString: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val clean = jsonString.replace("\uFEFF", "").trim()
            if (clean.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("فایل پشتیبان خالی است."))
            }

            var payload: DodotaBackupPayload? = null
            try {
                payload = adapter.fromJson(clean)
            } catch (_: Throwable) {
                // Moshi reflection or metadata failure, will parse resiliently below
            }

            if (payload == null) {
                payload = try {
                    parsePayloadResilient(clean)
                } catch (e: Exception) {
                    return@withContext Result.failure(IllegalArgumentException("فرمت فایل پشتیبان معتبر نیست: ${e.message}"))
                }
            }

            if (payload == null) {
                return@withContext Result.failure(IllegalArgumentException("امکان بازخوانی محتوای فایل پشتیبان وجود ندارد."))
            }

            // Create temporary safety backup of current state (safely ignore write errors so restore proceeds)
            try {
                val safetyBackupJson = createManualBackupJson()
                val safetyFile = File(context.filesDir, "dodota_pre_restore_safety.json")
                safetyFile.writeText(safetyBackupJson, Charsets.UTF_8)
            } catch (_: Exception) {}

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
                try {
                    db.chandRoozDao().clearAll()
                } catch (_: Exception) {}

                // Insert accounts
                if (payload.accounts.isNotEmpty()) {
                    val accounts = payload.accounts.map {
                        AccountEntity(
                            id = it.id,
                            name = it.name.ifBlank { "حساب بانکی" },
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
                            colorHex = it.colorHex.ifBlank { "#E50914" },
                            iconName = it.iconName.ifBlank { "card" },
                            createdAtMillis = it.createdAtMillis,
                            isActive = it.isActive
                        )
                    }
                    db.accountDao().insertAll(accounts)
                }

                // Insert categories
                if (payload.categories.isNotEmpty()) {
                    val categories = payload.categories.map {
                        CategoryEntity(
                            id = it.id,
                            name = it.name.ifBlank { "دسته‌بندی" },
                            type = try { TransactionType.valueOf(it.type) } catch (_: Exception) { TransactionType.EXPENSE },
                            iconName = it.iconName.ifBlank { "category" },
                            colorHex = it.colorHex.ifBlank { "#0EA5E9" },
                            isDefault = it.isDefault,
                            parentCategoryId = it.parentCategoryId
                        )
                    }
                    db.categoryDao().insertAll(categories)
                }

                // Insert transactions & restore receipt images if present
                if (payload.transactions.isNotEmpty()) {
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
                }

                // Insert debts & payments
                if (payload.debts.isNotEmpty()) {
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
                }

                if (payload.debtPayments.isNotEmpty()) {
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
                }

                // Insert installment plans & installments
                if (payload.installmentPlans.isNotEmpty()) {
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
                }

                if (payload.installments.isNotEmpty()) {
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
                }

                // Insert recurring
                if (payload.recurringTransactions.isNotEmpty()) {
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
                }

                // Insert reminders
                if (payload.reminders.isNotEmpty()) {
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
                }

                // Insert budgets
                if (payload.budgets.isNotEmpty()) {
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
                }

                // Insert goals
                if (payload.goals.isNotEmpty()) {
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
                }

                // Insert assets
                if (payload.assets.isNotEmpty()) {
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
                }

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

                // Insert chand rooz records
                if (payload.chandRoozRecords.isNotEmpty()) {
                    val records = payload.chandRoozRecords.map {
                        ChandRoozRecordEntity(
                            id = it.id,
                            title = it.title,
                            category = try { ChandRoozCategory.valueOf(it.category) } catch (_: Exception) { ChandRoozCategory.OTHER },
                            customCategory = it.customCategory,
                            isRange = it.isRange,
                            startDateMillis = it.startDateMillis,
                            endDateMillis = it.endDateMillis,
                            daysCount = it.daysCount,
                            location = it.location,
                            note = it.note,
                            colorHex = it.colorHex,
                            createdAtMillis = it.createdAtMillis
                        )
                    }
                    try {
                        db.chandRoozDao().insertAll(records)
                    } catch (_: Exception) {}
                }
            }

            Result.success("اطلاعات با موفقیت بازیابی شد (${payload.transactions.size} تراکنش و ${payload.accounts.size} حساب).")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
