package com.island.dynamicisland.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.Surface
import android.view.MotionEvent
import android.view.View
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.island.dynamicisland.power.InteractiveDisplayMonitor
import com.island.dynamicisland.config.CameraGuide
import com.island.dynamicisland.config.IslandConfig
import com.island.dynamicisland.ui.island.IslandRoot
import com.island.dynamicisland.ui.island.ActivityTouchBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the Dynamic Island as a `TYPE_ACCESSIBILITY_OVERLAY` window.
 *
 * This is the crucial difference from an ordinary `TYPE_APPLICATION_OVERLAY`: an accessibility
 * overlay is layered *above* the status bar, so the island can sit on the camera cutout and
 * still receive touches there. (An application overlay is layered below the status bar, whose
 * full-width touchable strip would otherwise steal every tap.)
 *
 * A [ComposeView] added to a WindowManager needs a Lifecycle, a ViewModelStore and a
 * SavedStateRegistry in its view tree, so the service supplies all three itself.
 */
class IslandAccessibilityService : AccessibilityService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var touchView: View? = null
    private var touchParams: WindowManager.LayoutParams? = null
    private class SecondaryTouch(val view: View, val params: WindowManager.LayoutParams) {
        val bounds = android.graphics.Rect()
        var available = false
        var gestureActive = false
    }
    private val secondaryTouches = linkedMapOf<String, SecondaryTouch>()
    private var dismissRequest by mutableIntStateOf(0)
    private var calibrating = false
    private val pillBounds = android.graphics.Rect()
    private var hasPillBounds = false
    private var captureOutside = false
    private var swallowingOutsideGesture = false
    private var foregroundPackage by mutableStateOf<String?>(null)
    private var foregroundRefreshJob: Job? = null
    private var displayActive by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var keyguardShowing by mutableStateOf(false)
    private var lockMonitorJob: Job? = null
    private var showingLock by mutableStateOf(false)
    private var unlockJob: Job? = null
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val lockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateShadeVisibility()
        }
    }
    private var lockReceiverRegistered = false
    private var shadeVisible by mutableStateOf(false)
    private var shadeExitComplete by mutableStateOf(false)
    private var portraitOrientation by mutableStateOf(true)
    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }
    private val rotationListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY) updateOrientationVisibility()
        }
    }
    private val islandVisible get() = displayActive && portraitOrientation && (showingLock || !shadeVisible)
    private val renderIsland get() = displayActive && portraitOrientation &&
        (showingLock || !shadeVisible || !shadeExitComplete)
    private val displayMonitor = InteractiveDisplayMonitor(this, ::onDisplayActivityChanged)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val bluetoothAudio by lazy { com.island.dynamicisland.audio.BluetoothAudioMonitor(this, scope) }

    override fun onCreate() {
        super.onCreate()
        // Android can unbind before onServiceConnected, including during an APK reload.
        // Establish the owner lifecycle at service creation so teardown is always legal.
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandConfig.init(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        updateOrientationVisibility()
        displayManager.registerDisplayListener(rotationListener, android.os.Handler(mainLooper))
        publishCameraGuide()
        addOverlay()
        observeConfig()
        ContextCompat.registerReceiver(this, lockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        lockReceiverRegistered = true
        displayMonitor.start()
        bluetoothAudio.start()
        updateShadeVisibility()
    }

    private fun updateOrientationVisibility() {
        val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        // Follow the logical screen, including rotation lock. Physical tilt must not
        // hide the island or require an always-running orientation sensor listener.
        val bounds = windowManager.currentWindowMetrics.bounds
        val portrait = bounds.height() > bounds.width() && display?.rotation == Surface.ROTATION_0
        if (portraitOrientation == portrait) return
        portraitOrientation = portrait
        if (!portrait) {
            dismissRequest++
            hasPillBounds = false
            captureOutside = false
            swallowingOutsideGesture = false
            secondaryTouches.values.forEach { it.gestureActive = false }
        }
        syncOverlayVisibility()
    }

    private fun observeConfig() {
        scope.launch {
            IslandConfig.tuning.collect { if (!calibrating) applyLayout() }
        }
        scope.launch {
            IslandConfig.calibrating.collect { c ->
                calibrating = c
                applyLayout()
            }
        }
    }

    private fun addOverlay() {
        if (overlayView != null) return
        // A stable, untouchable drawing surface keeps the shadow unclipped and avoids resizing
        // the rendered window every frame. A second, transparent window owns only pill touches.
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@IslandAccessibilityService)
            setViewTreeViewModelStoreOwner(this@IslandAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@IslandAccessibilityService)
            addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    publishCameraGuide()
                }
            }
            setContent {
                // Hidden surfaces must not keep equalizers, timers, or RemoteViews running.
                if (renderIsland) {
                    IslandRoot(bluetoothAudioEvent = bluetoothAudio.event.collectAsStateWithLifecycle().value, surfaceVisible = !shadeVisible, onSurfaceHidden = {
                        if (shadeVisible) {
                            shadeExitComplete = true
                            syncOverlayVisibility()
                        }
                    }, lockVisible = showingLock, locked = locked, keyguardShowing = keyguardShowing, dismissRequest = dismissRequest, foregroundPackage = foregroundPackage, onBoundsChanged = ::updateTouchBounds,
                        onSecondaryBoundsChanged = ::updateSecondaryBounds)
                }
            }
        }
        windowManager.addView(overlayView, baseParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            title = "Dynamic Island visuals"
        })
        touchParams = baseParams().apply {
            width = 1
            height = 1
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            title = "Dynamic Island touch target"
        }
        touchView = View(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnTouchListener { v, event -> forwardTouch(v, event) }
        }
        windowManager.addView(touchView, touchParams)
        applyLayout()
    }

    /**
     * Raw (display-space) position of the visuals window's origin. Usually (0, 0), but system
     * transforms such as one-handed mode translate every accessibility overlay, so raw
     * coordinates must be rebased onto the visuals window before they reach Compose.
     */
    private var overlayRawOriginX = 0f
    private var overlayRawOriginY = 0f
    private val receiverLocation = IntArray(2)
    private val overlayLocation = IntArray(2)

    private fun updateOverlayRawOrigin(receiver: View, event: MotionEvent) {
        val overlay = overlayView ?: return
        // rawX - x is the receiving window's raw origin. Removing the receiver's offset from
        // the visuals window (both measured in the same window-layout space) yields the
        // visuals window's raw origin, whatever transform the display area applies.
        receiver.getLocationOnScreen(receiverLocation)
        overlay.getLocationOnScreen(overlayLocation)
        overlayRawOriginX = event.rawX - event.x - (receiverLocation[0] - overlayLocation[0])
        overlayRawOriginY = event.rawY - event.y - (receiverLocation[1] - overlayLocation[1])
    }

    private fun forwardTouch(receiver: View, event: MotionEvent): Boolean {
                // The display-area transform cannot change mid-gesture; sample it on DOWN so later
                // events stay correct even while the touch window is being resized or moved.
                if (event.actionMasked == MotionEvent.ACTION_DOWN) updateOverlayRawOrigin(receiver, event)
                val overlayX = event.rawX - overlayRawOriginX
                val overlayY = event.rawY - overlayRawOriginY
                if (event.actionMasked == MotionEvent.ACTION_DOWN && captureOutside &&
                    !pillBounds.contains(overlayX.toInt(), overlayY.toInt())) {
                    swallowingOutsideGesture = true
                    dismissRequest++
                }
                if (swallowingOutsideGesture) {
                    // Keep ownership through UP/CANCEL even if the card finishes collapsing.
                    // Otherwise the tail of this gesture could reach the application below.
                    if (event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        swallowingOutsideGesture = false
                        syncTouchWindow()
                    }
                } else if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    dismissRequest++
                } else {
                    val forwarded = MotionEvent.obtain(event)
                    forwarded.offsetLocation(overlayX - event.x, overlayY - event.y)
                    overlayView?.dispatchTouchEvent(forwarded)
                    forwarded.recycle()
                }
        return true
    }

    private fun updateSecondaryBounds(bounds: List<ActivityTouchBounds>) {
        val keys = bounds.map { it.key }.toSet()
        secondaryTouches.keys.filter { it !in keys }.forEach { key ->
            secondaryTouches.remove(key)?.let { runCatching { windowManager.removeView(it.view) } }
        }
        bounds.forEach { bound ->
            val target = secondaryTouches.getOrPut(bound.key) {
                val params = baseParams().apply {
                    width = 1
                    height = 1
                    flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    title = "Dynamic Island secondary activity ${bound.key}"
                }
                val view = View(this).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    visibility = View.GONE
                }
                val target = SecondaryTouch(view, params)
                view.setOnTouchListener { v, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) target.gestureActive = true
                    forwardTouch(v, event)
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        target.gestureActive = false
                        syncSecondaryWindow()
                    }
                    true
                }
                windowManager.addView(view, params)
                target
            }
            target.available = bound.width > 0 && bound.height > 0
            // A held bubble retains its input stream while its visual travels into the main pill.
            if (target.available || !target.gestureActive) target.bounds.set(bound.left, bound.top,
                bound.left + bound.width, bound.top + bound.height)
        }
        syncSecondaryWindow()
    }

    private fun syncSecondaryWindow() {
        secondaryTouches.values.forEach { target ->
            val view = target.view
            val params = target.params
            val visible = islandVisible && !showingLock && !calibrating &&
                (target.gestureActive || (target.available && !captureOutside))
            view.visibility = if (visible) View.VISIBLE else View.GONE
            // Bounds keep following the animation in memory. A hidden input window
            // needs its latest geometry only when it becomes touchable again.
            if (!visible) return@forEach
            val width = target.bounds.width().coerceAtLeast(1)
            val height = target.bounds.height().coerceAtLeast(1)
            if (params.x != target.bounds.left || params.y != target.bounds.top || params.width != width || params.height != height) {
                params.x = target.bounds.left
                params.y = target.bounds.top
                params.width = width
                params.height = height
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun onDisplayActivityChanged(active: Boolean) {
        lockMonitorJob?.cancel()
        lockMonitorJob = null
        displayActive = active
        refreshLockState()
        if (!active) {
            swallowingOutsideGesture = false
            secondaryTouches.values.forEach { it.gestureActive = false }
            captureOutside = false
            syncTouchWindow()
        }
        syncOverlayVisibility()
        syncSecondaryWindow()
        lifecycleRegistry.currentState = if (active) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    }

    private fun updateTouchBounds(left: Int, top: Int, width: Int, height: Int, capture: Boolean) {
        hasPillBounds = width > 0 && height > 0
        pillBounds.set(left, top, left + width.coerceAtLeast(1), top + height.coerceAtLeast(1))
        captureOutside = capture
        syncTouchWindow()
    }

    private fun syncTouchWindow() {
        syncSecondaryWindow()
        val view = touchView ?: return
        val params = touchParams ?: return
        val visible = islandVisible && !showingLock && !calibrating && hasPillBounds
        view.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        val fullScreen = !calibrating && (captureOutside || swallowingOutsideGesture)
        val left = if (fullScreen) 0 else pillBounds.left
        val top = if (fullScreen) 0 else pillBounds.top
        val width = if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else pillBounds.width().coerceAtLeast(1)
        val height = if (fullScreen) WindowManager.LayoutParams.MATCH_PARENT else pillBounds.height().coerceAtLeast(1)
        if (params.x == left && params.y == top && params.width == width && params.height == height) return
        params.x = left
        params.y = top
        params.width = width
        params.height = height
        windowManager.updateViewLayout(view, params)
    }

    private fun applyLayout() {
        val view = touchView ?: return
        val params = touchParams ?: return
        params.flags = if (calibrating) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        windowManager.updateViewLayout(view, params)
        syncTouchWindow()
    }

    private fun baseParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        // Keep Compose's coordinate space at the physical top edge, including the cutout.
        fitInsetsTypes = 0
    }

    /** Read the real display cutout and publish its center/size so the UI can draw a guide. */
    private fun publishCameraGuide() {
        val density = resources.displayMetrics.density
        val metrics = windowManager.currentWindowMetrics
        val screenWidthPx = metrics.bounds.width()
        IslandConfig.setScreenWidthDp(screenWidthPx / density)
        val insets = metrics.windowInsets
        val topRadii = listOfNotNull(
            insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius,
            insets.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT)?.radius,
        )
        IslandConfig.setTopCornerRadiusDp(topRadii.maxOrNull()?.div(density))
        val cutout = insets.displayCutout
        val safeBounds = cutout?.boundingRectTop?.takeIf { !it.isEmpty }
        if (safeBounds == null) {
            IslandConfig.setCameraGuide(null)
            return
        }
        // As in DroidMirror, trim edge padding using the actual physical opening path.
        val cam = android.graphics.RectF(safeBounds)
        cutout.cutoutPath?.takeIf { !it.isEmpty }?.let { outline ->
            val clip = android.graphics.Path().apply { addRect(cam, android.graphics.Path.Direction.CW) }
            val tight = android.graphics.Path(outline)
            if (tight.op(clip, android.graphics.Path.Op.INTERSECT) && !tight.isEmpty) {
                tight.computeBounds(cam, true)
            }
        }
        IslandConfig.setCameraGuide(
            CameraGuide(
                centerXFromScreenCenterDp = (cam.centerX() - screenWidthPx / 2f) / density,
                centerYDp = cam.centerY() / density,
                radiusDp = maxOf(cam.width(), cam.height()) / 2f / density,
                horizontalRadiusDp = cam.width() / 2f / density,
                bottomDp = cam.bottom / density,
            )
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::windowManager.isInitialized || overlayView == null) return
        // Services survive configuration changes. Refresh dp geometry when density changes,
        // even if the physical pixel dimensions of the visual host remain identical.
        publishCameraGuide()
        updateOrientationVisibility()
    }

    private fun refreshLockState() {
        val showing = keyguard.isKeyguardLocked
        // Keyguard can remain visible after face authentication, awaiting a swipe.
        val requiresAuthentication = keyguard.isDeviceLocked
        locked = requiresAuthentication
        val showingChanged = showing != keyguardShowing
        keyguardShowing = showing
        if (displayActive && showing && requiresAuthentication && lockMonitorJob?.isActive != true) {
            // Face authentication need not send a window event. Sample only while the
            // interactive lock screen awaits authentication. After authentication, window
            // events and USER_PRESENT handle dismissal; no further polling is needed.
            lockMonitorJob = scope.launch {
                while (displayActive && keyguardShowing && locked) {
                    delay(100)
                    val wasShowing = keyguardShowing
                    refreshLockState()
                    if (wasShowing != keyguardShowing) syncOverlayVisibility()
                }
            }
        }
        if (!showingChanged) return
        unlockJob?.cancel()
        if (showing) {
            showingLock = true
            dismissRequest++
            captureOutside = false
        } else if (showingLock) {
            unlockJob = scope.launch {
                delay(1100)
                showingLock = false
                syncOverlayVisibility()
            }
        }
    }

    private var lastVisibilityDiagnostic: String? = null

    private fun syncOverlayVisibility() {
        val diagnostic = "visible=$islandVisible display=$displayActive portrait=$portraitOrientation " +
            "shade=$shadeVisible lock=$showingLock"
        if (diagnostic != lastVisibilityDiagnostic) {
            lastVisibilityDiagnostic = diagnostic
            android.util.Log.d("IslandVisibility", diagnostic)
        }
        // Dispose synchronously: a GONE view can pause recomposition before the hidden
        // subtree is removed, leaving delay-based timers or hosted widgets alive.
        if (!renderIsland) overlayView?.disposeComposition()
        overlayView?.visibility = if (renderIsland) View.VISIBLE else View.GONE
        syncTouchWindow()
    }

    /** Identify the focused full-screen System UI surface without reading notification text. */
    private fun updateShadeVisibility() {
        updateForegroundPackage()
        refreshLockState()
        val display = windowManager.currentWindowMetrics.bounds
        val visible = windows.any { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM || !window.isFocused) return@any false
            val bounds = android.graphics.Rect()
            window.getBoundsInScreen(bounds)
            if (!bounds.contains(display.centerX(), display.centerY()) ||
                bounds.width() < display.width() || bounds.height() < display.height()) return@any false
            // System windows often have no accessibility title. Check only root ownership;
            // this also suppresses the island on the full-screen lock screen.
            val root = window.root
            try {
                root?.packageName?.toString() == "com.android.systemui"
            } finally {
                @Suppress("DEPRECATION")
                root?.recycle()
            }
        }
        
        val enteringShade = visible && !shadeVisible
        if (visible != shadeVisible) shadeExitComplete = false
        shadeVisible = visible
        if (enteringShade) {
            dismissRequest++
            swallowingOutsideGesture = false
            secondaryTouches.values.forEach { it.gestureActive = false }
            captureOutside = false
        }
        syncOverlayVisibility()
    }

    /** Use application windows, rather than event ownership (which can be a keyboard,
     * toast, or notification shade). A shade keeps the underlying app's identity. */
    private fun updateForegroundPackage() {
        val applications = windows.filter {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !it.isInPictureInPictureMode
        }.sortedWith(compareByDescending<AccessibilityWindowInfo> { it.isFocused }
            .thenByDescending { it.isActive }.thenByDescending { it.layer })
        for (window in applications) {
            val root = window.root ?: continue
            val pkg = try {
                root.packageName?.toString()
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
            if (pkg.isNullOrBlank() || pkg == "android" || pkg == "com.android.systemui") continue
            if (foregroundPackage != pkg && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                android.util.Log.d("IslandSwitch", "foreground=$pkg")
            foregroundPackage = pkg
            return
        }
        // Transient system surfaces may hide all application roots. Preserve the
        // last app until windows settle instead of briefly showing its activity.
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!displayActive) return
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            updateShadeVisibility()
            foregroundRefreshJob?.cancel()
            foregroundRefreshJob = scope.launch {
                // Window roots can lag the event during launch/close animations.
                delay(150)
                if (displayActive) updateForegroundPackage()
                delay(350)
                if (displayActive) updateForegroundPackage()
            }
        }
    }

    override fun onInterrupt() { /* not needed */ }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        displayManager.unregisterDisplayListener(rotationListener)
        displayMonitor.stop()
        bluetoothAudio.stop()
        if (lockReceiverRegistered) {
            unregisterReceiver(lockReceiver)
            lockReceiverRegistered = false
        }
        scope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        touchView?.let { runCatching { windowManager.removeView(it) } }
        secondaryTouches.values.forEach { runCatching { windowManager.removeView(it.view) } }
        secondaryTouches.clear()
        touchView = null
        touchParams = null
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        IslandConfig.setCalibrating(false)
        viewModelStore.clear()
    }
}
