package com.island.dynamicisland.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.dynamicisland.config.LiveActivityConfig

/** Collapsed list length; the full history can grow to dozens of sources. */
private const val COLLAPSED_SOURCES = 5

@Composable
fun LiveActivitySettingsCard() {
    val history by LiveActivityConfig.history.collectAsStateWithLifecycle()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Live activities", style = MaterialTheme.typography.titleMedium)
            Text("Choose which ongoing notifications appear in the island. Changes apply immediately.",
                color = muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            if (history.isEmpty()) Text("Apps with ongoing notifications, like timers, calls, navigation and live scores, will appear here.",
                color = muted, style = MaterialTheme.typography.bodyMedium)
            val shown = if (showAll) history else history.take(COLLAPSED_SOURCES)
            shown.forEach { source ->
                key(source.key) {
                    val name = source.title.ifBlank { source.appName }
                    Row(Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .toggleable(value = source.enabled, role = Role.Switch,
                            onValueChange = { LiveActivityConfig.setEnabled(source.key, it) })
                        .semantics { contentDescription = "Show $name in island" }
                        .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val subtitle = buildString {
                                if (source.title.isNotBlank() && source.title != source.appName) append(source.appName).append(" · ")
                                append("Seen ")
                                append(DateUtils.getRelativeTimeSpanString(source.lastSeen, System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE))
                            }
                            Text(subtitle, color = muted, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Switch(source.enabled, onCheckedChange = null)
                    }
                }
            }
            if (history.size > COLLAPSED_SOURCES) TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "Show fewer" else "Show all ${history.size}")
            }
        }
    }
}
