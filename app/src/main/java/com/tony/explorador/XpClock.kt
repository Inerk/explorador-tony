package com.tony.explorador

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val esMX = Locale("es", "MX")

/** Hora actual que se actualiza sola cada [stepMs]. */
@Composable
fun rememberNow(stepMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stepMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(stepMs - (now % stepMs).coerceAtMost(stepMs - 1))
        }
    }
    return now
}

/** Reloj de la bandeja: hora con los dos puntos parpadeando y la fecha abajo. */
@Composable
fun TrayClock(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow(500)
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val showColon = cal.get(Calendar.MILLISECOND) < 500
    val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val m = cal.get(Calendar.MINUTE)
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "a. m." else "p. m."
    Column(
        modifier.clickable(onClick = onClick).padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$h", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(":", color = if (showColon) Color.White else Color.Transparent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(String.format(Locale.US, "%02d", m), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(" $ampm", color = Color.White, fontSize = 10.sp)
        }
        Text(SimpleDateFormat("EEE d MMM", esMX).format(Date(now)), color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
    }
}

/** Ventana "Fecha y hora" con reloj de manecillas y calendario del mes. */
@Composable
fun XpClockWindow(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow(50)
    val today = Calendar.getInstance().apply { timeInMillis = now }
    var monthShift by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp)

    Column(
        modifier.width(300.dp).shadow(14.dp, shape).clip(shape)
            .background(XpColors.TitleBottom).padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
            .clickable(enabled = false) {}
    ) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(XpColors.titleBrush).padding(start = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Schedule, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("Fecha y hora", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFE88C6F), Color(0xFFC4401E))))
                    .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        Column(Modifier.fillMaxWidth().background(XpColors.Window).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnalogClock(now, Modifier.size(150.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                SimpleDateFormat("h:mm:ss a", esMX).format(Date(now)),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF0A246A)
            )
            Text(
                SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", esMX).format(Date(now)).replaceFirstChar { it.uppercase() },
                fontSize = 12.sp, color = Color(0xFF333333)
            )
            Spacer(Modifier.height(10.dp))
            MonthCalendar(today, monthShift, onShift = { monthShift += it })
        }
    }
}

@Composable
private fun AnalogClock(now: Long, modifier: Modifier) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val sec = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
    val min = cal.get(Calendar.MINUTE) + sec / 60f
    val hour = (cal.get(Calendar.HOUR) + min / 60f)
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        // carátula
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFDCE6F7)), center = c, radius = r), r, c)
        drawCircle(XpColors.SelectBlue, r, c, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, r - 3.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        // marcas
        for (i in 0 until 60) {
            rotate(i * 6f, c) {
                val long = i % 5 == 0
                drawLine(
                    if (long) Color(0xFF0A246A) else Color(0xFF7F9DB9),
                    Offset(c.x, c.y - r + 6.dp.toPx()),
                    Offset(c.x, c.y - r + (if (long) 16 else 10).dp.toPx()),
                    strokeWidth = (if (long) 3 else 1).dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        // manecillas
        rotate(hour * 30f, c) {
            drawLine(Color(0xFF0A246A), c, Offset(c.x, c.y - r * 0.5f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
        rotate(min * 6f, c) {
            drawLine(Color(0xFF215DC6), c, Offset(c.x, c.y - r * 0.72f), strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round)
        }
        rotate(sec * 6f, c) {
            drawLine(Color(0xFFD9381E), Offset(c.x, c.y + r * 0.15f), Offset(c.x, c.y - r * 0.8f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(Color(0xFFD9381E), 4.dp.toPx(), c)
        drawCircle(Color.White, 1.5.dp.toPx(), c)
    }
}

@Composable
private fun MonthCalendar(today: Calendar, shift: Int, onShift: (Int) -> Unit) {
    val month = (today.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, shift)
    }
    val title = SimpleDateFormat("MMMM yyyy", esMX).format(month.time).replaceFirstChar { it.uppercase() }
    // lunes primero
    val firstWeekday = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val days = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val isThisMonth = shift == 0
    val todayNum = today.get(Calendar.DAY_OF_MONTH)

    Column(
        Modifier.fillMaxWidth().background(Color.White).border(1.dp, Color(0xFF7F9DB9)).padding(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", fontSize = 22.sp, color = XpColors.SideHeader, modifier = Modifier.clickable { onShift(-1) }.padding(horizontal = 10.dp))
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF0A246A), textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                .clickable { if (shift != 0) onShift(-shift) })
            Text("›", fontSize = 22.sp, color = XpColors.SideHeader, modifier = Modifier.clickable { onShift(1) }.padding(horizontal = 10.dp))
        }
        Row(Modifier.fillMaxWidth().background(XpColors.SidePanel)) {
            listOf("L", "M", "M", "J", "V", "S", "D").forEach {
                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0A246A),
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 3.dp))
            }
        }
        val cells = firstWeekday + days
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = r * 7 + col - firstWeekday + 1
                    val isToday = isThisMonth && day == todayNum
                    Box(
                        Modifier.weight(1f).aspectRatio(1.4f).padding(1.dp)
                            .then(if (isToday) Modifier.background(XpColors.SelectBlue, RoundedCornerShape(3.dp)) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day in 1..days) {
                            Text(
                                "$day", fontSize = 12.sp,
                                color = when {
                                    isToday -> Color.White
                                    col == 6 -> Color(0xFFC4401E)
                                    else -> Color.Black
                                },
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
