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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import androidx.compose.ui.platform.LocalContext
import ru.quickdeck.mobile.core.Actions
import ru.quickdeck.mobile.data.Contract
import ru.quickdeck.mobile.data.Db
import ru.quickdeck.mobile.data.Employee
import ru.quickdeck.mobile.data.Party
import ru.quickdeck.mobile.data.Section
import ru.quickdeck.mobile.data.Site
import ru.quickdeck.mobile.data.Stage
import ru.quickdeck.mobile.data.Status
import ru.quickdeck.mobile.data.Store
import ru.quickdeck.mobile.data.dateShort
import ru.quickdeck.mobile.data.money
import ru.quickdeck.mobile.data.overdueText
import ru.quickdeck.mobile.data.codeLabel
import ru.quickdeck.mobile.data.subName
import ru.quickdeck.mobile.ui.Pressable
import ru.quickdeck.mobile.ui.SendButton
import ru.quickdeck.mobile.ui.contractText
import ru.quickdeck.mobile.ui.partyText
import ru.quickdeck.mobile.ui.rememberSendState
import ru.quickdeck.mobile.ui.siteText
import ru.quickdeck.mobile.ui.shownStatus
import ru.quickdeck.mobile.ui.tone
import ru.quickdeck.mobile.ui.trimNumber

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
            .background(
                // Сверху светлее, снизу глубже: панель встаёт на фон, а не
                // лежит на ровной серой заливке.
                Brush.verticalGradient(
                    listOf(
                        T.panelScrim.copy(alpha = T.panelScrim.alpha * dim * 0.78f),
                        T.panelScrim.copy(alpha = T.panelScrim.alpha * dim)
                    )
                )
            )
        Box(
            if (mode == PanelMode.WHEEL) scrim
            else scrim.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { OverlayState.close() }
        )

        when (mode) {
            PanelMode.WHEEL -> WheelLayer(db)
            // Реестр живёт колодой по центру экрана: уровни вглубь, соседи
            // вбок. Лист снизу показывал один уровень и прятал, где ты.
            PanelMode.BROWSE, PanelMode.CARD -> DeckFrame { DeckLayer(db, host) }
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
        centerY = OverlayState.centerY,
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

/**
 * Иконка объекта по типу здания: школа выглядит школой, завод — заводом.
 * Каска на всём подряд не говорит ничего, а силуэт узнаётся мгновенно.
 */
internal fun buildingIcon(type: String): String {
    val t = type.lowercase()
    return when {
        t.contains("админ") || t.contains("офис") -> Ic.office
        t.contains("образ") || t.contains("школ") || t.contains("детс") || t.contains("учеб") -> Ic.school
        t.contains("жил") || t.contains("общеж") || t.contains("кварт") -> Ic.living
        t.contains("промышл") || t.contains("завод") || t.contains("цех") || t.contains("производ") -> Ic.factory
        t.contains("медиц") || t.contains("больн") || t.contains("поликлин") -> Ic.medical
        t.contains("торг") || t.contains("магаз") -> Ic.store
        t.contains("склад") || t.contains("ангар") -> Ic.storage
        t.contains("спорт") -> Ic.sport
        t.contains("культ") || t.contains("музе") || t.contains("театр") -> Ic.culture
        t.contains("энерг") || t.contains("подстан") || t.contains("котель") -> Ic.energy
        else -> Ic.sites
    }
}

/** Иконка отдела: у каждого своё дело, и знак у каждого свой. */
internal fun departmentIcon(dept: String): String {
    val d = dept.lowercase()
    return when {
        d.contains("пто") -> Ic.deptPto
        d.contains("сметн") -> Ic.deptEstimate
        d.contains("проект") -> Ic.deptDesign
        d.contains("обслед") -> Ic.deptSurvey
        d.contains("юр") -> Ic.deptLegal
        d.contains("снаб") -> Ic.deptSupply
        d.contains("монтаж") || d.contains("строит") || d.contains("смр") -> Ic.deptBuild
        d.contains("менеджмент") || d.contains("руковод") -> Ic.deptLead
        else -> Ic.staff
    }
}

internal fun sectionIcon(s: Section): String = when (s) {
    Section.SITES -> Ic.sites
    Section.CONTRACTS -> Ic.contracts
    Section.CUSTOMERS -> Ic.customers
    Section.STAFF -> Ic.staff
}

// --- рамка колоды ---------------------------------------------------------

/**
 * Колода занимает экран целиком и выезжает снизу вверх одним движением:
 * так видно, что она пришла от пузыря, а не подменила собой приложение.
 */
@Composable
private fun DeckFrame(content: @Composable ColumnScope.() -> Unit) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        enter = slideInVertically(tween(T.MS_SCREEN, easing = T.curve)) { it / 4 } +
            fadeIn(tween(T.MS_STATE, easing = T.curve)),
        exit = slideOutVertically(tween(T.MS_EXIT, easing = T.curve)) { it / 4 } +
            fadeOut(tween(T.MS_EXIT, easing = T.curve))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* клик мимо карточки не закрывает: есть крестик и пузырь */ },
            content = content
        )
    }
}

// --- лист снизу (оставлен для поиска и форм) -------------------------------

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
                val active = db.activeContractsOfSite(s.id).size
                PanelRow(
                    title = s.name.ifBlank { "Без названия" },
                    subtitle = listOfNotNull(
                        db.customer(s.customerId)?.name,
                        if (active > 0) "$active в работе" else null
                    ).joinToString(" · "),
                    icon = Ic.sites,
                    stage = db.stageOfSite(s.id),
                    trailing = if (s.progress > 0) "${s.progress} %" else null
                ) { OverlayState.openCard(CardRef(Section.SITES, s.id)) }
            }

            Section.CONTRACTS -> items(db.liveContracts, key = { it.id }) { c ->
                val st = shownStatus(c)
                PanelRow(
                    title = c.label(db.site(c.siteId)?.name),
                    subtitle = listOfNotNull(
                        st.label,
                        c.end.takeIf { it.isNotBlank() }?.let { "до ${dateShort(it)}" }
                    ).joinToString(" · "),
                    icon = Ic.contracts,
                    stage = st.stage,
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
                    stage = null,
                    trailing = db.sitesOfCustomer(p.id).size.takeIf { it > 0 }?.toString()
                ) { OverlayState.openCard(CardRef(Section.CUSTOMERS, p.id)) }
            }

            Section.STAFF -> items(db.liveEmployees, key = { it.id }) { e ->
                PanelRow(
                    title = e.name.ifBlank { "Без имени" },
                    subtitle = listOf(e.position, e.department).filter { it.isNotBlank() }.joinToString(" · "),
                    icon = Ic.staff,
                    stage = null,
                    trailing = null
                ) { OverlayState.openCard(CardRef(Section.STAFF, e.id)) }
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
    stage: Stage?,
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
                stage?.let { DarkStageChip(it) }
                trailing?.let {
                    if (stage != null) Spacer(Modifier.height(T.xs))
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
        Section.CUSTOMERS -> db.customer(ref.id)?.let { PartyBody(it, db, host) } ?: Missing()
        Section.STAFF -> db.employee(ref.id)?.let { StaffBody(it, db, host) } ?: Missing()
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
internal fun ColumnScope.SiteBody(site: Site, db: Db, host: OverlayHost) {
    val scroll = rememberScrollState()
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = T.lg)
    ) {
        val ctx = LocalContext.current
        val send = rememberSendState()

        // Имя выводится один раз. Полное наименование и номер показываются,
        // только если это действительно другие данные, а не копия имени.
        CardHead(
            title = site.name.ifBlank { "Без названия" },
            sub = listOf(site.codeLabel, site.subName).filter { it.isNotBlank() }.joinToString(" · ")
        )
        Spacer(Modifier.height(T.md))

        Facts(
            listOf(
                "Заказчик" to (db.customer(site.customerId)?.name ?: ""),
                "Адрес" to site.address,
                "Тип" to site.buildingType,
                "Площадь" to (if (site.area > 0) "${trimNumber(site.area)} ${site.unit}" else "")
            )
        )

        // Договоры объекта — отсюда и меняется статус: у объекта его нет.
        val contracts = db.contractsOfSite(site.id)
        if (contracts.isNotEmpty()) {
            Spacer(Modifier.height(T.lg))
            Q("Договоры", Type.caption, T.text2OnDark)
            Spacer(Modifier.height(T.sm))
            contracts.forEach { c ->
                Pressable({ OverlayState.openCard(CardRef(Section.CONTRACTS, c.id)) }, Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(T.rControl))
                            .background(T.panelCard)
                            .padding(T.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Q(c.workKind.ifBlank { c.code }, Type.small, T.textOnDark, 2)
                            Q(shownStatus(c).label, Type.caption, shownStatus(c).stage.tone().fill, 1)
                        }
                        if (c.amount != 0L) Q(money(c.amount), Type.smallNum, T.text2OnDark, 1)
                    }
                }
                Spacer(Modifier.height(T.xs))
            }
        }

        Spacer(Modifier.height(T.lg))
        Q("Готовность", Type.caption, T.text2OnDark)
        Spacer(Modifier.height(T.sm))
        ProgressStepper(site.progress) {
            Store.setSiteProgress(site.id, it)
            host.buzz(8)
            host.syncQuietly()
        }

        Spacer(Modifier.height(T.lg))
        // Карточка уходит собранной из тех же данных, что видны на экране,
        // и остаётся привязанной к объекту: текст собирает siteText(site, db).
        SendButton("Отправить карточку", send, dark = true) {
            Actions.share(ctx, siteText(site, db), "Карточка объекта")
        }

        Spacer(Modifier.height(T.xl))
    }
    BottomActions(
        primary = Pair("Изменить объект") { host.openForm(Section.SITES, site.id) },
        secondary = Pair("Добавить договор") { host.openForm(Section.CONTRACTS, null) }
    )
}

@Composable
internal fun ColumnScope.ContractBody(c: Contract, db: Db, host: OverlayHost) {
    val scroll = rememberScrollState()
    val status = shownStatus(c)
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = T.lg)
    ) {
        val ctx = LocalContext.current
        val send = rememberSendState()
        val siteName = db.site(c.siteId)?.name

        CardHead(title = c.label(siteName), sub = c.codeLabel(siteName))
        Spacer(Modifier.height(T.xs))
        if (c.amount != 0L) {
            Q(money(c.amount), Type.display, T.textOnDark, 1)
            Spacer(Modifier.height(T.sm))
        }

        overdueText(c.end)?.let {
            Q(it, Type.small, T.danger.fill, 1)
            Spacer(Modifier.height(T.sm))
        }

        // Связанные записи открываются сразу карточкой: из договора в объект
        // и в человека — один тап, без возврата на уровень списка.
        FactLink("Объект", db.site(c.siteId)?.name ?: "", c.siteId?.let { id ->
            { OverlayState.openCard(CardRef(Section.SITES, id)) }
        })
        val boss = db.liveEmployees.firstOrNull { it.name.equals(c.responsible, true) }
        FactLink("Ответственный", c.responsible, boss?.let { e ->
            { OverlayState.openCard(CardRef(Section.STAFF, e.id)) }
        })
        c.coExecutors.forEachIndexed { index, name ->
            val mate = db.liveEmployees.firstOrNull { it.name.equals(name, true) }
            FactLink(
                if (index == 0) "Соисполнители" else "",
                name,
                mate?.let { e -> { OverlayState.openCard(CardRef(Section.STAFF, e.id)) } }
            )
        }
        Facts(
            listOf(
                "Отдел" to db.refs.departmentOf(c.workKind),
                "Срок" to (c.end.takeIf { it.isNotBlank() }?.let { dateShort(it) } ?: "")
            )
        )

        Spacer(Modifier.height(T.lg))
        QuickStatus(status) {
            Store.setContractStatus(c.id, it)
            host.buzz(10)
            host.syncQuietly()
        }

        val pays = c.payments.filterNot { it.empty }
        if (pays.isNotEmpty()) {
            Spacer(Modifier.height(T.lg))
            Q("Оплаты", Type.caption, T.text2OnDark)
            Spacer(Modifier.height(T.sm))
            pays.forEachIndexed { index, p ->
                Pressable({
                    Store.setPaymentPaid(c.id, index, !p.paid)
                    host.buzz(8)
                    host.syncQuietly()
                }, Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(T.rControl))
                            .background(if (p.paid) T.success.fill.copy(alpha = 0.18f) else T.panelCard)
                            .padding(T.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QIcon(
                            if (p.paid) Ic.check else Ic.wallet,
                            size = 18.dp,
                            tint = if (p.paid) T.success.fill else T.text2OnDark,
                            stroke = if (p.paid) 2f else 1.75f
                        )
                        Spacer(Modifier.width(T.sm))
                        Q(p.condition, Type.small, T.textOnDark, 2, Modifier.weight(1f))
                        Q("${trimNumber(p.share * 100)} %", Type.smallNum, T.text2OnDark, 1)
                    }
                }
                Spacer(Modifier.height(T.xs))
            }
        }

        Spacer(Modifier.height(T.lg))
        SendButton("Отправить карточку", send, dark = true) {
            Actions.share(ctx, contractText(c, db), "Карточка договора")
        }

        Spacer(Modifier.height(T.xl))
    }
    BottomActions(primary = Pair("Изменить договор") { host.openForm(Section.CONTRACTS, c.id) })
}

@Composable
internal fun ColumnScope.PartyBody(p: Party, db: Db, host: OverlayHost) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = T.lg)
    ) {
        val send = rememberSendState()

        CardHead(title = p.name.ifBlank { "Без названия" }, sub = p.subName)
        Spacer(Modifier.height(T.md))

        if (p.phone.isNotBlank()) {
            DarkPill(Ic.phone, "Позвонить", T.success) { Actions.dial(ctx, p.phone) }
            Spacer(Modifier.height(T.md))
        }

        Facts(
            listOf(
                "ИНН" to p.inn,
                "Руководитель" to p.director,
                "Телефон" to p.phone,
                "Объектов" to db.sitesOfCustomer(p.id).size.toString()
            )
        )

        val sites = db.sitesOfCustomer(p.id)
        if (sites.isNotEmpty()) {
            Spacer(Modifier.height(T.lg))
            Q("Объекты", Type.caption, T.text2OnDark)
            Spacer(Modifier.height(T.sm))
            sites.forEach { s ->
                Pressable({ OverlayState.openCard(CardRef(Section.SITES, s.id)) }, Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(T.rControl))
                            .background(T.panelCard)
                            .padding(T.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Q(s.name, Type.small, T.textOnDark, 2, Modifier.weight(1f))
                        db.stageOfSite(s.id)?.let { DarkStageChip(it) }
                    }
                }
                Spacer(Modifier.height(T.xs))
            }
        }

        Spacer(Modifier.height(T.lg))
        SendButton("Отправить реквизиты", send, dark = true) {
            Actions.share(ctx, partyText(p), "Реквизиты")
        }

        Spacer(Modifier.height(T.xl))
    }
    BottomActions(primary = Pair("Изменить") { host.openForm(Section.CUSTOMERS, p.id) })
}

/**
 * Сотрудник в панели — это не анкета, а пульт: позвонить, написать,
 * поставить задачу. Всё в один тап, не выходя из того, чем занят.
 */
@Composable
internal fun ColumnScope.StaffBody(e: Employee, db: Db, host: OverlayHost) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(horizontal = T.lg)
    ) {
        val send = rememberSendState()

        CardHead(
            title = e.name.ifBlank { "Без имени" },
            sub = listOf(e.position, e.department).filter { it.isNotBlank() }.joinToString(" · ")
        )

        Spacer(Modifier.height(T.lg))

        e.phones.forEach { phone ->
            DarkPill(Ic.phone, phone, T.success) { Actions.dial(ctx, phone) }
            Spacer(Modifier.height(T.sm))
        }
        e.chats.forEach { chat ->
            val label = when (chat.kind) {
                "telegram" -> "Telegram"
                "whatsapp" -> "WhatsApp"
                "email" -> "Почта"
                else -> chat.kind
            }
            DarkPill(if (chat.kind == "email") Ic.mail else Ic.chat, label, T.accent) {
                Actions.chat(ctx, chat)
            }
            Spacer(Modifier.height(T.sm))
        }
        if (e.phones.isEmpty() && e.chats.isEmpty()) {
            Q("Контактов нет — добавь через «Изменить»", Type.small, T.text2OnDark)
            Spacer(Modifier.height(T.sm))
        }

        val mine = db.liveContracts.filter { it.responsible.equals(e.name, true) }
        if (mine.isNotEmpty()) {
            Spacer(Modifier.height(T.md))
            Q("Ведёт", Type.caption, T.text2OnDark)
            Spacer(Modifier.height(T.sm))
            mine.forEach { c ->
                Pressable({ OverlayState.openCard(CardRef(Section.CONTRACTS, c.id)) }, Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(T.rControl))
                            .background(T.panelCard)
                            .padding(T.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Q(c.label(db.site(c.siteId)?.name), Type.small, T.textOnDark, 2, Modifier.weight(1f))
                        Spacer(Modifier.width(T.sm))
                        Q(shownStatus(c).stage.short, Type.caption, shownStatus(c).stage.tone().fill, 1)
                    }
                }
                Spacer(Modifier.height(T.xs))
            }
        }

        Spacer(Modifier.height(T.lg))
        SendButton("Отправить контакт", send, dark = true) {
            Actions.share(ctx, Actions.employeeText(e), "Контакт сотрудника")
        }

        Spacer(Modifier.height(T.xl))
    }
    BottomActions(
        primary = Pair("Поставить задачу") { host.openTask(e.id) },
        secondary = Pair("Изменить") { host.openForm(Section.STAFF, e.id) }
    )
}

@Composable
private fun DarkPill(icon: String, label: String, tone: T.Tone, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = T.touchMin)
                .clip(RoundedCornerShape(T.rControl))
                .background(tone.fill.copy(alpha = 0.18f))
                .padding(horizontal = T.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QIcon(icon, size = 18.dp, tint = tone.fill, stroke = 2f)
            Spacer(Modifier.width(T.sm))
            Q(label, Type.small, T.textOnDark, 1)
        }
    }
}

// --- детали --------------------------------------------------------------

/**
 * Шапка карточки — одна на все четыре раздела.
 *
 * Имя показывается ровно один раз, подзаголовок — только если это другие
 * данные. Раньше каждая карточка рисовала шапку по-своему, и туда легко
 * попадало то же значение вторым абзацем.
 */
@Composable
private fun CardHead(title: String, sub: String) {
    Column(Modifier.fillMaxWidth()) {
        Q(title, Type.title, T.textOnDark, 3)
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(T.xs))
            Q(sub, Type.small, T.text2OnDark, 2)
        }
    }
}

/**
 * Строка факта, которая ведёт на связанную запись.
 *
 * Без ссылки выглядит как обычный факт — значит, переходить некуда: человека
 * с таким именем в реестре нет, объект не выбран. Так видно, где связь есть,
 * а где только текст.
 */
@Composable
private fun FactLink(key: String, value: String, onClick: (() -> Unit)?) {
    if (value.isBlank()) return
    val row: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Q(key, Type.caption, T.text2OnDark, 1, Modifier.width(104.dp))
            Q(
                value,
                Type.small,
                if (onClick != null) T.accent.fill else T.textOnDark,
                2,
                Modifier.weight(1f)
            )
            if (onClick != null) {
                QIcon(Ic.chevronRight, size = 16.dp, tint = T.accent.fill, stroke = 2f)
            }
        }
    }
    if (onClick != null) Pressable(onClick, Modifier.fillMaxWidth()) { row() } else row()
}

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

/**
 * Смена статуса в два тапа: стадия, потом статус внутри неё.
 * Сорок один статус одной лентой не читается, а стадий шесть.
 */
@Composable
private fun QuickStatus(current: Status, onPick: (Status) -> Unit) {
    var stage by remember(current) { mutableStateOf(current.stage) }

    Q("Статус", Type.caption, T.text2OnDark)
    Spacer(Modifier.height(T.sm))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(T.sm)
    ) {
        Stage.entries.forEach { st ->
            val on = st == stage
            Pressable({ stage = st }) {
                Box(
                    Modifier
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (on) st.tone().fill else Color.White.copy(alpha = 0.07f))
                        .padding(horizontal = T.md),
                    contentAlignment = Alignment.Center
                ) {
                    Q(st.short, Type.caption, if (on) Color.White else T.text2OnDark, 1)
                }
            }
        }
    }

    Spacer(Modifier.height(T.sm))
    Column(verticalArrangement = Arrangement.spacedBy(T.xs)) {
        Status.byStage(stage).forEach { s ->
            val on = s == current
            Pressable({ if (!on) onPick(s) }, Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = T.touchMin)
                        .clip(RoundedCornerShape(T.rControl))
                        .background(if (on) s.stage.tone().fill.copy(alpha = 0.22f) else T.panelCard)
                        .padding(horizontal = T.md, vertical = T.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Q(s.label, Type.small, if (on) s.stage.tone().fill else T.textOnDark, 2, Modifier.weight(1f))
                    if (on) QIcon(Ic.check, size = 18.dp, tint = s.stage.tone().fill, stroke = 2f)
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
internal fun RoundAction(icon: String, label: String, accent: Boolean = false, onClick: () -> Unit) {
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
internal fun DarkStageChip(stage: Stage) {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(stage.tone().fill.copy(alpha = 0.22f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Q(stage.short, Type.caption, stage.tone().fill, 1)
    }
}
