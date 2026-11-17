package com.island.dynamicisland.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * Turns Visualizer waveform captures into bar levels ordered bass (left) to treble (right).
 *
 * Levels are absolute, like the iOS Dynamic Island: captures are taken as played (not
 * peak-normalized), so each bar shows how loud its band really is on one fixed scale. A quiet
 * acoustic song keeps its bass bars as dots and its upper bars moderate, while a voice mixed
 * above the instruments pushes the right bars up; a heavy kick fills the left bars. Where the
 * phone applies the volume slider before the Visualizer (speaker, wired), the caller passes
 * that attenuation back in as `gainDb`; Bluetooth sinks apply volume themselves.
 *
 * Bands are log-spaced and each sums its bins' power, so pink noise reads flat; each band has
 * its own fixed floor and range ([floorDb], [rangeDb]) calibrated on real music.
 *
 * The Visualizer's own FFT is unwindowed, so a loud kick leaks into every band. A Hann
 * window here keeps bass energy in the bass bars. One instance per capture stream; not
 * thread-safe, so call it from the thread that receives captures.
 */
internal class SpectrumBands(private val size: Int, samplingRateHz: Float, private val liftMaleVoice: Boolean = true) {
    init { require(size >= 64 && size and (size - 1) == 0) { "capture size must be a power of two" } }

    private val window = FloatArray(size) { i -> (0.5 - 0.5 * cos(2 * PI * i / size)).toFloat() }
    private val cosTable = FloatArray(size / 2) { i -> cos(2 * PI * i / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { i -> -sin(2 * PI * i / size).toFloat() }
    private val bitReverse = IntArray(size).also { table ->
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) table[i] = Integer.reverse(i) ushr (32 - bits)
    }
    /** Expected rounding-noise power per FFT bin: size · mean(window²) · 1/12 LSB². */
    private val quantizationNoisePerBin = window.sumOf { (it * it).toDouble() }.toFloat() / 12f
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val db = FloatArray(COUNT)

    /** The latest capture's raw band dB, for debug diagnostics. Reused in place. */
    val lastBandDb: FloatArray get() = db

    /** Smoothed 0..1 confidence that a male voice is singing or rapping right now. */
    var maleVoice = 0f
        private set

    /** The latest detection's raw ingredients, for debug tuning: pitch, vowel periodicity, balance, Hz. */
    val lastVoiceParts = FloatArray(4)

    private val binHz = samplingRateHz / size
    private val power = FloatArray(size / 2 + 1)
    private val acRe = FloatArray(size)
    private val acIm = FloatArray(size)

    /** The Hann window's own autocorrelation, to undo its taper when comparing lags. */
    private val windowAutocorrelation = FloatArray(size).also { ac ->
        for (lag in 0 until size) {
            var sum = 0.0
            for (i in 0 until size - lag) sum += window[i] * window[i + lag]
            ac[lag] = sum.toFloat()
        }
    }

    // Male pitch range as autocorrelation lags. Lags stay below half the capture so the
    // FFT's circular wrap-around (lag l also sees lag size − l) stays negligible.
    private val maleLagMin = (samplingRateHz / MALE_MAX_HZ).toInt()
    private val maleLagMax = minOf((samplingRateHz / MALE_MIN_HZ).toInt(), size / 2)
    private val formantLo = kotlin.math.ceil(FORMANT_LO_HZ / binHz).toInt()
    private val formantHi = kotlin.math.ceil(FORMANT_HI_HZ / binHz).toInt().coerceAtMost(size / 2)

    /** Per-band smoothed dB across captures; NaN until the first capture. */
    private val smoothed = FloatArray(COUNT) { Float.NaN }

    /** Slow running average of each bass band's dB, the baseline a hit is measured against. */
    private val bassBaseline = FloatArray(BASS_BARS) { Float.NaN }

    /** This capture's per-band level after the fixed scale (0..1), and the bass bands' hit strength. */
    private val level = FloatArray(COUNT)
    private val hit = FloatArray(BASS_BARS)
    private val steady = FloatArray(BASS_BARS)

    /** Bin index ranges per band: band b owns bins [ranges[b], ranges[b + 1]). */
    val ranges: IntArray = run {
        val binHz = samplingRateHz / size
        val maxBin = size / 2
        // Bin k is centered on k * binHz; a band takes every bin centered inside [lo, hi).
        val ranges = IntArray(COUNT + 1) { b -> kotlin.math.ceil(edgesHz[b] / binHz).toInt().coerceIn(1, maxBin) }
        // At small capture sizes low bands can share a bin; keep each band at least one bin wide.
        for (b in 1..COUNT) if (ranges[b] <= ranges[b - 1]) ranges[b] = (ranges[b - 1] + 1).coerceAtMost(maxBin)
        ranges
    }

    /**
     * Raw windowed band power in dB from an 8-bit unsigned waveform (128 is silence).
     * Returns false for digital silence, which means muted output or no access, not a quiet song.
     */
    fun bandDb(waveform: ByteArray, out: FloatArray = db): Boolean {
        var any = false
        for (i in 0 until size) {
            val s = (waveform[i].toInt() and 0xFF) - 128
            if (s != 0) any = true
            val j = bitReverse[i]
            re[j] = s * window[i]
            im[j] = 0f
        }
        if (!any) {
            out.fill(Float.NEGATIVE_INFINITY)
            return false
        }
        fft(re, im)
        for (k in 0..size / 2) power[k] = re[k] * re[k] + im[k] * im[k]
        for (b in 0 until COUNT) {
            var power = 0f
            for (k in ranges[b] until ranges[b + 1]) power += re[k] * re[k] + im[k] * im[k]
            // Remove the 8-bit rounding noise every bin carries, so quiet captures (or volume
            // gain added back) don't light up wide treble bands with quantization hiss.
            power -= quantizationNoisePerBin * NOISE_MARGIN * (ranges[b + 1] - ranges[b])
            out[b] = if (power > 0f) 10f * log10(power) else Float.NEGATIVE_INFINITY
        }
        return true
    }

    /**
     * 0..1 bar levels into [out] from an as-played capture. [gainDb] adds back any volume
     * attenuation applied before the Visualizer. Returns false for digital silence (muted output
     * or no access); all bars then rest at 0.
     */
    fun levels(waveform: ByteArray, out: FloatArray, gainDb: Float = 0f): Boolean {
        if (!bandDb(waveform, db)) {
            out.fill(0f)
            return false
        }
        for (b in 0 until COUNT) {
            // Light smoothing across captures takes the edge off single-snapshot noise in the
            // bass bands (2–3 FFT bins each) without softening hits.
            val raw = if (db[b].isFinite()) db[b] else SILENT_DB
            val previous = smoothed[b]
            smoothed[b] = if (previous.isNaN()) raw
            else previous + (raw - previous) * (if (raw > previous) RISE else FALL)
            level[b] = ((smoothed[b] + gainDb - floorDb[b]) / rangeDb[b]).coerceIn(0f, 1f)
            if (b < BASS_BARS) bassHit(b, raw, gainDb)
        }
        val upper = { b: Int -> Math.pow(level[b].toDouble(), UPPER_SHAPE).toFloat() }
        // Kick: mostly hits. Bass: its own hits plus most of the kick's, so a kick spreads right.
        out[0] = steady[0] * STEADY_WEIGHT + hit[0] * HIT_WEIGHT
        out[1] = steady[1] * STEADY_WEIGHT + maxOf(hit[1], hit[0] * KICK_SPILL[1]) * HIT_WEIGHT
        // Third bar: upper bass into low mids (220–400 Hz), plus the tail of the kick.
        out[2] = upper(2) + hit[0] * KICK_SPILL[2] * HIT_WEIGHT
        // The right three carry the voice's body, clarity, and presence (and cymbals above).
        for (b in 3 until COUNT) out[b] = upper(b)
        // A male voice's pitch and first harmonics sit in the bass/low-mid bands, where the beat
        // hides them. When one is detected, also show that low energy in the voice bars, as if
        // transposed up into the range a female voice already reaches. On real mixes this also
        // fires for chords with a male-range root under a female voice (acoustic guitar under
        // "betty"); that still tracks the singing in bars 4–5, which was preferred, so it stays.
        if (liftMaleVoice) {
            updateMaleVoice()
            // Lift from 220–400 Hz only (a male voice's lower harmonics; 110–220 Hz is mostly beat
            // and bassline), and combine rather than add, so a bar nears the top only when both
            // its own level and the lift are strong. Adding pinned bars 4–5 on "Stronger".
            val lift = MALE_LIFT * maleVoice * level[2]
            out[3] = 1f - (1f - out[3]) * (1f - lift)
            out[4] = 1f - (1f - out[4]) * (1f - lift)
        }
        for (b in 0 until COUNT) out[b] = out[b].coerceIn(0f, 1f)
        return true
    }

    /**
     * Splits a bass band into a sustained part (its level over the last ~300 ms, so a held
     * bassline shows, modestly) and a hit: how many dB this capture rises above that, scaled by
     * the hit's absolute level. So a kick registers over a held note, and its height tracks how
     * strong it really is: a soft kick in a quiet passage stays small, and a bass-free song has
     * nothing to show.
     */
    private fun bassHit(b: Int, raw: Float, gainDb: Float) {
        val baseDb = bassBaseline[b].takeUnless { it.isNaN() } ?: raw
        bassBaseline[b] = baseDb + (raw - baseDb) * BASELINE_RATE
        val baseLevel = ((baseDb + gainDb - floorDb[b]) / rangeDb[b]).coerceIn(0f, 1f)
        steady[b] = Math.pow(baseLevel.toDouble(), BASS_SHAPE).toFloat()
        hit[b] = ((raw - baseDb) / HIT_RANGE_DB).coerceIn(0f, 1f) * level[b]
    }

    /**
     * Male voice detection from this capture's power spectrum. A voiced male syllable repeats at
     * its pitch (~94–180 Hz), and so does its vowel resonance band (400 Hz–2.8 kHz), because each
     * glottal pulse excites both. A bass guitar or kick has the low pitch but little periodic
     * energy up there. A female voice also repeats at twice its period, landing in the male
     * range, so a strong repeat at half the lag marks it as higher-pitched and it is ignored.
     */
    private fun updateMaleVoice() {
        val full = autocorrelation(1, formantHi)
        var best = 0f
        var bestLag = 0
        if (full > 0f) for (lag in maleLagMin..maleLagMax) {
            val r = normalized(lag, full)
            if (r > best) { best = r; bestLag = lag }
        }
        var raw = 0f
        lastVoiceParts.fill(0f)
        lastVoiceParts[0] = best
        if (bestLag > 0) lastVoiceParts[3] = binHz * size / bestLag
        if (bestLag > 0) {
            var half = 0f
            for (lag in bestLag / 2 - 2..bestLag / 2 + 2) half = maxOf(half, normalized(lag, full))
            if (half < OCTAVE_GUARD * best) {
                val mid = autocorrelation(formantLo, formantHi)
                var formant = 0f
                if (mid > 0f) for (lag in bestLag - 3..bestLag + 3) formant = maxOf(formant, normalized(lag, mid))
                // A real male voice has energy at its pitch and in its vowel band, at comparable
                // levels. Chords whose partials merely repeat at a male-range rate have nothing
                // down at that pitch; a bare bass note has almost nothing in the vowel band.
                val balance = energyBalance(bestLag)
                lastVoiceParts[1] = formant
                lastVoiceParts[2] = balance
                raw = ((best - PITCH_FLOOR) / PITCH_SPAN).coerceIn(0f, 1f) *
                    ((formant - FORMANT_FLOOR) / FORMANT_SPAN).coerceIn(0f, 1f) * balance
            }
        }
        maleVoice += (raw - maleVoice) * (if (raw > maleVoice) VOICE_RISE else VOICE_FALL)
    }

    /**
     * 0..1: whether there is real energy at the detected pitch (fundamental and first harmonic)
     * and in the vowel band, neither more than ~15 dB below the other.
     */
    private fun energyBalance(lag: Int): Float {
        val f0 = size.toFloat() / lag // pitch, in FFT bins
        var low = 0f
        for (k in kotlin.math.floor(f0 * 0.8f).toInt().coerceAtLeast(1)..kotlin.math.ceil(f0 * 2.2f).toInt()) low += power[k]
        var vowel = 0f
        for (k in formantLo until formantHi) vowel += power[k]
        if (low <= 0f || vowel <= 0f) return 0f
        val balanceDb = 10f * log10(low / vowel)
        val lowEnough = ((balanceDb + BALANCE_LIMIT_DB) / BALANCE_SPAN_DB).coerceIn(0f, 1f)
        val vowelEnough = ((BALANCE_LIMIT_DB - balanceDb) / BALANCE_SPAN_DB).coerceIn(0f, 1f)
        return lowEnough * vowelEnough
    }

    /** Autocorrelation of the power spectrum limited to bins [lo, hi) into [acRe]; returns lag 0. */
    private fun autocorrelation(lo: Int, hi: Int): Float {
        for (k in 0 until size) {
            val f = if (k <= size / 2) k else size - k
            acRe[bitReverse[k]] = if (f in lo until hi) power[f] else 0f
            acIm[bitReverse[k]] = 0f
        }
        fft(acRe, acIm)
        return acRe[0]
    }

    private fun normalized(lag: Int, zero: Float): Float =
        acRe[lag] / zero / (windowAutocorrelation[lag] / windowAutocorrelation[0])

    /** In-place iterative radix-2 FFT over [re]/[im], which are already in bit-reversed order. */
    private fun fft(re: FloatArray, im: FloatArray) {
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
                start += len
            }
            len *= 2
        }
    }

    companion object {
        const val COUNT = 6

        /**
         * Measurement band edges in Hz: kick, bass, low mids (a voice's pitch), vowel body
         * (400 Hz–1 kHz), vowel clarity (1–2.8 kHz), and presence/sibilance/cymbals above.
         */
        val edgesHz = floatArrayOf(30f, 110f, 220f, 400f, 1000f, 2800f, 11000f)

        /**
         * Fixed per-band scale (dB, 8-bit as-played capture; a full-scale sine reads ~90 dB): a
         * band at its floor rests, and its range above that is a full bar.
         *
         * Measured as played on a Pixel 8. Loud pop-rock bass reads p10/p50/p90 ≈ 55/66/73 dB in
         * the kick band; a 26 dB range keeps loud steady bass below the top, leaving full height
         * to hits (added separately), while quiet and bass-free songs rest.
         *
         * Right three bands, from "betty": its harmonica-and-guitar interlude measured ~67/66/54
         * dB (medians), its building, sung bridge ~71/67/64 dB with p90s of ~77/74/71 dB. These
         * scales put the interlude at about half height and the bridge at ~0.55–0.65, and reserve
         * full height for peaks a few dB above the bridge's loudest moments. The 1–2.8 kHz bar
         * rises least for the voice, since harmonica is loud in exactly that range. The far-right
         * band (presence, sibilance, cymbals) stays busy through most of a song, so its scale is
         * gentler still: ~0.35 in the interlude, ~0.6 in the bridge, ~0.8 on its loud moments.
         */
        val floorDb = floatArrayOf(50f, 50f, 50f, 43f, 43.5f, 25f)
        val rangeDb = floatArrayOf(26f, 26f, 28f, 35f, 33f, 52f)

        /** Per-capture smoothing of band dB (20 Hz): fraction of the gap closed when rising / falling. */
        const val RISE = 0.97f
        const val FALL = 0.87f

        /** Rounding noise is subtracted with this much headroom, covering its random spread. */
        const val NOISE_MARGIN = 2f

        /** The two left bands (kick and bass) get hit emphasis; the rest show steady level. */
        const val BASS_BARS = 2

        /** How much of a kick's hit reaches bars 0, 1 and 2, so a kick spreads to the right. */
        val KICK_SPILL = floatArrayOf(1f, 0.8f, 0.5f)


        /**
         * Sustained bass contributes this share of a bass bar (a typical held note ~0.3, a loud
         * one ~0.55), leaving room above it for hits, which add their own strength.
         */
        const val STEADY_WEIGHT = 0.55f
        const val HIT_WEIGHT = 1f

        /** A rise this many dB above the recent bass level is a full-strength hit. */
        const val HIT_RANGE_DB = 10f

        /** Per-capture rate of the bass baseline: ~300 ms memory at 20 Hz. */
        const val BASELINE_RATE = 0.15f

        const val BASS_SHAPE = 1.15

        /** Male voice pitch range searched, in Hz, and the vowel resonance band checked. */
        const val MALE_MIN_HZ = 85f
        const val MALE_MAX_HZ = 180f
        const val FORMANT_LO_HZ = 400f
        const val FORMANT_HI_HZ = 2800f

        /** Periodicity strength (0..1) that starts to count, and the span over which it becomes certain. */
        const val PITCH_FLOOR = 0.3f
        const val PITCH_SPAN = 0.35f
        const val FORMANT_FLOOR = 0.2f
        const val FORMANT_SPAN = 0.35f

        /** A repeat at half the lag this strong means the pitch is really an octave higher. */
        const val OCTAVE_GUARD = 0.8f

        /**
         * Confidence smoothing per capture (20 Hz). Only voiced syllables show a clear pitch, and
         * rap is full of unvoiced consonants, so confidence rises quickly and decays over ~1 s:
         * a few clear syllables per second keep the lift on through a phrase.
         */
        const val VOICE_RISE = 0.5f
        const val VOICE_FALL = 0.06f
        const val MALE_LIFT = 0.8f

        /** Pitch-band vs vowel-band energy may differ by up to this many dB, fading over the span. */
        const val BALANCE_LIMIT_DB = 15f
        const val BALANCE_SPAN_DB = 5f

        /**
         * Upper four bars: an expansion curve, so moderate levels (instruments under no vocal)
         * sit low while loud ones (a voice mixed above the band) still reach high: 0.5 maps to
         * 0.29, 0.85 to 0.75.
         */
        const val UPPER_SHAPE = 1.8

        private const val SILENT_DB = 0f
    }
}
