package com.localplayer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localplayer.app.util.formatPlaybackTime

@Composable
fun PlayerScreen(viewModel: PlayerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onDirectoryPicked(uri)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            DirectoryHeader(
                directoryName = state.directoryName,
                needsDirectory = state.needsDirectory,
                onPickDirectory = { picker.launch(null) }
            )
            HorizontalDivider()
            TrackList(
                state = state,
                onTrackClick = viewModel::playAt,
                modifier = Modifier.weight(1f)
            )
            if (state.notificationHint) {
                Text(
                    text = "为了回桌面继续播放，需要通知权限",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            PlaybackControls(
                state = state,
                onSeek = viewModel::seekTo,
                onPrevious = viewModel::skipPrevious,
                onPlayPause = viewModel::playPause,
                onNext = viewModel::skipNext
            )
        }
    }
}

@Composable
private fun DirectoryHeader(
    directoryName: String,
    needsDirectory: Boolean,
    onPickDirectory: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = directoryName.ifBlank { "未选择目录" },
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onPickDirectory) {
            Text(if (needsDirectory && directoryName.isBlank()) "选择目录" else "更换目录")
        }
    }
}

@Composable
private fun TrackList(
    state: PlayerUiState,
    onTrackClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex >= 0) {
            listState.animateScrollToItem(state.currentIndex)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        when {
            state.needsDirectory && state.tracks.isEmpty() -> {
                EmptyHint("请选择包含音频的文件夹")
            }
            state.tracks.isEmpty() -> {
                EmptyHint("该目录没有 mp3 或 wav 文件")
            }
            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = state.tracks,
                        key = { _, track -> track.documentUri.toString() }
                    ) { index, track ->
                        val selected = index == state.currentIndex
                        Text(
                            text = track.displayName,
                            fontSize = 20.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackClick(index) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun PlaybackControls(
    state: PlayerUiState,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val sliderMax = if (state.durationKnown) state.durationMs.toFloat() else 1f
    val sliderValue = if (dragging) {
        dragPosition
    } else {
        state.positionMs.toFloat().coerceIn(0f, sliderMax)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                dragging = true
                dragPosition = value
            },
            onValueChangeFinished = {
                onSeek(dragPosition.toLong())
                dragging = false
            },
            valueRange = 0f..sliderMax,
            enabled = state.durationKnown && state.tracks.isNotEmpty()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatPlaybackTime(if (dragging) dragPosition.toLong() else state.positionMs),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatPlaybackTime(state.durationMs, unset = !state.durationKnown),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onPrevious,
                enabled = state.tracks.isNotEmpty()
            ) {
                Text("上一首", fontSize = 18.sp)
            }
            Button(
                onClick = onPlayPause,
                enabled = state.tracks.isNotEmpty()
            ) {
                Text(if (state.isPlaying) "暂停" else "播放", fontSize = 18.sp)
            }
            TextButton(
                onClick = onNext,
                enabled = state.tracks.isNotEmpty()
            ) {
                Text("下一首", fontSize = 18.sp)
            }
        }
    }
}
