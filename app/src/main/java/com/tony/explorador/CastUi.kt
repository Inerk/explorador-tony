package com.tony.explorador

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Elegir a qué pantalla mandar el archivo. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CastPickerSheet(file: File, siblings: List<File>, onDismiss: () -> Unit) {
    DisposableEffect(Unit) {
        CastManager.startDiscovery()
        onDispose { CastManager.stopDiscovery() }
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ExpTheme.bar) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cast, null, tint = ExpTheme.onBar, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Mandar a la pantalla", color = ExpTheme.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(file.name, color = ExpTheme.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (CastManager.searching) CircularProgressIndicator(color = ExpTheme.onBar, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(12.dp))
            if (CastManager.targets.isEmpty()) {
                Text(
                    if (CastManager.searching) "Buscando pantallas en tu wifi…"
                    else "No encontré pantallas. Revisa que la TV esté prendida y en el mismo wifi que tu teléfono.\n\nEn LG: Ajustes → General → Dispositivos → «LG Connect Apps» o «Compartir pantalla» encendido.",
                    color = ExpTheme.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp)
                )
                if (!CastManager.searching) Text("Buscar otra vez", color = ExpTheme.onBar, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).background(ExpTheme.crumbBg).clickable { CastManager.startDiscovery() }
                        .padding(horizontal = 16.dp, vertical = 8.dp))
            }
            CastManager.targets.forEach { t ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { CastManager.send(t, file, siblings); onDismiss() }
                    .padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftIcon(if (t is CastTarget.Chromecast) Icons.Filled.Cast else Icons.Filled.Tv, ExpTheme.onBar, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, color = ExpTheme.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                        Text((if (t is CastTarget.Chromecast) "Google Cast · " else "DLNA · ") + t.detail, color = ExpTheme.sub, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun fmtT(ms: Long): String { val t = (ms / 1000).coerceAtLeast(0); return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, (t / 60) % 60, t % 60) else "%d:%02d".format(t / 60, t % 60) }

/** Control de lo que se está viendo en la TV: pastilla abajo que se abre con más controles. */
@Composable
fun CastControlBar(modifier: Modifier = Modifier) {
    val f = CastManager.file
    AnimatedVisibility(CastManager.active && f != null, modifier = modifier, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        val file = f ?: return@AnimatedVisibility
        val t = CastManager.target ?: return@AnimatedVisibility
        val isImage = mimeOf(file).startsWith("image/")
        var dragging by remember { mutableStateOf<Float?>(null) }
        Column(
            Modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth().shadow(14.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp)).background(ExpTheme.bar).padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { CastManager.expanded = !CastManager.expanded }) {
                Icon(Icons.Filled.Cast, null, tint = ExpTheme.onBar, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(file.nameWithoutExtension, color = ExpTheme.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(CastManager.status ?: "En ${t.name}", color = if (CastManager.status?.startsWith("No") == true) Color(0xFFD93A3A) else ExpTheme.sub,
                        fontSize = 12.sp, maxLines = 2)
                }
                if (!isImage) Box(Modifier.size(40.dp).clip(CircleShape).background(ExpTheme.onBar).clickable { CastManager.togglePlay() },
                    contentAlignment = Alignment.Center) {
                    Icon(if (CastManager.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.padding(start = 6.dp).size(36.dp).clip(CircleShape).clickable { CastManager.stop() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Stop, "Dejar de mandar", tint = ExpTheme.onBar, modifier = Modifier.size(22.dp))
                }
                Icon(if (CastManager.expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, null, tint = ExpTheme.sub, modifier = Modifier.size(20.dp))
            }
            if (CastManager.expanded) {
                if (!isImage) {
                    val dur = CastManager.duration.coerceAtLeast(1)
                    Slider(
                        value = dragging ?: (CastManager.position.toFloat() / dur).coerceIn(0f, 1f),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = { dragging?.let { CastManager.seek((it * dur).toLong()) }; dragging = null },
                        colors = SliderDefaults.colors(thumbColor = ExpTheme.onBar, activeTrackColor = ExpTheme.onBar, inactiveTrackColor = ExpTheme.line),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmtT(CastManager.position), color = ExpTheme.sub, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(fmtT(CastManager.duration), color = ExpTheme.sub, fontSize = 11.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    if (isImage) {
                        Pill(Icons.Filled.ChevronLeft, "Anterior") { CastManager.step(-1) }
                        Pill(Icons.Filled.ChevronRight, "Siguiente") { CastManager.step(1) }
                    } else {
                        Pill(Icons.Filled.VolumeDown, "Vol −") { CastManager.changeVolume(-5) }
                        if (CastManager.volume >= 0) Text("${CastManager.volume}", color = ExpTheme.sub, fontSize = 12.sp)
                        Pill(Icons.Filled.VolumeUp, "Vol +") { CastManager.changeVolume(5) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.clip(CircleShape).background(ExpTheme.crumbBg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ExpTheme.onBar, modifier = Modifier.size(18.dp))
        Text(" $label", color = ExpTheme.onBar, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
