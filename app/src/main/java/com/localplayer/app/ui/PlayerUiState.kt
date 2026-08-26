package com.localplayer.app.ui

import com.localplayer.app.data.AudioFile

data class PlayerUiState(
    val directoryName: String = "",
    val tracks: List<AudioFile> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val durationKnown: Boolean = false,
    val needsDirectory: Boolean = true,
    val errorMessage: String? = null,
    val notificationHint: Boolean = false
)
