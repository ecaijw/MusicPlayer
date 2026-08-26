package com.localplayer.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlaybackConnection(context: Context) {
    private val appContext = context.applicationContext
    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    suspend fun connect() {
        if (controller != null) return
        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )
        val newFuture = MediaController.Builder(appContext, token).buildAsync()
        future = newFuture
        controller = suspendCancellableCoroutine { continuation ->
            newFuture.addListener(
                {
                    if (!continuation.isActive) return@addListener
                    try {
                        continuation.resume(newFuture.get())
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                },
                ContextCompat.getMainExecutor(appContext)
            )
            continuation.invokeOnCancellation {
                MediaController.releaseFuture(newFuture)
            }
        }
    }

    fun release() {
        controller?.release()
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
    }
}
