package com.island.dynamicisland.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log

/**
 * Real-time bass-to-treble levels of whatever is playing, from the output-mix Visualizer.
 *
 * The Visualizer is an audio effect, not a recording: it never appears in
 * AudioManager.getActiveRecordingConfigurations(), starts no RECORD_AUDIO app-op, and leaves
 * players' output paths alone, so apps that drop Dolby Atmos or spatial audio when they detect
 * capture don't react to it. It only exists while an equalizer is on screen for a playing
 * track; the overlay disposes its composition when the screen turns off, which releases it.
 *
 * Captures are held back by the output's extra latency (see [BLUETOOTH_EXTRA_DELAY_MS]) so bass
 * hits show when they are heard, not when the mixer produced them.
 *
 * Main thread only: [acquire], [release], and the capture callbacks all run on the main looper,
 * so [targets] needs no synchronization.
 */
internal object LiveSpectrum {
    private const val TAG = "LiveSpectrum"
    private const val CAPTURE_SIZE = 1024

    /**
     * An effect created while no track is active (e.g. the instant play is pressed, before the
     * player restarts its stream) stays deaf even after Android moves it to the music's output,
     * and an audioserver restart kills it silently. Reopen when nothing has been heard for this
     * long, doubling up to [MAX_REOPEN_MS] so a genuinely muted phone doesn't churn.
     */
    private const val FIRST_REOPEN_MS = 600L
    private const val MAX_REOPEN_MS = 10_000L

    /**
     * Captures normally arrive every ~50 ms. On the Pixel 8 every Visualizer shares one capture
     * thread in the audio HAL, and releasing any of them (another app's, or the system's own)
     * stops it for the rest ("visualizer has been stopped"). No captures at all for this long
     * means that happened: reopen right away, with no backoff.
     */
    private const val STALLED_MS = 250L
    private const val FIRST_CAPTURE_MS = 800L

    /** A capture this late (two missed intervals) counts as missing; bars then fall by [STALE_DECAY] per frame. */
    private const val STALE_MS = 120L
    private const val STALE_DECAY = 0.85f

    /**
     * Holders call [maintain] from their frame loop. If captures keep arriving but no frame has
     * been drawn for this long, the bars are off screen without having been disposed (system
     * animations off, or the overlay window removed by the system), so stop capturing until
     * frames resume.
     */
    private const val UNDRAWN_MS = 2_000L

    /**
     * Extra delay before showing a capture when media plays over Bluetooth. Android already
     * delays Visualizer data by the output's nominal latency, but the Bluetooth sink adds more:
     * on a Pixel 8 with A2DP earbuds, test bursts reached the ears 58–68 ms after the Visualizer
     * reported them. Wired and built-in outputs need no extra delay.
     */
    private const val BLUETOOTH_EXTRA_DELAY_MS = 65L

    /**
     * Normalized captures read ~14 dB hotter per band than as-played ones of the same music
     * (measured on a Pixel 8 over A2DP, consistent across bands), so subtracting this puts
     * speaker and wired output on the same fixed scale as Bluetooth.
     */
    private const val NORMALIZED_OFFSET_DB = 14f

    /** How often to re-check which output media is routed to (the output switcher can move it). */
    private const val ROUTE_CHECK_MS = 1_000L

    /**
     * Bar levels due now, bass (index 0) to treble: the capture the listener is hearing at this
     * moment. Updated by [maintain]; reused in place, so copy before keeping.
     */
    val targets = FloatArray(SpectrumBands.COUNT)

    private val captured = FloatArray(SpectrumBands.COUNT)
    private val pending = LevelDelay(SpectrumBands.COUNT)
    private var outputDelayMs = 0L
    /** Shift applied to band levels so both capture modes share one fixed scale. */
    private var levelOffsetDb = 0f
    private var routeCheckedAtMs = 0L
    private var audio: android.media.AudioManager? = null

    /** Uptime of the latest non-silent capture; zero frames mean muted output or no access. */
    var lastAudibleAtMs = 0L
        private set

    private var openedAtMs = 0L
    private var lastCaptureAtMs = 0L
    private var staleFactor = 1f
    private var debugCaptures = 0
    private var debugCpuNanos = 0L
    private var reopenAfterMs = FIRST_REOPEN_MS
    private var maintainedAtMs = 0L
    private var pausedUndrawn = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private var visualizer: Visualizer? = null
    private var users = 0

    fun permitted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Start (or share) the capture. Returns false when live data is unavailable; then don't [release]. */
    fun acquire(context: Context): Boolean {
        if (!permitted(context)) return false
        if (audio == null) audio = context.applicationContext.getSystemService(android.media.AudioManager::class.java)
        maintainedAtMs = SystemClock.uptimeMillis()
        if (visualizer == null && !open()) return false
        pausedUndrawn = false
        users++
        return true
    }

    /** Call every frame while holding a capture: resumes a paused capture and reopens a deaf one. */
    fun maintain() {
        if (users == 0) return
        val now = SystemClock.uptimeMillis()
        maintainedAtMs = now
        pending.read(now - outputDelayMs, targets)
        // Missing captures: let bars fall toward rest rather than freeze, and reopen a stalled
        // Visualizer immediately.
        val sinceCapture = now - maxOf(lastCaptureAtMs, openedAtMs)
        staleFactor = if (sinceCapture > STALE_MS) staleFactor * STALE_DECAY else 1f
        if (staleFactor < 1f) for (b in targets.indices) targets[b] *= staleFactor
        // A new Visualizer takes ~300 ms to deliver its first capture; only judge stalls after that.
        val stallAfter = if (lastCaptureAtMs < openedAtMs) FIRST_CAPTURE_MS else STALLED_MS
        if (visualizer != null && sinceCapture > stallAfter) {
            Log.d(TAG, "captures stalled; reopening")
            close(keepUsers = true)
            open()
            return
        }
        if (now - routeCheckedAtMs >= ROUTE_CHECK_MS) {
            routeCheckedAtMs = now
            val (delay, _) = readOutput()
            if (delay != outputDelayMs && visualizer != null) {
                // Android sets the effect's own latency compensation when it attaches, and the
                // capture mode depends on the output, so a new output needs a fresh Visualizer.
                Log.d(TAG, "output changed; extra delay ${delay} ms, reopening")
                close(keepUsers = true)
                open()
                return
            }
        }
        if (pausedUndrawn) {
            Log.d(TAG, "frames resumed; reopening")
            pausedUndrawn = false
            open()
            return
        }
        if (now - maxOf(lastAudibleAtMs, openedAtMs) < reopenAfterMs) return
        Log.d(TAG, "no audio for ${reopenAfterMs} ms; reopening")
        close(keepUsers = true)
        open()
        reopenAfterMs = (reopenAfterMs * 2).coerceAtMost(MAX_REOPEN_MS)
    }

    fun release() {
        if (users == 0) return
        if (--users == 0) close()
    }

    private fun open(): Boolean {
        val v = try {
            Visualizer(0)
        } catch (e: RuntimeException) {
            // Engine busy, effect unavailable on this device, or permission revoked mid-session.
            Log.w(TAG, "Visualizer unavailable", e)
            return false
        }
        return try {
            v.enabled = false
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = CAPTURE_SIZE.coerceIn(range[0], range[1])
            // Bluetooth sinks apply the volume themselves, so as-played captures there carry the
            // music's real loudness. On the speaker and wired outputs the phone attenuates first,
            // and at normal volumes the 8-bit as-played capture rounds to silence, so those use
            // normalized captures (independent of volume, but each buffer rescaled to full scale,
            // which weakens overall loud/quiet differences while keeping hits and band balance).
            val (delay, bluetooth) = readOutput()
            v.scalingMode = if (bluetooth) Visualizer.SCALING_MODE_AS_PLAYED else Visualizer.SCALING_MODE_NORMALIZED
            levelOffsetDb = if (bluetooth) 0f else -NORMALIZED_OFFSET_DB
            // The pitched-voice lift is on. On real mixes it fires for a male voice ("Stronger"),
            // and also for a female voice over acoustic guitar ("betty"), where it locks onto the
            // guitar's low strings; there it still moves bars 4–5 with the singing, which reads
            // better, so it is kept deliberately.
            val spectrum = SpectrumBands(v.captureSize, v.samplingRate / 1000f)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer, waveform: ByteArray, samplingRate: Int) {
                    if (this@LiveSpectrum.visualizer !== visualizer) return
                    if (SystemClock.uptimeMillis() - maintainedAtMs > UNDRAWN_MS) {
                        // Release outside the callback that is still delivering this capture.
                        main.post { pauseUndrawn(visualizer) }
                        return
                    }
                    val now = SystemClock.uptimeMillis()
                    lastCaptureAtMs = now
                    val cpuStart = if (com.island.dynamicisland.BuildConfig.DEBUG) android.os.Debug.threadCpuTimeNanos() else 0L
                    val audible = spectrum.levels(waveform, captured, levelOffsetDb)
                    pending.push(now, captured)
                    if (com.island.dynamicisland.BuildConfig.DEBUG) debugCpuNanos += android.os.Debug.threadCpuTimeNanos() - cpuStart
                    if (com.island.dynamicisland.BuildConfig.DEBUG && ++debugCaptures % 10 == 0) {
                        // Twice a second: raw band levels and the bars they produce, for tuning.
                        Log.d("LiveBands", "dB=" + spectrum.lastBandDb.joinToString(",") { "%.0f".format(it) } +
                            " bars=" + captured.joinToString(",") { "%.2f".format(it) } +
                            " male=" + "%.2f".format(spectrum.maleVoice) +
                            " parts=" + spectrum.lastVoiceParts.joinToString(",") { "%.2f".format(it) } +
                            " cpuUsPerCapture=" + debugCpuNanos / 1000 / 10)
                        debugCpuNanos = 0L
                    }
                    if (audible) {
                        if (lastAudibleAtMs == 0L) Log.d(TAG, "hearing audio")
                        lastAudibleAtMs = now
                        reopenAfterMs = FIRST_REOPEN_MS
                    }
                }
                override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) = Unit
            }, Visualizer.getMaxCaptureRate(), true, false)
            v.enabled = true
            visualizer = v
            openedAtMs = SystemClock.uptimeMillis()
            outputDelayMs = delay
            routeCheckedAtMs = openedAtMs
            Log.d(TAG, "opened: ${v.captureSize} samples at ${v.samplingRate / 1000} Hz, " +
                "${if (bluetooth) "Bluetooth, as played" else "phone output, normalized"}, extra delay ${delay} ms")
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Visualizer setup failed", e)
            v.release()
            false
        }
    }

    /**
     * Extra display delay for where media plays right now, and whether that is Bluetooth.
     * Bluetooth sinks apply the volume themselves (measured on a Pixel 8: capture level held
     * steady across volume steps 7–13); the speaker and wired outputs are attenuated in the phone.
     */
    private fun readOutput(): Pair<Long, Boolean> {
        val am = audio ?: return 0L to false
        return runCatching {
            val type = if (android.os.Build.VERSION.SDK_INT >= 33) {
                val media = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()
                am.getAudioDevicesForAttributes(media).firstOrNull()?.type
            } else {
                @Suppress("DEPRECATION")
                if (am.isBluetoothA2dpOn) android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP else null
            } ?: android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            if (type in bluetoothOutputs) BLUETOOTH_EXTRA_DELAY_MS to true else 0L to false
        }.getOrDefault(0L to false)
    }

    private val bluetoothOutputs = setOf(
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
        android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
        android.media.AudioDeviceInfo.TYPE_BLE_BROADCAST,
        android.media.AudioDeviceInfo.TYPE_HEARING_AID,
    )

    private fun pauseUndrawn(stale: Visualizer) {
        if (visualizer !== stale || users == 0) return
        Log.d(TAG, "no frames drawn for ${UNDRAWN_MS} ms; pausing capture")
        close(keepUsers = true)
        pausedUndrawn = true
    }

    private fun close(keepUsers: Boolean = false) {
        visualizer?.let {
            runCatching { it.enabled = false }
            it.release()
        }
        visualizer = null
        targets.fill(0f)
        pending.clear()
        // A reopen keeps the last-heard time so a brief gap doesn't read as lost signal.
        if (!keepUsers) {
            Log.d(TAG, "released")
            pausedUndrawn = false
            users = 0
            lastAudibleAtMs = 0L
            reopenAfterMs = FIRST_REOPEN_MS
        }
    }
}
