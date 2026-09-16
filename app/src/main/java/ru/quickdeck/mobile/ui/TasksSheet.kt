package ru.quickdeck.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Feel
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.Contract
import ru.quickdeck.mobile.data.ContractTask
import ru.quickdeck.mobile.data.Db
import ru.quickdeck.mobile.data.Store

/**
 * Задачи по договору.
 *
 * Стандартного набора тут быть не может: у одного договора цель — пройти
 * экспертизу, у другого — забрать исходники у заказчика, у третьего —
 * дожать оплату. Поэтому задачи пишутся руками, одной строкой, и живут
 * ровно в том договоре, к которому относятся.
 *
 * Экран простой намеренно: поле ввода вверху, список под ним. Ни статусов,
 * ни исполнителей, ни приоритетов — задача либо сделана, либо нет. Всё
 * остальное превратило бы заметку в трекер, которым никто не пользуется.
 */
@Composable
fun ColumnScope.TasksSheet(contract: Contract, db: Db, onClose: () -> Unit) {
    var input by remember(contract.id) { mutableStateOf("") }
    var editing by remember(contract.id) { mutableStateOf<ContractTask?>(null) }

    val site = db.site(contract.siteId)?.name.orEmpty()

    Row(
        Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Q("Задачи", Type.title, T.text, 1)
            Q(
                listOf(contract.workKind, site).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { "По договору" },
                Type.small, T.text2, 1
            )
        }
        Pressable(onClose) {
            Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                QIcon(Ic.close, size = 20.dp, tint = T.text2)
            }
        }
    }

    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(T.lg)
    ) {
        val target = editing
        Field(
            label = if (target == null) "Новая задача" else "Меняем задачу",
            value = if (target == null) input else input,
            onChange = { input = it },
            placeholder = "Забрать исходники у заказчика",
            singleLine = false,
            hint = "Одна строка — одно дело. Сроки и цифры пиши прямо в тексте."
        )
        Spacer(Modifier.height(T.md))
        Row(horizontalArrangement = Arrangement.spacedBy(T.sm)) {
            PrimaryButton(
                if (target == null) "Добавить" else "Сохранить",
                onClick = {
                    if (target == null) Store.addContractTask(contract.id, input)
                    else Store.renameContractTask(contract.id, target.id, input)
                    Feel.confirm()
                    input = ""
                    editing = null
                },
                modifier = Modifier.weight(1f),
                enabled = input.isNotBlank()
            )
            if (target != null) {
                GhostButton("Отмена", { editing = null; input = "" }, Modifier.weight(1f))
            }
        }

        if (contract.tasks.isEmpty()) {
            Spacer(Modifier.height(T.xl))
            Q("Пока ни одной задачи", Type.heading, T.text2)
            Spacer(Modifier.height(T.xs))
            Q(
                "Напиши, что нужно сделать по этому договору — строка появится и здесь, и в карточке.",
                Type.small, T.text3
            )
            Spacer(Modifier.height(T.xxl))
            return@Column
        }

        Spacer(Modifier.height(T.xl))
        val open = contract.tasks.filterNot { it.done }
        val done = contract.tasks.filter { it.done }

        if (open.isNotEmpty()) {
            Q("Осталось · ${open.size}", Type.caption, T.text3)
            Spacer(Modifier.height(T.sm))
            open.forEach { t ->
                TaskRow(t, contract.id) { input = t.text; editing = t }
                Spacer(Modifier.height(T.xs))
            }
        }

        if (done.isNotEmpty()) {
            Spacer(Modifier.height(T.lg))
            Q("Сделано · ${done.size}", Type.caption, T.text3)
            Spacer(Modifier.height(T.sm))
            done.forEach { t ->
                TaskRow(t, contract.id) { input = t.text; editing = t }
                Spacer(Modifier.height(T.xs))
            }
        }

        Spacer(Modifier.height(T.xxl))
    }
}

/**
 * Строка задачи: квадрат-галочка слева, текст, карандаш и корзина справа.
 *
 * Каждое действие — своя мишень со своим знаком, а не спрятанное длинное
 * нажатие: угадывать, что строка что-то умеет, человек не обязан.
 */
@Composable
private fun TaskRow(task: ContractTask, contractId: String, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(T.rCard))
            .background(T.surface)
            .border(1.dp, T.hairline, RoundedCornerShape(T.rCard)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pressable({
            Feel.confirm()
            Store.setContractTaskDone(contractId, task.id, !task.done)
        }) {
            Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (task.done) T.success.fill else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (task.done) T.success.fill else T.hairline,
                            RoundedCornerShape(7.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (task.done) QIcon(Ic.check, size = 14.dp, tint = Color.White, stroke = 2.4f)
                }
            }
        }
        Q(
            task.text,
            Type.small,
            if (task.done) T.text3 else T.text,
            4,
            Modifier.weight(1f).padding(vertical = T.sm)
        )
        Spacer(Modifier.width(T.xs))
        Pressable(onEdit) {
            Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                QIcon(Ic.edit, size = 18.dp, tint = T.text2)
            }
        }
        Pressable({
            Feel.warn()
            Store.deleteContractTask(contractId, task.id)
        }) {
            Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                QIcon(Ic.trash, size = 18.dp, tint = T.dangerTone.ink)
            }
        }
    }
}

/**
 * Адрес таблицы.
 *
 * Одно поле — и потому отдельного экрана настроек у него нет: строка в
 * «Ещё» открывает этот лист, человек вставляет ссылку и закрывает.
 */
@Composable
fun ColumnScope.SheetUrlStep(onClose: () -> Unit) {
    var url by remember { mutableStateOf(Store.sheetsUrl) }

    Row(
        Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Q("Google-таблица", Type.title, T.text, 1)
            Q("Двусторонний обмен", Type.small, T.text2, 1)
        }
        Pressable(onClose) {
            Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                QIcon(Ic.close, size = 20.dp, tint = T.text2)
            }
        }
    }

    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(T.lg)) {
        Field(
            "Ссылка веб-приложения", url,
            { url = it; Store.sheetsUrl = it.trim() },
            placeholder = "https://script.google.com/macros/s/.../exec",
            singleLine = false,
            hint = "Развернуть → Веб-приложение → доступ «у всех» → ссылка /exec"
        )
        Spacer(Modifier.height(T.lg))
        PrimaryButton("Готово", { Feel.confirm(); onClose() }, enabled = true)
        Spacer(Modifier.height(T.xxl))
    }
}
