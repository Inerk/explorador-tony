package com.tony.explorador

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/** Lo que el escritorio recuerda aunque entres a otras pantallas (posición de la ventana, etc.). */
object DesktopState {
    val player = WinState(310.dp, 340.dp, minWidth = 250.dp, minHeight = 270.dp)
    val myPc = WinState(330.dp, 440.dp, minWidth = 240.dp, minHeight = 220.dp)
    val settings = WinState(320.dp, 420.dp, minWidth = 240.dp, minHeight = 220.dp)
}

@Composable
fun XpDesktop(
    roots: List<StorageRoot>,
    onOpenFolder: (File) -> Unit,
    onOpenMusic: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenVideos: () -> Unit,
    onExpandVideo: () -> Unit,
    onCheckUpdates: () -> Unit,
    onChooseTheme: () -> Unit,
    versionName: String,
    player: Player?,
    onOpenPlayer: () -> Unit,
) {
    var startOpen by remember { mutableStateOf(false) }
    var clockOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var wallpaperOpen by remember { mutableStateOf(false) }
    val tick = observePlayer(player)
    val hasMusic = player != null && player.mediaItemCount > 0 && tick >= 0
    BackHandler(enabled = startOpen) { startOpen = false }
    val myPc = DesktopState.myPc
    val settings = DesktopState.settings
    BackHandler(enabled = !startOpen && (myPc.open && !myPc.minimized || settings.open && !settings.minimized)) {
        if (settings.open && !settings.minimized) settings.open = false else myPc.open = false
    }

    fun showWindow(w: WinState) { w.open = true; w.minimized = false }

    val internal = roots.first().file
    fun dir(name: String) = File(internal, name)
    // Todo lo que puede ir en el escritorio (tú eliges cuáles se ven)
    val apps = linkedMapOf<String, DeskApp>().apply {
        fun add(a: DeskApp) { put(a.id, a) }
        add(DeskApp("mypc", "Mi PC", XpIconKind.COMPUTER) { showWindow(myPc) })
        roots.drop(1).firstOrNull()?.let { r -> add(DeskApp("sd", r.name, XpIconKind.SD_CARD) { onOpenFolder(r.file) }) }
        add(DeskApp("docs", "Mis documentos", XpIconKind.DOCUMENTS) { onOpenFolder(dir(Environment.DIRECTORY_DOCUMENTS)) })
        add(DeskApp("music", "Música", XpIconKind.PLAYER) { onOpenMusic() })
        add(DeskApp("pictures", "Galería", XpIconKind.GALLERY) { onOpenGallery() })
        add(DeskApp("camera", "Cámara", XpIconKind.CAMERA) { onOpenFolder(dir(Environment.DIRECTORY_DCIM)) })
        add(DeskApp("videos", "Videos", XpIconKind.VIDEO_PLAYER) { onOpenVideos() })
        add(DeskApp("downloads", "Descargas", XpIconKind.DOWNLOADS) { onOpenFolder(dir(Environment.DIRECTORY_DOWNLOADS)) })
        add(DeskApp("panel", "Panel de control", XpIconKind.SETTINGS) { showWindow(settings) })
        add(DeskApp("explorer", "Explorador de archivos", XpIconKind.FOLDER) { onOpenFolder(internal) })
        add(DeskApp("updates", "Actualizar", XpIconKind.DRIVE) { onCheckUpdates() })
    }
    LaunchedEffect(Unit) {
        LibraryStore.ensureDesktopDefaults(
            listOf("mypc", "sd", "docs", "music", "pictures", "camera", "videos", "downloads", "panel").filter { it in apps }
        )
    }

    // Botones de la barra de tareas (uno por ventana abierta)
    val tasks = buildList {
        if (myPc.open) add(TaskButton("Mi PC", XpIconKind.COMPUTER, !myPc.minimized) { myPc.minimized = !myPc.minimized })
        if (settings.open) add(TaskButton("Panel de control", XpIconKind.SETTINGS, !settings.minimized) { settings.minimized = !settings.minimized })
        if (hasMusic) add(TaskButton(player?.mediaMetadata?.title?.toString() ?: "Música", XpIconKind.PLAYER, !DesktopState.player.minimized) {
            DesktopState.player.minimized = !DesktopState.player.minimized
        })
        if (VideoSession.floating && VideoSession.player != null) add(TaskButton(VideoSession.currentTitle(), XpIconKind.VIDEO_PLAYER, !VideoSession.window.minimized) {
            VideoSession.window.minimized = !VideoSession.window.minimized
        })
    }

    Box(Modifier.fillMaxSize()) {
        WallpaperView(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            // Íconos del escritorio
            DesktopIconsArea(apps, Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(8.dp),
                onChangeWallpaper = { wallpaperOpen = true })
            Taskbar(
                onStart = { startOpen = !startOpen },
                startOpen = startOpen,
                tasks = tasks,
                onClock = { clockOpen = !clockOpen },
            )
        }

        // Capa de ventanas (arriba de la barra de tareas)
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 42.dp)) {
            if (myPc.open && !myPc.minimized) {
                XpWindowFrame(
                    state = myPc,
                    title = "Mi PC",
                    icon = { XpIcon(XpIconKind.COMPUTER, 18.dp) },
                    onClose = { myPc.open = false; myPc.maximized = false },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = if (myPc.maximized) 0.dp else 40.dp),
                ) {
                    MyComputerContent(
                        onOpenFolder = { f -> myPc.minimized = true; onOpenFolder(f) },
                        onOpenMusic = { myPc.minimized = true; onOpenMusic() },
                    )
                }
            }
            if (settings.open && !settings.minimized) {
                XpWindowFrame(
                    state = settings,
                    title = "Panel de control",
                    icon = { XpIcon(XpIconKind.SETTINGS, 18.dp) },
                    onClose = { settings.open = false; settings.maximized = false },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = if (settings.maximized) 0.dp else 60.dp),
                ) {
                    ControlPanelContent(
                        versionName = versionName,
                        onTheme = onChooseTheme,
                        onAccount = { accountOpen = true },
                        onMusicFolders = onOpenMusic,
                        onUpdates = onCheckUpdates,
                        onWallpaper = { wallpaperOpen = true },
                    )
                }
            }
            if (hasMusic && !DesktopState.player.minimized && player != null) {
                XpMediaWindow(
                    player = player,
                    onOpen = onOpenPlayer,
                    onClose = { player.stop(); player.clearMediaItems() },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp)
                )
            }
            // Ventanita del video que se estaba viendo
            val video = VideoSession.player
            if (VideoSession.floating && video != null && !VideoSession.window.minimized) {
                XpWindowFrame(
                    state = VideoSession.window,
                    title = VideoSession.currentTitle(),
                    icon = { XpIcon(XpIconKind.VIDEO_PLAYER, 18.dp) },
                    onClose = { VideoSession.release() },
                    onMaximize = onExpandVideo,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp),
                ) {
                    VideoMiniContent(video, onExpand = onExpandVideo)
                }
            }
        }

        if (clockOpen) {
            // tocar fuera del reloj lo cierra
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { clockOpen = false }
            )
            XpClockWindow(
                onClose = { clockOpen = false },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 6.dp, bottom = 48.dp)
            )
        }

        if (startOpen) {
            // tocar fuera cierra el menú
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { startOpen = false }
            )
            StartMenu(
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(bottom = 42.dp),
                onUserClick = { startOpen = false; accountOpen = true },
                entries = listOf("explorer", "music", "pictures", "videos", "camera").mapNotNull { apps[it] },
                places = listOf("mypc", "docs", "downloads", "panel", "updates").mapNotNull { apps[it] },
                onPicked = { startOpen = false }
            )
        }

        if (accountOpen) {
            UserAccountDialog(onDismiss = { accountOpen = false })
        }
        if (wallpaperOpen) WallpaperDialog(onDismiss = { wallpaperOpen = false })
    }
}

data class TaskButton(val title: String, val icon: XpIconKind, val active: Boolean, val onClick: () -> Unit)

private fun Color.darker(): Color = Color(red * 0.45f, green * 0.45f, blue * 0.45f, alpha)

@Composable
private fun Taskbar(
    onStart: () -> Unit,
    startOpen: Boolean,
    tasks: List<TaskButton>,
    onClock: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(XpColors.taskbarBrush).navigationBarsPadding().height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón Inicio (corto, con esfera brillante)
        Row(
            Modifier.fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                .background(if (startOpen) Brush.verticalGradient(listOf(XpColors.StartBottom, XpColors.StartMid)) else XpColors.startBrush)
                .clickable(onClick = onStart)
                .padding(start = 6.dp, end = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                StartOrb(Modifier.size(26.dp))
                if (UpdateBadge.available) NotificationDot(Modifier.align(Alignment.TopEnd), 10.dp)
            }
            Spacer(Modifier.width(5.dp))
            Text(
                "Inicio",
                style = TextStyle(
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
                    shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(1f, 1f), 2f)
                )
            )
        }
        // Botones de las ventanas abiertas
        Row(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 4.dp, vertical = 5.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tasks.forEach { t ->
                Row(
                    Modifier.padding(end = 3.dp).widthIn(max = 150.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (t.active) Brush.verticalGradient(listOf(Color(0xFF1E4FB8), Color(0xFF2A62D4)))
                            else Brush.verticalGradient(listOf(Color(0xFF5A9CF8), Color(0xFF3C7FEA)))
                        )
                        .border(1.dp, Color(0xFF1A3F94), RoundedCornerShape(3.dp))
                        .clickable(onClick = t.onClick)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    XpIcon(t.icon, 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(t.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Bandeja con reloj (toca para ver reloj y calendario)
        Box(
            Modifier.fillMaxHeight().background(XpColors.trayBrush)
                .border(width = 1.dp, color = Color(0xFF0F5FBF)),
            contentAlignment = Alignment.Center
        ) {
            TrayClock(onClick = onClock, modifier = Modifier.fillMaxHeight())
        }
    }
}

@Composable
private fun StartMenu(
    modifier: Modifier,
    entries: List<DeskApp>,
    places: List<DeskApp>,
    onPicked: () -> Unit,
    onUserClick: () -> Unit,
) {
    Column(
        modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth(0.9f)
            .shadow(12.dp, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .clickable(enabled = false) {}
    ) {
        // Encabezado con usuario (tócalo para cambiar foto y nombre)
        Row(
            Modifier.fillMaxWidth().background(XpColors.titleBrush).clickable(onClick = onUserClick).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(46.dp)
            Spacer(Modifier.width(10.dp))
            Text(LibraryStore.userName, style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(1f, 1f), 2f)))
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(XpColors.StartMenuOrange))
        Row(Modifier.fillMaxWidth().height(300.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().background(Color.White).padding(vertical = 6.dp)) {
                entries.forEach { app ->
                    StartEntry(app, Color.Black, FontWeight.SemiBold, iconSize = 36) { onPicked(); app.action() }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().background(XpColors.SidePanel).padding(vertical = 6.dp)) {
                places.forEach { app ->
                    StartEntry(app, Color(0xFF0A246A), FontWeight.Bold, iconSize = 28) { onPicked(); app.action() }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(10.dp).background(XpColors.titleBrush))
    }
}

/** Opción del menú Inicio. Déjala presionada para crear un acceso directo en el escritorio. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StartEntry(app: DeskApp, color: Color, weight: FontWeight, iconSize: Int, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Box {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                XpIcon(app.kind, iconSize.dp)
                if (app.id == "updates" && UpdateBadge.available) NotificationDot(Modifier.align(Alignment.TopEnd), 12.dp)
            }
            Spacer(Modifier.width(8.dp))
            Text(app.label, color = color, fontSize = 13.sp, fontWeight = weight, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Abrir") },
                onClick = { menu = false; onClick() })
            DropdownMenuItem(
                text = { Text("Crear acceso directo en el escritorio") },
                onClick = {
                    menu = false
                    val already = app.id in LibraryStore.desktopIds
                    LibraryStore.addDesktopShortcut(app.id)
                    android.widget.Toast.makeText(
                        context,
                        if (already) "\"${app.label}\" ya está en el escritorio" else "Acceso directo creado en el escritorio",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                })
        }
    }
}

/** Barra de título estilo XP para las ventanas del explorador. */
@Composable
fun XpTitleBackground(content: @Composable () -> Unit) {
    Box(Modifier.background(XpColors.titleBrush)) { content() }
}


/** Esfera del botón Inicio: cristal azul con un destello blanco de cuatro puntas. */
@Composable
private fun StartOrb(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val r = size.minDimension / 2
        val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.3f), r, c + androidx.compose.ui.geometry.Offset(0f, 1.5f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFBFE6FF), Color(0xFF3D9BFF), Color(0xFF0B4FB8)),
            center = c - androidx.compose.ui.geometry.Offset(r * 0.3f, r * 0.35f), radius = r * 1.4f), r, c)
        drawCircle(Color.White.copy(alpha = 0.85f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        // destello
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, c.y - r * 0.62f)
            quadraticBezierTo(c.x + r * 0.08f, c.y - r * 0.08f, c.x + r * 0.62f, c.y)
            quadraticBezierTo(c.x + r * 0.08f, c.y + r * 0.08f, c.x, c.y + r * 0.62f)
            quadraticBezierTo(c.x - r * 0.08f, c.y + r * 0.08f, c.x - r * 0.62f, c.y)
            quadraticBezierTo(c.x - r * 0.08f, c.y - r * 0.08f, c.x, c.y - r * 0.62f)
            close()
        }
        drawPath(p, Color.White)
        drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.Transparent), startY = c.y - r, endY = c.y),
            c - androidx.compose.ui.geometry.Offset(r * 0.6f, r * 0.92f), androidx.compose.ui.geometry.Size(r * 1.2f, r * 0.7f))
    }
}
