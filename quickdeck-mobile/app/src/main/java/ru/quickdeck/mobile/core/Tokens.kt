package ru.quickdeck.mobile.core

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Раздел 12 DESIGN-SYSTEM.md — токены как есть, без подбора похожих значений.
 * Ни один цвет не пишется мимо этого файла.
 */
object T {

    // --- нейтраль -------------------------------------------------------
    val bg = Color(0xFFF6F7F9)
    val bgDark = Color(0xFF0F1115)
    val surface = Color(0xFFFFFFFF)
    val surfaceDark = Color(0xFF181B21)
    val raisedDark = Color(0xFF1F232B)

    val text = Color(0xFF121418)
    val text2 = Color(0xFF5B6270)
    val text3 = Color(0xFF8A93A1)

    val textOnDark = Color(0xFFF2F4F7)
    val text2OnDark = Color(0xFF9AA4B2)

    val hairline = Color(0x1A121418)          // #121418 при 10 %
    val hairlineDark = Color(0x1FFFFFFF)      // #FFFFFF при 12 %
    val scrim = Color(0x990A0C10)             // #0A0C10 при 60 %

    // --- три версии каждого цвета: заливка / текст / плашка -------------
    data class Tone(val fill: Color, val ink: Color, val chip: Color)

    val accent = Tone(Color(0xFF5B8CFF), Color(0xFF2F5FD6), Color(0xFFE8EFFF))
    val success = Tone(Color(0xFF4ADE80), Color(0xFF127A4D), Color(0xFFE6FAED))
    val warning = Tone(Color(0xFFF0A04B), Color(0xFF9A5A08), Color(0xFFFDF2E6))
    val danger = Tone(Color(0xFFFF6B7A), Color(0xFFC22B41), Color(0xFFFFEAEC))
    val info = Tone(Color(0xFFA78BFA), Color(0xFF6D4AFF), Color(0xFFF3EFFE))
    val muted = Tone(Color(0xFF9AA4B2), Color(0xFF5B6270), Color(0xFFF1F2F4))

    // --- сетка: шаг 4 ---------------------------------------------------
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    // --- радиусы --------------------------------------------------------
    val rSheet = 24.dp
    val rCard = 16.dp
    val rControl = 12.dp
    val rIcon = 10.dp

    // --- касания --------------------------------------------------------
    val touchMin = 48.dp
    val touchGap = 8.dp

    // --- движение: одна кривая на всё -----------------------------------
    val curve: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val MS_PRESS = 140
    const val MS_STATE = 220
    const val MS_SCREEN = 300
    const val MS_EXIT = 180
    const val PRESS_SCALE = 0.96f
}
