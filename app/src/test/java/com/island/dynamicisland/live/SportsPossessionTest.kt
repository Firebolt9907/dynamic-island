package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class SportsPossessionTest {
    @Test fun iconRecognitionSurvivesTintOpacityAndAntialiasing() {
        val icon = List(100) { if (it in 20..79) 255 else 0 }
        assertTrue(sportsTurnIconMatches(icon, icon.map { it / 2 }))
        assertTrue(sportsTurnIconMatches(icon, icon.mapIndexed { index, value -> if (index == 20) 100 else value }))
        assertFalse(sportsTurnIconMatches(icon, List(100) { 0 }))
        assertFalse(sportsTurnIconMatches(icon, List(100) { if (it % 10 < 3) 255 else 0 }))
    }
    @Test fun recognizesExplicitSourceTurnSemanticsAcrossSports() {
        assertEquals("Possession", sportsPossessionLabel("home_possession", ""))
        assertEquals("Possession", sportsPossessionLabel("indicator", "Has the ball"))
        assertEquals("Serving", sportsPossessionLabel("team_two_serving", ""))
        assertEquals("Serving", sportsPossessionLabel("serve_indicator", ""))
        assertEquals("Batting", sportsPossessionLabel("away_batting", ""))
    }

    @Test fun winnerAndGenericIndicatorsDoNotImplyPossession() {
        assertNull(sportsPossessionLabel("iv_live_team_one_indicator", ""))
        assertNull(sportsPossessionLabel("winner_indicator", "Possession"))
        assertNull(sportsPossessionLabel("team_one_favorite", ""))
        assertNull(sportsPossessionLabel("team_one_score", "21"))
    }

    @Test fun ambiguousOrAbsentTurnsClearTheMarkerInsteadOfRetainingStalePossession() {
        val home = SportsPossession(1, "Possession")
        val away = SportsPossession(2, "Possession")
        assertEquals(home, resolveSportsPossession(listOf(home, home)))
        assertEquals(away, resolveSportsPossession(listOf(away)))
        assertNull(resolveSportsPossession(listOf(home, away)))
        assertNull(resolveSportsPossession(emptyList()))
        assertNull(resolveSportsPossession(listOf(home.copy(team = 0))))
    }
}
