package ru.quickdeck.mobile.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ru.quickdeck.mobile.MainActivity
import ru.quickdeck.mobile.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Наблюдатель: система работает, пока человек её не трогает.
 *
 * Две вещи, из-за которых это перестаёт быть просто приложением. Первая —
 * реестр обновляется сам: обмен с таблицей идёт в фоне, пока висит нить, и
 * к моменту, когда панель открывают, данные уже свежие. Вторая — утренняя
 * сводка приходит уведомлением: что просрочено, что горит сегодня, сколько
 * ждёт оплаты. Не нужно вспоминать проверить — оно само напомнит.
 *
 * Дайджест приходит один раз в день, не чаще: уведомление, которое повторяет
 * одно и то же, перестают читать на третий день.
 */
object Watcher {

    private const val CHANNEL = "digest"
    private const val NOTIF_ID = 77
    private const val KEY_LAST_DIGEST = "lastDigest"

    /** Утро руководителя: раньше — сон, позже — день уже начался. */
    private const val DIGEST_HOUR = 8

    /** Как часто система сама ходит в таблицу, пока нить на экране. */
    const val SYNC_PERIOD_MS = 20 * 60 * 1000L

    /**
     * Пора ли идти в таблицу. Решает не таймер, а состояние: если обмен
     * недавно был или он не настроен, поход отменяется — трафик и батарея
     * тратятся только по делу.
     */
    fun shouldSync(): Boolean =
        Store.syncConfigured && Store.autoSync && !Store.isSyncing

    /** Пора ли слать утреннюю сводку. */
    fun shouldDigest(): Boolean {
        if (!Store.digestEnabled) return false
        val now = Calendar.getInstance()
        if (now.get(Calendar.HOUR_OF_DAY) < DIGEST_HOUR) return false
        return Store.lastDigestDay != dayKey(now.time)
    }

    private fun dayKey(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)

    /** Что человек должен узнать про сегодня, одной строкой и списком. */
    data class Digest(val headline: String, val lines: List<String>, val urgent: Boolean)

    fun digestOf(db: Db): Digest {
        val live = db.liveContracts
        val active = live.filter { c ->
            val st = shownStatusOf(c)
            st.stage != Stage.PROBLEM && st != Status.PAID_FULL
        }
        val overdue = live.filter { overdueText(it.end) != null && shownStatusOf(it).signed }
        val today = active.filter { it.end.isNotBlank() && isToday(it.end) }
        val awaiting = active.filter { shownStatusOf(it).stage == Stage.PAYMENT }
        val rest = active.filter { shownStatusOf(it).signed }.sumOf { it.restAmount }

        val lines = buildList {
            if (overdue.isNotEmpty()) {
                add("Просрочено: " + overdue.size + " — " + overdue.take(2).joinToString(", ") {
                    db.site(it.siteId)?.name ?: it.workKind
                })
            }
            if (today.isNotEmpty()) {
                add("Сегодня срок: " + today.joinToString(", ") {
                    db.site(it.siteId)?.name ?: it.workKind
                })
            }
            if (awaiting.isNotEmpty()) add("Ждёт оплаты: " + awaiting.size)
            if (rest > 0) add("Не получено: " + money(rest))
        }

        val headline = when {
            overdue.isNotEmpty() -> "Просрочено " + overdue.size
            today.isNotEmpty() -> "Сегодня сдача по " + today.size
            lines.isNotEmpty() -> "Дела на сегодня"
            else -> "Всё по плану"
        }
        return Digest(headline, lines, overdue.isNotEmpty())
    }

    /** Статус с поправкой на просрочку — тот же расчёт, что и на экране. */
    private fun shownStatusOf(c: Contract): Status {
        val stage = c.status.stage
        val watch = stage == Stage.CONTRACT || stage == Stage.PRODUCTION || stage == Stage.ACCEPTANCE
        return if (c.status.signed && watch && overdueText(c.end) != null) Status.OVERDUE else c.status
    }

    private fun isToday(iso: String): Boolean =
        iso == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /**
     * Показать сводку. Уведомление тихое, когда всё в порядке, и обычное,
     * когда что-то просрочено: тревожный звук ради «всё по плану» —
     * худший способ потерять доверие к уведомлениям.
     */
    fun notify(ctx: Context, db: Db) {
        val digest = digestOf(db)
        if (digest.lines.isEmpty() && !digest.urgent) {
            Store.lastDigestDay = dayKey(Date())
            return
        }

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Сводка дня",
                if (digest.urgent) NotificationManager.IMPORTANCE_DEFAULT
                else NotificationManager.IMPORTANCE_LOW
            )
        )

        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = digest.lines.joinToString("\n")
        val notification = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(digest.headline)
            .setContentText(digest.lines.firstOrNull().orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIF_ID, notification)
        Store.lastDigestDay = dayKey(Date())
    }
}
