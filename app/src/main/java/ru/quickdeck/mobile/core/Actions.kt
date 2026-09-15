package ru.quickdeck.mobile.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import ru.quickdeck.mobile.data.Chat
import ru.quickdeck.mobile.data.Employee

/**
 * Быстрые действия: один тап — и уже звонишь или пишешь.
 * Никаких «скопировать номер — открыть мессенджер — найти — вставить».
 */
object Actions {

    private fun launch(ctx: Context, intent: Intent): Boolean = runCatching {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    private fun digits(s: String) = s.filter { it.isDigit() || it == '+' }

    fun dial(ctx: Context, phone: String): Boolean =
        launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits(phone))))

    /**
     * Чат открывается тем приложением, которое отвечает за ссылку. Telegram
     * понимает и ник, и телефон; WhatsApp — только телефон.
     */
    fun chat(ctx: Context, chat: Chat): Boolean {
        val h = chat.handle.trim()
        if (h.isBlank()) return false
        val url = when (chat.kind) {
            "telegram" ->
                if (h.startsWith("+") || h.first().isDigit()) "https://t.me/${digits(h)}"
                else "https://t.me/${h.removePrefix("@")}"
            "whatsapp" -> "https://wa.me/${digits(h).removePrefix("+")}"
            "max" -> "https://max.ru/${h.removePrefix("@")}"
            "vk" -> "https://vk.me/${h.removePrefix("@")}"
            "email" -> "mailto:$h"
            else -> return false
        }
        return launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Написать с готовым текстом — куда получится, туда и уйдёт. */
    fun message(ctx: Context, chat: Chat?, phone: String?, text: String): Boolean {
        val encoded = Uri.encode(text)
        val url = when (chat?.kind) {
            "whatsapp" -> "https://wa.me/${digits(chat.handle).removePrefix("+")}?text=$encoded"
            "telegram" -> {
                val h = chat.handle.trim()
                val target = if (h.startsWith("+") || h.first().isDigit()) digits(h) else h.removePrefix("@")
                "https://t.me/$target?text=$encoded"
            }
            "email" -> "mailto:${chat.handle}?body=$encoded"
            else -> phone?.takeIf { it.isNotBlank() }?.let { "sms:${digits(it)}?body=$encoded" }
        }
        if (url != null && launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) return true
        return share(ctx, text)
    }

    /** Системное «Поделиться» — когда адресата нет или он не в мессенджере. */
    fun share(ctx: Context, text: String, title: String = "Отправить"): Boolean =
        launch(
            ctx,
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                title
            )
        )

    /** Текстовая карточка сотрудника — вставить в любое сообщение. */
    fun employeeText(e: Employee): String = buildString {
        appendLine(e.name)
        listOf(e.position, e.department).filter { it.isNotBlank() }.joinToString(" · ")
            .takeIf { it.isNotBlank() }?.let { appendLine(it) }
        e.phones.forEach { appendLine(it) }
        e.chats.forEach { c ->
            appendLine(
                when (c.kind) {
                    "telegram" -> "Telegram: ${c.handle}"
                    "whatsapp" -> "WhatsApp: ${c.handle}"
                    "email" -> "E-mail: ${c.handle}"
                    else -> "${c.kind}: ${c.handle}"
                }
            )
        }
    }.trimEnd()
}
