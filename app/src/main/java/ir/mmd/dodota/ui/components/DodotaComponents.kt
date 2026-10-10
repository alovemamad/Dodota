package ir.mmd.dodota.ui.components

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ir.mmd.dodota.data.model.AppCurrency
import ir.mmd.dodota.data.model.AppThemeMode
import ir.mmd.dodota.data.model.TransactionType
import ir.mmd.dodota.ui.theme.*
import ir.mmd.dodota.util.AlarmHelper
import ir.mmd.dodota.util.PersianDateHelper
import ir.mmd.dodota.util.PersianNumberFormatter
import ir.mmd.dodota.util.ReminderAlarmReceiver
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DodotaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val themeMode = LocalAppThemeMode.current
    val isVipRed = themeMode == AppThemeMode.VIP_RED
    val accentColor = if (isVipRed) VipRedPrimary else MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor,
                                        accentColor.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                if (onBackClick != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isVipRed) VipRedSurfaceVariant.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            )
                            .border(
                                1.dp,
                                accentColor.copy(alpha = 0.28f),
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("top_bar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت",
                            tint = if (isVipRed) VipRedPrimary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            accentColor.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Glassmorphic container card that automatically renders frosted dark glass + neon specular border
 * and subtle top-edge inner sheen when VIP Red or dark/combo themes are active.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color? = null,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeMode = LocalAppThemeMode.current
    val isGlassTheme = themeMode == AppThemeMode.VIP_RED ||
            themeMode == AppThemeMode.BLUBANK ||
            themeMode == AppThemeMode.SUNSET ||
            themeMode == AppThemeMode.GOLD ||
            themeMode == AppThemeMode.PURPLE ||
            themeMode == AppThemeMode.BLACK

    val effectiveAccent = borderColor ?: accentColor

    val borderStroke = if (borderColor != null) {
        BorderStroke(
            width = if (isHighlighted) 1.6.dp else 1.1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderColor,
                    Color.White.copy(alpha = 0.16f),
                    borderColor.copy(alpha = 0.48f)
                )
            )
        )
    } else if (isGlassTheme) {
        BorderStroke(
            width = if (isHighlighted) 1.6.dp else 1.1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    effectiveAccent.copy(alpha = if (isHighlighted) 0.8f else 0.45f),
                    Color.White.copy(alpha = 0.12f),
                    effectiveAccent.copy(alpha = if (isHighlighted) 0.5f else 0.22f)
                )
            )
        )
    } else if (isHighlighted) {
        BorderStroke(1.6.dp, effectiveAccent.copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    }

    val containerColor = if (themeMode == AppThemeMode.VIP_RED) {
        VipRedSurfaceVariant.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
        modifier = if (onClick != null) modifier.clip(shape).clickable { onClick() } else modifier,
        content = content
    )
}

@Composable
fun DodotaExpandableSelector(
    label: String,
    selectedTitle: String,
    modifier: Modifier = Modifier,
    selectedSubtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.(closeSelector: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        isHighlighted = expanded
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (leadingIcon != null) {
                        leadingIcon()
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!selectedSubtitle.isNullOrBlank()) {
                            Text(
                                text = selectedSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "بستن" else "باز کردن",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    content { expanded = false }
                }
            }
        }
    }
}

@Composable
fun DodotaExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector = Icons.Default.Tune,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        isHighlighted = expanded
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "بستن" else "باز کردن",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    content()
                }
            }
        }
    }
}

@Composable
fun CategoryIconView(
    iconName: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val bg = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val iconVector = getIconVectorByName(iconName)
    val isVipRed = LocalAppThemeMode.current == AppThemeMode.VIP_RED

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2.6f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        bg.copy(alpha = if (isVipRed) 0.28f else 0.2f),
                        bg.copy(alpha = 0.08f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = bg.copy(alpha = if (isVipRed) 0.45f else 0.25f),
                shape = RoundedCornerShape(size / 2.6f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = bg,
            modifier = Modifier.size(size * 0.54f)
        )
    }
}

fun getIconVectorByName(name: String): ImageVector {
    return when (name.lowercase()) {
        "restaurant" -> Icons.Default.RestaurantMenu
        "home" -> Icons.Default.Roofing
        "directions_car" -> Icons.Default.DirectionsCarFilled
        "shopping_cart" -> Icons.Default.LocalMall
        "local_hospital" -> Icons.Default.MedicalServices
        "school" -> Icons.Default.AutoStories
        "sports_esports" -> Icons.Default.SportsEsports
        "receipt" -> Icons.Default.ReceiptLong
        "wifi" -> Icons.Default.Router
        "phone" -> Icons.Default.Smartphone
        "subscriptions" -> Icons.Default.Subscriptions
        "flight" -> Icons.Default.FlightTakeoff
        "checkroom" -> Icons.Default.Checkroom
        "build" -> Icons.Default.Handyman
        "payments" -> Icons.Default.Paid
        "card_giftcard" -> Icons.Default.Redeem
        "store" -> Icons.Default.Storefront
        "trending_up" -> Icons.Default.Insights
        "savings" -> Icons.Default.Savings
        "volunteer_activism" -> Icons.Default.VolunteerActivism
        "wallet" -> Icons.Default.AccountBalanceWallet
        "cash" -> Icons.Default.Payments
        "card" -> Icons.Default.CreditCard
        "account" -> Icons.Default.AccountBalance
        "target" -> Icons.Default.TrackChanges
        "swap_horiz" -> Icons.Default.CurrencyExchange
        "person_transfer" -> Icons.Default.SendToMobile
        else -> Icons.Default.GridView
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DodotaDropdownField(
    label: String,
    selectedText: String,
    items: List<T>,
    itemText: (T) -> String,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    itemSubtitle: ((T) -> String?)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = if (leadingIcon != null) {
                { Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            } else null,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
            ),
            singleLine = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = itemText(item),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val sub = itemSubtitle?.invoke(item)
                            if (!sub.isNullOrBlank()) {
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ReminderConfigSection(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    offsetDays: Int,
    onOffsetDaysChange: (Int) -> Unit,
    selectedHour: Int,
    onHourChange: (Int) -> Unit,
    selectedMinute: Int,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "یادآور و آلارم هوشمند سررسید"
) {
    val enabled = isEnabled
    val hour = selectedHour
    val minute = selectedMinute
    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(AlarmHelper.isNotificationPermissionGranted(context))
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (!granted) {
            Toast.makeText(
                context,
                "برای ارسال اعلان یادآور، لطفاً مجوز اعلان را تایید کنید",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var customDaysInput by remember(offsetDays) {
        mutableStateOf(offsetDays.toString())
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        isHighlighted = enabled
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { newEnabled ->
                        if (newEnabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlarmHelper.isNotificationPermissionGranted(context)) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        onEnabledChange(newEnabled)
                    }
                )
            }

            AnimatedVisibility(visible = enabled) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom user-defined offset days (Item 7: no fixed presets, user chooses freely)
                    Text(
                        text = "تعیین دلخواه زمان یادآوری (چند روز قبل از سررسید؟)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        val next = (offsetDays - 1).coerceAtLeast(0)
                                        onOffsetDaysChange(next)
                                        customDaysInput = next.toString()
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "کمتر")
                                }

                                OutlinedTextField(
                                    value = PersianNumberFormatter.toPersianDigits(customDaysInput),
                                    onValueChange = { raw ->
                                        val clean = PersianNumberFormatter.toEnglishDigits(raw).filter { it.isDigit() }.take(3)
                                        customDaysInput = clean
                                        val parsed = clean.toIntOrNull() ?: 0
                                        onOffsetDaysChange(parsed.coerceIn(0, 365))
                                    },
                                    label = { Text("تعداد روز قبل") },
                                    suffix = { Text("روز قبل") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp)
                                )

                                FilledTonalIconButton(
                                    onClick = {
                                        val next = (offsetDays + 1).coerceAtMost(365)
                                        onOffsetDaysChange(next)
                                        customDaysInput = next.toString()
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "بیشتر")
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (offsetDays) {
                                    0 -> "یادآوری در همان روز سررسید انجام می‌شود"
                                    1 -> "یادآوری ۱ روز قبل از سررسید انجام می‌شود"
                                    else -> "یادآوری ${PersianNumberFormatter.toPersianDigits(offsetDays)} روز قبل از سررسید انجام می‌شود"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Expandable Hour & Minute Selector
                    val formattedTime = "${PersianNumberFormatter.toPersianDigits(hour.toString().padStart(2, '0'))}:${PersianNumberFormatter.toPersianDigits(minute.toString().padStart(2, '0'))}"
                    DodotaExpandableSelector(
                        label = "ساعت پخش زنگ هشدار",
                        selectedTitle = "ساعت $formattedTime",
                        leadingIcon = {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { onMinuteChange((minute + 5) % 60) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "افزایش دقیقه", modifier = Modifier.size(16.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = PersianNumberFormatter.toPersianDigits(minute.toString().padStart(2, '0')),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text("دقیقه", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    IconButton(
                                        onClick = { onMinuteChange(if (minute - 5 < 0) 55 else minute - 5) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "کاهش دقیقه", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Text(":", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { onHourChange((hour + 1) % 24) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "افزایش ساعت", modifier = Modifier.size(16.dp))
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = PersianNumberFormatter.toPersianDigits(hour.toString().padStart(2, '0')),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text("ساعت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                    IconButton(
                                        onClick = { onHourChange(if (hour - 1 < 0) 23 else hour - 1) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "کاهش ساعت", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlarmHelper.isNotificationPermissionGranted(context)) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                ReminderAlarmReceiver.showAlarmNotification(
                                    context = context,
                                    notifId = (System.currentTimeMillis() % 10000).toInt(),
                                    title = "تست زنگ هشدار دُدوتا",
                                    message = "سیستم آلارم و یادآور شما به درستی فعال است."
                                )
                                Toast.makeText(context, "🔔 اعلان و صدای هشدار آزمایشی ارسال شد!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تست فوری زنگ و اعلان هشدار", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PersianAmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currency: AppCurrency = AppCurrency.TOMAN,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    val numericValue = PersianNumberFormatter.parseNumericValue(value)
    val formattedDisplay = remember(value, currency) {
        if (value.isBlank()) {
            ""
        } else if (numericValue > 0L) {
            PersianNumberFormatter.formatWithCommas(numericValue, currency.isEnglishDigits)
        } else {
            if (currency.isEnglishDigits) PersianNumberFormatter.toEnglishDigits(value)
            else PersianNumberFormatter.toPersianDigits(value)
        }
    }
    val formattedPreview = if (numericValue > 0) {
        PersianNumberFormatter.formatCurrency(numericValue, currency)
    } else ""

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = TextFieldValue(
                text = formattedDisplay,
                selection = TextRange(formattedDisplay.length)
            ),
            onValueChange = { tfv ->
                val cleanDigits = PersianNumberFormatter.toEnglishDigits(tfv.text)
                    .filter { it.isDigit() }
                    .trimStart('0')
                    .ifEmpty { if (tfv.text.any { it == '0' || it == '۰' }) "0" else "" }
                if (cleanDigits.length <= 15) {
                    onValueChange(cleanDigits)
                }
            },
            label = { Text("$label (${currency.unitName})") },
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            trailingIcon = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = currency.unitName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            },
            supportingText = {
                if (formattedPreview.isNotEmpty()) {
                    Text(
                        text = formattedPreview,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else if (errorMessage != null && isError) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            },
            isError = isError,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("persian_amount_input")
        )
    }
}

@Composable
fun PersianDatePickerDialog(
    initialDateMillis: Long = System.currentTimeMillis(),
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialPDate = PersianDateHelper.fromMillis(initialDateMillis)
    var selectedYear by remember { mutableIntStateOf(initialPDate.year) }
    var selectedMonth by remember { mutableIntStateOf(initialPDate.month) }
    var selectedDay by remember { mutableIntStateOf(initialPDate.day) }

    val daysInMonth = PersianDateHelper.getDaysInPersianMonth(selectedYear, selectedMonth)
    if (selectedDay > daysInMonth) {
        selectedDay = daysInMonth
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            shape = RoundedCornerShape(26.dp),
            isHighlighted = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "انتخاب تاریخ شمسی",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "با کشیدن (اسکرول) روی اعداد یا دکمه‌ها تاریخ را تغییر دهید",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Date Preview Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${PersianNumberFormatter.toPersianDigits(selectedDay)} ${PersianDateHelper.PERSIAN_MONTHS[selectedMonth - 1]} ${PersianNumberFormatter.toPersianDigits(selectedYear)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Scrollable Wheel Selectors Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Day Wheel
                    WheelPickerColumn(
                        label = "روز",
                        value = selectedDay,
                        range = 1..daysInMonth,
                        onValueChange = { selectedDay = it },
                        modifier = Modifier.weight(0.9f)
                    )

                    // Month Wheel
                    WheelPickerColumn(
                        label = "ماه",
                        value = selectedMonth,
                        range = 1..12,
                        displayTransform = { PersianDateHelper.PERSIAN_MONTHS[it - 1] },
                        onValueChange = { selectedMonth = it },
                        modifier = Modifier.weight(1.3f)
                    )

                    // Year Wheel
                    WheelPickerColumn(
                        label = "سال",
                        value = selectedYear,
                        range = (initialPDate.year - 10)..(initialPDate.year + 10),
                        onValueChange = { selectedYear = it },
                        modifier = Modifier.weight(1.05f)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val millis = PersianDateHelper.toMillis(selectedYear, selectedMonth, selectedDay)
                            onDateSelected(millis)
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("تأیید تاریخ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WheelPickerColumn(
    label: String,
    value: Int,
    range: IntRange,
    displayTransform: (Int) -> String = { PersianNumberFormatter.toPersianDigits(it) },
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(range) { range.toList() }
    val selectedIndex = (value - range.first).coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(value, range) {
        val targetIdx = (value - range.first).coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != targetIdx) {
            listState.animateScrollToItem(targetIdx)
        }
    }

    val centeredIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                selectedIndex
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index ?: selectedIndex
            }
        }
    }

    LaunchedEffect(centeredIndex, listState.isScrollInProgress) {
        if (centeredIndex in items.indices) {
            val newValue = items[centeredIndex]
            if (newValue != value) {
                onValueChange(newValue)
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        IconButton(
            onClick = {
                val nextVal = if (value > range.first) value - 1 else range.last
                onValueChange(nextVal)
                coroutineScope.launch {
                    listState.animateScrollToItem((nextVal - range.first).coerceIn(0, items.size - 1))
                }
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "قبلی", modifier = Modifier.size(18.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
            )

            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(vertical = 44.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(items.size) { index ->
                    val itemValue = items[index]
                    val isSelected = itemValue == value
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable {
                                onValueChange(itemValue)
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayTransform(itemValue),
                            style = if (isSelected) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = {
                val nextVal = if (value < range.last) value + 1 else range.first
                onValueChange(nextVal)
                coroutineScope.launch {
                    listState.animateScrollToItem((nextVal - range.first).coerceIn(0, items.size - 1))
                }
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "بعدی", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    description: String,
    icon: ImageVector = Icons.Outlined.Inbox,
    modifier: Modifier = Modifier
) {
    GlassCard(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CategoryPieChart(
    items: List<Pair<String, Pair<Long, Color>>>,
    totalAmount: Long,
    currency: AppCurrency,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty() || totalAmount <= 0L) {
        EmptyStateCard(
            title = "اطلاعاتی برای نمایش نمودار وجود ندارد",
            description = "پس از ثبت تراکنش‌ها، دسته‌بندی‌ها به صورت نمودار نمایش داده می‌شوند."
        )
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                val strokeWidth = 32f

                items.forEach { (_, data) ->
                    val (amount, color) = data
                    val sweepAngle = (amount.toFloat() / totalAmount.toFloat()) * 360f
                    if (sweepAngle > 0.5f) {
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle - 2f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += sweepAngle
                    }
                }
            }
            Text(
                text = PersianNumberFormatter.toPersianDigits(items.size) + "\nدسته",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.take(5).forEach { (categoryName, data) ->
                val (amount, color) = data
                val percent = (amount.toDouble() / totalAmount.toDouble() * 100).toInt()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = PersianNumberFormatter.formatPercent(percent, currency.isEnglishDigits),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureToggleCard(
    icon: ImageVector,
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        isHighlighted = isChecked,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
