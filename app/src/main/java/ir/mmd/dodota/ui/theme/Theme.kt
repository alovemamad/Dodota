package ir.mmd.dodota.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ir.mmd.dodota.data.model.AppThemeMode

// Standard Light
private val StandardLightScheme = lightColorScheme(
    primary = StandardLightPrimary,
    onPrimary = StandardLightOnPrimary,
    primaryContainer = StandardLightPrimaryContainer,
    onPrimaryContainer = StandardLightOnPrimaryContainer,
    secondary = StandardLightSecondary,
    background = StandardLightBackground,
    surface = StandardLightSurface,
    surfaceVariant = StandardLightSurfaceVariant,
    onBackground = StandardLightTextPrimary,
    onSurface = StandardLightTextPrimary,
    onSurfaceVariant = StandardLightTextSecondary
)

// Standard Dark
private val StandardDarkScheme = darkColorScheme(
    primary = StandardDarkPrimary,
    onPrimary = StandardDarkOnPrimary,
    primaryContainer = StandardDarkPrimaryContainer,
    onPrimaryContainer = StandardDarkOnPrimaryContainer,
    secondary = StandardDarkSecondary,
    background = StandardDarkBackground,
    surface = StandardDarkSurface,
    surfaceVariant = StandardDarkSurfaceVariant,
    onBackground = StandardDarkTextPrimary,
    onSurface = StandardDarkTextPrimary,
    onSurfaceVariant = StandardDarkTextSecondary
)

// BluBank Royal (Combo: Cobalt Blue + Electric Cyan Accent)
private val BluRoyalLightScheme = lightColorScheme(
    primary = BluRoyalPrimary,
    onPrimary = Color.White,
    primaryContainer = BluRoyalPrimaryContainer,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = BluRoyalSecondary,
    background = BluRoyalBackgroundLight,
    surface = BluRoyalSurfaceLight,
    surfaceVariant = Color(0xFFE2E8F0),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

private val BluRoyalDarkScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF22D3EE),
    background = BluRoyalBackgroundDark,
    surface = BluRoyalSurfaceDark,
    surfaceVariant = Color(0xFF1E293B),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

// Emerald & Mint (Combo: Emerald Green + Fresh Mint)
private val EmeraldLightScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = EmeraldOnPrimaryLight,
    primaryContainer = EmeraldPrimaryContainerLight,
    onPrimaryContainer = EmeraldOnPrimaryContainerLight,
    secondary = EmeraldSecondaryLight,
    background = EmeraldBackgroundLight,
    surface = EmeraldSurfaceLight,
    surfaceVariant = EmeraldSurfaceVariantLight,
    onBackground = EmeraldTextPrimaryLight,
    onSurface = EmeraldTextPrimaryLight,
    onSurfaceVariant = EmeraldTextSecondaryLight
)

private val EmeraldDarkScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = EmeraldOnPrimaryDark,
    primaryContainer = EmeraldPrimaryContainerDark,
    onPrimaryContainer = EmeraldOnPrimaryContainerDark,
    secondary = EmeraldSecondaryDark,
    background = EmeraldBackgroundDark,
    surface = EmeraldSurfaceDark,
    surfaceVariant = EmeraldSurfaceVariantDark,
    onBackground = EmeraldTextPrimaryDark,
    onSurface = EmeraldTextPrimaryDark,
    onSurfaceVariant = EmeraldTextSecondaryDark
)

// Sunset Neon (Combo: Purple / Magenta + Sunset Orange)
private val SunsetLightScheme = lightColorScheme(
    primary = SunsetPrimary,
    onPrimary = Color.White,
    primaryContainer = SunsetPrimaryContainerLight,
    onPrimaryContainer = Color(0xFF581C87),
    secondary = SunsetSecondary,
    background = SunsetBackgroundLight,
    surface = SunsetSurfaceLight,
    surfaceVariant = Color(0xFFFFEDD5),
    onBackground = Color(0xFF2E1065),
    onSurface = Color(0xFF2E1065)
)

private val SunsetDarkScheme = darkColorScheme(
    primary = Color(0xFFC084FC),
    onPrimary = Color(0xFF3B0764),
    primaryContainer = Color(0xFF581C87),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFFFB923C),
    background = SunsetBackgroundDark,
    surface = SunsetSurfaceDark,
    surfaceVariant = Color(0xFF3B185F),
    onBackground = Color(0xFFFAF5FF),
    onSurface = Color(0xFFFAF5FF)
)

// Crypto Gold Luxury (Combo: Rich Gold + Charcoal / Obsidian)
private val GoldLightScheme = lightColorScheme(
    primary = GoldPrimary,
    onPrimary = Color.White,
    primaryContainer = GoldPrimaryContainerLight,
    onPrimaryContainer = Color(0xFF78350F),
    secondary = Color(0xFFB45309),
    background = GoldBackgroundLight,
    surface = GoldSurfaceLight,
    surfaceVariant = Color(0xFFF5F0E8),
    onBackground = Color(0xFF1C1917),
    onSurface = Color(0xFF1C1917)
)

private val GoldDarkScheme = darkColorScheme(
    primary = GoldSecondary,
    onPrimary = Color(0xFF451A03),
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = Color(0xFFFCD34D),
    background = GoldBackgroundDark,
    surface = GoldSurfaceDark,
    surfaceVariant = GoldSurfaceVariantDark,
    onBackground = Color(0xFFFFFBEB),
    onSurface = Color(0xFFFFFBEB)
)

// Galaxy Purple (Combo: Royal Violet + Lavender)
private val PurpleLightScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurplePrimaryContainerLight,
    onPrimaryContainer = Color(0xFF3B0764),
    secondary = Color(0xFF9333EA),
    background = PurpleBackgroundLight,
    surface = PurpleSurfaceLight,
    surfaceVariant = Color(0xFFF3E8FF),
    onBackground = Color(0xFF1E1B4B),
    onSurface = Color(0xFF1E1B4B)
)

private val PurpleDarkScheme = darkColorScheme(
    primary = PurplePrimaryDark,
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFC084FC),
    background = PurpleBackgroundDark,
    surface = PurpleSurfaceDark,
    surfaceVariant = Color(0xFF3B0764),
    onBackground = Color(0xFFFAF5FF),
    onSurface = Color(0xFFFAF5FF)
)

// OLED Pure Black
private val BlackScheme = darkColorScheme(
    primary = BlackPrimary,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF27272A),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFA1A1AA),
    background = BlackBackground,
    surface = BlackSurface,
    surfaceVariant = BlackSurfaceVariant,
    onBackground = Color(0xFFF4F4F5),
    onSurface = Color(0xFFF4F4F5)
)

// VIP Red (Glassmorphic Crimson & Carbon Black)
private val VipRedScheme = darkColorScheme(
    primary = VipRedPrimary,
    onPrimary = VipRedOnPrimary,
    primaryContainer = VipRedPrimaryContainer,
    onPrimaryContainer = VipRedOnPrimaryContainer,
    secondary = VipRedSecondary,
    background = VipRedBackground,
    surface = VipRedSurface,
    surfaceVariant = VipRedSurfaceVariant,
    onBackground = VipRedTextPrimary,
    onSurface = VipRedTextPrimary,
    onSurfaceVariant = VipRedTextSecondary,
    outline = Color(0xFF94A3B8)
)

val LocalAppThemeMode = androidx.compose.runtime.staticCompositionLocalOf { AppThemeMode.VIP_RED }
val LocalAppFontFamily = androidx.compose.runtime.staticCompositionLocalOf { ir.mmd.dodota.data.model.AppFontFamilyOption.VAZIRMATN }
val LocalAppFontScale = androidx.compose.runtime.staticCompositionLocalOf { 1.0f }

data class DodotaGlassStyle(
    val isGlassEnabled: Boolean = true,
    val cardContainerColor: Color = VipRedSurfaceVariant.copy(alpha = 0.85f),
    val cardBorderColor: Color = Color.White.copy(alpha = 0.14f),
    val accentGlowColor: Color = VipRedPrimary
)

val LocalDodotaGlassStyle = androidx.compose.runtime.staticCompositionLocalOf { DodotaGlassStyle() }

val DodotaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun DodotaTheme(
    userPreferences: ir.mmd.dodota.data.preferences.UserPreferences,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    ir.mmd.dodota.util.PersianNumberFormatter.globalUseEnglishDigits = userPreferences.currency.isEnglishDigits
    DodotaTheme(
        themeMode = userPreferences.themeMode,
        fontFamily = userPreferences.fontFamily,
        fontScale = userPreferences.fontScale,
        darkTheme = darkTheme,
        content = content
    )
}

@Composable
fun DodotaTheme(
    themeMode: AppThemeMode = AppThemeMode.VIP_RED,
    fontFamily: ir.mmd.dodota.data.model.AppFontFamilyOption = ir.mmd.dodota.data.model.AppFontFamilyOption.VAZIRMATN,
    fontScale: Float = 1.0f,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.VIP_RED -> VipRedScheme
        AppThemeMode.SYSTEM -> if (darkTheme) StandardDarkScheme else StandardLightScheme
        AppThemeMode.LIGHT -> StandardLightScheme
        AppThemeMode.DARK -> StandardDarkScheme
        AppThemeMode.BLUBANK -> if (darkTheme) BluRoyalDarkScheme else BluRoyalLightScheme
        AppThemeMode.EMERALD -> if (darkTheme) EmeraldDarkScheme else EmeraldLightScheme
        AppThemeMode.SUNSET -> if (darkTheme) SunsetDarkScheme else SunsetLightScheme
        AppThemeMode.GOLD -> if (darkTheme) GoldDarkScheme else GoldLightScheme
        AppThemeMode.PURPLE -> if (darkTheme) PurpleDarkScheme else PurpleLightScheme
        AppThemeMode.BLACK -> BlackScheme
    }

    val isGlass = themeMode == AppThemeMode.VIP_RED ||
            themeMode == AppThemeMode.BLUBANK ||
            themeMode == AppThemeMode.SUNSET ||
            themeMode == AppThemeMode.GOLD ||
            themeMode == AppThemeMode.PURPLE ||
            themeMode == AppThemeMode.BLACK

    val glassStyle = DodotaGlassStyle(
        isGlassEnabled = isGlass,
        cardContainerColor = if (themeMode == AppThemeMode.VIP_RED) {
            VipRedSurfaceVariant.copy(alpha = 0.85f)
        } else {
            colorScheme.surfaceVariant.copy(alpha = if (isGlass) 0.88f else 0.96f)
        },
        cardBorderColor = if (themeMode == AppThemeMode.VIP_RED) {
            VipRedPrimary.copy(alpha = 0.35f)
        } else {
            colorScheme.primary.copy(alpha = 0.28f)
        },
        accentGlowColor = colorScheme.primary
    )

    val dynamicTypography = androidx.compose.runtime.remember(fontFamily, fontScale) {
        createDodotaTypography(fontOption = fontFamily, fontScale = fontScale)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                val isDark = when (themeMode) {
                    AppThemeMode.VIP_RED, AppThemeMode.DARK, AppThemeMode.BLACK -> true
                    AppThemeMode.SYSTEM -> darkTheme
                    AppThemeMode.LIGHT -> false
                    else -> darkTheme
                }
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = dynamicTypography,
        shapes = DodotaShapes
    ) {
        // Enforce Persian RTL layout direction and provide current theme & typography state throughout the entire app
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalAppThemeMode provides themeMode,
            LocalAppFontFamily provides fontFamily,
            LocalAppFontScale provides fontScale,
            LocalDodotaGlassStyle provides glassStyle
        ) {
            content()
        }
    }
}

