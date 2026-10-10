package ir.mmd.dodota.util

import ir.mmd.dodota.data.model.AppCurrency
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object PersianNumberFormatter {
    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    private val englishDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    private val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /**
     * Global digit language state synced from UserPreferences.currency.isEnglishDigits
     * When true, all numbers across the entire application are rendered in English digits (0-9).
     */
    @Volatile
    var globalUseEnglishDigits: Boolean = false

    fun toPersianDigits(text: String): String {
        if (globalUseEnglishDigits) {
            return toEnglishDigits(text)
        }
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                in '0'..'9' -> sb.append(persianDigits[ch - '0'])
                in '٠'..'٩' -> sb.append(persianDigits[ch - '٠'])
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun toPersianDigits(number: Number): String {
        val numValue = number.toLong()
        val raw = if (kotlin.math.abs(numValue) >= 1000L && numValue !in 1300L..1500L) {
            val symbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = ',' }
            DecimalFormat("#,###", symbols).format(numValue)
        } else {
            number.toString()
        }
        return toPersianDigits(raw)
    }

    fun formatDigits(text: String, isEnglishDigits: Boolean = globalUseEnglishDigits): String {
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

    fun formatDigits(number: Number, isEnglishDigits: Boolean = globalUseEnglishDigits): String {
        return formatDigits(number.toString(), isEnglishDigits)
    }

    fun formatDigits(text: String, currency: AppCurrency): String {
        return formatDigits(text, currency.isEnglishDigits)
    }

    fun formatDigits(number: Number, currency: AppCurrency): String {
        return formatDigits(number.toString(), currency.isEnglishDigits)
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
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = ','
        }
        val formatter = DecimalFormat("#,###", symbols)
        val formatted = formatter.format(amount)
        return if (isEnglishDigits) toEnglishDigits(formatted) else formatDigits(formatted, false)
    }

    fun formatNumberWithCommas(amount: Long, currency: AppCurrency): String {
        return formatNumberWithCommas(amount, currency.isEnglishDigits)
    }

    fun formatCurrency(
        amount: Long,
        currency: AppCurrency = AppCurrency.TOMAN,
        showSign: Boolean = false
    ): String {
        val useEn = currency.isEnglishDigits || globalUseEnglishDigits
        val formatted = formatNumberWithCommas(kotlin.math.abs(amount), useEn)
        val sign = when {
            amount < 0 -> "- "
            showSign && amount > 0 -> "+ "
            else -> ""
        }
        return "$sign$formatted ${currency.unitName}"
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
        formatNumberWithCommas(amount, isEnglishDigits)

    fun formatWithCommas(amount: Long, currency: AppCurrency): String =
        formatNumberWithCommas(amount, currency)

    fun formatPercent(value: Int, isEnglishDigits: Boolean = globalUseEnglishDigits): String =
        "${formatDigits(value, isEnglishDigits)}٪"

    fun formatPercent(value: Float, isEnglishDigits: Boolean = globalUseEnglishDigits): String =
        "${formatDigits(value.toInt(), isEnglishDigits)}٪"
}
