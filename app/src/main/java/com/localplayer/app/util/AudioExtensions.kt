package com.localplayer.app.util

private val audioMimeTypes = setOf(
    "audio/mpeg",
    "audio/wav",
    "audio/x-wav",
    "audio/wave",
    "audio/vnd.wave"
)

fun isSupportedAudio(name: String, mimeType: String?): Boolean {
    if (name.startsWith(".")) return false
    val lower = name.lowercase()
    if (lower.endsWith(".mp3") || lower.endsWith(".wav")) return true
    return mimeType != null && mimeType.lowercase() in audioMimeTypes
}

fun formatPlaybackTime(ms: Long, unset: Boolean = false): String {
    if (unset || ms < 0L) return "--:--"
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
