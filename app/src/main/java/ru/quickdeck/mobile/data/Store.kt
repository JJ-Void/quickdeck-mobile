package ru.quickdeck.mobile.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Всё лежит в одном JSON-файле в памяти приложения.
 * Реестр на сотни записей в базе не нуждается, а файл легко выгрузить в таблицу.
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

    @Synchronized
    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.applicationContext.filesDir, "quickdeck.json")
        prefs = ctx.applicationContext.getSharedPreferences("quickdeck", Context.MODE_PRIVATE)
        _db.value = runCatching {
            if (file.exists()) json.decodeFromString(Db.serializer(), file.readText()) else Db()
        }.getOrElse { Db() }
    }

    fun raw(): String = json.encodeToString(Db.serializer(), _db.value)

    fun replaceAll(text: String): Boolean = runCatching {
        _db.value = json.decodeFromString(Db.serializer(), text)
        persist()
        true
    }.getOrElse { false }

    @Synchronized
    private fun persist() {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Db.serializer(), _db.value))
            tmp.renameTo(file)
        }
    }

    private fun mutate(block: (Db) -> Db) {
        _db.value = block(_db.value)
        persist()
    }

    // --- запись -----------------------------------------------------------

    fun upsertCustomer(p: Party) = mutate { d ->
        d.copy(customers = d.customers.put(p).sortedBy { it.name.lowercase() })
    }

    fun upsertContractor(p: Party) = mutate { d ->
        d.copy(contractors = d.contractors.put(p).sortedBy { it.name.lowercase() })
    }

    fun upsertSite(s: Site) = mutate { d -> d.copy(sites = d.sites.put(s)) }

    fun upsertContract(c: Contract) = mutate { d -> d.copy(contracts = d.contracts.put(c)) }

    fun deleteCustomer(id: String) = mutate { d -> d.copy(customers = d.customers.filterNot { it.id == id }) }
    fun deleteContractor(id: String) = mutate { d -> d.copy(contractors = d.contractors.filterNot { it.id == id }) }
    fun deleteSite(id: String) = mutate { d -> d.copy(sites = d.sites.filterNot { it.id == id }) }
    fun deleteContract(id: String) = mutate { d -> d.copy(contracts = d.contracts.filterNot { it.id == id }) }

    // --- настройки --------------------------------------------------------

    var edgeEnabled: Boolean
        get() = prefs.getBoolean("edge", false)
        set(v) = prefs.edit().putBoolean("edge", v).apply()

    /** false — полоска слева, true — справа. */
    var edgeRight: Boolean
        get() = prefs.getBoolean("edgeRight", false)
        set(v) = prefs.edit().putBoolean("edgeRight", v).apply()

    var sheetsUrl: String
        get() = prefs.getString("sheetsUrl", "") ?: ""
        set(v) = prefs.edit().putString("sheetsUrl", v).apply()

    var lastSync: String
        get() = prefs.getString("lastSync", "") ?: ""
        set(v) = prefs.edit().putString("lastSync", v).apply()

    val isReady: Boolean get() = ::file.isInitialized
}

private fun List<Party>.put(v: Party): List<Party> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v

private fun List<Site>.put(v: Site): List<Site> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v

private fun List<Contract>.put(v: Contract): List<Contract> =
    if (any { it.id == v.id }) map { if (it.id == v.id) v else it } else this + v
