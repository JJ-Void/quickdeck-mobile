package ru.quickdeck.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
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
        Q(label, Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
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
        Column(verticalArrangement = Arrangement.spacedBy(T.xs)) {
            Status.byStage(stage).forEach { s ->
                val selected = s == value
                Pressable({ onPick(s) }, Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = T.touchMin)
                            .clip(RoundedCornerShape(T.rControl))
                            .background(if (selected) s.tone().chip else T.surface)
                            .border(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) s.tone().fill else T.hairline,
                                RoundedCornerShape(T.rControl)
                            )
                            .padding(horizontal = T.md, vertical = T.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Q(s.label, Type.small, if (selected) s.tone().ink else T.text, 2, Modifier.weight(1f))
                        if (selected) QIcon(Ic.check, size = 18.dp, tint = s.tone().ink, stroke = 2f)
                    }
                }
            }
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
