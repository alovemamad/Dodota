package ir.mmd.dodota.util

import ir.mmd.dodota.data.model.AppCurrency
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

object PersianNumberFormatter {
    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /**
     * Global digit language state synced from UserPreferences.currency.isEnglishDigits.
     * When true, all numbers across the entire application (calendars, charts, lists, cards, dialogs, inputs)
     * are rendered in English digits (0-9). When false, they are rendered in Persian digits (۰-۹).
     */
    @Volatile
    var globalUseEnglishDigits: Boolean = false

    /**
     * Central number & amount formatting function (Items 2 & 3).
     * Separates large numbers 3-by-3 with commas (e.g., 1,250,000 or ۱,۲۵۰,۰۰۰)
     * and applies the user's selected digit language (English or Persian).
     */
    fun formatNumber(
        number: Number,
        useCommas: Boolean = true,
        isEnglishDigits: Boolean = globalUseEnglishDigits
    ): String {
        val longVal = number.toLong()
        val raw = if (useCommas) {
            val symbols = DecimalFormatSymbols(Locale.US).apply {
                groupingSeparator = ','
            }
            DecimalFormat("#,###", symbols).format(longVal)
        } else {
            number.toString()
        }
        return applyDigitLanguage(raw, isEnglishDigits)
    }

    fun formatNumber(
        number: Number,
        currency: AppCurrency,
        useCommas: Boolean = true
    ): String = formatNumber(number, useCommas, currency.isEnglishDigits)

    /**
     * Formats numeric input text live while typing with 3-by-3 commas and active digit language,
     * while keeping raw digits clean for database storage.
     */
    fun formatInputWithCommas(
        rawInput: String,
        isEnglishDigits: Boolean = globalUseEnglishDigits
    ): String {
        val clean = toEnglishDigits(rawInput)
            .replace(",", "")
            .replace("٬", "")
            .replace(" ", "")
            .trim()
        if (clean.isEmpty()) return ""
        if (clean == "-") return "-"
        val parsed = clean.toLongOrNull()
        return if (parsed != null) {
            formatNumber(parsed, useCommas = true, isEnglishDigits = isEnglishDigits)
        } else {
            applyDigitLanguage(clean, isEnglishDigits)
        }
    }

    private fun applyDigitLanguage(text: String, isEnglishDigits: Boolean): String {
        return if (isEnglishDigits) {
            toEnglishDigits(text)
        } else {
            val sb = StringBuilder(text.length)
            for (ch in text) {
                when (ch) {
                    in '0'..'9' -> sb.append(persianDigits[ch - '0'])
                    in '٠'..'٩' -> sb.append(persianDigits[ch - '٠'])
                    else -> sb.append(ch)
                }
            }
            sb.toString()
        }
    }

    fun toPersianDigits(text: String): String {
        return applyDigitLanguage(text, globalUseEnglishDigits)
    }

    fun toPersianDigits(number: Number): String {
        val numValue = number.toLong()
        // Years (e.g., 1300..1500) stay without commas; all other large numbers >= 1000 get 3-by-3 commas
        val shouldUseCommas = abs(numValue) >= 1000L && numValue !in 1300L..1500L
        return formatNumber(numValue, useCommas = shouldUseCommas, isEnglishDigits = globalUseEnglishDigits)
    }

    fun formatDigits(text: String, isEnglishDigits: Boolean = globalUseEnglishDigits): String {
        return applyDigitLanguage(text, isEnglishDigits)
    }

    fun formatDigits(number: Number, isEnglishDigits: Boolean = globalUseEnglishDigits): String {
        val numValue = number.toLong()
        val shouldUseCommas = abs(numValue) >= 1000L && numValue !in 1300L..1500L
        return formatNumber(numValue, useCommas = shouldUseCommas, isEnglishDigits = isEnglishDigits)
    }

    fun formatDigits(text: String, currency: AppCurrency): String {
        return applyDigitLanguage(text, currency.isEnglishDigits)
    }

    fun formatDigits(number: Number, currency: AppCurrency): String {
        return formatDigits(number, currency.isEnglishDigits)
    }

    fun toEnglishDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val pIdx = persianDigits.indexOf(ch)
            val aIdx = arabicDigits.indexOf(ch)
            when {
                pIdx >= 0 -> sb.append(englishDigits[pIdx])
                aIdx >= 0 -> sb.append(englishDigits[aIdx])
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatNumberWithCommas(amount: Long, isEnglishDigits: Boolean = globalUseEnglishDigits): String {
        return formatNumber(amount, useCommas = true, isEnglishDigits = isEnglishDigits)
    }

    fun formatNumberWithCommas(amount: Long, currency: AppCurrency): String {
        return formatNumber(amount, useCommas = true, isEnglishDigits = currency.isEnglishDigits)
    }

    fun formatCurrency(
        amount: Long,
        currency: AppCurrency = AppCurrency.TOMAN,
        showSign: Boolean = false
    ): String {
        val useEn = currency.isEnglishDigits || globalUseEnglishDigits
        val formatted = formatNumber(abs(amount), useCommas = true, isEnglishDigits = useEn)
        val sign = when {
            amount < 0 -> "- "
            showSign && amount > 0 -> "+ "
            else -> ""
        }
        return "$sign$formatted ${currency.unitName}"
    }

    /**
     * Compact chart axis formatter that still uses 3-by-3 commas and respects digit language.
     */
    fun formatChartValue(amount: Long, currency: AppCurrency = AppCurrency.TOMAN): String {
        val useEn = currency.isEnglishDigits || globalUseEnglishDigits
        return formatNumber(amount, useCommas = true, isEnglishDigits = useEn)
    }

    fun parseAmount(input: String): Long {
        val english = toEnglishDigits(input)
            .replace(",", "")
            .replace("٬", "")
            .replace(" ", "")
            .trim()
        return english.toLongOrNull() ?: 0L
    }

    fun parseNumericValue(input: String): Long = parseAmount(input)

    fun formatWithCommas(amount: Long, isEnglishDigits: Boolean = globalUseEnglishDigits): String =
        formatNumber(amount, useCommas = true, isEnglishDigits = isEnglishDigits)

    fun formatWithCommas(amount: Long, currency: AppCurrency): String =
        formatNumber(amount, useCommas = true, isEnglishDigits = currency.isEnglishDigits)

    fun formatPercent(value: Int, isEnglishDigits: Boolean = globalUseEnglishDigits): String =
        "${applyDigitLanguage(value.toString(), isEnglishDigits)}٪"

    fun formatPercent(value: Float, isEnglishDigits: Boolean = globalUseEnglishDigits): String =
        "${applyDigitLanguage(value.toInt().toString(), isEnglishDigits)}٪"
}
