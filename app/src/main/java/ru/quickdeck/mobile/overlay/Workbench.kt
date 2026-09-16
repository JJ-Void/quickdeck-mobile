package ru.quickdeck.mobile.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Feel
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.Db
import ru.quickdeck.mobile.data.Section
import ru.quickdeck.mobile.data.Stage
import ru.quickdeck.mobile.data.Status
import ru.quickdeck.mobile.data.Store
import ru.quickdeck.mobile.data.summary
import ru.quickdeck.mobile.data.money
import ru.quickdeck.mobile.ui.Pressable
import ru.quickdeck.mobile.ui.shownStatus
import ru.quickdeck.mobile.ui.tone

/**
 * Панель поверх чужих экранов — тёмная ветка той же системы, что и приложение.
 *
 * Читается слоями: полка папок -> записи -> запись. Смысл слоёв в том, чтобы
 * не вникать в лишнее: сначала «что за пачка», потом «что внутри». Плоский
 * список с сегментами вверху это убивал — всё четыре раздела спорили за
 * внимание одновременно, и панель превращалась в таблицу.
 *
 * Правила те же, что в DESIGN-SYSTEM.md: один очаг на слой, три элемента в
 * карточке, 80 % текста приглушено, один акцент. Отличается только грунт:
 * светлая поверхность поверх карты, видео или галереи не читается.
 *
 * Ни одного жеста поверх системных: только тапы и вертикальная прокрутка.
 */
@Composable
fun ColumnScope.WorkbenchLayer(db: Db, host: OverlayHost) {
    val card = OverlayState.card
    val atFolders = OverlayState.atFolders
    val section = OverlayState.section

    val depth = if (card != null) 2 else if (atFolders) 0 else 1
    val key = when {
        card != null -> "card:${card.section}:${card.id}"
        atFolders -> "folders"
        else -> "items:$section"
    }

    Header(host, depth, section, card)

    AnimatedContent(
        targetState = Step(depth, key),
        modifier = Modifier.weight(1f).fillMaxWidth(),
        transitionSpec = {
            val forward = targetState.depth > initialState.depth
            val shift = if (forward) 1 else -1
            (
                slideInHorizontally(tween(T.MS_SCREEN, easing = T.curve)) { w -> shift * w / 5 } +
                    fadeIn(tween(T.MS_SCREEN, easing = T.curve))
                ) togetherWith (
                slideOutHorizontally(tween(T.MS_EXIT, easing = T.curve)) { w -> -shift * w / 6 } +
                    fadeOut(tween(T.MS_EXIT))
                )
        },
        label = "layer"
    ) { st ->
        when {
            st.depth == 2 && card != null -> DetailLayer(db, host, card)
            st.depth == 0 -> FoldersLayer(db)
            else -> ItemsLayer(db, section, OverlayState.group, host)
        }
    }
}

/** Куда едет слой, считается из самого перехода, а не из внешней переменной. */
private data class Step(val depth: Int, val key: String)

// --- шапка ----------------------------------------------------------------

@Composable
private fun Header(host: OverlayHost, depth: Int, section: Section, card: CardRef?) {
    Row(
        Modifier.fillMaxWidth().padding(start = T.md, end = T.sm, top = T.sm, bottom = T.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (depth > 0) {
            RoundAction(Ic.chevronLeft, "Назад") {
                Feel.tick()
                if (depth == 2) OverlayState.backFromCard() else OverlayState.backToFolders()
            }
            Spacer(Modifier.width(T.sm))
        }
        Column(Modifier.weight(1f)) {
            Q(
                when (depth) {
                    0 -> "Подряд"
                    1 -> section.title
                    else -> card?.section?.one.orEmpty()
                },
                Type.heading, T.textOnDark, 1
            )
            if (depth == 0) {
                val last = Store.lastSync
                Q(
                    if (last.isBlank()) "Обмен не настроен" else "Обновлено $last",
                    Type.label, T.text2OnDark, 1
                )
            }
        }
        if (depth <= 1) {
            RoundAction(Ic.plus, "Добавить", accent = true) { host.openForm(section, null) }
            Spacer(Modifier.width(T.xs))
        }
        RoundAction(Ic.close, "Закрыть") { OverlayState.close() }
    }
}

// --- слой 0: полка папок --------------------------------------------------

/**
 * Первый слой: один очаг и четыре папки. Сводка здесь не таблица из четырёх
 * равновеликих чисел — они спорили за внимание и не читались ни одной. Одно
 * число крупно, остальное строкой под ним.
 */
@Composable
private fun FoldersLayer(db: Db) {
    val s = remember(db) { db.summary() }
    val hot = remember(db) { hotCounts(db) }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = T.lg, end = T.lg, bottom = T.lg)
    ) {
        item {
            Fade(0) {
                Column(Modifier.fillMaxWidth().padding(top = T.sm, bottom = T.lg)) {
                    Q(s.inWork.toString(), Type.big, T.textOnDark, 1)
                    Spacer(Modifier.height(2.dp))
                    Q("договоров в работе", Type.small, T.text2OnDark, 1)
                    Spacer(Modifier.height(T.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s.overdue > 0) {
                            Q("Просрочено ${s.overdue}", Type.small, T.dangerTone.fill, 1)
                            Spacer(Modifier.width(T.md))
                        }
                        if (s.rest > 0) {
                            Q("Не получено ${money(s.rest)}", Type.small, T.action.fill, 1)
                        }
                    }
                }
            }
        }
        itemsIndexedSections { index, sec ->
            Fade(index + 1) {
                PanelFolder(
                    name = sec.title,
                    count = db.count(sec),
                    hot = hot[sec] ?: 0
                ) {
                    Feel.tick()
                    OverlayState.openSection(sec)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedSections(
    content: @Composable (Int, Section) -> Unit
) {
    val all = Section.entries
    items(all.size) { i -> content(i, all[i]) }
}

/**
 * Папка — карточка со стопкой под ней. Стопка не украшение: по ней видно,
 * что внутри лежит ещё слой, и это читается без единого слова.
 */
@Composable
private fun PanelFolder(name: String, count: Int, hot: Int, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = T.md)) {
        Pressable(onClick, Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(T.rRow))
                    .background(T.panelCard)
                    .padding(horizontal = T.lg, vertical = T.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Q(name, Type.heading, T.textOnDark, 1)
                    Q(
                        if (hot > 0) "$count · горит $hot" else count.toString(),
                        Type.label,
                        if (hot > 0) T.dangerTone.fill else T.text2OnDark,
                        1
                    )
                }
                QIcon(Ic.chevronRight, Modifier, 18.dp, T.text2OnDark, 1.8f)
            }
        }
        // Края нижних карточек: сужаются и гаснут, как настоящая стопка.
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(bottomStart = T.rIcon, bottomEnd = T.rIcon))
                .background(T.panelCard.copy(alpha = 0.55f))
        )
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(bottomStart = T.rInner, bottomEnd = T.rInner))
                .background(T.panelCard.copy(alpha = 0.28f))
        )
    }
}

/** Что в разделе горит: просроченные договоры и объекты с ними. */
private fun hotCounts(db: Db): Map<Section, Int> {
    val overdue = db.liveContracts.filter { shownStatus(it) == Status.OVERDUE }
    return mapOf(
        Section.CONTRACTS to overdue.size,
        Section.SITES to overdue.mapNotNull { it.siteId }.filter { it.isNotBlank() }.distinct().size,
        Section.CUSTOMERS to 0,
        Section.STAFF to 0
    )
}

/** Ступень входа: блоки появляются друг за другом, а не все разом. */
@Composable
private fun Fade(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val a by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(T.MS_STATE, delayMillis = index * T.MS_STEP, easing = T.curve),
        label = "step"
    )
    Box(Modifier.alpha(a)) { content() }
}

// --- слой 1: записи раздела ----------------------------------------------

@Composable
private fun ItemsLayer(db: Db, section: Section, group: String?, host: OverlayHost) {
    val rows = remember(db, section, group) { rowsOf(db, section, group) }

    Column(Modifier.fillMaxWidth()) {
        Tools(db, section, group, host)
        if (rows.isEmpty()) {
            Empty()
        } else {
            LazyColumn(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = T.lg, end = T.lg, bottom = T.lg)
            ) {
                items(rows, key = { it.id }) { row ->
                    EntryLine(row) {
                        Feel.tick()
                        OverlayState.openCard(CardRef(section, row.id))
                    }
                }
            }
        }
    }
}

/** Сколько по договору незакрытых задач. Значок, а не строка: место дорого. */
@Composable
private fun TaskBadge(count: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(T.panelRaised)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QIcon(Ic.task, Modifier, 10.dp, T.text2OnDark, 2.2f)
        Spacer(Modifier.width(3.dp))
        Q(count.toString(), Type.label, T.text2OnDark, 1)
    }
}

/**
 * Поиск и фильтры одной строкой.
 *
 * Поле ввода здесь невозможно: окно службы не берёт фокус, иначе перехватит
 * весь экран. Поэтому поиск — кнопка, открывающая обычный экран с
 * клавиатурой, а на месте остаются фильтры.
 */
@Composable
private fun Tools(db: Db, section: Section, current: String?, host: OverlayHost) {
    val packs = remember(db, section) { packsOf(db, section) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = T.md)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = T.lg),
        horizontalArrangement = Arrangement.spacedBy(T.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pressable({ host.openSearch() }) {
            Row(
                Modifier
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(T.rControl))
                    .background(T.panelCard)
                    .padding(horizontal = T.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QIcon(Ic.search, Modifier, 14.dp, T.text2OnDark, 2f)
                Spacer(Modifier.width(T.xs))
                Q("Найти", Type.small, T.text2OnDark, 1)
            }
        }

        if (packs.size >= 2) {
            Chip("Все", null, current == null) { OverlayState.pickGroup(null) }
            packs.forEach { pack ->
                Chip(pack.name, pack.count, current == pack.name) {
                    OverlayState.pickGroup(if (current == pack.name) null else pack.name)
                }
            }
        }
    }
}

/** Фильтр. Выбранный — акцентом, остальные молчат: акцент на экране один. */
@Composable
private fun Chip(label: String, count: Int?, on: Boolean, onClick: () -> Unit) {
    Pressable(onClick) {
        Row(
            Modifier
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(T.rControl))
                .background(if (on) T.action.fill else T.panelCard)
                .padding(horizontal = T.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Q(label, Type.small, if (on) T.textOnDark else T.text2OnDark, 1)
            if (count != null) {
                Spacer(Modifier.width(T.xs))
                Q(
                    count.toString(),
                    Type.label,
                    if (on) T.textOnDark.copy(alpha = 0.7f) else T.text2OnDark,
                    1
                )
            }
        }
    }
}

// --- строка списка --------------------------------------------------------

private data class Entry(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val tone: T.Tone,
    val value: String,
    val warn: Boolean,
    val badge: Int = 0
)

/**
 * Запись — карточка, а не строка таблицы: имя, одна подпись, одна цифра.
 * Стадию называет слово; цвет добавляется только когда горит.
 */
@Composable
private fun EntryLine(e: Entry, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = T.sm)
                .clip(RoundedCornerShape(T.rRow))
                .background(T.panelCard)
                .padding(horizontal = T.lg, vertical = T.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                QIcon(e.icon, Modifier, 18.dp, T.text2OnDark, 1.8f)
            }
            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Q(e.title, Type.body, T.textOnDark, 1, Modifier.weight(1f, fill = false))
                    if (e.badge > 0) {
                        Spacer(Modifier.width(T.sm))
                        TaskBadge(e.badge)
                    }
                }
                val under = listOf(e.subtitle, e.status).filter { it.isNotBlank() }.joinToString(" · ")
                if (under.isNotBlank()) {
                    Q(under, Type.label, if (e.warn) T.dangerTone.fill else T.text2OnDark, 1)
                }
            }
            if (e.value.isNotBlank()) {
                Spacer(Modifier.width(T.sm))
                Q(e.value, Type.amount, T.textOnDark, 1)
            }
        }
    }
}

@Composable
private fun Empty() {
    Column(
        Modifier.fillMaxWidth().padding(T.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Q("Здесь пока пусто", Type.body, T.textOnDark)
        Spacer(Modifier.height(T.xs))
        Q("Заведи первую запись — она появится тут", Type.small, T.text2OnDark)
    }
}

// --- деталь ---------------------------------------------------------------

@Composable
private fun DetailLayer(db: Db, host: OverlayHost, ref: CardRef) {
    Column(Modifier.fillMaxWidth()) {
        when (ref.section) {
            Section.SITES -> db.site(ref.id)?.let { SiteBody(it, db, host) } ?: Gone()
            Section.CONTRACTS -> db.contract(ref.id)?.let { ContractBody(it, db, host) } ?: Gone()
            Section.CUSTOMERS -> db.customer(ref.id)?.let { PartyBody(it, db, host) } ?: Gone()
            Section.STAFF -> db.employee(ref.id)?.let { StaffBody(it, db, host) } ?: Gone()
        }
    }
}

@Composable
private fun Gone() {
    Column(
        Modifier.fillMaxWidth().padding(T.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Q("Запись не найдена", Type.body, T.textOnDark)
        Spacer(Modifier.height(T.xs))
        Q("Возможно, её удалили в таблице", Type.small, T.text2OnDark)
    }
}

// --- данные ---------------------------------------------------------------

private fun rowsOf(db: Db, section: Section, group: String?): List<Entry> {
    val all = when (section) {
        Section.SITES -> db.liveSites.map { s ->
            val stage = db.stageOfSite(s.id)
            val active = db.activeContractsOfSite(s.id).size
            Entry(
                id = s.id,
                icon = buildingIcon(s.buildingType),
                title = s.name.ifBlank { "Без названия" },
                subtitle = db.customer(s.customerId)?.name.orEmpty(),
                status = stage?.short.orEmpty(),
                tone = stage?.tone() ?: T.muted,
                value = if (active > 0) active.toString() else "",
                warn = false
            )
        }

        Section.CONTRACTS -> db.liveContracts.map { c ->
            val st = shownStatus(c)
            Entry(
                id = c.id,
                icon = Ic.contracts,
                title = c.workKind.ifBlank { "Без вида работ" },
                subtitle = db.site(c.siteId)?.name.orEmpty(),
                status = st.stage.short,
                tone = st.tone(),
                value = if (c.amount != 0L) money(c.amount) else "",
                warn = st == Status.OVERDUE,
                badge = c.openTasks
            )
        }

        Section.CUSTOMERS -> db.liveCustomers.map { p ->
            Entry(
                id = p.id,
                icon = Ic.customers,
                title = p.name.ifBlank { "Без названия" },
                subtitle = if (p.inn.isNotBlank()) "ИНН ${p.inn}" else "",
                status = "",
                tone = T.muted,
                value = db.sitesOfCustomer(p.id).size.let { if (it > 0) it.toString() else "" },
                warn = false
            )
        }

        Section.STAFF -> db.liveEmployees.map { e ->
            Entry(
                id = e.id,
                icon = departmentIcon(e.department),
                title = e.name.ifBlank { "Без имени" },
                subtitle = e.position,
                status = "",
                tone = T.muted,
                value = db.liveContracts.count { it.responsible.equals(e.name, true) }
                    .let { if (it > 0) it.toString() else "" },
                warn = false
            )
        }
    }

    val byGroup = if (group == null) all else all.filter { groupOf(db, section, it.id) == group }
    return byGroup.sortedWith(compareByDescending<Entry> { it.warn }.thenBy { it.title.lowercase() })
}

private fun groupOf(db: Db, section: Section, id: String): String? = when (section) {
    Section.SITES -> db.site(id)?.let { s ->
        db.customer(s.customerId)?.name ?: "Без заказчика"
    }

    Section.CONTRACTS -> db.contract(id)?.let { c ->
        if (c.archived) "Архив" else shownStatus(c).stage.label
    }

    Section.STAFF -> db.employee(id)?.department?.ifBlank { "Без отдела" }
    Section.CUSTOMERS -> null
}

private data class Pack(val name: String, val count: Int)

private fun packsOf(db: Db, section: Section): List<Pack> {
    val ids = when (section) {
        Section.SITES -> db.liveSites.map { it.id }
        Section.CONTRACTS -> db.liveContracts.map { it.id }
        Section.CUSTOMERS -> emptyList()
        Section.STAFF -> db.liveEmployees.map { it.id }
    }
    val counts = LinkedHashMap<String, Int>()
    ids.forEach { id ->
        val g = groupOf(db, section, id) ?: return@forEach
        counts[g] = (counts[g] ?: 0) + 1
    }
    if (counts.isEmpty()) return emptyList()

    val order = when (section) {
        Section.CONTRACTS -> Stage.entries.map { it.label } + listOf("Архив")
        else -> counts.keys.sorted()
    }
    return order.filter { counts.containsKey(it) }.map { name ->
        Pack(name = name, count = counts[name] ?: 0)
    }
}
