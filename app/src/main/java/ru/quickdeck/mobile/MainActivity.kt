package ru.quickdeck.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.quickdeck.mobile.core.*
import ru.quickdeck.mobile.data.*
import ru.quickdeck.mobile.overlay.EdgeService
import ru.quickdeck.mobile.overlay.EmptyState
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
    data class CardSite(val id: String) : Screen
    data class CardContract(val id: String) : Screen
    data class CardParty(val id: String, val customer: Boolean) : Screen
    data class FormSite(val value: Site) : Screen
    data class FormContract(val value: Contract) : Screen
    data class FormParty(val value: Party, val customer: Boolean) : Screen
    data class Pick(val request: PickRequest, val stamp: Long) : Screen
}

@Composable
private fun AppRoot() {
    val db by Store.db.collectAsState()
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = stack.last()

    fun push(s: Screen) = stack.add(s)
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(T.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        when (val s = current) {
            is Screen.Home -> HomeScreen(db) { push(it) }

            is Screen.SectionList -> SectionScreen(
                section = s.section,
                db = db,
                onBack = { pop() },
                onOpen = { push(it) },
                onCreate = { push(newForm(s.section)) }
            )

            is Screen.CardSite -> db.site(s.id)?.let { site ->
                Scaffold(title = site.name, onBack = { pop() }) {
                    SiteCard(site, db,
                        onEdit = { push(Screen.FormSite(site)) },
                        onAddContract = {
                            push(Screen.FormContract(Contract(siteId = site.id, customerId = site.customerId)))
                        }
                    )
                }
            } ?: run { pop() }

            is Screen.CardContract -> db.contracts.firstOrNull { it.id == s.id }?.let { c ->
                Scaffold(title = "Договор ${c.number}", onBack = { pop() }) {
                    ContractCard(c, db, onEdit = { push(Screen.FormContract(c)) })
                }
            } ?: run { pop() }

            is Screen.CardParty -> {
                val list = if (s.customer) db.customers else db.contractors
                list.firstOrNull { it.id == s.id }?.let { p ->
                    Scaffold(title = p.name, onBack = { pop() }) {
                        PartyCard(p, db, s.customer, onEdit = { push(Screen.FormParty(p, s.customer)) })
                    }
                } ?: run { pop() }
            }

            is Screen.FormSite -> SiteForm(
                initial = s.value, db = db,
                onPick = { push(Screen.Pick(it, System.currentTimeMillis())) },
                onDone = { Store.upsertSite(it); pop() },
                onCancel = { pop() }
            )

            is Screen.FormContract -> ContractForm(
                initial = s.value, db = db,
                onPick = { push(Screen.Pick(it, System.currentTimeMillis())) },
                onDone = { Store.upsertContract(it); pop() },
                onCancel = { pop() }
            )

            is Screen.FormParty -> PartyForm(
                initial = s.value,
                title = if (s.customer) "Заказчик" else "Исполнитель",
                onDone = {
                    if (s.customer) Store.upsertCustomer(it) else Store.upsertContractor(it)
                    pop()
                },
                onCancel = { pop() }
            )

            is Screen.Pick -> PickScreen(s.request, db, onDone = { pop() })
        }
    }
}

@Composable
private fun Scaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = T.sm, vertical = T.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pressable(onBack) {
                Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                    QIcon(Ic.chevronLeft, size = 24.dp, tint = T.text)
                }
            }
            Q(title, Type.heading, T.text, 1, Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            content()
        }
    }
}

@Composable
private fun HomeScreen(db: Db, onOpen: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var edgeOn by remember { mutableStateOf(Store.edgeEnabled) }
    var right by remember { mutableStateOf(Store.edgeRight) }
    var url by remember { mutableStateOf(Store.sheetsUrl) }
    var syncing by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(EdgeService.canDraw(ctx)) }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val overlayLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        granted = EdgeService.canDraw(ctx)
        if (granted && edgeOn) EdgeService.start(ctx)
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

        // Полоска у края
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(T.rCard))
                .background(T.surface)
                .padding(T.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Q("Полоска у края", Type.heading, T.text)
                    Q(
                        if (edgeOn) "Свайп от края открывает колесо" else "Выключена",
                        Type.small, T.text2
                    )
                }
                Toggle(edgeOn) { value ->
                    if (value && !EdgeService.canDraw(ctx)) {
                        overlayLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            )
                        )
                        return@Toggle
                    }
                    edgeOn = value
                    Store.edgeEnabled = value
                    if (value) {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        EdgeService.start(ctx)
                    } else {
                        EdgeService.stop(ctx)
                    }
                }
            }

            if (!granted) {
                Spacer(Modifier.height(T.md))
                Q(
                    "Нужно разрешение «Поверх других приложений» — без него полоска не появится.",
                    Type.small, T.warning.ink
                )
                Spacer(Modifier.height(T.sm))
                GhostButton("Дать разрешение", {
                    overlayLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(T.lg))
            Q("Сторона", Type.caption, T.text3)
            Spacer(Modifier.height(T.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(T.sm)) {
                SideChip("Слева", !right) {
                    right = false; Store.edgeRight = false
                    if (edgeOn) { EdgeService.stop(ctx); EdgeService.start(ctx) }
                }
                SideChip("Справа", right) {
                    right = true; Store.edgeRight = true
                    if (edgeOn) { EdgeService.stop(ctx); EdgeService.start(ctx) }
                }
            }
        }

        Spacer(Modifier.height(T.xl))
        Q("Разделы", Type.caption, T.text3)
        Spacer(Modifier.height(T.sm))

        Section.entries.forEach { s ->
            val count = when (s) {
                Section.CUSTOMERS -> db.customers.size
                Section.SITES -> db.sites.size
                Section.CONTRACTS -> db.contracts.size
                Section.CONTRACTORS -> db.contractors.size
            }
            Pressable({ onOpen(Screen.SectionList(s)) }, Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(T.rCard))
                        .background(T.surface)
                        .padding(T.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardIcon(
                        when (s) {
                            Section.CUSTOMERS -> Ic.customers
                            Section.SITES -> Ic.sites
                            Section.CONTRACTS -> Ic.contracts
                            Section.CONTRACTORS -> Ic.contractors
                        }
                    )
                    Spacer(Modifier.width(T.md))
                    Q(s.title, Type.heading, T.text, 1, Modifier.weight(1f))
                    Q(count.toString(), Type.smallNum, T.text2)
                    Spacer(Modifier.width(T.sm))
                    QIcon(Ic.chevronRight, size = 20.dp, tint = T.text3)
                }
            }
            Spacer(Modifier.height(T.md))
        }

        Spacer(Modifier.height(T.lg))
        Q("Архив в Google-таблице", Type.caption, T.text3)
        Spacer(Modifier.height(T.sm))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(T.rCard))
                .background(T.surface)
                .padding(T.lg)
        ) {
            Field(
                "Ссылка веб-приложения Apps Script",
                url,
                { url = it; Store.sheetsUrl = it },
                placeholder = "https://script.google.com/macros/s/.../exec"
            )
            Spacer(Modifier.height(T.md))
            PrimaryButton(
                if (syncing) "Отправляю…" else "Отправить в таблицу",
                onClick = {
                    if (url.isBlank()) {
                        Toast.makeText(ctx, "Сначала вставь ссылку", Toast.LENGTH_SHORT).show()
                        return@PrimaryButton
                    }
                    syncing = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { Export.toSheets(url, Store.raw()) }
                        syncing = false
                        Toast.makeText(
                            ctx,
                            result.fold({ "Таблица обновлена" }, { "Не вышло: ${it.message}" }),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                enabled = !syncing
            )
            if (Store.lastSync.isNotBlank()) {
                Spacer(Modifier.height(T.sm))
                Q("Последняя отправка: ${Store.lastSync}", Type.caption, T.text3)
            }
            Spacer(Modifier.height(T.sm))
            GhostButton("Выгрузить CSV и копию", { Export.share(ctx) }, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(T.xxl))
    }
}

@Composable
private fun SideChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Pressable(onClick) {
        Box(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(if (selected) T.accent.chip else T.bg)
                .padding(horizontal = T.lg, vertical = T.md),
            contentAlignment = Alignment.Center
        ) { Q(text, Type.small, if (selected) T.accent.ink else T.text2) }
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
                    .background(androidx.compose.ui.graphics.Color.White)
            )
        }
    }
}

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
            Pressable(onBack) {
                Box(Modifier.size(T.touchMin), contentAlignment = Alignment.Center) {
                    QIcon(Ic.chevronLeft, size = 24.dp, tint = T.text)
                }
            }
            Q(section.title, Type.title, T.text, 1, Modifier.weight(1f))
            Pressable(onCreate) {
                Box(
                    Modifier
                        .size(T.touchMin)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(T.accent.fill),
                    contentAlignment = Alignment.Center
                ) { QIcon(Ic.plus, size = 22.dp, tint = androidx.compose.ui.graphics.Color.White, stroke = 2f) }
            }
        }

        val empty = when (section) {
            Section.CUSTOMERS -> db.customers.isEmpty()
            Section.CONTRACTORS -> db.contractors.isEmpty()
            Section.SITES -> db.sites.isEmpty()
            Section.CONTRACTS -> db.contracts.isEmpty()
        }

        if (empty) {
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
                Section.SITES -> items(db.sites, key = { it.id }) { s ->
                    SiteRow(s, db) { onOpen(Screen.CardSite(s.id)) }
                }

                Section.CONTRACTS -> items(db.contracts, key = { it.id }) { c ->
                    ContractRow(c, db) { onOpen(Screen.CardContract(c.id)) }
                }

                Section.CUSTOMERS -> items(db.customers, key = { it.id }) { p ->
                    PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.customers) {
                        onOpen(Screen.CardParty(p.id, true))
                    }
                }

                Section.CONTRACTORS -> items(db.contractors, key = { it.id }) { p ->
                    PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.contractors) {
                        onOpen(Screen.CardParty(p.id, false))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickScreen(request: PickRequest, db: Db, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(T.lg)) {
        Q(
            when (request) {
                is PickRequest.Customer -> "Выбери заказчика"
                is PickRequest.Contractor -> "Выбери исполнителя"
                is PickRequest.SitePick -> "Выбери объект"
            },
            Type.title, T.text
        )
        Spacer(Modifier.height(T.lg))
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(T.md)
        ) {
            when (request) {
                is PickRequest.Customer -> items(db.customers, key = { it.id }) { p ->
                    PartyRow(p, null, Ic.customers) { request.onPick(p); onDone() }
                }

                is PickRequest.Contractor -> items(db.contractors, key = { it.id }) { p ->
                    PartyRow(p, null, Ic.contractors) { request.onPick(p); onDone() }
                }

                is PickRequest.SitePick -> items(db.sites, key = { it.id }) { s ->
                    SiteRow(s, db) { request.onPick(s); onDone() }
                }
            }
        }
        Spacer(Modifier.height(T.md))
        GhostButton("Отмена", onDone, Modifier.fillMaxWidth())
    }
}

private fun newForm(section: Section): Screen = when (section) {
    Section.CUSTOMERS -> Screen.FormParty(Party(), true)
    Section.CONTRACTORS -> Screen.FormParty(Party(), false)
    Section.SITES -> Screen.FormSite(Site())
    Section.CONTRACTS -> Screen.FormContract(Contract())
}
