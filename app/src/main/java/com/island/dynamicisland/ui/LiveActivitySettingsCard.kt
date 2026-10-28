package com.island.dynamicisland.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.dynamicisland.config.LiveActivityConfig
import java.text.DateFormat
import java.util.Date

@Composable
fun LiveActivitySettingsCard() {
    val history by LiveActivityConfig.history.collectAsStateWithLifecycle()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Live activity history", style = MaterialTheme.typography.titleMedium)
            Text("Choose which persistent notification sources appear in the island. Choices apply immediately and are remembered.",
                style = MaterialTheme.typography.bodySmall)
            if (history.isEmpty()) Text("Persistent notifications will appear here when discovered.")
            history.forEach { source ->
                key(source.key) {
                    Row(Modifier.fillMaxWidth()
                        .toggleable(value = source.enabled, role = Role.Switch,
                            onValueChange = { LiveActivityConfig.setEnabled(source.key, it) })
                        .semantics { contentDescription = "Show ${source.title.ifBlank { source.appName }} in island" },
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(source.title.ifBlank { source.appName }, style = MaterialTheme.typography.bodyLarge)
                            Text(source.appName, style = MaterialTheme.typography.bodySmall)
                            Text("Last seen ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(source.lastSeen))}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(source.enabled, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
