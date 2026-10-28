package com.island.dynamicisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.island.dynamicisland.media.MediaRepository
import kotlinx.coroutines.launch

/** Shell-only diagnostic exercising the exact same extra-action path as the overlay. */
class MediaActionDemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("diagnose_home", false)) {
            val home = context.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
            resultData = home ?: "unresolved"
            return
        }
        if (intent.getBooleanExtra("diagnose_artwork", false)) {
            val np = MediaRepository.nowPlaying.value
            resultData = "appArt=${np?.albumArt != null}, appleStatic=${np?.appleAlbumArt != null}, appleMotion=${np?.appleMotionUrl != null}"
            return
        }
        if (intent.getBooleanExtra("diagnose_lyrics", false)) {
            val np = MediaRepository.nowPlaying.value
            resultData = "title=${np?.title}; artist=${np?.artist}; album=${np?.album}; duration=${np?.durationMs}; position=${np?.positionAt(android.os.SystemClock.elapsedRealtime())}"
            return
        }
        if (intent.getBooleanExtra("lookup_lyrics", false)) {
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val track = (if (intent.getBooleanExtra("current", false))
                        com.island.dynamicisland.lyrics.LyricsTrack.from(MediaRepository.nowPlaying.value) else null)
                        ?: com.island.dynamicisland.lyrics.LyricsTrack(
                        intent.getStringExtra("title") ?: "Cruel Summer",
                        intent.getStringExtra("artist") ?: "Taylor Swift",
                        intent.getStringExtra("album") ?: "Lover",
                        intent.getLongExtra("duration", 178426L),
                    )
                    val lyrics = kotlinx.coroutines.withTimeout(45000L) {
                        com.island.dynamicisland.lyrics.LyricsRepository.lookup(context.applicationContext, track)
                    }
                    pending.resultData = "source=${lyrics?.source}, lines=${lyrics?.lines?.size ?: 0}, wordSynced=${lyrics?.wordSynced == true}"
                } catch (e: Exception) {
                    pending.resultData = "lookup failed: ${e.javaClass.simpleName}"
                } finally { pending.finish() }
            }
            return
        }
        if (intent.getBooleanExtra("lookup_artwork", false)) {
            val pending = goAsync()
            Thread {
                try {
                    val art = com.island.dynamicisland.media.AppleMusicArtwork.lookup(
                        intent.getStringExtra("title") ?: "cardigan",
                        intent.getStringExtra("artist") ?: "Taylor Swift",
                        intent.getStringExtra("album") ?: "folklore", 0L,
                    )
                    pending.resultData = "appleStatic=${art.image != null}, appleMotion=${art.motionUrl != null}"
                } catch (e: Exception) {
                    pending.resultData = "lookup failed: ${e.javaClass.simpleName}"
                } finally { pending.finish() }
            }.start()
            return
        }
        val np = MediaRepository.nowPlaying.value ?: return
        val id = intent.getStringExtra("action") ?: return
        if (np.extraActions.none { it.id == id }) return
        MediaRepository.commands?.customAction(np.packageName, id)
    }
}
