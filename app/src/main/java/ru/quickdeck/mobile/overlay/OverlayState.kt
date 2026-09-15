package ru.quickdeck.mobile.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import ru.quickdeck.mobile.data.Section
import kotlin.math.roundToInt

/** Ссылка на запись — раздел плюс идентификатор, больше ничего не нужно. */
data class CardRef(val section: Section, val id: String)

/** Что сейчас на экране поверх всего. */
enum class PanelMode {
    /** Виден только пузырь. Окно панели не принимает касания вообще. */
    HIDDEN,

    /** Палец ведёт по колесу. Панель — чистая картинка, касания живут у пузыря. */
    WHEEL,

    /** Список раздела. Панель принимает касания. */
    BROWSE,

    /** Карточка записи. Панель принимает касания. */
    CARD
}

enum class WheelMode {
    /** Палец почти не отошёл от пузыря — отпустил, ничего не случилось. */
    CANCEL,

    /** Обычный выбор раздела. */
    BROWSE,

    /** Палец вытянут дальше — у раздела появился плюс. */
    CREATE
}

/**
 * То, что панель умеет попросить у службы. Сама себя панель не двигает.
 */
interface OverlayHost {
    /** Пропускать касания сквозь панель или ловить их. */
    fun panelTouchable(value: Boolean)

    /** Сдвинуть пузырь на столько пикселей. */
    fun moveBubble(dx: Float, dy: Float)

    /** Притянуть пузырь к ближайшему краю и запомнить место. */
    fun snapBubble()

    /** Форма — отдельная Activity, не оверлей. id == null значит «создать». */
    fun openForm(section: Section, id: String?)

    /** Поиск по реестру — тоже Activity, потому что там нужна клавиатура. */
    fun openSearch()

    fun buzz(ms: Long)

    /** Сходить в таблицу молча, не мешая человеку. */
    fun syncQuietly()
}

/**
 * Общее состояние двух окон: маленького с пузырём и большого с панелью.
 *
 * Оба окна рисуют свой Compose, но читают отсюда. Поэтому жест, который
 * целиком живёт в окне пузыря, двигает картинку в окне панели — и ни одно
 * окно во время жеста не пересоздаётся. Ровно из-за пересоздания окна
 * прошлая версия теряла палец на первом же движении.
 */
object OverlayState {

    var host: OverlayHost? = null

    // --- панель ---------------------------------------------------------

    var mode by mutableStateOf(PanelMode.HIDDEN)
        private set

    var section by mutableStateOf(Section.SITES)
        private set

    var card by mutableStateOf<CardRef?>(null)
        private set

    // --- колесо ---------------------------------------------------------

    var virtual by mutableFloatStateOf(0f)
        private set

    var wheelMode by mutableStateOf(WheelMode.CANCEL)
        private set

    var createArmed by mutableStateOf(false)

    var finger by mutableStateOf(Offset.Zero)
        private set

    /** Точка, где палец лёг на пузырь: от неё считается и выбор, и вытягивание. */
    var pivotY by mutableFloatStateOf(0f)
        private set

    var originX by mutableFloatStateOf(0f)
        private set

    /** Колесо раскрывается влево, если пузырь у правого края. */
    var fromRight by mutableStateOf(true)
        private set

    // --- пузырь ---------------------------------------------------------

    var bubbleLeft by mutableFloatStateOf(0f)
    var bubbleTop by mutableFloatStateOf(0f)
    var bubbleSize by mutableFloatStateOf(0f)

    /** Пузырь оторван и едет за пальцем. */
    var moving by mutableStateOf(false)

    /** Идёт обмен с таблицей — пузырь показывает это колечком. */
    var syncing by mutableStateOf(false)

    /** Сколько правок приехало последним обменом: цифра на пузыре. */
    var freshCount by mutableIntStateOf(0)

    // --- переходы -------------------------------------------------------

    fun beginWheel(origin: Offset, screenWidth: Float) {
        fromRight = origin.x > screenWidth / 2f
        originX = origin.x
        pivotY = origin.y
        finger = origin
        virtual = 0f
        wheelMode = WheelMode.CANCEL
        createArmed = false
        card = null
        mode = PanelMode.WHEEL
    }

    fun dragTo(point: Offset, virtualValue: Float, newMode: WheelMode) {
        finger = point
        virtual = virtualValue
        wheelMode = newMode
    }

    /** Палец отпущен. */
    fun releaseWheel() {
        val last = Section.entries.size - 1
        val index = virtual.roundToInt().coerceIn(0, last)
        val target = Section.entries[index]
        when (wheelMode) {
            WheelMode.CANCEL -> close()
            WheelMode.BROWSE -> openBrowse(target)
            WheelMode.CREATE -> {
                close()
                host?.openForm(target, null)
            }
        }
    }

    fun openBrowse(value: Section) {
        section = value
        card = null
        mode = PanelMode.BROWSE
        host?.panelTouchable(true)
    }

    fun openCard(ref: CardRef) {
        card = ref
        section = ref.section
        mode = PanelMode.CARD
        host?.panelTouchable(true)
    }

    fun backFromCard() {
        card = null
        mode = PanelMode.BROWSE
    }

    /** Тап по пузырю: сразу список того раздела, что открывали в прошлый раз. */
    fun openLast() = openBrowse(section)

    fun close() {
        mode = PanelMode.HIDDEN
        card = null
        virtual = 0f
        createArmed = false
        wheelMode = WheelMode.CANCEL
        moving = false
        host?.panelTouchable(false)
    }

    val isOpen: Boolean get() = mode != PanelMode.HIDDEN
}
