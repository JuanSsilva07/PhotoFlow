package com.example.aestheticcamera.ui.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Data class com o resultado do processamento de frame da câmera para recursos Pro.
 */
data class FrameAnalysisResult(
    val redHistogram: IntArray = IntArray(256),
    val greenHistogram: IntArray = IntArray(256),
    val blueHistogram: IntArray = IntArray(256),
    val focusPeakingEdges: FloatArray = FloatArray(0), // x, y (percentuais 0f..1f)
    val zebraOverexposedAreas: FloatArray = FloatArray(0) // x, y, width, height (percentuais)
)

/**
 * ⚡ CameraFrameAnalyzer — Processador de Frames em Tempo Real via ImageAnalysis (CameraX)
 *
 * Executa em thread de background (Frame-Rate cap ~20fps) para extrair:
 * 1. Histograma RGB
 * 2. Focus Peaking (Sobel edge detection em Y plane)
 * 3. Zebra Highlights (Luminância > 240)
 */
class CameraFrameAnalyzer(
    private val onFrameAnalyzed: (FrameAnalysisResult) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private val frameIntervalMs = 50L // Cap a ~20 FPS para performance impecável

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < frameIntervalMs) {
            image.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp

        val planes = image.planes
        if (planes.isEmpty()) {
            image.close()
            return
        }

        val yBuffer = planes[0].buffer
        val yRowStride = planes[0].rowStride
        val yPixelStride = planes[0].pixelStride
        val width = image.width
        val height = image.height

        // Arrays para resultados
        val rHist = IntArray(256)
        val gHist = IntArray(256)
        val bHist = IntArray(256)

        // Se houver planos U e V, calcular RGB parcial para o histograma
        val hasChroma = planes.size >= 3
        val uBuffer: ByteBuffer? = if (hasChroma) planes[1].buffer else null
        val vBuffer: ByteBuffer? = if (hasChroma) planes[2].buffer else null
        val uRowStride = if (hasChroma) planes[1].rowStride else 0
        val uPixelStride = if (hasChroma) planes[1].pixelStride else 0

        // Sub-amostragem para performance ultra-rápida (amostrar a cada N pixels)
        val sampleStep = 4
        val edgesList = ArrayList<Float>(500)
        val zebraList = ArrayList<Float>(500)

        val yBytes = ByteArray(yBuffer.remaining())
        yBuffer.get(yBytes)
        yBuffer.rewind()

        // 1. Histograma & Zebra & Focus Peaking em passagem única
        for (y in 1 until height - 1 step sampleStep) {
            val rowStart = y * yRowStride
            val prevRowStart = (y - 1) * yRowStride
            val nextRowStart = (y + 1) * yRowStride

            for (x in 1 until width - 1 step sampleStep) {
                val idx = rowStart + x * yPixelStride
                val luma = yBytes[idx].toInt() and 0xFF

                // Zebra Highlight Check (> 240 luminância)
                if (luma > 240) {
                    if (zebraList.size < 600) {
                        zebraList.add(x.toFloat() / width)
                        zebraList.add(y.toFloat() / height)
                    }
                }

                // Focus Peaking (Filtro Sobel simplificado na luminância Y)
                val lumaLeft = yBytes[rowStart + (x - 1) * yPixelStride].toInt() and 0xFF
                val lumaRight = yBytes[rowStart + (x + 1) * yPixelStride].toInt() and 0xFF
                val lumaUp = yBytes[prevRowStart + x * yPixelStride].toInt() and 0xFF
                val lumaDown = yBytes[nextRowStart + x * yPixelStride].toInt() and 0xFF

                val edgeGrad = abs(lumaRight - lumaLeft) + abs(lumaDown - lumaUp)
                if (edgeGrad > 65) { // Threshold de nitidez de borda
                    if (edgesList.size < 800) {
                        edgesList.add(x.toFloat() / width)
                        edgesList.add(y.toFloat() / height)
                    }
                }

                // Calculo de RGB para Histograma
                if (hasChroma && uBuffer != null && vBuffer != null) {
                    val uvY = y / 2
                    val uvX = x / 2
                    val uIdx = uvY * uRowStride + uvX * uPixelStride
                    val vIdx = uvY * planes[2].rowStride + uvX * planes[2].pixelStride

                    if (uIdx < uBuffer.capacity() && vIdx < vBuffer.capacity()) {
                        val uVal = (uBuffer.get(uIdx).toInt() and 0xFF) - 128
                        val vVal = (vBuffer.get(vIdx).toInt() and 0xFF) - 128

                        var r = (luma + 1.370705f * vVal).toInt().coerceIn(0, 255)
                        var g = (luma - 0.337633f * uVal - 0.698001f * vVal).toInt().coerceIn(0, 255)
                        var b = (luma + 1.732446f * uVal).toInt().coerceIn(0, 255)

                        rHist[r]++
                        gHist[g]++
                        bHist[b]++
                    } else {
                        rHist[luma]++
                        gHist[luma]++
                        bHist[luma]++
                    }
                } else {
                    rHist[luma]++
                    gHist[luma]++
                    bHist[luma]++
                }
            }
        }

        onFrameAnalyzed(
            FrameAnalysisResult(
                redHistogram = rHist,
                greenHistogram = gHist,
                blueHistogram = bHist,
                focusPeakingEdges = edgesList.toFloatArray(),
                zebraOverexposedAreas = zebraList.toFloatArray()
            )
        )

        image.close()
    }
}
