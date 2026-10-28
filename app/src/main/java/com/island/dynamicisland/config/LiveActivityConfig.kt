package com.island.dynamicisland.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LiveActivitySource(
    val packageName: String,
    val channelId: String,
    val appName: String,
    val title: String,
    val lastSeen: Long,
    val enabled: Boolean = true,
) {
    val key: String get() = liveActivitySourceKey(packageName, channelId)
}

internal fun liveActivitySourceKey(packageName: String, channelId: String) = "${packageName.length}:$packageName$channelId"

internal fun rememberLiveActivitySource(history: List<LiveActivitySource>, source: LiveActivitySource): List<LiveActivitySource> {
    val previous = history.firstOrNull { it.key == source.key }
    return listOf(source.copy(enabled = previous?.enabled ?: source.enabled)) + history.filterNot { it.key == source.key }
}

/** A local catalog of encountered ongoing sources, rather than a log of notification bodies. */
object LiveActivityConfig {
    private var prefs: android.content.SharedPreferences? = null
    private val mutable = MutableStateFlow<List<LiveActivitySource>>(emptyList())
    val history = mutable.asStateFlow()
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("live_activity_sources", Context.MODE_PRIVATE)
        mutable.value = runCatching {
            val array = JSONArray(prefs!!.getString("history", "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                LiveActivitySource(item.getString("package"), item.getString("channel"),
                    item.getString("app"), item.getString("title"), item.getLong("seen"), item.optBoolean("enabled", true))
            }
        }.getOrDefault(emptyList())
    }

    fun observe(context: Context, packageName: String, channelId: String, appName: String, title: String) {
        init(context)
        val key = liveActivitySourceKey(packageName, channelId)
        val previous = mutable.value.firstOrNull { it.key == key }
        val now = System.currentTimeMillis()
        // Chatty timers must not rewrite preferences on every notification update.
        if (previous != null && previous.appName == appName && previous.title == title &&
            now >= previous.lastSeen && now - previous.lastSeen < 60_000) return
        val source = LiveActivitySource(packageName, channelId, appName, title, now)
        mutable.value = rememberLiveActivitySource(mutable.value, source)
        save()
    }

    fun isEnabled(packageName: String, channelId: String) =
        mutable.value.firstOrNull { it.key == liveActivitySourceKey(packageName, channelId) }?.enabled ?: true

    fun setEnabled(key: String, enabled: Boolean) {
        if (mutable.value.none { it.key == key && it.enabled != enabled }) return
        mutable.value = mutable.value.map { if (it.key == key) it.copy(enabled = enabled) else it }
        save()
        revision.value++
    }

    private fun save() {
        val array = JSONArray()
        mutable.value.forEach { source ->
            array.put(JSONObject().put("package", source.packageName).put("channel", source.channelId)
                .put("app", source.appName).put("title", source.title).put("seen", source.lastSeen).put("enabled", source.enabled))
        }
        prefs?.edit()?.putString("history", array.toString())?.apply()
    }
}
