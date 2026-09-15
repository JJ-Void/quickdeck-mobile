package ru.quickdeck.mobile.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.quickdeck.mobile.MainActivity
import ru.quickdeck.mobile.R
import ru.quickdeck.mobile.SheetActivity
import ru.quickdeck.mobile.data.Section
import ru.quickdeck.mobile.data.Store
import ru.quickdeck.mobile.data.Sync
import kotlin.math.roundToInt

/**
 * Два окна поверх всего остального.
 *
 * Маленькое — пузырь. Оно ловит весь жест и за время жеста никогда не меняет
 * размер: именно пересоздание окна в прошлой версии рвало поток касаний и
 * палец переставал листать.
 *
 * Большое — панель. Оно добавляется один раз при запуске и по умолчанию
 * вообще не принимает касания: пока идёт ведение по колесу, это чистая
 * картинка. Касания оно берёт только когда открыт список или карточка.
 *
 * Ни одно из двух окон не бывает фокусируемым. Значит, оно не может перехватить
 * клавиатуру и системные клавиши, и не может залипнуть: пузырь всегда лежит
 * сверху панели и по тапу закрывает её.
 */
class BubbleService : Service(), OverlayHost {

    companion object {
        const val ACTION_STOP = "ru.quickdeck.mobile.STOP_BUBBLE"
        private const val CHANNEL = "bubble"
        private const val NOTIF_ID = 42
        private const val BUBBLE_DP = 72

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, BubbleService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BubbleService::class.java))
        }

        fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)
    }

    private lateinit var wm: WindowManager
    private val owners = OverlayOwners()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var panelHost: FrameLayout? = null
    private var bubbleHost: FrameLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var snapper: ValueAnimator? = null

    private val bubblePx get() = (BUBBLE_DP * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification())
        }

        if (!canDraw(this)) {
            stopSelf()
            return
        }
        // Служба могла быть перезапущена системой — состояние от прошлой
        // жизни сбрасываем, иначе панель воскреснет открытой без окна.
        OverlayState.close()
        OverlayState.host = this
        attach()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Store.bubbleEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // --- экран ------------------------------------------------------------

    private fun screen(): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(wm.currentWindowMetrics.bounds)
        } else {
            val m = resources.displayMetrics
            Rect(0, 0, m.widthPixels, m.heightPixels)
        }

    // --- окна -------------------------------------------------------------

    private fun baseFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    private fun panelLayout(touchable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        if (touchable) baseFlags() else baseFlags() or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 0
    }

    private fun bubbleLayout(x: Int, y: Int) = WindowManager.LayoutParams(
        bubblePx,
        bubblePx,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        baseFlags(),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    private fun compose(content: @androidx.compose.runtime.Composable () -> Unit): FrameLayout {
        val host = FrameLayout(this)
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owners)
            setViewTreeViewModelStoreOwner(owners)
            setViewTreeSavedStateRegistryOwner(owners)
            setContent { content() }
        }
        host.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        host.setViewTreeLifecycleOwner(owners)
        host.setViewTreeViewModelStoreOwner(owners)
        host.setViewTreeSavedStateRegistryOwner(owners)
        return host
    }

    private fun attach() {
        owners.create()
        val bounds = screen()
        val size = bubblePx

        // Панель — первой, чтобы пузырь лёг поверх неё и всегда оставался
        // доступным. Порядок добавления и есть порядок наложения.
        val panel = compose { PanelRoot(this) }
        val pParams = panelLayout(touchable = false)
        panelHost = panel
        panelParams = pParams
        runCatching { wm.addView(panel, pParams) }.onFailure { stopSelf(); return }

        val savedX = Store.bubbleX
        val savedY = Store.bubbleY
        val x = if (savedX >= 0) savedX else bounds.width() - size
        val y = if (savedY >= 0) savedY else (bounds.height() * 0.42f).toInt()

        val bubble = compose { BubbleRoot(this, bounds.width().toFloat()) }
        val bParams = bubbleLayout(clampX(x, bounds, size), clampY(y, bounds, size))
        bubbleHost = bubble
        bubbleParams = bParams
        runCatching { wm.addView(bubble, bParams) }.onFailure { stopSelf(); return }

        OverlayState.bubbleSize = size.toFloat()
        publishBubblePosition()
        excludeFromSystemGestures()

        if (Store.autoSync && Store.syncConfigured) syncQuietly()
    }

    private fun clampX(value: Int, bounds: Rect, size: Int) =
        value.coerceIn(0, (bounds.width() - size).coerceAtLeast(0))

    private fun clampY(value: Int, bounds: Rect, size: Int) =
        value.coerceIn(
            (24 * resources.displayMetrics.density).toInt(),
            (bounds.height() - size - 48 * resources.displayMetrics.density).toInt().coerceAtLeast(0)
        )

    private fun publishBubblePosition() {
        val p = bubbleParams ?: return
        OverlayState.bubbleLeft = p.x.toFloat()
        OverlayState.bubbleTop = p.y.toFloat()
    }

    /**
     * Система резервирует полосы у краёв под свои жесты. Пузырь у самого края
     * без этого просто не получал бы касание — вместо него срабатывал «назад».
     */
    private fun excludeFromSystemGestures() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val view = bubbleHost ?: return
        view.post {
            val size = bubblePx
            runCatching {
                view.systemGestureExclusionRects = listOf(Rect(0, 0, size, size))
            }
        }
    }

    // --- OverlayHost ------------------------------------------------------

    override fun panelTouchable(value: Boolean) {
        val view = panelHost ?: return
        val params = panelParams ?: return
        val wanted = if (value) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (wanted == params.flags) return
        params.flags = wanted
        runCatching { wm.updateViewLayout(view, params) }
    }

    override fun moveBubble(dx: Float, dy: Float) {
        val view = bubbleHost ?: return
        val params = bubbleParams ?: return
        val bounds = screen()
        val size = bubblePx
        params.x = clampX(params.x + dx.roundToInt(), bounds, size)
        params.y = clampY(params.y + dy.roundToInt(), bounds, size)
        runCatching { wm.updateViewLayout(view, params) }
        publishBubblePosition()
    }

    override fun snapBubble() {
        val view = bubbleHost ?: return
        val params = bubbleParams ?: return
        val bounds = screen()
        val size = bubblePx
        val from = params.x
        val to = if (from + size / 2 > bounds.width() / 2) bounds.width() - size else 0

        snapper?.cancel()
        snapper = ValueAnimator.ofInt(from, to).apply {
            duration = 200
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                runCatching { wm.updateViewLayout(view, params) }
                publishBubblePosition()
            }
            start()
        }

        Store.bubbleX = to
        Store.bubbleY = params.y
    }

    override fun openForm(section: Section, id: String?) {
        OverlayState.close()
        startActivity(SheetActivity.form(this, section, id))
    }

    override fun openSearch() {
        OverlayState.close()
        startActivity(SheetActivity.search(this))
    }

    @Suppress("DEPRECATION")
    override fun buzz(ms: Long) {
        runCatching {
            val effect = VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator.vibrate(effect)
            } else {
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(effect)
            }
        }
    }

    override fun syncQuietly() {
        if (!Store.syncConfigured || OverlayState.syncing) return
        scope.launch {
            OverlayState.syncing = true
            val result = withContext(Dispatchers.IO) { Sync.run() }
            OverlayState.syncing = false
            OverlayState.freshCount = result.getOrNull()?.fromTable ?: 0
        }
    }

    // --- жизнь ------------------------------------------------------------

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = bubbleHost ?: return
        val params = bubbleParams ?: return
        val bounds = screen()
        val size = bubblePx
        params.x = clampX(params.x, bounds, size)
        params.y = clampY(params.y, bounds, size)
        runCatching { wm.updateViewLayout(view, params) }
        publishBubblePosition()
    }

    override fun onDestroy() {
        snapper?.cancel()
        scope.cancel()
        OverlayState.host = null
        OverlayState.close()
        bubbleHost?.let { runCatching { wm.removeView(it) } }
        panelHost?.let { runCatching { wm.removeView(it) } }
        bubbleHost = null
        panelHost = null
        owners.destroy()
        super.onDestroy()
    }

    // --- уведомление ------------------------------------------------------

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Пузырь QuickDeck", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    description = "Реестр поверх других приложений"
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("QuickDeck")
            .setContentText("Пузырь реестра включён")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Убрать пузырь", stop).build())
            .build()
    }
}

/** Compose внутри окна службы требует своих владельцев жизненного цикла. */
class OverlayOwners : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val saved = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun create() {
        saved.performAttach()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.init(context)
        if (Store.bubbleEnabled && BubbleService.canDraw(context)) BubbleService.start(context)
    }
}
