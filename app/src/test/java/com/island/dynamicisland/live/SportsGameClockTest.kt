package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class SportsGameClockTest {
    @Test fun recognizesExplicitGameAndPeriodClocks() {
        for (id in listOf("tv_game_clock", "match_clock", "period_clock", "tv_status_primary", "game_timer"))
            assertTrue(id, isSportsGameClockField(id))
    }

    @Test fun rejectsOtherTimersAndUnidentifiedClocks() {
        for (id in listOf("shot_clock", "game_shot_clock", "play_clock", "playclock", "timeout_timer",
            "pregame_clock", "countdown_to_game_clock", "timer", "score"))
            assertFalse(id, isSportsGameClockField(id))
    }
}
