package com.example.aestheticcamera.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 🧪 Liquid Glass Pill Bar: Barra flutuante em formato de pílula com acabamento em vidro fosco.
 */
@Composable
fun GlassPillBar(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color(0xFF161618).copy(alpha = 0.85f),
    borderColor: Color = Color.White.copy(alpha = 0.12f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .border(
                BorderStroke(
                    0.5.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            borderColor,
                            borderColor.copy(alpha = 0.03f)
                        )
                    )
                ),
                shape
            ),
        color = backgroundColor,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * 🖼️ Glass Card: Card com acabamento frosted glass e acento de borda dourado.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    amberGlow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scaleAnim.animateTo(0.96f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            scaleAnim.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    val borderBrush = if (amberGlow) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFD54F).copy(alpha = 0.6f),
                Color(0xFFFFD54F).copy(alpha = 0.15f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.03f)
            )
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .scale(scaleAnim.value)
            .clip(shape)
            .background(Color(0xFF1E1E22).copy(alpha = 0.82f))
            .border(BorderStroke(0.8.dp, borderBrush), shape)
            .then(clickableModifier)
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

/**
 * ⚡ Spring Button: Botão interativo com física de mola tátil no clique.
 */
@Composable
fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed && enabled) {
            scaleAnim.animateTo(0.91f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            scaleAnim.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    Box(
        modifier = modifier
            .scale(scaleAnim.value)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
