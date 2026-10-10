package ir.mmd.dodota.util

import java.util.Calendar
import java.util.TimeZone

data class PersianDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int    // 1..31
)

object PersianDateHelper {
    val PERSIAN_MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    val PERSIAN_WEEKDAYS = listOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"
    )

    fun getCurrentPersianDate(): PersianDate {
        return gregorianMillisToPersian(System.currentTimeMillis())
    }

    fun fromMillis(millis: Long): PersianDate {
        return gregorianMillisToPersian(millis)
    }

    fun toMillis(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        return persianToGregorianMillis(PersianDate(year, month, day), hour, minute)
    }

    fun getMonthName(month: Int): String {
        return PERSIAN_MONTHS.getOrElse((month - 1).coerceIn(0, 11)) { "" }
    }

    fun getMonthLength(year: Int, month: Int): Int {
        return getDaysInPersianMonth(year, month)
    }

    fun addMonthsToPersianDate(
        startYear: Int,
        startMonth: Int,
        dayOfMonth: Int,
        monthsToAdd: Int
    ): Long {
        val totalMonths = (startYear * 12 + (startMonth - 1)) + monthsToAdd
        val targetYear = totalMonths / 12
        val targetMonth = (totalMonths % 12) + 1
        val maxDay = getDaysInPersianMonth(targetYear, targetMonth)
        val safeDay = dayOfMonth.coerceIn(1, maxDay)
        return persianToGregorianMillis(PersianDate(targetYear, targetMonth, safeDay))
    }

    fun getCurrentPersianDateFormatted(isEnglishDigits: Boolean = PersianNumberFormatter.globalUseEnglishDigits): String {
        val today = getCurrentPersianDate()
        val monthName = PERSIAN_MONTHS.getOrElse(today.month - 1) { "" }
        return "${PersianNumberFormatter.formatDigits(today.day, isEnglishDigits)} $monthName ${PersianNumberFormatter.formatDigits(today.year, isEnglishDigits)}"
    }

    fun getFormattedTodayPersian(isEnglishDigits: Boolean = PersianNumberFormatter.globalUseEnglishDigits): String {
        return getCurrentPersianDateFormatted(isEnglishDigits)
    }

    fun formatToPersianDate(millis: Long, isEnglishDigits: Boolean = PersianNumberFormatter.globalUseEnglishDigits): String {
        val pDate = gregorianMillisToPersian(millis)
        val monthName = PERSIAN_MONTHS.getOrElse(pDate.month - 1) { "" }
        return "${PersianNumberFormatter.formatDigits(pDate.day, isEnglishDigits)} $monthName ${PersianNumberFormatter.formatDigits(pDate.year, isEnglishDigits)}"
    }

    fun formatToPersianDateNumeric(millis: Long, isEnglishDigits: Boolean = PersianNumberFormatter.globalUseEnglishDigits): String {
        val pDate = gregorianMillisToPersian(millis)
        val raw = String.format("%04d/%02d/%02d", pDate.year, pDate.month, pDate.day)
        return PersianNumberFormatter.formatDigits(raw, isEnglishDigits)
    }

    fun gregorianMillisToPersian(millis: Long): PersianDate {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = millis
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        return gregorianToJalali(gy, gm, gd)
    }

    fun persianToGregorianMillis(persianDate: PersianDate, hour: Int = 12, minute: Int = 0): Long {
        val (gy, gm, gd) = jalaliToGregorian(persianDate.year, persianDate.month, persianDate.day)
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.YEAR, gy)
        cal.set(Calendar.MONTH, gm - 1)
        cal.set(Calendar.DAY_OF_MONTH, gd)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getStartOfMonthMillis(year: Int, month: Int): Long {
        val (gy, gm, gd) = jalaliToGregorian(year, month, 1)
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(gy, gm - 1, gd, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getEndOfMonthMillis(year: Int, month: Int): Long {
        val daysInMonth = getDaysInPersianMonth(year, month)
        val (gy, gm, gd) = jalaliToGregorian(year, month, daysInMonth)
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(gy, gm - 1, gd, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun getDaysInPersianMonth(year: Int, month: Int): Int {
        return when {
            month in 1..6 -> 31
            month in 7..11 -> 30
            month == 12 -> if (isLeapPersianYear(year)) 30 else 29
            else -> 30
        }
    }

    fun isLeapPersianYear(year: Int): Boolean {
        val rem = (year + 12) % 33
        return rem in listOf(1, 5, 9, 13, 17, 22, 26, 30)
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): PersianDate {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
                ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + (days / 31)
            jd = 1 + (days % 31)
        } else {
            jm = 7 + ((days - 186) / 30)
            jd = 1 + ((days - 186) % 30)
        }
        return PersianDate(jy, jm, jd)
    }

    private fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jy1 = jy + 1595
        var days = -355668 + (365 * jy1) + ((jy1 / 33) * 8) + (((jy1 % 33) + 3) / 4) + jd +
                if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val salA = intArrayOf(
            0, 31,
            if ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)) 29 else 28,
            31, 30, 31, 30, 31, 31, 30, 31, 30, 31
        )
        var gm = 0
        while (gm < 13 && gd > salA[gm]) {
            gd -= salA[gm]
            gm++
        }
        return Triple(gy, gm, gd)
    }
}

fun formatToPersianDate(millis: Long): String = PersianDateHelper.formatToPersianDate(millis)
