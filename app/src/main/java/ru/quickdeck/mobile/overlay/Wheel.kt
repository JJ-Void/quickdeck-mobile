package ru.quickdeck.mobile.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Один луч веера. Лучей всегда четыре — это разделы реестра. */
data class WheelItem(
    val title: String,
    val subtitle: String,
    val icon: String,
    val addLabel: String
)

/**
 * Геометрия веера.
 *
 * Выбор идёт по углу, а не по вертикали: палец выходит из нити и ведёт по
 * дуге, как стрелка прибора. Рука так двигается естественнее — запястье
 * само описывает дугу, — и то же движение потом продолжается в колоде,
 * которая листается вбок. Один язык на весь интерфейс.
 */
class WheelGeometry(densityPx: Float) {
    /** Ближе этого — палец ещё «на нити», выбор не начался. */
    val deadZone = 44f * densityPx

    /** Радиус, на котором стоят лучи. */
    val radius = 128f * densityPx

    /** Дальше — режим «создать»: рука ушла за пределы веера. */
    val createPull = 224f * densityPx

    val cardWidth = 168f * densityPx
    val cardHeight = 52f * densityPx

    /** Сектор одного луча по углу, в радианах. */
    val sector = (Math.PI / 4.4).toFloat()

    val safeTop = 96f * densityPx
    val safeBottom = 120f * densityPx

    /**
     * Центр веера. Хочется поставить его туда, где палец коснулся нити,
     * но у края экрана верхний и нижний лучи вылезли бы за границу —
     * поэтому центр отодвигается внутрь ровно настолько, чтобы веер влез.
     */
    fun centerFor(originY: Float, screenHeight: Float): Float {
        val margin = radius * 0.86f
        val top = safeTop + margin
        val bottom = screenHeight - safeBottom - margin
        return if (bottom <= top) screenHeight / 2f else originY.coerceIn(top, bottom)
    }

    /** Угол луча: веер раскрывается от края к центру экрана. */
    fun angleOf(index: Int, count: Int, fromRight: Boolean): Float {
        val span = sector * (count - 1)
        val start = -span / 2f
        val a = start + sector * index
        return if (fromRight) (Math.PI.toFloat() - a) else a
    }
}

/**
 * Прилипание к лучу: возле центра сектора кривая почти плоская, поэтому
 * луч «держится», а не дрожит между соседями, когда рука подрагивает.
 */
fun detent(virtual: Float): Float {
    val base = kotlin.math.round(virtual)
    val d = virtual - base
    return base + kotlin.math.sign(d) * (abs(d) * 2f).pow17() / 2f
}

private fun Float.pow17(): Float = Math.pow(this.toDouble(), 1.7).toFloat()

/**
 * Где палец — такой и выбор.
 *
 * Угол между пальцем и центром веера выбирает луч, расстояние — намерение:
 * не отходя от нити, человек ничего не выбирает; на радиусе веера —
 * открывает раздел; вытянув руку дальше — заводит новую запись.
 */
fun selectionFor(
    itemCount: Int,
    centerY: Float,
    originX: Float,
    finger: Offset,
    g: WheelGeometry
): Pair<Float, WheelMode> {
    if (itemCount == 0) return 0f to WheelMode.CANCEL

    val fromRight = OverlayState.fromRight
    val dx = (finger.x - originX) * (if (fromRight) -1f else 1f)
    val dy = finger.y - centerY
    val reach = hypot(dx.toDouble(), dy.toDouble()).toFloat()

    val angle = atan2(dy, dx.coerceAtLeast(1f))
    val span = g.sector * (itemCount - 1)
    val virtual = ((angle + span / 2f) / g.sector).coerceIn(0f, (itemCount - 1).toFloat())

    val mode = when {
        reach < g.deadZone -> WheelMode.CANCEL
        reach < g.createPull -> WheelMode.BROWSE
        else -> WheelMode.CREATE
    }
    return virtual to mode
}

/**
 * Веер: лучи расходятся дугой от точки касания, выбранный — ярче и ближе
 * к пальцу. Между центром и выбранным лучом натянута светящаяся нить:
 * видно, чем именно управляет рука.
 */
@Composable
fun Wheel(
    items: List<WheelItem>,
    virtual: Float,
    mode: WheelMode,
    createArmed: Boolean,
    centerY: Float,
    originX: Float,
    fromRight: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val g = remember(density) { WheelGeometry(density) }
    val shown = detent(virtual)
    val selected = virtual.roundToInt().coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val active = mode != WheelMode.CANCEL

    Box(modifier.fillMaxSize()) {
        Rays(items.size, shown, g, centerY, originX, fromRight, active, mode)

        items.forEachIndexed { index, item ->
            val angle = g.angleOf(index, items.size, fromRight)
            val away = abs(index - shown)
            if (away > 2.4f) return@forEachIndexed

            // Выбранный луч подаётся вперёд — как будто тянется к пальцу.
            val lift = (1f - (away / 1.6f)).coerceIn(0f, 1f)
            val r = g.radius + 18f * density * lift
            val cx = originX + cos(angle.toDouble()).toFloat() * r
            val cy = centerY + sin(angle.toDouble()).toFloat() * r

            WheelCard(
                item = item,
                selected = index == selected && active,
                creating = index == selected && mode == WheelMode.CREATE,
                armed = createArmed,
                widthPx = g.cardWidth,
                heightPx = g.cardHeight,
                fromRight = fromRight,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (cx - if (fromRight) g.cardWidth else 0f).roundToInt(),
                            (cy - g.cardHeight / 2f).roundToInt()
                        )
                    }
                    .scale(0.9f + 0.1f * lift)
                    .alpha((0.3f + 0.7f * lift).coerceIn(0f, 1f))
            )
        }
    }
}

/**
 * Лучи и натянутая нить.
 *
 * Дуги показывают, куда можно вести палец, яркая линия — куда он ведёт
 * сейчас. Без этого веер превращается в набор карточек, висящих в воздухе.
 */
@Composable
private fun Rays(
    count: Int,
    shown: Float,
    g: WheelGeometry,
    centerY: Float,
    originX: Float,
    fromRight: Boolean,
    active: Boolean,
    mode: WheelMode
) {
    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0.35f,
        animationSpec = tween(T.MS_PRESS, easing = T.curve),
        label = "rays"
    )
    val tone = if (mode == WheelMode.CREATE) T.glow else T.beam

    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(originX, centerY)

        // Тонкая дуга-направляющая, по которой стоят лучи.
        val span = Math.toDegrees((g.sector * (count - 1)).toDouble()).toFloat()
        val startDeg = if (fromRight) 180f - span / 2f else -span / 2f
        drawArc(
            color = T.textOnDark.copy(alpha = 0.10f * glow),
            startAngle = if (fromRight) startDeg - span / 2f else startDeg,
            sweepAngle = span,
            useCenter = false,
            topLeft = Offset(c.x - g.radius, c.y - g.radius),
            size = Size(g.radius * 2, g.radius * 2),
            style = Stroke(width = 1.dp.toPx())
        )

        // Нить от центра к выбранному лучу.
        val angle = g.angleOf(shown.roundToInt().coerceIn(0, count - 1), count, fromRight)
        val target = Offset(
            c.x + cos(angle.toDouble()).toFloat() * g.radius,
            c.y + sin(angle.toDouble()).toFloat() * g.radius
        )
        drawLine(
            brush = Brush.linearGradient(
                listOf(tone.copy(alpha = 0.06f * glow), tone.copy(alpha = 0.75f * glow)),
                start = c,
                end = target
            ),
            start = c,
            end = target,
            strokeWidth = 2.dp.toPx()
        )

        // Ореол в точке касания: рука держит источник света.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(tone.copy(alpha = 0.22f * glow), Color.Transparent),
                center = c,
                radius = 54.dp.toPx()
            ),
            radius = 54.dp.toPx(),
            center = c
        )
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
    fromRight: Boolean,
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
        creating -> T.action.chip
        selected -> T.panelRaised
        else -> T.panelCard
    }
    val ink = if (creating) T.action.ink else T.textOnDark
    val sub = if (creating) T.action.ink.copy(alpha = 0.76f) else T.text2OnDark

    Row(
        modifier
            .width(width)
            .height(height)
            .scale(1f + 0.03f * lift + 0.02f * grow)
            .clip(RoundedCornerShape(T.rControl))
            .background(fill)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = when {
                    creating -> T.glow.copy(alpha = 0.5f)
                    selected -> T.beam.copy(alpha = 0.18f + 0.5f * lift)
                    else -> T.panelEdge
                },
                shape = RoundedCornerShape(T.rControl)
            )
            .padding(horizontal = T.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(T.rIcon))
                .background(
                    when {
                        creating -> T.glowSoft
                        selected -> T.beamSoft
                        else -> T.hairlineDark
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            QIcon(
                if (creating) Ic.plus else item.icon,
                size = 17.dp,
                tint = when {
                    creating -> T.glow
                    selected -> T.beam
                    else -> T.text2OnDark
                },
                stroke = if (selected || creating) 2f else 1.75f
            )
        }
        Spacer(Modifier.width(T.sm))
        Column(Modifier.weight(1f)) {
            Q(if (creating) item.addLabel else item.title, Type.small, ink, 1)
            Q(item.subtitle, Type.caption, sub, 1)
        }
    }
}
