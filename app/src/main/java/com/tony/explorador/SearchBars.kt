package com.tony.explorador

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

/** A qué etiqueta de formato pertenece un archivo: "Carpetas", "JPG", "MP4"… */
fun formatKey(f: File): String =
    if (f.isDirectory) "Carpetas" else f.extension.uppercase(Locale.ROOT).ifBlank { "Sin ext." }.let { if (it == "JPEG") "JPG" else it }

/** Formatos encontrados, del que más hay al que menos. */
fun formatGroups(files: List<File>): List<Pair<String, Int>> =
    files.groupingBy { formatKey(it) }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

private fun colorFor(key: String): Color {
    if (key == "Carpetas") return Color(0xFFFFC83D)
    return when (File("x.${key.lowercase(Locale.ROOT)}").kind()) {
        FileKind.IMAGE -> Color(0xFF4FC3F7)
        FileKind.VIDEO -> Color(0xFFE57373)
        FileKind.AUDIO -> Color(0xFFBA68C8)
        else -> if (key == "PDF") Color(0xFFFF7043) else Color(0xFF90A4AE)
    }
}

/** Barra verde que se va llenando mientras busca, como la de la barra de direcciones de Windows. */
@Composable
fun SearchStatusBar(searching: Boolean, progress: Float, where: String, found: Int) {
    val p by animateFloatAsState(progress, tween(250), label = "searchP")
    val shine by rememberInfiniteTransition(label = "shine").animateFloat(-0.3f, 1.3f,
        infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shineX")
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            val barAlpha by animateFloatAsState(if (searching) 1f else 0.35f, tween(700), label = "barA")
            Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0.02f, 1f)).alpha(barAlpha)
                .background(Brush.horizontalGradient(listOf(Color(0xFF2E9E3E), Color(0xFF5BD46A)))))
            if (searching) Box(Modifier.fillMaxHeight().fillMaxWidth(p.coerceIn(0.02f, 1f))) {
                // brillo que corre sobre la parte verde
                Box(Modifier.fillMaxHeight().fillMaxWidth().background(Brush.horizontalGradient(
                    0f to Color.Transparent, (shine - 0.1f).coerceIn(0f, 1f) to Color.Transparent,
                    shine.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.35f),
                    (shine + 0.1f).coerceIn(0f, 1f) to Color.Transparent, 1f to Color.Transparent)))
            }
            Text(
                if (searching) "Buscando${if (where.isNotBlank()) " en $where" else ""}… · $found encontrados"
                else "Búsqueda terminada · $found resultados",
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp)
            )
        }
    }
}

/** Etiquetas para filtrar los resultados por formato (se pueden prender varias). */
@Composable
fun FormatChips(groups: List<Pair<String, Int>>, total: Int, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    if (groups.size < 2 && selected.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        item { Chip("Todos", total, Color(0xFF8E9CFF), selected.isEmpty()) { onChange(emptySet()) } }
        items(groups, key = { it.first }) { (k, n) ->
            Chip(k, n, colorFor(k), k in selected) { onChange(if (k in selected) selected - k else selected + k) }
        }
    }
}

@Composable
private fun Chip(label: String, count: Int, color: Color, on: Boolean, onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(CircleShape)
            .background(if (on) color.copy(alpha = 0.28f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (on) color else Color.Transparent, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = onSurface)
        Spacer(Modifier.width(5.dp))
        Text("$count", fontSize = 12.sp, color = onSurface.copy(alpha = 0.6f))
    }
}
