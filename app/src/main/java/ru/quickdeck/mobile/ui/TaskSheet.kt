package ru.quickdeck.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Actions
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.*

/**
 * Постановка задачи: кому → что → отправить. Три шага, один экран.
 *
 * Обращение не навязывается: формат задаётся один раз в настройках и
 * подставляется сам. Хочешь без «здравствуйте» — значит без него.
 */
@Composable
fun ColumnScope.TaskSheet(
    employee: Employee,
    db: Db,
    onPick: (PickRequest) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current

    var greeting by remember { mutableStateOf(Store.greeting) }
    var body by remember { mutableStateOf("") }
    var siteId by remember { mutableStateOf<String?>(null) }
    var contractId by remember { mutableStateOf<String?>(null) }

    val site = db.site(siteId)
    val contract = db.contract(contractId)

    // Текст собирается на лету — человек видит ровно то, что уйдёт.
    val message = remember(greeting, body, employee, site, contract) {
        val head = fillTemplate(greeting, name = employee.name)
        val text = fillTemplate(
            body,
            name = employee.name,
            site = site?.name.orEmpty(),
            contract = contract?.label(site?.name).orEmpty(),
            due = contract?.end?.takeIf { it.isNotBlank() }?.let { dateShort(it) }.orEmpty(),
            status = contract?.let { shownStatus(it).label }.orEmpty()
        )
        listOf(head, text).filter { it.isNotBlank() }.joinToString(" ")
    }

    Row(
        Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Q("Задача", Type.title, T.text, 1)
            Q(employee.name, Type.small, T.text2, 1)
        }
        Pressable(onClose) {
            Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                QIcon(Ic.close, size = 20.dp, tint = T.text2)
            }
        }
    }

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = T.lg)
    ) {
        Spacer(Modifier.height(T.md))

        Q("Шаблон", Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(T.sm)
        ) {
            db.templates.forEach { t ->
                Pressable({ body = t.body }) {
                    Box(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(if (body == t.body) T.accent.chip else T.surface)
                            .padding(horizontal = T.md),
                        contentAlignment = Alignment.Center
                    ) {
                        Q(t.title, Type.caption, if (body == t.body) T.accent.ink else T.text2, 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(T.md))
        PickerRow("Объект (необязательно)", site?.name, onClick = {
            onPick(PickRequest.SitePick { s -> siteId = s.id })
        })
        if (site != null) {
            val contracts = db.contractsOfSite(site.id)
            if (contracts.isNotEmpty()) {
                Spacer(Modifier.height(T.md))
                PickerRow("Договор", contract?.label(site.name), onClick = {
                    onPick(
                        PickRequest.Values(
                            "Договор",
                            contracts.map { it.label(site.name) },
                            contract?.label(site.name).orEmpty()
                        ) { picked ->
                            contractId = contracts.firstOrNull { it.label(site.name) == picked }?.id
                        }
                    )
                })
            }
        }

        Spacer(Modifier.height(T.md))
        Field(
            "Текст задачи", body, { body = it },
            placeholder = "Что нужно сделать…",
            singleLine = false
        )

        Spacer(Modifier.height(T.md))
        Field(
            "Обращение", greeting, { greeting = it; Store.greeting = it },
            placeholder = "{Имя}, — или оставь пустым",
            hint = "Подставится в начало. Переменные: {Имя}, {ФИО}",
            imeAction = ImeAction.Done
        )

        if (message.isNotBlank()) {
            Spacer(Modifier.height(T.lg))
            Q("Уйдёт вот так", Type.caption, T.text3)
            Spacer(Modifier.height(T.xs))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(T.rControl))
                    .background(T.accent.chip)
                    .padding(T.md)
            ) { Q(message, Type.body, T.accent.ink) }
        }

        Spacer(Modifier.height(T.lg))
    }

    Column(Modifier.padding(start = T.lg, end = T.lg, top = T.sm, bottom = T.lg)) {
        // Каждый канал — своя кнопка: выбор способа и есть выбор адресата.
        val chats = employee.chats
        if (chats.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(T.sm)
            ) {
                chats.forEach { chat ->
                    val label = when (chat.kind) {
                        "telegram" -> "Telegram"
                        "whatsapp" -> "WhatsApp"
                        "email" -> "Почта"
                        else -> chat.kind
                    }
                    ActionPill(if (chat.kind == "email") Ic.mail else Ic.chat, label, T.accent) {
                        Actions.message(ctx, chat, employee.phones.firstOrNull(), message)
                        onClose()
                    }
                }
                employee.phones.firstOrNull()?.let { phone ->
                    ActionPill(Ic.phone, "SMS", T.success) {
                        Actions.message(ctx, null, phone, message)
                        onClose()
                    }
                }
            }
            Spacer(Modifier.height(T.sm))
        }

        PrimaryButton("Отправить", {
            Actions.message(ctx, employee.chats.firstOrNull(), employee.phones.firstOrNull(), message)
            onClose()
        }, enabled = message.isNotBlank())
        Spacer(Modifier.height(T.sm))
        GhostButton("Скопировать текст", {
            Actions.share(ctx, message, "Задача")
        }, Modifier.fillMaxWidth())
    }
}

/**
 * Управление шаблонами. Встроенные можно менять и удалять — это просто
 * стартовый набор, а не что-то священное.
 */
@Composable
fun ColumnScope.TemplatesSheet(db: Db, onClose: () -> Unit) {
    var editing by remember { mutableStateOf<MsgTemplate?>(null) }

    Row(
        Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Q("Шаблоны сообщений", Type.title, T.text, 1, Modifier.weight(1f))
        Pressable({ editing = MsgTemplate() }) {
            Box(
                Modifier.size(T.touchMin).clip(RoundedCornerShape(percent = 50)).background(T.accent.fill),
                contentAlignment = Alignment.Center
            ) { QIcon(Ic.plus, size = 20.dp, tint = androidx.compose.ui.graphics.Color.White, stroke = 2f) }
        }
        Pressable(onClose) {
            Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                QIcon(Ic.close, size = 20.dp, tint = T.text2)
            }
        }
    }

    val draft = editing
    if (draft != null) {
        var title by remember(draft.id) { mutableStateOf(draft.title) }
        var text by remember(draft.id) { mutableStateOf(draft.body) }

        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(T.lg)
        ) {
            Field("Название", title, { title = it }, placeholder = "Проверить объект")
            Spacer(Modifier.height(T.md))
            Field(
                "Текст", text, { text = it },
                placeholder = "{Имя}, по объекту {Объект} нужно…",
                singleLine = false,
                hint = "Переменные: {Имя}, {Объект}, {Договор}, {Срок}, {Статус}"
            )
            Spacer(Modifier.height(T.lg))
            PrimaryButton("Сохранить", {
                Store.upsertTemplate(draft.copy(title = title.trim(), body = text.trim(), builtin = false))
                editing = null
            }, enabled = title.isNotBlank() && text.isNotBlank())
            Spacer(Modifier.height(T.sm))
            GhostButton("Отмена", { editing = null }, Modifier.fillMaxWidth())
        }
        return
    }

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = T.lg)
    ) {
        Spacer(Modifier.height(T.sm))
        db.templates.forEach { t ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = T.xs)
                    .clip(RoundedCornerShape(T.rControl))
                    .background(T.surface)
                    .padding(T.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Q(t.title, Type.heading, T.text, 1)
                    Q(t.body, Type.small, T.text2, 3)
                }
                Spacer(Modifier.width(T.sm))
                Pressable({ editing = t }) {
                    Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                        QIcon(Ic.edit, size = 18.dp, tint = T.text2)
                    }
                }
                Pressable({ Store.deleteTemplate(t.id) }) {
                    Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                        QIcon(Ic.trash, size = 18.dp, tint = T.danger.ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(T.lg))
    }
}
