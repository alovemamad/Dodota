package ir.mmd.dodota.ui.glass

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch

internal fun isRenderEffectSupported(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

object GlassShapes {
    val cell = RoundedCornerShape(26.dp)
    val module = RoundedCornerShape(24.dp)
    val capsule = RoundedCornerShape(percent = 50)
    val badge = RoundedCornerShape(8.dp)

    fun row(index: Int, count: Int, radius: Dp = 26.dp): Shape = when {
        count <= 1 -> RoundedCornerShape(radius)
        index == 0 -> RoundedCornerShape(topStart = radius, topEnd = radius)
        index == count - 1 -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
        else -> RoundedCornerShape(0.dp)
    }
}

fun glassRimBrush(c: GlassColors): Brush = Brush.linearGradient(
    0f to c.rimHi,
    0.28f to c.rimMid,
    0.48f to Color.Transparent,
    0.64f to Color.Transparent,
    1f to c.rimLo,
)

fun Modifier.glassRim(shape: Shape, c: GlassColors, width: Dp = 1.dp): Modifier =
    border(width, glassRimBrush(c), shape)

fun Modifier.glassHighlight(shape: Shape, color: Color, thickness: Dp = 1.dp): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val inner = Path().apply { addOutline(outline) }
    val shifted = Path().apply {
        addOutline(outline)
        translate(Offset(0f, thickness.toPx()))
    }
    onDrawWithContent {
        clipPath(inner) {
            clipPath(shifted, ClipOp.Difference) { drawRect(color) }
        }
        drawContent()
    }
}

fun Modifier.glassCell(c: GlassColors, shape: Shape = GlassShapes.cell): Modifier =
    clip(shape)
        .background(c.cell)
        .then(if (c.translucent) Modifier.glassHighlight(shape, c.highlight).glassRim(shape, c) else Modifier)

fun Modifier.glassModule(c: GlassColors, shape: Shape = GlassShapes.module): Modifier =
    clip(shape)
        .background(c.module)
        .then(if (c.reduced) Modifier else Modifier.glassHighlight(shape, c.highlight).glassRim(shape, c))

fun Modifier.glassChrome(
    c: GlassColors,
    shape: Shape,
    backdrop: Backdrop? = null,
    surface: Color = c.chrome,
    blurRadius: Dp = 12.dp,
    refraction: Boolean = true,
    elevated: Boolean = true,
    tilt: State<Float>? = null,
): Modifier {
    if (backdrop == null || c.reduced) {
        val fill = if (c.reduced) c.chromeSolid else lerp(c.chromeSolid, surface, 0.35f).copy(alpha = maxOf(surface.alpha, 0.9f))
        return this
            .then(if (elevated) Modifier.shadow(8.dp, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow) else Modifier)
            .clip(shape)
            .background(fill)
            .glassHighlight(shape, c.highlight)
            .glassRim(shape, c)
    }
    val fill = if (isRenderEffectSupported()) surface else c.chromeSolid.copy(alpha = 0.94f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            if (refraction) lens(12.dp.toPx(), 22.dp.toPx())
        },
        highlight = { glassHighlightOf(tilt) },
        shadow = if (elevated) ({ Shadow(radius = 24.dp, color = c.shadow) }) else null,
        onDrawSurface = { drawRect(fill) },
    )
}

fun glassHighlightOf(tilt: State<Float>?): Highlight =
    if (tilt == null) Highlight.Default else Highlight(style = HighlightStyle.Default(angle = tilt.value))

private class GlassPressIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = GlassPressNode(interactionSource, color)

    override fun equals(other: Any?): Boolean = other is GlassPressIndication && other.color == color

    override fun hashCode(): Int = color.hashCode()
}

private class GlassPressNode(
    private val source: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var count = 0
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> count++
                    is PressInteraction.Release -> count--
                    is PressInteraction.Cancel -> count--
                }
                val now = count > 0
                if (now != pressed) {
                    pressed = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        if (pressed) drawRect(color)
        drawContent()
    }
}

@Composable
fun glassRowIndication(): Indication {
    val c = Glass.colors
    return remember(c.fill2) { GlassPressIndication(c.fill2) }
}

@Composable
fun Modifier.glassPress(source: InteractionSource, scale: Float = 1.07f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed) scale else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "glass-press",
    )
    return graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 21.dp,
    tint: Color = Glass.colors.label,
    enabled: Boolean = true,
    backdrop: Backdrop? = null,
    tilt: State<Float>? = null,
) {
    val c = Glass.colors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .glassPress(source)
            .size(size)
            .glassChrome(c, CircleShape, backdrop, tilt = tilt)
            .semantics { contentDescription = label }
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

class GlassToolItem(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val modifier: Modifier = Modifier,
)

@Composable
fun GlassToolGroup(
    items: List<GlassToolItem>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    tilt: State<Float>? = null,
) {
    val c = Glass.colors
    Row(
        modifier
            .height(48.dp)
            .glassChrome(c, GlassShapes.capsule, backdrop, tilt = tilt)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            GlassToolIcon(item.icon, item.label, item.onClick, modifier = item.modifier, enabled = item.enabled)
        }
    }
}

@Composable
fun GlassToolIcon(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Glass.colors.label,
    content: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .glassPress(source)
            .size(48.dp)
            .semantics { contentDescription = label }
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        contentAlignment = Alignment.Center,
    ) {
        when {
            content != null -> content()
            icon != null -> Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconModifier: Modifier = Modifier,
    color: Color = Glass.colors.tint,
    enabled: Boolean = true,
    backdrop: Backdrop? = null,
) {
    val c = Glass.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .glassPress(source)
            .heightIn(min = 34.dp)
            .glassChrome(c, GlassShapes.capsule, backdrop = backdrop, elevated = false)
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = iconModifier.size(15.dp))
        }
        Text(
            text,
            style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val c = Glass.colors
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val track by animateColorAsState(if (checked) c.green else c.fill, label = "glass-switch-track")
    val x by animateDpAsState(
        if (checked) 24.dp else 2.dp,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "glass-switch-knob",
    )
    val squash by animateFloatAsState(if (pressed && enabled) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "glass-switch-press")
    Box(
        modifier
            .size(width = 63.dp, height = 28.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(GlassShapes.capsule)
            .background(track)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(checked, source, null, enabled, Role.Switch, onCheckedChange)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .offset(x = x, y = 2.dp)
                .size(width = 37.dp, height = 24.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.22f * squash
                    scaleY = 1f + 0.12f * squash
                }
                .shadow(3.dp, GlassShapes.capsule, clip = false, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                .clip(GlassShapes.capsule)
                .background(lerp(Color.White, Color.White.copy(alpha = 0.55f), squash))
                .then(if (squash > 0.01f) Modifier.glassHighlight(GlassShapes.capsule, Color.White, 1.dp) else Modifier),
        )
    }
}

@Composable
fun GlassSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val c = Glass.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .glassModule(c, GlassShapes.capsule)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(2.dp),
    ) {
        val slot = maxWidth / options.size
        val x by animateDpAsState(
            slot * selected.coerceIn(0, options.lastIndex),
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
            label = "glass-segment",
        )
        Box(
            Modifier
                .offset(x = x)
                .width(slot)
                .fillMaxHeight()
                .shadow(3.dp, GlassShapes.capsule, clip = false, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(GlassShapes.capsule)
                .background(c.thumb)
                .glassHighlight(GlassShapes.capsule, c.highlight),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            options.forEachIndexed { index, label ->
                val on = index == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(GlassShapes.capsule)
                        .selectable(on, remember { MutableInteractionSource() }, null, enabled, Role.Tab) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = GlassType.subhead().copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = c.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun GlassLargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = GlassType.largeTitle(),
        color = Glass.colors.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 20.dp),
    )
}

@Composable
fun GlassSectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = Glass.colors
    Row(
        modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 22.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = GlassType.footnote().copy(fontWeight = FontWeight.SemiBold),
            color = c.label2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(trailing, style = GlassType.footnote(), color = c.label2, maxLines = 1)
        }
    }
}

@Composable
fun GlassFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = GlassType.footnote(),
        color = Glass.colors.label2,
        modifier = modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp),
    )
}

@Composable
fun GlassPageBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backLabel: String = "بازگشت",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Glass.colors
    val backdrop = LocalGlassBackdrop.current
    val tilt = LocalGlassTilt.current
    Box(modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
        Text(
            title,
            style = GlassType.headline(),
            color = c.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
        )
        if (onBack != null) {
            GlassCircleButton(
                icon = GlassIcons.ChevronLeft,
                label = backLabel,
                onClick = onBack,
                backdrop = backdrop,
                tilt = tilt,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

private fun mix(a: Color, b: Color, t: Float): Color = lerp(a, b, t)

@Composable
fun GlassBadge(
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    @DrawableRes drawableRes: Int? = null,
    size: Dp = 30.dp,
) {
    val shape = RoundedCornerShape(size * 0.27f)
    val brush = remember(color) {
        Brush.verticalGradient(
            0f to mix(color, Color.White, 0.24f),
            0.62f to color,
            1f to mix(color, Color.Black, 0.08f),
        )
    }
    Box(
        modifier
            .size(size)
            .shadow(1.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(shape)
            .background(brush)
            .glassHighlight(shape, Color.White.copy(alpha = 0.5f), 0.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
            drawableRes != null -> Icon(painterResource(drawableRes), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
        }
    }
}

private class GlassLines {
    var ys by mutableStateOf(emptyList<Float>())
}

@Composable
fun GlassGroup(
    modifier: Modifier = Modifier,
    inset: Dp = 16.dp,
    horizontalPadding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val c = Glass.colors
    val lines = remember { GlassLines() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Layout(
        content = content,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .glassCell(c)
            .drawWithContent {
                drawContent()
                val start = inset.toPx()
                val stroke = maxOf(1f, 0.5.dp.toPx())
                val x0 = if (rtl) 0f else start
                val width = size.width - start
                lines.ys.forEach { y ->
                    drawRect(c.separator, topLeft = Offset(x0, y - stroke), size = Size(width, stroke))
                }
            },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
        val ys = ArrayList<Float>()
        var acc = 0
        val visible = placeables.count { it.height > 0 }
        var seen = 0
        placeables.forEach { p ->
            if (p.height > 0) {
                acc += p.height
                seen++
                if (seen < visible) ys += acc.toFloat()
            }
        }
        if (lines.ys != ys) lines.ys = ys
        layout(w, acc) {
            var y = 0
            placeables.forEach { p ->
                p.place(0, y)
                y += p.height
            }
        }
    }
}

@Composable
fun GlassRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    titleColor: Color = Glass.colors.label,
    onClick: (() -> Unit)? = null,
) {
    val c = Glass.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(source, glassRowIndication(), enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 10.dp else 8.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.body(), color = titleColor)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, style = GlassType.footnote(), color = c.label2)
            }
        }
        if (!value.isNullOrEmpty()) {
            Text(
                value,
                style = GlassType.body(),
                color = c.label2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
        if (trailing != null) trailing()
        if (chevron) {
            Icon(GlassIcons.ChevronRight, contentDescription = null, tint = c.label3, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
fun GlassSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Glass.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(checked, source, glassRowIndication(), enabled, Role.Switch, onCheckedChange)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = if (subtitle != null) 10.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f).graphicsLayer { alpha = if (enabled) 1f else 0.4f }) {
            Text(title, style = GlassType.body(), color = c.label)
            if (!subtitle.isNullOrEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, style = GlassType.footnote(), color = c.label2)
            }
        }
        GlassSwitch(checked = checked, onCheckedChange = null, enabled = enabled, interactionSource = source)
    }
}

@Composable
fun GlassRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val c = Glass.colors
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) c.tint else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, c.label3, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(GlassIcons.CheckBold, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

@Composable
fun <T> GlassListRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onSelected: (T) -> Unit,
) {
    val c = Glass.colors
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: options.firstOrNull()?.second.orEmpty()
    Box(modifier.fillMaxWidth()) {
        GlassRow(
            title = title,
            value = current,
            leading = leading,
            enabled = enabled,
            trailing = {
                Icon(GlassIcons.UpDown, contentDescription = null, tint = c.label2, modifier = Modifier.size(width = 13.dp, height = 16.dp))
            },
            onClick = { open = true },
        )
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(
                expanded = open && enabled,
                onDismissRequest = { open = false },
                shape = RoundedCornerShape(24.dp),
                containerColor = c.menu,
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = GlassType.body(), color = c.label) },
                        leadingIcon = {
                            if (value == selected) {
                                Icon(GlassIcons.Check, contentDescription = null, tint = c.label, modifier = Modifier.size(17.dp))
                            } else {
                                Spacer(Modifier.size(17.dp))
                            }
                        },
                        onClick = {
                            open = false
                            onSelected(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = Glass.colors
    val shape = GlassShapes.capsule
    Box(
        modifier
            .heightIn(min = 38.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(if (selected) c.tint else c.fill2)
            .selectable(selected, remember { MutableInteractionSource() }, glassRowIndication(), enabled, Role.RadioButton, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = GlassType.subhead().copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) Color.White else c.label,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> GlassChoices(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    onSelected: (T) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val style = GlassType.subhead().copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val density = LocalDensity.current
        val slot = maxWidth / options.size.coerceAtLeast(1)
        val labels = options.map { it.second }
        val measured = remember(labels, style, slot, density) {
            labels.all { label ->
                with(density) { measurer.measure(label, style, maxLines = 1, softWrap = false).size.width.toDp() } + 16.dp <= slot
            }
        }
        val fits = options.size in 2..4 && measured
        if (fits) {
            GlassSegmented(
                options = labels,
                selected = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
                onSelect = { onSelected(options[it].first) },
                enabled = enabled,
            )
        } else {
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { (value, label) ->
                    GlassChip(label, value == selected, enabled) { onSelected(value) }
                }
            }
        }
    }
}

@Composable
fun <T> GlassChoiceRow(
    title: String,
    summary: String?,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    onSelected: (T) -> Unit,
) {
    val c = Glass.colors
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)) {
        Text(title, style = GlassType.body(), color = c.label.copy(alpha = if (enabled) 1f else 0.4f))
        if (!summary.isNullOrEmpty()) {
            Spacer(Modifier.height(1.dp))
            Text(summary, style = GlassType.footnote(), color = c.label2)
        }
        Spacer(Modifier.height(10.dp))
        GlassChoices(options, selected, enabled, onSelected)
    }
}

@Composable
fun GlassInfoRow(title: String, body: String) {
    val c = Glass.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(22.dp).clip(GlassShapes.capsule).background(c.tint), contentAlignment = Alignment.Center) {
            Text("i", style = GlassType.footnote().copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.subhead().copy(fontWeight = FontWeight.SemiBold), color = c.label)
            Spacer(Modifier.height(2.dp))
            Text(body, style = GlassType.footnote(), color = c.label2)
        }
    }
}
