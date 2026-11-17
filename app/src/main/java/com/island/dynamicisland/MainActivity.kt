package com.island.dynamicisland

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.dynamicisland.config.IslandConfig
import com.island.dynamicisland.config.IslandTuning
import com.island.dynamicisland.service.IslandAccessibilityService
import com.island.dynamicisland.service.MediaNotificationListener
import com.island.dynamicisland.ui.LiveActivitySettingsCard
import com.island.dynamicisland.ui.MusicControlsSettingsCard
import com.island.dynamicisland.ui.theme.DynamicIslandTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35 is edge-to-edge on Android 15; opt in everywhere so system bar icons
        // follow the light/dark theme, and pad content with safeDrawing insets below.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        IslandConfig.init(this)
        com.island.dynamicisland.config.LiveActivityConfig.init(this)
        com.island.dynamicisland.config.MusicControlsConfig.init(this)
        setContent {
            DynamicIslandTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    SetupScreen()
                }
            }
        }
    }
}

/** Runs [action] each time the host lifecycle reaches [event]. */
@Composable
private fun OnLifecycleEvent(event: Lifecycle.Event, action: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, event) {
        val observer = LifecycleEventObserver { _, e -> if (e == event) action() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun SetupScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    // Re-check permission state every time the screen resumes (user returns from Settings).
    var refreshKey by remember { mutableIntStateOf(0) }
    OnLifecycleEvent(Lifecycle.Event.ON_RESUME) { refreshKey++ }

    val listenerGranted = remember(refreshKey) { isNotificationListenerEnabled(context) }
    val accessibilityGranted = remember(refreshKey) { isAccessibilityEnabled(context) }
    val bluetoothGranted = remember(refreshKey) {
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }
    // Once the system stops showing the runtime dialog ("Don't allow" twice), launching the
    // request returns instantly with no UI. Send the user to App info instead of a dead button.
    var bluetoothBlocked by remember { mutableStateOf(false) }
    val bluetoothPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        bluetoothBlocked = !granted &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
        refreshKey++
    }
    val audioGranted = remember(refreshKey) {
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    var audioBlocked by remember { mutableStateOf(false) }
    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { granted ->
        audioBlocked = !granted &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        refreshKey++
    }
    val requiredGranted = listOf(listenerGranted, accessibilityGranted).count { it }
    val allReady = requiredGranted == 2

    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Dynamic Island",
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Music, timers, calls and scores, live around your camera.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(20.dp))

        StatusBanner(allReady, requiredGranted)
        Spacer(Modifier.height(24.dp))

        SectionHeader("Setup")
        PermissionCard(
            step = "1",
            title = "Notification access",
            subtitle = "Reads the playing track, its controls, and ongoing notifications like timers and scores.",
            granted = listenerGranted,
            required = true,
        ) { openNotificationListenerSettings(context) }
        Spacer(Modifier.height(10.dp))
        PermissionCard(
            step = "2",
            title = "Accessibility service",
            subtitle = "Draws the island over the status bar so it can be tapped on the camera. No screen content is read.",
            granted = accessibilityGranted,
            required = true,
            hint = if (!accessibilityGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                "Switch greyed out? Open App info, tap ⋮, then “Allow restricted settings”." else null,
            hintAction = "App info" to { openAppDetails(context) },
        ) { openAccessibilitySettings(context) }
        Spacer(Modifier.height(10.dp))
        PermissionCard(
            step = "3",
            title = "Bluetooth audio battery",
            subtitle = if (bluetoothBlocked)
                "Permission was denied. Enable “Nearby devices” in App info to show headphone battery."
            else "Shows the battery level of connected headphones and speakers.",
            granted = bluetoothGranted,
            required = false,
            actionLabel = if (bluetoothBlocked) "Open" else "Allow",
        ) {
            if (bluetoothBlocked) openAppDetails(context)
            else bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        Spacer(Modifier.height(10.dp))
        PermissionCard(
            step = "4",
            title = "Live music visualizer",
            subtitle = if (audioBlocked)
                "Permission was denied. Enable “Microphone” in App info for live equalizer bars."
            else "Equalizer bars follow the music's real bass and treble. Android labels this " +
                "microphone access, but the microphone is never used and no audio is recorded or stored.",
            granted = audioGranted,
            required = false,
            actionLabel = if (audioBlocked) "Open" else "Allow",
        ) {
            if (audioBlocked) openAppDetails(context)
            else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        Spacer(Modifier.height(12.dp))
        GestureHints()

        Spacer(Modifier.height(28.dp))
        SectionHeader("Customize")
        MusicControlsSettingsCard()
        Spacer(Modifier.height(14.dp))
        LiveActivitySettingsCard()
        Spacer(Modifier.height(14.dp))
        CalibrationCard(serviceRunning = accessibilityGranted)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp).semantics { heading() },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun StatusBanner(allReady: Boolean, requiredGranted: Int) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (allReady) colors.primaryContainer else colors.surfaceContainer, label = "statusContainer")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (allReady) colors.primary else colors.outline),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (allReady) "Island is live" else "Finish setup to start the island",
                color = if (allReady) colors.onPrimaryContainer else colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (allReady) "Play a track or start a timer to see it."
                else "$requiredGranted of 2 required permissions granted.",
                color = if (allReady) colors.onPrimaryContainer.copy(alpha = .8f) else colors.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun GestureHints() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Rounded.Info, null, tint = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Tap the island to open its app. Press and hold to expand controls; tap or swipe up to close.",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

/**
 * Live calibration: drag the sliders and the floating island moves / resizes in real time so
 * it can be lined up with this device's exact camera cutout.
 */
@Composable
private fun CalibrationCard(serviceRunning: Boolean) {
    val tuning by IslandConfig.tuning.collectAsStateWithLifecycle()
    val calibrating by IslandConfig.calibrating.collectAsStateWithLifecycle()
    val cameraGuide by IslandConfig.cameraGuide.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // The guide makes the island untouchable. Never leave it on over other apps: the
    // composition survives backgrounding, so turn it off when the screen stops, too.
    DisposableEffect(Unit) { onDispose { IslandConfig.setCalibrating(false) } }
    OnLifecycleEvent(Lifecycle.Event.ON_STOP) { IslandConfig.setCalibrating(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Align to camera", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (serviceRunning) "Changes apply live to the island."
                        else "Turn on the accessibility service to preview changes live.",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { confirmReset = true }, enabled = tuning != IslandTuning.Default) {
                    Text("Reset")
                }
            }
            Spacer(Modifier.height(6.dp))

            SwitchRow("Show camera guide", calibrating,
                supporting = "Outlines the lens. The island ignores touches while this is on.") {
                IslandConfig.setCalibrating(it)
            }

            TuneSlider("Horizontal", tuning.centerXOffsetDp, IslandTuning.centerXRange,
                onChange = { IslandConfig.update(tuning.copy(centerXOffsetDp = it), persist = false) })
            TuneSlider("Vertical (top)", tuning.topOffsetDp, IslandTuning.topRange,
                onChange = { IslandConfig.update(tuning.copy(topOffsetDp = it), persist = false) })
            TuneSlider("Width", tuning.collapsedWidthDp, IslandTuning.widthRange,
                supporting = if (cameraGuide != null)
                    "Music and activity pills size themselves around the camera. This sets the lock screen and banner width."
                else null,
                onChange = { IslandConfig.update(tuning.copy(collapsedWidthDp = it), persist = false) })
            TuneSlider("Height", tuning.collapsedHeightDp, IslandTuning.heightRange,
                onChange = { IslandConfig.update(tuning.copy(collapsedHeightDp = it), persist = false) })

            Spacer(Modifier.height(4.dp))
            SwitchRow("Match screen corners", tuning.useDeviceCorners,
                supporting = "Expanded cards follow the display's rounded corners.") {
                IslandConfig.update(tuning.copy(useDeviceCorners = it))
            }
            TuneSlider("Expanded corner", tuning.expandedCornerDp, IslandTuning.cornerRange,
                dimmed = tuning.useDeviceCorners,
                valueLabel = if (tuning.useDeviceCorners) "Auto" else null,
                onChange = {
                    IslandConfig.update(tuning.copy(expandedCornerDp = it, useDeviceCorners = false), persist = false)
                })
            SwitchRow("Reflow through camera", tuning.reflowThroughCamera,
                supporting = "Activities tuck into the lens and re-emerge when they arrive or leave.") {
                IslandConfig.update(tuning.copy(reflowThroughCamera = it))
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Reset alignment?") },
        text = { Text("Position, size and corner settings return to their defaults.") },
        confirmButton = {
            TextButton(onClick = { IslandConfig.reset(); confirmReset = false }) { Text("Reset") }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, supporting: String? = null, onChange: (Boolean) -> Unit) {
    // The whole row is the touch target, announced as a single switch.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun TuneSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    supporting: String? = null,
    dimmed: Boolean = false,
    valueLabel: String? = null,
    onChange: (Float) -> Unit,
) {
    val shown = valueLabel ?: "${value.roundToInt()} dp"
    Column(Modifier.padding(top = 10.dp).alpha(if (dimmed) .6f else 1f)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(shown, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
        if (supporting != null) Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Slider(
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = shown
            },
            value = value,
            onValueChange = onChange,
            // Preview every frame, but write to disk once per drag.
            onValueChangeFinished = { IslandConfig.persist() },
            valueRange = range,
        )
    }
}

@Composable
private fun PermissionCard(
    step: String,
    title: String,
    subtitle: String,
    granted: Boolean,
    required: Boolean,
    actionLabel: String = "Grant",
    hint: String? = null,
    hintAction: Pair<String, () -> Unit>? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (granted) colors.primary else colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (granted) {
                        Icon(Icons.Rounded.Check, "Granted", tint = colors.onPrimary, modifier = Modifier.size(20.dp))
                    } else {
                        Text(step, color = colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (!required) {
                            Spacer(Modifier.width(6.dp))
                            Text("Optional", color = colors.onSurfaceVariant, fontSize = 11.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surfaceVariant)
                                    .padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                    Text(subtitle, color = colors.onSurfaceVariant, fontSize = 12.sp)
                }
                Spacer(Modifier.size(10.dp))
                if (!granted) {
                    Button(
                        onClick = onClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = if (required) ButtonDefaults.buttonColors()
                        else ButtonDefaults.buttonColors(
                            containerColor = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer,
                        ),
                    ) { Text(actionLabel, fontSize = 14.sp) }
                }
            }
            if (hint != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant.copy(alpha = .5f))
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(hint, Modifier.weight(1f), color = colors.onSurfaceVariant, fontSize = 12.sp)
                    if (hintAction != null) TextButton(onClick = hintAction.second) { Text(hintAction.first) }
                }
            }
        }
    }
}

/** Start [primary], falling back to [fallback] on OEM builds that lack the deep link. */
private fun Context.startFirst(primary: Intent, fallback: Intent) {
    try {
        startActivity(primary)
    } catch (_: ActivityNotFoundException) {
        startActivity(fallback)
    } catch (_: SecurityException) {
        startActivity(fallback)
    }
}

/** Deep-link to this app's own toggle instead of the full listener list. */
private fun openNotificationListenerSettings(context: Context) {
    val cn = ComponentName(context, MediaNotificationListener::class.java)
    context.startFirst(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString()),
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )
}

private fun openAccessibilitySettings(context: Context) {
    val fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val cn = ComponentName(context, IslandAccessibilityService::class.java)
        context.startFirst(
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, cn.flattenToString()),
            fallback,
        )
    } else context.startActivity(fallback)
}

private fun openAppDetails(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)))
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val cn = ComponentName(context, MediaNotificationListener::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.split(":")?.any { ComponentName.unflattenFromString(it) == cn } == true
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val cn = ComponentName(context, IslandAccessibilityService::class.java)
    val flat = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )
    return flat?.split(":")?.any { ComponentName.unflattenFromString(it) == cn } == true
}
