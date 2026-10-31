package io.selene.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Palette originale de Selene : bleu-vert profond, accents ambre.
private val LightColors = lightColorScheme(
    primary = Color(0xFF14615C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA9F2E9),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A635F),
    tertiary = Color(0xFF7D5800),
    surface = Color(0xFFFAFDFB),
    background = Color(0xFFFAFDFB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8ED6CD),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFA9F2E9),
    secondary = Color(0xFFB1CCC7),
    tertiary = Color(0xFFFABD1F),
    surface = Color(0xFF0E1513),
    background = Color(0xFF0E1513),
)

@Composable
fun SeleneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colors, content = content)
}
