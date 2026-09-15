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
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
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
}

private sealed interface Screen {
    data object Home : Screen
    data class SectionList(val section: Section) : Screen
    data class Card(val section: Section, val id: String) : Screen
    data class Form(val section: Section, val id: String?) : Screen
    data class Pick(val request: PickRequest, val stamp: Long) : Screen
}

@Composable
private fun AppRoot() {
    val db by Store.db.collectAsState()
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }

    fun push(s: Screen) = stack.add(s)
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    fun afterSave() {
        if (Store.autoSync && Store.syncConfigured) {
            scope.launch { withContext(Dispatchers.IO) { Sync.run() } }
        }
        pop()
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    Box(
        Modifier
            .fillMaxSize()
            .background(T.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
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
                onEdit = { push(Screen.Form(s.section, s.id)) },
                onAddContract = { push(Screen.Form(Section.CONTRACTS, null)) }
            )

            is Screen.Form -> FormScreen(
                section = s.section,
                id = s.id,
                db = db,
                onPick = { push(Screen.Pick(it, System.currentTimeMillis())) },
                onSaved = { afterSave() },
                onCancel = { pop() },
                onDeleted = {
                    // после удаления возвращаемся мимо карточки — её больше нет
                    while (stack.size > 1 && stack.last() !is Screen.SectionList) {
                        stack.removeAt(stack.size - 1)
                    }
                    if (Store.autoSync && Store.syncConfigured) {
                        scope.launch { withContext(Dispatchers.IO) { Sync.run() } }
                    }
                }
            )

            is Screen.Pick -> PickScreen(
                request = s.request,
                db = db,
                onDone = { pop() },
                onCreate = { section -> push(Screen.Form(section, null)) }
            )
        }
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
    var syncing by remember { mutableStateOf(false) }
    var lastSync by remember { mutableStateOf(Store.lastSync) }
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

    fun askOverlay() {
        overlayLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = T.lg)
    ) {
        Spacer(Modifier.height(T.lg))
        Q("QuickDeck", Type.title, T.text)
        Q("Реестр под большой палец", Type.small, T.text2)
        Spacer(Modifier.height(T.xl))

        // --- пузырь -------------------------------------------------------
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Q("Пузырь поверх приложений", Type.heading, T.text)
                    Q(
                        if (bubbleOn) "Висит поверх всего, таскается пальцем" else "Выключен",
                        Type.small, T.text2
                    )
                }
                Toggle(bubbleOn) { value ->
                    if (value && !BubbleService.canDraw(ctx)) {
                        askOverlay()
                        return@Toggle
                    }
                    bubbleOn = value
                    Store.bubbleEnabled = value
                    if (value) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        BubbleService.start(ctx)
                    } else {
                        BubbleService.stop(ctx)
                    }
                }
            }

            if (!granted) {
                Spacer(Modifier.height(T.md))
                Q(
                    "Нужно разрешение «Поверх других приложений» — без него пузыря не будет.",
                    Type.small, T.warning.ink
                )
                Spacer(Modifier.height(T.sm))
                GhostButton("Дать разрешение", { askOverlay() }, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(T.lg))
            Hairline()
            Spacer(Modifier.height(T.md))
            Q("Как им пользоваться", Type.caption, T.text3)
            Spacer(Modifier.height(T.sm))
            Gesture("Тап", "открыть список последнего раздела")
            Gesture("Потянуть в сторону", "колесо разделов; отпустил — открылся")
            Gesture("Потянуть дальше", "вместо открытия — сразу новая запись")
            Gesture("Долгое нажатие", "пузырь отрывается, тащи куда удобно")
        }

        Spacer(Modifier.height(T.xl))
        Q("Разделы", Type.caption, T.text3)
        Spacer(Modifier.height(T.sm))

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

        // --- таблица ------------------------------------------------------
        Spacer(Modifier.height(T.lg))
        Q("Google-таблица", Type.caption, T.text3)
        Spacer(Modifier.height(T.sm))
        Panel {
            Q(
                "Связь двусторонняя: правки из телефона уезжают в таблицу, правки в таблице приезжают обратно. Спорные случаи решаются по времени правки.",
                Type.small, T.text2
            )
            Spacer(Modifier.height(T.md))
            Field(
                "Ссылка веб-приложения Apps Script",
                url,
                { url = it; Store.sheetsUrl = it.trim() },
                placeholder = "https://script.google.com/macros/s/.../exec"
            )
            Spacer(Modifier.height(T.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Q("Обмениваться самому", Type.small, T.text)
                    Q("после каждой правки и при открытии пузыря", Type.caption, T.text3)
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
            Spacer(Modifier.height(T.sm))
            GhostButton("Выгрузить CSV и копию", { Export.share(ctx) }, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(T.xxl))
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(T.rCard))
            .background(T.surface)
            .padding(T.lg),
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
                    Modifier
                        .size(T.touchMin)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(T.accent.fill),
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

                Section.CONTRACTORS -> items(db.liveContractors, key = { it.id }) { p ->
                    PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.contractors) {
                        onOpen(Screen.Card(Section.CONTRACTORS, p.id))
                    }
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
    onEdit: () -> Unit,
    onAddContract: () -> Unit
) {
    val title = when (section) {
        Section.SITES -> db.site(id)?.name
        Section.CONTRACTS -> db.contract(id)?.let { "Договор ${it.number}" }
        Section.CUSTOMERS -> db.customer(id)?.name
        Section.CONTRACTORS -> db.contractor(id)?.name
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
                Section.SITES -> db.site(id)?.let { SiteCard(it, db, onEdit, onAddContract) }
                Section.CONTRACTS -> db.contract(id)?.let { ContractCard(it, db, onEdit) }
                Section.CUSTOMERS -> db.customer(id)?.let { PartyCard(it, db, true, onEdit) }
                Section.CONTRACTORS -> db.contractor(id)?.let { PartyCard(it, db, false, onEdit) }
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
    val canDelete = id != null
    when (section) {
        Section.SITES -> SiteForm(
            initial = db.site(id) ?: Site(),
            db = db,
            onPick = onPick,
            onDone = { Store.upsertSite(it); onSaved() },
            onCancel = onCancel,
            onDelete = if (canDelete) ({ Store.deleteSite(id!!); onDeleted() }) else null
        )

        Section.CONTRACTS -> ContractForm(
            initial = db.contract(id) ?: Contract(),
            db = db,
            onPick = onPick,
            onDone = { Store.upsertContract(it); onSaved() },
            onCancel = onCancel,
            onDelete = if (canDelete) ({ Store.deleteContract(id!!); onDeleted() }) else null
        )

        Section.CUSTOMERS -> PartyForm(
            initial = db.customer(id) ?: Party(),
            title = if (id == null) "Новый заказчик" else "Заказчик",
            onDone = { Store.upsertCustomer(it); onSaved() },
            onCancel = onCancel,
            onDelete = if (canDelete) ({ Store.deleteCustomer(id!!); onDeleted() }) else null
        )

        Section.CONTRACTORS -> PartyForm(
            initial = db.contractor(id) ?: Party(),
            title = if (id == null) "Новый исполнитель" else "Исполнитель",
            onDone = { Store.upsertContractor(it); onSaved() },
            onCancel = onCancel,
            onDelete = if (canDelete) ({ Store.deleteContractor(id!!); onDeleted() }) else null
        )
    }
}

// --- выбор ---------------------------------------------------------------

@Composable
private fun PickScreen(
    request: PickRequest,
    db: Db,
    onDone: () -> Unit,
    onCreate: (Section) -> Unit
) {
    val section = when (request) {
        is PickRequest.Customer -> Section.CUSTOMERS
        is PickRequest.Contractor -> Section.CONTRACTORS
        is PickRequest.SitePick -> Section.SITES
    }

    Column(Modifier.fillMaxSize().padding(horizontal = T.lg)) {
        Spacer(Modifier.height(T.lg))
        Q("Выбери ${section.one.lowercase()}", Type.title, T.text)
        Spacer(Modifier.height(T.lg))

        if (db.count(section) == 0) {
            EmptyState(
                text = "Здесь пока пусто",
                hint = "Сначала заведи запись — потом она появится в выборе.",
                action = "Создать",
                onAction = { onCreate(section) }
            )
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(T.md)) {
                when (request) {
                    is PickRequest.Customer -> items(db.liveCustomers, key = { it.id }) { p ->
                        PartyRow(p, null, Ic.customers) { request.onPick(p); onDone() }
                    }

                    is PickRequest.Contractor -> items(db.liveContractors, key = { it.id }) { p ->
                        PartyRow(p, null, Ic.contractors) { request.onPick(p); onDone() }
                    }

                    is PickRequest.SitePick -> items(db.liveSites, key = { it.id }) { s ->
                        SiteRow(s, db) { request.onPick(s); onDone() }
                    }
                }
            }
        }

        Spacer(Modifier.height(T.md))
        GhostButton("Отмена", onDone, Modifier.fillMaxWidth())
        Spacer(Modifier.height(T.lg))
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
    Section.CONTRACTORS -> Ic.contractors
}
