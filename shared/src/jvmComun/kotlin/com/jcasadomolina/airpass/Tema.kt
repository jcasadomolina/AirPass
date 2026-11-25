package com.jcasadomolina.airpass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Claro = lightColorScheme(
    primary = Color(0xFF151515),
    onPrimary = Color.White,
    secondary = Color(0xFF1F9D5C),
    onSecondary = Color.White,
    background = Color(0xFFF1F0ED),
    onBackground = Color(0xFF151515),
    surface = Color.White,
    onSurface = Color(0xFF151515),
    surfaceVariant = Color(0xFFEDECE8),
    onSurfaceVariant = Color(0xFF7C7B76),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEDECE8),
    secondaryContainer = Color(0xFFE6E5E1),
    onSecondaryContainer = Color(0xFF151515),
    outline = Color(0xFFD5D4CF),
    outlineVariant = Color(0xFFE6E5E1),
    error = Color(0xFFD5382B),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFF2F2F0),
    onPrimary = Color(0xFF121212),
    secondary = Color(0xFF34C27A),
    onSecondary = Color(0xFF0B0B0B),
    background = Color(0xFF0E0E0F),
    onBackground = Color(0xFFF2F2F0),
    surface = Color(0xFF1B1B1D),
    onSurface = Color(0xFFF2F2F0),
    surfaceVariant = Color(0xFF262628),
    onSurfaceVariant = Color(0xFF9B9BA0),
    surfaceContainerLowest = Color(0xFF1B1B1D),
    surfaceContainerLow = Color(0xFF1B1B1D),
    surfaceContainer = Color(0xFF202022),
    surfaceContainerHigh = Color(0xFF202022),
    surfaceContainerHighest = Color(0xFF2A2A2D),
    secondaryContainer = Color(0xFF2E2E31),
    onSecondaryContainer = Color(0xFFF2F2F0),
    outline = Color(0xFF3C3C40),
    outlineVariant = Color(0xFF2C2C30),
    error = Color(0xFFFF6B5E),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Tema de AirPass, inspirado en las referencias: fondo gris cálido, tarjetas blancas con sombra suave, botones
 * píldora casi negros y verde para el éxito. Sigue el tema claro u oscuro del sistema, salvo que se indique otro,
 * y pinta el fondo de la ventana.
 */
@Composable
fun TemaAirPass(oscuro: Boolean = isSystemInDarkTheme(), contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (oscuro) Oscuro else Claro, shapes = Formas) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize(), content = contenido)
    }
}
