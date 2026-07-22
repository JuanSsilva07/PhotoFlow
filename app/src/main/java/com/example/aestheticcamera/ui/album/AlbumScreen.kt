package com.example.aestheticcamera.ui.album

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.aestheticcamera.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumScreen(
    onNavigateToEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // Identificar as permissões de armazenamento adequadas para a versão do SDK do Android
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            permissionsToRequest.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = results.values.any { it }
        if (!hasPermission) {
            Toast.makeText(context, "Permiss\u00E3o de leitura negada", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher especial para lidar com RecoverableSecurityException na exclusão
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.loadPhotos(context)
            Toast.makeText(context, "Foto exclu\u00EDda com sucesso", Toast.LENGTH_SHORT).show()
        }
    }

    var photoToDelete by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadPhotos(context)
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Confirmação de Exclusão Dialog
    photoToDelete?.let { uri ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Excluir Foto?", color = Color.White) },
            text = { Text("Deseja realmente apagar esta imagem permanentemente do aplicativo e da galeria?", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePhoto(context, uri) { intentSender ->
                            intentSenderLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        }
                        photoToDelete = null
                    }
                ) {
                    Text("Excluir", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E1E20)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        if (!hasPermission) {
            // Tela explicativa de permissão negada
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_warning),
                    contentDescription = "Aviso de Permissão",
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Acesso à Galeria Necessário",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Precisamos de permissão para ler as imagens capturadas e salvas na pasta do aplicativo para exibi-las em seu álbum.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(permissionsToRequest) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD54F),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Permitir Acesso", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            val uiState by viewModel.uiState.collectAsState()

            when (val state = uiState) {
                is AlbumUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFFFFD54F)
                    )
                }
                is AlbumUiState.Success -> {
                    if (state.photos.isEmpty()) {
                        // Estado Vazio
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_album),
                                    contentDescription = "Álbum Vazio",
                                    tint = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Nenhuma foto encontrada",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "As fotos que você tirar com a nossa câmera ou salvar na galeria interna aparecerão aqui.",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        // Grid das Fotos
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            Text(
                                text = "Meu Álbum",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                            
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.photos) { uri ->
                                    val isVideo = remember(uri) {
                                        val mimeType = context.contentResolver.getType(uri)
                                        mimeType?.startsWith("video/") == true || uri.toString().contains("video")
                                    }

                                    val imageModel = remember(uri, isVideo) {
                                        if (isVideo) {
                                            try {
                                                val retriever = android.media.MediaMetadataRetriever()
                                                retriever.setDataSource(context, uri)
                                                val bmp = retriever.getFrameAtTime(500_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                                retriever.release()
                                                bmp ?: uri
                                            } catch (e: Exception) {
                                                uri
                                            }
                                        } else {
                                            uri
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1E1E1E))
                                            .combinedClickable(
                                                onClick = { onNavigateToEditor(Uri.encode(uri.toString())) },
                                                onLongClick = { photoToDelete = uri }
                                            )
                                    ) {
                                        AsyncImage(
                                            model = imageModel,
                                            contentDescription = "Imagem ou Vídeo do Álbum",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        if (isVideo) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(6.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black.copy(alpha = 0.65f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("▶", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        
                                        // Botão Flutuante de Lixeira (Overlay)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.5f))
                                                .clickable { photoToDelete = uri },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_delete),
                                                contentDescription = "Deletar",
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
