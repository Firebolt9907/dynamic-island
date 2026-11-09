package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class SportsGameContextTest {
    private fun score(context: String, possession: Int? = null) = SportsScore("WIS", "0", "Iowa", "3",
        "1st 5:18", secondaryStatus = context,
        possession = possession?.let { SportsPossession(it, "Possession") })

    @Test fun footballUsesNamedSideAndPossessionForDirection() {
        val iowa = footballFieldPosition(score("1st & 10\nIOWA 29", 2))!!
        assertEquals(71f, iowa.yards, .001f)
        assertEquals(61f, iowa.firstDownYards!!, .001f)
        val wis = footballFieldPosition(score("2nd & 7\nIowa 29", 1))!!
        assertEquals(78f, wis.firstDownYards!!, .001f)
        assertEquals(29f, footballFieldPosition(score("1st & 10\nWIS 29", 1))!!.yards, .001f)
    }

    @Test fun fieldCanShowBallWithoutGuessingFirstDownDirection() {
        val field = footballFieldPosition(score("3rd & 4\nIowa 29"))!!
        assertEquals(71f, field.yards, .001f)
        assertNull(field.firstDownYards)
        assertNull(field.attackingTeam)
    }

    @Test fun supportsGoalToGoAndMidfield() {
        assertEquals(100f, footballFieldPosition(score("1st & Goal\nIowa 5", 1))!!.firstDownYards!!, .001f)
        assertEquals(0f, footballFieldPosition(score("2nd & Goal\nWIS 5", 2))!!.firstDownYards!!, .001f)
        assertEquals(50f, footballFieldPosition(score("4th & 1\nMidfield 50", 1))!!.yards, .001f)
    }

    @Test fun ambiguousUnsupportedAndFinishedGamesKeepText() {
        for (context in listOf("Iowa 29", "1st & 10\nUnknown 29", "1st & 10\nIowa 65")) {
            assertNull(footballFieldPosition(score(context)))
        }
        assertNull(footballFieldPosition(score("1st & 10\nIowa 29", 1).copy(status = "Final")))
    }

    @Test fun onlyExplicitSetScoreFieldsQualify() {
        assertTrue(isSportsSetScoreField("team_one_set_score"))
        assertTrue(isSportsSetScoreField("set_1_score"))
        assertTrue(isSportsSetScoreField("team_two_score_set_2"))
        assertFalse(isSportsSetScoreField("reset_score"))
        assertFalse(isSportsSetScoreField("team_one_score"))
    }

    @Test fun outsRequireAnExplicitPublishedCount() {
        assertEquals(2, baseballOuts("Top 3rd · 2 Outs"))
        assertEquals(1, baseballOuts("1 out"))
        assertEquals(0, baseballOuts("0 outs"))
        assertNull(baseballOuts("Top 3rd"))
        assertNull(baseballOuts("4 outs"))
    }
}
