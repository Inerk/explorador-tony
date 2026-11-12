package com.tony.explorador

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Un botón del cuadro de diálogo. */
data class DialogButton(val label: String, val primary: Boolean = false, val onClick: () -> Unit)

/** Quién hizo la app. */
const val APP_CREDITS = "Explorador Tony · creado por Tony"

/** Quita de las notas de versión las líneas internas que no le dicen nada al usuario. */
fun cleanReleaseNotes(text: String): String =
    text.lines()
        .filterNot { l ->
            val t = l.trim()
            t.startsWith("Co-Authored-By", ignoreCase = true) || t.startsWith("Claude-Session", ignoreCase = true) ||
                t.startsWith("https://claude.ai/") || t.startsWith("Signed-off-by", ignoreCase = true)
        }
        .joinToString("\n").trim()

/**
 * Cuadro de diálogo que se viste con el tema elegido:
 * en XP es una ventana azul con botones grises; en Moderno, el diálogo normal de Android.
 */
@Composable
fun ThemedDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: List<DialogButton>,
    credits: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (LibraryStore.theme != AppTheme.XP) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    content()
                    if (credits) CreditsLine(Color.Unspecified)
                }
            },
            confirmButton = {
                Row {
                    buttons.forEach { b -> TextButton(onClick = b.onClick) { Text(b.label, fontWeight = if (b.primary) FontWeight.Bold else null) } }
                }
            }
        )
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
        Column(
            Modifier.widthIn(max = 380.dp).fillMaxWidth().shadow(16.dp, shape).clip(shape)
                .background(XpColors.TitleBottom).padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(30.dp).background(XpColors.titleBrush).padding(start = 8.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                XpIcon(XpIconKind.DRIVE, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                XpCaptionButton("✕", XpRedButton, onDismiss)
            }
            Column(Modifier.fillMaxWidth().background(XpColors.Window).padding(14.dp)) {
                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    content()
                }
                if (credits) CreditsLine(Color(0xFF555555))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    buttons.forEach { b ->
                        Spacer(Modifier.width(8.dp))
                        XpPushButton(b.label, b.primary, b.onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditsLine(color: Color) {
    Spacer(Modifier.height(14.dp))
    Text("★ $APP_CREDITS", fontSize = 12.sp, fontStyle = FontStyle.Italic,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

/** Botón gris redondeado de Windows XP (el principal con orilla azul). */
@Composable
fun XpPushButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFECEBE6), Color(0xFFD6D0C5))))
            .border(if (primary) 2.dp else 1.dp, if (primary) Color(0xFF6D8FD9) else Color(0xFF003C74), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.Black, fontSize = 14.sp, fontWeight = if (primary) FontWeight.Bold else null)
    }
}
