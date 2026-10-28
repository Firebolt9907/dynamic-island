package com.island.dynamicisland.ui.island

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log

/** Only called for a user tap: contribute the overlay's launch eligibility on Android 14+. */
internal fun sendLiveActivityIntent(context: Context, intent: PendingIntent): Boolean = runCatching {
    val options = if (Build.VERSION.SDK_INT >= 34) {
        ActivityOptions.makeBasic().apply {
            setPendingIntentBackgroundActivityStartMode(
                if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
            )
        }.toBundle()
    } else null
    // Preserve the original token and extras, including service/broadcast actions.
    intent.send(context, 0, null, null, null, null, options)
    true
}.getOrElse {
    Log.w("IslandAction", "Unable to send live activity action", it)
    false
}
