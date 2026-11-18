package com.tony.explorador

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.sin

/*
 * «Ajustes de música» con el diseño Nebula: tarjetas de cristal por sección y páginas para
 * Ecualizador, Efectos de sonido, Visualizador (barra de avance), Tema y apariencia, Letras y Biblioteca.
 */

private object SC {
    val accent get() = Nebula.Purple
    val accent2 get() = Nebula.Violet
    val text = Color.White
    val sub get() = Nebula.Sub
    val line = Color.White.copy(alpha = 0.08f)
}

private fun Modifier.card(shape: RoundedCornerShape = RoundedCornerShape(22.dp)) = this.clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))))
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), SC.accent.copy(alpha = 0.18f))), shape)

private enum class SPage(val title: String) {
    Main("Ajustes de música"), Eq("Ecualizador"), Fx("Efectos de sonido"), Visual("Visualizador"),
    Theme("Tema y apariencia"), Lyrics("Posición de la letra"), Lists("Tipos de contenido")
}

@Composable
fun MusicSettingsScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    remember { AudioFx.init(ctx); 0 }
    var page by remember { mutableStateOf(when (MusicUi.settingsPage) { "eq" -> SPage.Eq; "theme" -> SPage.Theme; else -> SPage.Main }).also { MusicUi.settingsPage = null } }
    BackHandler { if (page != SPage.Main) page = SPage.Main else onClose() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Nebula.Top, Nebula.Night, Nebula.Deep)))) {
        NebulaBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            SettingsHeader(page) { if (page != SPage.Main) page = SPage.Main else onClose() }
            AnimatedContent(page, label = "ajustes", transitionSpec = {
                if (targetState == SPage.Main) (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                else (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            }) { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                    when (p) {
                        SPage.Main -> MainPage(onOpen = { page = it }, onClose = onClose)
                        SPage.Eq -> EqPage()
                        SPage.Fx -> FxPage()
                        SPage.Visual -> VisualPage()
                        SPage.Theme -> ThemePage()
                        SPage.Lyrics -> LyricsPage()
                        SPage.Lists -> ListsPage()
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(page: SPage, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        // un planeta que asoma arriba a la derecha
        Canvas(Modifier.matchParentSizeSafe()) {
            val c = Offset(size.width * 1.02f, -size.height * 0.9f); val r = size.height * 2.1f
            drawCircle(Brush.radialGradient(listOf(SC.accent.copy(alpha = 0.40f), Color.Transparent), c, r * 1.35f), r * 1.35f, c)
            drawCircle(Brush.radialGradient(listOf(Nebula.Night, Nebula.Purple.copy(alpha = 0.5f)), c, r), r, c)
            drawCircle(Brush.sweepGradient(listOf(SC.accent2, Nebula.Pink, Color(0xFF8FD3FF), SC.accent2), c), r, c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(page.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (page == SPage.Main) Text("Personaliza tu experiencia de reproducción", color = SC.sub, fontSize = 12.sp)
            }
            if (page == SPage.Main) Icon(Icons.Filled.GraphicEq, null, tint = SC.accent2, modifier = Modifier.size(30.dp))
            if (page == SPage.Eq) Toggle(AudioFx.eqOn) { AudioFx.setEq(it) }
        }
    }
}

private fun Modifier.matchParentSizeSafe() = this.fillMaxWidth().height(70.dp)

// ───────── piezas ─────────

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = SC.accent, checkedThumbColor = Color.White,
        uncheckedTrackColor = Color.White.copy(alpha = 0.12f), uncheckedThumbColor = Color.White.copy(alpha = 0.8f), uncheckedBorderColor = Color.Transparent))
}

@Composable
private fun SectionCard(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).card().padding(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(listOf(SC.accent, Nebula.Blue))),
                contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String, last: Boolean = false, on: Boolean? = null,
                       onToggle: ((Boolean) -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Column {
        Row(Modifier.fillMaxWidth().clickable { if (on != null && onToggle != null) onToggle(!on) else onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color(0xFFD9CFFF), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (sub.isNotBlank()) Text(sub, color = SC.sub, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (on != null && onToggle != null) Toggle(on, onToggle)
            else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SC.sub)
        }
        if (!last) Box(Modifier.padding(start = 58.dp, end = 12.dp).fillMaxWidth().height(1.dp).background(SC.line))
    }
}

@Composable
private fun BigTile(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))
        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = SC.accent2, modifier = Modifier.size(28.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text(sub, color = SC.sub, fontSize = 10.sp, maxLines = 1)
    }
}

// ───────── página principal ─────────

@Composable
private fun MainPage(onOpen: (SPage) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheMsg by remember { mutableStateOf<String?>(null) }

    SectionCard(Icons.Filled.FormatQuote, "Letras") {
        SettingRow(Icons.Filled.Subtitles, "Dónde se ve la letra", when (LibraryStore.lyricsPlace) {
            "big" -> "Debajo de la carátula, grande"; "over" -> "Encima de la carátula"; "float" -> "En una ventana flotante"; else -> "Debajo de la carátula" }) { onOpen(SPage.Lyrics) }
        SettingRow(Icons.Filled.Translate, "Traducir letras al español", "Se traduce en el teléfono (~30 MB la primera vez por idioma)",
            on = LibraryStore.lyricsTranslate, onToggle = { LibraryStore.changeLyricsTranslate(it) })
        if (LibraryStore.lyricsTranslate) SettingRow(Icons.Filled.Translate, "Resaltar la traducción", "Si está apagado, resalta la letra original",
            on = LibraryStore.lyricsHighlight == "translation", onToggle = { LibraryStore.changeLyricsHighlight(if (it) "translation" else "original") })
        SettingRow(Icons.Filled.Tune, "Ajustar tiempos de una letra", "En el reproductor: ⋮ → «Sincronizar letra»", last = true) {}
    }

    SectionCard(Icons.Filled.Layers, "Biblioteca") {
        SettingRow(Icons.Filled.FolderOpen, "Carpetas y fuentes", "Elige dónde buscar tu música") { MusicUi.openScanFolders = true; onClose() }
        SettingRow(Icons.Filled.ViewList, "Tipos de contenido", "Listas, álbumes, artistas, años… cuáles se ven") { onOpen(SPage.Lists) }
        SettingRow(Icons.Filled.CloudSync, "Sincronizar con el NAS", "Subir y bajar tu música") { MusicUi.openNasSync = true; onClose() }
        SettingRow(Icons.Filled.Refresh, "Volver a buscar música", if (LibraryStore.scanning) "buscando…" else "${LibraryStore.songs.size} canciones", last = true) {
            scope.launch { LibraryStore.scan() }
        }
    }

    SectionCard(Icons.Filled.GraphicEq, "Audio y efectos") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigTile(Icons.Filled.Equalizer, "Ecualizador", if (AudioFx.eqOn) AudioFx.preset else "Apagado", Modifier.weight(1f)) { onOpen(SPage.Eq) }
            BigTile(Icons.Filled.SettingsInputAntenna, "Efectos", listOf(AudioFx.bassOn, AudioFx.virtOn, AudioFx.vocalOn, AudioFx.loudOn).count { it }
                .let { if (it == 0) "Apagados" else "$it activos" }, Modifier.weight(1f)) { onOpen(SPage.Fx) }
            BigTile(Icons.Filled.Waves, "Visualizador", SeekStyles.firstOrNull { it.first == LibraryStore.seekStyle }?.second?.substringBefore(" (") ?: "Barra", Modifier.weight(1f)) { onOpen(SPage.Visual) }
        }
        SettingRow(Icons.AutoMirrored.Filled.VolumeUp, "Volumen más fuerte", "Sube el volumen de las canciones bajitas",
            on = AudioFx.loudOn, onToggle = { AudioFx.setLoud(it) }, last = true)
    }

    SectionCard(Icons.Filled.PlayArrow, "Reproductor") {
        SettingRow(Icons.Filled.Palette, "Tema y apariencia", LibraryStore.musicSkin.label.substringBefore(" (")) { onOpen(SPage.Theme) }
        SettingRow(Icons.Filled.MovieFilter, "Carátula animada o video", "Mostrarla en el reproductor cuando exista",
            on = MotionPref.on, onToggle = { MotionPref.on = it }, last = true)
    }

    SectionCard(Icons.Filled.Settings, "Avanzado") {
        SettingRow(Icons.Filled.CleaningServices, "Borrar caché", cacheMsg ?: "Libera espacio de archivos temporales", last = true) {
            val n = ctx.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            cacheMsg = "Listo: se liberaron ${formatSize(n)}"
        }
    }
}

// ───────── Ecualizador ─────────

@Composable
private fun EqPage() {
    if (!AudioFx.available) {
        Text("Pon una canción para activar el ecualizador (necesita que el reproductor esté listo).", color = SC.sub, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).card().padding(16.dp))
        return
    }
    // formas listas
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AudioFx.PRESETS.keys.take(4).forEach { PresetChip(it, Modifier.weight(1f)) }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AudioFx.PRESETS.keys.drop(4).forEach { PresetChip(it, Modifier.weight(1f)) }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).card().padding(12.dp)) {
        val maxDb = AudioFx.maxLevel / 100
        Row(Modifier.fillMaxWidth().height(230.dp)) {
            Column(Modifier.fillMaxHeight().padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("+$maxDb dB", color = SC.sub, fontSize = 10.sp); Text("0 dB", color = SC.sub, fontSize = 10.sp); Text("-$maxDb dB", color = SC.sub, fontSize = 10.sp)
            }
            AudioFx.levels.indices.forEach { i ->
                BandSlider(AudioFx.levels[i], AudioFx.minLevel, AudioFx.maxLevel, Modifier.weight(1f).fillMaxHeight()) { AudioFx.setBand(i, it) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 34.dp, top = 6.dp)) {
            AudioFx.freqs.forEach { f ->
                Text(AudioFx.label(f), color = SC.sub, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
    Text("Las bandas son las que trae tu teléfono. Funciona con la música de esta app.", color = SC.sub, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
}

@Composable
private fun PresetChip(name: String, modifier: Modifier) {
    val on = AudioFx.eqOn && AudioFx.preset == name
    Text(name, color = Color.White, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(CircleShape).background(if (on) Brush.linearGradient(listOf(SC.accent, Nebula.Blue)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f))))
            .clickable { AudioFx.usePreset(name) }.padding(vertical = 8.dp, horizontal = 4.dp))
}

/** Barra vertical para cada banda: se arrastra hacia arriba o abajo. */
@Composable
private fun BandSlider(value: Int, min: Int, max: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    fun fromY(y: Float, h: Float) = (max - (y / h).coerceIn(0f, 1f) * (max - min)).toInt()
    Canvas(modifier.pointerInput(min, max) {
        detectTapGestures { onChange(fromY(it.y, size.height.toFloat())) }
    }.pointerInput(min, max) {
        detectVerticalDragGestures { ch, _ -> ch.consume(); onChange(fromY(ch.position.y, size.height.toFloat())) }
    }) {
        val cx = size.width / 2f; val w = 4.dp.toPx()
        val frac = (max - value).toFloat() / (max - min)
        val y = size.height * frac; val zero = size.height * (max.toFloat() / (max - min))
        drawLine(Color.White.copy(alpha = 0.12f), Offset(cx, 0f), Offset(cx, size.height), w, StrokeCap.Round)
        drawLine(Brush.verticalGradient(listOf(Nebula.Pink, SC.accent, Nebula.Blue)), Offset(cx, zero), Offset(cx, y), w, StrokeCap.Round)
        drawCircle(SC.accent2.copy(alpha = 0.35f), 11.dp.toPx(), Offset(cx, y))
        drawCircle(Color.White, 7.dp.toPx(), Offset(cx, y))
    }
}

// ───────── Efectos ─────────

@Composable
private fun FxPage() {
    @Composable
    fun FxRow(icon: ImageVector, title: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).card(RoundedCornerShape(18.dp)).clickable { set(!on) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SC.accent2, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(sub, color = SC.sub, fontSize = 11.sp)
            }
            Toggle(on, set)
        }
    }
    Spacer(Modifier.height(4.dp))
    FxRow(Icons.Filled.SettingsInputAntenna, "Refuerzo de graves", "Graves más profundos y potentes", AudioFx.bassOn) { AudioFx.setBass(it) }
    FxRow(Icons.Filled.Headphones, "Virtualizador", "Sonido envolvente 3D (mejor con audífonos)", AudioFx.virtOn) { AudioFx.setVirt(it) }
    FxRow(Icons.Filled.Mic, "Claridad vocal", "Voces más nítidas y definidas", AudioFx.vocalOn) { AudioFx.setVocal(it) }
    FxRow(Icons.AutoMirrored.Filled.VolumeUp, "Volumen más fuerte", "Sube las canciones bajitas", AudioFx.loudOn) { AudioFx.setLoud(it) }
    Text("Algunos teléfonos no traen todos los efectos; si uno no se nota, es que tu teléfono no lo tiene.", color = SC.sub, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
}

// ───────── Visualizador (barra de avance) ─────────

@Composable
private fun VisualPage() {
    Text("Estilo de la barra de avance: se mueve con la música que suena.", color = SC.sub, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
    SeekStyles.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (id, label) ->
                val on = LibraryStore.seekStyle == id
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (on) SC.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
                    .border(if (on) 1.5.dp else 1.dp, if (on) SC.accent2 else Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .clickable { LibraryStore.changeSeekStyle(id) }.padding(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(44.dp)) {
                        WaveSeekBar(progress = 0.6f, playing = true, accent = SC.accent2, onSeekChange = {}, onSeekEnd = {}, style = id, interactive = false)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Filled.Check, null, tint = SC.accent2, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

// ───────── Tema ─────────

private class SkinInfo(val name: String, val sub: String)
private val skinInfo = mapOf(
    MusicSkin.NEBULA to SkinInfo("Nebula", "Noche morada y rosa neón"),
    MusicSkin.BOCINA to SkinInfo("Bocina", "Bocina, aro y ondas al ritmo"),
    MusicSkin.GUITAR to SkinInfo("Guitar Nebula", "Escenario y notas que bajan"),
    MusicSkin.NAVIDAD to SkinInfo("Navidad Nebula", "Noche nevada que sigue tu dedo"),
    MusicSkin.LAVANDA to SkinInfo("Lavanda", "Claro y suave, morado"),
    MusicSkin.ORIGINAL to SkinInfo("Original", "Toma el color de la portada"),
    MusicSkin.MEDIA_PLAYER to SkinInfo("Media Player", "Azul cristal"),
    MusicSkin.WALKMAN to SkinInfo("Retro naranja", "Negro, naranja y morado"),
    MusicSkin.VINTAGE to SkinInfo("Vintage", "Tocadiscos y madera"),
)

@Composable
private fun ThemePage() {
    Text("Elige cómo se ve tu reproductor", color = SC.sub, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
    val order = skinInfo.keys.toList() + MusicSkin.entries.filter { it !in skinInfo }
    order.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { sk -> ThemeCard(sk, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).card().padding(vertical = 4.dp)) {
        SettingRow(Icons.Filled.MovieFilter, "Carátula animada o video", "Mostrarla en el reproductor cuando exista",
            on = MotionPref.on, onToggle = { MotionPref.on = it }, last = true)
    }
}

@Composable
private fun ThemeCard(sk: MusicSkin, modifier: Modifier) {
    val on = LibraryStore.musicSkin == sk
    val scale by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.96f, androidx.compose.animation.core.spring(dampingRatio = 0.6f), label = "tc")
    val info = skinInfo[sk] ?: SkinInfo(sk.label.substringBefore(" ("), sk.label.substringAfter("(", "").removeSuffix(")"))
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { LibraryStore.changeMusicSkin(sk) },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.56f)
            .shadow(if (on) 18.dp else 6.dp, RoundedCornerShape(24.dp), ambientColor = SC.accent, spotColor = SC.accent)
            .clip(RoundedCornerShape(24.dp))
            .border(if (on) 2.5.dp else 1.dp, if (on) Brush.linearGradient(listOf(SC.accent2, Nebula.Pink)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(24.dp))) {
            SkinPreview(sk, Modifier.fillMaxSize())
            if (on) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp).clip(CircleShape).background(SC.accent)
                .border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Text(info.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(info.sub, color = SC.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Mini pantalla del reproductor de cada tema, dibujada (no es foto). */
@Composable
private fun SkinPreview(sk: MusicSkin, modifier: Modifier) {
    val marco = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.bocina_marco)
    val stage = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.gn_escenario)
    val town = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.nv_pueblo)
    val ring = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.nv_aro)
    val logo = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.nv_logo)
    val tree = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.nv_arbol)
    val gems = listOf(R.drawable.gn_gema_verde, R.drawable.gn_gema_roja, R.drawable.gn_gema_amarilla, R.drawable.gn_gema_azul, R.drawable.gn_gema_naranja)
        .map { androidx.compose.ui.graphics.ImageBitmap.imageResource(it) }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun rr(c: Color, x: Float, y: Float, ww: Float, hh: Float, r: Float = hh / 2) =
            drawRoundRect(c, Offset(x, y), androidx.compose.ui.geometry.Size(ww, hh), CornerRadius(r))
        fun coverArt(x: Float, y: Float, s: Float, round: Boolean = false, c1: Color = Color(0xFFFF8A5B), c2: Color = Color(0xFF6A3FD0)) {
            val br = Brush.linearGradient(listOf(c1, c2), Offset(x, y), Offset(x + s, y + s))
            if (round) drawCircle(br, s / 2, Offset(x + s / 2, y + s / 2))
            else drawRoundRect(br, Offset(x, y), androidx.compose.ui.geometry.Size(s, s), CornerRadius(s * 0.12f))
            drawCircle(Color.White.copy(alpha = 0.25f), s * 0.16f, Offset(x + s * 0.5f, y + s * 0.5f))
        }
        fun texts(y: Float, ink: Color, center: Boolean = false) {
            val x = if (center) w * 0.25f else w * 0.1f
            rr(ink.copy(alpha = 0.9f), x, y, w * 0.5f, h * 0.018f)
            rr(ink.copy(alpha = 0.45f), x + if (center) w * 0.08f else 0f, y + h * 0.032f, w * 0.34f, h * 0.014f)
        }
        fun seek(y: Float, acc: Color, track: Color = Color.White.copy(alpha = 0.2f)) {
            rr(track, w * 0.1f, y, w * 0.8f, h * 0.008f)
            rr(acc, w * 0.1f, y, w * 0.34f, h * 0.008f)
            drawCircle(Color.White, h * 0.011f, Offset(w * 0.44f, y + h * 0.004f))
        }
        fun controls(y: Float, ink: Color, play: Brush, square: Boolean = false, btn: Color = Color.White.copy(alpha = 0.10f)) {
            val r = w * 0.055f
            listOf(0.16f, 0.32f, 0.68f, 0.84f).forEach { fx ->
                if (square) drawRoundRect(btn, Offset(w * fx - r, y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), CornerRadius(r * 0.6f))
                else drawCircle(btn, r, Offset(w * fx, y))
                drawCircle(ink.copy(alpha = 0.8f), r * 0.28f, Offset(w * fx, y))
            }
            drawCircle(play, w * 0.1f, Offset(w * 0.5f, y))
            val p = Path().apply { moveTo(w * 0.475f, y - w * 0.035f); lineTo(w * 0.53f, y); lineTo(w * 0.475f, y + w * 0.035f); close() }
            drawPath(p, Color.White)
        }
        when (sk) {
            MusicSkin.NEBULA, MusicSkin.WALKMAN -> {
                val orange = sk == MusicSkin.WALKMAN
                val pink = if (orange) Color(0xFFFF8A1F) else Color(0xFFFF4F9A)
                val pur = if (orange) Color(0xFF7B2FF7) else Color(0xFF8B5CF6)
                drawRect(Brush.verticalGradient(listOf(if (orange) Color(0xFF1A0E26) else Color(0xFF1B1038), Color(0xFF080512))))
                drawCircle(Brush.radialGradient(listOf(pur.copy(alpha = 0.5f), Color.Transparent), Offset(w, 0f), w * 0.8f), w * 0.8f, Offset(w, 0f))
                drawCircle(Brush.radialGradient(listOf(pink.copy(alpha = 0.3f), Color.Transparent), Offset(0f, h * 0.6f), w * 0.7f), w * 0.7f, Offset(0f, h * 0.6f))
                repeat(22) { val r = kotlin.random.Random(it * 31); drawCircle(Color.White.copy(alpha = 0.6f * r.nextFloat()), 1.2f + r.nextFloat() * 1.5f, Offset(r.nextFloat() * w, r.nextFloat() * h)) }
                drawCircle(Brush.sweepGradient(listOf(pink, pur, Color(0xFF8FD3FF), pink), Offset(w / 2, h * 0.32f)), w * 0.40f, Offset(w / 2, h * 0.32f), style = Stroke(2.dp.toPx()))
                coverArt(w * 0.2f, h * 0.32f - w * 0.3f, w * 0.6f, c1 = pink, c2 = pur)
                texts(h * 0.6f, Color.White); seek(h * 0.71f, pink)
                controls(h * 0.82f, Color.White, Brush.radialGradient(listOf(pink.copy(alpha = 0.8f), pink)), square = true)
                rr(Color.White.copy(alpha = 0.08f), w * 0.08f, h * 0.91f, w * 0.84f, h * 0.05f)
            }
            MusicSkin.BOCINA -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0D1E3A), Color(0xFF11264A), Color(0xFF060D1B))))
                val p = Path().apply { moveTo(w * 0.35f, 0f); quadraticBezierTo(w * 0.62f, h * 0.08f, w, h * 0.11f) }
                drawPath(p, Color(0xFFBFE2FF).copy(alpha = 0.7f), style = Stroke(1.2.dp.toPx()))
                val cy = h * 0.33f; val r = w * 0.36f
                for (i in 0 until 9) for (side in listOf(-1f, 1f)) {
                    val x = w / 2 + side * (r * 0.9f + i * w * 0.022f); val hh = h * 0.05f * (1f - i * 0.1f) * (0.5f + 0.5f * kotlin.math.abs(sin(i * 1.7f)))
                    drawLine(Color(0xFFBFE2FF).copy(alpha = 0.8f), Offset(x, cy - hh), Offset(x, cy + hh), 1.5.dp.toPx())
                }
                drawImage(marco, dstOffset = IntOffset((w / 2 - r).toInt(), (cy - r).toInt()), dstSize = IntSize((r * 2).toInt(), (r * 2).toInt()))
                drawCircle(Color(0xFFBFE2FF), r * 1.1f, Offset(w / 2, cy), style = Stroke(1.5.dp.toPx()))
                coverArt(w / 2 - r * 0.5f, cy - r * 0.5f, r, round = true, c1 = Color(0xFF4FC3F7), c2 = Color(0xFF283593))
                texts(h * 0.6f, Color.White); seek(h * 0.71f, Color(0xFF7FC4FF))
                controls(h * 0.82f, Color.White, Brush.radialGradient(listOf(Color(0xFF9FD2FF), Color(0xFF2E5C9A))), btn = Color(0xFF2E3D58))
                rr(Color(0xFF2A3A55), w * 0.08f, h * 0.91f, w * 0.84f, h * 0.05f)
            }
            MusicSkin.GUITAR -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF120C34), Color(0xFF05040F))))
                val sh = w * stage.height / stage.width * 1.3f
                drawImage(stage, dstOffset = IntOffset((-w * 0.15f).toInt(), 0), dstSize = IntSize((w * 1.3f).toInt(), sh.toInt()), alpha = 0.95f)
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0A0820)), sh * 0.5f, sh), Offset(0f, sh * 0.5f), androidx.compose.ui.geometry.Size(w, h))
                texts(h * 0.06f, Color.White, center = true)
                coverArt(w * 0.33f, h * 0.15f, w * 0.34f, c1 = Color(0xFF6FA8FF), c2 = Color(0xFF1C2A7A))
                val y0 = h * 0.15f + w * 0.32f; val y1 = h * 0.72f
                val road = Path().apply { moveTo(w * 0.37f, y0); lineTo(w * 0.63f, y0); lineTo(w * 0.98f, y1); lineTo(w * 0.02f, y1); close() }
                drawPath(road, Color(0xFF15102F))
                drawPath(road, Brush.linearGradient(listOf(Color(0xFF4C7DFF), Color(0xFFA35CFF))), style = Stroke(1.5.dp.toPx()))
                for (i in 0 until 5) {
                    val xt = w / 2 + (i - 2) * w * 0.052f; val xb = w / 2 + (i - 2) * w * 0.19f
                    drawLine(Guitar.lanes[i], Offset(xt, y0), Offset(xb, y1 - h * 0.03f), 1.2.dp.toPx())
                    val g = gems[i]; val gw = w * 0.17f; val gh = gw * g.height / g.width
                    drawImage(g, dstOffset = IntOffset((xb - gw / 2).toInt(), (y1 - h * 0.03f - gh / 2).toInt()), dstSize = IntSize(gw.toInt(), gh.toInt()))
                    val z = listOf(0.3f, 0.6f, 0.45f, 0.2f, 0.75f)[i]
                    val nx = xt + (xb - xt) * z; val ny = y0 + (y1 - y0) * z; val nw = gw * (0.35f + 0.4f * z)
                    drawImage(g, dstOffset = IntOffset((nx - nw / 2).toInt(), (ny - nw * 0.3f).toInt()), dstSize = IntSize(nw.toInt(), (nw * g.height / g.width).toInt()))
                }
                seek(h * 0.79f, Color(0xFFA35CFF))
                controls(h * 0.88f, Color.White, Brush.radialGradient(listOf(Color(0xFF2B1F5C), Color(0xFF120D2C))), square = true, btn = Color(0xFF231F45))
                drawCircle(Color(0xFFA35CFF), w * 0.1f, Offset(w * 0.5f, h * 0.88f), style = Stroke(2.dp.toPx()))
            }
            MusicSkin.NAVIDAD -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF07122E), Color(0xFF0A1C45), Color(0xFF12306A))))
                val tw = w * 2.2f; val th = tw * town.height / town.width
                drawImage(town, dstOffset = IntOffset((-w * 0.6f).toInt(), (h * 0.36f).toInt()), dstSize = IntSize(tw.toInt(), th.toInt()))
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1C45), Color.Transparent), h * 0.36f, h * 0.36f + th * 0.45f), Offset(0f, h * 0.36f), androidx.compose.ui.geometry.Size(w, th * 0.45f))
                drawRect(Brush.verticalGradient(listOf(Color(0xFF5F86C0), Color(0xFF13285A), Color(0xFF07122E)), h * 0.36f + th * 0.9f, h), Offset(0f, h * 0.36f + th * 0.9f), androidx.compose.ui.geometry.Size(w, h))
                val trw = w * 0.5f; val trh = trw * tree.height / tree.width
                drawImage(tree, dstOffset = IntOffset((-trw * 0.25f).toInt(), (h * 0.36f + th * 0.9f - trh * 0.8f).toInt()), dstSize = IntSize(trw.toInt(), trh.toInt()))
                val lw = w * 0.5f; val lh = lw * logo.height / logo.width
                drawImage(logo, dstOffset = IntOffset(((w - lw) / 2).toInt(), (h * 0.04f).toInt()), dstSize = IntSize(lw.toInt(), lh.toInt()))
                val cy = h * 0.3f; val r = w * 0.34f
                coverArt(w / 2 - r * 0.76f, cy - r * 0.76f, r * 1.52f, round = true, c1 = Color(0xFF3DFFC8), c2 = Color(0xFF1A3A8A))
                drawImage(ring, dstOffset = IntOffset((w / 2 - r).toInt(), (cy - r).toInt()), dstSize = IntSize((r * 2).toInt(), (r * 2).toInt()))
                repeat(40) { val rr = kotlin.random.Random(it * 13); drawCircle(Color.White.copy(alpha = 0.5f + rr.nextFloat() * 0.5f), 1f + rr.nextFloat() * 2f, Offset(rr.nextFloat() * w, rr.nextFloat() * h)) }
                seek(h * 0.72f, Color(0xFFCDEBFF))
                controls(h * 0.82f, Color.White, Brush.radialGradient(listOf(Color(0xFF2A67C0), Color(0xFF0D2552))), square = true, btn = Color(0xFF1F4E92))
                rr(Color(0xFF1F4E92), w * 0.08f, h * 0.91f, w * 0.84f, h * 0.05f)
            }
            MusicSkin.LAVANDA -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFFBF9FF), Color(0xFFF1EBFC))))
                coverArt(w * 0.15f, h * 0.1f, w * 0.7f, c1 = Color(0xFFB79BF2), c2 = Color(0xFF6A8BFF))
                texts(h * 0.55f, Color(0xFF221B33)); seek(h * 0.67f, Color(0xFF7C4DDB), Color(0xFF7C4DDB).copy(alpha = 0.18f))
                controls(h * 0.8f, Color(0xFF6A3FD0), Brush.radialGradient(listOf(Color(0xFFB79BF2), Color(0xFF7C4DDB))), btn = Color.White)
            }
            MusicSkin.MEDIA_PLAYER -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF1C3463), Color(0xFF0B1630))))
                coverArt(w * 0.12f, h * 0.08f, w * 0.76f, c1 = Color(0xFF3FA9F5), c2 = Color(0xFF0B2E6E))
                texts(h * 0.56f, Color.White); seek(h * 0.68f, Color(0xFF3FA9F5))
                controls(h * 0.8f, Color.White, Brush.radialGradient(listOf(Color.White, Color(0xFF3FA9F5), Color(0xFF0B2E6E))))
            }
            MusicSkin.VINTAGE -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF5A3517), Color(0xFF2A190C))))
                val c = Offset(w / 2, h * 0.32f)
                drawCircle(Color(0xFF111111), w * 0.38f, c)
                for (k in 1..5) drawCircle(Color.White.copy(alpha = 0.06f), w * 0.38f * (0.4f + k * 0.11f), c, style = Stroke(1f))
                drawCircle(Color(0xFFC9A15A), w * 0.12f, c)
                drawLine(Color(0xFFDCC28A), Offset(w * 0.86f, h * 0.1f), Offset(w * 0.62f, h * 0.38f), 2.dp.toPx(), StrokeCap.Round)
                texts(h * 0.6f, Color(0xFFF2E3C6)); seek(h * 0.71f, Color(0xFFC9A15A))
                controls(h * 0.82f, Color(0xFFF2E3C6), Brush.radialGradient(listOf(Color(0xFFDCC28A), Color(0xFF8B5A2B))))
            }
            else -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF26304A), Color(0xFF101522))))
                coverArt(w * 0.12f, h * 0.08f, w * 0.76f, c1 = Color(0xFFE07A5F), c2 = Color(0xFF3D405B))
                texts(h * 0.56f, Color.White); seek(h * 0.68f, Color(0xFFE07A5F))
                controls(h * 0.8f, Color.White, Brush.radialGradient(listOf(Color(0xFFE07A5F), Color(0xFFE07A5F))))
            }
        }
    }
}

// ───────── Letras ─────────

@Composable
private fun LyricsPage() {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).card().padding(vertical = 4.dp)) {
        val opts = listOf("below" to "Debajo de la carátula", "big" to "Debajo de la carátula, grande", "over" to "Encima de la carátula", "float" to "En una ventana flotante")
        opts.forEachIndexed { i, (id, label) ->
            val on = LibraryStore.lyricsPlace == id
            Row(Modifier.fillMaxWidth().clickable { LibraryStore.changeLyricsPlace(id) }.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (on) Icon(Icons.Filled.Check, null, tint = SC.accent2)
            }
            if (i < opts.lastIndex) Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(SC.line))
        }
    }
    Text("La ventana flotante se ve en toda la app y la puedes arrastrar; con la ✕ se esconde hasta la siguiente canción.", color = SC.sub, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
}

// ───────── Listas visibles ─────────

@Composable
private fun ListsPage() {
    Text("Escoge cuáles se ven en el menú de la biblioteca.", color = SC.sub, fontSize = 12.sp, modifier = Modifier.padding(4.dp))
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).card().padding(vertical = 4.dp)) {
        LibraryListNames.forEachIndexed { i, (id, label) ->
            SettingRow(Icons.Filled.LibraryMusic, label, "", last = i == LibraryListNames.lastIndex,
                on = id !in LibraryStore.hiddenLists, onToggle = { LibraryStore.toggleListVisible(id) })
        }
    }
}

@Suppress("unused") private val keepIcon = Icons.Filled.SpaceDashboard
