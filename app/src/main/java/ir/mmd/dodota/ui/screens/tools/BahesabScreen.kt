package ir.mmd.dodota.ui.screens.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.ui.components.DodotaExpandableSection
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.components.GraphicalFormDialog
import ir.mmd.dodota.ui.components.PersianAmountInput
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.ui.theme.LocalDodotaGlassStyle
import ir.mmd.dodota.util.PersianNumberFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

enum class BahesabMainTab(val title: String) {
    LOAN("وام"),
    DEPOSIT("سپرده")
}

enum class LoanCalculationType(val title: String) {
    INSTALLMENT_AMOUNT("اقساط و سود وام"),
    INTEREST_RATE("درصد سود وام"),
    EARLY_PAYOFF("مبلغ تسویه پیش از موعد وام"),
    STEPPED_LOAN("اقساط وام پلکانی"),
    QARZ_AL_HASANEH("وام قرض الحسنه ( ازدواج , ...)")
}

enum class InstallmentIntervalOption(val title: String, val months: Int) {
    ONE_MONTH("یک ماه", 1),
    TWO_MONTHS("دو ماه", 2),
    THREE_MONTHS("سه ماه", 3),
    FOUR_MONTHS("چهار ماه", 4),
    FIVE_MONTHS("پنج ماه", 5),
    SIX_MONTHS("شش ماه", 6),
    SEVEN_MONTHS("هفت ماه", 7),
    EIGHT_MONTHS("هشت ماه", 8),
    NINE_MONTHS("نه ماه", 9),
    TEN_MONTHS("ده ماه", 10),
    ELEVEN_MONTHS("یازده ماه", 11),
    TWELVE_MONTHS("دوازده ماه", 12)
}

enum class DepositCalculationType(val title: String) {
    PROFIT_AMOUNT("محاسبه مبلغ سود سپرده"),
    PROFIT_RATE("محاسبه درصد سود")
}

private data class ResultItem(
    val label: String,
    val value: String,
    val isHighlighted: Boolean = false
)

private fun solvePeriodicRate(principal: Double, installment: Double, count: Int): Double {
    if (principal <= 0.0 || installment <= 0.0 || count <= 0) return 0.0
    if (installment * count <= principal) return 0.0
    var low = 0.0
    var high = 10.0
    repeat(100) {
        val mid = (low + high) / 2.0
        if (mid <= 1e-12) {
            low = mid
        } else {
            val factor = (1.0 + mid).pow(count.toDouble())
            val estPmt = principal * (mid * factor) / (factor - 1.0)
            if (estPmt > installment) {
                high = mid
            } else {
                low = mid
            }
        }
    }
    return (low + high) / 2.0
}

@Composable
fun BahesabScreen(
    userPreferences: UserPreferences,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(BahesabMainTab.LOAN) }
    val glassStyle = LocalDodotaGlassStyle.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    // Loan States
    var loanCalcType by remember { mutableStateOf(LoanCalculationType.INSTALLMENT_AMOUNT) }
    var showLoanTypePicker by remember { mutableStateOf(false) }

    var loanAmount by remember { mutableStateOf("") }
    var loanRate by remember { mutableStateOf("") }
    var loanCount by remember { mutableStateOf("") }
    var loanInterval by remember { mutableStateOf(InstallmentIntervalOption.ONE_MONTH) }
    var showIntervalPicker by remember { mutableStateOf(false) }

    // Additional Loan States
    var monthlyInstallmentInput by remember { mutableStateOf("") }
    var paidInstallmentsCount by remember { mutableStateOf("") }
    var stepYearsInput by remember { mutableStateOf("") }
    var stepIncreasePercent by remember { mutableStateOf("") }

    var loanResults by remember { mutableStateOf<List<ResultItem>>(emptyList()) }
    var loanError by remember { mutableStateOf<String?>(null) }

    // Deposit States
    var depositCalcType by remember { mutableStateOf(DepositCalculationType.PROFIT_AMOUNT) }
    var showDepositTypePicker by remember { mutableStateOf(false) }

    var depositAmount by remember { mutableStateOf("") }
    var depositRate by remember { mutableStateOf("") }
    var depositDays by remember { mutableStateOf("30") }
    var depositProfitAmountInput by remember { mutableStateOf("") }

    var depositResults by remember { mutableStateOf<List<ResultItem>>(emptyList()) }
    var depositError by remember { mutableStateOf<String?>(null) }

    val unitName = "تومان"
    val isEnglish = userPreferences.currency.isEnglishDigits

    fun formatMoney(value: Double): String {
        val rounded = value.roundToLong()
        return "${PersianNumberFormatter.formatNumber(rounded, useCommas = true, isEnglishDigits = isEnglish)} $unitName"
    }

    fun formatBahesabRate(rate: Double): String {
        var s = String.format(Locale.US, "%.3f", rate).trimEnd('0').trimEnd('.')
        if (s == "0" && rate > 0.0) {
            s = String.format(Locale.US, "%.5f", rate).trimEnd('0').trimEnd('.')
        }
        val display = s.replace('.', '/')
        return "${PersianNumberFormatter.formatDigits(display, isEnglish)} ٪"
    }

    fun calculateLoan() {
        loanError = null
        val p = PersianNumberFormatter.parseAmount(loanAmount).toDouble()
        val n = PersianNumberFormatter.toEnglishDigits(loanCount).trim().toIntOrNull() ?: 0
        val intervalMonths = loanInterval.months
        val rateVal = PersianNumberFormatter.toEnglishDigits(loanRate).replace("٫", ".").replace("/", ".").trim().toDoubleOrNull() ?: -1.0
        val instVal = PersianNumberFormatter.parseAmount(monthlyInstallmentInput).toDouble()
        val paidCount = PersianNumberFormatter.toEnglishDigits(paidInstallmentsCount).trim().toIntOrNull() ?: -1

        when (loanCalcType) {
            LoanCalculationType.INSTALLMENT_AMOUNT -> {
                if (p <= 0) {
                    loanError = "خطا در ورود مبلغ وام"
                    loanResults = emptyList()
                    return
                }
                if (rateVal < 0) {
                    loanError = "خطا در ورود درصد سود وام"
                    loanResults = emptyList()
                    return
                }
                if (n <= 0) {
                    loanError = "خطا در ورود تعداد اقساط وام"
                    loanResults = emptyList()
                    return
                }
                val r = (rateVal / 100.0) * (intervalMonths / 12.0)
                val pmt = if (r <= 1e-11) {
                    p / n
                } else {
                    val factor = (1.0 + r).pow(n.toDouble())
                    p * (r * factor) / (factor - 1.0)
                }
                val roundedPmt = pmt.roundToLong()
                val totalPayment = (pmt * n).roundToLong()
                val totalInterest = totalPayment - p.roundToLong()

                loanResults = listOf(
                    ResultItem("اصل وام", formatMoney(p)),
                    ResultItem("نرخ سود سالانه", formatBahesabRate(rateVal)),
                    ResultItem("تعداد اقساط", "${PersianNumberFormatter.formatDigits(n, isEnglish)} قسط"),
                    ResultItem("فاصله اقساط", "${PersianNumberFormatter.formatDigits(intervalMonths, isEnglish)} ماه"),
                    ResultItem("مبلغ هر قسط", formatMoney(roundedPmt.toDouble()), isHighlighted = true),
                    ResultItem("جمع کل اقساط", formatMoney(totalPayment.toDouble()), isHighlighted = true),
                    ResultItem("سود کل وام", formatMoney(totalInterest.toDouble()), isHighlighted = true)
                )
            }

            LoanCalculationType.INTEREST_RATE -> {
                if (p <= 0) {
                    loanError = "خطا در ورود مبلغ وام"
                    loanResults = emptyList()
                    return
                }
                if (instVal <= 0) {
                    loanError = "خطا در ورود مبلغ قسط وام"
                    loanResults = emptyList()
                    return
                }
                if (n <= 0) {
                    loanError = "خطا در ورود تعداد اقساط وام"
                    loanResults = emptyList()
                    return
                }
                val totalPayment = instVal * n
                if (totalPayment < p) {
                    loanError = "خطا، مبلغ قسط یا تعداد اقساط نادرست است"
                    loanResults = emptyList()
                    return
                }
                val periodicRate = solvePeriodicRate(p, instVal, n)
                val annualRate = periodicRate * (12.0 / intervalMonths) * 100.0
                val totalInterest = totalPayment - p

                loanResults = listOf(
                    ResultItem("اصل وام", formatMoney(p)),
                    ResultItem("مبلغ قسط", formatMoney(instVal)),
                    ResultItem("تعداد اقساط", "${PersianNumberFormatter.formatDigits(n, isEnglish)} قسط"),
                    ResultItem("فاصله اقساط", "${PersianNumberFormatter.formatDigits(intervalMonths, isEnglish)} ماه"),
                    ResultItem("نرخ سود سالانه", formatBahesabRate(annualRate), isHighlighted = true),
                    ResultItem("جمع کل اقساط", formatMoney(totalPayment), isHighlighted = true),
                    ResultItem("سود کل وام", formatMoney(totalInterest), isHighlighted = true)
                )
            }

            LoanCalculationType.EARLY_PAYOFF -> {
                if (p <= 0) {
                    loanError = "خطا در ورود مبلغ وام"
                    loanResults = emptyList()
                    return
                }
                if (instVal <= 0) {
                    loanError = "خطا در ورود مبلغ قسط وام"
                    loanResults = emptyList()
                    return
                }
                if (paidCount < 0) {
                    loanError = "خطا در ورود تعداد اقساط پرداخت شده وام"
                    loanResults = emptyList()
                    return
                }
                if (n <= 0) {
                    loanError = "خطا در ورود تعداد اقساط وام"
                    loanResults = emptyList()
                    return
                }
                if (n * instVal < p) {
                    loanError = "خطا، مبلغ قسط یا تعداد اقساط نادرست است"
                    loanResults = emptyList()
                    return
                }
                if (n <= paidCount) {
                    loanError = "تعداد اقساط پرداخت شده باید کوچکتر از تعداد اقساط وام باشد"
                    loanResults = emptyList()
                    return
                }

                // Exact Bahesab formula for Early Settlement (op):
                // 1. Solve periodic interest rate from principal, installment, and total installments
                val periodicRate = solvePeriodicRate(p, instVal, n)
                val annualRate = periodicRate * (12.0 / intervalMonths) * 100.0
                val remainingCount = n - paidCount
                val paidTotal = paidCount * instVal

                // 2. Present value of remaining installments at periodicRate
                val payoffAmount = if (periodicRate <= 1e-11) {
                    remainingCount * instVal
                } else {
                    instVal * (1.0 - (1.0 + periodicRate).pow(-remainingCount.toDouble())) / periodicRate
                }

                loanResults = listOf(
                    ResultItem("اصل وام", formatMoney(p)),
                    ResultItem("مبلغ قسط", formatMoney(instVal)),
                    ResultItem("نرخ سود سالانه", formatBahesabRate(annualRate)),
                    ResultItem("تعداد اقساط", "${PersianNumberFormatter.formatDigits(n, isEnglish)} قسط"),
                    ResultItem("فاصله اقساط", "${PersianNumberFormatter.formatDigits(intervalMonths, isEnglish)} ماه"),
                    ResultItem("اقساط پرداخت شده", "${PersianNumberFormatter.formatDigits(paidCount, isEnglish)} قسط"),
                    ResultItem("جمع اقساط پرداخت شده", formatMoney(paidTotal), isHighlighted = true),
                    ResultItem("مبلغ تسویه پیش از موعد", formatMoney(payoffAmount), isHighlighted = true)
                )
            }

            LoanCalculationType.QARZ_AL_HASANEH -> {
                if (p <= 0) {
                    loanError = "خطا در ورود مبلغ وام"
                    loanResults = emptyList()
                    return
                }
                val effectiveFeeRate = if (loanRate.isBlank()) 4.0 else rateVal
                if (effectiveFeeRate < 0 || effectiveFeeRate > 200) {
                    loanError = "خطا در ورود درصد کارمزد وام"
                    loanResults = emptyList()
                    return
                }
                if (n <= 3 || n % 12 == 1) {
                    loanError = "خطا در تعداد اقساط وام"
                    loanResults = emptyList()
                    return
                }

                // Exact Bahesab formula for Qarz-al-Hasanah (og):
                // f = ceil(n / 12) years; 1 installment per year (1st, 13th, 25th, ...) is for fee
                // Principal is divided across (n - f) regular installments: B = p / (n - f)
                val f = kotlin.math.ceil(n / 12.0).toInt()
                val bExact = p / (n - f).toDouble()
                val regularInstallment = bExact.roundToLong()

                val items = mutableListOf(
                    ResultItem("اصل وام", formatMoney(p)),
                    ResultItem("درصد کارمزد", formatBahesabRate(effectiveFeeRate)),
                    ResultItem("تعداد اقساط", "${PersianNumberFormatter.formatDigits(n, isEnglish)} قسط"),
                    ResultItem("فاصله اقساط", "${PersianNumberFormatter.formatDigits(1, isEnglish)} ماه"),
                    ResultItem("مبلغ هر قسط (به جز اقساط زیر)", formatMoney(regularInstallment.toDouble()), isHighlighted = true)
                )

                var totalFee = 0L
                for (i in 1..f) {
                    val instNumber = 12 * (i - 1) + 1
                    val remainingPrincipal = p - 11.0 * bExact * (i - 1)
                    val w = if (i != f) {
                        ((effectiveFeeRate / 100.0) * remainingPrincipal).roundToLong()
                    } else {
                        ((effectiveFeeRate * (n - 12 * (i - 1)) / 1200.0) * remainingPrincipal).roundToLong()
                    }
                    totalFee += w
                    items.add(
                        ResultItem(
                            "مبلغ قسط ${PersianNumberFormatter.formatDigits(instNumber, isEnglish)}",
                            formatMoney(w.toDouble())
                        )
                    )
                }

                val totalPayable = p.roundToLong() + totalFee
                items.add(ResultItem("جمع کل اقساط وام", formatMoney(totalPayable.toDouble()), isHighlighted = true))
                items.add(ResultItem("سود کل وام", formatMoney(totalFee.toDouble()), isHighlighted = true))

                loanResults = items
            }

            LoanCalculationType.STEPPED_LOAN -> {
                val years = PersianNumberFormatter.toEnglishDigits(stepYearsInput).trim().toIntOrNull() ?: 0
                val stepPercent = PersianNumberFormatter.toEnglishDigits(stepIncreasePercent).replace("٫", ".").replace("/", ".").trim().toDoubleOrNull() ?: -1.0
                if (p <= 0) {
                    loanError = "خطا در ورود مبلغ وام"
                    loanResults = emptyList()
                    return
                }
                if (rateVal < 0) {
                    loanError = "خطا در ورود درصد سود وام"
                    loanResults = emptyList()
                    return
                }
                if (stepPercent < 0) {
                    loanError = "خطا در ورود درصد رشد سالانه پله"
                    loanResults = emptyList()
                    return
                }
                if (years <= 1 || years > 100) {
                    loanError = "مدت بازپرداخت وام بیشتر از یک سال باشد"
                    loanResults = emptyList()
                    return
                }

                val r = (rateVal / 100.0) / 12.0
                val g = stepPercent / 100.0
                val annuity12 = if (r <= 1e-11) 12.0 else (1.0 - (1.0 + r).pow(-12.0)) / r
                var pvFactor = 0.0
                for (y in 0 until years) {
                    pvFactor += (1.0 + g).pow(y.toDouble()) * (1.0 + r).pow((-12 * y).toDouble()) * annuity12
                }
                val firstYearInstallment = if (pvFactor > 0.0) p / pvFactor else p / (years * 12)

                val yearlyInstallments = DoubleArray(years)
                yearlyInstallments[0] = firstYearInstallment
                for (h in 1 until years) {
                    yearlyInstallments[h] = yearlyInstallments[h - 1] * (1.0 + stepPercent / 100.0)
                }

                val items = mutableListOf(
                    ResultItem("اصل وام", formatMoney(p)),
                    ResultItem("نرخ سود سالانه", formatBahesabRate(rateVal)),
                    ResultItem("تعداد اقساط", "${PersianNumberFormatter.formatDigits(12 * years, isEnglish)} قسط"),
                    ResultItem("رشد سالانه پله", formatBahesabRate(stepPercent))
                )

                var sumYearlyMonthly = 0.0
                for (idx in 0 until years) {
                    sumYearlyMonthly += yearlyInstallments[idx]
                    items.add(
                        ResultItem(
                            "قسط هر ماه در سال ${PersianNumberFormatter.formatDigits(idx + 1, isEnglish)}",
                            formatMoney(yearlyInstallments[idx]),
                            isHighlighted = idx == 0
                        )
                    )
                }

                val totalPay = (12.0 * sumYearlyMonthly).roundToLong()
                val totalInterest = (12.0 * sumYearlyMonthly - p).roundToLong()
                items.add(ResultItem("جمع کل اقساط", formatMoney(totalPay.toDouble()), isHighlighted = true))
                items.add(ResultItem("سود کل وام", formatMoney(totalInterest.toDouble()), isHighlighted = true))

                loanResults = items
            }
        }
    }

    fun calculateDeposit() {
        depositError = null
        val p = PersianNumberFormatter.parseAmount(depositAmount).toDouble()
        val days = (PersianNumberFormatter.toEnglishDigits(depositDays).trim().toIntOrNull() ?: 30).coerceAtLeast(1)
        val rateVal = PersianNumberFormatter.toEnglishDigits(depositRate).replace("٫", ".").replace("/", ".").trim().toDoubleOrNull() ?: 0.0
        val profitInput = PersianNumberFormatter.parseAmount(depositProfitAmountInput).toDouble()

        when (depositCalcType) {
            DepositCalculationType.PROFIT_AMOUNT -> {
                if (p <= 0 || rateVal <= 0) {
                    depositError = "لطفاً مبلغ سپرده و درصد سود سالانه را وارد نمایید."
                    depositResults = emptyList()
                    return
                }
                val annualProfit = (p * rateVal) / 100.0
                val dailyProfit = (p * rateVal) / 36500.0
                val periodProfit = dailyProfit * days

                depositResults = listOf(
                    ResultItem("سود ${PersianNumberFormatter.formatDigits(days, isEnglish)} روزه (ماهانه)", formatMoney(periodProfit), isHighlighted = true),
                    ResultItem("سود روزانه (هر روز)", formatMoney(dailyProfit)),
                    ResultItem("سود سالانه", formatMoney(annualProfit)),
                    ResultItem("اصل سرمایه + سود دوره", formatMoney(p + periodProfit))
                )
            }

            DepositCalculationType.PROFIT_RATE -> {
                if (p <= 0 || profitInput <= 0 || days <= 0) {
                    depositError = "لطفاً مبلغ سپرده، مبلغ سود دریافتی و تعداد روز را وارد نمایید."
                    depositResults = emptyList()
                    return
                }
                val annualRate = (profitInput * 36500.0) / (p * days.toDouble())
                val dailyProfit = profitInput / days.toDouble()
                val annualProfit = dailyProfit * 365.0

                depositResults = listOf(
                    ResultItem("درصد سود سالانه سپرده", formatBahesabRate(annualRate), isHighlighted = true),
                    ResultItem("سود روزانه", formatMoney(dailyProfit)),
                    ResultItem("معادل سود سالانه", formatMoney(annualProfit))
                )
            }
        }
    }

    Scaffold(
        topBar = {
            DodotaTopBar(
                title = "باحساب",
                onBackClick = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Segmented Top Tabs: وام | سپرده (Matching Bahesab header tabs)
            item {
                GlassCard(
                    shape = RoundedCornerShape(22.dp),
                    borderColor = primaryColor.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BahesabMainTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) primaryColor else Color.Transparent,
                                border = if (isSelected) null else BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedTab = tab }
                                    .testTag("bahesab_tab_${tab.name.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (tab == BahesabMainTab.LOAN) Icons.Default.AccountBalance else Icons.Default.Savings,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedTab == BahesabMainTab.LOAN) {
                // LOAN CALCULATOR SECTION
                item {
                    GlassCard(
                        shape = RoundedCornerShape(24.dp),
                        borderColor = primaryColor.copy(alpha = 0.4f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // انتخاب نوع محاسبه
                            BahesabPillSelectorField(
                                label = "انتخاب نوع محاسبه",
                                selectedValue = loanCalcType.title,
                                onClick = { showLoanTypePicker = true },
                                testTag = "loan_calc_type_selector"
                            )

                            // مبلغ وام ( تومان )
                            BahesabAmountField(
                                value = loanAmount,
                                onValueChange = { loanAmount = it },
                                label = "مبلغ وام ( تومان )",
                                placeholder = "مثال: 20,000,000",
                                isEnglishDigits = isEnglish
                            )

                            when (loanCalcType) {
                                LoanCalculationType.INSTALLMENT_AMOUNT -> {
                                    BahesabNumericField(
                                        value = loanRate,
                                        onValueChange = { loanRate = it },
                                        label = "درصد سود سالانه ( % )",
                                        placeholder = "مثال: 18",
                                        isDecimal = true,
                                        isEnglishDigits = isEnglish
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabNumericField(
                                                value = loanCount,
                                                onValueChange = { loanCount = it },
                                                label = "تعداد اقساط وام",
                                                placeholder = "مثال: 48",
                                                isDecimal = false,
                                                isEnglishDigits = isEnglish
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabPillSelectorField(
                                                label = "فاصله بین اقساط",
                                                selectedValue = loanInterval.title,
                                                onClick = { showIntervalPicker = true },
                                                testTag = "loan_interval_selector"
                                            )
                                        }
                                    }
                                }

                                LoanCalculationType.INTEREST_RATE -> {
                                    BahesabAmountField(
                                        value = monthlyInstallmentInput,
                                        onValueChange = { monthlyInstallmentInput = it },
                                        label = "مبلغ قسط ( تومان )",
                                        placeholder = "مثال: 1,000,000",
                                        isEnglishDigits = isEnglish
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabNumericField(
                                                value = loanCount,
                                                onValueChange = { loanCount = it },
                                                label = "تعداد اقساط وام",
                                                placeholder = "مثال: 48",
                                                isDecimal = false,
                                                isEnglishDigits = isEnglish
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabPillSelectorField(
                                                label = "فاصله بین اقساط",
                                                selectedValue = loanInterval.title,
                                                onClick = { showIntervalPicker = true }
                                            )
                                        }
                                    }
                                }

                                LoanCalculationType.EARLY_PAYOFF -> {
                                    BahesabAmountField(
                                        value = monthlyInstallmentInput,
                                        onValueChange = { monthlyInstallmentInput = it },
                                        label = "مبلغ قسط ( تومان )",
                                        placeholder = "مثال: 1,000,000",
                                        isEnglishDigits = isEnglish
                                    )

                                    BahesabNumericField(
                                        value = paidInstallmentsCount,
                                        onValueChange = { paidInstallmentsCount = it },
                                        label = "تعداد اقساط پرداخت شده",
                                        placeholder = "مثال: 20",
                                        isDecimal = false,
                                        isEnglishDigits = isEnglish
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabNumericField(
                                                value = loanCount,
                                                onValueChange = { loanCount = it },
                                                label = "تعداد اقساط وام",
                                                placeholder = "مثال: 48",
                                                isDecimal = false,
                                                isEnglishDigits = isEnglish
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabPillSelectorField(
                                                label = "فاصله بین اقساط",
                                                selectedValue = loanInterval.title,
                                                onClick = { showIntervalPicker = true }
                                            )
                                        }
                                    }
                                }

                                LoanCalculationType.QARZ_AL_HASANEH -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabNumericField(
                                                value = loanRate,
                                                onValueChange = { loanRate = it },
                                                label = "درصد کارمزد وام",
                                                placeholder = "مثال: 4",
                                                isDecimal = true,
                                                isEnglishDigits = isEnglish
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabNumericField(
                                                value = loanCount,
                                                onValueChange = { loanCount = it },
                                                label = "تعداد اقساط وام",
                                                placeholder = "مثال: 36",
                                                isDecimal = false,
                                                isEnglishDigits = isEnglish
                                            )
                                        }
                                    }
                                }

                                LoanCalculationType.STEPPED_LOAN -> {
                                    BahesabNumericField(
                                        value = loanRate,
                                        onValueChange = { loanRate = it },
                                        label = "درصد سود سالانه ( % )",
                                        placeholder = "مثال: 18",
                                        isDecimal = true,
                                        isEnglishDigits = isEnglish
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabNumericField(
                                                value = stepIncreasePercent,
                                                onValueChange = { stepIncreasePercent = it },
                                                label = "درصد رشد سالانه پله",
                                                placeholder = "مثال: 3",
                                                isDecimal = true,
                                                isEnglishDigits = isEnglish
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            BahesabNumericField(
                                                value = stepYearsInput,
                                                onValueChange = { stepYearsInput = it },
                                                label = "مدت بازپرداخت (سال)",
                                                placeholder = "مثال: 7",
                                                isDecimal = false,
                                                isEnglishDigits = isEnglish
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // دکمه محاسبه با خطوط طرفین مشابه تصویر باحساب
                            BahesabCalculateButton(
                                onClick = { calculateLoan() },
                                testTag = "calculate_loan_button"
                            )

                            Text(
                                text = "برای محاسبه اقساط وام ازدواج و وامهای قرض الحسنه ابتدا نوع محاسبه را به قرض الحسنه تغییر دهید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Loan Error or Results
                if (loanError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = loanError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                if (loanResults.isNotEmpty()) {
                    item {
                        BahesabResultsCard(
                            title = "نتیجه ${loanCalcType.title}",
                            results = loanResults
                        )
                    }
                }

                // راهنمای محاسبه وام (طبق متن درخواستی کاربر)
                item {
                    DodotaExpandableSection(
                        title = "راهنمای محاسبه‌گر وام و کارمزد قرض‌الحسنه",
                        icon = Icons.Default.HelpOutline,
                        initiallyExpanded = false
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "برای محاسبه اقساط وام و یا نرخ سود و مانده تسویه و سایر محاسبات لازم است ابتدا نوع محاسبه را بدقت انتخاب نمایید و پس از آن با وارد کردن مقادیر مرتبط با هر کادر نسبت به محاسبه مورد نظر اقدام نمایید. توجه داشته باشید که برای سهولت مبالغ به تومان در نظر گرفته شده اند. در حقیقت محاسبه گر وام را میتوان برای هر واحد پولی به کار برد و فقط نتایج برحسب همان واحد پولی است که وارد کرده‌اید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
                            Text(
                                text = "همچنین لازم بذکر است وامهای با کارمزد 4 درصد مانند وام ازدواج در ایران عموما از نوع قرض الحسنه هستند و برای محاسبه اقساط وام قرض الحسنه نوع محاسبه را به گزینه قرض الحسنه تغییر دهید.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
                        }
                    }
                }
            } else {
                // DEPOSIT CALCULATOR SECTION (سپرده)
                item {
                    GlassCard(
                        shape = RoundedCornerShape(24.dp),
                        borderColor = secondaryColor.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // نوع محاسبه
                            BahesabPillSelectorField(
                                label = "نوع محاسبه",
                                selectedValue = depositCalcType.title,
                                onClick = { showDepositTypePicker = true },
                                testTag = "deposit_calc_type_selector"
                            )

                            // مبلغ سپرده ( تومان )
                            BahesabAmountField(
                                value = depositAmount,
                                onValueChange = { depositAmount = it },
                                label = "مبلغ سپرده ( تومان )",
                                placeholder = "مثال: 20,000,000",
                                isEnglishDigits = isEnglish
                            )

                            if (depositCalcType == DepositCalculationType.PROFIT_AMOUNT) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1.35f)) {
                                        BahesabNumericField(
                                            value = depositRate,
                                            onValueChange = { depositRate = it },
                                            label = "درصد سود سالانه",
                                            placeholder = "مثال: 18",
                                            isDecimal = true,
                                            isEnglishDigits = isEnglish
                                        )
                                    }
                                    Box(modifier = Modifier.weight(0.85f)) {
                                        BahesabNumericField(
                                            value = depositDays,
                                            onValueChange = { depositDays = it },
                                            label = "تعداد روز",
                                            placeholder = "مثال: 30",
                                            isDecimal = false,
                                            isEnglishDigits = isEnglish
                                        )
                                    }
                                }
                            } else {
                                BahesabAmountField(
                                    value = depositProfitAmountInput,
                                    onValueChange = { depositProfitAmountInput = it },
                                    label = "مبلغ سود دریافتی ( تومان )",
                                    placeholder = "مثال: 300,000",
                                    isEnglishDigits = isEnglish
                                )

                                BahesabNumericField(
                                    value = depositDays,
                                    onValueChange = { depositDays = it },
                                    label = "تعداد روز",
                                    placeholder = "مثال: 30",
                                    isDecimal = false,
                                    isEnglishDigits = isEnglish
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            BahesabCalculateButton(
                                onClick = { calculateDeposit() },
                                testTag = "calculate_deposit_button"
                            )
                        }
                    }
                }

                if (depositError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = depositError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                if (depositResults.isNotEmpty()) {
                    item {
                        BahesabResultsCard(
                            title = "نتیجه ${depositCalcType.title}",
                            results = depositResults
                        )
                    }
                }

                // فرمول محاسبه سود بانکی (مطابق تصویر ارسالی کاربر)
                item {
                    DodotaExpandableSection(
                        title = "فرمول محاسبه سود بانکی",
                        icon = Icons.Default.Calculate,
                        initiallyExpanded = false
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "چنانچه به دنبال محاسبه نرخ سود سپرده بانکی هستید میتوانید از طریق کادر نوع محاسبه و انتخاب گزینه محاسبه درصد سود سالانه و وارد نمودن مبالغ ، نرخ سود را محاسبه نمایید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                            Text(
                                text = "• مبلغ سود سالانه برابر است با مبلغ اصل سرمایه ضربدر درصد سود سالانه سپرده تقسیم بر 100",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• مبلغ سود هر روز برابر است با مبلغ اصل سرمایه ضربدر درصد سود سالانه سپرده تقسیم بر 36500",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• مبلغ سود ماهانه برابر است با مبلغ سود هر روز ضربدر تعداد روزهای ماه.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog 1: Loan Calculation Type Radio Picker (matching Screenshot 2)
    if (showLoanTypePicker) {
        BahesabRadioPickerDialog(
            title = "انتخاب نوع محاسبه وام",
            options = LoanCalculationType.values().toList(),
            selectedOption = loanCalcType,
            optionTitle = { it.title },
            onSelect = {
                loanCalcType = it
                loanResults = emptyList()
                loanError = null
            },
            onDismiss = { showLoanTypePicker = false }
        )
    }

    // Dialog 2: Deposit Calculation Type Radio Picker (matching Screenshot 4)
    if (showDepositTypePicker) {
        BahesabRadioPickerDialog(
            title = "نوع محاسبه سپرده",
            options = DepositCalculationType.values().toList(),
            selectedOption = depositCalcType,
            optionTitle = { it.title },
            onSelect = {
                depositCalcType = it
                depositResults = emptyList()
                depositError = null
            },
            onDismiss = { showDepositTypePicker = false }
        )
    }

    // Dialog 3: Installment Interval Picker
    if (showIntervalPicker) {
        BahesabRadioPickerDialog(
            title = "فاصله بین اقساط",
            options = InstallmentIntervalOption.values().toList(),
            selectedOption = loanInterval,
            optionTitle = { it.title },
            onSelect = {
                loanInterval = it
                loanResults = emptyList()
            },
            onDismiss = { showIntervalPicker = false }
        )
    }
}

@Composable
private fun BahesabPillSelectorField(
    label: String,
    selectedValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            modifier = Modifier.fillMaxWidth()
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .clickable { onClick() }
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = selectedValue,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = label,
                    tint = primaryColor
                )
            }
        }
    }
}

@Composable
private fun BahesabAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isEnglishDigits: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val formattedDisplay = remember(value, isEnglishDigits) {
        PersianNumberFormatter.formatInputWithCommas(value, isEnglishDigits)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = formattedDisplay,
            onValueChange = { raw ->
                val clean = PersianNumberFormatter.toEnglishDigits(raw)
                    .filter { it.isDigit() }
                onValueChange(clean)
            },
            placeholder = {
                Text(
                    text = PersianNumberFormatter.formatDigits(placeholder, isEnglishDigits),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = primaryColor.copy(alpha = 0.38f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BahesabNumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isDecimal: Boolean,
    isEnglishDigits: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = PersianNumberFormatter.formatDigits(value, isEnglishDigits),
            onValueChange = { raw ->
                val clean = PersianNumberFormatter.toEnglishDigits(raw)
                    .filter { ch -> ch.isDigit() || (isDecimal && (ch == '.' || ch == '٫')) }
                    .replace('٫', '.')
                onValueChange(clean)
            },
            placeholder = {
                Text(
                    text = PersianNumberFormatter.formatDigits(placeholder, isEnglishDigits),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = primaryColor.copy(alpha = 0.38f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BahesabCalculateButton(
    onClick: () -> Unit,
    testTag: String
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.5.dp,
            color = primaryColor.copy(alpha = 0.45f)
        )
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 42.dp, vertical = 13.dp),
            modifier = Modifier.testTag(testTag)
        ) {
            Icon(
                Icons.Default.Calculate,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "محاسبه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.5.dp,
            color = primaryColor.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun BahesabResultsCard(
    title: String,
    results: List<ResultItem>
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    GlassCard(
        shape = RoundedCornerShape(24.dp),
        borderColor = IncomeGreen.copy(alpha = 0.65f),
        isHighlighted = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IncomeGreen.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = IncomeGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            results.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (item.isHighlighted) {
                        primaryColor.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (item.isHighlighted) primaryColor.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (item.isHighlighted) FontWeight.ExtraBold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.value,
                            style = if (item.isHighlighted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (item.isHighlighted) primaryColor else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> BahesabRadioPickerDialog(
    title: String,
    options: List<T>,
    selectedOption: T,
    optionTitle: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelected by remember(selectedOption) { mutableStateOf(selectedOption) }
    val currentIndex = options.indexOf(tempSelected).coerceAtLeast(0)

    GraphicalFormDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Default.Tune,
        accentColor = MaterialTheme.colorScheme.primary,
        confirmText = "انجام",
        dismissText = "انصراف",
        onConfirm = {
            onSelect(tempSelected)
            onDismiss()
        }
    ) {
        // قبلی / بعدی quick navigation buttons like in the screenshot
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = {
                    if (currentIndex > 0) tempSelected = options[currentIndex - 1]
                },
                enabled = currentIndex > 0,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("قبلی", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    if (currentIndex < options.lastIndex) tempSelected = options[currentIndex + 1]
                },
                enabled = currentIndex < options.lastIndex,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("بعدی", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == tempSelected
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            tempSelected = option
                            onSelect(option)
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = optionTitle(option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                tempSelected = option
                                onSelect(option)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}
