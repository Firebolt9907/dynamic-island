package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test

class IslandActivitiesTest {
    @Test fun twoLiveActivitiesAndMusicRemainReachable() {
        assertEquals(IslandActivities("ride", listOf("delivery", "music")),
            IslandActivities.select(listOf("ride", "delivery"), true))
    }
    @Test fun eachActivityHasExactlyOneSlot() {
        val slots = IslandActivities.select(listOf("ride", "ride", "delivery"), true)
        assertEquals(listOf("delivery", "music"), slots.secondaries)
    }
    @Test fun activitiesRemainReachableWithoutMusic() {
        assertEquals(IslandActivities("ride", listOf("delivery", "stopwatch")),
            IslandActivities.select(listOf("ride", "delivery", "stopwatch"), false))
    }
    @Test fun removingActivitiesRestoresSinglePresentation() {
        assertEquals(IslandActivities("music", emptyList()), IslandActivities.select(emptyList(), true))
        assertEquals(IslandActivities("ride", emptyList()), IslandActivities.select(listOf("ride"), false))
        assertEquals(IslandActivities(null, emptyList()), IslandActivities.select(emptyList(), false))
    }
    @Test fun threeActivityLimitKeepsRemainingActivitiesTrackedOutsideTheVisibleSlots() {
        assertEquals(IslandActivities("ride", listOf("delivery", "stopwatch")),
            IslandActivities.select(listOf("ride", "delivery", "stopwatch", "flight"), true))
    }
    @Test fun secondIsLeftAndThirdIsRightOnTheSameRow() {
        assertEquals(ActivityBubblePosition(84f, 0f), activityBubblePosition(0, 130f, 108f, 40f))
        assertEquals(ActivityBubblePosition(244f, 0f), activityBubblePosition(1, 130f, 108f, 40f))
    }
}
