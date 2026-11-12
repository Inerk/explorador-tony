package com.tony.explorador

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Temas de la app. Para agregar uno nuevo: añádelo aquí y en [skinFor]. */
enum class AppTheme(val label: String) {
    XP("Nebula"),
    MODERNO("Moderno"),
}

/** Colores y estilo que cambian con el tema. */
data class Skin(
    val titleBar: Brush?,          // degradado de la barra de título (null = color del sistema)
    val titleText: Color,
    val listBackground: Color?,     // fondo de la lista de archivos (null = color del sistema)
    val listText: Color?,
    val listSubText: Color?,
    val selection: Color?,
    val isRetro: Boolean,
)

object XpColors {
    val TitleTop = Color(0xFF6A3FD8)
    val TitleMid = Color(0xFF4A2BB0)
    val TitleBottom = Color(0xFF2A1670)
    val TitleHighlight = Color(0xFFFF7AD9)
    val TaskbarTop = Color(0xFF2B1C63)
    val TaskbarMid = Color(0xFF160F3A)
    val TaskbarBottom = Color(0xFF0B0820)
    val StartTop = Color(0xFFFF6FCF)
    val StartMid = Color(0xFFB04BE8)
    val StartBottom = Color(0xFF6A35D8)
    val TrayTop = Color(0xFF3A2A8C)
    val TrayBottom = Color(0xFF1B1250)
    val Window = Color(0xFFEEEAFB)
    val SideTop = Color(0xFF8E6BF0)
    val SideBottom = Color(0xFF5B3FC8)
    val SidePanel = Color(0xFFE3DCFA)
    val SideHeader = Color(0xFF5B2FC0)
    val SelectBlue = Color(0xFF7A4FE0)
    val SkyTop = Color(0xFF2E6FD8)
    val SkyBottom = Color(0xFF9CC9F5)
    val HillLight = Color(0xFF6DBE3F)
    val HillDark = Color(0xFF3E8E20)
    val StartMenuOrange = Color(0xFFFF7AD9)

    // barra de título: como una nebulosa, de violeta profundo a rosa y azul
    val titleBrush = Brush.horizontalGradient(
        listOf(Color(0xFF2A1670), Color(0xFF6A3FD8), Color(0xFFD64FC4), Color(0xFF4F7BFF), Color(0xFF2A1670))
    )
    val taskbarBrush = Brush.verticalGradient(
        0f to TaskbarTop, 0.15f to TaskbarMid, 0.85f to TaskbarMid, 1f to TaskbarBottom
    )
    val startBrush = Brush.verticalGradient(
        0f to Color(0xFFFFA8E4), 0.2f to StartTop, 0.7f to StartMid, 1f to StartBottom
    )
    val trayBrush = Brush.verticalGradient(listOf(TrayTop, TrayBottom))
}

fun skinFor(theme: AppTheme): Skin = when (theme) {
    AppTheme.XP -> Skin(
        titleBar = XpColors.titleBrush,
        titleText = Color.White,
        listBackground = Color.White,
        listText = Color.Black,
        listSubText = Color(0xFF6D6D6D),
        selection = XpColors.SelectBlue.copy(alpha = 0.25f),
        isRetro = true,
    )
    AppTheme.MODERNO -> Skin(
        titleBar = null,
        titleText = Color.Unspecified,
        listBackground = null,
        listText = null,
        listSubText = null,
        selection = null,
        isRetro = false,
    )
}

/** Temas de la sección de música (solo cambian colores e íconos). */
enum class MusicSkin(val label: String) {
    ORIGINAL("Original (como el reproductor, con los colores de la portada)"),
    WALKMAN("Walkman (negro y naranja)"),
    MEDIA_PLAYER("Media Player (azul cristal)"),
    VINTAGE("Vintage (tocadiscos y madera)"),
}

/** Aspecto de la ventanita del reproductor en el escritorio. */
enum class WindowSkin(val label: String) {
    MEDIA("Media (azul cristal)"),
    AMP("Clásico (amplificador gris con pantalla verde)"),
    VINTAGE("Vintage (tocadiscos de madera)"),
}

/** Colores del tema Vintage. */
object Vintage {
    val WalnutDark = androidx.compose.ui.graphics.Color(0xFF1C120B)
    val Walnut = androidx.compose.ui.graphics.Color(0xFF3A2416)
    val Teak = androidx.compose.ui.graphics.Color(0xFF8B5A2B)
    val TeakDark = androidx.compose.ui.graphics.Color(0xFF5A3517)
    val Brass = androidx.compose.ui.graphics.Color(0xFFC9A24A)
    val BrassLight = androidx.compose.ui.graphics.Color(0xFFF0D98F)
    val BrassDark = androidx.compose.ui.graphics.Color(0xFF7A5A1C)
    val Cream = androidx.compose.ui.graphics.Color(0xFFF3E7D0)
    val CreamDim = androidx.compose.ui.graphics.Color(0xFFB8A587)
    val Bakelite = androidx.compose.ui.graphics.Color(0xFF3B2414)
    val Burgundy = androidx.compose.ui.graphics.Color(0xFF8E2C2C)
}
