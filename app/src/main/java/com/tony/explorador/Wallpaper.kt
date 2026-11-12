package com.tony.explorador

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fondos de escritorio disponibles. "custom" = una foto del usuario. */
data class WallpaperOption(val id: String, val name: String)

val Wallpapers = listOf(
    WallpaperOption("nieve", "Muñeco de nieve"),
    WallpaperOption("nebula", "Nebulosa"),
    WallpaperOption("xp", "Colinas"),
    WallpaperOption("wood", "Madera"),
    WallpaperOption("azul", "Azul clásico"),
    WallpaperOption("noche", "Noche"),
    WallpaperOption("atardecer", "Atardecer"),
    WallpaperOption("bosque", "Bosque"),
)

@Composable
fun WallpaperView(modifier: Modifier, id: String = LibraryStore.wallpaper) {
    val context = LocalContext.current
    when (id) {
        "custom" -> if (LibraryStore.wallpaperFile.isFile) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(LibraryStore.wallpaperFile)
                    .memoryCacheKey("wallpaper-${LibraryStore.wallpaperVersion}")
                    .diskCachePolicy(CachePolicy.DISABLED).build(),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier
            )
        } else HillsWallpaper(modifier)
        "nieve" -> Image(painterResource(R.drawable.wp_nieve), null, contentScale = ContentScale.Crop, modifier = modifier)
        "nebula" -> NebulaWallpaper(modifier)
        "wood" -> Image(painterResource(R.drawable.vt_table), null, contentScale = ContentScale.Crop, modifier = modifier)
        "azul" -> Box(modifier.background(Color(0xFF3A6EA5)))
        "noche" -> Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF0B1A3A), Color(0xFF26305E), Color(0xFF3B1E5E)))))
        "atardecer" -> Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF6A3BA8), Color(0xFFFF5E7E), Color(0xFFFF9A5A), Color(0xFFFFD27A)))))
        "bosque" -> Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF0F3D22), Color(0xFF1E5631), Color(0xFF7CB342)))))
        else -> HillsWallpaper(modifier)
    }
}

/** Fondo original: cielo azul, nubes suaves y una colina verde. */
@Composable
fun HillsWallpaper(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(XpColors.SkyTop, XpColors.SkyBottom), endY = h * 0.75f))
        fun cloud(cx: Float, cy: Float, r: Float, a: Float) {
            val c = Color.White.copy(alpha = a)
            drawOval(c, Offset(cx - r * 1.6f, cy - r * 0.45f), Size(r * 3.2f, r * 0.9f))
            drawOval(c, Offset(cx - r * 0.9f, cy - r * 0.8f), Size(r * 1.6f, r * 1.0f))
            drawOval(c, Offset(cx - r * 0.1f, cy - r * 0.65f), Size(r * 1.4f, r * 0.85f))
        }
        cloud(w * 0.25f, h * 0.18f, w * 0.12f, 0.55f)
        cloud(w * 0.78f, h * 0.10f, w * 0.10f, 0.45f)
        cloud(w * 0.62f, h * 0.30f, w * 0.08f, 0.35f)
        val back = Path().apply {
            moveTo(0f, h * 0.68f); cubicTo(w * 0.35f, h * 0.56f, w * 0.7f, h * 0.62f, w, h * 0.58f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(back, Brush.verticalGradient(listOf(Color(0xFF8ACB52), XpColors.HillDark), startY = h * 0.56f, endY = h))
        val front = Path().apply {
            moveTo(0f, h * 0.80f); cubicTo(w * 0.25f, h * 0.62f, w * 0.65f, h * 0.66f, w, h * 0.78f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(front, Brush.verticalGradient(listOf(XpColors.HillLight, XpColors.HillDark), startY = h * 0.64f, endY = h))
    }
}

@Composable
fun WallpaperDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        LibraryStore.wallpaperFile.outputStream().use { input.copyTo(it) }
                    }
                    true
                }.getOrDefault(false)
            }
            if (ok) { LibraryStore.changeWallpaper("custom"); onDismiss() }
            else Toast.makeText(context, "No se pudo usar esa imagen", Toast.LENGTH_SHORT).show()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fondo de pantalla") },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 380.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(Wallpapers) { w ->
                        val selected = LibraryStore.wallpaper == w.id
                        Column(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { LibraryStore.changeWallpaper(w.id); onDismiss() },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.fillMaxWidth().aspectRatio(0.56f).clip(RoundedCornerShape(8.dp))
                                    .border(3.dp, if (selected) XpColors.SelectBlue else Color(0x33000000), RoundedCornerShape(8.dp))
                            ) { WallpaperView(Modifier.fillMaxSize(), w.id) }
                            Text(w.name, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(XpColors.SidePanel)
                        .clickable { picker.launch("image/*") }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, null, tint = XpColors.SideHeader)
                    Spacer(Modifier.width(8.dp))
                    Text("Usar una foto mía…", color = XpColors.SideHeader, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/** Fondo de nebulosa dibujado: espacio profundo, nubes de colores y estrellas. */
@Composable
fun NebulaWallpaper(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0820), Color(0xFF1A1145), Color(0xFF0B0820))))
        fun cloud(x: Float, y: Float, r: Float, c: Color) {
            val o = androidx.compose.ui.geometry.Offset(w * x, h * y)
            drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = o, radius = w * r), w * r, o)
        }
        cloud(0.25f, 0.28f, 0.75f, Color(0x99D64FC4))
        cloud(0.8f, 0.42f, 0.7f, Color(0x884F7BFF))
        cloud(0.45f, 0.7f, 0.8f, Color(0x777A3FE0))
        cloud(0.7f, 0.2f, 0.4f, Color(0x6646D6E8))
        cloud(0.2f, 0.82f, 0.45f, Color(0x55FF8A5A))
        cloud(0.5f, 0.45f, 0.22f, Color(0x99FFFFFF))
        // estrellas (siempre en el mismo lugar)
        val rnd = java.util.Random(7)
        repeat(170) {
            val p = androidx.compose.ui.geometry.Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
            val big = rnd.nextFloat() > 0.93f
            drawCircle(Color.White.copy(alpha = 0.35f + rnd.nextFloat() * 0.65f), if (big) w * 0.005f else w * 0.0022f, p)
            if (big) drawCircle(Color.White.copy(alpha = 0.15f), w * 0.012f, p)
        }
    }
}
