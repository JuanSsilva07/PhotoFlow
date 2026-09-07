package com.example.aestheticcamera.ui.editor.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.theme.*

/**
 * Card reutilizável para ferramentas de IA no editor.
 *
 * @param isApplied Se verdadeiro, exibe indicador visual de "ativo" no card.
 */
@Composable
fun AIToolCard(
    title: String,
    subtitle: String,
    isApplied: Boolean = false,
    onClick: () -> Unit
) {
    val borderBrush = if (isApplied) {
        Brush.linearGradient(listOf(Amber400, Amber600))
    } else {
        Brush.linearGradient(listOf(BorderMid, BorderSubtle))
    }
    val borderWidth = if (isApplied) 1.5.dp else 0.7.dp
    val bgColor = if (isApplied) Amber400.copy(alpha = 0.08f) else Surface2

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                androidx.compose.foundation.BorderStroke(borderWidth, borderBrush),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        title,
                        color = if (isApplied) Amber400 else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isApplied) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AmberGlow)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text("ATIVO", color = Amber400, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }

            // Seta indicadora
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isApplied) AmberGlow else Surface3),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isApplied) "✓" else "→",
                    color = Amber400,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
