package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test

class NumericTickerTest {
    @Test fun onlyChangedDigitsMoveAndSeparatorsStayStill() {
        assertEquals(listOf(TickingDigit(4, '8', '9')), tickingDigits("01:58", "01:59"))
        assertTrue(tickingDigits("01:59", "01:59").isEmpty())
        assertTrue(tickingDigits("1 min", "1 sec").isEmpty())
    }

    @Test fun secondAndMinuteCarriesAnimateEachChangedPlace() {
        assertEquals(listOf(TickingDigit(1, '1', '2'), TickingDigit(3, '5', '0'),
            TickingDigit(4, '9', '0')), tickingDigits("01:59", "02:00"))
        assertEquals(listOf(TickingDigit(2, '5', '0'), TickingDigit(3, '9', '0'),
            TickingDigit(5, '5', '0'), TickingDigit(6, '9', '0')), tickingDigits("59:59", "1:00:00"))
    }

    @Test fun countdownAndScoreChangesWorkInEitherDirection() {
        assertEquals(listOf(TickingDigit(4, '9', '8')), tickingDigits("01:59", "01:58"))
        assertEquals(listOf(TickingDigit(1, '9', '0'), TickingDigit(2, '9', '0')), tickingDigits("99", "100"))
        assertEquals(listOf(TickingDigit(0, '1', '2')), tickingDigits("19", "29"))
    }

    @Test fun directionFollowsTheWholeNumberAcrossCarriesAndBorrows() {
        for ((before, after) in listOf("19" to "20", "99" to "100",
            "01:59" to "02:00", "59:59" to "1:00:00", "9.9%" to "10.0%")) {
            tickingDigits(before, after).forEach {
                assertEquals(1f, numericTickDirection(before, after, it.index), 0f)
            }
            tickingDigits(after, before).forEach {
                assertEquals(-1f, numericTickDirection(after, before, it.index), 0f)
            }
        }
    }

    @Test fun separateReadoutsCanMoveInOppositeDirections() {
        assertEquals(1f, numericTickDirection("2 / 9", "3 / 8", 0), 0f)
        assertEquals(-1f, numericTickDirection("2 / 9", "3 / 8", 4), 0f)
        assertEquals(-1f, numericTickDirection("1.9%", "1.10%", 3), 0f)
    }

    @Test fun changedDigitsStartFromLeftToRightAndAllFinish() {
        val count = tickingDigits("01:59", "02:00").size
        val early = 24f / numericTickTotalDurationMs(count)
        assertTrue(numericTickDigitProgress(early, 0, count) > 0f)
        assertEquals(0f, numericTickDigitProgress(early, 1, count), 0f)
        assertEquals(0f, numericTickDigitProgress(early, 2, count), 0f)
        val middle = .5f
        assertTrue(numericTickDigitProgress(middle, 0, count) > numericTickDigitProgress(middle, 1, count))
        assertTrue(numericTickDigitProgress(middle, 1, count) > numericTickDigitProgress(middle, 2, count))
        repeat(count) { assertEquals(1f, numericTickDigitProgress(1f, it, count), 0f) }
        assertEquals(.5f, numericTickDigitProgress(.5f, 0, 1), 0f)
        assertTrue(numericTickTotalDurationMs(20) < 600)
        assertEquals(1f, numericTickDigitProgress(1f, 19, 20), 0f)
    }

    @Test fun hundredthsNeverRestartTheSecondsAnimation() {
        assertEquals("01:59", numericTickKey("01:59.95", true))
        assertEquals(numericTickKey("01:59.00", true), numericTickKey("01:59.95", true))
        assertNotEquals(numericTickKey("01:59.95", true), numericTickKey("02:00.00", true))
        assertEquals("12.5%", numericTickKey("12.5%", false))
    }
    @Test fun compactClockTargetsThirtyFramesWithoutPollingBetweenTicks() {
        assertEquals(30, COMPACT_TICK_FPS)
        assertEquals(33L, compactTickDelayMs(1_033_333_333L, 1_000_000_000L))
        assertEquals(23L, compactTickDelayMs(1_033_333_333L, 1_010_000_000L))
        assertEquals(0L, compactTickDelayMs(1_033_333_333L, 1_040_000_000L))
    }
    @Test fun compactClocksShareTheirFrameGridAndSkipMissedSlots() {
        assertEquals(compactTickTargetNanos(0L, 1_001_000_000L),
            compactTickTargetNanos(0L, 1_010_000_000L))
        val target = compactTickTargetNanos(0L, 1_001_000_000L)
        assertTrue(compactTickTargetNanos(target, target - 500_000L) > target)
        assertTrue(compactTickTargetNanos(target, target + 200_000_000L) > target + 200_000_000L)
    }
}
