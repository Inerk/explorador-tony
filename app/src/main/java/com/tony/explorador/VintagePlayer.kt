package com.tony.explorador

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ───────────────────────── Datos para el tocadiscos ─────────────────────────

/** Qué tan adentro del disco va la pluma: de la orilla al centro conforme avanza la canción. */
@Suppress("UNUSED_PARAMETER")
private fun armPosition(info: TrackInfo?, path: String?, progress: Float): Float = progress.coerceIn(0f, 1f)

/** Las canciones que siguen y las que ya sonaron, en el orden real (respeta aleatorio). */
private fun neighbours(player: Player, forward: Boolean, max: Int): List<Int> {
    val tl = player.currentTimeline
    if (tl.isEmpty) return emptyList()
    val out = mutableListOf<Int>()
    var i = player.currentMediaItemIndex
    repeat(max) {
        i = if (forward) tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        else tl.getPreviousWindowIndex(i, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        if (i == androidx.media3.common.C.INDEX_UNSET) return out
        out += i
    }
    return out
}

private fun remainingCount(player: Player): Int {
    val tl = player.currentTimeline
    if (tl.isEmpty) return 0
    var n = 0
    var i = player.currentMediaItemIndex
    while (n < 9999) {
        i = tl.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        if (i == androidx.media3.common.C.INDEX_UNSET) break
        n++
    }
    return n
}

// ───────────────────────── Pantalla del tocadiscos ─────────────────────────

@Composable
fun VintagePlayerScreen(
    player: Player,
    onClose: () -> Unit,
    onOpenCoverFlow: () -> Unit,
    onHome: () -> Unit,
    onGoTo: (String, String) -> Unit,
) {
    BackHandler(onBack = onClose)
    val tick = observePlayer(player, 250L)
    val path = player.currentMediaItem?.mediaId
    val info by rememberTrackInfo(path)
    var lyricsMode by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val title = info?.title ?: player.mediaMetadata.title?.toString() ?: "Sin canción"
    val artist = info?.artist ?: ""
    val album = info?.album ?: LibraryStore.songFor(path ?: "")?.album
    val duration = player.duration.let { if (it <= 0) 0L else it }
    val position = player.currentPosition + tick * 0L
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val lyrics = info?.lyrics.orEmpty()
    val song = path?.let { LibraryStore.songFor(it) }
    val animatedCover by rememberAnimatedCover(path, song?.album ?: album)
    val albumCount = song?.let { s -> LibraryStore.songs.count { it.album == s.album } } ?: 0
    val trackLabel = info?.trackNumber?.let { n ->
        "Pista $n" + ((info?.trackTotal ?: albumCount.takeIf { it > 1 })?.let { " de $it" } ?: "")
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) {}) {
        WoodTable(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 6.dp)) {
            // Barra superior
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Cerrar", tint = Vintage.Cream, modifier = Modifier.size(30.dp)) }
                HomeButton(onHome)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("~ VINTAGE ~", color = Vintage.Brass, fontFamily = FontFamily.Serif, fontSize = 13.sp, letterSpacing = 3.sp)
                    if (album != null) Text(album, color = Vintage.Cream, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { lyricsMode = !lyricsMode }) {
                    Icon(Icons.Filled.Lyrics, "Letra", tint = if (lyricsMode) Vintage.Brass else Vintage.Cream)
                }
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Más opciones", tint = Vintage.Cream) }
            }

            Spacer(Modifier.height(6.dp))
            if (lyricsMode) {
                // tocar el nombre de la canción cierra la letra
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { lyricsMode = false }.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(title, color = Vintage.Cream, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    if (artist.isNotBlank()) Text(artist, color = Vintage.CreamDim, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(12.dp)) {
                    LyricsPanelVintage(path, lyrics, currentLyricIndex(lyrics, position)) { player.seekTo(it); player.play() }
                }
            } else {
                // Tocadiscos
                Turntable(
                    cover = info?.cover,
                    albumKey = album ?: path ?: "",
                    playing = player.isPlaying,
                    armTarget = armPosition(info, path, progress),
                    modifier = Modifier.fillMaxWidth(),
                    onTap = { if (player.isPlaying) player.pause() else player.play() },
                    onLongPress = onOpenCoverFlow,
                )
                Spacer(Modifier.height(12.dp))
                // Discos que siguen (izquierda) · datos · los que ya sonaron (derecha)
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Top) {
                    RecordPile(
                        player = player, forward = false, label = "Ya sonaron", total = player.currentMediaItemIndex.coerceAtLeast(0),
                        modifier = Modifier.width(104.dp)
                    )
                    val fav = path != null && LibraryStore.isFavorite(path)
                    JacketAndNote(
                        cover = info?.cover,
                        title = title,
                        artist = artist,
                        trackLabel = trackLabel,
                        favorite = fav,
                        onFavorite = { path?.let { LibraryStore.toggleFavorite(it) } },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        video = if (isVideoPath(path)) player else null,
                        animated = animatedCover,
                        playing = player.isPlaying,
                    )
                    RecordCrate(
                        player = player, total = remainingCount(player),
                        modifier = Modifier.width(122.dp)
                    )
                }
            }

            // Cuadrante de avance (como la escala de un radio antiguo)
            RadioDial(
                progress = if (dragging) dragValue else progress,
                onChange = { dragging = true; dragValue = it },
                onEnd = { player.seekTo((it * duration).toLong()); dragging = false },
                modifier = Modifier.fillMaxWidth().height(44.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(if (dragging) (dragValue * duration).toLong() else position), color = Vintage.CreamDim,
                    fontFamily = FontFamily.Serif, fontSize = 12.sp)
                Text(formatTime(duration), color = Vintage.CreamDim, fontFamily = FontFamily.Serif, fontSize = 12.sp)
            }
            // Perillas y botones de baquelita
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrassToggle("ALEAT.", player.shuffleModeEnabled) { player.shuffleModeEnabled = !player.shuffleModeEnabled }
                BakeliteKnob(Icons.Filled.SkipPrevious, 52.dp) { player.seekToPrevious() }
                BakeliteKnob(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, 74.dp) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                BakeliteKnob(Icons.Filled.SkipNext, 52.dp) { player.seekToNextMediaItem() }
                BrassToggle(
                    when (player.repeatMode) { Player.REPEAT_MODE_ONE -> "REP. 1"; else -> "REPET." },
                    player.repeatMode != Player.REPEAT_MODE_OFF
                ) {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            }
        }
    }

    if (menuOpen && path != null) {
        SongMenuWindow(
            path = path, accent = Vintage.Brass,
            onDismiss = { menuOpen = false },
            onInfo = { menuOpen = false; infoOpen = true },
            onLyrics = { menuOpen = false; lyricsMode = true },
            onGoTo = { k, v -> menuOpen = false; onGoTo(k, v) },
            onDeleted = {
                menuOpen = false
                val i = player.currentMediaItemIndex
                if (i >= 0 && i < player.mediaItemCount) player.removeMediaItem(i)
            },
        )
    }
    if (infoOpen && path != null) SongInfoWindow(path, Vintage.Brass, onDismiss = { infoOpen = false })
}

// ───────────────────────── La mesa de madera ─────────────────────────

@Composable
fun WoodTable(modifier: Modifier) {
    Box(modifier) {
        Image(
            androidx.compose.ui.res.painterResource(R.drawable.vt_table), null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                center = Offset(size.width / 2, size.height * 0.42f), radius = size.maxDimension * 0.8f))
        }
    }
}

// ───────────────────────── Tocadiscos con disco y pluma ─────────────────────────

/**
 * Tocadiscos de madera. El disco gira mientras suena; la pluma baja al disco al reproducir
 * y se va a su base al pausar. Si cambia el álbum, el disco viejo se va a la derecha y entra el nuevo por la izquierda.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Turntable(
    cover: Bitmap?,
    albumKey: String,
    playing: Boolean,
    armTarget: Float,
    modifier: Modifier,
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    compact: Boolean = false,
) {
    // giro del disco (acelera y frena como uno de verdad)
    var angle by remember { mutableFloatStateOf(0f) }
    val speed by animateFloatAsState(if (playing) 1f else 0f, tween(if (playing) 900 else 1600), label = "rpm")
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) angle = (angle + (t - last) / 1e9f * 200f * speed) % 360f // 33⅓ rpm = 200°/s
                last = t
            }
        }
    }

    // cambio de disco cuando cambia el álbum
    val slide = remember { Animatable(0f) }          // 0 = en el plato, 1 = afuera a la derecha, -1 = afuera a la izquierda
    var shownCover by remember { mutableStateOf(cover) }
    var shownKey by remember { mutableStateOf(albumKey) }
    var swapping by remember { mutableStateOf(false) }
    LaunchedEffect(albumKey) {
        if (albumKey != shownKey) {
            swapping = true
            kotlinx.coroutines.delay(240)                 // la pluma se levanta primero
            slide.animateTo(-1.3f, tween(340, easing = FastOutSlowInEasing))
            shownKey = albumKey
            shownCover = cover
            slide.snapTo(1.3f)
            slide.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            swapping = false
        }
    }
    LaunchedEffect(cover) { if (!swapping && albumKey == shownKey) shownCover = cover }

    // pluma: -0.35 = descansando en su base; 0..1 = sobre el disco (orilla → centro)
    val armGoal = if (playing && !swapping) armTarget.coerceIn(0f, 1f) else -0.35f
    val arm by animateFloatAsState(armGoal, tween(if (armGoal < 0f) 380 else 560, easing = FastOutSlowInEasing), label = "arm")
    // la pluma se levanta un poquito mientras viaja y se asienta al llegar
    val lift by animateFloatAsState(if (abs(arm - armGoal) > 0.015f) 1f else 0f, tween(200), label = "lift")
    val plateImg = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.vt_arm_base)

    val plinthImg = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.vt_plinth)
    val armImg = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.vt_arm)
    val recordImg = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.vt_record)

    BoxWithConstraints(
        modifier.aspectRatio(Deck.ASPECT)
            // un poco de perspectiva, como si lo vieras sobre la mesa
            .graphicsLayer { rotationX = 6f; cameraDistance = 30f * density }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val center = Offset(w * Deck.CENTER_X, h * Deck.CENTER_Y)
        val recordR = w * Deck.RECORD_R
        val recordBox = recordR * 2f / Deck.REC_OUTER       // la imagen del disco trae un poco de margen
        val recordDp = with(density) { recordBox.toDp() }

        // base del tocadiscos (foto)
        Canvas(Modifier.fillMaxSize()) {
            drawImage(plinthImg, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
        }

        // el disco: la foto del vinilo no gira (sus brillos son la luz del cuarto), gira la etiqueta con la portada
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((center.x - recordBox / 2 + slide.value * w).toInt(), (center.y - recordBox / 2).toInt()) }
                .size(recordDp)
                .graphicsLayer {
                    alpha = 1f - (abs(slide.value) - 0.9f).coerceIn(0f, 0.4f) / 0.4f
                    // el vinilo nunca está perfectamente plano: se bambolea un poquito con cada vuelta
                    val a = angle * PI.toFloat() / 180f
                    val sw = if (slide.value != 0f) 1.04f else 1f + 0.0012f * sin(a) * speed
                    scaleX = sw; scaleY = sw
                    rotationZ = 0.45f * sin(a) * speed
                    translationY = 0.3f * this.density * sin(2f * a) * speed
                }
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.Black.copy(alpha = if (slide.value != 0f) 0.45f else 0.3f), size.minDimension / 2 * 0.985f,
                    Offset(size.width / 2 + (if (slide.value != 0f) 10f else 3f), size.height / 2 + (if (slide.value != 0f) 16f else 5f)))
            }
            Image(recordImg, null, modifier = Modifier.fillMaxSize(), filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
            val c = shownCover
            if (c != null) {
                Box(
                    Modifier.align(Alignment.Center).size(recordDp * Deck.LABEL)
                        .graphicsLayer { rotationZ = angle }
                        .clip(CircleShape),
                ) {
                    Image(remember(c) { c.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    // un poco de sombra en la orilla de la etiqueta, como papel pegado al vinilo
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                            radius = size.minDimension / 2), size.minDimension / 2)
                    }
                }
            }
            // eje metálico al centro
            Canvas(Modifier.fillMaxSize()) {
                val cc = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension * 0.016f
                drawCircle(Color.Black.copy(alpha = 0.4f), r * 1.3f, cc + Offset(1.5f, 2f))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF9A9AA0), Color(0xFF55555A)), center = cc - Offset(r * 0.3f, r * 0.3f), radius = r * 1.2f), r, cc)
            }
        }

        // la pluma (foto), girando sobre su eje
        Canvas(Modifier.fillMaxSize()) {
            val jitter = if (playing && arm >= 0f) 0.06f * sin(angle * PI.toFloat() / 180f) * speed else 0f
            drawArmImage(armImg, plateImg, center, recordR, arm, lift, jitter)
        }
    }
}

/** Medidas tomadas de las fotos (en fracciones, para que funcionen a cualquier tamaño). */
private object Deck {
    const val ASPECT = 1345f / 932f
    const val CENTER_X = 526f / 1345f       // centro del plato
    const val CENTER_Y = 446f / 932f
    const val RECORD_R = 376f / 1345f       // radio del disco (fracción del ancho)
    const val REC_OUTER = 618f / 627f       // el disco ocupa casi toda su imagen
    const val LABEL = 0.354f                // etiqueta = 35 % del disco
    const val PIVOT_X = 1220f / 1345f       // eje de la pluma sobre la base
    const val PIVOT_Y = 158f / 932f
    const val ARM_LEN = 640f / 1345f        // de eje a aguja (fracción del ancho)
    // en la foto de la pluma
    const val IMG_PIVOT_X = 362f / 724f
    const val IMG_PIVOT_Y = 380f / 2172f
    const val IMG_TIP_X = 310f / 724f
    const val IMG_TIP_Y = 2148f / 2172f
    // base redonda fija (recorte de 476 px de la foto original, centro 7 px a la izq. y 90 px abajo del eje)
    const val ORIG_W = 724f
    const val PLATE_SIZE = 476f
    const val PLATE_DX = -7f
    const val PLATE_DY = 90f
}

private fun DrawScope.drawArmImage(
    img: androidx.compose.ui.graphics.ImageBitmap,
    plate: androidx.compose.ui.graphics.ImageBitmap,
    center: Offset,
    recordR: Float,
    arm: Float,
    lift: Float,
    jitter: Float,
) {
    val pivot = Offset(size.width * Deck.PIVOT_X, size.height * Deck.PIVOT_Y)
    val len = size.width * Deck.ARM_LEN
    val vImg = Offset((Deck.IMG_TIP_X - Deck.IMG_PIVOT_X) * img.width, (Deck.IMG_TIP_Y - Deck.IMG_PIVOT_Y) * img.height)
    val armScale = len / vImg.getDistance()
    fun tip(theta: Float): Offset {
        val t = theta * PI.toFloat() / 180f
        val v = vImg * armScale
        return pivot + Offset(v.x * cos(t) - v.y * sin(t), v.x * sin(t) + v.y * cos(t))
    }
    fun thetaFor(radius: Float): Float {
        var lo = 0f; var hi = 90f
        repeat(28) {
            val mid = (lo + hi) / 2
            if ((tip(mid) - center).getDistance() > radius) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }
    val outer = thetaFor(recordR * 0.93f)
    val inner = thetaFor(recordR * 0.40f)
    val rest = -5f
    val theta = (if (arm < 0f) rest + (outer - rest) * (1f + arm / 0.35f).coerceIn(0f, 1f) else outer + (inner - outer) * arm) + jitter

    val fq = androidx.compose.ui.graphics.FilterQuality.High
    val shadowTint = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black.copy(alpha = 0.35f - 0.1f * lift), androidx.compose.ui.graphics.BlendMode.SrcIn)
    fun iOff(o: Offset) = androidx.compose.ui.unit.IntOffset(o.x.toInt(), o.y.toInt())
    fun iSize(w: Float, h: Float) = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt())

    // 1) base redonda con tornillos: NO gira
    val f = armScale * img.width / Deck.ORIG_W            // px de la foto original → px en pantalla
    val plateSize = Deck.PLATE_SIZE * f
    val plateTL = pivot + Offset(Deck.PLATE_DX * f - plateSize / 2, Deck.PLATE_DY * f - plateSize / 2)
    drawImage(plate, dstOffset = iOff(plateTL + Offset(plateSize * 0.03f, plateSize * 0.05f)), dstSize = iSize(plateSize, plateSize), colorFilter = shadowTint)
    drawImage(plate, dstOffset = iOff(plateTL), dstSize = iSize(plateSize, plateSize), filterQuality = fq)

    // 2) brazo: gira sobre el eje; al levantarse se acerca un poco y su sombra se separa
    val dstW = img.width * armScale
    val dstH = img.height * armScale
    val topLeft = pivot - Offset(Deck.IMG_PIVOT_X * dstW, Deck.IMG_PIVOT_Y * dstH)
    rotate(theta, pivot) {
        val sh = dstW * (0.03f + 0.025f * lift)
        drawImage(img, dstOffset = iOff(topLeft + Offset(sh, sh * 1.6f)), dstSize = iSize(dstW, dstH), colorFilter = shadowTint)
        val k = 1f + 0.015f * lift
        withTransform({ scale(k, k, pivot) }) {
            drawImage(img, dstOffset = iOff(topLeft), dstSize = iSize(dstW, dstH), filterQuality = fq)
        }
    }
}

private fun DrawScope.drawPlinth() {
    val r = CornerRadius(size.minDimension * 0.06f)
    // sombra sobre la mesa
    drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(6f, 10f), size, r)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFA36B36), Vintage.Teak, Vintage.TeakDark)), Offset.Zero, size, r)
    // vetas
    val rnd = Random(3)
    repeat(22) {
        val y = rnd.nextFloat() * size.height
        val a = rnd.nextFloat()
        drawLine(Color.Black.copy(alpha = 0.06f + a * 0.08f), Offset(0f, y), Offset(size.width, y + (a - 0.5f) * 20f), strokeWidth = 1f + a * 2f)
    }
    // tapa de aluminio cepillado
    val inset = size.minDimension * 0.035f
    drawRoundRect(
        Brush.linearGradient(listOf(Color(0xFFD8D6D0), Color(0xFFA9A69E), Color(0xFFCFCBC2)), start = Offset.Zero, end = Offset(size.width, size.height)),
        Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(size.minDimension * 0.03f)
    )
    drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
        CornerRadius(size.minDimension * 0.03f), style = Stroke(1.5f))
}

private fun DrawScope.drawPlatterAndVinyl() {
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension / 2
    // plato metálico
    drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.0f, c + Offset(3f, 5f))
    drawCircle(Brush.sweepGradient(listOf(Color(0xFF8F8F95), Color(0xFFDADAE0), Color(0xFF7A7A80), Color(0xFFC8C8CE), Color(0xFF8F8F95)), center = c), r, c)
    // vinilo
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1C1C1E), Color(0xFF0A0A0B)), center = c, radius = r), r * 0.97f, c)
    // surcos
    var gr = r * 0.95f
    var k = 0
    while (gr > r * 0.40f) {
        drawCircle(Color.White.copy(alpha = if (k % 7 == 0) 0.07f else 0.025f), gr, c, style = Stroke(1f))
        gr -= r * 0.012f
        k++
    }
    // separaciones entre canciones
    listOf(0.8f, 0.66f, 0.53f).forEach { f -> drawCircle(Color.Black, r * f, c, style = Stroke(2.5f)) }
}

/** Dibuja la pluma; `arm` < 0 = en su base, 0..1 = posición sobre el disco. */
private fun DrawScope.drawTonearm(center: Offset, recordR: Float, arm: Float, playing: Boolean) {
    val pivot = Offset(size.width * 0.86f, size.height * 0.18f)
    val len = (center - pivot).getDistance()
    fun tip(theta: Float): Offset {
        val t = theta * PI.toFloat() / 180f
        return pivot + Offset(-sin(t) * len, cos(t) * len)
    }
    // ángulo que deja la punta a cierta distancia del centro del disco
    fun thetaFor(radius: Float): Float {
        var lo = 0f; var hi = 80f
        repeat(28) {
            val mid = (lo + hi) / 2
            if ((tip(mid) - center).getDistance() > radius) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }
    val outer = thetaFor(recordR * 0.92f)
    val inner = thetaFor(recordR * 0.44f)
    val rest = 4f
    val theta = if (arm < 0f) rest + (outer - rest) * (1f + arm / 0.35f).coerceIn(0f, 1f) else outer + (inner - outer) * arm

    // base de la pluma
    drawCircle(Color.Black.copy(alpha = 0.35f), size.minDimension * 0.085f, pivot + Offset(3f, 5f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF2F2F4), Color(0xFF8C8C92)), center = pivot - Offset(6f, 6f), radius = size.minDimension * 0.09f),
        size.minDimension * 0.08f, pivot)
    // soporte donde descansa
    val restTip = tip(rest + 2f)
    drawCircle(Color(0xFF5A5A60), size.minDimension * 0.022f, restTip + Offset(0f, -len * 0.18f))

    rotate(theta, pivot) {
        val down = Offset(0f, len)
        // contrapeso
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF6E6E74), Color(0xFFE8E8EC), Color(0xFF6E6E74)),
                startX = pivot.x - size.minDimension * 0.045f, endX = pivot.x + size.minDimension * 0.045f),
            Offset(pivot.x - size.minDimension * 0.045f, pivot.y - size.minDimension * 0.16f),
            Size(size.minDimension * 0.09f, size.minDimension * 0.11f), CornerRadius(6f)
        )
        // brazo (sombra y tubo cromado)
        drawLine(Color.Black.copy(alpha = 0.3f), pivot + Offset(6f, 8f), pivot + down * 0.86f + Offset(6f, 8f), strokeWidth = size.minDimension * 0.022f, cap = StrokeCap.Round)
        drawLine(
            Brush.horizontalGradient(listOf(Color(0xFF8C8C92), Color(0xFFF4F4F6), Color(0xFF8C8C92)),
                startX = pivot.x - 6f, endX = pivot.x + 6f),
            pivot, pivot + down * 0.86f, strokeWidth = size.minDimension * 0.02f, cap = StrokeCap.Round
        )
        // codo y cápsula
        val elbow = pivot + down * 0.86f
        val head = pivot + Offset(-len * 0.08f, len * 1.0f)
        drawLine(Color(0xFFD8D8DC), elbow, head, strokeWidth = size.minDimension * 0.02f, cap = StrokeCap.Round)
        rotate(28f, head) {
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2B2B2E), Color(0xFF111113))),
                head - Offset(size.minDimension * 0.035f, size.minDimension * 0.02f),
                Size(size.minDimension * 0.07f, size.minDimension * 0.06f), CornerRadius(4f))
            drawRect(Vintage.Brass, head - Offset(size.minDimension * 0.035f, size.minDimension * 0.02f),
                Size(size.minDimension * 0.07f, size.minDimension * 0.012f))
        }
    }
    // foco de "aguja abajo"
    if (playing && arm >= 0f) drawCircle(Vintage.Brass.copy(alpha = 0.8f), 3f, pivot)
}

// ───────────────────────── Pilas de discos ─────────────────────────

@Composable
private fun RecordPile(player: Player, forward: Boolean, label: String, total: Int, modifier: Modifier) {
    val idx = neighbours(player, forward, 4)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(110.dp)) {
            // del fondo hacia el frente: el más cercano queda arriba
            idx.reversed().forEachIndexed { k, i ->
                val depth = idx.size - 1 - k
                val path = player.getMediaItemAt(i).mediaId
                androidx.compose.runtime.key(path, i) {
                    Sleeve(
                        path = path,
                        vinylOnRight = !forward,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(x = (if (forward) 1 else -1).let { s -> (depth * 4 * s).dp }, y = (-depth * 7).dp)
                            .rotate((if (depth % 2 == 0) -3f else 4f) * if (forward) 1 else -1)
                            .clickable { player.seekTo(i, 0L); player.play() }
                    )
                }
            }
            if (idx.isEmpty()) {
                Text("—", color = Vintage.CreamDim, modifier = Modifier.align(Alignment.Center), fontFamily = FontFamily.Serif)
            }
        }
        Text("$label ($total)", color = Vintage.CreamDim, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 12.sp)
    }
}

@Composable
private fun Sleeve(path: String, vinylOnRight: Boolean, modifier: Modifier) {
    val thumb by produceState<Bitmap?>(null, path) { value = loadThumb(path) }
    // funda: 1484x1060 en la foto; el frente cuadrado va de x 97..1052, y 66..976
    BoxWithConstraints(modifier.size(98.dp, 70.dp)) {
        Image(
            androidx.compose.ui.res.painterResource(R.drawable.vt_sleeve), null,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (vinylOnRight) 1f else -1f }
        )
        val frontW = maxWidth * (955f / 1484f)
        val frontH = maxHeight * (910f / 1060f)
        val left = if (vinylOnRight) maxWidth * (97f / 1484f) else maxWidth - maxWidth * (97f / 1484f) - frontW
        val b = thumb
        if (b != null) {
            Image(
                remember(b) { b.asImageBitmap() }, null, contentScale = ContentScale.Crop,
                modifier = Modifier.offset(left, maxHeight * (66f / 1060f))
                    .size(frontW, frontH).clip(RoundedCornerShape(1.dp))
            )
        }
    }
}

// ───────────────────────── Controles retro ─────────────────────────

@Composable
fun BakeliteKnob(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    var down by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (down) 0.92f else 1f, tween(90), label = "knob")
    Box(
        Modifier.size(size)
            .graphicsLayer { scaleX = press; scaleY = press }
            .shadow(8.dp, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { down = true; tryAwaitRelease(); down = false }, onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2
            // aro de latón
            drawCircle(Brush.sweepGradient(listOf(Vintage.BrassDark, Vintage.BrassLight, Vintage.Brass, Vintage.BrassDark, Vintage.BrassLight, Vintage.BrassDark), center = c), r, c)
            // perilla de baquelita
            drawCircle(Brush.radialGradient(listOf(Color(0xFF7A4E2E), Vintage.Bakelite, Color(0xFF140A05)),
                center = c - Offset(r * 0.3f, r * 0.35f), radius = r * 1.3f), r * 0.84f, c)
            // estrías del borde
            for (i in 0 until 36) {
                rotate(i * 10f, c) {
                    drawLine(Color.Black.copy(alpha = 0.35f), Offset(c.x, c.y - r * 0.84f), Offset(c.x, c.y - r * 0.74f), strokeWidth = 1.5f)
                }
            }
            drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), startY = c.y - r * 0.8f, endY = c.y),
                Offset(c.x - r * 0.5f, c.y - r * 0.78f), Size(r, r * 0.6f))
        }
        Icon(icon, null, tint = Vintage.Cream, modifier = Modifier.size(size * 0.42f))
    }
}

@Composable
fun BrassToggle(label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Canvas(Modifier.size(22.dp, 36.dp)) {
            // placa
            drawRoundRect(Brush.verticalGradient(listOf(Vintage.BrassLight, Vintage.Brass, Vintage.BrassDark)), cornerRadius = CornerRadius(6f))
            drawCircle(Color(0xFF2A1A0E), size.width * 0.18f, Offset(size.width / 2, size.height / 2))
            // palanca cromada
            val tipY = if (on) size.height * 0.12f else size.height * 0.88f
            drawLine(Color(0xFFE8E8EC), Offset(size.width / 2, size.height / 2), Offset(size.width / 2, tipY), strokeWidth = 5f, cap = StrokeCap.Round)
            drawCircle(Color(0xFFF7F7F9), 5f, Offset(size.width / 2, tipY))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (on) Vintage.Brass else Vintage.CreamDim, fontFamily = FontFamily.Serif, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

/** Escala de avance tipo radio antiguo: rayitas, números y una aguja que puedes arrastrar. */
@Composable
fun RadioDial(progress: Float, onChange: (Float) -> Unit, onEnd: (Float) -> Unit, modifier: Modifier) {
    val change by rememberUpdatedState(onChange)
    val end by rememberUpdatedState(onEnd)
    var drag by remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures { o -> end((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f); change(drag) },
                    onDragEnd = { end(drag) }
                ) { c, d -> c.consume(); drag = (drag + d / size.width).coerceIn(0f, 1f); change(drag) }
            }
    ) {
        // cristal del cuadrante con luz cálida
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF5E2B8), Color(0xFFE2C68C))))
        drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.25f))))
        val pad = 10f
        val usable = size.width - pad * 2
        for (i in 0..40) {
            val x = pad + usable * i / 40f
            val long = i % 5 == 0
            drawLine(Color(0xFF3A2416), Offset(x, size.height * 0.62f), Offset(x, size.height * (if (long) 0.30f else 0.45f)),
                strokeWidth = if (long) 2f else 1f)
        }
        // parte ya escuchada
        drawLine(Vintage.Burgundy.copy(alpha = 0.55f), Offset(pad, size.height * 0.78f), Offset(pad + usable * progress, size.height * 0.78f), strokeWidth = 4f)
        // aguja
        val x = pad + usable * progress
        drawLine(Color(0xFFB0281C), Offset(x, 4f), Offset(x, size.height - 4f), strokeWidth = 3f)
        drawCircle(Vintage.Brass, 5f, Offset(x, size.height - 6f))
        drawRect(Color(0xFF3A2416), style = Stroke(4f))
    }
}

@Composable
private fun LyricsPanelVintage(path: String?, lines: List<LyricLine>, current: Int, onSeek: (Long) -> Unit) {
    if (lines.isEmpty()) {
        NoLyricsPrompt(path, Vintage.CreamDim, Vintage.Brass, serif = true)
        return
    }
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(current) { if (current >= 0) state.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    androidx.compose.foundation.lazy.LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        items(lines.size) { i ->
            val active = i == current
            Text(
                lines[i].text.ifBlank { "♪" },
                color = if (active) Vintage.BrassLight else Vintage.Cream.copy(alpha = 0.45f),
                fontFamily = FontFamily.Serif, fontSize = if (active) 24.sp else 19.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth().clickable { if (lines[i].timeMs >= 0) onSeek(lines[i].timeMs) }.padding(vertical = 8.dp)
            )
        }
    }
}

// ───────────────────────── Ventanita del escritorio ─────────────────────────

@Composable
fun VintageWindowContent(player: Player, onOpen: () -> Unit) {
    val tick = observePlayer(player)
    val path = player.currentMediaItem?.mediaId
    val info by rememberTrackInfo(path)
    val duration = player.duration.coerceAtLeast(0L)
    val position = player.currentPosition + tick * 0L
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize()) {
        WoodTable(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1.15f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    val wFit = minOf(maxWidth, maxHeight * 1.28f)
                    Turntable(
                        cover = info?.cover,
                        albumKey = info?.album ?: path ?: "",
                        playing = player.isPlaying,
                        armTarget = armPosition(info, path, progress),
                        modifier = Modifier.width(wFit),
                        onTap = onOpen,
                        compact = true,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(info?.title ?: player.mediaMetadata.title?.toString() ?: "", color = Vintage.Cream, fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(info?.artist ?: "", color = Vintage.CreamDim, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("${formatTime(position)} / ${formatTime(duration)}", color = Vintage.Brass, fontFamily = FontFamily.Serif, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            RadioDial(
                progress = if (dragging) dragValue else progress,
                onChange = { dragging = true; dragValue = it },
                onEnd = { player.seekTo((it * duration).toLong()); dragging = false },
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                BakeliteKnob(Icons.Filled.SkipPrevious, 34.dp) { player.seekToPreviousMediaItem() }
                BakeliteKnob(if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, 44.dp) {
                    if (player.isPlaying) player.pause() else player.play()
                }
                BakeliteKnob(Icons.Filled.SkipNext, 34.dp) { player.seekToNextMediaItem() }
            }
        }
    }
}

@Suppress("unused")
private val unusedSqrt = sqrt(1f)

// ───────────────────────── Funda y sobre con la nota ─────────────────────────

/**
 * Al centro: la funda del disco con la portada impresa y, encima, un sobre de papel
 * del que sale una notita con el nombre de la canción. Al cambiar de canción la nota
 * se mete al sobre y vuelve a salir con el nuevo nombre.
 */
@Composable
private fun JacketAndNote(
    cover: Bitmap?,
    title: String,
    artist: String,
    trackLabel: String?,
    favorite: Boolean,
    onFavorite: () -> Unit,
    modifier: Modifier,
    video: androidx.media3.common.Player? = null,
    animated: java.io.File? = null,
    playing: Boolean = true,
) {
    val noteOut = remember { Animatable(1f) }
    var shownTitle by remember { mutableStateOf(title) }
    var shownArtist by remember { mutableStateOf(artist) }
    var shownTrack by remember { mutableStateOf(trackLabel) }
    LaunchedEffect(title, artist) {
        if (title != shownTitle || artist != shownArtist) {
            noteOut.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            shownTitle = title; shownArtist = artist; shownTrack = trackLabel
            noteOut.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 260f))
        } else shownTrack = trackLabel
    }
    val ink = Color(0xFF2B2118)

    BoxWithConstraints(modifier) {
        val w = maxWidth
        // funda del disco (arriba, un poco girada)
        val jacket = minOf(w * 0.74f, maxHeight * 0.55f)
        Box(
            Modifier.align(Alignment.TopCenter).offset(x = -w * 0.06f, y = 2.dp)
                .size(jacket)
                .rotate(-6f)
                .shadow(10.dp, RoundedCornerShape(3.dp))
        ) {
            if (video != null) {
                // un video se ve en la funda como a través de un cristal
                VideoGlass(video, Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)))
            } else if (animated != null) {
                AnimatedCoverView(animated, playing, Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)))
                Image(androidx.compose.ui.res.painterResource(R.drawable.vt_jacket), null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.18f })
            } else if (cover != null) {
                // el estuche ES la portada, con un poco de textura de cartón gastado encima
                Image(remember(cover) { cover.asImageBitmap() }, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)))
                Image(androidx.compose.ui.res.painterResource(R.drawable.vt_jacket), null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.22f })
            } else {
                Image(androidx.compose.ui.res.painterResource(R.drawable.vt_jacket), null, modifier = Modifier.fillMaxSize())
            }
            Text(
                if (favorite) "★" else "☆",
                color = if (favorite) Vintage.Brass else Vintage.Cream,
                fontSize = 22.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clickable(onClick = onFavorite)
            )
        }

        // sobre con la nota (abajo, girado hacia el otro lado)
        val envW = w * 1.02f
        val envH = envW * (589f / 700f)
        val bodyTop = envH * (397f / 1233f)          // donde empieza el bolsillo del sobre
        val noteW = envW * 0.84f
        val noteH = noteW * (457f / 700f)
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = (-4).dp)
                .size(envW, envH)
                .rotate(4f)
        ) {
            Image(androidx.compose.ui.res.painterResource(R.drawable.vt_env_back), null, modifier = Modifier.fillMaxSize())
            // la nota: adentro del sobre (0) → asomándose (1)
            Box(
                Modifier.offset(
                    x = (envW - noteW) / 2,
                    y = bodyTop + 6.dp - noteH * 0.58f * noteOut.value
                ).size(noteW, noteH)
            ) {
                Image(androidx.compose.ui.res.painterResource(R.drawable.vt_note), null, modifier = Modifier.fillMaxSize())
                Column(Modifier.fillMaxSize().padding(start = noteW * 0.07f, end = noteW * 0.07f, top = noteH * 0.07f)) {
                    Text(shownTitle, color = ink, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (shownArtist.isNotEmpty()) Text(shownArtist, color = ink.copy(alpha = 0.85f), fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    shownTrack?.let {
                        Text(it.lowercase(Locale.ROOT), color = Vintage.Burgundy, fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
            Image(androidx.compose.ui.res.painterResource(R.drawable.vt_env_front), null, modifier = Modifier.fillMaxSize())
        }
    }
}

// ───────────────────────── Caja con los discos que siguen ─────────────────────────

/**
 * Caja de madera con los estuches parados, como en una tienda de discos:
 * el siguiente se ve de frente y los demás asoman por arriba, uno detrás de otro.
 */
@Composable
private fun RecordCrate(player: Player, total: Int, modifier: Modifier) {
    val idx = neighbours(player, true, 5)
    Column(modifier.padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(700f / 482f)) {
            val w = maxWidth
            val h = maxHeight
            Image(androidx.compose.ui.res.painterResource(R.drawable.vt_crate_back), null, modifier = Modifier.fillMaxSize())
            // del más lejano al más cercano
            idx.indices.reversed().forEach { depth ->
                val i = idx[depth]
                val path = player.getMediaItemAt(i).mediaId
                val jw = w * (0.64f - 0.025f * depth)
                val bottom = h * (0.80f - 0.07f * depth)
                androidx.compose.runtime.key(path, i) {
                    CrateJacket(
                        path = path,
                        shade = 0.10f * depth,
                        modifier = Modifier
                            .offset(x = (w - jw) / 2 + (if (depth % 2 == 0) 0.dp else 2.dp), y = bottom - jw)
                            .size(jw)
                            .rotate(if (depth % 2 == 0) -1f else 1f)
                            .clickable { player.seekTo(i, 0L); player.play() }
                    )
                }
            }
            Image(androidx.compose.ui.res.painterResource(R.drawable.vt_crate_front), null, modifier = Modifier.fillMaxSize())
            if (idx.isEmpty()) {
                Text("vacía", color = Vintage.CreamDim, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
                    fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
            }
        }
        Text("Siguen ($total)", color = Vintage.CreamDim, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun CrateJacket(path: String, shade: Float, modifier: Modifier) {
    val cover by produceState(initialValue = peekCover(path), path) { if (value == null) value = loadCover(path) }
    Box(modifier.shadow(4.dp, RoundedCornerShape(2.dp)).clip(RoundedCornerShape(2.dp)).background(Vintage.Cream)) {
        val c = cover
        if (c != null) Image(remember(c) { c.asImageBitmap() }, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Image(androidx.compose.ui.res.painterResource(R.drawable.vt_jacket), null, modifier = Modifier.fillMaxSize())
        // canto de arriba con brillo y sombra para los que están atrás
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.35f)))
        if (shade > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = shade)))
    }
}
