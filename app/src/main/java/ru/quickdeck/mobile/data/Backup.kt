package ru.quickdeck.mobile.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Резервная копия. Всё хранится на телефоне, поэтому копия — единственный
 * способ не потерять реестр вместе с устройством.
 *
 * format — версия формата. Она нужна, чтобы обновление приложения могло
 * прочитать старую копию: если структура изменится,старую версию будет
 * куда привести. Без этого поля любая старая копия однажды станет мусором.
 */
@Serializable
data class BackupFile(
    val format: Int = FORMAT,
    val app: String = "podryad",
    val saved: String = "",
    val db: Db = Db()
) {
    companion object {
        const val FORMAT = 1
    }
}

object Backup {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun suggestedName(): String =
        "podryad-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".json"

    fun make(): String = json.encodeToString(
        BackupFile.serializer(),
        BackupFile(
            saved = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
            db = Store.db.value
        )
    )

    fun write(ctx: Context, uri: Uri): Result<Unit> = runCatching {
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(make().toByteArray(Charsets.UTF_8)) }
            ?: error("Не удалось открыть файл для записи")
        Store.lastBackup = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
    }

    /**
     * Чтение копии. Понимаем и новый формат с обёрткой, и голый реестр —
     * чтобы копии, сделанные до появления версионирования, тоже открывались.
     */
    fun read(ctx: Context, uri: Uri): Result<Int> = runCatching {
        val text = ctx.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: error("Не удалось прочитать файл")

        val parsed = runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrNull()
        val db = when {
            parsed != null && parsed.db != Db() -> {
                if (parsed.format > BackupFile.FORMAT) {
                    error("Копия сделана более новой версией приложения (формат ${parsed.format})")
                }
                parsed.db
            }
            else -> Store.parse(text) ?: error("Это не похоже на копию реестра")
        }

        Store.applyMerged(db)
        db.liveSites.size + db.liveContracts.size + db.liveCustomers.size + db.liveEmployees.size
    }

    /** Тихая копия рядом с данными — страховка на случай сбоя, без участия человека. */
    fun auto(ctx: Context) {
        runCatching {
            val dir = File(ctx.filesDir, "backup").apply { mkdirs() }
            val file = File(dir, "auto.json")
            file.writeText(make())
            // Держим один предыдущий срез: две копии лучше одной, а десять
            // на телефоне не нужны.
            val prev = File(dir, "auto-prev.json")
            if (file.length() > 0 && prev.exists()) prev.delete()
            if (file.exists()) file.copyTo(prev, overwrite = true)
        }
    }
}
