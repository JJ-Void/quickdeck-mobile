package ru.quickdeck.mobile

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.quickdeck.mobile.data.Contract
import ru.quickdeck.mobile.data.Db
import ru.quickdeck.mobile.data.Site
import ru.quickdeck.mobile.data.Status
import ru.quickdeck.mobile.data.mergeDb
import ru.quickdeck.mobile.data.summary

/**
 * Сводка считается по договорам и только по живым деньгам. Правила простые,
 * но ошибиться в них легко, а ошибка тихая: цифра просто окажется больше
 * настоящей, и никто этого не заметит.
 */
class SummaryTest {

    private fun contract(id: String, site: String, status: Status, amount: Long) =
        Contract(id = id, siteId = site, workKind = "Проект", status = status, amount = amount)

    private val db = Db(
        sites = listOf(Site(id = "s1", name = "Цимлянская 17")),
        contracts = listOf(
            contract("c1", "s1", Status.IN_WORK, 100_000),
            contract("c2", "s1", Status.HANDED, 200_000),
            contract("c3", "s1", Status.QUOTE_SENT, 500_000),      // ещё не договор
            contract("c4", "s1", Status.PAID_FULL, 300_000),       // архив
            contract("c5", "s1", Status.REJECTED, 400_000),        // архив
            contract("c6", "s1", Status.FINAL_INVOICED, 50_000)    // ждёт оплаты
        )
    )

    @Test
    fun `в работе считаются договоры, а не объекты`() {
        // Шесть договоров на одном объекте: «один объект в работе» ничего
        // не говорит о загрузке, а три заключённых незакрытых — говорят.
        assertEquals(3, db.summary().inWork)
    }

    @Test
    fun `незаключённые уходят в потенциальные и не попадают в сумму`() {
        val s = db.summary()
        assertEquals(1, s.potential)
        assertEquals(500_000L, s.potentialAmount)
        assertEquals(100_000L + 200_000L + 50_000L, s.contracted)
    }

    @Test
    fun `архив не попадает ни в работу, ни в сумму`() {
        val s = db.summary()
        assertEquals(2, s.archived)
        assertEquals(350_000L, s.contracted)
    }

    @Test
    fun `ждёт оплаты считается по стадии`() {
        assertEquals(1, db.summary().awaitingPay)
    }

    @Test
    fun `удалённые договоры в сводку не идут`() {
        val withDeleted = db.copy(
            contracts = db.contracts + contract("c7", "s1", Status.IN_WORK, 999).copy(deleted = true)
        )
        assertEquals(db.summary().inWork, withDeleted.summary().inWork)
    }

    @Test
    fun `задачи переживают обмен с таблицей`() {
        // В таблице столбца задач нет, поэтому приехавшая оттуда запись
        // всегда пустая. Без переноса первый же обмен стирал бы задачи.
        val mine = Db(contracts = listOf(
            contract("c1", "s1", Status.IN_WORK, 10).copy(
                tasks = listOf(ru.quickdeck.mobile.data.ContractTask(text = "Забрать исходники")),
                updatedAt = 100
            )
        ))
        val fromSheet = Db(contracts = listOf(
            contract("c1", "s1", Status.HANDED, 10).copy(updatedAt = 200)
        ))
        val merged = mergeDb(mine, fromSheet)
        val c = merged.contracts.first()
        assertEquals(Status.HANDED, c.status)              // таблица свежее — статус её
        assertEquals(1, c.tasks.size)                      // а задачи остались наши
        assertEquals("Забрать исходники", c.tasks[0].text)
    }
}
