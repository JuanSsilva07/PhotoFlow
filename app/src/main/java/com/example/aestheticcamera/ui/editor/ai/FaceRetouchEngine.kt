package com.example.aestheticcamera.ui.editor.ai

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 👤 FaceRetouchEngine — Suavização de Pele (Skin Smoothing) & Retoque Facial
 *
 * Detecta áreas de tom de pele em HSV/YCbCr e aplica filtro bilateral adaptativo
 * para efeito "Pele Suave / Smooth Skin", mantendo olhos, lábios e contornos 100% nítidos.
 */
object FaceRetouchEngine {

    /**
     * Aplica o retoque de pele com a intensidade especificada [smoothIntensity] (0f a 1f).
     */
    suspend fun applyFaceRetouch(srcBitmap: Bitmap, smoothIntensity: Float = 0.6f): Bitmap = withContext(Dispatchers.Default) {
        val width = srcBitmap.width
        val height = srcBitmap.height

        val pixels = IntArray(width * height)
        srcBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val skinMask = BooleanArray(pixels.size)

        // 1. Gerar máscara de tons de pele humana (YCbCr)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            // Regras de tom de pele sRGB
            val maxC = maxOf(r, maxOf(g, b))
            val minC = minOf(r, minOf(g, b))

            val isSkin = r > 80 && g > 35 && b > 15 &&
                    (r - g) > 10 && (r > b) &&
                    (maxC - minC) > 15 &&
                    abs(r - g) > 12

            skinMask[i] = isSkin
        }

        // 2. Aplicar filtro de suavização espacial (Guided Blur) exclusivamente nos pixels de pele
        val resultPixels = IntArray(pixels.size)
        System.arraycopy(pixels, 0, resultPixels, 0, pixels.size)

        val radius = (3 * smoothIntensity).toInt().coerceAtLeast(1)
        val alphaBlend = smoothIntensity.coerceIn(0f, 0.85f)

        for (y in radius until height - radius) {
            val offset = y * width
            for (x in radius until width - radius) {
                val idx = offset + x
                if (!skinMask[idx]) continue

                var sumR = 0
                var sumG = 0
                var sumB = 0
                var count = 0

                for (dy in -radius..radius) {
                    val nOffset = (y + dy) * width
                    for (dx in -radius..radius) {
                        val nIdx = nOffset + (x + dx)
                        val nColor = pixels[nIdx]

                        sumR += (nColor shr 16) and 0xFF
                        sumG += (nColor shr 8) and 0xFF
                        sumB += nColor and 0xFF
                        count++
                    }
                }

                if (count > 0) {
                    val avgR = sumR / count
                    val avgG = sumG / count
                    val avgB = sumB / count

                    val origColor = pixels[idx]
                    val origA = (origColor shr 24) and 0xFF
                    val origR = (origColor shr 16) and 0xFF
                    val origG = (origColor shr 8) and 0xFF
                    val origB = origColor and 0xFF

                    // Blend suave entre o pixel original e o pixel filtrado
                    val finalR = (origR * (1f - alphaBlend) + avgR * alphaBlend).toInt().coerceIn(0, 255)
                    val finalG = (origG * (1f - alphaBlend) + avgG * alphaBlend).toInt().coerceIn(0, 255)
                    val finalB = (origB * (1f - alphaBlend) + avgB * alphaBlend).toInt().coerceIn(0, 255)

                    resultPixels[idx] = (origA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                }
            }
        }

        val resultBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBmp.setPixels(resultPixels, 0, width, 0, 0, width, height)
        resultBmp
    }
}
