package com.example.aestheticcamera.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.aestheticcamera.ui.camera.AestheticFilters
import com.example.aestheticcamera.ui.camera.FilterType
import com.example.aestheticcamera.ui.camera.FilterThumbnailItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset

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

    // Estados dos Ajustes
    var activeFilter by remember { mutableStateOf(FilterType.ORIGINAL) }
    var filterIntensity by remember { mutableFloatStateOf(1.0f) }
    
    // Os 9 Ajustes Estendidos (valores de interface: -100 a 100 ou 0 a 100)
    var brightness by remember { mutableFloatStateOf(0f) }      // -100f a 100f
    var contrast by remember { mutableFloatStateOf(0f) }        // -100f a 100f
    var saturation by remember { mutableFloatStateOf(0f) }      // -100f a 100f
    var warmth by remember { mutableFloatStateOf(0f) }          // -100f a 100f
    var highlights by remember { mutableFloatStateOf(0f) }      // -100f a 100f
    var shadows by remember { mutableFloatStateOf(0f) }         // -100f a 100f
    var vignette by remember { mutableFloatStateOf(0f) }        // 0f a 100f
    var grain by remember { mutableFloatStateOf(0f) }           // 0f a 100f
    var sharpness by remember { mutableFloatStateOf(0f) }       // 0f a 100f

    var rotationAngle by remember { mutableFloatStateOf(0f) } // 0, 90, 180, 270
    var selectedCropRatio by remember { mutableStateOf(CropRatio.FREE) }

    // Estados do Comparador Antes/Depois
    var splitFraction by remember { mutableFloatStateOf(0f) }

    // Textura de Ruído de Grão Pré-calculada
    val noiseBitmap = remember {
        val width = 128
        val height = 128
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = java.util.Random()
        for (x in 0 until width) {
            for (y in 0 until height) {
                val noise = random.nextInt(256)
                bmp.setPixel(x, y, android.graphics.Color.argb(255, noise, noise, noise))
            }
        }
        bmp.asImageBitmap()
    }
    val noiseBrush = remember(noiseBitmap) {
        ShaderBrush(
            BitmapShader(
                noiseBitmap.asAndroidBitmap(),
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT
            )
        )
    }

    var isSaving by remember { mutableStateOf(false) }

    // Gerar Matriz de Cores Combinada com os 9 parâmetros de cor
    val combinedMatrix = remember(activeFilter, filterIntensity, brightness, contrast, saturation, warmth, highlights, shadows, sharpness) {
        getCombinedMatrix(activeFilter, filterIntensity, brightness, contrast, saturation, warmth, highlights, shadows, sharpness)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Preview da Imagem Principal
            val renderEffect = remember(combinedMatrix) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val colorMatrix = android.graphics.ColorMatrix(combinedMatrix)
                    RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(colorMatrix))
                } else {
                    null
                }
            }

            val currentAspectRatio = if (selectedCropRatio != CropRatio.FREE) {
                selectedCropRatio.w.toFloat() / selectedCropRatio.h.toFloat()
            } else {
                imageAspectRatio
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val imageModifier = Modifier
                    .graphicsLayer {
                        rotationZ = rotationAngle
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && renderEffect != null) {
                            this.renderEffect = renderEffect.asComposeRenderEffect()
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        if (vignette > 0f) {
                            val radius = maxOf(size.width, size.height) * 0.8f
                            val brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = (vignette / 100f) * 0.85f)),
                                center = Offset(size.width / 2, size.height / 2),
                                radius = radius
                            )
                            drawRect(brush = brush)
                        }
                        if (grain > 0f) {
                            drawRect(
                                brush = noiseBrush,
                                alpha = (grain / 100f) * 0.15f,
                                blendMode = BlendMode.Overlay
                            )
                        }
                    }

                BoxWithConstraints(
                    modifier = Modifier
                        .aspectRatio(currentAspectRatio)
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.5.dp, Color(0xFFFFD54F)), RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (splitFraction == 0f) {
                                        splitFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newX = (splitFraction * size.width) + dragAmount.x
                                    splitFraction = (newX / size.width).coerceIn(0f, 1f)
                                }
                            )
                        }
                ) {
                    val width = constraints.maxWidth.toFloat()
                    
                    // 1. Imagem Original (fundo, sem filtros/ajustes)
                    AsyncImage(
                        model = decodedUri,
                        contentDescription = "Original",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = rotationAngle },
                        contentScale = ContentScale.Crop
                    )
                    
                    // 2. Imagem Editada (por cima, recortada usando a RightSplitShape)
                    AsyncImage(
                        model = decodedUri,
                        contentDescription = "Editada",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RightSplitShape(splitFraction))
                            .then(imageModifier),
                        contentScale = ContentScale.Crop
                    )
                    
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
                }
            }

            // Botão flutuante removido (Comparador Antes/Depois agora é via arraste)

            // Barra de Ferramentas / Sliders e Filtros
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(bottom = 24.dp, top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Seletor de Abas de Ajuste
                var selectedTab by remember { mutableIntStateOf(0) } // 0: Filtros, 1: Ajustes, 2: Recortar/Girar

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFFFFD54F),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFFFFD54F)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Filtros", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Ajustes", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Recortar/Girar", fontSize = 12.sp) }
                    )
                }

                // 2. Conteúdo da Aba Selecionada
                when (selectedTab) {
                    0 -> {
                        // Aba de Filtros e Intensidade do Filtro
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (activeFilter != FilterType.ORIGINAL) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Intensidade", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                                    Slider(
                                        value = filterIntensity,
                                        onValueChange = { filterIntensity = it },
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
                                        isSelected = activeFilter == filter,
                                        onClick = { activeFilter = filter }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Aba de Ajustes Finos (Módulo 2.4)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp) // Limitar altura para caber na barra inferior do editor
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AdjustmentSlider(
                                name = "Brilho",
                                value = brightness,
                                onValueChange = { brightness = it },
                                valueRange = -100f..100f,
                                isCentralZero = true
                            )
                            AdjustmentSlider(
                                name = "Contraste",
                                value = contrast,
                                onValueChange = { contrast = it },
                                valueRange = -100f..100f,
                                isCentralZero = true
                            )
                            AdjustmentSlider(
                                name = "Saturação",
                                value = saturation,
                                onValueChange = { saturation = it },
                                valueRange = -100f..100f,
                                isCentralZero = true
                            )
                            AdjustmentSlider(
                                name = "Temperatura",
                                value = warmth,
                                onValueChange = { warmth = it },
                                valueRange = -100f..100f,
                                isCentralZero = true
                            )
                            AdjustmentSlider(
                                name = "Destaques",
                                value = highlights,
                                onValueChange = { highlights = it },
                                valueRange = -100f..100f,
                                isCentralZero = true
                            )
                            AdjustmentSlider(
                                name = "Sombras",
                                value = shadows,
                                onValueChange = { shadows = it },
                                valueRange = -100f..100f,
                                isCentralZero = true
                            )
                            AdjustmentSlider(
                                name = "Vinheta",
                                value = vignette,
                                onValueChange = { vignette = it },
                                valueRange = 0f..100f,
                                isCentralZero = false
                            )
                            AdjustmentSlider(
                                name = "Granulação",
                                value = grain,
                                onValueChange = { grain = it },
                                valueRange = 0f..100f,
                                isCentralZero = false
                            )
                            AdjustmentSlider(
                                name = "Nitidez",
                                value = sharpness,
                                onValueChange = { sharpness = it },
                                valueRange = 0f..100f,
                                isCentralZero = false
                            )
                        }
                    }
                    2 -> {
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
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(CropRatio.values()) { ratio ->
                                    val isSelected = selectedCropRatio == ratio
                                    val backgroundColor by animateColorAsState(
                                        if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f)
                                    )
                                    val textColor = if (isSelected) Color.Black else Color.White

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(backgroundColor)
                                            .clickable { selectedCropRatio = ratio }
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
                                onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                            ) {
                                Text("Girar 90°", color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Botões de Salvar e Voltar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onNavigateBack, enabled = !isSaving) {
                        Text("Descartar", color = Color.White.copy(alpha = 0.6f))
                    }

                    Button(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val success = saveEditedImage(
                                    context = context,
                                    sourceUri = decodedUri,
                                    colorMatrix = combinedMatrix,
                                    rotation = rotationAngle,
                                    cropRatio = selectedCropRatio,
                                    vignette = vignette,
                                    grain = grain,
                                    sharpness = sharpness
                                )
                                isSaving = false
                                if (success) {
                                    Toast.makeText(context, "Imagem salva na Galeria!", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                } else {
                                    Toast.makeText(context, "Erro ao salvar imagem editada", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
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
                            Text("Salvar na Galeria", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Lógica de manipulação de Bitmap em Background
private suspend fun saveEditedImage(
    context: Context,
    sourceUri: Uri,
    colorMatrix: FloatArray,
    rotation: Float,
    cropRatio: CropRatio,
    vignette: Float,
    grain: Float,
    sharpness: Float
): Boolean = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val inputStream: InputStream? = resolver.openInputStream(sourceUri)
        val srcBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (srcBitmap == null) return@withContext false

        // 1. Recortar se proporção foi selecionada
        val croppedBitmap = if (cropRatio != CropRatio.FREE) {
            cropBitmapToRatio(srcBitmap, cropRatio.w, cropRatio.h)
        } else {
            srcBitmap
        }

        // 2. Criar imagem destino e aplicar filtro/ajustes matriciais
        var destBitmap = Bitmap.createBitmap(croppedBitmap.width, croppedBitmap.height, croppedBitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(destBitmap)
        val paint = android.graphics.Paint()
        paint.colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(colorMatrix))
        canvas.drawBitmap(croppedBitmap, 0f, 0f, paint)

        // Limpar bitmap temporário se foi criado um novo para corte
        if (croppedBitmap != srcBitmap) {
            croppedBitmap.recycle()
        }

        // 3. Aplicar Vinheta (Vignette) no Bitmap salvo
        if (vignette > 0f) {
            val w = destBitmap.width.toFloat()
            val h = destBitmap.height.toFloat()
            val radius = Math.max(w, h) * 0.8f
            val vignettePaint = android.graphics.Paint()
            val gradient = android.graphics.RadialGradient(
                w / 2f, h / 2f, radius,
                intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(((vignette / 100f) * 0.85f * 255f).toInt(), 0, 0, 0)),
                floatArrayOf(0.4f, 1.0f),
                android.graphics.Shader.TileMode.CLAMP
            )
            vignettePaint.shader = gradient
            canvas.drawRect(0f, 0f, w, h, vignettePaint)
        }

        // 4. Aplicar Granulação (Grain) no Bitmap salvo
        if (grain > 0f) {
            val noiseBmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val rand = java.util.Random()
            for (x in 0 until 128) {
                for (y in 0 until 128) {
                    val noise = rand.nextInt(256)
                    noiseBmp.setPixel(x, y, android.graphics.Color.argb(255, noise, noise, noise))
                }
            }
            val grainPaint = android.graphics.Paint()
            grainPaint.shader = android.graphics.BitmapShader(noiseBmp, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                grainPaint.blendMode = android.graphics.BlendMode.OVERLAY
            }
            grainPaint.alpha = ((grain / 100f) * 0.15f * 255f).toInt()
            canvas.drawRect(0f, 0f, destBitmap.width.toFloat(), destBitmap.height.toFloat(), grainPaint)
            noiseBmp.recycle()
        }

        // 5. Aplicar Rotação se houver
        if (rotation != 0f) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotation)
            val rotatedBitmap = Bitmap.createBitmap(destBitmap, 0, 0, destBitmap.width, destBitmap.height, matrix, true)
            if (rotatedBitmap != destBitmap) {
                destBitmap.recycle()
                destBitmap = rotatedBitmap
            }
        }

        // 6. Aplicar Nitidez (Sharpness) no Bitmap salvo via Convolução 3x3 otimizada
        if (sharpness > 0f) {
            val sh = (sharpness / 100f) * 0.4f // escala (máximo 0.4f)
            val width = destBitmap.width
            val height = destBitmap.height
            val pixels = IntArray(width * height)
            destBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val outPixels = IntArray(width * height)
            System.arraycopy(pixels, 0, outPixels, 0, pixels.size)

            for (y in 1 until height - 1) {
                val offset = y * width
                val offsetPrev = (y - 1) * width
                val offsetNext = (y + 1) * width
                for (x in 1 until width - 1) {
                    val idx = offset + x

                    val cColor = pixels[idx]
                    val tColor = pixels[offsetPrev + x]
                    val bColor = pixels[offsetNext + x]
                    val lColor = pixels[idx - 1]
                    val rColor = pixels[idx + 1]

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

            val sharpBitmap = Bitmap.createBitmap(width, height, destBitmap.config ?: Bitmap.Config.ARGB_8888)
            sharpBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
            destBitmap.recycle()
            destBitmap = sharpBitmap
        }

        // 7. Salvar no MediaStore
        val filename = "EDITED_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AestheticCamera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (outUri != null) {
            resolver.openOutputStream(outUri)?.use { out ->
                destBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
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

// Recorta o bitmap de forma centralizada mantendo a proporção especificada
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

// Interpolação matemática e concatenação matricial para os ajustes de cor do editor
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
    // 1. Matriz de base interpolada do filtro
    val base = AestheticFilters.getInterpolatedMatrix(filter, intensity)
    val colorMatrix = android.graphics.ColorMatrix(base)

    // 2. Brilho (Brightness)
    val bOffset = (brightness / 100f) * 0.35f * 255f
    val brightnessMatrix = android.graphics.ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, bOffset,
        0f, 1f, 0f, 0f, bOffset,
        0f, 0f, 1f, 0f, bOffset,
        0f, 0f, 0f, 1f, 0f
    ))
    colorMatrix.postConcat(brightnessMatrix)

    // 3. Contraste (Contrast) + Simulação de Nitidez no Preview
    // Aumentamos levemente o contraste na visualização em tempo real para simular maior acuidade/nitidez
    val cScale = 1.0f + (contrast / 100f) * 0.5f + (sharpness / 100f) * 0.12f
    val cOffset = 128f * (1f - cScale)
    val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
        cScale, 0f, 0f, 0f, cOffset,
        0f, cScale, 0f, 0f, cOffset,
        0f, 0f, cScale, 0f, cOffset,
        0f, 0f, 0f, 1f, 0f
    ))
    colorMatrix.postConcat(contrastMatrix)

    // 4. Saturação (Saturation)
    val sScale = 1.0f + (saturation / 100f)
    val satMatrix = android.graphics.ColorMatrix()
    satMatrix.setSaturation(sScale)
    colorMatrix.postConcat(satMatrix)

    // 5. Temperatura (Warmth)
    val wFactor = (warmth / 100f) * 0.15f
    val warmthMatrix = android.graphics.ColorMatrix(floatArrayOf(
        1f + wFactor, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f - wFactor, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
    colorMatrix.postConcat(warmthMatrix)

    // 6. Destaques (Highlights) e Sombras (Shadows)
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

// Componente: Slider de Ajuste Estilizado (Módulo 2.4)
@Composable
fun AdjustmentSlider(
    name: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isCentralZero: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
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

        Spacer(modifier = Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Slider(
                value = value,
                onValueChange = onValueChange,
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
