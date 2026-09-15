package ru.quickdeck.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
 * Объект не спрашивается заново, если и так понятно, о чём речь: задача,
 * начатая из карточки объекта или договора, приходит уже привязанной.
 * Привязка одна и та же на всё время работы с задачей — сколько бы раз ни
 * применялся шаблон, второй связи не появляется.
 *
 * Обращение не навязывается: формат задаётся один раз и подставляется сам.
 */
@Composable
fun ColumnScope.TaskSheet(
    employee: Employee,
    db: Db,
    onPick: (PickRequest) -> Unit,
    onClose: () -> Unit,
    contextSiteId: String? = null,
    contextContractId: String? = null
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Черновик переживает перезапуск: привязка и текст не теряются, если
    // человека отвлекли на полпути.
    val saved = remember(employee.id) { Store.taskDraft(employee.id) }

    var greeting by remember { mutableStateOf(Store.greeting) }
    var body by remember { mutableStateOf(saved?.body.orEmpty()) }
    var templateId by remember { mutableStateOf(saved?.templateId) }
    var siteId by remember {
        mutableStateOf(contextSiteId ?: saved?.siteId ?: contractSite(db, contextContractId))
    }
    var contractId by remember { mutableStateOf(contextContractId ?: saved?.contractId) }
    var sending by remember { mutableStateOf(false) }

    val site = db.site(siteId)
    val contract = db.contract(contractId)
    // Привязка пришла из карточки — значит спрашивать было не о чем.
    val fromContext = contextSiteId != null || contextContractId != null

    // Значения переменных — один набор и для текста, и для предупреждений.
    val values = remember(employee, site, contract) {
        templateValues(
            name = employee.name,
            site = site?.name.orEmpty(),
            contract = contract?.label(site?.name).orEmpty(),
            due = contract?.end?.takeIf { it.isNotBlank() }?.let { dateShort(it) }.orEmpty(),
            status = contract?.let { shownStatus(it).label }.orEmpty()
        )
    }

    // Текст собирается заново из исходного шаблона — подстановки не копятся.
    val message = remember(greeting, body, values) {
        val head = fillTemplate(greeting, values)
        val text = fillTemplate(body, values)
        listOf(head, text).filter { it.isNotBlank() }.joinToString(" ")
    }
    val unknown = remember(body, greeting) { unknownVars(greeting) + unknownVars(body) }
    val missing = remember(body, greeting, values) {
        (missingVars(greeting, values) + missingVars(body, values)).distinct()
    }

    // Черновик пишется по мере работы — отдельного «сохранить» тут не нужно.
    LaunchedEffect(body, templateId, siteId, contractId) {
        Store.saveTaskDraft(employee.id, TaskDraft(templateId, body, siteId, contractId))
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
                // Выбранным считается шаблон по id, а не по совпадению текста:
                // иначе правка руками молча снимала бы выделение.
                val on = templateId == t.id
                val fill by animateColorAsState(
                    targetValue = if (on) T.accent.chip else T.surface,
                    animationSpec = tween(T.MS_PRESS, easing = T.curve),
                    label = "tplFill"
                )
                Pressable({
                    body = t.body
                    templateId = t.id
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }) {
                    Row(
                        Modifier
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(fill)
                            .border(
                                if (on) 1.5.dp else 1.dp,
                                if (on) T.accent.fill else T.hairline,
                                RoundedCornerShape(percent = 50)
                            )
                            .padding(horizontal = T.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (on) {
                            QIcon(Ic.check, size = 16.dp, tint = T.accent.ink, stroke = 2f)
                            Spacer(Modifier.width(T.xs))
                        }
                        Q(t.title, Type.caption, if (on) T.accent.ink else T.text2, 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(T.md))
        PickerRow(
            label = if (fromContext) "Объект (из карточки)" else "Объект",
            value = site?.name,
            sub = contract?.workKind,
            onClick = {
                onPick(PickRequest.SitePick { s ->
                    if (s.id != siteId) {
                        siteId = s.id
                        contractId = null      // связь одна: старый договор не тянем
                    }
                })
            }
        )
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

        // Про переменные говорим до отправки, а не показываем скобки адресату.
        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(T.md))
            Notice(
                T.warning,
                "Нет данных: " + missing.joinToString(", ") { "{$it}" },
                if (missing.contains("Объект")) "Выбери объект — иначе в тексте будет пропуск"
                else "Значение подставится пустым"
            )
        }
        if (unknown.isNotEmpty()) {
            Spacer(Modifier.height(T.sm))
            Notice(
                T.danger,
                "Неизвестные переменные: " + unknown.joinToString(", ") { "{$it}" },
                "Они уйдут как есть. Известные: " + templateVars.joinToString(", ") { "{$it}" }
            )
        }

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

        fun deliver(chat: Chat?, phone: String?) {
            if (sending) return
            sending = true
            val ok = Actions.message(ctx, chat, phone, message)
            if (ok) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Store.clearTaskDraft(employee.id)
                onClose()
            } else {
                sending = false
            }
        }

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
                        deliver(chat, employee.phones.firstOrNull())
                    }
                }
                employee.phones.firstOrNull()?.let { phone ->
                    ActionPill(Ic.phone, "SMS", T.success) { deliver(null, phone) }
                }
            }
            Spacer(Modifier.height(T.sm))
        }

        PrimaryButton(
            if (sending) "Отправляем…" else "Отправить",
            { deliver(employee.chats.firstOrNull(), employee.phones.firstOrNull()) },
            enabled = message.isNotBlank() && !sending
        )
        Spacer(Modifier.height(T.sm))
        GhostButton("Скопировать текст", {
            Actions.share(ctx, message, "Задача")
        }, Modifier.fillMaxWidth())
    }
}

/** Объект, к которому относится договор — чтобы привязка пришла целиком. */
private fun contractSite(db: Db, contractId: String?): String? =
    contractId?.let { db.contract(it)?.siteId }

/** Короткая плашка-предупреждение. Цвет тут не единственный признак — есть текст. */
@Composable
private fun Notice(tone: T.Tone, title: String, hint: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(T.rControl))
            .background(tone.chip)
            .padding(T.md)
    ) {
        QIcon(Ic.alert, size = 18.dp, tint = tone.ink, stroke = 2f)
        Spacer(Modifier.width(T.sm))
        Column(Modifier.weight(1f)) {
            Q(title, Type.small, tone.ink, 2)
            Spacer(Modifier.height(T.xs))
            Q(hint, Type.caption, tone.ink.copy(alpha = 0.8f), 2)
        }
    }
}

/**
 * Управление шаблонами. Встроенные можно менять и удалять — это просто
 * стартовый набор, а не что-то священное.
 *
 * Название и текст — разные поля с разным смыслом: название живёт на чипе
 * выбора, текст уходит адресату. Если они совпали, второй раз одно и то же
 * не показывается.
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
            ) { QIcon(Ic.plus, size = 20.dp, tint = Color.White, stroke = 2f) }
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
        val bad = remember(text) { unknownVars(text) }

        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(T.lg)
        ) {
            Field("Название", title, { title = it }, placeholder = "Проверить объект",
                hint = "Видно только тебе — на кнопке выбора")
            Spacer(Modifier.height(T.md))
            Field(
                "Текст сообщения", text, { text = it },
                placeholder = "{Имя}, по объекту {Объект} нужно…",
                singleLine = false,
                hint = "Уйдёт адресату. Переменные: " + templateVars.joinToString(", ") { "{$it}" }
            )
            if (bad.isNotEmpty()) {
                Spacer(Modifier.height(T.md))
                Notice(
                    T.danger,
                    "Неизвестные переменные: " + bad.joinToString(", ") { "{$it}" },
                    "Проверь написание — подставляются только известные"
                )
            }
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
            // Название и текст — разные данные. Одинаковые не дублируем.
            val text = t.body.takeIf { !it.trim().equals(t.title.trim(), true) }.orEmpty()
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = T.xs)
                    .clip(RoundedCornerShape(T.rCard))
                    .background(T.surface)
                    .border(1.dp, T.hairline, RoundedCornerShape(T.rCard))
                    .padding(T.md),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Q(t.title.ifBlank { "Без названия" }, Type.heading, T.text, 1)
                    if (text.isNotBlank()) {
                        Spacer(Modifier.height(T.xs))
                        Q(text, Type.small, T.text2, 3)
                    }
                    val used = templateVars.filter { t.body.contains("{$it}") }
                    if (used.isNotEmpty()) {
                        Spacer(Modifier.height(T.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(T.xs)) {
                            used.forEach { v ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(T.accent.chip)
                                        .padding(horizontal = T.sm, vertical = 2.dp)
                                ) { Q(v, Type.caption, T.accent.ink, 1) }
                            }
                        }
                    }
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
