package cz.tankono.widget.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import cz.tankono.widget.R

data class SoundItem(
    val resId: Int,
    val fileName: String,
    val displayName: String,
    val durationSec: Int
)

object SoundHelper {

    fun getAvailableSounds(context: Context): List<SoundItem> {
        val sounds = mutableListOf<SoundItem>()
        val fields = R.raw::class.java.fields
        
        for (field in fields) {
            if (field.type == Int::class.java) {
                try {
                    val resId = field.getInt(null)
                    val rawName = field.name
                    
                    var duration = -1
                    // Zkusíme nejdřív MediaMetadataRetriever, ten je bezpečnější
                    try {
                        val retriever = MediaMetadataRetriever()
                        val uri = Uri.parse("android.resource://${context.packageName}/$resId")
                        retriever.setDataSource(context, uri)
                        val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val timeMs = timeStr?.toLongOrNull() ?: 0L
                        duration = (timeMs / 1000).toInt()
                        retriever.release()
                    } catch (t: Throwable) {
                        // Fallback na MediaPlayer
                        try {
                            val mp = MediaPlayer.create(context, resId)
                            if (mp != null) {
                                duration = mp.duration / 1000
                                mp.release()
                            }
                        } catch (_: Throwable) { }
                    }

                    // Pokud se délku nepodařilo zjistit, zvuk ignorujeme
                    if (duration <= 0) {
                        AppLogger.w("SoundHelper: Přeskakuji zvuk s neznámou délkou: $rawName")
                        continue
                    }

                    val displayName = rawName.replace(".", " ").replace("_", " ")
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

                    sounds.add(SoundItem(resId, rawName, displayName, duration))
                } catch (_: Throwable) { }
            }
        }
        return sounds.sortedBy { it.displayName }
    }
}