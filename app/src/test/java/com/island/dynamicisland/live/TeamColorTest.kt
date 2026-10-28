package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class TeamColorTest {
    @Test fun whiteOutlinesAndTransparentPixelsDoNotWashOutTheTeamColor() {
        val red = 0xFF73000A.toInt()
        val pixels = IntArray(200) { if (it < 160) -1 else if (it < 190) 0x0000FF00 else red }
        assertEquals(red, dominantTeamColor(pixels))
    }

    @Test fun teamsKeepIndependentDominantColors() {
        assertEquals(0xFFFF8200.toInt(), dominantTeamColor(intArrayOf(0xFFFF8200.toInt())))
        assertEquals(0xFF73000A.toInt(), dominantTeamColor(intArrayOf(0xFF73000A.toInt())))
    }

    @Test fun monochromeOrMissingLogosUseNeutralInsteadOfInventingAColor() {
        assertEquals(0xFFE4E4E7.toInt(), dominantTeamColor(intArrayOf(-1, 0xFF000000.toInt(), 0)))
        assertEquals(0xFFE4E4E7.toInt(), dominantTeamColor(intArrayOf()))
    }

    @Test fun darkTeamScoreIsLiftedWithoutLosingItsHue() {
        val source = 0xFF73000A.toInt()
        val score = readableTeamColor(source)
        assertTrue((score ushr 16 and 255) > (source ushr 16 and 255))
        assertTrue((score ushr 16 and 255) > (score and 255))
        assertTrue((score and 255) > (score ushr 8 and 255))
        assertEquals(0xFFE4E4E7.toInt(), readableTeamColor(0xFFE4E4E7.toInt()))
    }
}
