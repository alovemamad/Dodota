package ir.mmd.dodota.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.mmd.dodota.data.model.AppDesignMode
import ir.mmd.dodota.data.model.GlassAppearanceMode
import ir.mmd.dodota.data.model.GlassMaterial
import ir.mmd.dodota.data.model.GlassPrefs
import ir.mmd.dodota.data.model.GlassTint
import ir.mmd.dodota.data.model.GlassWallpaper
import ir.mmd.dodota.ui.components.GlassCard
import ir.mmd.dodota.ui.theme.VipRedPrimary

fun glassWallpaperName(kind: GlassWallpaper): String = kind.title

private fun tintName(tint: GlassTint): String = tint.title

@Composable
fun DodotaDesignPicker(
    selected: AppDesignMode,
    onSelect: (AppDesignMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (Glass.active) {
        GlassSectionHeader("سبک رابط کاربری", modifier = modifier)
        GlassDesignPickerContent(selected, onSelect)
        GlassFooter("تغییر بین طراحی کلاسیک دودوتا و رابط شیشه‌ای پویا (Liquid Glass) بدون تغییر اطلاعات حساب‌ها")
        return
    }
    GlassCard(
        shape = RoundedCornerShape(22.dp),
        isHighlighted = true,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "سبک رابط کاربری برنامه",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "انتخاب بین ظاهر کلاسیک فعلی و طراحی شیشه‌ای زنده (لیکوئید گلس)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClassicDesignCard(
                    name = AppDesignMode.CLASSIC.title,
                    subtitle = "تم‌های فعلی و کارت‌های نئونی",
                    selected = selected == AppDesignMode.CLASSIC,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("design_mode_classic"),
                    onClick = { onSelect(AppDesignMode.CLASSIC) },
                ) { ClassicDodotaPreview() }
                ClassicDesignCard(
                    name = AppDesignMode.LIQUID_GLASS.title,
                    subtitle = "شیشه بازتاب‌دار و نوار شناور",
                    selected = selected == AppDesignMode.LIQUID_GLASS,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("design_mode_liquid_glass"),
                    onClick = { onSelect(AppDesignMode.LIQUID_GLASS) },
                ) { GlassDesignPreview() }
            }
        }
    }
}

@Composable
private fun GlassDesignPickerContent(
    selected: AppDesignMode,
    onSelect: (AppDesignMode) -> Unit,
) {
    val c = Glass.colors
    GlassGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val items = listOf(
                Triple(AppDesignMode.CLASSIC, AppDesignMode.CLASSIC.title, "design_mode_classic"),
                Triple(AppDesignMode.LIQUID_GLASS, AppDesignMode.LIQUID_GLASS.title, "design_mode_liquid_glass"),
            )
            items.forEach { (mode, label, tag) ->
                val on = selected == mode
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .testTag(tag)
                        .selectable(on, remember { MutableInteractionSource() }, null, true, Role.RadioButton) { onSelect(mode) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .border(if (on) 2.dp else 0.5.dp, if (on) c.tint else c.separator, shape)
                            .padding(if (on) 2.dp else 0.5.dp)
                            .clip(shape),
                    ) {
                        if (mode == AppDesignMode.LIQUID_GLASS) GlassDesignPreview() else ClassicDodotaPreview()
                    }
                    Text(
                        text = label,
                        style = GlassType.subhead().copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = c.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    GlassRadio(on)
                }
            }
        }
    }
}

@Composable
private fun ClassicDesignCard(
    name: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 26.dp else 18.dp, label = "design-corner")
    val shape = RoundedCornerShape(corner)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(142.dp)
                .clip(shape)
                .background(cs.surface)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) cs.primary else cs.outline.copy(alpha = 0.35f), shape)
                .selectable(
                    selected = selected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(),
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
        ) {
            preview()
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-10).dp, y = 10.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(cs.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = cs.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (selected) cs.primary else cs.onSurface,
            maxLines = 1,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ClassicDodotaPreview() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .padding(10.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4C0815), Color(0xFF22050B))))
                    .border(1.dp, VipRedPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF30D158).copy(alpha = 0.35f)))
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(VipRedPrimary.copy(alpha = 0.35f)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1C1C24))
                            .border(0.75.dp, VipRedPrimary.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1A1A22),
                border = BorderStroke(0.75.dp, VipRedPrimary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().height(20.dp),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(4) { i ->
                        Box(
                            Modifier
                                .size(if (i == 0) 12.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (i == 0) VipRedPrimary else Color.White.copy(alpha = 0.3f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassDesignPreview() {
    val dark = if (Glass.active) Glass.colors.dark else isSystemInDarkTheme()
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawGlassWallpaper(GlassWallpaper.Lagoon, dark) },
    ) {
        val ink = if (dark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.78f)
        val glassBit = if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.68f)
        val chromeBit = if (dark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.85f)
        Bit(0.10f, 0.10f, 0.42f, 0.06f, ink, 3.dp)
        Bit(0.08f, 0.22f, 0.84f, 0.26f, glassBit, 12.dp)
        Bit(0.08f, 0.53f, 0.40f, 0.18f, glassBit, 10.dp)
        Bit(0.52f, 0.53f, 0.40f, 0.18f, glassBit, 10.dp)
        Bit(0.08f, 0.82f, 0.62f, 0.11f, chromeBit, 12.dp)
        Bit(0.74f, 0.82f, 0.18f, 0.11f, chromeBit, 12.dp)
    }
}

@Composable
fun GlassAppearanceSection(
    prefs: GlassPrefs,
    onUpdatePrefs: ((GlassPrefs) -> GlassPrefs) -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (prefs.appearance) {
        GlassAppearanceMode.Automatic -> systemDark
        GlassAppearanceMode.Light -> false
        GlassAppearanceMode.Dark -> true
    }
    GlassSectionHeader("حالت نمایش")
    GlassGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(42.dp, Alignment.CenterHorizontally),
        ) {
            LookChoice("روشن", selected = !dark, darkPreview = false) {
                onUpdatePrefs { it.copy(appearance = GlassAppearanceMode.Light) }
            }
            LookChoice("تاریک", selected = dark, darkPreview = true) {
                onUpdatePrefs { it.copy(appearance = GlassAppearanceMode.Dark) }
            }
        }
        GlassSwitchRow(
            title = "خودکار (مطابق سیستم)",
            checked = prefs.appearance == GlassAppearanceMode.Automatic,
            onCheckedChange = { on ->
                onUpdatePrefs {
                    it.copy(
                        appearance = if (on) GlassAppearanceMode.Automatic
                        else if (dark) GlassAppearanceMode.Dark else GlassAppearanceMode.Light,
                    )
                }
            },
            modifier = Modifier.testTag("glass_auto_appearance_switch"),
        )
    }
}

@Composable
private fun PreviewChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val c = Glass.colors
    Column(
        modifier.selectable(selected, remember { MutableInteractionSource() }, null, true, Role.RadioButton, onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        preview()
        Text(label, style = GlassType.footnote().copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        GlassRadio(selected)
    }
}

private fun Modifier.choiceRing(selected: Boolean, c: GlassColors, shape: Shape): Modifier =
    border(if (selected) 2.dp else 0.5.dp, if (selected) c.tint else c.separator, shape)

@Composable
private fun LookChoice(label: String, selected: Boolean, darkPreview: Boolean, onClick: () -> Unit) {
    val c = Glass.colors
    val shape = RoundedCornerShape(14.dp)
    PreviewChoice(label, selected, onClick) {
        Box(
            Modifier
                .size(width = 68.dp, height = 122.dp)
                .choiceRing(selected, c, shape)
                .padding(if (selected) 2.dp else 0.5.dp)
                .clip(shape)
                .background(if (darkPreview) Color.Black else Color(0xFFF2F2F7)),
        ) {
            val bar = if (darkPreview) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.75f)
            val card = if (darkPreview) Color(0xFF1C1C1E) else Color.White
            val glass = if (darkPreview) Color(0xCC505054) else Color(0xE6FFFFFF)
            Bit(0.12f, 0.12f, 0.46f, 0.05f, bar, 3.dp)
            Bit(0.09f, 0.26f, 0.82f, 0.24f, card, 8.dp)
            Bit(0.09f, 0.55f, 0.82f, 0.16f, card, 8.dp)
            Bit(0.09f, 0.85f, 0.64f, 0.09f, glass, 10.dp)
            Bit(0.75f, 0.85f, 0.16f, 0.09f, glass, 10.dp)
        }
    }
}

@Composable
private fun BoxScope.Bit(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Dp) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRoundRect(
                    color,
                    topLeft = Offset(size.width * x, size.height * y),
                    size = Size(size.width * w, size.height * h),
                    cornerRadius = CornerRadius(radius.toPx()),
                )
            },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassLookSection(
    prefs: GlassPrefs,
    onUpdatePrefs: ((GlassPrefs) -> GlassPrefs) -> Unit,
) {
    val c = Glass.colors

    GlassSectionHeader("جنس شیشه (Liquid Glass)")
    GlassGroup {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        ) {
            MaterialChoice(GlassMaterial.Clear, "شفاف (Clear)", prefs.material == GlassMaterial.Clear) {
                onUpdatePrefs { it.copy(material = GlassMaterial.Clear) }
            }
            MaterialChoice(GlassMaterial.Tinted, "مات رنگی (Tinted)", prefs.material == GlassMaterial.Tinted) {
                onUpdatePrefs { it.copy(material = GlassMaterial.Tinted) }
            }
        }
    }
    GlassFooter("حالت شفاف عبور نور و پس‌زمینه را برجسته‌تر نشان می‌دهد و حالت مات کنتراست خوانایی بالاتری دارد.")

    Spacer(Modifier.height(16.dp))
    GlassGroup {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            Text("رنگ تاکیدی (Accent)", style = GlassType.body(), color = c.label)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassTint.entries.forEach { tint ->
                    val color = IosColors.tint(tint, c.dark)
                    val on = prefs.tint == tint
                    val name = tintName(tint)
                    Box(
                        Modifier
                            .size(36.dp)
                            .then(if (on) Modifier.border(2.5.dp, color, CircleShape) else Modifier)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(color)
                            .testTag("glass_tint_${tint.name.lowercase()}")
                            .semantics { contentDescription = name }
                            .selectable(on, remember { MutableInteractionSource() }, null, true, Role.RadioButton) {
                                onUpdatePrefs { it.copy(tint = tint) }
                            },
                    )
                }
            }
        }
        GlassListRow(
            title = "پس‌زمینه پویا",
            options = GlassWallpaper.entries.map { it to glassWallpaperName(it) },
            selected = prefs.wallpaper,
            enabled = true,
            modifier = Modifier.testTag("glass_wallpaper_picker"),
            onSelected = { v -> onUpdatePrefs { it.copy(wallpaper = v) } },
        )
    }

    Spacer(Modifier.height(16.dp))
    GlassGroup {
        GlassSwitchRow(
            title = "کارت‌ها و لیست‌های شیشه‌ای",
            checked = prefs.glassLists,
            enabled = !prefs.reduceTransparency,
            onCheckedChange = { v -> onUpdatePrefs { it.copy(glassLists = v) } },
        )
        GlassSwitchRow(
            title = "جمع شدن نوار پایین هنگام اسکرول",
            checked = prefs.shrinkTabBar,
            onCheckedChange = { v -> onUpdatePrefs { it.copy(shrinkTabBar = v) } },
        )
        GlassSwitchRow(
            title = "بازتاب نوری حرکت گوشی",
            subtitle = "تغییر زاویه انعکاس نور شیشه بر اساس شیب دستگاه",
            checked = prefs.motionHighlights,
            enabled = !prefs.reduceTransparency,
            onCheckedChange = { v -> onUpdatePrefs { it.copy(motionHighlights = v) } },
        )
        GlassSwitchRow(
            title = "کاهش شفافیت (خوانایی بالا)",
            checked = prefs.reduceTransparency,
            onCheckedChange = { v -> onUpdatePrefs { it.copy(reduceTransparency = v) } },
        )
    }
    GlassFooter("کاهش شفافیت، افکت بلور و انکسار نور را برای مصرف باتری کمتر و کنتراست بیشتر غیرفعال می‌کند.")

    GlassSectionHeader("اجزای صفحه ویترین (Home)")
    GlassGroup {
        GlassSwitchRow(
            title = "شاخص تراز درآمد و هزینه ماهانه",
            checked = prefs.homeGauges,
            onCheckedChange = { v -> onUpdatePrefs { it.copy(homeGauges = v) } },
        )
        GlassSwitchRow(
            title = "نمودار روند روزانه ماه جاری",
            checked = prefs.homeSparklines,
            onCheckedChange = { v -> onUpdatePrefs { it.copy(homeSparklines = v) } },
        )
        GlassSwitchRow(
            title = "نوار افقی حساب‌ها و کارت‌ها",
            checked = prefs.homeAccountsStrip,
            onCheckedChange = { v -> onUpdatePrefs { it.copy(homeAccountsStrip = v) } },
        )
        GlassSwitchRow(
            title = "شبکه ابزارهای مالی پیشرفته",
            checked = prefs.homeToolsGrid,
            onCheckedChange = { v -> onUpdatePrefs { it.copy(homeToolsGrid = v) } },
        )
    }
}

private val Photo = Brush.linearGradient(
    0f to Color(0xFFFF8D28),
    0.35f to Color(0xFFFF2D55),
    0.68f to Color(0xFF6155F5),
    1f to Color(0xFF00C3D0),
)

@Composable
private fun MaterialChoice(kind: GlassMaterial, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Glass.colors
    val shape = RoundedCornerShape(16.dp)
    val tinted = kind == GlassMaterial.Tinted
    PreviewChoice(label, selected, onClick) {
        Box(
            Modifier
                .size(width = 128.dp, height = 92.dp)
                .choiceRing(selected, c, shape)
                .padding(if (selected) 2.dp else 0.5.dp)
                .clip(shape)
                .background(Photo)
                .drawBehind {
                    val stripe = 5.dp.toPx()
                    val gap = 13.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawRect(Color.White.copy(alpha = 0.28f), topLeft = Offset(x, 0f), size = Size(stripe, size.height))
                        x += gap
                    }
                },
        ) {
            val capsule = GlassShapes.capsule
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .fillMaxWidth()
                    .height(30.dp)
                    .shadow(4.dp, capsule, clip = false)
                    .clip(capsule)
                    .background(if (tinted) Color(0xD1FFFFFF) else Color(0x24FFFFFF))
                    .glassHighlight(capsule, Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (tinted) Color.Black.copy(alpha = 0.72f) else Color.White
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(4.dp)).background(ink))
                Box(Modifier.size(11.dp).clip(CircleShape).background(ink))
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(ink))
            }
        }
    }
}
