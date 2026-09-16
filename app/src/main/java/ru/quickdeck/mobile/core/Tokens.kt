package ru.quickdeck.mobile.core

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Радиус размытия за панелью, dp. */
const val PANEL_BLUR_DP = 24

/**
 * Токены. Ни один цвет не пишется мимо этого файла.
 *
 * Система собрана по швейцарской школе: сетка, контраст, воздух там, где он
 * работает, и плотность там, где нужно читать много. Никаких свечений,
 * дуг и стеклянных переливов — они хорошо выглядят на картинке и мешают
 * работать. Цвет здесь несёт смысл, а не настроение: нейтраль для данных,
 * зелёный для «идёт как надо», красный для «сорвано», янтарь для «ждёт».
 *
 * Палитра и плотность взяты из design-system по профилю «enterprise
 * operations console, dense, professional» — это ровно наш случай:
 * руководитель смотрит в экран, чтобы принять решение, а не любоваться.
 */
object T {

    // --- нейтраль: тёмная база, потому что панель живёт поверх чужих экранов
    val bg = Color(0xFFF6F7F9)
    val bgDark = Color(0xFF020617)
    val surface = Color(0xFFFFFFFF)
    val surfaceDark = Color(0xFF0E1223)
    val raisedDark = Color(0xFF161B2E)

    val text = Color(0xFF0F172A)
    val text2 = Color(0xFF475569)
    val text3 = Color(0xFF8A93A1)

    val textOnDark = Color(0xFFF8FAFC)
    val text2OnDark = Color(0xFF94A3B8)

    val hairline = Color(0x1A0F172A)
    val hairlineDark = Color(0xFF334155)
    val scrim = Color(0x990A0C10)

    // --- панель поверх чужих приложений ---------------------------------
    /**
     * Панель тёмная не из вкуса, а из условий: она ложится на светлую
     * галерею, на тёмную карту, на видео. Светлая поверхность там читается
     * через раз, тёмная — всегда. Размытие (Android 12+) добавляет глубины,
     * но панель обязана быть читаемой и без него.
     */
    val blurSupported = android.os.Build.VERSION.SDK_INT >= 31

    val panelScrim = if (blurSupported) Color(0xC7020617) else Color(0xF0020617)
    val panelCard = if (blurSupported) Color(0xD90E1223) else Color(0xFF0E1223)
    val panelRaised = if (blurSupported) Color(0xF2161B2E) else Color(0xFF161B2E)
    val panelEdge = Color(0x66334155)

    // --- три версии каждого цвета: заливка / текст / плашка -------------
    data class Tone(val fill: Color, val ink: Color, val chip: Color)

    /**
     * Цвет статуса — сигнал, а не украшение. Рядом с ним всегда стоит текст
     * или иконка: по одному цвету状態 не читают ни дальтоники, ни человек
     * на солнце.
     */
    val accent = Tone(Color(0xFF22C55E), Color(0xFF15803D), Color(0xFFE7F8ED))
    val success = Tone(Color(0xFF22C55E), Color(0xFF15803D), Color(0xFFE7F8ED))
    val warning = Tone(Color(0xFFF59E0B), Color(0xFF92600A), Color(0xFFFEF3E2))
    val danger = Tone(Color(0xFFEF4444), Color(0xFFB91C1C), Color(0xFFFDECEC))
    val info = Tone(Color(0xFF38BDF8), Color(0xFF0369A1), Color(0xFFE6F6FE))
    val muted = Tone(Color(0xFF94A3B8), Color(0xFF475569), Color(0xFFF1F5F9))
    val action = accent

    // --- сетка: плотная, 8–32 --------------------------------------------
    // Дашборд читают глазами, а не пролистывают: лишний воздух здесь
    // означает меньше строк на экране и больше прокрутки.
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    // --- радиусы: сдержанные, без «мягкой игрушки» -----------------------
    val rSheet = 20.dp
    val rCard = 12.dp
    val rControl = 10.dp
    val rIcon = 8.dp

    // --- касания ---------------------------------------------------------
    val touchMin = 48.dp
    val touchGap = 8.dp

    // --- движение: тихое -------------------------------------------------
    // Переходы объясняют, что изменилось, и уходят с дороги. Ничего не
    // пульсирует само по себе: мигающий интерфейс воспринимается как сбой.
    val curve: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val MS_PRESS = 120
    const val MS_STATE = 200
    const val MS_SCREEN = 280
    const val MS_EXIT = 160
    const val PRESS_SCALE = 0.97f
}
