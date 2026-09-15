package ru.quickdeck.mobile.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.Db
import ru.quickdeck.mobile.data.Section
import ru.quickdeck.mobile.data.Stage
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

    DeckHeader(db, host)

    when (level) {
        DeckLevel.CATEGORIES -> CategoryDeck(db, host)
        DeckLevel.ITEMS -> ItemDeck(db, host)
        DeckLevel.CARD -> CardDeck(db, host)
    }

    DeckHint(level)
}

/** Путь и действия. Путь показывает уровень словами, а не только видом. */
@Composable
private fun DeckHeader(db: Db, host: OverlayHost) {
    val level = OverlayState.deck
    val section = OverlayState.section
    val title = when (level) {
        DeckLevel.CATEGORIES -> "Реестр"
        DeckLevel.ITEMS -> section.title
        DeckLevel.CARD -> section.one
    }
    val sub = when (level) {
        DeckLevel.CATEGORIES -> "Выбери раздел"
        DeckLevel.ITEMS -> "${db.count(section)} в реестре"
        DeckLevel.CARD -> section.title
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = T.md, vertical = T.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (level != DeckLevel.CATEGORIES) {
            RoundAction(Ic.chevronLeft, "Назад") { OverlayState.deckBack() }
            Spacer(Modifier.width(T.sm))
        }
        Column(Modifier.weight(1f)) {
            Q(title, Type.heading, T.textOnDark, 1)
            Q(sub, Type.caption, T.text2OnDark, 1)
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
        DeckLevel.ITEMS -> "Листай вбок · тап — открыть запись"
        DeckLevel.CARD -> "Листай вбок — соседние записи"
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
                val away = abs(index - center)
                val focused = index == center
                val scale by animateFloatAsState(
                    targetValue = if (focused) 1f else 0.92f,
                    animationSpec = tween(T.MS_STATE, easing = T.curve),
                    label = "cardScale"
                )
                val fade by animateFloatAsState(
                    targetValue = if (focused) 1f else (1f - 0.22f * away).coerceIn(0.35f, 1f),
                    animationSpec = tween(T.MS_STATE, easing = T.curve),
                    label = "cardFade"
                )

                Box(
                    Modifier
                        .width(cardW)
                        .height(cardH)
                        .scale(scale)
                        .alpha(fade)
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

@Composable
private fun ColumnScope.CategoryDeck(db: Db, host: OverlayHost) {
    val sections = remember { Section.entries.toList() }
    Carousel(
        items = sections,
        startIndex = sections.indexOf(OverlayState.section).coerceAtLeast(0),
        key = { it.name },
        onCenter = { OverlayState.focusSection(it, host) },
        onOpen = { OverlayState.openItems(it) }
    ) { section, focused ->
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

// --- уровень 2: записи раздела -------------------------------------------

/** Что показывать в карточке записи: одинаково для всех разделов. */
private data class ItemFace(
    val id: String,
    val title: String,
    val subtitle: String,
    val stage: Stage?,
    val trailing: String,
    val facts: List<Pair<String, String>>
)

@Composable
private fun ColumnScope.ItemDeck(db: Db, host: OverlayHost) {
    val section = OverlayState.section
    val faces = remember(db, section) { facesOf(db, section) }

    Carousel(
        items = faces,
        startIndex = faces.indexOfFirst { it.id == OverlayState.card?.id }.coerceAtLeast(0),
        key = { it.id },
        onCenter = { host.buzz(6) },
        onOpen = { OverlayState.openCard(CardRef(section, it.id)) }
    ) { face, focused ->
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(T.rIcon))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) { QIcon(sectionIcon(section), size = 22.dp, tint = T.text2OnDark) }
                Spacer(Modifier.width(T.md))
                face.stage?.let { DarkStageChip(it) }
            }

            Spacer(Modifier.height(T.lg))
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
    val ids = remember(db, section) { facesOf(db, section).map { it.id } }
    val startId = OverlayState.card?.id

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
