package ru.quickdeck.mobile.data

/**
 * Сводка по компании — то, ради чего руководитель открывает панель, ещё не
 * зная, что именно хочет посмотреть.
 *
 * Считается по договорам, а не по объектам. Объект — это адрес; работа,
 * деньги и сроки живут в договоре, и на одном объекте их может быть
 * несколько. «Пять объектов в работе» ничего не говорит о загрузке, «девять
 * договоров в работе» — говорит.
 *
 * В деньги попадают только заключённые и не закрытые договоры. Архив —
 * история, его сумма к сегодняшней выручке отношения не имеет.
 * Незаключённые — это ещё не деньги, а намерение, поэтому они считаются
 * отдельной строкой «Потенциально» и в общую сумму не входят.
 */
data class Summary(
    /** Заключённые и незакрытые — реальная загрузка. */
    val inWork: Int,
    /** До заключения договора: лид, КП, согласование. Ещё не деньги. */
    val potential: Int,
    val potentialAmount: Long,
    /** Сорван срок. Считается сам, руками такой статус не держат. */
    val overdue: Int,
    val awaitingPay: Int,
    /** Закончены: сдан и оплачен либо отказ. В суммы не входят. */
    val archived: Int,
    /** Сумма заключённых незакрытых договоров и сколько по ним не получено. */
    val contracted: Long,
    val rest: Long,
    /** Ближайшие сроки: что именно и когда. */
    val soon: List<Pair<String, String>>
)

/**
 * Статус с поправкой на просрочку.
 *
 * Просрочку невозможно держать руками: её выставляет календарь. Расчёт
 * один на всё приложение — иначе список, сводка и уведомление однажды
 * начнут показывать разное по одному и тому же договору.
 */
fun shownStatusOf(c: Contract): Status {
    val stage = c.status.stage
    val watch = stage == Stage.CONTRACT || stage == Stage.PRODUCTION || stage == Stage.ACCEPTANCE
    return if (c.status.signed && watch && overdueText(c.end) != null) Status.OVERDUE else c.status
}

/** Считается один раз на изменение реестра, а не на каждую перерисовку. */
fun Db.summary(): Summary {
    val live = liveContracts
    val open = live.filterNot { it.archived }
    val signed = open.filter { it.status.signed }
    val lead = open.filterNot { it.status.signed }

    val soon = signed
        .filter { it.end.isNotBlank() }
        .sortedBy { it.end }
        .take(3)
        .map { c -> (site(c.siteId)?.name ?: c.workKind.ifBlank { "Без вида работ" }) to dateShort(c.end) }

    return Summary(
        inWork = signed.size,
        potential = lead.size,
        potentialAmount = lead.sumOf { it.amount },
        overdue = signed.count { shownStatusOf(it) == Status.OVERDUE },
        awaitingPay = signed.count { it.status.stage == Stage.PAYMENT },
        archived = live.count { it.archived },
        contracted = signed.sumOf { it.amount },
        rest = signed.sumOf { it.restAmount },
        soon = soon
    )
}
