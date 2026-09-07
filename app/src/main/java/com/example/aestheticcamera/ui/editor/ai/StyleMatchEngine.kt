package com.example.aestheticcamera.ui.editor.ai

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * 🎨 StyleMatchEngine — Transferência de Estilo de Foto por Algoritmo de Reinhard (Color Transfer in Lab Space)
 *
 * Mapeia a atmosfera de cores (brilho, contraste, temperatura e matizes) de uma foto de referência
 * para a foto sob edição no espaço de cor Lab.
 */
object StyleMatchEngine {

    /**
     * Aplica o estilo da imagem de referência [refBitmap] na imagem de origem [srcBitmap].
     * Retorna a imagem com o estilo transferido.
     */
    suspend fun applyStyleMatch(srcBitmap: Bitmap, refBitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = srcBitmap.width
        val height = srcBitmap.height

        val srcPixels = IntArray(width * height)
        srcBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // 1. Extrair estatísticas (Média e Desvio Padrão L, a, b) da imagem de referência
        val refStats = calculateLabStats(refBitmap)

        // 2. Extrair estatísticas da imagem de origem
        val srcStats = calculateLabStatsFromPixels(srcPixels)

        // Evita divisão por zero se desvio for muito pequeno
        val stdLRatio = if (srcStats.stdL > 0.001f) refStats.stdL / srcStats.stdL else 1f
        val stdARatio = if (srcStats.stdA > 0.001f) refStats.stdA / srcStats.stdA else 1f
        val stdBRatio = if (srcStats.stdB > 0.001f) refStats.stdB / srcStats.stdB else 1f

        val resultPixels = IntArray(srcPixels.size)

        // 3. Mapear cada pixel da origem para o estilo da referência
        for (i in srcPixels.indices) {
            val color = srcPixels[i]
            val a = (color shr 24) and 0xFF
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            // RGB → Lab
            val lab = rgbToLab(r, g, b)

            // Escalonamento de Reinhard: (x - mean_src) * (std_ref / std_src) + mean_ref
            val newL = ((lab[0] - srcStats.meanL) * stdLRatio + refStats.meanL).coerceIn(0f, 100f)
            val newA = ((lab[1] - srcStats.meanA) * stdARatio + refStats.meanA).coerceIn(-128f, 127f)
            val newB = ((lab[2] - srcStats.meanB) * stdBRatio + refStats.meanB).coerceIn(-128f, 127f)

            // Lab → RGB
            val rgb = labToRgb(newL, newA, newB)
            resultPixels[i] = (a shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
        }

        val resultBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBmp.setPixels(resultPixels, 0, width, 0, 0, width, height)
        resultBmp
    }

    private data class LabStats(
        val meanL: Float, val stdL: Float,
        val meanA: Float, val stdA: Float,
        val meanB: Float, val stdB: Float
    )

    private fun calculateLabStats(bitmap: Bitmap): LabStats {
        val sampleW = (bitmap.width / 4).coerceAtLeast(64)
        val sampleH = (bitmap.height / 4).coerceAtLeast(64)
        val sampleBmp = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, false)
        val pixels = IntArray(sampleW * sampleH)
        sampleBmp.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        sampleBmp.recycle()

        return calculateLabStatsFromPixels(pixels)
    }

    private fun calculateLabStatsFromPixels(pixels: IntArray): LabStats {
        var sumL = 0.0
        var sumA = 0.0
        var sumB = 0.0
        val count = pixels.size.toDouble()

        val lVals = FloatArray(pixels.size)
        val aVals = FloatArray(pixels.size)
        val bVals = FloatArray(pixels.size)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val lab = rgbToLab(r, g, b)
            lVals[i] = lab[0]
            aVals[i] = lab[1]
            bVals[i] = lab[2]

            sumL += lab[0]
            sumA += lab[1]
            sumB += lab[2]
        }

        val meanL = (sumL / count).toFloat()
        val meanA = (sumA / count).toFloat()
        val meanB = (sumB / count).toFloat()

        var varL = 0.0
        var varA = 0.0
        var varB = 0.0

        for (i in pixels.indices) {
            val dL = lVals[i] - meanL
            val dA = aVals[i] - meanA
            val dB = bVals[i] - meanB

            varL += dL * dL
            varA += dA * dA
            varB += dB * dB
        }

        val stdL = sqrt(varL / count).toFloat()
        val stdA = sqrt(varA / count).toFloat()
        val stdB = sqrt(varB / count).toFloat()

        return LabStats(meanL, stdL, meanA, stdA, meanB, stdB)
    }

    // ─── Conversões RGB ↔ Lab (CIELAB sRGB D65) ───────────────────────────

    private fun rgbToLab(r: Int, g: Int, b: Int): FloatArray {
        var rf = r / 255f
        var gf = g / 255f
        var bf = b / 255f

        rf = if (rf > 0.04045f) Math.pow(((rf + 0.055) / 1.055), 2.4).toFloat() else (rf / 12.92f)
        gf = if (gf > 0.04045f) Math.pow(((gf + 0.055) / 1.055), 2.4).toFloat() else (gf / 12.92f)
        bf = if (bf > 0.04045f) Math.pow(((bf + 0.055) / 1.055), 2.4).toFloat() else (bf / 12.92f)

        // Observer = 2°, Illuminant = D65
        var x = (rf * 0.4124f + gf * 0.3576f + bf * 0.1805f) / 0.95047f
        var y = (rf * 0.2126f + gf * 0.7152f + bf * 0.0722f) / 1.00000f
        var z = (rf * 0.0193f + gf * 0.1192f + bf * 0.9505f) / 1.08883f

        x = if (x > 0.008856f) Math.cbrt(x.toDouble()).toFloat() else (7.787f * x + 16f / 116f)
        y = if (y > 0.008856f) Math.cbrt(y.toDouble()).toFloat() else (7.787f * y + 16f / 116f)
        z = if (z > 0.008856f) Math.cbrt(z.toDouble()).toFloat() else (7.787f * z + 16f / 116f)

        val l = (116f * y) - 16f
        val a = 500f * (x - y)
        val bVal = 200f * (y - z)

        return floatArrayOf(l, a, bVal)
    }

    private fun labToRgb(l: Float, a: Float, b: Float): IntArray {
        var y = (l + 16f) / 116f
        var x = a / 500f + y
        var z = y - b / 200f

        val x3 = x * x * x
        val y3 = y * y * y
        val z3 = z * z * z

        x = if (x3 > 0.008856f) x3 else (x - 16f / 116f) / 7.787f
        y = if (y3 > 0.008856f) y3 else (y - 16f / 116f) / 7.787f
        z = if (z3 > 0.008856f) z3 else (z - 16f / 116f) / 7.787f

        x *= 0.95047f
        y *= 1.00000f
        z *= 1.08883f

        var rf = x * 3.2406f + y * -1.5372f + z * -0.4986f
        var gf = x * -0.9689f + y * 1.8758f + z * 0.0415f
        var bf = x * 0.0557f + y * -0.2040f + z * 1.0570f

        rf = if (rf > 0.0031308f) (1.055f * Math.pow(rf.toDouble(), 1.0 / 2.4) - 0.055f).toFloat() else (12.92f * rf)
        gf = if (gf > 0.0031308f) (1.055f * Math.pow(gf.toDouble(), 1.0 / 2.4) - 0.055f).toFloat() else (12.92f * gf)
        bf = if (bf > 0.0031308f) (1.055f * Math.pow(bf.toDouble(), 1.0 / 2.4) - 0.055f).toFloat() else (12.92f * bf)

        val rInt = (rf * 255f).toInt().coerceIn(0, 255)
        val gInt = (gf * 255f).toInt().coerceIn(0, 255)
        val bInt = (bf * 255f).toInt().coerceIn(0, 255)

        return intArrayOf(rInt, gInt, bInt)
    }
}
