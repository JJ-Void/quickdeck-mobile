package ru.quickdeck.mobile.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun nowMs(): Long = System.currentTimeMillis()

/**
 * Один словарь статусов на все сущности — объекты, договоры, акты.
 * Разные словари для похожих сущностей ломают восприятие быстрее всего.
 */
@Serializable
enum class Status(val label: String) {
    DRAFT("Черновик"),
    WORK("В работе"),
    WAIT("Ждёт приёмки"),
    DONE("Сдан"),
    OVERDUE("Просрочен"),
    ARCHIVE("Архив");

    companion object {
        val forSite = listOf(DRAFT, WORK, WAIT, DONE, ARCHIVE)
        val forContract = listOf(DRAFT, WORK, WAIT, DONE, ARCHIVE)

        /** Таблица отдаёт русскую подпись, а не имя константы. Понимаем оба написания. */
        fun of(text: String): Status {
            val t = text.trim()
            if (t.isEmpty()) return DRAFT
            entries.firstOrNull { it.name.equals(t, true) }?.let { return it }
            entries.firstOrNull { it.label.equals(t, true) }?.let { return it }
            return DRAFT
        }
    }
}

/**
 * Общая часть всех записей реестра.
 *
 * updatedAt — когда запись правили в последний раз. По нему телефон и таблица
 * договариваются, чья версия свежее, без всякого сервера посередине.
 *
 * deleted — надгробие. Удалённую запись нельзя просто выкинуть из списка:
 * при следующем слиянии она вернётся из таблицы. Поэтому удаление — это
 * пометка, и она тоже уезжает в таблицу.
 */
interface Row {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
}

/** Заказчик и исполнитель устроены одинаково — различает только список. */
@Serializable
data class Party(
    override val id: String = newId(),
    val name: String = "",
    val inn: String = "",
    val contact: String = "",
    val phone: String = "",
    val note: String = "",
    override val updatedAt: Long = 0L,
    override val deleted: Boolean = false
) : Row

@Serializable
data class Site(
    override val id: String = newId(),
    val name: String = "",
    val address: String = "",
    val customerId: String? = null,
    val status: Status = Status.WORK,
    val deadline: String = "",      // ISO: 2026-09-30
    val progress: Int = 0,          // 0..100
    val note: String = "",
    override val updatedAt: Long = 0L,
    override val deleted: Boolean = false
) : Row

@Serializable
data class Contract(
    override val id: String = newId(),
    val number: String = "",
    val siteId: String? = null,
    val customerId: String? = null,
    val contractorId: String? = null,
    val amount: Long = 0,           // рубли, целые
    val status: Status = Status.DRAFT,
    val start: String = "",
    val end: String = "",
    val note: String = "",
    override val updatedAt: Long = 0L,
    override val deleted: Boolean = false
) : Row

@Serializable
data class Db(
    val customers: List<Party> = emptyList(),
    val contractors: List<Party> = emptyList(),
    val sites: List<Site> = emptyList(),
    val contracts: List<Contract> = emptyList()
) {
    // Живые записи — то, что видит человек. Надгробия наружу не выходят.
    val liveCustomers: List<Party> get() = customers.filterNot { it.deleted }
    val liveContractors: List<Party> get() = contractors.filterNot { it.deleted }
    val liveSites: List<Site> get() = sites.filterNot { it.deleted }
    val liveContracts: List<Contract> get() = contracts.filterNot { it.deleted }

    fun customer(id: String?) = customers.firstOrNull { it.id == id && !it.deleted }
    fun contractor(id: String?) = contractors.firstOrNull { it.id == id && !it.deleted }
    fun site(id: String?) = sites.firstOrNull { it.id == id && !it.deleted }
    fun contract(id: String?) = contracts.firstOrNull { it.id == id && !it.deleted }

    fun contractsOfSite(id: String) = liveContracts.filter { it.siteId == id }
    fun sitesOfCustomer(id: String) = liveSites.filter { it.customerId == id }

    /** Сколько записей в разделе — цифра под названием в колесе. */
    fun count(section: Section): Int = when (section) {
        Section.SITES -> liveSites.size
        Section.CONTRACTS -> liveContracts.size
        Section.CUSTOMERS -> liveCustomers.size
        Section.CONTRACTORS -> liveContractors.size
    }

    /** Таблица связывает записи именем, а не идентификатором. */
    fun customerByName(name: String): Party? =
        liveCustomers.firstOrNull { it.name.equals(name.trim(), true) }

    fun contractorByName(name: String): Party? =
        liveContractors.firstOrNull { it.name.equals(name.trim(), true) }

    fun siteByName(name: String): Site? =
        liveSites.firstOrNull { it.name.equals(name.trim(), true) }
}

/**
 * Слияние двух версий одного списка. Побеждает та запись, которую правили
 * позже. Запись, которой нет у одной из сторон, просто добавляется.
 */
fun <T : Row> mergeRows(local: List<T>, remote: List<T>): List<T> {
    val out = LinkedHashMap<String, T>()
    local.forEach { out[it.id] = it }
    remote.forEach { r ->
        val l = out[r.id]
        out[r.id] = if (l == null || r.updatedAt > l.updatedAt) r else l
    }
    return out.values.toList()
}

fun mergeDb(local: Db, remote: Db): Db = Db(
    customers = mergeRows(local.customers, remote.customers),
    contractors = mergeRows(local.contractors, remote.contractors),
    sites = mergeRows(local.sites, remote.sites),
    contracts = mergeRows(local.contracts, remote.contracts)
)

/**
 * Разделы реестра. Порядок — по частоте обращения, а не по логике сущностей:
 * чаще всего нужен объект, потом договор, стороны — реже.
 */
enum class Section(val title: String, val one: String) {
    SITES("Объекты", "Объект"),
    CONTRACTS("Договоры", "Договор"),
    CUSTOMERS("Заказчики", "Заказчик"),
    CONTRACTORS("Исполнители", "Исполнитель")
}
