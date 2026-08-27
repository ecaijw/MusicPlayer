package com.localplayer.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localplayer.app.R
import com.localplayer.app.data.AudioFile
import com.localplayer.app.util.formatPlaybackTime
import com.localplayer.app.util.trackLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            DirectoryHeader(
                library = state.library,
                pickLabel = if (state.needsDirectoryPicker) "选择目录" else "更换目录",
                onPickDirectory = { picker.launch(null) }
            )
            TrackList(
                state = state,
                onTrackClick = viewModel::playAt,
                modifier = Modifier.weight(1f)
            )
            val libraryError = (state.library as? LibraryState.Error)?.message
            val message = state.errorMessage ?: libraryError
            if (message != null) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
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
    library: LibraryState,
    pickLabel: String,
    onPickDirectory: () -> Unit
) {
    val title = when (library) {
        is LibraryState.Loaded -> library.directoryName
        LibraryState.Loading -> "正在加载…"
        else -> "音频"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        OutlinedButton(
            onClick = onPickDirectory,
            modifier = Modifier.height(40.dp),
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(pickLabel, fontSize = 14.sp)
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
    val previousIndex = remember { mutableIntStateOf(-1) }
    LaunchedEffect(state.currentIndex) {
        val newIndex = state.currentIndex
        val oldIndex = previousIndex.intValue
        previousIndex.intValue = newIndex
        listState.adjustForCurrentTrack(oldIndex, newIndex)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        when (val library = state.library) {
            LibraryState.NoDirectory -> EmptyHint("请选择包含音频的文件夹")
            LibraryState.PermissionLost -> EmptyHint("无法访问上次的目录，请重新选择")
            LibraryState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is LibraryState.Error -> EmptyHint(library.message)
            is LibraryState.Loaded -> {
                if (library.tracks.isEmpty()) {
                    EmptyHint("该目录没有 mp3 或 wav 文件")
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(
                            items = library.tracks,
                            key = { _, track -> track.documentUri.toString() }
                        ) { index, track ->
                            TrackRow(
                                track = track,
                                selected = index == state.currentIndex,
                                showDivider = index != library.tracks.lastIndex,
                                onClick = { onTrackClick(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun LazyListState.adjustForCurrentTrack(oldIndex: Int, newIndex: Int) {
    if (newIndex < 0) return
    snapshotFlow { layoutInfo.visibleItemsInfo }
        .first { it.isNotEmpty() }
    when {
        oldIndex < 0 -> animateScrollToItem(newIndex)
        newIndex == oldIndex - 1 || newIndex == oldIndex + 1 -> {
            ensureItemFullyVisible(newIndex)
        }
        else -> Unit
    }
}

private suspend fun LazyListState.ensureItemFullyVisible(index: Int) {
    val firstDelta = scrollDeltaToFullyShow(index) ?: return
    if (firstDelta == 0f) return
    animateScrollBy(firstDelta)
    snapshotFlow { layoutInfo.visibleItemsInfo.any { it.index == index } }
        .first { it }
    val remaining = scrollDeltaToFullyShow(index) ?: return
    if (remaining != 0f) {
        animateScrollBy(remaining)
    }
}

private fun LazyListState.scrollDeltaToFullyShow(index: Int): Float? {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return null
    val start = info.viewportStartOffset
    val end = info.viewportEndOffset
    val item = visible.find { it.index == index }
    if (item != null) {
        val topClip = start - item.offset
        if (topClip > 1) return -topClip.toFloat()
        val bottomClip = item.offset + item.size - end
        if (bottomClip > 1) return bottomClip.toFloat()
        return 0f
    }
    val first = visible.first()
    val last = visible.last()
    return when {
        index < first.index -> -first.size.toFloat() * (first.index - index)
        index > last.index -> last.size.toFloat() * (index - last.index)
        else -> 0f
    }
}

@Composable
private fun TrackRow(
    track: AudioFile,
    selected: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    val title = trackLabel(track.displayName).title
    val titleColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) {
                    CurrentTrackTitle(
                        text = title,
                        resetKey = track.documentUri.toString(),
                        color = titleColor,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = stringResource(R.string.now_playing),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun CurrentTrackTitle(
    text: String,
    resetKey: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val offset = remember(resetKey) { Animatable(0f) }
    var textWidth by remember(resetKey) { mutableFloatStateOf(0f) }
    var boxWidth by remember(resetKey) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    LaunchedEffect(resetKey, textWidth, boxWidth) {
        offset.snapTo(0f)
        val overflow = textWidth - boxWidth
        if (overflow <= 1f || boxWidth <= 0f) return@LaunchedEffect
        val durationMs = with(density) {
            (overflow / 32.dp.toPx() * 1000f).toInt().coerceIn(1_200, 24_000)
        }
        while (true) {
            delay(5_000)
            offset.animateTo(
                targetValue = -overflow,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
            )
            delay(2_000)
            offset.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { boxWidth = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .onSizeChanged { textWidth = it.width.toFloat() }
                .offset { IntOffset(offset.value.roundToInt(), 0) }
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp
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
    val hasTracks = state.tracks.isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
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
                enabled = state.durationKnown && hasTracks,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatPlaybackTime(
                        if (dragging) dragPosition.toLong() else state.positionMs
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Text(
                    text = formatPlaybackTime(state.durationMs, unset = !state.durationKnown),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = hasTracks,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.skip_previous),
                        tint = if (hasTracks) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = hasTracks,
                    modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = stringResource(
                            if (state.isPlaying) R.string.pause else R.string.play
                        ),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(
                    onClick = onNext,
                    enabled = hasTracks,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.skip_next),
                        tint = if (hasTracks) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
