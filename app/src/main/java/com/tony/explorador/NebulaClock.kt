package com.tony.explorador

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val mx = Locale("es", "MX")
private val Neon1 = Color(0xFF6EE7FF)
private val Neon2 = Color(0xFFB98CFF)
private val Neon3 = Color(0xFFFF7AC6)

/** Un dígito que cae suavemente cuando cambia (como reloj digital de tablero). */
@Composable
private fun RollingDigit(ch: Char, style: TextStyle) {
    AnimatedContent(ch, transitionSpec = {
        (slideInVertically(tween(320)) { -it } + fadeIn(tween(320))) togetherWith (slideOutVertically(tween(320)) { it } + fadeOut(tween(200)))
    }, label = "dig") { c -> Text(c.toString(), style = style) }
}

/** Reloj digital de DriveNebula: hora grande con dígitos que ruedan, anillo de segundos y calendario. */
@Composable
fun NebulaClockWindow(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow(50)
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
    val h12 = (cal.get(Calendar.HOUR).takeIf { it != 0 } ?: 12)
    val hh = h12.toString().padStart(2, ' ')
    val mm = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    val ss = cal.get(Calendar.SECOND).toString().padStart(2, '0')
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "a.m." else "p.m."
    val dayFrac = (cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + sec) / 86400f
    var shift by remember { mutableIntStateOf(0) }
    val blink = rememberInfiniteTransition(label = "blink")
    val colon by blink.animateFloat(1f, 0.25f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "c")
    val shape = RoundedCornerShape(28.dp)

    val big = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 50.sp, color = Color.White,
        shadow = Shadow(Neon1.copy(alpha = 0.9f), Offset.Zero, 22f))
    Column(
        modifier.width(320.dp).shadow(24.dp, shape).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0E1230), Color(0xFF1B1646), Color(0xFF2A1250))))
            .border(1.dp, Brush.linearGradient(listOf(Neon1.copy(alpha = 0.6f), Neon3.copy(alpha = 0.4f))), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // anillo de segundos con la hora adentro
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(230.dp)) {
                val r = size.minDimension / 2f - 10.dp.toPx()
                val c = Offset(size.width / 2, size.height / 2)
                // 60 marquitas: se encienden las que ya pasaron
                for (i in 0 until 60) {
                    val a = Math.toRadians((i * 6 - 90).toDouble())
                    val lit = i <= sec.toInt()
                    val len = if (i % 5 == 0) 10.dp.toPx() else 5.dp.toPx()
                    val p1 = Offset(c.x + (r - len) * Math.cos(a).toFloat(), c.y + (r - len) * Math.sin(a).toFloat())
                    val p2 = Offset(c.x + r * Math.cos(a).toFloat(), c.y + r * Math.sin(a).toFloat())
                    drawLine(if (lit) (if (i % 5 == 0) Neon3 else Neon1) else Color.White.copy(alpha = 0.12f), p1, p2,
                        strokeWidth = if (i % 5 == 0) 3.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round)
                }
                // arco suave que avanza con los segundos
                val rr = r + 6.dp.toPx()
                drawArc(Brush.sweepGradient(listOf(Neon2, Neon1, Neon3, Neon2), c), -90f, sec / 60f * 360f, false,
                    Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                // puntito que corre en la punta
                val a = Math.toRadians((sec / 60f * 360f - 90f).toDouble())
                drawCircle(Color.White, 4.5.dp.toPx(), Offset(c.x + rr * Math.cos(a).toFloat(), c.y + rr * Math.sin(a).toFloat()))
                drawCircle(Neon1.copy(alpha = 0.35f), 10.dp.toPx(), Offset(c.x + rr * Math.cos(a).toFloat(), c.y + rr * Math.sin(a).toFloat()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(SimpleDateFormat("EEEE", mx).format(Date(now)).uppercase(), color = Neon1, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    hh.forEach { if (it != ' ') RollingDigit(it, big) }
                    Text(":", style = big.copy(color = Color.White.copy(alpha = colon)))
                    mm.forEach { RollingDigit(it, big) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val small = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Neon3,
                        shadow = Shadow(Neon3.copy(alpha = 0.8f), Offset.Zero, 14f))
                    ss.forEach { RollingDigit(it, small) }
                    Spacer(Modifier.width(8.dp))
                    Text(ampm, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(SimpleDateFormat("d 'de' MMMM 'de' yyyy", mx).format(Date(now)), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        // cuánto va del día
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Día", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(dayFrac).height(6.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Neon1, Neon2, Neon3))))
            }
            Text("${(dayFrac * 100).toInt()}%", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        NeonCalendar(cal, shift) { shift += it }
    }
}

@Composable
private fun NeonCalendar(today: Calendar, shift: Int, onShift: (Int) -> Unit) {
    val m = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, shift) }
    val isThisMonth = shift == 0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mes anterior", tint = Color.White,
                modifier = Modifier.size(30.dp).clip(CircleShape).clickable { onShift(-1) }.padding(4.dp))
            Text(SimpleDateFormat("MMMM yyyy", mx).format(m.time).replaceFirstChar { it.uppercase() }, color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                    .clickable(enabled = shift != 0) { onShift(-shift) })
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mes siguiente", tint = Color.White,
                modifier = Modifier.size(30.dp).clip(CircleShape).clickable { onShift(1) }.padding(4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                Text(it, color = Neon1.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val first = (m.get(Calendar.DAY_OF_WEEK) + 5) % 7   // lunes = 0
        val days = m.getActualMaximum(Calendar.DAY_OF_MONTH)
        val todayN = today.get(Calendar.DAY_OF_MONTH)
        val cells = first + days
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0 until 7) {
                    val d = row * 7 + col - first + 1
                    Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                        if (d in 1..days) {
                            val isToday = isThisMonth && d == todayN
                            Box(Modifier.size(26.dp).clip(CircleShape)
                                .then(if (isToday) Modifier.background(Brush.linearGradient(listOf(Neon2, Neon3))) else Modifier),
                                contentAlignment = Alignment.Center) {
                                Text("$d", color = if (isToday) Color.White else if (col >= 5) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}
