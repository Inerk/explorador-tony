package com.tony.explorador

import android.content.Context
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GalleryItem(
    val path: String,
    val album: String,
    val date: Long,
    val isVideo: Boolean,
    val durationMs: Long,
) {
    val file get() = File(path)
}

private suspend fun loadGallery(context: Context): List<GalleryItem> = withContext(Dispatchers.IO) {
    val out = mutableListOf<GalleryItem>()
    fun query(uri: android.net.Uri, video: Boolean) {
        val proj = buildList {
            add(MediaStore.MediaColumns.DATA)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            if (video) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()
        runCatching {
            context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
                val iData = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val iDur = if (video) c.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    val taken = c.getLong(iTaken)
                    val date = if (taken > 0) taken else c.getLong(iMod) * 1000
                    out.add(
                        GalleryItem(
                            path = path,
                            album = c.getString(iBucket) ?: File(path).parentFile?.name ?: "Otros",
                            date = date,
                            isVideo = video,
                            durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                        )
                    )
                }
            }
        }
    }
    query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
    query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
    out.filter { it.file.isFile }.sortedByDescending { it.date }
}

private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("es", "MX"))

@Composable
fun GalleryScreen(
    onOpenImages: (List<File>, Int) -> Unit,
    onOpenVideo: (List<File>, Int) -> Unit,
    onClose: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val all by produceState<List<GalleryItem>?>(null) { value = loadGallery(context) }
    var tab by remember { mutableIntStateOf(0) }
    var album by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (album != null) album = null else onClose()
    }

    fun open(list: List<GalleryItem>, item: GalleryItem) {
        if (item.isVideo) {
            val vids = list.filter { it.isVideo }
            onOpenVideo(vids.map { it.file }, vids.indexOf(item).coerceAtLeast(0))
        } else {
            val imgs = list.filter { !it.isVideo }
            onOpenImages(imgs.map { it.file }, imgs.indexOf(item).coerceAtLeast(0))
        }
    }

    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // Barra superior
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (album != null) album = null else onClose() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás")
                        }
                        Text(album ?: "Galería", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        HomeButton(onHome, Modifier.padding(end = 10.dp), dark = false)
                    }
                    if (album == null) {
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Fotos") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Álbumes") })
                        }
                    }
                }
            }

            val items = all
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No encontré fotos ni videos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                album != null -> {
                    val list = items.filter { it.album == album }
                    PhotoGrid(list, onClick = { open(list, it) })
                }
                tab == 0 -> PhotoGrid(items, onClick = { open(items, it) })
                else -> AlbumGrid(items, onOpen = { album = it })
            }
        }
    }
}

/** Cuadrícula de fotos agrupada por mes. */
@Composable
private fun PhotoGrid(items: List<GalleryItem>, onClick: (GalleryItem) -> Unit) {
    val groups = remember(items) {
        items.groupBy { monthFormat.format(Date(it.date)).replaceFirstChar { c -> c.uppercase() } }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        groups.forEach { (month, list) ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "h-$month") {
                Text(month, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 8.dp))
            }
            items(list, key = { it.path }) { item -> Thumb(item, Modifier.clickable { onClick(item) }) }
        }
    }
}

@Composable
private fun Thumb(item: GalleryItem, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(model = item.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (item.isVideo) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .padding(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(3.dp))
                    if (item.durationMs > 0) Text(formatTime(item.durationMs), color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(items: List<GalleryItem>, onOpen: (String) -> Unit) {
    val albums = remember(items) {
        items.groupBy { it.album }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<GalleryItem>>> { it.key.equals("Camera", true) || it.key.equals("Cámara", true) }
                .thenByDescending { it.value.size })
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(albums, key = { it.key }) { (name, list) ->
            Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen(name) }) {
                Thumb(list.first(), Modifier.clip(RoundedCornerShape(12.dp)))
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp))
                Text("${list.size} elementos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}
