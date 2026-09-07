package com.example.aestheticcamera.ui.album

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * 🎬 SlideshowDialog — Apresentação de Slides Automática em Tela Cheia
 *
 * Reproduz fotos e vídeos da galeria:
 * - Fotos: Transição suave a cada 3.5s.
 * - Vídeos: Reprodução automática do áudio e vídeo com avanço inteligente ao finalizar o vídeo.
 */
@Composable
fun SlideshowDialog(
    photos: List<Uri>,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val currentUri = photos.getOrElse(currentIndex) { photos[0] }
    val isVideo = remember(currentUri) {
        val mimeType = context.contentResolver.getType(currentUri)
        mimeType?.startsWith("video/") == true || currentUri.toString().contains("video")
    }

    // Auto-advance timer para Fotos (3.5 segundos)
    LaunchedEffect(currentIndex, isVideo) {
        if (!isVideo) {
            delay(3500)
            currentIndex = (currentIndex + 1) % photos.size
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Transição de Fotos & Player de Vídeo Nativo
            AnimatedContent(
                targetState = currentUri,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                },
                label = "slideshowMediaTransition"
            ) { uri ->
                val itemIsVideo = remember(uri) {
                    val mimeType = context.contentResolver.getType(uri)
                    mimeType?.startsWith("video/") == true || uri.toString().contains("video")
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Toque na tela avança para o próximo slide
                            currentIndex = (currentIndex + 1) % photos.size
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (itemIsVideo) {
                        // Reprodução Nativa de Vídeo no Slideshow
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(uri)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = false
                                        start()
                                    }
                                    setOnCompletionListener {
                                        currentIndex = (currentIndex + 1) % photos.size
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        currentIndex = (currentIndex + 1) % photos.size
                                        true
                                    }
                                }
                            },
                            update = { vView ->
                                vView.setVideoURI(uri)
                                vView.start()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Exibição de Imagem
                        AsyncImage(
                            model = uri,
                            contentDescription = "Slideshow Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }

            // Barra Superior Limpa: Indicadores de Progresso & Botão Fechar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                // Indicador de Progresso Segmentado
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    photos.indices.forEach { index ->
                        val active = index == currentIndex
                        val passed = index < currentIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        active -> Color(0xFFFFD54F)
                                        passed -> Color.White
                                        else -> Color.White.copy(alpha = 0.3f)
                                    }
                                )
                        )
                    }
                }

                // Linha com Contagem e Botão Fechar (✕)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${currentIndex + 1} de ${photos.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isVideo) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFFD54F).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("▶ VÍDEO", color = Color(0xFFFFD54F), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
