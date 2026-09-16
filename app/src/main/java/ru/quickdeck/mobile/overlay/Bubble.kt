package ru.quickdeck.mobile.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.T

/**
 * Нить — то, чем система присутствует на экране, пока её не позвали.
 *
 * Это не кнопка и не тулбар: у края лежит короткая светящаяся полоса,
 * которая почти ничего не закрывает и ничего не обещает. Она живая —
 * дышит, наливается светом под пальцем, вспыхивает, когда из таблицы
 * приехали изменения. Вся суть в том, что интерфейс появляется только
 * в момент касания, а в покое остаётся полоской света.
 *
 * Рисуется только картинка: жест целиком ведёт служба, обычным
 * OnTouchListener по абсолютным координатам. Compose отдаёт координаты
 * внутри окна, а окно во время перетаскивания само едет за пальцем и
 * отстаёт на кадр — сдвиг считался бы дважды, и нить разгонялась бы.
 */
@Composable
fun BubbleRoot() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Thread()
    }
}

@Composable
private fun Thread() {
    val moving = OverlayState.moving
    val open = OverlayState.isOpen
    val syncing = OverlayState.syncing
    val fresh = OverlayState.freshCount
    val fromRight = OverlayState.fromRight

    // Дыхание: медленное, еле заметное. В покое нить чуть тускнеет и
    // снова наливается — видно, что система жива, но взгляд не цепляет.
    val pulse = rememberInfiniteTransition(label = "thread")
    val breath by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val live by animateFloatAsState(
        targetValue = if (moving || open) 1f else 0.7f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "threadLive"
    )
    val width by animateFloatAsState(
        targetValue = if (moving || open) 1f else 0.55f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "threadWidth"
    )

    val tone = when {
        syncing -> T.beam
        fresh > 0 -> T.glow
        else -> T.textOnDark
    }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val lineW = (2.dp.toPx() + 2.dp.toPx() * width)
        val lineH = h * 0.42f
        val x = if (fromRight) w - lineW * 1.6f else lineW * 1.6f
        val top = (h - lineH) / 2f

        // Ореол — то, из-за чего нить выглядит светящейся, а не нарисованной.
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    tone.copy(alpha = 0.16f * live * breath),
                    Color.Transparent
                )
            ),
            topLeft = Offset(x - lineW * 3f, top - lineH * 0.18f),
            size = Size(lineW * 7f, lineH * 1.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(lineW * 3f)
        )

        // Сама нить: ярче к середине, растворяется к концам.
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    tone.copy(alpha = 0.10f * live),
                    tone.copy(alpha = 0.92f * live * breath),
                    tone.copy(alpha = 0.10f * live)
                )
            ),
            topLeft = Offset(x, top),
            size = Size(lineW, lineH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(lineW)
        )

        // Засечка по центру — точка, от которой начинается жест.
        drawRoundRect(
            color = tone.copy(alpha = live),
            topLeft = Offset(x - lineW * 0.6f, h / 2f - lineW * 2.2f),
            size = Size(lineW * 2.2f, lineW * 4.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(lineW)
        )
    }

    if (syncing) SyncArc()
    if (fresh > 0 && !syncing) FreshSpark()
}

/** Обмен с таблицей: дуга бежит вокруг засечки. Ни текста, ни модалок. */
@Composable
private fun SyncArc() {
    val spin = rememberInfiniteTransition(label = "sync")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncAngle"
    )
    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension * 0.22f
        val c = Offset(size.width / 2f, size.height / 2f)
        rotate(angle, c) {
            drawArc(
                color = T.beam,
                startAngle = 0f,
                sweepAngle = 88f,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/** Приехали правки — по нити проходит короткая тёплая вспышка. */
@Composable
private fun FreshSpark() {
    val t = rememberInfiniteTransition(label = "spark")
    val shift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkShift"
    )
    Canvas(Modifier.fillMaxSize()) {
        val h = size.height
        val y = h * (0.28f + 0.44f * shift)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(T.glow.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width / 2f, y),
                radius = 18.dp.toPx()
            ),
            radius = 18.dp.toPx(),
            center = Offset(size.width / 2f, y)
        )
    }
}
