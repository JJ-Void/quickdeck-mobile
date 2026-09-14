package ru.quickdeck.mobile.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import ru.quickdeck.mobile.R

/** Manrope лежит внутри приложения. Вариативный файл, ось wght. */
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.W400),
    Font(R.font.manrope, FontWeight.W500),
    Font(R.font.manrope, FontWeight.W600),
    Font(R.font.manrope, FontWeight.W700)
)

/** Шесть размеров. Седьмой заводится только под новую роль. */
object Type {
    private fun base(
        size: Int,
        weight: FontWeight,
        tracking: Double,
        lineHeightEm: Float,
        tabular: Boolean = false
    ) = TextStyle(
        fontFamily = Manrope,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        lineHeight = (size * lineHeightEm).sp,
        fontFeatureSettings = if (tabular) "tnum" else null
    )

    /** Крупное число: сумма, счётчик. */
    val display = base(28, FontWeight.W700, -0.5, 1.0f, tabular = true)

    /** Заголовок экрана. */
    val title = base(22, FontWeight.W700, -0.3, 1.25f)

    /** Заголовок карточки, имя объекта. */
    val heading = base(17, FontWeight.W600, -0.2, 1.25f)

    /** Основной текст. */
    val body = base(15, FontWeight.W400, 0.0, 1.45f)

    /** Вторичный текст, строка списка. */
    val small = base(13, FontWeight.W500, 0.0, 1.45f)

    /** Подпись, статус, дата. */
    val caption = base(11, FontWeight.W600, 0.3, 1.25f)

    /** Числа в столбик — всегда табличными. */
    val amount = base(15, FontWeight.W600, 0.0, 1.25f, tabular = true)
    val smallNum = base(13, FontWeight.W500, 0.0, 1.45f, tabular = true)
}

/**
 * Единственный способ нарисовать текст в приложении.
 * Material тут не используется — стиль задан системой, а не темой.
 */
@Composable
fun Q(
    text: String,
    style: TextStyle = Type.body,
    color: Color = T.text,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) = BasicText(
    text = text,
    modifier = modifier,
    style = style.copy(color = color),
    maxLines = maxLines,
    overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
)
