package ru.quickdeck.mobile.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val NBSP = '\u00A0'

private val monthsShort = listOf(
    "янв.", "фев.", "мар.", "апр.", "мая", "июн.",
    "июл.", "авг.", "сент.", "окт.", "нояб.", "дек."
)
private val monthsLong = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря"
)

/** 1 250 000 ₽ — неразрывные пробелы в разрядах. */
fun money(v: Long): String {
    val s = kotlin.math.abs(v).toString()
    val sb = StringBuilder()
    for ((i, ch) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(NBSP)
        sb.append(ch)
    }
    val sign = if (v < 0) "−" else ""
    return "$sign$sb$NBSP₽"
}

fun parseDate(iso: String): LocalDate? =
    runCatching { LocalDate.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()

/** Ввод человеком: 30.09.2026, 30.09.26, 30/09/2026. */
fun parseHumanDate(text: String): String {
    val parts = text.split('.', '/', '-', ' ').filter { it.isNotBlank() }
    if (parts.size < 3) return ""
    val d = parts[0].toIntOrNull() ?: return ""
    val m = parts[1].toIntOrNull() ?: return ""
    var y = parts[2].toIntOrNull() ?: return ""
    if (y < 100) y += 2000
    return runCatching { LocalDate.of(y, m, d).toString() }.getOrElse { "" }
}

/** В списках коротко: 30 сент. */
fun dateShort(iso: String): String {
    val d = parseDate(iso) ?: return ""
    return "${d.dayOfMonth} ${monthsShort[d.monthValue - 1]}"
}

/** В карточке полностью: 30 сентября 2026. */
fun dateLong(iso: String): String {
    val d = parseDate(iso) ?: return ""
    return "${d.dayOfMonth} ${monthsLong[d.monthValue - 1]} ${d.year}"
}

/** Для ввода: 30.09.2026 */
fun dateInput(iso: String): String {
    val d = parseDate(iso) ?: return ""
    return "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year)
}

/** Просрочка — словом, а не только цветом. */
fun overdueDays(iso: String): Long {
    val d = parseDate(iso) ?: return 0
    val days = ChronoUnit.DAYS.between(d, LocalDate.now())
    return if (days > 0) days else 0
}

fun plural(n: Long, one: String, few: String, many: String): String {
    val n10 = n % 10
    val n100 = n % 100
    return when {
        n10 == 1L && n100 != 11L -> one
        n10 in 2..4 && (n100 < 12 || n100 > 14) -> few
        else -> many
    }
}

fun overdueText(iso: String): String? {
    val d = overdueDays(iso)
    if (d <= 0) return null
    return "просрочен на $d ${plural(d, "день", "дня", "дней")}"
}
