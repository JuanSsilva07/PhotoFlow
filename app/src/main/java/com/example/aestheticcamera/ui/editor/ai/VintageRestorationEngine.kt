package com.example.aestheticcamera.ui.editor.ai

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 📜 VintageRestorationEngine — Restauração de Fotos Antigas Digitalizadas
 *
 * Aplica equalização de histograma adaptativo e remoção de ruído escuro (median denoise)
 * para recuperar vivacidade e detalhes em fotos antigas e desbotadas.
 */
object VintageRestorationEngine {

    suspend fun restorePhoto(srcBitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = srcBitmap.width
        val height = srcBitmap.height

        val pixels = IntArray(width * height)
        srcBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Calcular limites de contraste (Percentis 2% e 98%) para remoção de desbotado
        val lums = IntArray(pixels.size)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            lums[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
        }

        lums.sort()
        val minLum = lums[(pixels.size * 0.02).toInt()].coerceAtLeast(0)
        val maxLum = lums[(pixels.size * 0.98).toInt()].coerceAtMost(255)
        val range = (maxLum - minLum).toFloat().coerceAtLeast(1f)

        val resultPixels = IntArray(pixels.size)

        // 2. Aplicar contraste estendido (Contrast Stretch) e desamarelamento
        for (i in pixels.indices) {
            val color = pixels[i]
            val a = (color shr 24) and 0xFF
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            // Esticar dinamicamente a luminância
            var newR = (((r - minLum) / range) * 255f).toInt().coerceIn(0, 255)
            var newG = (((g - minLum) / range) * 255f).toInt().coerceIn(0, 255)
            var newB = (((b - minLum) / range) * 255f).toInt().coerceIn(0, 255)

            // Balanceamento sutil de tons amarelados envelhecidos
            newB = (newB * 1.08f).toInt().coerceIn(0, 255)

            resultPixels[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
        }

        // 3. Denoise Mediano suave (3x3)
        val smoothedPixels = IntArray(pixels.size)
        System.arraycopy(resultPixels, 0, smoothedPixels, 0, resultPixels.size)

        val rList = IntArray(9)
        val gList = IntArray(9)
        val bList = IntArray(9)

        for (y in 1 until height - 1) {
            val offset = y * width
            for (x in 1 until width - 1) {
                var k = 0
                for (dy in -1..1) {
                    val nOffset = (y + dy) * width
                    for (dx in -1..1) {
                        val p = resultPixels[nOffset + (x + dx)]
                        rList[k] = (p shr 16) and 0xFF
                        gList[k] = (p shr 8) and 0xFF
                        bList[k] = p and 0xFF
                        k++
                    }
                }
                rList.sort()
                gList.sort()
                bList.sort()

                val a = (resultPixels[offset + x] shr 24) and 0xFF
                smoothedPixels[offset + x] = (a shl 24) or (rList[4] shl 16) or (gList[4] shl 8) or bList[4]
            }
        }

        val resultBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBmp.setPixels(smoothedPixels, 0, width, 0, 0, width, height)
        resultBmp
    }
}
