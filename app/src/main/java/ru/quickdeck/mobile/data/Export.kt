package ru.quickdeck.mobile.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Выгрузка в таблицы: CSV на раздел плюс полная копия.
 * Обмен с Google-таблицей живёт отдельно, в [Sync] — это разные задачи.
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
            "Наименование;Полное;ИНН;КПП;ОГРН;Руководитель;Телефон;E-mail;Банк;БИК;Счёт",
            db.liveCustomers.map {
                listOf(it.name, it.fullName, it.inn, it.kpp, it.ogrn, it.director, it.phone, it.email, it.bank, it.bik, it.account)
            }
        )
        out["Сотрудники.csv"] = rows(
            "Ф. И. О.;Отдел;Должность;Таб. №;Телефоны;Чаты;Комментарий",
            db.liveEmployees.map { e ->
                listOf(
                    e.name, e.department, e.position, e.tabNumber,
                    e.phones.joinToString(", "),
                    e.chats.joinToString(", ") { "${it.kind}: ${it.handle}" },
                    e.note
                )
            }
        )
        out["Объекты.csv"] = rows(
            "Объект;Полное;Заказчик;Адрес;Тип здания;Площадь;Ед. изм.;Комментарий",
            db.liveSites.map { s ->
                listOf(
                    s.name, s.fullName, db.customer(s.customerId)?.name ?: "", s.address,
                    s.buildingType, if (s.area == 0.0) "" else s.area.toString(), s.unit, s.note
                )
            }
        )
        out["Договоры.csv"] = rows(
            "Объект;Вид работы;Юр. лицо;Статус;Цена;Начало;Срок;Ответственный;Комментарий",
            db.liveContracts.map { c ->
                listOf(
                    db.site(c.siteId)?.name ?: "", c.workKind, c.legalEntity, c.status.label,
                    c.amount.toString(), dateInput(c.start), dateInput(c.end), c.responsible, c.note
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
        val backup = File(dir, Backup.suggestedName()).apply { writeText(Backup.make()) }
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
