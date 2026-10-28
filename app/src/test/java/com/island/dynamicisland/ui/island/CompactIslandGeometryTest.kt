package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test

class CompactIslandGeometryTest {
    @Test fun primarySportsTeamsClearThePaddedCameraWithOneTwoOrThreeActivities() {
        val height = 40f
        val leading = 48f
        val trailing = 42f
        val edge = height * .2f
        for (count in 1..3) for (offset in listOf(-8f, 0f, 8f)) {
            val radius = 8f
            val paddedRadius = radius + 3f
            val grouped = count == 2
            val width = CompactIslandGeometry.width(height, paddedRadius, offset, trailing, grouped, leading)
            val center = if (grouped) CompactIslandGeometry.centerFromCameraLeft(width, paddedRadius, offset) else 0f
            val cameraLeft = width / 2f + offset - center - paddedRadius
            val cameraRight = width / 2f + offset - center + paddedRadius
            val firstX = edge + if (grouped) sportsCameraSideSpace(width, edge, leading + trailing, 0f) else 0f
            val secondX = width - edge - trailing
            assertTrue(firstX + leading <= cameraLeft + .001f || firstX >= cameraRight - .001f)
            assertTrue(secondX >= cameraRight - .001f)
        }
    }

    @Test fun sportsCameraSpacePrecedesBothTeamsAndTracksActualCutout() {
        val height = 40f
        val leading = 45f
        val trailing = 37f
        val edge = height * .2f
        for (radius in listOf(4f, 8f, 16f)) {
            val width = CompactIslandGeometry.width(height, radius, 0f, trailing,
                grouped = true, leadingWidth = leading)
            val space = sportsCameraSideSpace(width, edge, leading + trailing, 0f)
            val teamOneLeft = edge + space
            val teamTwoLeft = width - edge - trailing
            assertEquals(6f, teamTwoLeft - (teamOneLeft + leading), .001f)
            assertTrue(teamOneLeft >= radius * 2f)
            assertEquals(0f, sportsCameraSideSpace(width, edge, leading + trailing, 1f), .001f)
        }
    }

    @Test fun detachedSportsPillReservesBothTeamsWithoutCameraSpace() {
        val width = compactActivityPillWidth(40f, 37f, 45f)
        assertEquals(104f, width, .001f)
        assertEquals(0f, sportsCameraSideSpace(width, 8f, 82f, 0f), .001f)
        val placements = activityPlacements(IslandActivities("camera", listOf("sports")),
            100f, 40f, secondaryIndicatorWidth = 37f, secondaryLeadingWidth = 45f)
        assertEquals(width, placements.getValue("sports").width, .001f)
    }

    @Test fun groupedIconAndChronometerClearTheCameraTogether() {
        val height = 40f
        val status = 36f
        for (offset in listOf(-10f, 0f, 10f)) {
            val width = CompactIslandGeometry.width(height, 6f, offset, status, grouped = true)
            val center = CompactIslandGeometry.centerFromCameraLeft(width, 6f, offset)
            assertEquals(offset - 6f, center - width / 2f, .001f)
            val statusLeft = center + width / 2f - 8f - status
            val iconLeft = statusLeft - 6f - 24f
            assertTrue(iconLeft >= offset + 6f)
            assertEquals(6f, statusLeft - (iconLeft + 24f), .001f)
        }
    }

    @Test fun smallCameraDoesNotReserveAnOversizedGap() {
        val width = CompactIslandGeometry.width(40f, 6f, 0f, 36f)
        assertEquals(100f, width, .001f)
        assertTrue(width < CompactIslandGeometry.width(40f, 20f, 0f, 36f))
    }
    @Test fun bothElementsClearAnOffsetCamera() {
        for (offset in listOf(-10f, 0f, 10f)) {
            val half = CompactIslandGeometry.width(40f, 6f, offset, 36f) / 2f
            val center = 0f
            assertTrue(center - half + 8f + 24f <= offset - 6f)
            assertTrue(center + half - 8f - 36f >= offset + 6f)
        }
    }
    @Test fun edgeAlignedElementsShrinkUntilOneSideTouchesTheCamera() {
        val height = 40f
        val radius = 8f
        for (bars in listOf(height * .54f, height * .6f, 36f)) {
            for (offset in listOf(-10f, 0f, 10f)) {
                val width = CompactIslandGeometry.width(height, radius, offset, bars)
                val placement = MusicElementMorph.symmetricPlacement(width, height, 0f,
                    height * .2f, height / 2f, height * .6f, 0f, bars, 20f)
                val leftGap = width / 2f + offset - radius - (placement.artLeft + height * .6f)
                val rightGap = placement.barsLeft - (width / 2f + offset + radius)
                assertTrue(leftGap >= -.001f)
                assertTrue(rightGap >= -.001f)
                assertEquals(0f, minOf(leftGap, rightGap), .001f)
                assertEquals(height * .2f, placement.artLeft, .001f)
                assertEquals(height * .2f, width - placement.barsLeft - bars, .001f)
                if (offset == 0f && bars == height * .6f) {
                    assertEquals(0f, leftGap, .001f)
                    assertEquals(0f, rightGap, .001f)
                }
            }
        }
    }
}
