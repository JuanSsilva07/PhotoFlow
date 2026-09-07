package com.example.aestheticcamera.ui.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.R
import com.example.aestheticcamera.theme.*
import com.example.aestheticcamera.ui.album.AlbumScreen
import com.example.aestheticcamera.ui.components.*

@Composable
fun MainScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToLessons: () -> Unit,
    onNavigateToCollage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) onNavigateToEditor(Uri.encode(uri.toString()))
        }
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Surface0,
        bottomBar = {
            PremiumBottomNav(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedTab = tab
                },
                onCameraClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToCamera()
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 10 })
                        .togetherWith(fadeOut(tween(200)))
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    0 -> HomeTabContent(
                        onNavigateToCamera  = onNavigateToCamera,
                        onNavigateToLessons = onNavigateToLessons,
                        onNavigateToCollage = onNavigateToCollage,
                        onOpenGallery       = { galleryLauncher.launch("image/*") }
                    )
                    2 -> AlbumScreen(
                        onNavigateToEditor = onNavigateToEditor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ─── Bottom Navigation Premium ────────────────────────────────────────────

@Composable
private fun PremiumBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCameraClick: () -> Unit
) {
    NavigationBar(
        containerColor = Surface1.copy(alpha = 0.97f),
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, BorderSubtle, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        // Início
        NavItem(
            selected = selectedTab == 0,
            onClick  = { onTabSelected(0) },
            icon     = R.drawable.ic_home,
            label    = "Início"
        )

        // Câmera (botão central de destaque)
        NavigationBarItem(
            selected = false,
            onClick  = { },
            icon = {
                SpringButton(
                    onClick = onCameraClick,
                    shape   = RoundedCornerShape(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(listOf(Amber400, Amber600))
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_camera),
                            contentDescription = "Câmera",
                            tint   = Surface0,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                }
            },
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )

        // Álbum
        NavItem(
            selected = selectedTab == 2,
            onClick  = { onTabSelected(2) },
            icon     = R.drawable.ic_album,
            label    = "Álbum"
        )
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: Int,
    label: String
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "navIconScale"
    )

    NavigationBarItem(
        selected = selected,
        onClick  = onClick,
        icon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier
                    .size(22.dp)
                    .scale(iconScale)
            )
        },
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor   = Amber400,
            selectedTextColor   = Amber400,
            unselectedIconColor = TextTertiary,
            unselectedTextColor = TextTertiary,
            indicatorColor      = AmberGlow
        )
    )
}

// ─── Home Tab Content ─────────────────────────────────────────────────────

@Composable
fun HomeTabContent(
    onNavigateToCamera: () -> Unit,
    onNavigateToLessons: () -> Unit,
    onNavigateToCollage: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Cabeçalho Premium ──────────────────────────────────────────
        AnimatedScreen(delayMs = 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Olá, Fotógrafo 👋",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Pronto para enquadrar o dia?",
                        color = TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AmberGlow)
                        .border(0.8.dp, BorderAmber, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "PhotoFlow",
                        color = Amber400,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    )
                }
            }
        }

        // ── Hero Card: Dica do Dia ─────────────────────────────────────
        AnimatedScreen(delayMs = 80) {
            HeroDicaCard()
        }

        // ── Ações Rápidas (Bento-Grid) ─────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedScreen(delayMs = 140) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(14.dp)
                            .background(Amber400, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AÇÕES RÁPIDAS",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.3.sp
                    )
                }
            }

            // Tile 1 — Câmera Hero (âmbar vibrante)
            StaggeredItem(index = 0, baseDelayMs = 70) {
                BentoCard(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNavigateToCamera()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Amber400,
                    amberGlow = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tirar Foto com Guia 📸",
                                color = Surface0,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.3).sp
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = "Câmera com filtros, sobreposições e modos Pro em tempo real.",
                                color = Surface0.copy(alpha = 0.72f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Surface0.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_camera),
                                contentDescription = "Câmera",
                                tint = Surface0,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // Tile 2 — Criador de Colagens
            StaggeredItem(index = 1, baseDelayMs = 70) {
                BentoCard(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToCollage()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Surface1,
                    amberGlow = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Criador de Colagens",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(AmberGlow)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "35mm",
                                        color = Amber400,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = "Monte colagens estilizadas e tiras de filme analógico.",
                                color = TextTertiary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Text("🎞️", fontSize = 28.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // Tiles 3 & 4 — Bento Row 2 colunas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StaggeredItem(index = 2, baseDelayMs = 70, modifier = Modifier.weight(1f)) {
                    BentoCard(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToLessons()
                        },
                        modifier = Modifier.height(138.dp),
                        backgroundColor = Surface1
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(BorderSubtle),
                                contentAlignment = Alignment.Center
                            ) { Text("💡", fontSize = 19.sp) }
                            Column {
                                Text("Aprender", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "Guia de composição",
                                    color = TextTertiary,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }

                StaggeredItem(index = 3, baseDelayMs = 70, modifier = Modifier.weight(1f)) {
                    BentoCard(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenGallery()
                        },
                        modifier = Modifier.height(138.dp),
                        backgroundColor = Surface1
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(BorderSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_album),
                                    contentDescription = "Galeria",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text("Editar Galeria", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "Importe e edite fotos",
                                    color = TextTertiary,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Rodapé ─────────────────────────────────────────────────────
        AnimatedScreen(delayMs = 350) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(1.5.dp)
                        .background(BorderSubtle, RoundedCornerShape(1.dp))
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "\"Fotografar é colocar na mesma linha de mira\na cabeça, o olho e o coração.\"\n— Henri Cartier-Bresson",
                    color = TextDisabled,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "PhotoFlow v1.0.0",
                    color = TextDisabled.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─── Hero Dica do Dia ─────────────────────────────────────────────────────

@Composable
private fun HeroDicaCard() {
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        shape  = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        border = BorderStroke(0.8.dp, BorderMid)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.tip_of_the_day),
                contentDescription = "Dica do Dia",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradiente overlay profundo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.10f),
                            0.5f to Color.Black.copy(alpha = 0.50f),
                            1f to Color.Black.copy(alpha = 0.92f)
                        )
                    )
            )

            // Conteúdo Hero
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Badge pulsante
                Box(
                    modifier = Modifier
                        .scale(pulseScale)
                        .background(
                            Brush.horizontalGradient(listOf(Amber400, Amber600)),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "✨ DICA DE COMPOSIÇÃO",
                        color = Surface0,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Texto hero
                Column {
                    Text(
                        text = "A Regra dos Terços",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "Alinhe os elementos importantes nas interseções da grade 3×3 para uma composição harmoniosa e impactante.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────

@Preview
@Composable
fun MainScreenPreview() {
    com.example.aestheticcamera.theme.AestheticCameraTheme {
        MainScreen(
            onNavigateToCamera  = {},
            onNavigateToEditor  = {},
            onNavigateToLessons = {},
            onNavigateToCollage = {}
        )
    }
}
