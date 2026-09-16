package ru.quickdeck.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.*

/**
 * Что выбирается поверх формы.
 *
 * Важно: выбор — это слой НАД формой, а не переход на другой экран. Когда
 * форма уходит из композиции, её состояние уничтожается вместе со всем
 * набранным, а лямбда выбора пишет в уже мёртвое состояние — из-за этого
 * заказчик не появлялся и терялось всё, что успели заполнить.
 */
sealed interface PickRequest {
    val title: String

    /** Список готовых значений: виды работ, типы зданий, юр. лица, сотрудники. */
    data class Values(
        override val title: String,
        val options: List<String>,
        val current: String,
        val onPick: (String) -> Unit
    ) : PickRequest

    data class CustomerPick(val onPick: (Party) -> Unit) : PickRequest {
        override val title get() = "Заказчик"
    }

    data class EmployeePick(val onPick: (Employee) -> Unit) : PickRequest {
        override val title get() = "Сотрудник"
    }

    data class SitePick(val onPick: (Site) -> Unit) : PickRequest {
        override val title get() = "Объект"
    }
}

/** Поле даты: показывает, что введено не датой, вместо того чтобы молча стереть. */
@Composable
private fun DateField(label: String, text: String, onChange: (String) -> Unit, placeholder: String) {
    val broken = text.isNotBlank() && parseHumanDate(text).isBlank()
    Field(
        label = label,
        value = text,
        onChange = onChange,
        placeholder = placeholder,
        numeric = true,
        hint = if (broken) "Нужен формат дд.мм.гггг — иначе дата не сохранится" else null,
        warn = broken
    )
}

@Composable
fun SiteForm(
    initial: Site,
    db: Db,
    onPick: (PickRequest) -> Unit,
    onDone: (Site) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var s by remember(initial.id) { mutableStateOf(initial) }
    var areaText by remember(initial.id) {
        mutableStateOf(if (initial.area == 0.0) "" else trimNumber(initial.area))
    }

    FormShell(
        title = if (initial.name.isBlank()) "Новый объект" else "Объект",
        code = initial.code,
        canSave = s.name.isNotBlank(),
        onSave = { onDone(s.copy(area = areaText.replace(',', '.').toDoubleOrNull() ?: 0.0)) },
        onCancel = onCancel,
        onDelete = onDelete
    ) {
        Field("Краткое наименование", s.name, { s = s.copy(name = it) }, placeholder = "Цимлянская 17")
        Spacer(Modifier.height(T.md))
        Field(
            "Полное наименование", s.fullName, { s = s.copy(fullName = it) },
            placeholder = "Ремонтно-восстановительные работы…", singleLine = false
        )
        Spacer(Modifier.height(T.md))
        PickerRow("Заказчик", db.customer(s.customerId)?.name, onClick = {
            onPick(PickRequest.CustomerPick { p -> s = s.copy(customerId = p.id) })
        })
        Spacer(Modifier.height(T.md))
        Field("Адрес", s.address, { s = s.copy(address = it) }, placeholder = "ЛНР, г. Луганск, ул. …")
        Spacer(Modifier.height(T.md))
        PickerRow("Тип здания", s.buildingType, onClick = {
            onPick(
                PickRequest.Values("Тип здания", db.refs.buildingTypes, s.buildingType) {
                    s = s.copy(buildingType = it)
                }
            )
        })
        Spacer(Modifier.height(T.md))
        Row {
            Box(Modifier.weight(1f)) {
                Field("Площадь", areaText, { areaText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    placeholder = "2335.52", numeric = true)
            }
            Spacer(Modifier.width(T.md))
            Box(Modifier.width(120.dp)) {
                PickerRow("Ед. изм.", s.unit, onClick = {
                    onPick(PickRequest.Values("Единица измерения", db.refs.units, s.unit) {
                        s = s.copy(unit = it)
                    })
                })
            }
        }
        Spacer(Modifier.height(T.md))
        Field("Комментарий", s.note, { s = s.copy(note = it) }, singleLine = false)
    }
}

@Composable
fun ContractForm(
    initial: Contract,
    db: Db,
    onPick: (PickRequest) -> Unit,
    onDone: (Contract) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var c by remember(initial.id) { mutableStateOf(initial) }
    var amountText by remember(initial.id) {
        mutableStateOf(if (initial.amount == 0L) "" else initial.amount.toString())
    }
    var startText by remember(initial.id) { mutableStateOf(dateInput(initial.start)) }
    var endText by remember(initial.id) { mutableStateOf(dateInput(initial.end)) }

    val department = remember(c.workKind, db.refs) { db.refs.departmentOf(c.workKind) }

    FormShell(
        title = if (initial.workKind.isBlank()) "Новый договор" else "Договор",
        code = initial.code,
        canSave = c.siteId != null && c.workKind.isNotBlank(),
        onSave = {
            onDone(
                c.copy(
                    amount = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L,
                    start = parseHumanDate(startText),
                    end = parseHumanDate(endText)
                )
            )
        },
        onCancel = onCancel,
        onDelete = onDelete
    ) {
        PickerRow("Объект", db.site(c.siteId)?.name, onClick = {
            onPick(PickRequest.SitePick { site ->
                c = c.copy(siteId = site.id)
            })
        })
        Spacer(Modifier.height(T.md))
        PickerRow(
            "Вид работы", c.workKind,
            sub = department.takeIf { it.isNotBlank() },
            onClick = {
                onPick(
                    PickRequest.Values("Вид работы", db.refs.workKinds.map { it.name }, c.workKind) {
                        c = c.copy(workKind = it)
                    }
                )
            }
        )
        Spacer(Modifier.height(T.md))
        PickerRow("Исполнитель (юр. лицо)", c.legalEntity, onClick = {
            onPick(PickRequest.Values("Юридическое лицо", db.refs.legalEntities, c.legalEntity) {
                c = c.copy(legalEntity = it)
            })
        })
        Spacer(Modifier.height(T.md))
        Field("Цена, ₽", amountText, { amountText = it.filter { ch -> ch.isDigit() } },
            placeholder = "1250000", numeric = true)
        if (amountText.isNotBlank()) {
            Spacer(Modifier.height(T.xs))
            Q(money(amountText.toLongOrNull() ?: 0L), Type.smallNum, T.text3)
        }
        Spacer(Modifier.height(T.md))
        Row {
            Box(Modifier.weight(1f)) { DateField("Начало", startText, { startText = it }, "01.04.2026") }
            Spacer(Modifier.width(T.md))
            Box(Modifier.weight(1f)) { DateField("Срок сдачи", endText, { endText = it }, "30.09.2026") }
        }
        Spacer(Modifier.height(T.lg))
        StatusPicker(c.status) { c = c.copy(status = it) }
        Spacer(Modifier.height(T.lg))

        PickerRow(
            "Ответственный", c.responsible,
            sub = db.employeeByName(c.responsible)?.let { listOf(it.position, it.department).filter { v -> v.isNotBlank() }.joinToString(" · ") },
            onClick = { onPick(PickRequest.EmployeePick { e -> c = c.copy(responsible = e.name) }) }
        )

        // Исполнителей на договоре обычно несколько: ответственный ведёт, но
        // работают ещё люди. В таблице под них три столбца соисполнителей.
        Spacer(Modifier.height(T.md))
        Q("Соисполнители", Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
        c.coExecutors.forEachIndexed { index, name ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = T.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    PickerRow(
                        label = "",
                        value = name,
                        sub = db.employeeByName(name)?.let { e ->
                            listOf(e.position, e.department).filter { it.isNotBlank() }.joinToString(" · ")
                        },
                        onClick = {
                            onPick(PickRequest.EmployeePick { e ->
                                c = c.copy(
                                    coExecutors = c.coExecutors.toMutableList().also { it[index] = e.name }
                                )
                            })
                        }
                    )
                }
                Pressable({
                    c = c.copy(coExecutors = c.coExecutors.filterIndexed { i, _ -> i != index })
                }) {
                    Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                        QIcon(Ic.close, size = 18.dp, tint = T.text3, stroke = 2f)
                    }
                }
            }
        }
        // Таблица держит трёх соисполнителей — больше туда не уедет.
        if (c.coExecutors.size < 3) {
            GhostButton("Добавить соисполнителя", {
                onPick(PickRequest.EmployeePick { e ->
                    if (e.name !in c.coExecutors && e.name != c.responsible) {
                        c = c.copy(coExecutors = c.coExecutors + e.name)
                    }
                })
            }, Modifier.fillMaxWidth())
        } else {
            Q("Больше трёх таблица не хранит", Type.caption, T.text3)
        }

        Spacer(Modifier.height(T.lg))
        Q("Оплаты", Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
        Payments(c.payments, db.refs.payStages, onPick) { c = c.copy(payments = it) }

        Spacer(Modifier.height(T.md))
        Field("Комментарий", c.note, { c = c.copy(note = it) }, singleLine = false)
    }
}

/** Три оплаты: условие из справочника, доля и отметка факта. */
@Composable
private fun Payments(
    payments: List<Payment>,
    stages: List<String>,
    onPick: (PickRequest) -> Unit,
    onChange: (List<Payment>) -> Unit
) {
    val rows = remember(payments) {
        List(3) { payments.getOrElse(it) { Payment() } }
    }
    val total = rows.sumOf { it.share }

    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, pay ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(T.rControl))
                    .background(T.surface)
                    .padding(T.md)
            ) {
                PickerRow("Оплата ${index + 1} — условие", pay.condition, onClick = {
                    onPick(PickRequest.Values("Условие оплаты", stages, pay.condition) { picked ->
                        onChange(rows.mapIndexed { i, p -> if (i == index) p.copy(condition = picked) else p })
                    })
                })
                Spacer(Modifier.height(T.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(110.dp)) {
                        Field(
                            "Доля, %",
                            if (pay.share == 0.0) "" else trimNumber(pay.share * 100),
                            { text ->
                                val v = text.filter { it.isDigit() }.take(3).toDoubleOrNull() ?: 0.0
                                onChange(rows.mapIndexed { i, p -> if (i == index) p.copy(share = v / 100.0) else p })
                            },
                            numeric = true, placeholder = "30"
                        )
                    }
                    Spacer(Modifier.width(T.md))
                    Pressable({
                        onChange(rows.mapIndexed { i, p -> if (i == index) p.copy(paid = !p.paid) else p })
                    }) {
                        Row(
                            Modifier
                                .heightIn(min = T.touchMin)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(if (pay.paid) T.success.chip else T.bg)
                                .padding(horizontal = T.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QIcon(
                                if (pay.paid) Ic.check else Ic.wallet,
                                size = 18.dp,
                                tint = if (pay.paid) T.success.ink else T.text3,
                                stroke = if (pay.paid) 2f else 1.75f
                            )
                            Spacer(Modifier.width(T.sm))
                            Q(
                                if (pay.paid) "Оплачено" else "Не оплачено",
                                Type.caption,
                                if (pay.paid) T.success.ink else T.text2
                            )
                        }
                    }
                }
            }
            if (index < rows.lastIndex) Spacer(Modifier.height(T.sm))
        }

        if (total > 0.0) {
            Spacer(Modifier.height(T.xs))
            val off = kotlin.math.abs(total - 1.0) > 0.001
            Q(
                if (off) "Доли дают ${trimNumber(total * 100)} % — проверь, должно быть 100"
                else "Доли дают 100 %",
                Type.caption,
                if (off) T.warning.ink else T.text3
            )
        }
    }
}

@Composable
fun PartyForm(
    initial: Party,
    title: String,
    onDone: (Party) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var p by remember(initial.id) { mutableStateOf(initial) }
    var details by remember(initial.id) { mutableStateOf(initial.ogrn.isNotBlank() || initial.bank.isNotBlank()) }

    FormShell(
        title = title,
        code = "",
        canSave = p.name.isNotBlank(),
        onSave = { onDone(p) },
        onCancel = onCancel,
        onDelete = onDelete
    ) {
        Field("Краткое наименование", p.name, { p = p.copy(name = it) }, placeholder = "ЭнергоКомплекс")
        Spacer(Modifier.height(T.md))
        Field("Полное наименование", p.fullName, { p = p.copy(fullName = it) }, singleLine = false)
        Spacer(Modifier.height(T.md))
        Row {
            Box(Modifier.weight(1f)) {
                Field("ИНН", p.inn, { p = p.copy(inn = it.filter { c -> c.isDigit() }.take(12)) }, numeric = true)
            }
            Spacer(Modifier.width(T.md))
            Box(Modifier.weight(1f)) {
                Field("КПП", p.kpp, { p = p.copy(kpp = it.filter { c -> c.isDigit() }.take(9)) }, numeric = true)
            }
        }
        Spacer(Modifier.height(T.md))
        Field("Руководитель", p.director, { p = p.copy(director = it) }, placeholder = "Н. А. Пронин")
        Spacer(Modifier.height(T.md))
        Field("Телефон", p.phone, { p = p.copy(phone = it) }, placeholder = "+7 …")
        Spacer(Modifier.height(T.md))
        Field("E-mail", p.email, { p = p.copy(email = it) })

        Spacer(Modifier.height(T.md))
        Pressable({ details = !details }, Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = T.touchMin),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Q(if (details) "Свернуть реквизиты" else "Банк и реквизиты", Type.small, T.action.ink, 1, Modifier.weight(1f))
                QIcon(if (details) Ic.chevronLeft else Ic.chevronRight, size = 18.dp, tint = T.action.ink)
            }
        }

        if (details) {
            Field("ОГРН", p.ogrn, { p = p.copy(ogrn = it.filter { c -> c.isDigit() }.take(15)) }, numeric = true)
            Spacer(Modifier.height(T.md))
            Field("Юридический адрес", p.legalAddress, { p = p.copy(legalAddress = it) }, singleLine = false)
            Spacer(Modifier.height(T.md))
            Field("Банк", p.bank, { p = p.copy(bank = it) }, placeholder = "ПАО Сбербанк")
            Spacer(Modifier.height(T.md))
            Field("Расчётный счёт", p.account, { p = p.copy(account = it.filter { c -> c.isDigit() }.take(20)) }, numeric = true)
            Spacer(Modifier.height(T.md))
            Field("БИК", p.bik, { p = p.copy(bik = it.filter { c -> c.isDigit() }.take(9)) }, numeric = true)
            Spacer(Modifier.height(T.md))
        }

        Field("Заметки", p.note, { p = p.copy(note = it) }, singleLine = false, imeAction = ImeAction.Done)
    }
}

@Composable
fun EmployeeForm(
    initial: Employee,
    db: Db,
    onPick: (PickRequest) -> Unit,
    onDone: (Employee) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var e by remember(initial.id) { mutableStateOf(initial) }
    // Телефоны и чаты редактируются как строки через запятую — на телефоне
    // это быстрее, чем плодить кнопки «добавить ещё».
    var phonesText by remember(initial.id) { mutableStateOf(initial.phones.joinToString(", ")) }
    var tgText by remember(initial.id) {
        mutableStateOf(initial.chats.firstOrNull { it.kind == "telegram" }?.handle ?: "")
    }
    var waText by remember(initial.id) {
        mutableStateOf(initial.chats.firstOrNull { it.kind == "whatsapp" }?.handle ?: "")
    }
    var mailText by remember(initial.id) {
        mutableStateOf(initial.chats.firstOrNull { it.kind == "email" }?.handle ?: "")
    }

    FormShell(
        title = if (initial.name.isBlank()) "Новый сотрудник" else "Сотрудник",
        code = initial.tabNumber.takeIf { it.isNotBlank() }?.let { "таб. № $it" } ?: "",
        canSave = e.name.isNotBlank(),
        onSave = {
            val chats = listOfNotNull(
                tgText.trim().takeIf { it.isNotBlank() }?.let { Chat("telegram", it) },
                waText.trim().takeIf { it.isNotBlank() }?.let { Chat("whatsapp", it) },
                mailText.trim().takeIf { it.isNotBlank() }?.let { Chat("email", it) }
            )
            onDone(
                e.copy(
                    phones = phonesText.split(',', ';').map { it.trim() }.filter { it.isNotBlank() },
                    chats = chats
                )
            )
        },
        onCancel = onCancel,
        onDelete = onDelete
    ) {
        Field("Ф. И. О.", e.name, { e = e.copy(name = it) }, placeholder = "Климов Владимир Андреевич")
        Spacer(Modifier.height(T.md))
        PickerRow("Отдел", e.department, onClick = {
            onPick(PickRequest.Values("Отдел", db.refs.departments, e.department) { e = e.copy(department = it) })
        })
        Spacer(Modifier.height(T.md))
        Field("Должность", e.position, { e = e.copy(position = it) }, placeholder = "Инженер ПТО")
        Spacer(Modifier.height(T.md))
        Field("Телефоны", phonesText, { phonesText = it }, placeholder = "+7 …, +7 …",
            hint = "Несколько — через запятую")
        Spacer(Modifier.height(T.md))
        Field("Telegram", tgText, { tgText = it }, placeholder = "@ник или +7 …")
        Spacer(Modifier.height(T.md))
        Field("WhatsApp", waText, { waText = it }, placeholder = "+7 …")
        Spacer(Modifier.height(T.md))
        Field("E-mail", mailText, { mailText = it })
        Spacer(Modifier.height(T.md))
        Row {
            Box(Modifier.weight(1f)) { Field("Таб. №", e.tabNumber, { e = e.copy(tabNumber = it) }, placeholder = "004") }
            Spacer(Modifier.width(T.md))
            Box(Modifier.weight(1f)) { Field("Нахождение", e.location, { e = e.copy(location = it) }, placeholder = "Офис, ЕКБ") }
        }
        Spacer(Modifier.height(T.md))
        Field("Комментарий", e.note, { e = e.copy(note = it) }, singleLine = false, imeAction = ImeAction.Done)
    }
}

/**
 * Общая рамка формы: прокручиваемая середина и прибитые к низу кнопки.
 * Кнопки не уезжают за экран вместе с содержимым — иначе на длинной форме
 * «Сохранить» приходится искать прокруткой.
 */
@Composable
private fun FormShell(
    title: String,
    code: String,
    canSave: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = T.lg, end = T.lg, top = T.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Q(title, Type.title, T.text, 1, Modifier.weight(1f))
                if (code.isNotBlank()) {
                    Q(code, Type.smallNum, T.text3)
                }
            }
            Spacer(Modifier.height(T.lg))
            content()
            Spacer(Modifier.height(T.lg))

            if (onDelete != null) {
                Pressable({ if (confirmDelete) onDelete() else confirmDelete = true }, Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = T.touchMin)
                            .clip(RoundedCornerShape(T.rControl))
                            .background(if (confirmDelete) T.dangerTone.chip else T.bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Q(
                            if (confirmDelete) "Нажми ещё раз, чтобы удалить" else "Удалить запись",
                            Type.small, T.dangerTone.ink
                        )
                    }
                }
                Spacer(Modifier.height(T.md))
            }
        }

        Column(Modifier.padding(start = T.lg, end = T.lg, top = T.sm, bottom = T.lg)) {
            PrimaryButton("Сохранить", onSave, enabled = canSave)
            Spacer(Modifier.height(T.sm))
            GhostButton("Отмена", onCancel, Modifier.fillMaxWidth())
        }
    }
}

/** 2335.52 без хвоста нулей, 30.0 как 30. */
fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
