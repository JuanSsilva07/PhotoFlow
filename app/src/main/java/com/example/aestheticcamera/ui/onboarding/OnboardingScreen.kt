package com.example.aestheticcamera.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.R
import com.example.aestheticcamera.theme.*
import com.example.aestheticcamera.ui.components.AnimatedScreen
import com.example.aestheticcamera.ui.components.SpringButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    // Animação de entrada do logo
    var logoVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); logoVisible = true }

    val logoScale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.7f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(500),
        label = "logoAlpha"
    )

    val slides = listOf(
        OnboardingSlide(
            emoji = "📸",
            title = "Por que minhas fotos\nnão ficam assim?",
            description = "Descubra a arte de capturar imagens estéticas com tonalidades quentes e sombras cinematográficas que contam uma história.",
            imageRes = R.drawable.onboarding_1
        ),
        OnboardingSlide(
            emoji = "🎯",
            title = "Componha como\nos profissionais",
            description = "Use guias clássicos — Regra dos Terços, Espiral Áurea e Linhas Simétricas — para enquadrar perfeitamente cada momento.",
            imageRes = R.drawable.onboarding_2
        ),
        OnboardingSlide(
            emoji = "✨",
            title = "Finalize com\nalma analógica",
            description = "Aplique filtros retrô e ajustes de nível profissional (grão, nitidez, vinheta) para dar personalidade única às suas fotos.",
            imageRes = R.drawable.onboarding_3
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Amber400.copy(alpha = 0.04f),
                        Surface0
                    ),
                    radius = 1200f
                )
            )
    ) {
        // ── Top Bar ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo animado
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .scale(logoScale)
                    .graphicsLayer { alpha = logoAlpha }
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(Amber400, Amber600))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("P", color = Surface0, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "PhotoFlow",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            }

            if (pagerState.currentPage < 2) {
                Text(
                    text = "Pular",
                    color = TextTertiary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { scope.launch { pagerState.animateScrollToPage(2) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // ── Pager ─────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 88.dp, bottom = 128.dp)
        ) { page ->
            val slide = slides[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Imagem
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 28.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface2),
                    border = androidx.compose.foundation.BorderStroke(0.8.dp, BorderMid)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(slide.imageRes),
                            contentDescription = slide.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f   to Color.Transparent,
                                        0.6f to Color.Black.copy(alpha = 0.3f),
                                        1f   to Color.Black.copy(alpha = 0.75f)
                                    )
                                )
                        )
                        // Emoji badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp)
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Surface0.copy(alpha = 0.7f))
                                .border(0.7.dp, BorderMid, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(slide.emoji, fontSize = 22.sp)
                        }
                    }
                }

                // Título e descrição
                Text(
                    text = slide.title,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 31.sp,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Text(
                    text = slide.description,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // ── Bottom Controls ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Indicadores de página (cápsulas)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 22.dp else 6.dp,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy),
                        label = "dot_$index"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.3f,
                        label = "dotAlpha_$index"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(Amber400.copy(alpha = alpha))
                    )
                }
            }

            // Botão de ação
            val isLastPage = pagerState.currentPage == 2
            SpringButton(
                onClick = {
                    if (isLastPage) onFinishOnboarding()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(Amber400, Amber600)))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLastPage) "✨ Começar agora" else "Avançar →",
                        color = Surface0,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
        }
    }
}

data class OnboardingSlide(
    val emoji: String,
    val title: String,
    val description: String,
    val imageRes: Int
)
