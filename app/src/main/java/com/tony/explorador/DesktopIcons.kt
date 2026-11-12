package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Un programa que puede estar en el escritorio o en el menú Inicio. */
data class DeskApp(val id: String, val label: String, val kind: XpIconKind, val action: () -> Unit)

private val CellW = 92.dp
private val CellH = 108.dp

/**
 * Íconos del escritorio acomodados en cuadrícula.
 * Toca para abrir; deja presionado y arrastra para moverlo; deja presionado y suelta para ver opciones.
 * Deja presionado en un espacio vacío para personalizar.
 */
@Composable
fun DesktopIconsArea(apps: Map<String, DeskApp>, modifier: Modifier, onChangeWallpaper: () -> Unit = {}) {
    var customize by remember { mutableStateOf(false) }
    var bgMenu by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { bgMenu = it })
        }
    ) {
        val cols = (maxWidth / CellW).toInt().coerceAtLeast(1)
        val rows = (maxHeight / CellH).toInt().coerceAtLeast(1)
        val visible = LibraryStore.desktopIds.filter { it in apps }

        // Lugar de cada ícono: el guardado si está libre, si no el siguiente hueco
        val placed = LinkedHashMap<String, Pair<Int, Int>>()
        val taken = HashSet<Pair<Int, Int>>()
        visible.forEach { id ->
            LibraryStore.desktopPos[id]?.split(",")?.let { p ->
                val c = p[0].toIntOrNull(); val r = p.getOrNull(1)?.toIntOrNull()
                if (c != null && r != null && c < cols && r < rows && (c to r) !in taken) {
                    placed[id] = c to r; taken += c to r
                }
            }
        }
        var next = 0
        visible.forEach { id ->
            if (id !in placed) {
                while ((next % cols to next / cols) in taken) next++
                val cell = next % cols to next / cols
                placed[id] = cell; taken += cell
            }
        }

        placed.forEach { (id, cell) ->
            val app = apps.getValue(id)
            androidx.compose.runtime.key(id) {
                MovableIcon(
                    app = app,
                    cell = cell,
                    isShortcut = id in LibraryStore.desktopShortcuts,
                    onDrop = { dropOffset ->
                        with(density) {
                            val col = ((cell.first * CellW.toPx() + dropOffset.x + CellW.toPx() / 2) / CellW.toPx()).toInt().coerceIn(0, cols - 1)
                            val row = ((cell.second * CellH.toPx() + dropOffset.y + CellH.toPx() / 2) / CellH.toPx()).toInt().coerceIn(0, rows - 1)
                            // si cae encima de otro, se intercambian
                            placed.forEach { (oid, oc) -> if (oid != id) LibraryStore.desktopPos.putIfAbsent(oid, "${oc.first},${oc.second}") }
                            LibraryStore.moveDesktopIcon(id, col, row)
                        }
                    },
                    onRemove = { LibraryStore.setDesktopVisible(id, false) },
                )
            }
        }

        // Menú del escritorio (dejar presionado en vacío)
        bgMenu?.let { at ->
            Box(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }) {
                DropdownMenu(expanded = true, onDismissRequest = { bgMenu = null }) {
                    DropdownMenuItem(text = { Text("Personalizar escritorio…") }, onClick = { bgMenu = null; customize = true })
                    DropdownMenuItem(text = { Text("Organizar íconos") }, onClick = { bgMenu = null; LibraryStore.resetDesktopPositions() })
                    DropdownMenuItem(text = { Text("Cambiar fondo de pantalla…") }, onClick = { bgMenu = null; onChangeWallpaper() })
                }
            }
        }
    }

    if (customize) CustomizeDesktopDialog(apps, onDismiss = { customize = false })
}

@Composable
private fun MovableIcon(
    app: DeskApp,
    cell: Pair<Int, Int>,
    isShortcut: Boolean,
    onDrop: (Offset) -> Unit,
    onRemove: () -> Unit,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var lifted by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val open by rememberUpdatedState(app.action)
    val drop by rememberUpdatedState(onDrop)

    Box(
        Modifier
            .offset(x = CellW * cell.first, y = CellH * cell.second)
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .zIndex(if (lifted) 10f else 0f)
            .width(CellW)
            .graphicsLayer {
                val s = if (lifted) 1.12f else 1f
                scaleX = s; scaleY = s
                alpha = if (lifted) 0.85f else 1f
            }
            .pointerInput(app.id) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    // 1 = toque normal, 2 = se canceló, null = se quedó presionado
                    val r = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        if (waitForUpOrCancellation() != null) 1 else 2
                    }
                    when (r) {
                        1 -> open()
                        2 -> {}
                        null -> {
                            lifted = true
                            var moved = 0f
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                val d = ch.positionChange()
                                drag += d
                                moved += d.getDistance()
                                ch.consume()
                            }
                            lifted = false
                            if (moved < 12f) menu = true else drop(drag)
                            drag = Offset.Zero
                        }
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxWidth().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                XpIcon(app.kind, 54.dp)
                if (app.id == "updates" && UpdateBadge.available) NotificationDot(Modifier.align(Alignment.TopEnd))
                if (isShortcut) ShortcutArrow(Modifier.align(Alignment.BottomStart))
            }
            Spacer(Modifier.size(2.dp))
            Text(
                app.label,
                style = TextStyle(
                    color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
                    shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(1.5f, 1.5f), 2f)
                ),
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(10.dp, (-20).dp)) {
            DropdownMenuItem(text = { Text("Abrir") }, onClick = { menu = false; app.action() })
            DropdownMenuItem(text = { Text("Quitar del escritorio") }, onClick = { menu = false; onRemove() })
        }
    }
}

/** Flechita de "acceso directo" en la esquina del ícono. */
@Composable
fun ShortcutArrow(modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        drawRect(Color.White, size = size)
        drawRect(Color(0xFF7F9DB9), size = size, style = Stroke(1.5f))
        val w = size.width
        val p = Path().apply {
            moveTo(w * 0.25f, w * 0.8f)
            cubicTo(w * 0.25f, w * 0.45f, w * 0.45f, w * 0.35f, w * 0.7f, w * 0.35f)
        }
        drawPath(p, Color(0xFF1E5CC8), style = Stroke(w * 0.13f))
        val head = Path().apply {
            moveTo(w * 0.55f, w * 0.15f); lineTo(w * 0.85f, w * 0.35f); lineTo(w * 0.55f, w * 0.55f); close()
        }
        drawPath(head, Color(0xFF1E5CC8))
    }
}

@Composable
private fun CustomizeDesktopDialog(apps: Map<String, DeskApp>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Íconos del escritorio") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps.values.toList(), key = { it.id }) { app ->
                    val on = app.id in LibraryStore.desktopIds
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                            .clickable { LibraryStore.setDesktopVisible(app.id, !on) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = on, onCheckedChange = { LibraryStore.setDesktopVisible(app.id, it) })
                        XpIcon(app.kind, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(app.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } }
    )
}

@Suppress("unused")
private fun unusedBg() = Modifier.background(Color.Transparent)
