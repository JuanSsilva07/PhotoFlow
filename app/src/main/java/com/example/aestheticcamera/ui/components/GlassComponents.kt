package com.example.aestheticcamera.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.theme.*

/**
 * 🧪 Liquid Glass Pill Bar
 * Barra flutuante em formato de pílula com acabamento em vidro fosco.
 */
@Composable
fun GlassPillBar(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Surface1.copy(alpha = 0.9f),
    borderColor: Color = BorderMid,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .clip(shape)
            .border(
                BorderStroke(
                    0.6.dp,
                    Brush.linearGradient(
                        colors = listOf(borderColor, borderColor.copy(alpha = 0.03f))
                    )
                ),
                shape
            ),
        color = backgroundColor,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
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
 * 🖼️ Glass Card
 * Card com acabamento frosted glass e acento de borda dourado.
 * Suporta brilho de shimmer sutil na borda ao pressionar.
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
    val glowAnim = remember { Animatable(0f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scaleAnim.animateTo(0.96f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
            glowAnim.animateTo(1f, tween(120))
        } else {
            scaleAnim.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
            glowAnim.animateTo(0f, tween(200))
        }
    }

    val borderBrush = if (amberGlow) {
        Brush.linearGradient(
            colors = listOf(
                BorderAmber.copy(alpha = 0.5f + glowAnim.value * 0.3f),
                Amber400.copy(alpha = 0.1f + glowAnim.value * 0.2f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                BorderStrong.copy(alpha = 0.8f + glowAnim.value * 0.2f),
                BorderSubtle
            )
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else Modifier

    Box(
        modifier = modifier
            .scale(scaleAnim.value)
            .clip(shape)
            .background(Surface2.copy(alpha = 0.85f))
            .border(BorderStroke(0.8.dp, borderBrush), shape)
            .then(clickableModifier)
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

/**
 * ⚡ Spring Button
 * Botão interativo com física de mola tátil no clique.
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
            scaleAnim.animateTo(0.91f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
        } else {
            scaleAnim.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
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

/**
 * ✨ Shimmer Box
 * Efeito visual animado de carregamento fluido (Skeleton screen).
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue  = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Surface2.copy(alpha = alphaAnim),
                        Surface3.copy(alpha = alphaAnim * 1.4f),
                        Surface2.copy(alpha = alphaAnim)
                    )
                )
            )
    )
}

/**
 * 🍱 BentoCard
 * Card de Bento-Grid premium com animação tátil e bordas responsivas.
 */
@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Surface1,
    borderColor: Color = BorderSubtle,
    amberGlow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scaleAnim.animateTo(0.96f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
        } else {
            scaleAnim.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium))
        }
    }

    val borderBrush = if (amberGlow) {
        Brush.linearGradient(
            colors = listOf(BorderAmber, Amber400.copy(alpha = 0.15f))
        )
    } else {
        Brush.linearGradient(
            colors = listOf(borderColor, borderColor.copy(alpha = 0.02f))
        )
    }

    Box(
        modifier = modifier
            .scale(scaleAnim.value)
            .clip(shape)
            .background(backgroundColor)
            .border(BorderStroke(0.8.dp, borderBrush), shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

/**
 * 🏅 PremiumChip
 * Tag/badge premium com gradiente e borda glow para filtros, labels e badges.
 *
 * @param label     Texto do chip
 * @param selected  Estado ativo (usa gradiente âmbar vs. surface cinza)
 * @param onClick   Callback de clique
 */
@Composable
fun PremiumChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scaleAnim = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) scaleAnim.animateTo(0.94f, spring(Spring.DampingRatioLowBouncy))
        else           scaleAnim.animateTo(1f,    spring(Spring.DampingRatioLowBouncy))
    }

    val background = if (selected) {
        Brush.horizontalGradient(listOf(Amber400, Amber600))
    } else {
        Brush.horizontalGradient(listOf(Surface3, Surface3))
    }

    val textColor  = if (selected) Surface0          else TextSecondary
    val borderBrush = if (selected) {
        Brush.linearGradient(listOf(BorderAmber, Amber400.copy(alpha = 0.3f)))
    } else {
        Brush.linearGradient(listOf(BorderMid, BorderSubtle))
    }

    Box(
        modifier = modifier
            .scale(scaleAnim.value)
            .clip(RoundedCornerShape(50.dp))
            .background(background)
            .border(BorderStroke(0.7.dp, borderBrush), RoundedCornerShape(50.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text  = label,
            color = textColor,
            fontSize   = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.2.sp
        )
    }
}
