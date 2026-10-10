package ir.mmd.dodota.ui.components.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.AppCurrency
import ir.mmd.dodota.ui.theme.ExpenseRed
import ir.mmd.dodota.ui.theme.IncomeGreen
import ir.mmd.dodota.util.PersianNumberFormatter

data class BarComparisonGroup(
    val label: String,
    val incomeAmount: Long,
    val expenseAmount: Long
)

@Composable
fun BarComparisonChart(
    groups: List<BarComparisonGroup>,
    currency: AppCurrency,
    modifier: Modifier = Modifier
) {
    if (groups.isEmpty() || groups.all { it.incomeAmount == 0L && it.expenseAmount == 0L }) {
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
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "داده‌ای برای مقایسه میله‌ای در این دوره وجود ندارد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(groups.indexOfLast { it.incomeAmount > 0 || it.expenseAmount > 0 }.takeIf { it >= 0 }) }
    var animationPlayed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "bar_anim"
    )

    LaunchedEffect(groups) {
        animationPlayed = true
    }

    val maxVal = groups.maxOfOrNull { maxOf(it.incomeAmount, it.expenseAmount) }?.coerceAtLeast(1L) ?: 1L

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        // Legend pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = IncomeGreen.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, IncomeGreen.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(IncomeGreen))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("درآمد ماهانه", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = IncomeGreen)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ExpenseRed.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ExpenseRed))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("هزینه ماهانه", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ExpenseRed)
                }
            }
        }

        // Canvas for gradient bars with tap selection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(groups) {
                        detectTapGestures { offset ->
                            val groupWidth = size.width / groups.size.coerceAtLeast(1)
                            val index = (offset.x / groupWidth).toInt().coerceIn(0, groups.size - 1)
                            selectedIndex = if (selectedIndex == index) null else index
                        }
                    }
            ) {
                val groupCount = groups.size.coerceAtLeast(1)
                val availableWidth = size.width
                val chartHeight = size.height - 16.dp.toPx()
                val groupWidth = availableWidth / groupCount
                val barWidth = (groupWidth * 0.28f).coerceAtMost(20.dp.toPx())
                val barSpacing = 4.dp.toPx()
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                // Draw dashed horizontal grid lines
                val gridLines = 4
                for (i in 0..gridLines) {
                    val y = chartHeight * (1f - (i.toFloat() / gridLines))
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.18f),
                        start = Offset(0f, y),
                        end = Offset(availableWidth, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (i > 0) dashEffect else null
                    )
                }

                groups.forEachIndexed { index, group ->
                    val centerX = (index * groupWidth) + (groupWidth / 2f)
                    val incomeHeight = (group.incomeAmount.toFloat() / maxVal.toFloat()) * chartHeight * progress
                    val expenseHeight = (group.expenseAmount.toFloat() / maxVal.toFloat()) * chartHeight * progress
                    val isSelected = selectedIndex == index

                    // Highlight column pill behind selected month
                    if (isSelected) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.05f),
                            topLeft = Offset(index * groupWidth + 4.dp.toPx(), 0f),
                            size = Size(groupWidth - 8.dp.toPx(), chartHeight),
                            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                        )
                    }

                    // Income Bar (Green Gradient)
                    val incLeft = centerX - barWidth - (barSpacing / 2f)
                    val incH = incomeHeight.coerceAtLeast(4.dp.toPx())
                    val incTop = chartHeight - incH
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (selectedIndex == null || isSelected) IncomeGreen else IncomeGreen.copy(alpha = 0.4f),
                                if (selectedIndex == null || isSelected) IncomeGreen.copy(alpha = 0.6f) else IncomeGreen.copy(alpha = 0.2f)
                            ),
                            startY = incTop,
                            endY = chartHeight
                        ),
                        topLeft = Offset(incLeft, incTop),
                        size = Size(barWidth, incH),
                        cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                    )

                    // Expense Bar (Red Gradient)
                    val expLeft = centerX + (barSpacing / 2f)
                    val expH = expenseHeight.coerceAtLeast(4.dp.toPx())
                    val expTop = chartHeight - expH
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (selectedIndex == null || isSelected) ExpenseRed else ExpenseRed.copy(alpha = 0.4f),
                                if (selectedIndex == null || isSelected) ExpenseRed.copy(alpha = 0.6f) else ExpenseRed.copy(alpha = 0.2f)
                            ),
                            startY = expTop,
                            endY = chartHeight
                        ),
                        topLeft = Offset(expLeft, expTop),
                        size = Size(barWidth, expH),
                        cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                    )
                }
            }
        }

        // Labels row below bars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            groups.forEachIndexed { index, group ->
                val isSelected = selectedIndex == index
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedIndex = if (selectedIndex == index) null else index }
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Selected group details card
        selectedIndex?.let { idx ->
            if (idx in groups.indices) {
                val group = groups[idx]
                val net = group.incomeAmount - group.expenseAmount
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "عملکرد ${group.label}",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "درآمد: +${PersianNumberFormatter.formatCurrency(group.incomeAmount, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = IncomeGreen
                            )
                            Text(
                                text = "هزینه: -${PersianNumberFormatter.formatCurrency(group.expenseAmount, currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ExpenseRed
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "تراز خالص ماه",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = (if (net >= 0) IncomeGreen else ExpenseRed).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = PersianNumberFormatter.formatCurrency(net, currency, showSign = true),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (net >= 0) IncomeGreen else ExpenseRed,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
