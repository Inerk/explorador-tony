package com.tony.explorador

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Las formas de ver una carpeta en el explorador. */
enum class ExplorerView(val id: String, val label: String, val icon: ImageVector) {
    LIST("list", "Lista", Icons.Filled.TableRows),
    COMPACT("compact", "Lista compacta", Icons.Filled.ViewHeadline),
    GRID("grid", "Cuadrícula", Icons.Filled.GridView),
    BIG("big", "Iconos grandes", Icons.Filled.ViewModule);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: LIST
    }
}

fun fileIcon(file: File, kind: FileKind): ImageVector = when {
    kind == FileKind.FOLDER -> Icons.Filled.Folder
    kind == FileKind.IMAGE -> Icons.Filled.Image
    kind == FileKind.AUDIO -> Icons.Filled.AudioFile
    kind == FileKind.VIDEO -> Icons.Filled.VideoFile
    file.extension.lowercase(Locale.ROOT) == "pdf" -> Icons.Filled.PictureAsPdf
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

fun fileColor(file: File, kind: FileKind): Color = when {
    kind == FileKind.FOLDER -> Color(0xFFF5B800)
    kind == FileKind.IMAGE -> Color(0xFF43A047)
    kind == FileKind.AUDIO -> Color(0xFFE53935)
    kind == FileKind.VIDEO -> Color(0xFF8E24AA)
    file.extension.lowercase(Locale.ROOT) == "pdf" -> Color(0xFFD32F2F)
    else -> Color(0xFF757575)
}

/** Vista previa de un archivo: foto, cuadro del video, primera hoja del PDF o su icono. */
@Composable
fun FilePreview(file: File, kind: FileKind, iconSize: Dp, modifier: Modifier = Modifier, richPdf: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            kind == FileKind.IMAGE || kind == FileKind.VIDEO -> {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (kind == FileKind.VIDEO) {
                    Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size((iconSize.value * 0.45f).coerceAtLeast(16f).dp))
                }
            }
            kind == FileKind.AUDIO -> {
                val thumb by produceState<android.graphics.Bitmap?>(null, file) { value = loadThumb(file.absolutePath) }
                val t = thumb
                if (t != null) Image(remember(t) { t.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else FriendlyIcon(file, kind, iconSize)
            }
            richPdf && file.isPdf() -> {
                val page by produceState<android.graphics.Bitmap?>(null, file) { value = loadPdfThumb(file) }
                val p = page
                if (p != null) {
                    Image(remember(p) { p.asImageBitmap() }, null, contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize().background(Color.White))
                    Icon(Icons.Filled.PictureAsPdf, null, tint = Color(0xFFD32F2F),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(22.dp))
                } else FriendlyIcon(file, kind, iconSize)
            }
            else -> FriendlyIcon(file, kind, iconSize)
        }
    }
}

@Composable
private fun SelectedMark(selected: Boolean) {
    if (!selected) return
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.CheckCircle, null, tint = Color.White)
    }
}

/** Lista compacta: una línea por archivo, con tamaño a la derecha (como "Detalles"). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactFileList(
    files: List<File>, selection: Set<File>, state: LazyListState,
    onClick: (File) -> Unit, onLongClick: (File) -> Unit, onMenu: (File) -> Unit = {}, focused: File? = null,
) {
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(files, key = { it.absolutePath }) { file ->
            val kind = remember(file) { file.kind() }
            val selected = file in selection
            Row(
                Modifier.fillMaxWidth()
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else if (file == focused) ExpTheme.crumbBg else Color.Transparent)
                    .combinedClickable(onClick = { onClick(file) }, onLongClick = { onLongClick(file) })
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                else FriendlyIcon(file, kind, 28.dp)
                Spacer(Modifier.width(12.dp))
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, modifier = Modifier.weight(1f))
                val info by produceState("", file) {
                    value = withContext(Dispatchers.IO) { if (file.isDirectory) "" else formatSize(file.length()) }
                }
                Text(info, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoreDots(Modifier.padding(start = 6.dp)) { onMenu(file) }
            }
            HorizontalDivider(Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

/** Cuadrícula de íconos (normal o grandes con vista previa). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGrid(
    files: List<File>, selection: Set<File>, big: Boolean, showHidden: Boolean, state: LazyGridState,
    onClick: (File) -> Unit, onLongClick: (File) -> Unit, onMenu: (File) -> Unit = {}, focused: File? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (big) 160.dp else 92.dp),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(if (big) 10.dp else 6.dp),
        horizontalArrangement = Arrangement.spacedBy(if (big) 10.dp else 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (big) 12.dp else 6.dp),
    ) {
        items(files, key = { it.absolutePath }) { file ->
            val kind = remember(file) { file.kind() }
            val selected = file in selection
            Column(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else if (file == focused) ExpTheme.crumbBg else Color.Transparent)
                    .combinedClickable(onClick = { onClick(file) }, onLongClick = { onLongClick(file) })
                    .padding(if (big) 6.dp else 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val thumbShape = RoundedCornerShape(if (big) 12.dp else 10.dp)
                val media = kind == FileKind.IMAGE || kind == FileKind.VIDEO || file.isPdf() || (big && kind == FileKind.AUDIO)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(if (big && kind == FileKind.VIDEO) 16f / 10f else 1f).clip(thumbShape)
                        .then(if (media) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thumbShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    FilePreview(file, kind, if (big) 72.dp else 48.dp, Modifier.fillMaxSize(), richPdf = true)
                    SelectedMark(selected)
                    MoreDots(Modifier.align(Alignment.TopEnd), onImage = media) { onMenu(file) }
                }
                Text(
                    file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    fontSize = if (big) 14.sp else 12.sp, lineHeight = if (big) 17.sp else 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                if (big) {
                    val info by produceState("", file, showHidden) {
                        value = withContext(Dispatchers.IO) {
                            if (file.isDirectory) "${folderItemCount(file, showHidden)} elementos" else formatSize(file.length())
                        }
                    }
                    Text(info, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}


/** Los tres puntos de cada archivo. */
@Composable
fun MoreDots(modifier: Modifier = Modifier, onImage: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .then(if (onImage) Modifier.background(Color.Black.copy(alpha = 0.35f)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.MoreVert, "Opciones", tint = if (onImage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
    }
}
