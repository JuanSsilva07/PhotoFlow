package com.example.aestheticcamera.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 📊 HistogramWidget
 *
 * Mini-gráfico de histograma RGB em tempo real para a imagem sob edição.
 * Calcula a distribuição de luminância e de canais de cor Red, Green, Blue.
 */
@Composable
fun HistogramWidget(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var histogramData by remember { mutableStateOf<HistogramData?>(null) }

    LaunchedEffect(bitmap) {
        if (bitmap == null) {
            histogramData = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            histogramData = calculateHistogram(bitmap)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2.copy(alpha = 0.85f))
            .border(0.8.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(8.dp)
    ) {
        val data = histogramData
        if (data != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val maxVal = data.maxFrequency.coerceAtLeast(1)

                val pathR = Path()
                val pathG = Path()
                val pathB = Path()

                pathR.moveTo(0f, height)
                pathG.moveTo(0f, height)
                pathB.moveTo(0f, height)

                val step = width / 255f

                for (i in 0..255) {
                    val x = i * step

                    val rY = height - (data.redBins[i].toFloat() / maxVal * height)
                    val gY = height - (data.greenBins[i].toFloat() / maxVal * height)
                    val bY = height - (data.blueBins[i].toFloat() / maxVal * height)

                    if (i == 0) {
                        pathR.moveTo(x, rY)
                        pathG.moveTo(x, gY)
                        pathB.moveTo(x, bY)
                    } else {
                        pathR.lineTo(x, rY)
                        pathG.lineTo(x, gY)
                        pathB.lineTo(x, bY)
                    }
                }

                // Desenha canais RGB com transparência e blending
                drawPath(
                    path = pathR,
                    color = Color(0xFFFF5252).copy(alpha = 0.6f),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawPath(
                    path = pathG,
                    color = Color(0xFF66BB6A).copy(alpha = 0.6f),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawPath(
                    path = pathB,
                    color = Color(0xFF42A5F5).copy(alpha = 0.6f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        } else {
            Text(
                text = "Carregando histograma...",
                color = TextDisabled,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Label discreto
        Text(
            text = "RGB HISTOGRAM",
            color = TextTertiary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

data class HistogramData(
    val redBins: IntArray,
    val greenBins: IntArray,
    val blueBins: IntArray,
    val maxFrequency: Int
)

private fun calculateHistogram(bitmap: Bitmap): HistogramData {
    val rBins = IntArray(256)
    val gBins = IntArray(256)
    val bBins = IntArray(256)

    // Reduz amostragem se a foto for muito grande para evitar sobrecarga na CPU
    val sampleStep = (bitmap.width * bitmap.height / 40000).coerceAtLeast(1)
    val width = bitmap.width
    val height = bitmap.height

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var maxFreq = 0
    var i = 0
    while (i < pixels.size) {
        val color = pixels[i]
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        rBins[r]++
        gBins[g]++
        bBins[b]++

        if (rBins[r] > maxFreq) maxFreq = rBins[r]
        if (gBins[g] > maxFreq) maxFreq = gBins[g]
        if (bBins[b] > maxFreq) maxFreq = bBins[b]

        i += sampleStep
    }

    return HistogramData(rBins, gBins, bBins, maxFreq)
}
