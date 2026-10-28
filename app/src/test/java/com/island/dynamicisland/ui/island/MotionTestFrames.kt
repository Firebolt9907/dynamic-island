package com.island.dynamicisland.ui.island

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
internal class MotionTestFrames {
    private val pending = mutableListOf<CancellableContinuation<Long>>()
    private var time = 0L
    val pendingRequests: Int get() = pending.size
    val clock = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            val nanos = suspendCancellableCoroutine<Long> { continuation ->
                pending.add(continuation)
                continuation.invokeOnCancellation { pending.remove(continuation) }
            }
            return onFrame(nanos)
        }
    }
    fun step(scope: TestScope, millis: Long) {
        scope.runCurrent()
        time += millis * 1_000_000L
        val waiters = pending.toList()
        pending.clear()
        waiters.filter { it.isActive }.forEach { it.resume(time) }
        scope.runCurrent()
    }
}
