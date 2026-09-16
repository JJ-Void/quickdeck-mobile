package ru.quickdeck.mobile.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
import ru.quickdeck.mobile.data.money
import ru.quickdeck.mobile.ui.Pressable
import ru.quickdeck.mobile.ui.shownStatus
import ru.quickdeck.mobile.ui.tone

/**
 * Рабочий стол — то, чем реестр разбирают, а не то, чем его показывают.
 *
 * Прошлая версия листала записи по одной большой карточке вбок. Выглядело
 * это эффектно, а работать мешало: на экран помещалась одна запись вместо
 * десяти, горизонтальный свайп спорил с системным жестом «назад», и чтобы
 * добраться до нужного договора, приходилось пройти три уровня — раздел,
 * пачку, запись.
 *
 * Здесь всё иначе и проще. Раздел переключается сегментами вверху. Пачки
 * стали фильтрами: не уровень навигации, а один тап, который снимается
 * таким же тапом. Записи идут плотным списком: строка — имя, подпись,
 * статус словом и цветом, главная цифра справа. Открытая запись
 * разворачивается на месте, закрывается кнопкой и системным «назад».
 *
 * Ни одного жеста поверх системных: только тапы и вертикальная прокрутка.
 */
@Composable
fun ColumnScope.WorkbenchLayer(db: Db, host: OverlayHost) {
    val card = OverlayState.card
    if (card != null) {
        DetailLayer(db, host, card)
        return
    }

    val section = OverlayState.section
    val group = OverlayState.group

    Header(host)
    Sections(db, section)
    Tools(db, section, group, host)

    val rows = remember(db, section, group) { rowsOf(db, section, group) }

    if (rows.isEmpty()) {
        Empty()
        return
    }

    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(bottom = T.lg)
    ) {
        items(rows, key = { it.id }) { row ->
            EntryLine(row) { OverlayState.openCard(CardRef(section, row.id)) }
        }
    }
}

// --- шапка ----------------------------------------------------------------

@Composable
private fun Header(host: OverlayHost) {
    Row(
        Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.sm, bottom = T.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Q("Реестр", Type.heading, T.textOnDark, 1)
            val last = Store.lastSync
            Q(
                if (last.isBlank()) "Обмен не настроен" else "Обновлено $last",
                Type.caption,
                T.text2OnDark,
                1
            )
        }
        RoundAction(Ic.settings, "Настройки") { host.openSettings() }
        Spacer(Modifier.width(T.xs))
        RoundAction(Ic.plus, "Добавить", accent = true) {
            host.openForm(OverlayState.section, null)
        }
        Spacer(Modifier.width(T.xs))
        RoundAction(Ic.close, "Закрыть") { OverlayState.close() }
    }
}

/** Разделы — сегменты, а не отдельный экран: переключение в один тап. */
@Composable
private fun Sections(db: Db, current: Section) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = T.lg, vertical = T.xs)
            .clip(RoundedCornerShape(T.rControl))
            .background(T.panelCard)
            .padding(3.dp)
    ) {
        Section.entries.forEach { s ->
            val on = s == current
            val fill by animateFloatAsState(
                targetValue = if (on) 1f else 0f,
                animationSpec = tween(T.MS_PRESS, easing = T.curve),
                label = "seg"
            )
            Box(Modifier.weight(1f)) {
                Pressable({ OverlayState.pickSection(s) }, Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(T.rIcon))
                            .background(T.panelRaised.copy(alpha = fill))
                            .padding(vertical = T.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Q(
                            db.count(s).toString(),
                            Type.smallNum,
                            if (on) T.textOnDark else T.text2OnDark,
                            1
                        )
                        Q(
                            s.title,
                            Type.caption,
                            if (on) T.accent.fill else T.text2OnDark,
                            1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Поиск и фильтры одной строкой.
 *
 * Поле ввода здесь невозможно: окно службы не берёт фокус, иначе перехватит
 * весь экран. Поэтому поиск — кнопка, открывающая обычный экран с
 * клавиатурой, а на месте остаются фильтры.
 *
 * Фильтры стоят вместо прежнего уровня «пачек». Стадии договоров, отделы
 * сотрудников, заказчики объектов — это срезы одного списка, а не шаг
 * вглубь: как фильтр они снимаются одним тапом и не прячут остальное, как
 * уровень — заставляли возвращаться назад ради соседней стадии.
 */
@Composable
private fun Tools(db: Db, section: Section, current: String?, host: OverlayHost) {
    val packs = remember(db, section) { packsOf(db, section) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = T.xs)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = T.lg),
        horizontalArrangement = Arrangement.spacedBy(T.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pressable({ host.openSearch() }) {
            Row(
                Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(T.rIcon))
                    .background(T.panelCard)
                    .border(1.dp, T.panelEdge, RoundedCornerShape(T.rIcon))
                    .padding(horizontal = T.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QIcon(Ic.search, Modifier, 14.dp, T.text2OnDark, 2f)
                Spacer(Modifier.width(T.xs))
                Q("Найти", Type.caption, T.text2OnDark, 1)
            }
        }

        if (packs.size >= 2) {
            Chip("Все", null, current == null, T.muted) { OverlayState.pickGroup(null) }
            packs.forEach { pack ->
                Chip(pack.name, pack.count, current == pack.name, pack.tone) {
                    OverlayState.pickGroup(if (current == pack.name) null else pack.name)
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, count: Int?, on: Boolean, tone: T.Tone, onClick: () -> Unit) {
    Pressable(onClick) {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .clip(RoundedCornerShape(T.rIcon))
                .background(if (on) tone.fill.copy(alpha = 0.18f) else T.panelCard)
                .border(
                    1.dp,
                    if (on) tone.fill.copy(alpha = 0.6f) else T.panelEdge,
                    RoundedCornerShape(T.rIcon)
                )
                .padding(horizontal = T.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Q(label, Type.caption, if (on) T.textOnDark else T.text2OnDark, 1)
            if (count != null) {
                Spacer(Modifier.width(T.xs))
                Q(count.toString(), Type.caption, if (on) tone.fill else T.text2OnDark, 1)
            }
        }
    }
}

// --- строка списка --------------------------------------------------------

/**
 * Что показывает строка. Одинаково для всех разделов, чтобы глаз не
 * переучивался при переключении сегмента.
 */
private data class Entry(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val tone: T.Tone,
    val value: String,
    val warn: Boolean
)

/**
 * Строка реестра.
 *
 * Слева знак и имя, справа главная цифра и статус словом. Слово
 * обязательно: по одному цвету статус не читается ни на солнце, ни при
 * дальтонизме. Высота фиксированная, разделитель тонкий — список
 * сканируется сверху вниз, а не разглядывается по одной карточке.
 */
@Composable
private fun EntryLine(e: Entry, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = T.lg, vertical = T.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Полоска состояния: цвет дублирует слово справа, а не заменяет.
                Box(
                    Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(e.tone.fill.copy(alpha = if (e.warn) 1f else 0.55f))
                )
                Spacer(Modifier.width(T.md))
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    QIcon(e.icon, Modifier, 18.dp, T.text2OnDark, 1.6f)
                }
                Spacer(Modifier.width(T.md))
                Column(Modifier.weight(1f)) {
                    Q(e.title, Type.small, T.textOnDark, 1)
                    if (e.subtitle.isNotBlank()) {
                        Q(e.subtitle, Type.caption, T.text2OnDark, 1)
                    }
                }
                Spacer(Modifier.width(T.sm))
                Column(horizontalAlignment = Alignment.End) {
                    if (e.value.isNotBlank()) {
                        Q(e.value, Type.smallNum, T.textOnDark, 1)
                    }
                    if (e.status.isNotBlank()) {
                        Q(
                            e.status,
                            Type.caption,
                            if (e.warn) T.danger.fill else T.text2OnDark,
                            1
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(T.panelEdge.copy(alpha = 0.4f)))
        }
    }
}

@Composable
private fun ColumnScope.Empty() {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(T.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Q("Здесь пока пусто", Type.small, T.textOnDark)
        Spacer(Modifier.height(T.xs))
        Q("Заведи первую запись — она появится тут", Type.caption, T.text2OnDark)
    }
}

// --- деталь ---------------------------------------------------------------

/**
 * Открытая запись занимает панель целиком: детали, действия, связи.
 * Возврат — кнопка слева и системное «назад», без жестов поверх содержимого.
 */
@Composable
private fun ColumnScope.DetailLayer(db: Db, host: OverlayHost, ref: CardRef) {
    Row(
        Modifier.fillMaxWidth().padding(start = T.sm, end = T.sm, top = T.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundAction(Ic.chevronLeft, "Назад") { OverlayState.backFromCard() }
        Spacer(Modifier.width(T.xs))
        Q(ref.section.one, Type.caption, T.text2OnDark, 1, Modifier.weight(1f))
        RoundAction(Ic.close, "Закрыть") { OverlayState.close() }
    }

    when (ref.section) {
        Section.SITES -> db.site(ref.id)?.let { SiteBody(it, db, host) } ?: Gone()
        Section.CONTRACTS -> db.contract(ref.id)?.let { ContractBody(it, db, host) } ?: Gone()
        Section.CUSTOMERS -> db.customer(ref.id)?.let { PartyBody(it, db, host) } ?: Gone()
        Section.STAFF -> db.employee(ref.id)?.let { StaffBody(it, db, host) } ?: Gone()
    }
}

@Composable
private fun Gone() {
    Column(
        Modifier.fillMaxWidth().padding(T.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Q("Запись не найдена", Type.small, T.textOnDark)
        Spacer(Modifier.height(T.xs))
        Q("Возможно, её удалили в таблице", Type.caption, T.text2OnDark)
    }
}

// --- данные ---------------------------------------------------------------

/** Срез списка: раздел и фильтр. Считается один раз на изменение. */
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
                warn = st == Status.OVERDUE
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
    // Порядок: сначала то, что горит, потом остальное по алфавиту. Список,
    // который каждый раз лежит иначе, читать невозможно.
    return byGroup.sortedWith(compareByDescending<Entry> { it.warn }.thenBy { it.title.lowercase() })
}

/** Пачка записи — тот же признак, по которому строятся фильтры. */
private fun groupOf(db: Db, section: Section, id: String): String? = when (section) {
    Section.SITES -> db.site(id)?.let { s ->
        db.customer(s.customerId)?.name ?: "Без заказчика"
    }

    Section.CONTRACTS -> db.contract(id)?.let { c ->
        val st = shownStatus(c)
        if (st.stage == Stage.PROBLEM || st == Status.PAID_FULL) "Архив" else st.stage.label
    }

    Section.STAFF -> db.employee(id)?.department?.ifBlank { "Без отдела" }
    Section.CUSTOMERS -> null
}

private data class Pack(val name: String, val count: Int, val tone: T.Tone)

/** Фильтры раздела в том порядке, в котором по ним ходят. */
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
        // Стадии идут по ходу работы, архив последним: так же, как в жизни.
        Section.CONTRACTS -> Stage.entries.filter { it != Stage.PROBLEM }.map { it.label } +
            listOf("Архив")
        else -> counts.keys.sorted()
    }
    return order.filter { counts.containsKey(it) }.map { name ->
        Pack(
            name = name,
            count = counts[name] ?: 0,
            tone = if (name == "Архив") T.muted
            else Stage.entries.firstOrNull { it.label == name }?.tone() ?: T.muted
        )
    }
}
