package com.example.aestheticcamera.ui.camera

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.RgbMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object VideoTranscoder {

    suspend fun transcodeWithFilter(
        context: Context,
        inputFile: File,
        filter: FilterType,
        intensity: Float,
        customColorMatrix4x4: FloatArray? = null,
        onSuccess: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.Default) {
            val processedFile = File(context.cacheDir, "processed_video_${System.currentTimeMillis()}.mp4")
            var savedUri: Uri? = null

            try {
                val matrixToApply = customColorMatrix4x4 ?: if (filter != FilterType.ORIGINAL && intensity > 0.01f) {
                    AestheticFilters.get4x4ColumnMajorMatrix(filter, intensity)
                } else null

                if (matrixToApply != null) {
                    try {
                        val mediaItem = MediaItem.fromUri(Uri.fromFile(inputFile))
                        val rgbMatrix = RgbMatrix { _, _ -> matrixToApply }
                        val effectsList = listOf<androidx.media3.common.Effect>(rgbMatrix)

                        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                            .setEffects(Effects(emptyList(), effectsList))
                            .build()

                        val transformer = Transformer.Builder(context)
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .build()

                        withContext(Dispatchers.Main) {
                            suspendCancellableCoroutine { continuation ->
                                val listener = object : Transformer.Listener {
                                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                        if (continuation.isActive) continuation.resume(Unit)
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(exportException)
                                        }
                                    }
                                }

                                transformer.addListener(listener)
                                transformer.start(editedMediaItem, processedFile.absolutePath)

                                continuation.invokeOnCancellation {
                                    try {
                                        transformer.cancel()
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        if (processedFile.exists() && processedFile.length() > 0) {
                            savedUri = saveVideoToGallery(context, processedFile)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("VideoTranscoder", "Falha na transcodificação Media3, aplicando fallback para vídeo original", e)
                    }
                }

                // Fallback: Se não salvou via transcodificação com filtro (ou se filtro era ORIGINAL), salva o vídeo gravado original
                if (savedUri == null && inputFile.exists() && inputFile.length() > 0) {
                    savedUri = saveVideoToGallery(context, inputFile)
                }

                val finalUri = savedUri
                withContext(Dispatchers.Main) {
                    if (finalUri != null) {
                        onSuccess(finalUri)
                    } else {
                        onError("Erro ao salvar vídeo na galeria")
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("VideoTranscoder", "Erro ao processar e salvar vídeo", e)
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Erro desconhecido ao salvar vídeo")
                }
            } finally {
                // Limpeza dos arquivos temporários de cache
                if (inputFile.absolutePath.startsWith(context.cacheDir.absolutePath) && inputFile.exists()) {
                    inputFile.delete()
                }
                if (processedFile.exists()) processedFile.delete()
            }
        }
    }

    private fun saveVideoToGallery(context: Context, file: File): Uri? {
        val resolver = context.contentResolver
        val filename = "VID_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PhotoFlow")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val videoUri = resolver.insert(collection, contentValues) ?: return null

        try {
            resolver.openOutputStream(videoUri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(videoUri, contentValues, null, null)
            }

            // Notificar o MediaScanner do sistema para tornar o vídeo visível imediatamente nas galerias nativas
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(videoUri.path ?: file.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
            } catch (e: Exception) {
                android.util.Log.w("VideoTranscoder", "Aviso ao escanear MediaScanner", e)
            }

            return videoUri
        } catch (e: Exception) {
            android.util.Log.e("VideoTranscoder", "Erro ao gravar no MediaStore", e)
            resolver.delete(videoUri, null, null)
            return null
        }
    }
}
