package ru.quickdeck.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.quickdeck.mobile.core.Feel
import ru.quickdeck.mobile.core.Ic
import ru.quickdeck.mobile.core.Q
import ru.quickdeck.mobile.core.QIcon
import ru.quickdeck.mobile.core.T
import ru.quickdeck.mobile.core.Type
import ru.quickdeck.mobile.data.*
import ru.quickdeck.mobile.overlay.BubbleService
import ru.quickdeck.mobile.ui.*

/**
 * Приложение целиком.
 *
 * Оно основное, а не вспомогательное: пузырь нужен для быстрого доступа,
 * но всё, что можно сделать через него, можно сделать и здесь.
 *
 * Устройство привычное, без изобретений: четыре вкладки внизу, реестр
 * читается слоями (папка → записи → запись), назад — стрелкой и системным
 * жестом. Ввод с клавиатуры живёт в SheetActivity: одна форма на всё
 * приложение вместо трёх похожих.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Feel.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { AppRoot() }
    }

    override fun onStop() {
        super.onStop()
        Backup.auto(this)
    }
}

/** Вкладки. Порядок — по частоте, а не по логике сущностей. */
private enum class Tab(val title: String, val icon: String) {
    SUMMARY("Сводка", Ic.summary),
    REGISTRY("Реестр", Ic.folder),
    TASKS("Задачи", Ic.task),
    MORE("Ещё", Ic.settings)
}

/**
 * Что сейчас на экране. Глубина хранится явно: по ней переход понимает,
 * ныряем мы или всплываем, и не гадает по внешней переменной, которая
 * к моменту анимации уже успела измениться.
 */
private data class Screen(val tab: Tab, val depth: Int, val layer: Layer)

/** Слой внутри вкладки «Реестр»: чем глубже, тем конкретнее. */
private sealed interface Layer {
    data object Folders : Layer
    data class Items(val section: Section, val group: String) : Layer
    data class Record(val section: Section, val id: String) : Layer
}

@Composable
private fun AppRoot() {
    val db by Store.db.collectAsState()
    var tab by remember { mutableStateOf(Tab.SUMMARY) }
    val layers = remember { mutableStateListOf<Layer>(Layer.Folders) }

    fun dive(l: Layer) { layers.add(l) }
    fun surface() { if (layers.size > 1) layers.removeAt(layers.size - 1) }

    fun openRecord(section: Section, id: String) {
        tab = Tab.REGISTRY
        if (layers.size == 1) layers.add(Layer.Items(section, ""))
        dive(Layer.Record(section, id))
    }

    BackHandler(enabled = layers.size > 1 || tab != Tab.SUMMARY) {
        Feel.tick()
        if (tab == Tab.REGISTRY && layers.size > 1) surface() else tab = Tab.SUMMARY
    }

    Box(Modifier.fillMaxSize().background(T.bg)) {
        // Единственная графика на фоне: мягкое пятно акцента сверху.
        AccentWash(Modifier.fillMaxWidth().height(320.dp))

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            TopBar(
                back = tab == Tab.REGISTRY && layers.size > 1,
                onBack = { surface() },
                title = when (tab) {
                    Tab.REGISTRY -> when (layers.last()) {
                        is Layer.Folders -> "Реестр"
                        is Layer.Items -> "Объекты"
                        is Layer.Record -> "Запись"
                    }
                    else -> tab.title
                }
            )

            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = Screen(tab, layers.size, layers.last()),
                    transitionSpec = {
                        // Вкладки — соседи, между ними нет «вперёд» и «назад»:
                        // любое боковое движение здесь читается как ошибка,
                        // потому что направление ничем не обосновано.
                        // Слои — глубина: вниз уезжает влево, вверх вправо.
                        val deeper = targetState.depth > initialState.depth
                        val shallower = targetState.depth < initialState.depth
                        when {
                            deeper -> (slideInHorizontally(tween(T.MS_SCREEN, easing = T.curve)) { it / 5 } +
                                fadeIn(tween(T.MS_STATE))) togetherWith
                                (slideOutHorizontally(tween(T.MS_EXIT, easing = T.curve)) { -it / 6 } +
                                    fadeOut(tween(T.MS_EXIT)))

                            shallower -> (slideInHorizontally(tween(T.MS_SCREEN, easing = T.curve)) { -it / 5 } +
                                fadeIn(tween(T.MS_STATE))) togetherWith
                                (slideOutHorizontally(tween(T.MS_EXIT, easing = T.curve)) { it / 6 } +
                                    fadeOut(tween(T.MS_EXIT)))

                            else -> fadeIn(tween(T.MS_STATE, easing = T.curve)) togetherWith
                                fadeOut(tween(T.MS_EXIT, easing = T.curve))
                        }
                    },
                    label = "screen"
                ) { state ->
                    val current = state.tab
                    val layer = state.layer
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = T.screenPad)
                    ) {
                        when (current) {
                            Tab.SUMMARY -> SummaryScreen(db) { s, id -> openRecord(s, id) }
                            Tab.TASKS -> TasksScreen(db) { id -> openRecord(Section.CONTRACTS, id) }
                            Tab.MORE -> MoreScreen(db)
                            Tab.REGISTRY -> when (layer) {
                                is Layer.Folders -> FoldersScreen(db) { s, g ->
                                    dive(Layer.Items(s, g))
                                }
                                is Layer.Items -> ItemsScreen(db, layer.section, layer.group) { id ->
                                    dive(Layer.Record(layer.section, id))
                                }
                                is Layer.Record -> RecordScreen(db, layer.section, layer.id) { s, id ->
                                    dive(Layer.Record(s, id))
                                }
                            }
                        }
                        Spacer(Modifier.height(T.xxl))
                    }
                }
            }

            TabBar(tab) { next ->
                Feel.tick()
                if (next == Tab.REGISTRY && tab == Tab.REGISTRY) {
                    while (layers.size > 1) layers.removeAt(layers.size - 1)
                }
                tab = next
            }
        }
    }
}

// ── шапка и вкладки ───────────────────────────────────────────────────────

@Composable
private fun TopBar(back: Boolean, onBack: () -> Unit, title: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = T.screenPad, end = 16.dp, top = 18.dp, bottom = T.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (back) {
            RoundBtn(Ic.back, "Назад", onClick = onBack)
            Spacer(Modifier.width(T.md))
        }
        Q(title, Type.body, T.ink, 1, Modifier.weight(1f))
        RoundBtn(Ic.search, "Поиск") { ctx.startActivity(SheetActivity.search(ctx)) }
        Spacer(Modifier.width(T.sm))
        RoundBtn(Ic.plus, "Добавить", accent = true) {
            ctx.startActivity(SheetActivity.form(ctx, Section.SITES, null))
        }
    }
}

@Composable
private fun RoundBtn(icon: String, label: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .tap(onClick = onClick)
            .shadowSoft(T.rIcon)
            .clip(RoundedCornerShape(T.rIcon))
            .background(if (accent) T.accent else T.card),
        contentAlignment = Alignment.Center
    ) {
        QIcon(icon, size = 20.dp, tint = if (accent) Color.White else T.mut)
    }
}

/**
 * Нижние вкладки — там, где их ищут все.
 *
 * Выбранная подсвечена заливкой акцента: цвет один на приложение, и здесь
 * он говорит «ты тут», а не украшает.
 */
@Composable
private fun TabBar(current: Tab, onPick: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Tab.entries.forEach { t ->
            val on = t == current
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) T.accent.copy(alpha = 0.10f) else Color.Transparent)
                    .tap(haptic = false) { onPick(t) }
                    .padding(vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                QIcon(t.icon, size = 20.dp, tint = if (on) T.accent else T.faint)
                Spacer(Modifier.height(5.dp))
                Q(t.title, Type.tab, if (on) T.accent else T.faint, 1)
            }
        }
    }
}

// ── сводка ────────────────────────────────────────────────────────────────

/**
 * Первый экран: один очаг, одна тревога, один график. Всё.
 *
 * Плитки «в работе / просрочено / ждёт оплаты / потенциально» отсюда убраны:
 * четыре равновеликих числа спорили друг с другом, и человек не понимал,
 * на что смотреть первым. Разбор по стадиям живёт в реестре.
 */
@Composable
private fun SummaryScreen(db: Db, onOpen: (Section, String) -> Unit) {
    val s = remember(db) { db.summary() }

    Hero(
        label = "По договорам",
        value = money(s.contracted).removeSuffix(" ₽"),
        unit = "₽",
        sub = if (s.rest > 0) "Не получено ${money(s.rest)}" else "Всё получено"
    )

    val overdue = remember(db) {
        db.liveContracts.filterNot { it.archived }
            .filter { shownStatusOf(it) == Status.OVERDUE }
    }
    if (overdue.isNotEmpty()) {
        val first = overdue.first()
        StatCard(
            label = "Просрочено",
            value = overdue.size.toString(),
            note = db.site(first.siteId)?.name ?: first.workKind,
            alarm = true,
            onClick = { onOpen(Section.CONTRACTS, first.id) }
        )
    } else if (s.awaitingPay > 0) {
        StatCard("Ждёт оплаты", s.awaitingPay.toString(), money(s.rest))
    } else {
        StatCard("В работе", s.inWork.toString(), "Сроки не горят")
    }

    val points = remember(db) { incomeByMonth(db) }
    if (points.size > 1) {
        SparkCard("Поступления", trendOf(points), points)
    }

    if (s.soon.isNotEmpty()) {
        GroupLabel("Ближайший срок")
        val (what, whenText) = s.soon.first()
        ItemRow(what, whenText) { }
    }
}

/** Поступления по месяцам — из оплаченных долей договоров. */
private fun incomeByMonth(db: Db): List<Float> {
    val now = java.time.LocalDate.now()
    val buckets = FloatArray(8)
    db.liveContracts.forEach { c ->
        val paid = c.paidAmount
        if (paid <= 0) return@forEach
        val end = parseDate(c.end) ?: return@forEach
        val diff = (now.year - end.year) * 12 + (now.monthValue - end.monthValue)
        if (diff in 0..7) buckets[7 - diff] += paid.toFloat()
    }
    return buckets.toList()
}

private fun trendOf(points: List<Float>): String {
    val half = points.size / 2
    val a = points.take(half).sum()
    val b = points.drop(half).sum()
    if (a <= 0f) return if (b > 0f) "рост" else "—"
    val pct = ((b - a) / a * 100).toInt()
    return if (pct >= 0) "+$pct%" else "$pct%"
}

// ── реестр: папки ─────────────────────────────────────────────────────────

/**
 * Верхний слой реестра — папки, а не список всего подряд.
 *
 * Смысл слоёв в том, чтобы не вникать в лишнее: сначала «что за пачка»,
 * потом «что внутри». Плоский список это и убивал.
 */
@Composable
private fun FoldersScreen(db: Db, onOpen: (Section, String) -> Unit) {
    val byCustomer = remember(db) {
        db.liveSites.groupBy { db.customer(it.customerId)?.name ?: "Без заказчика" }
            .toList().sortedByDescending { it.second.size }
    }

    Hero("Объекты", db.liveSites.size.toString(), sub = "в ${byCustomer.size} папках")

    if (byCustomer.isEmpty()) {
        Empty("Папок пока нет", "Заведи объект — он ляжет в папку своего заказчика")
    }

    byCustomer.forEach { (name, sites) ->
        val hot = sites.any { site ->
            db.activeContractsOfSite(site.id).any { shownStatusOf(it) == Status.OVERDUE }
        }
        FolderCard(name, sites.size, hot) { onOpen(Section.SITES, name) }
    }

    GroupLabel("Списки")
    FolderCard("Договоры", db.liveContracts.size, flat = true) { onOpen(Section.CONTRACTS, "") }
    FolderCard("Сотрудники", db.liveEmployees.size, flat = true) { onOpen(Section.STAFF, "") }
    FolderCard("Заказчики", db.liveCustomers.size, flat = true) { onOpen(Section.CUSTOMERS, "") }
}

// ── реестр: записи ────────────────────────────────────────────────────────

@Composable
private fun ItemsScreen(db: Db, section: Section, group: String, onOpen: (String) -> Unit) {
    when (section) {
        Section.SITES -> {
            val sites = remember(db, group) {
                db.liveSites.filter { (db.customer(it.customerId)?.name ?: "Без заказчика") == group }
            }
            Hero(group.ifBlank { "Объекты" }, sites.size.toString(), sub = "объекта")
            sites.forEach { site ->
                val active = db.activeContractsOfSite(site.id)
                val sum = active.sumOf { it.amount }
                val hot = active.any { shownStatusOf(it) == Status.OVERDUE }
                ItemRow(site.name.ifBlank { "Без названия" }, money(sum), hot) { onOpen(site.id) }
            }
            if (sites.isEmpty()) Empty("Пусто", "У этого заказчика пока нет объектов")
        }

        Section.CONTRACTS -> {
            val list = remember(db) { db.liveContracts.filterNot { it.archived } }
            Hero("Договоры", list.size.toString(), sub = "не закрыто")
            list.forEach { c ->
                val st = shownStatusOf(c)
                ItemRow(
                    c.workKind.ifBlank { "Без вида работ" },
                    money(c.amount),
                    st == Status.OVERDUE,
                    sub = db.site(c.siteId)?.name.orEmpty()
                ) { onOpen(c.id) }
            }
            if (list.isEmpty()) Empty("Пусто", "Заведи первый договор — он появится тут")
        }

        Section.STAFF -> {
            val list = remember(db) { db.liveEmployees }
            Hero("Сотрудники", list.size.toString(), sub = "в реестре")
            list.groupBy { it.department.ifBlank { "Без отдела" } }
                .toList().sortedBy { it.first }
                .forEach { (dept, people) ->
                    GroupLabel(dept)
                    people.forEach { e ->
                        ItemRow(e.name, "", sub = e.position) { onOpen(e.id) }
                    }
                }
            if (list.isEmpty()) Empty("Пусто", "Список тянется с листа «Сотрудники»")
        }

        Section.CUSTOMERS -> {
            val list = remember(db) { db.liveCustomers }
            Hero("Заказчики", list.size.toString(), sub = "в реестре")
            list.forEach { p ->
                ItemRow(p.name, db.sitesOfCustomer(p.id).size.toString()) { onOpen(p.id) }
            }
            if (list.isEmpty()) Empty("Пусто", "Список тянется с листа «Заказчики»")
        }
    }
}

// ── реестр: запись ────────────────────────────────────────────────────────

/**
 * Запись целиком. Здесь подробности уместны — сюда за ними и пришли.
 *
 * Но и тут порядок один: сперва имя, потом факты, потом задачи, и только
 * в самом низу — необратимое действие.
 */
@Composable
private fun RecordScreen(db: Db, section: Section, id: String, onOpen: (Section, String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    when (section) {
        Section.SITES -> {
            val site = db.site(id) ?: return Empty("Запись не найдена", "Возможно, её удалили в таблице")
            val contracts = db.contractsOfSite(site.id)
            val sum = contracts.filterNot { it.archived }.sumOf { it.amount }
            HeroTitle("Объект", site.name.ifBlank { "Без названия" }, site.fullName)
            Facts(
                listOfNotNull(
                    db.customer(site.customerId)?.name?.let { Triple("Заказчик", it, T.ink) },
                    site.address.takeIf { it.isNotBlank() }?.let { Triple("Адрес", it, T.ink) },
                    Triple("Сумма", money(sum), T.ink),
                    db.stageOfSite(site.id)?.let { Triple("Стадия", it.label, T.ink) }
                )
            )
            if (contracts.isNotEmpty()) {
                GroupLabel("Договоры")
                contracts.forEach { c ->
                    val st = shownStatusOf(c)
                    ItemRow(
                        c.workKind.ifBlank { "Без вида работ" },
                        money(c.amount),
                        st == Status.OVERDUE,
                        sub = st.label
                    ) { onOpen(Section.CONTRACTS, c.id) }
                }
            }
            EditRow("Изменить объект") {
                ctx.startActivity(SheetActivity.form(ctx, Section.SITES, site.id))
            }
        }

        Section.CONTRACTS -> {
            val c = db.contract(id) ?: return Empty("Запись не найдена", "Возможно, его удалили в таблице")
            val st = shownStatusOf(c)
            HeroTitle("Договор", c.workKind.ifBlank { "Без вида работ" }, db.site(c.siteId)?.name.orEmpty())
            Facts(
                listOf(
                    Triple("Статус", st.label, if (st == Status.OVERDUE) T.danger else T.ink),
                    Triple("Сумма", money(c.amount), T.ink),
                    Triple("Не получено", money(c.restAmount), T.accent),
                    Triple("Срок", c.end.takeIf { it.isNotBlank() }?.let { dateShort(it) } ?: "—",
                        if (st == Status.OVERDUE) T.danger else T.ink)
                )
            )
            GroupLabel("Задачи · ${c.tasks.size - c.openTasks} из ${c.tasks.size}")
            c.tasks.forEach { t ->
                TaskRow(t.text, t.done) { Store.setContractTaskDone(c.id, t.id, !t.done) }
            }
            AddRow("Задача") { ctx.startActivity(SheetActivity.tasks(ctx, c.id)) }
            EditRow("Изменить договор") {
                ctx.startActivity(SheetActivity.form(ctx, Section.CONTRACTS, c.id))
            }
        }

        Section.STAFF -> {
            val e = db.employee(id) ?: return Empty("Запись не найдена", "Возможно, его удалили в таблице")
            HeroTitle("Сотрудник", e.name, e.position)
            Facts(
                listOfNotNull(
                    e.department.takeIf { it.isNotBlank() }?.let { Triple("Отдел", it, T.ink) },
                    e.phones.firstOrNull()?.let { Triple("Телефон", it, T.ink) },
                    e.location.takeIf { it.isNotBlank() }?.let { Triple("Нахождение", it, T.ink) }
                )
            )
            EditRow("Поставить задачу") { ctx.startActivity(SheetActivity.task(ctx, e.id)) }
        }

        Section.CUSTOMERS -> {
            val p = db.customer(id) ?: return Empty("Запись не найдена", "Возможно, его удалили в таблице")
            HeroTitle("Заказчик", p.name, p.fullName)
            Facts(
                listOfNotNull(
                    p.inn.takeIf { it.isNotBlank() }?.let { Triple("ИНН", it, T.ink) },
                    p.director.takeIf { it.isNotBlank() }?.let { Triple("Руководитель", it, T.ink) },
                    p.phone.takeIf { it.isNotBlank() }?.let { Triple("Телефон", it, T.ink) },
                    p.bank.takeIf { it.isNotBlank() }?.let { Triple("Банк", it, T.ink) }
                )
            )
            val sites = db.sitesOfCustomer(p.id)
            if (sites.isNotEmpty()) {
                GroupLabel("Объекты")
                sites.forEach { s -> ItemRow(s.name, "") { onOpen(Section.SITES, s.id) } }
            }
        }
    }
}

/** Изменение записи — ровно одна кнопка со словом, внизу экрана. */
@Composable
private fun EditRow(text: String, onClick: () -> Unit) {
    Spacer(Modifier.height(T.lg))
    AddRow(text, onClick)
}

// ── задачи ────────────────────────────────────────────────────────────────

/**
 * Все открытые задачи, сгруппированные по договору.
 *
 * Задача заводится в договоре и живёт в нём — этот экран только собирает
 * их вместе. Поэтому над каждой группой стоит имя договора: видно, откуда
 * она взялась и к чему относится.
 */
@Composable
private fun TasksScreen(db: Db, onOpen: (String) -> Unit) {
    val withTasks = remember(db) { db.liveContracts.filter { it.tasks.isNotEmpty() } }
    val open = withTasks.sumOf { it.openTasks }

    Hero(
        "Открыто", open.toString(),
        sub = if (withTasks.isEmpty()) "Задачи заводятся в договоре"
        else "по ${withTasks.count { it.openTasks > 0 }} договорам"
    )

    if (withTasks.isEmpty()) {
        Empty("Задач пока нет", "Открой договор и нажми «Задача» — она появится здесь")
        return
    }

    withTasks.filter { it.openTasks > 0 }.forEach { c ->
        val title = listOfNotNull(
            db.site(c.siteId)?.name?.takeIf { it.isNotBlank() },
            c.workKind.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { "Договор" }
        GroupLabel(title)
        c.tasks.filterNot { it.done }.forEach { t ->
            TaskRow(t.text, false) { Store.setContractTaskDone(c.id, t.id, true) }
        }
    }

    val done = withTasks.flatMap { c -> c.tasks.filter { it.done }.map { c to it } }
    if (done.isNotEmpty()) {
        GroupLabel("Сделано")
        done.take(5).forEach { (c, t) ->
            TaskRow(t.text, true) { Store.setContractTaskDone(c.id, t.id, false) }
        }
    }
}

// ── ещё ───────────────────────────────────────────────────────────────────

/**
 * Настройки.
 *
 * Ни одной поясняющей строки под кнопкой: назначение читается из названия,
 * а подробность вылезает по долгому нажатию. Так устроены настройки везде,
 * и человеку не надо заново учиться читать наш экран.
 */
@Composable
private fun MoreScreen(db: Db) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var bubbleOn by remember { mutableStateOf(Store.bubbleEnabled) }
    var auto by remember { mutableStateOf(Store.autoSync) }
    var syncing by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf(Store.lastSync) }
    var lastBackup by remember { mutableStateOf(Store.lastBackup) }
    var granted by remember { mutableStateOf(BubbleService.canDraw(ctx)) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        granted = BubbleService.canDraw(ctx)
        if (granted && bubbleOn) BubbleService.start(ctx)
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val r = Backup.write(ctx, uri)
        lastBackup = Store.lastBackup
        Feel.confirm()
        Toast.makeText(ctx, r.fold({ "Копия сохранена" }, { "Не вышло: ${it.message}" }), Toast.LENGTH_LONG).show()
    }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val r = Backup.read(ctx, uri)
        Feel.confirm()
        Toast.makeText(ctx, r.fold({ "Восстановлено: $it" }, { "Не вышло: ${it.message}" }), Toast.LENGTH_LONG).show()
    }

    fun askOverlay() {
        overlayLauncher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        )
    }

    // Очаг здесь — состояние обмена, а не время: время в прочерк не
    // превращается, а «—» в 62 пункта выглядит как чёрточка через пол-экрана.
    val ready = Store.syncConfigured
    Hero(
        label = "Обмен",
        value = if (!ready) "нет" else if (lastSync.isBlank()) "готов" else lastSync,
        sub = when {
            !ready -> "Таблица не подключена"
            lastSync.isBlank() -> "Ещё ни разу не обменивались"
            else -> "Последняя синхронизация"
        },
        color = if (ready) T.ink else T.faint
    )

    GroupLabel("Таблица", top = 0.dp)
    SettingRow(
        name = if (syncing) "Обмениваюсь…" else "Синхронизировать",
        hint = "Правки уезжают в таблицу, правки из таблицы приезжают сюда. Побеждает тот, кто правил позже.",
        onClick = {
            if (!Store.syncConfigured) {
                Toast.makeText(ctx, "Сначала вставь ссылку на таблицу", Toast.LENGTH_SHORT).show()
                return@SettingRow
            }
            if (syncing) return@SettingRow
            syncing = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { Sync.run() }
                syncing = false
                lastSync = Store.lastSync
                Feel.confirm()
                Toast.makeText(ctx, r.fold({ it.text }, { "Не вышло: ${it.message}" }), Toast.LENGTH_LONG).show()
            }
        }
    )
    SettingRow(
        name = "Обмениваться самому",
        hint = "После каждой правки и при открытии пузыря.",
        toggle = auto
    ) { auto = !auto; Store.autoSync = auto; Feel.tick() }
    SettingRow(
        name = "Таблица",
        value = if (Store.syncConfigured) "подключена" else "не задана",
        hint = "Адрес веб-приложения Apps Script. Меняется раз в жизни.",
        onClick = { ctx.startActivity(SheetActivity.sheetUrl(ctx)) }
    )

    GroupLabel("Пузырь")
    SettingRow(
        name = "Поверх приложений",
        hint = "Реестр открывается поверх любого приложения в один тап.",
        toggle = bubbleOn
    ) {
        if (!bubbleOn && !BubbleService.canDraw(ctx)) { askOverlay(); return@SettingRow }
        bubbleOn = !bubbleOn
        Store.bubbleEnabled = bubbleOn
        Feel.tick()
        if (bubbleOn) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            BubbleService.start(ctx)
        } else BubbleService.stop(ctx)
    }
    if (!granted) {
        SettingRow(
            name = "Дать разрешение",
            value = "нужно",
            hint = "Android требует разрешение «Поверх других приложений».",
            onClick = { askOverlay() }
        )
    }

    GroupLabel("Шаблоны")
    SettingRow(
        name = "Шаблоны сообщений",
        value = db.templates.size.toString(),
        hint = "Заготовки задач сотрудникам: создать, изменить, удалить.",
        onClick = { ctx.startActivity(SheetActivity.templates(ctx)) }
    )
    GroupLabel("Копия")
    SettingRow(
        name = "Создать копию",
        hint = "Реестр живёт на телефоне. Копия — единственный способ не потерять его вместе с трубкой.",
        onClick = { saveLauncher.launch(Backup.suggestedName()) }
    )
    SettingRow(
        name = "Восстановить из копии",
        value = lastBackup.ifBlank { "" },
        hint = "Заменит весь реестр содержимым файла.",
        onClick = { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
    )
    SettingRow(
        name = "Выгрузить CSV",
        hint = "Отдаёт реестр таблицей — для почты или Excel.",
        onClick = { Export.share(ctx) }
    )
}

/**
 * Строка настройки: название, значение или переключатель.
 *
 * Подсказка живёт под долгим нажатием, а не под кнопкой: объяснение доступно
 * тому, кому оно нужно, и не мешает тому, кто и так знает.
 */
@Composable
private fun SettingRow(
    name: String,
    hint: String,
    value: String = "",
    toggle: Boolean? = null,
    onClick: () -> Unit = {}
) {
    var tip by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (tip) {
            Surface(
                Modifier.padding(bottom = T.sm),
                radius = 18.dp,
                fill = T.ink
            ) {
                Q(hint, Type.small, Color(0xFFEDF1F2), 5, Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
            }
        }
        Surface(
            Modifier
                .padding(bottom = T.gap)
                .longPressable(
                    onClick = { if (toggle != null) { onClick(); tip = false } else onClick() },
                    onLong = { tip = !tip; Feel.confirm() }
                ),
            radius = T.rRow
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = T.cardPad, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Q(name, Type.body, T.ink, 2, Modifier.weight(1f))
                Spacer(Modifier.width(T.md))
                when {
                    toggle != null -> Switch(toggle)
                    value.isNotBlank() -> Q(value, Type.small, T.faint, 1)
                    else -> QIcon(Ic.chevronRight, size = 18.dp, tint = T.faint)
                }
            }
        }
    }
}
