package ir.mmd.dodota.ui.components.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.AppCurrency
import ir.mmd.dodota.ui.components.CategoryIconView
import ir.mmd.dodota.util.PersianNumberFormatter
import kotlin.math.atan2

data class ChartSlice(
    val id: Long?,
    val name: String,
    val amount: Long,
    val color: Color,
    val iconName: String = "shopping_cart",
    val colorHex: String = "#EF4444",
    val transactionCount: Int = 1
)

@Composable
fun InteractiveDonutChart(
    slices: List<ChartSlice>,
    totalAmount: Long,
    currency: AppCurrency,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty() || totalAmount <= 0) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.DonutLarge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "داده‌ای برای ترسیم نمودار دایره‌ای در این بازه وجود ندارد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var animationPlayed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "donut_anim"
    )

    LaunchedEffect(slices) {
        animationPlayed = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Interactive Glowing Donut Ring
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(235.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(205.dp)
                    .pointerInput(slices, totalAmount) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val touchVec = offset - center
                            val distance = touchVec.getDistance()
                            val innerR = (size.width / 2f) * 0.42f
                            val outerR = (size.width / 2f) * 0.98f

                            if (distance in innerR..outerR) {
                                var angle = Math.toDegrees(atan2(touchVec.y.toDouble(), touchVec.x.toDouble())).toFloat()
                                if (angle < 0) angle += 360f
                                val shiftedAngle = (angle + 90f) % 360f

                                var accum = 0f
                                var hitIndex = -1
                                for (i in slices.indices) {
                                    val sweep = (slices[i].amount.toFloat() / totalAmount.toFloat()) * 360f
                                    if (shiftedAngle >= accum && shiftedAngle < accum + sweep) {
                                        hitIndex = i
                                        break
                                    }
                                    accum += sweep
                                }
                                selectedIndex = if (selectedIndex == hitIndex) null else hitIndex
                            } else {
                                selectedIndex = null
                            }
                        }
                    }
            ) {
                val strokeWidth = 34.dp.toPx()
                val selectedStrokeWidth = 44.dp.toPx()
                val centerOffset = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.width - selectedStrokeWidth) / 2f

                // Outer subtle decorative ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f),
                    radius = radius + (selectedStrokeWidth / 2f) + 4.dp.toPx(),
                    center = centerOffset,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Inner subtle track ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = radius - (strokeWidth / 2f) - 4.dp.toPx(),
                    center = centerOffset,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                var currentStartAngle = -90f

                slices.forEachIndexed { index, slice ->
                    val sweepAngle = (slice.amount.toFloat() / totalAmount.toFloat()) * 360f * progress
                    val isSelected = selectedIndex == index
                    val curStroke = if (isSelected) selectedStrokeWidth else strokeWidth
                    val gap = if (slices.size > 1) 3.5f else 0f

                    if (sweepAngle > 0.5f) {
                        // Ambient glow behind selected slice
                        if (isSelected) {
                            drawArc(
                                color = slice.color.copy(alpha = 0.28f),
                                startAngle = currentStartAngle,
                                sweepAngle = (sweepAngle - gap).coerceAtLeast(0.5f),
                                useCenter = false,
                                style = Stroke(width = curStroke + 10.dp.toPx(), cap = StrokeCap.Round),
                                topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                size = Size(radius * 2, radius * 2)
                            )
                        }

                        drawArc(
                            color = if (selectedIndex == null || isSelected) slice.color else slice.color.copy(alpha = 0.3f),
                            startAngle = currentStartAngle,
                            sweepAngle = (sweepAngle - gap).coerceAtLeast(0.5f),
                            useCenter = false,
                            style = Stroke(width = curStroke, cap = StrokeCap.Butt),
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(radius * 2, radius * 2)
                        )
                    }
                    currentStartAngle += sweepAngle
                }
            }

            // Center Interactive Hub
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(120.dp)
                    .padding(horizontal = 4.dp)
            ) {
                if (selectedIndex != null && selectedIndex in slices.indices) {
                    val s = slices[selectedIndex!!]
                    val pct = (s.amount.toDouble() / totalAmount.toDouble() * 100).toInt()
                    Text(
                        text = s.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = s.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${PersianNumberFormatter.formatDigits(pct, currency)}٪",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = PersianNumberFormatter.formatNumberWithCommas(s.amount, currency),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "${PersianNumberFormatter.formatDigits(slices.size, currency)} دسته‌بندی",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = PersianNumberFormatter.formatNumberWithCommas(totalAmount, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currency.unitName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Category Breakdown Cards with Progress Bars & Icons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.forEachIndexed { index, slice ->
                val isSelected = selectedIndex == index
                val pctFloat = (slice.amount.toFloat() / totalAmount.toFloat()).coerceIn(0f, 1f)
                val pct = (pctFloat * 100).toInt()

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) slice.color.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) slice.color else slice.color.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            selectedIndex = if (selectedIndex == index) null else index
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconView(
                                iconName = slice.iconName,
                                colorHex = slice.colorHex,
                                size = 38.dp
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = slice.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${PersianNumberFormatter.formatDigits(slice.transactionCount, currency)} تراکنش ثبت‌شده",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = PersianNumberFormatter.formatCurrency(slice.amount, currency),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = slice.color
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = slice.color.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${PersianNumberFormatter.formatDigits(pct, currency)}٪ از کل",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = slice.color,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { pctFloat * progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = slice.color,
                            trackColor = slice.color.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }
    }
}
