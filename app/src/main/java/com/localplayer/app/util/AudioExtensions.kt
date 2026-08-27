package com.localplayer.app.util

fun isSupportedAudio(name: String): Boolean {
    if (name.startsWith(".")) return false
    val lower = name.lowercase()
    return lower.endsWith(".mp3") || lower.endsWith(".wav")
}

fun formatPlaybackTime(ms: Long, unset: Boolean = false): String {
    if (unset || ms < 0L) return "--:--"
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
