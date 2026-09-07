package com.example.aestheticcamera.ui.album

import android.net.Uri
import android.widget.VideoView
import com.example.aestheticcamera.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aestheticcamera.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 🎬 VideoScrubberDialog — Visualizador de Vídeo com Scrubbing de Frames
 *
 * Permite que o usuário deslize pela linha do tempo (timeline slider)
 * para inspecionar frames do vídeo, reproduzir com áudio ou abrir no Editor.
 */
@Composable
fun VideoScrubberDialog(
    videoUri: Uri,
    onDismiss: () -> Unit,
    onOpenInEditor: (Uri) -> Unit
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableFloatStateOf(1f) }
    var isUserScrubbing by remember { mutableStateOf(false) }

    // Atualização em tempo real do timer
    LaunchedEffect(isPlaying, isUserScrubbing) {
        while (isPlaying && !isUserScrubbing) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition.toFloat()
                    if (vv.duration > 0) {
                        durationMs = vv.duration.toFloat()
                    }
                }
            }
            delay(100)
        }
    }

    // Limpeza de recursos ao fechar o diálogo
    DisposableEffect(Unit) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            color = Surface1
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header do Dialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_slideshow),
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Pré-visualização do Vídeo", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Fechar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Player de Vídeo Nativo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(videoUri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    durationMs = duration.toFloat().coerceAtLeast(1f)
                                    start()
                                    isPlaying = true
                                }
                                videoViewRef = this
                            }
                        },
                        update = { vv ->
                            videoViewRef = vv
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Indicador de Tempo Formatado (00:03 / 00:15)
                val currentSec = (currentPositionMs / 1000f).toInt()
                val totalSec = (durationMs / 1000f).toInt()
                val formattedCurrent = String.format(Locale.getDefault(), "%02d:%02d", currentSec / 60, currentSec % 60)
                val formattedTotal = String.format(Locale.getDefault(), "%02d:%02d", totalSec / 60, totalSec % 60)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏱ $formattedCurrent", color = Amber400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(formattedTotal, color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                // Timeline Scrubbing Slider
                Slider(
                    value = currentPositionMs.coerceIn(0f, durationMs),
                    onValueChange = { newValue ->
                        isUserScrubbing = true
                        currentPositionMs = newValue
                        videoViewRef?.seekTo(newValue.toInt())
                    },
                    onValueChangeFinished = {
                        isUserScrubbing = false
                        if (isPlaying) {
                            videoViewRef?.start()
                        }
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Amber400,
                        activeTrackColor = Amber400,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Botões de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play / Pause
                    Button(
                        onClick = {
                            videoViewRef?.let { vv ->
                                if (vv.isPlaying) {
                                    vv.pause()
                                    isPlaying = false
                                } else {
                                    vv.start()
                                    isPlaying = true
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isPlaying) "⏸ Pausar" else "▶ Reproduzir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Abrir no Editor
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenInEditor(videoUri)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Surface0),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("✨ Editar Vídeo", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
