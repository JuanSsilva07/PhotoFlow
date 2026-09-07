package com.example.aestheticcamera.ui.collage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aestheticcamera.ui.share.ShareHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CollageLayoutType(val title: String) {
    GRID_2X2("Grade 2x2"),
    DUAL_1X2("Lado a Lado (1x2)"),
    DUAL_2X1("Empilhado (2x1)"),
    EDITORIAL_3("Editorial (3)"),
    FILM_STRIP_35MM("Tira 35mm")
}

@Composable
fun CollageMakerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedLayout by remember { mutableStateOf(CollageLayoutType.GRID_2X2) }
    var spacingDp by remember { mutableFloatStateOf(8f) }
    var cornerRadiusDp by remember { mutableFloatStateOf(12f) }
    var backgroundColor by remember { mutableStateOf(Color.Black) }
    var isExporting by remember { mutableStateOf(false) }

    // Launcher para selecionar fotos da galeria
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris.take(9)
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
                IconButton(onClick = onNavigateBack, enabled = !isExporting) {
                    Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "🎞️ CRIADOR DE COLAGENS",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (selectedUris.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            if (selectedUris.isNotEmpty()) {
                                scope.launch {
                                    isExporting = true
                                    val savedUri = withContext(Dispatchers.IO) {
                                        saveCollageToGallery(context, selectedUris, selectedLayout, spacingDp, cornerRadiusDp, backgroundColor)
                                    }
                                    isExporting = false
                                    if (savedUri != null) {
                                        ShareHelper.shareMedia(context, savedUri)
                                    } else {
                                        Toast.makeText(context, "Erro ao preparar colagem para compartilhamento", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isExporting
                    ) {
                        Text("📲", fontSize = 18.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(44.dp))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Área principal da Colagem / Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .border(BorderStroke(1.5.dp, Color(0xFFFFD54F).copy(alpha = 0.6f)), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selectedUris.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text("🖼️", fontSize = 42.sp)
                        Text(
                            text = "Crie Colagens Incríveis",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Combine até 9 fotos em grades modernas, tiras de filme 35mm e estilos editoriais elegantes.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                "⊞ Grade 2x2",
                                "▥ Dupla",
                                "🎞️ Filme 35mm"
                            ).forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(tag, color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { photoPickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Selecionar Fotos (até 9)", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Exibir layout de colagem selecionado
                    CollagePreview(
                        uris = selectedUris,
                        layoutType = selectedLayout,
                        spacingDp = spacingDp,
                        cornerRadiusDp = cornerRadiusDp,
                        backgroundColor = backgroundColor
                    )
                }
            }

            if (selectedUris.isNotEmpty()) {
                // Botão para alterar fotos selecionadas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedUris.size} fotos selecionadas",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Alterar Fotos", color = Color(0xFFFFD54F), fontSize = 11.sp)
                    }
                }

                // Seletor de Layouts
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ESTILO DE LAYOUT",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(CollageLayoutType.values()) { layout ->
                            val isSelected = selectedLayout == layout
                            val bgCol by animateColorAsState(if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgCol)
                                    .clickable { selectedLayout = layout }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = layout.title,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Ajustes de Espaçamento e Cantos Arredondados
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Espaçamento", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Slider(
                            value = spacingDp,
                            onValueChange = { spacingDp = it },
                            valueRange = 0f..24f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F)),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Arredondamento", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Slider(
                            value = cornerRadiusDp,
                            onValueChange = { cornerRadiusDp = it },
                            valueRange = 0f..24f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFD54F), activeTrackColor = Color(0xFFFFD54F)),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                    }
                }

                // Cor do Fundo
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "COR DO FUNDO",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Color.Black to "Preto",
                            Color(0xFF1E1E1E) to "Grafite",
                            Color(0xFFF5E6D3) to "Creme Vintage",
                            Color(0xFFFFD54F) to "Amarelo Âmbar"
                        ).forEach { (color, name) ->
                            val isSelected = backgroundColor == color
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        BorderStroke(if (isSelected) 2.5.dp else 1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.3f)),
                                        CircleShape
                                    )
                                    .clickable { backgroundColor = color }
                            )
                        }
                    }
                }

                // Botão de Ação: Salvar Colagem na Galeria & Retornar à Tela Inicial
                Button(
                    onClick = {
                        scope.launch {
                            isExporting = true
                            val savedUri = withContext(Dispatchers.IO) {
                                saveCollageToGallery(context, selectedUris, selectedLayout, spacingDp, cornerRadiusDp, backgroundColor)
                            }
                            isExporting = false
                            if (savedUri != null) {
                                Toast.makeText(context, "Colagem salva com sucesso!", Toast.LENGTH_SHORT).show()
                                onNavigateBack() // Direciona para a tela inicial
                            } else {
                                Toast.makeText(context, "Erro ao salvar colagem", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Salvar Colagem na Galeria 💾",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollagePreview(
    uris: List<Uri>,
    layoutType: CollageLayoutType,
    spacingDp: Float,
    cornerRadiusDp: Float,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(spacingDp.dp),
        contentAlignment = Alignment.Center
    ) {
        when (layoutType) {
            CollageLayoutType.GRID_2X2 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)
                    ) {
                        CollageImageCell(uri = uris.getOrNull(0), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxHeight())
                        CollageImageCell(uri = uris.getOrNull(1), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)
                    ) {
                        CollageImageCell(uri = uris.getOrNull(2), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxHeight())
                        CollageImageCell(uri = uris.getOrNull(3), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            CollageLayoutType.DUAL_1X2 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)
                ) {
                    CollageImageCell(uri = uris.getOrNull(0), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxHeight())
                    CollageImageCell(uri = uris.getOrNull(1), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
            CollageLayoutType.DUAL_2X1 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
                ) {
                    CollageImageCell(uri = uris.getOrNull(0), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxWidth())
                    CollageImageCell(uri = uris.getOrNull(1), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
            CollageLayoutType.EDITORIAL_3 -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacingDp.dp)
                ) {
                    CollageImageCell(uri = uris.getOrNull(0), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1.5f).fillMaxHeight())
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
                    ) {
                        CollageImageCell(uri = uris.getOrNull(1), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxWidth())
                        CollageImageCell(uri = uris.getOrNull(2), cornerRadiusDp = cornerRadiusDp, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
            CollageLayoutType.FILM_STRIP_35MM -> {
                // Tira de filme 35mm com perfurações retrô laterais
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(spacingDp.dp)
                    ) {
                        for (i in 0..2) {
                            CollageImageCell(
                                uri = uris.getOrNull(i),
                                cornerRadiusDp = 4f,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CollageImageCell(
    uri: Uri?,
    cornerRadiusDp: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            val context = LocalContext.current
            var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(uri) {
                withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            bitmap = BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        Log.e("CollageMakerScreen", "Erro ao decodificar imagem para colagem", e)
                    }
                }
            }

            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Foto da Colagem",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Text("➕", fontSize = 16.sp, color = Color.White.copy(alpha = 0.3f))
        }
    }
}

private fun saveCollageToGallery(
    context: Context,
    uris: List<Uri>,
    layoutType: CollageLayoutType,
    spacingDp: Float,
    cornerRadiusDp: Float,
    backgroundColor: Color
): Uri? {
    val size = 1200
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // 1. Desenhar o Fundo
    if (layoutType == CollageLayoutType.FILM_STRIP_35MM) {
        canvas.drawColor(android.graphics.Color.BLACK)
    } else {
        canvas.drawColor(backgroundColor.toArgb())
    }

    val spacePx = (spacingDp * size / 360f).toInt()
    val radiusPx = (cornerRadiusDp * size / 360f)

    // Decodificar bitmaps das fotos selecionadas
    val photoBitmaps = uris.mapNotNull { uri ->
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun drawBitmapInRect(bmp: Bitmap?, rectF: RectF, cornerRadius: Float) {
        if (bmp == null) return
        val path = Path().apply {
            addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)

        // Center-crop scaling
        val scale = Math.max(rectF.width() / bmp.width, rectF.height() / bmp.height)
        val srcW = (rectF.width() / scale).toInt()
        val srcH = (rectF.height() / scale).toInt()
        val srcLeft = (bmp.width - srcW) / 2
        val srcTop = (bmp.height - srcH) / 2
        val srcRect = Rect(srcLeft, srcTop, srcLeft + srcW, srcTop + srcH)

        canvas.drawBitmap(bmp, srcRect, rectF, paint)
        canvas.restore()
    }

    when (layoutType) {
        CollageLayoutType.GRID_2X2 -> {
            val halfW = (size - spacePx * 3) / 2f
            val halfH = (size - spacePx * 3) / 2f

            val r0 = RectF(spacePx.toFloat(), spacePx.toFloat(), spacePx + halfW, spacePx + halfH)
            val r1 = RectF(spacePx * 2 + halfW, spacePx.toFloat(), size - spacePx.toFloat(), spacePx + halfH)
            val r2 = RectF(spacePx.toFloat(), spacePx * 2 + halfH, spacePx + halfW, size - spacePx.toFloat())
            val r3 = RectF(spacePx * 2 + halfW, spacePx * 2 + halfH, size - spacePx.toFloat(), size - spacePx.toFloat())

            drawBitmapInRect(photoBitmaps.getOrNull(0), r0, radiusPx)
            drawBitmapInRect(photoBitmaps.getOrNull(1), r1, radiusPx)
            drawBitmapInRect(photoBitmaps.getOrNull(2), r2, radiusPx)
            drawBitmapInRect(photoBitmaps.getOrNull(3), r3, radiusPx)
        }
        CollageLayoutType.DUAL_1X2 -> {
            val cellW = (size - spacePx * 3) / 2f
            val cellH = (size - spacePx * 2).toFloat()

            val r0 = RectF(spacePx.toFloat(), spacePx.toFloat(), spacePx + cellW, spacePx + cellH)
            val r1 = RectF(spacePx * 2 + cellW, spacePx.toFloat(), size - spacePx.toFloat(), spacePx + cellH)

            drawBitmapInRect(photoBitmaps.getOrNull(0), r0, radiusPx)
            drawBitmapInRect(photoBitmaps.getOrNull(1), r1, radiusPx)
        }
        CollageLayoutType.DUAL_2X1 -> {
            val cellW = (size - spacePx * 2).toFloat()
            val cellH = (size - spacePx * 3) / 2f

            val r0 = RectF(spacePx.toFloat(), spacePx.toFloat(), spacePx + cellW, spacePx + cellH)
            val r1 = RectF(spacePx.toFloat(), spacePx * 2 + cellH, spacePx + cellW, size - spacePx.toFloat())

            drawBitmapInRect(photoBitmaps.getOrNull(0), r0, radiusPx)
            drawBitmapInRect(photoBitmaps.getOrNull(1), r1, radiusPx)
        }
        CollageLayoutType.EDITORIAL_3 -> {
            val leftW = size * 0.58f
            val rightW = size - leftW - spacePx * 3
            val cellH = (size - spacePx * 3) / 2f

            val r0 = RectF(spacePx.toFloat(), spacePx.toFloat(), spacePx + leftW, size - spacePx.toFloat())
            val r1 = RectF(spacePx * 2 + leftW, spacePx.toFloat(), size - spacePx.toFloat(), spacePx + cellH)
            val r2 = RectF(spacePx * 2 + leftW, spacePx * 2 + cellH, size - spacePx.toFloat(), size - spacePx.toFloat())

            drawBitmapInRect(photoBitmaps.getOrNull(0), r0, radiusPx)
            drawBitmapInRect(photoBitmaps.getOrNull(1), r1, radiusPx)
            drawBitmapInRect(photoBitmaps.getOrNull(2), r2, radiusPx)
        }
        CollageLayoutType.FILM_STRIP_35MM -> {
            // Desenhar fita de filme 35mm
            val sideMargin = 90f
            val holeW = 32f
            val holeH = 22f

            // Furos de perfuração do filme (Sprocket Holes)
            val holePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#333333")
                isAntiAlias = true
            }
            var yHole = 30f
            while (yHole < size - 30f) {
                canvas.drawRoundRect(RectF(24f, yHole, 24f + holeW, yHole + holeH), 6f, 6f, holePaint)
                canvas.drawRoundRect(RectF(size - 24f - holeW, yHole, size - 24f, yHole + holeH), 6f, 6f, holePaint)
                yHole += 54f
            }

            // 3 quadros centrados
            val frameW = size - sideMargin * 2
            val frameH = (size - 160f) / 3f

            val textPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#FFFFD54F")
                textSize = 28f
                isFakeBoldText = true
            }

            for (i in 0..2) {
                val topY = 40f + i * (frameH + 30f)
                val rectF = RectF(sideMargin, topY, sideMargin + frameW, topY + frameH)
                drawBitmapInRect(photoBitmaps.getOrNull(i), rectF, 8f)
                canvas.drawText("0${i + 1}", sideMargin + 12f, topY + frameH - 12f, textPaint)
            }
        }
    }

    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "COLLAGE_${System.currentTimeMillis()}.jpg")
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
        Log.e("CollageMakerScreen", "Erro ao salvar colagem exportada", e)
        resolver.delete(uri, null, null)
        return null
    }
}
