package com.example.aestheticcamera.ui.album

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaItemData(
    val uri: Uri,
    val dateAdded: Long,
    val isVideo: Boolean,
    val displayName: String = "",
    val size: Long = 0L
)

enum class AlbumSortOption(val label: String) {
    DATE_DESC("Mais Recentes"),
    DATE_ASC("Mais Antigas"),
    NAME_ASC("Nome (A-Z)"),
    NAME_DESC("Nome (Z-A)"),
    SIZE_DESC("Maior Tamanho")
}

sealed interface AlbumUiState {
    object Loading : AlbumUiState
    data class Success(val mediaItems: List<MediaItemData>) : AlbumUiState
}

class AlbumViewModel : ViewModel() {
    companion object {
        private const val TAG = "AlbumViewModel"
        private val BUCKET_NAMES = arrayOf("PhotoFlow", "AestheticCamera")
    }

    private val _uiState = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val uiState: StateFlow<AlbumUiState> = _uiState

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun loadPhotos(context: Context) {
        viewModelScope.launch {
            _uiState.value = AlbumUiState.Loading
            loadFavorites(context)
            val media = queryMedia(context)
            _uiState.value = AlbumUiState.Success(media)
        }
    }

    private fun loadFavorites(context: Context) {
        val prefs = context.getSharedPreferences("album_prefs", Context.MODE_PRIVATE)
        val favs = prefs.getStringSet("favorite_uris", emptySet()) ?: emptySet()
        _favorites.value = favs.toSet()
    }

    fun toggleFavorite(context: Context, uri: Uri) {
        val uriStr = uri.toString()
        val current = _favorites.value.toMutableSet()
        if (current.contains(uriStr)) {
            current.remove(uriStr)
        } else {
            current.add(uriStr)
        }
        _favorites.value = current

        val prefs = context.getSharedPreferences("album_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("favorite_uris", current).apply()
    }

    fun toggleFavoritesBatch(context: Context, uris: List<Uri>) {
        val current = _favorites.value.toMutableSet()
        val allAlreadyFav = uris.all { current.contains(it.toString()) }

        uris.forEach { uri ->
            val uriStr = uri.toString()
            if (allAlreadyFav) {
                current.remove(uriStr)
            } else {
                current.add(uriStr)
            }
        }
        _favorites.value = current

        val prefs = context.getSharedPreferences("album_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("favorite_uris", current).apply()
    }

    private suspend fun queryMedia(context: Context): List<MediaItemData> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItemData>()

        // 1. Consultar Fotos (MediaStore.Images)
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE
            )
            val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            val selectionArgs = BUCKET_NAMES
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
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val name = cursor.getString(nameCol) ?: "Foto"
                    val size = cursor.getLong(sizeCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    mediaList.add(MediaItemData(contentUri, dateAdded, isVideo = false, displayName = name, size = size))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao consultar fotos no MediaStore", e)
        }

        // 2. Consultar Vídeos (MediaStore.Video)
        try {
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE
            )
            val selection = "${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ? OR ${MediaStore.Video.Media.BUCKET_DISPLAY_NAME} = ?"
            val selectionArgs = BUCKET_NAMES
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
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dateAdded = cursor.getLong(dateCol)
                    val name = cursor.getString(nameCol) ?: "Vídeo"
                    val size = cursor.getLong(sizeCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    mediaList.add(MediaItemData(contentUri, dateAdded, isVideo = true, displayName = name, size = size))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao consultar vídeos no MediaStore", e)
        }

        // 3. Fallback
        if (mediaList.isEmpty()) {
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE
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
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val dateAdded = cursor.getLong(dateCol)
                        val name = cursor.getString(nameCol) ?: "Foto"
                        val size = cursor.getLong(sizeCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        mediaList.add(MediaItemData(contentUri, dateAdded, isVideo = false, displayName = name, size = size))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro na consulta de fallback no MediaStore", e)
            }
        }

        mediaList.sortByDescending { it.dateAdded }
        mediaList
    }

    private val _pendingDeleteUris = MutableStateFlow<Set<Uri>>(emptySet())
    val pendingDeleteUris: StateFlow<Set<Uri>> = _pendingDeleteUris.asStateFlow()

    fun stageDelete(uris: List<Uri>) {
        _pendingDeleteUris.value = _pendingDeleteUris.value + uris
    }

    fun cancelPendingDelete(uris: List<Uri>) {
        _pendingDeleteUris.value = _pendingDeleteUris.value - uris.toSet()
    }

    fun commitPendingDelete(
        context: Context,
        uris: List<Uri>,
        onSecurityException: (android.content.IntentSender) -> Unit
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                            onSecurityException(e.userAction.actionIntent.intentSender)
                        } else {
                            Log.e(TAG, "Erro de segurança ao deletar mídia: $uri", e)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao deletar mídia: $uri", e)
                    }
                }
                _pendingDeleteUris.value = _pendingDeleteUris.value - uris.toSet()
                loadPhotos(context)
            }
        }
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
                        Log.e(TAG, "Erro de segurança ao deletar foto: $uri", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao deletar foto: $uri", e)
                }
            }
        }
    }
}
