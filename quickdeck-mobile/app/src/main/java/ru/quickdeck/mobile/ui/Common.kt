package ru.quickdeck.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.Status

fun Status.tone(): T.Tone = when (this) {
    Status.DRAFT -> T.muted
    Status.WORK -> T.accent
    Status.WAIT -> T.warning
    Status.DONE -> T.success
    Status.OVERDUE -> T.danger
    Status.ARCHIVE -> T.muted
}

/** Статус — всегда плашка с текстом. Цвет один не носит смысла. */
@Composable
fun StatusChip(status: Status, modifier: Modifier = Modifier) {
    val tone = status.tone()
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tone.chip)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Q(status.label, Type.caption, tone.ink)
    }
}

/** Поверхность-лист: радиус 24, мягкая двухслойная тень даётся окном-подложкой. */
@Composable
fun Sheet(
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = T.rSheet,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(T.surface)
            .border(1.dp, T.hairline, RoundedCornerShape(radius)),
        content = content
    )
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

/** Одна цветная кнопка на экран — главная. Остальные нейтральные. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Pressable(onClick, modifier.fillMaxWidth(), enabled) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = T.touchMin)
                .clip(RoundedCornerShape(T.rControl))
                .background(if (enabled) T.accent.fill else T.muted.fill),
            contentAlignment = Alignment.Center
        ) { Q(text, Type.heading, Color.White) }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Pressable(onClick, modifier) {
        Box(
            Modifier
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

/** Поле ввода. Рамка — линия, а не серый прямоугольник. */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    singleLine: Boolean = true
) {
    Column(modifier.fillMaxWidth()) {
        Q(label, Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = T.touchMin)
                .clip(RoundedCornerShape(T.rControl))
                .background(T.bg)
                .border(1.dp, T.hairline, RoundedCornerShape(T.rControl))
                .padding(horizontal = T.md, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Q(placeholder, Type.body, T.text3)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = Type.body.copy(color = T.text),
                cursorBrush = SolidColor(T.accent.fill),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Строка выбора: показывает, что выбрано, открывает список. */
@Composable
fun PickerRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Q(label, Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
        Pressable(onClick, Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = T.touchMin)
                    .clip(RoundedCornerShape(T.rControl))
                    .background(T.bg)
                    .border(1.dp, T.hairline, RoundedCornerShape(T.rControl))
                    .padding(horizontal = T.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Q(
                    value?.takeIf { it.isNotBlank() } ?: "Выбрать",
                    Type.body,
                    if (value.isNullOrBlank()) T.text3 else T.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                QIcon(ru.quickdeck.mobile.core.Ic.chevronRight, size = 20.dp, tint = T.text3)
            }
        }
    }
}

/** Выбор статуса — те же плашки, что и в списках. */
@Composable
fun StatusPicker(value: Status, options: List<Status>, onPick: (Status) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Q("Статус", Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(T.sm)
        ) {
            options.forEach { s ->
                val selected = s == value
                Pressable({ onPick(s) }) {
                    Box(
                        Modifier
                            .heightIn(min = 36.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (selected) s.tone().chip else T.bg)
                            .border(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) s.tone().fill else T.hairline,
                                RoundedCornerShape(percent = 50)
                            )
                            .padding(horizontal = T.md, vertical = T.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Q(s.label, Type.caption, if (selected) s.tone().ink else T.text2)
                    }
                }
            }
        }
    }
}
