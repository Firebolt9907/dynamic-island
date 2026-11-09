package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class FootballPlayHistoryTest {
    private fun score(yard: String, team: Int? = 1, status: String = "1st 5:18") =
        SportsScore("WIS", "0", "Iowa", "3", status, secondaryStatus = "1st & 10\n$yard",
            possession = team?.let { SportsPossession(it, "Possession") })

    @Test fun tracksGainsLossesAndMidfieldCrossingsWithoutCountingClockTicksAsPlays() {
        val history = FootballPlayHistory()
        assertNull(history.remember("game", score("WIS 45")).lastFootballPlay)
        val gain = history.remember("game", score("Iowa 48")).lastFootballPlay!!
        assertEquals(7, gain.gain)
        assertEquals(gain, history.remember("game", score("Iowa 48", status = "1st 5:12")).lastFootballPlay)
        assertEquals(-3, history.remember("game", score("WIS 49")).lastFootballPlay!!.gain)
    }

    @Test fun secondTeamAttacksInOppositeDirection() {
        val history = FootballPlayHistory()
        history.remember("game", score("Iowa 29", 2))
        assertEquals(10, history.remember("game", score("Iowa 39", 2)).lastFootballPlay!!.gain)
    }

    @Test fun clearsAcrossTurnoversMissingDataScoreChangesAndPeriods() {
        val history = FootballPlayHistory()
        history.remember("game", score("WIS 20"))
        assertNull(history.remember("game", score("WIS 30", 2)).lastFootballPlay)
        assertNull(history.remember("game", score("WIS 40", null)).lastFootballPlay)
        assertNull(history.remember("game", score("WIS 45", 2)).lastFootballPlay)
        assertNull(history.remember("game", score("Iowa 45", 2).copy(scoreOne = "7")).lastFootballPlay)
        assertNull(history.remember("game", score("Iowa 40", 2, "2nd 15:00")).lastFootballPlay)
        history.forget("game")
        assertNull(history.remember("game", score("Iowa 30", 2)).lastFootballPlay)
    }
}
