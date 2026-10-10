package ir.mmd.dodota.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.mmd.dodota.R
import ir.mmd.dodota.data.model.AppFontFamilyOption

private fun buildLocalFontFamily(fontResId: Int): FontFamily = FontFamily(
    Font(resId = fontResId, weight = FontWeight.Light),
    Font(resId = fontResId, weight = FontWeight.Normal),
    Font(resId = fontResId, weight = FontWeight.Medium),
    Font(resId = fontResId, weight = FontWeight.SemiBold),
    Font(resId = fontResId, weight = FontWeight.Bold),
    Font(resId = fontResId, weight = FontWeight.ExtraBold)
)

val VazirmatnFontFamily = buildLocalFontFamily(R.font.vazirmatn)
val CairoFontFamily = buildLocalFontFamily(R.font.cairo)
val AlmaraiFontFamily = buildLocalFontFamily(R.font.almarai)
val RubikFontFamily = buildLocalFontFamily(R.font.rubik)
val LalezarFontFamily = buildLocalFontFamily(R.font.lalezar)
val MarkaziFontFamily = buildLocalFontFamily(R.font.markazi_text)
val MirzaFontFamily = buildLocalFontFamily(R.font.mirza)
val NotoNaskhFontFamily = buildLocalFontFamily(R.font.noto_naskh_arabic)

val AppFontFamily = VazirmatnFontFamily

fun getDodotaFontFamily(option: AppFontFamilyOption): FontFamily {
    return when (option) {
        AppFontFamilyOption.VAZIRMATN -> VazirmatnFontFamily
        AppFontFamilyOption.CAIRO -> CairoFontFamily
        AppFontFamilyOption.ALMARAI -> AlmaraiFontFamily
        AppFontFamilyOption.RUBIK -> RubikFontFamily
        AppFontFamilyOption.LALEZAR -> LalezarFontFamily
        AppFontFamilyOption.MARKAZI -> MarkaziFontFamily
        AppFontFamilyOption.MIRZA -> MirzaFontFamily
        AppFontFamilyOption.NOTO_NASKH -> NotoNaskhFontFamily
    }
}

fun createDodotaTypography(
    fontOption: AppFontFamilyOption = AppFontFamilyOption.VAZIRMATN,
    fontScale: Float = 1.0f
): Typography {
    val family = getDodotaFontFamily(fontOption)
    // Some calligraphic/classic fonts like Markazi & Mirza have slightly smaller intrinsic x-height,
    // so we apply a subtle optical compensation factor alongside user's chosen fontScale.
    val opticalMultiplier = when (fontOption) {
        AppFontFamilyOption.MARKAZI, AppFontFamilyOption.MIRZA -> 1.08f
        else -> 1.0f
    }
    val s = (fontScale.coerceIn(0.80f, 1.35f) * opticalMultiplier)

    return Typography(
        displayLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (32f * s).sp,
            lineHeight = (40f * s).sp,
            letterSpacing = 0.sp
        ),
        displayMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (28f * s).sp,
            lineHeight = (36f * s).sp,
            letterSpacing = 0.sp
        ),
        displaySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (24f * s).sp,
            lineHeight = (32f * s).sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (22f * s).sp,
            lineHeight = (30f * s).sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (20f * s).sp,
            lineHeight = (28f * s).sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (18f * s).sp,
            lineHeight = (26f * s).sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = (19f * s).sp,
            lineHeight = (27f * s).sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16f * s).sp,
            lineHeight = (24f * s).sp,
            letterSpacing = 0.1.sp
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (14f * s).sp,
            lineHeight = (20f * s).sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (16f * s).sp,
            lineHeight = (24f * s).sp,
            letterSpacing = 0.15.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (14f * s).sp,
            lineHeight = (21f * s).sp,
            letterSpacing = 0.2.sp
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = (12f * s).sp,
            lineHeight = (18f * s).sp,
            letterSpacing = 0.3.sp
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (14f * s).sp,
            lineHeight = (20f * s).sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (12f * s).sp,
            lineHeight = (17f * s).sp,
            letterSpacing = 0.4.sp
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = (11f * s).sp,
            lineHeight = (16f * s).sp,
            letterSpacing = 0.4.sp
        )
    )
}

val Typography = createDodotaTypography()
