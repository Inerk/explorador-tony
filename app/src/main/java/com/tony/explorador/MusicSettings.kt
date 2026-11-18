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

/*
 * «Ajustes de música» con el diseño Nebula: tarjetas de cristal por sección y páginas para
 * Ecualizador, Efectos de sonido, Visualizador (barra de avance), Tema y apariencia, Letras y Biblioteca.
 */

private object SC {
    val accent = Color(0xFF8B5CF6)
    val accent2 = Color(0xFFB57BFF)
    val text = Color.White
    val sub = Color(0xFFB9ABDA)
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
    var page by remember { mutableStateOf(SPage.Main) }
    BackHandler { if (page != SPage.Main) page = SPage.Main else onClose() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1038), Color(0xFF120B26), Color(0xFF080512))))) {
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
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1A0F3D), Color(0xFF2B1666)), c, r), r, c)
            drawCircle(Brush.sweepGradient(listOf(SC.accent2, Color(0xFFFF4F9A), Color(0xFF8FD3FF), SC.accent2), c), r, c,
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
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Brush.linearGradient(listOf(SC.accent, Color(0xFF5B7CFF)))),
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
            BigTile(Icons.Filled.Waves, "Visualizador", SeekStyles.firstOrNull { it.first == LibraryStore.seekStyle }?.second ?: "Barra", Modifier.weight(1f)) { onOpen(SPage.Visual) }
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
        modifier = modifier.clip(CircleShape).background(if (on) Brush.linearGradient(listOf(SC.accent, Color(0xFF5B7CFF))) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.05f))))
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
        drawLine(Brush.verticalGradient(listOf(Color(0xFFFF4F9A), SC.accent, Color(0xFF5B7CFF))), Offset(cx, zero), Offset(cx, y), w, StrokeCap.Round)
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

@Composable
private fun ThemePage() {
    val previews = mapOf(
        MusicSkin.NEBULA to listOf(Color(0xFF1B1038), Color(0xFF8B5CF6), Color(0xFFFF4F9A)),
        MusicSkin.LAVANDA to listOf(Color(0xFFF7F4FD), Color(0xFFB79BF2), Color(0xFF4F7BFF)),
        MusicSkin.WALKMAN to listOf(Color(0xFF120A1C), Color(0xFFFF8A1F), Color(0xFF7B2FF7)),
        MusicSkin.MEDIA_PLAYER to listOf(Color(0xFF0B1630), Color(0xFF3FA9F5), Color(0xFF9CDBFF)),
        MusicSkin.VINTAGE to listOf(Color(0xFF3A2416), Color(0xFFC9A15A), Color(0xFFF2E3C6)),
        MusicSkin.ORIGINAL to listOf(Color(0xFF26304A), Color(0xFFE07A5F), Color(0xFF81B29A)),
    )
    MusicSkin.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { sk ->
                val on = LibraryStore.musicSkin == sk
                val c = previews[sk] ?: listOf(Color.DarkGray, Color.Gray, Color.LightGray)
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (on) SC.accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                    .border(if (on) 1.5.dp else 1.dp, if (on) SC.accent2 else Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                    .clickable { LibraryStore.changeMusicSkin(sk) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp))) {
                        drawRect(Brush.verticalGradient(listOf(c[0], c[0].copy(alpha = 0.8f))))
                        drawRoundRect(c[1], Offset(size.width * 0.12f, size.height * 0.2f), androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height * 0.6f), CornerRadius(8f))
                        drawRoundRect(c[2].copy(alpha = 0.8f), Offset(size.width * 0.5f, size.height * 0.3f), androidx.compose.ui.geometry.Size(size.width * 0.38f, 6f), CornerRadius(3f))
                        drawRoundRect(c[2].copy(alpha = 0.5f), Offset(size.width * 0.5f, size.height * 0.55f), androidx.compose.ui.geometry.Size(size.width * 0.28f, 6f), CornerRadius(3f))
                    }
                    Text(sk.label.substringBefore(" ("), color = Color.White, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).card().padding(vertical = 4.dp)) {
        SettingRow(Icons.Filled.MovieFilter, "Carátula animada o video", "Mostrarla en el reproductor cuando exista",
            on = MotionPref.on, onToggle = { MotionPref.on = it }, last = true)
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
