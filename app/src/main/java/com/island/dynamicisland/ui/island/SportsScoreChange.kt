package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import com.island.dynamicisland.live.LiveUpdate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal fun scoreIncrease(before: String, after: String): Int =
    before.toIntOrNull()?.let { old -> after.toIntOrNull()?.let { (it - old).coerceAtLeast(0) } } ?: 0

/** Presentation only: the repository always retains the newest real scoreboard. */
internal class SportsScoreChange(one: String, two: String) {
    var one by mutableStateOf(one); private set
    var two by mutableStateOf(two); private set
    var deltaOne by mutableIntStateOf(0); private set
    var deltaTwo by mutableIntStateOf(0); private set
    val progress = Animatable(0f)

    suspend fun update(nextOne: String, nextTwo: String, onCelebration: () -> Unit = {}, awaitExpanded: suspend () -> Boolean) {
        progress.snapTo(0f)
        deltaOne = scoreIncrease(one, nextOne)
        deltaTwo = scoreIncrease(two, nextTwo)
        if ((deltaOne > 0 || deltaTwo > 0) && awaitExpanded()) {
            onCelebration()
            progress.animateTo(1f, tween(900))
        }
        one = nextOne
        two = nextTwo
        deltaOne = 0
        deltaTwo = 0
    }
}

internal data class PresentedSportsActivities(val activities: List<LiveUpdate>, val changes: Map<String, SportsScoreChange>)

@Composable
internal fun presentSportsScores(updates: List<LiveUpdate>, onScoreChangeReady: () -> Unit = {}, isExpanded: (String) -> Boolean): PresentedSportsActivities {
    val ready by rememberUpdatedState(isExpanded)
    val celebrate by rememberUpdatedState(onScoreChangeReady)
    val changes = mutableMapOf<String, SportsScoreChange>()
    val activities = updates.map { update ->
        val score = update.sportsScore
        if (score == null) update else key(update.key, score.teamOne, score.teamTwo) {
            val change = remember { SportsScoreChange(score.scoreOne, score.scoreTwo) }
            changes[update.key] = change
            LaunchedEffect(score.scoreOne, score.scoreTwo) {
                // A newer score cancels/restarts this effect from the still-visible
                // total, combining rapid scoring updates without losing points.
                change.update(score.scoreOne, score.scoreTwo, onCelebration = { celebrate() }) {
                    withTimeoutOrNull(3000) {
                        snapshotFlow { ready(update.key) }.first { it }
                    } ?: false // Suppressed/offscreen activities must never freeze.
                }
            }
            update.copy(sportsScore = score.copy(scoreOne = change.one, scoreTwo = change.two))
        }
    }
    return PresentedSportsActivities(activities, changes)
}
