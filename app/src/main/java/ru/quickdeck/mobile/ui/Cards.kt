package ru.quickdeck.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.*
import ru.quickdeck.mobile.data.*

/** Иконка в карточке: радиус 10 внутри радиуса 16 при отступе 6 — концентрично. */
@Composable
fun CardIcon(path: String, tone: T.Tone = T.accent, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(T.rIcon)).background(tone.chip),
        contentAlignment = Alignment.Center
    ) { QIcon(path, size = 22.dp, tint = tone.ink) }
}

/** Инициалы вместо иконки — у человека должно быть лицо, хотя бы буквами. */
@Composable
fun Avatar(e: Employee, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(percent = 50)).background(T.info.chip),
        contentAlignment = Alignment.Center
    ) { Q(e.initials.ifBlank { "•" }, Type.heading, T.info.ink) }
}

/** Полоса готовности: 4 px, радиус 2. */
@Composable
fun Progress(percent: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(T.muted.chip)) {
            Box(
                Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(T.accent.fill)
            )
        }
        Spacer(Modifier.width(T.sm))
        Q("$percent %", Type.caption, T.text3)
    }
}

/**
 * Просрочка выставляется сама. Руками её держать невозможно, а забытый срок —
 * главное, что руководитель должен видеть с первого взгляда.
 */
fun shownStatus(c: Contract): Status = shownStatusOf(c)

// --- строки списков -----------------------------------------------------

@Composable
fun SiteRow(site: Site, db: Db, onClick: () -> Unit) {
    val stage = db.stageOfSite(site.id)
    val active = db.activeContractsOfSite(site.id).size
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(T.rCard)).background(T.surface).padding(T.md),
            verticalAlignment = Alignment.Top
        ) {
            CardIcon(Ic.sites, stage?.tone() ?: T.muted)
            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Q(site.name.ifBlank { "Без названия" }, Type.heading, T.text, 1, Modifier.weight(1f))
                    Spacer(Modifier.width(T.sm))
                    if (stage != null) StageChip(stage)
                }
                Spacer(Modifier.height(T.xs))
                val second = listOfNotNull(
                    db.customer(site.customerId)?.name,
                    if (active > 0) "$active ${plural(active.toLong(), "договор", "договора", "договоров")}" else null
                ).joinToString(" · ")
                if (second.isNotBlank()) Q(second, Type.small, T.text2, 1)
                if (site.progress > 0) {
                    Spacer(Modifier.height(T.sm))
                    Progress(site.progress)
                }
            }
        }
    }
}

@Composable
fun ContractRow(c: Contract, db: Db, onClick: () -> Unit) {
    val overdue = overdueText(c.end)
    val status = shownStatus(c)
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(T.rCard)).background(T.surface).padding(T.md),
            verticalAlignment = Alignment.Top
        ) {
            CardIcon(Ic.contracts, status.tone())
            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f)) {
                Q(c.label(db.site(c.siteId)?.name), Type.heading, T.text, 2)
                Spacer(Modifier.height(T.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status)
                    Spacer(Modifier.width(T.sm))
                    val due = overdue ?: c.end.takeIf { it.isNotBlank() }?.let { "до ${dateShort(it)}" }
                    if (due != null) Q(due, Type.caption, if (overdue != null) T.danger.ink else T.text3, 1)
                }
                if (c.amount != 0L) {
                    Spacer(Modifier.height(T.xs))
                    Q(money(c.amount), Type.amount, T.text)
                }
            }
        }
    }
}

@Composable
fun PartyRow(p: Party, subtitle: String?, icon: String, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(T.rCard)).background(T.surface).padding(T.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardIcon(icon, T.muted)
            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f)) {
                Q(p.name.ifBlank { "Без названия" }, Type.heading, T.text, 1)
                val sub = listOfNotNull(subtitle, p.phone.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(T.xs))
                    Q(sub, Type.small, T.text2, 1)
                }
            }
            QIcon(Ic.chevronRight, size = 20.dp, tint = T.text3)
        }
    }
}

/** Строка сотрудника. Звонок прямо отсюда — это самое частое действие. */
@Composable
fun EmployeeRow(e: Employee, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(T.rCard)).background(T.surface).padding(T.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(e)
            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f)) {
                Q(e.name.ifBlank { "Без имени" }, Type.heading, T.text, 1)
                val sub = listOf(e.position, e.department).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(T.xs))
                    Q(sub, Type.small, T.text2, 1)
                }
            }
            e.phones.firstOrNull()?.let { phone ->
                Spacer(Modifier.width(T.sm))
                Pressable({ Actions.dial(ctx, phone) }) {
                    Box(
                        Modifier.size(T.touchMin).clip(RoundedCornerShape(percent = 50)).background(T.success.chip),
                        contentAlignment = Alignment.Center
                    ) { QIcon(Ic.phone, size = 20.dp, tint = T.success.ink, stroke = 2f) }
                }
            }
        }
    }
}

// --- карточки деталей ---------------------------------------------------

@Composable
private fun KeyValue(key: String, value: String, valueColor: Color = T.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Q(key, Type.small, T.text3, 1, Modifier.width(110.dp))
        Q(value.ifBlank { "—" }, Type.small, valueColor, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SiteCard(
    site: Site,
    db: Db,
    onEdit: () -> Unit,
    onAddContract: () -> Unit,
    onOpenContract: (String) -> Unit
) {
    val ctx = LocalContext.current
    val send = rememberSendState()
    val stage = db.stageOfSite(site.id)
    Column(Modifier.fillMaxWidth().padding(T.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Q(site.name, Type.title, T.text, 2, Modifier.weight(1f))
            Spacer(Modifier.width(T.sm))
            if (stage != null) StageChip(stage)
        }
        // Номер и полное наименование — только если это не копия имени.
        val sub = listOf(site.codeLabel, site.subName).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotBlank()) Q(sub, Type.caption, T.text3, 2)

        Spacer(Modifier.height(T.lg))
        Progress(site.progress)

        Spacer(Modifier.height(T.xl))
        Hairline()
        Spacer(Modifier.height(T.md))
        KeyValue("Заказчик", db.customer(site.customerId)?.name ?: "")
        KeyValue("Адрес", site.address)
        KeyValue("Тип", site.buildingType)
        if (site.area > 0) KeyValue("Площадь", "${trimNumber(site.area)} ${site.unit}")
        if (site.subName.isNotBlank()) KeyValue("Полное", site.subName)
        if (site.note.isNotBlank()) KeyValue("Комментарий", site.note)

        val list = db.contractsOfSite(site.id)
        if (list.isNotEmpty()) {
            Spacer(Modifier.height(T.lg))
            Q("Договоры", Type.caption, T.text3)
            Spacer(Modifier.height(T.sm))
            list.forEach { c ->
                Pressable({ onOpenContract(c.id) }, Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = T.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Q(c.workKind.ifBlank { c.code }, Type.small, T.text, 2, Modifier.weight(1f))
                        Spacer(Modifier.width(T.sm))
                        StatusChip(shownStatus(c), short = true)
                    }
                }
            }
        }

        Spacer(Modifier.height(T.xl))
        PrimaryButton("Добавить договор", onAddContract)
        Spacer(Modifier.height(T.sm))
        SendButton("Отправить карточку", send) {
            Actions.share(ctx, siteText(site, db), "Карточка объекта")
        }
        Spacer(Modifier.height(T.sm))
        GhostButton("Изменить", onEdit, Modifier.fillMaxWidth())
    }
}

@Composable
fun ContractCard(c: Contract, db: Db, onEdit: () -> Unit) {
    val ctx = LocalContext.current
    val send = rememberSendState()
    val overdue = overdueText(c.end)
    val status = shownStatus(c)
    val siteName = db.site(c.siteId)?.name
    Column(Modifier.fillMaxWidth().padding(T.lg)) {
        Q(c.label(siteName), Type.title, T.text, 3)
        c.codeLabel(siteName).takeIf { it.isNotBlank() }?.let { Q(it, Type.caption, T.text3) }
        Spacer(Modifier.height(T.sm))
        StatusChip(status)
        if (c.amount != 0L) {
            Spacer(Modifier.height(T.sm))
            Q(money(c.amount), Type.display, T.text)
        }

        Spacer(Modifier.height(T.xl))
        Hairline()
        Spacer(Modifier.height(T.md))
        KeyValue("Объект", db.site(c.siteId)?.name ?: "")
        KeyValue("Заказчик", db.customer(db.site(c.siteId)?.customerId)?.name ?: "")
        KeyValue("Юр. лицо", c.legalEntity)
        KeyValue("Отдел", db.refs.departmentOf(c.workKind))
        KeyValue("Ответственный", c.responsible)
        KeyValue("Начало", if (c.start.isBlank()) "" else dateLong(c.start))
        KeyValue("Срок", if (c.end.isBlank()) "" else dateLong(c.end), if (overdue != null) T.danger.ink else T.text)
        if (overdue != null) KeyValue("", overdue, T.danger.ink)
        if (c.note.isNotBlank()) KeyValue("Комментарий", c.note)

        val pays = c.payments.filterNot { it.empty }
        if (pays.isNotEmpty()) {
            Spacer(Modifier.height(T.lg))
            Q("Оплаты", Type.caption, T.text3)
            Spacer(Modifier.height(T.sm))
            pays.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Q(p.condition, Type.small, T.text, 2, Modifier.weight(1f))
                    Spacer(Modifier.width(T.sm))
                    Q("${trimNumber(p.share * 100)} %", Type.smallNum, T.text2)
                    Spacer(Modifier.width(T.sm))
                    QIcon(
                        if (p.paid) Ic.check else Ic.wallet,
                        size = 18.dp,
                        tint = if (p.paid) T.success.ink else T.text3,
                        stroke = if (p.paid) 2f else 1.75f
                    )
                }
            }
            if (c.amount != 0L) {
                Spacer(Modifier.height(T.xs))
                Q("Оплачено ${money(c.paidAmount)} · остаток ${money(c.restAmount)}", Type.caption, T.text3)
            }
        }

        Spacer(Modifier.height(T.xl))
        PrimaryButton("Изменить договор", onEdit)
        Spacer(Modifier.height(T.sm))
        SendButton("Отправить карточку", send) {
            Actions.share(ctx, contractText(c, db), "Карточка договора")
        }
    }
}

@Composable
fun PartyCard(p: Party, db: Db, onEdit: () -> Unit, onOpenSite: (String) -> Unit) {
    val ctx = LocalContext.current
    val send = rememberSendState()
    Column(Modifier.fillMaxWidth().padding(T.lg)) {
        Q(p.name, Type.title, T.text, 2)
        if (p.subName.isNotBlank()) Q(p.subName, Type.small, T.text2, 3)

        if (p.phone.isNotBlank() || p.email.isNotBlank()) {
            Spacer(Modifier.height(T.md))
            Row(horizontalArrangement = Arrangement.spacedBy(T.sm)) {
                if (p.phone.isNotBlank()) {
                    ActionPill(Ic.phone, "Позвонить", T.success) { Actions.dial(ctx, p.phone) }
                }
                if (p.email.isNotBlank()) {
                    ActionPill(Ic.mail, "Написать", T.accent) { Actions.chat(ctx, Chat("email", p.email)) }
                }
            }
        }

        Spacer(Modifier.height(T.lg))
        Hairline()
        Spacer(Modifier.height(T.md))
        KeyValue("ИНН", p.inn)
        if (p.kpp.isNotBlank()) KeyValue("КПП", p.kpp)
        if (p.ogrn.isNotBlank()) KeyValue("ОГРН", p.ogrn)
        KeyValue("Руководитель", p.director)
        KeyValue("Телефон", p.phone)
        if (p.email.isNotBlank()) KeyValue("E-mail", p.email)
        if (p.legalAddress.isNotBlank()) KeyValue("Юр. адрес", p.legalAddress)
        if (p.bank.isNotBlank()) KeyValue("Банк", "${p.bank}, БИК ${p.bik}")
        if (p.account.isNotBlank()) KeyValue("Р/с", p.account)
        if (p.note.isNotBlank()) KeyValue("Заметки", p.note)

        val sites = db.sitesOfCustomer(p.id)
        if (sites.isNotEmpty()) {
            Spacer(Modifier.height(T.lg))
            Q("Объекты", Type.caption, T.text3)
            Spacer(Modifier.height(T.sm))
            sites.forEach { s ->
                Pressable({ onOpenSite(s.id) }, Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = T.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Q(s.name, Type.small, T.text, 1, Modifier.weight(1f))
                        db.stageOfSite(s.id)?.let { StageChip(it) }
                    }
                }
            }
        }

        Spacer(Modifier.height(T.xl))
        PrimaryButton("Изменить", onEdit)
        Spacer(Modifier.height(T.sm))
        SendButton("Отправить реквизиты", send) { Actions.share(ctx, partyText(p), "Реквизиты") }
    }
}

/**
 * Быстрая карточка сотрудника. Рассчитана на действие, а не на анкету:
 * позвонить, написать, поставить задачу, отправить контакт — по одному тапу.
 */
@Composable
fun EmployeeCard(e: Employee, db: Db, onEdit: () -> Unit, onTask: () -> Unit) {
    val ctx = LocalContext.current
    val send = rememberSendState()
    Column(Modifier.fillMaxWidth().padding(T.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(e, 56.dp)
            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f)) {
                Q(e.name, Type.title, T.text, 2)
                val sub = listOf(e.position, e.department).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Q(sub, Type.small, T.text2, 2)
            }
        }

        Spacer(Modifier.height(T.lg))

        if (e.phones.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(T.sm)
            ) {
                e.phones.forEach { phone -> ActionPill(Ic.phone, phone, T.success) { Actions.dial(ctx, phone) } }
            }
            Spacer(Modifier.height(T.sm))
        }
        if (e.chats.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(T.sm)
            ) {
                e.chats.forEach { chat ->
                    val label = when (chat.kind) {
                        "telegram" -> "Telegram"
                        "whatsapp" -> "WhatsApp"
                        "email" -> "Почта"
                        else -> chat.kind
                    }
                    ActionPill(if (chat.kind == "email") Ic.mail else Ic.chat, label, T.accent) {
                        Actions.chat(ctx, chat)
                    }
                }
            }
            Spacer(Modifier.height(T.sm))
        }
        if (e.phones.isEmpty() && e.chats.isEmpty()) {
            Q("Контакты не заполнены — добавь через «Изменить»", Type.small, T.text3)
            Spacer(Modifier.height(T.sm))
        }

        Spacer(Modifier.height(T.md))
        PrimaryButton("Поставить задачу", onTask)
        Spacer(Modifier.height(T.sm))
        SendButton("Отправить контакт", send) {
            Actions.share(ctx, Actions.employeeText(e), "Контакт сотрудника")
        }
        Spacer(Modifier.height(T.sm))
        GhostButton("Изменить", onEdit, Modifier.fillMaxWidth())

        val mine = db.liveContracts.filter {
            it.responsible.equals(e.name, true) || e.name in it.coExecutors
        }
        if (mine.isNotEmpty()) {
            Spacer(Modifier.height(T.xl))
            Q("Ведёт", Type.caption, T.text3)
            Spacer(Modifier.height(T.sm))
            mine.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Q(c.label(db.site(c.siteId)?.name), Type.small, T.text, 2, Modifier.weight(1f))
                    Spacer(Modifier.width(T.sm))
                    StatusChip(shownStatus(c), short = true)
                }
            }
        }

        if (e.location.isNotBlank() || e.note.isNotBlank() || e.tabNumber.isNotBlank()) {
            Spacer(Modifier.height(T.lg))
            Hairline()
            Spacer(Modifier.height(T.md))
            if (e.location.isNotBlank()) KeyValue("Нахождение", e.location)
            if (e.tabNumber.isNotBlank()) KeyValue("Таб. №", e.tabNumber)
            if (e.note.isNotBlank()) KeyValue("Комментарий", e.note)
        }
    }
}

/** Кнопка-действие: иконка и подпись, цвет по смыслу. */
@Composable
fun ActionPill(icon: String, label: String, tone: T.Tone, onClick: () -> Unit) {
    Pressable(onClick) {
        Row(
            Modifier
                .heightIn(min = T.touchMin)
                .clip(RoundedCornerShape(percent = 50))
                .background(tone.chip)
                .padding(horizontal = T.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QIcon(icon, size = 18.dp, tint = tone.ink, stroke = 2f)
            Spacer(Modifier.width(T.sm))
            Q(label, Type.small, tone.ink, 1)
        }
    }
}

// --- текстовые версии карточек ------------------------------------------
// Отправлять нужно текстом, а не картинкой: текст вставляется в любое
// сообщение, ищется поиском и не весит ничего.

fun siteText(s: Site, db: Db): String = buildString {
    appendLine(s.name)
    if (s.fullName.isNotBlank()) appendLine(s.fullName)
    db.customer(s.customerId)?.name?.let { appendLine("Заказчик: $it") }
    if (s.address.isNotBlank()) appendLine(s.address)
    val active = db.activeContractsOfSite(s.id)
    if (active.isNotEmpty()) {
        appendLine("В работе:")
        active.forEach { appendLine("  ${it.workKind} — ${shownStatus(it).label}") }
    }
}.trimEnd()

fun contractText(c: Contract, db: Db): String = buildString {
    appendLine(c.label(db.site(c.siteId)?.name))
    if (c.code.isNotBlank()) appendLine(c.code)
    appendLine("Статус: ${shownStatus(c).label}")
    if (c.amount != 0L) appendLine("Цена: ${money(c.amount)}")
    if (c.end.isNotBlank()) appendLine("Срок: ${dateLong(c.end)}")
    if (c.responsible.isNotBlank()) appendLine("Ответственный: ${c.responsible}")
}.trimEnd()

fun partyText(p: Party): String = buildString {
    appendLine(p.fullName.ifBlank { p.name })
    if (p.inn.isNotBlank()) appendLine("ИНН ${p.inn}" + if (p.kpp.isNotBlank()) " / КПП ${p.kpp}" else "")
    if (p.ogrn.isNotBlank()) appendLine("ОГРН ${p.ogrn}")
    if (p.legalAddress.isNotBlank()) appendLine(p.legalAddress)
    if (p.bank.isNotBlank()) appendLine("${p.bank}, БИК ${p.bik}")
    if (p.account.isNotBlank()) appendLine("Р/с ${p.account}")
    if (p.director.isNotBlank()) appendLine("Руководитель: ${p.director}")
    if (p.phone.isNotBlank()) appendLine(p.phone)
    if (p.email.isNotBlank()) appendLine(p.email)
}.trimEnd()
