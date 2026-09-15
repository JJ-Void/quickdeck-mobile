package ru.quickdeck.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.quickdeck.mobile.data.Stage
import ru.quickdeck.mobile.data.Status

/** Цвет статуса берётся от стадии: сорок один оттенок никто не различит. */
fun Stage.tone(): T.Tone = when (this) {
    Stage.LEAD -> T.info
    Stage.CONTRACT -> T.accent
    Stage.PRODUCTION -> T.accent
    Stage.ACCEPTANCE -> T.warning
    Stage.PAYMENT -> T.success
    Stage.PROBLEM -> T.danger
}

fun Status.tone(): T.Tone = stage.tone()

/** Статус — всегда плашка с текстом. Цвет один не носит смысла. */
@Composable
fun StatusChip(status: Status, modifier: Modifier = Modifier, short: Boolean = false) {
    val tone = status.tone()
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tone.chip)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Q(if (short) status.stage.short else status.label, Type.caption, tone.ink, 1)
    }
}

@Composable
fun StageChip(stage: Stage, modifier: Modifier = Modifier) {
    val tone = stage.tone()
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tone.chip)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Q(stage.short, Type.caption, tone.ink, 1)
    }
}

/** Нажатие — уменьшение до 0.96, без ряби и без цветовой анимации. */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) T.PRESS_SCALE else 1f,
        animationSpec = tween(T.MS_PRESS, easing = T.curve),
        label = "press"
    )
    Box(
        modifier
            .scale(s)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        content = content
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Pressable(onClick, modifier.fillMaxWidth(), enabled) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = T.touchMin)
                .clip(RoundedCornerShape(T.rControl))
                .background(if (enabled) T.accent.fill else T.muted.chip),
            contentAlignment = Alignment.Center
        ) { Q(text, Type.heading, if (enabled) Color.White else T.text3) }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Pressable(onClick, modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = T.touchMin)
                .clip(RoundedCornerShape(T.rControl))
                .background(T.muted.chip)
                .padding(horizontal = T.lg),
            contentAlignment = Alignment.Center
        ) { Q(text, Type.heading, T.text2) }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(T.hairline))
}

/**
 * Поле ввода. Подсказка рисуется внутри декорации, а не слоем поверх, иначе
 * она перекрывает курсор. Рамка подсвечивается по фокусу — это единственный
 * способ понять, куда сейчас попадут буквы, когда полей на экране восемь.
 */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    hint: String? = null,
    warn: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val line = when {
        warn -> T.danger.fill
        focused -> T.accent.fill
        else -> T.hairline
    }

    Column(modifier.fillMaxWidth()) {
        Q(label, Type.caption, if (warn) T.danger.ink else if (focused) T.accent.ink else T.text3)
        Spacer(Modifier.height(T.xs))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = Type.body.copy(color = T.text),
            cursorBrush = SolidColor(T.accent.fill),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                imeAction = if (singleLine) imeAction else ImeAction.Default
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (singleLine) T.touchMin else 88.dp)
                        .clip(RoundedCornerShape(T.rControl))
                        .background(T.surface)
                        .border(
                            width = if (focused || warn) 1.5.dp else 1.dp,
                            color = line,
                            shape = RoundedCornerShape(T.rControl)
                        )
                        .padding(horizontal = T.md, vertical = 14.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Q(placeholder, Type.body, T.text3, 1)
                    }
                    inner()
                }
            }
        )
        if (hint != null) {
            Spacer(Modifier.height(T.xs))
            Q(hint, Type.caption, if (warn) T.danger.ink else T.text3)
        }
    }
}

/** Строка выбора: показывает, что выбрано, и открывает список поверх формы. */
@Composable
fun PickerRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null
) {
    Column(modifier.fillMaxWidth()) {
        // Пустая подпись — строка идёт без неё: так выглядят повторяющиеся
        // строки внутри одного блока, например соисполнители.
        if (label.isNotBlank()) {
            Q(label, Type.caption, T.text3)
            Spacer(Modifier.height(T.xs))
        }
        Pressable(onClick, Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = T.touchMin)
                    .clip(RoundedCornerShape(T.rControl))
                    .background(T.surface)
                    .border(1.dp, T.hairline, RoundedCornerShape(T.rControl))
                    .padding(horizontal = T.md, vertical = T.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Q(
                        value?.takeIf { it.isNotBlank() } ?: "Выбрать",
                        Type.body,
                        if (value.isNullOrBlank()) T.text3 else T.text,
                        1
                    )
                    if (sub != null && !value.isNullOrBlank()) {
                        Q(sub, Type.caption, T.text3, 1)
                    }
                }
                QIcon(Ic.chevronRight, size = 20.dp, tint = T.text3)
            }
        }
    }
}

/**
 * Выбор статуса в две ступени: сначала стадия, потом статус внутри неё.
 * Сорок один статус одной лентой не читается, а стадий всего шесть.
 */
@Composable
fun StatusPicker(value: Status, onPick: (Status) -> Unit) {
    var stage by remember(value) { mutableStateOf(value.stage) }

    Column(Modifier.fillMaxWidth()) {
        Q("Стадия", Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(T.sm)
        ) {
            Stage.entries.forEach { s ->
                val selected = s == stage
                Pressable({ stage = s }) {
                    Box(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (selected) s.tone().chip else T.surface)
                            .border(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) s.tone().fill else T.hairline,
                                RoundedCornerShape(percent = 50)
                            )
                            .padding(horizontal = T.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Q(s.label, Type.caption, if (selected) s.tone().ink else T.text2, 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(T.md))
        Q("Статус", Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
        val list = Status.byStage(stage)
        WheelPicker(
            items = list,
            selected = if (value in list) value else list.firstOrNull(),
            label = { it.label },
            onSelect = onPick,
            accent = { it.tone().fill }
        )
    }
}

/**
 * Колесо выбора: выбранное значение стоит в центре, соседние видны выше и
 * ниже, лента крутится с инерцией и прилипает к позиции.
 *
 * Обычный список тут проигрывает: в нём выбранное надо искать глазами и
 * попадать по нему пальцем. В колесе попадать некуда — крутишь до нужного,
 * и значение уже выбрано. Тот же контрол, что в оверлее, только там лентой
 * управляет ведение от пузыря, а здесь — обычная прокрутка.
 */
@Composable
fun <E> WheelPicker(
    items: List<E>,
    selected: E?,
    label: (E) -> String,
    onSelect: (E) -> Unit,
    modifier: Modifier = Modifier,
    accent: (E) -> Color = { T.accent.fill },
    rowHeight: Dp = 44.dp,
    visibleRows: Int = 5
) {
    if (items.isEmpty()) return

    val state = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val pad = rowHeight * ((visibleRows - 1) / 2)

    // Центральная строка: та, к которой ближе всего остановилась лента.
    val centerIndex by remember {
        derivedStateOf {
            val first = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            val height = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            (first + if (offset > height / 2) 1 else 0).coerceIn(0, items.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        val start = items.indexOf(selected).takeIf { it >= 0 } ?: 0
        state.scrollToItem(start)
    }

    // Смена центра — это и есть выбор: отдельного нажатия не нужно.
    LaunchedEffect(centerIndex) {
        val item = items.getOrNull(centerIndex) ?: return@LaunchedEffect
        if (item != selected) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(item)
        }
    }

    Box(modifier.fillMaxWidth().height(rowHeight * visibleRows)) {
        // Линза: неподвижная рамка, показывающая центр ещё до прокрутки.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(rowHeight)
                .clip(RoundedCornerShape(T.rControl))
                .background(items.getOrNull(centerIndex)?.let { accent(it) }?.copy(alpha = 0.10f) ?: T.surface)
                .border(1.5.dp, items.getOrNull(centerIndex)?.let { accent(it) } ?: T.hairline, RoundedCornerShape(T.rControl))
        )

        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = pad),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, item ->
                val away = kotlin.math.abs(index - centerIndex)
                val center = index == centerIndex
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .alpha(if (center) 1f else (1f - 0.26f * away).coerceIn(0.3f, 1f))
                        .padding(horizontal = T.md),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Q(
                        label(item),
                        if (center) Type.body else Type.small,
                        if (center) T.text else T.text2,
                        1
                    )
                }
            }
        }
    }
}

// --- поиск в списках ------------------------------------------------------

/**
 * Отбор по подстроке, без учёта регистра и порядка слов: «ильин пто» найдёт
 * «Ильинов Генадий Ильич · ОТДЕЛ ПТО». Пустой запрос ничего не отсеивает.
 */
fun <E> filtered(list: List<E>, query: String, text: (E) -> String): List<E> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return list
    return list.filter { item ->
        val hay = text(item).lowercase()
        words.all { hay.contains(it) }
    }
}

/** Строка поиска над длинным списком. Появляется, только когда есть что искать. */
@Composable
fun SearchBox(query: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = T.lg, vertical = T.xs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = T.touchMin)
                .clip(RoundedCornerShape(T.rControl))
                .background(T.surface)
                .border(1.dp, T.hairline, RoundedCornerShape(T.rControl))
                .padding(horizontal = T.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QIcon(Ic.search, size = 18.dp, tint = T.text3)
            Spacer(Modifier.width(T.sm))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Q("Поиск", Type.body, T.text3, 1)
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = Type.body.copy(color = T.text),
                    cursorBrush = SolidColor(T.accent.fill),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Pressable({ onChange("") }) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        QIcon(Ic.close, size = 16.dp, tint = T.text3, stroke = 2f)
                    }
                }
            }
        }
    }
}

// --- отправка -------------------------------------------------------------

/** Что сейчас с отправкой. Одно состояние на кнопку, без своих флагов по месту. */
enum class SendPhase { IDLE, SENDING, SENT, FAILED }

/**
 * Состояние отправки карточки. Живёт рядом с экраном, переживает перерисовку
 * и не даёт нажать второй раз, пока первая отправка не закончилась.
 */
@Stable
class SendState {
    var phase by mutableStateOf(SendPhase.IDLE)
        internal set

    val busy: Boolean get() = phase == SendPhase.SENDING
}

@Composable
fun rememberSendState(): SendState = remember { SendState() }

/**
 * Кнопка отправки карточки: один механизм на приложение и на оверлей.
 *
 * Отправляет не «текст вообще», а готовую карточку — её собирает вызывающий,
 * из тех же данных, что показаны на экране. Результат берётся из ответа
 * системы (ушло ли в выбранное приложение), а не из того, что мы нажали:
 * если делиться нечем, кнопка честно скажет об этом и даст повторить.
 *
 * Вибро — только на успехе: это подтверждение, а не аккомпанемент.
 */
@Composable
fun SendButton(
    label: String,
    state: SendState,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    send: () -> Boolean
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val tone = when (state.phase) {
        SendPhase.SENT -> T.success
        SendPhase.FAILED -> T.danger
        else -> T.accent
    }
    val text = when (state.phase) {
        SendPhase.SENDING -> "Отправляем…"
        SendPhase.SENT -> "Отправлено"
        SendPhase.FAILED -> "Не ушло — повторить"
        SendPhase.IDLE -> label
    }
    val icon = when (state.phase) {
        SendPhase.SENT -> Ic.check
        SendPhase.FAILED -> Ic.close
        else -> Ic.send
    }
    val fill by animateColorAsState(
        targetValue = when {
            dark -> tone.fill.copy(alpha = if (state.phase == SendPhase.IDLE) 0.18f else 0.30f)
            state.phase == SendPhase.IDLE -> T.surface
            else -> tone.chip
        },
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "sendFill"
    )
    val ink = if (dark) (if (state.phase == SendPhase.IDLE) T.textOnDark else tone.fill) else tone.ink

    Pressable(
        {
            if (state.busy) return@Pressable
            scope.launch {
                state.phase = SendPhase.SENDING
                val ok = runCatching { send() }.getOrDefault(false)
                state.phase = if (ok) SendPhase.SENT else SendPhase.FAILED
                if (ok) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(2400)
                if (state.phase != SendPhase.SENDING) state.phase = SendPhase.IDLE
            }
        },
        modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = T.touchMin)
                .clip(RoundedCornerShape(T.rControl))
                .background(fill)
                .border(
                    1.dp,
                    if (dark) Color.Transparent else if (state.phase == SendPhase.IDLE) T.hairline else tone.fill,
                    RoundedCornerShape(T.rControl)
                )
                .padding(horizontal = T.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            QIcon(icon, size = 18.dp, tint = ink, stroke = 2f)
            Spacer(Modifier.width(T.sm))
            Q(text, Type.small, ink, 1)
        }
    }
}

/** Пустой раздел. Живёт здесь, а не в оверлее: нужен и приложению, и листу. */
@Composable
fun EmptyState(text: String, hint: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = T.lg, vertical = T.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        QIcon(Ic.layers, size = 44.dp, tint = T.text3, stroke = 1.5f)
        Spacer(Modifier.height(T.md))
        Q(text, Type.heading, T.text2)
        Spacer(Modifier.height(T.xs))
        Q(hint, Type.small, T.text3)
        Spacer(Modifier.height(T.lg))
        PrimaryButton(action, onAction)
    }
}
