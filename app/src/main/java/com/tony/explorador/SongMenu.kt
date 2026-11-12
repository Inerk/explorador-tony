package com.tony.explorador

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class MenuOption(val label: String, val icon: ImageVector, val tint: Color = Color.White, val action: () -> Unit)

/** Menú de la canción en forma de ventanita (sale del ⋮ de la portada). */
@Composable
fun SongMenuWindow(
    path: String,
    accent: Color,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onLyrics: () -> Unit,
    onGoTo: (String, String) -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by rememberTrackInfo(path)
    val song = LibraryStore.songFor(path)
    val artist = song?.artist ?: info?.artist ?: UNKNOWN_ARTIST
    val album = song?.album ?: info?.album ?: UNKNOWN_ALBUM
    val fav = LibraryStore.isFavorite(path)
    var addToList by remember { mutableStateOf(false) }
    var showCover by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tagOnline by remember { mutableStateOf(false) }
    var lyricsOnline by remember { mutableStateOf(false) }

    val options = listOf(
        MenuOption("Lista de repro", Icons.AutoMirrored.Filled.PlaylistAdd) { addToList = true },
        MenuOption(if (fav) "Quitar favorito" else "Favorito", if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
            if (fav) Color(0xFFFFC83D) else Color.White) { LibraryStore.toggleFavorite(path) },
        MenuOption("Carátula", Icons.Filled.Image) { showCover = true },
        MenuOption("Info./Etiq.", Icons.Filled.Info) { onInfo() },
        MenuOption("Letra", Icons.Filled.Lyrics) { onLyrics() },
        MenuOption("Etiquetar web", Icons.Filled.TravelExplore, Color(0xFF8FD3FF)) { tagOnline = true },
        MenuOption("Buscar letra", Icons.Filled.Search, Color(0xFF8FD3FF)) { lyricsOnline = true },
        MenuOption("Compartir", Icons.Filled.Share) { shareFiles(context, listOf(File(path))) },
        MenuOption("Artista", Icons.Filled.Mic) { onGoTo("artist", artist) },
        MenuOption("Álbum", Icons.Filled.Album) { onGoTo("album", album) },
        MenuOption("Carpeta", Icons.Filled.Folder) { onGoTo("folder", File(path).parent ?: "/") },
        MenuOption("Eliminar", Icons.Filled.Delete, Color(0xFFFF6B5E)) { confirmDelete = true },
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss
            ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth(0.9f)
                    .shadow(24.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF232329))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(vertical = 10.dp)
            ) {
                // encabezado
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center) {
                        val c = info?.cover
                        if (c != null) Image(remember(c) { c.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Filled.MusicNote, null, tint = Color.White.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info?.title ?: File(path).nameWithoutExtension, color = Color.White, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artist, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                // opciones en dos columnas
                options.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        pair.forEach { o ->
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = o.action)
                                    .padding(horizontal = 12.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(o.icon, null, tint = o.tint, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(o.label, color = o.tint, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (addToList) {
        AddToPlaylistDialog(
            onDismiss = { addToList = false },
            onPick = { id -> LibraryStore.addToPlaylist(id, listOf(path)); addToList = false
                Toast.makeText(context, "Agregada a la lista", Toast.LENGTH_SHORT).show() },
            onNew = { name -> LibraryStore.createPlaylist(name, listOf(path)); addToList = false
                Toast.makeText(context, "Lista \"$name\" creada", Toast.LENGTH_SHORT).show() }
        )
    }

    if (showCover) {
        Dialog(onDismissRequest = { showCover = false }) {
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFF232329)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val c = info?.cover
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.Black),
                    contentAlignment = Alignment.Center) {
                    if (c != null) Image(remember(c) { c.asImageBitmap() }, "Carátula", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    else Text("Esta canción no tiene carátula", color = Color.White.copy(alpha = 0.6f))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    if (c != null) TextButton(onClick = {
                        scope.launch {
                            val out = withContext(Dispatchers.IO) {
                                runCatching {
                                    val dir = File(android.os.Environment.getExternalStorageDirectory(),
                                        "${android.os.Environment.DIRECTORY_PICTURES}/Carátulas").apply { mkdirs() }
                                    val f = uniqueTarget(dir, (info?.title ?: "caratula").replace(Regex("""[\\/:*?"<>|]"""), "_") + ".jpg")
                                    f.outputStream().use { c.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
                                    f
                                }.getOrNull()
                            }
                            Toast.makeText(context, if (out != null) "Guardada en Imágenes/Carátulas" else "No se pudo guardar", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Guardar imagen") }
                    TextButton(onClick = { showCover = false }) { Text("Cerrar") }
                }
            }
        }
    }

    if (tagOnline) TagSearchDialog(path, accent, onDismiss = { tagOnline = false; onDismiss() })
    if (lyricsOnline) LyricsSearchDialog(path, accent, onDismiss = { lyricsOnline = false; onDismiss() })

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar canción?") },
            text = { Text("Se borrará el archivo \"${File(path).name}\" del teléfono. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { File(path).delete() }.getOrDefault(false) }
                        if (ok) {
                            LibraryStore.removeSong(path)
                            onDeleted()
                        }
                        Toast.makeText(context, if (ok) "Canción eliminada" else "No se pudo eliminar", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Eliminar", color = Color(0xFFFF6B5E)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}
