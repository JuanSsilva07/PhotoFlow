package com.example.aestheticcamera.ui.editor

import androidx.compose.ui.graphics.Color

enum class HslColorChannel(val displayName: String, val color: Color) {
    RED("Vermelho", Color(0xFFFF3B30)),
    ORANGE("Laranja", Color(0xFFFF9500)),
    YELLOW("Amarelo", Color(0xFFFFCC00)),
    GREEN("Verde", Color(0xFF34C759)),
    CYAN("Ciano", Color(0xFF5AC8FA)),
    BLUE("Azul", Color(0xFF007AFF)),
    PURPLE("Roxo", Color(0xFF5856D6)),
    MAGENTA("Magenta", Color(0xFFFF2D55))
}

data class HslAdjustment(
    val hue: Float = 0f,        // -100f to 100f
    val saturation: Float = 0f, // -100f to 100f
    val lightness: Float = 0f   // -100f to 100f
)
