package com.tony.explorador

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExploradorTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun ExploradorTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    androidx.compose.runtime.SideEffect { XpColors.systemDark = dark }
    fun C(v: Long) = androidx.compose.ui.graphics.Color(v)
    val scheme = when {
        LibraryStore.theme == AppTheme.XP && XpColors.dark -> darkColorScheme(
            primary = C(0xFFB99CFF), onPrimary = C(0xFF1A1145),
            primaryContainer = C(0xFF3A2A7A), onPrimaryContainer = C(0xFFEDE6FF),
            secondary = C(0xFFFF8FDD), onSecondary = C(0xFF2A0F2A),
            secondaryContainer = C(0xFF3A2A7A), onSecondaryContainer = C(0xFFEDE6FF),
            background = C(0xFF120E26), onBackground = C(0xFFF1ECFF),
            surface = C(0xFF120E26), onSurface = C(0xFFF1ECFF),
            surfaceVariant = C(0xFF241C4A), onSurfaceVariant = C(0xFFB5ABD6),
            surfaceContainer = C(0xFF1B1538), surfaceContainerHigh = C(0xFF241C4A), surfaceContainerHighest = C(0xFF2C2358),
            surfaceContainerLow = C(0xFF171232), outline = C(0xFF6E62A0), outlineVariant = C(0xFF3A3166),
        )
        LibraryStore.theme == AppTheme.XP -> lightColorScheme(
            primary = XpColors.SelectBlue,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFDCD0FA),
            secondary = XpColors.StartMid,
            secondaryContainer = androidx.compose.ui.graphics.Color(0xFFDCD0FA),
            background = androidx.compose.ui.graphics.Color(0xFFFAF8FF),
            surface = androidx.compose.ui.graphics.Color(0xFFFAF8FF),
            surfaceVariant = XpColors.Window,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun hasStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

/** Lo que se muestra encima del explorador. */
sealed interface Overlay {
    data class Images(val files: List<File>, val index: Int) : Overlay
    data class Video(val files: List<File>, val index: Int, val resume: Boolean = false) : Overlay
    data object Music : Overlay
    data object Library : Overlay
    data object CoverFlow : Overlay
    data object MyPc : Overlay
    data object Gallery : Overlay
    data object Videos : Overlay
    data class Pdf(val file: File) : Overlay
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasStorageAccess(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = hasStorageAccess(context)
    }

    if (!granted) {
        PermissionScreen(onGranted = { granted = hasStorageAccess(context) })
        return
    }

    val controller = rememberMediaController()
    val overlays = remember { androidx.compose.runtime.mutableStateListOf<Overlay>() }
    val overlayStates = remember { androidx.compose.runtime.mutableStateMapOf<Overlay, androidx.compose.animation.core.MutableTransitionState<Boolean>>() }
    fun open(o: Overlay) {
        overlays.remove(o); overlays.add(o)
        overlayStates[o] = androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
    }
    /** Cierra con animación; se quita de la lista cuando termina de salir. */
    fun close(o: Overlay) {
        val st = overlayStates[o]
        if (st != null) st.targetState = false else overlays.remove(o)
    }
    var choosingTheme by remember { mutableStateOf(false) }
    var homeSignal by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var libraryReset by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    /** Manda videos al reproductor de música: se oyen como canción y el video se ve en la portada. */
    fun playVideosAsMusic(files: List<File>, index: Int, position: Long) {
        val c = controller ?: return
        VideoSession.release()
        c.shuffleModeEnabled = false
        c.setMediaItems(mediaItemsFor(files), index, position)
        c.prepare(); c.play()
        open(Overlay.Music)
    }
    fun expandFloatingVideo() {
        val p = VideoSession.player ?: return
        open(Overlay.Video(VideoSession.files, p.currentMediaItemIndex.coerceAtLeast(0), resume = true))
    }
    fun goHome() { overlays.toList().forEach { close(it) }; homeSignal++ }
    var folderRequest by remember { mutableStateOf<File?>(null) }
    var libraryRequest by remember { mutableStateOf<Pair<String, String>?>(null) }
    fun goTo(kind: String, value: String) {
        if (kind == "folder") {
            overlays.toList().forEach { close(it) }
            folderRequest = File(value)
        } else {
            overlays.filter { it == Overlay.Music || it == Overlay.CoverFlow }.forEach { close(it) }
            libraryRequest = kind to value
            if (Overlay.Library !in overlays) open(Overlay.Library)
        }
    }
    /** Al empezar a deslizar el reproductor: la biblioteca se pone debajo para que se vea. */
    var peekedLibrary by remember { mutableStateOf(false) }
    var libMenuOpen by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var libMenuClose by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    fun peekMusicMenu() {
        libMenuOpen++
        if (Overlay.Library !in overlays) {
            val i = overlays.indexOf(Overlay.Music).coerceAtLeast(0)
            overlays.add(i, Overlay.Library)
            overlayStates[Overlay.Library] = androidx.compose.animation.core.MutableTransitionState(true)
            peekedLibrary = true
        }
    }
    fun cancelMusicMenu() {
        if (peekedLibrary) { overlays.remove(Overlay.Library); overlayStates.remove(Overlay.Library) }
        else libMenuClose++
        peekedLibrary = false
    }
    /** El reproductor ya salió de la pantalla deslizándose: se quita sin otra animación. */
    fun finishMusicMenu() {
        peekedLibrary = false
        listOf(Overlay.Music, Overlay.CoverFlow).forEach { overlays.remove(it); overlayStates.remove(it) }
    }
    fun musicMenu() {
        overlays.filter { it == Overlay.Music || it == Overlay.CoverFlow }.forEach { close(it) }
        libraryReset++
        open(Overlay.Library)
    }

    // ── Actualizaciones desde GitHub ──
    val scope = rememberCoroutineScope()
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val versionName = remember { currentVersionName(context) }

    fun checkUpdates(manual: Boolean) {
        scope.launch {
            if (manual) updateState = UpdateState.Checking
            try {
                val info = fetchLatestRelease()
                UpdateBadge.available = info.versionCode > currentVersionCode(context)
                updateState = when {
                    info.versionCode > currentVersionCode(context) -> UpdateState.Available(info)
                    manual -> UpdateState.UpToDate
                    else -> UpdateState.Idle
                }
            } catch (e: Exception) {
                if (manual) updateState = UpdateState.Error("Revisa tu conexión a internet.\n(${e.message})")
            }
        }
    }

    fun installOrAsk(apk: java.io.File) {
        if (canInstallApks(context)) {
            updateState = UpdateState.Idle
            installApk(context, apk)
        } else {
            updateState = UpdateState.NeedsPermission(apk)
            openInstallPermissionSettings(context)
        }
    }

    LaunchedEffect(Unit) { checkUpdates(manual = false) }
    // búsqueda automática de carátulas (si está activada), un ratito después de abrir
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(20_000)
        if (CoverScanner.auto && LibraryStore.songs.isNotEmpty()) CoverScanner.start()
        if (AnimatedScanner.auto && LibraryStore.songs.isNotEmpty()) AnimatedScanner.start()
    }

    Box(Modifier.fillMaxSize()) {
        BrowserScreen(
            controller = controller,
            onOpenImages = { files, index -> open(Overlay.Images(files, index)) },
            onOpenVideo = { files, index ->
                controller?.pause()
                open(Overlay.Video(files, index))
            },
            onPlayMusic = { files, index ->
                controller?.let { playMusic(it, files, index) }
                open(Overlay.Music)
            },
            onOpenMusicPlayer = { open(Overlay.Music) },
            onOpenLibrary = { open(Overlay.Library) },
            onChooseTheme = { choosingTheme = true },
            homeSignal = homeSignal,
            onOpenGallery = { open(Overlay.Gallery) },
            onOpenVideos = { open(Overlay.Videos) },
            onOpenPdf = { open(Overlay.Pdf(it)) },
            onPlayAsMusic = { files, index -> playVideosAsMusic(files, index, 0L) },
            onExpandVideo = { expandFloatingVideo() },
            folderRequest = folderRequest,
            onFolderHandled = { folderRequest = null },
            versionName = versionName,
            onCheckUpdates = { checkUpdates(manual = true) },
        )

        // ventanita del video al ir al inicio (en XP vive dentro del escritorio)
        val floatingVideo = VideoSession.player
        if (VideoSession.floating && floatingVideo != null && LibraryStore.theme != AppTheme.XP) {
            FloatingVideoWindow(
                floatingVideo,
                onExpand = { expandFloatingVideo() },
                onClose = { VideoSession.release() },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(bottom = 64.dp)
            )
        }

        overlays.toList().forEach { o ->
            androidx.compose.runtime.key(o) {
                val st = overlayStates[o] ?: remember { androidx.compose.animation.core.MutableTransitionState(true) }
                LaunchedEffect(st.currentState, st.isIdle) {
                    if (st.isIdle && !st.currentState && !st.targetState) {
                        overlays.remove(o); overlayStates.remove(o)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = st,
                    enter = enterFor(o),
                    exit = exitFor(o),
                ) {
                when (o) {
                    is Overlay.Images -> ImageViewerScreen(o.files, o.index, onClose = { close(o) }, onHome = { goHome() })
                    is Overlay.Video -> VideoPlayerScreen(o.files, o.index, onClose = { close(o) }, onHome = { goHome() },
                        resume = o.resume,
                        onPlayAsMusic = { files, index, pos -> playVideosAsMusic(files, index, pos); close(o) })
                    Overlay.Music -> if (controller != null) {
                        MusicPlayerScreen(controller, onClose = { close(o) },
                            onOpenCoverFlow = { open(Overlay.CoverFlow) },
                            onHome = { goHome() },
                            onMusicMenu = { musicMenu() },
                            onPeekMenu = { peekMusicMenu() },
                            onMenuCancel = { cancelMusicMenu() },
                            onMenuDone = { finishMusicMenu() },
                            onGoTo = { k, v -> goTo(k, v) })
                    }
                    Overlay.CoverFlow -> CoverFlowScreen(
                        startPath = controller?.currentMediaItem?.mediaId,
                        onPlay = { songs, index ->
                            controller?.let {
                                it.shuffleModeEnabled = false
                                playMusic(it, songs.map { s -> s.file }, index)
                            }
                            close(o)
                            open(Overlay.Music)
                        },
                        onClose = { close(o) },
                        onHome = { goHome() }
                    )
                    Overlay.Gallery -> GalleryScreen(
                        onOpenImages = { files, index -> open(Overlay.Images(files, index)) },
                        onOpenVideo = { files, index -> controller?.pause(); open(Overlay.Video(files, index)) },
                        onClose = { close(o) },
                        onHome = { goHome() }
                    )
                    Overlay.Videos -> VideosScreen(
                        onOpenVideo = { files, index -> controller?.pause(); open(Overlay.Video(files, index)) },
                        onPlayAsMusic = { files, index -> playVideosAsMusic(files, index, 0L) },
                        onClose = { close(o) },
                        onHome = { goHome() }
                    )
                    is Overlay.Pdf -> PdfViewerScreen(o.file, onClose = { close(o) }, onHome = { goHome() })
                    Overlay.MyPc -> MyComputerScreen(
                        onOpenFolder = { f -> close(o); folderRequest = f },
                        onOpenMusic = { close(o); open(Overlay.Library) },
                        onClose = { close(o) },
                        onHome = { goHome() }
                    )
                    Overlay.Library -> LibraryScreen(
                        onPlay = { songs, index, shuffle ->
                            controller?.let {
                                it.shuffleModeEnabled = shuffle
                                playMusic(it, songs.map { s -> s.file }, index)
                            }
                            open(Overlay.Music)
                        },
                        onClose = { close(o) },
                        miniPlayer = { if (controller != null) MiniPlayer(controller, onOpen = { open(Overlay.Music) }) },
                        onHome = { goHome() },
                        resetSignal = libraryReset,
                        openRequest = libraryRequest,
                        onRequestHandled = { libraryRequest = null },
                        onOpenFolder = { goTo("folder", it) },
                        menuOpenSignal = libMenuOpen,
                        menuCloseSignal = libMenuClose,
                    )
                }
                }
            }
        }

        // si la vez pasada la app se cerró sola, muestra el error para poder mandarlo
        var crashText by remember { mutableStateOf(runCatching {
            File(context.filesDir, "ultimo_error.txt").takeIf { it.isFile }?.readText() }.getOrNull()) }
        crashText?.let { txt ->
            val dismiss = { runCatching { File(context.filesDir, "ultimo_error.txt").delete() }; crashText = null }
            ThemedDialog("La app se cerró la vez pasada", { dismiss() }, listOf(
                DialogButton("Cerrar") { dismiss() },
                DialogButton("Copiar error", true) {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Error Explorador Tony", txt))
                    android.widget.Toast.makeText(context, "Copiado. Pégaselo a Claude.", android.widget.Toast.LENGTH_LONG).show()
                    dismiss()
                })) {
                Column {
                    Text("Copia este texto y mándamelo para arreglarlo:")
                    Text(txt.take(700), fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (choosingTheme) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { choosingTheme = false },
                title = { Text("Tema") },
                text = {
                    Column {
                        AppTheme.entries.forEach { t ->
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth()
                                    .clickable { LibraryStore.changeTheme(t); choosingTheme = false }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = LibraryStore.theme == t,
                                    onClick = { LibraryStore.changeTheme(t); choosingTheme = false })
                                Text(t.label)
                            }
                        }
                    }
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { choosingTheme = false }) { Text("Cerrar") } }
            )
        }

        UpdateDialog(
            state = updateState,
            currentName = versionName,
            onDownload = { info ->
                scope.launch {
                    updateState = UpdateState.Downloading(info, 0f)
                    try {
                        var last = -1
                        val apk = downloadApk(context, info) { p ->
                            val pct = (p * 100).toInt()
                            if (pct != last) { last = pct; updateState = UpdateState.Downloading(info, p) }
                        }
                        installOrAsk(apk)
                    } catch (e: Exception) {
                        updateState = UpdateState.Error("La descarga falló. Intenta de nuevo.\n(${e.message})")
                    }
                }
            },
            onInstall = { apk -> installOrAsk(apk) },
            onDismiss = { updateState = UpdateState.Idle },
        )
    }
}

@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { runCatching { controller = future.get() } },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onGranted() }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Explorador Tony", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Para ver y administrar tus archivos, la app necesita el permiso de acceso a todos los archivos.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                runCatching { context.startActivity(intent) }.onFailure {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else {
                legacyLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }) { Text("Dar permiso") }
    }
}

// ───── Animaciones al abrir y cerrar pantallas ─────

private val smooth = androidx.compose.animation.core.spring<Float>(
    dampingRatio = 0.9f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
)
private val smoothOffset = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
    dampingRatio = 0.9f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
)

private fun enterFor(o: Overlay): androidx.compose.animation.EnterTransition = when (o) {
    // el reproductor "crece" desde el mini reproductor de abajo
    Overlay.Music -> androidx.compose.animation.slideInVertically(smoothOffset) { it / 3 } +
        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
        androidx.compose.animation.scaleIn(smooth, initialScale = 0.86f,
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f))
    // secciones: entran deslizándose de lado
    Overlay.Library, Overlay.MyPc, Overlay.Gallery, Overlay.Videos -> androidx.compose.animation.slideInHorizontally(smoothOffset) { it / 3 } +
        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
        androidx.compose.animation.scaleIn(smooth, initialScale = 0.95f)
    // fotos, videos, carrusel: zoom desde el centro
    else -> androidx.compose.animation.scaleIn(smooth, initialScale = 0.82f) +
        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220))
}

private fun exitFor(o: Overlay): androidx.compose.animation.ExitTransition = when (o) {
    Overlay.Music -> androidx.compose.animation.slideOutVertically(smoothOffset) { it / 3 } +
        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
        androidx.compose.animation.scaleOut(smooth, targetScale = 0.86f,
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f))
    Overlay.Library, Overlay.MyPc, Overlay.Gallery, Overlay.Videos -> androidx.compose.animation.slideOutHorizontally(smoothOffset) { it / 3 } +
        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
        androidx.compose.animation.scaleOut(smooth, targetScale = 0.95f)
    else -> androidx.compose.animation.scaleOut(smooth, targetScale = 0.82f) +
        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
}
