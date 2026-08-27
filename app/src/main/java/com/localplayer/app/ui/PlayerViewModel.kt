package com.localplayer.app.ui

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.localplayer.app.data.AudioFile
import com.localplayer.app.data.LibraryRepository
import com.localplayer.app.data.PlaybackStore
import com.localplayer.app.playback.PlaybackConnection
import com.localplayer.app.util.appIconPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val library = LibraryRepository(application)
    private val store = PlaybackStore(application)
    private val connection = PlaybackConnection(application)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var tracks: List<AudioFile> = emptyList()
    private val defaultArtwork: ByteArray by lazy { getApplication<Application>().appIconPng() }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishPlayerState(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update { it.copy(errorMessage = "无法播放该文件") }
            viewModelScope.launch { refreshLibraryList() }
        }
    }

    init {
        viewModelScope.launch {
            try {
                connection.connect()
                connection.controller?.addListener(playerListener)
                restore()
                observePosition()
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "播放器启动失败") }
            }
        }
    }

    fun onDirectoryPicked(treeUri: Uri) {
        viewModelScope.launch {
            val previousLibrary = _uiState.value.library
            _uiState.update { it.copy(library = LibraryState.Loading, errorMessage = null) }
            try {
                library.takeTreePermission(treeUri)
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        library = previousLibrary,
                        errorMessage = "无法读取该目录"
                    )
                }
                return@launch
            }
            val loaded = try {
                withContext(Dispatchers.IO) { library.loadFromTree(treeUri) }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        library = previousLibrary,
                        errorMessage = "无法读取该目录"
                    )
                }
                return@launch
            }
            store.saveTreeUri(treeUri.toString())
            tracks = loaded
            applyPlaylist(loaded, startIndex = 0, startPositionMs = 0L, play = false)
            library.releaseOtherTreePermissions(treeUri)
            _uiState.update {
                it.copy(
                    library = LibraryState.Loaded(
                        directoryName = library.directoryName(treeUri),
                        tracks = loaded
                    ),
                    currentIndex = if (loaded.isEmpty()) -1 else 0,
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = 0L,
                    durationKnown = false,
                    errorMessage = null
                )
            }
        }
    }

    fun playAt(index: Int) {
        val controller = connection.controller ?: return
        val track = tracks.getOrNull(index) ?: return
        val playerIndex = indexInPlayer(controller, track.documentUri.toString())
        if (playerIndex >= 0) {
            controller.seekTo(playerIndex, 0L)
            controller.play()
            return
        }
        applyPlaylist(tracks, startIndex = index, startPositionMs = 0L, play = true)
    }

    fun playPause() {
        val controller = connection.controller ?: return
        if (controller.isPlaying) {
            controller.pause()
            return
        }
        if (tracks.isEmpty()) return
        if (controller.playbackState == Player.STATE_ENDED) {
            controller.seekTo(0)
        }
        controller.play()
    }

    fun skipNext() {
        val controller = connection.controller ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        }
    }

    fun skipPrevious() {
        connection.controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        connection.controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun onCleared() {
        connection.controller?.removeListener(playerListener)
        connection.release()
        super.onCleared()
    }

    private suspend fun restore() {
        val controller = connection.controller ?: return
        if (controller.mediaItemCount > 0) {
            syncLibraryFromStore()
            publishPlayerState(controller)
            return
        }

        val persisted = store.load()
        if (persisted.treeUri.isBlank()) {
            _uiState.update { it.copy(library = LibraryState.NoDirectory) }
            return
        }
        val treeUri = persisted.treeUri.toUri()
        if (!library.isTreeAccessible(treeUri)) {
            _uiState.update { it.copy(library = LibraryState.PermissionLost) }
            return
        }
        val loaded = withContext(Dispatchers.IO) { library.loadFromTree(treeUri) }
        tracks = loaded
        val matchedIndex = loaded.indexOfFirst { it.documentUri.toString() == persisted.fileUri }
        val startIndex = when {
            loaded.isEmpty() -> -1
            matchedIndex >= 0 -> matchedIndex
            else -> 0
        }
        val startPosition = if (matchedIndex >= 0) persisted.positionMs else 0L
        applyPlaylist(loaded, startIndex.coerceAtLeast(0), startPosition, play = false)
        _uiState.update {
            it.copy(
                library = LibraryState.Loaded(
                    directoryName = library.directoryName(treeUri),
                    tracks = loaded
                ),
                currentIndex = startIndex,
                positionMs = startPosition,
                errorMessage = null
            )
        }
        publishPlayerState(controller)
    }

    private suspend fun syncLibraryFromStore() {
        val persisted = store.load()
        if (persisted.treeUri.isBlank()) {
            return
        }
        val treeUri = persisted.treeUri.toUri()
        if (!library.isTreeAccessible(treeUri)) {
            _uiState.update { it.copy(library = LibraryState.PermissionLost) }
            return
        }
        val loaded = withContext(Dispatchers.IO) { library.loadFromTree(treeUri) }
        tracks = loaded
        _uiState.update {
            it.copy(
                library = LibraryState.Loaded(
                    directoryName = library.directoryName(treeUri),
                    tracks = loaded
                ),
                errorMessage = null
            )
        }
    }

    private suspend fun refreshLibraryList() {
        val persisted = store.load()
        if (persisted.treeUri.isBlank()) return
        val treeUri = persisted.treeUri.toUri()
        if (!library.isTreeAccessible(treeUri)) {
            _uiState.update { it.copy(library = LibraryState.PermissionLost) }
            return
        }
        val loaded = withContext(Dispatchers.IO) { library.loadFromTree(treeUri) }
        tracks = loaded
        _uiState.update {
            it.copy(
                library = LibraryState.Loaded(
                    directoryName = library.directoryName(treeUri),
                    tracks = loaded
                )
            )
        }
    }

    private fun applyPlaylist(
        loaded: List<AudioFile>,
        startIndex: Int,
        startPositionMs: Long,
        play: Boolean
    ) {
        val controller = connection.controller ?: return
        if (loaded.isEmpty()) {
            controller.stop()
            controller.clearMediaItems()
            return
        }
        controller.setMediaItems(
            loaded.toMediaItems(),
            startIndex.coerceIn(loaded.indices),
            startPositionMs.coerceAtLeast(0L)
        )
        controller.prepare()
        if (play) {
            controller.play()
        } else {
            controller.pause()
        }
    }

    private suspend fun observePosition() {
        while (viewModelScope.isActive) {
            connection.controller?.let { publishPlayerState(it) }
            delay(400)
        }
    }

    private fun publishPlayerState(player: Player) {
        val duration = player.duration
        val durationKnown = duration != C.TIME_UNSET && duration > 0L
        val mediaId = player.currentMediaItem?.mediaId
        val index = if (mediaId.isNullOrBlank() || tracks.isEmpty()) {
            -1
        } else {
            tracks.indexOfFirst { it.documentUri.toString() == mediaId }
        }
        _uiState.update {
            it.copy(
                currentIndex = index,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = if (durationKnown) duration else 0L,
                durationKnown = durationKnown
            )
        }
    }

    private fun indexInPlayer(player: Player, mediaId: String): Int {
        for (index in 0 until player.mediaItemCount) {
            if (player.getMediaItemAt(index).mediaId == mediaId) {
                return index
            }
        }
        return -1
    }

    private fun List<AudioFile>.toMediaItems(): List<MediaItem> {
        return map { file ->
            MediaItem.Builder()
                .setUri(file.documentUri)
                .setMediaId(file.documentUri.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(file.displayName)
                        .setDisplayTitle(file.displayName)
                        .setArtworkData(defaultArtwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build()
                )
                .build()
        }
    }
}
