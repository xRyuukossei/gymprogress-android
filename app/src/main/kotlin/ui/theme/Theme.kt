package br.com.ryuukossei.gymprogress.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import br.com.ryuukossei.gymprogress.R

// Paleta da identidade visual da Azuric aplicada ao GymProgress.
private val BrandDarkNavy = Color(0xFF061D2D)
private val BrandNavy = Color(0xFF04355B)
private val BrandBlue = Color(0xFF0264F0)
private val BrandBlueOnDark = Color(0xFF80B4FF)
private val BrandGray = Color(0xFF788492)
private val BrandOffWhite = Color(0xFFF5F6FA)
private val SuccessEmeraldOnDark = Color(0xFF42B883)
private val SuccessGreenOnLight = Color(0xFF176B45)

private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

private val Defaults = Typography()
private val GymTypography = Defaults.copy(
    displayLarge = Defaults.displayLarge.copy(fontFamily = Poppins),
    displayMedium = Defaults.displayMedium.copy(fontFamily = Poppins),
    displaySmall = Defaults.displaySmall.copy(fontFamily = Poppins),
    headlineLarge = Defaults.headlineLarge.copy(fontFamily = Poppins),
    headlineMedium = Defaults.headlineMedium.copy(fontFamily = Poppins),
    headlineSmall = Defaults.headlineSmall.copy(fontFamily = Poppins),
    titleLarge = Defaults.titleLarge.copy(fontFamily = Poppins),
    titleMedium = Defaults.titleMedium.copy(fontFamily = Poppins),
    titleSmall = Defaults.titleSmall.copy(fontFamily = Poppins),
    bodyLarge = Defaults.bodyLarge.copy(fontFamily = Poppins),
    bodyMedium = Defaults.bodyMedium.copy(fontFamily = Poppins),
    bodySmall = Defaults.bodySmall.copy(fontFamily = Poppins),
    labelLarge = Defaults.labelLarge.copy(fontFamily = Poppins),
    labelMedium = Defaults.labelMedium.copy(fontFamily = Poppins),
    labelSmall = Defaults.labelSmall.copy(fontFamily = Poppins),
)

private val GymDarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = BrandOffWhite,
    primaryContainer = Color(0xFF0D3B68),
    onPrimaryContainer = Color(0xFFE5EEFF),
    secondary = BrandBlueOnDark,
    onSecondary = BrandDarkNavy,
    secondaryContainer = BrandNavy,
    onSecondaryContainer = BrandOffWhite,
    tertiary = SuccessEmeraldOnDark,
    onTertiary = BrandDarkNavy,
    tertiaryContainer = Color(0xFF123E30),
    onTertiaryContainer = Color(0xFFC5F0DB),
    background = BrandDarkNavy,
    onBackground = BrandOffWhite,
    surface = BrandDarkNavy,
    onSurface = BrandOffWhite,
    surfaceVariant = Color(0xFF0B2A45),
    onSurfaceVariant = Color(0xFFC3CCD5),
    surfaceDim = BrandDarkNavy,
    surfaceBright = Color(0xFF174B72),
    surfaceContainerLowest = Color(0xFF031522),
    surfaceContainerLow = Color(0xFF082338),
    surfaceContainer = BrandNavy,
    surfaceContainerHigh = Color(0xFF0B3F66),
    surfaceContainerHighest = Color(0xFF134E75),
    outline = BrandGray,
    outlineVariant = Color(0xFF345166),
    error = Color(0xFFFFB4AB),
)

private val GymLightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = BrandOffWhite,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = BrandNavy,
    secondary = BrandBlue,
    onSecondary = BrandOffWhite,
    secondaryContainer = Color(0xFFE4EAF2),
    onSecondaryContainer = BrandDarkNavy,
    tertiary = SuccessGreenOnLight,
    onTertiary = BrandOffWhite,
    tertiaryContainer = Color(0xFFD7F1E3),
    onTertiaryContainer = Color(0xFF123B2A),
    background = BrandOffWhite,
    onBackground = BrandDarkNavy,
    surface = BrandOffWhite,
    onSurface = BrandDarkNavy,
    surfaceVariant = Color(0xFFE9EDF3),
    onSurfaceVariant = Color(0xFF596675),
    surfaceDim = Color(0xFFDCE2E9),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = BrandOffWhite,
    surfaceContainerHigh = Color(0xFFE9EDF3),
    surfaceContainerHighest = Color(0xFFDDE4EC),
    outline = BrandGray,
    outlineVariant = Color(0xFFC8D2DC),
    error = Color(0xFFB3261E),
)

@Composable
fun workoutWarningColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFF3C969)
    else Color(0xFF805600)

@Composable
fun GymProgressTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) GymDarkColors else GymLightColors,
        typography = GymTypography,
        content = content,
    )
}
