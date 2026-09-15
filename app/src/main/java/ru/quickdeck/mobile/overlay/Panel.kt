package ru.quickdeck.mobile.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.Contract
import ru.quickdeck.mobile.data.Db
import ru.quickdeck.mobile.data.Party
import ru.quickdeck.mobile.data.Section
import ru.quickdeck.mobile.data.Site
import ru.quickdeck.mobile.data.Status
import ru.quickdeck.mobile.data.Store
import ru.quickdeck.mobile.data.dateShort
import ru.quickdeck.mobile.data.money
import ru.quickdeck.mobile.data.overdueText
import ru.quickdeck.mobile.ui.Pressable

/**
 * Содержимое большого окна. Всё, что здесь есть, можно только смотреть и
 * тыкать — ни одного поля ввода. Ввод живёт в обычной Activity, потому что
 * окну службы для клавиатуры пришлось бы стать фокусируемым, а фокусируемый
 * оверлей перехватывает весь экран и его нечем закрыть.
 */
@Composable
fun PanelRoot(host: OverlayHost) {
    val db by Store.db.collectAsState()
    val mode = OverlayState.mode

    if (mode == PanelMode.HIDDEN) return

    val dim by animateFloatAsState(
        targetValue = if (mode == PanelMode.WHEEL) 0.55f else 1f,
        animationSpec = tween(T.MS_STATE, easing = T.curve),
        label = "dim"
    )

    Box(Modifier.fillMaxSize()) {

        // Затемнение. В режиме колеса окно вообще не принимает касания,
        // поэтому здесь клик вешается только когда он может сработать.
        val scrim = Modifier
            .fillMaxSize()
            .background(T.panelScrim.copy(alpha = T.panelScrim.alpha * dim))
        Box(
            if (mode == PanelMode.WHEEL) scrim
            else scrim.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { OverlayState.close() }
        )

        when (mode) {
            PanelMode.WHEEL -> WheelLayer(db)
            PanelMode.BROWSE -> SheetLayer { BrowseSheet(db, host) }
            PanelMode.CARD -> SheetLayer { CardSheet(db, host) }
            PanelMode.HIDDEN -> Unit
        }
    }
}

// --- колесо --------------------------------------------------------------

@Composable
private fun WheelLayer(db: Db) {
    val items = remember(db) {
        Section.entries.map { s ->
            val count = db.count(s)
            WheelItem(
                title = s.title,
                subtitle = if (count == 0) "пусто" else count.toString(),
                icon = sectionIcon(s),
                addLabel = "Добавить · ${s.one.lowercase()}"
            )
        }
    }

    // Стопка уже поставлена службой так, чтобы целиком влезть в экран,
    // где бы ни висел пузырь. Здесь её только рисуем.
    Wheel(
        items = items,
        virtual = OverlayState.virtual,
        mode = OverlayState.wheelMode,
        createArmed = OverlayState.createArmed,
        anchorTop = OverlayState.anchorTop,
        originX = OverlayState.originX,
        fromRight = OverlayState.fromRight
    )

    Hint(
        when (OverlayState.wheelMode) {
            WheelMode.CANCEL -> "Отпустишь здесь — ничего не произойдёт"
            WheelMode.BROWSE -> "Отпусти — откроется список. Дальше — добавить"
            WheelMode.CREATE -> "Отпусти — новая запись"
        }
    )
}

@Composable
private fun Hint(text: String) {
    Box(
        Modifier.fillMaxSize().padding(bottom = 48.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = T.lg, vertical = T.sm)
        ) {
            Q(text, Type.caption, Color.White.copy(alpha = 0.78f), 1)
        }
    }
}

private fun sectionIcon(s: Section): String = when (s) {
    Section.SITES -> Ic.sites
    Section.CONTRACTS -> Ic.contracts
    Section.CUSTOMERS -> Ic.customers
    Section.CONTRACTORS -> Ic.contractors
}

// --- лист снизу ----------------------------------------------------------

@Composable
private fun SheetLayer(content: @Composable ColumnScope.() -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.74f).dp

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = appear,
            enter = slideInVertically(tween(T.MS_SCREEN, easing = T.curve)) { it } +
                fadeIn(tween(T.MS_STATE, easing = T.curve)),
            exit = slideOutVertically(tween(T.MS_EXIT, easing = T.curve)) { it } +
                fadeOut(tween(T.MS_EXIT, easing = T.curve))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxH)
                    .clip(RoundedCornerShape(topStart = T.rSheet, topEnd = T.rSheet))
                    .background(T.surfaceDark)
                    .border(
                        1.dp, T.panelEdge,
                        RoundedCornerShape(topStart = T.rSheet, topEnd = T.rSheet)
                    )
                    .padding(bottom = 28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* клики по листу не должны закрывать панель */ },
                content = content
            )
        }
    }
}

@Composable
private fun Grip() {
    Box(Modifier.fillMaxWidth().padding(top = T.md), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.18f))
        )
    }
}

// --- список раздела ------------------------------------------------------

@Composable
private fun ColumnScope.BrowseSheet(db: Db, host: OverlayHost) {
    val section = OverlayState.section
    Grip()

    Row(
        Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Q(section.title, Type.title, T.textOnDark, 1)
            Q("${db.count(section)} в реестре", Type.caption, T.text2OnDark, 1)
        }
        RoundAction(Ic.search, "Найти") { host.openSearch() }
        Spacer(Modifier.width(T.xs))
        RoundAction(Ic.plus, "Добавить", accent = true) { host.openForm(section, null) }
        Spacer(Modifier.width(T.xs))
        RoundAction(Ic.close, "Закрыть") { OverlayState.close() }
    }

    Spacer(Modifier.height(T.md))
    SectionTabs(db, section)
    Spacer(Modifier.height(T.md))

    val empty = db.count(section) == 0
    if (empty) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = T.lg, vertical = T.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QIcon(Ic.layers, size = 40.dp, tint = T.text2OnDark, stroke = 1.5f)
            Spacer(Modifier.height(T.md))
            Q("Здесь пока пусто", Type.heading, T.textOnDark)
            Spacer(Modifier.height(T.xs))
            Q("Заведи первую запись — она появится тут", Type.small, T.text2OnDark)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = T.lg, end = T.lg, bottom = T.lg),
        verticalArrangement = Arrangement.spacedBy(T.sm)
    ) {
        when (section) {
            Section.SITES -> items(db.liveSites, key = { it.id }) { s ->
                PanelRow(
                    title = s.name.ifBlank { "Без названия" },
                    subtitle = listOfNotNull(
                        db.customer(s.customerId)?.name,
                        s.deadline.takeIf { it.isNotBlank() }?.let { "до ${dateShort(it)}" }
                    ).joinToString(" · "),
                    icon = Ic.sites,
                    status = effectiveStatus(s.status, s.deadline),
                    trailing = if (s.progress > 0) "${s.progress} %" else null
                ) { OverlayState.openCard(CardRef(Section.SITES, s.id)) }
            }

            Section.CONTRACTS -> items(db.liveContracts, key = { it.id }) { c ->
                PanelRow(
                    title = c.number.ifBlank { "Без номера" },
                    subtitle = listOfNotNull(
                        db.site(c.siteId)?.name,
                        c.end.takeIf { it.isNotBlank() }?.let { "до ${dateShort(it)}" }
                    ).joinToString(" · "),
                    icon = Ic.contracts,
                    status = effectiveStatus(c.status, c.end),
                    trailing = if (c.amount != 0L) money(c.amount) else null
                ) { OverlayState.openCard(CardRef(Section.CONTRACTS, c.id)) }
            }

            Section.CUSTOMERS -> items(db.liveCustomers, key = { it.id }) { p ->
                PanelRow(
                    title = p.name.ifBlank { "Без названия" },
                    subtitle = listOfNotNull(
                        p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" },
                        p.phone.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    icon = Ic.customers,
                    status = null,
                    trailing = db.sitesOfCustomer(p.id).size.takeIf { it > 0 }?.toString()
                ) { OverlayState.openCard(CardRef(Section.CUSTOMERS, p.id)) }
            }

            Section.CONTRACTORS -> items(db.liveContractors, key = { it.id }) { p ->
                PanelRow(
                    title = p.name.ifBlank { "Без названия" },
                    subtitle = listOfNotNull(
                        p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" },
                        p.phone.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    icon = Ic.contractors,
                    status = null,
                    trailing = null
                ) { OverlayState.openCard(CardRef(Section.CONTRACTORS, p.id)) }
            }
        }
    }
}

@Composable
private fun SectionTabs(db: Db, current: Section) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = T.lg),
        horizontalArrangement = Arrangement.spacedBy(T.sm)
    ) {
        Section.entries.forEach { s ->
            val selected = s == current
            Pressable({ OverlayState.openBrowse(s) }) {
                Row(
                    Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) T.accent.fill else Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = T.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Q(s.title, Type.caption, if (selected) Color.White else T.text2OnDark, 1)
                    Spacer(Modifier.width(T.xs))
                    Q(
                        db.count(s).toString(),
                        Type.caption,
                        if (selected) Color.White.copy(alpha = 0.7f) else T.text2OnDark.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelRow(
    title: String,
    subtitle: String,
    icon: String,
    status: Status?,
    trailing: String?,
    onClick: () -> Unit
) {
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(T.rCard))
                .background(T.panelCard)
                .border(1.dp, T.panelEdge, RoundedCornerShape(T.rCard))
                .padding(T.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(T.rIcon))
                    .background(Color.White.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) { QIcon(icon, size = 19.dp, tint = T.text2OnDark) }

            Spacer(Modifier.width(T.md))
            Column(Modifier.weight(1f)) {
                Q(title, Type.heading, T.textOnDark, 1)
                if (subtitle.isNotBlank()) {
                    Q(subtitle, Type.caption, T.text2OnDark, 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                status?.let { DarkStatusChip(it) }
                trailing?.let {
                    if (status != null) Spacer(Modifier.height(T.xs))
                    Q(it, Type.smallNum, T.text2OnDark, 1)
                }
            }
        }
    }
}

// --- карточка ------------------------------------------------------------

@Composable
private fun ColumnScope.CardSheet(db: Db, host: OverlayHost) {
    val ref = OverlayState.card ?: run { Missing(); return }
    Grip()

    Row(
        Modifier.fillMaxWidth().padding(start = T.sm, end = T.sm, top = T.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Pressable({ OverlayState.backFromCard() }) {
            Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                QIcon(Ic.chevronLeft, size = 22.dp, tint = T.textOnDark)
            }
        }
        Q(ref.section.one, Type.caption, T.text2OnDark, 1, Modifier.weight(1f))
        RoundAction(Ic.close, "Закрыть") { OverlayState.close() }
    }

    when (ref.section) {
        Section.SITES -> db.site(ref.id)?.let { SiteBody(it, db, host) } ?: Missing()
        Section.CONTRACTS -> db.contract(ref.id)?.let { ContractBody(it, db, host) } ?: Missing()
        Section.CUSTOMERS -> db.customer(ref.id)?.let { PartyBody(it, db, true, host) } ?: Missing()
        Section.CONTRACTORS -> db.contractor(ref.id)?.let { PartyBody(it, db, false, host) } ?: Missing()
    }
}

@Composable
private fun Missing() {
    Column(Modifier.fillMaxWidth().padding(T.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Q("Запись не найдена", Type.heading, T.textOnDark)
        Spacer(Modifier.height(T.xs))
        Q("Возможно, её удалили в таблице", Type.small, T.text2OnDark)
    }
}

@Composable
private fun ColumnScope.SiteBody(site: Site, db: Db, host: OverlayHost) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(scroll)
            .padding(horizontal = T.lg)
    ) {
        Q(site.name.ifBlank { "Без названия" }, Type.title, T.textOnDark, 2)
        Spacer(Modifier.height(T.sm))

        val overdue = overdueText(site.deadline)
        if (overdue != null) {
            Q(overdue, Type.small, T.danger.fill, 1)
            Spacer(Modifier.height(T.sm))
        }

        Facts(
            listOf(
                "Заказчик" to (db.customer(site.customerId)?.name ?: ""),
                "Адрес" to site.address,
                "Срок" to (site.deadline.takeIf { it.isNotBlank() }?.let { dateShort(it) } ?: ""),
                "Договоров" to db.contractsOfSite(site.id).size.toString()
            )
        )

        Spacer(Modifier.height(T.lg))
        QuickStatus(Status.forSite, effectiveStatus(site.status, site.deadline)) {
            Store.setSiteStatus(site.id, it)
            host.buzz(10)
            host.syncQuietly()
        }

        Spacer(Modifier.height(T.lg))
        Q("Готовность", Type.caption, T.text2OnDark)
        Spacer(Modifier.height(T.sm))
        ProgressStepper(site.progress) {
            Store.setSiteProgress(site.id, it)
            host.buzz(8)
            host.syncQuietly()
        }

        Spacer(Modifier.height(T.xl))
    }
    BottomActions(
        primary = Pair("Изменить объект") { host.openForm(Section.SITES, site.id) },
        secondary = Pair("Добавить договор") { host.openForm(Section.CONTRACTS, null) }
    )
}

@Composable
private fun ColumnScope.ContractBody(c: Contract, db: Db, host: OverlayHost) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(scroll)
            .padding(horizontal = T.lg)
    ) {
        Q("Договор ${c.number}".trim(), Type.title, T.textOnDark, 2)
        Spacer(Modifier.height(T.xs))
        if (c.amount != 0L) {
            Q(money(c.amount), Type.display, T.textOnDark, 1)
            Spacer(Modifier.height(T.sm))
        }

        val overdue = overdueText(c.end)
        if (overdue != null) {
            Q(overdue, Type.small, T.danger.fill, 1)
            Spacer(Modifier.height(T.sm))
        }

        Facts(
            listOf(
                "Объект" to (db.site(c.siteId)?.name ?: ""),
                "Заказчик" to (db.customer(c.customerId)?.name ?: ""),
                "Исполнитель" to (db.contractor(c.contractorId)?.name ?: ""),
                "Срок" to (c.end.takeIf { it.isNotBlank() }?.let { dateShort(it) } ?: "")
            )
        )

        Spacer(Modifier.height(T.lg))
        QuickStatus(Status.forContract, effectiveStatus(c.status, c.end)) {
            Store.setContractStatus(c.id, it)
            host.buzz(10)
            host.syncQuietly()
        }
        Spacer(Modifier.height(T.xl))
    }
    BottomActions(primary = Pair("Изменить договор") { host.openForm(Section.CONTRACTS, c.id) })
}

@Composable
private fun ColumnScope.PartyBody(p: Party, db: Db, customer: Boolean, host: OverlayHost) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(scroll)
            .padding(horizontal = T.lg)
    ) {
        Q(p.name.ifBlank { "Без названия" }, Type.title, T.textOnDark, 2)
        Spacer(Modifier.height(T.md))
        Facts(
            listOf(
                "ИНН" to p.inn,
                "Контакт" to p.contact,
                "Телефон" to p.phone,
                if (customer) "Объектов" to db.sitesOfCustomer(p.id).size.toString()
                else "Договоров" to db.liveContracts.count { it.contractorId == p.id }.toString()
            )
        )
        Spacer(Modifier.height(T.xl))
    }
    val section = if (customer) Section.CUSTOMERS else Section.CONTRACTORS
    BottomActions(primary = Pair("Изменить") { host.openForm(section, p.id) })
}

// --- детали --------------------------------------------------------------

@Composable
private fun Facts(pairs: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth()) {
        pairs.forEach { (key, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Q(key, Type.caption, T.text2OnDark, 1, Modifier.width(104.dp))
                Q(value.ifBlank { "—" }, Type.small, T.textOnDark, 2, Modifier.weight(1f))
            }
        }
    }
}

/** Смена статуса — один тап, без формы и без сохранения. */
@Composable
private fun QuickStatus(options: List<Status>, current: Status, onPick: (Status) -> Unit) {
    Q("Статус", Type.caption, T.text2OnDark)
    Spacer(Modifier.height(T.sm))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(T.sm)
    ) {
        options.forEach { s ->
            val selected = s == current
            Pressable({ if (!selected) onPick(s) }) {
                Box(
                    Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (selected) s.tone().fill else Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = T.md),
                    contentAlignment = Alignment.Center
                ) {
                    Q(s.label, Type.caption, if (selected) Color.White else T.text2OnDark, 1)
                }
            }
        }
    }
}

@Composable
private fun ProgressStepper(percent: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepButton("−") { onChange((percent - 10).coerceAtLeast(0)) }
        Spacer(Modifier.width(T.md))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Q("$percent %", Type.display, T.textOnDark, 1)
        }
        Spacer(Modifier.width(T.md))
        StepButton("+") { onChange((percent + 10).coerceAtMost(100)) }
    }
    Spacer(Modifier.height(T.sm))
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.1f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(T.accent.fill)
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Pressable(onClick) {
        Box(
            Modifier
                .size(T.touchMin)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) { Q(label, Type.title, T.textOnDark) }
    }
}

@Composable
private fun ColumnScope.BottomActions(
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>? = null
) {
    Column(Modifier.fillMaxWidth().padding(start = T.lg, end = T.lg, top = T.sm)) {
        Pressable(primary.second, Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = T.touchMin)
                    .clip(RoundedCornerShape(T.rControl))
                    .background(T.accent.fill),
                contentAlignment = Alignment.Center
            ) { Q(primary.first, Type.heading, Color.White) }
        }
        secondary?.let { (label, action) ->
            Spacer(Modifier.height(T.sm))
            Pressable(action, Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = T.touchMin)
                        .clip(RoundedCornerShape(T.rControl))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) { Q(label, Type.heading, T.textOnDark) }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: String, label: String, accent: Boolean = false, onClick: () -> Unit) {
    Pressable(onClick) {
        Box(
            Modifier
                .size(T.touchMin)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (accent) T.accent.fill else Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            QIcon(
                icon,
                size = 20.dp,
                tint = if (accent) Color.White else T.textOnDark,
                stroke = if (accent) 2f else 1.75f
            )
        }
    }
}

@Composable
private fun DarkStatusChip(status: Status) {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(status.tone().fill.copy(alpha = 0.22f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Q(status.label, Type.caption, status.tone().fill, 1)
    }
}

/** Просрочка выставляется сама: держать её руками в статусе невозможно. */
private fun effectiveStatus(status: Status, deadline: String): Status =
    if (status == Status.WORK && overdueText(deadline) != null) Status.OVERDUE else status

private fun Status.tone(): T.Tone = when (this) {
    Status.DRAFT -> T.muted
    Status.WORK -> T.accent
    Status.WAIT -> T.warning
    Status.DONE -> T.success
    Status.OVERDUE -> T.danger
    Status.ARCHIVE -> T.muted
}
