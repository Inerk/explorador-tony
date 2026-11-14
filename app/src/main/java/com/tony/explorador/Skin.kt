package com.tony.explorador

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

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
    /** Si el teléfono está en modo oscuro (lo actualiza el tema de la app). */
    var systemDark by androidx.compose.runtime.mutableStateOf(false)
    /** Modo oscuro activo: manual, o automático según el teléfono. */
    val dark: Boolean get() = LibraryStore.darkMode == "dark" || (LibraryStore.darkMode == "auto" && systemDark)
    val Window get() = if (dark) Color(0xFF1B1538) else Color(0xFFEEEAFB)
    /** Fondo del contenido (antes blanco). */
    val Paper get() = if (dark) Color(0xFF120E26) else Color.White
    /** Texto principal. */
    val Ink get() = if (dark) Color(0xFFF1ECFF) else Color.Black
    /** Texto secundario. */
    val InkSoft get() = if (dark) Color(0xFFB5ABD6) else Color(0xFF555555)
    /** Texto de encabezados. */
    val InkBlue get() = if (dark) Color(0xFFC9B6FF) else Color(0xFF0A246A)
    val SideTop = Color(0xFF8E6BF0)
    val SideBottom = Color(0xFF5B3FC8)
    val SidePanel get() = if (dark) Color(0xFF241C4A) else Color(0xFFE3DCFA)
    val SideHeader get() = if (dark) Color(0xFFC9B6FF) else Color(0xFF5B2FC0)
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
        listBackground = XpColors.Paper,
        listText = XpColors.Ink,
        listSubText = XpColors.InkSoft,
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
    LAVANDA("Lavanda (claro, íconos morados)"),
    NEBULA("Nebula (noche morada y rosa neón)"),
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
