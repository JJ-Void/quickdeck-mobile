package ru.quickdeck.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Export {

    private fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    /** Четыре листа одним файлом не сделать, поэтому CSV на раздел + архив в одном каталоге. */
    fun csv(db: Db): Map<String, String> {
        val out = LinkedHashMap<String, String>()

        out["Заказчики.csv"] = buildString {
            appendLine("Наименование;ИНН;Контакт;Телефон;Примечание")
            db.customers.forEach {
                appendLine(listOf(it.name, it.inn, it.contact, it.phone, it.note).joinToString(";") { v -> esc(v) })
            }
        }
        out["Исполнители.csv"] = buildString {
            appendLine("Наименование;ИНН;Контакт;Телефон;Примечание")
            db.contractors.forEach {
                appendLine(listOf(it.name, it.inn, it.contact, it.phone, it.note).joinToString(";") { v -> esc(v) })
            }
        }
        out["Объекты.csv"] = buildString {
            appendLine("Объект;Адрес;Заказчик;Статус;Срок;Готовность,%;Примечание")
            db.sites.forEach { s ->
                appendLine(
                    listOf(
                        s.name, s.address, db.customer(s.customerId)?.name ?: "",
                        s.status.label, s.deadline, s.progress.toString(), s.note
                    ).joinToString(";") { v -> esc(v) }
                )
            }
        }
        out["Договоры.csv"] = buildString {
            appendLine("Номер;Объект;Заказчик;Исполнитель;Сумма;Статус;Начало;Срок;Примечание")
            db.contracts.forEach { c ->
                appendLine(
                    listOf(
                        c.number, db.site(c.siteId)?.name ?: "",
                        db.customer(c.customerId)?.name ?: "",
                        db.contractor(c.contractorId)?.name ?: "",
                        c.amount.toString(), c.status.label, c.start, c.end, c.note
                    ).joinToString(";") { v -> esc(v) }
                )
            }
        }
        return out
    }

    /** Складывает CSV и JSON в кэш и открывает системный «Поделиться». */
    fun share(ctx: Context) {
        val dir = File(ctx.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val files = ArrayList<Uri>()
        val auth = ctx.packageName + ".files"

        csv(Store.db.value).forEach { (name, text) ->
            val f = File(dir, name)
            // BOM — чтобы Excel не ломал кириллицу
            f.writeText("\uFEFF" + text)
            files += FileProvider.getUriForFile(ctx, auth, f)
        }
        val backup = File(dir, "quickdeck.json").apply { writeText(Store.raw()) }
        files += FileProvider.getUriForFile(ctx, auth, backup)

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(intent, "Выгрузка реестра").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Отправка всего реестра в Google-таблицу.
     * На стороне таблицы стоит веб-приложение Apps Script (код в README),
     * оно принимает JSON и перезаписывает листы. Логина и OAuth не требует.
     */
    fun toSheets(url: String, payload: String): Result<String> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) error("Таблица ответила $code: ${body.take(120)}")
        Store.lastSync = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        body.take(200)
    }
}
