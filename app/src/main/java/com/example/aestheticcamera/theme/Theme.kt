package com.example.aestheticcamera.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// ─── Design System de Cores ───────────────────────────────────────────────
private val PhotoFlowDarkScheme = darkColorScheme(
    // Accent principal: Âmbar dourado
    primary          = Amber400,
    onPrimary        = Surface0,
    primaryContainer = AmberGlow,
    onPrimaryContainer = Amber300,

    // Accent secundário
    secondary        = Amber500,
    onSecondary      = Surface0,
    secondaryContainer = Surface2,
    onSecondaryContainer = TextSecondary,

    // Accent terciário
    tertiary         = Amber600,
    onTertiary       = Surface0,

    // Superfícies
    background       = Surface0,
    onBackground     = TextPrimary,
    surface          = Surface1,
    onSurface        = TextPrimary,
    surfaceVariant   = Surface2,
    onSurfaceVariant = TextSecondary,

    // Bordas e contornos
    outline          = BorderMid,
    outlineVariant   = BorderSubtle,

    // Erros
    error            = ErrorRed,
    onError          = TextPrimary,
)

@Composable
fun AestheticCameraTheme(
    content: @Composable () -> Unit,
) {
    // Dynamic Color desativado para preservar o design curado PhotoFlow
    MaterialTheme(
        colorScheme = PhotoFlowDarkScheme,
        typography  = Typography,
        content     = content
    )
}
