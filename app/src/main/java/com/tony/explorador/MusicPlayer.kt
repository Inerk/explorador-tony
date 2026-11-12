package com.tony.explorador

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

fun playMusic(player: Player, files: List<File>, index: Int) {
    val items = files.map { f ->
        MediaItem.Builder()
            .setMediaId(f.absolutePath)
            .setUri(Uri.fromFile(f))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(f.nameWithoutExtension)
                    .setArtist(f.parentFile?.name ?: "")
                    .build()
            )
            .build()
    }
    player.setMediaItems(items, index, 0L)
    player.prepare()
    player.play()
}

/** Fuerza a redibujar cuando cambia el reproductor y cada medio segundo para la barra de avance. */
@Composable
fun observePlayer(player: Player?): Int {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { tick++ }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(500)
            if (player?.isPlaying == true) tick++
        }
    }
    return tick
}

fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

@Composable
fun MiniPlayer(player: Player, onOpen: () -> Unit) {
    val tick = observePlayer(player)
    if (player.mediaItemCount == 0) return
    val title = player.mediaMetadata.title?.toString() ?: ""
    val duration = player.duration.coerceAtLeast(1L)
    val progress = (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) + tick * 0f

    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Reproducir/pausar")
                }
                IconButton(onClick = { player.seekToNextMediaItem() }, enabled = player.hasNextMediaItem()) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente")
                }
            }
        }
    }
}

@Composable
fun MusicPlayerScreen(player: Player, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val tick = observePlayer(player)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val title = player.mediaMetadata.title?.toString() ?: "Sin canción"
    val artist = player.mediaMetadata.artist?.toString() ?: ""
    val duration = player.duration.let { if (it <= 0) 0L else it }
    val position = player.currentPosition + tick * 0L

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Cerrar")
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            Slider(
                value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    player.seekTo((dragValue * duration).toLong())
                    dragging = false
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(if (dragging) (dragValue * duration).toLong() else position), style = MaterialTheme.typography.bodySmall)
                Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio",
                        tint = if (player.shuffleModeEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { player.seekToPrevious() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = { if (player.isPlaying) player.pause() else player.play() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors()
                ) {
                    Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Reproducir/pausar", modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { player.seekToNextMediaItem() }, enabled = player.hasNextMediaItem()) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }) {
                    Icon(
                        if (player.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repetir",
                        tint = if (player.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Text("Lista", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))

            val current = player.currentMediaItemIndex
            val count = player.mediaItemCount
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed((0 until count).toList()) { _, i ->
                    val item = player.getMediaItemAt(i)
                    val isCurrent = i == current
                    Text(
                        item.mediaMetadata.title?.toString() ?: "",
                        modifier = Modifier.fillMaxWidth()
                            .clickable { player.seekTo(i, 0L); player.play() }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
