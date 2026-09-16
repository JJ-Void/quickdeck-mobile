package ru.quickdeck.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
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
import ru.quickdeck.mobile.ui.*

/**
 * Всё, где нужна клавиатура, живёт здесь — в обычном окне приложения.
 *
 * Окну службы для клавиатуры пришлось бы стать фокусируемым, а фокусируемый
 * оверлей перехватывает весь экран и его нечем закрыть. Обычная Activity
 * получает и клавиатуру, и «назад», и прокрутку даром.
 */
class SheetActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SECTION = "section"
        private const val EXTRA_ID = "id"
        private const val EXTRA_SITE = "siteId"
        private const val EXTRA_CONTRACT = "contractId"
        private const val MODE_FORM = "form"
        private const val MODE_SEARCH = "search"
        private const val MODE_TASK = "task"
        private const val MODE_TEMPLATES = "templates"
        private const val MODE_TASKS = "tasks"
        private const val MODE_URL = "url"

        fun form(ctx: Context, section: Section, id: String?): Intent =
            Intent(ctx, SheetActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_FORM)
                putExtra(EXTRA_SECTION, section.name)
                if (id != null) putExtra(EXTRA_ID, id)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        fun search(ctx: Context): Intent =
            Intent(ctx, SheetActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_SEARCH)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /**
         * Задача помнит, откуда её начали: объект или договор приезжают
         * вместе с сотрудником, и спрашивать их заново не приходится.
         */
        fun task(
            ctx: Context,
            employeeId: String,
            siteId: String? = null,
            contractId: String? = null
        ): Intent =
            Intent(ctx, SheetActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_TASK)
                putExtra(EXTRA_ID, employeeId)
                if (siteId != null) putExtra(EXTRA_SITE, siteId)
                if (contractId != null) putExtra(EXTRA_CONTRACT, contractId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /** Задачи по договору: набор с клавиатуры, поэтому обычное окно. */
        fun tasks(ctx: Context, contractId: String): Intent =
            Intent(ctx, SheetActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_TASKS)
                putExtra(EXTRA_ID, contractId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /** Адрес таблицы: одно поле, поэтому отдельного экрана не заводим. */
        fun sheetUrl(ctx: Context): Intent =
            Intent(ctx, SheetActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_URL)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        fun templates(ctx: Context): Intent =
            Intent(ctx, SheetActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_TEMPLATES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Feel.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FORM
        val section = runCatching {
            Section.valueOf(intent.getStringExtra(EXTRA_SECTION) ?: Section.SITES.name)
        }.getOrDefault(Section.SITES)

        setContent {
            SheetRoot(
                mode = mode,
                section = section,
                id = intent.getStringExtra(EXTRA_ID),
                siteId = intent.getStringExtra(EXTRA_SITE),
                contractId = intent.getStringExtra(EXTRA_CONTRACT),
                onDone = { finish() }
            )
        }
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}

@Composable
private fun SheetRoot(
    mode: String,
    section: Section,
    id: String?,
    onDone: () -> Unit,
    siteId: String? = null,
    contractId: String? = null
) {
    val db by Store.db.collectAsState()
    val scope = rememberCoroutineScope()
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    // Выбор — слой ПОВЕРХ формы, а не переход на другой экран. Когда форма
    // уходила из композиции, её состояние уничтожалось вместе со всем
    // набранным, а лямбда выбора писала в уже мёртвое состояние: заказчик
    // не появлялся и терялось всё заполненное.
    var pick by remember { mutableStateOf<PickRequest?>(null) }
    var target by remember { mutableStateOf(section to id) }

    fun saved() {
        if (Store.autoSync && Store.syncConfigured) {
            scope.launch { withContext(Dispatchers.IO) { Sync.run() } }
        }
        onDone()
    }

    BackHandler { if (pick != null) pick = null else onDone() }

    Box(
        Modifier
            .fillMaxSize()
            .background(T.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (pick != null) pick = null else onDone() }
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        SheetSurface(maxH) {
            when (mode) {
                "search" -> SearchStep(db = db, onOpen = { target = it }, onClose = onDone)

                "templates" -> TemplatesSheet(db = db, onClose = onDone)

                "url" -> SheetUrlStep(onClose = onDone)

                "tasks" -> {
                    val contract = db.contract(id)
                    if (contract == null) {
                        Column(Modifier.fillMaxWidth().padding(T.xl)) {
                            Q("Договор не найден", Type.heading, T.text2)
                        }
                    } else {
                        TasksSheet(contract = contract, db = db, onClose = onDone)
                    }
                }

                "task" -> {
                    val who = db.employee(id)
                    if (who == null) {
                        Column(Modifier.fillMaxWidth().padding(T.xl)) {
                            Q("Сотрудник не найден", Type.heading, T.text2)
                        }
                    } else {
                        TaskSheet(
                            employee = who,
                            db = db,
                            onPick = { pick = it },
                            onClose = onDone,
                            contextSiteId = siteId,
                            contextContractId = contractId
                        )
                    }
                }

                else -> FormStep(
                    section = target.first,
                    id = target.second,
                    db = db,
                    onPick = { pick = it },
                    onSaved = { saved() },
                    onCancel = onDone
                )
            }
        }

        pick?.let { request -> PickOverlay(request, db, maxH) { pick = null } }
    }
}

@Composable
private fun SheetSurface(maxH: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = maxH)
            .clip(RoundedCornerShape(topStart = T.rSheet, topEnd = T.rSheet))
            .background(T.bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        Grip()
        content()
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
                .background(T.hairline)
        )
    }
}

// --- форма ---------------------------------------------------------------

@Composable
private fun ColumnScope.FormStep(
    section: Section,
    id: String?,
    db: Db,
    onPick: (PickRequest) -> Unit,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    // Заготовку создаём ОДИН раз. Site() и Party() выдают новый идентификатор
    // при каждом вызове, и без remember ключ состояния менялся бы на каждой
    // перерисовке, стирая всё набранное.
    val key = section to id
    when (section) {
        Section.SITES -> {
            val initial = remember(key) { db.site(id) ?: Site() }
            SiteForm(
                initial = initial, db = db, onPick = onPick,
                onDone = { Store.upsertSite(it); onSaved() },
                onCancel = onCancel,
                onDelete = if (id != null) ({ Store.deleteSite(id); onSaved() }) else null
            )
        }

        Section.CONTRACTS -> {
            val initial = remember(key) { db.contract(id) ?: Contract() }
            ContractForm(
                initial = initial, db = db, onPick = onPick,
                onDone = { Store.upsertContract(it); onSaved() },
                onCancel = onCancel,
                onDelete = if (id != null) ({ Store.deleteContract(id); onSaved() }) else null
            )
        }

        Section.CUSTOMERS -> {
            val initial = remember(key) { db.customer(id) ?: Party() }
            PartyForm(
                initial = initial,
                title = if (id == null) "Новый заказчик" else "Заказчик",
                onDone = { Store.upsertCustomer(it); onSaved() },
                onCancel = onCancel,
                onDelete = if (id != null) ({ Store.deleteCustomer(id); onSaved() }) else null
            )
        }

        Section.STAFF -> {
            val initial = remember(key) { db.employee(id) ?: Employee() }
            EmployeeForm(
                initial = initial, db = db, onPick = onPick,
                onDone = { Store.upsertEmployee(it); onSaved() },
                onCancel = onCancel,
                onDelete = if (id != null) ({ Store.deleteEmployee(id); onSaved() }) else null
            )
        }
    }
}

// --- выбор поверх формы --------------------------------------------------

@Composable
private fun BoxScope.PickOverlay(request: PickRequest, db: Db, maxH: Dp, onClose: () -> Unit) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(
        Modifier
            .matchParentSize()
            .background(T.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
    )

    Box(Modifier.matchParentSize(), contentAlignment = Alignment.BottomCenter) {
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
                    .background(T.bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
            ) {
                Grip()
                Row(
                    Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Q(request.title, Type.title, T.text, 1, Modifier.weight(1f))
                    Pressable(onClose) {
                        Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                            QIcon(Ic.close, size = 20.dp, tint = T.text2)
                        }
                    }
                }
                Spacer(Modifier.height(T.sm))

                when (request) {
                    is PickRequest.Values -> ValueList(request, onClose)
                    is PickRequest.CustomerPick -> PartyList(db.liveCustomers, request.onPick, onClose)
                    is PickRequest.EmployeePick -> EmployeeList(db.liveEmployees, request.onPick, onClose)
                    is PickRequest.SitePick -> SiteList(db, request.onPick, onClose)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ValueList(request: PickRequest.Values, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, request.options) { filtered(request.options, query) { it } }

    if (request.options.size > 7) SearchBox(query) { query = it }

    LazyColumn(
        Modifier.weight(1f, fill = false),
        contentPadding = PaddingValues(start = T.lg, end = T.lg, bottom = T.lg),
        verticalArrangement = Arrangement.spacedBy(T.xs)
    ) {
        items(shown) { option ->
            val selected = option.equals(request.current, true)
            Pressable({ request.onPick(option); onClose() }, Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = T.touchMin)
                        .clip(RoundedCornerShape(T.rControl))
                        .background(if (selected) T.action.chip else T.surface)
                        .padding(horizontal = T.md, vertical = T.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Q(option, Type.body, if (selected) T.action.ink else T.text, 2, Modifier.weight(1f))
                    if (selected) QIcon(Ic.check, size = 18.dp, tint = T.action.ink, stroke = 2f)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PartyList(
    list: List<Party>,
    onPick: (Party) -> Unit,
    onClose: () -> Unit
) {
    var fresh by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val shown = remember(query, list) { filtered(list, query) { it.name + " " + it.inn } }

    if (list.size > 7) SearchBox(query) { query = it }

    LazyColumn(
        Modifier.weight(1f, fill = false),
        contentPadding = PaddingValues(start = T.lg, end = T.lg),
        verticalArrangement = Arrangement.spacedBy(T.sm)
    ) {
        items(shown, key = { it.id }) { p ->
            PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.customers) {
                onPick(p); onClose()
            }
        }
    }

    // Заводим по одному названию, не уходя из формы: переход на вторую форму
    // выгрузил бы первую из композиции вместе со всем набранным.
    Column(Modifier.padding(T.lg)) {
        Field("Новый заказчик", fresh, { fresh = it }, placeholder = "Название")
        Spacer(Modifier.height(T.sm))
        PrimaryButton("Создать и выбрать", {
            val made = Party(name = fresh.trim())
            Store.upsertCustomer(made)
            onPick(made)
            onClose()
        }, enabled = fresh.isNotBlank())
    }
}

@Composable
private fun ColumnScope.EmployeeList(list: List<Employee>, onPick: (Employee) -> Unit, onClose: () -> Unit) {
    var fresh by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    // Сотрудников десятки: листать весь список ради одного человека — долго.
    val shown = remember(query, list) {
        filtered(list, query) { it.name + " " + it.department + " " + it.position }
    }

    if (list.size > 7) SearchBox(query) { query = it }

    LazyColumn(
        Modifier.weight(1f, fill = false),
        contentPadding = PaddingValues(start = T.lg, end = T.lg),
        verticalArrangement = Arrangement.spacedBy(T.sm)
    ) {
        items(shown, key = { it.id }) { e ->
            EmployeeRow(e) { onPick(e); onClose() }
        }
    }

    Column(Modifier.padding(T.lg)) {
        Field("Новый сотрудник", fresh, { fresh = it }, placeholder = "Ф. И. О.")
        Spacer(Modifier.height(T.sm))
        PrimaryButton("Создать и выбрать", {
            val made = Employee(name = fresh.trim())
            Store.upsertEmployee(made)
            onPick(made)
            onClose()
        }, enabled = fresh.isNotBlank())
    }
}

@Composable
private fun ColumnScope.SiteList(db: Db, onPick: (Site) -> Unit, onClose: () -> Unit) {
    var fresh by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val shown = remember(query, db.liveSites) {
        filtered(db.liveSites, query) { it.name + " " + it.address + " " + it.fullName }
    }

    if (db.liveSites.size > 7) SearchBox(query) { query = it }

    LazyColumn(
        Modifier.weight(1f, fill = false),
        contentPadding = PaddingValues(start = T.lg, end = T.lg),
        verticalArrangement = Arrangement.spacedBy(T.sm)
    ) {
        items(shown, key = { it.id }) { s ->
            SiteRow(s, db) { onPick(s); onClose() }
        }
    }

    Column(Modifier.padding(T.lg)) {
        Field("Новый объект", fresh, { fresh = it }, placeholder = "Краткое наименование")
        Spacer(Modifier.height(T.sm))
        PrimaryButton("Создать и выбрать", {
            val made = Site(name = fresh.trim())
            Store.upsertSite(made)
            onPick(made)
            onClose()
        }, enabled = fresh.isNotBlank())
    }
}

// --- поиск ---------------------------------------------------------------

@Composable
private fun ColumnScope.SearchStep(
    db: Db,
    onOpen: (Pair<Section, String>) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    val sites = remember(q, db) {
        if (q.isEmpty()) db.liveSites else db.liveSites.filter {
            it.name.lowercase().contains(q) || it.address.lowercase().contains(q) ||
                it.fullName.lowercase().contains(q)
        }
    }
    val contracts = remember(q, db) {
        if (q.isEmpty()) db.liveContracts else db.liveContracts.filter {
            it.workKind.lowercase().contains(q) || it.code.lowercase().contains(q) ||
                (db.site(it.siteId)?.name?.lowercase()?.contains(q) ?: false)
        }
    }
    val parties = remember(q, db) {
        if (q.isEmpty()) emptyList() else db.liveCustomers.filter {
            it.name.lowercase().contains(q) || it.inn.contains(q)
        }
    }
    val people = remember(q, db) {
        if (q.isEmpty()) emptyList() else db.liveEmployees.filter {
            it.name.lowercase().contains(q) || it.position.lowercase().contains(q) ||
                it.department.lowercase().contains(q)
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Q("Поиск по реестру", Type.title, T.text, 1, Modifier.weight(1f))
        Pressable(onClose) {
            Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                QIcon(Ic.close, size = 20.dp, tint = T.text2)
            }
        }
    }

    Spacer(Modifier.height(T.sm))
    Box(Modifier.padding(horizontal = T.lg)) {
        Field("Что ищем", query, { query = it }, placeholder = "Цимлянская, ИД, ЭнергоКомплекс")
    }
    Spacer(Modifier.height(T.md))

    LazyColumn(
        Modifier.weight(1f, fill = false),
        contentPadding = PaddingValues(start = T.lg, end = T.lg, bottom = T.lg),
        verticalArrangement = Arrangement.spacedBy(T.sm)
    ) {
        if (sites.isNotEmpty()) {
            item { GroupLabel("Объекты") }
            items(sites, key = { "s" + it.id }) { s ->
                SiteRow(s, db) { onOpen(Section.SITES to s.id) }
            }
        }
        if (contracts.isNotEmpty()) {
            item { GroupLabel("Договоры") }
            items(contracts, key = { "c" + it.id }) { c ->
                ContractRow(c, db) { onOpen(Section.CONTRACTS to c.id) }
            }
        }
        if (parties.isNotEmpty()) {
            item { GroupLabel("Заказчики") }
            items(parties, key = { "p" + it.id }) { p ->
                PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.customers) {
                    onOpen(Section.CUSTOMERS to p.id)
                }
            }
        }
        if (people.isNotEmpty()) {
            item { GroupLabel("Сотрудники") }
            items(people, key = { "e" + it.id }) { e ->
                EmployeeRow(e) { onOpen(Section.STAFF to e.id) }
            }
        }
        if (sites.isEmpty() && contracts.isEmpty() && parties.isEmpty() && people.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = T.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { Q("Ничего не нашлось", Type.heading, T.text2) }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Column {
        Spacer(Modifier.height(T.sm))
        Q(text, Type.caption, T.text3)
        Spacer(Modifier.height(T.xs))
    }
}
