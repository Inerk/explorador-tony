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
    var playerOffset by mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
    var playerMinimized by mutableStateOf(false)
}

@Composable
fun XpDesktop(
    roots: List<StorageRoot>,
    onOpenFolder: (File) -> Unit,
    onOpenMusic: () -> Unit,
    onOpenMyPc: () -> Unit,
    onCheckUpdates: () -> Unit,
    onChooseTheme: () -> Unit,
    versionName: String,
    player: Player?,
    onOpenPlayer: () -> Unit,
) {
    var startOpen by remember { mutableStateOf(false) }
    var clockOpen by remember { mutableStateOf(false) }
    val tick = observePlayer(player)
    val hasMusic = player != null && player.mediaItemCount > 0 && tick >= 0
    BackHandler(enabled = startOpen) { startOpen = false }

    val internal = roots.first().file
    fun dir(name: String) = File(internal, name)
    val items = buildList {
        add(DesktopItem("Mi PC", XpIconKind.COMPUTER) { onOpenMyPc() })
        roots.drop(1).forEach { r -> add(DesktopItem(r.name, XpIconKind.SD_CARD) { onOpenFolder(r.file) }) }
        add(DesktopItem("Mis documentos", XpIconKind.DOCUMENTS) { onOpenFolder(dir(Environment.DIRECTORY_DOCUMENTS)) })
        add(DesktopItem("Mi música", XpIconKind.MUSIC) { onOpenMusic() })
        add(DesktopItem("Mis imágenes", XpIconKind.PICTURES) { onOpenFolder(dir(Environment.DIRECTORY_PICTURES)) })
        add(DesktopItem("Cámara", XpIconKind.CAMERA) { onOpenFolder(dir(Environment.DIRECTORY_DCIM)) })
        add(DesktopItem("Mis videos", XpIconKind.VIDEOS) { onOpenFolder(dir(Environment.DIRECTORY_MOVIES)) })
        add(DesktopItem("Descargas", XpIconKind.DOWNLOADS) { onOpenFolder(dir(Environment.DIRECTORY_DOWNLOADS)) })
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
                musicTitle = if (hasMusic) player?.mediaMetadata?.title?.toString() ?: "Música" else null,
                musicWindowOpen = !DesktopState.playerMinimized,
                onMusicTask = { DesktopState.playerMinimized = !DesktopState.playerMinimized },
                onClock = { clockOpen = !clockOpen },
            )
        }

        if (hasMusic && !DesktopState.playerMinimized && player != null) {
            XpMediaWindow(
                player = player,
                onOpen = onOpenPlayer,
                onMinimize = { DesktopState.playerMinimized = true },
                onClose = { player.stop(); player.clearMediaItems() },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 50.dp)
            )
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
                entries = listOf(
                    Triple("Explorador de archivos", Icons.Filled.FolderOpen) { onOpenFolder(internal) },
                    Triple("Música", Icons.Filled.LibraryMusic) { onOpenMusic() },
                    Triple("Imágenes", Icons.Filled.PhotoLibrary) { onOpenFolder(dir(Environment.DIRECTORY_PICTURES)) },
                    Triple("Videos", Icons.Filled.Movie) { onOpenFolder(dir(Environment.DIRECTORY_MOVIES)) },
                ),
                places = listOf(
                    Triple("Mi PC", Icons.Filled.Computer) { onOpenMyPc() },
                    Triple("Mis documentos", Icons.Filled.Description) { onOpenFolder(dir(Environment.DIRECTORY_DOCUMENTS)) },
                    Triple("Descargas", Icons.Filled.Download) { onOpenFolder(dir(Environment.DIRECTORY_DOWNLOADS)) },
                    Triple("Tema", Icons.Filled.Palette) { onChooseTheme() },
                    Triple("Actualizar ($versionName)", Icons.Filled.SystemUpdate) { onCheckUpdates() },
                ),
                onPicked = { startOpen = false }
            )
        }
    }
}

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
    musicTitle: String?,
    musicWindowOpen: Boolean,
    onMusicTask: () -> Unit,
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
        // Botón de la ventana de música en la barra de tareas
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp, vertical = 5.dp)) {
            if (musicTitle != null) {
                Row(
                    Modifier.widthIn(max = 180.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (musicWindowOpen) Brush.verticalGradient(listOf(Color(0xFF1E4FB8), Color(0xFF2A62D4)))
                            else Brush.verticalGradient(listOf(Color(0xFF5A9CF8), Color(0xFF3C7FEA)))
                        )
                        .border(1.dp, Color(0xFF1A3F94), RoundedCornerShape(3.dp))
                        .clickable(onClick = onMusicTask)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(musicTitle, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
) {
    Column(
        modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth(0.9f)
            .shadow(12.dp, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .clickable(enabled = false) {}
    ) {
        // Encabezado con usuario
        Row(
            Modifier.fillMaxWidth().background(XpColors.titleBrush).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFD27A), Color(0xFFE88A1A))))
                    .border(2.dp, Color.White, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(10.dp))
            Text("Tony", style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
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

