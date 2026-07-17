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
            val photos = queryPhotos(context)
            _uiState.value = AlbumUiState.Success(photos)
        }
    }

    private suspend fun queryPhotos(context: Context): List<Uri> = withContext(Dispatchers.IO) {
        val photoList = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        // Usar BUCKET_DISPLAY_NAME para filtrar a pasta AestheticCamera de forma universal
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("AestheticCamera")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    photoList.add(contentUri)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        photoList
    }

    fun deletePhoto(context: Context, uri: Uri, onSecurityException: (android.content.IntentSender) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.delete(uri, null, null)
                    // Recarregar fotos após a exclusão
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
