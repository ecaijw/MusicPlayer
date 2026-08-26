package com.localplayer.app.data

import android.net.Uri

data class AudioFile(
    val documentUri: Uri,
    val displayName: String,
    val mimeType: String?
)
