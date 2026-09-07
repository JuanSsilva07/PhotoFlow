package com.example.aestheticcamera.ui.camera.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * 🧭 HorizonLevelOverlay — Nível de Horizonte Giroscópico para a Câmera
 *
 * Desenha uma linha central e indicadores de referência que acompanham a inclinação (Roll e Pitch)
 * do smartphone. Fica Verde Neon (#00E676) quando está nivelado (±1.5°), e Âmbar (#FFD54F) quando desalinhado.
 */
@Composable
fun HorizonLevelOverlay(
    rollDegrees: Float,
    pitchDegrees: Float,
    modifier: Modifier = Modifier
) {
    // Tolerância de alinhamento: ±1.5 graus
    val isLevel = kotlin.math.abs(rollDegrees) <= 1.5f && kotlin.math.abs(pitchDegrees) <= 2.5f

    val strokeColor by animateColorAsState(
        targetValue = if (isLevel) Color(0xFF00E676) else Color(0xFFFFD54F).copy(alpha = 0.85f),
        animationSpec = tween(durationMillis = 150),
        label = "horizonLevelColor"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val lineLength = 140.dp.toPx()
        val gap = 24.dp.toPx()
        val strokeWidth = 2.dp.toPx()
        val tickLength = 12.dp.toPx()

        // 1. Marcações Fixas de Referência Lateral (Esq/Dir)
        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(centerX - lineLength / 2f - gap, centerY),
            end = Offset(centerX - gap / 2f, centerY),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(centerX + gap / 2f, centerY),
            end = Offset(centerX + lineLength / 2f + gap, centerY),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Marcas verticais de extremidade
        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(centerX - lineLength / 2f - gap, centerY - tickLength / 2f),
            end = Offset(centerX - lineLength / 2f - gap, centerY + tickLength / 2f),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = Color.White.copy(alpha = 0.4f),
            start = Offset(centerX + lineLength / 2f + gap, centerY - tickLength / 2f),
            end = Offset(centerX + lineLength / 2f + gap, centerY + tickLength / 2f),
            strokeWidth = 1.dp.toPx()
        )

        // 2. Linha Dinâmica de Nível (Rotacionada de acordo com o Roll)
        rotate(degrees = -rollDegrees, pivot = Offset(centerX, centerY)) {
            // Segmento esquerdo
            drawLine(
                color = strokeColor,
                start = Offset(centerX - lineLength / 2f, centerY),
                end = Offset(centerX - 10.dp.toPx(), centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Ponto central
            drawCircle(
                color = strokeColor,
                radius = 3.dp.toPx(),
                center = Offset(centerX, centerY)
            )

            // Segmento direito
            drawLine(
                color = strokeColor,
                start = Offset(centerX + 10.dp.toPx(), centerY),
                end = Offset(centerX + lineLength / 2f, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
