package com.localplayer.app.data

data class PersistedPlayback(
    val treeUri: String,
    val fileUri: String,
    val positionMs: Long
)
