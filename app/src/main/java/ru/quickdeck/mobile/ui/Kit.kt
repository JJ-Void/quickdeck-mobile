package ru.quickdeck.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Feel
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type

/**
 * Набор светлой системы: очаг, карточка на три элемента, строки, метки.
 *
 * Здесь собрано ровно то, из чего складываются экраны. Если для нового
 * экрана не хватает кирпича — он добавляется сюда, а не собирается на месте
 * из Box и Modifier. Именно так три экрана приложения и разъехались в три
 * разных дизайна.
 *
 * Правила, зашитые в эти кирпичи:
 *  — карточка держит максимум три элемента и поле 24;
 *  — метка-рубрика всегда капслоком и всегда бледная;
 *  — нажатие отвечает пружиной и коротким ударом в палец.
 */

/** Тень карточки: почти невидимая, но отделяет белое от светло-серого. */
private val cardShadow = 10.dp

// ── базовое нажатие ──────────────────────────────────────────────────────

/**
 * Нажимаемая область — модификатором, а не обёрткой.
 *
 * Сжатие до 0.985 палец чувствует как отклик, не замечая самой анимации.
 * Глубже уже читается как «кнопка проваливается», и на длинном списке это
 * начинает раздражать.
 */
@Composable
fun Modifier.tap(
    enabled: Boolean = true,
    haptic: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) T.PRESS_SCALE else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "tap"
    )
    return this
        .scale(s)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled
        ) {
            if (haptic) Feel.tick()
            onClick()
        }
}

/**
 * Короткое и долгое нажатие на одной области.
 *
 * Долгое — способ дать объяснение тому, кому оно нужно, не засоряя экран
 * поясняющими строками под каждой кнопкой.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.longPressable(onClick: () -> Unit, onLong: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) T.PRESS_SCALE else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "long"
    )
    return this
        .scale(s)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLong,
            onClick = { Feel.tick(); onClick() }
        )
}

// ── очаг экрана ──────────────────────────────────────────────────────────

/**
 * Очаг: метка, большое число, подпись. Один на экран, слева, с воздухом.
 *
 * Два очага на одном экране гасят друг друга — глаз мечется между ними и
 * не выбирает ни одного. Если кажется, что нужен второй, значит экран надо
 * делить надвое, а не добавлять цифру.
 */
@Composable
fun Hero(
    label: String,
    value: String,
    unit: String = "",
    sub: String = "",
    color: Color = T.ink
) {
    Column(Modifier.fillMaxWidth().padding(top = T.sm, bottom = T.heroGap)) {
        Q(label.uppercase(), Type.label, T.faint)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Q(value, Type.hero, color, 1)
            if (unit.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Q(unit, Type.title, T.mut, 1, Modifier.padding(bottom = 6.dp))
            }
        }
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Q(sub, Type.small, T.mut, 2)
        }
    }
}

/** Заголовок записи вместо числа — для её собственного экрана. */
@Composable
fun HeroTitle(label: String, title: String, sub: String = "") {
    Column(Modifier.fillMaxWidth().padding(top = T.sm, bottom = T.xl)) {
        Q(label.uppercase(), Type.label, T.faint)
        Spacer(Modifier.height(10.dp))
        Q(title, Type.title, T.ink)
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Q(sub, Type.small, T.mut)
        }
    }
}

/** Метка-рубрика над группой. Говорит, к чему относится то, что ниже. */
@Composable
fun GroupLabel(text: String, top: androidx.compose.ui.unit.Dp = 28.dp) {
    Q(
        text.uppercase(), Type.label, T.faint, 1,
        Modifier.padding(start = T.xs, top = top, bottom = T.md)
    )
}

// ── поверхность ──────────────────────────────────────────────────────────

/** Белая карточка: единственная поверхность, на которой живут данные. */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = T.rCard,
    fill: Color = T.card,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadowSoft(radius)
            .clip(RoundedCornerShape(radius))
            .background(fill),
        content = content
    )
}

/** Мягкая тень одной строкой, чтобы не повторять её на каждом вызове. */
fun Modifier.shadowSoft(radius: androidx.compose.ui.unit.Dp) = this.then(
    Modifier.shadow(
        elevation = cardShadow,
        shape = RoundedCornerShape(radius),
        ambientColor = Color(0x1A0E1316),
        spotColor = Color(0x260E1316)
    )
)

/**
 * Карточка на три элемента: метка, значение, подпись.
 *
 * Четвёртый элемент сюда не влезает намеренно. Всё, что не поместилось,
 * живёт на слое глубже — в записи, а не в сводке.
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    note: String = "",
    alarm: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(T.cardPad)) {
            Q(
                label.uppercase(), Type.label,
                if (alarm) T.danger.copy(alpha = 0.62f) else T.faint
            )
            Spacer(Modifier.height(14.dp))
            Q(value, Type.big, if (alarm) T.danger else T.ink, 1)
            if (note.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Q(note, Type.small, T.mut, 1)
            }
        }
    }
    val fill = if (alarm) T.dangerWash else T.card
    val base = Modifier.padding(bottom = T.gap)
    if (onClick == null) Surface(base, fill = fill, content = body)
    else Surface(base.tap(onClick = onClick), fill = fill, content = body)
}

/**
 * Папка: имя и одна цифра. Ничего больше.
 *
 * Плашки «2 в работе · 1 просрочен» отсюда убраны сознательно: они делали
 * все строки одинаково пёстрыми, и папка переставала читаться как папка.
 */
@Composable
fun FolderRow(name: String, count: Int, hot: Boolean = false, onClick: () -> Unit) {
    Surface(Modifier.padding(bottom = 14.dp).tap(onClick = onClick)) {
        Row(
                Modifier.fillMaxWidth().padding(T.cardPad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Q(name, Type.heading, T.ink, 2, Modifier.weight(1f))
                Spacer(Modifier.width(T.lg))
            Q(count.toString(), Type.big, if (hot) T.accent else T.faint, 1)
        }
    }
}

/** Строка записи: имя и значение. Подпись — только когда без неё непонятно. */
@Composable
fun ItemRow(
    name: String,
    value: String,
    hot: Boolean = false,
    sub: String = "",
    onClick: () -> Unit
) {
    Surface(Modifier.padding(bottom = T.gap).tap(onClick = onClick), radius = T.rRow) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = T.cardPad, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Q(name, Type.heading, T.ink, 2)
                    if (sub.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Q(sub, Type.small, T.faint, 1)
                    }
                }
            Spacer(Modifier.width(T.md))
            Q(value, Type.amount, if (hot) T.danger else T.mut, 1)
        }
    }
}

/** Пара «подпись — значение» в списке фактов. */
@Composable
fun FactRow(key: String, value: String, tint: Color = T.ink, last: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = T.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Q(key, Type.small, T.mut, 1, Modifier.weight(1f))
            Spacer(Modifier.width(T.md))
            Q(value, Type.body, tint, 1)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(T.hairline))
    }
}

/** Карточка фактов: поле по бокам, разделители внутри. */
@Composable
fun Facts(pairs: List<Triple<String, String, Color>>) {
    if (pairs.isEmpty()) return
    Surface(Modifier.padding(bottom = T.gap)) {
        Column(Modifier.padding(horizontal = T.cardPad, vertical = T.sm)) {
            pairs.forEachIndexed { i, (k, v, c) ->
                FactRow(k, v, c, last = i == pairs.lastIndex)
            }
        }
    }
}

// ── задача ───────────────────────────────────────────────────────────────

/** Строка задачи: квадрат-галочка и текст. Один ряд текста, не больше. */
@Composable
fun TaskRow(text: String, done: Boolean, onToggle: () -> Unit) {
    Surface(
        Modifier.padding(bottom = T.gap).tap(haptic = false) { Feel.confirm(); onToggle() },
        radius = T.rRow
    ) {
        Row(
                Modifier.fillMaxWidth().padding(horizontal = T.cardPad, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val boxColor by androidx.compose.animation.animateColorAsState(
                    if (done) T.accent else Color(0x0F0E1316),
                    tween(T.MS_STATE, easing = T.curve), label = "box"
                )
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(T.rInner)).background(boxColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (done) QIcon(Ic.check, size = 13.dp, tint = Color.White, stroke = 2.6f)
                }
            Spacer(Modifier.width(14.dp))
            Q(text, Type.body, if (done) T.faint else T.ink, 3, Modifier.weight(1f))
        }
    }
}

/** Единственная кнопка со словом на экране: добавить. Контур, не заливка. */
@Composable
fun AddRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = T.xl)
            .tap(onClick = onClick)
            .clip(RoundedCornerShape(T.rRow))
            .androidBorder()
            .padding(horizontal = T.cardPad, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QIcon(Ic.plus, size = 17.dp, tint = T.mut)
        Spacer(Modifier.width(10.dp))
        Q(text, Type.body, T.mut, 1)
    }
}

private fun Modifier.androidBorder() =
    border(1.5.dp, Color(0x1A0E1316), RoundedCornerShape(T.rRow))

// ── фон: одно мягкое пятно ───────────────────────────────────────────────

/**
 * Единственная «графика» на фоне — размытое пятно акцента сверху.
 *
 * Оно задаёт характер и не спорит с данными. Решётка точек, которая стояла
 * здесь раньше, читалась как шум: на тепловой карте фон начинал набирать
 * внимание наравне с цифрами.
 */
@Composable
fun AccentWash(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.width * 0.78f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    T.accentSoft.copy(alpha = 0.30f),
                    T.accent.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.22f, -r * 0.18f),
                radius = r
            ),
            radius = r,
            center = Offset(size.width * 0.22f, -r * 0.18f)
        )
    }
}

// ── спарклайн ────────────────────────────────────────────────────────────

/**
 * График-разгрузчик. Он тут не для того, чтобы по нему считали, а чтобы
 * список цифр не был стеной текста. Поэтому ни осей, ни сетки, ни подписей.
 */
@Composable
fun Sparkline(points: List<Float>, modifier: Modifier = Modifier, tint: Color = T.accent) {
    if (points.size < 2) return
    Canvas(modifier) {
        val min = points.min()
        val max = points.max()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (points.size - 1)
        fun px(i: Int) = i * stepX
        fun py(v: Float) = size.height - (v - min) / span * (size.height - 8f) - 4f

        val line = Path().apply {
            moveTo(px(0), py(points[0]))
            for (i in 1 until points.size) lineTo(px(i), py(points[i]))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                listOf(tint.copy(alpha = 0.20f), Color.Transparent),
                startY = 0f, endY = size.height
            )
        )
        drawPath(line, tint, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(tint, 3.4f, Offset(px(points.lastIndex), py(points.last())))
    }
}

/** Карточка с графиком: метка, тренд, линия. Те же три элемента. */
@Composable
fun SparkCard(label: String, trend: String, points: List<Float>) {
    Surface(Modifier.padding(bottom = T.gap)) {
        Column(Modifier.padding(T.cardPad)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Q(label.uppercase(), Type.label, T.faint, 1, Modifier.weight(1f))
                Q(trend, Type.body, T.accent, 1)
            }
            Spacer(Modifier.height(T.lg))
            Sparkline(points, Modifier.fillMaxWidth().height(60.dp))
        }
    }
}

// ── подтверждение удержанием ─────────────────────────────────────────────

/**
 * Необратимое действие подтверждается удержанием, а не вторым диалогом.
 *
 * Полоса заполняется под пальцем: видно, сколько осталось, и в любой момент
 * можно передумать, просто отпустив. Диалог «вы уверены?» такой возможности
 * не даёт — он требует второго осознанного нажатия и потому нажимается не
 * глядя.
 */
@Composable
fun HoldToConfirm(
    idleText: String,
    holdText: String,
    doneText: String,
    onConfirm: () -> Unit
) {
    // 0 — покой, 1 — держат, 2 — свершилось.
    val phase = remember { mutableIntStateOf(0) }
    val progress = remember { mutableFloatStateOf(0f) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    LaunchedEffect(pressed) {
        if (phase.intValue == 2) return@LaunchedEffect
        if (!pressed) {
            progress.floatValue = 0f
            phase.intValue = 0
            return@LaunchedEffect
        }
        phase.intValue = 1
        Feel.tick()
        val start = withFrameMillis { it }
        while (progress.floatValue < 1f) {
            val now = withFrameMillis { it }
            progress.floatValue = ((now - start) / 900f).coerceAtMost(1f)
        }
        phase.intValue = 2
        Feel.warn()
        onConfirm()
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = T.sm, bottom = T.xl)
            .shadowSoft(T.rControl)
            .clip(RoundedCornerShape(T.rControl))
            .background(T.card)
            .heightIn(min = 58.dp)
            .clickable(interactionSource = interaction, indication = null) { },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.floatValue)
                .height(58.dp)
                .background(Color(0xFFFBE4DF))
        )
        Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) {
            Q(
                when (phase.intValue) {
                    1 -> holdText
                    2 -> doneText
                    else -> idleText
                },
                Type.body, T.danger, 1
            )
        }
    }
}

// ── переключатель ────────────────────────────────────────────────────────

/** Переключатель. Пружина на ручке — единственное место, где она нужна. */
@Composable
fun Switch(value: Boolean) {
    val x by animateFloatAsState(
        if (value) 20f else 0f,
        spring(dampingRatio = 0.5f, stiffness = 700f), label = "sw"
    )
    val track by androidx.compose.animation.animateColorAsState(
        if (value) T.accent else Color(0x1F0E1316),
        tween(T.MS_STATE, easing = T.curve), label = "track"
    )
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(track)
    ) {
        Box(
            Modifier
                .padding(start = x.dp + 3.dp, top = 3.dp)
                .size(22.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White)
        )
    }
}

/** Пустой экран: что тут будет и как это завести. Без иллюстраций. */
@Composable
fun Empty(title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = T.xxl)) {
        Q(title, Type.heading, T.mut)
        Spacer(Modifier.height(T.sm))
        Q(hint, Type.small, T.faint)
    }
}
