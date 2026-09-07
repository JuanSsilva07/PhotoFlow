package com.example.aestheticcamera.ui.editor.ai

import android.graphics.Bitmap
import com.example.aestheticcamera.ui.editor.EditState
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 🪄 AutoEnhanceEngineV2 — Motor de IA On-Device com Detecção Inteligente de Cena
 *
 * Classifica a imagem em categorias semânticas de cena e aplica otimizações adaptativas:
 * - 🌿 LANDSCAPE (Paisagem / Natureza)
 * - 👤 PORTRAIT (Retrato / Pessoas)
 * - 🍕 FOOD (Comida / Alimentos)
 * - 🌙 NIGHT (Cena Noturna / Pouca Luz)
 * - 🌅 SUNSET (Pôr do Sol / Hora Dourada)
 * - 📷 GENERAL (Geral / Padrão)
 */
object AutoEnhanceEngineV2 {

    enum class SceneType(val displayName: String, val emoji: String) {
        LANDSCAPE("Paisagem & Natureza", "🌿"),
        PORTRAIT("Retrato & Pessoas", "👤"),
        FOOD("Comida & Gastronomia", "🍕"),
        NIGHT("Cena Noturna", "🌙"),
        SUNSET("Pôr do Sol", "🌅"),
        GENERAL("Ajuste Balanceado", "✨")
    }

    data class AnalysisResult(
        val sceneType: SceneType,
        val confidence: Float,
        val enhancedState: EditState
    )

    fun analyzeAndEnhance(src: Bitmap, currentState: EditState): AnalysisResult {
        try {
            if (src.isRecycled || src.width <= 0 || src.height <= 0) {
                return AnalysisResult(SceneType.GENERAL, 1.0f, currentState)
            }

            // 1. Amostragem rápida em escala 128x128 para cálculo de métricas
            val sampleW = 128
            val sampleH = 128
            val sampleBmp = Bitmap.createScaledBitmap(src, sampleW, sampleH, false)
            val pixels = IntArray(sampleW * sampleH)
            sampleBmp.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
            sampleBmp.recycle()

        val pixelCount = pixels.size.toDouble()

        var totalLum = 0.0
        var totalSat = 0.0
        var totalRed = 0.0
        var totalGreen = 0.0
        var totalBlue = 0.0

        var skinPixelCount = 0
        var greenVegetationCount = 0
        var warmSunsetCount = 0
        var darkCount = 0
        var brightCount = 0

        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            totalLum += lum
            totalRed += r
            totalGreen += g
            totalBlue += b

            val maxC = maxOf(r, maxOf(g, b))
            val minC = minOf(r, minOf(g, b))
            val sat = maxC - minC
            totalSat += sat.toDouble()

            if (lum < 50) darkCount++
            if (lum > 200) brightCount++

            // Detecção de tom de pele em RGB (Regra de Kovac et al.)
            if (r > 95 && g > 40 && b > 20 &&
                (maxC - minC) > 15 &&
                abs(r - g) > 15 && r > g && r > b
            ) {
                skinPixelCount++
            }

            // Detecção de vegetação (Verde predominante)
            if (g > r + 15 && g > b + 15 && g > 60) {
                greenVegetationCount++
            }

            // Detecção de Pôr do sol (Tons quentes alaranjados saturados no terço superior/médio)
            if (r > 150 && g > 80 && b < 100 && r > g && sat > 50) {
                warmSunsetCount++
            }
        }

        val avgLum = (totalLum / pixelCount).toFloat()
        val avgSat = (totalSat / pixelCount).toFloat()
        val avgRed = (totalRed / pixelCount).toFloat()
        val avgGreen = (totalGreen / pixelCount).toFloat()
        val avgBlue = (totalBlue / pixelCount).toFloat()

        val skinRatio = skinPixelCount / pixelCount.toFloat()
        val greenRatio = greenVegetationCount / pixelCount.toFloat()
        val sunsetRatio = warmSunsetCount / pixelCount.toFloat()
        val darkRatio = darkCount / pixelCount.toFloat()

        // 2. Classificação de Cena
        val sceneType: SceneType
        val confidence: Float

        when {
            darkRatio > 0.45f && avgLum < 85f -> {
                sceneType = SceneType.NIGHT
                confidence = (darkRatio * 1.2f).coerceIn(0.6f, 0.98f)
            }
            skinRatio > 0.18f -> {
                sceneType = SceneType.PORTRAIT
                confidence = (skinRatio * 3.0f).coerceIn(0.65f, 0.95f)
            }
            sunsetRatio > 0.22f && avgRed > avgBlue + 40f -> {
                sceneType = SceneType.SUNSET
                confidence = (sunsetRatio * 2.5f).coerceIn(0.6f, 0.95f)
            }
            greenRatio > 0.20f || (avgGreen > avgRed + 10f && avgGreen > avgBlue) -> {
                sceneType = SceneType.LANDSCAPE
                confidence = (greenRatio * 2.5f).coerceIn(0.6f, 0.95f)
            }
            avgSat > 75f && avgRed > 120f && avgGreen > 100f -> {
                sceneType = SceneType.FOOD
                confidence = 0.75f
            }
            else -> {
                sceneType = SceneType.GENERAL
                confidence = 0.80f
            }
        }

        // 3. Ajustes Específicos por Tipo de Cena
        val enhancedState = when (sceneType) {
            SceneType.LANDSCAPE -> {
                currentState.copy(
                    brightness = 5f,
                    contrast = 18f,
                    saturation = 22f,
                    highlights = -15f, // Recupera detalhes de nuvens no céu
                    shadows = 20f,     // Ilumina sombras de árvores
                    sharpness = 25f    // Realça detalhes de folhagens
                )
            }
            SceneType.PORTRAIT -> {
                currentState.copy(
                    brightness = 8f,
                    contrast = 8f,      // Contraste suave para pele uniforme
                    saturation = 10f,
                    warmth = 12f,       // Tonalidade quente saudável
                    highlights = -10f,
                    shadows = 15f,
                    sharpness = 10f
                )
            }
            SceneType.FOOD -> {
                currentState.copy(
                    brightness = 10f,
                    contrast = 15f,
                    saturation = 28f,    // Saturação viva nos pratos
                    warmth = 18f,        // Calidez apetitosa
                    highlights = -8f,
                    shadows = 10f,
                    sharpness = 20f
                )
            }
            SceneType.NIGHT -> {
                currentState.copy(
                    brightness = 25f,    // Clareia áreas escuras
                    contrast = 22f,
                    saturation = 8f,
                    highlights = -30f,   // Evita estourar luzes da cidade
                    shadows = 35f,       // Traz detalhes da noite
                    grain = 0f           // Mantém grão sob controle
                )
            }
            SceneType.SUNSET -> {
                currentState.copy(
                    brightness = 5f,
                    contrast = 20f,
                    saturation = 25f,
                    warmth = 30f,        // Intensifica a Hora Dourada
                    highlights = -20f,   // Preserva a bola do sol
                    shadows = 15f
                )
            }
            SceneType.GENERAL -> {
                // Algoritmo clássico de auto-balanceamento
                val newBrightness = if (avgLum < 100f) (115f - avgLum) * 0.35f else if (avgLum > 165f) (155f - avgLum) * 0.3f else 0f
                val newContrast = if (avgLum in 90f..150f) 12f else 5f
                val newSat = if (avgSat < 45f) 15f else 5f
                currentState.copy(
                    brightness = newBrightness.coerceIn(-25f, 25f),
                    contrast = newContrast,
                    saturation = newSat,
                    highlights = -10f,
                    shadows = 15f
                )
            }
        }

        return AnalysisResult(sceneType, confidence, enhancedState)
        } catch (_: Throwable) {
            return AnalysisResult(SceneType.GENERAL, 1.0f, currentState)
        }
    }
}
