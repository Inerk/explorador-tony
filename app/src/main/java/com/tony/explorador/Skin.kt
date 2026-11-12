package com.tony.explorador

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Temas de la app. Para agregar uno nuevo: añádelo aquí y en [skinFor]. */
enum class AppTheme(val label: String) {
    XP("Clásico XP"),
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
    val TitleTop = Color(0xFF0A5FE0)
    val TitleMid = Color(0xFF0052D6)
    val TitleBottom = Color(0xFF0042B8)
    val TitleHighlight = Color(0xFF3D8BFF)
    val TaskbarTop = Color(0xFF3F8CF3)
    val TaskbarMid = Color(0xFF245DDB)
    val TaskbarBottom = Color(0xFF1941A5)
    val StartTop = Color(0xFF4CB04C)
    val StartMid = Color(0xFF3C9A3C)
    val StartBottom = Color(0xFF2E7D2E)
    val TrayTop = Color(0xFF16A4F0)
    val TrayBottom = Color(0xFF0E7BD6)
    val Window = Color(0xFFECE9D8)
    val SideTop = Color(0xFF7BA2E7)
    val SideBottom = Color(0xFF6375D6)
    val SidePanel = Color(0xFFD6DFF7)
    val SideHeader = Color(0xFF215DC6)
    val SelectBlue = Color(0xFF316AC5)
    val SkyTop = Color(0xFF2E6FD8)
    val SkyBottom = Color(0xFF9CC9F5)
    val HillLight = Color(0xFF6DBE3F)
    val HillDark = Color(0xFF3E8E20)
    val StartMenuOrange = Color(0xFFE8A33D)

    val titleBrush = Brush.verticalGradient(
        0f to TitleHighlight, 0.12f to TitleTop, 0.5f to TitleMid, 1f to TitleBottom
    )
    val taskbarBrush = Brush.verticalGradient(
        0f to TaskbarTop, 0.15f to TaskbarMid, 0.85f to TaskbarMid, 1f to TaskbarBottom
    )
    val startBrush = Brush.verticalGradient(
        0f to Color(0xFF6FD06F), 0.2f to StartTop, 0.7f to StartMid, 1f to StartBottom
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
    WALKMAN("Walkman (negro y naranja)"),
    MEDIA_PLAYER("Media Player (azul cristal)"),
}
