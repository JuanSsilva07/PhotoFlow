package com.example.aestheticcamera.ui.editor.ai

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🧹 ObjectRemovalEngine — Remoção de Objetos por Inpainting Adaptativo de Contexto
 *
 * Preenche áreas marcadas por máscara utilizando interpolação de textura periférica.
 */
object ObjectRemovalEngine {

    /**
     * Aplica inpainting na imagem [srcBitmap] preenchendo os pixels marcados na [maskBitmap].
     * Os pixels brancos da máscara indicam a área a remover.
     */
    suspend fun removeObject(srcBitmap: Bitmap, maskBitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val width = srcBitmap.width
        val height = srcBitmap.height

        val srcPixels = IntArray(width * height)
        srcBitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val maskW = maskBitmap.width
        val maskH = maskBitmap.height
        val scaledMask = if (maskW != width || maskH != height) {
            Bitmap.createScaledBitmap(maskBitmap, width, height, false)
        } else {
            maskBitmap
        }

        val maskPixels = IntArray(width * height)
        scaledMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        if (scaledMask != maskBitmap) {
            scaledMask.recycle()
        }

        val isMasked = BooleanArray(srcPixels.size)
        var maskedCount = 0
        for (i in maskPixels.indices) {
            val color = maskPixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            val isWhite = (r > 128 || g > 128 || b > 128)
            isMasked[i] = isWhite
            if (isWhite) maskedCount++
        }

        if (maskedCount == 0) return@withContext srcBitmap

        val resultPixels = IntArray(srcPixels.size)
        System.arraycopy(srcPixels, 0, resultPixels, 0, srcPixels.size)

        // Diffusion-based Inpainting (Iterações de vizinhança Laplaciana)
        val numPasses = 16
        val neighborDist = 2

        for (pass in 0 until numPasses) {
            for (y in neighborDist until height - neighborDist) {
                val offset = y * width
                for (x in neighborDist until width - neighborDist) {
                    val idx = offset + x
                    if (!isMasked[idx]) continue

                    var sumR = 0
                    var sumG = 0
                    var sumB = 0
                    var validCount = 0

                    for (dy in -neighborDist..neighborDist) {
                        val nOffset = (y + dy) * width
                        for (dx in -neighborDist..neighborDist) {
                            if (dx == 0 && dy == 0) continue
                            val nIdx = nOffset + (x + dx)

                            val nColor = resultPixels[nIdx]
                            sumR += (nColor shr 16) and 0xFF
                            sumG += (nColor shr 8) and 0xFF
                            sumB += nColor and 0xFF
                            validCount++
                        }
                    }

                    if (validCount > 0) {
                        val avgR = sumR / validCount
                        val avgG = sumG / validCount
                        val avgB = sumB / validCount
                        val origA = (resultPixels[idx] shr 24) and 0xFF

                        resultPixels[idx] = (origA shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                    }
                }
            }
        }

        val resultBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBmp.setPixels(resultPixels, 0, width, 0, 0, width, height)
        resultBmp
    }
}
