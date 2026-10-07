package com.omersusin.earlab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Night-lab world, picked from the use scene (dark room, focused listening):
// charcoal ground, one amber accent like a VU lamp. Light scheme exists for
// daylight, same roles, never a second identity.
private val Char = Color(0xFF14100B)
private val CharContainer = Color(0xFF221A10)
private val Amber = Color(0xFFFFB300)
private val AmberContainer = Color(0xFF4A2E00)
private val Bone = Color(0xFFF5E9DD)

private val Cream = Color(0xFFFFF8F1)
private val Ember = Color(0xFF9C4300)
private val EmberContainer = Color(0xFFFFD9C0)
private val Ink = Color(0xFF201A15)

private val DarkScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2A1A00),
    primaryContainer = AmberContainer,
    onPrimaryContainer = Color(0xFFFFDCC6),
    surface = Char,
    onSurface = Bone,
    onSurfaceVariant = Color(0xFFCDBFAC),
    surfaceContainer = CharContainer,
    outline = Color(0xFF4A382C),
)

private val LightScheme = lightColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    primaryContainer = EmberContainer,
    onPrimaryContainer = Color(0xFF3A1C00),
    surface = Cream,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF5C4B40),
    surfaceContainer = Color(0xFFFBEFE3),
    outline = Color(0xFFD8C4B2),
)

@Composable
fun EarLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
