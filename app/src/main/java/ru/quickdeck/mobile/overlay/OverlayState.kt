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

/** То, что панель умеет попросить у службы. Сама себя панель не двигает. */
interface OverlayHost {
    /** Пропускать касания сквозь панель или ловить их. */
    fun panelTouchable(value: Boolean)

    /** Размыть то, что за панелью. Включается, только пока панель видна. */
    fun panelBlur(on: Boolean)

    /**
     * Убрать окно панели с экрана, когда панель закрыта.
     *
     * Невидимое полноэкранное окно всё равно остаётся наложением, а из-за
     * наложения Android запрещает нажимать на защищённые экраны: подтверждение
     * входа в Google, смена аккаунта, системные разрешения. Поэтому закрытая
     * панель не просто прозрачная, а снятая.
     */
    fun panelVisible(value: Boolean)

    /** Притянуть пузырь к ближайшему краю и запомнить место. */
    fun snapBubble()

    /** Форма — отдельная Activity, не оверлей. id == null значит «создать». */
    fun openForm(section: Section, id: String?)

    /** Поиск по реестру — тоже Activity, потому что там нужна клавиатура. */
    fun openSearch()

    /** Постановка задачи сотруднику — Activity, там ввод и отправка. */
    fun openTask(employeeId: String)

    /** Настройки — тоже Activity: без них из панели не выйти к обмену и бэкапу. */
    fun openSettings()

    /** Задачи по договору — Activity: их набирают с клавиатуры. */
    fun openTasks(contractId: String)

    fun buzz(ms: Long)

    /** Сходить в таблицу молча, не мешая человеку. */
    fun syncQuietly()
}

/**
 * Общее состояние двух окон: маленького с пузырём и большого с панелью.
 *
 * Оба окна рисуют свой Compose, но читают отсюда. Поэтому жест, который
 * целиком живёт в окне пузыря, двигает картинку в окне панели — и ни одно
 * окно во время жеста не пересоздаётся.
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

    /**
     * Выбранный фильтр внутри раздела: стадия для договоров, отдел для
     * сотрудников, заказчик для объектов. Пусто — показываем всё.
     *
     * Это именно фильтр, а не уровень навигации: он снимается тем же тапом,
     * которым поставлен, и не заставляет возвращаться назад ради соседней
     * стадии.
     */
    var group by mutableStateOf<String?>(null)
        private set

    /** Откуда пришли: объект и договор последней открытой карточки. */
    var contextSiteId: String? = null
        private set

    var contextContractId: String? = null
        private set

    // --- колесо ---------------------------------------------------------

    var virtual by mutableFloatStateOf(0f)
        private set

    var wheelMode by mutableStateOf(WheelMode.CANCEL)
        private set

    var createArmed by mutableStateOf(false)

    /** Строка, в которой стоит выбранный пункт. Считается при старте жеста. */
    var centerY by mutableFloatStateOf(0f)
        private set

    var originX by mutableFloatStateOf(0f)
        private set

    /** Колесо раскрывается влево, если пузырь у правого края. */
    var fromRight by mutableStateOf(true)
        private set

    // --- пузырь ---------------------------------------------------------

    var bubbleLeft by mutableFloatStateOf(0f)
    var bubbleTop by mutableFloatStateOf(0f)

    /** Пузырь оторван и едет за пальцем. */
    var moving by mutableStateOf(false)

    /** Идёт обмен с таблицей — пузырь показывает это колечком. */
    var syncing by mutableStateOf(false)

    /** Сколько правок приехало последним обменом: цифра на пузыре. */
    var freshCount by mutableIntStateOf(0)

    // --- переходы -------------------------------------------------------

    fun beginWheel(origin: Offset, screenWidth: Float, screenHeight: Float, density: Float) {
        val g = WheelGeometry(density)
        fromRight = origin.x > screenWidth / 2f
        originX = origin.x
        centerY = g.centerFor(origin.y, screenHeight)
        virtual = 0f
        wheelMode = WheelMode.CANCEL
        createArmed = false
        card = null
        mode = PanelMode.WHEEL
        host?.panelVisible(true)
        host?.panelBlur(true)
    }

    fun dragTo(virtualValue: Float, newMode: WheelMode) {
        virtual = virtualValue
        wheelMode = newMode
    }

    /** Палец отпущен. */
    fun releaseWheel() {
        val last = Section.entries.size - 1
        val target = Section.entries[virtual.roundToInt().coerceIn(0, last)]
        when (wheelMode) {
            WheelMode.CANCEL -> close()
            WheelMode.BROWSE -> openBrowse(target)
            WheelMode.CREATE -> {
                close()
                host?.openForm(target, null)
            }
        }
    }

    /** Панель открылась списком текущего раздела. */
    fun openDeck() {
        card = null
        mode = PanelMode.BROWSE
        host?.panelVisible(true)
        host?.panelBlur(true)
        host?.panelTouchable(true)
    }

    /** Смена раздела сегментом: фильтр и открытая запись к нему не относятся. */
    fun pickSection(value: Section) {
        if (section == value) return
        section = value
        group = null
        card = null
        host?.buzz(6)
    }

    /** Поставить или снять фильтр. null — показать весь раздел. */
    fun pickGroup(value: String?) {
        if (group == value) return
        group = value
        host?.buzz(4)
    }

    fun openBrowse(value: Section) {
        section = value
        card = null
        group = null
        mode = PanelMode.BROWSE
        host?.panelVisible(true)
        host?.panelBlur(true)
        host?.panelTouchable(true)
    }

    /**
     * Открытая карточка — это и есть контекст: задача, поставленная отсюда,
     * привяжется к тому же объекту или договору без повторного выбора.
     */
    fun openCard(ref: CardRef) {
        // Переход из чужого раздела — например, из карточки объекта прямо в
        // договор. Фильтр там свой, старый не подходит: снимаем его, иначе
        // после возврата список окажется пустым.
        if (ref.section != section) group = null
        card = ref
        section = ref.section
        when (ref.section) {
            Section.SITES -> {
                contextSiteId = ref.id
                contextContractId = null
            }
            Section.CONTRACTS -> contextContractId = ref.id
            else -> Unit          // заказчик и сотрудник объект не задают
        }
        mode = PanelMode.CARD
        host?.panelVisible(true)
        host?.panelBlur(true)
        host?.panelTouchable(true)
    }

    /** Назад из карточки — в список раздела, а не наружу. */
    fun backFromCard() {
        card = null
        mode = PanelMode.BROWSE
    }

    /** Тап по пузырю: рабочий стол с тем разделом, где остановились. */
    fun openLast() = openDeck()

    fun close() {
        mode = PanelMode.HIDDEN
        card = null
        contextSiteId = null
        contextContractId = null
        group = null
        virtual = 0f
        createArmed = false
        wheelMode = WheelMode.CANCEL
        moving = false
        host?.panelBlur(false)
        host?.panelTouchable(false)
        host?.panelVisible(false)
    }

    val isOpen: Boolean get() = mode != PanelMode.HIDDEN

    const val SECTION_COUNT = 4
}
