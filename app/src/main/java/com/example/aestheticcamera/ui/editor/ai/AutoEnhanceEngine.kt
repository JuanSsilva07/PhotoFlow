package com.example.aestheticcamera.ui.editor.ai

import android.graphics.Bitmap
import com.example.aestheticcamera.ui.editor.EditState
import kotlin.math.sqrt

/**
 * 🪄 Motor de Otimização Automática Baseado em Análise de Histograma (On-Device AI Auto-Enhance)
 */
object AutoEnhanceEngine {

    fun autoEnhanceImage(src: Bitmap, currentState: EditState): EditState {
        if (src.width <= 0 || src.height <= 0) return currentState

        // 1. Reduzir a amostragem da imagem para análise rápida do histograma (128x128)
        val sampleW = 128
        val sampleH = 128
        val sampleBmp = Bitmap.createScaledBitmap(src, sampleW, sampleH, false)
        val pixels = IntArray(sampleW * sampleH)
        sampleBmp.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        sampleBmp.recycle()

        var totalLum = 0.0
        var totalSat = 0.0
        var totalRed = 0.0
        var totalBlue = 0.0
        var highlightCount = 0
        var shadowCount = 0

        val pixelCount = pixels.size.toDouble()

        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            totalLum += lum

            val maxC = maxOf(r, maxOf(g, b))
            val minC = minOf(r, minOf(g, b))
            val sat = (maxC - minC).toDouble()
            totalSat += sat

            totalRed += r
            totalBlue += b

            if (lum > 210) highlightCount++
            if (lum < 45) shadowCount++
        }

        val avgLum = (totalLum / pixelCount).toFloat()
        val avgSat = (totalSat / pixelCount).toFloat()
        val avgRed = (totalRed / pixelCount).toFloat()
        val avgBlue = (totalBlue / pixelCount).toFloat()

        // 2. Calcular variância/desvio padrão para o contraste
        var variance = 0.0
        for (color in pixels) {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            val diff = lum - avgLum
            variance += diff * diff
        }
        val stdDevContrast = sqrt(variance / pixelCount).toFloat()

        // 3. Determinar Ajustes Compensatórios
        var newBrightness = 0f
        var newContrast = 0f
        var newSaturation = 0f
        var newWarmth = 0f
        var newHighlights = 0f
        var newShadows = 0f

        // A) Ajuste de Brilho e Sombras/Realces
        if (avgLum < 100f) {
            // Imagem escura (Subexposta): Aumentar brilho e recuperar sombras
            newBrightness = ((115f - avgLum) * 0.4f).coerceIn(10f, 35f)
            newShadows = ((100f - avgLum) * 0.35f).coerceIn(10f, 30f)
        } else if (avgLum > 165f) {
            // Imagem clara (Super-exposta): Reduzir brilho e reter realces
            newBrightness = ((155f - avgLum) * 0.35f).coerceIn(-30f, -8f)
            newHighlights = ((165f - avgLum) * 0.4f).coerceIn(-35f, -10f)
        }

        // B) Ajuste de Realces/Sombras baseado na distribuição
        val shadowRatio = (shadowCount / pixelCount).toFloat()
        val highlightRatio = (highlightCount / pixelCount).toFloat()
        if (shadowRatio > 0.25f) {
            newShadows += (shadowRatio * 40f).coerceIn(10f, 35f)
        }
        if (highlightRatio > 0.20f) {
            newHighlights -= (highlightRatio * 45f).coerceIn(10f, 40f)
        }

        // C) Ajuste de Contraste
        if (stdDevContrast < 45f) {
            // Baixo contraste: Dar vida à imagem
            newContrast = ((50f - stdDevContrast) * 0.7f).coerceIn(12f, 32f)
        } else if (stdDevContrast > 75f) {
            // Alto contraste excessivo: Suavizar
            newContrast = ((70f - stdDevContrast) * 0.4f).coerceIn(-20f, -5f)
        }

        // D) Ajuste de Saturação
        if (avgSat < 40f) {
            newSaturation = ((45f - avgSat) * 0.6f).coerceIn(10f, 30f)
        }

        // E) Ajuste de Balanço de Cor (Warmth)
        val tempDiff = avgRed - avgBlue
        if (tempDiff < -15f) {
            // Tom frio (azulado): Adicionar calidez
            newWarmth = (-tempDiff * 0.5f).coerceIn(8f, 25f)
        } else if (tempDiff > 40f) {
            // Tom quente excessivo (amarelado): Resfriar levemente
            newWarmth = ((30f - tempDiff) * 0.35f).coerceIn(-20f, -5f)
        }

        return currentState.copy(
            brightness = newBrightness,
            contrast = newContrast,
            saturation = newSaturation,
            warmth = newWarmth,
            highlights = newHighlights,
            shadows = newShadows
        )
    }
}
