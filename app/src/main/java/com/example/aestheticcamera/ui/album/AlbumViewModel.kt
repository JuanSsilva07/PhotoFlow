package com.example.aestheticcamera.ui.album

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaItemData(
    val uri: Uri,
    val dateAdded: Long,
    val isVideo: Boolean
)

sealed interface AlbumUiState {
    object Loading : AlbumUiState
    data class Success(val photos: List<Uri>) : AlbumUiState
}

class AlbumViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val uiState: StateFlow<AlbumUiState> = _uiState

    fun loadPhotos(context: Context) {
        viewModelScope.launch {
            _uiState.value = AlbumUiState.Loading
            val media = queryMedia(context)
            _uiState.value = AlbumUiState.Success(media)
        }
    }

    private suspend fun queryMedia(context: Context): List<Uri> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItemData>()

        // 1. Consultar Fotos (MediaStore.Images)
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf("AestheticCamera")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    mediaList.add(MediaItemData(contentUri, dateAdded, isVideo = false))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Consultar Vídeos (MediaStore.Video)
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf("AestheticCamera")
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    mediaList.add(MediaItemData(contentUri, dateAdded, isVideo = true))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: Se a pasta específica AestheticCamera não retornar mídias (ex: Android 13+ isolado), consultar mídia geral recente
        if (mediaList.isEmpty()) {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_ADDED
                )
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 50"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val dateAdded = cursor.getLong(dateCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        mediaList.add(MediaItemData(contentUri, dateAdded, isVideo = false))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Combinar e ordenar todas as fotos e vídeos por data mais recente
        mediaList.sortByDescending { it.dateAdded }
        mediaList.map { it.uri }
    }

    fun deletePhoto(context: Context, uri: Uri, onSecurityException: (android.content.IntentSender) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.delete(uri, null, null)
                    loadPhotos(context)
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                        onSecurityException(e.userAction.actionIntent.intentSender)
                    } else {
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
