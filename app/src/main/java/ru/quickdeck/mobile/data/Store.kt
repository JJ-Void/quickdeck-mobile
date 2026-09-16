package ru.quickdeck.mobile.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Весь реестр в одном JSON-файле. Базы нет и не нужно: записей сотни,
 * а файл легко выгрузить и слить с таблицей.
 *
 * Хранилище — единственное место, где проставляется updatedAt. Ни один экран
 * не пишет метку сам, иначе слияние однажды разъедется.
 */
object Store {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private lateinit var file: File
    private lateinit var prefs: SharedPreferences

    private val _db = MutableStateFlow(Db())
    val db: StateFlow<Db> get() = _db

    val refs: Refs get() = _db.value.refs
    val isReady: Boolean get() = ::file.isInitialized

    @Synchronized
    fun init(ctx: Context) {
        if (::file.isInitialized) return
        val app = ctx.applicationContext
        file = File(app.filesDir, "quickdeck.json")
        prefs = app.getSharedPreferences("quickdeck", Context.MODE_PRIVATE)
        _db.value = runCatching {
            // deduped() — на входе гасится эхо имени, приехавшее из таблицы.
            if (file.exists()) json.decodeFromString(Db.serializer(), file.readText()).deduped() else Db()
        }.getOrElse { Db() }
    }

    fun raw(): String = json.encodeToString(Db.serializer(), _db.value)

    fun parse(text: String): Db? =
        runCatching { json.decodeFromString(Db.serializer(), text).deduped() }.getOrNull()

    fun replaceAll(text: String): Boolean {
        val parsed = parse(text) ?: return false
        mutate { parsed }
        return true
    }

    @Synchronized
    private fun persist() {
        if (!::file.isInitialized) return
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Db.serializer(), _db.value))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    private fun mutate(block: (Db) -> Db) {
        _db.value = block(_db.value)
        persist()
    }

    // --- запись -----------------------------------------------------------
    // Каждая правка помечается временем. Это и есть вся механика обмена:
    // чья версия свежее, та и остаётся.

    fun upsertCustomer(p: Party) = mutate { d ->
        d.copy(customers = d.customers.putParty(p.copy(updatedAt = nowMs())))
    }

    fun upsertEmployee(e: Employee) = mutate { d ->
        d.copy(employees = d.employees.putEmployee(e.copy(updatedAt = nowMs())))
    }

    fun upsertSite(s: Site) = mutate { d ->
        d.copy(sites = d.sites.putSite(s.copy(updatedAt = nowMs())))
    }

    fun upsertContract(c: Contract) = mutate { d ->
        d.copy(contracts = d.contracts.putContract(c.copy(updatedAt = nowMs())))
    }

    /** Смена статуса — самое частое действие, поэтому отдельным входом. */
    fun setContractStatus(id: String, status: Status) = mutate { d ->
        val c = d.contracts.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(contracts = d.contracts.putContract(c.copy(status = status, updatedAt = nowMs())))
    }

    /** Готовность — отметка только для телефона, в таблице такого столбца нет. */
    fun setSiteProgress(id: String, percent: Int) = mutate { d ->
        val s = d.sites.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(sites = d.sites.putSite(s.copy(progress = percent.coerceIn(0, 100), updatedAt = nowMs())))
    }

    /** Отметить оплату по договору — второе по частоте действие после статуса. */
    fun setPaymentPaid(id: String, index: Int, paid: Boolean) = mutate { d ->
        val c = d.contracts.firstOrNull { it.id == id } ?: return@mutate d
        if (index !in c.payments.indices) return@mutate d
        val payments = c.payments.toMutableList()
        payments[index] = payments[index].copy(paid = paid)
        d.copy(contracts = d.contracts.putContract(c.copy(payments = payments, updatedAt = nowMs())))
    }

    // --- задачи по договору ------------------------------------------------
    // Задачи живут только в телефоне, но правка договора всё равно метится
    // временем: иначе таблица решит, что её версия свежее, и перезапишет
    // то, что человек только что поменял на экране.

    fun addContractTask(contractId: String, text: String) = mutate { d ->
        val c = d.contracts.firstOrNull { it.id == contractId } ?: return@mutate d
        val clean = text.trim()
        if (clean.isEmpty()) return@mutate d
        val task = ContractTask(text = clean)
        d.copy(contracts = d.contracts.putContract(c.copy(tasks = c.tasks + task, updatedAt = nowMs())))
    }

    fun setContractTaskDone(contractId: String, taskId: String, done: Boolean) = mutate { d ->
        val c = d.contracts.firstOrNull { it.id == contractId } ?: return@mutate d
        if (c.tasks.none { it.id == taskId }) return@mutate d
        val tasks = c.tasks.map { if (it.id == taskId) it.copy(done = done) else it }
        d.copy(contracts = d.contracts.putContract(c.copy(tasks = tasks, updatedAt = nowMs())))
    }

    fun renameContractTask(contractId: String, taskId: String, text: String) = mutate { d ->
        val c = d.contracts.firstOrNull { it.id == contractId } ?: return@mutate d
        val clean = text.trim()
        if (clean.isEmpty() || c.tasks.none { it.id == taskId }) return@mutate d
        val tasks = c.tasks.map { if (it.id == taskId) it.copy(text = clean) else it }
        d.copy(contracts = d.contracts.putContract(c.copy(tasks = tasks, updatedAt = nowMs())))
    }

    /** Задачу удаляем насовсем: надгробие в таблице ей не нужно, её там нет. */
    fun deleteContractTask(contractId: String, taskId: String) = mutate { d ->
        val c = d.contracts.firstOrNull { it.id == contractId } ?: return@mutate d
        if (c.tasks.none { it.id == taskId }) return@mutate d
        d.copy(
            contracts = d.contracts.putContract(
                c.copy(tasks = c.tasks.filterNot { it.id == taskId }, updatedAt = nowMs())
            )
        )
    }

    // --- удаление ---------------------------------------------------------
    // Не вырезаем строку, а ставим надгробие: иначе запись вернётся из таблицы.

    fun deleteCustomer(id: String) = mutate { d ->
        val p = d.customers.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(customers = d.customers.putParty(p.copy(deleted = true, updatedAt = nowMs())))
    }

    fun deleteEmployee(id: String) = mutate { d ->
        val e = d.employees.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(employees = d.employees.putEmployee(e.copy(deleted = true, updatedAt = nowMs())))
    }

    fun deleteSite(id: String) = mutate { d ->
        val s = d.sites.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(sites = d.sites.putSite(s.copy(deleted = true, updatedAt = nowMs())))
    }

    fun deleteContract(id: String) = mutate { d ->
        val c = d.contracts.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(contracts = d.contracts.putContract(c.copy(deleted = true, updatedAt = nowMs())))
    }

    // --- шаблоны сообщений ------------------------------------------------

    fun upsertTemplate(t: MsgTemplate) = mutate { d ->
        val list = if (d.templates.any { it.id == t.id })
            d.templates.map { if (it.id == t.id) t else it } else d.templates + t
        d.copy(templates = list)
    }

    fun deleteTemplate(id: String) = mutate { d ->
        d.copy(templates = d.templates.filterNot { it.id == id })
    }

    /**
     * Результат слияния с таблицей. Метки времени расставлены обеими сторонами,
     * поэтому здесь ничего не штампуем — кладём как есть.
     */
    fun applyMerged(merged: Db) = mutate { merged.deduped() }

    // --- черновик задачи --------------------------------------------------

    /**
     * Незаконченная задача переживает перезапуск: привязка к объекту,
     * выбранный шаблон и набранный текст лежат рядом с сотрудником, которому
     * задача адресована. Привязка тут одна и та же запись, а не список —
     * поэтому сколько бы раз ни применялся шаблон, второй связи не заводится.
     */
    fun taskDraft(employeeId: String): TaskDraft? {
        if (!::prefs.isInitialized) return null
        val raw = prefs.getString("draft:$employeeId", null) ?: return null
        return runCatching { json.decodeFromString(TaskDraft.serializer(), raw) }.getOrNull()
    }

    fun saveTaskDraft(employeeId: String, draft: TaskDraft) {
        if (!::prefs.isInitialized) return
        if (draft.isEmpty) {
            clearTaskDraft(employeeId)
            return
        }
        prefs.edit()
            .putString("draft:$employeeId", json.encodeToString(TaskDraft.serializer(), draft))
            .apply()
    }

    fun clearTaskDraft(employeeId: String) {
        if (!::prefs.isInitialized) return
        prefs.edit().remove("draft:$employeeId").apply()
    }

    // --- настройки --------------------------------------------------------

    private fun flag(key: String, def: Boolean) = if (::prefs.isInitialized) prefs.getBoolean(key, def) else def
    private fun str(key: String, def: String) = if (::prefs.isInitialized) prefs.getString(key, def) ?: def else def
    private fun num(key: String, def: Int) = if (::prefs.isInitialized) prefs.getInt(key, def) else def

    var bubbleEnabled: Boolean
        get() = flag("bubble", false)
        set(v) { if (::prefs.isInitialized) prefs.edit().putBoolean("bubble", v).apply() }

    /** Куда человек утащил пузырь. −1 — ещё не таскал, встанем по умолчанию. */
    var bubbleX: Int
        get() = num("bubbleX", -1)
        set(v) { if (::prefs.isInitialized) prefs.edit().putInt("bubbleX", v).apply() }

    var bubbleY: Int
        get() = num("bubbleY", -1)
        set(v) { if (::prefs.isInitialized) prefs.edit().putInt("bubbleY", v).apply() }

    var sheetsUrl: String
        get() = str("sheetsUrl", "")
        set(v) { if (::prefs.isInitialized) prefs.edit().putString("sheetsUrl", v).apply() }

    /** Синхронизировать самому — при открытии панели и после правки. */
    var autoSync: Boolean
        get() = flag("autoSync", true)
        set(v) { if (::prefs.isInitialized) prefs.edit().putBoolean("autoSync", v).apply() }

    /**
     * Как начинается сообщение. Пустая строка — без обращения вовсе.
     * Никаких принудительных «Доброе утро»: формат выбирает человек.
     */
    var greeting: String
        get() = str("greeting", "{Имя},")
        set(v) { if (::prefs.isInitialized) prefs.edit().putString("greeting", v).apply() }

    var lastBackup: String
        get() = str("lastBackup", "")
        set(v) { if (::prefs.isInitialized) prefs.edit().putString("lastBackup", v).apply() }

    var lastSync: String
        get() = str("lastSync", "")
        set(v) { if (::prefs.isInitialized) prefs.edit().putString("lastSync", v).apply() }

    val syncConfigured: Boolean get() = sheetsUrl.isNotBlank()
}

// Замена по идентификатору, добавление в конец. Порядок держим стабильным:
// список, который прыгает после каждой правки, невозможно читать.
private fun List<Party>.putParty(v: Party): List<Party> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v

private fun List<Employee>.putEmployee(v: Employee): List<Employee> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v

private fun List<Site>.putSite(v: Site): List<Site> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v

private fun List<Contract>.putContract(v: Contract): List<Contract> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v
