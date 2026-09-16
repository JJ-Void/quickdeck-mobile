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

/**
 * Manrope лежит внутри приложения — вариативный файл по оси wght.
 *
 * Объявлены все веса, которыми пользуется шкала, включая 800: без явной
 * записи система подставляет ближайший и очаг экрана выходит жидким.
 */
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.W400),
    Font(R.font.manrope, FontWeight.W500),
    Font(R.font.manrope, FontWeight.W600),
    Font(R.font.manrope, FontWeight.W700),
    Font(R.font.manrope, FontWeight.W800)
)

/** Шкала приложения. Новый размер заводится только под новую роль. */
object Type {
    private fun base(
        size: Number,
        weight: FontWeight,
        tracking: Double,
        lineHeightEm: Float,
        tabular: Boolean = false
    ) = TextStyle(
        fontFamily = Manrope,
        fontSize = size.toFloat().sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
        lineHeight = (size.toFloat() * lineHeightEm).sp,
        fontFeatureSettings = if (tabular) "tnum" else null
    )

    /**
     * Шкала с разрывом.
     *
     * Соседние размеры отличаются в полтора-два раза, а не на два пункта.
     * Шкала без разрывов — главная причина, по которой экран «весь в куче»:
     * когда всё примерно одного кегля, глазу не за что зацепиться, и он
     * мечется. Проверено тепловой картой, а не на глаз.
     *
     *   hero 62 · big 44 · title 30 · heading 19 · body 15 · label 11
     */

    /** Очаг экрана. Ровно один на экран, слева, с воздухом вокруг. */
    val hero = base(62, FontWeight.W800, -3.2, 0.92f, tabular = true)

    /** Значение в карточке. Второй по величине знак, уже не очаг. */
    val big = base(44, FontWeight.W800, -2.0, 1.0f, tabular = true)

    /** Имя записи на её собственном экране. */
    val title = base(30, FontWeight.W800, -1.1, 1.08f)

    /** Имя записи в списке, счётчик папки. */
    val heading = base(19, FontWeight.W700, -0.5, 1.2f)

    /** Основной текст. */
    val body = base(15, FontWeight.W500, -0.15, 1.45f)

    /** Вторичный текст: подписи под очагом, значения настроек. */
    val small = base(13.5f, FontWeight.W500, 0.0, 1.45f)

    /**
     * Метка-рубрика. Только капслоком и в разрядку: так она читается как
     * ярлык раздела, а не как ещё одна строка данных.
     */
    val label = base(11, FontWeight.W700, 1.6, 1.25f)

    /** Подпись вкладки: мелкая, но без разрядки — это слово, а не рубрика. */
    val tab = base(10.5f, FontWeight.W700, 0.0, 1.2f)

    /** Числа в строке списка. */
    val amount = base(17, FontWeight.W700, -0.35, 1.25f, tabular = true)

    // --- имена, которые ещё зовут старые экраны ---
    val display = hero
    val caption = label
    val smallNum = small
}

/**
 * Единственный способ нарисовать текст в приложении.
 *
 * Material тут не используется: стиль приходит из шкалы, а не из темы.
 * Всё, что рисует текст мимо Q, рано или поздно расходится по кеглю и весу —
 * именно так у нас разъезжались экраны.
 */
@Composable
fun Q(
    text: String,
    style: TextStyle = Type.body,
    color: Color = T.ink,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) = BasicText(
    text = text,
    modifier = modifier,
    style = style.copy(color = color),
    maxLines = maxLines,
    overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
)
