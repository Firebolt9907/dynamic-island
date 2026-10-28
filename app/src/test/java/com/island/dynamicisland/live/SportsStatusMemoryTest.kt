package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class SportsStatusMemoryTest {
    private var now = 0L
    private val memory = SportsStatusMemory { now }
    private fun game(context: String? = null) = SportsScore("TENN", "24", "SC", "17", "9:28 - 4th", secondaryStatus = context)

    @Test fun rotatingStatsPanelsRetainLatestDownAndFieldPositionAcrossClockTicks() {
        memory.remember("game", game("4th & 16\n50"))
        now = 30_000
        assertEquals("4th & 16\n50", memory.remember("game", game().copy(status = "9:27 - 4th")).secondaryStatus)
        memory.remember("game", game("1st & 10\nSC 25"))
        assertEquals("1st & 10\nSC 25", memory.remember("game", game()).secondaryStatus)
    }

    @Test fun statsOnlyRefreshesDoNotKeepStaleContextAliveForever() {
        memory.remember("game", game("4th & 16\n50"))
        now = 100_000
        assertNotNull(memory.remember("game", game()).secondaryStatus)
        now = 120_001
        assertNull(memory.remember("game", game()).secondaryStatus)
    }

    @Test fun scorePhaseAndMatchChangesClearOldContext() {
        for (next in listOf(game().copy(scoreOne = "27"), game().copy(status = "8:00 - OT"),
            game().copy(teamOne = "OTHER"), game().copy(status = "Final"))) {
            memory.remember("game", game("4th & 16\n50"))
            assertNull(memory.remember("game", next).secondaryStatus)
        }
    }

    @Test fun separateGamesAndRemovalNeverShareContext() {
        memory.remember("one", game("4th & 16\n50"))
        assertNull(memory.remember("two", game()).secondaryStatus)
        memory.forget("one")
        assertNull(memory.remember("one", game()).secondaryStatus)
        memory.remember("one", game("1st & 10"))
        memory.clear()
        assertNull(memory.remember("one", game()).secondaryStatus)
    }

    @Test fun otherSportsRetainSourceAuthoredContextWithoutFootballAssumptions() {
        val baseball = SportsScore("BOS", "3", "NYY", "1", "Top 7th", secondaryStatus = "2 outs\n2-1 count")
        memory.remember("baseball", baseball)
        assertEquals(baseball.secondaryStatus, memory.remember("baseball", baseball.copy(secondaryStatus = "")).secondaryStatus)
        assertNull(memory.remember("baseball", baseball.copy(status = "Bottom 7th", secondaryStatus = null)).secondaryStatus)
    }
}
