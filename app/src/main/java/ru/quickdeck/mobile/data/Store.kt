package ru.quickdeck.mobile.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Всё лежит в одном JSON-файле в памяти приложения.
 * Реестр на сотни записей в базе не нуждается, а файл легко слить в таблицу.
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

    val isReady: Boolean get() = ::file.isInitialized

    @Synchronized
    fun init(ctx: Context) {
        if (::file.isInitialized) return
        val app = ctx.applicationContext
        file = File(app.filesDir, "quickdeck.json")
        prefs = app.getSharedPreferences("quickdeck", Context.MODE_PRIVATE)
        _db.value = runCatching {
            if (file.exists()) json.decodeFromString(Db.serializer(), file.readText()) else Db()
        }.getOrElse { Db() }
    }

    fun raw(): String = json.encodeToString(Db.serializer(), _db.value)

    fun parse(text: String): Db? =
        runCatching { json.decodeFromString(Db.serializer(), text) }.getOrNull()

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
    // Каждая правка помечается текущим временем. Это и есть вся механика
    // синхронизации: кто правил позже, того версия и останется.

    fun upsertCustomer(p: Party) = mutate { d ->
        d.copy(customers = d.customers.put(p.copy(updatedAt = nowMs())))
    }

    fun upsertContractor(p: Party) = mutate { d ->
        d.copy(contractors = d.contractors.put(p.copy(updatedAt = nowMs())))
    }

    fun upsertSite(s: Site) = mutate { d ->
        d.copy(sites = d.sites.put(s.copy(updatedAt = nowMs())))
    }

    fun upsertContract(c: Contract) = mutate { d ->
        d.copy(contracts = d.contracts.put(c.copy(updatedAt = nowMs())))
    }

    /** Смена статуса — самое частое действие, поэтому отдельным входом. */
    fun setSiteStatus(id: String, status: Status) = mutate { d ->
        val s = d.sites.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(sites = d.sites.put(s.copy(status = status, updatedAt = nowMs())))
    }

    fun setContractStatus(id: String, status: Status) = mutate { d ->
        val c = d.contracts.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(contracts = d.contracts.put(c.copy(status = status, updatedAt = nowMs())))
    }

    fun setSiteProgress(id: String, percent: Int) = mutate { d ->
        val s = d.sites.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(sites = d.sites.put(s.copy(progress = percent.coerceIn(0, 100), updatedAt = nowMs())))
    }

    // --- удаление ---------------------------------------------------------
    // Не вырезаем строку, а ставим надгробие: иначе запись вернётся из таблицы.

    fun deleteCustomer(id: String) = mutate { d ->
        val p = d.customers.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(customers = d.customers.put(p.copy(deleted = true, updatedAt = nowMs())))
    }

    fun deleteContractor(id: String) = mutate { d ->
        val p = d.contractors.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(contractors = d.contractors.put(p.copy(deleted = true, updatedAt = nowMs())))
    }

    fun deleteSite(id: String) = mutate { d ->
        val s = d.sites.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(sites = d.sites.put(s.copy(deleted = true, updatedAt = nowMs())))
    }

    fun deleteContract(id: String) = mutate { d ->
        val c = d.contracts.firstOrNull { it.id == id } ?: return@mutate d
        d.copy(contracts = d.contracts.put(c.copy(deleted = true, updatedAt = nowMs())))
    }

    /**
     * Результат слияния с таблицей. Метки времени уже расставлены обеими
     * сторонами, поэтому здесь ничего не штампуем — просто кладём как есть.
     */
    fun applyMerged(merged: Db) = mutate { merged }

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

    var lastSync: String
        get() = str("lastSync", "")
        set(v) { if (::prefs.isInitialized) prefs.edit().putString("lastSync", v).apply() }

    val syncConfigured: Boolean get() = sheetsUrl.isNotBlank()
}

// Замена по идентификатору, добавление в конец. Порядок держим стабильным:
// список, который прыгает после каждой правки, невозможно читать.
private fun List<Party>.put(v: Party): List<Party> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v

private fun List<Site>.put(v: Site): List<Site> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v

private fun List<Contract>.put(v: Contract): List<Contract> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v
