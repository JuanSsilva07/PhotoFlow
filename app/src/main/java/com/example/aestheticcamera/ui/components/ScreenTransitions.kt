package com.example.aestheticcamera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * ✨ AnimatedScreen
 *
 * Wrapper que aplica entrada suave (fade + slide up) a qualquer tela.
 * Use no conteúdo principal de cada Screen para evitar transições abruptas.
 *
 * @param modifier Modifier padrão
 * @param delayMs  Delay opcional em ms antes de iniciar a animação (para stagger entre telas)
 * @param content  O conteúdo da tela
 */
@Composable
fun AnimatedScreen(
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (delayMs > 0) {
            kotlinx.coroutines.delay(delayMs.toLong())
        }
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 380)
        ) + slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialOffsetY = { it / 8 } // slide up de 1/8 da altura
        ),
        exit = fadeOut(tween(200)) + slideOutVertically(tween(200))
    ) {
        content()
    }
}

/**
 * 🎴 StaggeredItem
 *
 * Aplica animação de entrada escalonada (stagger) a um item de lista.
 * Cada item recebe um [index] que multiplica o delay base.
 *
 * @param index    Posição do item na lista (começa em 0)
 * @param baseDelayMs Delay base entre cada item (default: 60ms)
 * @param maxIndex Limita o stagger até este índice para evitar delays longos
 */
@Composable
fun StaggeredItem(
    index: Int,
    baseDelayMs: Int = 60,
    maxIndex: Int = 10,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val clampedIndex = minOf(index, maxIndex)
    AnimatedScreen(
        modifier = modifier,
        delayMs = clampedIndex * baseDelayMs,
        content = content
    )
}
