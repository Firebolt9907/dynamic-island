package com.island.dynamicisland

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.dynamicisland.config.IslandConfig
import com.island.dynamicisland.config.IslandTuning
import com.island.dynamicisland.service.IslandAccessibilityService
import com.island.dynamicisland.service.MediaNotificationListener
import com.island.dynamicisland.ui.theme.DynamicIslandTheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
                ) {
                    SetupScreen()
                }
            }
        }
    }
}

@Composable
private fun SetupScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity

    // Re-check permission state every time the screen resumes (user returns from Settings).
    var refreshKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val listenerGranted = remember(refreshKey) { isNotificationListenerEnabled(activity) }
    val accessibilityGranted = remember(refreshKey) { isAccessibilityEnabled(activity) }

    val bluetoothGranted = remember(refreshKey) {
        androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val bluetoothPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refreshKey++ }
    val allReady = listenerGranted && accessibilityGranted

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Dynamic Island",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "A smooth, live music island for Android.",
            color = Color(0xFF9A9AA0),
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(32.dp))

        PermissionCard(
            step = "1",
            title = "Notification access",
            subtitle = "Reads the currently playing track and its controls.",
            granted = listenerGranted,
        ) {
            activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        Spacer(Modifier.height(14.dp))
        PermissionCard(
            step = "2",
            title = "Accessibility · display island",
            subtitle = "Draws the island over the status bar so it can be tapped on the camera.",
            granted = accessibilityGranted,
        ) {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        Spacer(Modifier.height(14.dp))
        PermissionCard(
            step = "3",
            title = "Bluetooth audio battery",
            subtitle = "Shows the battery level reported by your connected audio device.",
            granted = bluetoothGranted,
        ) { bluetoothPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }

        Spacer(Modifier.height(28.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (allReady) Color(0xFF12351E) else Color(0xFF1A1A1E))
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (allReady) "Island is live — play a track to see it." else "Grant both permissions above to begin.",
                color = if (allReady) Color(0xFF57D982) else Color(0xFF9A9AA0),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap the island to open the app · long-press for quick controls.",
            color = Color(0xFF6A6A70),
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(28.dp))
        com.island.dynamicisland.ui.MusicControlsSettingsCard()
        Spacer(Modifier.height(24.dp))
        com.island.dynamicisland.ui.LiveActivitySettingsCard()
        Spacer(Modifier.height(24.dp))
        CalibrationCard()
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Live calibration: drag the sliders and the floating island moves / resizes in real time so
 * it can be lined up with this device's exact camera cutout.
 */
@Composable
private fun CalibrationCard() {
    val tuning by IslandConfig.tuning.collectAsStateWithLifecycle()
    val calibrating by IslandConfig.calibrating.collectAsStateWithLifecycle()

    // Never leave the full-screen guide showing once the user leaves this screen.
    DisposableEffect(Unit) { onDispose { IslandConfig.setCalibrating(false) } }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF16161A))
            .padding(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Align to camera", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Adjust live — start the island first to see it move.",
                    color = Color(0xFF9A9AA0),
                    fontSize = 12.sp,
                )
            }
            androidx.compose.material3.TextButton(onClick = { IslandConfig.reset() }) {
                Text("Reset", color = Color(0xFF34C759))
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Show camera guide", color = Color(0xFFCFCFD4), fontSize = 14.sp)
            androidx.compose.material3.Switch(
                checked = calibrating,
                onCheckedChange = { IslandConfig.setCalibrating(it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color(0xFF34C759),
                ),
            )
        }

        TuneSlider("Horizontal", tuning.centerXOffsetDp, IslandTuning.centerXRange, "dp") {
            IslandConfig.update(tuning.copy(centerXOffsetDp = it))
        }
        TuneSlider("Vertical (top)", tuning.topOffsetDp, IslandTuning.topRange, "dp") {
            IslandConfig.update(tuning.copy(topOffsetDp = it))
        }
        TuneSlider("Width", tuning.collapsedWidthDp, IslandTuning.widthRange, "dp") {
            IslandConfig.update(tuning.copy(collapsedWidthDp = it))
        }
        TuneSlider("Height", tuning.collapsedHeightDp, IslandTuning.heightRange, "dp") {
            IslandConfig.update(tuning.copy(collapsedHeightDp = it))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Match screen corners", color = Color(0xFFCFCFD4), fontSize = 14.sp)
            androidx.compose.material3.Switch(checked = tuning.useDeviceCorners,
                onCheckedChange = { IslandConfig.update(tuning.copy(useDeviceCorners = it)) })
        }
        TuneSlider("Expanded corner", tuning.expandedCornerDp, IslandTuning.cornerRange, "dp") {
            IslandConfig.update(tuning.copy(expandedCornerDp = it, useDeviceCorners = false))
        }
    }
}

@Composable
private fun TuneSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color(0xFFCFCFD4), fontSize = 13.sp)
            Text("${value.roundToInt()} $unit", color = Color(0xFF9A9AA0), fontSize = 13.sp)
        }
        Slider(
            modifier = Modifier.semantics { contentDescription = label },
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF34C759),
                activeTrackColor = Color(0xFF34C759),
                inactiveTrackColor = Color(0xFF2E2E34),
            ),
        )
    }
}

@Composable
private fun PermissionCard(
    step: String,
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF16161A))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (granted) Color(0xFF34C759) else Color(0xFF2A2A2E)),
            contentAlignment = Alignment.Center,
        ) {
            if (granted) {
                Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(20.dp))
            } else {
                Text(step, color = Color(0xFFB0B0B6), fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF9A9AA0), fontSize = 12.sp)
        }
        Spacer(Modifier.size(10.dp))
        if (!granted) {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E2E34),
                    contentColor = Color.White,
                ),
            ) { Text("Grant", fontSize = 14.sp) }
        }
    }
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
