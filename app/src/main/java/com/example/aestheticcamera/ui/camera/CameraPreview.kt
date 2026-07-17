package com.example.aestheticcamera.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import android.net.Uri
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aestheticcamera.ui.camera.components.CompositionOverlays
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.camera.core.FocusMeteringAction
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onNavigateToEditor: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Estado do painel retrátil de filtros/guias
    var showControlPanel by remember { mutableStateOf(false) }
    // Estado do popup de aspect ratio
    var showAspectRatioPopup by remember { mutableStateOf(false) }

    // Estados para animação do anel de foco (Módulo 2.2)
    val scope = rememberCoroutineScope()
    var focusPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val focusScale = remember { Animatable(1.6f) }
    val focusAlpha = remember { Animatable(0f) }
    var focusJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Permissão de Câmera
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                Toast.makeText(context, "Permissão de câmera é necessária para tirar fotos.", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Configuração do CameraX com suporte a Flip e Flash
    // ═══════════════════════════════════════════════════════════
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Referência da câmera ativa (para controle de zoom)
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    // Estado reativo para observar o zoom da câmera
    var zoomState by remember { mutableStateOf<androidx.camera.core.ZoomState?>(null) }
    LaunchedEffect(cameraRef) {
        cameraRef?.cameraInfo?.zoomState?.observe(lifecycleOwner) { state ->
            zoomState = state
        }
    }

    // Criar CameraSelector reativo com base no estado isFrontCamera
    val cameraSelector = remember(uiState.isFrontCamera) {
        if (uiState.isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA
    }

    // Rebind quando câmera muda (flip) ou permissão é concedida
    LaunchedEffect(hasCameraPermission, uiState.isFrontCamera) {
        if (hasCameraPermission) {
            val cameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                cameraRef = camera
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao iniciar a câmera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Aplicar flash mode ao ImageCapture
    LaunchedEffect(uiState.flashMode) {
        imageCapture.flashMode = when (uiState.flashMode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    // Aplicar zoom level quando muda
    LaunchedEffect(uiState.zoomLevel, cameraRef) {
        cameraRef?.cameraControl?.setZoomRatio(uiState.zoomLevel)
    }

    // Obter min/max zoom do dispositivo (ou valores default se emulador/nulo)
    val minZoom = zoomState?.minZoomRatio ?: 1f
    val maxZoom = zoomState?.maxZoomRatio ?: 1f

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black
    ) { paddingValues ->
        if (hasCameraPermission) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val screenWidthPx = constraints.maxWidth.toFloat()
                val screenHeightPx = constraints.maxHeight.toFloat()

                val renderEffect = remember(uiState.activeFilter, uiState.filterIntensity) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AestheticFilters.createRenderEffect(uiState.activeFilter, uiState.filterIntensity)
                    } else {
                        null
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // CÂMERA FULLSCREEN com overlay de Aspect Ratio
                // ═══════════════════════════════════════════════════════════
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(hasCameraPermission) {
                            if (!hasCameraPermission) return@pointerInput
                            detectTapGestures { offset ->
                                // 1. Fechar painéis se abertos
                                showControlPanel = false
                                showAspectRatioPopup = false

                                // 2. Disparar foco físico e fotometria no ponto tocado
                                try {
                                    val factory = previewView.meteringPointFactory
                                    val point = factory.createPoint(offset.x, offset.y)
                                    val action = androidx.camera.core.FocusMeteringAction.Builder(
                                        point,
                                        androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE
                                    )
                                        .setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()
                                    cameraRef?.cameraControl?.startFocusAndMetering(action)
                                } catch (e: Exception) {
                                    android.util.Log.e("AestheticCamera", "Erro ao focar câmera", e)
                                }

                                // 3. Animar o anel de foco (FocusRing)
                                focusJob?.cancel()
                                focusJob = scope.launch {
                                    focusPosition = offset
                                    focusScale.snapTo(1.6f)
                                    focusAlpha.snapTo(1f)
                                    focusScale.animateTo(
                                        targetValue = 1.0f,
                                        animationSpec = tween(
                                            durationMillis = 250,
                                            easing = androidx.compose.animation.core.LinearOutSlowInEasing
                                        )
                                    )
                                    kotlinx.coroutines.delay(2000)
                                    focusAlpha.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 250)
                                    )
                                    focusPosition = null
                                }
                            }
                        }
                ) {
                    // Preview com filtro de cor em tempo real
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && renderEffect != null) {
                                    this.renderEffect = renderEffect.asComposeRenderEffect()
                                }
                            }
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Overlay escuro para Aspect Ratio (quando não é FULL)
                    if (uiState.aspectRatio != AspectRatioMode.FULL) {
                        AspectRatioOverlay(
                            aspectRatio = uiState.aspectRatio,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Guias de composição sobre o preview
                    CompositionOverlays(
                        guideType = uiState.activeGuide,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Anel de Foco visual (FocusRing) amarelo com fade/escala
                    focusPosition?.let { pos ->
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = with(LocalDensity.current) { pos.x.toDp() } - 32.dp,
                                    y = with(LocalDensity.current) { pos.y.toDp() } - 32.dp
                                )
                                .size(64.dp)
                                .graphicsLayer {
                                    alpha = focusAlpha.value
                                    scaleX = focusScale.value
                                    scaleY = focusScale.value
                                }
                        ) {
                            // Círculo externo
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.5.dp, Color(0xFFFFD54F), CircleShape)
                            )
                            // Ponto central de confirmação
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Color(0xFFFFD54F), CircleShape)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // ÍCONES FLUTUANTES SUPERIORES
                // ═══════════════════════════════════════════════════════════

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão Voltar (←)
                    FloatingIconButton(
                        text = "←",
                        onClick = { onNavigateBack() }
                    )

                    // Controles centrais: Flash + Aspect Ratio
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Flash Toggle
                        FloatingIconButton(
                            text = when (uiState.flashMode) {
                                FlashMode.OFF -> "⚡✕"
                                FlashMode.ON -> "⚡"
                                FlashMode.AUTO -> "⚡A"
                            },
                            isActive = uiState.flashMode != FlashMode.OFF,
                            onClick = { viewModel.toggleFlash() }
                        )

                        // Aspect Ratio
                        Box {
                            FloatingIconButton(
                                text = uiState.aspectRatio.icon,
                                isActive = uiState.aspectRatio != AspectRatioMode.FULL,
                                onClick = { showAspectRatioPopup = !showAspectRatioPopup }
                            )

                            // Dropdown de Aspect Ratio
                            DropdownMenu(
                                expanded = showAspectRatioPopup,
                                onDismissRequest = { showAspectRatioPopup = false },
                                containerColor = Color(0xFF1E1E1E),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                AspectRatioMode.values().forEach { ratio ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = ratio.displayName,
                                                color = if (uiState.aspectRatio == ratio) Color(0xFFFFD54F) else Color.White,
                                                fontWeight = if (uiState.aspectRatio == ratio) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                        },
                                        onClick = {
                                            viewModel.setAspectRatio(ratio)
                                            showAspectRatioPopup = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Camera Flip (🔄)
                    val flipRotation by animateFloatAsState(
                        targetValue = if (uiState.isFrontCamera) 180f else 0f,
                        animationSpec = spring(stiffness = 200f)
                    )
                    FloatingIconButton(
                        text = "🔄",
                        onClick = { viewModel.flipCamera() },
                        rotationDegrees = flipRotation
                    )
                }

                // Indicador de Filtro Ativo — abaixo da row superior
                if (uiState.activeFilter != FilterType.ORIGINAL) {
                    Box(
                        modifier = Modifier
                            .padding(top = 56.dp)
                            .align(Alignment.TopCenter)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = uiState.activeFilter.displayName.uppercase(),
                            color = Color(0xFFFFD54F),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // LAYOUT INFERIOR (Zoom + Painel Retrátil + Botões)
                // ═══════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Painel Retrátil Glassmorphic de Filtros e Guias
                    AnimatedVisibility(
                        visible = showControlPanel,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                        ) + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .border(
                                    BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                                )
                                .padding(top = 16.dp, bottom = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Handle / indicador de arrasto no topo
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.2f))
                                )

                                // ── Guias de Composição ──
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "COMPOSIÇÃO",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 2.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(GuideType.values()) { guide ->
                                            GlassPill(
                                                text = guide.displayName,
                                                isSelected = uiState.activeGuide == guide,
                                                onClick = { viewModel.selectGuide(guide) }
                                            )
                                        }
                                    }
                                }

                                // ── Controle de Intensidade do Filtro (Módulo 2.3) ──
                                if (uiState.activeFilter != FilterType.ORIGINAL) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                         ) {
                                             Text(
                                                 text = "INTENSIDADE",
                                                 color = Color.White.copy(alpha = 0.35f),
                                                 fontSize = 9.sp,
                                                 fontWeight = FontWeight.SemiBold,
                                                 letterSpacing = 2.sp
                                             )
                                             Text(
                                                 text = "${(uiState.filterIntensity * 100).toInt()}%",
                                                 color = Color(0xFFFFD54F),
                                                 fontSize = 11.sp,
                                                 fontWeight = FontWeight.Bold
                                             )
                                         }
                                         Slider(
                                             value = uiState.filterIntensity,
                                             onValueChange = { viewModel.setFilterIntensity(it) },
                                             valueRange = 0f..1f,
                                             colors = SliderDefaults.colors(
                                                 thumbColor = Color(0xFFFFD54F),
                                                 activeTrackColor = Color(0xFFFFD54F),
                                                 inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                             ),
                                             modifier = Modifier.height(32.dp)
                                         )
                                     }
                                 }

                                 // ── Filtros Estéticos com Miniaturas (Módulo 2.3) ──
                                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                     Text(
                                         text = "FILTROS",
                                         color = Color.White.copy(alpha = 0.35f),
                                         fontSize = 9.sp,
                                         fontWeight = FontWeight.SemiBold,
                                         letterSpacing = 2.sp,
                                         modifier = Modifier.padding(bottom = 8.dp)
                                     )
                                     LazyRow(
                                         modifier = Modifier.fillMaxWidth(),
                                         contentPadding = PaddingValues(horizontal = 16.dp),
                                         horizontalArrangement = Arrangement.spacedBy(10.dp)
                                     ) {
                                         items(FilterType.values()) { filter ->
                                             FilterThumbnailItem(
                                                 filter = filter,
                                                 isSelected = uiState.activeFilter == filter,
                                                 onClick = { viewModel.selectFilter(filter) }
                                             )
                                         }
                                     }
                                 }
                            }
                        }
                    }

                    // ── Zoom Selector (.5x / 1x / 2x / 3x) ──
                    ZoomSelector(
                        currentZoom = uiState.zoomLevel,
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        isFrontCamera = uiState.isFrontCamera,
                        onZoomSelected = { viewModel.setZoomLevel(it) },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Console de Ações Principal (sempre visível, fundo transparente)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Esquerda: Botão 🎨 liquid/glassmorphic
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val buttonColor by animateColorAsState(
                                if (showControlPanel) Color(0xFFFFD54F) else Color.Black.copy(alpha = 0.35f)
                            )
                            val iconColor = if (showControlPanel) Color.Black else Color.White

                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(buttonColor)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (showControlPanel) Color.Transparent else Color.White.copy(alpha = 0.12f)
                                        ),
                                        CircleShape
                                    )
                                    .clickable { showControlPanel = !showControlPanel },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🎨",
                                    color = iconColor,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        // Centro: Botão de Captura
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val innerSize by animateDpAsState(if (uiState.isCapturing) 48.dp else 54.dp)
                                Box(
                                    modifier = Modifier
                                        .size(innerSize)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable(enabled = !uiState.isCapturing) {
                                            viewModel.capturePhoto(
                                                context = context,
                                                imageCapture = imageCapture,
                                                screenWidth = screenWidthPx,
                                                screenHeight = screenHeightPx
                                            ) { uri ->
                                                onNavigateToEditor(Uri.encode(uri.toString()))
                                            }
                                        }
                                )
                            }
                        }

                        // Direita: Espaçador (balanceia o botão de captura)
                        Box(modifier = Modifier.weight(1f))
                    }
                }

                // Indicador de Carregamento da Captura
                if (uiState.isCapturing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFFD54F))
                    }
                }
            }
        } else {
            // Placeholder quando permissão não está concedida
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A permissão da câmera é necessária", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Conceder Permissão")
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Componente: Floating Icon Button (ícones translúcidos superiores)
// ═══════════════════════════════════════════════════════════
@Composable
fun FloatingIconButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    rotationDegrees: Float = 0f
) {
    val bgColor = if (isActive) Color(0xFFFFD54F).copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.3f)
    val fgColor = if (isActive) Color.Black else Color.White

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), CircleShape)
            .clickable { onClick() }
            .graphicsLayer { rotationY = rotationDegrees },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fgColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════
// Componente: Zoom Selector (dinâmico baseado no hardware)
// ═══════════════════════════════════════════════════════════
@Composable
fun ZoomSelector(
    currentZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    isFrontCamera: Boolean,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Lista de zoom dinâmica apenas com 1x, 2x, 3x e 5x conforme o suporte físico da câmera
    val zoomLevels = remember(minZoom, maxZoom, isFrontCamera) {
        buildList {
            // Wide (1x) — sempre presente
            add(1.0f to "1")
            // 2x — se suportado
            if (maxZoom >= 2.0f) {
                add(2.0f to "2")
            }
            // 3x — se suportado
            if (maxZoom >= 3.0f) {
                add(3.0f to "3")
            }
            // 5x — se suportado (telephoto avançado)
            if (maxZoom >= 5.0f) {
                add(5.0f to "5")
            }
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(50))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        zoomLevels.forEach { (ratio, label) ->
            // Verificar se o zoom atual é "próximo" deste nível (tolerância de 0.05)
            val isSelected = (currentZoom - ratio).let { kotlin.math.abs(it) } < 0.05f
            val pillBg by animateColorAsState(
                if (isSelected) Color(0xFFFFD54F) else Color.Transparent,
                animationSpec = spring(stiffness = 400f)
            )
            val pillText = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f)

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(pillBg)
                    .clickable { onZoomSelected(ratio) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${label}x",
                    color = pillText,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Componente: Aspect Ratio Overlay (escurece áreas fora do crop)
// ═══════════════════════════════════════════════════════════
@Composable
fun AspectRatioOverlay(
    aspectRatio: AspectRatioMode,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.drawWithContent {
            drawContent()

            val screenW = size.width
            val screenH = size.height

            val (targetW, targetH) = when (aspectRatio) {
                AspectRatioMode.RATIO_4_3 -> {
                    val w = screenW
                    val h = w * 4f / 3f
                    if (h > screenH) {
                        val adjustedW = screenH * 3f / 4f
                        adjustedW to screenH
                    } else {
                        w to h
                    }
                }
                AspectRatioMode.RATIO_1_1 -> {
                    val side = minOf(screenW, screenH)
                    side to side
                }
                AspectRatioMode.RATIO_16_9 -> {
                    val w = screenW
                    val h = w * 16f / 9f
                    if (h > screenH) {
                        val adjustedW = screenH * 9f / 16f
                        adjustedW to screenH
                    } else {
                        w to h
                    }
                }
                else -> screenW to screenH
            }

            val offsetX = (screenW - targetW) / 2f
            val offsetY = (screenH - targetH) / 2f

            // Desenha o overlay escuro nas áreas fora do aspect ratio
            val visibleRect = Path().apply {
                addRect(Rect(Offset(offsetX, offsetY), Size(targetW, targetH)))
            }

            clipPath(visibleRect, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }

            // Borda fina no perímetro da área visível
            drawRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = Offset(offsetX, offsetY),
                size = Size(targetW, targetH),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }
    )
}

// ═══════════════════════════════════════════════════════════
// Componente reutilizável: Pill Glassmorphic
// ═══════════════════════════════════════════════════════════
@Composable
fun GlassPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.06f),
        animationSpec = spring(stiffness = 400f)
    )
    val borderColor = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.1f)
    val textColor = if (isSelected) Color.Black else Color.White

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════
// Componente: Filter Thumbnail Item (visualização premium com miniatura real)
// ═══════════════════════════════════════════════════════════
@Composable
fun FilterThumbnailItem(
    filter: FilterType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorFilter = remember(filter) {
        val matrix = AestheticFilters.getColorMatrix(filter)
        if (matrix != null) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(matrix)) else null
    }

    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thumbnail da imagem procedimental
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.15f)
                    ),
                    RoundedCornerShape(10.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Desenhar céu azul suave
                drawRect(
                    color = Color(0xFF3F51B5),
                    colorFilter = colorFilter
                )
                // Sol dourado
                drawCircle(
                    color = Color(0xFFFFEB3B),
                    radius = 8.dp.toPx(),
                    center = Offset(38.dp.toPx(), 14.dp.toPx()),
                    colorFilter = colorFilter
                )
                // Montanha traseira
                val pathRear = Path().apply {
                    moveTo(10.dp.toPx(), size.height)
                    lineTo(32.dp.toPx(), 18.dp.toPx())
                    lineTo(size.width + 10.dp.toPx(), size.height)
                    close()
                }
                drawPath(
                    path = pathRear,
                    color = Color(0xFF2E7D32),
                    colorFilter = colorFilter
                )
                // Montanha frontal
                val pathFront = Path().apply {
                    moveTo(-10.dp.toPx(), size.height)
                    lineTo(16.dp.toPx(), 28.dp.toPx())
                    lineTo(48.dp.toPx(), size.height)
                    close()
                }
                drawPath(
                    path = pathFront,
                    color = Color(0xFF4CAF50),
                    colorFilter = colorFilter
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Nome do Filtro
        Text(
            text = filter.displayName,
            color = if (isSelected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 11.sp,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}
