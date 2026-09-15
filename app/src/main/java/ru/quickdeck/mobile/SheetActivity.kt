package ru.quickdeck.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * Раньше формы рисовались внутри окна службы. Чтобы туда пустить клавиатуру,
 * окно приходилось делать фокусируемым, и оно начинало перехватывать весь
 * экран: ни «назад», ни промах мимо кнопки уже не помогали. Обычная Activity
 * получает всё это даром — и клавиатуру, и «назад», и жесты, и прокрутку.
 */
class SheetActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SECTION = "section"
        private const val EXTRA_ID = "id"

        private const val MODE_FORM = "form"
        private const val MODE_SEARCH = "search"

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_FORM
        val section = runCatching {
            Section.valueOf(intent.getStringExtra(EXTRA_SECTION) ?: Section.SITES.name)
        }.getOrDefault(Section.SITES)
        val id = intent.getStringExtra(EXTRA_ID)

        setContent {
            SheetRoot(
                start = if (mode == MODE_SEARCH) Step.Search else Step.Form(section, id),
                onDone = { finish() }
            )
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}

/** Шаги внутри листа. Стек короткий: форма, выбор из реестра, поиск. */
private sealed interface Step {
    data class Form(val section: Section, val id: String?) : Step
    data class Pick(val request: PickRequest, val stamp: Long) : Step
    data object Search : Step
}

@Composable
private fun SheetRoot(start: Step, onDone: () -> Unit) {
    val db by Store.db.collectAsState()
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf(start) }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.size - 1) else onDone()
    }

    // После правки сразу отправляем в таблицу, чтобы она не расходилась.
    fun saved() {
        if (Store.autoSync && Store.syncConfigured) {
            scope.launch { withContext(Dispatchers.IO) { Sync.run() } }
        }
        pop()
    }

    BackHandler { pop() }

    Box(
        Modifier
            .fillMaxSize()
            .background(T.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone
            ),
        contentAlignment = Alignment.BottomCenter
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
                ) { /* тап по листу не закрывает его */ }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
        ) {
            Grip()
            when (val step = stack.last()) {
                is Step.Form -> FormStep(
                    step = step,
                    db = db,
                    onPick = { stack.add(Step.Pick(it, System.currentTimeMillis())) },
                    onSaved = { saved() },
                    onCancel = { pop() },
                    onDeleted = { saved() }
                )

                is Step.Pick -> PickStep(
                    request = step.request,
                    db = db,
                    onDone = { pop() },
                    onCreate = { section -> stack.add(Step.Form(section, null)) }
                )

                Step.Search -> SearchStep(
                    db = db,
                    onOpen = { ref -> stack.add(Step.Form(ref.first, ref.second)) },
                    onClose = onDone
                )
            }
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
                .background(T.hairline)
        )
    }
}

// --- форма ---------------------------------------------------------------

@Composable
private fun ColumnScope.FormStep(
    step: Step.Form,
    db: Db,
    onPick: (PickRequest) -> Unit,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    onDeleted: () -> Unit
) {
    val id = step.id
    when (step.section) {
        Section.SITES -> SiteForm(
            initial = db.site(id) ?: Site(),
            db = db,
            onPick = onPick,
            onDone = { Store.upsertSite(it); onSaved() },
            onCancel = onCancel,
            onDelete = if (id != null) ({ Store.deleteSite(id); onDeleted() }) else null
        )

        Section.CONTRACTS -> ContractForm(
            initial = db.contract(id) ?: Contract(),
            db = db,
            onPick = onPick,
            onDone = { Store.upsertContract(it); onSaved() },
            onCancel = onCancel,
            onDelete = if (id != null) ({ Store.deleteContract(id); onDeleted() }) else null
        )

        Section.CUSTOMERS -> PartyForm(
            initial = db.customer(id) ?: Party(),
            title = if (id == null) "Новый заказчик" else "Заказчик",
            onDone = { Store.upsertCustomer(it); onSaved() },
            onCancel = onCancel,
            onDelete = if (id != null) ({ Store.deleteCustomer(id); onDeleted() }) else null
        )

        Section.CONTRACTORS -> PartyForm(
            initial = db.contractor(id) ?: Party(),
            title = if (id == null) "Новый исполнитель" else "Исполнитель",
            onDone = { Store.upsertContractor(it); onSaved() },
            onCancel = onCancel,
            onDelete = if (id != null) ({ Store.deleteContractor(id); onDeleted() }) else null
        )
    }
}

// --- выбор из реестра ----------------------------------------------------

@Composable
private fun ColumnScope.PickStep(
    request: PickRequest,
    db: Db,
    onDone: () -> Unit,
    onCreate: (Section) -> Unit
) {
    val title = when (request) {
        is PickRequest.Customer -> "Заказчик"
        is PickRequest.Contractor -> "Исполнитель"
        is PickRequest.SitePick -> "Объект"
    }
    val section = when (request) {
        is PickRequest.Customer -> Section.CUSTOMERS
        is PickRequest.Contractor -> Section.CONTRACTORS
        is PickRequest.SitePick -> Section.SITES
    }
    val empty = db.count(section) == 0

    Row(
        Modifier.fillMaxWidth().padding(start = T.lg, end = T.sm, top = T.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Q(title, Type.title, T.text, 1, Modifier.weight(1f))
        Pressable({ onCreate(section) }) {
            Box(
                Modifier
                    .size(T.touchMin)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(T.accent.fill),
                contentAlignment = Alignment.Center
            ) { QIcon(Ic.plus, size = 20.dp, tint = androidx.compose.ui.graphics.Color.White, stroke = 2f) }
        }
    }

    if (empty) {
        EmptyState(
            text = "Здесь пока пусто",
            hint = "Сначала заведи запись — потом она появится в выборе.",
            action = "Создать",
            onAction = { onCreate(section) }
        )
        Spacer(Modifier.height(T.md))
        GhostButton("Отмена", onDone, Modifier.fillMaxWidth().padding(horizontal = T.lg))
        Spacer(Modifier.height(T.lg))
        return
    }

    LazyColumn(
        Modifier.weight(1f, fill = false),
        contentPadding = PaddingValues(T.lg),
        verticalArrangement = Arrangement.spacedBy(T.sm)
    ) {
        when (request) {
            is PickRequest.Customer -> items(db.liveCustomers, key = { it.id }) { p ->
                PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.customers) {
                    request.onPick(p); onDone()
                }
            }

            is PickRequest.Contractor -> items(db.liveContractors, key = { it.id }) { p ->
                PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.contractors) {
                    request.onPick(p); onDone()
                }
            }

            is PickRequest.SitePick -> items(db.liveSites, key = { it.id }) { s ->
                SiteRow(s, db) { request.onPick(s); onDone() }
            }
        }
    }

    GhostButton("Отмена", onDone, Modifier.fillMaxWidth().padding(horizontal = T.lg))
    Spacer(Modifier.height(T.lg))
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
            it.name.lowercase().contains(q) || it.address.lowercase().contains(q)
        }
    }
    val contracts = remember(q, db) {
        if (q.isEmpty()) db.liveContracts else db.liveContracts.filter {
            it.number.lowercase().contains(q) ||
                (db.site(it.siteId)?.name?.lowercase()?.contains(q) ?: false)
        }
    }
    val parties = remember(q, db) {
        if (q.isEmpty()) emptyList() else (db.liveCustomers + db.liveContractors).filter {
            it.name.lowercase().contains(q) || it.inn.contains(q)
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
        Field("Что ищем", query, { query = it }, placeholder = "Цимлянская, 14, ЭнергоКомплекс")
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
            item { GroupLabel("Стороны") }
            items(parties, key = { "p" + it.id }) { p ->
                val isCustomer = db.customers.any { it.id == p.id }
                PartyRow(
                    p,
                    p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" },
                    if (isCustomer) Ic.customers else Ic.contractors
                ) {
                    onOpen((if (isCustomer) Section.CUSTOMERS else Section.CONTRACTORS) to p.id)
                }
            }
        }
        if (sites.isEmpty() && contracts.isEmpty() && parties.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = T.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Q("Ничего не нашлось", Type.heading, T.text2)
                }
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
