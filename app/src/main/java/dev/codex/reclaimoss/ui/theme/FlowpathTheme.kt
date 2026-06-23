package dev.codex.reclaimoss.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.codex.reclaimoss.settings.FontSizeScale
import dev.codex.reclaimoss.settings.ThemeMode

private val FlowpathLightColors = lightColorScheme(
    primary = Color(0xFF3F5FBF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF13265B),
    secondary = Color(0xFF6A8F82),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EFEA),
    onSecondaryContainer = Color(0xFF1B362E),
    tertiary = Color(0xFF7C5798),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEFDBFF),
    onTertiaryContainer = Color(0xFF2D1148),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF3F6FB),
    onBackground = Color(0xFF16202D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16202D),
    surfaceVariant = Color(0xFFD6DDEB),
    onSurfaceVariant = Color(0xFF3D4858),
    outline = Color(0xFFC7D2E0),
    outlineVariant = Color(0xFFDCE4EF),
    inverseSurface = Color(0xFF2B313A),
    inverseOnSurface = Color(0xFFEDF1F9),
    inversePrimary = Color(0xFFB2C6FF),
    surfaceTint = Color(0xFF3F5FBF),
)

private val FlowpathDarkColors = darkColorScheme(
    primary = Color(0xFFB8C7FF),
    onPrimary = Color(0xFF132762),
    primaryContainer = Color(0xFF2A448F),
    onPrimaryContainer = Color(0xFFE3E9FF),
    secondary = Color(0xFFA6CCBE),
    onSecondary = Color(0xFF17342C),
    secondaryContainer = Color(0xFF334C44),
    onSecondaryContainer = Color(0xFFE5F4EE),
    tertiary = Color(0xFFDBBDFF),
    onTertiary = Color(0xFF3E275A),
    tertiaryContainer = Color(0xFF553F71),
    onTertiaryContainer = Color(0xFFEFDBFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1E2024),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF292D33),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8E9199),
    outlineVariant = Color(0xFF43474E),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF2B313A),
    inversePrimary = Color(0xFF3F5FBF),
    surfaceTint = Color(0xFFB8C7FF),
)

private val FlowpathTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

internal fun scaledTypography(
    base: Typography,
    scaleFactor: Float,
): Typography = base.copy(
    displayLarge = base.displayLarge.scaledBy(scaleFactor),
    displayMedium = base.displayMedium.scaledBy(scaleFactor),
    displaySmall = base.displaySmall.scaledBy(scaleFactor),
    headlineLarge = base.headlineLarge.scaledBy(scaleFactor),
    headlineMedium = base.headlineMedium.scaledBy(scaleFactor),
    headlineSmall = base.headlineSmall.scaledBy(scaleFactor),
    titleLarge = base.titleLarge.scaledBy(scaleFactor),
    titleMedium = base.titleMedium.scaledBy(scaleFactor),
    titleSmall = base.titleSmall.scaledBy(scaleFactor),
    bodyLarge = base.bodyLarge.scaledBy(scaleFactor),
    bodyMedium = base.bodyMedium.scaledBy(scaleFactor),
    bodySmall = base.bodySmall.scaledBy(scaleFactor),
    labelLarge = base.labelLarge.scaledBy(scaleFactor),
    labelMedium = base.labelMedium.scaledBy(scaleFactor),
    labelSmall = base.labelSmall.scaledBy(scaleFactor),
)

private fun TextStyle.scaledBy(scaleFactor: Float): TextStyle = copy(
    fontSize = fontSize.scaledBy(scaleFactor),
    lineHeight = lineHeight.scaledBy(scaleFactor),
    letterSpacing = letterSpacing.scaledBy(scaleFactor),
)

private fun TextUnit.scaledBy(scaleFactor: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else (value * scaleFactor).sp

private val FlowpathShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun FlowpathTheme(
    themeMode: ThemeMode,
    fontSizeScale: FontSizeScale,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (useDarkTheme) FlowpathDarkColors else FlowpathLightColors
    val view = LocalView.current

    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        window.statusBarColor = colorScheme.background.toArgb()
        window.navigationBarColor = colorScheme.surface.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = !useDarkTheme
            isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(FlowpathTypography, fontSizeScale.scaleFactor),
        shapes = FlowpathShapes,
        content = content,
    )
}
