package com.tony.explorador

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Tema «Nebula» (el oficial): pantalla de inicio con iconos de cristal, fondo de onda y una barra
 * de abajo con apps, buscar, inicio y el reloj. El tema anterior quedó como «Nebula Neko».
 */

/** Estilo de la pantalla de inicio: "nebula" (nuevo, predeterminado) o "neko" (el escritorio anterior). */
object HomeStyle {
    const val NEBULA = "nebula"
    const val NEKO = "neko"
    val all = listOf(NEBULA to "Nebula", NEKO to "Nebula Neko")
}

/** Los iconos de cristal de cada app. */
fun nebulaIconFor(app: DeskApp): Int = when (app.id) {
    "music" -> R.drawable.pv_musica
    "pictures", "camera" -> R.drawable.pv_galeria
    "videos" -> R.drawable.pv_video
    "downloads" -> R.drawable.pv_descargas
    "panel" -> R.drawable.pv_ajustes
    "updates" -> R.drawable.pv_actualizar
    "trash" -> R.drawable.nbx_trash
    "web" -> R.drawable.pv_internet
    "explorer", "mypc", "sd" -> R.drawable.pv_explorador
    "remote" -> R.drawable.pv_control
    "tienda" -> R.drawable.pv_tienda_nebula
    "casa" -> R.drawable.pv_inicio
    "docs" -> R.drawable.pv_carpeta_enlace
    else -> when (app.kind) {
        XpIconKind.FOLDER -> if (Regex("(?i)m[uú]sic").containsMatchIn(app.label)) R.drawable.pv_musica_carpeta else R.drawable.pv_carpeta_enlace
        XpIconKind.PICTURES -> R.drawable.pv_galeria
        XpIconKind.MUSIC -> R.drawable.pv_musica
        XpIconKind.VIDEOS -> R.drawable.pv_video
        else -> R.drawable.pv_carpeta_enlace
    }
}

private val NebulaDefaults = listOf("music", "pictures", "videos", "downloads", "panel", "updates", "trash", "web", "explorer", "tienda")

private val labelStyle get() = if (XpColors.dark) TextStyle(color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f))
    else TextStyle(color = Color(0xFF0E1A3A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
    shadow = Shadow(Color.White.copy(alpha = 0.8f), blurRadius = 8f))

@Composable
private fun NebulaWallpaperImage(modifier: Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (LibraryStore.wallpaper == "custom" && LibraryStore.wallpaperFile.isFile)
        AsyncImage(ImageRequest.Builder(ctx).data(LibraryStore.wallpaperFile).memoryCacheKey("wallpaper-${LibraryStore.wallpaperVersion}")
            .diskCachePolicy(CachePolicy.DISABLED).build(), null, contentScale = ContentScale.Crop, modifier = modifier)
    else Image(painterResource(if (XpColors.dark) R.drawable.pv_fondo_oscuro else R.drawable.pv_fondo_claro), null, contentScale = ContentScale.Crop, modifier = modifier)
}

private fun Modifier.glassBar(shape: RoundedCornerShape) = if (XpColors.dark) this.clip(shape)
    .background(Brush.verticalGradient(listOf(Color(0xFF1B2450).copy(alpha = 0.75f), Color(0xFF0E1433).copy(alpha = 0.75f))))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))), shape)
    else this.clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.72f), Color.White.copy(alpha = 0.52f))))
    .border(1.dp, Color.White.copy(alpha = 0.95f), shape)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NebulaAppIcon(app: DeskApp, size: Int = 64, onLongClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .combinedClickable(onClick = app.action, onLongClick = onLongClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Image(painterResource(nebulaIconFor(app)), app.label, Modifier.size(size.dp))
            if (app.id == "updates" && UpdateBadge.available) NotificationDot(Modifier.align(Alignment.TopEnd), 12.dp)
        }
        Text(app.label, style = labelStyle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
    }
}

/** Pantalla de inicio del tema Nebula. Las ventanas (Mi PC, video) y Ajustes las dibuja XpDesktop encima. */
@Composable
fun NebulaLauncher(
    apps: Map<String, DeskApp>,
    player: Player?,
    hasMusic: Boolean,
    onOpenPlayer: () -> Unit,
    onClock: () -> Unit,
    onHome: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var drawer by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // la primera vez con este tema se ponen las apps principales en el inicio
        val prefs = ctx.getSharedPreferences("iconos", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("nebula_inicio", false)) {
            NebulaDefaults.filter { it in apps }.forEach { LibraryStore.setDesktopVisible(it, true) }
            prefs.edit().putBoolean("nebula_inicio", true).apply()
        }
    }
    BackHandler(enabled = drawer) { drawer = false }
    val home = LibraryStore.desktopIds.mapNotNull { apps[it] }

    Box(Modifier.fillMaxSize()) {
        NebulaWallpaperImage(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(home, key = { it.id }) { app ->
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        NebulaAppIcon(app, onLongClick = { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Abrir") }, onClick = { menu = false; app.action() })
                            DropdownMenuItem(text = { Text("Quitar del inicio") }, onClick = { menu = false; LibraryStore.setDesktopVisible(app.id, false) })
                        }
                    }
                }
            }
            if (hasMusic && player != null) NebulaMiniPlayer(player, onOpenPlayer)
            NebulaDock(
                onApps = { searching = false; drawer = true },
                onSearch = { searching = true; drawer = true },
                onHome = { drawer = false; onHome() },
                onClock = onClock,
            )
        }
        AnimatedVisibility(drawer, enter = slideInVertically { it / 3 } + fadeIn(), exit = slideOutVertically { it / 3 } + fadeOut()) {
            NebulaAppDrawer(apps, focusSearch = searching, onClose = { drawer = false })
        }
    }
}

@Composable
private fun NebulaMiniPlayer(player: Player, onOpen: () -> Unit) {
    val tick = observePlayer(player)
    @Suppress("UNUSED_EXPRESSION") tick
    val md = player.mediaMetadata
    Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp).fillMaxWidth().glassBar(RoundedCornerShape(22.dp))
        .clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.pv_musica), null, Modifier.size(38.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(md.title?.toString() ?: "Música", color = if (XpColors.dark) Color.White else Color(0xFF0E1A3A), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(md.artist?.toString() ?: "", color = if (XpColors.dark) Color.White.copy(alpha = 0.65f) else Color(0xFF4A5878), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir o pausar", tint = if (XpColors.dark) Color.White else Color(0xFF1E5BFF),
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { if (player.isPlaying) player.pause() else player.play() }.padding(7.dp))
        Icon(Icons.Filled.SkipNext, "Siguiente", tint = if (XpColors.dark) Color.White else Color(0xFF1E5BFF),
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { player.seekToNextMediaItem() }.padding(8.dp))
    }
}

@Composable
private fun NebulaDock(onApps: () -> Unit, onSearch: () -> Unit, onHome: () -> Unit, onClock: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    val es = Locale("es", "MX")
    Row(Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 10.dp).fillMaxWidth()
        .glassBar(RoundedCornerShape(30.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.pv_apps), "Todas las apps", Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onApps))
        Spacer(Modifier.width(10.dp))
        DockButton(Icons.Filled.Search, "Buscar", false, onSearch)
        Spacer(Modifier.width(10.dp))
        Image(painterResource(R.drawable.pv_inicio), "Inicio", Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onHome))
        Spacer(Modifier.weight(1f))
        val ink = if (XpColors.dark) Color.White else Color(0xFF0E1A3A)
        Row(Modifier.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = if (XpColors.dark) 0.07f else 0.4f)).clickable(onClick = onClock)
            .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (XpColors.dark) "🌙" else "🌸", fontSize = 22.sp, modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).padding(6.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(SimpleDateFormat("h:mm a", es).format(Date(now)), color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(SimpleDateFormat("EEE, d MMM", es).format(Date(now)).replaceFirstChar { it.uppercase() }.replace(".", ""),
                    color = ink.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
        .background(if (XpColors.dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f)))
            else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFE6EEFB))))
        .border(1.dp, Color.White.copy(alpha = if (XpColors.dark) 0.18f else 0.95f), RoundedCornerShape(16.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        GradIcon(icon, listOf(Color(0xFF3FA9FF), Color(0xFF1E5BFF)), 26.dp)
    }
}

/** Todas las apps, con buscador. El círculo agrega o quita la app del inicio. */
@Composable
private fun NebulaAppDrawer(apps: Map<String, DeskApp>, focusSearch: Boolean, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { focus.requestFocus() } }
    val list = apps.values.filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }.sortedBy { it.label.lowercase() }
    Box(Modifier.fillMaxSize().background(Color(0xE60B0820)).clickable(remember { MutableInteractionSource() }, null) {}) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar", tint = Color.White, modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp))
                Row(Modifier.weight(1f).glassBar(RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = Color(0xFFB9AEDB), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("Buscar apps", color = Color(0xFFB9AEDB), fontSize = 15.sp)
                        BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(Color(0xFFB98CFF)), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                    }
                }
            }
            Text("Toca el círculo para ponerla o quitarla del inicio", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp))
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp)) {
                items(list, key = { it.id }) { app ->
                    val onHome = app.id in LibraryStore.desktopIds
                    Box {
                        NebulaAppIcon(app.copy(action = { onClose(); app.action() }), size = 58)
                        Icon(if (onHome) Icons.Filled.CheckCircle else Icons.Filled.AddCircle, if (onHome) "Quitar del inicio" else "Poner en el inicio",
                            tint = if (onHome) Color(0xFF8FE3A8) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 6.dp).size(24.dp).clip(CircleShape)
                                .background(Color(0xCC1A1238)).clickable { LibraryStore.setDesktopVisible(app.id, !onHome) })
                    }
                }
            }
        }
    }
}
