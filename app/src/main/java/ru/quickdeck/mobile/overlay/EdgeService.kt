package ru.quickdeck.mobile.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
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
import ru.quickdeck.mobile.MainActivity
import ru.quickdeck.mobile.R
import ru.quickdeck.mobile.data.Store

/** Ручка, через которую содержимое окна управляет самим окном. */
class OverlayHandle(
    val expand: () -> Unit,
    val collapse: () -> Unit,
    val focusable: (Boolean) -> Unit
)

class EdgeService : Service() {

    companion object {
        const val ACTION_STOP = "ru.quickdeck.mobile.STOP_EDGE"
        private const val CHANNEL = "edge"
        private const val NOTIF_ID = 42
        private const val STRIP_DP = 22

        fun start(ctx: Context) {
            val i = Intent(ctx, EdgeService::class.java)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, EdgeService::class.java))
        }

        fun canDraw(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)
    }

    private lateinit var wm: WindowManager
    private var root: FrameLayout? = null
    private val owners = OverlayOwners()
    private var expanded = false
    private var isFocusable = false
    private var onBack: (() -> Unit)? = null

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification())
        }
        if (canDraw(this)) attach() else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Store.edgeEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // --- окно -----------------------------------------------------------

    private fun px(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun params(full: Boolean, focusable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        return WindowManager.LayoutParams(
            if (full) WindowManager.LayoutParams.MATCH_PARENT else px(STRIP_DP),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or (if (Store.edgeRight) Gravity.END else Gravity.START)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun attach() {
        owners.create()

        val container = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onBack?.invoke()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }

        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owners)
            setViewTreeViewModelStoreOwner(owners)
            setViewTreeSavedStateRegistryOwner(owners)
            setContent {
                OverlayRoot(
                    handle = OverlayHandle(
                        expand = { setExpanded(true) },
                        collapse = { setExpanded(false); setFocusable(false) },
                        focusable = { setFocusable(it) }
                    ),
                    fromRight = Store.edgeRight,
                    registerBack = { onBack = it }
                )
            }
        }
        container.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        container.setViewTreeLifecycleOwner(owners)
        container.setViewTreeViewModelStoreOwner(owners)
        container.setViewTreeSavedStateRegistryOwner(owners)

        root = container
        runCatching { wm.addView(container, params(full = false, focusable = false)) }
            .onFailure { stopSelf() }
    }

    private fun setExpanded(value: Boolean) {
        val v = root ?: return
        if (expanded == value) return
        expanded = value
        runCatching { wm.updateViewLayout(v, params(value, isFocusable && value)) }
    }

    private fun setFocusable(value: Boolean) {
        val v = root ?: return
        if (isFocusable == value) return
        isFocusable = value
        runCatching { wm.updateViewLayout(v, params(expanded, value && expanded)) }
    }

    override fun onDestroy() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        owners.destroy()
        super.onDestroy()
    }

    // --- уведомление ------------------------------------------------------

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Полоска у края", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    description = "Панель быстрого доступа поверх других приложений"
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("QuickDeck")
            .setContentText("Полоска у края включена")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(open)
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
        if (Store.edgeEnabled && EdgeService.canDraw(context)) EdgeService.start(context)
    }
}
