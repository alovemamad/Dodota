package ir.mmd.dodota.ui.components.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.AppCurrency
import ir.mmd.dodota.util.PersianNumberFormatter

data class TrendPoint(
    val label: String,
    val value: Long
)

@Composable
fun TrendLineChart(
    points: List<TrendPoint>,
    currency: AppCurrency,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    chartTitle: String = "روند تغییرات",
    modifier: Modifier = Modifier
) {
    if (points.size < 2 || points.all { it.value == 0L }) {
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
                    imageVector = Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(38.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "داده کافی برای ترسیم نمودار خطی روند (حداقل ۲ روز/ماه) وجود ندارد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var selectedIndex by remember(points) { mutableStateOf<Int?>(points.lastIndex) }
    var animationPlayed by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "line_anim"
    )

    LaunchedEffect(points) {
        animationPlayed = true
    }

    val maxVal = points.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
    val minVal = points.minOfOrNull { it.value }?.coerceAtMost(0L) ?: 0L
    val range = (maxVal - minVal).coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                            val index = ((offset.x + (stepX / 2f)) / stepX).toInt().coerceIn(0, points.lastIndex)
                            selectedIndex = index
                        }
                    }
            ) {
                val width = size.width
                val height = size.height - 20.dp.toPx()
                val stepX = width / (points.size - 1).coerceAtLeast(1)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                // Draw background dashed grid lines
                for (i in 0..3) {
                    val y = height * (i.toFloat() / 3f)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.16f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (i < 3) dashEffect else null
                    )
                }

                val path = Path()
                val fillPath = Path()

                val offsets = points.mapIndexed { index, p ->
                    val x = index * stepX
                    val normY = (p.value - minVal).toFloat() / range.toFloat()
                    val y = height - (normY * (height - 16.dp.toPx()) * progress) - 8.dp.toPx()
                    Offset(x, y)
                }

                if (offsets.isNotEmpty()) {
                    path.moveTo(offsets[0].x, offsets[0].y)
                    fillPath.moveTo(offsets[0].x, height)
                    fillPath.lineTo(offsets[0].x, offsets[0].y)

                    for (i in 1 until offsets.size) {
                        val prev = offsets[i - 1]
                        val cur = offsets[i]
                        val cx = (prev.x + cur.x) / 2f
                        path.cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
                        fillPath.cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
                    }

                    fillPath.lineTo(offsets.last().x, height)
                    fillPath.close()

                    // Draw glowing gradient fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.42f), lineColor.copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = height
                        )
                    )

                    // Draw outer glow stroke
                    drawPath(
                        path = path,
                        color = lineColor.copy(alpha = 0.25f),
                        style = Stroke(
                            width = 8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw crisp main stroke line
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw vertical guide line & glowing point dots
                    offsets.forEachIndexed { index, pt ->
                        val isSelected = selectedIndex == index
                        if (isSelected) {
                            drawLine(
                                color = lineColor.copy(alpha = 0.45f),
                                start = Offset(pt.x, pt.y),
                                end = Offset(pt.x, height),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = dashEffect
                            )
                            drawCircle(
                                color = lineColor.copy(alpha = 0.3f),
                                radius = 12.dp.toPx(),
                                center = pt
                            )
                        }
                        val dotRadius = if (isSelected) 6.dp.toPx() else 4.dp.toPx()
                        drawCircle(
                            color = Color.White,
                            radius = dotRadius + 2.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = lineColor,
                            radius = dotRadius,
                            center = pt
                        )
                    }
                }
            }
        }

        // Labels row below line chart
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = (points.size / 5).coerceAtLeast(1)
            points.forEachIndexed { index, point ->
                if (index % step == 0 || index == points.lastIndex) {
                    val isSelected = selectedIndex == index
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isSelected) lineColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { selectedIndex = index }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Selected point card
        selectedIndex?.let { idx ->
            if (idx in points.indices) {
                val pt = points[idx]
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, lineColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "نقطه انتخابی: ${pt.label}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = chartTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = PersianNumberFormatter.formatCurrency(pt.value, currency, showSign = true),
                            fontWeight = FontWeight.ExtraBold,
                            color = lineColor,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}
