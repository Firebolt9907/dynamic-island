package com.island.dynamicisland.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class SpectrumBandsTest {
    private val sr = 48_000f
    private val size = 1024

    /** 8-bit unsigned as-played capture; [signal] is in full-scale units (1.0 = 0 dBFS peak). */
    private fun capture(signal: (Double) -> Double): ByteArray = ByteArray(size) { i ->
        (128 + Math.round(signal(i / sr.toDouble()) * 127).coerceIn(-128, 127)).toInt().toByte()
    }

    private fun dbfs(db: Double) = 10.0.pow(db / 20)

    private fun sine(hz: Double, db: Double, phase: Double = 0.0) = { t: Double -> dbfs(db) * sin(2 * PI * hz * t + phase) }

    /** Acoustic-guitar-like partials from 400 Hz up (no low end), loudest at [db] dBFS. */
    private fun strings(db: Double, seed: Long = 1): (Double) -> Double {
        val rnd = Random(seed)
        val partials = DoubleArray(12) { 400.0 + it * 310.0 + rnd.nextDouble() * 40 }
        val phases = DoubleArray(12) { rnd.nextDouble() * 6 }
        return { t -> partials.indices.sumOf { dbfs(db - it) * sin(2 * PI * partials[it] * t + phases[it]) } }
    }

    /** A sung vowel: 220 Hz fundamental with formant-weighted harmonics up to ~5 kHz. */
    private fun voice(db: Double): (Double) -> Double = { t ->
        (1..22).sumOf { h ->
            val f = 220.0 * h
            val formant = if (f in 500.0..1_000.0 || f in 1_800.0..3_200.0) 0.0 else -12.0
            dbfs(db + formant - h * 0.5) * sin(2 * PI * f * t + h)
        }
    }

    private operator fun ((Double) -> Double).plus(other: (Double) -> Double): (Double) -> Double = { t -> this(t) + other(t) }

    /**
     * A sung "ah" at pitch [f0]: harmonics up to ~5 kHz shaped by vowel resonances near 700 Hz,
     * 1.2 kHz and 2.6 kHz, as in both male and female voices.
     */
    private fun vowel(f0: Double, db: Double): (Double) -> Double {
        val count = (5_000 / f0).toInt()
        return { t ->
            (1..count).sumOf { h ->
                val f = h * f0
                val resonance = listOf(700.0 to 130.0, 1_200.0 to 150.0, 2_600.0 to 250.0)
                    .maxOf { (c, w) -> -12.0 * ((f - c) / w).pow(2).coerceAtMost(1.0) }
                dbfs(db + resonance - 6.0 * kotlin.math.ln(h.toDouble())) * sin(2 * PI * f * t + h * 0.7)
            }
        }
    }

    /** A bass guitar note: strong fundamental, harmonics fading out by ~600 Hz. */
    private fun bassGuitar(f0: Double, db: Double): (Double) -> Double = { t ->
        (1..6).sumOf { h -> dbfs(db - 8.0 * (h - 1)) * sin(2 * PI * f0 * h * t + h) }
    }

    /** Feed [frames] captures through one instance and return the final bars. */
    private fun bars(frames: Int = 20, gainDb: Float = 0f, lift: Boolean = true, signal: () -> (Double) -> Double): FloatArray {
        val bands = SpectrumBands(size, sr, liftMaleVoice = lift)
        val out = FloatArray(SpectrumBands.COUNT)
        repeat(frames) { assertTrue(bands.levels(capture(signal()), out, gainDb)) }
        return out
    }

    @Test fun bandsCoverKickToAirAtTheDeviceCaptureSize() {
        // 46.9 Hz bins: the kick band keeps both its 47 and 94 Hz bins.
        assertArrayEquals(intArrayOf(1, 3, 5, 9, 22, 60, 235), SpectrumBands(size, sr).ranges)
    }

    @Test fun steadyLoudBassShowsOnTheLeftOnlyAndLeavesRoomForHits() {
        val l = bars { sine(60.0, -12.0) }
        assertTrue("held loud bass should show but leave room for hits: ${l[0]}", l[0] in 0.4f..0.6f)
        for (b in 2 until SpectrumBands.COUNT) assertTrue("bar $b = ${l[b]}", l[b] < 0.05f)
    }

    @Test fun kicksOverAHeldBasslineJumpWellAboveIt() {
        val bands = SpectrumBands(size, sr)
        val out = FloatArray(SpectrumBands.COUNT)
        val between = ArrayList<Float>()
        val hits = ArrayList<Float>()
        // A bassline at -24 dBFS with a -12 dBFS kick every 10 captures (two per second).
        for (i in 0 until 80) {
            val kick = i % 10 == 0
            bands.levels(capture(sine(110.0, -24.0) + (if (kick) sine(55.0, -12.0) else { _ -> 0.0 })), out)
            if (i >= 20 && kick) hits.add(out[0])
            if (i >= 20 && i % 10 == 5) between.add(out[0])
        }
        assertTrue("kicks should hit the top: $hits", hits.min() > 0.9f)
        assertTrue("and sit well above the held bass between them: $between", between.max() < 0.45f)
        assertTrue("which should still be visible: $between", between.min() > 0.15f)
    }

    @Test fun aKickRegistersClearlyOverASustainedNote() {
        val bands = SpectrumBands(size, sr)
        val out = FloatArray(SpectrumBands.COUNT)
        // A held 60 Hz note at -20 dBFS, then a kick 6 dB above it.
        repeat(30) { bands.levels(capture(sine(60.0, -20.0)), out) }
        val held = out[0]
        bands.levels(capture(sine(60.0, -20.0) + sine(50.0, -16.0)), out)
        assertTrue("kick over a sustain should add a clear jump: $held -> ${out[0]}", out[0] - held > 0.3f)
    }

    @Test fun aKickSpreadsIntoTheSecondAndThirdBars() {
        val bands = SpectrumBands(size, sr)
        val out = FloatArray(SpectrumBands.COUNT)
        repeat(10) { bands.levels(capture(strings(-36.0)), out) }
        val before = out.copyOf()
        bands.levels(capture(strings(-36.0) + sine(55.0, -12.0)), out)
        assertTrue("second bar should take most of the kick: ${before[1]} -> ${out[1]}", out[1] - before[1] > 0.4f)
        assertTrue("third bar should take some: ${before[2]} -> ${out[2]}", out[2] - before[2] > 0.25f)
        assertTrue("and less than the second: ${out.toList()}", out[2] < out[1])
    }

    @Test fun loudTrebleFillsTheRightBarOnly() {
        val l = bars { sine(7_000.0, -12.0) }
        assertTrue("air bar: ${l[5]}", l[5] > 0.9f)
        for (b in 0 until 4) assertTrue("bar $b = ${l[b]}", l[b] < 0.05f)
    }

    @Test fun eachBandsToneTopsItsOwnBar() {
        val centers = doubleArrayOf(70.0, 160.0, 300.0, 650.0, 1_700.0, 6_000.0)
        for ((band, hz) in centers.withIndex()) {
            val l = bars { sine(hz, -20.0) }
            assertEquals("a $hz Hz tone should top bar $band: ${l.toList()}", band, l.indices.maxBy { l[it] })
        }
    }

    @Test fun quietSongWithoutBassKeepsTheLeftBarsAsDots() {
        val l = bars { strings(-36.0) }
        assertEquals(0f, l[0], 0.02f)
        assertEquals(0f, l[1], 0.02f)
        assertTrue("strings should still show: ${l.toList()}", (2 until 6).any { l[it] > 0.05f })
    }

    @Test fun quietInstrumentsWithoutVocalsKeepTheUpperBarsLow() {
        // The same quiet guitar bed as the vocal test below, with nobody singing.
        val l = bars { strings(-36.0) }
        assertTrue("guitar-only upper bars should stay low: ${l.toList()}", (2 until 6).map { l[it] }.average() < 0.25)
    }

    @Test fun moderateVoiceOverQuietInstrumentsLiftsTheRightBarsALot() {
        val instruments = bars { strings(-36.0) }
        val singing = bars { strings(-36.0) + voice(-24.0) }
        val lift = (0 until 6).map { singing[it] - instruments[it] }
        val right = (3 until 6).map { lift[it] }
        assertTrue("right three bars should rise a lot on average: $lift", right.average() > 0.3)
        assertTrue("no invented bass: ${singing[0]}", singing[0] < 0.05f)
    }

    @Test fun quieterMusicDrawsShorterBars() {
        val loud = bars { strings(-24.0) + sine(70.0, -16.0) }
        val quiet = bars { strings(-36.0) + sine(70.0, -28.0) }
        for (b in 0 until SpectrumBands.COUNT) assertTrue("bar $b: ${quiet[b]} vs ${loud[b]}", quiet[b] <= loud[b])
        assertTrue("the kick bar should drop clearly: ${quiet[0]} vs ${loud[0]}", loud[0] - quiet[0] > 0.15f)
    }

    @Test fun volumeGainMakesBarsIndependentOfTheSlider() {
        // The same song through a phone volume 12 dB lower, with that attenuation added back.
        val full = bars { strings(-24.0) + sine(70.0, -16.0) }
        val turnedDown = bars(gainDb = 12f) { strings(-36.0) + sine(70.0, -28.0) }
        assertArrayEquals(full, turnedDown, 0.06f)
    }

    @Test fun aHitLandsInOneCaptureAndClearsSoonAfter() {
        val bands = SpectrumBands(size, sr)
        val out = FloatArray(SpectrumBands.COUNT)
        repeat(10) { bands.levels(capture(strings(-36.0)), out) }
        bands.levels(capture(strings(-36.0) + sine(60.0, -12.0)), out)
        assertTrue("kick should land at once: ${out[0]}", out[0] > 0.85f)
        repeat(3) { bands.levels(capture(strings(-36.0)), out) }
        assertTrue("and clear within a few captures: ${out[0]}", out[0] < 0.3f)
    }

    @Test fun digitalSilenceIsReportedAsNoSignal() {
        val out = FloatArray(SpectrumBands.COUNT) { 0.7f }
        assertFalse(SpectrumBands(size, sr).levels(ByteArray(size) { 128.toByte() }, out))
        assertArrayEquals(FloatArray(SpectrumBands.COUNT), out, 0f)
    }

    @Test fun smallCaptureSizesStillGiveEveryBandABin() {
        val r = SpectrumBands(128, sr).ranges
        for (b in 0 until SpectrumBands.COUNT) assertTrue("band $b empty in ${r.toList()}", r[b + 1] > r[b])
    }

    @Test fun hitHeightIsProportionalToHowStrongTheHitIs() {
        // The same kick pattern, loud and 12 dB softer, each over silence-level bass.
        fun peak(kickDb: Double): Float {
            val bands = SpectrumBands(size, sr)
            val out = FloatArray(SpectrumBands.COUNT)
            var top = 0f
            for (i in 0 until 60) {
                bands.levels(capture(strings(-40.0) + (if (i % 10 == 0) sine(55.0, kickDb) else { _ -> 0.0 })), out)
                if (i >= 20 && i % 10 == 0) top = maxOf(top, out[0])
            }
            return top
        }
        val loud = peak(-12.0)
        val soft = peak(-24.0)
        assertTrue("a loud kick should reach the top: $loud", loud > 0.9f)
        assertTrue("a soft kick should stay well below it: $soft vs $loud", soft < 0.6f && soft > 0.15f)
    }

    private fun maleConfidence(signal: () -> (Double) -> Double): Float {
        val bands = SpectrumBands(size, sr)
        val out = FloatArray(SpectrumBands.COUNT)
        repeat(20) { bands.levels(capture(signal()), out) }
        return bands.maleVoice
    }

    @Test fun aMaleVoiceIsLiftedIntoTheVoiceBars() {
        val signal = { strings(-40.0) + vowel(120.0, -22.0) }
        val with = bars(lift = true, signal = signal)
        val without = bars(lift = false, signal = signal)
        assertTrue("detector should be confident: ${maleConfidence(signal)}", maleConfidence(signal) > 0.6f)
        for (b in 3..4) assertTrue("bar ${b + 1} should rise clearly: ${without[b]} -> ${with[b]}", with[b] - without[b] > 0.08f)
        for (b in listOf(0, 1, 2, 5)) assertEquals("bar ${b + 1} unchanged", without[b], with[b], 0.001f)
    }

    @Test fun aFemaleVoiceIsLeftAlone() {
        val signal = { strings(-40.0) + vowel(230.0, -22.0) }
        assertTrue("detector should stay quiet: ${maleConfidence(signal)}", maleConfidence(signal) < 0.15f)
        assertArrayEquals(bars(lift = false, signal = signal), bars(lift = true, signal = signal), 0.03f)
    }

    @Test fun aBassGuitarIsNotMistakenForAMaleVoice() {
        val signal = { bassGuitar(90.0, -18.0) }
        assertTrue("detector should stay quiet: ${maleConfidence(signal)}", maleConfidence(signal) < 0.15f)
        assertArrayEquals(bars(lift = false, signal = signal), bars(lift = true, signal = signal), 0.03f)
    }

    @Test fun instrumentsAloneAreNotMistakenForAMaleVoice() {
        val signal = { strings(-30.0) }
        assertTrue("detector should stay quiet: ${maleConfidence(signal)}", maleConfidence(signal) < 0.15f)
    }
}
