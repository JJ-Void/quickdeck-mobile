package ru.quickdeck.mobile.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import ru.quickdeck.mobile.core.*
import ru.quickdeck.mobile.data.*
import ru.quickdeck.mobile.ui.*

/** Уровень колеса: разделы или записи внутри раздела. */
private sealed interface Level {
    data object Sections : Level
    data class Items(val section: Section) : Level
}

/** Окно поверх колеса. Каждое следующее накрывает предыдущее. */
private sealed interface Layer {
    val key: String

    data class CardSite(val id: String) : Layer {
        override val key get() = "site-$id"
    }

    data class CardContract(val id: String) : Layer {
        override val key get() = "contract-$id"
    }

    data class CardParty(val id: String, val customer: Boolean) : Layer {
        override val key get() = "party-$id"
    }

    data class FormSite(val value: Site) : Layer {
        override val key get() = "form-site-${value.id}"
    }

    data class FormContract(val value: Contract) : Layer {
        override val key get() = "form-contract-${value.id}"
    }

    data class FormParty(val value: Party, val customer: Boolean) : Layer {
        override val key get() = "form-party-${value.id}"
    }

    data class Pick(val request: PickRequest, val stamp: Long) : Layer {
        override val key get() = "pick-$stamp"
    }
}

@Composable
fun OverlayRoot(
    handle: OverlayHandle,
    fromRight: Boolean,
    registerBack: (() -> Unit) -> Unit
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val config = LocalConfiguration.current
    val screenH = config.screenHeightDp * density
    val screenW = config.screenWidthDp * density
    val g = remember(density) { WheelGeometry(density) }
    val db by Store.db.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf<Level>(Level.Sections) }
    var pivotY by remember { mutableFloatStateOf(screenH / 2f) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    var baseOffset by remember { mutableFloatStateOf(0f) }
    var virtual by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableStateOf(WheelMode.CANCEL) }
    var createArmed by remember { mutableStateOf(false) }
    val layers = remember { mutableStateListOf<Layer>() }

    val items = remember(level, db) { wheelItems(level, db) }
    val itemsLive = rememberUpdatedState(items)
    val dbLive = rememberUpdatedState(db)
    val createLabel = remember(level) {
        val s = (level as? Level.Items)?.section
        if (s != null) "Добавить · ${s.one.lowercase()}" else "Добавить"
    }

    fun close() {
        layers.clear()
        level = Level.Sections
        active = false
        expanded = false
        handle.collapse()
    }

    fun openLayer(layer: Layer) {
        layers.add(layer)
        val needsKeyboard = layer is Layer.FormSite || layer is Layer.FormContract ||
            layer is Layer.FormParty || layer is Layer.Pick
        if (needsKeyboard) handle.focusable(true)
    }

    fun popLayer() {
        if (layers.isNotEmpty()) layers.removeAt(layers.size - 1)
        val top = layers.lastOrNull()
        val needsKeyboard = top is Layer.FormSite || top is Layer.FormContract ||
            top is Layer.FormParty || top is Layer.Pick
        handle.focusable(needsKeyboard)
    }

    SideEffect {
        registerBack {
            when {
                layers.isNotEmpty() -> popLayer()
                level is Level.Items -> level = Level.Sections
                else -> close()
            }
        }
    }

    // подсказка пальцу: короткий отклик на смену пункта и на появление плюса
    var lastIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(virtual.roundToInt(), active) {
        val idx = virtual.roundToInt()
        if (active && idx != lastIndex) {
            lastIndex = idx
            buzz(ctx, 8)
        }
    }
    LaunchedEffect(mode) {
        createArmed = false
        if (mode == WheelMode.CREATE) {
            delay(160) // «зафиксировать» — короткая задержка от случайного перелёта
            createArmed = true
            buzz(ctx, 18)
        }
    }

    // у верхнего и нижнего края лист крутится сам
    LaunchedEffect(active) {
        while (active) {
            val top = finger.y < g.autoScrollZone
            val bottom = finger.y > screenH - g.autoScrollZone
            if (top || bottom) {
                baseOffset = (baseOffset + if (bottom) 0.06f else -0.06f)
                    .coerceIn(-(itemsLive.value.size.toFloat()), itemsLive.value.size.toFloat())
            }
            delay(16)
        }
    }

    fun onRelease() {
        active = false
        val live = itemsLive.value
        val idx = virtual.roundToInt().coerceIn(0, (live.size - 1).coerceAtLeast(0))
        when (mode) {
            WheelMode.CANCEL -> {
                if (level is Level.Items) level = Level.Sections else close()
            }

            WheelMode.BROWSE -> when (val lv = level) {
                is Level.Sections -> {
                    baseOffset = 0f
                    level = Level.Items(Section.entries[idx])
                }

                is Level.Items -> openCard(lv.section, idx, dbLive.value)?.let { openLayer(it) }
            }

            WheelMode.CREATE -> {
                val section = when (val lv = level) {
                    is Level.Sections -> Section.entries[idx]
                    is Level.Items -> lv.section
                }
                openLayer(newForm(section))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(fromRight) {
                detectDragGestures(
                    onStart = { offset ->
                        if (layers.isNotEmpty()) return@detectDragGestures

                        if (!expanded) {
                            expanded = true
                            handle.expand()
                        }
                        pivotY = offset.y
                        baseOffset = if (level is Level.Sections) 0f else baseOffset
                        finger = offset
                        active = true

                        val (v0, m0) = selectionFor(itemsLive.value.size, pivotY, baseOffset, finger, fromRight, screenW, g)
                        virtual = v0
                        mode = m0
                    },
                    onDrag = { change, _ ->
                        if (layers.isNotEmpty() || !active) return@detectDragGestures
                        change.consume()

                        finger = change.position
                        val (v, m) = selectionFor(itemsLive.value.size, pivotY, baseOffset, finger, fromRight, screenW, g)
                        virtual = v
                        mode = m
                    },
                    onDragEnd = {
                        if (active) onRelease()
                    },
                    onDragCancel = {
                        if (active) {
                            active = false
                            close()
                        }
                    }
                )
            }
    ) {
        if (!expanded) {
            EdgeHint(fromRight)
        } else {
            val dim by animateFloatAsState(
                targetValue = if (layers.isEmpty()) 0.58f else 0.72f,
                animationSpec = tween(T.MS_STATE, easing = T.curve),
                label = "dim"
            )
            Box(Modifier.fillMaxSize().background(Color(0xFF0A0C10).copy(alpha = dim)))

            val back = layers.size
            Box(
                Modifier
                    .fillMaxSize()
                    .scale(1f - 0.03f * back.coerceAtMost(2))
            ) {
                Wheel(
                    items = items,
                    title = when (val lv = level) {
                        is Level.Sections -> "Реестр"
                        is Level.Items -> lv.section.title
                    },
                    virtual = virtual,
                    mode = if (active) mode else WheelMode.BROWSE,
                    createArmed = createArmed,
                    pivotY = pivotY,
                    fromRight = fromRight,
                    createLabel = createLabel
                )
            }

            if (layers.isEmpty()) {
                BottomHint(
                    text = when (level) {
                        is Level.Sections -> "Веди пальцем — выбор. Дальше от края — плюс."
                        is Level.Items -> "Назад — к самому краю. Дальше от края — плюс."
                    },
                    onClose = { close() }
                )
            }
        }

        // Стопка окон: каждое следующее накрывает предыдущее.
        layers.forEachIndexed { index, layer ->
            key(layer.key) {
                val depth = layers.size - 1 - index
                LayerSheet(depth = depth) {
                    LayerContent(
                        layer = layer,
                        db = db,
                        onPop = { popLayer() },
                        onPick = { req -> openLayer(Layer.Pick(req, System.currentTimeMillis())) },
                        openLayer = { openLayer(it) }
                    )
                }
            }
        }
    }
}

// --- содержимое окон ----------------------------------------------------

@Composable
private fun LayerContent(
    layer: Layer,
    db: Db,
    onPop: () -> Unit,
    onPick: (PickRequest) -> Unit,
    openLayer: (Layer) -> Unit
) {
    when (layer) {
        is Layer.CardSite -> db.site(layer.id)?.let { site ->
            SiteCard(
                site = site,
                db = db,
                onEdit = { openLayer(Layer.FormSite(site)) },
                onAddContract = {
                    openLayer(
                        Layer.FormContract(
                            Contract(siteId = site.id, customerId = site.customerId, status = Status.DRAFT)
                        )
                    )
                }
            )
        }

        is Layer.CardContract -> db.contracts.firstOrNull { it.id == layer.id }?.let { c ->
            ContractCard(c, db, onEdit = { openLayer(Layer.FormContract(c)) })
        }

        is Layer.CardParty -> {
            val list = if (layer.customer) db.customers else db.contractors
            list.firstOrNull { it.id == layer.id }?.let { p ->
                PartyCard(p, db, layer.customer, onEdit = { openLayer(Layer.FormParty(p, layer.customer)) })
            }
        }

        is Layer.FormSite -> SiteForm(
            initial = layer.value,
            db = db,
            onPick = onPick,
            onDone = { Store.upsertSite(it); onPop() },
            onCancel = onPop
        )

        is Layer.FormContract -> ContractForm(
            initial = layer.value,
            db = db,
            onPick = onPick,
            onDone = { Store.upsertContract(it); onPop() },
            onCancel = onPop
        )

        is Layer.FormParty -> PartyForm(
            initial = layer.value,
            title = if (layer.customer) "Заказчик" else "Исполнитель",
            onDone = {
                if (layer.customer) Store.upsertCustomer(it) else Store.upsertContractor(it)
                onPop()
            },
            onCancel = onPop
        )

        is Layer.Pick -> PickList(
            request = layer.request,
            db = db,
            onDone = onPop,
            onCreate = { openLayer(it) }
        )
    }
}

@Composable
private fun PickList(
    request: PickRequest,
    db: Db,
    onDone: () -> Unit,
    onCreate: (Layer) -> Unit
) {
    val title: String
    val rows: @Composable () -> Unit

    when (request) {
        is PickRequest.Customer -> {
            title = "Заказчик"
            rows = {
                Column {
                    db.customers.forEach { p ->
                        PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.customers) {
                            request.onPick(p); onDone()
                        }
                        Spacer(Modifier.height(T.sm))
                    }
                }
            }
        }

        is PickRequest.Contractor -> {
            title = "Исполнитель"
            rows = {
                Column {
                    db.contractors.forEach { p ->
                        PartyRow(p, p.inn.takeIf { it.isNotBlank() }?.let { "ИНН $it" }, Ic.contractors) {
                            request.onPick(p); onDone()
                        }
                        Spacer(Modifier.height(T.sm))
                    }
                }
            }
        }

        is PickRequest.SitePick -> {
            title = "Объект"
            rows = {
                Column {
                    db.sites.forEach { s ->
                        SiteRow(s, db) { request.onPick(s); onDone() }
                        Spacer(Modifier.height(T.sm))
                    }
                }
            }
        }
    }

    val empty = when (request) {
        is PickRequest.Customer -> db.customers.isEmpty()
        is PickRequest.Contractor -> db.contractors.isEmpty()
        is PickRequest.SitePick -> db.sites.isEmpty()
    }

    Column(Modifier.fillMaxWidth().padding(T.lg)) {
        Q(title, Type.title, T.text)
        Spacer(Modifier.height(T.lg))
        if (empty) {
            EmptyState(
                text = "Здесь пока пусто",
                hint = "Сначала заведи запись — потом она появится в выборе.",
                action = "Создать",
                onAction = {
                    when (request) {
                        is PickRequest.Customer -> onCreate(Layer.FormParty(Party(), true))
                        is PickRequest.Contractor -> onCreate(Layer.FormParty(Party(), false))
                        is PickRequest.SitePick -> onCreate(Layer.FormSite(Site()))
                    }
                }
            )
        } else {
            rows()
            Spacer(Modifier.height(T.lg))
            GhostButton("Отмена", onDone, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun EmptyState(text: String, hint: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = T.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        QIcon(Ic.layers, size = 48.dp, tint = T.text3, stroke = 1.5f)
        Spacer(Modifier.height(T.md))
        Q(text, Type.heading, T.text2)
        Spacer(Modifier.height(T.xs))
        Q(hint, Type.small, T.text3)
