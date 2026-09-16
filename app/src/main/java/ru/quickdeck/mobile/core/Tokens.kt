package ru.quickdeck.mobile.core

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Радиус размытия за панелью, dp. */
const val PANEL_BLUR_DP = 24

/**
 * Токены. Ни один цвет и ни один отступ не пишется мимо этого файла.
 *
 * Правила системы целиком — в DESIGN-SYSTEM.md. Коротко, потому что именно
 * эти три пункта нарушались чаще всего:
 *
 *  1. ОДИН ОЧАГ НА ЭКРАН. Ровно одна большая цифра. Остальное слабее — и по
 *     размеру, и по контрасту. Два «главных» элемента гасят друг друга:
 *     это видно на тепловой карте, а не в теории.
 *  2. ТРИ ЭЛЕМЕНТА В КАРТОЧКЕ. Метка, значение, подпись. Адрес, стадия,
 *     вид работ, срок — на слой глубже, а не всё сразу в одну плашку.
 *  3. ОДИН АКЦЕНТ. Оранжевый — единственное действие. Красный — только
 *     просрочка. Больше цветов в системе нет.
 *
 * Тёмные токены остались ровно для одного места — панели поверх чужих
 * экранов. Там светлая поверхность не читается ни на карте, ни на видео.
 */
object T {

    // --- светлая система: основа приложения -----------------------------

    /** Грунт. Холодный светло-серый, не белый: белая карточка должна отделяться. */
    val bg = Color(0xFFF4F5F7)

    /** Карточка. Чистый белый — единственная поверхность, на которой живут данные. */
    val card = Color(0xFFFFFFFF)

    /** Текст. 20 % текста на экране — этим цветом, не больше. */
    val ink = Color(0xFF0E1316)

    /** Приглушённый: подписи, единицы, значения второго плана. */
    val mut = Color(0x850E1316)      // 52 %

    /** Едва заметный: метки-рубрики, счётчики, всё, что не ищут глазами. */
    val faint = Color(0x570E1316)    // 34 %

    /** Разделитель внутри карточки. Появляется, когда воздуха уже не хватает. */
    val hairline = Color(0x120E1316) // 7 %

    // --- цвет: ровно два ------------------------------------------------

    /** Акцент. Единственное действие на экране, выбранная вкладка, «не получено». */
    val accent = Color(0xFFE2701A)

    /** Мягкая пара акцента — для размытого пятна на фоне и градиента. */
    val accentSoft = Color(0xFFF2A93B)

    /** Просрочка и удаление. Больше нигде. */
    val danger = Color(0xFFD8412F)

    /** Подложка тревожной карточки: белый с едва заметным розовым сверху. */
    val dangerWash = Color(0xFFFFF6F4)

    // --- панель поверх чужих экранов ------------------------------------

    val blurSupported = android.os.Build.VERSION.SDK_INT >= 31

    val panelScrim = if (blurSupported) Color(0xC7070C0E) else Color(0xF0070C0E)
    val panelCard = if (blurSupported) Color(0xD9121A1D) else Color(0xFF121A1D)
    val panelRaised = if (blurSupported) Color(0xF2192326) else Color(0xFF192326)
    val panelEdge = Color(0x33FFFFFF)
    val textOnDark = Color(0xFFE9EFF0)
    val text2OnDark = Color(0x9EE9EFF0)
    val hairlineDark = Color(0x26FFFFFF)

    // --- совместимость со старыми экранами ------------------------------
    // Формы и списки ещё зовут эти имена. Уберутся вместе с их переделкой.
    val surface = card
    val text = ink
    val text2 = mut
    val text3 = faint
    val bgDark = Color(0xFF070C0E)
    val surfaceDark = Color(0xFF121A1D)
    val raisedDark = Color(0xFF192326)
    val scrim = Color(0x8A0E1316)

    /**
     * Тон — три версии одного смысла: заливка, чернила на светлом, плашка.
     * Стадий много, цвет один: стадию называет слово, а не оттенок.
     */
    data class Tone(val fill: Color, val ink: Color, val chip: Color)

    val action = Tone(accent, Color(0xFFB2540D), Color(0x1FE2701A))
    val success = Tone(Color(0xFF2E9E74), Color(0xFF1F7355), Color(0x1F2E9E74))
    val warning = Tone(accent, Color(0xFFB2540D), Color(0x1FE2701A))
    val dangerTone = Tone(danger, Color(0xFFA32C1E), Color(0x1FD8412F))
    val muted = Tone(Color(0xFF8C979C), Color(0xFF5A6468), Color(0x140E1316))
    val info = Tone(Color(0xFF3F7EA6), Color(0xFF2C5C7C), Color(0x1F3F7EA6))
    val accentTone = action

    // --- сетка: воздух, а не плотность ----------------------------------
    // Числа выросли вдвое против прошлой версии намеренно. Плотный экран
    // читается как таблица, а таблицу человек и так может открыть в книге.

    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 40.dp

    /** Поле внутри карточки. Меньше 24 карточка перестаёт дышать. */
    val cardPad = 24.dp

    /** Между карточками. */
    val gap = 12.dp

    /** Воздух вокруг очага — он должен стоять один. */
    val heroGap = 40.dp

    /** Поля экрана. */
    val screenPad = 24.dp

    // --- радиусы: концентрические ---------------------------------------
    // Внешний = внутренний + отступ. Карточка 28 при поле 24 держит внутри 4;
    // вложенные плашки берут rInner.

    val rCard = 28.dp
    val rRow = 24.dp
    val rControl = 20.dp
    val rIcon = 14.dp
    val rInner = 8.dp
    val rSheet = 28.dp

    // --- касания ---------------------------------------------------------
    val touchMin = 48.dp
    val touchGap = 8.dp

    // --- движение ---------------------------------------------------------
    // Вход ступенями по 70 мс, смена слоя 340, пружина на переключателях.
    val curve: Easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
    const val MS_PRESS = 140
    const val MS_STATE = 220
    const val MS_SCREEN = 340
    const val MS_EXIT = 200
    const val MS_STEP = 70
    const val PRESS_SCALE = 0.985f
}
