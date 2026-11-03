package ir.mmd.dodota.ui.screens.accounts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mmd.dodota.data.model.AccountEntity
import ir.mmd.dodota.data.model.AccountType
import ir.mmd.dodota.data.preferences.UserPreferences
import ir.mmd.dodota.data.repository.DodotaRepository
import ir.mmd.dodota.ui.components.DodotaTextField
import ir.mmd.dodota.ui.components.DodotaTopBar
import ir.mmd.dodota.ui.components.EmptyStateCard
import ir.mmd.dodota.ui.components.GraphicalFormDialog
import ir.mmd.dodota.ui.components.PersianAmountInput
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlinx.coroutines.launch

@Composable
fun CardsManagerScreen(
    accounts: List<AccountEntity>,
    userPreferences: UserPreferences,
    repository: DodotaRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var deletingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val activeCards = remember(accounts, searchQuery) {
        accounts.filter { it.isActive }.filter { acc ->
            if (searchQuery.isBlank()) true
            else acc.name.contains(searchQuery, ignoreCase = true) ||
                    acc.bankName.contains(searchQuery, ignoreCase = true) ||
                    acc.cardNumber.contains(searchQuery, ignoreCase = true) ||
                    acc.cardHolderName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DodotaTopBar(
                title = "کارت‌ها",
                onBackClick = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            editingAccount = null
                            showAddOrEditDialog = true
                        }
                    ) {
                        Icon(
                            Icons.Default.AddCard,
                            contentDescription = "افزودن کارت جدید",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingAccount = null
                    showAddOrEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                icon = { Icon(Icons.Default.AddCard, contentDescription = null) },
                text = { Text("ثبت کارت جدید", fontWeight = FontWeight.ExtraBold) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                DodotaTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "جستجوی نام کارت، بانک، شماره کارت یا صاحب کارت...",
                    leadingIcon = Icons.Default.Search,
                    trailingContent = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (activeCards.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "کارتی ثبت نشده است",
                        description = "",
                        icon = Icons.Outlined.CreditCard
                    )
                }
            } else {
                items(activeCards, key = { it.id }) { cardAccount ->
                    RealisticIranianBankCardItem(
                        account = cardAccount,
                        currency = userPreferences.currency,
                        onCopyField = { label, textToCopy ->
                            copyToClipboard(context, label, textToCopy)
                        },
                        onEdit = {
                            editingAccount = cardAccount
                            showAddOrEditDialog = true
                        },
                        onDelete = {
                            deletingAccount = cardAccount
                        }
                    )
                }
            }
        }
    }

    if (showAddOrEditDialog) {
        AddOrEditBankCardDialog(
            initialAccount = editingAccount,
            currency = userPreferences.currency,
            onDismiss = {
                showAddOrEditDialog = false
                editingAccount = null
            },
            onSave = { updatedEntity ->
                coroutineScope.launch {
                    if (editingAccount == null) {
                        repository.insertAccount(updatedEntity)
                        Toast.makeText(context, "کارت جدید ثبت شد", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.updateAccount(updatedEntity)
                        Toast.makeText(context, "اطلاعات کارت ویرایش شد", Toast.LENGTH_SHORT).show()
                    }
                    showAddOrEditDialog = false
                    editingAccount = null
                }
            }
        )
    }

    deletingAccount?.let { acc ->
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            title = { Text("حذف کارت بانکی", fontWeight = FontWeight.ExtraBold) },
            text = { Text("آیا از حذف کارت «${acc.name}» اطمینان دارید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteAccountSafely(acc)
                            Toast.makeText(context, "کارت حذف شد", Toast.LENGTH_SHORT).show()
                            deletingAccount = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAccount = null }) {
                    Text("انصراف")
                }
            }
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    if (value.isBlank()) {
        Toast.makeText(context, "$label ثبت نشده است", Toast.LENGTH_SHORT).show()
        return
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label کپی شد", Toast.LENGTH_SHORT).show()
}

private fun formatCardNumberGroups(raw: String, useEnglish: Boolean): String {
    val clean = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() }.take(16)
    if (clean.isEmpty()) return "----   ----   ----   ----"
    val grouped = clean.chunked(4).joinToString("   ")
    return PersianNumberFormatter.formatDigits(grouped, useEnglish)
}

private fun parseExpiryYearAndMonth(raw: String): Pair<String, String> {
    val normalized = PersianNumberFormatter.toEnglishDigits(raw).trim()
    if (normalized.isEmpty()) return "" to ""
    val parts = normalized.split('/', ' ', '-').map { it.filter { ch -> ch.isDigit() } }.filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> parts[0].takeLast(2) to parts[1].takeLast(2)
        else -> {
            val digits = normalized.filter { it.isDigit() }
            when {
                digits.length >= 4 -> digits.take(2) to digits.substring(2, 4)
                digits.length > 2 -> digits.take(2) to digits.drop(2).take(2)
                else -> digits.take(2) to ""
            }
        }
    }
}

/**
 * Realistic Iranian Physical Bank Card (matching the user's physical Bank Sepah card layout & proportions).
 *
 * Layout from top to bottom (RTL aware, matching physical card):
 * - Top Row:
 *   Right side: Official Bank Logo + Persian Bank Name ("بانک سپه") + English Subtitle ("BANK SEPAH")
 *   Left side: Classic Shetab "کارت نقدی / Debit Card" badge + Quick Edit/Delete/CopyAll icons
 * - Upper-Center above curve: Account Number (شماره حساب) e.g. 313138809 (tappable to copy)
 * - Middle Curved Horizon Line: Signature two-tone metallic upper body + arabesque patterned lower body
 * - Along the Horizon Curve: `شبا: IR240150000010840501463560` (tappable to copy)
 * - Center: Large embossed 16-digit Card Number `5892   1016   2076   1128` (tappable to copy)
 * - Below Card Number: Cardholder Name (`محمدرضا حسین پور`) (tappable to copy)
 * - Bottom Corners:
 *   Right Corner: `تاریخ انقضا: 08/08` (tappable to copy)
 *   Left Corner: `CVV2: 704` (tappable to copy)
 */
@Composable
private fun RealisticIranianBankCardItem(
    account: AccountEntity,
    currency: ir.mmd.dodota.data.model.AppCurrency,
    onCopyField: (String, String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val bankInfo = remember(account.bankName, account.cardNumber) {
        IranianBanksCatalog.resolveBank(account.bankName, account.cardNumber)
    }

    val baseColor = remember(account.colorHex, bankInfo) {
        bankInfo?.primaryColor ?: try {
            Color(android.graphics.Color.parseColor(account.colorHex))
        } catch (_: Exception) {
            Color(0xFF334155)
        }
    }
    val horizonAccentColor = bankInfo?.accentColor ?: Color(0xFFF97316)

    val normalizedSheba = remember(account.shebaNumber) {
        if (account.shebaNumber.isBlank()) ""
        else if (account.shebaNumber.uppercase().startsWith("IR")) account.shebaNumber.uppercase()
        else "IR${account.shebaNumber}"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Physical ISO/IEC 7810 ID-1 Bank Card Aspect Ratio (~1.586f) - طراحی کاملاً هماهنگ با هویت و لوگوی بانک
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            border = BorderStroke(
                1.3.dp,
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        horizonAccentColor.copy(alpha = 0.85f),
                        baseColor.copy(alpha = 0.50f),
                        horizonAccentColor.copy(alpha = 0.70f)
                    )
                )
            ),
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.58f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Realistic Coordinated Bank Card Canvas Background (طراحی پس‌زمینه اختصاصی و هماهنگ با لوگو)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val bankId = bankInfo?.id ?: ""

                    // 1. Dynamic Bank Brand Palette Gradients
                    val gradientColors = when (bankId) {
                        "blu" -> listOf(Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF0F172A))
                        "pasargad" -> listOf(Color(0xFF262626), Color(0xFF18181B), Color(0xFF09090B))
                        "mellat" -> listOf(Color(0xFFDC2626), Color(0xFF991B1B), Color(0xFF450A0A))
                        "melli" -> listOf(Color(0xFFB91C1C), Color(0xFF881337), Color(0xFF0F172A))
                        "saman" -> listOf(Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF082F49))
                        "keshavarzi" -> listOf(Color(0xFF16A34A), Color(0xFF14532D), Color(0xFF052E16))
                        "tejarat" -> listOf(Color(0xFF2563EB), Color(0xFF1E40AF), Color(0xFF172554))
                        "sepah" -> listOf(Color(0xFF475569), Color(0xFF334155), Color(0xFF0F172A))
                        "maskan" -> listOf(Color(0xFFEA580C), Color(0xFFC2410C), Color(0xFF431407))
                        "refah" -> listOf(Color(0xFF1E40AF), Color(0xFF172554), Color(0xFF0F172A))
                        "saderat" -> listOf(Color(0xFF312E81), Color(0xFF1E1B4B), Color(0xFF0F172A))
                        "resalat" -> listOf(Color(0xFF0891B2), Color(0xFF0E7490), Color(0xFF155E75))
                        "mehr-iran" -> listOf(Color(0xFF16A34A), Color(0xFF15803D), Color(0xFF052E16))
                        "parsian" -> listOf(Color(0xFF9A3412), Color(0xFF78350F), Color(0xFF291004))
                        "ayandeh" -> listOf(Color(0xFF881337), Color(0xFF701A75), Color(0xFF2E0827))
                        "shahr" -> listOf(Color(0xFFDC2626), Color(0xFF991B1B), Color(0xFF450A0A))
                        "eghtesad-novin" -> listOf(Color(0xFF7E22CE), Color(0xFF581C87), Color(0xFF1E0A38))
                        "gardeshgari" -> listOf(Color(0xFF9F1239), Color(0xFF881337), Color(0xFF2E0814))
                        "karafarin" -> listOf(Color(0xFF047857), Color(0xFF065F46), Color(0xFF022C20))
                        "sina" -> listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A), Color(0xFF0F172A))
                        "iran-zamin" -> listOf(Color(0xFF581C87), Color(0xFF4C1D95), Color(0xFF1E0A38))
                        "khavar-mianeh" -> listOf(Color(0xFFC2410C), Color(0xFF9A3412), Color(0xFF3B1204))
                        "dey" -> listOf(Color(0xFF0891B2), Color(0xFF0E7490), Color(0xFF164E63))
                        "sarmayeh" -> listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF0F172A))
                        else -> listOf(
                            baseColor.copy(alpha = 0.95f),
                            baseColor,
                            Color(0xFF0F172A)
                        )
                    }

                    drawRect(
                        brush = Brush.linearGradient(
                            colors = gradientColors,
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        ),
                        size = size
                    )

                    // 2. Atmospheric Bank Brand Aura (هاله نورانی برند در زیر لوگو در بالا راست)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                horizonAccentColor.copy(alpha = 0.32f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.82f, h * 0.18f),
                            radius = w * 0.40f
                        ),
                        radius = w * 0.40f,
                        center = Offset(w * 0.82f, h * 0.18f)
                    )

                    // 3. Bank-Specific Artistic Curves & Signature Waves (طرح و نقش خطوط هماهنگ با بانک)
                    when (bankId) {
                        "pasargad" -> {
                            // Pasargad Luxury 24K Gold Arcs & Subtle Gold Sheen
                            val goldWave1 = Path().apply {
                                moveTo(0f, h * 0.42f)
                                cubicTo(w * 0.35f, h * 0.30f, w * 0.65f, h * 0.55f, w, h * 0.38f)
                                lineTo(w, h * 0.42f)
                                cubicTo(w * 0.65f, h * 0.59f, w * 0.35f, h * 0.34f, 0f, h * 0.46f)
                                close()
                            }
                            drawPath(
                                path = goldWave1,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFECC09).copy(alpha = 0.75f),
                                        Color(0xFFFBBF24),
                                        Color(0xFFFECC09).copy(alpha = 0.85f)
                                    )
                                )
                            )
                            val goldWave2 = Path().apply {
                                moveTo(0f, h * 0.72f)
                                cubicTo(w * 0.45f, h * 0.60f, w * 0.75f, h * 0.85f, w, h * 0.70f)
                            }
                            drawPath(
                                path = goldWave2,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFFFECC09).copy(alpha = 0.45f),
                                        Color(0xFFFBBF24).copy(alpha = 0.65f)
                                    )
                                ),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                        "blu" -> {
                            // Blu Bank Neo-Bank Minimalist Cyber Waves
                            val cyberWave = Path().apply {
                                moveTo(0f, h * 0.62f)
                                cubicTo(w * 0.30f, h * 0.45f, w * 0.70f, h * 0.75f, w, h * 0.50f)
                            }
                            drawPath(
                                path = cyberWave,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF38BDF8).copy(alpha = 0.65f),
                                        Color(0xFF60A5FA).copy(alpha = 0.85f),
                                        Color(0xFF38BDF8).copy(alpha = 0.50f)
                                    )
                                ),
                                style = Stroke(width = 2.6.dp.toPx())
                            )
                            drawCircle(
                                color = Color(0xFF38BDF8).copy(alpha = 0.08f),
                                radius = w * 0.55f,
                                center = Offset(w * 0.90f, h * 0.80f)
                            )
                        }
                        "mellat" -> {
                            // Mellat Ruby-Red Gold Sunburst Curve (subtle watermark)
                            val mellatWave = Path().apply {
                                moveTo(0f, h * 0.52f)
                                quadraticBezierTo(w * 0.50f, h * 0.42f, w, h * 0.55f)
                            }
                            drawPath(
                                path = mellatWave,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFFC730).copy(alpha = 0.35f),
                                        Color(0xFFFBBF24).copy(alpha = 0.40f),
                                        Color(0xFFFFC730).copy(alpha = 0.30f)
                                    )
                                ),
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                            drawCircle(
                                color = Color(0xFFFFC730).copy(alpha = 0.04f),
                                radius = w * 0.48f,
                                center = Offset(w * 0.85f, h * 0.45f)
                            )
                        }
                        "melli" -> {
                            // Melli Imperial Gold Flourish Waves (subtle watermark)
                            val melliWave = Path().apply {
                                moveTo(0f, h * 0.50f)
                                cubicTo(w * 0.25f, h * 0.42f, w * 0.75f, h * 0.58f, w, h * 0.48f)
                            }
                            drawPath(
                                path = melliWave,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFF59E0B).copy(alpha = 0.35f),
                                        Color(0xFFFEF08A).copy(alpha = 0.40f),
                                        Color(0xFFF59E0B).copy(alpha = 0.35f)
                                    )
                                ),
                                style = Stroke(width = 1.6.dp.toPx())
                            )
                        }
                        "saman" -> {
                            // Saman Oceanic Wave Ripples
                            val samanWave1 = Path().apply {
                                moveTo(0f, h * 0.48f)
                                cubicTo(w * 0.35f, h * 0.40f, w * 0.65f, h * 0.60f, w, h * 0.52f)
                            }
                            val samanWave2 = Path().apply {
                                moveTo(0f, h * 0.56f)
                                cubicTo(w * 0.35f, h * 0.48f, w * 0.65f, h * 0.68f, w, h * 0.60f)
                            }
                            drawPath(
                                path = samanWave1,
                                color = Color(0xFF7DCDF1).copy(alpha = 0.30f),
                                style = Stroke(width = 1.6.dp.toPx())
                            )
                            drawPath(
                                path = samanWave2,
                                color = Color.White.copy(alpha = 0.20f),
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }
                        "keshavarzi" -> {
                            // Keshavarzi Harvest Wheat Curve
                            val wheatCurve = Path().apply {
                                moveTo(0f, h * 0.55f)
                                quadraticBezierTo(w * 0.48f, h * 0.46f, w, h * 0.58f)
                            }
                            drawPath(
                                path = wheatCurve,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFCA8A04).copy(alpha = 0.35f),
                                        Color(0xFFFDE047).copy(alpha = 0.40f),
                                        Color(0xFFCA8A04).copy(alpha = 0.35f)
                                    )
                                ),
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        }
                        "sepah" -> {
                            // Sepah Flame-Orange Horizon Divider (subtle watermark)
                            val sepahCurve = Path().apply {
                                moveTo(0f, h * 0.50f + h * 0.04f)
                                quadraticBezierTo(w * 0.5f, h * 0.50f - h * 0.07f, w, h * 0.50f + h * 0.04f)
                            }
                            drawPath(
                                path = sepahCurve,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFEA580C).copy(alpha = 0.35f),
                                        Color(0xFFF97316).copy(alpha = 0.40f),
                                        Color(0xFFFBBF24).copy(alpha = 0.35f),
                                        Color(0xFFEA580C).copy(alpha = 0.35f)
                                    )
                                ),
                                style = Stroke(width = 2.0.dp.toPx())
                            )
                        }
                        else -> {
                            // General Elegant Dual Wave matching Bank Accent
                            val generalWave = Path().apply {
                                moveTo(0f, h * 0.52f)
                                cubicTo(w * 0.30f, h * 0.44f, w * 0.70f, h * 0.60f, w, h * 0.50f)
                            }
                            drawPath(
                                path = generalWave,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        horizonAccentColor.copy(alpha = 0.30f),
                                        Color.White.copy(alpha = 0.30f),
                                        horizonAccentColor.copy(alpha = 0.30f)
                                    )
                                ),
                                style = Stroke(width = 1.6.dp.toPx())
                            )
                        }
                    }

                    // 4. Realistic EMV Smart Chip (چیپ هوشمند شتاب)
                    val chipX = w * 0.09f
                    val chipY = h * 0.36f
                    val chipW = 38.dp.toPx()
                    val chipH = 28.dp.toPx()
                    val chipRadius = 4.dp.toPx()

                    // Chip Body with Metallic Gold Gradient
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFDE68A),
                                Color(0xFFF59E0B),
                                Color(0xFFD97706),
                                Color(0xFFFBBF24)
                            ),
                            start = Offset(chipX, chipY),
                            end = Offset(chipX + chipW, chipY + chipH)
                        ),
                        topLeft = Offset(chipX, chipY),
                        size = androidx.compose.ui.geometry.Size(chipW, chipH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(chipRadius, chipRadius)
                    )
                    drawRoundRect(
                        color = Color(0xFF92400E).copy(alpha = 0.6f),
                        topLeft = Offset(chipX, chipY),
                        size = androidx.compose.ui.geometry.Size(chipW, chipH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(chipRadius, chipRadius),
                        style = Stroke(width = 1f)
                    )
                    // Chip Contact Lines
                    val chipMidX = chipX + chipW * 0.50f
                    val chipMidY = chipY + chipH * 0.50f
                    drawLine(
                        color = Color(0xFF78350F).copy(alpha = 0.55f),
                        start = Offset(chipX, chipMidY),
                        end = Offset(chipX + chipW, chipMidY),
                        strokeWidth = 1.2f
                    )
                    drawLine(
                        color = Color(0xFF78350F).copy(alpha = 0.55f),
                        start = Offset(chipMidX, chipY),
                        end = Offset(chipMidX, chipY + chipH),
                        strokeWidth = 1.2f
                    )
                    drawRoundRect(
                        color = Color(0xFF78350F).copy(alpha = 0.45f),
                        topLeft = Offset(chipX + chipW * 0.28f, chipY + chipH * 0.22f),
                        size = androidx.compose.ui.geometry.Size(chipW * 0.44f, chipH * 0.56f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = Stroke(width = 1f)
                    )

                    // 5. Contactless NFC Payment Waves (نماد پرداخت بدون تماس)
                    val nfcX = chipX + chipW + 10.dp.toPx()
                    val nfcY = chipMidY
                    val waveColor = Color.White.copy(alpha = 0.65f)
                    for (i in 1..3) {
                        val arcRadius = (4 + i * 4).dp.toPx()
                        drawArc(
                            color = waveColor,
                            startAngle = -45f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(nfcX - arcRadius, nfcY - arcRadius),
                            size = androidx.compose.ui.geometry.Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = 1.6f, cap = StrokeCap.Round)
                        )
                    }

                    // 6. Specular Metallic Sheen Across Card
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.52f, h * 0.12f),
                            radius = w * 0.55f
                        ),
                        radius = w * 0.55f,
                        center = Offset(w * 0.52f, h * 0.12f)
                    )
                }

                // Foreground Content Positioned Matching Real Physical Iranian Card
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. TOP HEADER: Right = Bank Logo + Persian & English Name | Left = Debit Card Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Right side (in RTL): Bank Emblem & Name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            IranianBankLogoBadge(
                                bankInfo = bankInfo,
                                size = 42.dp
                            )
                            Column {
                                Text(
                                    text = bankInfo?.persianName ?: account.bankName.ifBlank { account.name },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = bankInfo?.englishName ?: "IRANIAN BANK CARD",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }

                        // Left side (in RTL): Classic "کارت نقدی / Debit Card" badge
                        DebitCardShetabBadge()
                    }

                    // 2. UPPER-CENTER: Account Number + Sheba (IR...) in Frosted Capsule
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Account Number centered (tappable to copy)
                        if (account.accountNumber.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xDD080E1C),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onCopyField("شماره حساب", account.accountNumber) }
                            ) {
                                Text(
                                    text = PersianNumberFormatter.formatDigits(account.accountNumber, currency),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        textDirection = TextDirection.Ltr,
                                        letterSpacing = 1.2.sp
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Sheba Number in opaque high-contrast shielded capsule (tappable to copy)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xF2080E1C),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCopyField("شماره شبا", normalizedSheba) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "شبا: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFBBF24)
                                )
                                Text(
                                    text = if (normalizedSheba.isBlank()) "IR--------------------------"
                                    else PersianNumberFormatter.formatDigits(normalizedSheba, currency),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        textDirection = TextDirection.Ltr,
                                        letterSpacing = 0.8.sp
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 3. LOWER HALF: 16-Digit Card Number + Cardholder Name + Expiry & CVV2
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // 16-Digit Card Number (tappable to copy)
                        Text(
                            text = formatCardNumberGroups(account.cardNumber, currency.isEnglishDigits),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.5.sp,
                                letterSpacing = 2.4.sp,
                                textDirection = TextDirection.Ltr
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            color = if (bankInfo?.id == "pasargad") Color(0xFFFEF08A) else Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCopyField("شماره کارت", account.cardNumber) }
                                .padding(vertical = 2.dp)
                        )

                        // Cardholder Name centered below card number (tappable to copy)
                        Text(
                            text = account.cardHolderName.ifBlank { account.name },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White.copy(alpha = 0.95f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    onCopyField(
                                        "نام صاحب کارت",
                                        account.cardHolderName.ifBlank { account.name }
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 1.dp)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Bottom Row: Right (in RTL) = تاریخ انقضا | Left (in RTL) = CVV2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Right side in RTL: تاریخ انقضا (۲ بخش جداگانه: سال و ماه)
                            val (expYear, expMonth) = remember(account.expiryDate) {
                                parseExpiryYearAndMonth(account.expiryDate)
                            }
                            val copyableExpiry = remember(expYear, expMonth, account.expiryDate) {
                                if (expYear.isNotBlank() || expMonth.isNotBlank()) {
                                    "${expYear.ifBlank { "--" }} ${expMonth.ifBlank { "--" }}"
                                } else account.expiryDate
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onCopyField("تاریخ انقضا", copyableExpiry) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "تاریخ انقضا:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White.copy(alpha = 0.90f)
                                )
                                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = Color.White.copy(alpha = 0.20f),
                                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = if (expYear.isBlank()) "--"
                                                else PersianNumberFormatter.formatDigits(expYear.padStart(2, '0'), currency),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    textDirection = TextDirection.Ltr
                                                ),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(5.dp),
                                            color = Color.White.copy(alpha = 0.20f),
                                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f))
                                        ) {
                                            Text(
                                                text = if (expMonth.isBlank()) "--"
                                                else PersianNumberFormatter.formatDigits(expMonth.padStart(2, '0'), currency),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    textDirection = TextDirection.Ltr
                                                ),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Left side in RTL: CVV2 in Frosted Capsule
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.20f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onCopyField("CVV2", account.cvv2) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CVV2: ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            textDirection = TextDirection.Ltr
                                        ),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = horizonAccentColor
                                    )
                                    Text(
                                        text = if (account.cvv2.isBlank()) "---"
                                        else PersianNumberFormatter.formatDigits(account.cvv2, currency),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            textDirection = TextDirection.Ltr
                                        ),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sleek Action & Balance Bar Underneath Card (Balance + Copy All + Edit + Delete)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "موجودی:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = PersianNumberFormatter.formatCurrency(account.balance, currency),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            val fullInfo = buildString {
                                append("بانک: ${account.bankName.ifBlank { account.name }}\n")
                                if (account.cardHolderName.isNotBlank()) append("صاحب کارت: ${account.cardHolderName}\n")
                                if (account.cardNumber.isNotBlank()) append("شماره کارت: ${account.cardNumber}\n")
                                if (normalizedSheba.isNotBlank()) append("شبا: $normalizedSheba\n")
                                if (account.accountNumber.isNotBlank()) append("شماره حساب: ${account.accountNumber}\n")
                                if (account.cvv2.isNotBlank()) append("CVV2: ${account.cvv2}\n")
                                if (account.expiryDate.isNotBlank()) append("انقضا: ${account.expiryDate}")
                            }.trim()
                            onCopyField("اطلاعات کامل کارت", fullInfo)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            Icons.Default.CopyAll,
                            contentDescription = "کپی همه",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("کپی همه", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "ویرایش کارت",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "حذف کارت",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddOrEditBankCardDialog(
    initialAccount: AccountEntity?,
    currency: ir.mmd.dodota.data.model.AppCurrency,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit
) {
    var name by remember { mutableStateOf(initialAccount?.name ?: "") }
    var bankName by remember { mutableStateOf(initialAccount?.bankName ?: "بانک سپه") }
    var cardHolderName by remember { mutableStateOf(initialAccount?.cardHolderName ?: "") }
    var cardNumber by remember { mutableStateOf(initialAccount?.cardNumber ?: "") }
    var cvv2 by remember { mutableStateOf(initialAccount?.cvv2 ?: "") }
    val initialExpParts = remember(initialAccount?.expiryDate) {
        parseExpiryYearAndMonth(initialAccount?.expiryDate ?: "")
    }
    var expiryYear by remember { mutableStateOf(initialExpParts.first) }
    var expiryMonth by remember { mutableStateOf(initialExpParts.second) }
    var shebaNumber by remember { mutableStateOf(initialAccount?.shebaNumber ?: "") }
    var accountNumber by remember { mutableStateOf(initialAccount?.accountNumber ?: "") }
    var description by remember { mutableStateOf(initialAccount?.cardOrAccountDescription ?: "") }
    var balanceText by remember { mutableStateOf(initialAccount?.balance?.toString() ?: "") }

    val colors = listOf("#475569", "#1E3A8A", "#059669", "#991B1B", "#78350F", "#581C87", "#0F766E")
    var selectedColor by remember {
        mutableStateOf(
            initialAccount?.colorHex
                ?: IranianBanksCatalog.resolveBank(bankName)?.primaryColorHex
                ?: colors[0]
        )
    }

    val monthFocusRequester = remember { FocusRequester() }
    val yearFocusRequester = remember { FocusRequester() }
    val cvv2FocusRequester = remember { FocusRequester() }
    val shebaFocusRequester = remember { FocusRequester() }

    var showBankPickerDialog by remember { mutableStateOf(false) }

    val selectedBankInfo = remember(bankName, cardNumber) {
        IranianBanksCatalog.resolveBank(bankName, cardNumber)
    }

    val accentColor = try {
        Color(android.graphics.Color.parseColor(selectedColor))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    GraphicalFormDialog(
        title = if (initialAccount == null) "ثبت کارت بانکی" else "ویرایش کارت",
        icon = Icons.Default.CreditCard,
        accentColor = accentColor,
        confirmText = if (initialAccount == null) "ثبت کارت" else "ذخیره",
        onConfirm = {
            val finalBank = bankName.trim().ifEmpty { selectedBankInfo?.persianName ?: "بانک" }
            val finalName = name.trim().ifEmpty { finalBank }
            val finalBalance = PersianNumberFormatter.parseAmount(balanceText)
            val combinedExpiry = if (expiryYear.isNotBlank() || expiryMonth.isNotBlank()) {
                "${expiryYear.trim().padStart(2, '0')} ${expiryMonth.trim().padStart(2, '0')}".trim()
            } else ""
            val entity = (initialAccount ?: AccountEntity(name = finalName, type = AccountType.BANK_CARD)).copy(
                name = finalName,
                bankName = finalBank,
                cardHolderName = cardHolderName.trim(),
                cardNumber = cardNumber.trim(),
                cvv2 = cvv2.trim(),
                expiryDate = combinedExpiry,
                shebaNumber = shebaNumber.trim(),
                accountNumber = accountNumber.trim(),
                cardOrAccountDescription = description.trim(),
                balance = finalBalance,
                colorHex = selectedColor,
                iconName = "card"
            )
            onSave(entity)
        },
        onDismiss = onDismiss
    ) {
        // Graphical Bank Selector Row (opens full 33-bank logo pack)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { showBankPickerDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IranianBankLogoBadge(
                        bankInfo = selectedBankInfo,
                        size = 40.dp
                    )
                    Column {
                        Text(
                            text = "انتخاب بانک (پک لوگوی بانک‌ها)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedBankInfo?.persianName ?: bankName.ifBlank { "انتخاب از لیست بانک‌ها" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تغییر بانک",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        DodotaTextField(
            value = name,
            onValueChange = { name = it },
            label = "عنوان کارت",
            leadingIcon = Icons.Default.CreditCard,
            modifier = Modifier.fillMaxWidth()
        )

        DodotaTextField(
            value = cardHolderName,
            onValueChange = { cardHolderName = it },
            label = "نام صاحب کارت",
            leadingIcon = Icons.Default.Person,
            modifier = Modifier.fillMaxWidth()
        )

        DodotaTextField(
            value = PersianNumberFormatter.formatDigits(cardNumber, currency),
            onValueChange = { raw ->
                val digits = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() }.take(16)
                cardNumber = digits
                // Auto-detect bank from 6-digit BIN if recognized
                IranianBanksCatalog.resolveBank("", digits)?.let { detected ->
                    bankName = detected.persianName
                    selectedColor = detected.primaryColorHex
                }
                if (digits.length == 16) {
                    try { monthFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            },
            label = "شماره کارت ۱۶ رقمی",
            leadingIcon = Icons.Default.Dialpad,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        // تاریخ انقضا (۲ بخش مجزا: ماه و سال - با انتقال فوکوس خودکار به فیلد بعدی)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DodotaTextField(
                value = PersianNumberFormatter.formatDigits(expiryMonth, currency),
                onValueChange = { raw ->
                    val clean = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() }.take(2)
                    expiryMonth = clean
                    if (clean.length == 2) {
                        try { yearFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                },
                label = "ماه انقضا (08)",
                placeholder = "08",
                leadingIcon = null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(monthFocusRequester)
            )

            DodotaTextField(
                value = PersianNumberFormatter.formatDigits(expiryYear, currency),
                onValueChange = { raw ->
                    val clean = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() }.take(2)
                    expiryYear = clean
                    if (clean.length == 2) {
                        try { cvv2FocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                },
                label = "سال انقضا (08)",
                placeholder = "08",
                leadingIcon = null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(yearFocusRequester)
            )
        }

        DodotaTextField(
            value = PersianNumberFormatter.formatDigits(cvv2, currency),
            onValueChange = { raw ->
                val clean = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() }.take(4)
                cvv2 = clean
                if (clean.length == 4) {
                    try { shebaFocusRequester.requestFocus() } catch (_: Exception) {}
                }
            },
            label = "CVV2",
            leadingIcon = Icons.Default.Password,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(cvv2FocusRequester)
        )

        DodotaTextField(
            value = PersianNumberFormatter.formatDigits(shebaNumber, currency),
            onValueChange = { raw ->
                val clean = PersianNumberFormatter.toEnglishDigits(raw)
                    .uppercase()
                    .filter { it.isDigit() || it == 'I' || it == 'R' }
                    .take(26)
                shebaNumber = clean
            },
            label = "شماره شبا (IR)",
            leadingIcon = Icons.Default.CompareArrows,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(shebaFocusRequester)
        )

        DodotaTextField(
            value = PersianNumberFormatter.formatDigits(accountNumber, currency),
            onValueChange = { raw ->
                accountNumber = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() || it == '.' || it == '-' }.take(24)
            },
            label = "شماره حساب",
            leadingIcon = Icons.Default.Numbers,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        PersianAmountInput(
            value = balanceText,
            onValueChange = { balanceText = it },
            currency = currency,
            label = "موجودی کارت"
        )

        Text("پالت رنگ زمینه کارت:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(colors) { hex ->
                val c = Color(android.graphics.Color.parseColor(hex))
                val isSelected = selectedColor.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedColor = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showBankPickerDialog) {
        IranianBankPickerDialog(
            selectedBankName = bankName,
            onSelectBank = { chosenBank ->
                bankName = chosenBank.persianName
                selectedColor = chosenBank.primaryColorHex
                if (name.isBlank()) {
                    name = chosenBank.persianName
                }
            },
            onDismiss = { showBankPickerDialog = false }
        )
    }
}
