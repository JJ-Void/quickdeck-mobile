package ru.quickdeck.mobile.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun nowMs(): Long = System.currentTimeMillis()

/**
 * Стадия — шесть крупных этапов жизни договора. Именно по ним человек
 * ориентируется, а не по сорока одному статусу: сначала выбираешь стадию,
 * потом уточняешь статус внутри неё.
 */
@Serializable
enum class Stage(val label: String, val short: String) {
    LEAD("Лид / КП", "Лид"),
    CONTRACT("Договор", "Договор"),
    PRODUCTION("Производство", "Работа"),
    ACCEPTANCE("Приёмка и сдача", "Приёмка"),
    PAYMENT("Оплата", "Оплата"),
    PROBLEM("Проблема / стоп", "Стоп");

    companion object {
        fun of(text: String): Stage? {
            val t = text.trim()
            return entries.firstOrNull { it.label.equals(t, true) || it.name.equals(t, true) }
        }
    }
}

/**
 * Статусы — один в один со справочником таблицы, вместе со стадией,
 * нормативом в днях и признаком «договор заключён».
 *
 * Подписи менять нельзя: по ним таблица и телефон узнают друг друга.
 */
@Serializable
enum class Status(
    val label: String,
    val stage: Stage,
    val signed: Boolean,
    val norm: Int
) {
    POTENTIAL("Потенциальный", Stage.LEAD, false, 3),
    INPUT_REQUESTED("Запрошены исходные данные", Stage.LEAD, false, 3),
    SURVEY_SCHEDULED("Выезд / обследование назначено", Stage.LEAD, false, 3),
    QUOTE_CALC("Расчёт КП", Stage.LEAD, false, 2),
    QUOTE_SENT("КП направлено", Stage.LEAD, false, 3),
    QUOTE_REVIEW("КП на рассмотрении у заказчика", Stage.LEAD, false, 5),
    PRICE_TALKS("Торг / пересчёт цены", Stage.LEAD, false, 2),
    QUOTE_APPROVED("КП согласовано", Stage.LEAD, false, 2),

    DRAFTING("Подготовка договора", Stage.CONTRACT, false, 2),
    LEGAL_REVIEW("Договор на согласовании с юристом", Stage.CONTRACT, false, 3),
    CONTRACT_SENT("Договор направлен", Stage.CONTRACT, false, 5),
    DISPUTES("Разногласия по договору", Stage.CONTRACT, false, 3),
    AMENDING("Корректировка договора", Stage.CONTRACT, false, 3),
    SIGNED("Договор подписан", Stage.CONTRACT, true, 2),
    ADVANCE_INVOICED("Счёт на аванс выставлен", Stage.CONTRACT, true, 5),
    ADVANCE_PAID("Аванс получен", Stage.CONTRACT, true, 3),
    AWAITING_INPUT("Ожидание исходных данных", Stage.CONTRACT, true, 3),

    IN_WORK("В работе", Stage.PRODUCTION, true, 7),
    INTERNAL_CHECK("Внутренняя проверка (нормоконтроль)", Stage.PRODUCTION, true, 2),
    READY_TO_HAND("Готово к передаче", Stage.PRODUCTION, true, 1),

    HANDED("Документация передана", Stage.ACCEPTANCE, true, 5),
    CLIENT_REVIEW("На проверке у заказчика", Stage.ACCEPTANCE, true, 5),
    REMARKS("Получены замечания", Stage.ACCEPTANCE, true, 5),
    FIXING("Устранение замечаний", Stage.ACCEPTANCE, true, 3),
    REHANDED("Повторно передана", Stage.ACCEPTANCE, true, 3),
    EXPERTISE("Передана на экспертизу (ГГЭ)", Stage.ACCEPTANCE, true, 14),
    EXPERTISE_REMARKS("Замечания экспертизы", Stage.ACCEPTANCE, true, 7),
    EXPERTISE_OK("Положительное заключение получено", Stage.ACCEPTANCE, true, 2),
    ACT_SENT("Акт направлен", Stage.ACCEPTANCE, true, 5),
    ACT_SIGNED("Акт подписан", Stage.ACCEPTANCE, true, 3),
    DELIVERED("Сдан", Stage.ACCEPTANCE, true, 5),

    FINAL_INVOICED("Счёт на окончательный расчёт выставлен", Stage.PAYMENT, true, 5),
    PAID_PART("Оплачен частично", Stage.PAYMENT, true, 5),
    PAID_FULL("Оплачен полностью", Stage.PAYMENT, true, 10),
    WARRANTY("Гарантийный период", Stage.PAYMENT, true, 30),

    SUSPENDED("Приостановлен", Stage.PROBLEM, true, 3),
    AWAITING_CLIENT("Ожидание решения заказчика", Stage.PROBLEM, true, 3),
    OVERDUE("Просрочен", Stage.PROBLEM, true, 1),
    CLAIM("Претензия / спор", Stage.PROBLEM, true, 3),
    REJECTED("Отказ", Stage.PROBLEM, false, 10),
    TERMINATED("Расторжение договора", Stage.PROBLEM, false, 5);

    companion object {
        val default = POTENTIAL

        fun byStage(stage: Stage): List<Status> = entries.filter { it.stage == stage }

        /** Таблица отдаёт русскую подпись, а не имя константы. Понимаем оба написания. */
        fun of(text: String): Status {
            val t = text.trim()
            if (t.isEmpty()) return default
            entries.firstOrNull { it.label.equals(t, true) }?.let { return it }
            entries.firstOrNull { it.name.equals(t, true) }?.let { return it }
            return default
        }
    }
}

/** Статус сдачи — отдельный словарь, лист «Сдача». Пока только для чтения. */
@Serializable
enum class HandoverStatus(val label: String) {
    NOT_STARTED("Не начато"),
    IN_WORK("В работе"),
    READY("Готово к сдаче"),
    HANDED("Передано заказчику"),
    CLIENT_REVIEW("На проверке у заказчика"),
    REMARKS("Получены замечания"),
    FIXING("Устранение замечаний"),
    ACCEPTED("Принято");

    companion object {
        fun of(text: String): HandoverStatus =
            entries.firstOrNull { it.label.equals(text.trim(), true) } ?: NOT_STARTED
    }
}

/**
 * Общая часть всех записей реестра.
 *
 * updatedAt — когда запись правили в последний раз. По нему телефон и таблица
 * договариваются, чья версия свежее. deleted — надгробие: удалённую запись
 * нельзя выкинуть из списка, иначе она вернётся из таблицы при слиянии.
 *
 * row — номер строки в таблице. Нужен, чтобы писать правку ровно туда, откуда
 * запись пришла, и не трогать соседние столбцы с формулами.
 */
interface Row {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
    val row: Int
}

/** Заказчик. Реквизиты — как в карточке на листе «Заказчики». */
@Serializable
data class Party(
    override val id: String = newId(),
    val name: String = "",                 // краткое, оно же ключ связи
    val fullName: String = "",
    val inn: String = "",
    val kpp: String = "",
    val ogrn: String = "",
    val legalAddress: String = "",
    val director: String = "",
    val phone: String = "",
    val email: String = "",
    val bank: String = "",
    val account: String = "",
    val bik: String = "",
    val note: String = "",
    override val updatedAt: Long = 0L,
    override val deleted: Boolean = false,
    override val row: Int = 0
) : Row

/**
 * Объект. Статуса у объекта нет — он живёт на договорах, а объект показывает
 * стадию своих активных договоров. В таблице ровно так же.
 *
 * progress — поле телефона, в таблице такого столбца нет. Это быстрая отметка
 * готовности для себя, она никуда не уезжает.
 */
@Serializable
data class Site(
    override val id: String = newId(),
    val code: String = "",                 // О-001, считается таблицей
    val name: String = "",                 // краткое наименование, ключ связи
    val fullName: String = "",
    val customerId: String? = null,
    val address: String = "",
    val buildingType: String = "",
    val area: Double = 0.0,
    val unit: String = "",
    val note: String = "",
    val progress: Int = 0,                 // только в телефоне
    override val updatedAt: Long = 0L,
    override val deleted: Boolean = false,
    override val row: Int = 0
) : Row

/** Одна оплата из трёх на листе «Договора»: условие, доля, факт. */
@Serializable
data class Payment(
    val condition: String = "",
    val share: Double = 0.0,
    val paid: Boolean = false
) {
    val empty: Boolean get() = condition.isBlank() && share == 0.0 && !paid
}

@Serializable
data class Contract(
    override val id: String = newId(),
    val code: String = "",                 // Д-001, считается таблицей
    val siteId: String? = null,
    val workKind: String = "",             // вид работы из справочника
    val legalEntity: String = "",          // исполнитель, юр. лицо
    val status: Status = Status.default,
    val amount: Long = 0,                  // цена, рубли целые
    val start: String = "",                // ISO
    val end: String = "",                  // ISO
    val responsible: String = "",
    val coExecutors: List<String> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val note: String = "",
    override val updatedAt: Long = 0L,
    override val deleted: Boolean = false,
    override val row: Int = 0
) : Row {
    /** Метка, которой договор зовётся в таблице: «объект · вид работы». */
    fun label(siteName: String?): String =
        listOfNotNull(siteName?.takeIf { it.isNotBlank() }, workKind.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .ifBlank { code.ifBlank { "Без номера" } }

    val paidShare: Double get() = payments.filter { it.paid }.sumOf { it.share }
    val paidAmount: Long get() = (amount * paidShare).toLong()
    val restAmount: Long get() = amount - paidAmount
}

/** Веха сдачи. Пока приложение её только читает. */
@Serializable
data class Handover(
    override val id: String = newId(),
    val code: String = "",
    val contractId: String? = null,
    val what: String = "",
    val kind: String = "",
    val plan: String = "",
    val fact: String = "",
    val status: HandoverStatus = HandoverStatus.NOT_STARTED,
    val responsible: String = "",
    val note: String = "",
    override val updatedAt: Long = 0L,
    override val deleted: Boolean = false,
    override val row: Int = 0
) : Row

/** Способ связи с сотрудником: мессенджер и адрес в нём. */
@Serializable
data class Chat(
    val kind: String = "telegram",   // telegram, whatsapp, max, vk, email
    val handle: String = ""          // @ник, номер или адрес
)

/**
 * Сотрудник — работник организации, как на листе «Сотрудники».
 * Это не заказчик и не юрлицо-исполнитель: у него есть отдел, должность
 * и каналы связи, по которым руководитель звонит и ставит задачи.
 */
@Serializable
data class Employee(
    override val id: String = newId(),
    val name: String = "",
    val department: String = "",
    val position: String = "",
    val tabNumber: String = "",
    val employment: String = "",      // официально / договор / …
    val location: String = "",        // офис, вахта, удалёнка
    val phones: List<String> = emptyList(),
    val chats: List<Chat> = emptyList(),
    val note: String = "",
    override val updatedAt: Long = 0L,
    override val deleted: Boolean = false,
    override val row: Int = 0
) : Row {
    val initials: String
        get() = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercaseChar().toString() }
}

/**
 * Шаблон быстрого сообщения. Переменные подставляются при отправке:
 * {Имя}, {Объект}, {Договор}, {Срок}, {Статус}.
 */
@Serializable
data class MsgTemplate(
    val id: String = newId(),
    val title: String = "",
    val body: String = "",
    val builtin: Boolean = false
)

/** Вид работы знает свой отдел — в таблице отдел подставляется формулой. */
@Serializable
data class WorkKind(
    val name: String = "",
    val department: String = ""
)

/**
 * Справочники приезжают из таблицы и лежат рядом с данными, чтобы формы
 * работали и без сети. Значения по умолчанию — то, что в таблице сейчас.
 */
@Serializable
data class Refs(
    val workKinds: List<WorkKind> = defaultWorkKinds,
    val buildingTypes: List<String> = defaultBuildingTypes,
    val units: List<String> = defaultUnits,
    val legalEntities: List<String> = defaultLegalEntities,
    val departments: List<String> = defaultDepartments,
    val payStages: List<String> = defaultPayStages
) {
    fun departmentOf(workKind: String): String =
        workKinds.firstOrNull { it.name.equals(workKind.trim(), true) }?.department ?: ""
}

@Serializable
data class Db(
    val customers: List<Party> = emptyList(),
    val employees: List<Employee> = emptyList(),
    val sites: List<Site> = emptyList(),
    val contracts: List<Contract> = emptyList(),
    val handovers: List<Handover> = emptyList(),
    val templates: List<MsgTemplate> = defaultTemplates,
    val refs: Refs = Refs()
) {
    val liveCustomers: List<Party> get() = customers.filterNot { it.deleted }
    val liveEmployees: List<Employee> get() = employees.filterNot { it.deleted }
    val liveSites: List<Site> get() = sites.filterNot { it.deleted }
    val liveContracts: List<Contract> get() = contracts.filterNot { it.deleted }
    val liveHandovers: List<Handover> get() = handovers.filterNot { it.deleted }

    fun customer(id: String?) = customers.firstOrNull { it.id == id && !it.deleted }
    fun employee(id: String?) = employees.firstOrNull { it.id == id && !it.deleted }
    fun employeeByName(name: String): Employee? =
        liveEmployees.firstOrNull { it.name.equals(name.trim(), true) }
    fun site(id: String?) = sites.firstOrNull { it.id == id && !it.deleted }
    fun contract(id: String?) = contracts.firstOrNull { it.id == id && !it.deleted }

    fun contractsOfSite(id: String) = liveContracts.filter { it.siteId == id }
    fun sitesOfCustomer(id: String) = liveSites.filter { it.customerId == id }
    fun handoversOfContract(id: String) = liveHandovers.filter { it.contractId == id }

    /** Договоры, по которым ещё идёт работа: не отказ, не расторжение, не архив оплаты. */
    fun activeContractsOfSite(id: String) = contractsOfSite(id).filter {
        it.status != Status.REJECTED && it.status != Status.TERMINATED && it.status != Status.WARRANTY
    }

    /** У объекта своего статуса нет — показываем стадию его активных договоров. */
    fun stageOfSite(id: String): Stage? =
        activeContractsOfSite(id).minByOrNull { it.status.stage.ordinal }?.status?.stage

    fun count(section: Section): Int = when (section) {
        Section.SITES -> liveSites.size
        Section.CONTRACTS -> liveContracts.size
        Section.CUSTOMERS -> liveCustomers.size
        Section.STAFF -> liveEmployees.size
    }

    fun customerByName(name: String): Party? =
        liveCustomers.firstOrNull { it.name.equals(name.trim(), true) }

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
    employees = mergeRows(local.employees, remote.employees),
    sites = mergeRows(local.sites, remote.sites),
    contracts = mergeRows(local.contracts, remote.contracts),
    handovers = mergeRows(local.handovers, remote.handovers),
    // Шаблоны и справочники живут по своим правилам: шаблоны — только на
    // телефоне, справочники приходят из таблицы целиком.
    templates = local.templates,
    refs = if (remote.refs.workKinds.isNotEmpty()) remote.refs else local.refs
)

/**
 * Разделы реестра. Порядок — по частоте обращения, а не по логике сущностей:
 * чаще всего нужен объект, потом договор, стороны — реже.
 */
enum class Section(val title: String, val one: String) {
    SITES("Объекты", "Объект"),
    CONTRACTS("Договоры", "Договор"),
    CUSTOMERS("Заказчики", "Заказчик"),
    STAFF("Сотрудники", "Сотрудник")
}

// --- значения справочников по умолчанию ---------------------------------
// Ровно то, что лежит на листе «Справочники». Приезжает обменом и заменяется.

val defaultDepartments = listOf(
    "ОТДЕЛ ПТО", "СМЕТНЫЙ ОТДЕЛ", "ОТДЕЛ ПРОЕКТИРОВАНИЯ", "МЕНЕДЖМЕНТ",
    "ЮР отдел", "СНАБЖЕНИЕ", "СТРОИТЕЛЬНО МОНТАЖНЫЙ ОТДЕЛ", "ОТДЕЛ ОБСЛЕДОВАНИЯ"
)

val defaultWorkKinds = listOf(
    WorkKind("Обследование", "ОТДЕЛ ОБСЛЕДОВАНИЯ"),
    WorkKind("Техническое заключение (ТЗК)", "ОТДЕЛ ОБСЛЕДОВАНИЯ"),
    WorkKind("Обмерные работы", "ОТДЕЛ ОБСЛЕДОВАНИЯ"),
    WorkKind("Инженерные изыскания", "ОТДЕЛ ПРОЕКТИРОВАНИЯ"),
    WorkKind("Проектирование ПД", "ОТДЕЛ ПРОЕКТИРОВАНИЯ"),
    WorkKind("Проектирование РД", "ОТДЕЛ ПРОЕКТИРОВАНИЯ"),
    WorkKind("Проектирование (стадия не разделяется)", "ОТДЕЛ ПРОЕКТИРОВАНИЯ"),
    WorkKind("Корректировка проектной документации", "ОТДЕЛ ПРОЕКТИРОВАНИЯ"),
    WorkKind("Исполнительная документация (ИД)", "ОТДЕЛ ПТО"),
    WorkKind("Сметная документация", "СМЕТНЫЙ ОТДЕЛ"),
    WorkKind("Сопровождение экспертизы", "ОТДЕЛ ПРОЕКТИРОВАНИЯ"),
    WorkKind("Авторский надзор", "ОТДЕЛ ПРОЕКТИРОВАНИЯ"),
    WorkKind("Строительный контроль", "ОТДЕЛ ПТО"),
    WorkKind("СМР: демонтаж", "СТРОИТЕЛЬНО МОНТАЖНЫЙ ОТДЕЛ"),
    WorkKind("СМР: общестроительные", "СТРОИТЕЛЬНО МОНТАЖНЫЙ ОТДЕЛ"),
    WorkKind("СМР: отделка", "СТРОИТЕЛЬНО МОНТАЖНЫЙ ОТДЕЛ"),
    WorkKind("СМР: кровля", "СТРОИТЕЛЬНО МОНТАЖНЫЙ ОТДЕЛ"),
    WorkKind("СМР: фасад", "СТРОИТЕЛЬНО МОНТАЖНЫЙ ОТДЕЛ"),
    WorkKind("СМР: инженерные сети", "СТРОИТЕЛЬНО МОНТАЖНЫЙ ОТДЕЛ"),
    WorkKind("СМР: электромонтаж", "СТРОИТЕЛЬНО МОНТАЖНЫЙ ОТДЕЛ"),
    WorkKind("Пусконаладочные работы", "СТРОИТЕЛЬНО МОНТАЖНЫЙ ОТДЕЛ"),
    WorkKind("Прочее", "МЕНЕДЖМЕНТ")
)

val defaultBuildingTypes = listOf(
    "Административное здание", "Жилой дом многоквартирный", "Индивидуальный жилой дом",
    "Производственный корпус", "Складское здание", "Образовательное учреждение",
    "Медицинское учреждение", "Спортивный объект", "Торговое здание", "Объект культуры",
    "Инженерное сооружение", "Наружные сети", "Дорога / благоустройство",
    "Линейный объект", "Прочее"
)

val defaultUnits = listOf("м²", "м³", "п.м", "га", "шт.", "компл.", "объект")

val defaultLegalEntities = listOf("ООО \"РОТОНДА\"", "ИП Гильфанов Р. Р.", "ИП Фомин Д. А.")

val defaultPayStages = listOf(
    "После заключения договора",
    "После передачи заказчику",
    "После выхода с ГГЭ",
    "После подписания акта",
    "После получения замечаний",
    "Ежемесячно по КС-2",
    "По факту завершения этапа",
    "Ежемесячными равными платежами с зачетом аванса",
    "Еженедельными равными платежами с зачетом аванса",
    "За каждую сметную строчку"
)

/** Заготовки сообщений. Их можно менять и удалять — это просто стартовый набор. */
val defaultTemplates = listOf(
    MsgTemplate(id = "t-check", title = "Проверить объект", builtin = true,
        body = "{Имя}, посмотри по объекту {Объект} — нужно проверить состояние и отписаться."),
    MsgTemplate(id = "t-docs", title = "Подготовить документы", builtin = true,
        body = "{Имя}, по {Договор} подготовь комплект документов. Срок — {Срок}."),
    MsgTemplate(id = "t-info", title = "Нужна информация", builtin = true,
        body = "{Имя}, нужна информация по объекту {Объект}. Когда сможешь прислать?"),
    MsgTemplate(id = "t-client", title = "Связаться с заказчиком", builtin = true,
        body = "{Имя}, свяжись с заказчиком по объекту {Объект} и уточни статус."),
    MsgTemplate(id = "t-deadline", title = "Проверить сроки", builtin = true,
        body = "{Имя}, по {Договор} срок {Срок}. Успеваем?"),
    MsgTemplate(id = "t-when", title = "Когда завершишь", builtin = true,
        body = "{Имя}, когда сможешь завершить работу по {Объект}?"),
    MsgTemplate(id = "t-fix", title = "Внести изменения", builtin = true,
        body = "{Имя}, по {Договор} нужно внести изменения. Подробности пришлю следом."),
    MsgTemplate(id = "t-done", title = "Задача выполнена?", builtin = true,
        body = "{Имя}, задача по {Объект} выполнена?")
)

/** Подстановка переменных шаблона. Незаполненное просто исчезает. */
fun fillTemplate(
    body: String,
    name: String = "",
    site: String = "",
    contract: String = "",
    due: String = "",
    status: String = ""
): String = body
    .replace("{Имя}", name.trim().split(" ").firstOrNull().orEmpty())
    .replace("{ФИО}", name)
    .replace("{Объект}", site)
    .replace("{Договор}", contract)
    .replace("{Срок}", due)
    .replace("{Статус}", status)
    .replace(Regex("\\s{2,}"), " ")
    .trim()
