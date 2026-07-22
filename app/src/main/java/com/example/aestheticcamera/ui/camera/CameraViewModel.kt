package com.example.aestheticcamera.ui.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.widget.Toast

enum class GuideType(val displayName: String) {
    NONE("Nenhum"),
    THIRDS("Grade 3x3"),
    GOLDEN("Espiral Áurea"),
    SYMMETRY("Simetria"),
    SILHOUETTE("Silhueta Retrato")
}

enum class FlashMode(val displayName: String, val icon: String) {
    OFF("Desligado", "⚡"),      // ícone com barra
    ON("Ligado", "⚡"),
    AUTO("Automático", "⚡A")
}

enum class AspectRatioMode(val displayName: String, val icon: String) {
    FULL("Full", "▣"),
    RATIO_4_3("4:3", "4:3"),
    RATIO_1_1("1:1", "1:1"),
    RATIO_16_9("16:9", "16:9")
}

enum class AwbMode(val displayName: String, val icon: String) {
    AUTO("Auto", "AWB"),
    DAYLIGHT("Sol", "☀️"),
    CLOUDY("Nublado", "☁️"),
    INCANDESCENT("Tungstênio", "💡"),
    FLUORESCENT("Fluorescente", "🔦"),
    KELVIN("Kelvin", "🌡️")
}

enum class CameraMode {
    PHOTO, VIDEO
}

data class CameraUiState(
    val activeFilter: FilterType = FilterType.ORIGINAL,
    val activeGuide: GuideType = GuideType.NONE,
    val isCapturing: Boolean = false,
    val captureResultUri: Uri? = null,
    val captureError: String? = null,
    // Novos controles de câmera (Módulo 2.1)
    val zoomLevel: Float = 1f,
    val flashMode: FlashMode = FlashMode.OFF,
    val isFrontCamera: Boolean = false,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FULL,
    // Módulo 2.3: Intensidade do Filtro
    val filterIntensity: Float = 1.0f,
    // Fase 3 - Sprint 1: Controles Pro
    val isProMode: Boolean = false,
    val manualIso: Float? = null, // null = Auto
    val manualShutterSpeed: Long? = null, // null = Auto, em nanossegundos
    val awbMode: AwbMode = AwbMode.AUTO,
    val kelvinTemp: Int = 5000,
    val exposureCompensation: Int = 0, // EV
    val isExposureLocked: Boolean = false,
    val isFocusLocked: Boolean = false,
    val timerSeconds: Int = 0, // 0 = Off, 3, 5, 10
    val isBurstMode: Boolean = false,
    val isNightMode: Boolean = false,
    val isHdrMode: Boolean = false,
    val timerCountdown: Int = 0,
    // Fase 3 - Sprint 3: Efeitos Retrô / Vintage
    val isRetroGrainEnabled: Boolean = false,
    val isAutoLightLeakEnabled: Boolean = false,
    val autoLightLeakStyle: Int = 1,
    val isAutoDateStampEnabled: Boolean = false,
    val autoFrameStyle: Int = 0,
    // Fase 3 - Sprint 4: Gravação de Vídeo
    val cameraMode: CameraMode = CameraMode.PHOTO,
    val isRecordingVideo: Boolean = false,
    val recordingDurationSeconds: Int = 0,
    val isSlowMotionEnabled: Boolean = false
)

class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun selectFilter(filter: FilterType) {
        _uiState.value = _uiState.value.copy(activeFilter = filter)
    }

    fun selectGuide(guide: GuideType) {
        _uiState.value = _uiState.value.copy(activeGuide = guide)
    }

    fun setFilterIntensity(intensity: Float) {
        _uiState.value = _uiState.value.copy(filterIntensity = intensity)
    }

    // ═══ Módulo 2.1: Controles de Câmera ═══

    fun setZoomLevel(zoom: Float) {
        _uiState.value = _uiState.value.copy(zoomLevel = zoom)
    }

    fun toggleFlash() {
        val next = when (_uiState.value.flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        _uiState.value = _uiState.value.copy(flashMode = next)
    }

    fun flipCamera() {
        _uiState.value = _uiState.value.copy(isFrontCamera = !_uiState.value.isFrontCamera)
    }

    fun setAspectRatio(ratio: AspectRatioMode) {
        _uiState.value = _uiState.value.copy(aspectRatio = ratio)
    }

    // ═══ Fase 3: Sprint 1 - Controles Pro ═══

    fun toggleProMode() {
        _uiState.value = _uiState.value.copy(isProMode = !_uiState.value.isProMode)
    }

    fun setManualIso(iso: Float?) {
        _uiState.value = _uiState.value.copy(manualIso = iso)
    }

    fun setManualShutterSpeed(shutter: Long?) {
        _uiState.value = _uiState.value.copy(manualShutterSpeed = shutter)
    }

    fun setAwbMode(mode: AwbMode) {
        _uiState.value = _uiState.value.copy(awbMode = mode)
    }

    fun setKelvinTemp(temp: Int) {
        _uiState.value = _uiState.value.copy(kelvinTemp = temp)
    }

    fun setExposureCompensation(value: Int) {
        _uiState.value = _uiState.value.copy(exposureCompensation = value)
    }

    fun toggleExposureLock() {
        _uiState.value = _uiState.value.copy(isExposureLocked = !_uiState.value.isExposureLocked)
    }

    fun toggleFocusLock() {
        _uiState.value = _uiState.value.copy(isFocusLocked = !_uiState.value.isFocusLocked)
    }

    fun setTimerSeconds(seconds: Int) {
        _uiState.value = _uiState.value.copy(timerSeconds = seconds)
    }

    fun toggleBurstMode() {
        _uiState.value = _uiState.value.copy(isBurstMode = !_uiState.value.isBurstMode)
    }

    fun toggleNightMode() {
        val currentNight = _uiState.value.isNightMode
        _uiState.value = _uiState.value.copy(
            isNightMode = !currentNight,
            isHdrMode = false // HDR e Night Mode são mutuamente exclusivos
        )
    }

    fun toggleHdrMode() {
        val currentHdr = _uiState.value.isHdrMode
        _uiState.value = _uiState.value.copy(
            isHdrMode = !currentHdr,
            isNightMode = false // HDR e Night Mode são mutuamente exclusivos
        )
    }

    fun toggleRetroGrain() {
        _uiState.value = _uiState.value.copy(isRetroGrainEnabled = !_uiState.value.isRetroGrainEnabled)
    }

    fun toggleAutoLightLeak() {
        _uiState.value = _uiState.value.copy(isAutoLightLeakEnabled = !_uiState.value.isAutoLightLeakEnabled)
    }

    fun setAutoLightLeakStyle(style: Int) {
        _uiState.value = _uiState.value.copy(autoLightLeakStyle = style)
    }

    fun toggleAutoDateStamp() {
        _uiState.value = _uiState.value.copy(isAutoDateStampEnabled = !_uiState.value.isAutoDateStampEnabled)
    }

    fun setAutoFrameStyle(style: Int) {
        _uiState.value = _uiState.value.copy(autoFrameStyle = style)
    }

    fun setCameraMode(mode: CameraMode) {
        _uiState.value = _uiState.value.copy(
            cameraMode = mode,
            isRecordingVideo = false,
            recordingDurationSeconds = 0
        )
    }

    fun toggleSlowMotion() {
        _uiState.value = _uiState.value.copy(
            isSlowMotionEnabled = !_uiState.value.isSlowMotionEnabled
        )
    }

    fun setRecordingVideo(recording: Boolean) {
        _uiState.value = _uiState.value.copy(isRecordingVideo = recording)
        if (recording) {
            startRecordingTimer()
        } else {
            stopRecordingTimer()
        }
    }

    private var videoTimerJob: kotlinx.coroutines.Job? = null

    private fun startRecordingTimer() {
        videoTimerJob?.cancel()
        videoTimerJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(recordingDurationSeconds = 0)
            while (true) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    recordingDurationSeconds = _uiState.value.recordingDurationSeconds + 1
                )
            }
        }
    }

    private fun stopRecordingTimer() {
        videoTimerJob?.cancel()
        videoTimerJob = null
    }

    // Recorta o bitmap de forma centralizada mantendo a proporção especificada
    // Usa Canvas para GARANTIR que um bitmap novo independente é criado
    private fun cropBitmapToRatio(src: Bitmap, ratioWidth: Int, ratioHeight: Int): Bitmap {
        val srcWidth = src.width
        val srcHeight = src.height

        val targetRatio = ratioWidth.toFloat() / ratioHeight.toFloat()
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()

        val cropWidth: Int
        val cropHeight: Int
        val xOffset: Int
        val yOffset: Int

        if (srcRatio > targetRatio) {
            // Origem é mais larga que a proporção desejada: recortar largura
            cropHeight = srcHeight
            cropWidth = (srcHeight * targetRatio).toInt()
            xOffset = (srcWidth - cropWidth) / 2
            yOffset = 0
        } else {
            // Origem é mais alta que a proporção desejada: recortar altura
            cropWidth = srcWidth
            cropHeight = (srcWidth / targetRatio).toInt()
            xOffset = 0
            yOffset = (srcHeight - cropHeight) / 2
        }

        // Criar bitmap novo explicitamente via Canvas (mais robusto que createBitmap com offsets)
        val result = Bitmap.createBitmap(cropWidth, cropHeight, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        // Desenhar o src com offset negativo para "recortar" a região central
        canvas.drawBitmap(src, -xOffset.toFloat(), -yOffset.toFloat(), null)
        return result
    }

    // Recorta o bitmap para corresponder exatamente à área visível do PreviewView (que usa FILL_CENTER)
    // e depois aplica o aspect ratio selecionado.
    private fun cropToMatchPreviewAndRatio(
        src: Bitmap,
        screenWidth: Float,
        screenHeight: Float,
        targetRatioMode: AspectRatioMode
    ): Bitmap {
        val srcWidth = src.width.toFloat()
        val srcHeight = src.height.toFloat()

        // 1. Recortar o bitmap original para a proporção da tela (área visível do preview)
        val screenRatio = screenWidth / screenHeight
        val srcRatio = srcWidth / srcHeight

        val previewMatchedBitmap = if (srcRatio > screenRatio) {
            // O sensor é mais largo que a tela: o preview corta as laterais (esquerda/direita)
            val cropHeight = srcHeight
            val cropWidth = srcHeight * screenRatio
            val xOffset = (srcWidth - cropWidth) / 2
            val yOffset = 0f
            
            val result = Bitmap.createBitmap(cropWidth.toInt(), cropHeight.toInt(), src.config ?: Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawBitmap(src, -xOffset, -yOffset, null)
            result
        } else {
            // O sensor é mais alto que a tela: o preview corta topo/base
            val cropWidth = srcWidth
            val cropHeight = srcWidth / screenRatio
            val xOffset = 0f
            val yOffset = (srcHeight - cropHeight) / 2
            
            val result = Bitmap.createBitmap(cropWidth.toInt(), cropHeight.toInt(), src.config ?: Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(result)
            canvas.drawBitmap(src, -xOffset, -yOffset, null)
            result
        }

        // 2. Aplicar o recorte do aspect ratio selecionado (1:1, 4:3, 16:9) sobre a área que era visível
        val finalBitmap = when (targetRatioMode) {
            AspectRatioMode.FULL -> previewMatchedBitmap
            AspectRatioMode.RATIO_4_3 -> {
                if (screenHeight > screenWidth) {
                    cropBitmapToRatio(previewMatchedBitmap, 3, 4)
                } else {
                    cropBitmapToRatio(previewMatchedBitmap, 4, 3)
                }
            }
            AspectRatioMode.RATIO_1_1 -> cropBitmapToRatio(previewMatchedBitmap, 1, 1)
            AspectRatioMode.RATIO_16_9 -> {
                if (screenHeight > screenWidth) {
                    cropBitmapToRatio(previewMatchedBitmap, 9, 16)
                } else {
                    cropBitmapToRatio(previewMatchedBitmap, 16, 9)
                }
            }
        }

        // Reciclar o intermediário se foi criado um novo e não é o mesmo do final
        if (previewMatchedBitmap !== src && previewMatchedBitmap !== finalBitmap) {
            previewMatchedBitmap.recycle()
        }

        return finalBitmap
    }

    private suspend fun takePictureSuspend(
        context: Context,
        imageCapture: ImageCapture
    ): ImageProxy = suspendCancellableCoroutine { continuation ->
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    if (continuation.isActive) {
                        continuation.resume(image)
                    } else {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
            }
        )
    }

    private var timerJob: kotlinx.coroutines.Job? = null

    fun capturePhoto(
        context: Context,
        imageCapture: ImageCapture,
        screenWidth: Float,
        screenHeight: Float,
        onCaptureSuccess: (Uri) -> Unit
    ) {
        // SNAPSHOT: Capturar aspect ratio, filtro, intensidade e modos ANTES da execução
        val captureAspectRatio = _uiState.value.aspectRatio
        val captureFilter = _uiState.value.activeFilter
        val captureFilterIntensity = _uiState.value.filterIntensity
        val captureBurst = _uiState.value.isBurstMode
        val timerSecs = _uiState.value.timerSeconds
        val isGrain = _uiState.value.isRetroGrainEnabled
        val isLightLeak = _uiState.value.isAutoLightLeakEnabled
        val lightLeakStyle = _uiState.value.autoLightLeakStyle
        val isDateStamp = _uiState.value.isAutoDateStampEnabled
        val frameStyle = _uiState.value.autoFrameStyle

        android.util.Log.d(
            "AestheticCamera",
            "capturePhoto() — AR=${captureAspectRatio.name}, Filtro=${captureFilter.name}, Burst=${captureBurst}, Timer=${timerSecs}s, Grain=${isGrain}, LightLeak=${isLightLeak}, DateStamp=${isDateStamp}, Frame=${frameStyle}"
        )

        _uiState.value = _uiState.value.copy(
            isCapturing = true,
            captureError = null,
            captureResultUri = null,
            timerCountdown = timerSecs
        )

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            try {
                // 1. Contagem regressiva do Timer (se aplicável)
                if (timerSecs > 0) {
                    for (i in timerSecs downTo 1) {
                        _uiState.value = _uiState.value.copy(timerCountdown = i)
                        delay(1000)
                    }
                    _uiState.value = _uiState.value.copy(timerCountdown = 0)
                }

                // 2. Captura única ou Sequencial (Burst Mode)
                val photoCount = if (captureBurst) 5 else 1
                var lastSavedUri: Uri? = null

                for (idx in 1..photoCount) {
                    val image = takePictureSuspend(context, imageCapture)
                    
                    val savedUri = withContext(Dispatchers.Default) {
                        try {
                            val bitmap = imageProxyToBitmap(image)
                            image.close()

                            android.util.Log.d("AestheticCamera", "Burst/Single $idx: Original ${bitmap.width}x${bitmap.height}")

                            val croppedBitmap = cropToMatchPreviewAndRatio(
                                bitmap,
                                screenWidth,
                                screenHeight,
                                captureAspectRatio
                            )

                            android.util.Log.d("AestheticCamera", "Burst/Single $idx: Cropped ${croppedBitmap.width}x${croppedBitmap.height}")

                            val filteredBitmap = applyFilter(croppedBitmap, captureFilter, captureFilterIntensity)

                            var processedBitmap = filteredBitmap

                            // 2.1. Vazamento de luz
                            if (isLightLeak) {
                                val next = applyLightLeak(processedBitmap, lightLeakStyle, 0.7f)
                                if (processedBitmap !== next && processedBitmap !== filteredBitmap && processedBitmap !== croppedBitmap && processedBitmap !== bitmap) {
                                    processedBitmap.recycle()
                                }
                                processedBitmap = next
                            }

                            // 2.2. Granulação
                            if (isGrain) {
                                val next = applyGrain(processedBitmap, 25f)
                                if (processedBitmap !== next && processedBitmap !== filteredBitmap && processedBitmap !== croppedBitmap && processedBitmap !== bitmap) {
                                    processedBitmap.recycle()
                                }
                                processedBitmap = next
                            }

                            // 2.3. Carimbo de data
                            if (isDateStamp) {
                                val next = applyDateStamp(processedBitmap)
                                if (processedBitmap !== next && processedBitmap !== filteredBitmap && processedBitmap !== croppedBitmap && processedBitmap !== bitmap) {
                                    processedBitmap.recycle()
                                }
                                processedBitmap = next
                            }

                            // 2.4. Moldura
                            if (frameStyle > 0) {
                                val next = applyFrame(processedBitmap, frameStyle)
                                if (processedBitmap !== next && processedBitmap !== filteredBitmap && processedBitmap !== croppedBitmap && processedBitmap !== bitmap) {
                                    processedBitmap.recycle()
                                }
                                processedBitmap = next
                            }

                            val uri = saveBitmapToGallery(context, processedBitmap)

                            // Reciclagem de intermediários
                            if (filteredBitmap !== croppedBitmap && filteredBitmap !== bitmap) {
                                filteredBitmap.recycle()
                            }
                            if (croppedBitmap !== bitmap) {
                                croppedBitmap.recycle()
                            }
                            if (processedBitmap !== bitmap) {
                                bitmap.recycle()
                            }
                            if (processedBitmap !== filteredBitmap && processedBitmap !== croppedBitmap && processedBitmap !== bitmap) {
                                processedBitmap.recycle()
                            }

                            uri
                        } catch (e: Exception) {
                            android.util.Log.e("AestheticCamera", "Error processing photo $idx", e)
                            image.close()
                            null
                        }
                    }

                    if (savedUri != null) {
                        lastSavedUri = savedUri
                        if (captureBurst) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Burst: Foto $idx de 5 salva!", Toast.LENGTH_SHORT).show()
                            }
                            delay(200) // Pequeno intervalo entre disparos no burst
                        }
                    }
                }

                if (lastSavedUri != null) {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        captureResultUri = lastSavedUri
                    )
                    withContext(Dispatchers.Main) {
                        onCaptureSuccess(lastSavedUri)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        captureError = "Erro ao salvar imagem na galeria"
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("AestheticCamera", "Capture error", e)
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    captureError = e.localizedMessage ?: "Erro desconhecido durante processamento"
                )
            }
        }
    }

    fun startTranscoding(context: Context, inputFile: java.io.File) {
        val captureFilter = _uiState.value.activeFilter
        val captureFilterIntensity = _uiState.value.filterIntensity

        _uiState.value = _uiState.value.copy(
            isCapturing = true,
            captureError = null,
            captureResultUri = null
        )

        viewModelScope.launch {
            VideoTranscoder.transcodeWithFilter(
                context = context,
                inputFile = inputFile,
                filter = captureFilter,
                intensity = captureFilterIntensity,
                onSuccess = { uri ->
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        captureResultUri = uri
                    )
                    Toast.makeText(context, "Vídeo salvo com sucesso na Galeria!", Toast.LENGTH_LONG).show()
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        captureError = error
                    )
                    Toast.makeText(context, "Erro ao salvar vídeo com filtro: $error", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        
        // Rotacionar de acordo com a rotação da imagem capturada
        val rotationDegrees = image.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun applyFilter(src: Bitmap, filter: FilterType, intensity: Float): Bitmap {
        if (filter == FilterType.ORIGINAL || intensity <= 0.01f) return src
        val matrix = AestheticFilters.getInterpolatedMatrix(filter, intensity)
        val bitmap = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint()
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(matrix))
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bitmap
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
        val filename = "AESTHETIC_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AestheticCamera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                return null
            }
        }
        return uri
    }

    // Aplicar Vazamento de Luz (Procedural com Canvas Gradients)
    private fun applyLightLeak(src: Bitmap, style: Int, opacity: Float): Bitmap {
        if (style == 0 || opacity <= 0.01f) return src
        val result = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val paint = android.graphics.Paint()
        
        when (style) {
            1 -> { // Soft Warm: linear da esquerda (laranja/vermelho) para a direita
                val shader = android.graphics.LinearGradient(
                    0f, h / 2f, w * 0.45f, h / 2f,
                    intArrayOf(android.graphics.Color.argb((opacity * 230).toInt(), 255, 61, 0), android.graphics.Color.argb((opacity * 140).toInt(), 255, 145, 0), android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0f, 0.4f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                paint.shader = shader
            }
            2 -> { // Rainbow: faixa diagonal
                val shader = android.graphics.LinearGradient(
                    0f, 0f, w * 0.7f, h * 0.7f,
                    intArrayOf(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.argb((opacity * 130).toInt(), 255, 0, 0),     // Vermelho
                        android.graphics.Color.argb((opacity * 130).toInt(), 255, 127, 0),   // Laranja
                        android.graphics.Color.argb((opacity * 130).toInt(), 255, 255, 0),   // Amarelo
                        android.graphics.Color.argb((opacity * 110).toInt(), 0, 255, 0),     // Verde
                        android.graphics.Color.argb((opacity * 110).toInt(), 0, 0, 255),     // Azul
                        android.graphics.Color.argb((opacity * 110).toInt(), 139, 0, 255),   // Violeta
                        android.graphics.Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.25f, 0.32f, 0.39f, 0.46f, 0.53f, 0.60f, 0.85f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                paint.shader = shader
            }
            3 -> { // Orange Flare: radial no canto superior direito
                val radius = maxOf(w, h) * 0.5f
                val shader = android.graphics.RadialGradient(
                    w, 0f, radius,
                    intArrayOf(android.graphics.Color.argb((opacity * 240).toInt(), 255, 145, 0), android.graphics.Color.argb((opacity * 90).toInt(), 255, 61, 0), android.graphics.Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1.0f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                paint.shader = shader
            }
        }
        canvas.drawRect(0f, 0f, w, h, paint)
        return result
    }

    // Aplicar Granulação no Bitmap
    private fun applyGrain(src: Bitmap, amount: Float): Bitmap {
        if (amount <= 0.01f) return src
        val result = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val noiseBmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val rand = java.util.Random()
        for (x in 0 until 128) {
            for (y in 0 until 128) {
                val noise = rand.nextInt(256)
                noiseBmp.setPixel(x, y, android.graphics.Color.argb(255, noise, noise, noise))
            }
        }
        val canvas = android.graphics.Canvas(result)
        val grainPaint = android.graphics.Paint()
        grainPaint.shader = android.graphics.BitmapShader(noiseBmp, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            grainPaint.blendMode = android.graphics.BlendMode.OVERLAY
        }
        grainPaint.alpha = ((amount / 100f) * 0.15f * 255f).toInt()
        canvas.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), grainPaint)
        noiseBmp.recycle()
        return result
    }

    // Aplicar Carimbo de Data no Canto Inferior Direito
    private fun applyDateStamp(src: Bitmap): Bitmap {
        val result = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val w = src.width.toFloat()
        val h = src.height.toFloat()

        // Formatar data atual: 'YY MM DD
        val sdf = java.text.SimpleDateFormat("''yy MM dd", java.util.Locale.US)
        val dateText = sdf.format(java.util.Date())

        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(255, 90, 0) // Laranja Retro
            textSize = w * 0.038f // Tamanho proporcional
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }

        // Medir dimensões do texto para posicionar no canto inferior direito
        val bounds = android.graphics.Rect()
        paint.getTextBounds(dateText, 0, dateText.length, bounds)
        
        val x = w - bounds.width() - (w * 0.06f)
        val y = h - (h * 0.06f)

        // Adicionar efeito de brilho (glow) de LED clássico desenhando uma sombra sutil por trás
        paint.setShadowLayer(w * 0.008f, 0f, 0f, android.graphics.Color.argb(160, 255, 60, 0))
        canvas.drawText(dateText, x, y, paint)
        return result
    }

    // Aplicar Molduras / Bordas Retro
    private fun applyFrame(src: Bitmap, frameStyle: Int): Bitmap {
        if (frameStyle == 0) return src
        val w = src.width
        val h = src.height

        when (frameStyle) {
            1 -> { // Polaroid: Moldura branca com margem inferior alongada
                val border = (minOf(w, h) * 0.07f).toInt()
                val bottomBorder = border * 3
                val newW = w + 2 * border
                val newH = h + border + bottomBorder

                val dest = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(dest)
                canvas.drawColor(android.graphics.Color.rgb(248, 249, 250)) // Off-White Polaroid

                // Sombra interna sutil sob a imagem
                val shadowPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(30, 0, 0, 0)
                }
                canvas.drawRect(
                    (border - 2).toFloat(), (border - 2).toFloat(),
                    (border + w + 2).toFloat(), (border + h + 2).toFloat(),
                    shadowPaint
                )

                canvas.drawBitmap(src, border.toFloat(), border.toFloat(), null)
                return dest
            }
            2 -> { // Filme 35mm: Moldura preta com sprocket holes laterais
                val border = (w * 0.12f).toInt() // Borda lateral preta
                val newW = w + 2 * border
                val newH = h

                val dest = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(dest)
                canvas.drawColor(android.graphics.Color.BLACK)

                // Desenhar a imagem centralizada
                canvas.drawBitmap(src, border.toFloat(), 0f, null)

                // Desenhar furos do filme (sprocket holes)
                val holePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(30, 30, 30) // Fundo escuro do furo
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

                    // Furo da esquerda
                    val leftX1 = (border * 0.35f).toFloat()
                    val leftX2 = leftX1 + holeW
                    canvas.drawRoundRect(leftX1, topY, leftX2, bottomY, 10f, 10f, holePaint)
                    canvas.drawRoundRect(leftX1, topY, leftX2, bottomY, 10f, 10f, borderHolePaint)

                    // Furo da direita
                    val rightX1 = (newW - border * 0.35f - holeW).toFloat()
                    val rightX2 = rightX1 + holeW
                    canvas.drawRoundRect(rightX1, topY, rightX2, bottomY, 10f, 10f, holePaint)
                    canvas.drawRoundRect(rightX1, topY, rightX2, bottomY, 10f, 10f, borderHolePaint)
                }

                // Inscrições estéticas em laranja estilo Kodak/Fuji
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(255, 145, 0)
                    textSize = border * 0.22f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                    isAntiAlias = true
                }
                canvas.drawText("KODAK 400", border * 0.15f, h * 0.08f, textPaint)
                canvas.drawText("TX 5063", border * 0.18f, h * 0.94f, textPaint)
                canvas.drawText("▶ 13A", newW - border * 0.9f, h * 0.94f, textPaint)

                return dest
            }
            3 -> { // Instax: Moldura macia branca com cantos arredondados
                val border = (minOf(w, h) * 0.08f).toInt()
                val newW = w + 2 * border
                val newH = h + 2 * border

                val dest = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(dest)
                canvas.drawColor(android.graphics.Color.rgb(243, 243, 243)) // Soft White Instax

                // Desenhar a imagem com cantos arredondados
                val rect = android.graphics.RectF(border.toFloat(), border.toFloat(), (border + w).toFloat(), (border + h).toFloat())
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                }
                
                // Recortar cantos da imagem
                val roundPath = android.graphics.Path().apply {
                    addRoundRect(rect, 35f, 35f, android.graphics.Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(roundPath)
                canvas.drawBitmap(src, border.toFloat(), border.toFloat(), paint)
                canvas.restore()

                // Desenhar borda de contorno interna fina
                val strokePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(15, 0, 0, 0)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 3f
                    isAntiAlias = true
                }
                canvas.drawRoundRect(rect, 35f, 35f, strokePaint)

                return dest
            }
        }
        return src
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
    }
}
