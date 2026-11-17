package com.island.dynamicisland.audio

/**
 * Holds recent bar levels with their capture times so they can be shown when the listener
 * actually hears them, rather than when the Visualizer saw them.
 *
 * Android already delays Visualizer captures by the output's nominal latency, but Bluetooth
 * sinks add buffering beyond what the audio HAL reports (measured ~63 ms extra on a Pixel 8
 * with A2DP headphones), so without this the bars lead the music.
 */
internal class LevelDelay(private val width: Int, private val capacity: Int = 16) {
    private val levels = FloatArray(width * capacity)
    private val times = LongArray(capacity)
    private var count = 0
    private var head = 0

    fun clear() {
        count = 0
        head = 0
    }

    fun push(timeMs: Long, values: FloatArray) {
        System.arraycopy(values, 0, levels, head * width, width)
        times[head] = timeMs
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    /**
     * Copy the newest levels captured at or before [timeMs] into [out]. Returns false (leaving
     * [out] untouched) when everything held is newer, i.e. nothing is due yet.
     */
    fun read(timeMs: Long, out: FloatArray): Boolean {
        for (i in 1..count) {
            val slot = (head - i + capacity) % capacity
            if (times[slot] <= timeMs) {
                System.arraycopy(levels, slot * width, out, 0, width)
                return true
            }
        }
        return false
    }
}
