package com.localplayer.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackDataStore by preferencesDataStore(name = "playback")

class PlaybackStore(context: Context) {
    private val dataStore = context.applicationContext.playbackDataStore

    suspend fun load(): PersistedPlayback {
        return dataStore.data.map { prefs ->
            PersistedPlayback(
                treeUri = prefs[Keys.TREE] ?: "",
                fileUri = prefs[Keys.FILE] ?: "",
                positionMs = prefs[Keys.POSITION] ?: 0L
            )
        }.first()
    }

    suspend fun saveTreeUri(treeUri: String) {
        dataStore.edit {
            it[Keys.TREE] = treeUri
            it[Keys.FILE] = ""
            it[Keys.POSITION] = 0L
        }
    }

    suspend fun saveCurrentTrack(fileUri: String, positionMs: Long) {
        dataStore.edit {
            it[Keys.FILE] = fileUri
            it[Keys.POSITION] = positionMs.coerceAtLeast(0L)
        }
    }

    private object Keys {
        val TREE = stringPreferencesKey("tree_uri")
        val FILE = stringPreferencesKey("file_uri")
        val POSITION = longPreferencesKey("position_ms")
    }
}
