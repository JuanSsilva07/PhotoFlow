@file:OptIn(
    androidx.camera.video.ExperimentalHighSpeedVideo::class,
    androidx.camera.core.ExperimentalSessionConfig::class
)
package com.example.aestheticcamera.ui.camera

import com.example.aestheticcamera.ui.components.SpringButton

import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.aestheticcamera.R
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Paint
import android.graphics.Bitmap

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
    var activeTab by remember { mutableStateOf(0) }
    // Estado do popup de aspect ratio
    var showAspectRatioPopup by remember { mutableStateOf(false) }

    // Estados para animação do anel de foco (Módulo 2.2)
    val scope = rememberCoroutineScope()
    var focusPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val focusScale = remember { Animatable(1.6f) }
    val focusAlpha = remember { Animatable(0f) }
    var focusJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Permissão de Câmera e Áudio
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: hasCameraPermission
            hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
            if (permissions[Manifest.permission.CAMERA] == false) {
                Toast.makeText(context, "Permissão de câmera é necessária para tirar fotos.", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(key1 = true) {
        val reqs = mutableListOf<String>()
        if (!hasCameraPermission) reqs.add(Manifest.permission.CAMERA)
        if (!hasAudioPermission) reqs.add(Manifest.permission.RECORD_AUDIO)
        if (reqs.isNotEmpty()) {
            permissionLauncher.launch(reqs.toTypedArray())
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

    // Video Recording Setup (Sprint 4)
    val recorder = remember {
        androidx.camera.video.Recorder.Builder()
            .setQualitySelector(androidx.camera.video.QualitySelector.from(androidx.camera.video.Quality.HIGHEST))
            .build()
    }
    val videoCapture = remember {
        androidx.camera.video.VideoCapture.withOutput(recorder)
    }

    // Referência da câmera ativa (para controle de zoom)
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    var activeRecording by remember { mutableStateOf<androidx.camera.video.Recording?>(null) }
    var isSlowMotionSupported by remember { mutableStateOf(false) }
    var hfrQuality by remember { mutableStateOf<androidx.camera.video.Quality?>(null) }
    var hfrVideoCapture by remember { mutableStateOf<androidx.camera.video.VideoCapture<androidx.camera.video.Recorder>?>(null) }

    // Check slow motion capabilities when camera is active
    LaunchedEffect(cameraRef) {
        val cameraInfo = cameraRef?.cameraInfo
        if (cameraInfo != null) {
            val hfrCapabilities = androidx.camera.video.Recorder.getHighSpeedVideoCapabilities(cameraInfo)
            if (hfrCapabilities != null && hfrCapabilities.getSupportedQualities(androidx.camera.core.DynamicRange.SDR).isNotEmpty()) {
                isSlowMotionSupported = true
                hfrQuality = hfrCapabilities.getSupportedQualities(androidx.camera.core.DynamicRange.SDR).first()
            } else {
                isSlowMotionSupported = false
                hfrQuality = null
            }
        } else {
            isSlowMotionSupported = false
            hfrQuality = null
        }
    }

    LaunchedEffect(hfrQuality) {
        val quality = hfrQuality
        if (quality != null) {
            val hfrRecorder = androidx.camera.video.Recorder.Builder()
                .setQualitySelector(androidx.camera.video.QualitySelector.from(quality))
                .build()
            hfrVideoCapture = androidx.camera.video.VideoCapture.withOutput(hfrRecorder)
        } else {
            hfrVideoCapture = null
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Estado reativo para observar o zoom da cÃ¢mera
    var zoomState by remember { mutableStateOf<androidx.camera.core.ZoomState?>(null) }
    LaunchedEffect(cameraRef) {
        cameraRef?.cameraInfo?.zoomState?.observe(lifecycleOwner) { state ->
            zoomState = state
        }
    }

    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // Suporte a CameraX Extensions (Modo Noturno / HDR)
    // â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    var extensionsManager by remember { mutableStateOf<androidx.camera.extensions.ExtensionsManager?>(null) }
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            withContext(Dispatchers.IO) {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val extManager = androidx.camera.extensions.ExtensionsManager.getInstanceAsync(context, cameraProvider).get()
                    withContext(Dispatchers.Main) {
                        extensionsManager = extManager
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AestheticCamera", "Erro ao inicializar ExtensionsManager", e)
                }
            }
        }
    }

    val baseCameraSelector = remember(uiState.isFrontCamera) {
        if (uiState.isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
        else CameraSelector.DEFAULT_BACK_CAMERA
    }

    val cameraSelector = remember(baseCameraSelector, extensionsManager, uiState.isNightMode, uiState.isHdrMode) {
        val manager = extensionsManager
        if (manager == null) {
            baseCameraSelector
        } else {
            val mode = when {
                uiState.isNightMode && manager.isExtensionAvailable(baseCameraSelector, androidx.camera.extensions.ExtensionMode.NIGHT) -> {
                    androidx.camera.extensions.ExtensionMode.NIGHT
                }
                uiState.isHdrMode && manager.isExtensionAvailable(baseCameraSelector, androidx.camera.extensions.ExtensionMode.HDR) -> {
                    androidx.camera.extensions.ExtensionMode.HDR
                }
                else -> androidx.camera.extensions.ExtensionMode.NONE
            }
            if (mode != androidx.camera.extensions.ExtensionMode.NONE) {
                manager.getExtensionEnabledCameraSelector(baseCameraSelector, mode)
            } else {
                baseCameraSelector
            }
        }
    }

    val isNightSupported = remember(baseCameraSelector, extensionsManager) {
        extensionsManager?.isExtensionAvailable(baseCameraSelector, androidx.camera.extensions.ExtensionMode.NIGHT) == true
    }
    val isHdrSupported = remember(baseCameraSelector, extensionsManager) {
        extensionsManager?.isExtensionAvailable(baseCameraSelector, androidx.camera.extensions.ExtensionMode.HDR) == true
    }

    // Rebind quando cÃ¢mera muda (flip), permissÃ£o Ã© concedida, modo de cÃ¢mera muda ou slow motion Ã© alterado
    LaunchedEffect(hasCameraPermission, cameraSelector, uiState.cameraMode, uiState.isSlowMotionEnabled, hfrVideoCapture) {
        if (hasCameraPermission) {
            val cameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.unbindAll()
                
                val camera = if (uiState.cameraMode == CameraMode.VIDEO) {
                    val cameraInfo = cameraProvider.getCameraInfo(cameraSelector)
                    val hfrCapabilities = androidx.camera.video.Recorder.getHighSpeedVideoCapabilities(cameraInfo)
                    val currentHfrVideoCapture = hfrVideoCapture

                    if (uiState.isSlowMotionEnabled && hfrCapabilities != null && currentHfrVideoCapture != null) {
                        // ConfiguraÃ§Ã£o especial de alta velocidade para Slow Motion
                        val sessionConfig = androidx.camera.video.HighSpeedVideoSessionConfig.Builder(currentHfrVideoCapture)
                            .setPreview(preview)
                            .build()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            sessionConfig
                        )
                    } else {
                        // GravaÃ§Ã£o de vÃ­deo padrÃ£o
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            videoCapture
                        )
                    }
                } else {
                    // Modo Foto padrÃ£o
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                }
                
                cameraRef = camera
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (e: Exception) {
                android.util.Log.e("AestheticCamera", "Erro ao iniciar a cÃ¢mera", e)
                Toast.makeText(context, "Erro ao iniciar a cÃ¢mera: ${e.message}", Toast.LENGTH_LONG).show()
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

    // Aplicar configuraÃ§Ãµes Pro (ISO, Obturador, WB/Kelvin, EV, AF/AE Locks) via Camera2 Interop
    LaunchedEffect(
        cameraRef,
        uiState.manualIso,
        uiState.manualShutterSpeed,
        uiState.awbMode,
        uiState.kelvinTemp,
        uiState.exposureCompensation,
        uiState.isExposureLocked,
        uiState.isFocusLocked
    ) {
        val camera = cameraRef ?: return@LaunchedEffect
        
        // 1. CompensaÃ§Ã£o de ExposiÃ§Ã£o (EV)
        try {
            camera.cameraControl.setExposureCompensationIndex(uiState.exposureCompensation)
        } catch (e: Exception) {
            android.util.Log.e("AestheticCamera", "Erro ao ajustar EV", e)
        }

        // 2. OpÃ§Ãµes avanÃ§adas via Camera2 Camera Control
        try {
            val camera2Control = Camera2CameraControl.from(camera.cameraControl)
            val builder = CaptureRequestOptions.Builder()
            
            // Capture local properties for smart casting
            val manualIso = uiState.manualIso
            val manualShutterSpeed = uiState.manualShutterSpeed

            // Controle Manual de ISO e Velocidade do Obturador
            if (manualIso != null || manualShutterSpeed != null) {
                // Desligar AE automÃ¡tico para que o ISO e Obturador manual faÃ§am efeito
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                
                if (manualIso != null) {
                    builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manualIso.toInt())
                }
                if (manualShutterSpeed != null) {
                    builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manualShutterSpeed)
                }
            } else {
                // Modo AE automÃ¡tico ativo
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            
            // AE Lock (Bloqueio de ExposiÃ§Ã£o)
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_LOCK,
                uiState.isExposureLocked
            )
            
            // AF Lock (Bloqueio de Foco)
            if (uiState.isFocusLocked) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            
            // BalanÃ§o de Branco (AWB / Kelvin)
            when (uiState.awbMode) {
                AwbMode.AUTO -> {
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                }
                AwbMode.DAYLIGHT -> {
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
                }
                AwbMode.CLOUDY -> {
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
                }
                AwbMode.INCANDESCENT -> {
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
                }
                AwbMode.FLUORESCENT -> {
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
                }
                AwbMode.KELVIN -> {
                    // White Balance manual: desligar AWB automÃ¡tico e aplicar ganhos RGGB
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
                    builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
                    
                    // CÃ¡lculo de ganho de cor baseado em Kelvin (2000K a 10000K)
                    val factor = if (uiState.kelvinTemp < 6500) {
                        (6500f - uiState.kelvinTemp) / (6500f - 2000f)
                    } else {
                        (uiState.kelvinTemp - 6500f) / (10000f - 6500f)
                    }
                    
                    val r = if (uiState.kelvinTemp < 6500) 1.0f - 0.4f * factor else 1.0f + 1.2f * factor
                    val b = if (uiState.kelvinTemp < 6500) 1.0f + 1.2f * factor else 1.0f - 0.4f * factor
                    val gains = RggbChannelVector(r, 1.0f, 1.0f, b)
                    
                    builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
                }
            }
            
            camera2Control.captureRequestOptions = builder.build()
        } catch (e: Exception) {
            android.util.Log.e("AestheticCamera", "Erro ao aplicar opcoes do Camera2", e)
        }
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
                    // Preview com filtro de cor em tempo real e granulação analógica em tempo real
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

                        if (uiState.isRetroGrainEnabled) {
                            var grainOffset by remember { mutableStateOf(Offset.Zero) }
                            LaunchedEffect(Unit) {
                                val rand = java.util.Random()
                                while (true) {
                                    grainOffset = Offset(rand.nextInt(128).toFloat(), rand.nextInt(128).toFloat())
                                    delay(33) // ~30 FPS
                                }
                            }

                            val noiseBitmap = remember {
                                val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                                val rand = java.util.Random()
                                for (x in 0 until 128) {
                                    for (y in 0 until 128) {
                                        val n = rand.nextInt(256)
                                        bmp.setPixel(x, y, android.graphics.Color.argb(255, n, n, n))
                                    }
                                }
                                bmp.asImageBitmap()
                            }

                            Canvas(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val paint = Paint().apply {
                                    shader = ImageShader(
                                        image = noiseBitmap,
                                        tileModeX = TileMode.Repeated,
                                        tileModeY = TileMode.Repeated
                                    )
                                    alpha = 0.08f
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        blendMode = androidx.compose.ui.graphics.BlendMode.Overlay
                                    }
                                }
                                drawIntoCanvas { canvas ->
                                    canvas.withSave {
                                        canvas.translate(grainOffset.x, grainOffset.y)
                                        canvas.drawRect(
                                            androidx.compose.ui.geometry.Rect(
                                                offset = -grainOffset,
                                                size = androidx.compose.ui.geometry.Size(size.width + grainOffset.x, size.height + grainOffset.y)
                                            ),
                                            paint
                                        )
                                    }
                                }
                            }
                        }

                        // ── Overlay: Carimbo de Data Retrô ──────────────────
                        if (uiState.isAutoDateStampEnabled) {
                            val dateText = remember {
                                try {
                                    java.text.SimpleDateFormat("''yy MM dd", java.util.Locale.US)
                                        .format(java.util.Date())
                                } catch (e: Exception) { "'26 07 19" }
                            }
                            BoxWithConstraints(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val screenW = constraints.maxWidth.toFloat()
                                val screenH = constraints.maxHeight.toFloat()
                                val (w, h) = when (uiState.aspectRatio) {
                                    AspectRatioMode.RATIO_4_3 -> {
                                        val tw = screenW
                                        val th = tw * 4f / 3f
                                        if (th > screenH) (screenH * 3f / 4f) to screenH else tw to th
                                    }
                                    AspectRatioMode.RATIO_1_1 -> {
                                        val side = minOf(screenW, screenH)
                                        side to side
                                    }
                                    AspectRatioMode.RATIO_16_9 -> {
                                        val tw = screenW
                                        val th = tw * 16f / 9f
                                        if (th > screenH) (screenH * 9f / 16f) to screenH else tw to th
                                    }
                                    else -> screenW to screenH
                                }
                                val x0 = (screenW - w) / 2f
                                val y0 = (screenH - h) / 2f

                                val density = LocalDensity.current
                                val padEndDp = with(density) { (screenW - (x0 + w) + 18.dp.toPx()).toDp() }
                                val padBottomDp = with(density) { (screenH - (y0 + h) + 22.dp.toPx()).toDp() }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = padEndDp, bottom = padBottomDp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Text(
                                        text = dateText,
                                        color = Color(0xFFF97316),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }
                        }

                        // ── Overlay: Moldura Automática (Ajustada ao Aspect Ratio) ──
                        if (uiState.autoFrameStyle > 0) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val screenW = size.width
                                val screenH = size.height
                                val (w, h) = when (uiState.aspectRatio) {
                                    AspectRatioMode.RATIO_4_3 -> {
                                        val tw = screenW
                                        val th = tw * 4f / 3f
                                        if (th > screenH) (screenH * 3f / 4f) to screenH else tw to th
                                    }
                                    AspectRatioMode.RATIO_1_1 -> {
                                        val side = minOf(screenW, screenH)
                                        side to side
                                    }
                                    AspectRatioMode.RATIO_16_9 -> {
                                        val tw = screenW
                                        val th = tw * 16f / 9f
                                        if (th > screenH) (screenH * 9f / 16f) to screenH else tw to th
                                    }
                                    else -> screenW to screenH
                                }
                                val x0 = (screenW - w) / 2f
                                val y0 = (screenH - h) / 2f

                                when (uiState.autoFrameStyle) {
                                    1 -> { // Polaroid
                                        val borderPx = minOf(w, h) * 0.06f
                                        val bottomBorderPx = borderPx * 3.5f
                                        drawRect(
                                            color = Color(0xFFF8F9FA),
                                            topLeft = Offset(x0, y0),
                                            size = Size(w, borderPx)
                                        )
                                        drawRect(
                                            color = Color(0xFFF8F9FA),
                                            topLeft = Offset(x0, y0 + h - bottomBorderPx),
                                            size = Size(w, bottomBorderPx)
                                        )
                                        drawRect(
                                            color = Color(0xFFF8F9FA),
                                            topLeft = Offset(x0, y0),
                                            size = Size(borderPx, h)
                                        )
                                        drawRect(
                                            color = Color(0xFFF8F9FA),
                                            topLeft = Offset(x0 + w - borderPx, y0),
                                            size = Size(borderPx, h)
                                        )
                                    }
                                    2 -> { // Filme 35mm
                                        val borderPx = w * 0.1f
                                        // Faixa esquerda preta
                                        drawRect(
                                            color = Color(0xFF0A0A0A),
                                            topLeft = Offset(x0, y0),
                                            size = Size(borderPx, h)
                                        )
                                        // Faixa direita preta
                                        drawRect(
                                            color = Color(0xFF0A0A0A),
                                            topLeft = Offset(x0 + w - borderPx, y0),
                                            size = Size(borderPx, h)
                                        )
                                        // Furos do filme (sprockets)
                                        val numHoles = 9
                                        val holeH = h / (numHoles * 2f)
                                        val holeW = borderPx * 0.4f
                                        for (i in 0 until numHoles) {
                                            val topY = y0 + i * (holeH * 2f) + holeH * 0.5f
                                            val leftX1 = x0 + borderPx * 0.3f
                                            val rightX1 = x0 + w - borderPx * 0.3f - holeW
                                            drawRoundRect(
                                                color = Color(0xFF1E1E1E),
                                                topLeft = Offset(leftX1, topY),
                                                size = Size(holeW, holeH),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                            )
                                            drawRoundRect(
                                                color = Color(0xFF1E1E1E),
                                                topLeft = Offset(rightX1, topY),
                                                size = Size(holeW, holeH),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                            )
                                        }
                                    }
                                    3 -> { // Instax
                                        val borderPx = minOf(w, h) * 0.07f
                                        drawRect(
                                            color = Color(0xFFF3F3F3),
                                            topLeft = Offset(x0, y0),
                                            size = Size(w, borderPx)
                                        )
                                        drawRect(
                                            color = Color(0xFFF3F3F3),
                                            topLeft = Offset(x0, y0 + h - borderPx),
                                            size = Size(w, borderPx)
                                        )
                                        drawRect(
                                            color = Color(0xFFF3F3F3),
                                            topLeft = Offset(x0, y0),
                                            size = Size(borderPx, h)
                                        )
                                        drawRect(
                                            color = Color(0xFFF3F3F3),
                                            topLeft = Offset(x0 + w - borderPx, y0),
                                            size = Size(borderPx, h)
                                        )
                                    }
                                }
                            }
                        }

                        // ── Overlay: Vazamento de Luz ────────────────────────
                        if (uiState.isAutoLightLeakEnabled) {
                            val leakAlpha = remember { Animatable(0.25f) }
                            LaunchedEffect(uiState.autoLightLeakStyle) {
                                while (true) {
                                    leakAlpha.animateTo(0.45f, tween(1200))
                                    leakAlpha.animateTo(0.22f, tween(1400))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = when (uiState.autoLightLeakStyle) {
                                            1 -> androidx.compose.ui.graphics.Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0xFFFFA040).copy(alpha = leakAlpha.value),
                                                    Color.Transparent
                                                ),
                                                radius = 600f
                                            )
                                            2 -> androidx.compose.ui.graphics.Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFFFF4080).copy(alpha = leakAlpha.value * 0.7f),
                                                    Color(0xFF40CFFF).copy(alpha = leakAlpha.value * 0.7f)
                                                )
                                            )
                                            else -> androidx.compose.ui.graphics.Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0xFFFFFFE0).copy(alpha = leakAlpha.value * 1.2f),
                                                    Color.Transparent
                                                ),
                                                radius = 800f
                                            )
                                        }
                                    )
                            )
                        }
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

                    // ── Duração da Gravação de Vídeo (Sprint 4) ──
                    if (uiState.isRecordingVideo) {
                        val minutes = uiState.recordingDurationSeconds / 60
                        val seconds = uiState.recordingDurationSeconds % 60
                        val timeStr = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                    Text(
                                        text = timeStr,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

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

                                 // Seletor de Abas (Estilo / Pro / Vintage)
                                 Row(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .padding(horizontal = 24.dp, vertical = 2.dp),
                                     horizontalArrangement = Arrangement.spacedBy(20.dp),
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Text(
                                         text = "ESTILO",
                                         color = if (activeTab == 0) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.4f),
                                         fontSize = 11.sp,
                                         fontWeight = FontWeight.Bold,
                                         letterSpacing = 1.sp,
                                         modifier = Modifier
                                             .clickable { activeTab = 0 }
                                             .padding(vertical = 4.dp)
                                     )
                                     Text(
                                         text = "PRO CONTROLS",
                                         color = if (activeTab == 1) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.4f),
                                         fontSize = 11.sp,
                                         fontWeight = FontWeight.Bold,
                                         letterSpacing = 1.sp,
                                         modifier = Modifier
                                             .clickable { activeTab = 1 }
                                             .padding(vertical = 4.dp)
                                     )
                                     Text(
                                         text = "VINTAGE",
                                         color = if (activeTab == 2) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.4f),
                                         fontSize = 11.sp,
                                         fontWeight = FontWeight.Bold,
                                         letterSpacing = 1.sp,
                                         modifier = Modifier
                                             .clickable { activeTab = 2 }
                                             .padding(vertical = 4.dp)
                                     )
                                 }

                                 if (activeTab == 0) {
                                     // Guias de Composicao
                                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                         Text(
                                             text = "COMPOSI\u00C7\u00C3O",
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

                                     // Controle de Intensidade
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

                                     // Filtros Esteticos com Miniaturas
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
                                 } else if (activeTab == 1) {
                                     Column(
                                         modifier = Modifier
                                             .fillMaxWidth()
                                             .height(220.dp)
                                             .verticalScroll(rememberScrollState())
                                             .padding(horizontal = 16.dp),
                                         verticalArrangement = Arrangement.spacedBy(14.dp),
                                         horizontalAlignment = Alignment.CenterHorizontally
                                     ) {
                                          // 1. Toggles Rapidos (Timer, Burst, Locks, Noturno, HDR, Slow Mo)
                                          Row(
                                              modifier = Modifier.fillMaxWidth(),
                                              horizontalArrangement = Arrangement.spacedBy(8.dp)
                                          ) {
                                              if (uiState.cameraMode == CameraMode.VIDEO) {
                                                  // Slow Motion
                                                  GlassPill(
                                                      text = "\uD83D\uDC0C Slow Mo" + (if (isSlowMotionSupported) "" else " (N/S)"),
                                                      isSelected = uiState.isSlowMotionEnabled,
                                                      onClick = {
                                                          if (isSlowMotionSupported) {
                                                              viewModel.toggleSlowMotion()
                                                          }
                                                      },
                                                      modifier = Modifier.weight(1f)
                                                  )
                                                  Box(modifier = Modifier.weight(1f))
                                              } else {
                                                  val timerLabels = listOf("Off", "3s", "5s", "10s")
                                                  val currentTimerIdx = when (uiState.timerSeconds) {
                                                      3 -> 1
                                                      5 -> 2
                                                      10 -> 3
                                                      else -> 0
                                                  }
                                                  GlassPill(
                                                      text = "\u23F1\uFE0F " + timerLabels[currentTimerIdx],
                                                      isSelected = uiState.timerSeconds > 0,
                                                      onClick = {
                                                          val nextSeconds = when (uiState.timerSeconds) {
                                                              0 -> 3
                                                              3 -> 5
                                                              5 -> 10
                                                              else -> 0
                                                          }
                                                          viewModel.setTimerSeconds(nextSeconds)
                                                      },
                                                      modifier = Modifier.weight(1f)
                                                  )
                                                  GlassPill(
                                                      text = "\uD83D\uDCF8 Burst",
                                                      isSelected = uiState.isBurstMode,
                                                      onClick = { viewModel.toggleBurstMode() },
                                                      modifier = Modifier.weight(1f)
                                                  )
                                              }
                                          }

                                          if (uiState.cameraMode == CameraMode.PHOTO) {
                                              Row(
                                                  modifier = Modifier.fillMaxWidth(),
                                                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                                              ) {
                                                  // Night Mode (Noturno)
                                                  GlassPill(
                                                      text = "\uD83C\uDF19 Noite" + (if (isNightSupported) "" else " (N/S)"),
                                                      isSelected = uiState.isNightMode,
                                                      onClick = { if (isNightSupported) viewModel.toggleNightMode() },
                                                      modifier = Modifier.weight(1f)
                                                  )

                                                  // HDR Mode
                                                  GlassPill(
                                                      text = "\uD83C\uDF08 HDR" + (if (isHdrSupported) "" else " (N/S)"),
                                                      isSelected = uiState.isHdrMode,
                                                      onClick = { if (isHdrSupported) viewModel.toggleHdrMode() },
                                                      modifier = Modifier.weight(1f)
                                                  )
                                              }
                                          }

                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                                         ) {
                                             // AF Lock
                                             GlassPill(
                                                 text = "\uD83D\uDD12 AF Lock",
                                                 isSelected = uiState.isFocusLocked,
                                                 onClick = { viewModel.toggleFocusLock() },
                                                 modifier = Modifier.weight(1f)
                                             )

                                             // AE Lock
                                             GlassPill(
                                                 text = "\uD83D\uDD12 AE Lock",
                                                 isSelected = uiState.isExposureLocked,
                                                 onClick = { viewModel.toggleExposureLock() },
                                                 modifier = Modifier.weight(1f)
                                             )
                                         }

                                         // 2. ISO Selector
                                         Column(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalAlignment = Alignment.CenterHorizontally
                                         ) {
                                             Text(
                                                 text = "ISO: ${if (uiState.manualIso == null) "AUTO" else uiState.manualIso!!.toInt()}",
                                                 color = Color.White.copy(alpha = 0.5f),
                                                 fontSize = 9.sp,
                                                 fontWeight = FontWeight.SemiBold,
                                                 letterSpacing = 1.sp,
                                                 modifier = Modifier.padding(bottom = 6.dp)
                                             )
                                             val isoValues = listOf(null, 50f, 100f, 200f, 400f, 800f, 1600f, 3200f)
                                             LazyRow(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                                             ) {
                                                 items(isoValues) { iso ->
                                                     val label = if (iso == null) "AUTO" else iso.toInt().toString()
                                                     val isSelected = uiState.manualIso == iso
                                                     GlassPill(
                                                         text = label,
                                                         isSelected = isSelected,
                                                         onClick = { viewModel.setManualIso(iso) }
                                                     )
                                                 }
                                             }
                                         }

                                         // 3. Shutter Speed Selector
                                         Column(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalAlignment = Alignment.CenterHorizontally
                                         ) {
                                             Text(
                                                 text = "OBTURADOR: ${if (uiState.manualShutterSpeed == null) "AUTO" else getShutterDisplayName(uiState.manualShutterSpeed!!)}",
                                                 color = Color.White.copy(alpha = 0.5f),
                                                 fontSize = 9.sp,
                                                 fontWeight = FontWeight.SemiBold,
                                                 letterSpacing = 1.sp,
                                                 modifier = Modifier.padding(bottom = 6.dp)
                                             )
                                             val shutterValues = listOf(
                                                 null,
                                                 1000000L,     // 1/1000s
                                                 2000000L,     // 1/500s
                                                 4000000L,     // 1/250s
                                                 8000000L,     // 1/125s
                                                 16666666L,    // 1/60s
                                                 33333333L,    // 1/30s
                                                 66666666L,    // 1/15s
                                                 125000000L,   // 1/8s
                                                 250000000L,   // 1/4s
                                                 500000000L    // 1/2s
                                             )
                                             LazyRow(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                                             ) {
                                                 items(shutterValues) { shutter ->
                                                     val label = if (shutter == null) "AUTO" else getShutterDisplayName(shutter)
                                                     val isSelected = uiState.manualShutterSpeed == shutter
                                                     GlassPill(
                                                         text = label,
                                                         isSelected = isSelected,
                                                         onClick = { viewModel.setManualShutterSpeed(shutter) }
                                                     )
                                                 }
                                             }
                                         }

                                         // 4. White Balance Selector
                                         Column(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalAlignment = Alignment.CenterHorizontally
                                         ) {
                                             Text(
                                                 text = "BALAN\u00C7O DE BRANCO: ${uiState.awbMode.displayName}",
                                                 color = Color.White.copy(alpha = 0.5f),
                                                 fontSize = 9.sp,
                                                 fontWeight = FontWeight.SemiBold,
                                                 letterSpacing = 1.sp,
                                                 modifier = Modifier.padding(bottom = 6.dp)
                                             )
                                             LazyRow(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                                             ) {
                                                 items(AwbMode.values()) { awb ->
                                                     val isSelected = uiState.awbMode == awb
                                                     GlassPill(
                                                         text = awb.icon + " " + awb.displayName,
                                                         isSelected = isSelected,
                                                         onClick = { viewModel.setAwbMode(awb) }
                                                     )
                                                 }
                                             }
                                         }

                                         // Kelvin Slider if mode Kelvin
                                         if (uiState.awbMode == AwbMode.KELVIN) {
                                             Column(
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .padding(horizontal = 8.dp),
                                                 horizontalAlignment = Alignment.CenterHorizontally
                                             ) {
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     Text(
                                                         text = "TEMPERATURA (KELVIN)",
                                                         color = Color.White.copy(alpha = 0.35f),
                                                         fontSize = 9.sp,
                                                         fontWeight = FontWeight.SemiBold,
                                                         letterSpacing = 1.sp
                                                     )
                                                     Text(
                                                         text = "${uiState.kelvinTemp} K",
                                                         color = Color(0xFFFFD54F),
                                                         fontSize = 11.sp,
                                                         fontWeight = FontWeight.Bold
                                                     )
                                                 }
                                                 Slider(
                                                     value = uiState.kelvinTemp.toFloat(),
                                                     onValueChange = { viewModel.setKelvinTemp(it.toInt()) },
                                                     valueRange = 2000f..10000f,
                                                     colors = SliderDefaults.colors(
                                                         thumbColor = Color(0xFFFFD54F),
                                                         activeTrackColor = Color(0xFFFFD54F),
                                                         inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                                     ),
                                                     modifier = Modifier.height(32.dp)
                                                 )
                                             }
                                         }

                                         // 5. EV Slider (only if Auto exposure)
                                         if (uiState.manualIso == null && uiState.manualShutterSpeed == null) {
                                             Column(
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .padding(horizontal = 8.dp),
                                                 horizontalAlignment = Alignment.CenterHorizontally
                                             ) {
                                                 val evValue = uiState.exposureCompensation
                                                 val evText = if (evValue > 0) "+${evValue}" else "$evValue"
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     Text(
                                                         text = "EXPOSI\u00C7\u00C3O (EV)",
                                                         color = Color.White.copy(alpha = 0.35f),
                                                         fontSize = 9.sp,
                                                         fontWeight = FontWeight.SemiBold,
                                                         letterSpacing = 1.sp
                                                     )
                                                     Text(
                                                         text = evText,
                                                         color = Color(0xFFFFD54F),
                                                         fontSize = 11.sp,
                                                         fontWeight = FontWeight.Bold
                                                     )
                                                 }
                                                 Slider(
                                                     value = uiState.exposureCompensation.toFloat(),
                                                     onValueChange = { viewModel.setExposureCompensation(it.toInt()) },
                                                     valueRange = -6f..6f,
                                                     steps = 11,
                                                     colors = SliderDefaults.colors(
                                                         thumbColor = Color(0xFFFFD54F),
                                                         activeTrackColor = Color(0xFFFFD54F),
                                                         inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                                     ),
                                                     modifier = Modifier.height(32.dp)
                                                 )
                                             }
                                         }
                                      }
                                  } else {
                                      Column(
                                          modifier = Modifier
                                              .fillMaxWidth()
                                              .height(220.dp)
                                              .verticalScroll(rememberScrollState())
                                              .padding(horizontal = 16.dp),
                                          verticalArrangement = Arrangement.spacedBy(14.dp),
                                          horizontalAlignment = Alignment.CenterHorizontally
                                      ) {
                                          // 1. Toggles para Grain e Date Stamp
                                          Row(
                                              modifier = Modifier.fillMaxWidth(),
                                              horizontalArrangement = Arrangement.SpaceBetween,
                                              verticalAlignment = Alignment.CenterVertically
                                          ) {
                                              Row(
                                                  verticalAlignment = Alignment.CenterVertically,
                                                  modifier = Modifier.clickable { viewModel.toggleRetroGrain() }
                                              ) {
                                                  Checkbox(
                                                      checked = uiState.isRetroGrainEnabled,
                                                      onCheckedChange = { viewModel.toggleRetroGrain() },
                                                      colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFD54F))
                                                  )
                                                  Text("Granulação Analógica", color = Color.White, fontSize = 12.sp)
                                              }

                                              Row(
                                                  verticalAlignment = Alignment.CenterVertically,
                                                  modifier = Modifier.clickable { viewModel.toggleAutoDateStamp() }
                                              ) {
                                                  Checkbox(
                                                      checked = uiState.isAutoDateStampEnabled,
                                                      onCheckedChange = { viewModel.toggleAutoDateStamp() },
                                                      colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFD54F))
                                                  )
                                                  Text("Carimbo de Data", color = Color.White, fontSize = 12.sp)
                                              }
                                          }

                                          // 2. Vazamento de Luz (Auto)
                                          Column(modifier = Modifier.fillMaxWidth()) {
                                              Row(
                                                  modifier = Modifier.fillMaxWidth(),
                                                  verticalAlignment = Alignment.CenterVertically
                                              ) {
                                                  Row(
                                                      verticalAlignment = Alignment.CenterVertically,
                                                      modifier = Modifier.clickable { viewModel.toggleAutoLightLeak() }
                                                  ) {
                                                      Checkbox(
                                                          checked = uiState.isAutoLightLeakEnabled,
                                                          onCheckedChange = { viewModel.toggleAutoLightLeak() },
                                                          colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFD54F))
                                                      )
                                                      Text("Vazamento de Luz", color = Color.White, fontSize = 12.sp)
                                                  }
                                              }
                                              
                                              if (uiState.isAutoLightLeakEnabled) {
                                                  Row(
                                                      modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp),
                                                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                  ) {
                                                      listOf(1 to "Warm", 2 to "Rainbow", 3 to "Flare").forEach { (styleId, label) ->
                                                          val isSelected = uiState.autoLightLeakStyle == styleId
                                                          GlassPill(
                                                              text = label,
                                                              isSelected = isSelected,
                                                              onClick = { viewModel.setAutoLightLeakStyle(styleId) }
                                                          )
                                                      }
                                                  }
                                              }
                                          }

                                          // 3. Moldura Retro
                                          Column(modifier = Modifier.fillMaxWidth()) {
                                              Text(
                                                  text = "MOLDURA AUTOMÁTICA",
                                                  color = Color.White.copy(alpha = 0.35f),
                                                  fontSize = 9.sp,
                                                  fontWeight = FontWeight.SemiBold,
                                                  letterSpacing = 2.sp,
                                                  modifier = Modifier.padding(bottom = 6.dp)
                                              )
                                              Row(
                                                  modifier = Modifier.fillMaxWidth(),
                                                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                                              ) {
                                                  listOf(0 to "Nenhum", 1 to "Polaroid", 2 to "35mm Film", 3 to "Instax").forEach { (styleId, label) ->
                                                      val isSelected = uiState.autoFrameStyle == styleId
                                                      GlassPill(
                                                          text = label,
                                                          isSelected = isSelected,
                                                          onClick = { viewModel.setAutoFrameStyle(styleId) }
                                                      )
                                                  }
                                              }
                                          }
                                      }
                                  }
                                 }
                            }
                        }

                    // ── Seletor de Modo (FOTO / VÍDEO) ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FOTO",
                            color = if (uiState.cameraMode == CameraMode.PHOTO) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.setCameraMode(CameraMode.PHOTO) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "VÍDEO",
                            color = if (uiState.cameraMode == CameraMode.VIDEO) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.setCameraMode(CameraMode.VIDEO) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
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
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Esquerda: BotÃ£o ðŸŽ¨ liquid/glassmorphic
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
                                    text = "\uD83C\uDFA8",
                                    color = iconColor,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        // Centro: Botão de Captura / Gravação
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            val isRecording = uiState.isRecordingVideo
                            val onShutterClick: () -> Unit = {
                                if (uiState.cameraMode == CameraMode.VIDEO) {
                                    if (isRecording) {
                                        // Stop Recording
                                        activeRecording?.stop()
                                        activeRecording = null
                                    } else {
                                        // Start Recording
                                        val tempFile = java.io.File(context.cacheDir, "temp_video_" + System.currentTimeMillis() + ".mp4")
                                        val fileOutputOptions = androidx.camera.video.FileOutputOptions.Builder(tempFile).build()
                                        
                                        val currentVideoCapture = if (uiState.isSlowMotionEnabled && hfrVideoCapture != null) {
                                            hfrVideoCapture!!
                                        } else {
                                            videoCapture
                                        }

                                        try {
                                            val recording = currentVideoCapture.output
                                                .prepareRecording(context, fileOutputOptions)
                                                .apply {
                                                    if (hasAudioPermission) {
                                                        withAudioEnabled()
                                                    }
                                                }
                                                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                                                    when (recordEvent) {
                                                        is androidx.camera.video.VideoRecordEvent.Start -> {
                                                            viewModel.setRecordingVideo(true)
                                                        }
                                                        is androidx.camera.video.VideoRecordEvent.Finalize -> {
                                                            viewModel.setRecordingVideo(false)
                                                            if (recordEvent.hasError()) {
                                                                android.util.Log.e("AestheticCamera", "Erro ao gravar vídeo: ${recordEvent.error}")
                                                                Toast.makeText(context, "Erro ao gravar vídeo.", Toast.LENGTH_SHORT).show()
                                                                if (tempFile.exists()) tempFile.delete()
                                                            } else {
                                                                viewModel.startTranscoding(context, tempFile)
                                                            }
                                                        }
                                                    }
                                                }
                                            activeRecording = recording
                                        } catch (e: Exception) {
                                            android.util.Log.e("AestheticCamera", "Erro ao iniciar gravação", e)
                                            Toast.makeText(context, "Erro ao iniciar gravação: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    // Photo Capture (silencioso, sem toasts/logs invasivos na tela)
                                    viewModel.capturePhoto(
                                        context = context,
                                        imageCapture = imageCapture,
                                        screenWidth = screenWidthPx,
                                        screenHeight = screenHeightPx
                                    ) { uri ->
                                        // A miniatura no canto inferior direito atualiza de forma reativa
                                    }
                                }
                            }

                            SpringButton(
                                onClick = onShutterClick,
                                enabled = !uiState.isCapturing,
                                shape = CircleShape
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.35f)), CircleShape)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Animando tamanho interno
                                    val innerSize by animateDpAsState(
                                        targetValue = when {
                                            uiState.isCapturing -> 48.dp
                                            isRecording -> 36.dp
                                            else -> 54.dp
                                        }
                                    )
                                    
                                    // Animando raio do canto (círculo 50% vs quadrado arredondado 25%)
                                    val cornerPercent by animateIntAsState(
                                        targetValue = if (isRecording) 25 else 50
                                    )
                                    
                                    // Animando cor
                                    val buttonColor by animateColorAsState(
                                        targetValue = if (uiState.cameraMode == CameraMode.VIDEO) Color.Red else Color.White
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(innerSize)
                                            .clip(RoundedCornerShape(percent = cornerPercent))
                                            .background(buttonColor)
                                    )
                                }
                            }
                        }

                        // Direita: Thumbnail da última foto capturada com acesso direto
                        val lastUri = uiState.lastCapturedMediaUri
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .border(BorderStroke(1.5.dp, Color(0xFFFFD54F)), CircleShape)
                                    .clickable {
                                        if (lastUri != null) {
                                            onNavigateToEditor(Uri.encode(lastUri.toString()))
                                        } else {
                                            onNavigateBack()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (lastUri != null) {
                                    AsyncImage(
                                        model = lastUri,
                                        contentDescription = "Última Foto",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_album),
                                        contentDescription = "Álbum",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Indicador de Carregamento da Captura ou Contagem Regressiva do Timer
                if (uiState.isCapturing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (uiState.timerCountdown > 0) 0.3f else 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.timerCountdown > 0) {
                            Text(
                                text = "${uiState.timerCountdown}",
                                color = Color(0xFFFFD54F),
                                fontSize = 120.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFFFFD54F))
                                Text(
                                    text = if (uiState.cameraMode == CameraMode.VIDEO) "Aplicando filtros ao v\u00EDdeo..." else "Processando foto...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Placeholder quando permissao nao esta concedida
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A permiss\u00E3o da c\u00E2mera \u00E9 necess\u00E1ria", color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) {
                        Text("Conceder Permiss\u00E3o")
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


fun getShutterDisplayName(nanos: Long): String {
    return when (nanos) {
        1000000L -> "1/1000"
        2000000L -> "1/500"
        4000000L -> "1/250"
        8000000L -> "1/125"
        16666666L -> "1/60"
        33333333L -> "1/30"
        66666666L -> "1/15"
        125000000L -> "1/8"
        250000000L -> "1/4"
        500000000L -> "1/2"
        else -> "1/${1000000000L / nanos}"
    }
}
