package com.example.aestheticcamera.ui.editor.model

import androidx.compose.ui.graphics.Color

/**
 * ✍️ Data class para sobreposição de Texto no Editor
 */
data class TextOverlayItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String = "Sua Legenda",
    val fontStyle: String = "SERIF", // SERIF, SCRIPT, SANS, TYPEWRITER, BOLD
    val colorHex: String = "#FFFFFF",
    val xPercent: Float = 0.5f,
    val yPercent: Float = 0.5f,
    val rotation: Float = 0f,
    val fontSizeSp: Float = 24f,
    val opacity: Float = 1.0f
)

/**
 * 🌟 Data class para sobreposição de Stickers no Editor
 */
data class StickerOverlayItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val symbol: String = "✨",
    val category: String = "Sparkles",
    val xPercent: Float = 0.5f,
    val yPercent: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f
)

/**
 * 🔍 Modo de Desfoque Tilt-Shift
 */
enum class TiltShiftMode {
    NONE,
    LINEAR,
    RADIAL
}

/**
 * 🔍 Configurações do Tilt-Shift
 */
data class TiltShiftConfig(
    val mode: TiltShiftMode = TiltShiftMode.NONE,
    val focusPosition: Float = 0.5f, // 0.0f a 1.0f no eixo vertical ou raio central
    val radius: Float = 0.3f,        // Largura da faixa focal / Raio do círculo
    val blurStrength: Float = 12f     // Intensidade do desfoque (1f a 25f)
)
