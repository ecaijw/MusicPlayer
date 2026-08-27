package com.localplayer.app.ui

import com.localplayer.app.data.AudioFile

sealed interface LibraryState {
    data object NoDirectory : LibraryState
    data object Loading : LibraryState
    data class Loaded(
        val directoryName: String,
        val tracks: List<AudioFile>
    ) : LibraryState
    data object PermissionLost : LibraryState
    data class Error(val message: String) : LibraryState
}

data class PlayerUiState(
    val library: LibraryState = LibraryState.NoDirectory,
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val durationKnown: Boolean = false,
    val errorMessage: String? = null
) {
    val tracks: List<AudioFile>
        get() = (library as? LibraryState.Loaded)?.tracks.orEmpty()

    val needsDirectoryPicker: Boolean
        get() = library is LibraryState.NoDirectory || library is LibraryState.PermissionLost
}
