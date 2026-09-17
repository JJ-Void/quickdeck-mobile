package ru.quickdeck.mobile.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.quickdeck.mobile.core.Feel
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.Db
import ru.quickdeck.mobile.data.Section
import ru.quickdeck.mobile.data.Stage
import ru.quickdeck.mobile.data.Status
import ru.quickdeck.mobile.data.Store
import ru.quickdeck.mobile.data.dateShort
import ru.quickdeck.mobile.data.money
import ru.quickdeck.mobile.data.plural
import ru.quickdeck.mobile.data.summary
import ru.quickdeck.mobile.ui.shownStatus
import ru.quickdeck.mobile.ui.tone
import kotlin.math.abs

/**
 * Колода — панель поверх чужих экранов.
 *
 * Карточки стоят в центре и листаются пальцем вбок, как в 1.10: полка
 * разделов -> пачки -> записи -> раскрытая запись. На каждом слое одна
 * карточка в фокусе, соседи выглядывают по краям и подсказывают, что рядом.
 * Смысл слоёв — не вникать в лишнее: сначала «что за пачка», потом «что
 * внутри», и только потом подробности.
 *
 * Жесты разведены, чтобы не спорить друг с другом:
 *  - вбок — соседи по слою;
 *  - тап по центральной — шаг вглубь, по соседней — подвести её в центр;
 *  - удержание — быстрое действие, кольцо под пальцем показывает, сколько
 *    осталось держать; отпустил раньше — ничего не случилось;
 *  - назад — стрелка в шапке.
 * Вертикаль внутри раскрытой карточки отдана её прокрутке.
 *
 * Переход между слоями — приближение, а не сдвиг: вглубь карточка
 * вырастает из центра, назад — отъезжает вдаль. Боковое движение уже
 * занято листанием, и второй смысл у него путал направление.
 */
@Composable
fun ColumnScope.DeckLayer(db: Db, host: OverlayHost) {
    val section = OverlayState.section
    val packs = remember(db, section) { packsOf(db, section) }
    val hasPacks = packs.size >= 2

    // Пачка одна — слой пачек пустой по смыслу, его пропускаем.
    val level = OverlayState.level.let {
        if (it == DeckLevel.PACKS && !hasPacks) DeckLevel.ITEMS else it
    }
    val group = OverlayState.group

    DeckHeader(db, host, level, section, group, hasPacks)

    AnimatedContent(
        targetState = Layer(level, section, group),
        modifier = Modifier.weight(1f).fillMaxWidth(),
        transitionSpec = {
            val deeper = targetState.level.ordinal > initialState.level.ordinal
            val same = targetState.level == initialState.level
            when {
                same -> fadeIn(tween(T.MS_STATE, easing = T.curve)) togetherWith
                    fadeOut(tween(T.MS_EXIT))

                deeper -> (scaleIn(tween(T.MS_SCREEN, easing = T.curve), initialScale = 0.86f) +
                    fadeIn(tween(T.MS_STATE, easing = T.curve))) togetherWith
                    (scaleOut(tween(T.MS_EXIT, easing = T.curve), targetScale = 1.08f) +
                        fadeOut(tween(T.MS_EXIT)))

                else -> (scaleIn(tween(T.MS_SCREEN, easing = T.curve), initialScale = 1.08f) +
                    fadeIn(tween(T.MS_STATE, easing = T.curve))) togetherWith
                    (scaleOut(tween(T.MS_EXIT, easing = T.curve), targetScale = 0.86f) +
                        fadeOut(tween(T.MS_EXIT)))
            }
        },
        label = "deck"
    ) { layer ->
        Column(Modifier.fillMaxSize()) {
            when (layer.level) {
                DeckLevel.SHELF -> ShelfDeck(db, host)
                DeckLevel.PACKS -> PackDeck(db, host, layer.section, packs)
                DeckLevel.ITEMS -> ItemDeck(db, host, layer.section, layer.group)
                DeckLevel.CARD -> CardDeck(db, host, layer.section, layer.group)
            }
        }
    }
}

/** Ключ слоя. Смена записи внутри раскрытого слоя — не смена слоя. */
private data class Layer(val level: DeckLevel, val section: Section, val group: String?)

// --- шапка ----------------------------------------------------------------

@Composable
private fun DeckHeader(
    db: Db,
    host: OverlayHost,
    level: DeckLevel,
    section: Section,
    group: String?,
    hasPacks: Boolean
) {
    val title = when (level) {
        DeckLevel.SHELF -> "Подряд"
        DeckLevel.PACKS -> section.title
        DeckLevel.ITEMS -> group ?: section.title
        DeckLevel.CARD -> section.one
    }
    val sub = when (level) {
        DeckLevel.SHELF -> Store.lastSync.let { if (it.isBlank()) "Обмен не настроен" else "Обновлено $it" }
        DeckLevel.PACKS -> packsTitle(section)
        DeckLevel.ITEMS -> if (group != null) section.title else countLabel(db.count(section))
        DeckLevel.CARD -> listOfNotNull(section.title, group).joinToString(" · ")
    }

    Row(
        Modifier.fillMaxWidth().padding(start = T.md, end = T.sm, top = T.sm, bottom = T.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (level != DeckLevel.SHELF) {
            RoundAction(Ic.chevronLeft, "Назад") {
                Feel.tick()
                OverlayState.back(hasPacks)
            }
            Spacer(Modifier.width(T.sm))
        }
        Column(Modifier.weight(1f)) {
            Q(title, Type.heading, T.textOnDark, 1)
            Q(sub.uppercase(), Type.label, T.text2OnDark, 1)
        }
        if (level != DeckLevel.CARD) {
            RoundAction(Ic.search, "Найти") { host.openSearch() }
            Spacer(Modifier.width(T.xs))
            RoundAction(Ic.plus, "Добавить", accent = true) {
                val target = if (level == DeckLevel.SHELF) {
                    Section.entries.firstOrNull { it.name == OverlayState.focus["shelf"] } ?: Section.CONTRACTS
                } else section
                host.openForm(target, null)
            }
            Spacer(Modifier.width(T.xs))
        } else {
            RoundAction(Ic.edit, "Изменить") {
                OverlayState.card?.let { host.openForm(it.section, it.id) }
            }
            Spacer(Modifier.width(T.xs))
        }
        RoundAction(Ic.close, "Закрыть") { OverlayState.close() }
    }
}

private fun countLabel(n: Int) = "$n " + plural(n.toLong(), "запись", "записи", "записей")

// --- лента ----------------------------------------------------------------

/**
 * Лента карточек. Одна на все слои: отличается только тем, что нарисовано
 * внутри, а поведение — прокрутка, прилипание, центр, дуга — общее.
 *
 * @param memo ключ, под которым запоминается центральная карточка слоя.
 * @param interactive карточка сама принимает касания (раскрытая запись);
 *   тогда тап и удержание по ней не перехватываются.
 */
@Composable
private fun <E> ColumnScope.Carousel(
    items: List<E>,
    memo: String,
    key: (E) -> String,
    onCenter: (E) -> Unit = {},
    onOpen: (E) -> Unit,
    onHold: ((E) -> Unit)? = null,
    hint: String,
    interactive: Boolean = false,
    stack: (E) -> Int = { 0 },
    card: @Composable BoxScope.(E, Boolean) -> Unit
) {
    if (items.isEmpty()) {
        DeckEmpty()
        return
    }

    val start = remember(items) {
        items.indexOfFirst { key(it) == OverlayState.focus[memo] }.coerceAtLeast(0)
    }
    val state = rememberLazyListState(start)
    val scope = rememberCoroutineScope()

    val center by remember(items) {
        derivedStateOf { centerIndex(state).coerceIn(0, items.lastIndex) }
    }

    // Смена центра — это и есть выбор: короткий удар подтверждает шаг.
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(center, items) {
        val item = items.getOrNull(center) ?: return@LaunchedEffect
        OverlayState.focus[memo] = key(item)
        onCenter(item)
        if (!first) Feel.tick()
        first = false
    }

    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val cardW = maxWidth * CARD_WIDTH
        val side = (maxWidth - cardW) / 2
        // Место под стопку сверху: у папок видно, что внутри ещё слой.
        val cardH = maxHeight - STACK_ROOM

        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(T.md),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                val focused = index == center
                Box(
                    Modifier
                        .width(cardW)
                        .fillMaxHeight()
                        .graphicsLayer {
                            // Насколько карточка ушла от центра: 0 — в центре,
                            // ±1 — на месте соседа. Берётся из реального смещения,
                            // поэтому поворот едет вместе с пальцем.
                            val d = centerOffset(state, index).coerceIn(-1.6f, 1.6f)
                            val k = abs(d)
                            rotationY = -d * 24f
                            scaleX = 1f - 0.10f * k
                            scaleY = 1f - 0.10f * k
                            alpha = (1f - 0.45f * k).coerceIn(0.25f, 1f)
                            cameraDistance = 14f * density
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val depth = stack(item).coerceIn(0, 2)
                    StackSheets(depth, cardH)
                    DeckCard(
                        height = cardH,
                        focused = focused,
                        interactive = interactive && focused,
                        onTap = {
                            if (focused) {
                                Feel.tick()
                                onOpen(item)
                            } else {
                                scope.launch { state.animateScrollToItem(index) }
                            }
                        },
                        onHold = if (focused) onHold?.let { h -> { h(item) } } else null
                    ) { card(item, focused) }
                }
            }
        }
    }

    Pager(center, items.size, hint)
}

private val CARD_WIDTH = 0.80f
private val STACK_ROOM = 18.dp

/** Индекс карточки, которая ближе всех к центру. */
private fun centerIndex(state: LazyListState): Int {
    val info = state.layoutInfo
    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - mid) }?.index
        ?: state.firstVisibleItemIndex
}

/** Смещение карточки от центра в ширинах карточки. */
private fun centerOffset(state: LazyListState, index: Int): Float {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return (index - state.firstVisibleItemIndex).toFloat()
    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val step = (item.size + info.mainAxisItemSpacing).coerceAtLeast(1)
    return (item.offset + item.size / 2f - mid) / step
}

/**
 * Края нижних листов над карточкой. Стопка не украшение: по ней видно,
 * что внутри лежит ещё слой, и это читается без единого слова.
 */
@Composable
private fun BoxScope.StackSheets(depth: Int, cardH: Dp) {
    if (depth <= 0) return
    for (i in depth downTo 1) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(7 * i).dp)
                .padding(horizontal = (14 * i).dp)
                .fillMaxWidth()
                .height(cardH)
                .clip(RoundedCornerShape(T.rCard))
                .background(if (i == 1) Color(0xFF1E282B) else Color(0xFF172023))
                .border(1.dp, T.panelEdge.copy(alpha = 0.18f), RoundedCornerShape(T.rCard))
        )
    }
}

/**
 * Подложка карточки. Кромка ярче у центральной: глаз находит фокус без
 * подписи. Внутри — тап, удержание с кольцом под пальцем и отклик сжатием.
 */
@Composable
private fun DeckCard(
    height: Dp,
    focused: Boolean,
    interactive: Boolean,
    onTap: () -> Unit,
    onHold: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit
) {
    val edge by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "edge"
    )
    val shape = RoundedCornerShape(T.rCard)
    val base = Modifier
        .fillMaxWidth()
        .height(height)
        .clip(shape)
        .background(if (focused) Color(0xFF1B2528) else Color(0xFF141C1F))
        .border(
            width = 1.dp,
            color = Color.White.copy(alpha = 0.10f + 0.22f * edge),
            shape = shape
        )

    if (interactive) {
        // Раскрытая запись: касания принадлежат её кнопкам и прокрутке.
        Box(base, content = content)
    } else {
        // Содержимое неинтерактивной карточки — картинка: тап и удержание
        // ловит подложка, даже если внутри нарисованы кнопки соседа.
        HoldSurface(base, onTap = onTap, onHold = onHold, content = content)
    }
}

/** Сколько держать палец, чтобы сработало быстрое действие. */
private const val HOLD_MS = 560
private const val HOLD_DELAY = 160L

/**
 * Поверхность с удержанием.
 *
 * Пока палец лежит, из-под него расходится кольцо и замыкается по кругу:
 * видно, что что-то происходит и сколько осталось. Отпустил раньше — кольцо
 * втягивается, действия нет. Замкнулось — удар в палец и действие. Так
 * подтверждается то, что не хочется сделать случайно тапом.
 */
@Composable
private fun HoldSurface(
    modifier: Modifier,
    onTap: () -> Unit,
    onHold: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val tapNow by rememberUpdatedState(onTap)
    val holdNow by rememberUpdatedState(onHold)
    val progress = remember { Animatable(0f) }
    var at by remember { mutableStateOf(Offset.Zero) }
    var pressed by remember { mutableStateOf(false) }
    val fired = remember { booleanArrayOf(false) }
    val ring = T.action.fill
    val ringPx = with(LocalDensity.current) { 30.dp.toPx() }
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }

    val squeeze by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "squeeze"
    )

    Box(
        modifier
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Ранний проход: подложка видит касание раньше того, что
                    // нарисовано внутри, и может погасить его отпускание.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    at = down.position
                    pressed = true
                    fired[0] = false
                    val hold = holdNow
                    val job: Job? = if (hold == null) null else scope.launch {
                        delay(HOLD_DELAY)
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        fired[0] = true
                        Feel.confirm()
                        progress.snapTo(0f)
                        hold()
                    }
                    var tap = true
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null) { tap = false; break }
                        if (!ch.pressed) {
                            // Отпускание гасим: кнопки внутри соседней
                            // карточки не должны срабатывать.
                            ch.consume()
                            break
                        }
                        // Палец поехал — это листание ленты, а не тап.
                        if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            tap = false
                            break
                        }
                    }
                    pressed = false
                    job?.cancel()
                    if (!fired[0] && progress.value > 0f) {
                        scope.launch { progress.animateTo(0f, tween(T.MS_EXIT, easing = T.curve)) }
                    }
                    if (tap && !fired[0]) tapNow()
                }
            }
            .drawWithContent {
                drawContent()
                val p = progress.value
                if (p > 0f) {
                    val r = ringPx * (0.7f + 0.3f * p)
                    drawCircle(ring.copy(alpha = 0.18f * p), radius = r * 1.6f, center = at)
                    drawCircle(Color.White.copy(alpha = 0.10f), radius = r, center = at, style = Stroke(strokePx))
                    drawArc(
                        color = ring,
                        startAngle = -90f,
                        sweepAngle = 360f * p,
                        useCenter = false,
                        topLeft = Offset(at.x - r, at.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(strokePx, cap = StrokeCap.Round)
                    )
                }
            },
        content = content
    )
}

/**
 * Где ты в ленте: точки, если карточек немного, и полоска, если много.
 * Под ними — одна строка о жестах этого слоя.
 */
@Composable
private fun Pager(index: Int, total: Int, hint: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = T.md, bottom = T.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (total in 2..12) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(total) { i ->
                    val on = i == index
                    val w by animateFloatAsState(
                        targetValue = if (on) 18f else 6f,
                        animationSpec = tween(T.MS_STATE, easing = T.curve),
                        label = "dot"
                    )
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(w.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (on) T.textOnDark else Color.White.copy(alpha = 0.22f))
                    )
                }
            }
        } else if (total > 12) {
            val frac by animateFloatAsState(
                targetValue = (index + 1f) / total,
                animationSpec = tween(T.MS_STATE, easing = T.curve),
                label = "track"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(T.textOnDark)
                    )
                }
                Spacer(Modifier.width(T.sm))
                Q("${index + 1} / $total", Type.label, T.text2OnDark, 1)
            }
        }
        Spacer(Modifier.height(T.sm))
        Q(hint, Type.label, T.text2OnDark.copy(alpha = 0.7f), 1)
    }
}

@Composable
private fun ColumnScope.DeckEmpty() {
    Box(Modifier.weight(1f).fillMaxWidth().padding(T.xl), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            QIcon(Ic.layers, size = 40.dp, tint = T.text2OnDark, stroke = 1.5f)
            Spacer(Modifier.height(T.md))
            Q("Здесь пока пусто", Type.heading, T.textOnDark)
            Spacer(Modifier.height(T.xs))
            Q("Плюс вверху — первая запись", Type.small, T.text2OnDark)
        }
    }
}

// --- общие куски карточек -------------------------------------------------

/** Знак в квадрате. Цвет — только у центральной: акцент на экране один. */
@Composable
private fun Badge(icon: String, focused: Boolean, size: Dp = 56.dp, tone: Color? = null) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(T.rIcon + 2.dp))
            .background(
                (tone ?: Color.White).copy(alpha = if (tone != null) 0.18f else if (focused) 0.10f else 0.06f)
            ),
        contentAlignment = Alignment.Center
    ) {
        QIcon(
            icon,
            size = size * 0.46f,
            tint = tone ?: if (focused) T.textOnDark else T.text2OnDark,
            stroke = 1.9f
        )
    }
}

/** «Открыть ›» внизу центральной карточки — куда ведёт тап. */
@Composable
private fun OpenCue(label: String, focused: Boolean) {
    val a by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "cue"
    )
    Row(Modifier.graphicsLayer { alpha = a }, verticalAlignment = Alignment.CenterVertically) {
        Q(label, Type.small, T.action.fill, 1)
        Spacer(Modifier.width(T.xs))
        QIcon(Ic.chevronRight, size = 16.dp, tint = T.action.fill, stroke = 2.2f)
    }
}

/**
 * Полоса из долей: стадии договоров, отделы. Её не читают по делениям —
 * она разбивает текст и даёт глазу форму: много ли красного, есть ли хвост.
 */
@Composable
private fun ShareBar(parts: List<Pair<Int, Color>>, height: Dp = 8.dp) {
    val total = parts.sumOf { it.first }
    if (total <= 0) return
    Row(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(percent = 50)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        parts.filter { it.first > 0 }.forEach { (n, c) ->
            Box(
                Modifier
                    .weight(n.toFloat())
                    .fillMaxHeight()
                    .background(c)
            )
        }
    }
}

/** Доля от целого одной полоской: оплачено, готовность. */
@Composable
private fun Meter(label: String, value: String, frac: Float, tone: Color) {
    val f by animateFloatAsState(
        targetValue = frac.coerceIn(0f, 1f),
        animationSpec = tween(T.MS_SCREEN, easing = T.curve),
        label = "meter"
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Q(label.uppercase(), Type.label, T.text2OnDark, 1, Modifier.weight(1f))
            Q(value, Type.small, T.textOnDark, 1)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.10f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(tone)
            )
        }
    }
}

/** Легенда полосы: цвет и слово, не больше трёх. */
@Composable
private fun Legend(parts: List<Triple<String, Int, Color>>) {
    Column(Modifier.fillMaxWidth()) {
        parts.filter { it.second > 0 }.take(4).forEach { (name, n, c) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(percent = 50)).background(c))
                Spacer(Modifier.width(T.sm))
                Q(name, Type.small, T.text2OnDark, 1, Modifier.weight(1f))
                Q(n.toString(), Type.small, T.textOnDark, 1)
            }
        }
    }
}

private fun stageColor(stage: Stage): Color = when (stage) {
    Stage.LEAD -> Color(0xFF5B8DB0)
    Stage.CONTRACT -> Color(0xFFB88A5A)
    Stage.PRODUCTION -> T.action.fill
    Stage.ACCEPTANCE -> Color(0xFFD9B24A)
    Stage.PAYMENT -> T.success.fill
    Stage.PROBLEM -> T.dangerTone.fill
}

/** Стадии открытых договоров — для полосы на полке и в пачках. */
private fun stageParts(db: Db): List<Triple<String, Int, Color>> {
    val open = db.liveContracts.filterNot { it.archived }
    return Stage.entries.map { st ->
        Triple(st.label, open.count { shownStatus(it).stage == st }, stageColor(st))
    }
}

// --- слой 0: полка --------------------------------------------------------

/** На полке первая карточка — не раздел, а состояние дел. */
private const val SUMMARY = "summary"

@Composable
private fun ColumnScope.ShelfDeck(db: Db, host: OverlayHost) {
    val items = remember { listOf(SUMMARY) + Section.entries.map { it.name } }
    val hot = remember(db) { hotCounts(db) }

    Carousel(
        items = items,
        memo = "shelf",
        key = { it },
        onOpen = { id ->
            val sec = Section.entries.firstOrNull { it.name == id } ?: Section.CONTRACTS
            OverlayState.openSection(sec)
        },
        onHold = { id ->
            Section.entries.firstOrNull { it.name == id }?.let { host.openForm(it, null) }
        },
        hint = "Листай вбок · тап — открыть · удерживай — новая запись",
        stack = { id -> if (id == SUMMARY) 0 else db.count(Section.valueOf(id)).coerceAtMost(2) }
    ) { id, focused ->
        if (id == SUMMARY) SummaryFace(db, focused)
        else SectionFace(db, Section.valueOf(id), hot[Section.valueOf(id)] ?: 0, focused)
    }
}

@Composable
private fun SectionFace(db: Db, section: Section, hot: Int, focused: Boolean) {
    val count = db.count(section)
    Column(Modifier.fillMaxSize().padding(T.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(sectionIcon(section), focused)
            Spacer(Modifier.weight(1f))
            if (hot > 0) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(T.dangerTone.fill.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Q("горит $hot", Type.label, T.dangerTone.fill, 1) }
            }
        }
        Spacer(Modifier.weight(1f))
        Q(count.toString(), Type.hero, T.textOnDark, 1)
        Q(section.title, Type.title, T.textOnDark, 1)
        Spacer(Modifier.height(T.lg))

        when (section) {
            Section.CONTRACTS -> {
                val parts = remember(db) { stageParts(db) }
                ShareBar(parts.map { it.second to it.third })
                Spacer(Modifier.height(T.md))
                Legend(parts.sortedByDescending { it.second }.take(3))
            }
            Section.SITES -> {
                val parts = remember(db) {
                    Stage.entries.map { st ->
                        Triple(st.label, db.liveSites.count { db.stageOfSite(it.id) == st }, stageColor(st))
                    }
                }
                ShareBar(parts.map { it.second to it.third })
                Spacer(Modifier.height(T.md))
                Legend(parts.sortedByDescending { it.second }.take(3))
            }
            Section.STAFF -> {
                val parts = remember(db) {
                    db.liveEmployees.groupBy { it.department.ifBlank { "Без отдела" } }
                        .map { it.key to it.value.size }
                        .sortedByDescending { it.second }
                }
                val shades = listOf(0.85f, 0.6f, 0.42f, 0.3f, 0.22f, 0.16f)
                val colored = parts.mapIndexed { i, (n, c) ->
                    Triple(n, c, Color.White.copy(alpha = shades.getOrElse(i) { 0.12f }))
                }
                ShareBar(colored.map { it.second to it.third })
                Spacer(Modifier.height(T.md))
                Legend(colored.take(3))
            }
            Section.CUSTOMERS -> {
                val top = remember(db) {
                    db.liveCustomers.map { it.name to db.sitesOfCustomer(it.id).size }
                        .sortedByDescending { it.second }
                        .take(3)
                }
                Legend(top.map { Triple(it.first.ifBlank { "Без названия" }, it.second, T.text2OnDark) })
            }
        }
        Spacer(Modifier.height(T.lg))
        OpenCue("Открыть", focused)
    }
}

/**
 * Сводка — первое, что видно при открытии: сколько в работе, что горит,
 * сколько денег ждёт. Одно число крупно, остальное слабее.
 */
@Composable
private fun SummaryFace(db: Db, focused: Boolean) {
    val s = remember(db) { db.summary() }
    val parts = remember(db) { stageParts(db) }
    Column(Modifier.fillMaxSize().padding(T.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(Ic.summary, focused, tone = T.action.fill)
            Spacer(Modifier.width(T.md))
            Column {
                Q("Сводка", Type.heading, T.textOnDark, 1)
                Q("НА СЕГОДНЯ", Type.label, T.text2OnDark, 1)
            }
        }
        Spacer(Modifier.weight(1f))
        Q(s.inWork.toString(), Type.hero, T.textOnDark, 1)
        Q("договоров в работе", Type.small, T.text2OnDark, 1)
        Spacer(Modifier.height(T.lg))
        ShareBar(parts.map { it.second to it.third })
        Spacer(Modifier.height(T.lg))

        if (s.contracted > 0) {
            val paid = (s.contracted - s.rest).coerceAtLeast(0)
            Meter(
                label = "Получено",
                value = money(paid),
                frac = paid.toFloat() / s.contracted.toFloat(),
                tone = T.success.fill
            )
            Spacer(Modifier.height(T.md))
        }
        Row(Modifier.fillMaxWidth()) {
            MiniStat("Просрочено", s.overdue.toString(), if (s.overdue > 0) T.dangerTone.fill else T.textOnDark, Modifier.weight(1f))
            MiniStat("К оплате", s.awaitingPay.toString(), T.textOnDark, Modifier.weight(1f))
            MiniStat("Лиды", s.potential.toString(), T.textOnDark, Modifier.weight(1f))
        }
        if (s.soon.isNotEmpty()) {
            Spacer(Modifier.height(T.md))
            s.soon.take(2).forEach { (label, due) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    QIcon(Ic.clock, size = 14.dp, tint = T.text2OnDark, stroke = 2f)
                    Spacer(Modifier.width(T.sm))
                    Q(label, Type.small, T.text2OnDark, 1, Modifier.weight(1f))
                    Q(due, Type.small, T.textOnDark, 1)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Q(value, Type.amount, color, 1)
        Q(label.uppercase(), Type.label, T.text2OnDark, 1)
    }
}

/** Что в разделе горит: просроченные договоры и объекты с ними. */
private fun hotCounts(db: Db): Map<Section, Int> {
    val overdue = db.liveContracts.filter { shownStatus(it) == Status.OVERDUE }
    return mapOf(
        Section.CONTRACTS to overdue.size,
        Section.SITES to overdue.mapNotNull { it.siteId }.filter { it.isNotBlank() }.distinct().size,
        Section.CUSTOMERS to 0,
        Section.STAFF to 0
    )
}

// --- слой 1: пачки --------------------------------------------------------

/**
 * Пачка — промежуточный слой между разделом и записями. Полсотни договоров
 * одной лентой не читаются: договоры разложены по стадиям, закрытое уходит
 * в «Архив» последним, сотрудники — по отделам, объекты — по заказчикам.
 */
private data class Pack(val name: String, val count: Int, val color: Color?, val archive: Boolean)

private const val ARCHIVE = "Архив"

private fun packsTitle(section: Section): String = when (section) {
    Section.CONTRACTS -> "По стадиям"
    Section.STAFF -> "По отделам"
    Section.SITES -> "По заказчикам"
    Section.CUSTOMERS -> ""
}

private fun groupOf(db: Db, section: Section, id: String): String? = when (section) {
    Section.SITES -> db.site(id)?.let { s -> db.customer(s.customerId)?.name ?: "Без заказчика" }
    Section.CONTRACTS -> db.contract(id)?.let { c -> if (c.archived) ARCHIVE else shownStatus(c).stage.label }
    Section.STAFF -> db.employee(id)?.department?.ifBlank { "Без отдела" }
    Section.CUSTOMERS -> null
}

private fun idsOf(db: Db, section: Section): List<String> = when (section) {
    Section.SITES -> db.liveSites.map { it.id }
    Section.CONTRACTS -> db.liveContracts.map { it.id }
    Section.CUSTOMERS -> db.liveCustomers.map { it.id }
    Section.STAFF -> db.liveEmployees.map { it.id }
}

private fun packsOf(db: Db, section: Section): List<Pack> {
    val counts = LinkedHashMap<String, Int>()
    idsOf(db, section).forEach { id ->
        val g = groupOf(db, section, id) ?: return@forEach
        counts[g] = (counts[g] ?: 0) + 1
    }
    if (counts.isEmpty()) return emptyList()
    val order = when (section) {
        Section.CONTRACTS -> Stage.entries.map { it.label } + listOf(ARCHIVE)
        else -> counts.entries.sortedByDescending { it.value }.map { it.key }
    }
    return order.filter { counts.containsKey(it) }.map { name ->
        val stage = Stage.entries.firstOrNull { it.label == name }
        Pack(
            name = name,
            count = counts[name] ?: 0,
            color = if (section == Section.CONTRACTS && stage != null) stageColor(stage) else null,
            archive = name == ARCHIVE
        )
    }
}

@Composable
private fun ColumnScope.PackDeck(db: Db, host: OverlayHost, section: Section, packs: List<Pack>) {
    val total = packs.sumOf { it.count }.coerceAtLeast(1)
    Carousel(
        items = packs,
        memo = "packs:$section",
        key = { it.name },
        onOpen = { OverlayState.openGroup(it.name) },
        onHold = { host.openForm(section, null) },
        hint = "Тап — открыть пачку · удерживай — новая запись",
        stack = { it.count.coerceAtMost(2) }
    ) { pack, focused ->
        Column(Modifier.fillMaxSize().padding(T.xl)) {
            Badge(
                icon = when {
                    pack.archive -> Ic.archive
                    section == Section.STAFF -> departmentIcon(pack.name)
                    else -> sectionIcon(section)
                },
                focused = focused,
                tone = pack.color
            )
            Spacer(Modifier.weight(1f))
            Q(pack.count.toString(), Type.hero, T.textOnDark, 1)
            Q(pack.name, Type.title, T.textOnDark, 3)
            Spacer(Modifier.height(T.lg))
            Meter(
                label = "Доля раздела",
                value = "${pack.count * 100 / total} %",
                frac = pack.count.toFloat() / total,
                tone = pack.color ?: T.textOnDark
            )
            if (pack.archive) {
                Spacer(Modifier.height(T.md))
                Q("Оплаченные, отказы и расторжения", Type.small, T.text2OnDark, 2)
            }
            Spacer(Modifier.height(T.lg))
            OpenCue("Открыть пачку", focused)
        }
    }
}

// --- слой 2: записи -------------------------------------------------------

/** Что показать на лицевой стороне записи — одинаково для всех разделов. */
private data class Face(
    val id: String,
    val title: String,
    val subtitle: String,
    val stage: Stage?,
    val warn: Boolean,
    val big: String,
    val icon: String,
    val meter: Triple<String, String, Float>?,
    val facts: List<Pair<String, String>>,
    val tasks: Int = 0
)

private fun facesOf(db: Db, section: Section, group: String?): List<Face> {
    val all = when (section) {
        Section.SITES -> db.liveSites.map { s ->
            val active = db.activeContractsOfSite(s.id).size
            Face(
                id = s.id,
                title = s.name.ifBlank { "Без названия" },
                subtitle = db.customer(s.customerId)?.name.orEmpty(),
                stage = db.stageOfSite(s.id),
                warn = false,
                big = if (active > 0) "$active " + plural(active.toLong(), "договор", "договора", "договоров") else "",
                icon = buildingIcon(s.buildingType),
                meter = Triple("Готовность", "${s.progress} %", s.progress / 100f),
                facts = listOf("Адрес" to s.address, "Тип" to s.buildingType)
            )
        }

        Section.CONTRACTS -> db.liveContracts.map { c ->
            val st = shownStatus(c)
            val share = c.paidShare.let { if (it > 1.0) it / 100.0 else it }.toFloat()
            Face(
                id = c.id,
                title = c.workKind.ifBlank { c.code.ifBlank { "Без вида работ" } },
                subtitle = db.site(c.siteId)?.name.orEmpty(),
                stage = st.stage,
                warn = st == Status.OVERDUE,
                big = if (c.amount != 0L) money(c.amount) else "",
                icon = departmentIcon(db.refs.departmentOf(c.workKind)),
                meter = if (c.amount != 0L) Triple("Оплачено", "${(share * 100).toInt()} %", share) else null,
                facts = listOf(
                    "Срок" to (c.end.takeIf { it.isNotBlank() }?.let { dateShort(it) } ?: ""),
                    "Ответственный" to c.responsible
                ),
                tasks = c.openTasks
            )
        }

        Section.CUSTOMERS -> db.liveCustomers.map { p ->
            val sites = db.sitesOfCustomer(p.id).size
            Face(
                id = p.id,
                title = p.name.ifBlank { "Без названия" },
                subtitle = if (p.inn.isNotBlank()) "ИНН ${p.inn}" else "",
                stage = null,
                warn = false,
                big = if (sites > 0) "$sites " + plural(sites.toLong(), "объект", "объекта", "объектов") else "",
                icon = Ic.customers,
                meter = null,
                facts = emptyList()
            )
        }

        Section.STAFF -> db.liveEmployees.map { e ->
            val n = db.liveContracts.count { it.responsible.equals(e.name, true) && !it.archived }
            Face(
                id = e.id,
                title = e.name.ifBlank { "Без имени" },
                subtitle = listOf(e.position, e.department).filter { it.isNotBlank() }.joinToString(" · "),
                stage = null,
                warn = false,
                big = if (n > 0) "$n " + plural(n.toLong(), "договор", "договора", "договоров") else "",
                icon = departmentIcon(e.department),
                meter = null,
                facts = emptyList()
            )
        }
    }
    val filtered = if (group == null) all else all.filter { groupOf(db, section, it.id) == group }
    return filtered.sortedWith(compareByDescending<Face> { it.warn }.thenBy { it.title.lowercase() })
}

@Composable
private fun ColumnScope.ItemDeck(db: Db, host: OverlayHost, section: Section, group: String?) {
    val faces = remember(db, section, group) { facesOf(db, section, group) }
    Carousel(
        items = faces,
        memo = "items",
        key = { it.id },
        onOpen = { OverlayState.openCard(CardRef(section, it.id)) },
        onHold = { host.openForm(section, it.id) },
        hint = "Тап — открыть карточку · удерживай — изменить"
    ) { face, focused -> FaceView(face, focused) }
}

/**
 * Лицевая сторона записи: знак, имя, одна подпись, одна большая цифра и
 * одна полоса. Подробности — слоем глубже, по тапу.
 */
@Composable
private fun FaceView(face: Face, focused: Boolean) {
    Column(Modifier.fillMaxSize().padding(T.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Badge(face.icon, focused, size = 48.dp, tone = face.stage?.let { stageColor(it) })
            Spacer(Modifier.weight(1f))
            if (face.tasks > 0) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QIcon(Ic.task, size = 12.dp, tint = T.text2OnDark, stroke = 2.2f)
                    Spacer(Modifier.width(4.dp))
                    Q(face.tasks.toString(), Type.label, T.text2OnDark, 1)
                }
                Spacer(Modifier.width(T.xs))
            }
            face.stage?.let { st ->
                val c = if (face.warn) T.dangerTone.fill else stageColor(st)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(c.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Q(if (face.warn) "Просрочен" else st.short, Type.label, c, 1) }
            }
        }
        Spacer(Modifier.height(T.lg))
        Q(face.title, Type.title, T.textOnDark, 3)
        if (face.subtitle.isNotBlank()) {
            Spacer(Modifier.height(T.xs))
            Q(face.subtitle, Type.small, T.text2OnDark, 2)
        }

        Spacer(Modifier.weight(1f))
        if (face.big.isNotBlank()) {
            Q(face.big, Type.title, T.textOnDark, 1)
            Spacer(Modifier.height(T.md))
        }
        face.meter?.let { (label, value, frac) ->
            Meter(label, value, frac, face.stage?.let { stageColor(it) } ?: T.textOnDark)
            Spacer(Modifier.height(T.md))
        }
        face.facts.filter { it.second.isNotBlank() }.take(2).forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Q(k.uppercase(), Type.label, T.text2OnDark, 1, Modifier.width(112.dp))
                Q(v, Type.small, T.textOnDark, 2, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(T.md))
        OpenCue("Открыть карточку", focused)
    }
}

// --- слой 3: раскрытая запись ---------------------------------------------

/**
 * Раскрытая запись — тоже карточка в ленте: соседей по пачке видно по
 * краям, и к ним можно перелистнуть, не возвращаясь назад.
 */
@Composable
private fun ColumnScope.CardDeck(db: Db, host: OverlayHost, section: Section, group: String?) {
    val current = OverlayState.card ?: return
    val faces = remember(db, section, group) { facesOf(db, section, group) }
    // Пришли по ссылке из другой карточки, а в пачке этой записи нет —
    // показываем её одну, чтобы не терять.
    val startKey = remember { current.id }
    val ids = remember(faces, startKey) {
        faces.map { it.id }.let { if (startKey in it) it else listOf(startKey) }
    }
    remember(startKey) { OverlayState.focus.put("card", startKey) }

    Carousel(
        items = ids,
        memo = "card",
        key = { it },
        onCenter = { OverlayState.focusCard(CardRef(section, it)) },
        onOpen = { },
        hint = "Листай вбок — соседние записи",
        interactive = true
    ) { id, focused ->
        Column(Modifier.fillMaxSize().padding(top = T.lg, bottom = T.md)) {
            when (section) {
                Section.SITES -> db.site(id)?.let { SiteBody(it, db, host) } ?: Gone()
                Section.CONTRACTS -> db.contract(id)?.let { ContractBody(it, db, host) } ?: Gone()
                Section.CUSTOMERS -> db.customer(id)?.let { PartyBody(it, db, host) } ?: Gone()
                Section.STAFF -> db.employee(id)?.let { StaffBody(it, db, host) } ?: Gone()
            }
        }
        if (!focused) {
            // Соседняя раскрытая карточка — картинка: тап подводит её в центр.
            Box(Modifier.fillMaxSize().background(Color(0x66070C0E)))
        }
    }
}

@Composable
private fun Gone() {
    Column(
        Modifier.fillMaxWidth().padding(T.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Q("Запись не найдена", Type.body, T.textOnDark)
        Spacer(Modifier.height(T.xs))
        Q("Возможно, её удалили в таблице", Type.small, T.text2OnDark)
    }
}
