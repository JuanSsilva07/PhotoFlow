package com.example.aestheticcamera.ui.editor

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.theme.*
import com.example.aestheticcamera.ui.components.SpringButton
import com.example.aestheticcamera.ui.editor.PresetManager.applyTo
import com.example.aestheticcamera.ui.editor.PresetManager.toPreset

/**
 * 💾 PresetBottomSheet
 *
 * Bottom sheet completo de presets do editor:
 * - Salvar preset atual com nome personalizado
 * - Listar presets salvos com data relativa
 * - Aplicar, Exportar, Importar e Deletar presets
 *
 * @param currentState   Estado de edição atual para salvar como preset
 * @param onApplyPreset  Callback ao aplicar um preset
 * @param onDismiss      Callback ao fechar o bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetBottomSheet(
    currentState: EditState,
    onApplyPreset: (EditState) -> Unit,
    onDismiss: () -> Unit
) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Lista de presets reativa
    var presets by remember { mutableStateOf(PresetManager.loadAll(context)) }
    var presetName by remember { mutableStateOf("") }
    var showSaveRow by remember { mutableStateOf(false) }

    // Launcher para importar arquivo .youngpreset
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val stream  = context.contentResolver.openInputStream(uri)
                val preset  = stream?.let { PresetManager.importFromStream(it) }
                if (preset != null) {
                    PresetManager.save(context, preset)
                    presets = PresetManager.loadAll(context)
                    Toast.makeText(context, "✅ Preset \"${preset.name}\" importado!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Arquivo inválido ou corrompido", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao importar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Surface1,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderMid)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text       = "Meus Presets",
                        color      = TextPrimary,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text     = "${presets.size} preset${if (presets.size != 1) "s" else ""} salvos",
                        color    = TextTertiary,
                        fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Botão Importar
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        shape  = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, BorderMid),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("⬇ Importar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    // Botão Salvar Novo
                    Button(
                        onClick = { showSaveRow = !showSaveRow },
                        shape  = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showSaveRow) BorderAmber else Amber400,
                            contentColor   = Surface0
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text       = if (showSaveRow) "Cancelar" else "💾 Salvar",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // ── Campo de nome para salvar ─────────────────────────────
            AnimatedVisibility(
                visible = showSaveRow,
                enter   = fadeIn() + slideInVertically { -it / 2 },
                exit    = fadeOut()
            ) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    OutlinedTextField(
                        value         = presetName,
                        onValueChange = { presetName = it.take(32) },
                        label         = { Text("Nome do preset", color = TextTertiary) },
                        placeholder   = { Text("Ex: Estilo Vintage Quente", color = TextDisabled, fontSize = 13.sp) },
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Amber400,
                            unfocusedBorderColor = BorderMid,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary,
                            cursorColor          = Amber400
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier      = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val trimmed = presetName.trim()
                            if (trimmed.isBlank()) {
                                Toast.makeText(context, "Digite um nome para o preset", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val preset = currentState.toPreset(trimmed)
                            PresetManager.save(context, preset)
                            presets = PresetManager.loadAll(context)
                            Toast.makeText(context, "✅ Preset \"$trimmed\" salvo!", Toast.LENGTH_SHORT).show()
                            presetName  = ""
                            showSaveRow = false
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = Amber400,
                            contentColor   = Surface0
                        )
                    ) {
                        Text("Salvar Preset", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
            }

            // ── Divider ───────────────────────────────────────────────
            if (presets.isNotEmpty()) {
                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(bottom = 12.dp))
            }

            // ── Lista de Presets ──────────────────────────────────────
            if (presets.isEmpty()) {
                // Estado vazio
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎨", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text      = "Nenhum preset salvo ainda.\nClique em \"💾 Salvar\" para criar o primeiro!",
                            color     = TextTertiary,
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(presets, key = { it.name + it.createdAt }) { preset ->
                        PresetCard(
                            preset     = preset,
                            isActive   = currentState.presetName == preset.name,
                            onApply    = {
                                val newState = preset.applyTo(currentState)
                                onApplyPreset(newState)
                                Toast.makeText(context, "✨ Preset \"${preset.name}\" aplicado!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onExport   = {
                                val intent = PresetManager.exportIntent(context, preset)
                                context.startActivity(Intent.createChooser(intent, "Exportar Preset"))
                            },
                            onDelete   = {
                                PresetManager.delete(context, preset.name)
                                presets = PresetManager.loadAll(context)
                                Toast.makeText(context, "Preset removido", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── Card de Preset Individual ────────────────────────────────────────────

@Composable
private fun PresetCard(
    preset: PresetManager.Preset,
    isActive: Boolean,
    onApply: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue    = if (isActive) 1.02f else 1f,
        animationSpec  = spring(Spring.DampingRatioMediumBouncy),
        label          = "presetScale"
    )

    val borderBrush = if (isActive) {
        Brush.linearGradient(listOf(Amber400, Amber600))
    } else {
        Brush.linearGradient(listOf(BorderMid, BorderSubtle))
    }

    Box(
        modifier = Modifier
            .scale(scaleAnim)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .border(
                androidx.compose.foundation.BorderStroke(
                    if (isActive) 1.5.dp else 0.7.dp,
                    borderBrush
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onApply)
            .padding(14.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Preview de gradiente (representativo dos ajustes)
            val previewBrush = presetGradient(preset)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewBrush)
                        .border(0.7.dp, BorderSubtle, RoundedCornerShape(12.dp))
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text       = preset.name,
                            color      = TextPrimary,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AmberGlow)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("ativo", color = Amber400, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    Text(
                        text     = PresetManager.formatRelativeTime(preset.createdAt),
                        color    = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            // Ações
            if (!showConfirmDelete) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Exportar
                    IconButton(onClick = onExport, modifier = Modifier.size(34.dp)) {
                        Text("⬆", color = TextSecondary, fontSize = 15.sp)
                    }
                    // Deletar
                    IconButton(
                        onClick  = { showConfirmDelete = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text("🗑", fontSize = 15.sp)
                    }
                }
            } else {
                // Confirmação de exclusão inline
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showConfirmDelete = false },
                        colors  = ButtonDefaults.textButtonColors(contentColor = TextTertiary)
                    ) {
                        Text("Não", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onDelete(); showConfirmDelete = false },
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed.copy(alpha = 0.2f),
                            contentColor   = ErrorRed
                        ),
                        shape  = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Excluir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Gradiente de preview representativo do preset ────────────────────────

private fun presetGradient(preset: PresetManager.Preset): Brush {
    // Mapeia os ajustes para cores de preview
    val warm    = preset.warmth.coerceIn(-1f, 1f)
    val bright  = preset.brightness.coerceIn(-1f, 1f)
    val baseHue = when {
        warm  > 0.2f  -> Color(0xFFFFB347)  // laranja quente
        warm  < -0.2f -> Color(0xFF82B3D7)  // azul frio
        else          -> Color(0xFF9E9E9E)  // neutro
    }
    val topColor = if (bright > 0) baseHue.copy(alpha = 0.8f) else baseHue.copy(alpha = 0.5f)
    val botColor = Color(0xFF1A1A1A)
    return Brush.verticalGradient(listOf(topColor, botColor))
}
