package com.example.aestheticcamera.ui.camera.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.ui.camera.FrameAnalysisResult

/**
 * 🔍 FocusPeakingOverlay — Desenha pontos em verde neon (#00E676) sobre bordas nítidas em foco.
 */
@Composable
fun FocusPeakingOverlay(
    edges: FloatArray,
    modifier: Modifier = Modifier
) {
    if (edges.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val pointRadius = 1.8.dp.toPx()
        val neonGreen = Color(0xFF00E676)

        var i = 0
        while (i < edges.size - 1) {
            val x = edges[i] * width
            val y = edges[i + 1] * height
            drawCircle(
                color = neonGreen,
                radius = pointRadius,
                center = Offset(x, y)
            )
            i += 2
        }
    }
}

/**
 * 🦓 ZebraHighlightOverlay — Desenha padrão diagonal de linhas amarelas sobre áreas superexpostas (>95% brilho).
 */
@Composable
fun ZebraHighlightOverlay(
    overexposedPoints: FloatArray,
    modifier: Modifier = Modifier
) {
    if (overexposedPoints.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val yellowZebra = Color(0xFFFFD54F).copy(alpha = 0.85f)

        var i = 0
        while (i < overexposedPoints.size - 1) {
            val x = overexposedPoints[i] * width
            val y = overexposedPoints[i + 1] * height

            // Desenhar pequena listra diagonal zebra
            drawLine(
                color = yellowZebra,
                start = Offset(x - 4.dp.toPx(), y - 4.dp.toPx()),
                end = Offset(x + 4.dp.toPx(), y + 4.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
            i += 2
        }
    }
}

/**
 * 📊 LiveHistogramWidget — Histograma RGB ao vivo no HUD da Câmera
 */
@Composable
fun LiveHistogramWidget(
    analysisResult: FrameAnalysisResult,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(130.dp)
            .height(65.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .border(0.8.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val r = analysisResult.redHistogram
            val g = analysisResult.greenHistogram
            val b = analysisResult.blueHistogram

            var maxCount = 1
            for (i in 0 until 256) {
                if (r[i] > maxCount) maxCount = r[i]
                if (g[i] > maxCount) maxCount = g[i]
                if (b[i] > maxCount) maxCount = b[i]
            }

            val pathR = Path()
            val pathG = Path()
            val pathB = Path()

            val stepX = w / 255f

            for (i in 0 until 256) {
                val x = i * stepX
                val yR = h - (r[i].toFloat() / maxCount * h)
                val yG = h - (g[i].toFloat() / maxCount * h)
                val yB = h - (b[i].toFloat() / maxCount * h)

                if (i == 0) {
                    pathR.moveTo(x, yR)
                    pathG.moveTo(x, yG)
                    pathB.moveTo(x, yB)
                } else {
                    pathR.lineTo(x, yR)
                    pathG.lineTo(x, yG)
                    pathB.lineTo(x, yB)
                }
            }

            drawPath(pathR, color = Color(0xFFFF5252).copy(alpha = 0.7f), style = Stroke(width = 1.dp.toPx()))
            drawPath(pathG, color = Color(0xFF69F0AE).copy(alpha = 0.7f), style = Stroke(width = 1.dp.toPx()))
            drawPath(pathB, color = Color(0xFF448AFF).copy(alpha = 0.7f), style = Stroke(width = 1.dp.toPx()))
        }

        Text(
            text = "RGB",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}
