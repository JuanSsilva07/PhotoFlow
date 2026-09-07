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
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import com.example.aestheticcamera.theme.*

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.aestheticcamera.ui.components.ShimmerBox
import com.example.aestheticcamera.ui.components.PremiumChip
import com.example.aestheticcamera.ui.components.AnimatedScreen
import com.example.aestheticcamera.ui.components.StaggeredItem
import com.example.aestheticcamera.ui.share.ShareHelper

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumScreen(
    onNavigateToEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingDeletes by viewModel.pendingDeleteUris.collectAsState()

    // Estado do Modo de Seleção Múltipla
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPhotos = remember { mutableStateListOf<Uri>() }
    var showSlideshow by remember { mutableStateOf(false) }

    // Estados de Ordenação, Video Scrubbing e Visualizador de Foto (Fase 1)
    var sortOption by remember { mutableStateOf(AlbumSortOption.DATE_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var videoScrubbingUri by remember { mutableStateOf<Uri?>(null) }
    var photoViewerUri by remember { mutableStateOf<Uri?>(null) }

    val favorites by viewModel.favorites.collectAsState()
    
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
            Toast.makeText(context, "Permissão de leitura negada", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher especial para lidar com RecoverableSecurityException na exclusão
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.loadPhotos(context)
            Toast.makeText(context, "Fotos excluídas com sucesso", Toast.LENGTH_SHORT).show()
        }
    }

    var photoToDelete by remember { mutableStateOf<Uri?>(null) }
    var confirmDeleteSelectedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.loadPhotos(context)
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Confirmação de Exclusão Dialog (Foto Única) com Suporte a Desfazer
    photoToDelete?.let { uri ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Excluir Foto?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Deseja realmente apagar esta imagem permanentemente do aplicativo e da galeria?", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uriToDelete = uri
                        photoToDelete = null
                        scope.launch {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val urisToStage = listOf(uriToDelete)
                            viewModel.stageDelete(urisToStage)
                            val result = snackbarHostState.showSnackbar(
                                message = "Foto excluída",
                                actionLabel = "Desfazer",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.cancelPendingDelete(urisToStage)
                                Toast.makeText(context, "Exclusão desfeita!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.commitPendingDelete(context, urisToStage) { intentSender ->
                                    intentSenderLauncher.launch(
                                        IntentSenderRequest.Builder(intentSender).build()
                                    )
                                }
                            }
                        }
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

    // Confirmação de Exclusão Dialog (Seleção Múltipla em Lote) com Suporte a Desfazer
    if (confirmDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelectedDialog = false },
            title = { Text("Excluir ${selectedPhotos.size} Mídias?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("As mídias selecionadas serão permanentemente removidas do dispositivo.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val urisToStage = selectedPhotos.toList()
                        selectedPhotos.clear()
                        isSelectionMode = false
                        confirmDeleteSelectedDialog = false
                        scope.launch {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.stageDelete(urisToStage)
                            val result = snackbarHostState.showSnackbar(
                                message = "${urisToStage.size} mídias excluídas",
                                actionLabel = "Desfazer",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.cancelPendingDelete(urisToStage)
                                Toast.makeText(context, "Exclusão desfeita!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.commitPendingDelete(context, urisToStage) { intentSender ->
                                    intentSenderLauncher.launch(
                                        IntentSenderRequest.Builder(intentSender).build()
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text("Excluir ${selectedPhotos.size}", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelectedDialog = false }) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E1E20)
        )
    }

    // Modal de Scrubbing de Vídeo (Sprint 13.7)
    videoScrubbingUri?.let { vUri ->
        VideoScrubberDialog(
            videoUri = vUri,
            onDismiss = { videoScrubbingUri = null },
            onOpenInEditor = { uriToEdit ->
                onNavigateToEditor(Uri.encode(uriToEdit.toString()))
            }
        )
    }

    // Modal Visualizador de Foto Fullscreen com Ações (Fase 1.4)
    photoViewerUri?.let { pUri ->
        PhotoViewerDialog(
            photoUri = pUri,
            isFavorite = favorites.contains(pUri.toString()),
            onDismiss = { photoViewerUri = null },
            onNavigateToEditor = { uriToEdit ->
                onNavigateToEditor(Uri.encode(uriToEdit.toString()))
            },
            onToggleFavorite = { uriToFav ->
                viewModel.toggleFavorite(context, uriToFav)
            },
            onDeletePhoto = { uriToDelete ->
                photoToDelete = uriToDelete
            }
        )
    }

    // Estado de filtro: 0=Todos, 1=Fotos, 2=Vídeos, 3=Favoritos
    var filterIndex by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Surface0)
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
                    // Estado de Carregamento com Shimmer Skeleton Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShimmerBox(modifier = Modifier.width(140.dp).height(28.dp), shape = RoundedCornerShape(8.dp))
                            ShimmerBox(modifier = Modifier.width(80.dp).height(24.dp), shape = RoundedCornerShape(8.dp))
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(12) {
                                ShimmerBox(modifier = Modifier.aspectRatio(1f), shape = RoundedCornerShape(14.dp))
                            }
                        }
                    }
                }
                is AlbumUiState.Success -> {
                    if (state.mediaItems.isEmpty()) {
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
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_album),
                                    contentDescription = "Álbum Vazio",
                                    tint = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(36.dp)
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
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        // 🔍 Filtragem por Categoria + Ordenação (13.6)
                        val filteredMediaItems = remember(state.mediaItems, filterIndex, favorites, sortOption, pendingDeletes) {
                            var items = state.mediaItems.filter { item ->
                                val uri = item.uri
                                if (pendingDeletes.contains(uri)) return@filter false
                                when (filterIndex) {
                                    1 -> !item.isVideo
                                    2 -> item.isVideo
                                    3 -> favorites.contains(uri.toString())
                                    else -> true
                                }
                            }

                            items = when (sortOption) {
                                AlbumSortOption.DATE_DESC -> items.sortedByDescending { it.dateAdded }
                                AlbumSortOption.DATE_ASC -> items.sortedBy { it.dateAdded }
                                AlbumSortOption.NAME_ASC -> items.sortedBy { it.displayName.lowercase() }
                                AlbumSortOption.NAME_DESC -> items.sortedByDescending { it.displayName.lowercase() }
                                AlbumSortOption.SIZE_DESC -> items.sortedByDescending { it.size }
                            }
                            items
                        }

                        val filteredPhotosUris = remember(filteredMediaItems) {
                            filteredMediaItems.map { it.uri }
                        }

                        if (showSlideshow) {
                            SlideshowDialog(
                                photos = filteredPhotosUris,
                                onDismiss = { showSlideshow = false }
                            )
                        }

                        // Grid das Fotos + Cabeçalho Premium
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header
                            AnimatedScreen {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Meu Álbum",
                                                color = TextPrimary,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = (-0.4).sp
                                            )
                                            Text(
                                                text = "${filteredMediaItems.size} de ${state.mediaItems.size} mídias",
                                                color = TextTertiary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Botão Dropdown Ordenação (13.6)
                                            Box {
                                                IconButton(
                                                    onClick = { showSortMenu = true },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_sort),
                                                        contentDescription = "Ordenar Álbum",
                                                        tint = Amber400,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = showSortMenu,
                                                    onDismissRequest = { showSortMenu = false },
                                                    modifier = Modifier.background(Surface1)
                                                ) {
                                                    AlbumSortOption.values().forEach { option ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    option.label,
                                                                    color = if (sortOption == option) Amber400 else Color.White,
                                                                    fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                                                )
                                                            },
                                                            onClick = {
                                                                sortOption = option
                                                                showSortMenu = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            // Botão Slideshow
                                            TextButton(
                                                onClick = { showSlideshow = true },
                                                enabled = filteredPhotosUris.isNotEmpty()
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_slideshow),
                                                        contentDescription = "Slideshow",
                                                        tint = if (filteredPhotosUris.isNotEmpty()) Color(0xFFFFD54F) else Color.Gray,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        "Slideshow",
                                                        color = if (filteredPhotosUris.isNotEmpty()) Color(0xFFFFD54F) else Color.Gray,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }

                                            // Botão Seleção Múltipla
                                            TextButton(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    isSelectionMode = !isSelectionMode
                                                    if (!isSelectionMode) selectedPhotos.clear()
                                                },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = if (isSelectionMode) Amber400 else TextSecondary
                                                )
                                            ) {
                                                Text(
                                                    text = if (isSelectionMode) "Cancelar" else "Selecionar",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    // Chips de filtro (Todos, Fotos, Vídeos, Favoritos)
                                    Spacer(Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PremiumChip("Todos",       selected = filterIndex == 0, onClick = { filterIndex = 0 })
                                        PremiumChip("Fotos",       selected = filterIndex == 1, onClick = { filterIndex = 1 })
                                        PremiumChip("Vídeos",      selected = filterIndex == 2, onClick = { filterIndex = 2 })
                                        PremiumChip("Favoritos",   selected = filterIndex == 3, onClick = { filterIndex = 3 })
                                    }
                                }
                            }
                            
                            Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = if (isSelectionMode && selectedPhotos.isNotEmpty()) 80.dp else 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(filteredMediaItems) { itemIndex, mediaItem ->
                                        val uri = mediaItem.uri
                                        val isSelected = selectedPhotos.contains(uri)
                                        val isVideo = mediaItem.isVideo

                                        val imageModel by produceState<Any>(initialValue = uri, key1 = uri, key2 = isVideo) {
                                            if (isVideo) {
                                                value = withContext(Dispatchers.IO) {
                                                    try {
                                                        val retriever = android.media.MediaMetadataRetriever()
                                                        retriever.setDataSource(context, uri)
                                                        val bmp = retriever.getFrameAtTime(500_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                                        retriever.release()
                                                        bmp ?: uri
                                                    } catch (e: Exception) {
                                                        uri
                                                    }
                                                }
                                            } else {
                                                value = uri
                                            }
                                        }

                                        StaggeredItem(index = itemIndex, baseDelayMs = 30, maxIndex = 12) {
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Surface1)
                                                .border(
                                                    if (isSelected) 2.dp else 0.5.dp,
                                                    if (isSelected) Amber400 else BorderSubtle,
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .combinedClickable(
                                                    onClick = {
                                                        if (isSelectionMode) {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            if (isSelected) selectedPhotos.remove(uri) else selectedPhotos.add(uri)
                                                        } else {
                                                             if (isVideo) {
                                                                 // Abrir Video Scrubbing & Preview Dialog (13.7)
                                                                 videoScrubbingUri = uri
                                                             } else {
                                                                 // Abrir Visualizador Fullscreen com Barra de Ações (Fase 1.4)
                                                                 photoViewerUri = uri
                                                             }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (!isSelectionMode) {
                                                            isSelectionMode = true
                                                            selectedPhotos.add(uri)
                                                        } else {
                                                            photoToDelete = uri
                                                        }
                                                    }
                                                )
                                        ) {
                                            AsyncImage(
                                                model = imageModel,
                                                contentDescription = "Mídia do Álbum",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )

                                            if (isVideo) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(6.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color.Black.copy(alpha = 0.7f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_slideshow),
                                                        contentDescription = "Vídeo",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            }
                                            
                                            // Star Badge para Favoritos
                                            val isFav = favorites.contains(uri.toString())
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.55f))
                                                    .clickable {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        viewModel.toggleFavorite(context, uri)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                     painter = painterResource(id = if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                                                     contentDescription = "Favorito",
                                                     tint = if (isFav) Amber400 else Color.White.copy(alpha = 0.6f),
                                                     modifier = Modifier.size(12.dp)
                                                 )
                                            }

                                            // Overlay de Seleção Múltipla
                                            if (isSelectionMode) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(6.dp)
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Amber400 else Color.Black.copy(alpha = 0.5f))
                                                        .border(1.dp, Color.White, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Text("✓", color = Surface0, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                                    }
                                                }
                                            }
                                        }
                                        } // StaggeredItem
                                    }
                                }

                                // Barra Flutuante de Ações em Lote (Seleção Múltipla)
                                if (isSelectionMode && selectedPhotos.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 16.dp)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(20.dp))
                                            .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                                        color = Surface1.copy(alpha = 0.97f),
                                        tonalElevation = 8.dp
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${selectedPhotos.size} selecionadas",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                // Botão Favoritar Lote
                                                Button(
                                                    onClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        viewModel.toggleFavoritesBatch(context, selectedPhotos)
                                                        Toast.makeText(context, "Favoritos atualizados!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F).copy(alpha = 0.2f), contentColor = Color(0xFFFFD54F)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_star_filled),
                                                            contentDescription = "Favoritar",
                                                            tint = Color(0xFFFFD54F),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text("Favoritar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }

                                                // Botão Compartilhar Lote
                                                Button(
                                                    onClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedPhotos.firstOrNull()?.let { firstUri ->
                                                            ShareHelper.shareMedia(context, firstUri)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Enviar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                }

                                                // Botão Excluir Lote
                                                Button(
                                                    onClick = { confirmDeleteSelectedDialog = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252).copy(alpha = 0.2f), contentColor = Color(0xFFFF5252)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text("Excluir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
        // Snackbar Host para Desfazer Exclusão
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFF1E1E22),
                contentColor = Color.White,
                actionColor = Amber400,
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}
}
