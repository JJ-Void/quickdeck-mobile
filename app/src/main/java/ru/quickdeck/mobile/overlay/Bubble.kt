package ru.quickdeck.mobile.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type

/**
 * Пузырь — только картинка.
 *
 * Весь жест обрабатывает служба обычным OnTouchListener, потому что ей нужны
 * абсолютные экранные координаты (rawX/rawY). Compose отдаёт координаты внутри
 * окна, а окно во время перетаскивания само едет за пальцем и отстаёт на кадр —
 * из-за этого сдвиг считался дважды и пузырь разгонялся по экрану.
 */
@Composable
fun BubbleRoot() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Bubble()
    }
}

@Composable
private fun Bubble() {
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
        Modifier
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
            animation = tween(1100, easing = LinearEasing),
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
