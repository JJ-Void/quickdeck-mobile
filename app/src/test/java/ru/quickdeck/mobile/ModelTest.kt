package ru.quickdeck.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.quickdeck.mobile.data.Contract
import ru.quickdeck.mobile.data.Db
import ru.quickdeck.mobile.data.Party
import ru.quickdeck.mobile.data.Site
import ru.quickdeck.mobile.data.TaskDraft
import ru.quickdeck.mobile.data.codeLabel
import ru.quickdeck.mobile.data.deduped
import ru.quickdeck.mobile.data.fillTemplate
import ru.quickdeck.mobile.data.missingVars
import ru.quickdeck.mobile.data.subName
import ru.quickdeck.mobile.data.templateValues
import ru.quickdeck.mobile.data.unknownVars

/**
 * Проверяется то, что ломалось: подстановка, эхо имени и черновик задачи.
 * Всё это чистые функции — разметка и Android тут не нужны.
 */
class ModelTest {

    private val values = templateValues(
        name = "Иванов Иван Иванович",
        site = "Цимлянская 17",
        contract = "Цимлянская 17 · ИД",
        due = "31.12.2026",
        status = "В работе"
    )

    @Test
    fun `подставляет имя и объект`() {
        val out = fillTemplate("{Имя}, по объекту {Объект} нужно отписаться.", values)
        assertEquals("Иванов, по объекту Цимлянская 17 нужно отписаться.", out)
    }

    @Test
    fun `повторное применение не накапливает подстановки`() {
        val template = "{Имя}, по {Объект} — {Статус}"
        val once = fillTemplate(template, values)
        val twice = fillTemplate(template, values)
        assertEquals(once, twice)
        // Исходник цел: из него и считается результат, а не из уже подставленного.
        assertTrue(template.contains("{Объект}"))
    }

    @Test
    fun `неизвестная переменная видна, известная — нет`() {
        val bad = unknownVars("{Имя}, по {Обьект} нужно")
        assertEquals(listOf("Обьект"), bad)
        assertTrue(unknownVars("{Имя} {Объект} {Договор} {Срок} {Статус} {ФИО}").isEmpty())
    }

    @Test
    fun `пустые данные попадают в предупреждение`() {
        val empty = templateValues(name = "Пётр")
        assertEquals(listOf("Объект"), missingVars("{Имя}, по {Объект}", empty))
        assertTrue(missingVars("{Имя}, привет", empty).isEmpty())
    }

    @Test
    fun `эхо имени гасится, настоящие данные остаются`() {
        val db = Db(
            sites = listOf(
                Site(name = "Цимлянская 17", fullName = "цимлянская 17", code = "Цимлянская 17"),
                Site(name = "Херсонская 7а", fullName = "Общежитие № 3 ЛГАКИ", code = "О-002")
            ),
            customers = listOf(Party(name = "ЭнергоКомплекс", fullName = "ЭнергоКомплекс")),
            contracts = listOf(Contract(workKind = "ИД", code = "ИД"))
        ).deduped()

        assertEquals("", db.sites[0].fullName)
        assertEquals("", db.sites[0].code)
        assertEquals("Общежитие № 3 ЛГАКИ", db.sites[1].fullName)
        assertEquals("О-002", db.sites[1].code)
        assertEquals("", db.customers[0].fullName)
        assertEquals("", db.contracts[0].code)
    }

    @Test
    fun `имя не выводится вторым полем`() {
        val site = Site(name = "Цимлянская 17", fullName = "Цимлянская 17", code = "Цимлянская 17")
        assertEquals("", site.subName)
        assertEquals("", site.codeLabel)

        val real = Site(name = "Цимлянская 17", fullName = "РВР по адресу…", code = "О-001")
        assertEquals("РВР по адресу…", real.subName)
        assertEquals("О-001", real.codeLabel)
    }

    @Test
    fun `привязка договора не дублирует номер в заголовке`() {
        val c = Contract(workKind = "ИД", code = "ИД")
        assertEquals("", c.codeLabel("Цимлянская 17"))
        assertEquals("Д-007", Contract(workKind = "ИД", code = "Д-007").codeLabel("Цимлянская 17"))
    }

    @Test
    fun `пустой черновик не сохраняется`() {
        assertTrue(TaskDraft().isEmpty)
        assertTrue(!TaskDraft(siteId = "s1").isEmpty)
        assertTrue(!TaskDraft(body = "текст").isEmpty)
    }
}
