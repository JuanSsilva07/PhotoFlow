package com.example.aestheticcamera.ui.editor.ai

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aestheticcamera.theme.*
import com.example.aestheticcamera.ui.editor.EditState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 🪄 AIToolsBottomSheet — Suite Completa de Ferramentas de IA On-Device
 *
 * Implementado via Dialog nativo (usePlatformDefaultWidth = false) com alinhamento inferior
 * para garantir exibição 100% confiável em qualquer versão do Android/Compose.
 */
@Composable
fun AIToolsBottomSheet(
    previewBitmap: Bitmap?,
    currentState: EditState,
    onApplyEnhancedState: (EditState) -> Unit,
    onApplyProcessedBitmap: (Bitmap) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("Processando com IA...") }

    // Análise de cena assíncrona
    var sceneAnalysis by remember { mutableStateOf<AutoEnhanceEngineV2.AnalysisResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(true) }

    LaunchedEffect(previewBitmap) {
        if (previewBitmap != null && !previewBitmap.isRecycled) {
            isAnalyzing = true
            sceneAnalysis = withContext(Dispatchers.Default) {
                try {
                    AutoEnhanceEngineV2.analyzeAndEnhance(previewBitmap, currentState)
                } catch (_: Throwable) {
                    null
                }
            }
            isAnalyzing = false
        } else {
            isAnalyzing = false
        }
    }

    // Launcher para selecionar imagem de referência para o Style Match
    val stylePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && previewBitmap != null) {
            scope.launch {
                try {
                    isProcessing = true
                    processingMessage = "Transferindo estilo de cores..."
                    val refBmp = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        }
                    }
                    if (refBmp != null) {
                        val matchedBmp = withContext(Dispatchers.Default) {
                            StyleMatchEngine.applyStyleMatch(previewBitmap, refBmp)
                        }
                        onApplyProcessedBitmap(matchedBmp)
                        Toast.makeText(context, "🎨 Estilo transferido com sucesso!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Erro ao carregar imagem de referência", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro no Style Match: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false, onClick = {}) // Impede fechar ao clicar no cartão
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                colors = CardDefaults.cardColors(containerColor = Surface1)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Barra de alça (Drag handle visual)
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BorderMid)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🪄", fontSize = 20.sp)
                                Text(
                                    text = "Suite de IA On-Device",
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.3).sp
                                )
                            }
                            Text(
                                text = "Melhorias inteligentes processadas 100% no dispositivo",
                                color = TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(bottom = 14.dp))

                    // Cards das Ferramentas de IA
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // 1. Auto-Enhance V2
                        item {
                            val scene = sceneAnalysis?.sceneType ?: AutoEnhanceEngineV2.SceneType.GENERAL
                            val sceneBadge = if (isAnalyzing) "⏳ Analisando..." else "${scene.emoji} ${scene.displayName}"
                            AIToolCard(
                                title = "Auto-Enhance Inteligente V2",
                                subtitle = "Cena detectada: $sceneBadge",
                                badgeText = "V2 AI",
                                iconEmoji = "✨",
                                isRecommended = true,
                                enabled = !isAnalyzing && sceneAnalysis != null && !isProcessing,
                                onClick = {
                                    val analysis = sceneAnalysis
                                    if (analysis != null) {
                                        onApplyEnhancedState(analysis.enhancedState)
                                        Toast.makeText(context, "✨ Auto-Enhance V2 (${scene.displayName}) Aplicado!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }
                            )
                        }

                        // 2. Style Match
                        item {
                            AIToolCard(
                                title = "Copiar Estilo de Foto (Style Match)",
                                subtitle = "Selecione uma foto da galeria e copie a atmosfera de cores",
                                badgeText = "Reinhard Lab",
                                iconEmoji = "🎨",
                                enabled = !isProcessing,
                                onClick = { stylePickerLauncher.launch("image/*") }
                            )
                        }

                        // 3. Retoque Facial
                        item {
                            AIToolCard(
                                title = "Retoque Facial & Pele Suave",
                                subtitle = "Suaviza imperfeições de pele preservando nitidez dos olhos",
                                badgeText = "Smooth Skin",
                                iconEmoji = "👤",
                                enabled = previewBitmap != null && !isProcessing,
                                onClick = {
                                    if (previewBitmap != null) {
                                        scope.launch {
                                            try {
                                                isProcessing = true
                                                processingMessage = "Suavizando tons de pele..."
                                                val retouched = withContext(Dispatchers.Default) {
                                                    FaceRetouchEngine.applyFaceRetouch(previewBitmap, 0.6f)
                                                }
                                                onApplyProcessedBitmap(retouched)
                                                Toast.makeText(context, "👤 Retoque Facial Aplicado!", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro no Retoque: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        // 4. Restauração de Foto Antiga
                        item {
                            AIToolCard(
                                title = "Restaurar Foto Antiga / Digitalizada",
                                subtitle = "Remove desbotamento e aplica denoise adaptativo",
                                badgeText = "Restore",
                                iconEmoji = "📜",
                                enabled = previewBitmap != null && !isProcessing,
                                onClick = {
                                    if (previewBitmap != null) {
                                        scope.launch {
                                            try {
                                                isProcessing = true
                                                processingMessage = "Restaurando contraste e cores..."
                                                val restored = withContext(Dispatchers.Default) {
                                                    VintageRestorationEngine.restorePhoto(previewBitmap)
                                                }
                                                onApplyProcessedBitmap(restored)
                                                Toast.makeText(context, "📜 Foto Restaurada com Sucesso!", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Erro na Restauração: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Overlay de Carregamento durante processamento
                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Surface0.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Amber400, strokeWidth = 3.dp)
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = processingMessage,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AIToolCard(
    title: String,
    subtitle: String,
    badgeText: String,
    iconEmoji: String,
    isRecommended: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val borderBrush = if (isRecommended) {
        Brush.linearGradient(listOf(Amber400, Amber600))
    } else {
        Brush.linearGradient(listOf(BorderMid, BorderSubtle))
    }

    val cardAlpha = if (enabled) 1f else 0.45f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2.copy(alpha = cardAlpha))
            .border(
                androidx.compose.foundation.BorderStroke(
                    if (isRecommended) 1.5.dp else 0.7.dp,
                    borderBrush
                ),
                RoundedCornerShape(16.dp)
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isRecommended) AmberGlow else Surface3)
                    .border(0.7.dp, BorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(iconEmoji, fontSize = 22.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        title,
                        color = TextPrimary.copy(alpha = cardAlpha),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isRecommended) AmberGlow else BorderMid)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            badgeText,
                            color = if (isRecommended) Amber400 else TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    color = TextSecondary.copy(alpha = cardAlpha),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
