package ir.mmd.dodota.util

import ir.mmd.dodota.data.model.*
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.ui.components.PrintConfirmationPayload

object DodotaPrintBuilders {

    private fun fmtMoney(amount: Long, currency: AppCurrency, showSign: Boolean = false): String =
        PersianNumberFormatter.formatCurrency(amount, currency, showSign)

    private fun fmtDate(millis: Long?, currency: AppCurrency): String =
        if (millis == null || millis <= 0L) "—"
        else PersianDateHelper.formatToPersianDateNumeric(millis, currency.isEnglishDigits)

    private fun fmtNum(num: Number, currency: AppCurrency, commas: Boolean = false): String =
        PersianNumberFormatter.formatNumber(num, useCommas = commas, isEnglishDigits = currency.isEnglishDigits)

    private fun fmtTextDigits(text: String, currency: AppCurrency): String =
        PersianNumberFormatter.formatDigits(text, currency)

    private fun inRange(millis: Long, start: Long?, end: Long?): Boolean {
        if (start != null && millis < start) return false
        if (end != null && millis > end) return false
        return true
    }

    // ------------------------------------------------------------------
    // 1. TRANSACTIONS REPORT
    // ------------------------------------------------------------------
    fun buildTransactionsDocument(
        payload: PrintConfirmationPayload,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        userPreferences: UserPreferences,
        titleOverride: String = "گزارش تراکنش‌ها",
        subtitleOverride: String = "صورت‌حساب ریز تراکنش‌های مالی"
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val accountMap = accounts.associateBy { it.id }
        val categoryMap = categories.associateBy { it.id }

        val allowedTypes = buildSet {
            if ("income" in payload.selectedSections) add(TransactionType.INCOME)
            if ("expense" in payload.selectedSections) add(TransactionType.EXPENSE)
            if ("transfer" in payload.selectedSections) add(TransactionType.TRANSFER)
        }

        val filtered = transactions
            .filter { tx ->
                tx.type in allowedTypes && inRange(tx.dateMillis, payload.startMillis, payload.endMillis)
            }
            .sortedByDescending { it.dateMillis }

        val totalIncome = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalTransfer = filtered.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount }
        val netFlow = totalIncome - totalExpense

        val kpis = buildList {
            if ("income" in payload.selectedSections) {
                add(PrintKpiItem("جمع درآمدها", fmtMoney(totalIncome, currency), PrintTone.POSITIVE))
            }
            if ("expense" in payload.selectedSections) {
                add(PrintKpiItem("جمع هزینه‌ها", fmtMoney(totalExpense, currency), PrintTone.NEGATIVE))
            }
            if ("income" in payload.selectedSections && "expense" in payload.selectedSections) {
                add(
                    PrintKpiItem(
                        "تراز خالص بازه",
                        fmtMoney(netFlow, currency, showSign = true),
                        if (netFlow >= 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE
                    )
                )
            }
            if ("transfer" in payload.selectedSections && totalTransfer > 0L) {
                add(PrintKpiItem("جمع انتقال‌ها", fmtMoney(totalTransfer, currency), PrintTone.INFO))
            }
            add(PrintKpiItem("تعداد تراکنش", "${fmtNum(filtered.size, currency)} ردیف", PrintTone.NEUTRAL))
        }

        val headers = buildList {
            add("ردیف")
            add("تاریخ")
            add("نوع")
            add("عنوان / شرح")
            add("دسته‌بندی")
            add("حساب")
            add("مبلغ")
            if (payload.includeNotes) add("یادداشت")
        }

        val rows = filtered.mapIndexed { idx, tx ->
            val accName = accountMap[tx.accountId]?.name ?: "—"
            val targetName = tx.targetAccountId?.let { accountMap[it]?.name }
            val accountDisplay = when {
                tx.type == TransactionType.TRANSFER && targetName != null -> "$accName ⬅ $targetName"
                tx.type == TransactionType.TRANSFER && tx.externalDestinationInfo.isNotBlank() -> "$accName ⬅ ${tx.externalDestinationInfo}"
                else -> accName
            }
            val catName = tx.categoryId?.let { categoryMap[it]?.name } ?: "—"
            val titleText = tx.title.ifBlank { catName }
            val tone = when (tx.type) {
                TransactionType.INCOME -> PrintTone.POSITIVE
                TransactionType.EXPENSE -> PrintTone.NEGATIVE
                TransactionType.TRANSFER -> PrintTone.INFO
            }
            buildList {
                add(PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true))
                add(PrintCell(fmtDate(tx.dateMillis, currency), isNumericOrDate = true))
                add(PrintCell(tx.type.title, tone = tone))
                add(PrintCell(fmtTextDigits(titleText, currency), isBold = true))
                add(PrintCell(catName))
                add(PrintCell(fmtTextDigits(accountDisplay, currency)))
                add(PrintCell(fmtMoney(tx.amount, currency), isNumericOrDate = true, isBold = true, tone = tone))
                if (payload.includeNotes) {
                    add(PrintCell(fmtTextDigits(tx.note.ifBlank { "—" }, currency)))
                }
            }
        }

        val sections = listOf(
            PrintSectionBlock(
                title = "فهرست تراکنش‌های انتخاب‌شده",
                badge = "${fmtNum(rows.size, currency)} تراکنش",
                headers = headers,
                rows = rows,
                footerSummary = "جمع کل درآمد: ${fmtMoney(totalIncome, currency)}  |  جمع کل هزینه: ${fmtMoney(totalExpense, currency)}  |  تراز: ${fmtMoney(netFlow, currency, showSign = true)}"
            )
        )

        return PrintableReportDocument(
            documentTitle = titleOverride,
            subtitle = subtitleOverride,
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = kpis,
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 2. REPORTS & ANALYTICS DOCUMENT
    // ------------------------------------------------------------------
    fun buildReportsDocument(
        payload: PrintConfirmationPayload,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val currentPDate = PersianDateHelper.getCurrentPersianDate()
        val categoryMap = categories.associateBy { it.id }

        val filtered = transactions
            .filter { inRange(it.dateMillis, payload.startMillis, payload.endMillis) }
            .sortedByDescending { it.dateMillis }

        val totalIncome = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netSavings = totalIncome - totalExpense
        val savingsRate = if (totalIncome > 0L) ((netSavings.coerceAtLeast(0L).toDouble() / totalIncome) * 100).toInt() else 0
        val activeDays = filtered.map {
            val d = PersianDateHelper.fromMillis(it.dateMillis)
            "${d.year}-${d.month}-${d.day}"
        }.distinct().size.coerceAtLeast(1)
        val dailyAvgExpense = if (totalExpense > 0L) totalExpense / activeDays else 0L

        val kpis = listOf(
            PrintKpiItem("کل درآمد بازه", fmtMoney(totalIncome, currency), PrintTone.POSITIVE),
            PrintKpiItem("کل هزینه بازه", fmtMoney(totalExpense, currency), PrintTone.NEGATIVE),
            PrintKpiItem(
                "پس‌انداز خالص",
                fmtMoney(netSavings, currency, showSign = true),
                if (netSavings >= 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE
            ),
            PrintKpiItem("نرخ پس‌انداز", PersianNumberFormatter.formatPercent(savingsRate, currency.isEnglishDigits), PrintTone.INFO)
        )

        val sections = mutableListOf<PrintSectionBlock>()

        if ("kpi" in payload.selectedSections) {
            val biggestExp = filtered.filter { it.type == TransactionType.EXPENSE }.maxByOrNull { it.amount }
            val biggestInc = filtered.filter { it.type == TransactionType.INCOME }.maxByOrNull { it.amount }
            val kpiRows = listOf(
                listOf(
                    PrintCell("تعداد کل تراکنش‌ها", isBold = true),
                    PrintCell("${fmtNum(filtered.size, currency)} تراکنش", isNumericOrDate = true)
                ),
                listOf(
                    PrintCell("میانگین هزینه روزانه (در روزهای فعال)", isBold = true),
                    PrintCell(fmtMoney(dailyAvgExpense, currency), isNumericOrDate = true, tone = PrintTone.NEGATIVE)
                ),
                listOf(
                    PrintCell("بیشترین هزینه تک‌موردی", isBold = true),
                    PrintCell(
                        if (biggestExp != null) "${biggestExp.title.ifBlank { "هزینه" }} (${fmtMoney(biggestExp.amount, currency)})" else "—",
                        tone = PrintTone.NEGATIVE
                    )
                ),
                listOf(
                    PrintCell("بیشترین درآمد تک‌موردی", isBold = true),
                    PrintCell(
                        if (biggestInc != null) "${biggestInc.title.ifBlank { "درآمد" }} (${fmtMoney(biggestInc.amount, currency)})" else "—",
                        tone = PrintTone.POSITIVE
                    )
                )
            )
            sections.add(
                PrintSectionBlock(
                    title = "شاخص‌های تحلیلی و عملکرد مالی",
                    badge = "خلاصه آماری",
                    headers = listOf("شاخص مالی", "مقدار"),
                    rows = kpiRows
                )
            )
        }

        if ("categories" in payload.selectedSections) {
            val expenseGroups = filtered.filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .entries
                .sortedByDescending { it.value.sumOf { tx -> tx.amount } }

            val catRows = expenseGroups.mapIndexed { idx, (catId, txs) ->
                val catName = catId?.let { categoryMap[it]?.name } ?: "سایر هزینه‌ها"
                val sum = txs.sumOf { it.amount }
                val share = if (totalExpense > 0L) ((sum.toDouble() / totalExpense.toDouble()) * 100).toInt() else 0
                listOf(
                    PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true),
                    PrintCell(catName, isBold = true),
                    PrintCell("${fmtNum(txs.size, currency)} تراکنش", isNumericOrDate = true),
                    PrintCell(PersianNumberFormatter.formatPercent(share, currency.isEnglishDigits), isNumericOrDate = true, tone = PrintTone.INFO),
                    PrintCell(fmtMoney(sum, currency), isNumericOrDate = true, isBold = true, tone = PrintTone.NEGATIVE)
                )
            }
            sections.add(
                PrintSectionBlock(
                    title = "تفکیک هزینه‌ها بر اساس دسته‌بندی",
                    badge = "${fmtNum(catRows.size, currency)} دسته",
                    headers = listOf("ردیف", "دسته‌بندی", "تعداد", "سهم", "جمع مبلغ"),
                    rows = catRows,
                    footerSummary = "جمع کل هزینه‌های دسته‌بندی‌شده: ${fmtMoney(totalExpense, currency)}"
                )
            )
        }

        if ("monthly" in payload.selectedSections) {
            val monthPairs = buildList {
                var y = currentPDate.year
                var m = currentPDate.month
                repeat(6) {
                    add(y to m)
                    val prev = PersianDateHelper.getPreviousPersianMonth(y, m)
                    y = prev.first
                    m = prev.second
                }
            }.reversed()
            val monthRows = monthPairs.map { (y, m) ->
                val s = PersianDateHelper.getStartOfMonthMillis(y, m)
                val e = PersianDateHelper.getEndOfMonthMillis(y, m)
                val monthTxs = transactions.filter { it.dateMillis in s..e }
                val inc = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                val exp = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val net = inc - exp
                listOf(
                    PrintCell("${PersianDateHelper.getMonthName(m)} ${fmtNum(y, currency)}", isBold = true),
                    PrintCell(fmtMoney(inc, currency), isNumericOrDate = true, tone = PrintTone.POSITIVE),
                    PrintCell(fmtMoney(exp, currency), isNumericOrDate = true, tone = PrintTone.NEGATIVE),
                    PrintCell(
                        fmtMoney(net, currency, showSign = true),
                        isNumericOrDate = true,
                        isBold = true,
                        tone = if (net >= 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE
                    )
                )
            }
            sections.add(
                PrintSectionBlock(
                    title = "روند و مقایسه ۶ ماهه اخیر",
                    badge = "${fmtNum(monthRows.size, currency)} ماه",
                    headers = listOf("ماه", "درآمد", "هزینه", "تراز خالص"),
                    rows = monthRows
                )
            )
        }

        if ("transactions" in payload.selectedSections) {
            val txDoc = buildTransactionsDocument(
                payload = payload.copy(selectedSections = setOf("income", "expense", "transfer")),
                transactions = filtered,
                accounts = accounts,
                categories = categories,
                userPreferences = userPreferences
            )
            sections.addAll(txDoc.sections)
        }

        return PrintableReportDocument(
            documentTitle = "گزارش تحلیلی و آمار مالی",
            subtitle = "تحلیل عملکرد درآمد، هزینه و دسته‌بندی‌ها",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = kpis,
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 3. ACCOUNTS DOCUMENT
    // ------------------------------------------------------------------
    fun buildAccountsDocument(
        payload: PrintConfirmationPayload,
        accounts: List<AccountEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val activeAccounts = accounts.filter { it.isActive }
        val inactiveAccounts = accounts.filter { !it.isActive }
        val totalBalance = activeAccounts.sumOf { it.balance }

        val targetAccounts = buildList {
            if ("active_accounts" in payload.selectedSections) addAll(activeAccounts)
            if ("inactive_accounts" in payload.selectedSections) addAll(inactiveAccounts)
        }
        val showCardDetails = "card_details" in payload.selectedSections

        val kpis = listOf(
            PrintKpiItem("موجودی کل حساب‌های فعال", fmtMoney(totalBalance, currency), PrintTone.POSITIVE),
            PrintKpiItem("تعداد حساب‌های فعال", "${fmtNum(activeAccounts.size, currency)} حساب", PrintTone.INFO),
            PrintKpiItem("تعداد کل کارت و حساب", "${fmtNum(accounts.size, currency)} مورد", PrintTone.NEUTRAL)
        )

        val headers = buildList {
            add("ردیف")
            add("نام حساب")
            add("نوع")
            add("بانک")
            if (showCardDetails) {
                add("شماره کارت")
                add("شماره حساب / شبا")
            }
            add("موجودی")
        }

        val rows = targetAccounts.mapIndexed { idx, acc ->
            val cardFormatted = if (acc.cardNumber.isNotBlank()) {
                fmtTextDigits(acc.cardNumber.chunked(4).joinToString("-"), currency)
            } else "—"
            val accSheba = buildList {
                if (acc.accountNumber.isNotBlank()) add("حساب: ${fmtTextDigits(acc.accountNumber, currency)}")
                if (acc.shebaNumber.isNotBlank()) add("شبا: ${fmtTextDigits(acc.shebaNumber, currency)}")
            }.joinToString(" | ").ifBlank { "—" }

            buildList {
                add(PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true))
                add(PrintCell(fmtTextDigits(acc.name, currency) + if (!acc.isActive) " (غیرفعال)" else "", isBold = true))
                add(PrintCell(acc.type.title))
                add(PrintCell(acc.bankName.ifBlank { "—" }))
                if (showCardDetails) {
                    add(PrintCell(cardFormatted, isNumericOrDate = true))
                    add(PrintCell(accSheba, isNumericOrDate = true))
                }
                add(
                    PrintCell(
                        fmtMoney(acc.balance, currency),
                        isNumericOrDate = true,
                        isBold = true,
                        tone = if (acc.balance >= 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE
                    )
                )
            }
        }

        return PrintableReportDocument(
            documentTitle = "گزارش حساب‌ها و کارت‌های بانکی",
            subtitle = "فهرست موجودی و مشخصات حساب‌های مالی",
            dateRangeLabel = "وضعیت فعلی",
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = kpis,
            sections = listOf(
                PrintSectionBlock(
                    title = "لیست حساب‌ها و موجودی‌ها",
                    badge = "${fmtNum(rows.size, currency)} حساب",
                    headers = headers,
                    rows = rows,
                    footerSummary = "جمع کل موجودی حساب‌های درج‌شده: ${fmtMoney(targetAccounts.sumOf { it.balance }, currency)}"
                )
            )
        )
    }

    // ------------------------------------------------------------------
    // 4. ACCOUNT DETAIL STATEMENT
    // ------------------------------------------------------------------
    fun buildAccountDetailDocument(
        payload: PrintConfirmationPayload,
        account: AccountEntity,
        transactions: List<TransactionEntity>,
        allAccounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val sections = mutableListOf<PrintSectionBlock>()

        if ("account_info" in payload.selectedSections) {
            val infoRows = listOf(
                listOf(PrintCell("نام حساب", isBold = true), PrintCell(fmtTextDigits(account.name, currency))),
                listOf(PrintCell("نوع و بانک", isBold = true), PrintCell("${account.type.title} ${if (account.bankName.isNotBlank()) "— ${account.bankName}" else ""}")),
                listOf(PrintCell("شماره کارت", isBold = true), PrintCell(if (account.cardNumber.isNotBlank()) fmtTextDigits(account.cardNumber.chunked(4).joinToString("-"), currency) else "—", isNumericOrDate = true)),
                listOf(PrintCell("شماره حساب / شبا", isBold = true), PrintCell(listOfNotNull(account.accountNumber.takeIf { it.isNotBlank() }, account.shebaNumber.takeIf { it.isNotBlank() }).joinToString(" | ").ifBlank { "—" }, isNumericOrDate = true)),
                listOf(PrintCell("موجودی فعلی", isBold = true), PrintCell(fmtMoney(account.balance, currency), isNumericOrDate = true, isBold = true, tone = PrintTone.POSITIVE))
            )
            sections.add(
                PrintSectionBlock(
                    title = "مشخصات حساب",
                    badge = account.type.title,
                    headers = listOf("عنوان", "اطلاعات"),
                    rows = infoRows
                )
            )
        }

        val txPayload = payload.copy(
            selectedSections = buildSet {
                if ("income" in payload.selectedSections) add("income")
                if ("expense" in payload.selectedSections) add("expense")
                if ("transfer" in payload.selectedSections) add("transfer")
            }
        )
        val txDoc = buildTransactionsDocument(
            payload = txPayload,
            transactions = transactions,
            accounts = allAccounts,
            categories = categories,
            userPreferences = userPreferences,
            titleOverride = "صورت‌حساب ${account.name}",
            subtitleOverride = "گردش و جزئیات حساب ${account.bankName.ifBlank { account.type.title }}"
        )
        if (txPayload.selectedSections.isNotEmpty()) {
            sections.addAll(txDoc.sections)
        }

        return PrintableReportDocument(
            documentTitle = "صورت‌حساب ${account.name}",
            subtitle = "مشخصات و گردش مالی حساب",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("موجودی فعلی حساب", fmtMoney(account.balance, currency), PrintTone.INFO)
            ) + txDoc.kpis.take(3),
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 5. DEBTS & RECEIVABLES DOCUMENT
    // ------------------------------------------------------------------
    fun buildDebtsDocument(
        payload: PrintConfirmationPayload,
        debts: List<DebtEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val nonChecks = debts.filter { !it.isCheck }

        val includeActive = "active_only" in payload.selectedSections
        val includeSettled = "settled_only" in payload.selectedSections

        fun filterByStatusAndDate(list: List<DebtEntity>): List<DebtEntity> {
            return list.filter { d ->
                val isSettled = d.status == DebtStatus.SETTLED || (d.totalAmount > 0L && d.paidAmount >= d.totalAmount)
                val statusOk = (includeActive && !isSettled) || (includeSettled && isSettled)
                val refDate = d.dueDateMillis ?: d.startDateMillis
                statusOk && inRange(refDate, payload.startMillis, payload.endMillis)
            }.sortedBy { it.dueDateMillis ?: Long.MAX_VALUE }
        }

        val myDebts = if ("debts_owe" in payload.selectedSections) {
            filterByStatusAndDate(nonChecks.filter { it.type == DebtType.DEBT_I_OWE })
        } else emptyList()

        val myReceivables = if ("receivables" in payload.selectedSections) {
            filterByStatusAndDate(nonChecks.filter { it.type == DebtType.RECEIVABLE_OWED_TO_ME })
        } else emptyList()

        val totalDebtRem = myDebts.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0L) }
        val totalRecRem = myReceivables.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0L) }

        val kpis = buildList {
            if ("debts_owe" in payload.selectedSections) {
                add(PrintKpiItem("مانده بدهی‌ها", fmtMoney(totalDebtRem, currency), PrintTone.NEGATIVE))
            }
            if ("receivables" in payload.selectedSections) {
                add(PrintKpiItem("مانده طلب‌ها", fmtMoney(totalRecRem, currency), PrintTone.POSITIVE))
            }
            add(
                PrintKpiItem(
                    "تراز طلب و بدهی",
                    fmtMoney(totalRecRem - totalDebtRem, currency, showSign = true),
                    if (totalRecRem >= totalDebtRem) PrintTone.POSITIVE else PrintTone.NEGATIVE
                )
            )
        }

        fun buildTableSection(title: String, items: List<DebtEntity>, isDebt: Boolean): PrintSectionBlock {
            val headers = buildList {
                add("ردیف")
                add("شخص / شرکت")
                add("تاریخ ثبت")
                add("سررسید")
                add("کل مبلغ")
                add("پرداخت/وصول‌شده")
                add("مانده")
                add("وضعیت")
                if (payload.includeNotes) add("توضیحات")
            }
            val rows = items.mapIndexed { idx, d ->
                val rem = (d.totalAmount - d.paidAmount).coerceAtLeast(0L)
                val isSettled = d.status == DebtStatus.SETTLED || rem == 0L
                val tone = if (isSettled) PrintTone.POSITIVE else if (isDebt) PrintTone.NEGATIVE else PrintTone.INFO
                buildList {
                    add(PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true))
                    add(PrintCell(fmtTextDigits(d.personOrCompanyName, currency), isBold = true))
                    add(PrintCell(fmtDate(d.startDateMillis, currency), isNumericOrDate = true))
                    add(PrintCell(fmtDate(d.dueDateMillis, currency), isNumericOrDate = true))
                    add(PrintCell(fmtMoney(d.totalAmount, currency), isNumericOrDate = true))
                    add(PrintCell(fmtMoney(d.paidAmount, currency), isNumericOrDate = true, tone = PrintTone.POSITIVE))
                    add(PrintCell(fmtMoney(rem, currency), isNumericOrDate = true, isBold = true, tone = tone))
                    add(PrintCell(if (isSettled) "تسویه‌شده" else d.status.title, tone = tone))
                    if (payload.includeNotes) {
                        add(PrintCell(fmtTextDigits(d.notes.ifBlank { "—" }, currency)))
                    }
                }
            }
            val sumTotal = items.sumOf { it.totalAmount }
            val sumRem = items.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0L) }
            return PrintSectionBlock(
                title = title,
                badge = "${fmtNum(items.size, currency)} مورد",
                headers = headers,
                rows = rows,
                footerSummary = "جمع کل: ${fmtMoney(sumTotal, currency)}  |  مانده باقیمانده: ${fmtMoney(sumRem, currency)}"
            )
        }

        val sections = buildList {
            if ("debts_owe" in payload.selectedSections) {
                add(buildTableSection("فهرست بدهی‌های من", myDebts, isDebt = true))
            }
            if ("receivables" in payload.selectedSections) {
                add(buildTableSection("فهرست طلب‌های من", myReceivables, isDebt = false))
            }
        }

        return PrintableReportDocument(
            documentTitle = "گزارش بدهی‌ها و طلب‌ها",
            subtitle = "وضعیت تسویه و سررسید بدهی‌ها و مطالبات",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = kpis,
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 6. DEBT DETAIL DOCUMENT
    // ------------------------------------------------------------------
    fun buildDebtDetailDocument(
        payload: PrintConfirmationPayload,
        debt: DebtEntity,
        payments: List<DebtPaymentEntity>,
        accounts: List<AccountEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val accountMap = accounts.associateBy { it.id }
        val rem = (debt.totalAmount - debt.paidAmount).coerceAtLeast(0L)
        val filteredPayments = payments
            .filter { inRange(it.dateMillis, payload.startMillis, payload.endMillis) }
            .sortedByDescending { it.dateMillis }

        val sections = mutableListOf<PrintSectionBlock>()
        if ("debt_summary" in payload.selectedSections) {
            val summaryRows = listOf(
                listOf(PrintCell("طرف حساب", isBold = true), PrintCell(fmtTextDigits(debt.personOrCompanyName, currency), isBold = true)),
                listOf(PrintCell("نوع پرونده", isBold = true), PrintCell(if (debt.isCheck) "چک (${debt.type.title})" else debt.type.title)),
                listOf(PrintCell("تاریخ ثبت / سررسید", isBold = true), PrintCell("${fmtDate(debt.startDateMillis, currency)}  تا  ${fmtDate(debt.dueDateMillis, currency)}", isNumericOrDate = true)),
                listOf(PrintCell("مبلغ کل", isBold = true), PrintCell(fmtMoney(debt.totalAmount, currency), isNumericOrDate = true)),
                listOf(PrintCell("مبلغ پرداخت / وصول‌شده", isBold = true), PrintCell(fmtMoney(debt.paidAmount, currency), isNumericOrDate = true, tone = PrintTone.POSITIVE)),
                listOf(PrintCell("مانده باقیمانده", isBold = true), PrintCell(fmtMoney(rem, currency), isNumericOrDate = true, isBold = true, tone = if (rem == 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE))
            )
            sections.add(
                PrintSectionBlock(
                    title = "مشخصات پرونده",
                    badge = if (rem == 0L) "تسویه‌شده" else debt.status.title,
                    headers = listOf("عنوان", "مقدار"),
                    rows = summaryRows
                )
            )
        }

        if ("payments" in payload.selectedSections) {
            val headers = buildList {
                add("ردیف")
                add("تاریخ")
                add("حساب")
                add("مبلغ")
                if (payload.includeNotes) add("یادداشت")
            }
            val rows = filteredPayments.mapIndexed { idx, p ->
                buildList {
                    add(PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true))
                    add(PrintCell(fmtDate(p.dateMillis, currency), isNumericOrDate = true))
                    add(PrintCell(p.accountId?.let { accountMap[it]?.name } ?: "—"))
                    add(PrintCell(fmtMoney(p.amount, currency), isNumericOrDate = true, isBold = true, tone = PrintTone.POSITIVE))
                    if (payload.includeNotes) add(PrintCell(fmtTextDigits(p.notes.ifBlank { "—" }, currency)))
                }
            }
            sections.add(
                PrintSectionBlock(
                    title = "تاریخچه پرداخت‌ها / وصول‌ها",
                    badge = "${fmtNum(rows.size, currency)} تراکنش",
                    headers = headers,
                    rows = rows,
                    footerSummary = "جمع پرداختی‌های این بازه: ${fmtMoney(filteredPayments.sumOf { it.amount }, currency)}"
                )
            )
        }

        return PrintableReportDocument(
            documentTitle = "پرونده ${debt.personOrCompanyName}",
            subtitle = if (debt.isCheck) "گزارش چک و تاریخچه تسویه" else "گزارش جزئیات ${debt.type.title}",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("مبلغ کل", fmtMoney(debt.totalAmount, currency), PrintTone.INFO),
                PrintKpiItem("پرداخت‌شده", fmtMoney(debt.paidAmount, currency), PrintTone.POSITIVE),
                PrintKpiItem("مانده", fmtMoney(rem, currency), if (rem == 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE)
            ),
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 7. CHECKS DOCUMENT
    // ------------------------------------------------------------------
    fun buildChecksDocument(
        payload: PrintConfirmationPayload,
        debts: List<DebtEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val allChecks = debts.filter {
            it.isCheck || it.personOrCompanyName.contains("چک") || it.notes.contains("چک")
        }

        val includePending = "pending_checks" in payload.selectedSections
        val includeSettled = "settled_checks" in payload.selectedSections

        val filtered = allChecks.filter { c ->
            val isSettled = c.status == DebtStatus.SETTLED || (c.totalAmount > 0L && c.paidAmount >= c.totalAmount)
            val statusOk = (includePending && !isSettled) || (includeSettled && isSettled)
            val refDate = c.dueDateMillis ?: c.startDateMillis
            statusOk && inRange(refDate, payload.startMillis, payload.endMillis)
        }.sortedBy { it.dueDateMillis ?: Long.MAX_VALUE }

        val totalAll = filtered.sumOf { it.totalAmount }
        val totalPaid = filtered.sumOf { it.paidAmount.coerceAtMost(it.totalAmount) }
        val totalRem = (totalAll - totalPaid).coerceAtLeast(0L)

        val headers = buildList {
            add("ردیف")
            add("شماره / عنوان چک")
            add("نوع")
            add("سررسید")
            add("مبلغ چک")
            add("مانده")
            add("وضعیت")
            if (payload.includeNotes) add("توضیحات")
        }
        val rows = filtered.mapIndexed { idx, c ->
            val rem = (c.totalAmount - c.paidAmount).coerceAtLeast(0L)
            val isSettled = c.status == DebtStatus.SETTLED || rem == 0L
            val isIncoming = c.type == DebtType.RECEIVABLE_OWED_TO_ME
            val title = buildString {
                append(c.personOrCompanyName)
                if (c.checkNumber.isNotBlank()) append(" (شماره ${c.checkNumber})")
            }
            buildList {
                add(PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true))
                add(PrintCell(fmtTextDigits(title, currency), isBold = true))
                add(PrintCell(if (isIncoming) "دریافتی" else "پرداختی", tone = if (isIncoming) PrintTone.POSITIVE else PrintTone.NEGATIVE))
                add(PrintCell(fmtDate(c.dueDateMillis, currency), isNumericOrDate = true))
                add(PrintCell(fmtMoney(c.totalAmount, currency), isNumericOrDate = true))
                add(PrintCell(fmtMoney(rem, currency), isNumericOrDate = true, isBold = true, tone = if (isSettled) PrintTone.POSITIVE else PrintTone.WARNING))
                add(PrintCell(if (isSettled) "پاس‌شده" else "در جریان", tone = if (isSettled) PrintTone.POSITIVE else PrintTone.WARNING))
                if (payload.includeNotes) add(PrintCell(fmtTextDigits(c.notes.ifBlank { "—" }, currency)))
            }
        }

        return PrintableReportDocument(
            documentTitle = "گزارش چک‌ها",
            subtitle = "فهرست چک‌های دریافتی و پرداختی و تاریخ سررسید",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("جمع مبلغ چک‌ها", fmtMoney(totalAll, currency), PrintTone.INFO),
                PrintKpiItem("مبلغ پاس‌شده", fmtMoney(totalPaid, currency), PrintTone.POSITIVE),
                PrintKpiItem("مانده در جریان", fmtMoney(totalRem, currency), PrintTone.WARNING)
            ),
            sections = listOf(
                PrintSectionBlock(
                    title = "فهرست چک‌ها",
                    badge = "${fmtNum(rows.size, currency)} فقره",
                    headers = headers,
                    rows = rows,
                    footerSummary = "جمع مبلغ چک‌ها: ${fmtMoney(totalAll, currency)}  |  مانده: ${fmtMoney(totalRem, currency)}"
                )
            )
        )
    }

    // ------------------------------------------------------------------
    // 8. INSTALLMENTS HUB & DETAIL DOCUMENTS
    // ------------------------------------------------------------------
    fun buildInstallmentsDocument(
        payload: PrintConfirmationPayload,
        plans: List<InstallmentPlanEntity>,
        allInstallments: List<InstallmentEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val plansMap = plans.associateBy { it.id }

        val includeUnpaid = "unpaid" in payload.selectedSections
        val includePaid = "paid" in payload.selectedSections
        val includePlansSummary = "plans_summary" in payload.selectedSections

        val filteredInsts = allInstallments.filter { inst ->
            inst.planId in plansMap &&
                inRange(inst.dueDateMillis, payload.startMillis, payload.endMillis) &&
                ((includeUnpaid && !inst.isPaid) || (includePaid && inst.isPaid))
        }.sortedWith(compareBy<InstallmentEntity> { it.isPaid }.thenBy { it.dueDateMillis })

        val totalDue = filteredInsts.sumOf { it.amount }
        val totalPaidAmt = filteredInsts.filter { it.isPaid }.sumOf { it.amount }
        val totalUnpaidAmt = (totalDue - totalPaidAmt).coerceAtLeast(0L)

        val sections = mutableListOf<PrintSectionBlock>()

        if (includeUnpaid || includePaid) {
            val instRows = filteredInsts.mapIndexed { idx, inst ->
                val plan = plansMap[inst.planId]
                val title = plan?.title ?: "طرح قسط"
                val creditor = plan?.creditorOrCompany?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                val qNum = if (plan != null) "${fmtNum(inst.installmentIndex, currency)} از ${fmtNum(plan.numberOfInstallments, currency)}" else fmtNum(inst.installmentIndex, currency)
                listOf(
                    PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true),
                    PrintCell(fmtTextDigits("$title$creditor", currency), isBold = true),
                    PrintCell(qNum, isNumericOrDate = true),
                    PrintCell(fmtDate(inst.dueDateMillis, currency), isNumericOrDate = true),
                    PrintCell(fmtMoney(inst.amount, currency), isNumericOrDate = true, isBold = true, tone = if (inst.isPaid) PrintTone.POSITIVE else PrintTone.NEGATIVE),
                    PrintCell(if (inst.isPaid) "پرداخت‌شده (${fmtDate(inst.paidDateMillis, currency)})" else "در انتظار پرداخت", tone = if (inst.isPaid) PrintTone.POSITIVE else PrintTone.WARNING)
                )
            }
            sections.add(
                PrintSectionBlock(
                    title = "جدول سررسید اقساط",
                    badge = "${fmtNum(instRows.size, currency)} قسط",
                    headers = listOf("ردیف", "عنوان وام / قسط", "شماره قسط", "تاریخ سررسید", "مبلغ قسط", "وضعیت"),
                    rows = instRows,
                    footerSummary = "جمع اقساط بازه: ${fmtMoney(totalDue, currency)}  |  پرداخت‌نشده: ${fmtMoney(totalUnpaidAmt, currency)}"
                )
            )
        }

        if (includePlansSummary) {
            val planRows = plans.mapIndexed { idx, p ->
                val pInsts = allInstallments.filter { it.planId == p.id }
                val totalP = if (pInsts.isNotEmpty()) pInsts.sumOf { it.amount } else p.numberOfInstallments * p.installmentAmount
                val paidP = if (pInsts.isNotEmpty()) pInsts.filter { it.isPaid }.sumOf { it.amount } else p.paidInstallments * p.installmentAmount
                val remP = (totalP - paidP).coerceAtLeast(0L)
                val paidCount = if (pInsts.isNotEmpty()) pInsts.count { it.isPaid } else p.paidInstallments
                listOf(
                    PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true),
                    PrintCell(fmtTextDigits(p.title, currency), isBold = true),
                    PrintCell(p.creditorOrCompany.ifBlank { "—" }),
                    PrintCell("${fmtNum(paidCount, currency)} / ${fmtNum(p.numberOfInstallments, currency)}", isNumericOrDate = true),
                    PrintCell(fmtMoney(totalP, currency), isNumericOrDate = true),
                    PrintCell(fmtMoney(remP, currency), isNumericOrDate = true, isBold = true, tone = if (remP == 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE)
                )
            }
            sections.add(
                PrintSectionBlock(
                    title = "خلاصه وضعیت طرح‌های وام و قسط",
                    badge = "${fmtNum(planRows.size, currency)} طرح",
                    headers = listOf("ردیف", "عنوان طرح", "مرجع / بانک", "تعداد پرداختی", "کل بازپرداخت", "مانده"),
                    rows = planRows
                )
            )
        }

        return PrintableReportDocument(
            documentTitle = "گزارش اقساط و تسهیلات",
            subtitle = "فهرست اقساط سررسیددار و وضعیت طرح‌های وام",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("جمع اقساط بازه", fmtMoney(totalDue, currency), PrintTone.INFO),
                PrintKpiItem("پرداخت‌شده", fmtMoney(totalPaidAmt, currency), PrintTone.POSITIVE),
                PrintKpiItem("باقیمانده در انتظار", fmtMoney(totalUnpaidAmt, currency), PrintTone.NEGATIVE)
            ),
            sections = sections
        )
    }

    fun buildInstallmentDetailDocument(
        payload: PrintConfirmationPayload,
        plan: InstallmentPlanEntity,
        installments: List<InstallmentEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val includeUnpaid = "unpaid" in payload.selectedSections
        val includePaid = "paid" in payload.selectedSections
        val sections = mutableListOf<PrintSectionBlock>()

        val totalPlanAmt = if (installments.isNotEmpty()) installments.sumOf { it.amount } else plan.totalAmount
        val paidAmt = installments.filter { it.isPaid }.sumOf { it.amount }
        val remAmt = (totalPlanAmt - paidAmt).coerceAtLeast(0L)

        if ("plan_info" in payload.selectedSections) {
            sections.add(
                PrintSectionBlock(
                    title = "مشخصات طرح قسط / وام",
                    badge = if (remAmt == 0L) "تسویه‌شده" else "جاری",
                    headers = listOf("مشخصه", "اطلاعات"),
                    rows = listOf(
                        listOf(PrintCell("عنوان طرح", isBold = true), PrintCell(fmtTextDigits(plan.title, currency), isBold = true)),
                        listOf(PrintCell("بانک / مرجع", isBold = true), PrintCell(plan.creditorOrCompany.ifBlank { "—" })),
                        listOf(PrintCell("تاریخ شروع", isBold = true), PrintCell(fmtDate(plan.startDateMillis, currency), isNumericOrDate = true)),
                        listOf(PrintCell("تعداد کل اقساط", isBold = true), PrintCell("${fmtNum(plan.numberOfInstallments, currency)} قسط", isNumericOrDate = true)),
                        listOf(PrintCell("مبلغ کل بازپرداخت", isBold = true), PrintCell(fmtMoney(totalPlanAmt, currency), isNumericOrDate = true))
                    )
                )
            )
        }

        if (includeUnpaid || includePaid) {
            val filtered = installments.filter { inst ->
                inRange(inst.dueDateMillis, payload.startMillis, payload.endMillis) &&
                    ((includeUnpaid && !inst.isPaid) || (includePaid && inst.isPaid))
            }.sortedBy { it.installmentIndex }

            val rows = filtered.map { inst ->
                listOf(
                    PrintCell(fmtNum(inst.installmentIndex, currency), isNumericOrDate = true, isBold = true),
                    PrintCell(fmtDate(inst.dueDateMillis, currency), isNumericOrDate = true),
                    PrintCell(fmtMoney(inst.amount, currency), isNumericOrDate = true, isBold = true, tone = if (inst.isPaid) PrintTone.POSITIVE else PrintTone.NEGATIVE),
                    PrintCell(if (inst.isPaid) "پرداخت‌شده" else "پرداخت‌نشده", tone = if (inst.isPaid) PrintTone.POSITIVE else PrintTone.WARNING),
                    PrintCell(fmtDate(inst.paidDateMillis, currency), isNumericOrDate = true)
                )
            }
            sections.add(
                PrintSectionBlock(
                    title = "ریز اقساط طرح",
                    badge = "${fmtNum(rows.size, currency)} قسط",
                    headers = listOf("شماره قسط", "تاریخ سررسید", "مبلغ قسط", "وضعیت", "تاریخ پرداخت"),
                    rows = rows,
                    footerSummary = "جمع پرداخت‌شده: ${fmtMoney(paidAmt, currency)}  |  مانده: ${fmtMoney(remAmt, currency)}"
                )
            )
        }

        return PrintableReportDocument(
            documentTitle = "دفترچه اقساط ${plan.title}",
            subtitle = plan.creditorOrCompany.ifBlank { "جزئیات کامل بازپرداخت اقساط" },
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("کل بازپرداخت", fmtMoney(totalPlanAmt, currency), PrintTone.INFO),
                PrintKpiItem("پرداخت‌شده", fmtMoney(paidAmt, currency), PrintTone.POSITIVE),
                PrintKpiItem("مانده", fmtMoney(remAmt, currency), if (remAmt == 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE)
            ),
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 9. FINANCIAL CALENDAR DOCUMENT
    // ------------------------------------------------------------------
    fun buildCalendarDocument(
        payload: PrintConfirmationPayload,
        transactions: List<TransactionEntity>,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>,
        debts: List<DebtEntity>,
        installmentPlans: List<InstallmentPlanEntity>,
        allInstallments: List<InstallmentEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val sections = mutableListOf<PrintSectionBlock>()

        val filteredTxs = transactions.filter { inRange(it.dateMillis, payload.startMillis, payload.endMillis) }
        val inc = filteredTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val exp = filteredTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        if ("transactions" in payload.selectedSections) {
            val txDoc = buildTransactionsDocument(
                payload = payload.copy(selectedSections = setOf("income", "expense", "transfer")),
                transactions = filteredTxs,
                accounts = accounts,
                categories = categories,
                userPreferences = userPreferences
            )
            sections.addAll(txDoc.sections)
        }

        if ("installments" in payload.selectedSections) {
            val instDoc = buildInstallmentsDocument(
                payload = payload.copy(selectedSections = setOf("unpaid", "paid")),
                plans = installmentPlans,
                allInstallments = allInstallments,
                userPreferences = userPreferences
            )
            sections.addAll(instDoc.sections)
        }

        if ("checks_debts" in payload.selectedSections) {
            val dueDebts = debts.filter { d ->
                val refDate = d.dueDateMillis ?: d.startDateMillis
                inRange(refDate, payload.startMillis, payload.endMillis)
            }.sortedBy { it.dueDateMillis ?: Long.MAX_VALUE }

            val rows = dueDebts.mapIndexed { idx, d ->
                val rem = (d.totalAmount - d.paidAmount).coerceAtLeast(0L)
                val isSettled = d.status == DebtStatus.SETTLED || rem == 0L
                listOf(
                    PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true),
                    PrintCell(if (d.isCheck) "چک (${d.type.title})" else d.type.title),
                    PrintCell(fmtTextDigits(d.personOrCompanyName, currency), isBold = true),
                    PrintCell(fmtDate(d.dueDateMillis ?: d.startDateMillis, currency), isNumericOrDate = true),
                    PrintCell(fmtMoney(d.totalAmount, currency), isNumericOrDate = true),
                    PrintCell(fmtMoney(rem, currency), isNumericOrDate = true, isBold = true, tone = if (isSettled) PrintTone.POSITIVE else PrintTone.WARNING)
                )
            }
            sections.add(
                PrintSectionBlock(
                    title = "سررسید چک‌ها، بدهی‌ها و طلب‌های بازه",
                    badge = "${fmtNum(rows.size, currency)} مورد",
                    headers = listOf("ردیف", "نوع", "شرح / طرف حساب", "تاریخ", "کل مبلغ", "مانده"),
                    rows = rows
                )
            )
        }

        return PrintableReportDocument(
            documentTitle = "گزارش تقویم مالی و سررسیدها",
            subtitle = "تراکنش‌ها و تعهدات سررسیددار در بازه انتخابی",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("درآمد بازه", fmtMoney(inc, currency), PrintTone.POSITIVE),
                PrintKpiItem("هزینه بازه", fmtMoney(exp, currency), PrintTone.NEGATIVE),
                PrintKpiItem("اقساط در بازه", "${fmtNum(allInstallments.count { inRange(it.dueDateMillis, payload.startMillis, payload.endMillis) }, currency)} قسط", PrintTone.INFO)
            ),
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 10. BUDGETS DOCUMENT
    // ------------------------------------------------------------------
    fun buildBudgetsDocument(
        payload: PrintConfirmationPayload,
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>,
        monthlyExpenses: List<TransactionEntity>,
        monthTitle: String,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val categoryMap = categories.associateBy { it.id }
        val totalBudget = budgets.sumOf { it.monthlyAmount }
        val totalSpent = budgets.sumOf { b ->
            monthlyExpenses.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
        }
        val sections = mutableListOf<PrintSectionBlock>()

        if ("budgets_status" in payload.selectedSections) {
            val rows = budgets.mapIndexed { idx, b ->
                val catName = categoryMap[b.categoryId]?.name ?: "دسته‌بندی"
                val spent = monthlyExpenses.filter { it.categoryId == b.categoryId }.sumOf { it.amount }
                val rem = b.monthlyAmount - spent
                val percent = if (b.monthlyAmount > 0L) ((spent.toDouble() / b.monthlyAmount.toDouble()) * 100).toInt() else 0
                val tone = when {
                    percent >= 100 -> PrintTone.NEGATIVE
                    percent >= b.warningThresholdPercent -> PrintTone.WARNING
                    else -> PrintTone.POSITIVE
                }
                listOf(
                    PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true),
                    PrintCell(catName, isBold = true),
                    PrintCell(fmtMoney(b.monthlyAmount, currency), isNumericOrDate = true),
                    PrintCell(fmtMoney(spent, currency), isNumericOrDate = true, tone = tone),
                    PrintCell(fmtMoney(rem, currency, showSign = true), isNumericOrDate = true, isBold = true, tone = if (rem >= 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE),
                    PrintCell(PersianNumberFormatter.formatPercent(percent, currency.isEnglishDigits), isNumericOrDate = true, tone = tone)
                )
            }
            sections.add(
                PrintSectionBlock(
                    title = "وضعیت بودجه‌های ماهانه",
                    badge = "${fmtNum(rows.size, currency)} ردیف",
                    headers = listOf("ردیف", "دسته‌بندی", "سقف بودجه", "مصرف‌شده", "باقیمانده", "درصد مصرف"),
                    rows = rows,
                    footerSummary = "کل بودجه: ${fmtMoney(totalBudget, currency)}  |  جمع مصرفی: ${fmtMoney(totalSpent, currency)}"
                )
            )
        }

        if ("expenses_detail" in payload.selectedSections) {
            val budgetCatIds = budgets.map { it.categoryId }.toSet()
            val detailRows = monthlyExpenses.filter { it.categoryId in budgetCatIds }
                .sortedByDescending { it.dateMillis }
                .mapIndexed { idx, tx ->
                    listOf(
                        PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true),
                        PrintCell(fmtDate(tx.dateMillis, currency), isNumericOrDate = true),
                        PrintCell(tx.categoryId?.let { categoryMap[it]?.name } ?: "—"),
                        PrintCell(fmtTextDigits(tx.title.ifBlank { "هزینه" }, currency), isBold = true),
                        PrintCell(fmtMoney(tx.amount, currency), isNumericOrDate = true, tone = PrintTone.NEGATIVE)
                    )
                }
            sections.add(
                PrintSectionBlock(
                    title = "ریز هزینه‌های مرتبط با بودجه‌ها",
                    badge = "${fmtNum(detailRows.size, currency)} تراکنش",
                    headers = listOf("ردیف", "تاریخ", "دسته‌بندی", "عنوان", "مبلغ"),
                    rows = detailRows
                )
            )
        }

        return PrintableReportDocument(
            documentTitle = "گزارش بودجه‌بندی ($monthTitle)",
            subtitle = "کنترل سقف هزینه و مصرف ماهانه دسته‌بندی‌ها",
            dateRangeLabel = monthTitle,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("کل بودجه تعریف‌شده", fmtMoney(totalBudget, currency), PrintTone.INFO),
                PrintKpiItem("مصرف‌شده تا امروز", fmtMoney(totalSpent, currency), PrintTone.WARNING),
                PrintKpiItem("باقیمانده کل", fmtMoney(totalBudget - totalSpent, currency, showSign = true), if (totalBudget >= totalSpent) PrintTone.POSITIVE else PrintTone.NEGATIVE)
            ),
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 11. GOALS DOCUMENT
    // ------------------------------------------------------------------
    fun buildGoalsDocument(
        payload: PrintConfirmationPayload,
        goals: List<FinancialGoalEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val includeActive = "active_goals" in payload.selectedSections
        val includeCompleted = "completed_goals" in payload.selectedSections

        val filtered = goals.filter { g ->
            val done = g.isCompleted || (g.targetAmount > 0L && g.currentAmount >= g.targetAmount)
            (includeActive && !done) || (includeCompleted && done)
        }

        val totalTarget = filtered.sumOf { it.targetAmount }
        val totalSaved = filtered.sumOf { it.currentAmount }

        val headers = buildList {
            add("ردیف")
            add("عنوان هدف")
            add("مبلغ هدف")
            add("پس‌انداز فعلی")
            add("مانده")
            add("پیشرفت")
            add("سررسید")
            if (payload.includeNotes) add("توضیحات")
        }
        val rows = filtered.mapIndexed { idx, g ->
            val rem = (g.targetAmount - g.currentAmount).coerceAtLeast(0L)
            val pct = if (g.targetAmount > 0L) ((g.currentAmount.toDouble() / g.targetAmount.toDouble()) * 100).toInt().coerceIn(0, 100) else 0
            val done = g.isCompleted || rem == 0L
            buildList {
                add(PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true))
                add(PrintCell(fmtTextDigits(g.title, currency), isBold = true))
                add(PrintCell(fmtMoney(g.targetAmount, currency), isNumericOrDate = true))
                add(PrintCell(fmtMoney(g.currentAmount, currency), isNumericOrDate = true, tone = PrintTone.POSITIVE))
                add(PrintCell(fmtMoney(rem, currency), isNumericOrDate = true, tone = if (done) PrintTone.POSITIVE else PrintTone.INFO))
                add(PrintCell(PersianNumberFormatter.formatPercent(pct, currency.isEnglishDigits), isNumericOrDate = true, isBold = true, tone = if (done) PrintTone.POSITIVE else PrintTone.INFO))
                add(PrintCell(fmtDate(g.deadlineMillis, currency), isNumericOrDate = true))
                if (payload.includeNotes) add(PrintCell(fmtTextDigits(g.note.ifBlank { "—" }, currency)))
            }
        }

        return PrintableReportDocument(
            documentTitle = "گزارش اهداف مالی و پس‌انداز",
            subtitle = "میزان پیشرفت و موجودی اختصاص‌یافته به اهداف",
            dateRangeLabel = "وضعیت فعلی",
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("جمع مبلغ اهداف", fmtMoney(totalTarget, currency), PrintTone.INFO),
                PrintKpiItem("جمع پس‌اندازشده", fmtMoney(totalSaved, currency), PrintTone.POSITIVE),
                PrintKpiItem("مانده تا تحقق", fmtMoney((totalTarget - totalSaved).coerceAtLeast(0L), currency), PrintTone.WARNING)
            ),
            sections = listOf(
                PrintSectionBlock(
                    title = "فهرست اهداف مالی",
                    badge = "${fmtNum(rows.size, currency)} هدف",
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    // ------------------------------------------------------------------
    // 12. ASSETS DOCUMENT
    // ------------------------------------------------------------------
    fun buildAssetsDocument(
        payload: PrintConfirmationPayload,
        assets: List<AssetEntity>,
        netWorth: Long,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val totalAssetsVal = assets.sumOf { it.estimatedValue }
        val sections = mutableListOf<PrintSectionBlock>()

        if ("net_worth_summary" in payload.selectedSections) {
            sections.add(
                PrintSectionBlock(
                    title = "خلاصه وضعیت ثروت و دارایی",
                    badge = "تراز کل",
                    headers = listOf("سرفصل", "مبلغ"),
                    rows = listOf(
                        listOf(PrintCell("ارزش کل دارایی‌های ثبت‌شده", isBold = true), PrintCell(fmtMoney(totalAssetsVal, currency), isNumericOrDate = true, tone = PrintTone.INFO)),
                        listOf(PrintCell("ارزش خالص کل (با احتساب حساب‌ها و بدهی‌ها)", isBold = true), PrintCell(fmtMoney(netWorth, currency), isNumericOrDate = true, isBold = true, tone = if (netWorth >= 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE))
                    )
                )
            )
        }

        if ("assets_list" in payload.selectedSections) {
            val headers = buildList {
                add("ردیف")
                add("نام دارایی")
                add("دسته‌بندی")
                add("ارزش تخمینی")
                add("آخرین بروزرسانی")
                if (payload.includeNotes) add("توضیحات")
            }
            val rows = assets.mapIndexed { idx, a ->
                buildList {
                    add(PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true))
                    add(PrintCell(fmtTextDigits(a.name, currency), isBold = true))
                    add(PrintCell(a.type.title))
                    add(PrintCell(fmtMoney(a.estimatedValue, currency), isNumericOrDate = true, isBold = true, tone = PrintTone.POSITIVE))
                    add(PrintCell(fmtDate(a.lastUpdateMillis, currency), isNumericOrDate = true))
                    if (payload.includeNotes) add(PrintCell(fmtTextDigits(a.notes.ifBlank { "—" }, currency)))
                }
            }
            sections.add(
                PrintSectionBlock(
                    title = "فهرست دارایی‌ها",
                    badge = "${fmtNum(rows.size, currency)} دارایی",
                    headers = headers,
                    rows = rows,
                    footerSummary = "جمع ارزش دارایی‌ها: ${fmtMoney(totalAssetsVal, currency)}"
                )
            )
        }

        return PrintableReportDocument(
            documentTitle = "گزارش دارایی‌ها و ارزش خالص",
            subtitle = "فهرست سرمایه‌ها، اموال و ارزش تخمینی روز",
            dateRangeLabel = "وضعیت فعلی",
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("ارزش کل دارایی‌ها", fmtMoney(totalAssetsVal, currency), PrintTone.INFO),
                PrintKpiItem("ارزش خالص کل", fmtMoney(netWorth, currency), if (netWorth >= 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE),
                PrintKpiItem("تعداد اقلام", "${fmtNum(assets.size, currency)} مورد", PrintTone.NEUTRAL)
            ),
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 13. CHAND ROOZ (چندروز؟) DOCUMENT
    // ------------------------------------------------------------------
    fun buildChandRoozDocument(
        payload: PrintConfirmationPayload,
        records: List<ChandRoozRecordEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val allowedCategories = buildSet {
            if ("mission" in payload.selectedSections) add(ChandRoozCategory.MISSION)
            if ("leave" in payload.selectedSections) add(ChandRoozCategory.LEAVE)
            if ("sick" in payload.selectedSections) add(ChandRoozCategory.SICK_LEAVE)
            if ("bank" in payload.selectedSections) add(ChandRoozCategory.BANK)
            if ("other" in payload.selectedSections) add(ChandRoozCategory.OTHER)
        }

        val filtered = records.filter { rec ->
            rec.category in allowedCategories &&
                (inRange(rec.startDateMillis, payload.startMillis, payload.endMillis) ||
                    inRange(rec.endDateMillis, payload.startMillis, payload.endMillis))
        }.sortedByDescending { it.startDateMillis }

        val totalDays = filtered.sumOf { it.daysCount }
        val missionDays = filtered.filter { it.category == ChandRoozCategory.MISSION }.sumOf { it.daysCount }
        val leaveDays = filtered.filter { it.category == ChandRoozCategory.LEAVE }.sumOf { it.daysCount }
        val sickDays = filtered.filter { it.category == ChandRoozCategory.SICK_LEAVE }.sumOf { it.daysCount }
        val bankCount = filtered.count { it.category == ChandRoozCategory.BANK }

        val kpis = listOf(
            PrintKpiItem("جمع کل روزها", "${fmtNum(totalDays, currency)} روز", PrintTone.INFO),
            PrintKpiItem("ماموریت / مرخصی", "${fmtNum(missionDays, currency)} / ${fmtNum(leaveDays, currency)} روز", PrintTone.POSITIVE),
            PrintKpiItem("استعلاجی / بانک", "${fmtNum(sickDays, currency)} روز / ${fmtNum(bankCount, currency)} مورد", PrintTone.WARNING)
        )

        val headers = buildList {
            add("ردیف")
            add("دسته")
            add("عنوان")
            add("تاریخ / بازه")
            add("تعداد روز")
            add("محل / مقصد")
            if (payload.includeNotes) add("توضیحات")
        }
        val rows = filtered.mapIndexed { idx, rec ->
            val catTitle = if (rec.category == ChandRoozCategory.OTHER && rec.customCategory.isNotBlank()) {
                rec.customCategory
            } else rec.category.title

            val dateText = if (rec.isRange && fmtDate(rec.startDateMillis, currency) != fmtDate(rec.endDateMillis, currency)) {
                "${fmtDate(rec.startDateMillis, currency)} تا ${fmtDate(rec.endDateMillis, currency)}"
            } else {
                fmtDate(rec.startDateMillis, currency)
            }
            buildList {
                add(PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true))
                add(PrintCell(catTitle, isBold = true, tone = PrintTone.INFO))
                add(PrintCell(fmtTextDigits(rec.title, currency), isBold = true))
                add(PrintCell(dateText, isNumericOrDate = true))
                add(PrintCell("${fmtNum(rec.daysCount, currency)} روز", isNumericOrDate = true, isBold = true))
                add(PrintCell(fmtTextDigits(rec.location.ifBlank { "—" }, currency)))
                if (payload.includeNotes) add(PrintCell(fmtTextDigits(rec.note.ifBlank { "—" }, currency)))
            }
        }

        return PrintableReportDocument(
            documentTitle = "گزارش رویدادهای چندروز؟",
            subtitle = "فهرست روزهای ماموریت، مرخصی، استعلاجی و بانک",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = kpis,
            sections = listOf(
                PrintSectionBlock(
                    title = "سوابق ثبت‌شده",
                    badge = "${fmtNum(rows.size, currency)} رویداد",
                    headers = headers,
                    rows = rows,
                    footerSummary = "مجموع کل روزهای انتخاب‌شده: ${fmtNum(totalDays, currency)} روز"
                )
            )
        )
    }

    // ------------------------------------------------------------------
    // 14. CHECKLISTS DOCUMENT
    // ------------------------------------------------------------------
    fun buildChecklistsDocument(
        payload: PrintConfirmationPayload,
        checklists: List<ChecklistEntity>,
        allItems: List<ChecklistItemEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val includeUnchecked = "unchecked" in payload.selectedSections
        val includeChecked = "checked" in payload.selectedSections

        val sections = checklists.mapNotNull { list ->
            val items = allItems.filter { item ->
                item.checklistId == list.id &&
                    ((includeUnchecked && !item.isChecked) || (includeChecked && item.isChecked))
            }
            if (items.isEmpty()) return@mapNotNull null
            val estSum = items.sumOf { it.estimatedAmount }
            val rows = items.mapIndexed { idx, item ->
                listOf(
                    PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true),
                    PrintCell(if (item.isChecked) "✓ انجام‌شده" else "○ در انتظار", tone = if (item.isChecked) PrintTone.POSITIVE else PrintTone.WARNING),
                    PrintCell(fmtTextDigits(item.text, currency), isBold = true),
                    PrintCell(if (item.estimatedAmount > 0L) fmtMoney(item.estimatedAmount, currency) else "—", isNumericOrDate = true)
                )
            }
            PrintSectionBlock(
                title = "${list.title} (${list.category})",
                badge = "${fmtNum(rows.size, currency)} مورد",
                headers = listOf("ردیف", "وضعیت", "شرح مورد", "برآورد هزینه"),
                rows = rows,
                footerSummary = if (estSum > 0L) "جمع برآورد این چک‌لیست: ${fmtMoney(estSum, currency)}" else ""
            )
        }

        val totalEst = checklists.flatMap { l ->
            allItems.filter { it.checklistId == l.id && ((includeUnchecked && !it.isChecked) || (includeChecked && it.isChecked)) }
        }.sumOf { it.estimatedAmount }

        return PrintableReportDocument(
            documentTitle = "گزارش چک‌لیست‌ها",
            subtitle = "فهرست اقلام و برآورد هزینه‌ها",
            dateRangeLabel = "وضعیت فعلی",
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("تعداد چک‌لیست‌ها", "${fmtNum(sections.size, currency)} لیست", PrintTone.INFO),
                PrintKpiItem("جمع برآورد کل", fmtMoney(totalEst, currency), PrintTone.POSITIVE)
            ),
            sections = sections
        )
    }

    // ------------------------------------------------------------------
    // 15. REMINDERS DOCUMENT
    // ------------------------------------------------------------------
    fun buildRemindersDocument(
        payload: PrintConfirmationPayload,
        reminders: List<ReminderEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val includeActive = "active_reminders" in payload.selectedSections
        val includeCompleted = "completed_reminders" in payload.selectedSections

        val filtered = reminders.filter { r ->
            ((includeActive && !r.isCompleted) || (includeCompleted && r.isCompleted)) &&
                inRange(r.dueDateTimeMillis, payload.startMillis, payload.endMillis)
        }.sortedBy { it.dueDateTimeMillis }

        val headers = buildList {
            add("ردیف")
            add("عنوان یادآور")
            add("دسته‌بندی")
            add("تاریخ سررسید")
            add("وضعیت")
            if (payload.includeNotes) add("توضیحات")
        }
        val rows = filtered.mapIndexed { idx, r ->
            buildList {
                add(PrintCell(fmtNum(idx + 1, currency), isNumericOrDate = true))
                add(PrintCell(fmtTextDigits(r.title, currency), isBold = true))
                add(PrintCell(r.targetType.title))
                add(PrintCell(fmtDate(r.dueDateTimeMillis, currency), isNumericOrDate = true))
                add(
                    PrintCell(
                        when {
                            r.isCompleted -> "تکمیل‌شده"
                            r.isEnabled -> "فعال"
                            else -> "غیرفعال"
                        },
                        tone = if (r.isCompleted) PrintTone.POSITIVE else if (r.isEnabled) PrintTone.INFO else PrintTone.NEUTRAL
                    )
                )
                if (payload.includeNotes) add(PrintCell(fmtTextDigits(r.description.ifBlank { "—" }, currency)))
            }
        }

        return PrintableReportDocument(
            documentTitle = "گزارش یادآورها و سررسیدها",
            subtitle = "فهرست یادآورها و هشدارهای مالی زمان‌بندی‌شده",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("تعداد کل موارد", "${fmtNum(rows.size, currency)} یادآور", PrintTone.INFO),
                PrintKpiItem("یادآورهای فعال", "${fmtNum(filtered.count { !it.isCompleted && it.isEnabled }, currency)} مورد", PrintTone.WARNING)
            ),
            sections = listOf(
                PrintSectionBlock(
                    title = "فهرست یادآورها",
                    badge = "${fmtNum(rows.size, currency)} مورد",
                    headers = headers,
                    rows = rows
                )
            )
        )
    }

    // ------------------------------------------------------------------
    // 16. HOME COMPREHENSIVE MULTI-SECTION REPORT
    // ------------------------------------------------------------------
    fun buildHomeMasterDocument(
        payload: PrintConfirmationPayload,
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        debts: List<DebtEntity>,
        installmentPlans: List<InstallmentPlanEntity>,
        allInstallments: List<InstallmentEntity>,
        budgets: List<BudgetEntity>,
        goals: List<FinancialGoalEntity>,
        assets: List<AssetEntity>,
        netWorth: Long,
        chandRoozRecords: List<ChandRoozRecordEntity>,
        userPreferences: UserPreferences
    ): PrintableReportDocument {
        val currency = userPreferences.currency
        val sections = mutableListOf<PrintSectionBlock>()

        val filteredTxs = transactions.filter { inRange(it.dateMillis, payload.startMillis, payload.endMillis) }
        val totalBalance = accounts.filter { it.isActive }.sumOf { it.balance }
        val inc = filteredTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val exp = filteredTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        if ("accounts" in payload.selectedSections) {
            sections.addAll(
                buildAccountsDocument(
                    payload.copy(selectedSections = setOf("active_accounts", "card_details")),
                    accounts,
                    userPreferences
                ).sections
            )
        }
        if ("transactions" in payload.selectedSections) {
            sections.addAll(
                buildTransactionsDocument(
                    payload.copy(selectedSections = setOf("income", "expense", "transfer")),
                    transactions,
                    accounts,
                    categories,
                    userPreferences
                ).sections
            )
        }
        if ("debts" in payload.selectedSections) {
            sections.addAll(
                buildDebtsDocument(
                    payload.copy(selectedSections = setOf("debts_owe", "receivables", "active_only", "settled_only")),
                    debts,
                    userPreferences
                ).sections
            )
        }
        if ("checks" in payload.selectedSections) {
            sections.addAll(
                buildChecksDocument(
                    payload.copy(selectedSections = setOf("pending_checks", "settled_checks")),
                    debts,
                    userPreferences
                ).sections
            )
        }
        if ("installments" in payload.selectedSections) {
            sections.addAll(
                buildInstallmentsDocument(
                    payload.copy(selectedSections = setOf("unpaid", "paid", "plans_summary")),
                    installmentPlans,
                    allInstallments,
                    userPreferences
                ).sections
            )
        }
        if ("budgets" in payload.selectedSections) {
            val currentP = PersianDateHelper.getCurrentPersianDate()
            val startM = PersianDateHelper.getStartOfMonthMillis(currentP.year, currentP.month)
            val endM = PersianDateHelper.getEndOfMonthMillis(currentP.year, currentP.month)
            val monthKey = "${currentP.year}-${currentP.month.toString().padStart(2, '0')}"
            val currentBudgets = budgets.filter { it.monthKey == monthKey }.ifEmpty { budgets }
            val monthExp = transactions.filter { it.type == TransactionType.EXPENSE && it.dateMillis in startM..endM }
            sections.addAll(
                buildBudgetsDocument(
                    payload.copy(selectedSections = setOf("budgets_status")),
                    currentBudgets,
                    categories,
                    monthExp,
                    PersianDateHelper.getMonthName(currentP.month),
                    userPreferences
                ).sections
            )
        }
        if ("goals" in payload.selectedSections) {
            sections.addAll(
                buildGoalsDocument(
                    payload.copy(selectedSections = setOf("active_goals", "completed_goals")),
                    goals,
                    userPreferences
                ).sections
            )
        }
        if ("assets" in payload.selectedSections) {
            sections.addAll(
                buildAssetsDocument(
                    payload.copy(selectedSections = setOf("assets_list")),
                    assets,
                    netWorth,
                    userPreferences
                ).sections
            )
        }
        if ("chandrooz" in payload.selectedSections) {
            sections.addAll(
                buildChandRoozDocument(
                    payload.copy(selectedSections = setOf("mission", "leave", "sick", "bank", "other")),
                    chandRoozRecords,
                    userPreferences
                ).sections
            )
        }

        return PrintableReportDocument(
            documentTitle = "گزارش جامع مالی دودوتا",
            subtitle = "گزارش یکپارچه بخش‌های انتخاب‌شده",
            dateRangeLabel = payload.dateRangeLabel,
            currency = currency,
            fontFamily = userPreferences.fontFamily,
            showFrame = payload.includeFrame,
            showSummary = payload.includeSummary,
            kpis = listOf(
                PrintKpiItem("موجودی کل حساب‌ها", fmtMoney(totalBalance, currency), PrintTone.INFO),
                PrintKpiItem("درآمد بازه", fmtMoney(inc, currency), PrintTone.POSITIVE),
                PrintKpiItem("هزینه بازه", fmtMoney(exp, currency), PrintTone.NEGATIVE),
                PrintKpiItem("ارزش خالص کل", fmtMoney(netWorth, currency), if (netWorth >= 0L) PrintTone.POSITIVE else PrintTone.NEGATIVE)
            ),
            sections = sections
        )
    }
}
