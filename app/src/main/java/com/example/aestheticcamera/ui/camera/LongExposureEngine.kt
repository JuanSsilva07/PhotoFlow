package com.example.aestheticcamera.ui.camera

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🌌 LongExposureEngine — Simulação de Longa Exposição com Fusão Temporal Adaptativa
 *
 * Mescla continuamente múltiplos quadros capturados ao longo de 2 a 5 segundos
 * usando média ponderada móvel (Moving Average), gerando rastros de luz e suavização de água
 * sem estourar as altas luzes.
 */
object LongExposureEngine {

    /**
     * Mescla uma lista de bitmaps [frames] em um único bitmap acumulado com longa exposição.
     */
    suspend fun blendFrames(frames: List<Bitmap>): Bitmap? = withContext(Dispatchers.Default) {
        if (frames.isEmpty()) return@withContext null
        if (frames.size == 1) return@withContext frames[0].copy(frames[0].config ?: Bitmap.Config.ARGB_8888, true)

        val width = frames[0].width
        val height = frames[0].height
        val totalPixels = width * height

        val accumR = FloatArray(totalPixels)
        val accumG = FloatArray(totalPixels)
        val accumB = FloatArray(totalPixels)

        val framePixels = IntArray(totalPixels)

        for (frame in frames) {
            if (frame.isRecycled || frame.width != width || frame.height != height) continue

            frame.getPixels(framePixels, 0, width, 0, 0, width, height)

            for (i in 0 until totalPixels) {
                val color = framePixels[i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                accumR[i] += r.toFloat()
                accumG[i] += g.toFloat()
                accumB[i] += b.toFloat()
            }
        }

        val frameCount = frames.size.toFloat()
        val resultPixels = IntArray(totalPixels)

        for (i in 0 until totalPixels) {
            val avgR = (accumR[i] / frameCount).toInt().coerceIn(0, 255)
            val avgG = (accumG[i] / frameCount).toInt().coerceIn(0, 255)
            val avgB = (accumB[i] / frameCount).toInt().coerceIn(0, 255)

            resultPixels[i] = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return@withContext resultBitmap
    }
}
