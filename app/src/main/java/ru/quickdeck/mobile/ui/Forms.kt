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
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.*

/** Что выбирается из реестра, когда форме нужна ссылка на другую запись. */
sealed interface PickRequest {
    data class Customer(val onPick: (Party) -> Unit) : PickRequest
    data class Contractor(val onPick: (Party) -> Unit) : PickRequest
    data class SitePick(val onPick: (Site) -> Unit) : PickRequest
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
    var dateText by remember(initial.id) { mutableStateOf(dateInput(initial.deadline)) }
    var progressText by remember(initial.id) {
        mutableStateOf(if (initial.progress == 0) "" else initial.progress.toString())
    }

    FormShell(
        title = if (initial.name.isBlank()) "Новый объект" else "Объект",
        canSave = s.name.isNotBlank(),
        onSave = {
            onDone(
                s.copy(
                    deadline = parseHumanDate(dateText),
                    progress = progressText.toIntOrNull()?.coerceIn(0, 100) ?: 0
                )
            )
        },
        onCancel = onCancel,
        onDelete = onDelete
    ) {
        Field("Краткое наименование", s.name, { s = s.copy(name = it) }, placeholder = "Цимлянская 17")
        Spacer(Modifier.height(T.md))
        PickerRow("Заказчик", db.customer(s.customerId)?.name, onClick = {
            onPick(PickRequest.Customer { p -> s = s.copy(customerId = p.id) })
        })
        Spacer(Modifier.height(T.md))
        Field("Адрес", s.address, { s = s.copy(address = it) }, placeholder = "Луганск, ул. Цимлянская, 17")
        Spacer(Modifier.height(T.md))
        Row {
            Box(Modifier.weight(1f)) {
                Field("Срок сдачи", dateText, { dateText = it }, placeholder = "30.09.2026", numeric = true)
            }
            Spacer(Modifier.width(T.md))
            Box(Modifier.width(112.dp)) {
                Field(
                    "Готовность", progressText,
                    { progressText = it.filter { c -> c.isDigit() }.take(3) },
                    placeholder = "0", numeric = true
                )
            }
        }
        Spacer(Modifier.height(T.md))
        StatusPicker(s.status, Status.forSite) { s = s.copy(status = it) }
        Spacer(Modifier.height(T.md))
        Field("Примечание", s.note, { s = s.copy(note = it) }, singleLine = false)
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

    FormShell(
        title = if (initial.number.isBlank()) "Новый договор" else "Договор",
        canSave = c.number.isNotBlank(),
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
        Field("Номер", c.number, { c = c.copy(number = it) }, placeholder = "14")
        Spacer(Modifier.height(T.md))
        PickerRow("Объект", db.site(c.siteId)?.name, onClick = {
            onPick(PickRequest.SitePick { site ->
                // заказчик подтягивается с объекта, если ещё не выбран
                c = c.copy(siteId = site.id, customerId = c.customerId ?: site.customerId)
            })
        })
        Spacer(Modifier.height(T.md))
        PickerRow("Заказчик", db.customer(c.customerId)?.name, onClick = {
            onPick(PickRequest.Customer { p -> c = c.copy(customerId = p.id) })
        })
        Spacer(Modifier.height(T.md))
        PickerRow("Исполнитель", db.contractor(c.contractorId)?.name, onClick = {
            onPick(PickRequest.Contractor { p -> c = c.copy(contractorId = p.id) })
        })
        Spacer(Modifier.height(T.md))
        Field(
            "Сумма, ₽", amountText,
            { amountText = it.filter { ch -> ch.isDigit() } },
            placeholder = "1250000", numeric = true
        )
        if (amountText.isNotBlank()) {
            Spacer(Modifier.height(T.xs))
            Q(money(amountText.toLongOrNull() ?: 0L), Type.smallNum, T.text3)
        }
        Spacer(Modifier.height(T.md))
        Row {
            Box(Modifier.weight(1f)) {
                Field("Начало", startText, { startText = it }, placeholder = "01.04.2026", numeric = true)
            }
            Spacer(Modifier.width(T.md))
            Box(Modifier.weight(1f)) {
                Field("Срок", endText, { endText = it }, placeholder = "30.09.2026", numeric = true)
            }
        }
        Spacer(Modifier.height(T.md))
        StatusPicker(c.status, Status.forContract) { c = c.copy(status = it) }
        Spacer(Modifier.height(T.md))
        Field("Примечание", c.note, { c = c.copy(note = it) }, singleLine = false)
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

    FormShell(
        title = title,
        canSave = p.name.isNotBlank(),
        onSave = { onDone(p) },
        onCancel = onCancel,
        onDelete = onDelete
    ) {
        Field("Наименование", p.name, { p = p.copy(name = it) }, placeholder = "ЭнергоКомплекс")
        Spacer(Modifier.height(T.md))
        Field("ИНН", p.inn, { p = p.copy(inn = it.filter { c -> c.isDigit() }.take(12)) }, numeric = true)
        Spacer(Modifier.height(T.md))
        Field("Контактное лицо", p.contact, { p = p.copy(contact = it) })
        Spacer(Modifier.height(T.md))
        Field("Телефон", p.phone, { p = p.copy(phone = it) }, placeholder = "+7 ...")
        Spacer(Modifier.height(T.md))
        Field("Примечание", p.note, { p = p.copy(note = it) }, singleLine = false, imeAction = ImeAction.Done)
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
            Q(title, Type.title, T.text)
            Spacer(Modifier.height(T.lg))
            content()
            Spacer(Modifier.height(T.lg))

            if (onDelete != null) {
                Pressable({
                    if (confirmDelete) onDelete() else confirmDelete = true
                }, Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = T.touchMin)
                            .clip(RoundedCornerShape(T.rControl))
                            .background(if (confirmDelete) T.danger.chip else T.bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Q(
                            if (confirmDelete) "Нажми ещё раз, чтобы удалить" else "Удалить запись",
                            Type.small,
                            T.danger.ink
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
