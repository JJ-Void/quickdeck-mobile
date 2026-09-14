package ru.quickdeck.mobile.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Что рисуем в колесе: раздел или конкретная запись. */
data class WheelItem(
    val title: String,
    val subtitle: String? = null,
    val icon: String = Ic.layers,
    val tone: T.Tone = T.accent
)

enum class WheelMode {
    /** Палец у самого края — отпустил, ничего не произошло. */
    CANCEL,

    /** Обычный выбор пункта. */
    BROWSE,

    /** Палец вытянут дальше и зафиксирован — у пункта появился плюс. */
    CREATE
}

/** Геометрия в пикселях. Шаг 4 сохраняется: 56 = 14 × 4, 40, 200. */
class WheelGeometry(densityPx: Float) {
    val pitch = 56f * densityPx          // один пункт на 56 dp хода пальца
    val cancelEdge = 40f * densityPx     // ближе к краю — отмена
    val createEdge = 200f * densityPx    // дальше — режим «плюс»
    val arcRadius = 3.4f * pitch         // насколько заметно выгибается колесо
    val autoScrollZone = 96f * densityPx // у верхнего и нижнего края лист сам крутится
}

/** Чистая функция: где палец — такой и выбор. */
fun selectionFor(
    itemCount: Int,
    pivotY: Float,
    baseOffset: Float,
    finger: Offset,
    fromRight: Boolean,
    widthPx: Float,
    g: WheelGeometry
): Pair<Float, WheelMode> {
    if (itemCount == 0) return 0f to WheelMode.CANCEL
    val dx = if (fromRight) widthPx - finger.x else finger.x
    val virtual = (baseOffset + (finger.y - pivotY) / g.pitch)
        .coerceIn(0f, (itemCount - 1).toFloat())
    val mode = when {
        dx < g.cancelEdge -> WheelMode.CANCEL
        dx < g.createEdge -> WheelMode.BROWSE
        else -> WheelMode.CREATE
    }
    return virtual to mode
}

/**
 * Колесо: пункты идут по дуге от края, выбранный — ближе всех к пальцу и дальше всех от края.
 * Выделение — белая карточка, кольцо акцента 1.5 и свечение того же цвета, а не заливка.
 */
@Composable
fun Wheel(
    items: List<WheelItem>,
    title: String,
    virtual: Float,
    mode: WheelMode,
    createArmed: Boolean,
    pivotY: Float,
    fromRight: Boolean,
    createLabel: String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val g = remember(density) { WheelGeometry(density) }
    val selected = virtual.roundToInt().coerceIn(0, (items.size - 1).coerceAtLeast(0))

    Box(modifier.fillMaxSize()) {

        // Заголовок уровня — у края, на уровне точки касания.
        Row(
            Modifier
                .offset { IntOffset(0, (pivotY - 5.2f * g.pitch).roundToInt()) }
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = if (fromRight) Arrangement.End else Arrangement.Start
        ) {
            Q(title, Type.caption, Color.White.copy(alpha = 0.72f))
        }

        items.forEachIndexed { index, item ->
            val rel = index - virtual
            if (abs(rel) > 4.6f) return@forEachIndexed

            val dy = rel * g.pitch
            // точка на окружности вокруг места касания: в центре пункт дальше всего
            // от края, к концам дуги — прижимается обратно
            val t = (dy / g.arcRadius).let { 1f - it * it }.coerceAtLeast(0f)
            val bowDp = 30f * sqrt(t)
            val isSelected = index == selected
            val fade = (1f - 0.17f * abs(rel)).coerceIn(0.18f, 1f)
            val shrink = (1f - 0.07f * abs(rel)).coerceIn(0.74f, 1f)
            val inset = (12f + bowDp).dp
            val yPx = (pivotY + dy - 26f * density).roundToInt()

            WheelRow(
                item = item,
                selected = isSelected && mode != WheelMode.CANCEL,
                showPlus = isSelected && mode == WheelMode.CREATE,
                armed = createArmed,
                createLabel = createLabel,
                fromRight = fromRight,
                modifier = Modifier
                    .offset { IntOffset(0, yPx) }
                    .fillMaxWidth()
                    .padding(start = if (fromRight) 0.dp else inset, end = if (fromRight) inset else 0.dp)
                    .alpha(fade)
                    .scale(shrink)
            )
        }
    }
}

@Composable
private fun WheelRow(
    item: WheelItem,
    selected: Boolean,
    showPlus: Boolean,
    armed: Boolean,
    createLabel: String,
    fromRight: Boolean,
    modifier: Modifier = Modifier
) {
    val ring by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(T.MS_PRESS, easing = T.curve),
        label = "ring"
    )
    val plusScale by animateFloatAsState(
        targetValue = if (showPlus && armed) 1f else 0.25f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "plus"
    )

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fromRight) Arrangement.End else Arrangement.Start
    ) {
        if (fromRight && showPlus) {
            PlusBadge(plusScale, createLabel, fromRight)
            Spacer(Modifier.width(T.sm))
        }

        Row(
            Modifier
                .clip(RoundedCornerShape(T.rCard))
                .background(if (selected) T.surface else T.surface.copy(alpha = 0.92f))
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected)
                        item.tone.fill.copy(alpha = 0.26f + 0.74f * ring)
                    else T.hairline,
                    shape = RoundedCornerShape(T.rCard)
                )
                .heightIn(min = 52.dp)
                .widthIn(min = 180.dp, max = 260.dp)
                .padding(horizontal = T.md, vertical = T.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(T.rIcon))
                    .background(if (selected) item.tone.chip else T.muted.chip),
                contentAlignment = Alignment.Center
            ) {
                QIcon(item.icon, size = 18.dp, tint = if (selected) item.tone.ink else T.text2, stroke = if (selected) 2f else 1.75f)
            }
            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f, fill = false)) {
                Q(item.title, Type.heading, T.text, 1)
                item.subtitle?.let {
                    Q(it, Type.caption, T.text3, 1)
                }
            }
        }

        if (!fromRight && showPlus) {
            Spacer(Modifier.width(T.sm))
            PlusBadge(plusScale, createLabel, fromRight)
        }
    }
}

@Composable
private fun PlusBadge(scale: Float, label: String, fromRight: Boolean) {
    Row(
        Modifier.scale(scale).alpha(scale.coerceIn(0f, 1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (fromRight) {
            Q(label, Type.caption, Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.width(T.sm))
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(T.accent.fill),
            contentAlignment = Alignment.Center
        ) { QIcon(Ic.plus, size = 22.dp, tint = Color.White, stroke = 2f) }
        if (!fromRight) {
            Spacer(Modifier.width(T.sm))
            Q(label, Type.caption, Color.White.copy(alpha = 0.85f))
        }
    }
}
