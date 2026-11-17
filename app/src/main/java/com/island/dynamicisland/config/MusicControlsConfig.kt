package com.island.dynamicisland.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.island.dynamicisland.media.NowPlaying
import com.island.dynamicisland.media.PlaybackAction
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class SavedMusicApp(val packageName: String, val name: String, val actions: List<PlaybackAction>)
/**
 * [liveVisualizer]: equalizer bars follow the playing audio. Off skips the audio capture and
 * keeps the bars still (as in Battery Saver), since redrawing them is most of their cost.
 */
data class MusicControlsSettings(val remember: Boolean = true, val app: SavedMusicApp? = null, val revision: Int = 0,
                                 val liveVisualizer: Boolean = true)
data class MusicControlSlots(val left: String = "none", val right: String = "cast")

/** Persist only the latest player's action catalog; slot choices are separate for each app. */
object MusicControlsConfig {
    private var prefs: android.content.SharedPreferences? = null
    private val mutable = MutableStateFlow(MusicControlsSettings())
    val settings = mutable.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("music_controls", Context.MODE_PRIVATE)
        prefs = p
        val remember = p.getBoolean("remember", true)
        val saved = if (remember) runCatching {
            val json = JSONObject(p.getString("app", null) ?: return@runCatching null)
            val entries = json.getJSONArray("actions")
            SavedMusicApp(json.getString("package"), json.getString("name"), (0 until entries.length()).map { i ->
                val item = entries.getJSONObject(i)
                val bytes = Base64.decode(item.getString("icon"), Base64.DEFAULT)
                PlaybackAction(item.getString("id"), item.getString("label"),
                    if (bytes.isEmpty()) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size), item.optInt("resource"))
            })
        }.getOrNull() else null
        mutable.value = MusicControlsSettings(remember, saved, liveVisualizer = p.getBoolean("live_visualizer", true))
    }

    fun remember(value: Boolean) {
        prefs?.edit()?.putBoolean("remember", value)?.apply()
        mutable.value = mutable.value.copy(remember = value)
        if (value) save() else prefs?.edit()?.remove("app")?.apply()
    }

    fun liveVisualizer(value: Boolean) {
        prefs?.edit()?.putBoolean("live_visualizer", value)?.apply()
        mutable.value = mutable.value.copy(liveVisualizer = value)
    }

    fun slots(packageName: String?) = MusicControlSlots(
        prefs?.getString("$packageName:left", "none") ?: "none",
        prefs?.getString("$packageName:right", "cast") ?: "cast")

    fun select(packageName: String, left: Boolean, id: String) {
        prefs?.edit()?.putString("$packageName:${if (left) "left" else "right"}", id)?.apply()
        mutable.value = mutable.value.copy(revision = mutable.value.revision + 1)
    }

    fun observe(context: Context, np: NowPlaying) {
        init(context)
        val previous = mutable.value.app
        if (previous?.packageName == np.packageName && previous.actions.map { Triple(it.id, it.label, it.iconResource) } ==
            np.extraActions.map { Triple(it.id, it.label, it.iconResource) }) return
        val name = if (previous?.packageName == np.packageName) previous.name else runCatching {
            val info = context.packageManager.getApplicationInfo(np.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(np.packageName)
        mutable.value = mutable.value.copy(app = SavedMusicApp(np.packageName, name, np.extraActions))
        if (mutable.value.remember) save()
    }

    private fun save() {
        val app = mutable.value.app ?: return
        val actions = JSONArray()
        app.actions.forEach { action ->
            val bytes = ByteArrayOutputStream()
            action.icon?.compress(Bitmap.CompressFormat.PNG, 100, bytes)
            actions.put(JSONObject().put("id", action.id).put("label", action.label)
                .put("resource", action.iconResource)
                .put("icon", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)))
        }
        prefs?.edit()?.putString("app", JSONObject().put("package", app.packageName)
            .put("name", app.name).put("actions", actions).toString())?.apply()
    }
}
