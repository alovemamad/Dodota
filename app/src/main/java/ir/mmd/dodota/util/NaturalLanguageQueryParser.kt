package ir.mmd.dodota.util

import ir.mmd.dodota.data.model.CategoryEntity
import ir.mmd.dodota.data.model.TransactionEntity
import ir.mmd.dodota.data.model.TransactionType

data class ParsedTransactionQuery(
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val exactAmount: Long? = null,
    val forcedType: TransactionType? = null,
    val remainingKeywords: List<String> = emptyList()
)

object NaturalLanguageQueryParser {

    private val GENERIC_STOP_WORDS = setOf(
        "خرید", "خریدهای", "خریدها", "هزینه", "هزینههای", "هزینه‌های", "هزینه‌ها",
        "تراکنش", "تراکنشهای", "تراکنش‌های", "تراکنش‌ها", "مورد", "موارد",
        "پرداخت", "پرداختهای", "پرداخت‌های", "واریز", "واریزی", "درآمد", "درآمدهای",
        "انتقال", "کارت", "به", "از", "در", "با", "برای", "مبلغ", "قیمت",
        "تومان", "تومن", "ریال", "های", "ها", "رو", "را", "که", "همه"
    )

    private val ABOVE_WORDS = listOf(
        "بالای", "بیشتر از", "بیش از", "فوق", "حداقل", "از", ">"
    )

    private val BELOW_WORDS = listOf(
        "زیر", "کمتر از", "کم تر از", "حداکثر", "تا", "<"
    )

    fun parse(rawQuery: String): ParsedTransactionQuery {
        val normalized = PersianNumberFormatter.toEnglishDigits(rawQuery)
            .replace("٬", "")
            .replace(",", "")
            .replace("،", "")
            .replace("ي", "ی")
            .replace("ك", "ک")
            .replace("\u200c", " ")
            .trim()
            .lowercase()

        if (normalized.isBlank()) {
            return ParsedTransactionQuery()
        }

        var forcedType: TransactionType? = null
        if (normalized.contains("درآمد") || normalized.contains("واریز")) {
            forcedType = TransactionType.INCOME
        } else if (normalized.contains("انتقال")) {
            forcedType = TransactionType.TRANSFER
        } else if (normalized.contains("هزینه") || normalized.contains("خرید")) {
            // Note: only force EXPENSE if user explicitly used expense/purchase terms with amount filter or standalone
            if (ABOVE_WORDS.any { normalized.contains(it) } || BELOW_WORDS.any { normalized.contains(it) }) {
                forcedType = TransactionType.EXPENSE
            }
        }

        var minAmount: Long? = null
        var maxAmount: Long? = null
        var exactAmount: Long? = null

        // Regex for "بین X تا Y" or "از X تا Y"
        val betweenRegex = Regex("""(?:بین|از)\s*(\d+(?:\.\d+)?)\s*(هزار|میلیون|میلیارد|تومان|تومن|ریال)?\s*(?:تا|الی)\s*(\d+(?:\.\d+)?)\s*(هزار|میلیون|میلیارد|تومان|تومن|ریال)?""")
        val betweenMatch = betweenRegex.find(normalized)

        var workingText = normalized

        if (betweenMatch != null) {
            val firstVal = parseScaledNumber(betweenMatch.groupValues[1], betweenMatch.groupValues[2].ifBlank { betweenMatch.groupValues[4] })
            val secondVal = parseScaledNumber(betweenMatch.groupValues[3], betweenMatch.groupValues[4])
            if (firstVal != null && secondVal != null) {
                minAmount = minOf(firstVal, secondVal)
                maxAmount = maxOf(firstVal, secondVal)
            }
            workingText = workingText.removeRange(betweenMatch.range).trim()
        } else {
            // Check "بالای / بیشتر از / بیش از / حداقل X"
            val aboveRegex = Regex("""(?:بالای|بیشتر\s*از|بیش\s*از|حداقل|>)\s*(\d+(?:\.\d+)?)\s*(هزار|میلیون|میلیارد|ملیون|تومان|تومن|ریال)?""")
            val aboveMatch = aboveRegex.find(workingText)
            if (aboveMatch != null) {
                minAmount = parseScaledNumber(aboveMatch.groupValues[1], aboveMatch.groupValues[2])
                workingText = workingText.removeRange(aboveMatch.range).trim()
            }

            // Check "زیر / کمتر از / حداکثر / تا X"
            val belowRegex = Regex("""(?:زیر|کمتر\s*از|کم\s*تر\s*از|حداکثر|تا|<)\s*(\d+(?:\.\d+)?)\s*(هزار|میلیون|میلیارد|ملیون|تومان|تومن|ریال)?""")
            val belowMatch = belowRegex.find(workingText)
            if (belowMatch != null) {
                maxAmount = parseScaledNumber(belowMatch.groupValues[1], belowMatch.groupValues[2])
                workingText = workingText.removeRange(belowMatch.range).trim()
            }
        }

        // If no range keywords were used, check if there's a standalone scaled number like "500 هزار"
        if (minAmount == null && maxAmount == null) {
            val standaloneNumRegex = Regex("""^(\d+(?:\.\d+)?)\s*(هزار|میلیون|میلیارد|ملیون)\s*(?:تومان|تومن|ریال)?$""")
            val standaloneMatch = standaloneNumRegex.find(workingText)
            if (standaloneMatch != null) {
                exactAmount = parseScaledNumber(standaloneMatch.groupValues[1], standaloneMatch.groupValues[2])
                workingText = ""
            }
        }

        val tokens = workingText
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val hasAmountCondition = minAmount != null || maxAmount != null || exactAmount != null
        val remainingKeywords = if (hasAmountCondition) {
            tokens.filter { token -> token !in GENERIC_STOP_WORDS }
        } else {
            tokens
        }

        return ParsedTransactionQuery(
            minAmount = minAmount,
            maxAmount = maxAmount,
            exactAmount = exactAmount,
            forcedType = forcedType,
            remainingKeywords = remainingKeywords
        )
    }

    private fun parseScaledNumber(numStr: String, unit: String): Long? {
        val base = numStr.toDoubleOrNull() ?: return null
        val multiplier = when (unit.trim()) {
            "هزار" -> 1_000L
            "میلیون", "ملیون" -> 1_000_000L
            "میلیارد" -> 1_000_000_000L
            else -> 1L
        }
        return (base * multiplier).toLong()
    }

    fun matches(
        tx: TransactionEntity,
        category: CategoryEntity?,
        accountName: String?,
        rawQuery: String
    ): Boolean {
        if (rawQuery.isBlank()) return true

        val parsed = parse(rawQuery)

        if (parsed.forcedType != null && tx.type != parsed.forcedType) {
            return false
        }

        if (parsed.minAmount != null && tx.amount < parsed.minAmount) {
            return false
        }

        if (parsed.maxAmount != null && tx.amount > parsed.maxAmount) {
            return false
        }

        if (parsed.exactAmount != null && tx.amount != parsed.exactAmount) {
            return false
        }

        if (parsed.remainingKeywords.isEmpty()) {
            return true
        }

        val searchableText = buildString {
            append(tx.title).append(" ")
            append(tx.note).append(" ")
            append(tx.tags).append(" ")
            append(tx.externalDestinationInfo).append(" ")
            append(category?.name ?: "").append(" ")
            append(accountName ?: "").append(" ")
            append(tx.amount.toString())
        }.replace("ي", "ی")
            .replace("ك", "ک")
            .replace("\u200c", " ")
            .lowercase()

        return parsed.remainingKeywords.all { kw ->
            searchableText.contains(kw)
        }
    }
}
