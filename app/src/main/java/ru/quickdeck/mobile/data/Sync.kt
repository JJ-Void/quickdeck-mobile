package ru.quickdeck.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Обмен с Google-таблицей в обе стороны.
 *
 * Как это устроено. Телефон отправляет свой реестр целиком. Скрипт таблицы
 * читает листы, сливает их с присланным по метке времени каждой записи и
 * возвращает общий результат. Телефон сливает ещё раз — уже с тем, что мог
 * успеть поменять человек, пока шёл запрос, — и сохраняет.
 *
 * Правка в таблице побеждает правку в телефоне, если она свежее, и наоборот.
 * Сервера тут нет: вся договорённость держится на updatedAt.
 */
object Sync {

    private const val MAX_REDIRECTS = 5

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Что изменилось за обмен — чтобы человеку было что показать. */
    data class Report(val fromTable: Int, val at: String) {
        val text: String
            get() = if (fromTable == 0) "Таблица уже совпадает"
            else "Из таблицы пришло $fromTable ${plural(fromTable.toLong(), "изменение", "изменения", "изменений")}"
    }

    fun run(): Result<Report> = runCatching {
        val url = Store.sheetsUrl.trim()
        if (url.isBlank()) error("Ссылка на таблицу не задана")

        val sent = Store.db.value
        val request = buildJsonObject {
            put("action", "sync")
            put("db", json.encodeToJsonElement(Db.serializer(), sent))
        }

        val text = post(url, json.encodeToString(JsonObject.serializer(), request))
        val root = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { error("Таблица ответила не по-джейсонному. Обычно это значит, что скрипт развёрнут с доступом «только я».") }

        if (root["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            error(root["error"]?.jsonPrimitive?.contentOrNull ?: "Скрипт таблицы вернул отказ")
        }

        val remoteEl = root["db"] ?: error("В ответе таблицы нет реестра")
        val remote = json.decodeFromJsonElement(Db.serializer(), remoteEl)

        // Ещё одно слияние — уже с текущим состоянием, а не с отправленным:
        // человек мог поменять статус, пока запрос был в пути.
        val local = Store.db.value
        Store.applyMerged(mergeDb(local, remote))

        val at = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
        Store.lastSync = at
        Report(fromTable = incomingCount(local, remote), at = at)
    }

    private fun incomingCount(local: Db, remote: Db): Int =
        newer(local.customers, remote.customers) +
            newer(local.contractors, remote.contractors) +
            newer(local.sites, remote.sites) +
            newer(local.contracts, remote.contracts)

    private fun <T : Row> newer(local: List<T>, remote: List<T>): Int {
        val byId = local.associateBy { it.id }
        return remote.count { r ->
            val l = byId[r.id]
            l == null || r.updatedAt > l.updatedAt
        }
    }

    /**
     * Apps Script на POST отвечает переадресацией на googleusercontent, и тело
     * ответа лежит уже там. HttpURLConnection сам через это не проходит,
     * поэтому переадресации разбираем руками.
     */
    private fun post(url: String, payload: String): String {
        var current = url
        var method = "POST"
        var hops = 0

        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 45_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }

            if (method == "POST") {
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }

            val code = conn.responseCode

            if (code in 301..308) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next.isNullOrBlank()) error("Таблица переадресовала в пустоту")
                if (++hops > MAX_REDIRECTS) error("Слишком много переадресаций")
                current = next
                method = "GET"   // результат doPost лежит уже по новому адресу
                continue
            }

            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            if (code !in 200..299) {
                error(
                    when (code) {
                        401, 403 -> "Доступ закрыт ($code). Разверни скрипт заново с доступом «Все, у кого есть ссылка»."
                        404 -> "Ссылка не найдена (404). Нужен адрес, который кончается на /exec."
                        else -> "Таблица ответила $code: ${body.take(140)}"
                    }
                )
            }
            return body
        }
    }
}
