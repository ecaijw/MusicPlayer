package com.localplayer.app.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.localplayer.app.data.PlaybackStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var store: PlaybackStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val saveHandler = Handler(Looper.getMainLooper())
    private var pausedByAudioFocus = false

    private val periodicSave = object : Runnable {
        override fun run() {
            persistProgress()
            saveHandler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PlaybackStore(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(3_000)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                shuffleModeEnabled = false
                addListener(playerListener)
            }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistProgress()
        if (!player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveHandler.removeCallbacks(periodicSave)
        persistProgress()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            saveHandler.removeCallbacks(periodicSave)
            persistProgress()
            if (isPlaying) {
                saveHandler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val uri = mediaItem?.localConfiguration?.uri?.toString() ?: return
            val position = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                player.currentPosition
            } else {
                0L
            }
            scope.launch { store.saveCurrentTrack(uri, position) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                persistProgress()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
                pausedByAudioFocus = true
            } else if (playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                pausedByAudioFocus = false
            } else if (playWhenReady && pausedByAudioFocus) {
                pausedByAudioFocus = false
                player.pause()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            } else {
                player.pause()
            }
        }
    }

    private fun persistProgress() {
        val uri = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        var position = player.currentPosition
        if (player.playbackState == Player.STATE_ENDED) {
            val duration = player.duration
            if (duration != C.TIME_UNSET) {
                position = duration
            }
        }
        scope.launch { store.saveCurrentTrack(uri, position) }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 2_000L
    }
}
