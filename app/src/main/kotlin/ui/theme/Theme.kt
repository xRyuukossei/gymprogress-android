package br.com.ryuukossei.gymprogress.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta escura, no mesmo tom do app web (fundo azul-petróleo #061D2D).
private val Background = Color(0xFF061D2D)
private val Surface = Color(0xFF0B2738)
private val SurfaceVariant = Color(0xFF12344A)
private val Primary = Color(0xFF3DD6C6)
private val OnPrimary = Color(0xFF00201C)
private val Secondary = Color(0xFF8FB8D0)
private val Success = Color(0xFF4ADE80)
private val Warning = Color(0xFFFBBF24)

val SuccessColor = Success
val WarningColor = Warning

private val GymColors = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFF0F4A44),
    onPrimaryContainer = Color(0xFFB8F5EE),
    secondary = Secondary,
    onSecondary = Color(0xFF0B2738),
    background = Background,
    onBackground = Color(0xFFE6EEF3),
    surface = Surface,
    onSurface = Color(0xFFE6EEF3),
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = Color(0xFF9DB3C2),
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceVariant,
    outline = Color(0xFF2B4E63),
    error = Color(0xFFFF8A80),
)

@Composable
fun GymProgressTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GymColors, content = content)
}
