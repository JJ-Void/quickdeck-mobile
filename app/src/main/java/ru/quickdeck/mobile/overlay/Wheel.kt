package ru.quickdeck.mobile.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import kotlin.math.abs
import kotlin.math.roundToInt

/** Один пункт колеса. Пунктов всегда четыре — это разделы реестра. */
data class WheelItem(
    val title: String,
    val subtitle: String,
    val icon: String,
    val addLabel: String
)

/**
 * Геометрия колеса в пикселях. Шаг сетки 4 сохраняется:
 * 64 = 16 × 4, 56 = 14 × 4, 192 = 48 × 4.
 */
class WheelGeometry(densityPx: Float) {
    /** Расстояние между пунктами по вертикали. */
    val pitch = 64f * densityPx

    /** Палец почти не ушёл от пузыря — это отмена. */
    val cancelPull = 56f * densityPx

    /** Дальше этого — режим «добавить». */
    val createPull = 192f * densityPx

    /** Просвет между пузырём и карточками. */
    val gap = 16f * densityPx

    val cardWidth = 240f * densityPx
    val cardHeight = 56f * densityPx

    /** Сколько нельзя занимать сверху и снизу: статусная строка и навигация. */
    val safeTop = 96f * densityPx
    val safeBottom = 120f * densityPx

    fun stackHeight(count: Int) = (count - 1) * pitch + cardHeight

    /**
     * Верх первой карточки.
     *
     * Колесо хочет начаться там, где палец лёг на пузырь, чтобы первый пункт
     * оказался прямо под ним. Но если пузырь висит у края, стопка из четырёх
     * карточек туда не влезет — тогда она сдвигается внутрь экрана целиком.
     * Раньше этого не было, и нижние пункты просто уезжали за край.
     */
    fun anchorFor(originY: Float, screenHeight: Float, count: Int): Float {
        val stack = stackHeight(count)
        val lowest = screenHeight - safeBottom - stack
        val wanted = originY - cardHeight / 2f
        return if (lowest <= safeTop) safeTop else wanted.coerceIn(safeTop, lowest)
    }
}

/**
 * Чистая функция: где палец — такой и выбор.
 *
 * По вертикали палец показывает прямо на карточку: стопка стоит на месте,
 * двигается только выделение. По горизонтали — насколько человек вытянул:
 * чуть-чуть значит передумал, нормально — открыть, далеко — новая запись.
 */
fun selectionFor(
    itemCount: Int,
    anchorTop: Float,
    originX: Float,
    finger: Offset,
    g: WheelGeometry
): Pair<Float, WheelMode> {
    if (itemCount == 0) return 0f to WheelMode.CANCEL

    val virtual = ((finger.y - anchorTop - g.cardHeight / 2f) / g.pitch)
        .coerceIn(0f, (itemCount - 1).toFloat())

    val pull = abs(finger.x - originX)
    val mode = when {
        pull < g.cancelPull -> WheelMode.CANCEL
        pull < g.createPull -> WheelMode.BROWSE
        else -> WheelMode.CREATE
    }
    return virtual to mode
}

/** Стопка стоит неподвижно, по ней едет выделение — так предсказуемее. */
@Composable
fun Wheel(
    items: List<WheelItem>,
    virtual: Float,
    mode: WheelMode,
    createArmed: Boolean,
    anchorTop: Float,
    originX: Float,
    fromRight: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val g = remember(density) { WheelGeometry(density) }
    val selected = virtual.roundToInt().coerceIn(0, (items.size - 1).coerceAtLeast(0))

    Box(modifier.fillMaxSize()) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selected
            val away = abs(index - selected)
            val fade = if (isSelected) 1f else (1f - 0.18f * away).coerceIn(0.42f, 1f)

            val xPx = if (fromRight) originX - g.gap - g.cardWidth else originX + g.gap
            val yPx = anchorTop + index * g.pitch

            WheelCard(
                item = item,
                selected = isSelected && mode != WheelMode.CANCEL,
                creating = isSelected && mode == WheelMode.CREATE,
                armed = createArmed,
                widthPx = g.cardWidth,
                heightPx = g.cardHeight,
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .alpha(fade)
            )
        }
    }
}

@Composable
private fun WheelCard(
    item: WheelItem,
    selected: Boolean,
    creating: Boolean,
    armed: Boolean,
    widthPx: Float,
    heightPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val width = with(density) { widthPx.toDp() }
    val height = with(density) { heightPx.toDp() }

    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(T.MS_PRESS, easing = T.curve),
        label = "lift"
    )
    val grow by animateFloatAsState(
        targetValue = if (creating && armed) 1f else 0f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "grow"
    )

    val fill = when {
        creating -> T.accent.fill
        selected -> T.panelRaised
        else -> T.panelCard
    }
    val ink = if (creating) Color.White else T.textOnDark
    val sub = if (creating) Color.White.copy(alpha = 0.82f) else T.text2OnDark

    Row(
        modifier
            .width(width)
            .height(height)
            .scale(1f + 0.04f * lift + 0.02f * grow)
            .clip(RoundedCornerShape(T.rCard))
            .background(fill)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = when {
                    creating -> Color.White.copy(alpha = 0.34f)
                    selected -> T.accent.fill.copy(alpha = 0.22f + 0.6f * lift)
                    else -> T.hairlineDark
                },
                shape = RoundedCornerShape(T.rCard)
            )
            .padding(horizontal = T.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(T.rIcon))
                .background(
                    when {
                        creating -> Color.White.copy(alpha = 0.2f)
                        selected -> T.accent.fill.copy(alpha = 0.18f)
                        else -> Color.White.copy(alpha = 0.06f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            QIcon(
                if (creating) Ic.plus else item.icon,
                size = 20.dp,
                tint = when {
                    creating -> Color.White
                    selected -> T.accent.fill
                    else -> T.text2OnDark
                },
                stroke = if (selected || creating) 2f else 1.75f
            )
        }
        Spacer(Modifier.width(T.md))
        Column(Modifier.weight(1f)) {
            Q(if (creating) item.addLabel else item.title, Type.heading, ink, 1)
            Q(item.subtitle, Type.caption, sub, 1)
        }
    }
}
