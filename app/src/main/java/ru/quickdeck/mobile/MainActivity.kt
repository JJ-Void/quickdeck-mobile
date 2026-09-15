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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.quickdeck.mobile.core.*
import ru.quickdeck.mobile.data.*
import ru.quickdeck.mobile.overlay.BubbleService
import ru.quickdeck.mobile.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent { AppRoot() }
    }

    override fun onStop() {
        super.onStop()
        // Тихий срез при уходе из приложения: данные живут только здесь.
        Backup.auto(this)
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class SectionList(val section: Section) : Screen
    data class Card(val section: Section, val id: String) : Screen
    data class Form(val section: Section, val id: String?) : Screen
}

@Composable
private fun AppRoot() {
    val db by Store.db.collectAsState()
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }

    // Выбор — слой поверх экрана, а не отдельный экран в стеке. Иначе форма
    // уходит из композиции и теряет всё набранное.
    var pick by remember { mutableStateOf<PickRequest?>(null) }

    fun push(s: Screen) = stack.add(s)
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }

    fun afterSave() {
        if (Store.autoSync && Store.syncConfigured) {
            scope.launch { withContext(Dispatchers.IO) { Sync.run() } }
        }
        pop()
    }

    BackHandler(enabled = pick != null || stack.size > 1) {
        if (pick != null) pick = null else pop()
    }

    Box(Modifier.fillMaxSize().background(T.bg)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            when (val s = stack.last()) {
                Screen.Home -> HomeScreen(db) { push(it) }

                is Screen.SectionList -> SectionScreen(
                    section = s.section,
                    db = db,
                    onBack = { pop() },
                    onOpen = { push(it) },
                    onCreate = { push(Screen.Form(s.section, null)) }
                )

                is Screen.Card -> CardScreen(
                    section = s.section,
                    id = s.id,
                    db = db,
                    onBack = { pop() },
                    onOpen = { push(it) }
                )

                is Screen.Form -> FormScreen(
                    section = s.section,
                    id = s.id,
                    db = db,
                    onPick = { pick = it },
                    onSaved = { afterSave() },
                    onCancel = { pop() },
                    onDeleted = {
                        while (stack.size > 1 && stack.last() !is Screen.SectionList) {
                            stack.removeAt(stack.size - 1)
                        }
                        if (Store.autoSync && Store.syncConfigured) {
                            scope.launch { withContext(Dispatchers.IO) { Sync.run() } }
                        }
                    }
                )
            }
        }

        pick?.let { request -> PickLayer(request, db) { pick = null } }
    }
}

// --- выбор поверх экрана -------------------------------------------------

@Composable
private fun BoxScope.PickLayer(request: PickRequest, db: Db, onClose: () -> Unit) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.8f).dp

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
                Row(
                    Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.lg),
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
                PickBody(request, db, onClose)
            }
        }
    }
}

@Composable
private fun ColumnScope.PickBody(request: PickRequest, db: Db, onClose: () -> Unit) {
    when (request) {
        is PickRequest.Values -> LazyColumn(
            Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = T.lg, end = T.lg, bottom = T.lg),
            verticalArrangement = Arrangement.spacedBy(T.xs)
        ) {
            items(request.options) { option ->
                val on = option.equals(request.current, true)
                Pressable({ request.onPick(option); onClose() }, Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = T.touchMin)
                            .clip(RoundedCornerShape(T.rControl))
                            .background(if (on) T.accent.chip else T.surface)
                            .padding(horizontal = T.md, vertical = T.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Q(option, Type.body, if (on) T.accent.ink else T.text, 2, Modifier.weight(1f))
                        if (on) QIcon(Ic.check, size = 18.dp, tint = T.accent.ink, stroke = 2f)
                    }
                }
            }
        }

        is PickRequest.CustomerPick -> QuickList(
            rows = { onDone ->
                db.liveCustomers.forEach { p ->
                    PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.customers) {
                        request.onPick(p); onDone()
                    }
                    Spacer(Modifier.height(T.sm))
                }
            },
            label = "Новый заказчик",
            onCreate = { name ->
                val made = Party(name = name)
                Store.upsertCustomer(made)
                request.onPick(made)
            },
            onClose = onClose
        )

        is PickRequest.EmployeePick -> QuickList(
            rows = { onDone ->
                db.liveEmployees.forEach { e ->
                    EmployeeRow(e) { request.onPick(e); onDone() }
                    Spacer(Modifier.height(T.sm))
                }
            },
            label = "Новый сотрудник",
            onCreate = { name ->
                val made = Employee(name = name)
                Store.upsertEmployee(made)
                request.onPick(made)
            },
            onClose = onClose
        )

        is PickRequest.SitePick -> QuickList(
            rows = { onDone ->
                db.liveSites.forEach { s ->
                    SiteRow(s, db) { request.onPick(s); onDone() }
                    Spacer(Modifier.height(T.sm))
                }
            },
            label = "Новый объект",
            onCreate = { name ->
                val made = Site(name = name)
                Store.upsertSite(made)
                request.onPick(made)
            },
            onClose = onClose
        )
    }
}

/**
 * Список с возможностью завести запись прямо здесь, одним названием.
 * Уход на вторую форму выгрузил бы первую вместе со всем набранным.
 */
@Composable
private fun ColumnScope.QuickList(
    rows: @Composable ColumnScope.(onDone: () -> Unit) -> Unit,
    label: String,
    onCreate: (String) -> Unit,
    onClose: () -> Unit
) {
    var fresh by remember { mutableStateOf("") }

    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = T.lg)
    ) {
        rows(onClose)
    }

    Column(Modifier.padding(T.lg)) {
        Field(label, fresh, { fresh = it }, placeholder = "Название")
        Spacer(Modifier.height(T.sm))
        PrimaryButton("Создать и выбрать", {
            onCreate(fresh.trim())
            onClose()
        }, enabled = fresh.isNotBlank())
    }
}

// --- главный экран -------------------------------------------------------

@Composable
private fun HomeScreen(db: Db, onOpen: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var bubbleOn by remember { mutableStateOf(Store.bubbleEnabled) }
    var url by remember { mutableStateOf(Store.sheetsUrl) }
    var auto by remember { mutableStateOf(Store.autoSync) }
    var greeting by remember { mutableStateOf(Store.greeting) }
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
        val result = Backup.write(ctx, uri)
        lastBackup = Store.lastBackup
        Toast.makeText(
            ctx,
            result.fold({ "Копия сохранена" }, { "Не вышло: ${it.message}" }),
            Toast.LENGTH_LONG
        ).show()
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = Backup.read(ctx, uri)
        Toast.makeText(
            ctx,
            result.fold({ "Восстановлено записей: $it" }, { "Не вышло: ${it.message}" }),
            Toast.LENGTH_LONG
        ).show()
    }

    fun askOverlay() {
        overlayLauncher.launch(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = T.lg)
    ) {
        Spacer(Modifier.height(T.lg))
        Q("Подряд", Type.title, T.text)
        Q("Объекты, договоры и люди под большой палец", Type.small, T.text2)
        Spacer(Modifier.height(T.xl))

        // --- разделы ------------------------------------------------------
        Section.entries.forEach { s ->
            Pressable({ onOpen(Screen.SectionList(s)) }, Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(T.rCard))
                        .background(T.surface)
                        .padding(T.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardIcon(sectionIcon(s))
                    Spacer(Modifier.width(T.md))
                    Q(s.title, Type.heading, T.text, 1, Modifier.weight(1f))
                    Q(db.count(s).toString(), Type.smallNum, T.text2)
                    Spacer(Modifier.width(T.sm))
                    QIcon(Ic.chevronRight, size = 20.dp, tint = T.text3)
                }
            }
            Spacer(Modifier.height(T.md))
        }

        // --- пузырь -------------------------------------------------------
        Spacer(Modifier.height(T.lg))
        Q("Пузырь поверх приложений", Type.caption, T.text3)
        Spacer(Modifier.height(T.sm))
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Q("Быстрый доступ", Type.heading, T.text)
                    Q(
                        if (bubbleOn) "Висит поверх всего, таскается пальцем" else "Выключен",
                        Type.small, T.text2
                    )
                }
                Toggle(bubbleOn) { value ->
                    if (value && !BubbleService.canDraw(ctx)) { askOverlay(); return@Toggle }
                    bubbleOn = value
                    Store.bubbleEnabled = value
                    if (value) {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        BubbleService.start(ctx)
                    } else BubbleService.stop(ctx)
                }
            }

            if (!granted) {
                Spacer(Modifier.height(T.md))
                Q("Нужно разрешение «Поверх других приложений».", Type.small, T.warning.ink)
                Spacer(Modifier.height(T.sm))
                GhostButton("Дать разрешение", { askOverlay() }, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(T.lg))
            Hairline()
            Spacer(Modifier.height(T.md))
            Gesture("Тап", "список последнего раздела")
            Gesture("Потянуть", "колесо разделов")
            Gesture("Потянуть дальше", "сразу новая запись")
            Gesture("Долгое нажатие", "пузырь отрывается")
        }

        // --- сообщения ----------------------------------------------------
        Spacer(Modifier.height(T.xl))
        Q("Сообщения сотрудникам", Type.caption, T.text3)
        Spacer(Modifier.height(T.sm))
        Panel {
            Field(
                "Обращение", greeting, { greeting = it; Store.greeting = it },
                placeholder = "{Имя}, — или оставь пустым",
                hint = "Подставляется в начало задачи. Пусто — без обращения."
            )
            Spacer(Modifier.height(T.md))
            GhostButton(
                "Шаблоны сообщений (${db.templates.size})",
                { ctx.startActivity(SheetActivity.templates(ctx)) },
                Modifier.fillMaxWidth()
            )
        }

        // --- таблица ------------------------------------------------------
        Spacer(Modifier.height(T.xl))
        Q("Google-таблица", Type.caption, T.text3)
        Spacer(Modifier.height(T.sm))
        Panel {
            Q(
                "Связь двусторонняя: правки из телефона уезжают в таблицу, правки в таблице приезжают обратно. Спор решается по времени правки.",
                Type.small, T.text2
            )
            Spacer(Modifier.height(T.md))
            Field(
                "Ссылка веб-приложения Apps Script", url,
                { url = it; Store.sheetsUrl = it.trim() },
                placeholder = "https://script.google.com/macros/s/.../exec"
            )
            Spacer(Modifier.height(T.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Q("Обмениваться самому", Type.small, T.text)
                    Q("после правки и при открытии пузыря", Type.caption, T.text3)
                }
                Toggle(auto) { auto = it; Store.autoSync = it }
            }
            Spacer(Modifier.height(T.md))
            PrimaryButton(
                if (syncing) "Обмениваюсь…" else "Синхронизировать сейчас",
                onClick = {
                    if (url.isBlank()) {
                        Toast.makeText(ctx, "Сначала вставь ссылку", Toast.LENGTH_SHORT).show()
                        return@PrimaryButton
                    }
                    syncing = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { Sync.run() }
                        syncing = false
                        lastSync = Store.lastSync
                        Toast.makeText(
                            ctx,
                            result.fold({ it.text }, { "Не вышло: ${it.message}" }),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                enabled = !syncing
            )
            if (lastSync.isNotBlank()) {
                Spacer(Modifier.height(T.sm))
                Q("Последний обмен: $lastSync", Type.caption, T.text3)
            }
        }

        // --- копия --------------------------------------------------------
        Spacer(Modifier.height(T.xl))
        Q("Резервная копия", Type.caption, T.text3)
        Spacer(Modifier.height(T.sm))
        Panel {
            Q(
                "Реестр хранится на телефоне. Копия — единственный способ не потерять его вместе с устройством.",
                Type.small, T.text2
            )
            Spacer(Modifier.height(T.md))
            PrimaryButton("Создать копию", { saveLauncher.launch(Backup.suggestedName()) })
            Spacer(Modifier.height(T.sm))
            GhostButton(
                "Восстановить из копии",
                { openLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                Modifier.fillMaxWidth()
            )
            if (lastBackup.isNotBlank()) {
                Spacer(Modifier.height(T.sm))
                Q("Последняя копия: $lastBackup", Type.caption, T.text3)
            }
            Spacer(Modifier.height(T.sm))
            GhostButton("Выгрузить CSV", { Export.share(ctx) }, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(T.xxl))
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(T.rCard)).background(T.surface).padding(T.lg),
        content = content
    )
}

@Composable
private fun Gesture(name: String, what: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Q(name, Type.small, T.text, 1, Modifier.width(132.dp))
        Q(what, Type.small, T.text2, 2, Modifier.weight(1f))
    }
}

@Composable
private fun Toggle(value: Boolean, onChange: (Boolean) -> Unit) {
    Pressable({ onChange(!value) }) {
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (value) T.accent.fill else T.muted.chip),
            contentAlignment = if (value) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White)
            )
        }
    }
}

// --- список раздела ------------------------------------------------------

@Composable
private fun SectionScreen(
    section: Section,
    db: Db,
    onBack: () -> Unit,
    onOpen: (Screen) -> Unit,
    onCreate: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = T.sm, vertical = T.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onBack)
            Q(section.title, Type.title, T.text, 1, Modifier.weight(1f))
            Pressable(onCreate) {
                Box(
                    Modifier.size(T.touchMin).clip(RoundedCornerShape(percent = 50)).background(T.accent.fill),
                    contentAlignment = Alignment.Center
                ) { QIcon(Ic.plus, size = 22.dp, tint = Color.White, stroke = 2f) }
            }
        }

        if (db.count(section) == 0) {
            EmptyState(
                text = "${section.title} — пусто",
                hint = "Здесь появятся записи, которые ты заведёшь.",
                action = "Добавить ${section.one.lowercase()}",
                onAction = onCreate
            )
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = T.lg, end = T.lg, bottom = T.xxl),
            verticalArrangement = Arrangement.spacedBy(T.md)
        ) {
            when (section) {
                Section.SITES -> items(db.liveSites, key = { it.id }) { s ->
                    SiteRow(s, db) { onOpen(Screen.Card(Section.SITES, s.id)) }
                }

                Section.CONTRACTS -> items(db.liveContracts, key = { it.id }) { c ->
                    ContractRow(c, db) { onOpen(Screen.Card(Section.CONTRACTS, c.id)) }
                }

                Section.CUSTOMERS -> items(db.liveCustomers, key = { it.id }) { p ->
                    PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.customers) {
                        onOpen(Screen.Card(Section.CUSTOMERS, p.id))
                    }
                }

                Section.STAFF -> items(db.liveEmployees, key = { it.id }) { e ->
                    EmployeeRow(e) { onOpen(Screen.Card(Section.STAFF, e.id)) }
                }
            }
        }
    }
}

// --- карточка ------------------------------------------------------------

@Composable
private fun CardScreen(
    section: Section,
    id: String,
    db: Db,
    onBack: () -> Unit,
    onOpen: (Screen) -> Unit
) {
    val ctx = LocalContext.current
    val title = when (section) {
        Section.SITES -> db.site(id)?.name
        Section.CONTRACTS -> db.contract(id)?.code
        Section.CUSTOMERS -> db.customer(id)?.name
        Section.STAFF -> db.employee(id)?.name
    } ?: section.one

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = T.sm, vertical = T.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onBack)
            Q(title, Type.heading, T.text, 1, Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (section) {
                Section.SITES -> db.site(id)?.let { site ->
                    SiteCard(
                        site, db,
                        onEdit = { onOpen(Screen.Form(Section.SITES, id)) },
                        onAddContract = { onOpen(Screen.Form(Section.CONTRACTS, null)) },
                        onOpenContract = { onOpen(Screen.Card(Section.CONTRACTS, it)) }
                    )
                }

                Section.CONTRACTS -> db.contract(id)?.let {
                    ContractCard(it, db, onEdit = { onOpen(Screen.Form(Section.CONTRACTS, id)) })
                }

                Section.CUSTOMERS -> db.customer(id)?.let {
                    PartyCard(
                        it, db,
                        onEdit = { onOpen(Screen.Form(Section.CUSTOMERS, id)) },
                        onOpenSite = { onOpen(Screen.Card(Section.SITES, it)) }
                    )
                }

                Section.STAFF -> db.employee(id)?.let { e ->
                    EmployeeCard(
                        e, db,
                        onEdit = { onOpen(Screen.Form(Section.STAFF, id)) },
                        onTask = { ctx.startActivity(SheetActivity.task(ctx, e.id)) }
                    )
                }
            }
        }
    }
}

// --- форма ---------------------------------------------------------------

@Composable
private fun FormScreen(
    section: Section,
    id: String?,
    db: Db,
    onPick: (PickRequest) -> Unit,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    onDeleted: () -> Unit
) {
    // Заготовка создаётся один раз: Site() и Party() выдают новый
    // идентификатор при каждом вызове, и ключ состояния менялся бы
    // на каждой перерисовке, стирая всё набранное.
    val key = section to id
    when (section) {
        Section.SITES -> {
            val initial = remember(key) { db.site(id) ?: Site() }
            SiteForm(
                initial = initial, db = db, onPick = onPick,
                onDone = { Store.upsertSite(it); onSaved() },
                onCancel = onCancel,
                onDelete = if (id != null) ({ Store.deleteSite(id); onDeleted() }) else null
            )
        }

        Section.CONTRACTS -> {
            val initial = remember(key) { db.contract(id) ?: Contract() }
            ContractForm(
                initial = initial, db = db, onPick = onPick,
                onDone = { Store.upsertContract(it); onSaved() },
                onCancel = onCancel,
                onDelete = if (id != null) ({ Store.deleteContract(id); onDeleted() }) else null
            )
        }

        Section.CUSTOMERS -> {
            val initial = remember(key) { db.customer(id) ?: Party() }
            PartyForm(
                initial = initial,
                title = if (id == null) "Новый заказчик" else "Заказчик",
                onDone = { Store.upsertCustomer(it); onSaved() },
                onCancel = onCancel,
                onDelete = if (id != null) ({ Store.deleteCustomer(id); onDeleted() }) else null
            )
        }

        Section.STAFF -> {
            val initial = remember(key) { db.employee(id) ?: Employee() }
            EmployeeForm(
                initial = initial, db = db, onPick = onPick,
                onDone = { Store.upsertEmployee(it); onSaved() },
                onCancel = onCancel,
                onDelete = if (id != null) ({ Store.deleteEmployee(id); onDeleted() }) else null
            )
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Pressable(onBack) {
        Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
            QIcon(Ic.chevronLeft, size = 24.dp, tint = T.text)
        }
    }
}

private fun sectionIcon(s: Section): String = when (s) {
    Section.SITES -> Ic.sites
    Section.CONTRACTS -> Ic.contracts
    Section.CUSTOMERS -> Ic.customers
    Section.STAFF -> Ic.staff
}
