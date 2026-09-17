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

/** Слой колоды. Чем глубже, тем конкретнее. */
enum class DeckLevel { SHELF, PACKS, ITEMS, CARD }

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
     * Слой колоды: полка -> пачки -> записи -> запись.
     *
     * Карточки лежат слоями, чтобы не вникать в лишнее: сначала «что за
     * пачка», потом «что внутри». Плоский список с сегментами вверху это
     * убивал — все четыре раздела спорили за внимание одновременно.
     */
    var level by mutableStateOf(DeckLevel.SHELF)
        private set

    /** Выбранная пачка: стадия договоров, отдел, заказчик. null — весь раздел. */
    var group by mutableStateOf<String?>(null)
        private set

    /** Какая карточка стояла в центре на каждом слое — чтобы назад вернуться туда же. */
    val focus = HashMap<String, String>()

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

    /** Тап по пузырю открывает полку — верхний слой колоды. */
    fun openDeck() {
        card = null
        level = DeckLevel.SHELF
        group = null
        showPanel()
    }

    /** Открыть раздел с полки: сначала пачки, если их больше одной. */
    fun openSection(value: Section) {
        if (section != value) focus.remove("items")
        section = value
        group = null
        card = null
        level = DeckLevel.PACKS
        host?.buzz(6)
    }

    /** Открыть пачку: слой записей только этой пачки. */
    fun openGroup(value: String?) {
        group = value
        card = null
        level = DeckLevel.ITEMS
        host?.buzz(6)
    }

    /** Колесо заводит сразу в раздел, минуя полку. */
    fun openBrowse(value: Section) {
        section = value
        card = null
        group = null
        level = DeckLevel.PACKS
        showPanel()
    }

    /**
     * Открытая карточка — это и есть контекст: задача, поставленная отсюда,
     * привяжется к тому же объекту или договору без повторного выбора.
     */
    fun openCard(ref: CardRef) {
        // Переход из чужого раздела — например, из карточки объекта прямо в
        // договор. Пачка там своя, старая не подходит.
        if (ref.section != section) group = null
        card = ref
        section = ref.section
        level = DeckLevel.CARD
        when (ref.section) {
            Section.SITES -> {
                contextSiteId = ref.id
                contextContractId = null
            }
            Section.CONTRACTS -> contextContractId = ref.id
            else -> Unit          // заказчик и сотрудник объект не задают
        }
        showPanel()
    }

    /** Листание в раскрытом виде: соседняя запись становится текущей. */
    fun focusCard(ref: CardRef) {
        if (level != DeckLevel.CARD || card == ref) return
        card = ref
        if (ref.section == Section.SITES) contextSiteId = ref.id
        if (ref.section == Section.CONTRACTS) contextContractId = ref.id
    }

    /**
     * Шаг назад по слоям. hasPacks — есть ли у раздела слой пачек:
     * если пачка одна, её не показываем и назад идём сразу на полку.
     */
    fun back(hasPacks: Boolean) {
        when (level) {
            DeckLevel.CARD -> {
                card?.let { focus["items"] = it.id }
                card = null
                level = DeckLevel.ITEMS
            }
            DeckLevel.ITEMS -> {
                level = if (hasPacks && group != null) DeckLevel.PACKS else DeckLevel.SHELF
                group = null
            }
            DeckLevel.PACKS -> level = DeckLevel.SHELF
            DeckLevel.SHELF -> close()
        }
    }

    /** Совместимость со старым вызовом: назад из карточки — к записям. */
    fun backFromCard() = back(hasPacks = true)

    /** Тап по пузырю: колода с верхнего слоя. */
    fun openLast() = openDeck()

    private fun showPanel() {
        mode = PanelMode.BROWSE
        host?.panelVisible(true)
        host?.panelBlur(true)
        host?.panelTouchable(true)
    }

    fun close() {
        mode = PanelMode.HIDDEN
        card = null
        level = DeckLevel.SHELF
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
