package com.island.dynamicisland.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.dynamicisland.config.MusicControlsConfig
import com.island.dynamicisland.media.PlaybackAction

@Composable
fun MusicControlsSettingsCard() {
    val settings by MusicControlsConfig.settings.collectAsStateWithLifecycle()
    val app = settings.app
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Music controls", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().toggleable(value = settings.remember, role = Role.Switch,
                onValueChange = MusicControlsConfig::remember), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Remember last music app")
                    Text("Keep its control icons available after playback stops.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(settings.remember, onCheckedChange = null)
            }
            if (app == null) Text("Play music to discover available controls.", style = MaterialTheme.typography.bodyMedium)
            else {
                Text(app.name, style = MaterialTheme.typography.labelLarge)
                Text("Choose up to two extra controls. Choices are saved separately for each music app.",
                    style = MaterialTheme.typography.bodySmall)
                val slots = MusicControlsConfig.slots(app.packageName)
                SlotPicker("Left of Previous", slots.left, app.actions) { MusicControlsConfig.select(app.packageName, true, it) }
                SlotPicker("Right of Next", slots.right, app.actions) { MusicControlsConfig.select(app.packageName, false, it) }
            }
        }
    }
}

@Composable
private fun SlotPicker(title: String, selected: String, actions: List<PlaybackAction>, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedAction = actions.firstOrNull { it.id == selected }
    val label = when (selected) { "none" -> "None"; "cast" -> "Cast"; else -> selectedAction?.label ?: "Unavailable" }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f))
            ActionImage(selected, selectedAction)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            listOf("none" to "None", "cast" to "Cast").plus(actions.map { it.id to it.label }).forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name + if (id == selected) " ✓" else "") },
                    leadingIcon = { ActionImage(id, actions.firstOrNull { it.id == id }) },
                    onClick = { choose(id); expanded = false })
            }
        }
    }
}

@Composable
private fun ActionImage(id: String, action: PlaybackAction?) {
    if (id == "cast") Icon(Icons.Rounded.Cast, null, Modifier.size(22.dp))
    else action?.icon?.let {
        Image(it.asImageBitmap(), null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
    }
}
