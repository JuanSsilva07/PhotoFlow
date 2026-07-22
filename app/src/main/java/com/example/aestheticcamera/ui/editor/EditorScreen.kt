package com.example.aestheticcamera.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.ui.camera.AestheticFilters
import com.example.aestheticcamera.ui.camera.FilterThumbnailItem
import com.example.aestheticcamera.ui.camera.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.style.TextAlign

enum class CropRatio(val displayName: String, val w: Int, val h: Int) {
    FREE("Livre", 0, 0),
    SQUARE("1:1", 1, 1),
    FOUR_THREE("4:3", 4, 3),
    SIXTEEN_NINE("16:9", 16, 9)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUriString: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val decodedUri = remember(imageUriString) { Uri.parse(Uri.decode(imageUriString)) }

    // Aspect ratio da imagem original
    val imageAspectRatio = remember(decodedUri) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(decodedUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth.toFloat() / options.outHeight.toFloat()
            } else {
                1f
            }
        } catch (e: Exception) {
            1f
        }
    }

    // Bloquear a orientação em modo Retrato (vertical) durante a edição
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val originalOrientation = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Gerenciador de Histórico de Edição
    val history = remember { EditHistory(EditState()) }
    var currentEditState by remember { mutableStateOf(history.getCurrentState()) }

    // Bitmaps para o Preview em tempo real otimizado
    var originalPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Estados locais temporários para controlar as abas
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Filtros, 1: Ajustes, 2: Curvas, 3: HSL, 4: Recortar/Girar
    var activeCurveChannel by remember { mutableStateOf(CurveChannel.RGB) }
    var selectedHslChannel by remember { mutableStateOf(HslColorChannel.RED) }

    // Estados do Comparador Antes/Depois
    var splitFraction by remember { mutableFloatStateOf(0f) }
    var isSaving by remember { mutableStateOf(false) }

    // Estados do Modal de Opções de Exportação & Marca d'Água
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedExportFormat by remember { mutableStateOf("JPEG") }
    var selectedExportQuality by remember { mutableFloatStateOf(90f) }
    var watermarkEnabled by remember { mutableStateOf(true) }

    // Verificar se o URI da mídia é um arquivo de vídeo
    val isVideo = remember(decodedUri) {
        val mimeType = context.contentResolver.getType(decodedUri)
        mimeType?.startsWith("video/") == true || decodedUri.toString().contains("video")
    }

    var isVideoPlaying by remember { mutableStateOf(false) }
    var mediaPlayerRef by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    LaunchedEffect(isVideo) {
        if (isVideo) {
            splitFraction = 0f
        }
    }



    // Matriz de cor combinada (Filtro + Brilho + Contraste + Saturação + Temperatura + Realces + Sombras) em tempo real
    val filterColorMatrix = remember(
        currentEditState.activeFilter,
        currentEditState.filterIntensity,
        currentEditState.brightness,
        currentEditState.contrast,
        currentEditState.saturation,
        currentEditState.warmth,
        currentEditState.highlights,
        currentEditState.shadows
    ) {
        val mat5x4 = getCombinedMatrix(
            currentEditState.activeFilter,
            currentEditState.filterIntensity,
            currentEditState.brightness,
            currentEditState.contrast,
            currentEditState.saturation,
            currentEditState.warmth,
            currentEditState.highlights,
            currentEditState.shadows,
            0f
        )
        androidx.compose.ui.graphics.ColorMatrix(mat5x4)
    }

    // 1. Carregar Bitmap reduzido para preview em tempo real (max 720px) ou extrair frame de vídeo
    LaunchedEffect(decodedUri) {
        withContext(Dispatchers.IO) {
            try {
                val mimeType = context.contentResolver.getType(decodedUri)
                val isVideo = mimeType?.startsWith("video/") == true || decodedUri.toString().contains("video")

                if (isVideo) {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, decodedUri)
                        val frame = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (frame != null) {
                            originalPreviewBitmap = frame
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("EditorScreen", "Erro ao extrair frame do vídeo", e)
                    } finally {
                        try { retriever.release() } catch (e: Exception) {}
                    }
                } else {
                    var inputStream = context.contentResolver.openInputStream(decodedUri)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    inputStream?.close()

                    val maxDimension = 720
                    var sampleSize = 1
                    if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                        val halfWidth = options.outWidth / 2
                        val halfHeight = options.outHeight / 2
                        while (halfWidth / sampleSize >= maxDimension && halfHeight / sampleSize >= maxDimension) {
                            sampleSize *= 2
                        }
                    }

                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    inputStream = context.contentResolver.openInputStream(decodedUri)
                    val decoded = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                    inputStream?.close()

                    if (decoded != null) {
                        originalPreviewBitmap = decoded
                    } else {
                        // Fallback: Tentar extrair frame com MediaMetadataRetriever se a decodificação de imagem retornar null
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, decodedUri)
                            val frame = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            val rotationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                            val rotation = rotationStr?.toIntOrNull() ?: 0
                            if (frame != null) {
                                if (rotation != 0) {
                                    val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                                    val rotatedFrame = android.graphics.Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
                                    originalPreviewBitmap = rotatedFrame
                                } else {
                                    originalPreviewBitmap = frame
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            try { retriever.release() } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 2. Processar bitmap reduzido em background de forma reativa a cada mudança de estado
    LaunchedEffect(currentEditState, originalPreviewBitmap) {
        val src = originalPreviewBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            // Calcular matriz de cores combinada
            val matrix = getCombinedMatrix(
                currentEditState.activeFilter,
                currentEditState.filterIntensity,
                currentEditState.brightness,
                currentEditState.contrast,
                currentEditState.saturation,
                currentEditState.warmth,
                currentEditState.highlights,
                currentEditState.shadows,
                currentEditState.sharpness
            )

            // Calcular LUTs das curvas
            val rgbLut = CubicSpline.calculateLut(currentEditState.rgbCurve)
            val redLut = CubicSpline.calculateLut(currentEditState.redCurve)
            val greenLut = CubicSpline.calculateLut(currentEditState.greenCurve)
            val blueLut = CubicSpline.calculateLut(currentEditState.blueCurve)

            // Combinar curva Master (RGB) com as curvas individuais de cada canal
            val finalLutR = IntArray(256) { rgbLut[redLut[it]] }
            val finalLutG = IntArray(256) { rgbLut[greenLut[it]] }
            val finalLutB = IntArray(256) { rgbLut[blueLut[it]] }
            val combinedLuts = CurvesLuts(finalLutR, finalLutG, finalLutB)

            // Aplicar pipeline completa de imagem
            val processed = applyImagePipeline(
                src = src,
                colorMatrix = matrix,
                curvesLuts = combinedLuts,
                hslAdjustments = currentEditState.hslAdjustments,
                vignette = currentEditState.vignette,
                grain = currentEditState.grain,
                sharpness = currentEditState.sharpness,
                lightLeakStyle = currentEditState.lightLeakStyle,
                lightLeakOpacity = currentEditState.lightLeakOpacity,
                dateStampEnabled = currentEditState.dateStampEnabled,
                frameStyle = currentEditState.frameStyle,
                tiltShiftConfig = currentEditState.tiltShiftConfig
            )

            withContext(Dispatchers.Main) {
                processedPreviewBitmap = processed
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack, enabled = !isSaving) {
                    Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Painel de Histórico (Undo / Redo / Reset)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val state = history.undo()
                            if (state != null) {
                                currentEditState = state
                                // Haptic feedback
                                try {
                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } catch (e: Exception) {}
                            }
                        },
                        enabled = history.canUndo() && !isSaving
                    ) {
                        Text("↶", color = if (history.canUndo()) Color.White else Color.White.copy(alpha = 0.3f), fontSize = 22.sp)
                    }

                    IconButton(
                        onClick = {
                            val state = history.redo()
                            if (state != null) {
                                currentEditState = state
                                // Haptic feedback
                                try {
                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(20, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } catch (e: Exception) {}
                            }
                        },
                        enabled = history.canRedo() && !isSaving
                    ) {
                        Text("↷", color = if (history.canRedo()) Color.White else Color.White.copy(alpha = 0.3f), fontSize = 22.sp)
                    }

                    TextButton(
                        onClick = {
                            originalPreviewBitmap?.let { bmp ->
                                val enhanced = com.example.aestheticcamera.ui.editor.ai.AutoEnhanceEngine.autoEnhanceImage(bmp, currentEditState)
                                currentEditState = enhanced
                                history.pushState(enhanced)
                                Toast.makeText(context, "🪄 Auto-Enhance Aplicado!", Toast.LENGTH_SHORT).show()
                                try {
                                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } catch (e: Exception) {}
                            }
                        },
                        enabled = originalPreviewBitmap != null && !isSaving
                    ) {
                        Text("🪄 AI", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            val initialState = EditState()
                            history.pushState(initialState)
                            currentEditState = initialState
                            try {
                                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(45, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } catch (e: Exception) {}
                        },
                        enabled = (currentEditState != EditState()) && !isSaving
                    ) {
                        Text("Redefinir", color = if (currentEditState != EditState()) Color.White else Color.White.copy(alpha = 0.3f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { showExportDialog = true },
                        enabled = !isSaving
                    ) {
                        Text("⚙️", fontSize = 18.sp)
                    }

                    IconButton(
                        onClick = { com.example.aestheticcamera.ui.share.ShareHelper.shareMedia(context, decodedUri) },
                        enabled = !isSaving
                    ) {
                        Text("📲", fontSize = 18.sp)
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val availableTabs = remember(isVideo) {
                if (isVideo) {
                    listOf("Filtros", "Ajustes", "Retrô", "Recortar/Girar")
                } else {
                    listOf("Filtros", "Ajustes", "Curvas", "HSL", "Retrô", "Texto", "Stickers", "Tilt-Shift", "Recortar/Girar")
                }
            }
            val safeTab = selectedTab.coerceIn(0, availableTabs.size - 1)
            val isTiltShiftTabActive = availableTabs.getOrElse(safeTab) { "" } == "Tilt-Shift"

            val baseAspectRatio = if (currentEditState.selectedCropRatio != CropRatio.FREE) {
                currentEditState.selectedCropRatio.w.toFloat() / currentEditState.selectedCropRatio.h.toFloat()
            } else {
                imageAspectRatio
            }
            // Quando há moldura, o bitmap processado tem dimensões diferentes da foto original.
            // Usamos as dimensões reais do bitmap para calcular o aspect ratio correto.
            val currentAspectRatio = if (currentEditState.frameStyle > 0) {
                processedPreviewBitmap?.let { bmp ->
                    bmp.width.toFloat() / bmp.height.toFloat()
                } ?: baseAspectRatio
            } else {
                baseAspectRatio
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.52f)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val imageModifier = Modifier
                    .graphicsLayer {
                        rotationZ = currentEditState.rotationAngle
                    }

                BoxWithConstraints(
                    modifier = Modifier
                        .aspectRatio(currentAspectRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.5.dp, Color(0xFFFFD54F)), RoundedCornerShape(8.dp))
                        .pointerInput(isVideo) {
                            if (!isVideo) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val touchX = offset.x / size.width
                                        val touchY = offset.y / size.height
                                        val touchedItem = currentEditState.textOverlays.any {
                                            val dx = it.xPercent - touchX
                                            val dy = it.yPercent - touchY
                                            (dx * dx + dy * dy) < 0.02f
                                        } || currentEditState.stickerOverlays.any {
                                            val dx = it.xPercent - touchX
                                            val dy = it.yPercent - touchY
                                            (dx * dx + dy * dy) < 0.02f
                                        }
                                        if (!touchedItem) {
                                            splitFraction = touchX.coerceIn(0f, 1f)
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (splitFraction > 0f) {
                                            change.consume()
                                            val newX = (splitFraction * size.width) + dragAmount.x
                                            splitFraction = (newX / size.width).coerceIn(0f, 1f)
                                        }
                                    },
                                    onDragEnd = {
                                        if (splitFraction < 0.05f || splitFraction > 0.95f) {
                                            splitFraction = 0f
                                        }
                                    }
                                )
                            }
                        }
                ) {
                    val width = constraints.maxWidth.toFloat()
                    val height = constraints.maxHeight.toFloat()
                    val originalBmp = originalPreviewBitmap
                    val editedBmp = processedPreviewBitmap ?: originalPreviewBitmap

                    if (isVideo) {
                        val videoAspectRatio = imageAspectRatio
                        val cropScaleX = if (videoAspectRatio > currentAspectRatio) (videoAspectRatio / currentAspectRatio) else 1f
                        val cropScaleY = if (videoAspectRatio < currentAspectRatio) (currentAspectRatio / videoAspectRatio) else 1f

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(imageModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            // 1. Player de vídeo nativo baseado em TextureView (suporta filtros de cor, rotação e escala 2D)
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.view.TextureView(ctx).apply {
                                        surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                                            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                                                try {
                                                    val mp = android.media.MediaPlayer().apply {
                                                        setDataSource(ctx, decodedUri)
                                                        setSurface(android.view.Surface(surface))
                                                        isLooping = true
                                                        setOnPreparedListener { player ->
                                                            player.seekTo(500)
                                                        }
                                                        prepareAsync()
                                                    }
                                                    mediaPlayerRef = mp
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }

                                            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                                            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                                                mediaPlayerRef?.release()
                                                mediaPlayerRef = null
                                                return true
                                            }
                                            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = cropScaleX
                                        scaleY = cropScaleY
                                        if (filterColorMatrix != null) {
                                            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(filterColorMatrix)
                                        }
                                    }
                            )

                            // 3. Moldura Retrô Transparente (Polaroid, 35mm, Instax) sobre o vídeo
                            if (currentEditState.frameStyle > 0) {
                                TransparentRetroMolduraOverlay(
                                    frameStyle = currentEditState.frameStyle,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // 4. Vazamento de Luz (Soft Warm, Rainbow, Orange Flare) sobre o vídeo
                            if (currentEditState.lightLeakStyle > 0 && currentEditState.lightLeakOpacity > 0.01f) {
                                LightLeakVideoOverlay(
                                    style = currentEditState.lightLeakStyle,
                                    opacity = currentEditState.lightLeakOpacity,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // 5. Vinheta e Granulação sobre o vídeo
                            if (currentEditState.vignette > 0.1f) {
                                VignetteVideoOverlay(
                                    intensity = currentEditState.vignette / 100f,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (currentEditState.grain > 0.1f) {
                                GrainVideoOverlay(
                                    intensity = currentEditState.grain / 100f,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // 6. Carimbo de Data Retrô sobre o vídeo
                            if (currentEditState.dateStampEnabled) {
                                RetroDateStampVideoOverlay(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 16.dp, end = 16.dp)
                                )
                            }

                            // 4. Botão de Play/Pause Integrado e Compacto
                            IconButton(
                                onClick = {
                                    mediaPlayerRef?.let { mp ->
                                        if (mp.isPlaying) {
                                            mp.pause()
                                            isVideoPlaying = false
                                        } else {
                                            mp.start()
                                            isVideoPlaying = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp)
                                    .size(44.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    .border(1.dp, Color(0xFFFFD54F), CircleShape)
                            ) {
                                Text(
                                    text = if (isVideoPlaying) "\u23F8" else "\u25B6",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (originalBmp != null && editedBmp != null) {
                        // 1. Imagem Original (fundo, sem filtros/ajustes)
                        Image(
                            bitmap = originalBmp.asImageBitmap(),
                            contentDescription = "Original",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = currentEditState.rotationAngle },
                            contentScale = ContentScale.Fit
                        )

                        // 2. Imagem Editada (por cima, recortada usando a RightSplitShape)
                        Image(
                            bitmap = editedBmp.asImageBitmap(),
                            contentDescription = "Editada",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RightSplitShape(splitFraction))
                                .then(imageModifier),
                            contentScale = ContentScale.Fit
                        )

                        // 3. Renderização de Textos & Stickers com Gestos de Arrasto Livre (recortado na visão Antes/Depois)
                        if (currentEditState.textOverlays.isNotEmpty() || currentEditState.stickerOverlays.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RightSplitShape(splitFraction))
                            ) {
                                currentEditState.textOverlays.forEach { textItem ->
                                    val textX = with(LocalDensity.current) { (width * textItem.xPercent).toDp() }
                                    val textY = with(LocalDensity.current) { (height * textItem.yPercent).toDp() }
                                    val composeFontFamily = when (textItem.fontStyle) {
                                        "SERIF" -> androidx.compose.ui.text.font.FontFamily.Serif
                                        "SCRIPT" -> androidx.compose.ui.text.font.FontFamily.Cursive
                                        "TYPEWRITER" -> androidx.compose.ui.text.font.FontFamily.Monospace
                                        "SANS" -> androidx.compose.ui.text.font.FontFamily.SansSerif
                                        else -> androidx.compose.ui.text.font.FontFamily.Default
                                    }
                                    Text(
                                        text = textItem.text,
                                        color = Color(android.graphics.Color.parseColor(textItem.colorHex)),
                                        fontSize = textItem.fontSizeSp.sp,
                                        fontWeight = if (textItem.fontStyle == "BOLD") FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = composeFontFamily,
                                        modifier = Modifier
                                            .offset(x = textX, y = textY)
                                            .graphicsLayer { rotationZ = textItem.rotation }
                                            .pointerInput(textItem.id) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val latest = currentEditState.textOverlays.find { it.id == textItem.id } ?: textItem
                                                    val deltaX = dragAmount.x / width
                                                    val deltaY = dragAmount.y / height
                                                    val newX = (latest.xPercent + deltaX).coerceIn(0.02f, 0.98f)
                                                    val newY = (latest.yPercent + deltaY).coerceIn(0.02f, 0.98f)
                                                    val updated = currentEditState.textOverlays.map {
                                                        if (it.id == textItem.id) it.copy(xPercent = newX, yPercent = newY) else it
                                                    }
                                                    currentEditState = currentEditState.copy(textOverlays = updated)
                                                }
                                            }
                                    )
                                }

                                currentEditState.stickerOverlays.forEach { stickerItem ->
                                    val stickerX = with(LocalDensity.current) { (width * stickerItem.xPercent).toDp() }
                                    val stickerY = with(LocalDensity.current) { (height * stickerItem.yPercent).toDp() }
                                    Text(
                                        text = stickerItem.symbol,
                                        fontSize = (32 * stickerItem.scale).sp,
                                        modifier = Modifier
                                            .offset(x = stickerX, y = stickerY)
                                            .graphicsLayer { rotationZ = stickerItem.rotation }
                                            .pointerInput(stickerItem.id) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val latest = currentEditState.stickerOverlays.find { it.id == stickerItem.id } ?: stickerItem
                                                    val deltaX = dragAmount.x / width
                                                    val deltaY = dragAmount.y / height
                                                    val newX = (latest.xPercent + deltaX).coerceIn(0.02f, 0.98f)
                                                    val newY = (latest.yPercent + deltaY).coerceIn(0.02f, 0.98f)
                                                    val updated = currentEditState.stickerOverlays.map {
                                                        if (it.id == stickerItem.id) it.copy(xPercent = newX, yPercent = newY) else it
                                                    }
                                                    currentEditState = currentEditState.copy(stickerOverlays = updated)
                                                }
                                            }
                                    )
                                }
                            }
                        }

                        // 4. Guias de Foco do Tilt-Shift (visíveis apenas enquanto na aba 'Tilt-Shift')
                        if (isTiltShiftTabActive && currentEditState.tiltShiftConfig.mode != com.example.aestheticcamera.ui.editor.model.TiltShiftMode.NONE) {
                            val config = currentEditState.tiltShiftConfig
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val focalY = h * config.focusPosition
                                val bandHalfH = h * (config.radius / 2f)

                                if (config.mode == com.example.aestheticcamera.ui.editor.model.TiltShiftMode.LINEAR) {
                                    drawLine(
                                        color = Color(0xFFFFD54F),
                                        start = Offset(0f, focalY - bandHalfH),
                                        end = Offset(w, focalY - bandHalfH),
                                        strokeWidth = 2f
                                    )
                                    drawLine(
                                        color = Color(0xFFFFD54F),
                                        start = Offset(0f, focalY + bandHalfH),
                                        end = Offset(w, focalY + bandHalfH),
                                        strokeWidth = 2f
                                    )
                                } else if (config.mode == com.example.aestheticcamera.ui.editor.model.TiltShiftMode.RADIAL) {
                                    val radiusPx = (minOf(w, h) * config.radius).coerceAtLeast(10f)
                                    drawCircle(
                                        color = Color(0xFFFFD54F),
                                        radius = radiusPx,
                                        center = Offset(w / 2f, focalY),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                                    )
                                }
                            }
                        }

                        // 5. Grade de Enquadramento Inteligente (Regra dos Terços na aba 'Recortar/Girar')
                        if (availableTabs.getOrElse(safeTab) { "" } == "Recortar/Girar") {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val col1 = w / 3f
                                val col2 = (w * 2f) / 3f
                                val row1 = h / 3f
                                val row2 = (h * 2f) / 3f

                                val gridColor = Color(0xFFFFD54F).copy(alpha = 0.45f)

                                drawLine(color = gridColor, start = Offset(col1, 0f), end = Offset(col1, h), strokeWidth = 1.5f)
                                drawLine(color = gridColor, start = Offset(col2, 0f), end = Offset(col2, h), strokeWidth = 1.5f)
                                drawLine(color = gridColor, start = Offset(0f, row1), end = Offset(w, row1), strokeWidth = 1.5f)
                                drawLine(color = gridColor, start = Offset(0f, row2), end = Offset(w, row2), strokeWidth = 1.5f)

                                listOf(Offset(col1, row1), Offset(col2, row1), Offset(col1, row2), Offset(col2, row2)).forEach { dot ->
                                    drawCircle(color = Color(0xFFFFD54F), radius = 3.dp.toPx(), center = dot)
                                }
                            }
                        }

                        if (splitFraction > 0f) {
                            // 3. Linha Divisória e Alça de Arrasto
                            val splitXPx = width * splitFraction
                            val splitXDp = with(LocalDensity.current) { splitXPx.toDp() }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.5.dp)
                                    .offset(x = splitXDp)
                                    .background(Color.White)
                            )

                            // Círculo com seta de alça no centro
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .offset(x = splitXDp - 14.dp)
                                    .align(Alignment.CenterStart)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.2f)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "↔",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Rótulos BEFORE / AFTER
                            Text(
                                text = "BEFORE",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )

                            Text(
                                text = "AFTER",
                                color = Color(0xFFFFD54F).copy(alpha = 0.8f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        CircularProgressIndicator(color = Color(0xFFFFD54F))
                    }
                }
            }

            // Barra de Ferramentas / Sliders e Filtros
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(bottom = 12.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val availableTabs = remember(isVideo) {
                    if (isVideo) {
                        listOf("Filtros", "Ajustes", "Retrô", "Recortar/Girar")
                    } else {
                        listOf("Filtros", "Ajustes", "Curvas", "HSL", "Retrô", "Texto", "Stickers", "Tilt-Shift", "Recortar/Girar")
                    }
                }
                val safeTab = selectedTab.coerceIn(0, availableTabs.size - 1)

                // Seletor de Abas Deslizável (ScrollableTabRow)
                ScrollableTabRow(
                    selectedTabIndex = safeTab,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFFFFD54F),
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        if (safeTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[safeTab]),
                                color = Color(0xFFFFD54F)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    availableTabs.forEachIndexed { index, tabName ->
                        Tab(
                            selected = safeTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(tabName, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                // Conteúdo da Aba Selecionada
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (availableTabs.getOrElse(safeTab) { "Filtros" }) {
                        "Filtros" -> {
                            // Filtros e Intensidade do Filtro
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (currentEditState.activeFilter != FilterType.ORIGINAL) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Intensidade", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                                        Slider(
                                            value = currentEditState.filterIntensity,
                                            onValueChange = {
                                                currentEditState = currentEditState.copy(filterIntensity = it)
                                            },
                                            onValueChangeFinished = {
                                                history.pushState(currentEditState)
                                            },
                                            valueRange = 0f..1f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFFFFD54F),
                                                activeTrackColor = Color(0xFFFFD54F)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(FilterType.values()) { filter ->
                                        FilterThumbnailItem(
                                            filter = filter,
                                            isSelected = currentEditState.activeFilter == filter,
                                            onClick = {
                                                currentEditState = currentEditState.copy(activeFilter = filter)
                                                history.pushState(currentEditState)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        "Ajustes" -> {
                            // Ajustes Finos (Básicos)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AdjustmentSlider(
                                    name = "Brilho",
                                    value = currentEditState.brightness,
                                    onValueChange = { currentEditState = currentEditState.copy(brightness = it) },
                                    onValueChangeFinished = { history.pushState(currentEditState) },
                                    valueRange = -100f..100f,
                                    isCentralZero = true
                                )
                                AdjustmentSlider(
                                    name = "Contraste",
                                    value = currentEditState.contrast,
                                    onValueChange = { currentEditState = currentEditState.copy(contrast = it) },
                                    onValueChangeFinished = { history.pushState(currentEditState) },
                                    valueRange = -100f..100f,
                                    isCentralZero = true
                                )
                                AdjustmentSlider(
                                    name = "Saturação",
                                    value = currentEditState.saturation,
                                    onValueChange = { currentEditState = currentEditState.copy(saturation = it) },
                                    onValueChangeFinished = { history.pushState(currentEditState) },
                                    valueRange = -100f..100f,
                                    isCentralZero = true
                                )
                                AdjustmentSlider(
                                    name = "Temperatura",
                                    value = currentEditState.warmth,
                                    onValueChange = { currentEditState = currentEditState.copy(warmth = it) },
                                    onValueChangeFinished = { history.pushState(currentEditState) },
                                    valueRange = -100f..100f,
                                    isCentralZero = true
                                )
                                AdjustmentSlider(
                                    name = "Destaques",
                                    value = currentEditState.highlights,
                                    onValueChange = { currentEditState = currentEditState.copy(highlights = it) },
                                    onValueChangeFinished = { history.pushState(currentEditState) },
                                    valueRange = -100f..100f,
                                    isCentralZero = true
                                )
                                AdjustmentSlider(
                                    name = "Sombras",
                                    value = currentEditState.shadows,
                                    onValueChange = { currentEditState = currentEditState.copy(shadows = it) },
                                    onValueChangeFinished = { history.pushState(currentEditState) },
                                    valueRange = -100f..100f,
                                    isCentralZero = true
                                )
                                AdjustmentSlider(
                                    name = "Vinheta",
                                    value = currentEditState.vignette,
                                    onValueChange = { currentEditState = currentEditState.copy(vignette = it) },
                                    onValueChangeFinished = { history.pushState(currentEditState) },
                                    valueRange = 0f..100f,
                                    isCentralZero = false
                                )
                                AdjustmentSlider(
                                    name = "Granulação",
                                    value = currentEditState.grain,
                                    onValueChange = { currentEditState = currentEditState.copy(grain = it) },
                                    onValueChangeFinished = { history.pushState(currentEditState) },
                                    valueRange = 0f..100f,
                                    isCentralZero = false
                                )
                                if (!isVideo) {
                                    AdjustmentSlider(
                                        name = "Nitidez",
                                        value = currentEditState.sharpness,
                                        onValueChange = { currentEditState = currentEditState.copy(sharpness = it) },
                                        onValueChangeFinished = { history.pushState(currentEditState) },
                                        valueRange = 0f..100f,
                                        isCentralZero = false
                                    )
                                }
                            }
                        }
                        "Curvas" -> {
                            // Aba de Curvas de Tons
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CurvesControlTabRow(
                                    selectedChannel = activeCurveChannel,
                                    onChannelChange = { activeCurveChannel = it },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                CurvesCanvas(
                                    points = when (activeCurveChannel) {
                                        CurveChannel.RGB -> currentEditState.rgbCurve
                                        CurveChannel.RED -> currentEditState.redCurve
                                        CurveChannel.GREEN -> currentEditState.greenCurve
                                        CurveChannel.BLUE -> currentEditState.blueCurve
                                    },
                                    channelColor = when (activeCurveChannel) {
                                        CurveChannel.RGB -> Color.White
                                        CurveChannel.RED -> Color(0xFFFF3B30)
                                        CurveChannel.GREEN -> Color(0xFF34C759)
                                        CurveChannel.BLUE -> Color(0xFF007AFF)
                                    },
                                    onPointsChange = { newPoints ->
                                        currentEditState = when (activeCurveChannel) {
                                            CurveChannel.RGB -> currentEditState.copy(rgbCurve = newPoints)
                                            CurveChannel.RED -> currentEditState.copy(redCurve = newPoints)
                                            CurveChannel.GREEN -> currentEditState.copy(greenCurve = newPoints)
                                            CurveChannel.BLUE -> currentEditState.copy(blueCurve = newPoints)
                                        }
                                    },
                                    onDragFinished = {
                                        history.pushState(currentEditState)
                                    },
                                    modifier = Modifier.height(115.dp)
                                )
                            }
                        }
                        "HSL" -> {
                            // Aba de Ajuste HSL
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Seletores de cores HSL
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    items(HslColorChannel.values()) { channel ->
                                        val isSelected = selectedHslChannel == channel
                                        val hasAdjustment = currentEditState.hslAdjustments[channel]?.let {
                                            it.hue != 0f || it.saturation != 0f || it.lightness != 0f
                                        } ?: false

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .clickable { selectedHslChannel = channel }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isSelected) 30.dp else 24.dp)
                                                    .clip(CircleShape)
                                                    .background(channel.color)
                                                    .border(
                                                        BorderStroke(
                                                            if (isSelected) 2.5.dp else 0.dp,
                                                            Color.White
                                                        ),
                                                        CircleShape
                                                    )
                                            )
                                            if (hasAdjustment) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 2.dp)
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFFFD54F))
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }
                                        }
                                    }
                                }

                                // Sliders de Ajuste HSL
                                val adj = currentEditState.hslAdjustments[selectedHslChannel] ?: HslAdjustment()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    HslAdjustmentSlider(
                                        name = "Matiz",
                                        value = adj.hue,
                                        onValueChange = { newVal ->
                                            val updatedMap = currentEditState.hslAdjustments.toMutableMap().apply {
                                                put(selectedHslChannel, adj.copy(hue = newVal))
                                            }
                                            currentEditState = currentEditState.copy(hslAdjustments = updatedMap)
                                        },
                                        onValueChangeFinished = { history.pushState(currentEditState) }
                                    )

                                    HslAdjustmentSlider(
                                        name = "Saturação",
                                        value = adj.saturation,
                                        onValueChange = { newVal ->
                                            val updatedMap = currentEditState.hslAdjustments.toMutableMap().apply {
                                                put(selectedHslChannel, adj.copy(saturation = newVal))
                                            }
                                            currentEditState = currentEditState.copy(hslAdjustments = updatedMap)
                                        },
                                        onValueChangeFinished = { history.pushState(currentEditState) }
                                    )

                                    HslAdjustmentSlider(
                                        name = "Luminância",
                                        value = adj.lightness,
                                        onValueChange = { newVal ->
                                            val updatedMap = currentEditState.hslAdjustments.toMutableMap().apply {
                                                put(selectedHslChannel, adj.copy(lightness = newVal))
                                            }
                                            currentEditState = currentEditState.copy(hslAdjustments = updatedMap)
                                        },
                                        onValueChangeFinished = { history.pushState(currentEditState) }
                                    )
                                }
                            }
                        }
                        "Retrô" -> {
                            // Aba Retrô (Vazamento, Data, Moldura)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Vazamento de Luz
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "VAZAMENTO DE LUZ",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        val styleLabel = when (currentEditState.lightLeakStyle) {
                                            1 -> "Warm"
                                            2 -> "Rainbow"
                                            3 -> "Flare"
                                            else -> "Nenhum"
                                        }
                                        Text(
                                            text = styleLabel,
                                            color = if (currentEditState.lightLeakStyle > 0) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.3f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(0 to "Nenhum", 1 to "Soft Warm", 2 to "Rainbow", 3 to "Orange Flare").forEach { (styleId, label) ->
                                            val isSelected = currentEditState.lightLeakStyle == styleId
                                            val bgCol by animateColorAsState(if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(bgCol)
                                                    .clickable {
                                                        currentEditState = currentEditState.copy(
                                                            lightLeakStyle = styleId,
                                                            lightLeakOpacity = if (styleId > 0 && currentEditState.lightLeakOpacity <= 0.05f) 0.6f else currentEditState.lightLeakOpacity
                                                        )
                                                        history.pushState(currentEditState)
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text(label, color = if (isSelected) Color.Black else Color.White, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    if (currentEditState.lightLeakStyle > 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Opacidade", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.width(60.dp))
                                            Slider(
                                                value = currentEditState.lightLeakOpacity,
                                                onValueChange = { currentEditState = currentEditState.copy(lightLeakOpacity = it) },
                                                onValueChangeFinished = { history.pushState(currentEditState) },
                                                valueRange = 0f..1f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F)),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                Divider(color = Color.White.copy(alpha = 0.06f))

                                // 2. Carimbo de Data
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("CARIMBO DE DATA RETRÔ", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        Text("Adiciona display LED '90s no canto inferior", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                                    }
                                    Switch(
                                        checked = currentEditState.dateStampEnabled,
                                        onCheckedChange = {
                                            currentEditState = currentEditState.copy(dateStampEnabled = it)
                                            history.pushState(currentEditState)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFD54F), checkedTrackColor = Color(0xFFFFD54F).copy(alpha = 0.5f))
                                    )
                                }

                                Divider(color = Color.White.copy(alpha = 0.06f))

                                // 3. Molduras Retro
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                    Text(
                                        text = "MOLDURA VINTAGE",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(0 to "Nenhum", 1 to "Polaroid", 2 to "35mm Film", 3 to "Instax").forEach { (styleId, label) ->
                                            val isSelected = currentEditState.frameStyle == styleId
                                            val bgCol by animateColorAsState(if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(bgCol)
                                                    .clickable {
                                                        currentEditState = currentEditState.copy(frameStyle = styleId)
                                                        history.pushState(currentEditState)
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(label, color = if (isSelected) Color.Black else Color.White, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "Texto" -> {
                            var showTextEntryDialog by remember { mutableStateOf(false) }
                            var editingTextString by remember { mutableStateOf("Aesthetic Camera") }

                            if (showTextEntryDialog) {
                                AlertDialog(
                                    onDismissRequest = { showTextEntryDialog = false },
                                    title = { Text("Digitar Texto", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                                    text = {
                                        OutlinedTextField(
                                            value = editingTextString,
                                            onValueChange = { editingTextString = it },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color(0xFFFFD54F),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                showTextEntryDialog = false
                                                if (editingTextString.isNotBlank()) {
                                                    val newItem = com.example.aestheticcamera.ui.editor.model.TextOverlayItem(text = editingTextString)
                                                    currentEditState = currentEditState.copy(textOverlays = currentEditState.textOverlays + newItem)
                                                    history.pushState(currentEditState)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F), contentColor = Color.Black)
                                        ) {
                                            Text("Adicionar", fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showTextEntryDialog = false }) {
                                            Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
                                        }
                                    },
                                    containerColor = Color(0xFF1E1E22)
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Legendas (${currentEditState.textOverlays.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { showTextEntryDialog = true }) {
                                        Text("✏️ + Digitar Texto", color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (currentEditState.textOverlays.isNotEmpty()) {
                                    val activeText = currentEditState.textOverlays.last()
                                    val textColors = listOf("#FFFFFF", "#FFFFD54F", "#000000", "#FF5252", "#448AFF", "#FF4081", "#69F0AE")

                                    // 1. Cores
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(textColors) { hex ->
                                            val isSel = activeText.colorHex == hex
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                                    .border(if (isSel) 2.dp else 0.5.dp, if (isSel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.3f), CircleShape)
                                                    .clickable {
                                                        val updated = currentEditState.textOverlays.map { if (it.id == activeText.id) it.copy(colorHex = hex) else it }
                                                        currentEditState = currentEditState.copy(textOverlays = updated)
                                                        history.pushState(currentEditState)
                                                    }
                                            )
                                        }
                                    }

                                    // 2. Estilos Tipográficos & Tamanho
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("SERIF", "SCRIPT", "SANS", "TYPEWRITER", "BOLD").forEach { style ->
                                            val isSel = activeText.fontStyle == style
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f))
                                                    .clickable {
                                                        val updated = currentEditState.textOverlays.map { if (it.id == activeText.id) it.copy(fontStyle = style) else it }
                                                        currentEditState = currentEditState.copy(textOverlays = updated)
                                                        history.pushState(currentEditState)
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(style.take(4), color = if (isSel) Color.Black else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        IconButton(onClick = {
                                            val updated = currentEditState.textOverlays.dropLast(1)
                                            currentEditState = currentEditState.copy(textOverlays = updated)
                                            history.pushState(currentEditState)
                                        }) {
                                            Text("🗑️", fontSize = 14.sp)
                                        }
                                    }

                                    // 3. Slider de Rotação
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Rotação", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.width(55.dp))
                                        Slider(
                                            value = activeText.rotation,
                                            onValueChange = { rot ->
                                                val updated = currentEditState.textOverlays.map { if (it.id == activeText.id) it.copy(rotation = rot) else it }
                                                currentEditState = currentEditState.copy(textOverlays = updated)
                                            },
                                            onValueChangeFinished = { history.pushState(currentEditState) },
                                            valueRange = -180f..180f,
                                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F)),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Text("Clique em '✏️ + Digitar Texto' para escrever legendas retrô na foto (arraste para posicionar).", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp))
                                }
                            }
                        }
                        "Stickers" -> {
                            val stickerPacks = listOf(
                                "Sparkles" to listOf("✨", "🌟", "💫", "⭐", "⚡", "☀️"),
                                "Vintage" to listOf("📌", "🎞️", "🎬", "📷", "📼", "🏷️"),
                                "Aesthetic" to listOf("🌸", "🌙", "🕊️", "☕", "🌊", "🖤")
                            )

                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Stickers (${currentEditState.stickerOverlays.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    if (currentEditState.stickerOverlays.isNotEmpty()) {
                                        TextButton(onClick = {
                                            currentEditState = currentEditState.copy(stickerOverlays = emptyList())
                                            history.pushState(currentEditState)
                                        }) {
                                            Text("Limpar Todos", color = Color.Red.copy(alpha = 0.8f), fontSize = 10.sp)
                                        }
                                    }
                                }

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    stickerPacks.forEach { (cat, symbols) ->
                                        items(symbols) { sym ->
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color.White.copy(alpha = 0.12f))
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        val newSticker = com.example.aestheticcamera.ui.editor.model.StickerOverlayItem(
                                                            symbol = sym,
                                                            category = cat,
                                                            xPercent = (0.2f + (currentEditState.stickerOverlays.size * 0.15f) % 0.6f),
                                                            yPercent = (0.3f + (currentEditState.stickerOverlays.size * 0.12f) % 0.5f)
                                                        )
                                                        currentEditState = currentEditState.copy(stickerOverlays = currentEditState.stickerOverlays + newSticker)
                                                        history.pushState(currentEditState)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(sym, fontSize = 20.sp)
                                            }
                                        }
                                    }
                                }

                                if (currentEditState.stickerOverlays.isNotEmpty()) {
                                    val activeSticker = currentEditState.stickerOverlays.last()
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Escala", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, modifier = Modifier.width(45.dp))
                                        Slider(
                                            value = activeSticker.scale,
                                            onValueChange = { sc ->
                                                val updated = currentEditState.stickerOverlays.map { if (it.id == activeSticker.id) it.copy(scale = sc) else it }
                                                currentEditState = currentEditState.copy(stickerOverlays = updated)
                                            },
                                            onValueChangeFinished = { history.pushState(currentEditState) },
                                            valueRange = 0.5f..3.0f,
                                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F)),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = {
                                            val updated = currentEditState.stickerOverlays.dropLast(1)
                                            currentEditState = currentEditState.copy(stickerOverlays = updated)
                                            history.pushState(currentEditState)
                                        }) {
                                            Text("🗑️", fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                        "Tilt-Shift" -> {
                            val config = currentEditState.tiltShiftConfig
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text("DESFOQUE TILT-SHIFT", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    com.example.aestheticcamera.ui.editor.model.TiltShiftMode.values().forEach { mode ->
                                        val label = when (mode) {
                                            com.example.aestheticcamera.ui.editor.model.TiltShiftMode.NONE -> "Nenhum"
                                            com.example.aestheticcamera.ui.editor.model.TiltShiftMode.LINEAR -> "Linear (Faixa)"
                                            com.example.aestheticcamera.ui.editor.model.TiltShiftMode.RADIAL -> "Radial (Círculo)"
                                        }
                                        val isSel = config.mode == mode
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f))
                                                .clickable {
                                                    val updated = config.copy(mode = mode)
                                                    currentEditState = currentEditState.copy(tiltShiftConfig = updated)
                                                    history.pushState(currentEditState)
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(label, color = if (isSel) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (config.mode != com.example.aestheticcamera.ui.editor.model.TiltShiftMode.NONE) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Raio Focal", color = Color.White, fontSize = 10.sp, modifier = Modifier.width(75.dp))
                                            Slider(
                                                value = config.radius,
                                                onValueChange = {
                                                    currentEditState = currentEditState.copy(tiltShiftConfig = config.copy(radius = it))
                                                },
                                                onValueChangeFinished = { history.pushState(currentEditState) },
                                                valueRange = 0.1f..0.6f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F)),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Intensidade", color = Color.White, fontSize = 10.sp, modifier = Modifier.width(75.dp))
                                            Slider(
                                                value = config.blurStrength,
                                                onValueChange = {
                                                    currentEditState = currentEditState.copy(tiltShiftConfig = config.copy(blurStrength = it))
                                                },
                                                onValueChangeFinished = { history.pushState(currentEditState) },
                                                valueRange = 4f..25f,
                                                colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F)),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "Recortar/Girar" -> {
                            // Aba de Recortar e Girar
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "PROPORÇÃO DE CORTE",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(CropRatio.values()) { ratio ->
                                        val isSelected = currentEditState.selectedCropRatio == ratio
                                        val backgroundColor by animateColorAsState(
                                            if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f)
                                        )
                                        val textColor = if (isSelected) Color.Black else Color.White

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(backgroundColor)
                                                .clickable {
                                                    currentEditState = currentEditState.copy(selectedCropRatio = ratio)
                                                    history.pushState(currentEditState)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = ratio.displayName,
                                                color = textColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        currentEditState = currentEditState.copy(rotationAngle = (currentEditState.rotationAngle + 90f) % 360f)
                                        history.pushState(currentEditState)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                                ) {
                                    Text("Girar 90°", color = Color.White)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botão de Salvar final
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showExportDialog = true },
                        colors = ButtonColors(
                            containerColor = Color(0xFFFFD54F),
                            contentColor = Color.Black,
                            disabledContainerColor = Color.Gray,
                            disabledContentColor = Color.DarkGray
                        ),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                        } else {
                            Text("Salvar na Galeria 💾", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showExportDialog) {
            ExportOptionsDialog(
                initialFormat = selectedExportFormat,
                initialQuality = selectedExportQuality,
                initialWatermark = watermarkEnabled,
                onDismiss = { showExportDialog = false },
                onConfirmExport = { fmt, qual, wmark ->
                    showExportDialog = false
                    selectedExportFormat = fmt
                    selectedExportQuality = qual
                    watermarkEnabled = wmark

                    scope.launch {
                        isSaving = true
                        if (isVideo) {
                            val inputTempFile = java.io.File(context.cacheDir, "input_edit_video_${System.currentTimeMillis()}.mp4")
                            try {
                                withContext(Dispatchers.IO) {
                                    context.contentResolver.openInputStream(decodedUri)?.use { input ->
                                        java.io.FileOutputStream(inputTempFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                                val mat5x4 = getCombinedMatrix(
                                    currentEditState.activeFilter,
                                    currentEditState.filterIntensity,
                                    currentEditState.brightness,
                                    currentEditState.contrast,
                                    currentEditState.saturation,
                                    currentEditState.warmth,
                                    currentEditState.highlights,
                                    currentEditState.shadows,
                                    0f
                                )
                                val colMajor4x4 = floatArrayOf(
                                    mat5x4[0], mat5x4[5], mat5x4[10], mat5x4[15],
                                    mat5x4[1], mat5x4[6], mat5x4[11], mat5x4[16],
                                    mat5x4[2], mat5x4[7], mat5x4[12], mat5x4[17],
                                    mat5x4[4], mat5x4[9], mat5x4[14], mat5x4[19]
                                )
                                com.example.aestheticcamera.ui.camera.VideoTranscoder.transcodeWithFilter(
                                    context = context,
                                    inputFile = inputTempFile,
                                    filter = currentEditState.activeFilter,
                                    intensity = currentEditState.filterIntensity,
                                    customColorMatrix4x4 = colMajor4x4,
                                    onSuccess = { uri ->
                                        isSaving = false
                                        Toast.makeText(context, "Vídeo salvo com sucesso na galeria!", Toast.LENGTH_SHORT).show()
                                        onNavigateBack()
                                    },
                                    onError = { error ->
                                        isSaving = false
                                        Toast.makeText(context, "Erro ao salvar vídeo: $error", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } catch (e: Exception) {
                                isSaving = false
                                Toast.makeText(context, "Erro ao preparar vídeo: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val rgbLut = CubicSpline.calculateLut(currentEditState.rgbCurve)
                            val redLut = CubicSpline.calculateLut(currentEditState.redCurve)
                            val greenLut = CubicSpline.calculateLut(currentEditState.greenCurve)
                            val blueLut = CubicSpline.calculateLut(currentEditState.blueCurve)

                            val finalLutR = IntArray(256) { rgbLut[redLut[it]] }
                            val finalLutG = IntArray(256) { rgbLut[greenLut[it]] }
                            val finalLutB = IntArray(256) { rgbLut[blueLut[it]] }
                            val combinedLuts = CurvesLuts(finalLutR, finalLutG, finalLutB)

                            val matrix = getCombinedMatrix(
                                currentEditState.activeFilter,
                                currentEditState.filterIntensity,
                                currentEditState.brightness,
                                currentEditState.contrast,
                                currentEditState.saturation,
                                currentEditState.warmth,
                                currentEditState.highlights,
                                currentEditState.shadows,
                                currentEditState.sharpness
                            )

                            val success = saveEditedImage(
                                context = context,
                                sourceUri = decodedUri,
                                colorMatrix = matrix,
                                curvesLutsCombined = combinedLuts,
                                hslAdjustments = currentEditState.hslAdjustments,
                                rotation = currentEditState.rotationAngle,
                                cropRatio = currentEditState.selectedCropRatio,
                                vignette = currentEditState.vignette,
                                grain = currentEditState.grain,
                                sharpness = currentEditState.sharpness,
                                lightLeakStyle = currentEditState.lightLeakStyle,
                                lightLeakOpacity = currentEditState.lightLeakOpacity,
                                dateStampEnabled = currentEditState.dateStampEnabled,
                                frameStyle = currentEditState.frameStyle,
                                exportFormat = fmt,
                                exportQuality = qual.toInt(),
                                watermarkEnabled = wmark,
                                textOverlays = currentEditState.textOverlays,
                                stickerOverlays = currentEditState.stickerOverlays,
                                tiltShiftConfig = currentEditState.tiltShiftConfig
                            )
                            isSaving = false
                            if (success) {
                                Toast.makeText(context, "Imagem salva na Galeria ($fmt)! ", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            } else {
                                Toast.makeText(context, "Erro ao salvar imagem editada", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun HslAdjustmentSlider(
    name: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(65.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = -100f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFFD54F),
                activeTrackColor = Color(0xFFFFD54F),
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toInt().toString(),
            color = if (value.toInt() != 0) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.Center
        )
    }
}

// Pipeline completa de filtros e processamentos de imagem em um loop único altamente otimizado
private suspend fun applyImagePipeline(
    src: Bitmap,
    colorMatrix: FloatArray,
    curvesLuts: CurvesLuts,
    hslAdjustments: Map<HslColorChannel, HslAdjustment>,
    vignette: Float,
    grain: Float,
    sharpness: Float,
    lightLeakStyle: Int = 0,
    lightLeakOpacity: Float = 0f,
    dateStampEnabled: Boolean = false,
    frameStyle: Int = 0,
    tiltShiftConfig: com.example.aestheticcamera.ui.editor.model.TiltShiftConfig = com.example.aestheticcamera.ui.editor.model.TiltShiftConfig()
): Bitmap = withContext(Dispatchers.Default) {
    val width = src.width
    val height = src.height
    val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)

    val channelKeys = HslColorChannel.values()
    val hues = FloatArray(8)
    val sats = FloatArray(8)
    val lits = FloatArray(8)
    var hasHsl = false
    for (i in 0 until 8) {
        val adj = hslAdjustments[channelKeys[i]] ?: HslAdjustment()
        hues[i] = adj.hue
        sats[i] = adj.saturation
        lits[i] = adj.lightness
        if (adj.hue != 0f || adj.saturation != 0f || adj.lightness != 0f) {
            hasHsl = true
        }
    }

    val lutR = curvesLuts.r
    val lutG = curvesLuts.g
    val lutB = curvesLuts.b

    val m = colorMatrix

    for (i in pixels.indices) {
        val color = pixels[i]
        val a = (color shr 24) and 0xFF
        val rVal = (color shr 16) and 0xFF
        val gVal = (color shr 8) and 0xFF
        val bVal = color and 0xFF

        // 1. Color Matrix (filtros, brilho, contraste, saturação, temperatura)
        val newR = (m[0] * rVal + m[1] * gVal + m[2] * bVal + m[4]).toInt().coerceIn(0, 255)
        val newG = (m[5] * rVal + m[6] * gVal + m[7] * bVal + m[9]).toInt().coerceIn(0, 255)
        val newB = (m[10] * rVal + m[11] * gVal + m[12] * bVal + m[14]).toInt().coerceIn(0, 255)

        // 2. Curvas LUT
        var r = lutR[newR]
        var g = lutG[newG]
        var b = lutB[newB]

        // 3. Ajuste HSL
        if (hasHsl) {
            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f

            val max = maxOf(rf, gf, bf)
            val min = minOf(rf, gf, bf)
            var h = 0f
            val s: Float
            val l = (max + min) / 2f

            if (max != min) {
                val d = max - min
                s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
                h = when (max) {
                    rf -> (gf - bf) / d + (if (gf < bf) 6f else 0f)
                    gf -> (bf - rf) / d + 2f
                    else -> (rf - gf) / d + 4f
                }
                h *= 60f
                if (h < 0f) h += 360f

                // Interpolar suavemente entre canais com base no Hue
                val centerHues = floatArrayOf(0f, 30f, 60f, 120f, 180f, 240f, 280f, 320f, 360f)
                val indices = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 0)

                var idx1 = 0
                var idx2 = 1
                for (j in 0..7) {
                    if (h >= centerHues[j] && h <= centerHues[j + 1]) {
                        idx1 = indices[j]
                        idx2 = indices[j + 1]
                        break
                    }
                }

                val h1 = centerHues[idx1]
                val h2 = centerHues[if (idx1 == 7 && idx2 == 0) 8 else idx2]
                val t = if (h2 > h1) (h - h1) / (h2 - h1) else 0f

                val dh = hues[idx1] * (1f - t) + hues[idx2] * t
                val ds = sats[idx1] * (1f - t) + sats[idx2] * t
                val dl = lits[idx1] * (1f - t) + lits[idx2] * t

                // Ajustar Hue (escala máxima 18 graus)
                var newHue = (h + dh * 0.18f) % 360f
                if (newHue < 0f) newHue += 360f

                // Ajustar Saturação
                val newSat = (s * (1f + ds / 100f)).coerceIn(0f, 1f)

                // Ajustar Lightness sem clipping duro
                val newLit = if (dl > 0f) {
                    l + (1f - l) * (dl / 100f)
                } else {
                    l + l * (dl / 100f)
                }.coerceIn(0f, 1f)

                // Converter HSL de volta para RGB
                val cVal = (1f - kotlin.math.abs(2f * newLit - 1f)) * newSat
                val xVal = cVal * (1f - kotlin.math.abs((newHue / 60f) % 2f - 1f))
                val mVal = newLit - cVal / 2f

                var r1 = 0f
                var g1 = 0f
                var b1 = 0f
                when {
                    newHue < 60f -> { r1 = cVal; g1 = xVal }
                    newHue < 120f -> { r1 = xVal; g1 = cVal }
                    newHue < 180f -> { g1 = cVal; b1 = xVal }
                    newHue < 240f -> { g1 = xVal; b1 = cVal }
                    newHue < 300f -> { r1 = xVal; b1 = cVal }
                    else -> { r1 = cVal; b1 = xVal }
                }

                r = ((r1 + mVal) * 255f).toInt().coerceIn(0, 255)
                g = ((g1 + mVal) * 255f).toInt().coerceIn(0, 255)
                b = ((b1 + mVal) * 255f).toInt().coerceIn(0, 255)
            }
        }

        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    dest.setPixels(pixels, 0, width, 0, 0, width, height)

    // 3.5. Vazamento de Luz (Procedural com Canvas Gradients)
    if (lightLeakStyle > 0 && lightLeakOpacity > 0f) {
        val w = dest.width.toFloat()
        val h = dest.height.toFloat()
        val leakCanvas = android.graphics.Canvas(dest)
        val leakPaint = android.graphics.Paint()
        
        when (lightLeakStyle) {
            1 -> { // Soft Warm
                val shader = android.graphics.LinearGradient(
                    0f, h / 2f, w * 0.45f, h / 2f,
                    intArrayOf(android.graphics.Color.argb((lightLeakOpacity * 230).toInt(), 255, 61, 0), android.graphics.Color.argb((lightLeakOpacity * 140).toInt(), 255, 145, 0), android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0f, 0.4f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                leakPaint.shader = shader
            }
            2 -> { // Rainbow
                val shader = android.graphics.LinearGradient(
                    0f, 0f, w * 0.7f, h * 0.7f,
                    intArrayOf(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.argb((lightLeakOpacity * 130).toInt(), 255, 0, 0),
                        android.graphics.Color.argb((lightLeakOpacity * 130).toInt(), 255, 127, 0),
                        android.graphics.Color.argb((lightLeakOpacity * 130).toInt(), 255, 255, 0),
                        android.graphics.Color.argb((lightLeakOpacity * 110).toInt(), 0, 255, 0),
                        android.graphics.Color.argb((lightLeakOpacity * 110).toInt(), 0, 0, 255),
                        android.graphics.Color.argb((lightLeakOpacity * 110).toInt(), 139, 0, 255),
                        android.graphics.Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.25f, 0.32f, 0.39f, 0.46f, 0.53f, 0.60f, 0.85f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                leakPaint.shader = shader
            }
            3 -> { // Orange Flare
                val radius = maxOf(w, h) * 0.5f
                val shader = android.graphics.RadialGradient(
                    w, 0f, radius,
                    intArrayOf(android.graphics.Color.argb((lightLeakOpacity * 240).toInt(), 255, 145, 0), android.graphics.Color.argb((lightLeakOpacity * 90).toInt(), 255, 61, 0), android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                leakPaint.shader = shader
            }
        }
        leakCanvas.drawRect(0f, 0f, w, h, leakPaint)
    }

    // 4. Vinheta
    if (vignette > 0f) {
        val w = dest.width.toFloat()
        val h = dest.height.toFloat()
        val radius = maxOf(w, h) * 0.8f
        val vignetteCanvas = android.graphics.Canvas(dest)
        val vignettePaint = android.graphics.Paint()
        val gradient = android.graphics.RadialGradient(
            w / 2f, h / 2f, radius,
            intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(((vignette / 100f) * 0.85f * 255f).toInt(), 0, 0, 0)),
            floatArrayOf(0.4f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
        vignettePaint.shader = gradient
        vignetteCanvas.drawRect(0f, 0f, w, h, vignettePaint)
    }

    // 5. Granulação
    if (grain > 0f) {
        val noiseBmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val rand = java.util.Random()
        for (x in 0 until 128) {
            for (y in 0 until 128) {
                val noise = rand.nextInt(256)
                noiseBmp.setPixel(x, y, android.graphics.Color.argb(255, noise, noise, noise))
            }
        }
        val grainCanvas = android.graphics.Canvas(dest)
        val grainPaint = android.graphics.Paint().apply {
            shader = android.graphics.BitmapShader(noiseBmp, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                blendMode = android.graphics.BlendMode.OVERLAY
            }
            alpha = ((grain / 100f) * 0.15f * 255f).toInt()
        }
        grainCanvas.drawRect(0f, 0f, dest.width.toFloat(), dest.height.toFloat(), grainPaint)
        noiseBmp.recycle()
    }

    // 6. Nitidez
    var finalDest = dest
    if (sharpness > 0f) {
        val sh = (sharpness / 100f) * 0.4f
        val w = finalDest.width
        val h = finalDest.height
        val pxs = IntArray(w * h)
        finalDest.getPixels(pxs, 0, w, 0, 0, w, h)

        val outPixels = IntArray(w * h)
        System.arraycopy(pxs, 0, outPixels, 0, pxs.size)

        for (y in 1 until h - 1) {
            val offset = y * w
            val offsetPrev = (y - 1) * w
            val offsetNext = (y + 1) * w
            for (x in 1 until w - 1) {
                val idx = offset + x

                val cColor = pxs[idx]
                val tColor = pxs[offsetPrev + x]
                val bColor = pxs[offsetNext + x]
                val lColor = pxs[idx - 1]
                val rColor = pxs[idx + 1]

                val cR = (cColor shr 16) and 0xFF
                val cG = (cColor shr 8) and 0xFF
                val cB = cColor and 0xFF
                val cA = (cColor shr 24) and 0xFF

                val tR = (tColor shr 16) and 0xFF
                val tG = (tColor shr 8) and 0xFF
                val tB = tColor and 0xFF

                val bR = (bColor shr 16) and 0xFF
                val bG = (bColor shr 8) and 0xFF
                val bB = bColor and 0xFF

                val lR = (lColor shr 16) and 0xFF
                val lG = (lColor shr 8) and 0xFF
                val lB = lColor and 0xFF

                val rR = (rColor shr 16) and 0xFF
                val rG = (rColor shr 8) and 0xFF
                val rB = rColor and 0xFF

                val centerWeight = 1f + 4f * sh
                val outR = (cR * centerWeight - (tR + bR + lR + rR) * sh).toInt().coerceIn(0, 255)
                val outG = (cG * centerWeight - (tG + bG + lG + rG) * sh).toInt().coerceIn(0, 255)
                val outB = (cB * centerWeight - (tB + bB + lB + rB) * sh).toInt().coerceIn(0, 255)

                outPixels[idx] = (cA shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
        val sharpBitmap = Bitmap.createBitmap(w, h, finalDest.config ?: Bitmap.Config.ARGB_8888)
        sharpBitmap.setPixels(outPixels, 0, w, 0, 0, w, h)
        finalDest.recycle()
        finalDest = sharpBitmap
    }

    // 7. Carimbo de Data
    if (dateStampEnabled) {
        val w = finalDest.width.toFloat()
        val h = finalDest.height.toFloat()
        val stampCanvas = android.graphics.Canvas(finalDest)
        
        val sdf = java.text.SimpleDateFormat("''yy MM dd", java.util.Locale.US)
        val dateText = sdf.format(java.util.Date())

        val stampPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(255, 90, 0)
            textSize = w * 0.038f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }

        val bounds = android.graphics.Rect()
        stampPaint.getTextBounds(dateText, 0, dateText.length, bounds)
        
        val x = w - bounds.width() - (w * 0.06f)
        val y = h - (h * 0.06f)

        stampPaint.setShadowLayer(w * 0.008f, 0f, 0f, android.graphics.Color.argb(160, 255, 60, 0))
        stampCanvas.drawText(dateText, x, y, stampPaint)
    }

    // 8. Moldura
    if (frameStyle > 0) {
        val w = finalDest.width
        val h = finalDest.height
        var framedBmp = finalDest
        
        when (frameStyle) {
            1 -> { // Polaroid
                val border = (minOf(w, h) * 0.07f).toInt()
                val bottomBorder = border * 3
                val newW = w + 2 * border
                val newH = h + border + bottomBorder

                val destFrame = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(destFrame)
                canvas.drawColor(android.graphics.Color.rgb(248, 249, 250))

                val shadowPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(30, 0, 0, 0)
                }
                canvas.drawRect(
                    (border - 2).toFloat(), (border - 2).toFloat(),
                    (border + w + 2).toFloat(), (border + h + 2).toFloat(),
                    shadowPaint
                )

                canvas.drawBitmap(finalDest, border.toFloat(), border.toFloat(), null)
                framedBmp = destFrame
            }
            2 -> { // Filme 35mm
                val border = (w * 0.12f).toInt()
                val newW = w + 2 * border
                val newH = h

                val destFrame = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(destFrame)
                canvas.drawColor(android.graphics.Color.BLACK)

                canvas.drawBitmap(finalDest, border.toFloat(), 0f, null)

                val holePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(30, 30, 30)
                    isAntiAlias = true
                }
                val borderHolePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(60, 60, 60)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 2f
                    isAntiAlias = true
                }

                val numHoles = 8
                val holeH = h / (numHoles * 2)
                val holeW = border * 0.35f
                val holeMarginY = holeH

                for (i in 0 until numHoles) {
                    val topY = (i * (holeH + holeMarginY) + holeMarginY / 2).toFloat()
                    val bottomY = topY + holeH

                    val leftX1 = (border * 0.35f).toFloat()
                    val leftX2 = leftX1 + holeW
                    canvas.drawRoundRect(leftX1, topY, leftX2, bottomY, 10f, 10f, holePaint)
                    canvas.drawRoundRect(leftX1, topY, leftX2, bottomY, 10f, 10f, borderHolePaint)

                    val rightX1 = (newW - border * 0.35f - holeW).toFloat()
                    val rightX2 = rightX1 + holeW
                    canvas.drawRoundRect(rightX1, topY, rightX2, bottomY, 10f, 10f, holePaint)
                    canvas.drawRoundRect(rightX1, topY, rightX2, bottomY, 10f, 10f, borderHolePaint)
                }

                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(255, 145, 0)
                    textSize = border * 0.22f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                    isAntiAlias = true
                }
                canvas.drawText("KODAK 400", border * 0.15f, h * 0.08f, textPaint)
                canvas.drawText("TX 5063", border * 0.18f, h * 0.94f, textPaint)
                canvas.drawText("▶ 13A", newW - border * 0.9f, h * 0.94f, textPaint)

                framedBmp = destFrame
            }
            3 -> { // Instax
                val border = (minOf(w, h) * 0.08f).toInt()
                val newW = w + 2 * border
                val newH = h + 2 * border

                val destFrame = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(destFrame)
                canvas.drawColor(android.graphics.Color.rgb(243, 243, 243))

                val rect = android.graphics.RectF(border.toFloat(), border.toFloat(), (border + w).toFloat(), (border + h).toFloat())
                val paint = android.graphics.Paint().apply { isAntiAlias = true }
                
                val roundPath = android.graphics.Path().apply {
                    addRoundRect(rect, 35f, 35f, android.graphics.Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(roundPath)
                canvas.drawBitmap(finalDest, border.toFloat(), border.toFloat(), paint)
                canvas.restore()

                val strokePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(15, 0, 0, 0)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 3f
                    isAntiAlias = true
                }
                canvas.drawRoundRect(rect, 35f, 35f, strokePaint)

                framedBmp = destFrame
            }
        }
        if (framedBmp !== finalDest) {
            finalDest.recycle()
            finalDest = framedBmp
        }
    }

    if (tiltShiftConfig.mode != com.example.aestheticcamera.ui.editor.model.TiltShiftMode.NONE) {
        val tsBmp = applyTiltShiftToBitmap(finalDest, tiltShiftConfig)
        if (tsBmp !== finalDest) {
            finalDest.recycle()
            finalDest = tsBmp
        }
    }

    finalDest
}

private fun applyTiltShiftToBitmap(src: Bitmap, config: com.example.aestheticcamera.ui.editor.model.TiltShiftConfig): Bitmap {
    if (config.mode == com.example.aestheticcamera.ui.editor.model.TiltShiftMode.NONE) return src
    val width = src.width
    val height = src.height

    // 1. Criar imagem desfocada (downsampling dinâmico baseado no blurStrength)
    val downsample = config.blurStrength.coerceIn(3f, 30f).toInt()
    val smallW = (width / downsample).coerceAtLeast(4)
    val smallH = (height / downsample).coerceAtLeast(4)
    val smallBmp = Bitmap.createScaledBitmap(src, smallW, smallH, true)
    val blurredBmp = Bitmap.createScaledBitmap(smallBmp, width, height, true)
    smallBmp.recycle()

    val resultBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(resultBmp)

    // Desenhar o fundo desfocado
    val paint = android.graphics.Paint().apply { isAntiAlias = true }
    canvas.drawBitmap(blurredBmp, 0f, 0f, paint)
    blurredBmp.recycle()

    // 2. Criar camada temporária para aplicar a máscara (Xfermode)
    val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

    // Desenhar o bitmap nítido original
    canvas.drawBitmap(src, 0f, 0f, paint)

    // 3. Desenhar a máscara de transparência (gradiente) usando DST_IN
    val maskPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }

    if (config.mode == com.example.aestheticcamera.ui.editor.model.TiltShiftMode.LINEAR) {
        val focalY = height * config.focusPosition
        val bandHalfH = height * (config.radius / 2f)
        val topBand = focalY - bandHalfH
        val bottomBand = focalY + bandHalfH

        val shader = android.graphics.LinearGradient(
            0f, topBand - bandHalfH, 0f, bottomBand + bandHalfH,
            intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.35f, 0.65f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        maskPaint.shader = shader
    } else {
        val centerX = width / 2f
        val centerY = height * config.focusPosition
        val radiusPx = (minOf(width, height) * config.radius).coerceAtLeast(10f)

        val shader = android.graphics.RadialGradient(
            centerX, centerY, radiusPx * 1.5f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.6f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        maskPaint.shader = shader
    }

    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
    canvas.restoreToCount(saveCount)

    return resultBmp
}

private suspend fun saveEditedImage(
    context: Context,
    sourceUri: Uri,
    colorMatrix: FloatArray,
    curvesLutsCombined: CurvesLuts,
    hslAdjustments: Map<HslColorChannel, HslAdjustment>,
    rotation: Float,
    cropRatio: CropRatio,
    vignette: Float,
    grain: Float,
    sharpness: Float,
    lightLeakStyle: Int = 0,
    lightLeakOpacity: Float = 0f,
    dateStampEnabled: Boolean = false,
    frameStyle: Int = 0,
    exportFormat: String = "JPEG",
    exportQuality: Int = 90,
    watermarkEnabled: Boolean = true,
    textOverlays: List<com.example.aestheticcamera.ui.editor.model.TextOverlayItem> = emptyList(),
    stickerOverlays: List<com.example.aestheticcamera.ui.editor.model.StickerOverlayItem> = emptyList(),
    tiltShiftConfig: com.example.aestheticcamera.ui.editor.model.TiltShiftConfig = com.example.aestheticcamera.ui.editor.model.TiltShiftConfig()
): Boolean = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        var inputStream: InputStream? = resolver.openInputStream(sourceUri)
        val srcBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (srcBitmap == null) return@withContext false

        // 1. Recortar se proporção foi selecionada
        val croppedBitmap = if (cropRatio != CropRatio.FREE) {
            cropBitmapToRatio(srcBitmap, cropRatio.w, cropRatio.h)
        } else {
            srcBitmap
        }

        // 2. Aplicar a pipeline no bitmap original de alta resolução
        var destBitmap = applyImagePipeline(
            src = croppedBitmap,
            colorMatrix = colorMatrix,
            curvesLuts = curvesLutsCombined,
            hslAdjustments = hslAdjustments,
            vignette = vignette,
            grain = grain,
            sharpness = sharpness,
            lightLeakStyle = lightLeakStyle,
            lightLeakOpacity = lightLeakOpacity,
            dateStampEnabled = dateStampEnabled,
            frameStyle = frameStyle,
            tiltShiftConfig = tiltShiftConfig
        )

        // Limpar bitmap temporário se foi criado um novo para corte
        if (croppedBitmap != srcBitmap) {
            croppedBitmap.recycle()
        }

        // 3. Aplicar Rotação se houver
        if (rotation != 0f) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotation)
            val rotatedBitmap = Bitmap.createBitmap(destBitmap, 0, 0, destBitmap.width, destBitmap.height, matrix, true)
            if (rotatedBitmap != destBitmap) {
                destBitmap.recycle()
                destBitmap = rotatedBitmap
            }
        }

        // 4. Desenhar Textos e Stickers sobrepostos no Bitmap final
        if (textOverlays.isNotEmpty() || stickerOverlays.isNotEmpty()) {
            val canvas = android.graphics.Canvas(destBitmap)

            // Desenhar Textos
            textOverlays.forEach { item ->
                canvas.save()
                val x = destBitmap.width * item.xPercent
                val y = destBitmap.height * item.yPercent
                canvas.rotate(item.rotation, x, y)

                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor(item.colorHex)
                    textSize = (destBitmap.width * (item.fontSizeSp / 360f)).coerceIn(24f, 120f)
                    isAntiAlias = true
                    alpha = (item.opacity * 255).toInt().coerceIn(0, 255)
                    typeface = when (item.fontStyle) {
                        "SCRIPT" -> android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.ITALIC)
                        "SERIF" -> android.graphics.Typeface.SERIF
                        "TYPEWRITER" -> android.graphics.Typeface.MONOSPACE
                        "BOLD" -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        else -> android.graphics.Typeface.DEFAULT
                    }
                    setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                }
                canvas.drawText(item.text, x, y, paint)
                canvas.restore()
            }

            // Desenhar Stickers
            stickerOverlays.forEach { item ->
                canvas.save()
                val x = destBitmap.width * item.xPercent
                val y = destBitmap.height * item.yPercent
                canvas.rotate(item.rotation, x, y)

                val paint = android.graphics.Paint().apply {
                    textSize = (destBitmap.width * 0.08f * item.scale).coerceIn(32f, 200f)
                    isAntiAlias = true
                }
                canvas.drawText(item.symbol, x, y, paint)
                canvas.restore()
            }
        }

        // 5. Desenhar Marca d'Água Retrô se ativado
        if (watermarkEnabled) {
            val canvas = android.graphics.Canvas(destBitmap)
            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#FFFFD54F")
                textSize = (destBitmap.width * 0.032f).coerceIn(28f, 56f)
                isAntiAlias = true
                isFakeBoldText = true
                setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
            }
            val watermarkText = "📷 AESTHETIC CAMERA"
            val margin = destBitmap.width * 0.035f
            val x = destBitmap.width - textPaint.measureText(watermarkText) - margin
            val y = destBitmap.height - margin
            canvas.drawText(watermarkText, x, y, textPaint)
        }

        // 5. Configurar Formato e Compressão
        val (compressFormat, mimeType, ext) = when (exportFormat.uppercase()) {
            "PNG" -> Triple(Bitmap.CompressFormat.PNG, "image/png", "png")
            "WEBP" -> Triple(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP, "image/webp", "webp")
            else -> Triple(Bitmap.CompressFormat.JPEG, "image/jpeg", "jpg")
        }

        // 6. Salvar no MediaStore
        val filename = "EDITED_${System.currentTimeMillis()}.$ext"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AestheticCamera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (outUri != null) {
            resolver.openOutputStream(outUri)?.use { out ->
                destBitmap.compress(compressFormat, exportQuality.coerceIn(70, 100), out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(outUri, contentValues, null, null)
            }
            destBitmap.recycle()
            return@withContext true
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext false
}

// Recorta o bitmap mantendo proporções
fun cropBitmapToRatio(src: Bitmap, ratioWidth: Int, ratioHeight: Int): Bitmap {
    val srcWidth = src.width
    val srcHeight = src.height

    val targetRatio = ratioWidth.toFloat() / ratioHeight.toFloat()
    val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()

    val cropWidth: Int
    val cropHeight: Int
    val xOffset: Int
    val yOffset: Int

    if (srcRatio > targetRatio) {
        cropHeight = srcHeight
        cropWidth = (srcHeight * targetRatio).toInt()
        xOffset = (srcWidth - cropWidth) / 2
        yOffset = 0
    } else {
        cropWidth = srcWidth
        cropHeight = (srcWidth / targetRatio).toInt()
        xOffset = 0
        yOffset = (srcHeight - cropHeight) / 2
    }

    return Bitmap.createBitmap(src, xOffset, yOffset, cropWidth, cropHeight)
}

// Interpolação e concatenação matricial para os ajustes de cor
private fun getCombinedMatrix(
    filter: FilterType,
    intensity: Float,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    warmth: Float,
    highlights: Float,
    shadows: Float,
    sharpness: Float
): FloatArray {
    val base = AestheticFilters.getInterpolatedMatrix(filter, intensity)
    val colorMatrix = android.graphics.ColorMatrix(base)

    // Brilho
    val bOffset = (brightness / 100f) * 0.35f * 255f
    val brightnessMatrix = android.graphics.ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, bOffset,
        0f, 1f, 0f, 0f, bOffset,
        0f, 0f, 1f, 0f, bOffset,
        0f, 0f, 0f, 1f, 0f
    ))
    colorMatrix.postConcat(brightnessMatrix)

    // Contraste + Simulação Nitidez
    val cScale = 1.0f + (contrast / 100f) * 0.5f + (sharpness / 100f) * 0.12f
    val cOffset = 128f * (1f - cScale)
    val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
        cScale, 0f, 0f, 0f, cOffset,
        0f, cScale, 0f, 0f, cOffset,
        0f, 0f, cScale, 0f, cOffset,
        0f, 0f, 0f, 1f, 0f
    ))
    colorMatrix.postConcat(contrastMatrix)

    // Saturação
    val sScale = 1.0f + (saturation / 100f)
    val satMatrix = android.graphics.ColorMatrix()
    satMatrix.setSaturation(sScale)
    colorMatrix.postConcat(satMatrix)

    // Temperatura
    val wFactor = (warmth / 100f) * 0.15f
    val warmthMatrix = android.graphics.ColorMatrix(floatArrayOf(
        1f + wFactor, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f - wFactor, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
    colorMatrix.postConcat(warmthMatrix)

    // Destaques e Sombras
    val sOffset = (shadows / 100f) * 15f
    val hFactor = (highlights / 100f) * 0.08f
    val hsMatrix = android.graphics.ColorMatrix(floatArrayOf(
        1f + hFactor, 0f, 0f, 0f, sOffset,
        0f, 1f + hFactor, 0f, 0f, sOffset,
        0f, 0f, 1f + hFactor, 0f, sOffset,
        0f, 0f, 0f, 1f, 0f
    ))
    colorMatrix.postConcat(hsMatrix)

    return colorMatrix.array
}

// Forma de corte de comparação vertical de tela dividida (Split-screen)
class RightSplitShape(private val splitFraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val splitX = size.width * splitFraction
        return Outline.Rectangle(
            Rect(
                left = splitX,
                top = 0f,
                right = size.width,
                bottom = size.height
            )
        )
    }
}

class LeftSplitShape(private val splitFraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val splitX = size.width * splitFraction
        return Outline.Rectangle(
            Rect(
                left = 0f,
                top = 0f,
                right = splitX,
                bottom = size.height
            )
        )
    }
}

// Componente: Slider de Ajuste Estilizado
@Composable
fun AdjustmentSlider(
    name: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isCentralZero: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name.uppercase(),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            val percentValue = value.toInt()
            val formattedValue = if (isCentralZero && percentValue > 0) "+$percentValue%" else "$percentValue%"
            Text(
                text = formattedValue,
                color = if (percentValue != 0) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFFD54F),
                    activeTrackColor = Color(0xFFFFD54F),
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (isCentralZero) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.5f))
                )
            }
        }
    }
}

private fun saveImageToGallery(context: Context, bitmap: Bitmap): Uri? {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AestheticCamera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val uri = resolver.insert(collection, contentValues) ?: return null
    try {
        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(uri.path ?: ""), arrayOf("image/jpeg"), null)
        return uri
    } catch (e: Exception) {
        e.printStackTrace()
        resolver.delete(uri, null, null)
        return null
    }
}

@Composable
fun TransparentRetroMolduraOverlay(
    frameStyle: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (frameStyle) {
            1 -> {
                // Polaroid Retro: Borda branca externa com base larga, centro 100% transparente
                val topBorder = h * 0.05f
                val sideBorder = w * 0.05f
                val bottomBorder = h * 0.16f

                drawRect(color = Color.White, size = androidx.compose.ui.geometry.Size(w, topBorder))
                drawRect(color = Color.White, size = androidx.compose.ui.geometry.Size(sideBorder, h))
                drawRect(color = Color.White, topLeft = Offset(w - sideBorder, 0f), size = androidx.compose.ui.geometry.Size(sideBorder, h))
                drawRect(color = Color.White, topLeft = Offset(0f, h - bottomBorder), size = androidx.compose.ui.geometry.Size(w, bottomBorder))
            }
            2 -> {
                // 35mm Film: Barras pretas topo e fundo com furinhos de filme, centro 100% transparente
                val barHeight = h * 0.08f
                drawRect(color = Color.Black, size = androidx.compose.ui.geometry.Size(w, barHeight))
                drawRect(color = Color.Black, topLeft = Offset(0f, h - barHeight), size = androidx.compose.ui.geometry.Size(w, barHeight))

                val holeW = w * 0.035f
                val holeH = barHeight * 0.45f
                val spacing = w * 0.05f
                var x = spacing
                while (x < w - holeW) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(x, barHeight * 0.28f),
                        size = androidx.compose.ui.geometry.Size(holeW, holeH),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f),
                        topLeft = Offset(x, h - barHeight + barHeight * 0.28f),
                        size = androidx.compose.ui.geometry.Size(holeW, holeH),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                    x += holeW + spacing
                }
            }
            3 -> {
                // Instax: Borda off-white suave com centro 100% transparente
                val border = w * 0.045f
                val offWhite = Color(0xFFFAF6EE)
                drawRect(color = offWhite, size = androidx.compose.ui.geometry.Size(w, border))
                drawRect(color = offWhite, size = androidx.compose.ui.geometry.Size(border, h))
                drawRect(color = offWhite, topLeft = Offset(w - border, 0f), size = androidx.compose.ui.geometry.Size(border, h))
                drawRect(color = offWhite, topLeft = Offset(0f, h - border * 2.2f), size = androidx.compose.ui.geometry.Size(w, border * 2.2f))
            }
        }
    }
}

@Composable
fun LightLeakVideoOverlay(
    style: Int,
    opacity: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.graphicsLayer { alpha = opacity.coerceIn(0f, 1f) }) {
        val w = size.width
        val h = size.height
        when (style) {
            1 -> {
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color(0xFFFFB300).copy(alpha = 0.7f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = w * 0.9f
                    )
                )
            }
            2 -> {
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF4081).copy(alpha = 0.6f),
                            Color(0xFFFFEB3B).copy(alpha = 0.4f),
                            Color(0xFF00E5FF).copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(w * 0.8f, h * 0.8f)
                    )
                )
            }
            3 -> {
                drawRect(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color(0xFFFF6D00).copy(alpha = 0.8f), Color.Transparent),
                        center = Offset(w, h),
                        radius = w * 1.1f
                    )
                )
            }
        }
    }
}

@Composable
fun VignetteVideoOverlay(
    intensity: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val alpha = (intensity * 0.85f).coerceIn(0f, 0.85f)
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = alpha)),
                center = Offset(w / 2f, h / 2f),
                radius = kotlin.math.hypot(w / 2f, h / 2f)
            )
        )
    }
}

@Composable
fun GrainVideoOverlay(
    intensity: Float,
    modifier: Modifier = Modifier
) {
    val alpha = (intensity * 0.14f).coerceIn(0f, 0.14f)
    if (alpha <= 0.005f) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val step = 4f
        val random = java.util.Random(42)

        var y = 0f
        while (y < h) {
            var x = 0f
            while (x < w) {
                if (random.nextFloat() > 0.55f) {
                    val isDark = random.nextBoolean()
                    drawRect(
                        color = if (isDark) Color.Black.copy(alpha = alpha) else Color.White.copy(alpha = alpha),
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(step, step)
                    )
                }
                x += step
            }
            y += step
        }
    }
}

@Composable
fun RetroDateStampVideoOverlay(
    modifier: Modifier = Modifier
) {
    Text(
        text = "'26  07  20",
        color = Color(0xFFFF8C00),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun ExportOptionsDialog(
    initialFormat: String = "JPEG",
    initialQuality: Float = 90f,
    initialWatermark: Boolean = true,
    onDismiss: () -> Unit,
    onConfirmExport: (format: String, quality: Float, watermark: Boolean) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var selectedQuality by remember { mutableFloatStateOf(initialQuality) }
    var watermarkEnabled by remember { mutableStateOf(initialWatermark) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚙️", fontSize = 18.sp)
                Text(
                    "Opções de Exportação",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Formato
                Column {
                    Text(
                        "FORMATO DE SAÍDA",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("JPEG", "PNG", "WEBP").forEach { fmt ->
                            val isSelected = selectedFormat == fmt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f))
                                    .clickable { selectedFormat = fmt }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    fmt,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Qualidade
                if (selectedFormat != "PNG") {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "QUALIDADE DE COMPRESSÃO",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "${selectedQuality.toInt()}%",
                                color = Color(0xFFFFD54F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = selectedQuality,
                            onValueChange = { selectedQuality = it },
                            valueRange = 70f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFFD54F),
                                activeTrackColor = Color(0xFFFFD54F),
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }
                }

                // Marca d'Água
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "MARCA D'ÁGUA RETRÔ",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "📷 AESTHETIC CAMERA no rodapé",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    }
                    Switch(
                        checked = watermarkEnabled,
                        onCheckedChange = { watermarkEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFD54F),
                            checkedTrackColor = Color(0xFFFFD54F).copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmExport(selectedFormat, selectedQuality, watermarkEnabled) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F))
            ) {
                Text("Exportar Mídia 💾", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
            }
        }
    )
}

