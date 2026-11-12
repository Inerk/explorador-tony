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

private data class DesktopItem(val label: String, val icon: XpIconKind, val action: () -> Unit)

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
    onCheckUpdates: () -> Unit,
    onChooseTheme: () -> Unit,
    versionName: String,
    player: Player?,
    onOpenPlayer: () -> Unit,
) {
    var startOpen by remember { mutableStateOf(false) }
    var clockOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
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
    val items = buildList {
        add(DesktopItem("Mi PC", XpIconKind.COMPUTER) { showWindow(myPc) })
        roots.drop(1).forEach { r -> add(DesktopItem(r.name, XpIconKind.SD_CARD) { onOpenFolder(r.file) }) }
        add(DesktopItem("Mis documentos", XpIconKind.DOCUMENTS) { onOpenFolder(dir(Environment.DIRECTORY_DOCUMENTS)) })
        add(DesktopItem("Mi música", XpIconKind.MUSIC) { onOpenMusic() })
        add(DesktopItem("Mis imágenes", XpIconKind.PICTURES) { onOpenGallery() })
        add(DesktopItem("Cámara", XpIconKind.CAMERA) { onOpenFolder(dir(Environment.DIRECTORY_DCIM)) })
        add(DesktopItem("Mis videos", XpIconKind.VIDEOS) { onOpenFolder(dir(Environment.DIRECTORY_MOVIES)) })
        add(DesktopItem("Descargas", XpIconKind.DOWNLOADS) { onOpenFolder(dir(Environment.DIRECTORY_DOWNLOADS)) })
        add(DesktopItem("Panel de control", XpIconKind.SETTINGS) { showWindow(settings) })
    }

    // Botones de la barra de tareas (uno por ventana abierta)
    val tasks = buildList {
        if (myPc.open) add(TaskButton("Mi PC", XpIconKind.COMPUTER, !myPc.minimized) { myPc.minimized = !myPc.minimized })
        if (settings.open) add(TaskButton("Panel de control", XpIconKind.SETTINGS, !settings.minimized) { settings.minimized = !settings.minimized })
        if (hasMusic) add(TaskButton(player?.mediaMetadata?.title?.toString() ?: "Música", XpIconKind.MUSIC, !DesktopState.player.minimized) {
            DesktopState.player.minimized = !DesktopState.player.minimized
        })
    }

    Box(Modifier.fillMaxSize()) {
        XpWallpaper(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            // Íconos del escritorio
            LazyVerticalGrid(
                columns = GridCells.Adaptive(92.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items) { item -> DesktopIcon(item) }
            }
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
        }

        if (clockOpen) {
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
                entries = listOf(
                    Triple("Explorador de archivos", Icons.Filled.FolderOpen) { onOpenFolder(internal) },
                    Triple("Música", Icons.Filled.LibraryMusic) { onOpenMusic() },
                    Triple("Imágenes", Icons.Filled.PhotoLibrary) { onOpenGallery() },
                    Triple("Videos", Icons.Filled.Movie) { onOpenFolder(dir(Environment.DIRECTORY_MOVIES)) },
                ),
                places = listOf(
                    Triple("Mi PC", Icons.Filled.Computer) { showWindow(myPc) },
                    Triple("Mis documentos", Icons.Filled.Description) { onOpenFolder(dir(Environment.DIRECTORY_DOCUMENTS)) },
                    Triple("Panel de control", Icons.Filled.Settings) { showWindow(settings) },
                    Triple("Tema", Icons.Filled.Palette) { onChooseTheme() },
                    Triple("Actualizar ($versionName)", Icons.Filled.SystemUpdate) { onCheckUpdates() },
                ),
                onPicked = { startOpen = false }
            )
        }

        if (accountOpen) {
            UserAccountDialog(onDismiss = { accountOpen = false })
        }
    }
}

data class TaskButton(val title: String, val icon: XpIconKind, val active: Boolean, val onClick: () -> Unit)

/** Fondo original: cielo azul, nubes suaves y una colina verde. */
@Composable
private fun XpWallpaper(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(XpColors.SkyTop, XpColors.SkyBottom), endY = h * 0.75f))
        // nubes
        fun cloud(cx: Float, cy: Float, r: Float, a: Float) {
            val c = Color.White.copy(alpha = a)
            drawOval(c, Offset(cx - r * 1.6f, cy - r * 0.45f), Size(r * 3.2f, r * 0.9f))
            drawOval(c, Offset(cx - r * 0.9f, cy - r * 0.8f), Size(r * 1.6f, r * 1.0f))
            drawOval(c, Offset(cx - r * 0.1f, cy - r * 0.65f), Size(r * 1.4f, r * 0.85f))
        }
        cloud(w * 0.25f, h * 0.18f, w * 0.12f, 0.55f)
        cloud(w * 0.78f, h * 0.10f, w * 0.10f, 0.45f)
        cloud(w * 0.62f, h * 0.30f, w * 0.08f, 0.35f)
        // colina de atrás
        val back = Path().apply {
            moveTo(0f, h * 0.68f)
            cubicTo(w * 0.35f, h * 0.56f, w * 0.7f, h * 0.62f, w, h * 0.58f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(back, Brush.verticalGradient(listOf(Color(0xFF8ACB52), XpColors.HillDark), startY = h * 0.56f, endY = h))
        // colina principal
        val front = Path().apply {
            moveTo(0f, h * 0.80f)
            cubicTo(w * 0.25f, h * 0.62f, w * 0.65f, h * 0.66f, w, h * 0.78f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(front, Brush.verticalGradient(listOf(XpColors.HillLight, XpColors.HillDark), startY = h * 0.64f, endY = h))
    }
}

@Composable
private fun DesktopIcon(item: DesktopItem) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = item.action).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        XpIcon(item.icon, 54.dp)
        Spacer(Modifier.height(2.dp))
        Text(
            item.label,
            style = TextStyle(
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(1.5f, 1.5f), 2f)
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
        // Botón Inicio
        Row(
            Modifier.fillMaxHeight()
                .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                .background(if (startOpen) Brush.verticalGradient(listOf(XpColors.StartBottom, XpColors.StartMid)) else XpColors.startBrush)
                .clickable(onClick = onStart)
                .padding(start = 10.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.FolderOpen, null, tint = XpColors.StartMid, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "Inicio",
                style = TextStyle(
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
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
    entries: List<Triple<String, ImageVector, () -> Unit>>,
    places: List<Triple<String, ImageVector, () -> Unit>>,
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
        Row(Modifier.fillMaxWidth().height(280.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().background(Color.White).padding(vertical = 6.dp)) {
                entries.forEach { (label, icon, action) ->
                    StartEntry(label, icon, Color.Black, FontWeight.SemiBold, iconSize = 30) { onPicked(); action() }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().background(XpColors.SidePanel).padding(vertical = 6.dp)) {
                places.forEach { (label, icon, action) ->
                    StartEntry(label, icon, Color(0xFF0A246A), FontWeight.Bold, iconSize = 22) { onPicked(); action() }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(10.dp).background(XpColors.titleBrush))
    }
}

@Composable
private fun StartEntry(label: String, icon: ImageVector, color: Color, weight: FontWeight, iconSize: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = XpColors.SideHeader, modifier = Modifier.size(iconSize.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 13.sp, fontWeight = weight, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Barra de título estilo XP para las ventanas del explorador. */
@Composable
fun XpTitleBackground(content: @Composable () -> Unit) {
    Box(Modifier.background(XpColors.titleBrush)) { content() }
}

