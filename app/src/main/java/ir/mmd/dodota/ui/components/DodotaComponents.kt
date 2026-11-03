package ir.mmd.dodota.ui.components

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 4.dp)
    ) {
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
                            .padding(start = 6.dp, top = 2.dp)
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
            actions = {
                Row(
                    modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            },
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
 * Glassmorphic shadowed container card that renders frosted glass + neon specular border
 * and subtle elevation shadow coordinated with the active theme.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    accentColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color? = null,
    isHighlighted: Boolean = false,
    elevation: Dp? = null,
    shadowElevation: Dp = elevation ?: if (isHighlighted) 8.dp else 4.dp,
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
            width = if (isHighlighted) 1.5.dp else 1.dp,
            color = if (isHighlighted) borderColor else borderColor.copy(alpha = 0.55f)
        )
    } else if (isGlassTheme) {
        BorderStroke(
            width = if (isHighlighted) 1.5.dp else 1.dp,
            color = effectiveAccent.copy(alpha = if (isHighlighted) 0.65f else 0.32f)
        )
    } else if (isHighlighted) {
        BorderStroke(1.5.dp, effectiveAccent.copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    }

    val containerColor = if (themeMode == AppThemeMode.VIP_RED) {
        VipRedSurfaceVariant.copy(alpha = 0.90f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
    }

    val baseModifier = modifier.shadow(
        elevation = shadowElevation,
        shape = shape,
        ambientColor = effectiveAccent.copy(alpha = 0.20f),
        spotColor = effectiveAccent.copy(alpha = 0.28f)
    )

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = borderStroke,
        modifier = if (onClick != null) baseModifier.clip(shape).clickable { onClick() } else baseModifier,
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
    title: String = "یادآور",
    dueDateMillis: Long = System.currentTimeMillis(),
    onDueDateChange: ((Long) -> Unit)? = null
) {
    val enabled = isEnabled
    val hour = selectedHour
    val minute = selectedMinute
    val context = LocalContext.current
    val useEn = LocalUseEnglishDigits.current || PersianNumberFormatter.globalUseEnglishDigits

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "برای ارسال اعلان یادآور، لطفاً مجوز اعلان را تایید کنید",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var showGraphicalCalendar by remember { mutableStateOf(false) }

    val selectedReminderDateMillis = remember(dueDateMillis, offsetDays) {
        java.util.Calendar.getInstance().apply {
            timeInMillis = dueDateMillis
            add(java.util.Calendar.DAY_OF_YEAR, -offsetDays)
        }.timeInMillis
    }

    if (showGraphicalCalendar) {
        PersianDatePickerDialog(
            initialDateMillis = selectedReminderDateMillis,
            onDateSelected = { pickedMillis ->
                if (onDueDateChange != null) {
                    onDueDateChange(pickedMillis)
                    onOffsetDaysChange(0)
                } else {
                    val dueCal = java.util.Calendar.getInstance().apply {
                        timeInMillis = dueDateMillis
                        set(java.util.Calendar.HOUR_OF_DAY, 12)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val pickedCal = java.util.Calendar.getInstance().apply {
                        timeInMillis = pickedMillis
                        set(java.util.Calendar.HOUR_OF_DAY, 12)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    val diffDays = Math.round((dueCal.timeInMillis - pickedCal.timeInMillis) / (24.0 * 60.0 * 60.0 * 1000.0)).toInt()
                    onOffsetDaysChange(diffDays)
                }
                showGraphicalCalendar = false
            },
            onDismiss = { showGraphicalCalendar = false }
        )
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        isHighlighted = enabled
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                            modifier = Modifier.size(18.dp)
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
                val formattedTime = "${PersianNumberFormatter.formatDigits(hour.toString().padStart(2, '0'), useEn)}:${PersianNumberFormatter.formatDigits(minute.toString().padStart(2, '0'), useEn)}"
                val formattedDate = PersianDateHelper.formatToPersianDate(selectedReminderDateMillis, useEn)

                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Graphical Calendar Picker Button
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1.2f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showGraphicalCalendar = true }
                                .testTag("reminder_date_picker_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = "انتخاب روز یادآوری",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "روز یادآوری",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formattedDate,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Phone Clock Time Picker Button
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(0.9f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, pickedHour, pickedMinute ->
                                            onHourChange(pickedHour)
                                            onMinuteChange(pickedMinute)
                                        },
                                        hour,
                                        minute,
                                        true
                                    ).show()
                                }
                                .testTag("reminder_time_picker_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "انتخاب ساعت",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "ساعت",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formattedTime,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DodotaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = Icons.Default.EditNote,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    trailingContent: (@Composable () -> Unit)? = null,
    prefixText: String? = null,
    suffixText: String? = null,
    suffix: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    shape: Shape = RoundedCornerShape(18.dp),
    isError: Boolean = false,
    supportingText: String? = null,
    formatNumericWithCommas: Boolean = false
) {
    val useEn = LocalUseEnglishDigits.current || PersianNumberFormatter.globalUseEnglishDigits
    val effectiveSuffix: (@Composable () -> Unit)? = suffix ?: if (suffixText != null) {
        { Text(suffixText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
    } else null

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusBringModifier = Modifier
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { focusState ->
            if (focusState.isFocused) {
                coroutineScope.launch {
                    kotlinx.coroutines.delay(220)
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }

    val leadingIconContent: (@Composable () -> Unit)? = if (leadingIcon != null) {
        {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                iconTint.copy(alpha = 0.20f),
                                iconTint.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(1.dp, iconTint.copy(alpha = 0.30f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    } else null

    if (formatNumericWithCommas) {
        val displayStr = remember(value, useEn) {
            PersianNumberFormatter.formatInputWithCommas(value, useEn)
        }
        OutlinedTextField(
            value = TextFieldValue(
                text = displayStr,
                selection = TextRange(displayStr.length)
            ),
            onValueChange = { tfv ->
                val clean = PersianNumberFormatter.toEnglishDigits(tfv.text)
                    .filter { it.isDigit() }
                    .trimStart('0')
                    .ifEmpty { if (tfv.text.any { it == '0' || it == '۰' }) "0" else "" }
                if (clean.length <= 15) {
                    onValueChange(clean)
                    coroutineScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            },
            label = if (label.isNotEmpty()) {
                { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else null,
            placeholder = if (placeholder != null) {
                { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else null,
            leadingIcon = leadingIconContent,
            trailingIcon = trailingContent,
            prefix = if (prefixText != null) { { Text(prefixText, fontWeight = FontWeight.Bold, color = iconTint) } } else null,
            suffix = effectiveSuffix,
            supportingText = if (supportingText != null) { { Text(supportingText) } } else null,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = iconTint,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            ),
            modifier = modifier
                .fillMaxWidth()
                .then(focusBringModifier)
        )
    } else {
        val isNumKeyboard = keyboardOptions.keyboardType == KeyboardType.Number ||
                keyboardOptions.keyboardType == KeyboardType.Phone ||
                keyboardOptions.keyboardType == KeyboardType.Decimal
        val displayVal = if (isNumKeyboard) PersianNumberFormatter.formatDigits(value, useEn) else value

        OutlinedTextField(
            value = displayVal,
            onValueChange = { raw ->
                if (isNumKeyboard) {
                    onValueChange(PersianNumberFormatter.toEnglishDigits(raw))
                } else {
                    onValueChange(raw)
                }
                coroutineScope.launch {
                    bringIntoViewRequester.bringIntoView()
                }
            },
            label = if (label.isNotEmpty()) {
                { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else null,
            placeholder = if (placeholder != null) {
                { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else null,
            leadingIcon = leadingIconContent,
            trailingIcon = trailingContent,
            prefix = if (prefixText != null) { { Text(prefixText, fontWeight = FontWeight.Bold, color = iconTint) } } else null,
            suffix = effectiveSuffix,
            supportingText = if (supportingText != null) { { Text(supportingText) } } else null,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = iconTint,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            ),
            modifier = modifier
                .fillMaxWidth()
                .then(focusBringModifier)
        )
    }
}

@Composable
fun PersianAmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currency: AppCurrency = AppCurrency.TOMAN,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Default.Payments,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    val useEn = currency.isEnglishDigits || LocalUseEnglishDigits.current || PersianNumberFormatter.globalUseEnglishDigits
    val numericValue = PersianNumberFormatter.parseNumericValue(value)
    val formattedDisplay = remember(value, useEn) {
        if (value.isBlank()) {
            ""
        } else if (numericValue > 0L) {
            PersianNumberFormatter.formatNumber(numericValue, useCommas = true, isEnglishDigits = useEn)
        } else {
            PersianNumberFormatter.formatInputWithCommas(value, useEn)
        }
    }
    val formattedPreview = if (numericValue > 0L) {
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                )
                            )
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            trailingIcon = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
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
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
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
    val useEn = LocalUseEnglishDigits.current || PersianNumberFormatter.globalUseEnglishDigits
    val todayPDate = remember { PersianDateHelper.getCurrentPersianDate() }
    val initialPDate = remember(initialDateMillis) { PersianDateHelper.fromMillis(initialDateMillis) }
    var selectedYear by remember { mutableIntStateOf(initialPDate.year) }
    var selectedMonth by remember { mutableIntStateOf(initialPDate.month) }
    var selectedDay by remember { mutableIntStateOf(initialPDate.day) }
    var isSelectingYearInDialog by remember { mutableStateOf(false) }

    val daysInMonth = PersianDateHelper.getDaysInPersianMonth(selectedYear, selectedMonth)
    if (selectedDay > daysInMonth) {
        selectedDay = daysInMonth
    }

    val startWeekdayOffset = remember(selectedYear, selectedMonth) {
        val firstDayMillis = PersianDateHelper.toMillis(selectedYear, selectedMonth, 1)
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = firstDayMillis
        when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SATURDAY -> 0
            java.util.Calendar.SUNDAY -> 1
            java.util.Calendar.MONDAY -> 2
            java.util.Calendar.TUESDAY -> 3
            java.util.Calendar.WEDNESDAY -> 4
            java.util.Calendar.THURSDAY -> 5
            java.util.Calendar.FRIDAY -> 6
            else -> 0
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            shape = RoundedCornerShape(24.dp),
            isHighlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Graphical Header Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Year navigation با امکان لمس روی سال برای باز شدن فهرست سال‌ها
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            IconButton(
                                onClick = { selectedYear-- },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "سال قبل",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isSelectingYearInDialog = !isSelectingYearInDialog }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = PersianNumberFormatter.formatNumber(selectedYear, useCommas = false, isEnglishDigits = useEn),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "انتخاب سال",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { selectedYear++ },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = "سال بعد",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Month navigation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (selectedMonth > 1) {
                                        selectedMonth--
                                    } else {
                                        selectedMonth = 12
                                        selectedYear--
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "ماه قبل", modifier = Modifier.size(18.dp))
                            }

                            Text(
                                text = "${PersianNumberFormatter.formatNumber(selectedDay, useCommas = false, isEnglishDigits = useEn)} ${PersianDateHelper.PERSIAN_MONTHS[selectedMonth - 1]}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            FilledTonalIconButton(
                                onClick = {
                                    if (selectedMonth < 12) {
                                        selectedMonth++
                                    } else {
                                        selectedMonth = 1
                                        selectedYear++
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "ماه بعد", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isSelectingYearInDialog) {
                    val dialogYearsList = remember(todayPDate.year) {
                        (todayPDate.year - 20..todayPDate.year + 20).toList()
                    }
                    val dialogGridState = rememberLazyGridState(
                        initialFirstVisibleItemIndex = (dialogYearsList.indexOf(selectedYear) - 3).coerceAtLeast(0)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "سال مورد نظر را انتخاب کنید:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { isSelectingYearInDialog = false },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("تأیید و برگشت", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = dialogGridState,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                        ) {
                            items(dialogYearsList) { y ->
                                val isSelected = y == selectedYear
                                val isCurrentYear = y == todayPDate.year
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else if (isCurrentYear) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedYear = y
                                        isSelectingYearInDialog = false
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = PersianNumberFormatter.formatNumber(y, useCommas = false, isEnglishDigits = useEn),
                                            style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Weekdays Header Row
                    val weekInitials = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        weekInitials.forEachIndexed { index, dayLabel ->
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Graphical 7-Column Calendar Grid
                    val totalCells = startWeekdayOffset + daysInMonth
                    val rows = (totalCells + 6) / 7
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (row in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (col in 0 until 7) {
                                    val cellIndex = row * 7 + col
                                    val dayNum = cellIndex - startWeekdayOffset + 1
                                    if (dayNum in 1..daysInMonth) {
                                        val isSelected = dayNum == selectedDay
                                        val isToday = selectedYear == todayPDate.year &&
                                                selectedMonth == todayPDate.month &&
                                                dayNum == todayPDate.day

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                                                    }
                                                )
                                                .border(
                                                    width = if (isSelected || isToday) 1.dp else 0.5.dp,
                                                    color = when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                                                    },
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable { selectedDay = dayNum },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = PersianNumberFormatter.formatNumber(dayNum, useCommas = false, isEnglishDigits = useEn),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    col == 6 -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f).height(38.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            selectedYear = todayPDate.year
                            selectedMonth = todayPDate.month
                            selectedDay = todayPDate.day
                        }
                    ) {
                        Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("امروز", fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text("انصراف")
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val millis = PersianDateHelper.toMillis(selectedYear, selectedMonth, selectedDay)
                                onDateSelected(millis)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("تأیید", fontWeight = FontWeight.Bold)
                        }
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
    displayTransform: (Int) -> String = { PersianNumberFormatter.formatNumber(it, useCommas = false) },
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
            if (description.isNotBlank()) {
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
}

@Composable
fun CategoryPieChart(
    items: List<Pair<String, Pair<Long, Color>>>,
    totalAmount: Long,
    currency: AppCurrency,
    modifier: Modifier = Modifier
) {
    val useEn = currency.isEnglishDigits || LocalUseEnglishDigits.current || PersianNumberFormatter.globalUseEnglishDigits
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${PersianNumberFormatter.formatNumber(items.size, useCommas = false, isEnglishDigits = useEn)} دسته",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = PersianNumberFormatter.formatNumber(totalAmount, useCommas = true, isEnglishDigits = useEn),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                        text = "${PersianNumberFormatter.formatNumber(amount, useCommas = true, isEnglishDigits = useEn)} (${PersianNumberFormatter.formatPercent(percent, useEn)})",
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

@Composable
fun GraphicalFormDialog(
    title: String,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    confirmText: String = "ذخیره",
    dismissText: String = "انصراف",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        var isDialogVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            isDialogVisible = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isDialogVisible,
                enter = scaleIn(
                    initialScale = 0.92f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
                exit = scaleOut(
                    targetScale = 0.94f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 180))
            ) {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    isHighlighted = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                    // Graphical Header Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.22f),
                                        accentColor.copy(alpha = 0.06f)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accentColor.copy(alpha = 0.2f))
                                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = dismissText,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = accentColor.copy(alpha = 0.18f))

                    // Controlled-size scrollable form content
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))

                    // Compact Action Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(dismissText)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onConfirm,
                            enabled = confirmEnabled,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(confirmText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun GraphicalFormDialog(
    onDismissRequest: () -> Unit,
    title: String,
    icon: ImageVector = Icons.Default.EditNote,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    confirmText: String = "ذخیره",
    dismissText: String = "انصراف",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    GraphicalFormDialog(
        title = title,
        icon = icon,
        accentColor = accentColor,
        confirmText = confirmText,
        dismissText = dismissText,
        confirmEnabled = confirmEnabled,
        onDismiss = onDismissRequest,
        onConfirm = onConfirm,
        content = content
    )
}


