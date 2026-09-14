package ru.quickdeck.mobile.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

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
    }
}

/** Заказчик и исполнитель устроены одинаково — различает только список. */
@Serializable
data class Party(
    val id: String = newId(),
    val name: String = "",
    val inn: String = "",
    val contact: String = "",
    val phone: String = "",
    val note: String = ""
)

@Serializable
data class Site(
    val id: String = newId(),
    val name: String = "",
    val address: String = "",
    val customerId: String? = null,
    val status: Status = Status.WORK,
    val deadline: String = "",      // ISO: 2026-09-30
    val progress: Int = 0,          // 0..100
    val note: String = ""
)

@Serializable
data class Contract(
    val id: String = newId(),
    val number: String = "",
    val siteId: String? = null,
    val customerId: String? = null,
    val contractorId: String? = null,
    val amount: Long = 0,           // рубли, целые
    val status: Status = Status.DRAFT,
    val start: String = "",
    val end: String = "",
    val note: String = ""
)

@Serializable
data class Db(
    val customers: List<Party> = emptyList(),
    val contractors: List<Party> = emptyList(),
    val sites: List<Site> = emptyList(),
    val contracts: List<Contract> = emptyList()
) {
    fun customer(id: String?) = customers.firstOrNull { it.id == id }
    fun contractor(id: String?) = contractors.firstOrNull { it.id == id }
    fun site(id: String?) = sites.firstOrNull { it.id == id }
    fun contractsOfSite(id: String) = contracts.filter { it.siteId == id }
    fun sitesOfCustomer(id: String) = sites.filter { it.customerId == id }
}

/** Четыре раздела колеса. Порядок — как в жизни: кто, что, чем, кем. */
enum class Section(val title: String, val one: String) {
    CUSTOMERS("Заказчики", "Заказчик"),
    SITES("Объекты", "Объект"),
    CONTRACTS("Договоры", "Договор"),
    CONTRACTORS("Исполнители", "Исполнитель")
}
