package ru.quickdeck.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.*
import ru.quickdeck.mobile.data.*

/** Иконка в карточке: радиус 10 внутри радиуса 16 при отступе 6 — концентрично. */
@Composable
fun CardIcon(path: String, tone: T.Tone = T.accent, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(T.rIcon))
            .background(tone.chip),
        contentAlignment = Alignment.Center
    ) { QIcon(path, size = 22.dp, tint = tone.ink) }
}

/** Полоса готовности: 4 px, радиус 2. */
@Composable
fun Progress(percent: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(T.muted.chip)
        ) {
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
 * Строка списка объекта: имя, статус плашкой, контрагент и срок, прогресс.
 * Вся строка — одна зона нажатия.
 */
@Composable
fun SiteRow(site: Site, db: Db, onClick: () -> Unit) {
    val overdue = overdueText(site.deadline)
    val status = if (overdue != null && site.status == Status.WORK) Status.OVERDUE else site.status
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(T.rCard))
                .background(T.surface)
                .padding(T.md),
            verticalAlignment = Alignment.Top
        ) {
            CardIcon(Ic.sites, status.tone())
            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Q(site.name.ifBlank { "Без названия" }, Type.heading, T.text, 1, Modifier.weight(1f))
                    Spacer(Modifier.width(T.sm))
                    StatusChip(status)
                }
                Spacer(Modifier.height(T.xs))
                val second = listOfNotNull(
                    db.customer(site.customerId)?.name,
                    overdue ?: site.deadline.takeIf { it.isNotBlank() }?.let { "до ${dateShort(it)}" }
                ).joinToString(" · ")
                if (second.isNotBlank()) {
                    Q(second, Type.small, if (overdue != null) T.danger.ink else T.text2, 1)
                }
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
    val status = if (overdue != null && c.status == Status.WORK) Status.OVERDUE else c.status
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(T.rCard))
                .background(T.surface)
                .padding(T.md),
            verticalAlignment = Alignment.Top
        ) {
            CardIcon(Ic.contracts, status.tone())
            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Q(c.number.ifBlank { "Без номера" }, Type.heading, T.text, 1, Modifier.weight(1f))
                    Spacer(Modifier.width(T.sm))
                    StatusChip(status)
                }
                Spacer(Modifier.height(T.xs))
                Q(
                    listOfNotNull(
                        db.site(c.siteId)?.name,
                        overdue ?: c.end.takeIf { it.isNotBlank() }?.let { "до ${dateShort(it)}" }
                    ).joinToString(" · "),
                    Type.small,
                    if (overdue != null) T.danger.ink else T.text2,
                    1
                )
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
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(T.rCard))
                .background(T.surface)
                .padding(T.md),
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

// --- карточки деталей ---------------------------------------------------

@Composable
private fun KeyValue(key: String, value: String, valueColor: androidx.compose.ui.graphics.Color = T.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Q(key, Type.small, T.text3, 1, Modifier.width(110.dp))
        Q(value.ifBlank { "—" }, Type.small, valueColor, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SiteCard(site: Site, db: Db, onEdit: () -> Unit, onAddContract: () -> Unit) {
    val overdue = overdueText(site.deadline)
    val status = if (overdue != null && site.status == Status.WORK) Status.OVERDUE else site.status
    Column(Modifier.fillMaxWidth().padding(T.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Q(site.name, Type.title, T.text, 2, Modifier.weight(1f))
            Spacer(Modifier.width(T.sm))
            StatusChip(status)
        }
        Spacer(Modifier.height(T.sm))
        Q("${site.progress} %", Type.display, T.text)
        Q("готовность", Type.caption, T.text3)

        Spacer(Modifier.height(T.lg))
        Progress(site.progress)

        Spacer(Modifier.height(T.xl))
        Hairline()
        Spacer(Modifier.height(T.md))
        KeyValue("Заказчик", db.customer(site.customerId)?.name ?: "")
        KeyValue("Адрес", site.address)
        KeyValue("Срок", if (site.deadline.isBlank()) "" else dateLong(site.deadline), if (overdue != null) T.danger.ink else T.text)
        if (overdue != null) KeyValue("", overdue, T.danger.ink)
        if (site.note.isNotBlank()) KeyValue("Примечание", site.note)

        val list = db.contractsOfSite(site.id)
        if (list.isNotEmpty()) {
            Spacer(Modifier.height(T.lg))
            Q("Договоры", Type.caption, T.text3)
            Spacer(Modifier.height(T.sm))
            list.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Q(c.number.ifBlank { "Без номера" }, Type.small, T.text, 1, Modifier.weight(1f))
                    Q(money(c.amount), Type.smallNum, T.text2)
                }
            }
        }

        Spacer(Modifier.height(T.xl))
        PrimaryButton("Добавить договор", onAddContract)
        Spacer(Modifier.height(T.sm))
        GhostButton("Изменить объект", onEdit, Modifier.fillMaxWidth())
    }
}

@Composable
fun ContractCard(c: Contract, db: Db, onEdit: () -> Unit) {
    val overdue = overdueText(c.end)
    val status = if (overdue != null && c.status == Status.WORK) Status.OVERDUE else c.status
    Column(Modifier.fillMaxWidth().padding(T.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Q("Договор ${c.number}", Type.title, T.text, 2, Modifier.weight(1f))
            Spacer(Modifier.width(T.sm))
            StatusChip(status)
        }
        Spacer(Modifier.height(T.sm))
        Q(money(c.amount), Type.display, T.text)

        Spacer(Modifier.height(T.xl))
        Hairline()
        Spacer(Modifier.height(T.md))
        KeyValue("Объект", db.site(c.siteId)?.name ?: "")
        KeyValue("Заказчик", db.customer(c.customerId)?.name ?: "")
        KeyValue("Исполнитель", db.contractor(c.contractorId)?.name ?: "")
        KeyValue("Начало", if (c.start.isBlank()) "" else dateLong(c.start))
        KeyValue("Срок", if (c.end.isBlank()) "" else dateLong(c.end), if (overdue != null) T.danger.ink else T.text)
        if (overdue != null) KeyValue("", overdue, T.danger.ink)
        if (c.note.isNotBlank()) KeyValue("Примечание", c.note)

        Spacer(Modifier.height(T.xl))
        PrimaryButton("Изменить договор", onEdit)
    }
}

@Composable
fun PartyCard(p: Party, db: Db, isCustomer: Boolean, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(T.lg)) {
        Q(p.name, Type.title, T.text, 2)
        Spacer(Modifier.height(T.lg))
        Hairline()
        Spacer(Modifier.height(T.md))
        KeyValue("ИНН", p.inn)
        KeyValue("Контакт", p.contact)
        KeyValue("Телефон", p.phone)
        if (p.note.isNotBlank()) KeyValue("Примечание", p.note)

        if (isCustomer) {
            val sites = db.sitesOfCustomer(p.id)
            if (sites.isNotEmpty()) {
                Spacer(Modifier.height(T.lg))
                Q("Объекты", Type.caption, T.text3)
                Spacer(Modifier.height(T.sm))
                sites.forEach { s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Q(s.name, Type.small, T.text, 1, Modifier.weight(1f))
                        StatusChip(s.status)
                    }
                }
            }
        } else {
            val list = db.contracts.filter { it.contractorId == p.id }
            if (list.isNotEmpty()) {
                Spacer(Modifier.height(T.lg))
                Q("Договоры", Type.caption, T.text3)
                Spacer(Modifier.height(T.sm))
                list.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Q(c.number.ifBlank { "Без номера" }, Type.small, T.text, 1, Modifier.weight(1f))
                        Q(money(c.amount), Type.smallNum, T.text2)
                    }
                }
            }
        }

        Spacer(Modifier.height(T.xl))
        PrimaryButton("Изменить", onEdit)
    }
}
