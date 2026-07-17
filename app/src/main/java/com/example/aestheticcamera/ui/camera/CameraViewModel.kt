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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    val filterIntensity: Float = 1.0f
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

    fun capturePhoto(
        context: Context,
        imageCapture: ImageCapture,
        screenWidth: Float,
        screenHeight: Float,
        onCaptureSuccess: (Uri) -> Unit
    ) {
        // SNAPSHOT: Capturar aspect ratio, filtro e intensidade ANTES da captura assíncrona
        val captureAspectRatio = _uiState.value.aspectRatio
        val captureFilter = _uiState.value.activeFilter
        val captureFilterIntensity = _uiState.value.filterIntensity

        android.util.Log.d(
            "AestheticCamera",
            "capturePhoto() chamado — AR=${captureAspectRatio.name}, Filtro=${captureFilter.name}, Intensidade=${captureFilterIntensity}"
        )

        _uiState.value = _uiState.value.copy(isCapturing = true, captureError = null, captureResultUri = null)

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    cameraExecutor.execute {
                        try {
                            val bitmap = imageProxyToBitmap(image)
                            image.close()

                            android.util.Log.d("AestheticCamera", "Original bitmap: ${bitmap.width}x${bitmap.height}")

                            // 1. Recortar alinhando com a área visível do preview e aspect ratio
                            val croppedBitmap = cropToMatchPreviewAndRatio(
                                bitmap,
                                screenWidth,
                                screenHeight,
                                captureAspectRatio
                            )

                            android.util.Log.d("AestheticCamera", "Cropped bitmap: ${croppedBitmap.width}x${croppedBitmap.height}")

                            // Mostrar Toast com as dimensões para verificação
                            ContextCompat.getMainExecutor(context).execute {
                                android.widget.Toast.makeText(
                                    context,
                                    "Foto salva: ${croppedBitmap.width}x${croppedBitmap.height} (${captureAspectRatio.displayName})",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }

                            // 2. Aplicar filtro com a intensidade correspondente
                            val filteredBitmap = applyFilter(croppedBitmap, captureFilter, captureFilterIntensity)

                            // Se criamos um novo bitmap no crop, reciclar o original
                            if (croppedBitmap !== bitmap) {
                                bitmap.recycle()
                            }

                            // Salvar na galeria
                            val savedUri = saveBitmapToGallery(context, filteredBitmap)

                            // Se criamos um novo bitmap no filtro, reciclar o intermediário
                            if (filteredBitmap !== croppedBitmap && croppedBitmap !== bitmap) {
                                croppedBitmap.recycle()
                            }

                            if (savedUri != null) {
                                _uiState.value = _uiState.value.copy(
                                    isCapturing = false,
                                    captureResultUri = savedUri
                                )
                                ContextCompat.getMainExecutor(context).execute {
                                    onCaptureSuccess(savedUri)
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
                            image.close()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    _uiState.value = _uiState.value.copy(
                        isCapturing = false,
                        captureError = exception.localizedMessage ?: "Erro na captura da foto"
                    )
                }
            }
        )
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

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
    }
}
