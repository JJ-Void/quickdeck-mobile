package ru.quickdeck.mobile.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Отклик в палец.
 *
 * Правило одно: вибрация подтверждает действие, которое человек совершил, и
 * никогда не сопровождает то, что приложение делает само. Телефон, который
 * дрожит на каждую перерисовку, через день выключают целиком — вместе с
 * теми ударами, ради которых всё затевалось.
 *
 * Поэтому ударов ровно три, и у каждого своя работа:
 *
 *  - [tick] — палец попал по цели: сменился раздел, встал фильтр,
 *    открылась запись. Самый короткий, почти на границе ощутимого.
 *  - [confirm] — состояние изменилось насовсем: задача закрыта, оплата
 *    отмечена, статус сменён, сообщение ушло.
 *  - [warn] — что-то удалено или не получилось. Двойной, потому что
 *    одиночный на этом месте не отличить от [confirm].
 *
 * Живёт объектом, а не хуком Compose: панель рисуется из службы, экраны —
 * из Activity, и обе стороны должны бить одинаково.
 */
object Feel {

    private var vibrator: Vibrator? = null

    fun init(ctx: Context) {
        if (vibrator != null) return
        vibrator = runCatching {
            val app = ctx.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }.getOrNull()
    }

    /** Попадание по цели. */
    fun tick() = shot(8)

    /** Действие состоялось. */
    fun confirm() = shot(18)

    /** Удалено или не вышло. */
    fun warn() = pattern(longArrayOf(0, 14, 60, 22))

    private fun shot(ms: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun pattern(steps: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(steps, -1)) }
    }
}
