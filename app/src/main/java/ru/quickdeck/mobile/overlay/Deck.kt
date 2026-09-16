package ru.quickdeck.mobile.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import androidx.compose.ui.geometry.Offset
import ru.quickdeck.mobile.data.Db
import ru.quickdeck.mobile.data.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.quickdeck.mobile.data.Section
import ru.quickdeck.mobile.data.Stage
import ru.quickdeck.mobile.data.Status
import ru.quickdeck.mobile.data.plural
import ru.quickdeck.mobile.data.dateShort
import ru.quickdeck.mobile.data.money
import ru.quickdeck.mobile.ui.Pressable
import ru.quickdeck.mobile.ui.shownStatus
import ru.quickdeck.mobile.ui.tone
import kotlin.math.abs

/**
 * Колода: карточки стоят в центре экрана и листаются пальцем вбок.
 *
 * Почему так, а не списком снизу. Реестр — это уровни: разделы, записи
 * раздела, сама запись. Лист снизу показывал один уровень и прятал, где ты
 * находишься; в колоде уровень виден целиком, соседние карточки подсказывают,
 * что рядом, а переход вглубь — это шаг вперёд, а не новый экран.
 *
 * Движение разведено по осям, чтобы жесты не спорили: вбок — соседи по
 * уровню, вперёд — тап по центральной карточке, назад — стрелка в шапке.
 * Внутри раскрытой карточки вертикаль отдана её прокрутке.
 */

/** Ширина карточки — доля ширины экрана. Соседи должны выглядывать. */
private const val CARD_WIDTH_FRACTION = 0.78f
private const val CARD_HEIGHT_FRACTION = 0.62f

@Composable
fun ColumnScope.DeckLayer(db: Db, host: OverlayHost) {
    val level = OverlayState.deck

    StatusBar(db)
    DeckHeader(db, host)

    when (level) {
        DeckLevel.CATEGORIES -> CategoryDeck(db, host)
        DeckLevel.GROUPS -> GroupDeck(db, host)
        DeckLevel.ITEMS -> ItemDeck(db, host)
        DeckLevel.CARD -> CardDeck(db, host)
        DeckLevel.SUMMARY -> SummaryDeck(db)
    }

    DeckHint(level)
}


/**
 * Строка состояния — то, что отличает терминал от списка.
 *
 * Слева видно, на связи ли система и когда последний раз говорила с
 * таблицей, справа — время и объём реестра. Человек открывает панель и
 * сразу знает, свежие ли перед ним данные, ещё не прочитав ни одной
 * карточки.
 */
@Composable
private fun StatusBar(db: Db) {
    val syncing = OverlayState.syncing
    val pulse = rememberInfiniteTransition(label = "status")
    val beat by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beat"
    )
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val now = remember(OverlayState.mode) { clock.format(Date()) }
    val last = Store.lastSync.ifBlank { "нет связи" }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = T.lg, vertical = T.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background((if (syncing) T.beam else T.success.fill).copy(alpha = beat))
        )
        Spacer(Modifier.width(T.sm))
        Q(
            if (syncing) "ОБМЕН" else "ПОДРЯД · " + last,
            Type.caption,
            T.text2OnDark,
            1,
            Modifier.weight(1f)
        )
        Q(
            "${db.liveSites.size}/${db.liveContracts.size}/${db.liveEmployees.size}",
            Type.caption,
            T.text2OnDark,
            1
        )
        Spacer(Modifier.width(T.sm))
        Q(now, Type.caption, T.textOnDark, 1)
    }
}

/** Путь и действия. Путь показывает уровень словами, а не только видом. */
@Composable
private fun DeckHeader(db: Db, host: OverlayHost) {
    val level = OverlayState.deck
    val section = OverlayState.section
    val group = OverlayState.group
    val title = when (level) {
        DeckLevel.CATEGORIES -> "Реестр"
        DeckLevel.GROUPS -> section.title
        DeckLevel.ITEMS -> group ?: section.title
        DeckLevel.CARD -> section.one
        DeckLevel.SUMMARY -> "Сводка"
    }
    val sub = when (level) {
        DeckLevel.CATEGORIES -> "Выбери раздел"
        DeckLevel.GROUPS -> groupTitle(section)
        DeckLevel.ITEMS -> if (group != null) section.title else "${db.count(section)} в реестре"
        DeckLevel.CARD -> listOfNotNull(section.title, group).joinToString(" · ")
        DeckLevel.SUMMARY -> "Как идут дела"
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = T.md, vertical = T.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (level != DeckLevel.CATEGORIES) {
            RoundAction(Ic.chevronLeft, "Назад") {
                OverlayState.deckBack(groupsOf(db, OverlayState.section).size > 1)
            }
            Spacer(Modifier.width(T.sm))
        }
        Column(Modifier.weight(1f)) {
            Q(title, Type.heading, T.textOnDark, 1)
            Q(sub, Type.caption, T.text2OnDark, 1)
        }
        if (level == DeckLevel.CATEGORIES) {
            // Настройки под рукой: раньше за ними нужно было выходить в
            // приложение, хотя работа идёт из панели.
            RoundAction(Ic.settings, "Настройки") { host.openSettings() }
            Spacer(Modifier.width(T.xs))
        }
        if (level != DeckLevel.CARD) {
            RoundAction(Ic.search, "Найти") { host.openSearch() }
            Spacer(Modifier.width(T.xs))
            RoundAction(Ic.plus, "Добавить", accent = true) {
                host.openForm(OverlayState.section, null)
            }
            Spacer(Modifier.width(T.xs))
        }
        RoundAction(Ic.close, "Закрыть") { OverlayState.close() }
    }
}

@Composable
private fun DeckHint(level: DeckLevel) {
    val text = when (level) {
        DeckLevel.CATEGORIES -> "Листай вбок · тап — открыть раздел"
        DeckLevel.GROUPS -> "Листай вбок · тап — открыть пачку"
        DeckLevel.ITEMS -> "Листай вбок · тап — открыть запись"
        DeckLevel.CARD -> "Листай вбок — соседние записи"
        DeckLevel.SUMMARY -> "Назад — к разделам"
    }
    Box(Modifier.fillMaxWidth().padding(bottom = T.lg), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = T.md, vertical = 6.dp)
        ) { Q(text, Type.caption, T.text2OnDark, 1) }
    }
}

/**
 * Сама лента. Одна на все три уровня: отличается только тем, что нарисовано
 * внутри карточки, а поведение — прокрутка, прилипание, центр — общее.
 */
@Composable
private fun <E> ColumnScope.Carousel(
    items: List<E>,
    startIndex: Int,
    key: (E) -> Any,
    onCenter: (E) -> Unit,
    onOpen: (E) -> Unit,
    card: @Composable (E, Boolean) -> Unit
) {
    if (items.isEmpty()) {
        DeckEmpty()
        return
    }

    val conf = LocalConfiguration.current
    val cardW = (conf.screenWidthDp * CARD_WIDTH_FRACTION).dp
    val cardH = (conf.screenHeightDp * CARD_HEIGHT_FRACTION).dp
    val side = ((conf.screenWidthDp.dp - cardW) / 2).coerceAtLeast(0.dp)

    val state = rememberLazyListState(startIndex.coerceIn(0, items.lastIndex))
    val scope = rememberCoroutineScope()
    val arcDepth = with(LocalDensity.current) { 34.dp.toPx() }

    val center by remember(items) {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            val width = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            (first + if (offset > width / 2) 1 else 0).coerceIn(0, items.lastIndex)
        }
    }

    // Смена центра — это и есть выбор: короткое вибро подтверждает шаг.
    LaunchedEffect(center, items) {
        items.getOrNull(center)?.let(onCenter)
    }

    Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(T.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                val focused = index == center
                // Насколько карточка ушла от центра: 0 — ровно в центре,
                // ±1 — на месте соседа. Берётся из реального смещения ленты,
                // поэтому дуга едет вместе с пальцем, а не скачками по индексу.
                val offset = centerOffset(state, index)

                Box(
                    Modifier
                        .width(cardW)
                        .height(cardH)
                        .graphicsLayer {
                            val d = offset.coerceIn(-2f, 2f)
                            val fall = d * d
                            // Карточки идут по дуге: края ниже и завалены
                            // наружу, будто лежат на колесе, а не на рельсе.
                            translationY = fall * arcDepth
                            rotationZ = d * 6f
                            scaleX = 1f - 0.12f * fall
                            scaleY = 1f - 0.12f * fall
                            alpha = (1f - 0.34f * fall).coerceIn(0.28f, 1f)
                            cameraDistance = 16f * density
                        }
                ) {
                    DeckCard(focused) {
                        // Тап по соседней карточке подводит её в центр, а не
                        // открывает вслепую: сначала видно, что открываешь.
                        if (focused) onOpen(item)
                        else scope.launch { state.animateScrollToItem(index) }
                    }
                    Box(Modifier.fillMaxSize().padding(T.lg)) { card(item, focused) }
                }
            }
        }
    }
}

/**
 * Смещение карточки от центра экрана в ширинах карточки: 0 — в центре,
 * ±1 — на месте соседа. Пока карточка не отрисована, считаем по индексам.
 */
private fun centerOffset(state: LazyListState, index: Int): Float {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return (index - state.firstVisibleItemIndex).toFloat()
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val itemCenter = item.offset + item.size / 2f
    val step = (item.size + info.mainAxisItemSpacing).coerceAtLeast(1)
    return (itemCenter - viewportCenter) / step
}

/**
 * Угловые маркеры: четыре коротких штриха по углам активной карточки.
 *
 * Приборный приём — рамка прицела. Он говорит «вот это сейчас под
 * управлением» тише, чем заливка, и не спорит с содержимым.
 */
@Composable
private fun Corners(active: Boolean) {
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "corners"
    )
    if (glow <= 0.01f) return
    Canvas(Modifier.fillMaxSize().padding(T.sm)) {
        val len = 14.dp.toPx()
        val w = 1.6.dp.toPx()
        val c = T.glow.copy(alpha = 0.8f * glow)
        val pts = listOf(
            Offset(0f, 0f) to listOf(Offset(len, 0f), Offset(0f, len)),
            Offset(size.width, 0f) to listOf(Offset(size.width - len, 0f), Offset(size.width, len)),
            Offset(0f, size.height) to listOf(Offset(len, size.height), Offset(0f, size.height - len)),
            Offset(size.width, size.height) to
                listOf(Offset(size.width - len, size.height), Offset(size.width, size.height - len))
        )
        pts.forEach { (from, ends) ->
            ends.forEach { to -> drawLine(c, from, to, strokeWidth = w) }
        }
    }
}

/** Подложка карточки. Стеклянная, с заметной кромкой у центральной. */
@Composable
private fun DeckCard(focused: Boolean, onClick: () -> Unit) {
    val border by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "cardBorder"
    )
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(T.rSheet))
            .background(if (focused) T.panelRaised else T.panelCard)
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = T.panelEdge.copy(alpha = 0.6f + 0.4f * border),
                shape = RoundedCornerShape(T.rSheet)
            )
    ) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(T.rSheet)),
            contentAlignment = Alignment.Center
        ) {
            Pressable(onClick, Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
        }
        Corners(focused)
    }
}

@Composable
private fun ColumnScope.DeckEmpty() {
    Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(T.xl), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QIcon(Ic.layers, size = 40.dp, tint = T.text2OnDark, stroke = 1.5f)
            Spacer(Modifier.height(T.md))
            Q("Здесь пока пусто", Type.heading, T.textOnDark)
            Spacer(Modifier.height(T.xs))
            Q("Заведи первую запись — она появится тут", Type.small, T.text2OnDark)
        }
    }
}

// --- уровень 1: разделы ---------------------------------------------------

/** Первая карточка колоды — не раздел, а состояние дел. */
private val SUMMARY_KEY: Section? = null

@Composable
private fun ColumnScope.CategoryDeck(db: Db, host: OverlayHost) {
    // null — сводка, дальше обычные разделы. Одна лента, разный смысл карточек.
    val items = remember { listOf<Section?>(SUMMARY_KEY) + Section.entries.toList() }
    Carousel(
        items = items,
        startIndex = (items.indexOf(OverlayState.section)).coerceAtLeast(0),
        key = { it?.name ?: "summary" },
        onCenter = { if (it != null) OverlayState.focusSection(it, host) else host.buzz(6) },
        onOpen = {
            if (it == null) OverlayState.openSummary()
            else OverlayState.openItems(it, groupsOf(db, it).size > 1)
        }
    ) { value, focused ->
        if (value == null) {
            SummaryFace(db, focused)
            return@Carousel
        }
        val section = value
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(T.rCard))
                    .background(Color.White.copy(alpha = if (focused) 0.12f else 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                QIcon(
                    sectionIcon(section),
                    size = 34.dp,
                    tint = if (focused) T.accent.fill else T.text2OnDark,
                    stroke = if (focused) 2f else 1.75f
                )
            }
            Spacer(Modifier.height(T.lg))
            Q(section.title, Type.display, T.textOnDark, 2)
            Spacer(Modifier.height(T.xs))
            Q("${db.count(section)} в реестре", Type.small, T.text2OnDark, 1)
        }
    }
}

/**
 * Сводка — первое, что видно при открытии панели: сколько в работе, что
 * горит, сколько денег ждёт. Руководителю обычно нужен именно этот ответ,
 * а не список из сорока записей.
 */
@Composable
private fun SummaryFace(db: Db, focused: Boolean) {
    val s = remember(db) { summaryOf(db) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(T.rIcon))
                    .background(T.accent.fill.copy(alpha = if (focused) 0.22f else 0.12f)),
                contentAlignment = Alignment.Center
            ) { QIcon(Ic.summary, size = 22.dp, tint = T.accent.fill, stroke = 2f) }
            Spacer(Modifier.width(T.md))
            Column {
                Q("Сводка", Type.title, T.textOnDark, 1)
                Q("на сегодня", Type.caption, T.text2OnDark, 1)
            }
        }

        Spacer(Modifier.height(T.lg))
        SummaryLine("В работе", "${s.inWork}", T.accent)
        SummaryLine("Просрочено", "${s.overdue}", if (s.overdue > 0) T.danger else T.muted)
        SummaryLine("Ждёт оплаты", "${s.awaitingPay}", if (s.awaitingPay > 0) T.warning else T.muted)

        Spacer(Modifier.height(T.md))
        Q("Законтрактовано", Type.caption, T.text2OnDark)
        Q(money(s.contracted), Type.display, T.textOnDark, 1)
        Spacer(Modifier.height(T.xs))
        Q("Осталось получить " + money(s.rest), Type.small, T.text2OnDark, 1)

        if (s.soon.isNotEmpty()) {
            Spacer(Modifier.height(T.md))
            Q("Ближайшие сроки", Type.caption, T.text2OnDark)
            Spacer(Modifier.height(T.xs))
            s.soon.forEach { (label, due) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    QIcon(Ic.clock, size = 14.dp, tint = T.text2OnDark)
                    Spacer(Modifier.width(T.xs))
                    Q(label, Type.caption, T.textOnDark, 1, Modifier.weight(1f))
                    Q(due, Type.caption, T.text2OnDark, 1)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(title: String, value: String, tone: T.Tone) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(tone.fill)
        )
        Spacer(Modifier.width(T.sm))
        Q(title, Type.small, T.text2OnDark, 1, Modifier.weight(1f))
        Q(value, Type.amount, T.textOnDark, 1)
    }
}

private data class Summary(
    val inWork: Int,
    val overdue: Int,
    val awaitingPay: Int,
    val contracted: Long,
    val rest: Long,
    val soon: List<Pair<String, String>>
)

/** Считается один раз на изменение реестра, а не на каждую перерисовку. */
private fun summaryOf(db: Db): Summary {
    val live = db.liveContracts
    val active = live.filter { shownStatus(it).stage !in archiveStages && shownStatus(it) != Status.PAID_FULL }
    val soon = active
        .filter { it.end.isNotBlank() }
        .sortedBy { it.end }
        .take(3)
        .map { c -> (db.site(c.siteId)?.name ?: c.workKind) to dateShort(c.end) }
    return Summary(
        inWork = active.count { shownStatus(it).stage == Stage.PRODUCTION },
        overdue = live.count { shownStatus(it) == Status.OVERDUE },
        awaitingPay = active.count { shownStatus(it).stage == Stage.PAYMENT },
        contracted = active.filter { shownStatus(it).signed }.sumOf { it.amount },
        rest = active.filter { shownStatus(it).signed }.sumOf { it.restAmount },
        soon = soon
    )
}

/** Развёрнутая сводка — одна карточка во весь рост. */
@Composable
private fun ColumnScope.SummaryDeck(db: Db) {
    Carousel(
        items = listOf(Unit),
        startIndex = 0,
        key = { "summary" },
        onCenter = { },
        onOpen = { }
    ) { _, focused -> SummaryFace(db, focused) }
}


/**
 * Обложка карточки — узкая цветная полоса поверху.
 *
 * Цвет выводится из названия: у одной и той же записи он всегда одинаковый,
 * у соседних — разный. Карточки перестают выглядеть близнецами, и пролистывая
 * ленту, узнаёшь нужную боковым зрением, ещё не прочитав подпись. Фото сюда
 * встанет позже, на то же место.
 */
@Composable
private fun Cover(seed: String, icon: String, tone: T.Tone?) {
    val hue = remember(seed) { coverHue(seed) }
    val base = tone?.fill ?: Color.hsv(hue, 0.45f, 0.85f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(T.rCard))
            .background(
                Brush.linearGradient(
                    listOf(base.copy(alpha = 0.34f), base.copy(alpha = 0.10f))
                )
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.padding(start = T.md)) {
            QIcon(icon, size = 28.dp, tint = base, stroke = 2f)
        }
    }
}

/** Устойчивый оттенок из строки: одно имя — один цвет, всегда. */
private fun coverHue(seed: String): Float {
    var h = 0
    seed.forEach { h = h * 31 + it.code }
    return ((h % 360) + 360) % 360f
}

// --- уровень 2: пачки ------------------------------------------------------

/**
 * Пачка — промежуточный слой между разделом и записями.
 *
 * Полсотни договоров одной лентой не читаются: глаз цепляется за отказы и
 * закрытые, хотя работать нужно с текущими. Поэтому договоры разложены по
 * стадиям, а всё отменённое и оплаченное уходит в «Архив» — он последний и
 * открывается, только если туда зайти. Сотрудники разложены по отделам,
 * объекты — по заказчикам.
 */
private data class Pack(val name: String, val count: Int, val tone: T.Tone, val archive: Boolean)

private fun groupTitle(section: Section): String = when (section) {
    Section.CONTRACTS -> "По стадиям"
    Section.STAFF -> "По отделам"
    Section.SITES -> "По заказчикам"
    Section.CUSTOMERS -> ""
}

/** Архив — то, с чем уже не работают: отказ, расторжение, полностью оплачен. */
private val archiveStages = setOf(Stage.PROBLEM)

private fun packToneOf(section: Section, name: String): T.Tone = when (section) {
    Section.CONTRACTS -> Stage.entries.firstOrNull { it.label == name }?.tone() ?: T.muted
    else -> T.muted
}

/** Пачки раздела в порядке, в котором по ним ходят. */
private fun groupsOf(db: Db, section: Section): List<Pack> {
    val faces = facesOf(db, section)
    if (faces.isEmpty()) return emptyList()
    val names = LinkedHashMap<String, Int>()
    faces.forEach { f ->
        val key = f.group ?: return@forEach
        names[key] = (names[key] ?: 0) + 1
    }
    if (names.isEmpty()) return emptyList()

    val order = when (section) {
        Section.CONTRACTS -> Stage.entries.map { it.label } + listOf(ARCHIVE)
        else -> names.keys.toList()
    }
    return order.filter { names.containsKey(it) }.map { name ->
        Pack(
            name = name,
            count = names[name] ?: 0,
            tone = if (name == ARCHIVE) T.muted else packToneOf(section, name),
            archive = name == ARCHIVE
        )
    }
}

private const val ARCHIVE = "Архив"

@Composable
private fun ColumnScope.GroupDeck(db: Db, host: OverlayHost) {
    val section = OverlayState.section
    val packs = remember(db, section) { groupsOf(db, section) }

    Carousel(
        items = packs,
        startIndex = packs.indexOfFirst { it.name == OverlayState.group }.coerceAtLeast(0),
        key = { it.name },
        onCenter = { OverlayState.focusGroup(it.name, host) },
        onOpen = { OverlayState.openGroup(it.name) }
    ) { pack, focused ->
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(T.rCard))
                    .background(
                        if (pack.archive) Color.White.copy(alpha = 0.06f)
                        else pack.tone.fill.copy(alpha = if (focused) 0.22f else 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                QIcon(
                    when {
                        pack.archive -> Ic.layers
                        section == Section.STAFF -> departmentIcon(pack.name)
                        else -> sectionIcon(section)
                    },
                    size = 30.dp,
                    tint = if (pack.archive) T.text2OnDark else pack.tone.fill,
                    stroke = if (focused) 2f else 1.75f
                )
            }
            Spacer(Modifier.height(T.lg))
            Q(pack.name, Type.title, T.textOnDark, 3)
            Spacer(Modifier.height(T.xs))
            Q(
                "${pack.count} " + plural(pack.count.toLong(), "запись", "записи", "записей"),
                Type.small,
                T.text2OnDark,
                1
            )
            if (pack.archive) {
                Spacer(Modifier.height(T.sm))
                Q("Отказы и расторжения — чтобы не мешались", Type.caption, T.text2OnDark, 2)
            }
        }
    }
}

// --- уровень 3: записи пачки ----------------------------------------------

/** Что показывать в карточке записи: одинаково для всех разделов. */
private data class ItemFace(
    val id: String,
    val title: String,
    val subtitle: String,
    val stage: Stage?,
    val trailing: String,
    val group: String?,
    val icon: String,
    val facts: List<Pair<String, String>>
)

@Composable
private fun ColumnScope.ItemDeck(db: Db, host: OverlayHost) {
    val section = OverlayState.section
    val group = OverlayState.group
    val faces = remember(db, section, group) { facesOf(db, section).filter { group == null || it.group == group } }

    Carousel(
        items = faces,
        startIndex = faces.indexOfFirst { it.id == OverlayState.card?.id }.coerceAtLeast(0),
        key = { it.id },
        onCenter = { host.buzz(6) },
        onOpen = { OverlayState.openCard(CardRef(section, it.id)) }
    ) { face, focused ->
        Column(Modifier.fillMaxSize()) {
            Cover(face.title, face.icon, face.stage?.tone())
            Spacer(Modifier.height(T.md))
            face.stage?.let {
                DarkStageChip(it)
                Spacer(Modifier.height(T.sm))
            }
            Q(face.title, Type.title, T.textOnDark, 3)
            if (face.subtitle.isNotBlank()) {
                Spacer(Modifier.height(T.xs))
                Q(face.subtitle, Type.small, T.text2OnDark, 2)
            }
            if (face.trailing.isNotBlank()) {
                Spacer(Modifier.height(T.md))
                Q(face.trailing, Type.display, T.textOnDark, 1)
            }

            Spacer(Modifier.height(T.lg))
            face.facts.filter { it.second.isNotBlank() }.take(4).forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Q(k, Type.caption, T.text2OnDark, 1, Modifier.width(96.dp))
                    Q(v, Type.small, T.textOnDark, 2, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.weight(1f))
            if (focused) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Q("Открыть карточку", Type.small, T.accent.fill, 1)
                    Spacer(Modifier.width(T.xs))
                    QIcon(Ic.chevronRight, size = 18.dp, tint = T.accent.fill, stroke = 2f)
                }
            }
        }
    }
}

private fun facesOf(db: Db, section: Section): List<ItemFace> = when (section) {
    Section.SITES -> db.liveSites.map { s ->
        ItemFace(
            id = s.id,
            title = s.name.ifBlank { "Без названия" },
            subtitle = db.customer(s.customerId)?.name.orEmpty(),
            stage = db.stageOfSite(s.id),
            trailing = if (s.progress > 0) "${s.progress} %" else "",
            group = db.customer(s.customerId)?.name ?: "Без заказчика",
            icon = buildingIcon(s.buildingType),
            facts = listOf(
                "Адрес" to s.address,
                "Тип" to s.buildingType,
                "Договоров" to db.activeContractsOfSite(s.id).size.toString()
            )
        )
    }

    Section.CONTRACTS -> db.liveContracts.map { c ->
        val st = shownStatus(c)
        ItemFace(
            id = c.id,
            title = c.label(db.site(c.siteId)?.name),
            subtitle = st.label,
            stage = st.stage,
            trailing = if (c.amount != 0L) money(c.amount) else "",
            // Отказ и расторжение — в архив, остальное по стадии.
            group = if (st.stage in archiveStages || st == Status.PAID_FULL) ARCHIVE else st.stage.label,
            icon = departmentIcon(db.refs.departmentOf(c.workKind)),
            facts = listOf(
                "Объект" to (db.site(c.siteId)?.name ?: ""),
                "Срок" to (c.end.takeIf { it.isNotBlank() }?.let { dateShort(it) } ?: ""),
                "Ответственный" to c.responsible
            )
        )
    }

    Section.CUSTOMERS -> db.liveCustomers.map { p ->
        ItemFace(
            id = p.id,
            title = p.name.ifBlank { "Без названия" },
            subtitle = p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }.orEmpty(),
            stage = null,
            trailing = "",
            group = null,
            icon = Ic.customers,
            facts = listOf(
                "Телефон" to p.phone,
                "Руководитель" to p.director,
                "Объектов" to db.sitesOfCustomer(p.id).size.toString()
            )
        )
    }

    Section.STAFF -> db.liveEmployees.map { e ->
        ItemFace(
            id = e.id,
            title = e.name.ifBlank { "Без имени" },
            subtitle = listOf(e.position, e.department).filter { it.isNotBlank() }.joinToString(" · "),
            stage = null,
            trailing = "",
            group = e.department.ifBlank { "Без отдела" },
            icon = departmentIcon(e.department),
            facts = listOf(
                "Телефон" to e.phones.firstOrNull().orEmpty(),
                "Чатов" to e.chats.size.toString(),
                "Таб. №" to e.tabNumber
            )
        )
    }
}

// --- уровень 3: раскрытая карточка ---------------------------------------

/**
 * Раскрытая запись — такая же карточка колоды, только внутри полное тело.
 * Соседи остаются рядом: с объекта на объект переходишь тем же движением,
 * не возвращаясь на уровень выше.
 */
@Composable
private fun ColumnScope.CardDeck(db: Db, host: OverlayHost) {
    val section = OverlayState.section
    val group = OverlayState.group
    val startId = OverlayState.card?.id
    // Если открытая запись не из текущей пачки (пришли из другой карточки),
    // соседями становится весь раздел — иначе лента оказалась бы пустой.
    val ids = remember(db, section, group, startId) {
        val all = facesOf(db, section)
        val inGroup = all.filter { group == null || it.group == group }
        val list = if (startId != null && inGroup.none { it.id == startId }) all else inGroup
        list.map { it.id }
    }

    Carousel(
        items = ids,
        startIndex = ids.indexOf(startId).coerceAtLeast(0),
        key = { it },
        onCenter = { OverlayState.focusCard(section, it, host) },
        onOpen = { }
    ) { id, _ ->
        // Тела карточек взяты как есть: они уже умеют прокрутку и действия,
        // переписывать их ради новой раскладки незачем.
        Column(Modifier.fillMaxSize()) {
            when (section) {
                Section.SITES -> db.site(id)?.let { SiteBody(it, db, host) }
                Section.CONTRACTS -> db.contract(id)?.let { ContractBody(it, db, host) }
                Section.CUSTOMERS -> db.customer(id)?.let { PartyBody(it, db, host) }
                Section.STAFF -> db.employee(id)?.let { StaffBody(it, db, host) }
            }
        }
    }
}
