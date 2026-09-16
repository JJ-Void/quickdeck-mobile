package ru.quickdeck.mobile.core

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Радиус размытия за панелью, dp. */
const val PANEL_BLUR_DP = 34

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

    // Имена остались с тех пор, когда панель была тёмной. Панель стала
    // светлой, а разметка опирается на эти токены — переименовывать её
    // ради названий незачем, значения важнее имён.
    val textOnDark = Color(0xFF10131A)
    val text2OnDark = Color(0xFF6B7482)

    val hairline = Color(0x1A121418)          // #121418 при 10 %
    val hairlineDark = Color(0x14121418)      // тонкая линия на светлом стекле
    val scrim = Color(0x990A0C10)             // #0A0C10 при 60 %

    // --- панель поверх чужих приложений ---------------------------------
    /**
     * Панель — светящееся стекло, а не тёмная плита.
     *
     * Размытие (FLAG_BLUR_BEHIND, Android 12+) даёт под ней настоящий
     * матовый свет: за поверхностями видно размытый экран, и панель
     * выглядит включённой. Где размытия нет, та же прозрачность смотрелась
     * бы грязью, поэтому поверхности становятся плотными.
     */
    val blurSupported = android.os.Build.VERSION.SDK_INT >= 31

    val panelScrim = if (blurSupported) Color(0xBCEFF1F4) else Color(0xF4F3F5F8)
    val panelCard = if (blurSupported) Color(0x8FFFFFFF) else Color(0xF7FFFFFF)
    val panelRaised = if (blurSupported) Color(0xF2FFFFFF) else Color(0xFFFFFFFF)
    val panelEdge = Color(0x1A121418)

    /**
     * Свет — главный материал интерфейса. Тонкая линия, ореол вокруг
     * активного элемента, тёплый янтарь на действии. Холодный луч —
     * для состояния связи и живых данных.
     */
    val glow = Color(0xFFFF7A1A)
    val glowSoft = Color(0x2EFF7A1A)
    val beam = Color(0xFF3AA0FF)
    val beamSoft = Color(0x243AA0FF)

    // --- три версии каждого цвета: заливка / текст / плашка -------------
    data class Tone(val fill: Color, val ink: Color, val chip: Color)

    val accent = Tone(Color(0xFF5B8CFF), Color(0xFF2F5FD6), Color(0xFFE8EFFF))
    val success = Tone(Color(0xFF4ADE80), Color(0xFF127A4D), Color(0xFFE6FAED))
    val warning = Tone(Color(0xFFF0A04B), Color(0xFF9A5A08), Color(0xFFFDF2E6))
    val danger = Tone(Color(0xFFFF6B7A), Color(0xFFC22B41), Color(0xFFFFEAEC))
    val info = Tone(Color(0xFFA78BFA), Color(0xFF6D4AFF), Color(0xFFF3EFFE))
    val muted = Tone(Color(0xFF9AA4B2), Color(0xFF5B6270), Color(0xFFF1F2F4))

    /** Действие — янтарь: одно горячее пятно на весь спокойный экран. */
    val action = Tone(glow, Color(0xFFB4530A), Color(0xFFFFF0E4))

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
