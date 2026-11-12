package com.tony.explorador

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import kotlin.math.roundToInt

fun isVideoPath(path: String?): Boolean = path != null && File(path).kind() == FileKind.VIDEO

/**
 * Un reproductor sólo puede pintar en una pantallita a la vez. Si se abren dos
 * (la ventanita y el reproductor grande), gana la última y al cerrarla vuelve la anterior.
 */
object VideoSurfaces {
    private val stacks = HashMap<Player, MutableList<TextureView>>()

    fun attach(player: Player, view: TextureView) {
        val s = stacks.getOrPut(player) { mutableListOf() }
        s.remove(view); s.add(view)
        player.setVideoTextureView(view)
    }

    fun detach(player: Player, view: TextureView) {
        val s = stacks[player] ?: return
        val wasTop = s.lastOrNull() == view
        s.remove(view)
        if (!wasTop) return
        val prev = s.lastOrNull()
        if (prev != null) player.setVideoTextureView(prev) else player.clearVideoTextureView(view)
        if (s.isEmpty()) stacks.remove(player)
    }
}

/** El video del reproductor. crop = llena el cuadro recortando; si no, se ve completo. */
@Composable
fun VideoView(player: Player, modifier: Modifier = Modifier, crop: Boolean = true) {
    var videoSize by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val vw = videoSize.width * videoSize.pixelWidthHeightRatio
        val vh = videoSize.height.toFloat()
        val ratio = if (vw > 0 && vh > 0) vw / vh else 16f / 9f
        val boxRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val (w, h) = if ((ratio > boxRatio) == crop) maxHeight * ratio to maxHeight else maxWidth to maxWidth / ratio
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { VideoSurfaces.attach(player, it) } },
            onRelease = { VideoSurfaces.detach(player, it) },
            modifier = Modifier.requiredSize(w, h)
        )
    }
}

/** El video como si se viera a través de un cristal: brillo en diagonal, reflejo que pasa y orilla clara. */
@Composable
fun VideoGlass(player: Player, modifier: Modifier = Modifier) {
    val shine = rememberInfiniteTransition(label = "glass")
    val sweep by shine.animateFloat(
        -0.6f, 1.6f,
        infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "sweep"
    )
    Box(modifier) {
        VideoView(player, Modifier.fillMaxSize(), crop = true)
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // brillo fijo de arriba a la izquierda
            drawRect(Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.30f), 0.35f to Color.White.copy(alpha = 0.06f), 0.36f to Color.Transparent,
                start = Offset(0f, 0f), end = Offset(w, h)
            ))
            // reflejo que va pasando
            val cx = w * sweep
            drawRect(Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                start = Offset(cx - w * 0.18f, 0f), end = Offset(cx + w * 0.18f, h * 0.5f)
            ))
            // sombra suave abajo, como el fondo del vidrio
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)), startY = h * 0.65f, endY = h))
            // orilla del cristal
            drawRoundRect(Color.White.copy(alpha = 0.35f), style = Stroke(2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()))
        }
    }
}

/** El video que se está viendo, para que pueda seguir en una ventanita al ir al inicio. */
object VideoSession {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var files: List<File> = emptyList()
        private set
    /** true = se ve en la ventanita del escritorio. */
    var floating by mutableStateOf(false)
    val window = WinState(300.dp, 250.dp, minWidth = 210.dp, minHeight = 190.dp)

    fun obtain(context: Context, list: List<File>, index: Int, resume: Boolean): ExoPlayer {
        val current = player
        if (resume && current != null) return current
        current?.release()
        files = list
        val p = ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItems(list.map { f ->
                MediaItem.Builder().setUri(Uri.fromFile(f))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(f.nameWithoutExtension).build()).build()
            }, index, 0L)
            prepare()
            playWhenReady = true
        }
        player = p
        return p
    }

    fun release() {
        floating = false
        window.minimized = false
        player?.release()
        player = null
    }

    fun currentTitle(): String =
        player?.mediaMetadata?.title?.toString() ?: files.getOrNull(player?.currentMediaItemIndex ?: 0)?.nameWithoutExtension ?: "Video"
}

/** Lo de adentro de la ventanita de video: el video y unos botones. */
@Composable
fun VideoMiniContent(player: Player, onExpand: () -> Unit, dark: Boolean = true) {
    val tick = observePlayer(player)
    val fg = if (dark) Color.White else Color(0xFF1B2A55)
    Column(Modifier.fillMaxSize().background(if (dark) Color(0xFF101114) else Color(0xFFECE9D8))) {
        Box(Modifier.weight(1f).fillMaxWidth().clickable { if (player.isPlaying) player.pause() else player.play() }) {
            VideoView(player, Modifier.fillMaxSize(), crop = false)
        }
        val dur = player.duration.coerceAtLeast(0L)
        val pos = player.currentPosition + tick * 0L
        Box(Modifier.fillMaxWidth().height(3.dp).background(fg.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).height(3.dp).background(Color(0xFF3D8BFF)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
                Box(Modifier.size(34.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
                    Icon(icon, desc, tint = fg.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(22.dp))
                }
            }
            Btn(Icons.Filled.SkipPrevious, "Anterior", player.hasPreviousMediaItem()) { player.seekToPreviousMediaItem() }
            Btn(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Reproducir") {
                if (player.isPlaying) player.pause() else player.play()
            }
            Btn(Icons.Filled.SkipNext, "Siguiente", player.hasNextMediaItem()) { player.seekToNextMediaItem() }
            Text("${formatTime(pos)} / ${formatTime(dur)}", color = fg.copy(alpha = 0.7f), fontSize = 11.sp,
                maxLines = 1, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Btn(Icons.Filled.OpenInFull, "Pantalla completa", onClick = onExpand)
        }
    }
}

/** Ventanita flotante para el tema Moderno (en XP va dentro del escritorio). */
@Composable
fun FloatingVideoWindow(player: Player, onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.navigationBarsPadding().padding(12.dp)
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .width(260.dp).height(200.dp)
            .shadow(18.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); offset += d } }
    ) {
        VideoMiniContent(player, onExpand)
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White, modifier = Modifier.size(18.dp)) }
        Text(VideoSession.currentTitle(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 10.dp, end = 40.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
