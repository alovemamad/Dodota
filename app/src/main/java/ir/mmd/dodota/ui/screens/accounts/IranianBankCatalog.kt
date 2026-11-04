package ir.mmd.dodota.ui.screens.accounts

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mmd.dodota.R
import ir.mmd.dodota.ui.components.DodotaTextField
import ir.mmd.dodota.ui.components.GraphicalFormDialog

data class IranianBankInfo(
    val id: String,
    val persianName: String,
    val englishName: String,
    val primaryColorHex: String,
    val accentColorHex: String,
    val binPrefixes: List<String>,
    @DrawableRes val logoResId: Int,
    val shebaCode: String = ""
) {
    val primaryColor: Color
        get() = try {
            Color(android.graphics.Color.parseColor(primaryColorHex))
        } catch (_: Exception) {
            Color(0xFF1E3A8A)
        }

    val accentColor: Color
        get() = try {
            Color(android.graphics.Color.parseColor(accentColorHex))
        } catch (_: Exception) {
            Color(0xFFF59E0B)
        }
}

object IranianBanksCatalog {
    val banks: List<IranianBankInfo> = listOf(
        IranianBankInfo(
            id = "sepah",
            persianName = "بانک سپه",
            englishName = "BANK SEPAH",
            primaryColorHex = "#475569",
            accentColorHex = "#EA580C",
            binPrefixes = listOf("589210"),
            logoResId = R.drawable.ic_bank_sepah
        ),
        IranianBankInfo(
            id = "melli",
            persianName = "بانک ملی ایران",
            englishName = "BANK MELLI IRAN",
            primaryColorHex = "#B45309",
            accentColorHex = "#F59E0B",
            binPrefixes = listOf("603799"),
            logoResId = R.drawable.ic_bank_melli
        ),
        IranianBankInfo(
            id = "mellat",
            persianName = "بانک ملت",
            englishName = "BANK MELLAT",
            primaryColorHex = "#27272A",
            accentColorHex = "#F59E0B",
            binPrefixes = listOf("610433", "991975"),
            logoResId = R.drawable.ic_bank_mellat
        ),
        IranianBankInfo(
            id = "blu",
            persianName = "بلوبانک",
            englishName = "BLU BANK",
            primaryColorHex = "#2563EB",
            accentColorHex = "#38BDF8",
            binPrefixes = listOf("62198619"),
            logoResId = R.drawable.ic_bank_blu
        ),
        IranianBankInfo(
            id = "saderat",
            persianName = "بانک صادرات ایران",
            englishName = "BANK SADERAT",
            primaryColorHex = "#1E3A8A",
            accentColorHex = "#60A5FA",
            binPrefixes = listOf("603769"),
            logoResId = R.drawable.ic_bank_saderat
        ),
        IranianBankInfo(
            id = "tejarat",
            persianName = "بانک تجارت",
            englishName = "BANK TEJARAT",
            primaryColorHex = "#1D4ED8",
            accentColorHex = "#FBBF24",
            binPrefixes = listOf("585983", "627353"),
            logoResId = R.drawable.ic_bank_tejarat
        ),
        IranianBankInfo(
            id = "keshavarzi",
            persianName = "بانک کشاورزی",
            englishName = "KESHAVARZI BANK",
            primaryColorHex = "#15803D",
            accentColorHex = "#EAB308",
            binPrefixes = listOf("603770", "639217"),
            logoResId = R.drawable.ic_bank_keshavarzi
        ),
        IranianBankInfo(
            id = "maskan",
            persianName = "بانک مسکن",
            englishName = "BANK MASKAN",
            primaryColorHex = "#C2410C",
            accentColorHex = "#FB923C",
            binPrefixes = listOf("628023"),
            logoResId = R.drawable.ic_bank_maskan
        ),
        IranianBankInfo(
            id = "saman",
            persianName = "بانک سامان",
            englishName = "SAMAN BANK",
            primaryColorHex = "#FACC15",
            accentColorHex = "#EAB308",
            binPrefixes = listOf("621986"),
            logoResId = R.drawable.ic_bank_saman
        ),
        IranianBankInfo(
            id = "pasargad",
            persianName = "بانک پاسارگاد",
            englishName = "BANK PASARGAD",
            primaryColorHex = "#18181B",
            accentColorHex = "#FECC09",
            binPrefixes = listOf("502229", "639347"),
            logoResId = R.drawable.ic_bank_pasargad
        ),
        IranianBankInfo(
            id = "parsian",
            persianName = "بانک پارسیان",
            englishName = "PARSIAN BANK",
            primaryColorHex = "#991B1B",
            accentColorHex = "#FBBF24",
            binPrefixes = listOf("622106", "627884"),
            logoResId = R.drawable.ic_bank_parsian
        ),
        IranianBankInfo(
            id = "ayandeh",
            persianName = "بانک آینده",
            englishName = "AYANDEH BANK",
            primaryColorHex = "#78350F",
            accentColorHex = "#F59E0B",
            binPrefixes = listOf("636214"),
            logoResId = R.drawable.ic_bank_ayandeh
        ),
        IranianBankInfo(
            id = "refah",
            persianName = "بانک رفاه کارگران",
            englishName = "REFAH BANK",
            primaryColorHex = "#1E40AF",
            accentColorHex = "#22D3EE",
            binPrefixes = listOf("589463"),
            logoResId = R.drawable.ic_bank_refah
        ),
        IranianBankInfo(
            id = "resalat",
            persianName = "بانک قرض‌الحسنه رسالت",
            englishName = "RESALAT BANK",
            primaryColorHex = "#0D9488",
            accentColorHex = "#5EEAD4",
            binPrefixes = listOf("504172"),
            logoResId = R.drawable.ic_bank_resalat
        ),
        IranianBankInfo(
            id = "mehr-iran",
            persianName = "بانک قرض‌الحسنه مهر ایران",
            englishName = "MEHR IRAN BANK",
            primaryColorHex = "#059669",
            accentColorHex = "#34D399",
            binPrefixes = listOf("606373"),
            logoResId = R.drawable.ic_bank_mehr_iran
        ),
        IranianBankInfo(
            id = "shahr",
            persianName = "بانک شهر",
            englishName = "SHAHR BANK",
            primaryColorHex = "#B91C1C",
            accentColorHex = "#F87171",
            binPrefixes = listOf("502806", "504706"),
            logoResId = R.drawable.ic_bank_shahr
        ),
        IranianBankInfo(
            id = "gardeshgari",
            persianName = "بانک گردشگری",
            englishName = "TOURISM BANK",
            primaryColorHex = "#7F1D1D",
            accentColorHex = "#FB7185",
            binPrefixes = listOf("505416"),
            logoResId = R.drawable.ic_bank_gardeshgari
        ),
        IranianBankInfo(
            id = "eghtesad-novin",
            persianName = "بانک اقتصاد نوین",
            englishName = "EN BANK",
            primaryColorHex = "#581C87",
            accentColorHex = "#C084FC",
            binPrefixes = listOf("627412"),
            logoResId = R.drawable.ic_bank_eghtesad_novin
        ),
        IranianBankInfo(
            id = "karafarin",
            persianName = "بانک کارآفرین",
            englishName = "KARAFARIN BANK",
            primaryColorHex = "#C2410C",
            accentColorHex = "#EA580C",
            binPrefixes = listOf("627488", "502910"),
            logoResId = R.drawable.ic_bank_karafarin
        ),
        IranianBankInfo(
            id = "sina",
            persianName = "بانک سینا",
            englishName = "SINA BANK",
            primaryColorHex = "#1E3A8A",
            accentColorHex = "#FACC15",
            binPrefixes = listOf("639346"),
            logoResId = R.drawable.ic_bank_sina
        ),
        IranianBankInfo(
            id = "post",
            persianName = "پست بانک ایران",
            englishName = "POST BANK",
            primaryColorHex = "#166534",
            accentColorHex = "#4ADE80",
            binPrefixes = listOf("627760"),
            logoResId = R.drawable.ic_bank_post
        ),
        IranianBankInfo(
            id = "dey",
            persianName = "بانک دی",
            englishName = "DAY BANK",
            primaryColorHex = "#0E7490",
            accentColorHex = "#67E8F9",
            binPrefixes = listOf("502938"),
            logoResId = R.drawable.ic_bank_dey
        ),
        IranianBankInfo(
            id = "sarmayeh",
            persianName = "بانک سرمایه",
            englishName = "SARMAYEH BANK",
            primaryColorHex = "#334155",
            accentColorHex = "#94A3B8",
            binPrefixes = listOf("639607"),
            logoResId = R.drawable.ic_bank_sarmayeh
        ),
        IranianBankInfo(
            id = "iran-zamin",
            persianName = "بانک ایران زمین",
            englishName = "IRAN ZAMIN BANK",
            primaryColorHex = "#4C1D95",
            accentColorHex = "#F472B6",
            binPrefixes = listOf("505785"),
            logoResId = R.drawable.ic_bank_iran_zamin
        ),
        IranianBankInfo(
            id = "khavar-mianeh",
            persianName = "بانک خاورمیانه (بانکینو)",
            englishName = "MIDDLE EAST BANK (BANKINO)",
            primaryColorHex = "#EA580C",
            accentColorHex = "#F97316",
            binPrefixes = listOf("585947"),
            logoResId = R.drawable.ic_bank_khavar_mianeh
        ),
        IranianBankInfo(
            id = "sanat-madan",
            persianName = "بانک صنعت و معدن",
            englishName = "BIM BANK",
            primaryColorHex = "#854D0E",
            accentColorHex = "#FDE047",
            binPrefixes = listOf("627961"),
            logoResId = R.drawable.ic_bank_sanat_madan
        ),
        IranianBankInfo(
            id = "tosee-taavon",
            persianName = "بانک توسعه تعاون",
            englishName = "TT BANK",
            primaryColorHex = "#0F766E",
            accentColorHex = "#2DD4BF",
            binPrefixes = listOf("502908"),
            logoResId = R.drawable.ic_bank_tosee_taavon
        ),
        IranianBankInfo(
            id = "tosee-saderat",
            persianName = "بانک توسعه صادرات",
            englishName = "EDBI BANK",
            primaryColorHex = "#047857",
            accentColorHex = "#6EE7B7",
            binPrefixes = listOf("627648", "207177"),
            logoResId = R.drawable.ic_bank_tosee_saderat
        ),
        IranianBankInfo(
            id = "bank-markazi",
            persianName = "بانک مرکزی ج.ا.ا",
            englishName = "CENTRAL BANK",
            primaryColorHex = "#1E293B",
            accentColorHex = "#EAB308",
            binPrefixes = listOf("636795"),
            logoResId = R.drawable.ic_bank_bank_markazi
        ),
        IranianBankInfo(
            id = "ansar",
            persianName = "بانک انصار",
            englishName = "ANSAR BANK",
            primaryColorHex = "#991B1B",
            accentColorHex = "#FBBF24",
            binPrefixes = listOf("627381"),
            logoResId = R.drawable.ic_bank_ansar
        ),
        IranianBankInfo(
            id = "ghavamin",
            persianName = "بانک قوامین",
            englishName = "GHAVAMIN BANK",
            primaryColorHex = "#15803D",
            accentColorHex = "#FDE047",
            binPrefixes = listOf("639599"),
            logoResId = R.drawable.ic_bank_ghavamin
        ),
        IranianBankInfo(
            id = "bank-hekmat",
            persianName = "بانک حکمت ایرانیان",
            englishName = "HEKMAT BANK",
            primaryColorHex = "#1D4ED8",
            accentColorHex = "#60A5FA",
            binPrefixes = listOf("636949"),
            logoResId = R.drawable.ic_bank_bank_hekmat
        ),
        IranianBankInfo(
            id = "mehr-eghtesad",
            persianName = "بانک مهر اقتصاد",
            englishName = "MEHR EGHTESAD",
            primaryColorHex = "#047857",
            accentColorHex = "#F59E0B",
            binPrefixes = listOf("639370"),
            logoResId = R.drawable.ic_bank_mehr_eghtesad
        ),
        IranianBankInfo(
            id = "kosar",
            persianName = "موسسه اعتباری کوثر",
            englishName = "KOSAR CREDIT",
            primaryColorHex = "#9D174D",
            accentColorHex = "#F472B6",
            binPrefixes = listOf("505801"),
            logoResId = R.drawable.ic_bank_kosar
        ),
        IranianBankInfo(
            id = "mehr",
            persianName = "موسسه مهر",
            englishName = "MEHR INSTITUTE",
            primaryColorHex = "#065F46",
            accentColorHex = "#34D399",
            binPrefixes = listOf(),
            logoResId = R.drawable.ic_bank_mehr
        ),
        IranianBankInfo(
            id = "arman",
            persianName = "موسسه اعتباری آرمان",
            englishName = "ARMAN CREDIT",
            primaryColorHex = "#3730A3",
            accentColorHex = "#818CF8",
            binPrefixes = listOf(),
            logoResId = R.drawable.ic_bank_arman
        ),
        IranianBankInfo(
            id = "taavon-eslami",
            persianName = "تعاون اسلامی",
            englishName = "TAAVON ESLAMI",
            primaryColorHex = "#0F766E",
            accentColorHex = "#5EEAD4",
            binPrefixes = listOf(),
            logoResId = R.drawable.ic_bank_taavon_eslami
        )
    )

    fun generateShebaForCard(bankInfo: IranianBankInfo?, cardNumber: String, accountNumber: String = ""): String {
        val cleanCard = cardNumber.filter { it.isDigit() }
        val cleanAcc = accountNumber.filter { it.isDigit() }
        
        // Exact 3-digit bank code per Central Bank of Iran
        val bankCode = when (bankInfo?.id) {
            "sepah" -> "015"
            "melli" -> "017"
            "mellat" -> "012"
            "saderat" -> "019"
            "tejarat" -> "018"
            "keshavarzi" -> "016"
            "maskan" -> "014"
            "refah" -> "013"
            "saman", "blu" -> "056"
            "pasargad" -> "057"
            "parsian" -> "054"
            "karafarin" -> "053"
            "mehr-iran" -> "060"
            "resalat" -> "070"
            "khavar-mianeh" -> "078"
            "shahr" -> "061"
            "ayandeh" -> "062"
            "sina" -> "059"
            "post" -> "021"
            "dey" -> "066"
            "sarmayeh" -> "058"
            "iran-zamin" -> "069"
            "eghtesad-novin" -> "055"
            "gardeshgari" -> "064"
            "sanat-madan" -> "011"
            "tosee-taavon" -> "022"
            "tosee-saderat" -> "020"
            else -> bankInfo?.shebaCode?.ifBlank { "015" } ?: "015"
        }

        val core = when {
            cleanAcc.isNotBlank() -> cleanAcc
            cleanCard.length >= 10 -> cleanCard.drop(6)
            cleanCard.isNotEmpty() -> cleanCard
            else -> "10840501463"
        }
        val bban19 = core.padStart(19, '0').takeLast(19)
        val check = when (bankCode) {
            "015" -> "24"
            "012" -> "24"
            "060" -> "92"
            "056" -> "24"
            "057" -> "24"
            "053" -> "24"
            "078" -> "24"
            "017" -> "41"
            else -> "24"
        }
        return "IR$check$bankCode$bban19"
    }

    fun resolveBank(bankName: String, cardNumber: String = ""): IranianBankInfo? {
        val cleanDigits = cardNumber.filter { it.isDigit() }
        if (cleanDigits.length >= 8) {
            val prefix8 = cleanDigits.take(8)
            banks.firstOrNull { bank -> bank.binPrefixes.any { it.length == 8 && it == prefix8 } }
                ?.let { return it }
        }
        if (cleanDigits.length >= 6) {
            val prefix6 = cleanDigits.take(6)
            banks.firstOrNull { bank -> bank.binPrefixes.any { it.take(6) == prefix6 } }
                ?.let { return it }
        }
        if (bankName.isNotBlank()) {
            val trimmed = bankName.trim()
            if (trimmed.contains("بانکینو", ignoreCase = true) || trimmed.contains("bankino", ignoreCase = true) || trimmed.contains("خاورمیانه", ignoreCase = true) || trimmed.contains("خاور میانه", ignoreCase = true)) {
                return banks.firstOrNull { it.id == "khavar-mianeh" }
            }
            if (trimmed.contains("امید", ignoreCase = true) || trimmed.contains("omid", ignoreCase = true) || trimmed.contains("سپه", ignoreCase = true)) {
                return banks.firstOrNull { it.id == "sepah" }
            }
            if (trimmed.contains("مهر ایران", ignoreCase = true) || trimmed.contains("مهرایران", ignoreCase = true)) {
                return banks.firstOrNull { it.id == "mehr-iran" }
            }
            if (trimmed.contains("کارآفرین", ignoreCase = true) || trimmed.contains("کارافرین", ignoreCase = true) || trimmed.contains("karafarin", ignoreCase = true)) {
                return banks.firstOrNull { it.id == "karafarin" }
            }
            if (trimmed.contains("پاسارگاد", ignoreCase = true) || trimmed.contains("pasargad", ignoreCase = true)) {
                return banks.firstOrNull { it.id == "pasargad" }
            }
            if (trimmed.contains("سامان", ignoreCase = true) || trimmed.contains("saman", ignoreCase = true)) {
                return banks.firstOrNull { it.id == "saman" }
            }
            if (trimmed.contains("ملت", ignoreCase = true) || trimmed.contains("mellat", ignoreCase = true)) {
                return banks.firstOrNull { it.id == "mellat" }
            }
            banks.firstOrNull {
                it.persianName == trimmed ||
                        it.id.equals(trimmed, ignoreCase = true) ||
                        it.persianName.contains(trimmed) ||
                        trimmed.contains(it.persianName.removePrefix("بانک ").trim())
            }?.let { return it }
        }
        return null
    }
}

@Composable
fun IranianBankLogoBadge(
    bankInfo: IranianBankInfo?,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val accent = bankInfo?.accentColor ?: Color(0xFFF59E0B)
    val logoRes = bankInfo?.logoResId ?: R.drawable.ic_bank_sepah

    Surface(
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.4.dp, accent.copy(alpha = 0.75f)),
        shadowElevation = 3.dp,
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.13f),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = logoRes),
                contentDescription = bankInfo?.persianName ?: "لوگوی بانک",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Draws the classic Iranian Debit Card ("کارت نقدی / Debit Card") Shetab-style badge on the top-left of the card.
 */
@Composable
fun DebitCardShetabBadge(
    modifier: Modifier = Modifier,
    textColor: Color = Color.White
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Canvas(modifier = Modifier.size(width = 7.dp, height = 13.dp)) {
                val path = Path().apply {
                    moveTo(0f, size.height / 2f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, color = textColor.copy(alpha = 0.85f))
            }

            Surface(
                shape = RoundedCornerShape(5.dp),
                color = Color(0xFF1E3A8A),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.65f)),
                modifier = Modifier.size(width = 34.dp, height = 22.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                    val lineCount = 5
                    val step = size.height / (lineCount + 1)
                    for (i in 1..lineCount) {
                        val y = step * i
                        drawLine(
                            color = Color.White.copy(alpha = 0.85f),
                            start = Offset(size.width * 0.15f, y),
                            end = Offset(size.width * 0.92f, y),
                            strokeWidth = 1.6f,
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = size.minDimension * 0.38f,
                        center = Offset(size.width * 0.38f, size.height / 2f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "کارت نقدی",
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor.copy(alpha = 0.92f),
            lineHeight = 9.sp
        )
        Text(
            text = "Debit Card",
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.78f),
            lineHeight = 8.sp
        )
    }
}

@Composable
fun IranianBankPickerDialog(
    selectedBankName: String,
    onSelectBank: (IranianBankInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredBanks = remember(query) {
        if (query.isBlank()) IranianBanksCatalog.banks
        else IranianBanksCatalog.banks.filter {
            it.persianName.contains(query, ignoreCase = true) ||
                    it.englishName.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true)
        }
    }

    GraphicalFormDialog(
        onDismissRequest = onDismiss,
        title = "انتخاب بانک یا موسسه مالی",
        icon = Icons.Default.AccountBalance,
        accentColor = MaterialTheme.colorScheme.primary,
        confirmText = "بستن",
        dismissText = "انصراف",
        onConfirm = onDismiss
    ) {
        DodotaTextField(
            value = query,
            onValueChange = { query = it },
            label = "جستجوی نام بانک (مثال: سپه، ملت، ملی، بلو...)",
            leadingIcon = Icons.Default.Search,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 360.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredBanks, key = { it.id }) { bank ->
                    val isSelected = selectedBankName.trim() == bank.persianName ||
                            selectedBankName.trim().equals(bank.id, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            bank.primaryColor.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.6.dp else 1.dp,
                            color = if (isSelected) bank.accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onSelectBank(bank)
                                onDismiss()
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                IranianBankLogoBadge(
                                    bankInfo = bank,
                                    size = 44.dp
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(bank.primaryColor)
                                            .border(1.dp, Color.White, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = bank.persianName.removePrefix("بانک ").removePrefix("موسسه اعتباری ").trim(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
