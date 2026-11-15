package com.island.dynamicisland.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-tunable placement of the collapsed island so it can be aligned to any device's
 * punch-hole / camera cutout. All values are in dp.
 *
 * - [centerXOffsetDp]  horizontal shift from the screen's horizontal center (+ = right).
 * - [topOffsetDp]      distance from the very top of the screen.
 * - [collapsedWidthDp] / [collapsedHeightDp]  size of the resting pill that hugs the camera.
 */
data class IslandTuning(
    val centerXOffsetDp: Float = 0f,
    val topOffsetDp: Float = 7f,
    val collapsedWidthDp: Float = 168f,
    val collapsedHeightDp: Float = 37f,
    /** Corner radius of the expanded music card. */
    val expandedCornerDp: Float = 38f,
    val useDeviceCorners: Boolean = true,
    /** Activities arriving or leaving collapse every bubble into the camera and re-emerge. */
    val reflowThroughCamera: Boolean = true,
) {
    companion object {
        val Default = IslandTuning()
        // Slider ranges used by the calibration UI.
        val centerXRange = -160f..160f
        val topRange = 0f..80f
        val widthRange = 80f..320f
        val heightRange = 24f..64f
        val cornerRange = 12f..56f
    }
}

/**
 * Where the physical camera cutout actually is, measured from the real display cutout.
 * All values in dp. [centerXFromScreenCenterDp] is +right of the screen's horizontal center.
 */
data class CameraGuide(
    val centerXFromScreenCenterDp: Float,
    val centerYDp: Float,
    val radiusDp: Float,
    val horizontalRadiusDp: Float = radiusDp,
    val bottomDp: Float = centerYDp + radiusDp,
)

/** Process-wide, persisted island placement shared by the overlay window and the Compose UI. */
object IslandConfig {
    private const val PREFS = "island_config"
    private var prefs: android.content.SharedPreferences? = null

    private val _tuning = MutableStateFlow(IslandTuning())
    val tuning: StateFlow<IslandTuning> = _tuning.asStateFlow()

    /** True while the user is aligning the island; enables the full-screen camera guide. */
    private val _calibrating = MutableStateFlow(false)
    val calibrating: StateFlow<Boolean> = _calibrating.asStateFlow()

    /** Real camera-cutout location, published by the overlay service. */
    private val _cameraGuide = MutableStateFlow<CameraGuide?>(null)
    val cameraGuide: StateFlow<CameraGuide?> = _cameraGuide.asStateFlow()

    /** Physical display width in dp, refreshed by the overlay host after display changes. */
    private val _screenWidthDp = MutableStateFlow(0f)
    val screenWidthDp: StateFlow<Float> = _screenWidthDp.asStateFlow()

    private val _topCornerRadiusDp = MutableStateFlow<Float?>(null)
    val topCornerRadiusDp: StateFlow<Float?> = _topCornerRadiusDp.asStateFlow()
    fun setTopCornerRadiusDp(radius: Float?) { _topCornerRadiusDp.value = radius }

    fun setCalibrating(value: Boolean) { _calibrating.value = value }
    fun setCameraGuide(guide: CameraGuide?) { _cameraGuide.value = guide }
    fun setScreenWidthDp(value: Float) { _screenWidthDp.value = value }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _tuning.value = IslandTuning(
            centerXOffsetDp = p.getFloat("cx", IslandTuning.Default.centerXOffsetDp),
            topOffsetDp = p.getFloat("top", IslandTuning.Default.topOffsetDp),
            collapsedWidthDp = p.getFloat("w", IslandTuning.Default.collapsedWidthDp),
            collapsedHeightDp = p.getFloat("h", IslandTuning.Default.collapsedHeightDp),
            expandedCornerDp = p.getFloat("corner", IslandTuning.Default.expandedCornerDp),
            useDeviceCorners = p.getBoolean("deviceCorners", !p.contains("corner")),
            reflowThroughCamera = p.getBoolean("reflowThroughCamera", IslandTuning.Default.reflowThroughCamera),
        )
    }

    /**
     * Apply [t] live. Slider drags pass `persist = false` on every frame and persist once
     * when the drag ends, rather than rewriting SharedPreferences dozens of times a second.
     */
    fun update(t: IslandTuning, persist: Boolean = true) {
        _tuning.value = t
        if (persist) persist()
    }

    /** Write the current in-memory tuning to disk. */
    fun persist() {
        val t = _tuning.value
        prefs?.edit()
            ?.putFloat("cx", t.centerXOffsetDp)
            ?.putFloat("top", t.topOffsetDp)
            ?.putFloat("w", t.collapsedWidthDp)
            ?.putFloat("h", t.collapsedHeightDp)
            ?.putFloat("corner", t.expandedCornerDp)
            ?.putBoolean("deviceCorners", t.useDeviceCorners)
            ?.putBoolean("reflowThroughCamera", t.reflowThroughCamera)
            ?.apply()
    }

    fun reset() = update(IslandTuning.Default)
}
