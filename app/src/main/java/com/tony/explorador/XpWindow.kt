package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Posición, tamaño y estado de una ventana del escritorio. Vive fuera de la pantalla para que no se pierda. */
@Stable
class WinState(width: Dp, height: Dp, val minWidth: Dp = 220.dp, val minHeight: Dp = 180.dp) {
    var offset by mutableStateOf(Offset.Zero)
    var width by mutableStateOf(width)
    var height by mutableStateOf(height)
    var open by mutableStateOf(false)
    var minimized by mutableStateOf(false)
    var maximized by mutableStateOf(false)
    /** Orden de encimado: la última que tocaste queda arriba, como en Windows. */
    var z by mutableStateOf(0f)
    fun toFront() { if (z < WinZ.top) z = WinZ.next() }
}

object WinZ {
    var top = 0f
        private set
    fun next(): Float { top += 1f; return top }
}

val XpRedButton = Brush.verticalGradient(listOf(Color(0xFFFF7AA8), Color(0xFFD0306A)))
val XpBlueButton = Brush.verticalGradient(listOf(Color(0xFF9B7BFF), Color(0xFF5B3FD0)))

/**
 * Marco de ventana estilo XP: barra de título para arrastrar, botones _ □ ✕
 * y esquina inferior derecha para cambiar el tamaño.
 */
@Composable
fun XpWindowFrame(
    state: WinState,
    title: String,
    icon: @Composable () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onMaximize: (() -> Unit)? = { state.maximized = !state.maximized },
    extraCaption: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val shape = if (state.maximized) RoundedCornerShape(0.dp)
    else RoundedCornerShape(16.dp)

    val base = if (state.maximized) Modifier.fillMaxSize()
    else Modifier
        .offset { IntOffset(state.offset.x.roundToInt(), state.offset.y.roundToInt()) }
        .size(state.width, state.height)

    // al aparecer, queda encima de las demás
    androidx.compose.runtime.LaunchedEffect(Unit) { state.toFront() }
    Box(Modifier.zIndex(state.z).then(modifier).then(base)
        .pointerInput(Unit) {
            // cualquier toque en la ventana la pasa al frente (sin quitarle el toque a lo de adentro)
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.toFront()
            }
        }
        .shadow(14.dp, shape).clip(shape).background(XpColors.TitleBottom)) {
        Column(Modifier.fillMaxSize().padding(start = 3.dp, end = 3.dp, bottom = 3.dp)) {
            Row(
                Modifier.fillMaxWidth().height(30.dp)
                    .background(XpColors.titleBrush)
                    .pointerInput(state.maximized) {
                        if (!state.maximized) detectDragGestures { change, amount ->
                            change.consume()
                            state.offset += amount
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onMaximize?.invoke() }) }
                    .padding(start = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Spacer(Modifier.width(6.dp))
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (extraCaption != null) { extraCaption(); Spacer(Modifier.width(2.dp)) }
                XpCaptionButton("_", XpBlueButton) { state.minimized = true }
                if (onMaximize != null) {
                    Spacer(Modifier.width(2.dp))
                    XpCaptionButton(if (state.maximized) "❐" else "□", XpBlueButton, onMaximize)
                }
                Spacer(Modifier.width(2.dp))
                XpCaptionButton("✕", XpRedButton, onClose)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }

        // Esquina para cambiar el tamaño
        if (!state.maximized) {
            Canvas(
                Modifier.align(Alignment.BottomEnd).size(26.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            with(density) {
                                state.width = (state.width + amount.x.toDp()).coerceIn(state.minWidth, 900.dp)
                                state.height = (state.height + amount.y.toDp()).coerceIn(state.minHeight, 1400.dp)
                            }
                        }
                    }
            ) {
                // rayitas diagonales de agarre
                val c = Color(0xFF8A8A8A)
                val hl = Color.White
                for (i in 1..3) {
                    val d = i * 5.dp.toPx()
                    drawLine(hl, Offset(size.width - d, size.height - 2f), Offset(size.width - 2f, size.height - d), strokeWidth = 2f)
                    drawLine(c, Offset(size.width - d + 2f, size.height - 2f), Offset(size.width - 2f, size.height - d + 2f), strokeWidth = 2f)
                }
            }
        }
    }
}

@Composable
fun XpCaptionButton(label: String, brush: Brush, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
