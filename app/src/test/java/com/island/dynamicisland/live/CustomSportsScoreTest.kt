package com.island.dynamicisland.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSportsScoreTest {
    @Test fun preservesDownDistanceAndFieldPositionBelowClockAndPeriod() {
        val score = SportsScore("TENN", "21", "SC", "17", "14:51 - 4th",
            secondaryStatus = "1st & 10\nSC 44")
        assertEquals("14:51 - 4th\n1st & 10\nSC 44", score.gameStatus)
    }

    @Test fun trimsBlankStatusLinesWithoutDroppingGameInformation() {
        val score = SportsScore(null, "0", null, "0", " Halftime ",
            secondaryStatus = "\n Halftime \n ")
        assertEquals("Halftime", score.gameStatus)
        assertEquals("", score.copy(status = null, secondaryStatus = null).gameStatus)
    }

    @Test fun formatsScoresAndTitles() {
        val score = SportsScore("TENN", "21", "SC", "17", "Halftime")
        assertEquals("21 - 17", score.formattedScore)
        assertEquals("TENN vs SC", score.summaryTitle)
    }

    @Test fun handlesMissingTeamNames() {
        val score = SportsScore(null, "3", null, "1", "Final")
        assertEquals("3 - 1", score.formattedScore)
        assertEquals(null, score.summaryTitle)
    }

    @Test fun recognizesFinalGameStatuses() {
        assertTrue(isSportsFinalStatus("Final"))
        assertTrue(isSportsFinalStatus("FINAL"))
        assertTrue(isSportsFinalStatus("final"))
        assertTrue(isSportsFinalStatus("Final/OT"))
        assertTrue(isSportsFinalStatus("Final - OT"))
        assertTrue(isSportsFinalStatus("Final (OT)"))
        assertTrue(isSportsFinalStatus("Final/SO"))
        assertTrue(isSportsFinalStatus("Final Score"))
        assertTrue(isSportsFinalStatus("Full Time"))
        assertTrue(isSportsFinalStatus("Full-Time"))
        assertTrue(isSportsFinalStatus("FT"))
        assertTrue(isSportsFinalStatus("Game Ended"))
        assertTrue(isSportsFinalStatus("Postgame"))

        assertFalse(isSportsFinalStatus("1st Qtr"))
        assertFalse(isSportsFinalStatus("2nd 4:12"))
        assertFalse(isSportsFinalStatus("Halftime"))
        assertFalse(isSportsFinalStatus("Pregame"))
        assertFalse(isSportsFinalStatus(null))
        assertFalse(isSportsFinalStatus(""))
    }
    @Test fun espnLeaderRowsFollowScoreboardSidesAndPreserveTheirData() {
        val players = listOf(SportsPlayer("E. Grunkemeyer", "4/7 · 17 YDS"), SportsPlayer("T. Reynolds", "3/6 · 13 YDS"))
        val assigned = assignSportsPlayerSides(players, espn = true)
        assertEquals(listOf(1, 2), assigned.map { it.teamSide })
        assertEquals(players.map { it.name to it.stats }, assigned.map { it.name to it.stats })
    }

    @Test fun otherSourcesAndAmbiguousPanelsDoNotGetGuessedTeams() {
        val players = listOf(SportsPlayer("A", "10"), SportsPlayer("B", "20"))
        assertEquals(players, assignSportsPlayerSides(players, espn = false))
        assertEquals(players.take(1), assignSportsPlayerSides(players.take(1), espn = true))
        val explicit = listOf(players[0].copy(teamSide = 2), players[1].copy(teamSide = 1))
        assertEquals(explicit, assignSportsPlayerSides(explicit, espn = true))
    }
    @Test fun sectionTitlesRemoveOnlyTheLeadersWord() {
        assertEquals("Rushing", sportsSectionTitle("Rushing Leaders"))
        assertEquals("Passing", sportsSectionTitle(" Passing   LEADERS "))
        assertEquals("Receiving", sportsSectionTitle("Receiving"))
        assertEquals("Leadership", sportsSectionTitle("Leadership"))
        assertEquals("", sportsSectionTitle("Leaders"))
    }

}
