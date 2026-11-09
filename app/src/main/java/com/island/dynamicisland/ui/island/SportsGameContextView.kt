package com.island.dynamicisland.ui.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.island.dynamicisland.live.SportsScore
import com.island.dynamicisland.live.footballFieldPosition
import com.island.dynamicisland.live.baseballOuts
import com.island.dynamicisland.live.readableTeamColor

/** Source-backed context; springs request frames only while a field update is moving. */
@Composable
internal fun SportsGameContextView(score: SportsScore, active: Boolean) {
    val field = footballFieldPosition(score)
    if (field != null) {
        val ball by rememberLiveProgress(field.yards / 100f, active)
        val target by rememberLiveProgress((field.firstDownYards ?: field.yards) / 100f, active)
        val play = score.lastFootballPlay
        val playAccent = Color(readableTeamColor(if (play?.team == 1) score.teamOneColor else score.teamTwoColor))
        val accent = Color(readableTeamColor(when (field.attackingTeam) {
            1 -> score.teamOneColor
            2 -> score.teamTwoColor
            else -> 0xFFE4E4E7.toInt()
        }))
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Canvas(Modifier.fillMaxWidth().height(16.dp).graphicsLayer {
                alpha = if (score.fieldContextStale) .55f else 1f
            }.semantics {
                contentDescription = (if (score.fieldContextStale) "Last known ball position: " else "Ball at ") + field.sourceText +
                    (field.firstDownYards?.let { ", first-down target shown" } ?: "") +
                    (play?.let { ", last observed movement ${it.gain} yards" } ?: "")
            }) {
                val inset = 5.dp.toPx()
                val width = (size.width - 2 * inset).coerceAtLeast(0f)
                fun x(yards: Float) = inset + width * yards / 100f
                val y = size.height / 2f
                drawRoundRect(Color.White.copy(alpha = .18f), Offset(inset, y - 3.dp.toPx()),
                    Size(width, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                for (yard in 10..90 step 10) {
                    drawLine(Color.White.copy(alpha = .4f), Offset(x(yard.toFloat()), y - 2.dp.toPx()),
                        Offset(x(yard.toFloat()), y + 2.dp.toPx()), 1.dp.toPx())
                }
                play?.let {
                    drawLine(playAccent.copy(alpha = .32f), Offset(x(it.fromYards), y),
                        Offset(x(ball * 100f), y), 6.dp.toPx(), StrokeCap.Round)
                }
                field.firstDownYards?.let {
                    drawLine(Color(0xFFFFC247), Offset(x(target * 100f), y - 5.dp.toPx()), Offset(x(target * 100f), y + 5.dp.toPx()),
                        2.dp.toPx(), StrokeCap.Round)
                }
                drawCircle(Color.Black, 4.5.dp.toPx(), Offset(x(ball * 100f), y))
                drawCircle(accent, 3.5.dp.toPx(), Offset(x(ball * 100f), y))
            }
        }
    } else if (score.basesGraphic != null) {
        Image(score.basesGraphic.asImageBitmap(), "Base occupancy from scoreboard",
            Modifier.padding(top = 4.dp).size(width = 56.dp, height = 32.dp))
        baseballOuts(score.gameStatus)?.let { outs ->
            Canvas(Modifier.size(width = 30.dp, height = 10.dp).semantics {
                contentDescription = "$outs outs"
            }) {
                for (index in 0..2) drawCircle(
                    if (index < outs) Color.White else Color.White.copy(alpha = .2f),
                    2.dp.toPx(), Offset(size.width * (index + .5f) / 3f, size.height / 2f))
            }
        }
    }
    score.possession?.let { turn ->
        if (turn.label != "Possession" || field == null) {
            val name = if (turn.team == 1) score.teamOne else score.teamTwo
            Text("${name.orEmpty()} · ${turn.label}", color = Color(0xFFDADAE0), fontSize = 10.sp)
        }
    }
    if (score.setScores.isNotEmpty()) {
        Text("Sets: " + score.setScores.joinToString(" · ") { set ->
            val name = when (set.team) { 1 -> score.teamOne; 2 -> score.teamTwo; else -> null }
            listOfNotNull(name, set.text).joinToString(" ")
        }, color = Color(0xFFDADAE0), fontSize = 10.sp)
    }
}
