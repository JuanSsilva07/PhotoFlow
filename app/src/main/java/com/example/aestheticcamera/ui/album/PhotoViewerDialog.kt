package com.example.aestheticcamera.ui.album

import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.aestheticcamera.R
import com.example.aestheticcamera.theme.*
import com.example.aestheticcamera.ui.share.ShareHelper

/**
 * 🖼️ PhotoViewerDialog — Visualizador de Imagem em Tela Cheia com Barra de Ações Responsiva
 * (Editar, Enviar, Favoritar e Excluir)
 */
@Composable
fun PhotoViewerDialog(
    photoUri: Uri,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onNavigateToEditor: (Uri) -> Unit,
    onToggleFavorite: (Uri) -> Unit,
    onDeletePhoto: (Uri) -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Imagem em Tela Cheia (Fit ao centro)
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Visualização de Foto",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Topbar Translúcida com Botão de Fechar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 44.dp, start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Visualização",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Text("✕", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Barra Flutuante Inferior Responsiva com Flex Weight
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                    color = Surface1.copy(alpha = 0.94f),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Botão Editar (Primary Amber)
                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToEditor(photoUri)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Amber400, contentColor = Surface0),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_sparkles),
                                    contentDescription = "Editar",
                                    tint = Surface0,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Editar",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 2. Botão Enviar (Secondary Glass)
                        Button(
                            onClick = {
                                ShareHelper.shareMedia(context, photoUri)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f), contentColor = Color.White),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Text(
                                "Enviar",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 3. Botão Favoritar (Icon Circle Vector)
                        IconButton(
                            onClick = { onToggleFavorite(photoUri) },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                painter = painterResource(id = if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                                contentDescription = "Favorito",
                                tint = if (isFavorite) Amber400 else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 4. Botão Excluir (Icon Circle Destructive)
                        IconButton(
                            onClick = {
                                onDismiss()
                                onDeletePhoto(photoUri)
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252).copy(alpha = 0.18f))
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = "Deletar",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
