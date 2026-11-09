package com.island.dynamicisland.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView

data class SportsPlayer(val name: String, val stats: String, val portrait: Bitmap? = null, val teamSide: Int? = null)

/** ESPN's two-row leaders panel follows scoreboard order; do not assume this for other layouts. */
internal fun assignSportsPlayerSides(players: List<SportsPlayer>, espn: Boolean): List<SportsPlayer> {
    if (!espn || players.size != 2 || players.withIndex().any { (i, p) -> p.teamSide != null && p.teamSide != i + 1 }) return players
    return players.mapIndexed { index, player -> player.copy(teamSide = index + 1) }
}

internal fun sportsSectionTitle(source: String): String = source
    .replace(Regex("\\bLeaders\\b", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s+"), " ").trim()

/** Pregame RemoteViews hide score views but still publish both team identities. */
internal fun sportsScoreValues(scoreOne: String?, scoreTwo: String?,
                               hasTeamOne: Boolean, hasTeamTwo: Boolean): Pair<String, String>? {
    if (scoreOne != null && scoreTwo != null) return scoreOne to scoreTwo
    if (scoreOne.isNullOrBlank() && scoreTwo.isNullOrBlank() && hasTeamOne && hasTeamTwo) return "" to ""
    return null
}

internal fun isSportsPrimaryStatusField(name: String): Boolean =
    name.contains("game_status", ignoreCase = true) ||
        name.contains("status_primary", ignoreCase = true) ||
        name.equals("tv_pre_status_line_one", ignoreCase = true)

data class SportsScore(
    val teamOne: String?,
    val scoreOne: String,
    val teamTwo: String?,
    val scoreTwo: String,
    val status: String? = null,
    val teamOneLogo: Bitmap? = null,
    val teamTwoLogo: Bitmap? = null,
    val notes: List<String> = emptyList(),
    val players: List<SportsPlayer> = emptyList(),
    val secondaryStatus: String? = null,
    val teamOneColor: Int = 0xFFE4E4E7.toInt(),
    val teamTwoColor: Int = 0xFFE4E4E7.toInt(),
    val possession: SportsPossession? = null,
    val basesGraphic: Bitmap? = null,
    val setScores: List<SportsSetScore> = emptyList(),
    val gameClockId: Int? = null,
    val gameClockText: String? = null,
    val lastFootballPlay: FootballPlay? = null,
    val retainedFootballField: FootballFieldPosition? = null,
    val fieldContextStale: Boolean = false,
    val possessionStale: Boolean = false,
) {
    val gameStatus: String get() = listOfNotNull(status, secondaryStatus)
        .flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
    val formattedScore: String get() = if (scoreOne.isBlank() && scoreTwo.isBlank()) "" else "$scoreOne - $scoreTwo"
    val summaryTitle: String? get() = if (!teamOne.isNullOrBlank() && !teamTwo.isNullOrBlank()) "$teamOne vs $teamTwo" else null
}

/** Check if the status text indicates a terminal game result (e.g. final score). */
fun isSportsFinalStatus(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    return Regex("""\b(final|final[\s\/-]*score|full[\s\/-]*time|ft|ended|postgame)\b""", RegexOption.IGNORE_CASE)
        .containsMatchIn(status)
}

/**
 * Inspect custom notification hierarchy for sports score patterns (e.g. ESPN Live Activities).
 * Extracts team names, scores, game status, and team logo bitmaps from RemoteViews.
 */
fun readCustomSportsScore(context: Context, packageName: String, content: RemoteViews): SportsScore? = runCatching {
    val root = FrameLayout(context)
    val view = content.apply(context, root)
    root.addView(view)

    var teamOne: String? = null
    var scoreOne: String? = null
    var teamOneLogo: Bitmap? = null
    var teamTwo: String? = null
    var scoreTwo: String? = null
    var teamTwoLogo: Bitmap? = null
    var status: String? = null
    var secondaryStatus: String? = null
    var pregameBroadcast: String? = null
    val possessionCandidates = mutableListOf<SportsPossession>()
    var basesGraphic: Bitmap? = null
    val setScores = mutableListOf<SportsSetScore>()
    var gameClockId: Int? = null
    var gameClockText: String? = null
    val notes = mutableListOf<String>()
    val players = mutableListOf<SportsPlayer>()
    val playerFields = mutableSetOf<View>()
    var pendingNoteLabel = false
    val sourceResources = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull()

    fun extractBitmap(view: ImageView): Bitmap? {
        val drawable = view.drawable ?: return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else view.width.coerceAtLeast(1)
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun resourceName(view: View): String = if (view.id == View.NO_ID) "" else
        runCatching { (sourceResources ?: view.resources).getResourceEntryName(view.id) }.getOrDefault("")

    fun alphaMask(drawable: android.graphics.drawable.Drawable): List<Int> {
        val oldBounds = android.graphics.Rect(drawable.bounds)
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, 24, 24)
            drawable.draw(Canvas(bitmap))
        } finally { drawable.bounds = oldBounds }
        val pixels = IntArray(24 * 24)
        bitmap.getPixels(pixels, 0, 24, 0, 0, 24, 24)
        return pixels.map { android.graphics.Color.alpha(it) }
    }
    val turnIcons by lazy {
        listOf("football_possession" to "Possession", "ic_ball_possession" to "Possession",
            "ic_tennis_ball" to "Serving").mapNotNull { (name, label) ->
            runCatching {
                val resources = sourceResources ?: return@runCatching null
                val id = resources.getIdentifier(name, "drawable", packageName)
                if (id == 0) null else alphaMask(resources.getDrawable(id, null)) to label
            }.getOrNull()
        }
    }

    fun visit(view: View, inheritedTeam: Int = 0) {
        if (view.visibility != View.VISIBLE || view.alpha <= 0f) return
        val idName = resourceName(view)
        if (view is android.widget.Chronometer && view.id != View.NO_ID && isSportsGameClockField(idName)) {
            gameClockId = view.id
            gameClockText = view.text.toString().trim()
        }
        // ESPN keeps each player's portrait, name and statistics in one row.
        // Preserve that relationship rather than flattening the row into text.
        if (view is ViewGroup) {
            val children = (0 until view.childCount).map { view.getChildAt(it) }
                .filter { it.visibility == View.VISIBLE }
            val label = children.filterIsInstance<TextView>().firstOrNull { resourceName(it).contains("note_label") }
            val message = children.filterIsInstance<TextView>().firstOrNull { resourceName(it).contains("note_message") }
            if (label != null && message != null) {
                val avatar = children.filterIsInstance<ImageView>().firstOrNull { resourceName(it).contains("note_avatar") }
                players += SportsPlayer(label.text.toString().trim(), message.text.toString().trim(), avatar?.let(::extractBitmap), inheritedTeam.takeIf { it in 1..2 })
                playerFields += label
                playerFields += message
            }
        }

        var currentTeam = inheritedTeam
        if (idName.contains("team_one", ignoreCase = true) || idName.contains("home_team", ignoreCase = true) || idName.contains("team1", ignoreCase = true)) {
            currentTeam = 1
        } else if (idName.contains("team_two", ignoreCase = true) || idName.contains("away_team", ignoreCase = true) || idName.contains("team2", ignoreCase = true)) {
            currentTeam = 2
        }

        if (view is ImageView) {
            if (idName.equals("iv_bases", true) || idName.equals("base_occupancy", true)) {
                basesGraphic = extractBitmap(view)
            }
            val indicatorTeam = when {
                currentTeam in 1..2 -> currentTeam
                idName.startsWith("home_", ignoreCase = true) -> 1
                idName.startsWith("away_", ignoreCase = true) -> 2
                else -> 0
            }
            if (indicatorTeam in 1..2 && view.drawable != null) {
                val label = sportsPossessionLabel(idName, view.contentDescription?.toString().orEmpty())
                    ?: if (idName.matches(Regex("iv_live_team_(one|two)_indicator"))) {
                        val mask = alphaMask(view.drawable)
                        turnIcons.firstOrNull { (candidate, _) ->
                            sportsTurnIconMatches(candidate, mask)
                        }?.second
                    } else null
                if (label != null) possessionCandidates += SportsPossession(indicatorTeam, label)
            }
            val isLogo = idName.contains("logo", ignoreCase = true) ||
                (idName.contains("iv_team", ignoreCase = true) && !idName.contains("indicator", ignoreCase = true)) ||
                idName.contains("team_icon", ignoreCase = true)

            if (isLogo) {
                val bmp = extractBitmap(view)
                if (bmp != null) {
                    if (idName.contains("team_one", ignoreCase = true) || idName.contains("team1", ignoreCase = true) || currentTeam == 1) {
                        if (teamOneLogo == null) teamOneLogo = bmp
                    } else if (idName.contains("team_two", ignoreCase = true) || idName.contains("team2", ignoreCase = true) || currentTeam == 2) {
                        if (teamTwoLogo == null) teamTwoLogo = bmp
                    }
                }
            }
        }

        if (view is TextView && view !in playerFields) {
            val text = view.text?.toString()?.trim().orEmpty()
            when {
                isSportsSetScoreField(idName) && text.isNotBlank() ->
                    setScores.add(SportsSetScore(currentTeam.takeIf { it in 1..2 }, text))
                idName.contains("score_one", ignoreCase = true) || idName.contains("home_score", ignoreCase = true) || (currentTeam == 1 && idName.contains("score", ignoreCase = true)) -> {
                    scoreOne = text
                }
                idName.contains("score_two", ignoreCase = true) || idName.contains("away_score", ignoreCase = true) || (currentTeam == 2 && idName.contains("score", ignoreCase = true)) -> {
                    scoreTwo = text
                }
                (idName.contains("team_one", ignoreCase = true) || idName.contains("home_team", ignoreCase = true)) && idName.contains("name", ignoreCase = true) -> {
                    teamOne = text
                }
                (idName.contains("team_two", ignoreCase = true) || idName.contains("away_team", ignoreCase = true)) && idName.contains("name", ignoreCase = true) -> {
                    teamTwo = text
                }
                idName.contains("status_secondary", ignoreCase = true) -> secondaryStatus = text
                isSportsPrimaryStatusField(idName) -> if (text.isNotBlank()) status = text
                idName.equals("tv_network_chip", ignoreCase = true) ||
                    idName.equals("tv_pre_broadcast_overflow", ignoreCase = true) -> if (text.isNotBlank()) pregameBroadcast = text
                idName.contains("note_message", ignoreCase = true) && text.isNotBlank() -> {
                    val label = notes.lastOrNull()
                    if (label != null && pendingNoteLabel) notes[notes.lastIndex] = "$label · $text"
                    else notes += text
                    pendingNoteLabel = false
                }
                idName.contains("note", ignoreCase = true) || idName.contains("leader", ignoreCase = true) -> if (text.isNotBlank()) {
                    notes += text
                    pendingNoteLabel = idName.contains("note_label", ignoreCase = true)
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) visit(view.getChildAt(i), currentTeam)
        }
    }
    visit(root)
    fun logoColor(bitmap: Bitmap?): Int = runCatching {
        if (bitmap == null) return@runCatching dominantTeamColor(intArrayOf())
        val sample = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        sample.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        dominantTeamColor(pixels)
    }.getOrDefault(0xFFE4E4E7.toInt())
    sportsScoreValues(scoreOne, scoreTwo,
        !teamOne.isNullOrBlank() || teamOneLogo != null,
        !teamTwo.isNullOrBlank() || teamTwoLogo != null)?.let { (first, second) ->
        SportsScore(teamOne, first, teamTwo, second, status, teamOneLogo, teamTwoLogo, notes.distinct(), assignSportsPlayerSides(players, packageName.equals("com.espn.score_center", ignoreCase = true)), secondaryStatus ?: pregameBroadcast?.takeIf { first.isBlank() && second.isBlank() }, logoColor(teamOneLogo), logoColor(teamTwoLogo),
            resolveSportsPossession(possessionCandidates).takeUnless {
                first.isBlank() || second.isBlank() || isSportsFinalStatus(status)
            }, basesGraphic, setScores.distinct(), gameClockId, gameClockText)
    }
}.getOrNull()
