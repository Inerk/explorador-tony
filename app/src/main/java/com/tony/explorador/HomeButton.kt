package com.tony.explorador

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Botón redondo que regresa al Inicio desde cualquier pantalla. */
@Composable
fun HomeButton(onClick: () -> Unit, modifier: Modifier = Modifier, dark: Boolean = true) {
    val retro = LibraryStore.theme == AppTheme.XP
    Box(
        modifier.size(38.dp)
            .clip(CircleShape)
            .background(
                when {
                    retro -> XpColors.startBrush
                    dark -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.12f)))
                    else -> Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.06f)))
                }
            )
            .border(1.dp, Color.White.copy(alpha = if (retro) 0.8f else 0.25f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Home, "Inicio", tint = if (retro || dark) Color.White else Color.Black, modifier = Modifier.size(22.dp))
    }
}
