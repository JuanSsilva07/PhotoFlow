package com.example.aestheticcamera.ui.camera.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.aestheticcamera.ui.camera.GuideType

@Composable
fun CompositionOverlays(
    guideType: GuideType,
    modifier: Modifier = Modifier
) {
    if (guideType == GuideType.NONE) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val strokeColor = Color.White.copy(alpha = 0.7f)
        val strokeWidth = 1.5.dp.toPx()

        when (guideType) {
            GuideType.THIRDS -> {
                // Linhas Verticais
                drawLine(
                    color = strokeColor,
                    start = Offset(width / 3f, 0f),
                    end = Offset(width / 3f, height),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(2f * width / 3f, 0f),
                    end = Offset(2f * width / 3f, height),
                    strokeWidth = strokeWidth
                )
                // Linhas Horizontais
                drawLine(
                    color = strokeColor,
                    start = Offset(0f, height / 3f),
                    end = Offset(width, height / 3f),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = strokeColor,
                    start = Offset(0f, 2f * height / 3f),
                    end = Offset(width, 2f * height / 3f),
                    strokeWidth = strokeWidth
                )
            }
            GuideType.SYMMETRY -> {
                // Linha Vertical Central
                drawLine(
                    color = strokeColor,
                    start = Offset(width / 2f, 0f),
                    end = Offset(width / 2f, height),
                    strokeWidth = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                // Linha Horizontal Central
                drawLine(
                    color = strokeColor,
                    start = Offset(0f, height / 2f),
                    end = Offset(width, height / 2f),
                    strokeWidth = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                // Círculo central de alinhamento
                drawCircle(
                    color = strokeColor,
                    radius = 40.dp.toPx(),
                    center = Offset(width / 2f, height / 2f),
                    style = Stroke(width = strokeWidth)
                )
            }
            GuideType.GOLDEN -> {
                // Desenhar a Espiral Áurea usando arcos conectados
                val path = Path().apply {
                    var x = 0f
                    var y = 0f
                    var w = width
                    var h = height

                    // A espiral áurea pode ser aproximada dividindo retângulos pela proporção áurea (~0.618)
                    // Faremos 5 iterações para desenhar a espiral principal
                    // Iteração 1: Superior Direito -> Inferior Direito
                    val phi = 0.6180339887f
                    
                    // Arco 1: Canto superior direito para inferior direito
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = x - w,
                            top = y,
                            right = x + w,
                            bottom = y + 2 * h
                        ),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = true
                    )
                    
                    // Ajustar limites para o próximo quadrado interno
                    x += w * (1f - phi)
                    w *= phi
                    
                    // Arco 2: Canto inferior direito para inferior esquerdo
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = x - w,
                            top = y - h,
                            right = x + w,
                            bottom = y + h
                        ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                }

                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(width = strokeWidth)
                )

                // Linhas auxiliares da Proporção Áurea
                drawLine(
                    color = strokeColor.copy(alpha = 0.3f),
                    start = Offset(width * 0.618f, 0f),
                    end = Offset(width * 0.618f, height),
                    strokeWidth = strokeWidth / 2
                )
                drawLine(
                    color = strokeColor.copy(alpha = 0.3f),
                    start = Offset(0f, height * 0.618f),
                    end = Offset(width, height * 0.618f),
                    strokeWidth = strokeWidth / 2
                )
            }
            GuideType.SILHOUETTE -> {
                // Desenhar uma silhueta amigável para retratos (Cabeça e ombros)
                val path = Path().apply {
                    val cx = width / 2f
                    val cy = height / 2f
                    
                    // Cabeça (Elipse/Círculo no topo)
                    val headRadiusX = 60.dp.toPx()
                    val headRadiusY = 80.dp.toPx()
                    
                    // Ponto de partida no topo da cabeça
                    moveTo(cx, cy - headRadiusY)
                    
                    // Curva para a direita da cabeça
                    cubicTo(
                        cx + headRadiusX, cy - headRadiusY,
                        cx + headRadiusX, cy + headRadiusY * 0.2f,
                        cx + headRadiusX * 0.5f, cy + headRadiusY
                    )
                    
                    // Pescoço direito
                    lineTo(cx + 25.dp.toPx(), cy + 110.dp.toPx())
                    
                    // Ombro direito
                    cubicTo(
                        cx + 40.dp.toPx(), cy + 120.dp.toPx(),
                        cx + 120.dp.toPx(), cy + 150.dp.toPx(),
                        cx + 140.dp.toPx(), cy + 220.dp.toPx()
                    )
                    
                    // Base inferior (invisível / linha de corte)
                    moveTo(cx - 140.dp.toPx(), cy + 220.dp.toPx())
                    
                    // Ombro esquerdo
                    cubicTo(
                        cx - 120.dp.toPx(), cy + 150.dp.toPx(),
                        cx - 40.dp.toPx(), cy + 120.dp.toPx(),
                        cx - 25.dp.toPx(), cy + 110.dp.toPx()
                    )
                    
                    // Pescoço esquerdo
                    lineTo(cx - headRadiusX * 0.5f, cy + headRadiusY)
                    
                    // Curva para a esquerda da cabeça
                    cubicTo(
                        cx - headRadiusX, cy + headRadiusY * 0.2f,
                        cx - headRadiusX, cy - headRadiusY,
                        cx, cy - headRadiusY
                    )
                }

                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                )
            }
            else -> {}
        }
    }
}
