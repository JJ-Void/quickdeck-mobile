package ru.quickdeck.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Выгрузка «на всякий случай»: CSV на раздел плюс полная копия в JSON.
 * Обмен с таблицей живёт отдельно, в [Sync] — это разные задачи.
 */
object Export {

    private fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun rows(head: String, lines: List<List<String>>): String = buildString {
        appendLine(head)
        lines.forEach { appendLine(it.joinToString(";") { v -> esc(v) }) }
    }

    fun csv(db: Db): Map<String, String> {
        val out = LinkedHashMap<String, String>()

        out["Заказчики.csv"] = rows(
            "Наименование;ИНН;Контакт;Телефон;Примечание",
            db.liveCustomers.map { listOf(it.name, it.inn, it.contact, it.phone, it.note) }
        )
        out["Исполнители.csv"] = rows(
            "Наименование;ИНН;Контакт;Телефон;Примечание",
            db.liveContractors.map { listOf(it.name, it.inn, it.contact, it.phone, it.note) }
        )
        out["Объекты.csv"] = rows(
            "Объект;Адрес;Заказчик;Статус;Срок;Готовность,%;Примечание",
            db.liveSites.map { s ->
                listOf(
                    s.name, s.address, db.customer(s.customerId)?.name ?: "",
                    s.status.label, dateInput(s.deadline), s.progress.toString(), s.note
                )
            }
        )
        out["Договоры.csv"] = rows(
            "Номер;Объект;Заказчик;Исполнитель;Сумма;Статус;Начало;Срок;Примечание",
            db.liveContracts.map { c ->
                listOf(
                    c.number, db.site(c.siteId)?.name ?: "",
                    db.customer(c.customerId)?.name ?: "",
                    db.contractor(c.contractorId)?.name ?: "",
                    c.amount.toString(), c.status.label,
                    dateInput(c.start), dateInput(c.end), c.note
                )
            }
        )
        return out
    }

    /** Складывает CSV и копию в кэш и открывает системное «Поделиться». */
    fun share(ctx: Context) {
        val dir = File(ctx.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val files = ArrayList<Uri>()
        val auth = ctx.packageName + ".files"

        csv(Store.db.value).forEach { (name, text) ->
            val f = File(dir, name)
            // BOM — чтобы Excel не ломал кириллицу
            f.writeText("﻿" + text)
            files += FileProvider.getUriForFile(ctx, auth, f)
        }
        val backup = File(dir, "quickdeck.json").apply { writeText(Store.raw()) }
        files += FileProvider.getUriForFile(ctx, auth, backup)

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(
            Intent.createChooser(intent, "Выгрузка реестра").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
