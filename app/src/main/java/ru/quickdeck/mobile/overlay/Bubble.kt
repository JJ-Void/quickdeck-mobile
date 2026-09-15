package ru.quickdeck.mobile.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type

/** Чем кончилось первое движение пальца после касания пузыря. */
private enum class First { DRAG, UP, GONE }

/**
 * Пузырь и весь жест разом.
 *
 * Здесь важна одна вещь: окно пузыря за время жеста не меняет размер.
 * Android отменяет поток касаний, когда окно пересоздают, — поэтому панель
 * вынесена в отдельное окно, а это остаётся неподвижным и маленьким.
 * Палец, легший на пузырь, продолжает слать события даже далеко за его
 * границами: система отдаёт весь жест тому окну, где случилось нажатие.
 *
 * Три жеста, каждый со своим смыслом:
 *   тап            — открыть список того раздела, где был в прошлый раз;
 *   потянул        — колесо разделов под пальцем, отпустил — выбрал;
 *   долгое нажатие — пузырь оторвался, тащи куда удобно.
 */
@Composable
fun BubbleRoot(host: OverlayHost, screenWidthPx: Float) {
    val density = LocalDensity.current.density
    val g = remember(density) { WheelGeometry(density) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val holdMs = viewConfiguration.longPressTimeoutMillis

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val startLocal = down.position
                    val origin = Offset(
                        OverlayState.bubbleLeft + startLocal.x,
                        OverlayState.bubbleTop + startLocal.y
                    )

                    val first = withTimeoutOrNull(holdMs) {
                        firstIntent(down.id, startLocal, slop)
                    }

                    when (first) {
                        // Времени вышло, палец на месте — пузырь отрывается.
                        null -> {
                            host.buzz(20)
                            OverlayState.moving = true
                            dragBubble(down.id, startLocal, host)
                            OverlayState.moving = false
                            host.snapBubble()
                        }

                        First.DRAG -> {
                            OverlayState.beginWheel(origin, screenWidthPx)
                            host.buzz(8)
                            spinWheel(down.id, origin, g, host)
                            OverlayState.releaseWheel()
                        }

                        First.UP -> {
                            host.buzz(6)
                            if (OverlayState.isOpen) OverlayState.close() else OverlayState.openLast()
                        }

                        First.GONE -> Unit
                    }
                }
            }
    ) {
        Bubble(Modifier.align(Alignment.Center))
    }
}

/** Ждём: ушёл за порог, отпустил, или палец потерялся. */
private suspend fun AwaitPointerEventScope.firstIntent(
    id: PointerId,
    start: Offset,
    slop: Float
): First {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return First.GONE
        if (!change.pressed) return First.UP
        if ((change.position - start).getDistance() > slop) return First.DRAG
    }
}

/**
 * Таскание пузыря. Шаг считается от точки нажатия, а не от прошлого события:
 * окно едет за пальцем, поэтому местная координата каждый раз возвращается
 * туда же, и разница между соседними событиями всегда была бы нулём.
 */
private suspend fun AwaitPointerEventScope.dragBubble(
    id: PointerId,
    start: Offset,
    host: OverlayHost
) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        change.consume()
        val step = change.position - start
        if (step.x != 0f || step.y != 0f) host.moveBubble(step.x, step.y)
    }
}

/** Ведение по колесу. Окно неподвижно, поэтому экранная точка считается прямо. */
private suspend fun AwaitPointerEventScope.spinWheel(
    id: PointerId,
    origin: Offset,
    g: WheelGeometry,
    host: OverlayHost
) {
    var lastIndex = -1
    var lastMode = WheelMode.CANCEL

    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        change.consume()

        val point = Offset(
            OverlayState.bubbleLeft + change.position.x,
            OverlayState.bubbleTop + change.position.y
        )
        val (virtual, mode) = selectionFor(SECTION_COUNT, origin, point, g)
        OverlayState.dragTo(point, virtual, mode)

        val index = virtual.toInt()
        if (index != lastIndex && mode != WheelMode.CANCEL) {
            lastIndex = index
            host.buzz(8)
        }
        if (mode != lastMode) {
            lastMode = mode
            OverlayState.createArmed = mode == WheelMode.CREATE
            if (mode == WheelMode.CREATE) host.buzz(18)
        }
    }
}

private const val SECTION_COUNT = 4

/** Сам кружок. В покое полупрозрачный, чтобы не лез в глаза поверх чужого экрана. */
@Composable
private fun Bubble(modifier: Modifier = Modifier) {
    val moving = OverlayState.moving
    val open = OverlayState.isOpen
    val syncing = OverlayState.syncing
    val fresh = OverlayState.freshCount

    val alpha by animateFloatAsState(
        targetValue = if (moving || open) 1f else 0.62f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "bubbleAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (moving) 1.18f else 1f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "bubbleScale"
    )

    Box(
        modifier
            .size(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(percent = 50))
            .background(T.panelRaised.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        if (syncing) SyncRing() else Ring(alpha)

        when {
            open -> QIcon(Ic.close, size = 20.dp, tint = T.textOnDark, stroke = 2f)
            fresh > 0 -> Q(fresh.toString(), Type.amount, T.accent.fill)
            else -> QIcon(Ic.layers, size = 22.dp, tint = T.textOnDark, stroke = 1.9f)
        }
    }
}

@Composable
private fun Ring(alpha: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            color = T.accent.fill.copy(alpha = 0.55f * alpha),
            radius = size.minDimension / 2f - 1.dp.toPx(),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

/** Обмен с таблицей — дуга, которая бежит по кругу. Без текста и без модалок. */
@Composable
private fun SyncRing() {
    val spin = rememberInfiniteTransition(label = "sync")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncAngle"
    )
    Canvas(Modifier.fillMaxSize()) {
        val inset = 1.dp.toPx()
        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = size.minDimension / 2f - inset,
            style = Stroke(width = 2.dp.toPx())
        )
        drawArc(
            color = T.accent.fill,
            startAngle = angle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
